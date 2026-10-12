# 本地开发交接（2026-10-12）

按用户要求，云端停止新增功能和长时间测试，保存并推送源码后暂停。当前版本为 **0.1.0-preview**。没有发布 Release、部署服务或操作用户电脑。此前完整验证基线为 `7acc1cf17f462e630d2d944fca3592342ff06456`；本次追加保存 GPU WIP。

## 当前可用与未完成

Android 已实现配置源、搜索、详情、播放/暂停/定位、错误提示、返回导航、历史、收藏。Rust 提供共享推理、帧统计及画面布局计算。Windows 有 Go/WebView2 窗口程序及共享 Rust WASM；只做过交叉编译和 Linux 浏览器验证，未运行 Windows 原生程序。iOS 未实现、未构建。

最终目标仍是骁龙 8 Gen 3 与 Windows 上，按源原始 480p/720p/1080p 分别验证，持续至少 30 个真实 AI 增强呈现帧/秒，按播放器实际视口等比显示。**尚未满足此验收**，PC GPU 型号仍待明确。不得把重复帧、原画回退、抽帧预览或离线处理速度算作增强播放 FPS。

Android 已集成 Real-ESRGAN animevideov3 x2 ncnn CPU 推理；默认关闭的低分辨率抽帧预览与有限制的离线处理都不等于实时超分。新 Vulkan 路径只在 debug 测试中存在，没有主界面入口。它使用原创、合成数据训练的 TinySR2 验证计算正确性，并非已经选定的成熟影视超分模型。详见 [GPU 原型](GPU-PROTOTYPE.md)、[实时架构](REALTIME-ARCHITECTURE.md)。

## Linux 构建环境

已使用 JDK 17、Rust 1.85.1、Gradle 8.9（仓库 wrapper）、AGP 8.7.3、Android SDK Platform 35 / Build Tools 35、NDK 27.2.12479018、Python 3、Go 1.24.2。脚本当前使用 Linux x86_64 NDK 工具路径。先自行安装并授权相应工具；这些命令不自动接受新许可。许可和模型归属见 [THIRD_PARTY.md](../THIRD_PARTY.md)。

设置 `JAVA_HOME`、`ANDROID_HOME`、`ANDROID_NDK_HOME` 为本地已安装路径，确保 java/cargo/adb/python3 在 PATH。不要照抄云端 `/workspace/toolchains` 路径。避免冲突的旧变量：

```sh
unset ANDROID_SDK_HOME
export ANDROID_NDK_HOME="$ANDROID_HOME/ndk/27.2.12479018"
rustup target add aarch64-linux-android x86_64-linux-android wasm32-unknown-unknown
./tools/fetch-ncnn.sh
export NCNN_ANDROID_DIR="$PWD/.tools/ncnn-20250503-android"
./tools/build-native.sh
./gradlew testDebugUnitTest lintDebug assembleDebug
cargo test --manifest-path core/Cargo.toml
cargo clippy --manifest-path core/Cargo.toml -- -D warnings
```

APK 位于 `app/build/outputs/apk/debug/app-debug.apk`，仅为调试签名测试版。未提交密钥，不要覆盖现有生产签名密钥。常规 Android 流程测试：

```sh
./gradlew assembleDebugAndroidTest
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument -w studio.nkg.tv.test/studio.nkg.tv.FlowInstrumentation
```

GPU WIP 单独生成本机二进制后测试；SPIR-V 与 JNI 库不入 Git：

```sh
./tools/build-gpu-prototype.sh
./gradlew assembleDebug assembleDebugAndroidTest -PtestRunner=studio.nkg.tv.GpuDecodeInstrumentation
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument -w studio.nkg.tv.test/studio.nkg.tv.GpuDecodeInstrumentation
```

该测试允许软件 Vulkan 来验证正确性，同时检查硬件要求会拒绝软件设备；没有音频、屏幕呈现或播放 FPS 验收。最后一次云端已完成两个 APK 编译，运行阶段未完成，不能声明通过。

Windows 构建（Linux 上）：

```sh
mkdir -p artifacts
cargo build --manifest-path core/Cargo.toml --release --target wasm32-unknown-unknown
cp core/target/wasm32-unknown-unknown/release/nkg_core.wasm desktop/ui/core.wasm
cd desktop
go test ./...
GOOS=windows GOARCH=amd64 CGO_ENABLED=0 go build -trimpath -ldflags='-H windowsgui -s -w' -o ../artifacts/NKG-TV-0.1.0-preview-windows-x64.exe .
```

实际运行需要 Windows 10/11 和已安装的 WebView2 Runtime；不自动安装或接受微软许可。EXE 未签名。详见 [desktop/README.md](../desktop/README.md)。

## 已验证的范围

基线：19 个 Android JVM 测试，lint 0 错误（16 警告），14 个 Rust 测试及 clippy，29 项 Android 模拟器流程检查，以及截图检查；Go 测试/race 与 Linux 浏览器回归通过。离线处理测试覆盖取消/清理、重启、色彩策略和 AAC priming/时间戳校验。准确结果与局限见 [TEST_REPORT.md](TEST_REPORT.md)、[MILESTONE-5.md](MILESTONE-5.md)。不把基线测试结果扩展为新 GPU 路径已验证。

GPU 软件模拟器：64×36 与 854×480 原始尺寸完整帧输出和 Rust 参考逐字节一致。480p 单次上传/计算/回读 p50 6383.87 ms；720p 等待计算超过 10 秒超时，1080p 未运行。无骁龙真机、真实 GPU 或 Windows 性能证据。

## 来源与下一步

默认订阅 `https://tv.lfzxb.top/sources/feed.json` 在云端 HTTPS CONNECT 阶段被代理返回 403，因此没有获得实际 JSON，无法准确适配其 schema。现有导入仅支持 `nkg-catalog-v1`（HTTPS、2 MiB、2000 条限制）。需在获准访问的环境取得实际内容再适配，不绕过云端访问限制。只使用有权访问的内容，不绕过 DRM。

接续优先项：核实订阅结构；选择可再分发且符合质量/性能目标的模型；完成原始 YUV→GPU 推理→实际呈现与音频时钟链路；接入 Rust 帧统计/视口布局；在目标真机及明确 GPU 的 Windows 机器分别持续测量。GPU 当前仍有 CPU 上传/回读，并非零拷贝。

构建产物、工具链、缓存、签名密钥不推送。此前 Library 上传工具网络失败，没有取得 library_file_id；本次按要求交接 GitHub 源码，不发布新安装包或 Release。
