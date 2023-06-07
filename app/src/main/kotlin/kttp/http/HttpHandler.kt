package kttp.http

import kttp.net.IOStream
import java.net.Socket

class HttpHandler {


    fun handle(socket: Socket) {
        IOStream(socket).readLine()
    }
}