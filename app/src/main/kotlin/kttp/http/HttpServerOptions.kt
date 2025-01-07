package kttp.http

import kttp.http.protocol.HttpVersion
import kttp.security.SSL
import java.time.Duration
import javax.net.ServerSocketFactory
import javax.net.ssl.SSLContext


data class HttpServerOptions(
    val secure: Boolean = false,
    val port: Int = if(secure) 443 else 80,
    val socketTimeout: Duration = Duration.ofSeconds(30),
    val hostName: String = "127.0.0.1",
    val httpVersion: HttpVersion = HttpVersion.DEFAULT_VERSION,
    val tlsOptions: TLSOptions = TLSOptions.DEFAULT,
    val maxConcurrentConnections: Int = 20, // -1 for unlimited
    val socketFactory: ServerSocketFactory = if(secure) tlsOptions.createSocketFactory() else ServerSocketFactory.getDefault(),
    val transferOptions: TransferOptions = TransferOptions.DEFAULT,

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

    private val sslContext: SSLContext = SSL.createDefaultSSLContextFromKeyStore(),

    ) {
    companion object {
        val DEFAULT = TLSOptions()

       }


    fun createSocketFactory(): ServerSocketFactory {
        return sslContext.serverSocketFactory
    }
}

data class TransferOptions(val shouldAlwaysCompress: Boolean = false) {
    companion object {
        val DEFAULT = TransferOptions()
    }
}