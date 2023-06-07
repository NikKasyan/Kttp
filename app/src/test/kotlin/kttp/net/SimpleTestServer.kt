package kttp.net

import java.net.ServerSocket

class SimpleTestServer(port: Int) {

    private val serverSocket = ServerSocket(port)
    private lateinit var  ioStream: IOStream


    fun start(){
        val socket = serverSocket.accept()

        ioStream = IOStream(socket)


    }

    fun stop(){
        serverSocket.close()
        if(::ioStream.isInitialized)
            ioStream.close()
    }

    fun readLine(): String{
        return ioStream.readLine()
    }

    fun write(msg: String){
        ioStream.writeln(msg)
    }



}