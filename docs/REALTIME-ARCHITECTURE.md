# Mandatory acceptance update: real AI frames at >=30 FPS

The user explicitly requires **real-time in-playback AI super-resolution, with sustained enhanced frames >=30 FPS on BOTH Android and Windows**. Offline transcode does not satisfy acceptance and is no longer the primary development path. Never count original fallback, repeated display, interpolated frames or low-frequency sampled enhancement toward 30 enhanced FPS. Do not promise this on every device. Android minimum target is now confirmed as Snapdragon 8 Gen 3. The user confirmed source-native input dimensions: never pre-downsample all videos to 720p. Test 480p/720p/1080p source tiers separately; 720p is a benchmark tier only. PC GPU is still unconfirmed. Output must preserve display aspect ratio and fit the physical player content viewport with letter/pillarboxing, rotation and sample-aspect correction, rather than stretching to the whole screen.

# Real-time video AI: remaining architecture and validation

Offline transcode is an additional feature. It does not replace the original goal of synchronized, full-resolution real-time super-resolution. The current TextureView -> CPU readback -> ncnn -> ImageView overlay is a sampling preview and cannot meet that goal. Linux host full-frame 320x180 inference already measured p50 213.572 ms versus a 33.333 ms budget for the required minimum 30 fps; QEMU is much slower. TinySR2 WASM on desktop is a different experimental model, not Android model parity.

A credible next implementation needs:

1. Android hardware MediaCodec output into a native GPU rendering pipeline; avoid Java Bitmap/int-array round trips. Evaluate ncnn Vulkan image/buffer input and decoder texture interop on actual drivers. GPU interop is not implemented or assumed to be zero-copy.
2. Explicit video presentation timestamps, audio master clock, bounded inference queue and GPU fence synchronization. On deadline misses, select the original frame with matching PTS; do not display stale neural output or silently slow audio. Preserve user choice and default-off behavior.
3. A measured model/resolution/device matrix. Target input/output resolutions are awaiting user confirmation; use source material with at least 30 distinct frames per second, then evaluate 720p/1080p and live action separately. Measure end-to-end decode+inference+render p50/p95/p99, dropped/repeated frames, A/V drift, CPU/GPU memory, 30-minute thermals/power, seek and lifecycle interruption. Temporal flicker and quality need motion/cut clips and human inspection, not only PSNR.
4. Windows native decoded-frame access (e.g. Media Foundation/D3D11) and GPU inference/rendering rather than DOM canvas sampling. The current Go WebView2 window can remain UI, but native video/AI code and a tested bridge are substantial new work. Any newly required Windows SDK/runtime licenses must be authorized before installation.
5. iOS needs a macOS/Xcode execution environment, AVFoundation/Metal/CoreML or a verified compatible ncnn backend, model conversion validation, and device tests. None of this is implemented or built.

Required environments: a cloud-accessible Windows 10/11 runner with an authorized WebView2 Runtime; ARM64 Android hardware with working GPU drivers (plus low/mid/high performance devices for claims); macOS/iOS environment for that platform. All development/test execution must stay off the user's computer. The current cloud Linux container has no KVM or usable Android GPU, no Windows runtime, Wine or X server, and no macOS toolchain.

## Windows compatibility-layer feasibility

No wine/wine64, Xvfb/Weston, or WebView2 Runtime executable was found in the installed tool paths. Only the small WebView2Loader DLL exists; it is not the browser runtime. An EXE launch attempt cannot succeed without these prerequisites. No Wine or Microsoft runtime installer was downloaded/run and no new license was accepted.

A possible experimental stack needs Wine (LGPL-2.1-or-later), a headless X server such as Debian Xvfb and its packaged dependencies, plus the Microsoft WebView2 Runtime under its own software license. This would require explicit authorization under this task's dependency constraint. Even after authorization it may fail: Wine compatibility is not equivalent to a supported native Windows environment, so it could provide only a limited startup smoke test.

Official references:
- Wine license: https://raw.githubusercontent.com/wine-mirror/wine/master/LICENSE
- Debian Wine package notices: https://sources.debian.org/src/wine/10.0~repack-12/debian/copyright/
- Debian Xvfb package/source notices: https://packages.debian.org/trixie/xvfb
- Microsoft Runtime download and license dialog: https://developer.microsoft.com/en-us/microsoft-edge/webview2/
- Microsoft explains where Runtime license terms appear: https://learn.microsoft.com/en-us/microsoft-edge/webview2/concepts/data-privacy
- Microsoft's Wine compatibility discussion: https://github.com/MicrosoftEdge/WebView2Feedback/issues/3127

