package kttp.security

import kttp.log.Logger
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo
import org.bouncycastle.cert.X509v3CertificateBuilder
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import java.io.IOException
import java.io.InputStream
import java.math.BigInteger
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.security.*
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.spec.PKCS8EncodedKeySpec
import java.time.Duration
import java.time.Instant
import java.util.*
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory
import kotlin.io.path.inputStream

object SSL {

    private val log: Logger = Logger(javaClass)

    fun createDefaultSSLContextFromKeyStore(
        sslAlgorithm: String = "TLS",
        keyStoreOptions: KeyStoreOptions = KeyStoreOptions(),
        keyOptions: KeyOptions = KeyOptions(),
        certificateOptions: CertificateOptions = CertificateOptions()
    ): SSLContext {
        val sslContext = SSLContext.getInstance(sslAlgorithm)
        val keyStoreFile = keyStoreOptions.keyStoreFilePath.toFile()
        val keyStore = keyStoreOptions.keyStore
        val keyStorePassword = keyStoreOptions.keyStorePassword.toCharArray()
        if (!keyStoreFile.exists()) {
            keyStore.load(null, null)
            // generate keypair

            log.debug { "Generating new keyPair" }
            val keyPair = generateKeyPair(keyOptions)
            log.debug { "Generating certificates" }
            val cert = generateCertificate(
                certificateOptions.dn,
                keyPair,
                certificateOptions.certValidity,
                certificateOptions.certSignatureAlgorithm
            )
            keyStore.setKeyEntry(keyStoreOptions.keyPairAlias, keyPair.private, keyStorePassword, arrayOf(cert))
            if (keyStoreOptions.createIfNotExists)
                keyStore.store(keyStoreFile.outputStream(), keyStorePassword).also {
                    log.debug { "KeyStore created at ${keyStoreFile.absolutePath}" }
                }

        }
        if (keyStoreFile.exists())
            keyStore.load(keyStoreFile.inputStream(), keyStorePassword).also {
                log.debug { "KeyStore loaded from ${keyStoreFile.absolutePath}" }
            }

        val keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
        val trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())

        keyManagerFactory.init(keyStore, keyStorePassword)

        trustManagerFactory.init(keyStore)
        sslContext.init(keyManagerFactory.keyManagers, trustManagerFactory.trustManagers, null)
        log.debug { "SSLContext initialized" }
        return sslContext
    }

    fun createSSLContextFromCertificateAndKey(certificatePath: String, keyPath: String): SSLContext {
        return createSSLContextFromCertificateAndKey(Paths.get(certificatePath), Paths.get(keyPath))
    }

    fun createSSLContextFromCertificateAndKey(certificatePath: Path, keyPath: Path): SSLContext {
        if (!Files.exists(certificatePath))
            throw IllegalArgumentException("Certificate file does not exist")
        if (!Files.exists(keyPath))
            throw IllegalArgumentException("Key file does not exist")
        return createSSLContextFromCertificateChainAndKey(listOf(certificatePath.inputStream()), keyPath.inputStream())
    }

    fun createSSLContextFromCertificateChainAndKey(certificateChain: List<Path>, keyPath: Path): SSLContext {
        if (certificateChain.any { !Files.exists(it) })
            throw IllegalArgumentException("Certificate file does not exist")
        if (!Files.exists(keyPath))
            throw IllegalArgumentException("Key file does not exist")
        val streams = certificateChain.map { it.inputStream() }
        return createSSLContextFromCertificateChainAndKey(streams, keyPath.inputStream())
    }

    fun createSSLContextFromCertificateChainAndKey(
        certificateChainStreams: List<InputStream>,
        keyStream: InputStream
    ): SSLContext {
        val sslContext = SSLContext.getInstance("TLS")
        val keyStore = KeyStore.getInstance(KeyStore.getDefaultType())
        keyStore.load(null, null)
        val certificateFactory = CertificateFactory.getInstance("X.509")
        val certificates = certificateChainStreams.map { certificateFactory.generateCertificate(it) as X509Certificate }
        val privateKeyBytes = keyStream.readAllBytes()
        val privateKeyString = String(privateKeyBytes).replace("-----BEGIN PRIVATE KEY-----", "")
            .replace("-----END PRIVATE KEY-----", "")
            .replace("\n", "")
        val decoded = Base64.getDecoder().decode(privateKeyString)
        val keySpec = PKCS8EncodedKeySpec(decoded)
        val privateKey = KeyFactory.getInstance("RSA").generatePrivate(keySpec)

        keyStore.setKeyEntry("alias", privateKey, null, certificates.toTypedArray())
        val keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
        keyManagerFactory.init(keyStore, null)
        sslContext.init(keyManagerFactory.keyManagers, null, null)

        return sslContext
    }

    fun generateKeyPair(keyOptions: KeyOptions = KeyOptions()): KeyPair {
        val keyPairGenerator = KeyPairGenerator.getInstance(keyOptions.keyAlgorithm)
        keyPairGenerator.initialize(keyOptions.keySize)
        return keyPairGenerator.genKeyPair()
    }


    @Throws(GeneralSecurityException::class, IOException::class)
    fun generateCertificate(dn: String?, keyPair: KeyPair, validity: Duration, algorithm: String?): X509Certificate {
        val subPubKeyInfo = SubjectPublicKeyInfo.getInstance(keyPair.public.encoded)
        val now = Instant.now()
        val validFrom = Date.from(now)
        val validTo = Date.from(now.plusSeconds(validity.toSeconds()))
        val certBuilder = X509v3CertificateBuilder(
            X500Name(dn),
            BigInteger(128, SecureRandom()),
            validFrom,
            validTo,
            X500Name(dn),
            subPubKeyInfo
        )
        val signer = JcaContentSignerBuilder(algorithm)
            .setProvider(BouncyCastleProvider())
            .build(keyPair.private)
        val certificate = certBuilder.build(signer)
        return JcaX509CertificateConverter()
            .setProvider(BouncyCastleProvider())
            .getCertificate(certificate)
    }

}

data class KeyStoreOptions(
    val keyStore: KeyStore = KeyStore.getInstance(KeyStore.getDefaultType()),
    val keyStoreFilePath: Path = Paths.get("keystore.jks"),
    val keyPairAlias: String = "alias",
    val keyStorePassword: String = "password",
    val createIfNotExists: Boolean = true
)

data class KeyOptions(
    val keyAlgorithm: String = "RSA",
    val keySize: Int = 4096
)

data class CertificateOptions(
    val dn: String? = "CN=localhost, OU=Test, O=Test, L=Test, ST=Test, C=Test",
    val certValidity: Duration = Duration.ofDays(365),
    val certSignatureAlgorithm: String = "SHA256withRSA",
)
