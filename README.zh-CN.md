# Voice

[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](https://voice.woitaschek.de/license/)
[![Latest release](https://img.shields.io/github/v/release/AlexIllinois2/voice?display_name=tag&sort=semver)](https://github.com/AlexIllinois2/voice/releases/latest)

[English](README.md) | **简体中文**

**一款极简、专注于收听体验的 Android 有声书播放器，可靠、克制、易用。**

本仓库是 [PaulWoitaschek/Voice](https://github.com/PaulWoitaschek/Voice) 的个人分支，保留原版的设计与理念，并加入了我日常使用所需的功能，最重要的是可以直接播放 ZIP 压缩包中的有声书。

<p align="center">
  <img alt="Voice 书架界面" width="30%" src="fastlane/metadata/android/en-US/images/phoneScreenshots/1_en-US.png" />
  <img alt="Voice 播放界面" width="30%" src="fastlane/metadata/android/en-US/images/phoneScreenshots/2_en-US.png" />
  <img alt="Voice 睡眠定时" width="30%" src="fastlane/metadata/android/en-US/images/phoneScreenshots/3_en-US.png" />
</p>

## 本分支新增功能

- **ZIP 直接播放：** 把整个 ZIP 压缩包（含加密 ZIP，支持密码记忆）当作一本有声书添加并直接播放，无需解压。压缩包内容以流式读取并预加载到缓存；解析失败的压缩包会被记录，避免每次扫描反复重试。
- **HiFi 模式：** 可选开启 32-bit 浮点音频输出，保留高分辨率音源的原始位深。修改后需重启应用生效。
- **章节后台补齐：** 扫描发现书籍后立即加入书架，时长与章节信息随后台解析逐步补齐；扫描进度通过前台服务通知显示。
- **解析日志：** 可在应用内查看最近扫描与解析产生的错误，并支持导出保存。
- **更多格式与排序：** 支持 ADTS/AAC（`.aac`）文件的时长读取，文件名与章节名采用更精确的自然排序。
- **向下兼容到 Android 6.0：** `minSdk` 由 28 降至 23（借助 core library desugaring），并补充了中文文案。

## 核心功能

- **为你的本地文件而生：** 只需指定一次文件夹，Voice 就会整理本地 M4B、MP3、M4A、OGG、OGA、OPUS、AAC 以及 ZIP 有声书。
- **为聆听而设计：** 断点续播、书签、淡出睡眠定时、音量增强、倍速播放、静音跳过、暂停后自动回退，并支持 Android Auto。
- **隐私、无干扰：** 没有账号、没有广告、不强制云同步，书架始终留在你的设备上。

## 下载

预构建 APK 发布在 [Releases](https://github.com/AlexIllinois2/voice/releases) 页面。这些包使用 Android 调试密钥签名，适合侧载与测试，不适合上架应用商店。

原版应用可从 [Google Play](https://play.google.com/store/apps/details?id=de.ph1b.audiobook) 和 [F-Droid](https://f-droid.org/packages/de.ph1b.audiobook/) 获取；功能说明、书架整理与常见问题见[官方文档站](https://voice.woitaschek.de)。

## 从源码构建

环境要求：JDK 25 与 Android SDK。

```bash
./gradlew :app:assembleFreeDebug      # free 渠道的 debug APK
./gradlew :app:assembleFreeRelease    # free 渠道的 release APK
./gradlew voiceUnitTest               # 运行全部单元测试
```

release 构建已按 `free` 渠道配置，`assembleRelease` 默认也使用该渠道。需要按 ABI 拆分时追加 `-Pvoice.abiSplits=true`。

## 参与贡献

这是个人分支，向上游提出的修复与功能建议请提交到 [PaulWoitaschek/Voice](https://github.com/PaulWoitaschek/Voice)；与上方分支专属功能相关的问题和 PR 欢迎提交到本仓库。

## 致谢与许可

Voice 由 [Paul Woitaschek](https://github.com/PaulWoitaschek) 创建，采用 [GNU GPLv3](https://voice.woitaschek.de/license/) 许可。本分支沿用同一许可；参与贡献即表示你同意以相同条款授权你的代码。
