# AGENTS.md

本文件给在本仓库中工作的编码代理（以及人）使用。仓库是 Navic 的 fork：Android 端 Subsonic 音乐播放器，
Kotlin + Jetpack Compose。当前分支的主线工作是歌词显示。

## 1. 硬性约定

- 每个新建的 Kotlin 文件都必须保留许可证头（与相邻文件一致）：

  ```
  /*
   * Navic, a Subsonic music streaming app for Android
   * Copyright (c) 2026 paige
   * SPDX-License-Identifier: GPL-3.0-only
   */
  ```

- 上游代码不做格式化、重命名、批量重构。改动只落在本次任务需要的位置；上游文件风格偏差不修。
- 代码标识符、注释、KDoc 用英文；提交信息、与用户的交流用中文。
- 提交使用中文约定式提交：`type(scope): 描述`，一次改动一个提交，正文列要点。
  常用 type：`feat` `fix` `refactor` `style` `chore` `docs` `build` `test`。
- 禁止伪造验证结果。构建跑不了就说跑不了，并说明已做的替代验证。

## 2. 构建

- 多模块工程：`:app` + `core/{common,database,designsystem,icons,model,preferences,server,widgets}`
  + `build-logic`。`composeApp/` 只剩一个资源文件，不属于任何模块，不要往里放代码。
- 两个 submodule 都在 `vendor/` 下（`vendor/androidx-media` = `Nightdavisao/android-media3`，
  `vendor/subsonic-kotlin`），`settings.gradle.kts` 用 `includeMedia3` + `includeBuild` 接入；
  缺了它们配置阶段就失败：先 `git submodule update --init --recursive`。
- 工具链：Gradle wrapper（9.9）、JDK 21（`jvmToolchain(21)`）、AGP 9.3.3、Kotlin 2.4.20、
  compileSdk 37.2、minSdk 24。依赖走腾讯/阿里云镜像 + Google + Maven Central，`dev.zt64.subsonic` 走 Central Portal Snapshots。
- 常用命令：

  ```bash
  ./gradlew :app:compileDebugKotlin     # 改动后最小验证
  ./gradlew :app:assembleDebug          # 出包
  ```

- 没有配置 ktlint / detekt 任务，风格检查用 ktlint CLI 手动跑（见第 4 节）。

## 3. 代码组织

```
app/src/main/kotlin/paige/navic/
  data/       Room3 库、DAO、实体、mapper
  domain/     模型、repository、parser、manager（偏好、播放、下载…）
  ui/         screen / component / navigation / theme
  playback/   PlayerRepository（播放器门面）、exoplayer/ 实现
  preferences/ PreferenceManager 等偏好（以 core/preferences 模块为源）
  di/         Koin 模块（AppModule / RepositoryModule / ManagerModule / ViewModelModule / Locals）
  util/       通用扩展
```

- 领域模型在 `core/model`，数据库在 `core/database`，主题/形状在 `core/designsystem`——改模型要改对应模块。
- 播放器统一经 `paige.navic.playback.PlayerRepository`（不要再用已删除的 `MediaPlayerViewModel`）。
- DI 一律 Koin；页面级的 Koin 注入放在 screen 层，叶子 composable 只收参数与回调（状态提升）。
- 主题是 Material3 Expressive（`MaterialExpressiveTheme`），形状走 `ContinuousRoundedRectangle`；
  `MotionScheme` 取自偏好 `animationStyle`。
- 底部弹出的页面统一用 `SheetScaffold` + `LocalSheetState`；返回栈是自定义的 `LocalNavStack`（navigation3）。
- Compose 依赖是 androidx 1.12.x，第三方 KMP 库（如 Compose Multiplatform 版）会通过版本对齐落到 androidx 上。

## 4. 代码风格

- `.editorconfig` 是唯一事实来源：tab 缩进、行宽 100、`ktlint_code_style = android_studio`
  （即 IntelliJ/Android Studio 默认风格），并放开了 Compose 组合函数的大驼峰命名。
- 提交前对本轮改动文件跑一次 ktlint，要求零告警；不要求一次性清理历史告警：

  ```bash
  java -jar ktlint-cli-<version>-all.jar "app/src/main/kotlin/paige/navic/**/*.kt"
  ```

- 除 `.editorconfig` 已覆盖的规则外，跟随 Kotlin 官方编码约定与 Compose API 指南：
  显式 import（无通配符）、可选参数里 `modifier` 排在最前、组合函数以名词命名、KDoc 解释“为什么”。
- 修改歌词相关代码时保持状态提升：`ui/screen/lyrics/component/*` 里不直接 `koinInject`。

## 5. 歌词（当前主线）

数据流：`LyricsRepository` → `LyricsContentParser`（LRC / JSON / youly）→ `LyricsResult` →
`ui/screen/lyrics/component/Content.kt` → `AppleMusicLyrics`。

- 渲染完全交给 `com.mocharealm.accompanist:lyrics-ui`（Apache-2.0）。不要另写逐字遮罩、模糊、跟随滚动；
  需要调外观就改 `AppleMusicLyrics.kt` 的参数。
- `SyncedLyricsMapping.kt` 的不变量：音节内容拼接必须逐字符等于原行文本（词间空白挂到后一个词前缀），
  否则渲染会错位；对不上就退化成 `SyncedLine` 整行扫过。行尾顺序：显式 duration → 末词结束 →
  下一行开始 → 歌曲时长 → +4s 兜底。
- 播放器进度只有 200ms 粒度，逐字点亮必须经 `rememberPlaybackPosition` 插值，不要直接用 `progress`。
- 歌词页三种形态：卡拉 OK 视图（有逐行时间且开启自动滚动）、选择分享列表、普通列表（无时间戳或关闭自动滚动）。
- 目标观感、参数映射、已知限制写在 `docs/apple-music-lyrics.md`，改外观参数时同步更新该文档。

## 6. 环境受限时的验证

如果当前环境没有 JDK / Android SDK，或 submodule 拉不到（例如 GitHub 不可达），不要假装跑过 Gradle。
可行的替代验证：

1. 纯逻辑：把待验证的映射代码连同它依赖的真实模型（如 `lyrics-core` 的 commonMain 源码）用
   `kotlin-compiler-embeddable` 编译成 JVM 产物，写 `main()` 断言后直接运行。
2. Compose 代码：同一套编译器加 `kotlin-compose-compiler-plugin-embeddable`，classpath 用真实 jar
   （android.jar、compose runtime/ui/foundation/material3、kotlinx-collections-immutable），
   可对单个文件做类型与组合规则检查。
3. 报告里明确区分“已执行验证”与“未执行验证”，并给出下一步命令。
