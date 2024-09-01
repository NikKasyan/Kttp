package kttp.io

import java.io.InputStream

abstract class TransformingInputStream(private val inputStream: InputStream) : DefaultInputStream() {

    val transformedData = ByteBuffer(4096)


    protected val buffer = ByteBuffer(4096)

    val isStreamFinished
        get() = buffer.length == -1

    override fun close() {
        inputStream.close()
    }

    abstract fun transform()
    abstract fun canTransform(): Boolean
    override fun read(bytes: ByteArray, offset: Int, length: Int): Int {
        val readBytesFromBuffer = readFromInternalBuffer(bytes, offset, length)
        if (readBytesFromBuffer == length) {
            return readBytesFromBuffer
        }
        val newReadBytes = readIntoInternalBuffer(length - readBytesFromBuffer)
        if (newReadBytes == -1) {
            return if (readBytesFromBuffer == 0) -1 else readBytesFromBuffer
        } else if(newReadBytes == 0) {
            return readBytesFromBuffer
        }
        return readBytesFromBuffer + read(bytes, offset + readBytesFromBuffer, length - readBytesFromBuffer)
    }

    private fun readFromInternalBuffer(b: ByteArray, off: Int, len: Int): Int {
        if (transformedData.isEmpty()) {
            return 0
        }
        return transformedData.moveInto(b, off, len)
    }

    private fun readIntoInternalBuffer(neededBytes: Int): Int {
        if (transformedData.isFullyRead()) {
            transformedData.clear()
        }
        var totalReadBytes = 0
        while (
            neededBytes > transformedData.length &&
            transformedData.hasCapacity() &&
            canTransform()
        ) {
            if (buffer.isFullyRead() && !isStreamFinished) {
                buffer.clear()
                buffer.fillWith(inputStream)
                if (buffer.length == -1) {
                    if (!canTransform())
                        break
                } else
                    totalReadBytes += buffer.length


            }
            transform()
        }
        if (totalReadBytes == 0 && transformedData.isEmpty()) {
            return -1
        }
        return transformedData.length

    }

    override fun available(): Int {
        return transformedData.length
    }

}
