# 水课帮 (ShuikeBang) — 项目开发规范

> 课堂提问助手 Android 应用。任何贡献者（包括 AI Agent）在修改代码前必须阅读本文档。

## 项目概述

水课帮是一款纯本地、离线运行的 Android 课堂辅助工具。核心能力：麦克风实时收音 → 本地流式语音识别 → 自动检测老师提问 → 振动提醒 + 问题回溯。

**核心约束**：
- 音频/文本全部本地处理，**不上传云端**
- APK 本体尽量小（~20-30MB），语音模型首次启动时下载
- 不保存原始音频文件，只保存识别后的文本

## 技术栈

| 层 | 技术 |
|------|------|
| 语言 | Kotlin (100%) |
| UI | Jetpack Compose + Material3 |
| 架构 | MVVM + Hilt DI |
| 数据库 | Room (SQLite) |
| ASR | sherpa-onnx `OnlineRecognizer` (JitPack: `com.github.k2-fsa:sherpa-onnx:1.13.6`) |
| 模型 | `sherpa-onnx-streaming-zipformer-small-ctc-zh-int8-2025-04-01` (中文流式CTC) |
| 网络 | OkHttp (仅模型下载) |
| CI/CD | GitHub Actions |

## 目录结构约定

```
app/src/main/java/com/star/shuikebang/
├── ShuikebangApp.kt          # 不要动，全局异常捕获在这里
├── MainActivity.kt           # Compose 入口
├── di/                       # Hilt 模块，只放 @Module
├── data/
│   ├── entity/               # Room @Entity，每个实体一个文件
│   ├── db/                   # @Database + @Dao
│   └── repository/           # 数据仓库层
├── domain/
│   ├── asr/
│   │   ├── AsrEngine.kt      # ⭐ 核心：OnlineRecognizer 流式识别
│   │   └── ModelManager.kt   # ⭐ 核心：模型下载管理
│   ├── question/
│   │   └── QuestionDetector.kt  # 提问检测规则
│   └── session/
│       └── SessionManager.kt    # 会话生命周期管理
├── service/
│   └── RecordingService.kt   # 前台服务，音频采集 + ASR 调度
├── util/
│   └── IslandNotificationHelper.kt  # 小米/vivo 灵动岛
└── ui/
    ├── NavHost.kt            # 路由
    ├── home/                 # 首页
    ├── history/              # 历史列表
    ├── detail/               # 会话详情
    └── theme/                # 主题
```

## 命名规范

- **文件名**：PascalCase，如 `AsrEngine.kt`、`ModelManager.kt`
- **类名**：PascalCase
- **函数/变量**：camelCase
- **常量**：UPPER_SNAKE_CASE（`companion object` 内）
- **资源文件**：snake_case（`strings.xml`、`themes.xml`）
- **包名**：`com.star.shuikebang.*`

## 核心模块说明

### AsrEngine（流式识别引擎）

基于 `sherpa-onnx` 的 `OnlineRecognizer`，真流式识别：
```
AudioRecord → acceptWaveform() → decode() → getResult() → isEndpoint() → reset
```
- 音频格式：16kHz, 16bit, Mono PCM
- 每次读取 ~300ms 音频（4800 samples）
- `isFinal=true` 表示模型检测到句尾，该句识别完成
- **不要引入 VAD**，模型自带 endpoint detection

### ModelManager（模型管理）

下载 `sherpa-onnx-streaming-zipformer-small-ctc-zh-int8-2025-04-01.tar.bz2`，解压到应用私有目录。
- 只下载一次，后续启动检查文件是否存在
- 下载进度通过 `StateFlow<DownloadState>` 推给 UI
- 模型文件：`model.int8.onnx` + `tokens.txt`（~15MB）

### QuestionDetector（提问检测）

纯规则化，**不使用 AI/大模型**。检测逻辑：
1. 强模式：问号结尾、"请XX回答"、"哪位同学"、"谁能"、"大家觉得"、"对不对/是不是"
2. 弱模式：仅"什么是XX"、"为什么XX"开头 + 问号
3. 反模式：包含"好的/明白/可以/谢谢"的排除
4. 英文：必须问号结尾

### RecordingService（前台服务）

- 必须是 `Foreground Service`，有常驻通知
- 音频采集在独立线程，ASR 回调在协程
- 检测到提问 → 振动 + 岛通知更新 + 数据库写入
- 需要用户手动关闭电池优化（APP 内引导）

## 开发约定

### 必须遵守

1. **不保存音频二进制**：数据库只存文本、时间戳，不存音频
2. **不引入大模型**：提问检测是规则引擎，不是 LLM
3. **不打包模型进 APK**：模型首次启动下载
4. **不引入网络框架除了 OkHttp**：仅用于模型下载
5. **修改前先读 CLAUDE.md**：本文档是规范源头

### 修改 AsrEngine/ModelManager 时

- 确认 sherpa-onnx API 兼容性（当前 1.13.6）
- 不要删除 `isEndpoint()` 检测逻辑
- 不要改音频采样率（必须 16kHz）
- 模型路径格式：`{modelsDir}/{modelName}/model.int8.onnx`

### 修改 RecordingService 时

- 不要删 WakeLock 逻辑（防系统杀进程）
- 不要删岛通知集成（IslandNotificationHelper）
- `isFinal` 逻辑：只有 final 结果才写数据库，中间结果只给 UI

### 修改 UI 时

- 使用 Material3 主题色，不要自造色值
- 实时文本区域需要自动滚动到底部
- 提问行用高亮背景色区分

## 构建与 CI

- `./gradlew assembleDebug` 本地构建
- Push 到 `main` 分支自动触发 GitHub Actions
- 只打包 `arm64-v8a` 架构（`app/build.gradle.kts` 中 `ndk.abiFilters`）
- ProGuard 不要混淆 sherpa-onnx 的 JNI 类

## 故障排查

### 模型下载失败
- 检查 `INTERNET` 权限（AndroidManifest.xml）
- GitHub Releases 在国内可能慢，考虑镜像或预下载

### 录音闪退
- 检查麦克风权限
- 检查电池优化是否白名单
- 查看 `ShuikebangApp.kt` 写入的崩溃日志文件

### 识别不准
- Zipformer-Small-CTC 是中文优化模型，英文识别能力有限
- 课堂环境噪声大时可能影响识别
- 可调整 `EndpointConfig` 参数影响断句灵敏度
