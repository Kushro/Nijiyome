// SPDX-License-Identifier: AGPL-3.0-or-later
// Ported from Tatakai Next tk-link/src/identity.rs and protect.rs (AGPL-3.0, same author), adapted from Enigmata-Temple
// temple-core/src/identity.rs. rcgen / ring replaced by Bouncy Castle (MIT).
package app.nijiyome.server.link

import app.nijiyome.link.LinkCrypto
import org.bouncycastle.asn1.ASN1ObjectIdentifier
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x500.X500NameBuilder
import org.bouncycastle.asn1.x500.style.BCStyle
import org.bouncycastle.asn1.x509.BasicConstraints
import org.bouncycastle.asn1.x509.ExtendedKeyUsage
import org.bouncycastle.asn1.x509.Extension
import org.bouncycastle.asn1.x509.GeneralName
import org.bouncycastle.asn1.x509.GeneralNames
import org.bouncycastle.asn1.x509.KeyPurposeId
import org.bouncycastle.asn1.x509.KeyUsage
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo
import org.bouncycastle.cert.X509v3CertificateBuilder
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509ExtensionUtils
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import org.bouncycastle.operator.jcajce.JcaContentVerifierProviderBuilder
import org.bouncycastle.pkcs.PKCS10CertificationRequest
import java.io.File
import java.math.BigInteger
import java.net.InetAddress
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermission
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.PublicKey
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import java.time.Duration
import java.time.Instant
import java.util.Date
import java.util.UUID

class IdentityException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** A client certificate issued at pairing. */
class ClientCertificate(val deviceId: String, val certificate: X509Certificate, val pem: String, val fingerprint: String, val notAfter: Instant)

/**
 * This PC's Link identity: an own CA (the phone pins the SHA-256 of its DER) and a server certificate signed by it, with every
 * LAN IPv4 it listens on + 127.0.0.1 + DNS `localhost` as SANs, re-issued when the IPs change or it nears expiry (the CA never
 * changes). Client certificates are issued from the phone's CSR: only its P-256 public key is taken; the server assigns the
 * device id as CN. Files in the injected link dir: `ca.der`, `ca.key`, `server.crt.pem`, `server.key`.
 */
