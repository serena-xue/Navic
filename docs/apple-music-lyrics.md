# Apple Music 风格歌词（iOS 26/27）实现说明

目标：把歌词页做成 Apple Music 在 iOS 26/27 上的观感。本文记录目标效果的具体参数、所采用的
开源组件、参数映射与已知限制。

## 1. 目标效果

对 iOS 26/27 Apple Music 歌词的可观察行为（公开录屏与拆解）归纳为：

| 特征 | 数值 / 行为 |
| --- | --- |
| 字体 | 左侧对齐，粗体，约 30pt，行距约 22pt |
| 逐字点亮 | 当前行按字/词从左到右点亮，未唱到约 35% 白，唱到为纯白，前沿有柔和过渡 |
| 逐词动效 | 拉丁文按词有极小的放大 + 发光；CJK 按整行发光 |
| 非活动行 | 约 25%–35% 白，按距离递增模糊（0 / 2 / 4 / 6pt），越远越淡 |
| 跟随滚动 | 活动行固定在视口约 38% 高度，切换行时用约 0.6s 的缓动跟随 |
| 边缘渐隐 | 视口上下边缘淡出，歌词“浮”在背景上 |
| 背景 | 专辑封面衍生的色场，歌词为白色 |
| 交互 | 点击某行跳到该时间点；长按进入分享 |
| 间奏 | 显示呼吸点（…） |

工程实现要点（公开资料一致）：时间轴来自 LRC / TTML 的毫秒级时间戳，逐字进度是
`(now - wordStart) / (wordEnd - wordStart)` 驱动的遮罩，缩放/淡入用 spring 曲线。

## 2. 采用的组件

`com.mocharealm.accompanist:lyrics-ui`（Apache-2.0）+ `lyrics-core`：

- 解析、模型与渲染分离；渲染器自带按语言的切分策略（Latin / CJK / 阿拉伯 / 印度诸语等）。
- 已实现：逐字遮罩、逐词 scale + glow、CJK 行级 glow、按距离模糊、锚点跟随、边缘渐隐、
  翻译行、间奏呼吸点、长按/点击回调、文本纹理预取。
- 结论：歌词渲染不再自己写，本项目只做数据映射与外观参数。

本仓库原有的自绘卡拉 OK 实现（`component/KaraokeText.kt`）与逐字进度工具
（`util/LyricUtils.kt`）已删除。

## 3. 参数映射

`component/AppleMusicLyrics.kt`：

| 参数 | 取值 | 对应目标特征 |
| --- | --- | --- |
| `normalLineTextStyle` | 30sp / Bold / letterSpacing -0.4sp | 字体 |
| `translationTextStyle` | 18sp / SemiBold | 翻译行 |
| `anchor` | `Fraction(0.38f)` | 活动行位置 |
| `itemSpacing` | 20dp | 行间距 |
| `topFade` | `ToAnchor(16.dp)` | 上边缘渐隐 |
| `bottomFade` | `Fraction(0.35f)` | 下边缘渐隐 |
| `blurDelta` | 3f（“明亮非活动行”开启时 1f） | 距离模糊 |
| `useBlurEffect` | 偏好 `lyricsBlur` | 模糊开关 |
| `showPhonetic` | false | 未提供音译数据 |
| `blendMode` | `SrcOver` | 默认 `Plus` 在浅色主题上会把文字洗白 |

主题相关：`nowPlayingBackgroundStyle == Dynamic` 时歌词页复用 `BlendBackground`
（与播放页同一套封面色场）并改用白色文字，否则用主题 `onBackground`。

## 4. 数据映射

`component/SyncedLyricsMapping.kt`：

- 有逐词时间戳的行 → `KaraokeLine.MainKaraokeLine`（逐字点亮）。
- 只有行时间戳的行 → `SyncedLine`（整行从左到右扫过）。
- 行结束时间取：显式 duration → 最后一个词的结束 → 下一行开始 → 歌曲时长 → +4s 兜底。
- 渲染器把音节首尾相接排布，因此音节内容拼接必须等于原文：词间空白挂到后一个词的前缀上；
  当逐词时间与原文对不上（或只有一个词）时退化为 `SyncedLine`，不做逐字。

## 5. 播放时钟

播放器每 200ms 上报一次进度，直接使用会让逐字点亮变成 5Hz 的台阶。
`component/PlaybackPosition.kt` 把每次上报当作锚点，按帧用墙钟推进，下一次上报再校正，
漂移不超过一个上报周期。

## 6. 主题/偏好映射

| 偏好 | 作用 |
| --- | --- |
| `lyricsBlur` | 非活动行模糊开关 |
| `lyricsBrightInactive` | 模糊强度（3 → 1），因为未激活行透明度由组件内部固定为 0.4 |
| `lyricsAutoscroll` | 关闭时不使用卡拉 OK 视图，退化为普通滚动列表（组件始终跟随播放） |
| `lyricsKeepAlive` | 保持屏幕常亮（不变） |
| `lyricsBeatByBeat` / `lyricsFakeBeatByBeat` | 既有死配置，未接入 |

## 7. 已知限制

- 模糊使用 `RenderEffect`，Android 12（API 31）以下不生效，只有透明度变化。
- 未激活行的透明度由组件内部固定（0.4），无法按行再调。
- 选择分享模式、无时间戳歌词、关闭自动滚动时走普通列表，不带卡拉 OK 动效。
- 歌词来源名只显示在普通列表底部，卡拉 OK 视图没有尾项插槽。

## 8. 验证

本机没有 JDK / Android SDK，且 `androidx-media` 子模块无法拉取（GitHub 不可达），因此没有跑
Gradle。已做的验证：

- 映射逻辑：用 `lyrics-core` 真实模型 + 真实 `kotlinc 2.4.20` 编译并执行 18 项断言（空白重建、
  CJK、时间退化、行尾推导、零时长等），全部通过。
- Compose 代码：用 `kotlin-compiler-embeddable 2.4.20` + `kotlin-compose-compiler-plugin`
  + Compose 1.12.1 / Material3 1.5.0-alpha29 / android-37.2 真实 jar 编译 `Content.kt`、
  `AppleMusicLyrics.kt`、`PlaybackPosition.kt`、`SyncedLyricsMapping.kt`，零错误零警告。
- 代码风格：ktlint 1.8.0（android_studio 风格 + Compose 命名例外）对本轮改动的 5 个文件零告警。

在具备工具链的机器上执行：

```bash
git submodule update --init --recursive
./gradlew :app:compileDebugKotlin
```

真机确认点：逐字点亮是否与歌声同步、模糊/渐隐边缘、切换行时活动行是否稳定在 38% 高度、
长按进入选择、点击跳转后是否恢复跟随。
