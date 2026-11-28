// Fallback decoder when WebCodecs is unavailable (e.g. plain http://<lan-ip>,
// non-secure context, or older TV browsers): muxes H.264 Annex B into fMP4 and plays via
// Media Source Extensions (MSE) in a <video> element, which works outside secure contexts.
//
// Each video packet becomes a single-frame fragment (moof + mdat). Frame duration is only known
// when the next frame arrives, so one frame is held back (adds ~1 frame of latency).
// Wrapped in an IIFE so internal helpers (box, bytes, u32...) do not pollute the page scope.
window.MsePlayer = (() => {
'use strict';

class MsePlayer {
  constructor(video, onError) {
    this.video = video;
    this.onError = onError;
    this.source = null;
    this.buffer = null;
    this.queue = [];
    this.seq = 1;
    this.firstPts = null;
    this.held = null;        // frame held back until next frame reveals its duration
    this.ready = false;
    this.lastTrim = 0;
  }

  static supported() {
    const MS = window.MediaSource || window.ManagedMediaSource;
    return !!MS && MS.isTypeSupported('video/mp4; codecs="avc1.42E01F"');
  }

  /** data: Uint8Array Annex B; ptsUs: number; key: whether frame is a keyframe (contains SPS/PPS). */
  push(data, ptsUs, key) {
    if (!this.ready) {
      if (!key) return;
      const nals = splitNals(data);
      const sps = nals.find(n => (n[0] & 0x1f) === 7);
      const pps = nals.find(n => (n[0] & 0x1f) === 8);
      if (!sps || !pps) return;
      this.start(sps, pps);
    }
    const sample = { data: toAvcc(data), pts: ptsUs, key };
    if (this.held) {
      const duration = Math.max(1, Math.round((sample.pts - this.held.pts) * 0.09));
      this.append(this.fragment(this.held, duration));
    }
    this.held = sample;
  }

  start(sps, pps) {
    const MS = window.MediaSource || window.ManagedMediaSource;
    const codec = 'avc1.' + [sps[1], sps[2], sps[3]].map(b => b.toString(16).padStart(2, '0')).join('');
    const size = spsSize(sps);
    this.source = new MS();
    if (window.ManagedMediaSource && MS === window.ManagedMediaSource) this.video.disableRemotePlayback = true;
    this.video.src = URL.createObjectURL(this.source);
    this.ready = true;
    this.source.addEventListener('sourceopen', () => {
      try {
        this.buffer = this.source.addSourceBuffer(`video/mp4; codecs="${codec}"`);
        this.buffer.mode = 'segments';
        this.buffer.addEventListener('updateend', () => this.flush());
        this.buffer.addEventListener('error', () => this.onError('Lỗi MSE'));
        this.queue.unshift(initSegment(sps, pps, size.width, size.height));
        this.flush();
      } catch (e) {
        this.onError('MSE: ' + e.message);
      }
    }, { once: true });
    this.video.play().catch(() => {});
  }

  fragment(sample, duration) {
    if (this.firstPts === null) this.firstPts = sample.pts;
    const dts = Math.round((sample.pts - this.firstPts) * 0.09);
    return mediaSegment(this.seq++, dts, duration, sample.data, sample.key);
  }

  append(segment) {
    this.queue.push(segment);
    // Queue growing means decoder is falling behind: drop until next keyframe.
    if (this.queue.length > 120) this.queue.splice(1);
    this.flush();
  }

  flush() {
    const b = this.buffer;
    if (!b || b.updating || this.queue.length === 0) return;
    try {
      b.appendBuffer(this.queue.shift());
    } catch (e) {
      if (e.name === 'QuotaExceededError') this.trim(true);
      else this.onError('MSE: ' + e.message);
      return;
    }
    this.catchUp();
  }

  /** Keep playback near the live edge for low latency. */
  catchUp() {
    const v = this.video;
    if (v.buffered.length === 0) return;
    const end = v.buffered.end(v.buffered.length - 1);
    if (end - v.currentTime > 0.25) v.currentTime = Math.max(0, end - 0.05);
    if (v.paused) v.play().catch(() => {});
    const now = performance.now();
    if (now - this.lastTrim > 10000) { this.lastTrim = now; this.trim(false); }
  }

  /** Trim already-played buffer ranges to prevent unbounded memory growth. */
  trim(force) {
    const b = this.buffer, v = this.video;
    if (!b || b.updating || v.buffered.length === 0) return;
    const start = v.buffered.start(0);
    const keep = force ? 1 : 10;
    if (v.currentTime - start > keep) b.remove(start, v.currentTime - 1);
  }

  close() {
    try { if (this.source && this.source.readyState === 'open') this.source.endOfStream(); } catch (_) {}
    this.ready = false;
  }
}

// ------------------------------------------------------------------ H.264

/** Split Annex B byte stream into NAL units (without start codes). */
function splitNals(data) {
  const out = [];
  let i = 0, start = -1;
  while (i + 2 < data.length) {
    if (data[i] === 0 && data[i + 1] === 0 && (data[i + 2] === 1 || (data[i + 2] === 0 && data[i + 3] === 1))) {
      if (start >= 0) out.push(data.subarray(start, i));
      i += data[i + 2] === 1 ? 3 : 4;
      start = i;
    } else {
      i++;
    }
  }
  if (start >= 0) out.push(data.subarray(start));
  return out;
}

/** Annex B -> AVCC (4-byte length prefix per NAL), stripping SPS/PPS/AUD already in avcC. */
function toAvcc(data) {
  const nals = splitNals(data).filter(n => { const t = n[0] & 0x1f; return t !== 7 && t !== 8 && t !== 9; });
  let size = 0;
  for (const n of nals) size += 4 + n.length;
  const out = new Uint8Array(size);
  const dv = new DataView(out.buffer);
  let o = 0;
  for (const n of nals) { dv.setUint32(o, n.length); out.set(n, o + 4); o += 4 + n.length; }
  return out;
}

/** Parse frame dimensions from SPS (strips emulation prevention bytes, reads Exp-Golomb). */
function spsSize(sps) {
  const bytes = [];
  for (let i = 0; i < sps.length; i++) {
    if (i >= 2 && sps[i] === 3 && sps[i - 1] === 0 && sps[i - 2] === 0) continue;
    bytes.push(sps[i]);
  }
  let bit = 8; // skip NAL header byte
  const u = n => { let v = 0; for (let k = 0; k < n; k++, bit++) v = (v << 1) | ((bytes[bit >> 3] >> (7 - (bit & 7))) & 1); return v; };
  const ue = () => { let z = 0; while (u(1) === 0 && z < 32) z++; return (1 << z) - 1 + u(z); };
  const se = () => { const v = ue(); return v & 1 ? (v + 1) / 2 : -v / 2; };
  const profile = u(8); u(16); ue();
  let chroma = 1;
  if ([100, 110, 122, 244, 44, 83, 86, 118, 128, 138, 139, 134, 135].includes(profile)) {
    chroma = ue();
    if (chroma === 3) u(1);
    ue(); ue(); u(1);
    if (u(1)) {
      for (let i = 0; i < (chroma !== 3 ? 8 : 12); i++) {
        if (u(1)) { let last = 8, next = 8; for (let j = 0; j < (i < 6 ? 16 : 64); j++) { if (next) next = (last + se() + 256) % 256; last = next || last; } }
      }
    }
  }
  ue();
  const pocType = ue();
  if (pocType === 0) ue();
  else if (pocType === 1) { u(1); se(); se(); const n = ue(); for (let i = 0; i < n; i++) se(); }
  ue(); u(1);
  const wMbs = ue() + 1, hMapUnits = ue() + 1;
  const frameMbsOnly = u(1);
  if (!frameMbsOnly) u(1);
  u(1);
  let cl = 0, cr = 0, ct = 0, cb = 0;
  if (u(1)) { cl = ue(); cr = ue(); ct = ue(); cb = ue(); }
  const cropX = chroma === 0 ? 1 : 2, cropY = (chroma === 1 ? 2 : 1) * (2 - frameMbsOnly);
  return {
    width: wMbs * 16 - (cl + cr) * cropX,
    height: (2 - frameMbsOnly) * hMapUnits * 16 - (ct + cb) * cropY,
  };
}

// ------------------------------------------------------------------ MP4 boxes

function box(type, ...parts) {
  let size = 8;
  for (const p of parts) size += p.length;
  const out = new Uint8Array(size);
  new DataView(out.buffer).setUint32(0, size);
  for (let i = 0; i < 4; i++) out[4 + i] = type.charCodeAt(i);
  let o = 8;
  for (const p of parts) { out.set(p, o); o += p.length; }
  return out;
}

function bytes(...values) { return new Uint8Array(values); }
function u16(v) { return bytes((v >> 8) & 255, v & 255); }
function u32(v) { return bytes((v >>> 24) & 255, (v >>> 16) & 255, (v >>> 8) & 255, v & 255); }
function u64(v) { return new Uint8Array([...u32(Math.floor(v / 4294967296)), ...u32(v % 4294967296)]); }
function zeros(n) { return new Uint8Array(n); }
function str(s) { return new Uint8Array([...s].map(c => c.charCodeAt(0))); }
const MATRIX = new Uint8Array([...u32(0x00010000), ...u32(0), ...u32(0), ...u32(0), ...u32(0x00010000), ...u32(0), ...u32(0), ...u32(0), ...u32(0x40000000)]);
const TIMESCALE = 90000;

function initSegment(sps, pps, width, height) {
  const ftyp = box('ftyp', str('isom'), u32(0x200), str('isom'), str('iso2'), str('avc1'), str('mp41'));
  const mvhd = box('mvhd', u32(0), u32(0), u32(0), u32(1000), u32(0), u32(0x00010000), u16(0x0100), zeros(10), MATRIX, zeros(24), u32(2));
  const tkhd = box('tkhd', u32(3), u32(0), u32(0), u32(1), u32(0), u32(0), zeros(8), u16(0), u16(0), u16(0), u16(0), MATRIX, u32(width << 16), u32(height << 16));
  const mdhd = box('mdhd', u32(0), u32(0), u32(0), u32(TIMESCALE), u32(0), u16(0x55c4), u16(0));
  const hdlr = box('hdlr', u32(0), u32(0), str('vide'), zeros(12), str('VideoHandler'), zeros(1));
  const avcC = box('avcC', bytes(1, sps[1], sps[2], sps[3], 0xff, 0xe1), u16(sps.length), sps, bytes(1), u16(pps.length), pps);
  const avc1 = box('avc1', zeros(6), u16(1), zeros(16), u16(width), u16(height), u32(0x00480000), u32(0x00480000), u32(0), u16(1), zeros(32), u16(0x18), u16(0xffff), avcC);
  const stsd = box('stsd', u32(0), u32(1), avc1);
  const empty = t => box(t, u32(0), u32(0));
  const stbl = box('stbl', stsd, empty('stts'), empty('stsc'), box('stsz', u32(0), u32(0), u32(0)), empty('stco'));
  const dinf = box('dinf', box('dref', u32(0), u32(1), box('url ', u32(1))));
  const minf = box('minf', box('vmhd', u32(1), zeros(8)), dinf, stbl);
  const mdia = box('mdia', mdhd, hdlr, minf);
  const trak = box('trak', tkhd, mdia);
  const mvex = box('mvex', box('trex', u32(0), u32(1), u32(1), u32(0), u32(0), u32(0)));
  const moov = box('moov', mvhd, trak, mvex);
  const out = new Uint8Array(ftyp.length + moov.length);
  out.set(ftyp); out.set(moov, ftyp.length);
  return out;
}

function mediaSegment(seq, dts, duration, data, key) {
  const flags = key ? 0x02000000 : 0x01010000; // keyframe: independent; delta: dependent, non-sync
  const build = offset => box('moof',
    box('mfhd', u32(0), u32(seq)),
    box('traf',
      box('tfhd', u32(0x020000), u32(1)),
      box('tfdt', u32(0x01000000), u64(dts)),
      box('trun', u32(0x000701), u32(1), u32(offset), u32(duration), u32(data.length), u32(flags))));
  const size = build(0).length;
  const moof = build(size + 8);
  const mdat = box('mdat', data);
  const out = new Uint8Array(moof.length + mdat.length);
  out.set(moof); out.set(mdat, moof.length);
  return out;
}

return MsePlayer;
})();