class Identity private constructor(
    val ca: X509Certificate,
    private val caKey: PrivateKey,
    server: X509Certificate,
    serverKey: PrivateKey,
    private val sealer: KeySealer,
) {
    @Volatile var server: X509Certificate = server
        private set

    @Volatile var serverKey: PrivateKey = serverKey
        private set

    val caFingerprint: String get() = LinkCrypto.fingerprint(ca)
    val caPem: String get() = LinkCrypto.certificatePem(ca)

    /** Leaf + CA, so the phone checks its pin during pairing. */
    fun serverChain(): Array<X509Certificate> = arrayOf(server, ca)

    fun serverIps(): Set<InetAddress> = sanIps(server)

    /** Re-issues the server certificate if its SANs differ from [ips] + 127.0.0.1, it nears expiry or it is not ours. */
    fun ensureServerCertificate(dir: File, ips: List<InetAddress>, now: Instant): Boolean {
        val wanted = ipSet(ips)
        if (!serverNeedsRenewal(wanted, now)) return false
        val (cert, key) = issueServer(wanted, now)
        server = cert
        serverKey = key
        persistServer(dir)
        return true
    }

    /** A client certificate for the P-256 key of a (signature-verified) CSR; the requested subject and extensions are ignored. */
    fun issueClientFromCsr(csrPem: String, now: Instant): ClientCertificate {
        val publicKey = csrPublicKey(csrPem) ?: throw IdentityException("CSR is malformed, its signature is wrong or the key is not P-256")
        val deviceId = UUID.randomUUID().toString()
        val notAfter = wholeSeconds(now + CLIENT_VALIDITY)
        val cert = sign(
            subject = dn(deviceId),
            publicKey = publicKey,
            notBefore = wholeSeconds(now - BACKDATE),
            notAfter = notAfter,
        ) { b, utils ->
            b.addExtension(Extension.keyUsage, true, KeyUsage(KeyUsage.digitalSignature))
            b.addExtension(Extension.extendedKeyUsage, false, ExtendedKeyUsage(KeyPurposeId.id_kp_clientAuth))
            b.addExtension(Extension.authorityKeyIdentifier, false, utils.createAuthorityKeyIdentifier(ca))
        }
        return ClientCertificate(deviceId, cert, LinkCrypto.certificatePem(cert), LinkCrypto.fingerprint(cert), notAfter)
    }

    private fun serverNeedsRenewal(wanted: Set<InetAddress>, now: Instant): Boolean {
        val renewAt = server.notAfter.toInstant() - SERVER_RENEWAL_MARGIN
        val signedByUs = runCatching { server.verify(ca.publicKey) }.isSuccess
        return now >= renewAt || sanIps(server) != wanted || !keyMatches(server, serverKey) || !signedByUs
    }

    private fun issueServer(ips: Set<InetAddress>, now: Instant): Pair<X509Certificate, PrivateKey> {
        val key = generateKey()
        val sans = listOf(GeneralName(GeneralName.dNSName, "localhost")) + ips.sortedBy { it.hostAddress }.map { GeneralName(GeneralName.iPAddress, it.hostAddress) }
        val cert = sign(dn("Nijiyome Link server"), key.public, wholeSeconds(now - BACKDATE), wholeSeconds(now + SERVER_VALIDITY)) { b, utils ->
            b.addExtension(Extension.subjectAlternativeName, false, GeneralNames(sans.toTypedArray()))
            b.addExtension(Extension.keyUsage, true, KeyUsage(KeyUsage.digitalSignature))
            b.addExtension(Extension.extendedKeyUsage, false, ExtendedKeyUsage(KeyPurposeId.id_kp_serverAuth))
            b.addExtension(Extension.authorityKeyIdentifier, false, utils.createAuthorityKeyIdentifier(ca))
        }
        return cert to key.private
    }

    private fun sign(
        subject: X500Name,
        publicKey: PublicKey,
        notBefore: Instant,
        notAfter: Instant,
        extensions: (X509v3CertificateBuilder, JcaX509ExtensionUtils) -> Unit,
    ): X509Certificate {
        val b = X509v3CertificateBuilder(
            X500Name.getInstance(ca.subjectX500Principal.encoded),
            randomSerial(),
            Date.from(notBefore),
            Date.from(notAfter),
            subject,
            SubjectPublicKeyInfo.getInstance(publicKey.encoded),
        )
        extensions(b, JcaX509ExtensionUtils())
        return JcaX509CertificateConverter().getCertificate(b.build(JcaContentSignerBuilder(SIG_ALG).build(caKey)))
    }

    private fun persistServer(dir: File) {
        File(dir, SERVER_CERT_FILE).writeText(LinkCrypto.certificatePem(server))
        writeSecret(File(dir, SERVER_KEY_FILE), serverKey.encoded, sealer)
    }

    override fun toString(): String = "Identity(ca=${caFingerprint.take(8)}…)"

    companion object {
        val CA_VALIDITY: Duration = Duration.ofDays(3650)
        val SERVER_VALIDITY: Duration = Duration.ofDays(365)
        val CLIENT_VALIDITY: Duration = Duration.ofDays(365)
        val SERVER_RENEWAL_MARGIN: Duration = Duration.ofDays(30)
        /** Back-dating tolerates clock skew between PC and phone. */
        private val BACKDATE: Duration = Duration.ofHours(1)
        private const val CA_CERT_FILE = "ca.der"
        private const val CA_KEY_FILE = "ca.key"
        private const val SERVER_CERT_FILE = "server.crt.pem"
        private const val SERVER_KEY_FILE = "server.key"
        private const val ORG = "Nijiyome Link"
        private const val SIG_ALG = "SHA256withECDSA"
        private val P256_OID = ASN1ObjectIdentifier("1.2.840.10045.3.1.7")
        private val random = SecureRandom()

        /**
         * Loads the identity from [dir] or creates it; re-issues the server certificate when [ips] changed or it nears expiry.
         * The private keys are stored through [sealer]; a plain key from before sealing is rewritten sealed.
         */
        fun loadOrInit(dir: File, ips: List<InetAddress>, now: Instant = Instant.now(), sealer: KeySealer = KeySealer.Plain): Identity {
            dir.mkdirs()
            val caFile = File(dir, CA_CERT_FILE)
            val caKeyFile = File(dir, CA_KEY_FILE)
            if (!caFile.isFile || !caKeyFile.isFile) return init(dir, ips, now, sealer)
            val ca = LinkCrypto.certificateFromDer(caFile.readBytes())
            val caKey = runCatching { ecPrivate(readSecret(caKeyFile, sealer)) }.getOrElse { throw IdentityException("CA key is unreadable", it) }
            if (!keyMatches(ca, caKey)) throw IdentityException("CA key does not match its certificate")
            val existing = runCatching {
                LinkCrypto.certificateFromPem(File(dir, SERVER_CERT_FILE).readText()) to ecPrivate(readSecret(File(dir, SERVER_KEY_FILE), sealer))
            }.getOrNull()
            val id = if (existing != null) {
                Identity(ca, caKey, existing.first, existing.second, sealer)
            } else {
                // a placeholder leaf, replaced right below
                Identity(ca, caKey, ca, caKey, sealer)
            }
            id.ensureServerCertificate(dir, ips, now)
            return id
        }

        private fun init(dir: File, ips: List<InetAddress>, now: Instant, sealer: KeySealer): Identity {
            val key = generateKey()
            val serial = randomSerial()
            val suffix = serial.toByteArray().takeLast(4).joinToString("") { "%02x".format(it) }
            val name = dn("Nijiyome Link CA $suffix")
            val b = X509v3CertificateBuilder(
                name, serial, Date.from(wholeSeconds(now - BACKDATE)), Date.from(wholeSeconds(now + CA_VALIDITY)), name,
                SubjectPublicKeyInfo.getInstance(key.public.encoded),
            )
            b.addExtension(Extension.basicConstraints, true, BasicConstraints(0))
            b.addExtension(Extension.keyUsage, true, KeyUsage(KeyUsage.keyCertSign or KeyUsage.cRLSign or KeyUsage.digitalSignature))
            b.addExtension(Extension.subjectKeyIdentifier, false, JcaX509ExtensionUtils().createSubjectKeyIdentifier(key.public))
            val ca = JcaX509CertificateConverter().getCertificate(b.build(JcaContentSignerBuilder(SIG_ALG).build(key.private)))
            File(dir, CA_CERT_FILE).writeBytes(ca.encoded)
            writeSecret(File(dir, CA_KEY_FILE), key.private.encoded, sealer)
            val id = Identity(ca, key.private, ca, key.private, sealer)
            id.ensureServerCertificate(dir, ips, now)
            return id
        }

        fun generateKey(): KeyPair = KeyPairGenerator.getInstance("EC").run {
            initialize(ECGenParameterSpec("secp256r1"))
            generateKeyPair()
        }

        /** The P-256 public key of a PEM CSR whose self-signature verifies; null for anything else (never consumes an attempt). */
        fun csrPublicKey(csrPem: String): PublicKey? = runCatching {
            val csr = PKCS10CertificationRequest(LinkCrypto.pemDer(csrPem, "CERTIFICATE REQUEST"))
            val spki = csr.subjectPublicKeyInfo
            val curve = spki.algorithm.parameters?.let { ASN1ObjectIdentifier.getInstance(it) }
            if (spki.algorithm.algorithm.id != "1.2.840.10045.2.1" || curve != P256_OID) return null
            if (!csr.isSignatureValid(JcaContentVerifierProviderBuilder().build(spki))) return null
            KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(spki.encoded)) as? ECPublicKey
        }.getOrNull()

        private fun dn(cn: String): X500Name = X500NameBuilder(BCStyle.INSTANCE).addRDN(BCStyle.O, ORG).addRDN(BCStyle.CN, cn).build()

        private fun ipSet(ips: List<InetAddress>): Set<InetAddress> = (ips + InetAddress.getByName("127.0.0.1")).toSet()

        private fun sanIps(cert: X509Certificate): Set<InetAddress> =
            (runCatching { cert.subjectAlternativeNames }.getOrNull() ?: emptyList())
                .filter { it.size >= 2 && it[0] == GeneralName.iPAddress }
                .mapNotNull { runCatching { InetAddress.getByName(it[1] as String) }.getOrNull() }
                .toSet()

        private fun keyMatches(cert: X509Certificate, key: PrivateKey): Boolean = runCatching {
            val probe = byteArrayOf(1, 2, 3)
            val sig = java.security.Signature.getInstance(SIG_ALG).apply { initSign(key); update(probe) }.sign()
            java.security.Signature.getInstance(SIG_ALG).apply { initVerify(cert.publicKey); update(probe) }.verify(sig)
        }.getOrDefault(false)

        /** 128-bit positive serial without a leading zero byte. */
        private fun randomSerial(): BigInteger {
            val b = ByteArray(16).also(random::nextBytes)
            b[0] = ((b[0].toInt() and 0x7f) or 0x40).toByte()
            return BigInteger(1, b)
        }

        private fun wholeSeconds(t: Instant): Instant = Instant.ofEpochSecond(t.epochSecond)

        private fun ecPrivate(der: ByteArray): PrivateKey = KeyFactory.getInstance("EC").generatePrivate(PKCS8EncodedKeySpec(der))

        /** Private keys at rest: sealed by [sealer] (the app's secret store on desktop) and 0600 where POSIX permissions exist. */
        private fun writeSecret(file: File, data: ByteArray, sealer: KeySealer) {
            val tmp = File(file.path + ".tmp")
            tmp.writeBytes(sealer.seal(data))
            runCatching { Files.setPosixFilePermissions(tmp.toPath(), setOf(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE)) }
            if (!tmp.renameTo(file)) {
                file.delete()
                tmp.renameTo(file)
            }
        }

        private fun readSecret(file: File, sealer: KeySealer): ByteArray {
            val stored = file.readBytes()
            val plain = sealer.open(stored)
            // a key written before sealing: rewrite it sealed (best effort; it is read fine either way)
            if (!sealer.isSealed(stored)) runCatching { writeSecret(file, plain, sealer) }
            return plain
        }
    }
}
