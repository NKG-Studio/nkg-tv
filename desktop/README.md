# Windows desktop preview

A real Windows x64 GUI EXE. Go creates a native window and embeds WebView2Loader.dll; UI/assets are embedded and served only on a random loopback port with an unguessable path. No hosted service, browser tab launcher, user computer access, or external deployment is used in development.

Requires Windows 10/11 and an existing Microsoft Edge WebView2 Runtime. The application does not install the runtime or accept Microsoft runtime agreements. The included WebView2Loader has a BSD-style redistribution license; see `licenses/WebView2Loader.txt`. No Windows SDK is used for cross compilation.

Build in Linux with Go 1.24.2:

```sh
# from repository root: rebuild shared Rust WASM first
rustup target add wasm32-unknown-unknown
cargo build --manifest-path core/Cargo.toml --release --target wasm32-unknown-unknown
cp core/target/wasm32-unknown-unknown/release/nkg_core.wasm desktop/ui/core.wasm
cd desktop
go test ./...
GOOS=windows GOARCH=amd64 CGO_ENABLED=0 go build -trimpath -ldflags='-H windowsgui -s -w' -o ../artifacts/NKG-TV-0.1.0-preview-windows-x64.exe .
```

State lives in `%AppData%/NKG-TV/state.json`; WebView2 data lives beside it. No telemetry. Source URLs must be HTTPS. Catalog parsing is bounded (2 MiB, 2000 items), redirects remain HTTPS, network timeouts are 18 seconds.

Implemented: local catalog search, details, MP4 playback, native video controls, fullscreen, favorites, history/resume, source refresh/change, JSON import, errors, reduced-motion support. No HLS library is bundled, so playback formats depend on installed WebView2 media support. The UI uses DOM text nodes for untrusted source text.

Windows AI is currently the small Rust TinySR2 WASM experimental model on sampled frames, disabled by default. It is **not** the Android ncnn mature animation model and has not demonstrated film quality benefits. Cross-origin video readback without CORS fails safely and returns to original playback. This remains a preview, not final acceptance.

Linux browser UI tests do not establish that the Windows native binary works on Windows; actual Windows execution, GPU behavior and sustained performance are still unverified. The EXE is unsigned (no Authenticode certificate); no paid certificate/service used.
