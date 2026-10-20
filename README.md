# Voice

[![CI](https://github.com/PaulWoitaschek/Voice/actions/workflows/ci.yml/badge.svg?branch=main)](https://github.com/PaulWoitaschek/Voice/actions/workflows/ci.yml)
[![Translation status](https://hosted.weblate.org/widgets/voice/-/svg-badge.svg)](https://hosted.weblate.org/engage/voice/)
[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](https://voice.woitaschek.de/license/)

**A minimalistic, user-focused audiobook player for Android, built for reliability and simplicity.**

Voice turns your own audiobook files into a calm, focused listening library. It remembers where you stopped, keeps playback controls close, and includes the audiobook-specific details that generic music players miss: chapter navigation, sleep timer, bookmarks, playback speed, silence skipping, and auto-rewind after pauses.

<p align="center">
  <img alt="Voice library view" width="30%" src="fastlane/metadata/android/en-US/images/phoneScreenshots/1_en-US.png" />
  <img alt="Voice player view" width="30%" src="fastlane/metadata/android/en-US/images/phoneScreenshots/2_en-US.png" />
  <img alt="Voice sleep timer" width="30%" src="fastlane/metadata/android/en-US/images/phoneScreenshots/3_en-US.png" />
</p>

## 本地修改（Fork Changes）

这是 [PaulWoitaschek/Voice](https://github.com/PaulWoitaschek/Voice) 的个人分支，在原版基础上增加了以下功能：

- **ZIP 直接播放：** 把整个 ZIP 压缩包（含加密 ZIP，支持密码记忆）当作一本有声书添加并直接播放，无需解压。压缩包内容流式读取并预加载缓存，损坏的压缩包解析尝试会被记录，避免反复重试。
- **HiFi 模式：** 设置中可开启 32-bit 浮点音频输出，保留高分辨率音源的原始位深。
- **章节后台补齐：** 初始扫描先尽快把书列入书架，时长与章节信息随后台解析逐步补齐；扫描过程以前台服务通知显示进度。
- **解析日志：** 应用内可查看最近扫描与解析产生的错误日志，并支持导出保存。
- **更多格式与排序：** 支持 ADTS/AAC（.aac）文件的时长读取；文件名与章节采用更精确的自然排序。
- **向下兼容到 Android 6.0：** minSdk 由 28 降至 23（借助 core library desugaring），并补充了中文文案。

## Why Voice?

- **Made for your files:** Add a folder once and Voice organizes your local M4B, MP3, M4A, OGG, OGA, and OPUS audiobooks.
- **Built for listening:** Resume positions, bookmarks, sleep timer fade-out, volume boost, speed control, and Android Auto support are part of the core experience.
- **Private and uncluttered:** No account, no ads, no forced cloud sync. Your library stays on your device.

## Download

<a href="https://play.google.com/store/apps/details?id=de.ph1b.audiobook">
  <img alt="Get it on Google Play"
       height="70"
       src="https://play.google.com/intl/en_us/badges/images/generic/en_badge_web_generic.png" />
</a>
<a href="https://f-droid.org/packages/de.ph1b.audiobook/">
  <img alt="Get it on F-Droid"
       height="70"
       src="https://f-droid.org/badge/get-it-on.png" />
</a>

## Learn more

The [documentation website](https://voice.woitaschek.de) covers features, the philosophy behind the app, how to organize your library, and the FAQ.

## Contributing

Contributions are very welcome: code, translations, bug reports, or ideas. Start with [Development](https://voice.woitaschek.de/development/) and [Architecture](https://voice.woitaschek.de/architecture/), or help translate Voice on [Weblate](https://hosted.weblate.org/engage/voice/).

## Support Voice

Voice is free and built in spare time. If it has been useful to you, consider chipping in. One-time or monthly, no sign-up required:

<a href="https://ko-fi.com/paul_voice">
  <img alt="Support on Ko-fi"
       height="50"
       src="https://storage.ko-fi.com/cdn/brandasset/v2/support_me_on_kofi_dark.png" />
</a>

You can also [sponsor on GitHub](https://github.com/sponsors/PaulWoitaschek) if you prefer.

## License

Voice is licensed under [GNU GPLv3](https://voice.woitaschek.de/license/). By contributing, you agree to license your code under the same terms.
