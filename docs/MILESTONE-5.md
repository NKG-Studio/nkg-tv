# Measured stability, layout and media-policy regression milestone

This is a reviewable preview, not final acceptance of the cross-platform real-time AI product. The user now requires real in-playback AI at >=30 enhanced FPS on both Android and Windows; no current build meets or claims this. Offline testing below is completed repair evidence only, not the primary path or a substitute.

## Android changes

- Centralized playback start/pause screen-awake flags; completion, errors and release clear them. Background preparation still requires explicit user resume.
- Asynchronous MediaPlayer errors release the failed player so the preparation timeout cannot later overwrite the error.
- Two-line header with ellipsis; complete long titles remain readable inside the scrollable detail body.
- Decoder output format changes now override extractor color metadata. Supported matrix/range/transfer values are explicitly validated. SDR BT.2020, HDR and unknown configurations are rejected instead of mislabeled. Missing metadata on this limited SD path uses disclosed BT.601 limited-range SDR defaults. This is not a color-management solution for wide-gamut content.
- Multi-audio-track media is rejected before inference with an explicit message. No track is silently discarded. Track selection is not implemented.
- Cancellation removes unfinished output and retry starts from the beginning; there is no checkpoint/resume feature. Cancellation waits for any current native inference to return.

A new original MIT fixture contains moving blocks, diagonal texture and a hard cut at 2 seconds: 4 seconds, 16 frames, 64x36 at 4 fps, plus a 660 Hz audio tone. Generator: tools/make-motion-fixture.py. This expands pipeline coverage but is still a small low-resolution clip, not a long-film stability benchmark. Peak PSS is sampled once per second for the app process; it excludes separate codec/system services and may miss shorter peaks. QEMU TCG has no KVM and other cloud build work ran concurrently, so elapsed time is diagnostic, not a controlled hardware benchmark.

## Desktop regressions and rendering

Seven browser regressions pass: invalid catalog still restores favorites/history; late refresh cannot replace imports or update after navigation; JS and Go agree on UTF-16 title limits (256 Chinese accepted, 257 ASCII or 129 emoji rejected); local cross-origin HTTPS with CORS permits actual Rust WASM inference; without CORS original playback works and AI is disabled. Native Go parser/persistence tests and the race detector pass separately. The browser bridge is mocked; this does not prove native WebView2 integration.

80 route changes completed without page errors. During 30 one-second playback samples, ~29.130 seconds between first/last observations decoded 694 additional frames with one additional dropped frame. JS heap sampled ~2.31–3.94 MB and no browser Long Tasks were observed. This was headless Linux Chromium with original local MP4, not a Windows executable, display smoothness or GPU benchmark.

Visual inspection found clipped long ASCII titles and neighboring cards stretched by long descriptions. Cards now wrap/clamp metadata, retain full text in a scrollable detail dialog and align at the top. Navigation resets stale scroll position. Layout checks cover 1180, 900 and 600-pixel widths across home, long metadata, detail, player and sources, with no document/dialog horizontal overflow. Screenshots in screenshots/ are actual Chromium renders, not Windows native screenshots. Do not infer Windows appearance/performance solely from them.

## Environment limits and next architecture

No Wine, virtual X server or WebView2 Runtime is installed, so no EXE launch was attempted. New dependency/license requirements and the native GPU/video restructuring needed for real-time enhancement are documented in REALTIME-ARCHITECTURE.md. No installer license was accepted, no remote Release was published, and no work used the user's computer.

Actual feed JSON is still unavailable because of the cloud CONNECT 403. It remains an input blocker; no access-control workaround was attempted. iOS remains unimplemented/unbuilt. Windows still has the experimental TinySR2 model, not mature-model parity with Android.

## Executed expanded Android results

