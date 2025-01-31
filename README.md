# Kttp
Simple RFC conform Kotlin Http Library

# Disclaimer
This is a learning project. I am trying to implement the HTTP/1.1 RFC standards in Kotlin. 
This is not intended as a production ready library.

# Road Map
Trying to implement all RFC standards for HTTP/1.1


1. "Semantics" https://www.rfc-editor.org/rfc/rfc9110
2. "Caching" https://www.rfc-editor.org/rfc/rfc9111
3. "HTTP/1.1" https://www.rfc-editor.org/rfc/rfc9112

# Current Progress
Websocket https://www.rfc-editor.org/rfc/rfc6455.html#page-20

Http https://www.rfc-editor.org/rfc/rfc9112#section-9.3.1
Add testing for persistent connections

# Todo
- [X] Add SSL support
- [ ] Add client certificate (verification) support
- [X] Add support for kotlin coroutines
- [ ] Add Websocket support (https://www.rfc-editor.org/rfc/rfc6455)
- [ ] Add support for HTTP/2? (Websocket https://www.rfc-editor.org/rfc/rfc8441)
- [ ] Add basic X.509 certificate to remove bouncy castle dependency
- [ ] Add support for HTTP/3 (https://www.rfc-editor.org/rfc/rfc9000)
- [ ] Add Missing content-endcodings (br, bzip2, zstd)
- [ ] Add ByteString support to not allocate additional memory for every string
# Not planned to be implemented soon

compress or x-compress (Currently not feeling like implementing LZW with correct encoding) 
