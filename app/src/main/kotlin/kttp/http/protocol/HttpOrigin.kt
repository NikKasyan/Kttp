package kttp.http.protocol

import java.net.URI

class HttpOrigin(uri: URI) {

    val scheme: String
    val host: String
    val port: Int

    init {
        this.scheme = uri.scheme
        this.host = uri.host
        this.port = URIUtil.getPortByScheme(scheme, uri.port)
    }
}