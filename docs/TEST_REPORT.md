# 阶段验证报告（2026-10-12，非最终验收）

## 已执行

- Android API 35 / AGP 8.7.3 / Gradle 8.9 / JDK 17 调试构建成功，arm64-v8a 与 x86_64 JNI 随 APK 打包。
- 9 项 Java 单元测试通过：中英搜索、空目录、未知格式、非 HTTPS、缺字段、重复 ID、凭据地址、脚本地址、合法地址。
- 4 项 Rust 测试通过；clippy -D warnings、rustfmt 通过。
- Android lint 0 errors，中文硬编码等警告尚存（预览版只支持中文）。
- APK v2 签名验证成功，签名者 Android Debug；不是生产包。
- Linux 主机真实 JNI 调用 Rust 160×90→320×180：p50 6.01 ms，p95 6.57 ms；与 NumPy 参考计算最大字节误差 0。此性能不代表 Android 真机。
- 官方 Real-ESRGAN animevideov3 ×2 / ncnn 对解码视频帧推理成功，主机首次推理约 128 ms。
- 原创程序生成 MP4 在 3 秒处取帧，面积降采样后，Real-ESRGAN PSNR 22.82 dB，最近邻 23.89 dB。**此样本未显示 PSNR 改善，不能声称已证明画质收益**。模型偏向动画，自然影视、主观质量和时域稳定性仍未验证。

## 验证中/未通过最终验收

- 软件模拟器 Android 10 已启动，APK 安装返回 Success；启动命令超时，随后出现系统 UI 无响应弹窗。运行环境为无 KVM 的软件模拟，暂不能证明应用流程稳定或顺滑。没有真机数据。
- 用户订阅 `https://tv.lfzxb.top/sources/feed.json`：云端 CONNECT 403，无响应正文；网页读取也失败。不能推断源站故障。待用户提供 JSON 内容；当前只支持自有 nkg-catalog-v1 目录，不声称已适配指定源。
- 原先 APK UIAutomator 全流程未完成；现已由下述第四里程碑直接 instrumentation 验证替代。
- Android AI 路径是连续 TextureView 帧→ncnn→ImageView 采样预览，160 像素宽、最大约 8 fps、默认关闭、连续慢帧回退原片。尚非全分辨率同步实时增强。
- Windows x64 GUI EXE 已在 Linux 交叉编译成功（Go 1.24.2，CGO=0），PE32+ / GUI / x86-64；无 Authenticode 签名。Windows 原生执行、WebView2 Runtime 兼容和 GPU 性能未验证。iOS 未实现和验证。
- 不满足“首个安卓和 PC 都美观、动画顺滑、性能优秀、稳定可用、直接解析实际订阅”的最终验收。此提交仅为可审查里程碑。

## 第二里程碑新增证据

- Windows 内容核心 Go 测试通过：有效目录、未知格式、重复 ID、HTTPS/凭据/脚本地址校验。
- 桌面 UI 在 Linux 系统 Chromium 自动化验证通过：搜索无结果、详情、收藏与刷新后持久化、真实离线 MP4 播放、Rust WASM 连续帧推理、观看历史、错误格式导入、空目录导入；无页面脚本异常。
- 浏览器 Rust WASM 推理状态曾显示 25.2 ms/帧（160×90 输入）；这不是 Windows 原生/持续性能基准。
- Windows 只集成 TinySR2 实验模型，尚未达到 Android 的 ncnn 动画模型功能；明确默认关闭且披露实验限制。
- 桌面 UI 使用原生 WebView2 窗口、嵌入式本机资源、仅回环地址临时服务；独立 EXE，不是远程网页快捷方式。
- Android 后续截图确认主页正确显示并可读取，自动化已通过主页、详情、收藏切换断言；播放页等待 UIAutomator 空闲时 25 秒超时，完整播放/历史用例未通过。不能把系统卡顿或 UIAutomator 超时直接当作应用成功或失败。
- 指定订阅仍未获得 JSON 样本，在线解析验证未完成，不能作为最终版交付。

## 补充实际播放证据

软件模拟器截图显示离线 MP4 已播放到 00:00:11.958 并显示「播放结束」；MediaPlayer dumpsys 记录 H.264 视频与 AAC 音频解码器、媒体时钟和处理帧消息。这证明基础离线播放运行过，但 UIAutomator 在活动视频页面等待空闲超时，完整自动化仍未通过。模拟器无音频输出，不能声称听感验证或真机流畅度。

Library 按规定使用最新 prepared-upload helper 尝试保存四个阶段产物，但在 tools/list 服务发现阶段报 network；在获准的云端权限重试后相同失败，尚未调用 prepare/finalize，无 Library 文件 ID 或版本，不能声称上传成功。阶段文件保存在本仓库 artifacts/，原仓库源码已推送；没有发布 Release。

## 第四里程碑：Android 运行验证更新

直接 instrumentation 已通过 20 项检查，覆盖搜索空结果、详情、收藏、播放/暂停/恢复、两种跳转、完成/重播、后台准备不自动播放、续播、返回、历史和来源失败提示。30 次性能采样仅体现无 KVM 软件模拟环境：媒体时钟约为墙钟 0.57 倍，不代表真机流畅。

新增离线完整帧 Real-ESRGAN 2× 转码：4/4 帧从 64×36 到 128×72，视频 PTS 全部保持，23 个 AAC 包的内容及 PTS 全部保持；耗时 205.385 秒。详见 MILESTONE-4.md 和 android-enhancer.json。这与原先 160 像素采样预览是两个独立功能；仍不声称全分辨率实时播放或画质改善。缺少真机、长片和 Windows 原生验证。

## 最新稳定性/同步/布局回归

详见 MILESTONE-5.md：16 帧运动与切镜片段、取消/失败后重试、应用进程 PSS 采样、29 项 Android 流程、真实异步 onError、Activity 重建收藏恢复，以及桌面 7 项回归和真实渲染截图。AAC 编码延迟在外部复核中发现并修复；不能只比较 MediaExtractor 时间戳就声称音画同步。最新独立 FFprobe/FFmpeg 包时间戳、音频 payload、4 秒时长及 PCM 验证通过。实时全视频超分所需架构和环境详见 REALTIME-ARCHITECTURE.md。