The 16-frame motion/cut test passed: all 16 video timestamps match, all 174 AAC payloads and extractor timestamps match, output 128x72 is decodable. Full transcode took 669.389 seconds; peak sampled app PSS was 45,249 KiB (44.19 MiB), 588 samples. The preceding cancellation after two frames took 80.625 seconds including inference, removed partial output, and invalid-source failure also removed its partial file. A fresh complete run after these failures succeeded. It is restart-from-beginning, not checkpoint resume. Evidence: android-enhancer-stress.json.

An independent FFprobe check then exposed a gap hidden by comparing only Android MediaExtractor timestamps: AAC priming edit metadata was lost. The added bounded MP4 metadata writer preserves simple encoder delay/padding edits, track/movie duration and all mdat offsets; unsupported existing timing-edit structures fail instead of silently changing audio. The same Java writer corrected the captured 16-frame output. Independent FFprobe/FFmpeg now confirms identical audio packet PTS/payloads, priming skip samples, 4.000000-second audio duration, and decoded PCM hash/length. This metadata pass did not rerun neural inference. Evidence: audio-timing-independent.json and tests/verify-audio-timing.py. The 16-frame stress run predates the subsequent color/priming fixes; separate final-APK short-clip and policy regressions verify their integration.

29 Android flow assertions passed, including actual asynchronous MediaPlayer onError from a corrupt file in the allowed local directory, screen-awake flags, Activity recreation followed by favorites recovery, and long-header layout/navigation. The earlier asset:missing.mp4 check is only synchronous URL validation, not a preparation-failure test. No process-restart persistence test is claimed. The actual 30 playback samples remain in android-flow-final.json.

Android policy instrumentation passes: decoder color standard/range supersede extractor values while absent transfer remains; a decoder BT.2020 update is rejected; actual multi-audio and BT.2020 MP4 fixtures are rejected before inference and leave no partial output. 19 JVM unit tests pass, Android lint has 0 errors and 16 warnings.

AAC fix references: Android exposes encoder-delay / encoder-padding in MediaFormat, while the observed MediaMuxer output did not retain the edit. The custom writer is original code for our own bounded output files, not a general MP4 editor. Source audit: https://android.googlesource.com/platform/frameworks/av/+/refs/heads/main/media/libstagefright/MPEG4Writer.cpp . Independent byte/PCM verification, not that source alone, establishes this fixture's result.

The final Android APK then completed the 4-frame regression with both decoder-color handling and AAC timing preservation active: 187.358 seconds, 66,590 KiB peak sampled PSS, 161 samples, video/audio packet checks pass, and decoded output frame available. Independently auditing this actual Android-produced MP4 with FFprobe/FFmpeg passes the same audio packet/PTS/priming/duration/PCM comparison (audio-timing-final-android.json). The different PSS values reflect separate runs/process state; neither is a certified maximum for larger input.

## Real-time priority change and screenshot closure

After the user explicitly required real in-playback >=30 enhanced FPS, offline expansion stopped. The remaining independent work adds native Vulkan capability probing, shared Rust bounded frame accounting and viewport-fit geometry. The actual Android Vulkan device is SwiftShader CPU; existing ncnn is CPU-only. Model dense arithmetic cost and the GPU implementation/testing gap are documented without claiming Snapdragon 8 Gen 3 performance. Android target is now Snapdragon 8 Gen 3; input must remain at source-native resolution, with 480p/720p/1080p benchmark tiers. Windows GPU remains unconfirmed.

Final compositor captures were repeated after waiting for actual rendering and inspected visually. A stale paused-status label after resume was found and fixed; preparation/error controls are now disabled rather than actionable-looking no-ops. The separate final visual runner passed pause/resume status and screen-awake assertions, error control state, and six 540x960 captures. Evidence: android-visual-final.json and screenshots/. Desktop captures remain Linux Chromium renders.

The complete Android rebuild also exposed and fixed a build-script issue: Cargo cdylib output now explicitly uses each ABI's NDK linker, rather than host cc. Both ABIs were rebuilt successfully, followed by Gradle tests/lint/debug packaging and v2 signature verification. Rust now has 14 passing unit tests, with clippy -D warnings and rustfmt passing.
