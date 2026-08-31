# 水课帮 ShuikeBang

> 大学生课堂提问助手 — 走神时快速回溯老师的问题

[![Build APK](https://github.com/STAR-10086/class-help/actions/workflows/build.yml/badge.svg)](https://github.com/STAR-10086/class-help/actions/workflows/build.yml)
[![License](https://img.shields.io/badge/license-MIT-blue.svg)](LICENSE)
[![API](https://img.shields.io/badge/API-26%2B-brightgreen.svg)](https://developer.android.com/about/versions/oreo)

---

## 产品定位

课堂上走神了？老师刚才是不是提了个问题？

**水课帮** 通过麦克风实时收音 + 本地离线语音识别，自动检测老师的提问句子，走神时快速回溯问题。

- 🔒 **隐私优先** — 音频不上传云端，全部设备本地运算
- 📱 **离线运行** — 首次启动下载语音模型后，完全无需联网
- ⚡ **极小体积** — APK本体仅几MB，模型动态下发

## 功能特性

### 核心功能

| 功能 | 说明 |
|------|------|
| 🎙️ 实时录音识别 | 一键开始/停止，麦克风持续收音，离线实时流式语音转文字 |
| 🔍 提问自动检测 | 疑问句、点名提问句式自动识别，振动+弹窗提醒 |
| ❓ 问题回溯 | 捕获完整提问句子，高亮展示，单独沉淀到提问列表 |
| 📝 课堂记录管理 | 完整转录文本展示，一键复制，历史会话浏览 |
| 📳 提问振动提醒 | 检测到提问时设备振动，走神也能感知 |

### 系统适配

| 平台 | 适配 |
|------|------|
| 🏝️ 小米 HyperOS | 超级岛通知（大岛显示录音状态+提问数量） |
| 🏝️ vivo OriginOS | 原子岛通知（左岛录音状态，右岛提问数） |
| 📱 通用 Android | 标准前台服务通知栏 |

## 技术架构

```
┌─────────────────────────────────────────────────┐
│                    UI Layer                      │
│  Jetpack Compose + Material3 + Navigation       │
├─────────────────────────────────────────────────┤
│                 ViewModel Layer                  │
│  HomeVM / HistoryVM / SessionDetailVM           │
├─────────────────────────────────────────────────┤
│                 Service Layer                    │
│  RecordingService (Foreground Service)          │
│  ├── AudioRecord (16kHz PCM)                    │
│  ├── AsrEngine (OnlineRecognizer 流式识别)      │
│  ├── QuestionDetector (规则NLP)                  │
│  └── IslandNotificationHelper (灵动岛)          │
├─────────────────────────────────────────────────┤
│                  Data Layer                      │
│  Room (SQLite) + SessionRepository              │
├─────────────────────────────────────────────────┤
│                Domain Layer                      │
│  ModelManager (模型下载) + SessionManager        │
└─────────────────────────────────────────────────┘
```

### 技术栈

| 组件 | 技术 | 说明 |
|------|------|------|
| 语言 | Kotlin | 100% Kotlin |
| UI | Jetpack Compose + Material3 | 声明式UI |
| 架构 | MVVM + Hilt | 依赖注入 |
| 数据库 | Room (SQLite) | 会话/文本/提问存储 |
| ASR引擎 | sherpa-onnx 1.13.6 | JitPack依赖 |
| 语音模型 | Zipformer-Small-CTC-INT8 | 中文流式，真流式识别 |
| 网络 | OkHttp | 仅模型下载 |
| CI/CD | GitHub Actions | push自动编译 |

### ASR管线（真流式）

```
麦克风 (16kHz, 16bit, Mono)
    ↓ 每300ms读取 (~4800 samples)
AudioRecord.read()
    ↓ Short → Float
OnlineStream.acceptWaveform()
    ↓ 流式送入 Zipformer-Small-CTC-INT8
OnlineRecognizer.decode()
    ↓ 实时识别
getResult() → text
    ↓ isEndpoint() 检测句尾
    ↓ 自动 reset stream 开始下一句
QuestionDetector.detect()
    ↓ 是否提问?
振动 + 高亮 + 数据库写入
```

## 快速开始

### 环境要求

- Android Studio Hedgehog+
- JDK 17
- Android SDK 35

### 构建

```bash
# Debug APK
./gradlew assembleDebug

# Release APK
./gradlew assembleRelease
```

### 首次运行

1. 安装APK后打开APP
2. 点击录制按钮，授予麦克风权限
3. 首次使用会自动下载语音模型 (~15MB)
4. 模型就绪后开始识别

## 项目结构

```
app/src/main/java/com/star/shuikebang/
├── ShuikebangApp.kt              # Application (Hilt入口 + 全局异常捕获)
├── MainActivity.kt               # Compose入口
├── di/AppModule.kt               # Room数据库注入
├── data/
│   ├── entity/                   # Session, TranscriptLine, Question
│   ├── db/                       # DAO + AppDatabase
│   └── repository/               # SessionRepository
├── domain/
│   ├── asr/
│   │   ├── AsrEngine.kt          # OnlineRecognizer 流式识别引擎
│   │   └── ModelManager.kt       # 模型下载管理
│   ├── question/
│   │   └── QuestionDetector.kt   # 规则化提问检测
│   └── session/
│       └── SessionManager.kt     # 会话管理
├── service/
│   └── RecordingService.kt       # 前台服务(录音+识别+提问检测)
├── util/
│   └── IslandNotificationHelper.kt  # 小米/vivo灵动岛适配
└── ui/
    ├── NavHost.kt                # 路由导航
    ├── home/                     # 首页(录制控制+实时文本)
    ├── history/                  # 历史会话列表
    ├── detail/                   # 会话详情(文本+提问)
    └── theme/                    # Material3主题
```

## 设计决策

### 为什么用 Zipformer-Small-CTC 流式模型？

Zipformer-Small-CTC 是真正的流式模型：音频持续喂入，实时逐字输出，模型自动检测句尾断句。
- **实时性**：边说边出字，延迟极低，不像旧版需要等 VAD 切段后批量识别
- **体积小**：INT8量化后模型仅 ~15MB，远小于 SenseVoice 的 ~200MB
- **中文优化**：专门针对中文场景训练，课堂识别准确率高
- **无需 VAD**：模型自带 endpoint detection，架构更简洁

### 为什么提问检测不用 AI？

引入大模型会导致 APK 体积膨胀，违背"几MB本体"目标。规则化检测(正则+关键词)在课堂提问场景下准确率已经很高，且零开销。

### 为什么模型不打包进 APK？

即使 Zipformer 模型只有 ~15MB，打包进 APK 仍会增加体积。首次启动时从 GitHub Releases 下载到应用私有目录，卸载时自动清除。

## 模型下载

首次使用时自动从以下地址下载：

| 模型 | 大小 | 说明 |
|------|------|------|
| Zipformer-Small-CTC-INT8 | ~15MB | 中文流式语音识别，tar.bz2打包 |

下载源: [GitHub Releases](https://github.com/k2-fsa/sherpa-onnx/releases/tag/asr-models)

## 不做的事

- ❌ 不保存原始录音文件
- ❌ 不导入外部音频做转写
- ❌ 没有 AI 大模型总结/问答/思维导图
- ❌ 没有云同步/账号登录
- ❌ 无广告/社区/分享

## 贡献

参见 [CLAUDE.md](CLAUDE.md) 了解项目规范和开发约定。

## License

MIT
