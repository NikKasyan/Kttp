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
https://www.rfc-editor.org/rfc/rfc9112#section-9.3.1
Add testing for persistent connections

# Todo
- [X] Add SSL support
- [ ] Add support for kotlin coroutines
- [ ] Add Websocket support
- [ ] Add support for HTTP/2?
- [ ] Add basic X.509 certificate to remove bouncy castle dependency

# Not planned to be implemented soon

compress or x-compress (Currently not feeling like implementing LZW with correct encoding) 
