# Third-party notices and provenance

## Bundled in APK

- **ncnn 20250503**: Tencent contributors, BSD-3-Clause plus component notices. Official Android CPU archive: https://github.com/Tencent/ncnn/releases/download/20250503/ncnn-20250503-android.zip . Full upstream license preserved at `app/src/main/assets/licenses/ncnn.txt`. Compiled statically into JNI with NDK libc++/OpenMP; NDK LLVM runtime notices: Apache-2.0 with LLVM exceptions and third-party notices, see bundled `LLVM-NOTICE.txt`. No Vulkan driver or external binary is downloaded by the application.
- **Real-ESRGAN realesr-animevideov3 ×2**: Xintao Wang et al., model obtained without conversion from official release https://github.com/xinntao/Real-ESRGAN/releases/download/v0.2.5.0/realesrgan-ncnn-vulkan-20220424-ubuntu.zip . Upstream project BSD-3-Clause: https://github.com/xinntao/Real-ESRGAN/blob/v0.2.5.0/LICENSE . No separate weight license was found in the official release; redistribution follows its project license, preserved in `assets/licenses/Real-ESRGAN.txt`. Model is animation-oriented, spatial (single-frame), not temporally trained video restoration. Official model description: https://github.com/xinntao/Real-ESRGAN/blob/master/docs/anime_video_model.md . The upstream ncnn distribution license is also preserved at `assets/licenses/Real-ESRGAN-ncnn-vulkan.txt` (MIT).
- Model parameter SHA256: `b88ff4f00ebf019a7fdac17fdd45a7fd3665d37509efc5baf2e4da2e24420a04`.
- Model weight SHA256: `548a36f9c3f4ab8da56cd3b13badf23968bee207b396dad14d04b830e5f2ab2d`.
- **Rust standard library**: MIT / Apache-2.0 and bundled notices, https://rust-lang.org/policies/licenses/ . Core has no third-party Rust crates. Applicable toolchain license files copied into APK notices.
- **TinySR2**: original 9-input, 24-ReLU, 4-output residual network, 340 float32 parameters, trained from procedural sine/shape fields only; no third-party training images/weights. MIT, same as original project code. Not used as the default Android model. Synthetic-only metrics are not claims of film quality.
- **demo.mp4**: original procedural FFmpeg testsrc2 + sine tone. Contains no third-party film or soundtrack. Reproducible using `tools/make-demo.sh`; provided under the project MIT license. FFmpeg itself is a build/test tool, not bundled.

## Build and test only

- Android SDK/NDK: official Google packages, Android SDK license https://developer.android.com/studio/terms ; installation explicitly authorized for this cloud task. These tools are not included in source archives or APK except required redistributable runtime code and notices.
- Eclipse Temurin OpenJDK 17: GPLv2 with Classpath Exception; compiler/runtime used only in cloud builds. https://github.com/adoptium/temurin17-binaries . No JDK bundled.
- Gradle 8.9 / Android Gradle Plugin 8.7.3: Apache-2.0; wrapper source/binary provided with source. https://github.com/gradle/gradle/blob/v8.9.0/LICENSE . Wrapper distribution checksum pinned.
- JUnit 4.13.2: EPL-1.0, https://github.com/junit-team/junit4/blob/r4.13.2/LICENSE-junit.txt ; Hamcrest 1.3: BSD-3-Clause. JVM tests only, not app runtime.
- JSON-java 20240303: public-domain implementation, https://github.com/stleary/JSON-java/blob/20240303/LICENSE ; JVM test implementation only. Runtime uses Android org.json.
- NumPy 2.3.5: BSD-3-Clause, preinstalled cloud training tool; not bundled.

## Product reference

LibreSpark/LibreTV, AGPL-3.0: https://github.com/LibreSpark/LibreTV/blob/main/LICENSE . Only product flow was studied. No LibreTV code, artwork, source list or implementation copied. This repository is an independent native Android implementation.

## Windows preview additions

- Go 1.24.2 standard library: BSD-style Go license, `desktop/licenses/Go.txt`.
- `github.com/jchv/go-webview2` pinned `v0.0.0-20260205173254-56598839c808`: MIT, `desktop/licenses/go-webview2.txt`.
- `go-winloader` pinned `v0.0.0-20250406163304-c1995be93bd1`: MIT, `desktop/licenses/go-winloader.txt`; loads the embedded official WebView2Loader.
- WebView2Loader.dll: Microsoft BSD-style permissive redistribution license, full text `desktop/licenses/WebView2Loader.txt`. This is the loader, not the browser runtime. The Windows EXE requires an existing WebView2 Runtime and never silently installs it or accepts new runtime terms.
- `golang.org/x/sys` pinned in `desktop/go.mod`: BSD-style, `desktop/licenses/x-sys.txt`.
- Windows Rust WASM uses original TinySR2, with Rust notices and project license bundled under `desktop/licenses/`. No Windows SDK, MinGW, Wine or paid build service was used.
