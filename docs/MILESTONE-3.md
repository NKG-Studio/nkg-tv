# Milestone 3 — desktop correctness regressions

- CORS mode is set before a remote media URL. A real isolated HTTPS test origin verifies that CORS-enabled media permits frame inference; missing CORS retries original playback once and explicitly disables AI.
- Source generation tokens prevent old refresh responses from overwriting a later imported source or a navigation change.
- Go and JavaScript both enforce 256 UTF-16 units for titles (including emoji boundary tests).
- Invalid cached catalog content no longer prevents separately valid favorites/history restoration.
- Native Go persistence is extracted and tested: parser-to-storage round trip, invalid-write preservation, concurrent saves, complete atomic replacement. Go race tests pass.
- Linux Chromium regression suite passes. It uses mocked WebView2 bridge functions; native Go storage/parser is tested separately. This does not prove Windows runtime behavior.
- Windows EXE rebuilt. The cloud contains no Windows executor, Wine or WebView2 runtime; no Windows native runtime validation is claimed.
- Feed remains unread due to CONNECT 403, with no alternate route attempted.
