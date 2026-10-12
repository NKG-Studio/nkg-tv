# NKG TV — Android preview

在空仓库上独立实现的 Android 影视播放器预览。目标是 Android / iOS / PC；**当前已有 Android APK 和 Windows x64 EXE 预览，Windows 原生运行未验证；iOS 尚未实现或构建**。Rust 共享核心及 C ABI 可移植，并不等于三端已完成。

**最新验收目标：Android 和 Windows 播放中真实 AI 超分持续 ≥30 增强 FPS。当前版本未达标；离线转码、抽帧、原画回退和重复帧都不能替代该目标。** Android 目标至少为骁龙 8 Gen 3；按原视频分辨率输入，禁止统一降至 720p；PC GPU 尚待确认，输出须等比适配实际播放器内容区域。云端 Vulkan 探针仅发现 SwiftShader CPU 软件设备。GPU 管线、真实计数接入和目标硬件实测仍未完成，详见 `docs/REALTIME-ARCHITECTURE.md`。

## 当前可用

- 原生中文界面：本地目录搜索、详情、播放/暂停/进度、收藏、历史、续播、返回导航。
- HTTPS 目录订阅刷新、更换地址、缓存、严格格式校验、超时/HTTP/空结果提示；手动粘贴目录 JSON。
- 离线原创 12 秒 MP4 测试片，系统 MediaPlayer 解码（MP4/HLS 支持取决于设备，不支持 DRM 绕过）。
- 内置官方 **Real-ESRGAN animevideov3 ×2**，ncnn CPU 推理，默认关闭。连续视频帧通过 TextureView 读回、推理、ImageView 呈现，保留原片音频。
- **超分是低分辨率动画采样预览**：宽度 160 像素、最高约 8 fps，350 ms 慢帧连续 3 次自动关闭。不是全分辨率实时电影修复；抽帧、读回、时延可能降低观看体验。真人电影效果未验证。无 HDR/DRM/时域重建支持。
- 新增独立的「逐帧生成 AI 2× 副本」：原分辨率逐帧推理、2× AVC 编码、保留视频时间戳与压缩音轨，副本加入本机历史。当前仅支持偶数尺寸、最高 320×180、最长一小时的单音轨 BT.601/BT.709 SDR 渐进式 MP4（拒绝多音轨、BT.2020 和 HDR）；离线处理，可取消，无实时性能承诺。模拟器已验证 4 帧完整转码，尚无长片稳定性数据。
- 额外 Rust TinySR2 自训模型用于可复现核心/JNI 测试，**不作为成熟模型的替代或默认视频增强**。

## 默认订阅的重要限制

用户指定 `https://tv.lfzxb.top/sources/feed.json` 已保存为默认地址。云端访问被出口代理以 CONNECT 403 拒绝，未读取 JSON，所以**尚未完成该来源的实际格式适配**。应用不猜测其格式、不执行订阅代码、不会默认引入不明影视源。

当前明确支持项目自有的 `nkg-catalog-v1` 格式，不声称它就是用户订阅格式：

```json
{
  "format": "nkg-catalog-v1",
  "videos": [
    {"id": "my-video-1", "title": "我的影片", "description": "内容说明", "source": "我的媒体库", "url": "https://your-domain.example/video.mp4"}
  ]
}
```

示例域名是格式占位，不是可播放内容。须换成有权访问的真实 HTTPS MP4/HLS。响应上限 2 MiB、2000 部影片，拒绝重复 ID、非 HTTPS、URL 内凭据；失败保留上次成功目录。目录是影片列表，不是采集站/TVBox/LibreTV 插件执行器。网络搜索和剧集接口适配仍待真实订阅样本。

## 构建

全部开发和构建在云端完成；无需访问用户电脑。工具：JDK 17、Gradle 8.9、AGP 8.7.3、Android SDK 35、Build Tools 35.0.0、NDK 27.2.12479018、Rust 1.85.1。先自行获得适用工具许可授权；本项目脚本不自动接受 SDK 许可。

```sh
# 设置为已安装且获得授权的路径
export ANDROID_HOME=/path/to/android-sdk
export ANDROID_NDK_HOME="$ANDROID_HOME/ndk/27.2.12479018"
export JAVA_HOME=/path/to/jdk17
rustup target add aarch64-linux-android x86_64-linux-android
./tools/fetch-ncnn.sh
export NCNN_ANDROID_DIR="$PWD/.tools/ncnn-20250503-android"
./tools/build-native.sh
./gradlew testDebugUnitTest lintDebug assembleDebug
```

APK：`app/build/outputs/apk/debug/app-debug.apk`，包名 `studio.nkg.tv`，Android 8.0+，arm64-v8a / x86_64，**调试签名**。无 32 位 ARM 支持，不是生产签名或商店发布包。不要覆盖已有生产密钥。首次安装需允许对应文件应用安装 APK。

已提交模型和合法测试 MP4，不需要在构建时重新训练或下载视频。可复现训练：`OPENBLAS_NUM_THREADS=1 python3 tools/train_sr.py`（NumPy 2.3.5）；原片生成：`tools/make-demo.sh`（FFmpeg）。训练后运行 `cargo fmt --manifest-path core/Cargo.toml`。

## 结构与验证

- `app/` Android 原生 UI、内容/存储/网络、视频生命周期。
- `core/` Rust 推理、C ABI、JNI 和 ncnn C++ 适配；无 Rust 第三方 crate。
- `tools/` 构建/训练/原片生成。
- `tests/` 主机 JNI/模型烟测；Android JVM 测试位于 `app/src/test/`。
- `desktop/` Windows 原生 WebView2 窗口、内容访问与本机状态、Rust WASM 实验推理。
- `docs/TEST_REPORT.md` 实际验证结果与未验证事项；最新稳定性、色彩/音轨策略与截图见 `docs/MILESTONE-5.md`；实时架构缺口见 `docs/REALTIME-ARCHITECTURE.md`。
- `THIRD_PARTY.md` 官方来源、许可证和模型 SHA256。

Rust 检查：`cargo test --manifest-path core/Cargo.toml`、`cargo clippy --manifest-path core/Cargo.toml --all-targets -- -D warnings`、`cargo fmt --manifest-path core/Cargo.toml --check`。

## 隐私、来源与参考

无账号、遥测或分析 SDK。历史、收藏和订阅仅存本机；服务器收到正常媒体/订阅请求和网络地址。只使用有权访问的内容，不代理、破解或绕过 DRM。来源 JSON 始终作为数据处理。

产品流程参考 [LibreSpark/LibreTV](https://github.com/LibreSpark/LibreTV)（AGPL-3.0）；本仓库未复制其代码、资源或第三方源列表。第三方模型/运行库保留原许可，见 `THIRD_PARTY.md`。原创代码及 TinySR2 采用 MIT。
