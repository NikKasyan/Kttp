# Improvements

A review of Kttp as of commit `0448efd`. Each item names the file and line, and links the RFC section it breaks where there is one.

Items marked **(reproduced)** were confirmed by sending raw requests to a running `HttpServer` or by calling the class directly from JShell. The rest come from reading the code.

## Where to start

1. Section 1 first. These bugs break ordinary requests: `curl http://127.0.0.1:8080/test1` gets a 500, and every error response is padded with NUL bytes.
2. Then section 2 (message framing). This is the heart of RFC 9112, and several bugs there let a request body be parsed as a second request.
3. Then the path traversal in section 4, if you ever serve files with `hostFiles`.
4. Then fix the test hang in section 10, so that `gradlew test` finishes reliably.

**Test baseline.** The full `gradlew test` hung twice in `IOStreamTest` teardown (section 10), and I killed it after 10 minutes. Without `IOStreamTest`, 126 tests ran with 1 failure: `HttpServerConnectionTest.connectWith21Client_LastClientShouldNotConnect` (expected 20 active connections, got 18). `IOStreamTest` passes when run on its own.

---

## 1. Bugs that break ordinary requests

- [x] **Requests without a `Connection` header get a 500.** (reproduced) [HttpServer.kt:160](app/src/main/kotlin/kttp/http/server/HttpServer.kt#L160) calls `hasConnection(Connection.CLOSE)`, which goes through `connection()` to `headers[CONNECTION]!!` ([HttpHeaders.kt:599-618](app/src/main/kotlin/kttp/http/protocol/HttpHeaders.kt#L599-L618)). That throws a `NullPointerException` when the header is missing. curl doesn't send `Connection`, and the tests don't catch this because `HttpClient` always sends `Connection: keep-alive`. A related bug: unknown tokens such as `Connection: keep-alive, TE` make `Connection.byValue` throw ([HttpHeaders.kt:143-145](app/src/main/kotlin/kttp/http/protocol/HttpHeaders.kt#L143-L145)), although any field name is a valid connection option ([RFC 9110 §7.6.1](https://www.rfc-editor.org/rfc/rfc9110#section-7.6.1)).
  Fix: return an empty list when the header is missing, and keep unknown tokens as strings.

- [x] **Error responses are padded to 2048 bytes with NUL bytes.** (reproduced) `writeTo` writes the whole buffer, not just the bytes it read: `ioStream.writeBytes(buffer)` ([HttpResponse.kt:91](app/src/main/kotlin/kttp/http/protocol/HttpResponse.kt#L91)). Every response from `respondWithError` ends in NUL bytes past its `Content-Length`.
  Fix: write only `read` bytes, or use `io.writeFromStream(asStream())`.

- [x] **Unknown paths return `200 OK`.** (reproduced) `NOT_FOUND_HANDLER` calls `respond(String)`, which always builds `HttpResponse.ok` ([CommonHandlers.kt:12-17](app/src/main/kotlin/kttp/http/server/CommonHandlers.kt#L12-L17)).
  Fix: `respond(HttpResponse.notFound(body = ...))`.

- [x] **`Date` is sent in local time.** (reproduced: `Date: Wed, 30 Sep 2026 22:25:14 CEST`) `withDate` and `DateFormats.createImfFixDateFormat` default to `TimeZone.getDefault()` ([HttpHeaders.kt:204-214](app/src/main/kotlin/kttp/http/protocol/HttpHeaders.kt#L204-L214), [HttpHeaders.kt:509](app/src/main/kotlin/kttp/http/protocol/HttpHeaders.kt#L509)). IMF-fixdate is always in GMT ([RFC 9110 §5.6.7](https://www.rfc-editor.org/rfc/rfc9110#section-5.6.7)). The same section says a recipient MUST accept the two obsolete date formats as well, but `date()` only parses IMF-fixdate.
  Fix: always format with `TimeZone.getTimeZone("GMT")`.

- [x] **A CRLF split across two reads breaks the line.** (reproduced) In `LineReader.readLine`, `readCr` is reset on every buffer refill ([LineReader.kt:31-49](app/src/main/kotlin/kttp/io/LineReader.kt#L31-L49)). If one read ends with `\r` and the next starts with `\n`, the `\r` stays in the line. For the request line this gives `HTTP/1.1\r`, which is rejected as "HTTP-Version must not contain bare CR" (400). Whether it happens depends on how TCP splits the data.
  Fix: remember a pending CR across refills.

- [x] **End of stream looks like an empty line.** At EOF, `LineReader.readLine` and `IOStream.readLine` return `""` ([LineReader.kt:24-26](app/src/main/kotlin/kttp/io/LineReader.kt#L24-L26), [IOStream.kt:79](app/src/main/kotlin/kttp/io/IOStream.kt#L79)). This has two effects:
  - When a keep-alive client closes the connection, the server parses `""` as a request line and logs a 400 "Invalid: " (visible in the test logs).
  - A request cut off in the middle of its headers is handled as if it were complete ([RFC 9112 §8](https://www.rfc-editor.org/rfc/rfc9112#section-8)).

  Fix: throw `EndOfStream` (or return `null`) at EOF.

- [x] **The client sends `Host: null`.** (reproduced) For a relative URI like `/test`, `HttpRequest.from` calls `withHost(uri)`, which writes the literal string `null` ([HttpRequest.kt:37-38](app/src/main/kotlin/kttp/http/protocol/HttpRequest.kt#L37-L38)). `HttpClient.request` then sees a Host header and doesn't replace it ([HttpClient.kt:63-64](app/src/main/kotlin/kttp/http/HttpClient.kt#L63-L64)). As a result, every `client.get("/path")` sends `Host: null`.
  Fix: only derive Host from absolute URIs.

## 2. Message framing ([RFC 9112 §6](https://www.rfc-editor.org/rfc/rfc9112#section-6))

Framing decides where a message ends. When the server gets it wrong, leftover body bytes are read as the next request on the same connection. RFC 9112 calls this request smuggling ([§11.2](https://www.rfc-editor.org/rfc/rfc9112#section-11.2)).

- [x] **The request body is not limited to its length.** `HttpBody.read` passes straight through to the socket stream ([HttpBody.kt:100-102](app/src/main/kotlin/kttp/http/protocol/HttpBody.kt#L100-L102)). Only `readAllBytes` stops at `contentLength`. A handler that reads with a buffer can read into the next request, and a handler that ignores the body leaves it on the connection. (reproduced: a POST whose unread body is `GET /nope ...` gets two responses.)
  Fix: wrap the socket stream in a small bounded stream that returns -1 after `Content-Length` bytes. After each handler, discard whatever the handler left unread before parsing the next request.

- [x] **The method decides whether a request has a body.** `readBody` only reads a body for POST, PUT and PATCH ([HttpRequestHandler.kt:52-54](app/src/main/kotlin/kttp/http/server/HttpRequestHandler.kt#L52-L54), [Method.kt:25-30](app/src/main/kotlin/kttp/http/protocol/Method.kt#L25-L30)). RFC 9112 §6 says: "Request message framing is independent of method semantics." (reproduced: a GET with `Content-Length` gets two responses, the second for the request hidden in its body.)
  Fix: decide from `Transfer-Encoding` and `Content-Length` only, in the order given in [§6.3](https://www.rfc-editor.org/rfc/rfc9112#section-6.3).

- [x] **A request without `Content-Length` or `Transfer-Encoding` waits for the socket to close.** `HttpBody(stream, null).readAllBytes()` reads until EOF ([HttpBody.kt:108-112](app/src/main/kotlin/kttp/http/protocol/HttpBody.kt#L108-L112)). For such a request the body length is zero ([§6.3](https://www.rfc-editor.org/rfc/rfc9112#section-6.3), rule 7). (reproduced: `POST /echo` without `Content-Length` hangs until the socket timeout.)

- [x] **Header names are case-sensitive.** (reproduced) `HttpHeaders` is a `HashMap` keyed by the name as received ([HttpHeaders.kt:227](app/src/main/kotlin/kttp/http/protocol/HttpHeaders.kt#L227)), but field names are case-insensitive ([RFC 9110 §5.1](https://www.rfc-editor.org/rfc/rfc9110#section-5.1)). This causes three bugs:
  - `content-length: 5` and `transfer-encoding: chunked` are invisible to `hasContentLength()` and `hasTransferEncoding()`, so the body is left on the connection.
  - `host: x` counts as a missing Host header.
  - `host: a` followed by `Host: b` passes the duplicate check.

  Fix: key the map by `name.lowercase()` and keep the original spelling only for output.

- [x] **Repeated header lines overwrite each other.** `add` does `headers[key] = value` ([HttpHeaders.kt:266](app/src/main/kotlin/kttp/http/protocol/HttpHeaders.kt#L266)), so `Content-Length: 5` followed by `Content-Length: 10` gives 10 (reproduced). Repeated lines must be combined with commas ([RFC 9110 §5.3](https://www.rfc-editor.org/rfc/rfc9110#section-5.3)), and differing Content-Length values must be rejected ([RFC 9112 §6.3](https://www.rfc-editor.org/rfc/rfc9112#section-6.3), rule 5). This design also means the client can't receive more than one `Set-Cookie`.
  Fix: store a list of values per name.

- [x] **`Content-Length: +5` is accepted.** (reproduced) Kotlin's `toLongOrNull` accepts a leading `+` ([HttpHeaders.kt:317](app/src/main/kotlin/kttp/http/protocol/HttpHeaders.kt#L317)), but the grammar is `1*DIGIT` ([RFC 9110 §8.6](https://www.rfc-editor.org/rfc/rfc9110#section-8.6)).
  Fix: check that every character is a digit before converting.

- [x] **Chunk sizes can overflow silently.** (reproduced: `111111120` is read as `0x11111120`) The check `prevChunkSize > chunkSize` misses some overflows ([ChunkedInputStream.kt:73-78](app/src/main/kotlin/kttp/http/protocol/transfer/ChunkedInputStream.kt#L73-L78)). RFC 9112 says recipients MUST prevent integer overflow here ([§7.1](https://www.rfc-editor.org/rfc/rfc9112#section-7.1)). A related bug: an empty chunk-size line is read as the last chunk, but a chunk size needs at least one hex digit.
  Fix: use `Long` and check before multiplying: `if (chunkSize > (Long.MAX_VALUE - digit) / 16) throw ...`.

- [x] **Trailer fields are merged into the request headers** ([ChunkedInputStream.kt:174](app/src/main/kotlin/kttp/http/protocol/transfer/ChunkedInputStream.kt#L174)). A recipient MUST NOT do that unless the field's definition allows it ([RFC 9110 §6.5.1](https://www.rfc-editor.org/rfc/rfc9110#section-6.5.1)).
  Fix: collect trailers in a separate `HttpHeaders` that belongs to the body.

- [x] **List headers are split on `", "`** ([HttpHeaders.kt:451-453](app/src/main/kotlin/kttp/http/protocol/HttpHeaders.kt#L451-L453); `teAsStrings` and `acceptLanguageAsList` do the same). As a result, `gzip,chunked` becomes one unknown coding. Lists allow optional whitespace around the comma ([RFC 9110 §5.6.1](https://www.rfc-editor.org/rfc/rfc9110#section-5.6.1)).
  Fix: split on `,` and trim.

- [x] **Responses are not always framed.** (reproduced) `HttpExchange.write(...)` sends a 200 header block without `Content-Length` or `Transfer-Encoding` and then keeps the connection open, so the client can't tell where the body ends. The same happens for `HttpResponse.fromStatus(status, headers, body: HttpBody)` when the body has no length.
  Fix: when the length is unknown, use chunked encoding or close the connection after the response.

- [x] **`shouldAlwaysCompress` sends both `Content-Length` and `Transfer-Encoding`.** (reproduced: `Content-Length: 11` plus `Transfer-Encoding: chunked` in front of a 166-byte body) The cause is a chain of shared, mutated headers:
  1. The property initializer `HttpExchange.response` calls `HttpResponse.ok(defaultHeaders)` ([HttpExchange.kt:17](app/src/main/kotlin/kttp/http/server/HttpExchange.kt#L17)).
  2. That call adds `Transfer-Encoding: chunked` to the shared default headers ([HttpResponse.kt:37-41](app/src/main/kotlin/kttp/http/protocol/HttpResponse.kt#L37-L41)).
  3. `addMissingHeaders` then copies it into a response that already has a `Content-Length`.

  A sender MUST NOT send both headers ([RFC 9112 §6.2](https://www.rfc-editor.org/rfc/rfc9112#section-6.2)). See also "Headers are changed from far away" in section 9.

- [x] **Non-ASCII bodies get the wrong `Content-Length`.** (reproduced: `"äöü"` gets 3, but 6 bytes are sent) `HttpBody.fromString` and `HttpResponse.fromStatus(..., body: String)` use `body.length`, which counts characters ([HttpBody.kt:23-26](app/src/main/kotlin/kttp/http/protocol/HttpBody.kt#L23-L26), [HttpResponse.kt:46-47](app/src/main/kotlin/kttp/http/protocol/HttpResponse.kt#L46-L47)). Content-Length counts octets ([RFC 9110 §8.6](https://www.rfc-editor.org/rfc/rfc9110#section-8.6)).
  Fix: convert the body to bytes first.

- [x] **HEAD responses include the body.** (reproduced) HEAD falls back to the GET handler (see section 7), and the body is written. The server MUST NOT send content in a HEAD response ([RFC 9110 §9.3.2](https://www.rfc-editor.org/rfc/rfc9110#section-9.3.2)).
  Fix: for HEAD, write only the status line and headers.

## 3. Server behavior

- [x] **A handler that never calls `respond` leaves the client without a response.** (reproduced) `HttpServer.respond` runs the handler but never calls `httpExchange.close()`, although `HttpExchange` is `AutoCloseable` ([HttpServer.kt:230-236](app/src/main/kotlin/kttp/http/server/HttpServer.kt#L230-L236)). In addition, neither `respond()` nor `close()` sets `closed = true` ([HttpExchange.kt:59-75](app/src/main/kotlin/kttp/http/server/HttpExchange.kt#L59-L75)), so calling `respond` twice writes two bodies.
  Fix: `httpExchange.use { handler.handle(it) }`, and set `closed` in both methods.

- [x] **An exception after the handler started writing appends a second response.** (reproduced: `partialHTTP/1.1 500 ...`) Once the headers are sent, the only correct option is to close the connection. Log the error, then close.

- [x] **Many client errors become 500.** `httpResponseFromException` lists a few `InvalidHttpRequest` subclasses, and everything else is a 500 ([HttpServer.kt:193-220](app/src/main/kotlin/kttp/http/server/HttpServer.kt#L193-L220)). Examples:
  - obs-fold throws `LineFoldingNotAllowed` and gets a 500, where the RFC requires a 400 ([RFC 9112 §5.2](https://www.rfc-editor.org/rfc/rfc9112#section-5.2)). (reproduced)
  - `InvalidHttpVersion`, `InvalidContentLength`, `InvalidTransferEncoding` and `UnknownContentEncoding` all end up as 500.
  - The chunked-stream errors (`InvalidChunkedStream`) don't extend `InvalidHttpRequest` at all.
  - An invalid port or an IPv6 host in `Host` throws a `NumberFormatException` in `combineToRequestUri` ([HttpRequest.kt:69-85](app/src/main/kotlin/kttp/http/protocol/HttpRequest.kt#L69-L85)). (reproduced for `[::1]:8080` and `example.com:abc`) An invalid Host MUST get a 400 ([RFC 9112 §3.2](https://www.rfc-editor.org/rfc/rfc9112#section-3.2)).
  - A query parameter without `=` (`/?flag`) throws `IndexOutOfBoundsException` in `Parameters.addFromQuery` ([Parameters.kt:24-27](app/src/main/kotlin/kttp/http/protocol/Parameters.kt#L24-L27)). (reproduced)

  Fix: end the `when` with `is InvalidHttpRequest -> 400`, make the chunked-stream exceptions extend it, and have the parsers throw specific subclasses.

- [x] **Unknown methods get 405 instead of 501** ([HttpServer.kt:199-202](app/src/main/kotlin/kttp/http/server/HttpServer.kt#L199-L202)). A 405 means the method is known but not allowed for this resource, and it requires an `Allow` header ([RFC 9110 §15.5.6](https://www.rfc-editor.org/rfc/rfc9110#section-15.5.6)). For an unrecognized method the server SHOULD send 501 ([RFC 9110 §9.1](https://www.rfc-editor.org/rfc/rfc9110#section-9.1)). The test `testServerRespondsWith405MethodNotAllowedOnInvalidMethod` asserts the current behavior.

- [x] **Unsupported codings throw `NotImplementedError`** ([HttpBody.kt:161](app/src/main/kotlin/kttp/http/protocol/HttpBody.kt#L161), [176](app/src/main/kotlin/kttp/http/protocol/HttpBody.kt#L176), [190](app/src/main/kotlin/kttp/http/protocol/HttpBody.kt#L190), [204](app/src/main/kotlin/kttp/http/protocol/HttpBody.kt#L204)). `NotImplementedError` is an `Error`, which `catch (exception: Exception)` in `handleSocket` doesn't catch, so the client gets a closed connection and no response. Separately, the `compress` and `bzip2` content codings fall into `else -> currentBody` and are passed on still compressed.
  Fix: throw an `InvalidHttpRequest` subclass and answer 415 ([RFC 9110 §15.5.16](https://www.rfc-editor.org/rfc/rfc9110#section-15.5.16)).

- [x] **`HTTP/2.0` in the request line is served as HTTP/1.1.** (reproduced) See the TODO at [HttpServer.kt:225-229](app/src/main/kotlin/kttp/http/server/HttpServer.kt#L225-L229).
  Fix: answer 505 when the major version isn't 1 ([RFC 9110 §15.6.6](https://www.rfc-editor.org/rfc/rfc9110#section-15.6.6)).

- [x] **Connection persistence ignores the HTTP version.** Only a request with `Connection: close` ends the connection ([HttpServer.kt:160](app/src/main/kotlin/kttp/http/server/HttpServer.kt#L160)). An HTTP/1.0 connection only persists with `keep-alive` ([RFC 9112 §9.3](https://www.rfc-editor.org/rfc/rfc9112#section-9.3)). The server also keeps the connection open after it sends `Connection: close` itself.

- [x] **After a WebSocket upgrade the server keeps reading HTTP.** When the handler returns, `handleSocket` loops back to `handleRequest` on the closed stream ([HttpServer.kt:155-164](app/src/main/kotlin/kttp/http/server/HttpServer.kt#L155-L164)). That throws `StreamAlreadyClosed`, and `respondWithError` throws again from inside the `catch` block.
  Fix: stop the loop after a 101 response.

- [x] **`request.uri` ignores absolute-form targets and TLS.** `combineToRequestUri` always takes the host from the `Host` header and always uses the scheme `http` ([HttpRequest.kt:69-85](app/src/main/kotlin/kttp/http/protocol/HttpRequest.kt#L69-L85)). For an absolute-form target, the server MUST ignore `Host` and use the target's authority ([RFC 9112 §3.2.2](https://www.rfc-editor.org/rfc/rfc9112#section-3.2.2)). The scheme is `https` on a TLS connection ([§3.3](https://www.rfc-editor.org/rfc/rfc9112#section-3.3)).

- [x] **`OPTIONS *` and `CONNECT host:port` are rejected with 400.** (reproduced for `OPTIONS *`) `URIUtil.checkURI` only accepts origin-form and absolute-form ([URIUtil.kt:57-63](app/src/main/kotlin/kttp/http/protocol/URIUtil.kt#L57-L63)). Asterisk-form ([§3.2.4](https://www.rfc-editor.org/rfc/rfc9112#section-3.2.4)) and authority-form ([§3.2.3](https://www.rfc-editor.org/rfc/rfc9112#section-3.2.3)) are missing.

- [x] **Paths and queries are decoded wrongly.**
  - `RequestLine` parses `uri.query`, which `java.net.URI` has already decoded, and `Parameters.addFromQuery` decodes it a second time ([RequestLine.kt:26](app/src/main/kotlin/kttp/http/protocol/RequestLine.kt#L26), [Parameters.kt:26](app/src/main/kotlin/kttp/http/protocol/Parameters.kt#L26)). So `a=%26b` is split into `a=` and `b`, and `%2525` becomes `%`. Use `rawQuery`.
  - `normalizeURI` decodes the path with `URLDecoder`, which turns `+` into a space. That rule only applies to form data. It also doesn't remove dot segments ([URIUtil.kt:16-26](app/src/main/kotlin/kttp/http/protocol/URIUtil.kt#L16-L26), [RFC 3986 §5.2.4](https://www.rfc-editor.org/rfc/rfc3986#section-5.2.4)).
  - `RequestLine.toString` writes the decoded `uri.path` ([RequestLine.kt:42-45](app/src/main/kotlin/kttp/http/protocol/RequestLine.kt#L42-L45)). For `/a%20b` the client therefore sends `GET /a b HTTP/1.1`. Use `rawPath`.

- [x] **`stop()` races with the accept loop.** `stop()` shuts down the executor before it closes the server socket ([HttpServer.kt:242-253](app/src/main/kotlin/kttp/http/server/HttpServer.kt#L242-L253)). A connection accepted in between is submitted to a terminated executor, and the accept thread dies with `RejectedExecutionException` (seen in the test logs). `stop()` also shuts down an executor that the caller passed in and may still be using.
  Fix: close the server socket first.

- [x] **Shared state isn't thread-safe.** `openConnections` is an `ArrayList` that every pool thread changes ([HttpServer.kt:70](app/src/main/kotlin/kttp/http/server/HttpServer.kt#L70), [153](app/src/main/kotlin/kttp/http/server/HttpServer.kt#L153), [179](app/src/main/kotlin/kttp/http/server/HttpServer.kt#L179)). `isRunning` and `hasStarted` are plain fields that other threads read ([HttpServer.kt:64-68](app/src/main/kotlin/kttp/http/server/HttpServer.kt#L64-L68)).
  Fix: use a concurrent set and `@Volatile`.

- [x] **The documented "-1 for unlimited" options throw** ([HttpServerOptions.kt:13](app/src/main/kotlin/kttp/http/server/HttpServerOptions.kt#L13), [17](app/src/main/kotlin/kttp/http/server/HttpServerOptions.kt#L17), [ClientConnection.kt:14](app/src/main/kotlin/kttp/net/ClientConnection.kt#L14)).
  - `maxConcurrentConnections = -1` makes `Executors.newFixedThreadPool(-1)` and `ArrayList(-1)` throw.
  - A `socketTimeout` of -1 seconds sets a negative `soTimeout`, which also throws. For `soTimeout`, 0 means no timeout.

  Fix: implement these options, or correct the comments.

## 4. Security

The README says Kttp isn't meant for production. These are still worth fixing, because they are the classic HTTP server mistakes.

- [x] **Path traversal in `hostFiles`.** (reproduced) `resolvePath` resolves the request path against the root without normalizing it or checking that the result stays inside the root ([CommonHandlers.kt:61-65](app/src/main/kotlin/kttp/http/server/CommonHandlers.kt#L61-L65)). With the root `C:/srv/www`:
  - `/../../secret.txt` resolves to `C:\secret.txt`.
  - `/%2e%2e/secret.txt` does the same, because the path is decoded first.
  - `/C:/Windows/win.ini` resolves to `C:\Windows\win.ini`, because `Path.resolve` returns an absolute argument unchanged.

  Fix: compute `root.resolve(relative).normalize()` and reject the result unless it `startsWith(root)`. `hostFiles` has three smaller problems: file names go into the directory listing HTML unescaped, `file.readText()` corrupts binary files, and no `Content-Type` is set.

- [x] **The HTTPS client doesn't check the host name.** `SSL.getSecureSocketFactory(true)` returns the default `SSLSocketFactory` ([SSL.kt:157-161](app/src/main/kotlin/kttp/security/SSL.kt#L157-L161)). Its sockets verify the certificate chain, but not that the certificate belongs to the host being contacted. A client MUST verify the server identity ([RFC 9110 §4.3.4](https://www.rfc-editor.org/rfc/rfc9110#section-4.3.4)). `Websocket.connect` with `wss` has the same problem.
  Fix: set `sslParameters = sslParameters.apply { endpointIdentificationAlgorithm = "HTTPS" }` on the socket before the handshake.

- [x] **Header values can contain CR LF.** `checkHeaderNotContainsBareCR` only rejects a CR that is *not* followed by LF ([HttpHeaders.kt:217-222](app/src/main/kotlin/kttp/http/protocol/HttpHeaders.kt#L217-L222)), and `set` and the `withX` methods check nothing. A handler that puts user input into a header can inject extra headers or a whole second response ([RFC 9112 §11.1](https://www.rfc-editor.org/rfc/rfc9112#section-11.1)). CR, LF and NUL are invalid in field values ([RFC 9110 §5.5](https://www.rfc-editor.org/rfc/rfc9110#section-5.5)).
  Fix: reject them in `HttpHeader`, and send every setter through the same check.

- [x] **A plain HTTP server generates a TLS keystore.** (reproduced) The default argument `tlsOptions = TLSOptions.DEFAULT` is evaluated for every `HttpServerOptions`, even with `secure = false` ([HttpServerOptions.kt:16](app/src/main/kotlin/kttp/http/server/HttpServerOptions.kt#L16)). The first time, it creates a 4096-bit RSA key and writes a `keystore.jks` with the password `password` into the working directory; after that, every start loads the file ([SSL.kt:30-79](app/src/main/kotlin/kttp/security/SSL.kt#L30-L79)). This is why `.gitignore` needs `keystore.jks`.
  Fix: only build the TLS options when `secure` is true.

- [x] **500 responses include the exception message** ([HttpServer.kt:218-219](app/src/main/kotlin/kttp/http/server/HttpServer.kt#L218-L219)). Log the message and send a generic body instead.

- [x] **Streams in `SSL.kt` are never closed**: `keyStoreFile.outputStream()`, `keyStoreFile.inputStream()` and the PEM streams ([SSL.kt:55](app/src/main/kotlin/kttp/security/SSL.kt#L55), [61](app/src/main/kotlin/kttp/security/SSL.kt#L61), [91](app/src/main/kotlin/kttp/security/SSL.kt#L91), [99](app/src/main/kotlin/kttp/security/SSL.kt#L99)). On Windows an open stream keeps the file locked. Use `.use {}`. Separately, the PEM parser only strips `\n`, so a key file with Windows line endings fails to decode ([SSL.kt:113-115](app/src/main/kotlin/kttp/security/SSL.kt#L113-L115)).

## 5. WebSocket ([RFC 6455](https://www.rfc-editor.org/rfc/rfc6455))

- [ ] **Fragmented messages can't be sent or received.** (reproduced) The two checks in [WebsocketProtocol.kt:77-78](app/src/main/kotlin/kttp/websocket/WebsocketProtocol.kt#L77-L78) are the opposite of [§5.4](https://www.rfc-editor.org/rfc/rfc6455#section-5.4). The first fragment has FIN=0 and opcode TEXT or BINARY, and the last fragment has FIN=1 and opcode CONTINUATION; both are rejected. This causes several problems:
  - A fragmented message from a browser throws.
  - `send(InputStream)` sends only CONTINUATION frames, followed by a separate empty BINARY frame ([Websocket.kt:112-122](app/src/main/kotlin/kttp/websocket/Websocket.kt#L112-L122)). A receiver sees a protocol error and then an empty message.
  - `handleEvents` delivers each fragment as its own message instead of joining them. After the second fragment, `lastOpCode` is CONTINUATION.
  - `testFragmentedMessageSending` only checks the first frame's payload, so it passes despite this.

- [ ] **Receiving a Close frame ends in an exception.** `handleEvents` calls `close()` and keeps looping. The next `readFrame` throws `StreamAlreadyClosed`, and `onFailure` rethrows it ([Websocket.kt:147-175](app/src/main/kotlin/kttp/websocket/Websocket.kt#L147-L175)). The Close frame sent in reply has no status code; the endpoint usually echoes the code it received ([§5.5.1](https://www.rfc-editor.org/rfc/rfc6455#section-5.5.1)).
  Fix: leave the loop after a Close frame.

- [ ] **Frames are not validated.**
  - Masking isn't enforced: a server MUST fail the connection on an unmasked client frame, and a client MUST fail it on a masked server frame ([§5.1](https://www.rfc-editor.org/rfc/rfc6455#section-5.1)).
  - Non-zero RSV bits without a negotiated extension MUST fail the connection ([§5.2](https://www.rfc-editor.org/rfc/rfc6455#section-5.2)).
  - Control frames may have at most 125 bytes of payload and must not be fragmented ([§5.5](https://www.rfc-editor.org/rfc/rfc6455#section-5.5)).
  - Text frames must be valid UTF-8 ([§8.1](https://www.rfc-editor.org/rfc/rfc6455#section-8.1)), but `toString(UTF_8)` silently replaces invalid bytes.

- [ ] **The handshake checks have gaps.** All in [WebsocketConnectionUpgrade.kt](app/src/main/kotlin/kttp/websocket/WebsocketConnectionUpgrade.kt):
  - `hasValidWebSocketVersion` returns true when the version is *invalid* ([L106-109](app/src/main/kotlin/kttp/websocket/WebsocketConnectionUpgrade.kt#L106-L109)).
  - A missing `Sec-WebSocket-Version` is accepted, but the request MUST include version 13 ([§4.2.1](https://www.rfc-editor.org/rfc/rfc6455#section-4.2.1)). On a version mismatch, answer 426 with a `Sec-WebSocket-Version` header instead of 400 ([§4.4](https://www.rfc-editor.org/rfc/rfc6455#section-4.4)).
  - `hasWebsocketUpgrade` compares with `== "websocket"` before the case-insensitive check, so `WebSocket` is rejected ([L112-116](app/src/main/kotlin/kttp/websocket/WebsocketConnectionUpgrade.kt#L112-L116)).
  - The server echoes the client's first subprotocol without checking that it supports it ([L54-55](app/src/main/kotlin/kttp/websocket/WebsocketConnectionUpgrade.kt#L54-L55)).
  - The client doesn't check for status 101 or `Connection: Upgrade`, and throws a `NullPointerException` when `Sec-WebSocket-Accept` is missing ([L83-93](app/src/main/kotlin/kttp/websocket/WebsocketConnectionUpgrade.kt#L83-L93), [§4.1](https://www.rfc-editor.org/rfc/rfc6455#section-4.1)).
  - `createWebSocketAcceptFromRequest()` without arguments creates a `Sec-WebSocket-Key`, not an accept value ([L130-136](app/src/main/kotlin/kttp/websocket/WebsocketConnectionUpgrade.kt#L130-L136)).
  - `Origin` isn't checked yet (TODO at [L78](app/src/main/kotlin/kttp/websocket/WebsocketConnectionUpgrade.kt#L78)). Without that check, any website can open a WebSocket to the server using the visitor's cookies ([§10.2](https://www.rfc-editor.org/rfc/rfc6455#section-10.2)).

- [ ] **`send` isn't synchronized.** The event loop sends pongs while another thread may call `send`, as `WebsocketClient` in `Main.kt` does. `writeTo` writes one frame in several calls, so two frames can interleave ([WebsocketProtocol.kt:81-92](app/src/main/kotlin/kttp/websocket/WebsocketProtocol.kt#L81-L92)).
  Fix: guard `send(frame)` with a lock.

- [ ] **Two smaller issues.** `onLongMessage` returns from `handleEvents`, which ends the event loop ([Websocket.kt:152-155](app/src/main/kotlin/kttp/websocket/Websocket.kt#L152-L155)). The class comment says it is "only for client side", but the server uses it too ([Websocket.kt:13](app/src/main/kotlin/kttp/websocket/Websocket.kt#L13)).

## 6. Client

- [ ] **`fixMissingScheme` throws a `NullPointerException`** for URLs without a scheme (reproduced: `HttpClient("example.com")`). It calls `uri.scheme.startsWith` right after checking `uri.scheme == null` ([HttpClient.kt:40-47](app/src/main/kotlin/kttp/http/HttpClient.kt#L40-L47)).
- [ ] **The companion `HttpClient.get(url)` ignores the path** of `url` and requests `/` ([HttpClient.kt:24-26](app/src/main/kotlin/kttp/http/HttpClient.kt#L24-L26)).
- [ ] **Failed TLS handshakes are ignored.** For errors other than PKIX, the client logs, closes the socket and then carries on using it ([HttpClient.kt:85-91](app/src/main/kotlin/kttp/http/HttpClient.kt#L85-L91)). Rethrow instead.
- [ ] **A relative base URL hits `TODO()`**, which throws `NotImplementedError` ([HttpClient.kt:36-37](app/src/main/kotlin/kttp/http/HttpClient.kt#L36-L37)).
- [ ] **`readResponse` mishandles responses without a normal body.** It doesn't skip 1xx responses, which a client MUST be able to parse ([RFC 9110 §15.2](https://www.rfc-editor.org/rfc/rfc9110#section-15.2)). It also reads a body for HEAD, 204 and 304 responses, which never have one ([RFC 9112 §6.3](https://www.rfc-editor.org/rfc/rfc9112#section-6.3), rule 1).
- [ ] **Status codes:**
  - Unknown status codes throw `InvalidHttpStatus` (reproduced for 308). A client MUST treat an unknown code like the x00 code of its class ([RFC 9110 §15](https://www.rfc-editor.org/rfc/rfc9110#section-15)).
  - 304 is named `USE_PROXY` (reproduced: `byCode(304)` returns `USE_PROXY`). 304 is Not Modified; Use Proxy is 305 ([§15.4.5](https://www.rfc-editor.org/rfc/rfc9110#section-15.4.5)).
  - 308 Permanent Redirect is missing.
  - Several reason phrases are older than RFC 9110, which uses "Content Too Large" (413), "URI Too Long" (414) and "Range Not Satisfiable" (416).
- [ ] **The client has no `close()`** and doesn't reconnect after the server closes the connection. It also relies on the caller to read each response body completely before sending the next request.

## 7. Routing

- [ ] **`on(method, …)` and both `on(methods, …)` ignore their argument** and register the handler for all methods ([HttpServerRouting.kt:44-54](app/src/main/kotlin/kttp/http/server/HttpServerRouting.kt#L44-L54)).
- [ ] **GET and POST can't be registered on the same path.** (reproduced) `addHandler` rejects a second handler for a path whatever its methods are ([HttpServer.kt:288-292](app/src/main/kotlin/kttp/http/server/HttpServer.kt#L288-L292)).
- [ ] **A method mismatch falls back to matching the path only.** (reproduced: a POST to a GET-only path runs the GET handler) `getHandlerForRequest` retries with `getHandlerByPath`, which ignores the method ([HttpServer.kt:298-303](app/src/main/kotlin/kttp/http/server/HttpServer.kt#L298-L303)).
  Fix: answer 405 with an `Allow` header ([RFC 9110 §15.5.6](https://www.rfc-editor.org/rfc/rfc9110#section-15.5.6)).
- [ ] **`/x/*` matches `/y`.** (reproduced) When the matching loop breaks on a mismatch, the check `requestPath.size + 1 == handlerPath.size && handlerPath.last().contains("*")` adds the handler anyway ([HttpServer.kt:329-332](app/src/main/kotlin/kttp/http/server/HttpServer.kt#L329-L332)). Two smaller problems: a `**` in the middle of a pattern ignores everything after it, and a segment like `*.html` matches any segment.

## 8. IO helpers and concurrency

- [ ] **`IOStream.close()` throws on the second call** ([IOStream.kt:139-142](app/src/main/kotlin/kttp/io/IOStream.kt#L139-L142)). Calling `Closeable.close` on a closed stream should have no effect.
- [ ] **`CombinedInputStream.read` returns 0 at the end** before it returns -1 ([CombinedInputStream.kt:26](app/src/main/kotlin/kttp/io/CombinedInputStream.kt#L26)). `read` may only return 0 when asked for 0 bytes.
- [ ] **`ChunkingInputStream` with fixed chunk sizes crashes when the data runs out** in the middle of a chunk (reproduced: `"abc"` with chunk size 5 throws `ArrayIndexOutOfBoundsException`). The last chunk must announce its real size. The test helper `chunkString` has the same bug: it announces `5` and then sends `abc` ([Chunking.kt:32](app/src/main/kotlin/kttp/http/protocol/transfer/Chunking.kt#L32)). Tests that compare against the helper therefore can't catch the problem. `Chunking.kt` is only used by tests, so it belongs in `src/test`.
- [ ] **`ByteBuffer` marks end of stream by letting `length` become -1** ([ByteBuffer.kt:65-69](app/src/main/kotlin/kttp/io/ByteBuffer.kt#L65-L69), [TransformingInputStream.kt:12-13](app/src/main/kotlin/kttp/io/TransformingInputStream.kt#L12-L13)). `capacity` is then `size + 1`. A separate `isStreamFinished` flag would be easier to follow.
- [ ] **The format overloads of `Logger` pass the vararg array as a single argument** ([Logger.kt:10-13](app/src/main/kotlin/kttp/log/Logger.kt#L10-L13), [22](app/src/main/kotlin/kttp/log/Logger.kt#L22)). SLF4J then prints `[a, b]` for the first `{}`. Use `*arguments`. Nothing calls these overloads yet.
- [ ] **`CoroutineExecutorService` breaks the `ExecutorService` contract** ([CoroutineExecutorService.kt](app/src/main/kotlin/kttp/concurrent/CoroutineExecutorService.kt)):
  - `shutdown()` cancels running tasks, which is what `shutdownNow()` should do, while `shutdownNow()` cancels nothing.
  - `invokeAll` returns before the tasks finish.
  - `Future.get` throws the raw exception instead of wrapping it in `ExecutionException`.
  - `awaitAny` checks `task.isCancelled` (the task that just finished) instead of `it.isCancelled` ([L109](app/src/main/kotlin/kttp/concurrent/CoroutineExecutorService.kt#L109)). Because a failed coroutine also counts as cancelled, the first failing task makes `invokeAny` fail even if another task would succeed.

## 9. Readability and structure

- [ ] **[HttpHeaders.kt](app/src/main/kotlin/kttp/http/protocol/HttpHeaders.kt) is 1,036 lines long.** Most of it is the same five members repeated for each header (`var x`, `withX`, `hasX`, `x()`, `xAsString()`), and some pairs are identical (`server()` and `serverAsString()`, `contentType()` and `contentTypeAsString()`). There are four `withConnection` overloads: two validate their input and two don't. A case-insensitive store with several values per name (see section 2) would be shorter and easier to check against the RFC. Typed accessors would then only be needed where parsing is involved (Content-Length, Transfer-Encoding, Connection, Date). `Encoding`, `MimeTypes`, `DateFormats` and `Connection` could move to their own files.
- [ ] **Headers are changed from far away.** `HttpResponse.fromStatus` changes the caller's `HttpHeaders`. The `HttpExchange.response` property initializer changes `defaultHeaders`. `ChunkedInputStream` writes into the request headers. The `Content-Length` plus `Transfer-Encoding` bug in section 2 comes from this. Copying headers instead would avoid this kind of bug.
- [ ] **Some package dependencies point the wrong way.** `HttpRequest`, `HttpHeaders` and `HttpClient` import `kttp.http.server.MissingHostHeader`, and the server's WebSocket code uses `HttpClientConnection`. Move `MissingHostHeader` to `protocol`, and rename `HttpClientConnection` to something like `HttpConnection`.
- [ ] **The Host check runs twice**: in `HttpRequestHandler.handleRequest` and in the `HttpRequest` constructor ([HttpRequestHandler.kt:19-20](app/src/main/kotlin/kttp/http/server/HttpRequestHandler.kt#L19-L20), [HttpRequest.kt:23-24](app/src/main/kotlin/kttp/http/protocol/HttpRequest.kt#L23-L24)).
- [ ] **`Parameters.to(...)` is a reflection-based object mapper** of about 240 lines, already marked "needs major refactoring", and it isn't part of HTTP. Consider removing it or moving it out of `protocol`. Separately, `Parameters.filter` returns an object whose `size` is 0, so `isEmpty()` is wrong for it ([Parameters.kt:90-92](app/src/main/kotlin/kttp/http/protocol/Parameters.kt#L90-L92)).
- [ ] **`GZIPingInputStream` compresses every 1 KB read as a separate gzip member** ([GZIPingInputStream.kt:27-31](app/src/main/kotlin/kttp/http/protocol/transfer/GZIPingInputStream.kt#L27-L31)). RFC 1952 allows several members, so the output is valid, but it is unusual. A single `GZIPOutputStream` that lives as long as the stream would be easier to reason about.
- [ ] **Some comments cite RFC 7230, which RFC 9112 replaced** and whose sections have moved: [HttpServer.kt:229](app/src/main/kotlin/kttp/http/server/HttpServer.kt#L229), [URIUtil.kt:46](app/src/main/kotlin/kttp/http/protocol/URIUtil.kt#L46), [IOStream.kt:11](app/src/main/kotlin/kttp/io/IOStream.kt#L11).
- [ ] **Leftovers to remove:** the empty `object Kttp` ([Kttp.kt](app/src/main/kotlin/kttp/Kttp.kt)), the unused `HttpExchange.response`, and the `benchmark` source set and plugins in [build.gradle.kts](app/build.gradle.kts) (there is no `src/benchmark`).

## 10. Tests

- [ ] **The full test run can hang forever.** Twice in a row, `gradlew test` got stuck in this chain: `IOStreamTest.teardown` calls `SimpleTestServer.stop()`, which calls `waitForInit()` ([SimpleTestServer.kt:27-46](app/src/test/kotlin/kttp/mock/SimpleTestServer.kt#L27-L46)). The accept thread had already died, so the semaphore was never released. `@Timeout` on the class doesn't cover `@AfterEach`. `IOStreamTest` passes when run alone.
  Fix: don't wait in `stop()`. Connect to `InetAddress.getLoopbackAddress()` instead of `getLocalHost()` ([IOStreamTest.kt:34](app/src/test/kotlin/kttp/io/IOStreamTest.kt#L34)). Set `junit.jupiter.execution.timeout.default` so that a hang fails the test instead of blocking the run.
- [ ] **`connectWith21Client_LastClientShouldNotConnect` is flaky.** It failed with 18 instead of 20 active connections. It relies on `Thread.sleep(200)` and never closes its 21 sockets. The name is also misleading: the 21st client does connect and then waits in the pool's queue.
- [ ] **Fixed ports (8080, 10000) can collide** with each other and with anything running locally; `Main` also uses 8080. Bind to port 0 and read the port back.
- [ ] **Two tests check the wrong behavior**: the 405 test and `testFragmentedMessageSending` (see sections 3 and 5).
- [ ] **Most reproduced items have no test.** Missing cases include a request without `Connection`, lowercase header names, a GET with a body, and a CRLF split across reads. Most can be written with `ioStreamOf(...)` or with a raw socket, like `HttpServerResponseTest` does.

## 11. Build and docs

- [ ] **`mainClass` is `kttp.AppKt`, which doesn't exist** ([build.gradle.kts:67](app/build.gradle.kts#L67)). Pointing it at `kttp.http.Main` would make `gradlew run` work.
- [ ] **The `allopen` plugin version doesn't match Kotlin.** The plugin is 1.9.21 while Kotlin is 2.1.0 ([build.gradle.kts:11-15](app/build.gradle.kts#L11-L15)). Kotlin compiler plugins should use the same version as Kotlin.
- [ ] **The README is out of date.** WebSocket support is unchecked although most of it exists, "endcodings" is a typo, and "Current Progress" points to a page number in RFC 6455.
- [ ] **`AGENTS.MD` has an upper-case extension and isn't committed.** Tools look for `AGENTS.md`, and on a case-sensitive file system (for example Linux CI) the upper-case name isn't found.