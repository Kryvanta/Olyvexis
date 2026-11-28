# Olyvexis

**Desktop mode for Android phones without built-in desktop support.** Connect an external monitor over USB-C, or open a browser on your laptop or TV over Wi-Fi: the big screen gets a full desktop with movable, resizable windows, a taskbar, and an app drawer. Your phone becomes the trackpad and keyboard.

[Tiếng Việt](README.vi.md) · Developed by **Kryvanta** · Looking for iOS/iPadOS? See [iDesk](https://github.com/itshieuk/iDesk), the sister project with the same desktop look and shortcuts

> **Note:** Olyvexis requires Android 11+ and [Shizuku](https://shizuku.rikka.app/) (activated via Wireless Debugging - no root, no PC required). It does not replace Samsung DeX, Motorola Ready For, or Honor PC mode; disable vendor desktop mode first if your device has one.

## Features

- **Per-window virtual displays**: Works on every Android 11+ ROM, even without system freeform support (HyperOS, ColorOS, MagicOS, OriginOS, Pixel, etc.).
- **Windows**: Move, resize from edges/corners, double-click title bar to maximize, snap to halves, thirds or quarters, keep on top, remember window bounds per app, workspaces (`1-4`), `Alt + Tab` window switcher.
- **Desktop & Taskbar**: Dynamic time-of-day wallpaper & curated gradients, widgets (clock, month calendar, focus timer), sticky notes, clipboard history (`Meta + Shift + V`), lock screen (`Meta + L`), Quick Search (`Meta + Space`), Desktop Settings & Android System Settings, Quick Panel (Night Light, brightness dimmer, active windows).
- **Two display modes**:
  - **Over Wi-Fi**: Hardware-accelerated H.264 stream (`MediaCodec`) over WebSocket/WSS. Uses **WebCodecs** over self-signed **HTTPS** (`https://<ip>:8766`) for ultra-low latency, with automatic **MSE** fallback (`http://<ip>:8765`) for TV browsers.
  - **Over USB-C / HDMI**: Launches the desktop directly on external displays (`DisplayMode.External`) and turns the phone into a trackpad with an on-screen cursor.
- **Input**: Full browser mouse/wheel/keyboard forwarding (including Vietnamese Telex/VNI IME), phone trackpad with gesture controls, and compose bar.

## Connect a display

| Mode                    | What you need                                                                                |
| ----------------------- | -------------------------------------------------------------------------------------------- |
| **Cable**               | USB-C → HDMI / DisplayPort cable (for phones with DP Alt Mode)                               |
| **Laptop / TV (Wi-Fi)** | Tap **Laptop** and open the `https://` (or `http://`) address in a browser on the same Wi-Fi |
| **USB (adb)**           | `adb forward tcp:8765 tcp:8765` and open `http://localhost:8765`                             |

## Build

Requires JDK 17 or 21 and Android SDK (platform 35, `minSdk 30`).

```bash
git clone https://github.com/Kryvanta/Olyvexis.git && cd Olyvexis
./gradlew assembleDebug
```

The debug APK is generated at `app/build/outputs/apk/debug/app-debug.apk`.

Install onto a connected device:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Shortcuts

| Shortcut                   | Action                                        |
| -------------------------- | --------------------------------------------- |
| `Meta`                     | Toggle App Drawer                             |
| `Meta + D`                 | Minimize all windows (Show Desktop)           |
| `Meta + ← / → / ↑ / ↓`     | Snap left / right / maximize / minimize       |
| `Alt + Tab` / `Meta + Tab` | Switch windows                                |
| `Alt + F4`                 | Close focused window                          |
| `Esc`                      | Close App Drawer / Quick Panel / context menu |

## Project Structure

Package: `dev.kryvanta.olyvexis`

| Package      | Role                                                                                         |
| ------------ | -------------------------------------------------------------------------------------------- |
| `shizuku`    | Shizuku binder management, permissions, `ShellService` (`IShellService.aidl`)                |
| `display`    | External display detection, `VirtualDisplayController`, `CableSession`                       |
| `desktop`    | `DesktopActivity`, `DesktopController`, `DexUi`, `AppRepository`                             |
| `stream`     | `VideoEncoder` (H.264), `StreamServer` (HTTP/HTTPS/WS/WSS), `SelfSignedTls`, `StreamService` |
| `input`      | `BrowserInput`, `ActiveInput`, `InputSettings`                                               |
| `ui`         | Phone controller UI (`Controller`, `ControllerSheets`, `Welcome`, `Theme`, `DeskIcons`)      |
| `onboarding` | Shizuku wireless setup guide                                                                 |

Browser client assets live in `app/src/main/assets/web/`.

[GPL-3.0](LICENSE) · © Kryvanta · Olyvexis is not affiliated with Google, Shizuku or any app developer.
