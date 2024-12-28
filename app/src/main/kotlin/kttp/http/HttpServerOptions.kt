package kttp.http

import kttp.http.protocol.HttpVersion
import kttp.http.security.SSL
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo
import org.bouncycastle.cert.X509v3CertificateBuilder
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import sun.security.x509.*
import java.io.File
import java.io.IOException
import java.math.BigInteger
import java.security.*
import java.security.cert.X509Certificate
import java.time.Duration
import java.time.Instant
import java.util.*
import javax.net.ServerSocketFactory
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory


data class HttpServerOptions(
    val secure: Boolean = false,
    val port: Int = if(secure) 443 else 80,
    val socketTimeout: Duration = Duration.ofSeconds(30),
    val hostName: String = "127.0.0.1",
    val httpVersion: HttpVersion = HttpVersion.DEFAULT_VERSION,
    val tlsOptions: TLSOptions = TLSOptions.DEFAULT,
    val maxConcurrentConnections: Int = 20, // -1 for unlimited
    val socketFactory: ServerSocketFactory = if(secure) tlsOptions.createSocketFactory() else ServerSocketFactory.getDefault(),
) {
    companion object {
        val DEFAULT = HttpServerOptions()
    }

    init {
        require(port in 1..0xFFFF) { "Port must be in range 1..65535" }
        require(httpVersion == HttpVersion.DEFAULT_VERSION) { "Only HTTP/1.1 is supported" }
    }
}

data class TLSOptions(

    private val sslContext: SSLContext = SSL.createDefaultSSLContext(),

) {
    companion object {
        val DEFAULT = TLSOptions()

       }


    fun createSocketFactory(): ServerSocketFactory {
        return sslContext.serverSocketFactory
    }
}
