# UpWard

UpWard 是一个 Android 原生应用，用于按项目记录对话、沉淀每日进展，并通过可配置的 AI 接口辅助整理项目状态。

## 主要功能

- 项目管理：创建、编辑和查看不同项目。
- 项目对话：围绕单个项目保存用户消息和 AI 回复。
- 每日记录：手动维护项目日报，也可以基于当天对话生成总结。
- AI 接口配置：配置 OpenAI 兼容接口地址、API Key、模型和温度参数。
- 本地存储：使用 Room 保存项目、消息、日报和接口配置。

## 技术栈

- Kotlin
- Jetpack Compose
- Material 3
- Navigation Compose
- Room
- OkHttp
- Gradle Kotlin DSL

## 开发环境

- Android Studio
- JDK 17 或 Android Studio 内置 JDK
- Android Gradle Plugin 8.6.0
- compileSdk 34
- minSdk 31

## 本地运行

1. 克隆项目并使用 Android Studio 打开根目录。
2. 等待 Gradle 同步完成。
3. 连接 Android 设备或启动模拟器。
4. 运行 `app` 配置。

也可以在命令行执行：

```bash
./gradlew assembleDebug
```

Windows 环境可执行：

```powershell
.\gradlew.bat assembleDebug
```

## AI 接口说明

应用请求路径为：

```text
{baseUrl}/chat/completions
```

接口需兼容 OpenAI Chat Completions 响应格式。配置 API Key 后，应用会通过 `Authorization: Bearer <apiKey>` 请求接口；如果 API Key 留空，则不添加该请求头。

## 项目结构

```text
app/src/main/java/com/xdl/upward
├── data        # Room 数据表、DAO、Repository 和远程请求
├── domain      # AI 消息构建等领域逻辑
├── ui          # Compose 页面、导航和主题
└── MainActivity.kt
```

## 版本控制说明

仓库会提交源码、Gradle Wrapper 和必要的项目配置；本地构建产物、IDE 临时文件、`local.properties` 等环境相关文件已在 `.gitignore` 中排除。
