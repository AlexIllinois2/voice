# Voice

[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](https://voice.woitaschek.de/license/)
[![Latest release](https://img.shields.io/github/v/release/AlexIllinois2/voice?display_name=tag&sort=semver)](https://github.com/AlexIllinois2/voice/releases/latest)

**English** | [简体中文](README.zh-CN.md)

**A minimalistic, user-focused audiobook player for Android, built for reliability and simplicity.**

This repository is a personal fork of [PaulWoitaschek/Voice](https://github.com/PaulWoitaschek/Voice). It keeps the original design and philosophy, and adds the features I use every day — most importantly playing audiobooks straight from ZIP archives.

<p align="center">
  <img alt="Voice library view" width="30%" src="fastlane/metadata/android/en-US/images/phoneScreenshots/1_en-US.png" />
  <img alt="Voice player view" width="30%" src="fastlane/metadata/android/en-US/images/phoneScreenshots/2_en-US.png" />
  <img alt="Voice sleep timer" width="30%" src="fastlane/metadata/android/en-US/images/phoneScreenshots/3_en-US.png" />
</p>

## What this fork adds

- **ZIP playback:** Add a whole ZIP archive — including encrypted ZIPs, with remembered passwords — as a single audiobook and play it without extracting. Entries are read as a stream and preloaded into a cache; archives that fail to parse are recorded so they are not retried on every scan.
- **HiFi mode:** Optional 32-bit float audio output that preserves the original bit depth of high-resolution sources. Restart the app after changing it.
- **Progressive chapter enrichment:** Books show up in the library as soon as scanning finds them, and durations and chapter details are filled in the background. Scan progress is shown in a foreground service notification.
- **Parse log:** Inspect the errors produced by the latest scan and parsing runs inside the app, and export them.
- **More formats, better ordering:** Duration detection for ADTS/AAC (`.aac`) files, and more precise natural sorting for file and chapter names.
- **Android 6.0 and up:** `minSdk` lowered from 28 to 23 (with core library desugaring), plus Chinese translations.

## Core features

- **Made for your files:** Point Voice at a folder once and it organizes your local M4B, MP3, M4A, OGG, OGA, OPUS, AAC, and ZIP audiobooks.
- **Built for listening:** Resume positions, bookmarks, sleep timer with fade-out, volume boost, speed control, silence skipping, auto-rewind after pauses, and Android Auto support.
- **Private and uncluttered:** No account, no ads, no forced cloud sync. Your library stays on your device.

## Download

Prebuilt APKs are attached to the [Releases](https://github.com/AlexIllinois2/voice/releases) page. They are signed with Android's debug key, which is fine for sideloading and testing but not for publishing to a store.

The upstream app is available on [Google Play](https://play.google.com/store/apps/details?id=de.ph1b.audiobook) and [F-Droid](https://f-droid.org/packages/de.ph1b.audiobook/); its [documentation website](https://voice.woitaschek.de) covers features, library organization, and the FAQ.

## Build from source

Requirements: JDK 25 and an Android SDK.

```bash
./gradlew :app:assembleFreeDebug      # free flavor, debug APK
./gradlew :app:assembleFreeRelease    # free flavor, release APK
./gradlew voiceUnitTest               # all unit tests
```

Release builds are configured for the `free` flavor; `assembleRelease` defaults to it as well. Pass `-Pvoice.abiSplits=true` for per-ABI APKs.

## Contributing

This is a personal fork, so upstream-facing fixes and features are best sent to [PaulWoitaschek/Voice](https://github.com/PaulWoitaschek/Voice). Issues and pull requests about the fork-specific features above are welcome here.

## Credits and license

Voice was created by [Paul Woitaschek](https://github.com/PaulWoitaschek) and is licensed under [GNU GPLv3](https://voice.woitaschek.de/license/). This fork keeps the same license; by contributing you agree to license your code under the same terms.
