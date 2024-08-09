package kttp.http.protocol.transfer

import kttp.http.protocol.HttpHeaders

typealias Chunking = Pair<Int, String>

fun chunkString(payload: String, chunkSize: Int = 100, headers: HttpHeaders = HttpHeaders()): String {
    val chunks = mutableListOf<String>()
    var index = 0
    while (index < payload.length) {
        val chunk = payload.substring(index, minOf(index + chunkSize, payload.length))
        chunks.add("${chunk.length.toString(16)}\r\n$chunk\r\n")
        index += chunkSize
    }
    chunks.add("0\r\n")
    chunks.add(headersToString(headers) + "\r\n")
    return chunks.joinToString("")
}

fun chunkStringWithChunkSize(payload: String, chunkSizes: List<Int>, headers: HttpHeaders = HttpHeaders()): String {
    return chunkString(payload, chunkSizes.map { it to "" }, headers)
}

fun chunkString(payload: String, chunkings: List<Chunking>, headers: HttpHeaders = HttpHeaders()): String {
    val chunks = mutableListOf<String>()
    var index = 0
    var chunkIndex = 0
    while (index < payload.length) {
        val chunking = chunkings[chunkIndex]
        val endIndex = minOf(index + chunking.first, payload.length)
        val chunk = payload.substring(index, endIndex)
        chunks.add("${chunking.first.toString(16)}${chunkExt(chunking)}\r\n$chunk\r\n")
        index += chunking.first
        chunkIndex++
        if (chunkIndex == chunkings.size)
            chunkIndex = 0

    }
    chunks.add("0\r\n")
    chunks.add(headersToString(headers) + "\r\n")
    return chunks.joinToString("")
}

private fun headersToString(headers: HttpHeaders): String {
    var headersString = ""
    for (header in headers) {
        headersString += "${header.key}: ${header.value}\r\n"
    }
    return headersString
}

private fun chunkExt(chunking: Chunking): String {
    return if (chunking.second.isEmpty()) "" else ";${chunking.second}"
}