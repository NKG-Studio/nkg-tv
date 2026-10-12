# Milestone 4 — Android playback and full-frame AI work

## Playback regression results

A direct Android instrumentation runner replaces UIAutomator's global-idle dependency for the continuously updating player. It uses actual UI click/seek events, real MediaPlayer state and clock assertions, and target app preferences.

20 checks pass: home, empty search, favorite, prepare and advance, pause freeze, resume, direct seek, seek-bar touch, back, stored-position resume, completion, replay, preparing in background, no background auto-start, explicit foreground resume, sustained looping, history, persistent favorite, invalid source error, error back navigation.

The app now tracks foreground state before starting a newly prepared player, and ignores callbacks from released players.

30 samples over 34.675 seconds: media advanced 19.849 seconds (~0.572x wall time), app PSS 62,171–67,972 KiB, app process CPU delta 9,749 ms. This is Android 10 x86_64 under QEMU TCG without KVM. It proves functioning controls and sustained state, not smooth native-device performance.

Full raw observations: android-instrumentation.json and android-performance.json.

## Full-frame AI limitation and implementation

Host ncnn benchmark at original 320x180 -> 640x360: p50 213.572 ms, p95 360.991 ms, versus a 41.667 ms deadline at 24 fps. This CPU path cannot responsibly be advertised as real-time.

A separate user-triggered enhancement path passed an Android emulator instrumentation test: MediaExtractor -> MediaCodec readable YUV frames -> official ncnn model at original dimensions -> 2x AVC encode with original video timestamps -> original compressed audio packets -> playable MP4. Every decoded frame is processed, without downsampling or dropping frames. It is explicitly offline, cancelable, and currently limited to even-sized SDR progressive MP4 up to 320x180 and one hour; no HLS, HDR or DRM export.

Windows still has only the experimental Rust WASM frame path; mature-model parity and native Windows runtime testing remain incomplete. No Windows/Wine/WebView2 execution environment is available here.

The instrumented test processed all 4 original 64x36 frames into 128x72 AVC. Input/output video PTS were exactly [0, 125000, 250000, 375000] microseconds. The 23 compressed AAC packets retained identical timestamps and SHA256. Runtime was 205.385 seconds in software emulation; this tiny fixture validates pipeline mechanics only, not sustained transcode stability or quality. Full evidence: android-enhancer.json. Enhanced copies appear in local history and remain inside app storage. Cancellation is checked between codec operations and after neural inference; a running native inference cannot be immediately interrupted.

Follow-up reused that generated MP4 without rerunning inference: Android MediaMetadataRetriever successfully decoded a 128x72 frame, and a pre-cancelled job rejected before processing. Evidence: android-enhancer-decode.json; its 1.341-second elapsed time is validation time, NOT enhancement speed. Latest Gradle unit tests, lint (0 errors, 14 warnings), debug APK and instrumentation APK builds passed. Both APKs installed successfully on the software emulator.
