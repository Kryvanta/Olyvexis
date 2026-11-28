package dev.kryvanta.olyvexis.stream

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.math.BigInteger
import java.net.InetAddress
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.SecureRandom
import java.security.Signature
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.spec.ECGenParameterSpec
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext

/**
 * Generates an in-memory self-signed X.509 v3 certificate (ECDSA P-256) for local IPs and
 * `localhost`, so `https://<lan-ip>:<port>` provides a Secure Context for WebCodecs.
 */
object SelfSignedTls {

    data class Bundle(
        val sslContext: SSLContext,
        val certificate: X509Certificate,
    )

    fun create(ips: List<String>): Bundle {
        val kpg = KeyPairGenerator.getInstance("EC").apply {
            initialize(ECGenParameterSpec("secp256r1"), SecureRandom())
        }
        val keyPair = kpg.generateKeyPair()

        val now = System.currentTimeMillis()
        val notBefore = Date(now - 24L * 3600_000) // backdate 1 day for minor clock skew
        val notAfter = Date(now + 3650L * 24 * 3600_000) // 10 years
        val serial = BigInteger(63, SecureRandom()).abs().max(BigInteger.ONE)

        val algId = derSeq(derOid(1, 2, 840, 10045, 4, 3, 2)) // ecdsa-with-SHA256
        val name = derSeq(derSet(derSeq(derOid(2, 5, 4, 3), derUtf8("Olyvexis"))))
        val validity = derSeq(derUtcTime(notBefore), derUtcTime(notAfter))

        // SubjectAltName: localhost + 127.0.0.1 + all current local IPv4 addresses
        val sanItems = mutableListOf<ByteArray>()
        sanItems += derContextPrimitive(2, "localhost".toByteArray(Charsets.US_ASCII))
        val allIps = (listOf("127.0.0.1") + ips).distinct()
        for (ip in allIps) {
            runCatching { InetAddress.getByName(ip).address }.getOrNull()?.let { addr ->
                sanItems += derContextPrimitive(7, addr)
            }
        }

        val extensions = derSeq(
            // BasicConstraints: CA=FALSE (critical)
            derSeq(derOid(2, 5, 29, 19), derBoolean(true), derOctet(derSeq())),
            // KeyUsage: digitalSignature | keyEncipherment (critical)
            derSeq(derOid(2, 5, 29, 15), derBoolean(true), derOctet(derBitString(5, byteArrayOf(0xA0.toByte())))),
            // ExtendedKeyUsage: serverAuth
            derSeq(derOid(2, 5, 29, 37), derOctet(derSeq(derOid(1, 3, 6, 1, 5, 5, 7, 3, 1)))),
            // SubjectAltName
            derSeq(derOid(2, 5, 29, 17), derOctet(derSeq(*sanItems.toTypedArray()))),
        )

        val tbs = derSeq(
            derContextConstructed(0, derInt(BigInteger.valueOf(2))), // version v3
            derInt(serial),
            algId,
            name,
            validity,
            name,
            keyPair.public.encoded,
            derContextConstructed(3, extensions),
        )

        val sig = Signature.getInstance("SHA256withECDSA").run {
            initSign(keyPair.private)
            update(tbs)
            sign()
        }

        val certDer = derSeq(tbs, algId, derBitString(0, sig))
        val cert = CertificateFactory.getInstance("X.509")
            .generateCertificate(ByteArrayInputStream(certDer)) as X509Certificate

        val password = "olyvexis".toCharArray()
        val keyStore = KeyStore.getInstance("PKCS12").apply {
            load(null, null)
            setKeyEntry("olyvexis", keyPair.private, password, arrayOf(cert))
        }
        val kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm()).apply {
            init(keyStore, password)
        }
        val ctx = SSLContext.getInstance("TLS").apply {
            init(kmf.keyManagers, null, SecureRandom())
        }
        return Bundle(ctx, cert)
    }

    // ---------------------------------------------------------------- ASN.1 DER helpers

    private fun derTag(tag: Int, content: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(tag)
        val len = content.size
        when {
            len < 128 -> out.write(len)
            len <= 0xFF -> {
                out.write(0x81)
                out.write(len)
            }
            else -> {
                out.write(0x82)
                out.write(len shr 8)
                out.write(len and 0xFF)
            }
        }
        out.write(content)
        return out.toByteArray()
    }

    private fun derSeq(vararg items: ByteArray): ByteArray {
        val body = ByteArrayOutputStream()
        items.forEach { body.write(it) }
        return derTag(0x30, body.toByteArray())
    }

    private fun derSet(vararg items: ByteArray): ByteArray {
        val body = ByteArrayOutputStream()
        items.forEach { body.write(it) }
        return derTag(0x31, body.toByteArray())
    }

    private fun derInt(value: BigInteger): ByteArray = derTag(0x02, value.toByteArray())
    private fun derBoolean(v: Boolean): ByteArray = derTag(0x01, byteArrayOf(if (v) 0xFF.toByte() else 0x00))
    private fun derOctet(bytes: ByteArray): ByteArray = derTag(0x04, bytes)
    private fun derUtf8(s: String): ByteArray = derTag(0x0C, s.toByteArray(Charsets.UTF_8))
    private fun derContextPrimitive(num: Int, bytes: ByteArray): ByteArray = derTag(0x80 or num, bytes)
    private fun derContextConstructed(num: Int, bytes: ByteArray): ByteArray = derTag(0xA0 or num, bytes)

    private fun derBitString(unusedBits: Int, bytes: ByteArray): ByteArray {
        val out = ByteArray(1 + bytes.size)
        out[0] = unusedBits.toByte()
        System.arraycopy(bytes, 0, out, 1, bytes.size)
        return derTag(0x03, out)
    }

    private fun derUtcTime(date: Date): ByteArray {
        val fmt = SimpleDateFormat("yyMMddHHmmss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        return derTag(0x17, fmt.format(date).toByteArray(Charsets.US_ASCII))
    }

    private fun derOid(vararg arcs: Int): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(arcs[0] * 40 + arcs[1])
        for (i in 2 until arcs.size) {
            var v = arcs[i]
            val parts = ArrayList<Int>()
            parts.add(v and 0x7F)
            v = v ushr 7
            while (v > 0) {
                parts.add((v and 0x7F) or 0x80)
                v = v ushr 7
            }
            for (j in parts.lastIndex downTo 0) out.write(parts[j])
        }
        return derTag(0x06, out.toByteArray())
    }
}
