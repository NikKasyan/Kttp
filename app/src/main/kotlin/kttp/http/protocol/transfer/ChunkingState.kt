package kttp.http.protocol.transfer

enum class ChunkingState {
    CHUNK_SIZE,
    CHUNK_SIZE_EXT,
    CHUNK_SIZE_EOL,
    CHUNK_DATA,
    CHUNK_DATA_EOL,
    TRAILERS,
    TRAILERS_EOL,
    DONE
}