## Actual cloud capability probe and implementable next steps

`tools/vulkan-probe.cpp` was compiled with the already authorized Android NDK and executed inside the cloud Android emulator. Vulkan instance creation succeeds, but the sole enumerated device is **SwiftShader Device (Subzero), VkPhysicalDeviceType CPU (4)**. This is software execution, not performance hardware. The Linux container exposes no /dev/dri, /dev/nvidia0 or /dev/kvm. Current ncnn platform headers explicitly set NCNN_VULKAN=0. See android-vulkan-probe.txt and cloud-capabilities.json.

Required implementation order:
1. Replace the ncnn CPU-only build with an authorized Vulkan-enabled build and preserve its dependency notices. Select/record actual physical device type and driver; software Vulkan can test correctness only. Never silently label CPU fallback as GPU.
2. Introduce native Android decoder output ownership and a Vulkan render/inference adapter with bounded in-flight frames. Remove TextureView.getBitmap/int-array/ImageView from the real-time path. Confirm actual external-image interop support on the target GPU before claiming zero-copy.
3. Windows needs a native Media Foundation/D3D11 decoder/render bridge and model backend, not the present WASM canvas path. Native Windows/GPU execution is mandatory; Wine/software rendering cannot qualify.
4. Wire GPU completion fences and actual render acknowledgement into the Rust frame ledger, along with source/model resolution, GPU timestamps and audio-master PTS. Only then run sustained acceptance tests.

The new Rust `core/src/realtime.rs` is a tested accounting/scheduling foundation, **not an implemented GPU playback engine**. It bounds pending work, rejects duplicate/out-of-order source PTS, distinguishes inference completion from presentation, rejects stale/late results, holds early results, excludes originals/preview frames, and invalidates results at seeks. Fixed one-second processing/display counters require every measured window >=30 for at least 60 seconds; short bursts cannot be combined across seeks. These synthetic timestamp unit tests are not performance evidence. The numerical counter gate is necessary but not sufficient: real display cadence/frame gaps, backend correctness, actual complete-frame model execution and thermal stability must also pass. It is not wired to Java/WebView2 playback yet.

Each eventual performance report must include: source/target width and height; source FPS; exact model/hash and precision; backend and device/driver; warmup and sustained duration; unique full-frame inference FPS and unique enhanced presentation FPS separately; per-window minima and frame-interval percentiles; originals/repeats/drops; PTS drift; peak app+GPU/codec memory; temperature/throttling and power condition. Require >=30 distinct input frames/s for a >=30 distinct enhanced presentation test; a 24 FPS source cannot qualify by duplicating frames. A >=60-second initial run is only a smoke gate; 30-minute thermal/stability validation remains required.

Remaining environment requirement is a cloud-accessible ARM64 Android GPU device plus a Windows 10/11 GPU runner with an authorized WebView2 Runtime. Exact device/resolution/model choices must precede any performance commitment. No user's computer or paid service is used without further authorization.

Shared geometry is implemented and tested in core/src/layout.rs (portrait/landscape, non-square pixels, rotation and actual content bounds); native renderer integration remains pending. The bundled animevideov3-x2 parameters contain 18 convolutions, 619,200 dense MACs per source pixel, then an internal 4x PixelShuffle and 0.5x resampling. At candidate 1280x720 this is ~570.655 GMAC/frame or ~1.141 TFLOP/frame under the conventional 2-FLOP/MAC count, ~34.239 TFLOP/s equivalent at 30 FPS. This is arithmetic accounting, NOT device measurement or a strict hardware lower bound (e.g. Winograd/precision optimization changes executed operations). It identifies serious model-cost risk; merely enabling Vulkan is not a performance guarantee. 1080p cost is separately listed in model-cost-estimate.json. A lighter, licensed and quality-validated model may be necessary. No new model has been selected or silently substituted.

The next GPU prototype can be developed without inventing a hardware pass. Hardware profiling still requires the target device. Existing source-native input, full-frame accounting, actual display fences and model-quality requirements must survive any backend optimization; lowering input resolution cannot be used to satisfy the 30 FPS claim.
