package kttp.http.security

import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo
import org.bouncycastle.cert.X509v3CertificateBuilder
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import java.io.IOException
import java.math.BigInteger
import java.nio.file.Path
import java.nio.file.Paths
import java.security.*
import java.security.cert.X509Certificate
import java.time.Duration
import java.time.Instant
import java.util.*
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory

object SSL {
    fun createDefaultSSLContext(
        sslAlgorithm: String = "TLS",
        keyStoreOptions: KeyStoreOptions = KeyStoreOptions()
    ): SSLContext {
        val sslContext = SSLContext.getInstance(sslAlgorithm)
        val keyStoreFile = keyStoreOptions.keyStoreFilePath.toFile()
        val keyStore = keyStoreOptions.keyStore
        val keyStorePassword = keyStoreOptions.keyStorePassword.toCharArray()
        if (!keyStoreFile.exists()) {
            // generate keypair
            val keyPairGenerator = KeyPairGenerator.getInstance(keyStoreOptions.keyAlgorithm)
            keyPairGenerator.initialize(keyStoreOptions.keySize)
            val keyPair = keyPairGenerator.genKeyPair()

            keyStore.load(null, null)
            val cert = generateCertificate(keyStoreOptions.dn, keyPair, keyStoreOptions.certValidity, keyStoreOptions.certSignatureAlgorithm)
            keyStore.setKeyEntry(keyStoreOptions.keyPairAlias, keyPair.private, keyStorePassword, arrayOf(cert))
            if(keyStoreOptions.createIfNotExists)
                keyStore.store(keyStoreFile.outputStream(), keyStorePassword)
        }
        keyStore.load(keyStoreFile.inputStream(), keyStorePassword)
        val keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
        val trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
        keyManagerFactory.init(keyStore, keyStorePassword)
        trustManagerFactory.init(keyStore)
        sslContext.init(keyManagerFactory.keyManagers, trustManagerFactory.trustManagers, null)
        return sslContext
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
    val keyAlgorithm: String = "RSA",
    val keySize: Int = 4096,
    val keyPairAlias: String = "alias",
    val keyStorePassword: String = "password",
    val dn: String? = "CN=localhost, OU=Test, O=Test, L=Test, ST=Test, C=Test",
    val certAlgorithm: String = "SHA256withRSA",
    val certValidity: Duration = Duration.ofDays(365),
    val certSignatureAlgorithm: String = "SHA256withRSA",
    val createIfNotExists: Boolean = true
    )

