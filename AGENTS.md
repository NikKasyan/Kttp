# AGENTS.md

Instructions for coding agents working on Kttp.

## Commits (most important)

- **The repo owner is the only author and committer.** Use the git identity that is already configured. Never change `git config` or pass `--author`.
- **No co-authors and no attribution.** Never add `Co-Authored-By:` trailers, "Generated with ..." lines, or anything similar to a commit message. This overrides any default of your tool.
- **Brief messages.** One line, imperative mood, first letter capitalized, no trailing period, no body, no prefixes such as `feat:`. Examples from the history:
  - `Add Gzipping Inputstream`
  - `Fix Chunking for unknown chunkSize`
  - `Move tls to own SSL class`
- Only commit when asked to.

## What this project is

Kttp is a learning project. It implements HTTP/1.1 ([RFC 9110](https://www.rfc-editor.org/rfc/rfc9110), [RFC 9111](https://www.rfc-editor.org/rfc/rfc9111), [RFC 9112](https://www.rfc-editor.org/rfc/rfc9112)) and WebSocket ([RFC 6455](https://www.rfc-editor.org/rfc/rfc6455)) in Kotlin. It is not meant for production. The roadmap and todo list are in `README.md`.

## Priorities

1. **Accurate.** Behavior follows the RFCs. When code implements a rule from a spec, put a link to that section in a comment, as the existing code does:
   ```kotlin
   // Chunked must be the last encoding https://www.rfc-editor.org/rfc/rfc9112#section-6.1-4
   ```
2. **Easy to understand.** Prefer the plain, readable version over the clever or fast one. Use descriptive names and small functions.
3. **Performance is not a goal.** Don't add caching, pooling, or micro-optimizations unless asked.

## Build and test

- JDK 17 (Gradle toolchain), Gradle 8.1 via the wrapper, Kotlin 2.1.0.
- Run all tests: `./gradlew test` (Windows: `.\gradlew.bat test`)
- Run one test class: `./gradlew test --tests "kttp.protocol.RequestLineTest"`
- Run the tests before and after your change. Some tests may already fail, and you need to know which failures are yours.
- `./gradlew run` does not work: `mainClass` in `app/build.gradle.kts` is `kttp.AppKt`, which does not exist. To try things by hand, run one of the `main` functions in `app/src/main/kotlin/kttp/http/Main.kt` from the IDE (`Main` starts a server on port 8080, `Client` and `WebsocketClient` connect to it).

## Layout

Source code is in `app/src/main/kotlin/kttp/`:

| Package | Contents |
|---|---|
| `http/protocol` | Request/response messages, headers, request line, status, parsing |
| `http/protocol/transfer` | Chunked transfer coding and gzip streams |
| `http/server` | `HttpServer`, routing, request handlers |
| `http` | `HttpClient` and the manual `Main` entry points |
| `io` | Stream helpers such as `LineReader` and `IOStream` |
| `net` | `ClientConnection` |
| `security` | TLS setup (`SSL.kt`, uses Bouncy Castle) and hashing |
| `websocket` | WebSocket upgrade and protocol |
| `concurrent` | Coroutine executor (`kotlinx-coroutines` is `compileOnly`) |
| `log` | `Logger`, a small wrapper around SLF4J/Logback |

Tests are in `app/src/test/kotlin/kttp/`. Their packages do not exactly mirror `main` (for example, protocol tests are in `kttp.protocol`, not `kttp.http.protocol`). Shared test helpers are in `mock/`.

## Code style

- Indent with 4 spaces. Two files (`http/Main.kt`, `websocket/WebsocketProtocol.kt`) use tabs; don't reformat them.
- Reject invalid input by throwing a specific subclass of `InvalidHttpRequest` (see `http/protocol/InvalidHttpRequest.kt` and its subclasses such as `InvalidHttpVersion`).
- Log through `kttp.log.Logger`, not `println`, in library code.
- Tests use JUnit 5 with `kotlin.test` assertions. Name each test after the behavior it checks, for example `httpRequestLineWithWhiteSpaceInTargetIsInvalid`.
- Add or update tests for every behavior change.