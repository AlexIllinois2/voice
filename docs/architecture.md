# Architecture

## Overview

Voice is built on a **modular, layered architecture**. The design separates infrastructure, shared domain logic, and user-facing features.
This modularization improves build times, testability, and maintainability, while keeping feature ownership clear.

At a high level:

* **Infrastructure modules** provide the app entry point, build tooling, and navigation.
* **Core modules** encapsulate reusable domain and service logic (playback, scanning, data, logging, etc.).
* **Feature modules** implement user-facing screens, composed from core logic and UI components.
* **Feature flags** are defined per-feature and implemented via `:core:featureflag`, sourced through remote config.

## Layers

### Infrastructure

* `:app` – Main application entry point, dependency injection setup
* `:navigation` – Navigation framework abstractions and route definitions
* `:plugins` – Gradle build plugins for consistency and convention
* `:scripts` – Build and utility scripts

### Core (Shared Logic)

Core modules provide the underlying services and abstractions:

* **UI & Theming**

  * `:core:ui` – Shared UI components, typography, colors, Material 3 theming

* **Data & Storage**

  * `:core:data:api` – Interfaces for repositories and data sources
  * `:core:data:impl` – Implementations for Room, network, or file access
  * `:core:documentfile` – File system abstractions

* **Playback & Media**

  * `:core:playback` – Audio playback logic using a Media3 `ExoPlayer`, with `VoicePlayer` adding audiobook semantics and a Media3 session for system integration. Zip chapters are served from an in-memory cache, and upcoming chapters are preloaded (zip entries extracted to memory, transcodable formats converted in the background) so chapter transitions stay seamless
  * `:core:zip` – Zip archive access (`ZipUriCodec`) and the in-memory playback cache (`ZipPlaybackCache`)
  * `:core:transcode` – Lazy ffmpeg (`ffmpeg-kit`) transcoding of formats that the player does not support natively (WMA/ASF) to m4a. Results are cached by uri hash with an LRU cleanup that keeps only the most recently played chapters
  * `:core:sleeptimer:api` & `:core:sleeptimer:impl` – Sleep timer contracts and implementation

* **Utility Modules**

  * `:core:scanner` – File scanning and metadata extraction
  * `:core:search` – Search logic
  * `:core:strings` – Localized string resources

* **Cross-Cutting Concerns**

  * Logging: `:core:logging:api`, `:core:logging:debug`, `:core:logging:inapp`
  * Feature Flags: `:core:featureflag` – Feature flag abstractions backed by remote config
  * Remote Config: `:core:remoteconfig:api`, `:core:remoteconfig:noop`

### Features

Feature modules are screen- or flow-based. Each module owns its UI (Compose) and presentation logic, while delegating to `:core` modules for
data and services:

* `:features:playbackScreen` – Main playback interface
* `:features:bookOverview` – Library / book list
* `:features:sleepTimer` – Sleep timer control UI
* `:features:settings` – App settings
* `:features:folderPicker` – Folder selection flow
* `:features:cover` – Cover art management
* `:features:onboarding` – First-time user flow
* `:features:bookmark` – Bookmark management
* `:features:widget` – Homescreen widget support
* `:features:logViewer` – In-app log viewer
* `:features:review:noop` – No-op app review prompts
* `:features:support:api` & `:features:support:free` – Support/back-end abstraction

## Dependency Flow

* Features depend **only on `:core` and infrastructure abstractions**.
* `:core` modules depend on each other as needed, but never on features.
* Infrastructure modules (`:app`, `:navigation`) wire everything together at runtime.

This ensures **unidirectional dependency flow**:

```
Infrastructure → Core → Features
```

## Diagram

````mermaid
flowchart LR
    subgraph Infrastructure
        app(":app")
        navigation(":navigation")
    end

    subgraph Core
        core(":core")
    end

    subgraph Features
        features(":features")
    end

    app --> navigation
    app --> features
    features --> core
    features --> navigation
````

## Tech Decisions

* **Compose (UI)** – Declarative UI with Material 3 for consistency and accessibility
* **Metro (DI)** – Lightweight dependency injection across modules
* **Navigation3** – Type-safe, modular navigation
* **Media3 ExoPlayer** – Audio playback engine; zip chapters are served from an in-memory cache (`ZipEntryDataSource` + `ZipPlaybackCache`), and Media3 also powers the session integration and scan-time metadata parsing
* **ffmpeg-kit** – Lazy transcoding of unsupported formats (WMA/ASF) to m4a on first play, cached by uri hash with an LRU cleanup that keeps only the most recent chapters; `TranscodePreloader` converts the next chapter in the background while the current one plays
* **Room** – Persistent storage
* **Kotlin Serialization** – JSON parsing and object serialization
* **Coil** – Efficient image loading

## Module Lifecycle

1. **Add new functionality as a feature module.**
2. **Extract reusable logic into `:core` modules** once multiple features need it.
3. **Keep infrastructure minimal** — mainly for wiring and build configuration.
