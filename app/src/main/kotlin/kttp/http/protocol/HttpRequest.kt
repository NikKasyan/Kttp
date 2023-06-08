package kttp.http.protocol

class HttpRequest(httpInitialRequest: HttpInitialRequest, val httpHeaders: HttpHeaders, val body: String) {

    val httpVersion = httpInitialRequest.httpVersion
    val requestUri = httpInitialRequest.uri
    val method = httpInitialRequest.method



}