# Vulkan TinySR2 prototype — WIP

This optional debug-only prototype preserves unfinished work for local continuation. It is not connected to the playback UI, not a final film-quality model, and does not meet the 30 enhanced presented FPS requirement.

`core/gpu/tinysr2.comp` is generated from the original MIT TinySR2 Rust weights by `tools/generate-tinysr-shader.py`. `vulkan_sr.hpp` runs FP32 compute at original source dimensions, with host upload and readback. The additional YUV branch converts strided decoder planes in the shader. `jni.cpp` and debug `GpuCore.java` expose this to a worker thread. `GpuDecodeInstrumentation` checks MediaCodec frames against the CPU reference, without audio or presentation. Both Android ABIs compiled and debug/test APK assembly succeeded; the new decoder instrumentation run was stopped for handoff and has no completed result.

Verified earlier ARGB path on SwiftShader CPU (not hardware GPU):

| Input | Result | Timing |
|---|---|---|
| 64×36 | Exact Rust byte parity | Six measured dispatches, p50 61.3425 ms |
| 854×480 | Exact full-frame byte parity using halo-tiled Rust reference | Three measured dispatches, p50 6383.87 ms, p95 7303.06 ms |
| 1280×720 | Compute fence timeout (VkResult 2) | 10-second wait exceeded; no valid benchmark |
| 1920×1080 | Not run after failure | No result |

These synthetic repeated-frame timings include upload, compute and readback; they are not playback FPS. The hardware-only gate rejected SwiftShader as expected. After adding the YUV shader branch, the small ARGB regression remained byte-exact; this does not validate actual YUV decoder integration.

Evidence: [480p benchmark](gpu-480p.json), [480p parity](gpu-480p-parity.json), [small-frame parity after YUV branch](gpu-small-parity.json), [tier failure log](gpu-tier-run.txt). Local build/test commands are in [LOCAL-HANDOFF.md](LOCAL-HANDOFF.md). `test-vulkan-tiers.sh` expects the smoke CLI and shader already installed under `/data/local/tmp`; it is a low-level developer harness, not a standalone playback test.

Remaining: mature licensed model selection, quality validation, device-aware GPU support, robust resource/timeout recovery, external-image import and GPU presentation, audio synchronization, Rust ledger integration, and target-device sustained playback measurement. CPU readback, software-device test opt-in and temporary test infrastructure must not be mistaken for production implementation.
