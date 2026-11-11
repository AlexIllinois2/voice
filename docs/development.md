# Development

## Project Setup

To run the project, open it in the latest Version of Android Studio and build the project as usual.
The project has a single build variant without product flavors, so `./gradlew :app:assembleDebug` works out of the box
and does not require any Google services credentials.

By default, there is not enough memory configured for gradle. You can fix this by running.

```sh
scripts/gradle_bootstrap.sh
```

This will configure your global `~/.gradle/gradle.properties` to use more memory, depending on your machine.

Example of the generated properties, check the `gradle_bootstrap.sh` script for exact details.

```properties
# Begin: Gradle JVM bootstrap-generated properties
org.gradle.jvmargs=-Dfile.encoding=UTF-8 -XX:+ExitOnOutOfMemoryError -Xms4g -Xmx16g
kotlin.daemon.jvm.options=-Dfile.encoding=UTF-8 -XX:+ExitOnOutOfMemoryError -Xms4g -Xmx16g
# End: Gradle JVM bootstrap-generated properties
```

## Tests

### Unit tests

To run the unit tests, run the following command:

```sh
./gradlew voiceUnitTest
```

### Instrumentation tests

To run the instrumentation tests, run the following command:

```sh
./gradlew :app:connectedDebugAndroidTest
```

## Developer Menu

The developer menu is hidden by default.

To unlock it:

1. Open `Settings`.
2. Tap the app version entry 13 times.
3. Open the newly visible developer menu from `Settings`.

## Ktlint

Voice uses **Ktlint** to enforce consistent code formatting.

- Check for formatting issues:

```sh
./gradlew lintKotlin
```

- Auto-fix formatting:

```sh
./gradlew formatKotlin
```

- To make commits fail on formatting errors, set up a pre-commit hook:

```sh
echo "./gradlew lintKotlin" > .git/hooks/pre-commit
chmod +x .git/hooks/pre-commit
```

## Releasing

To release a new version, trigger the [Release Workflow](https://github.com/PaulWoitaschek/Voice/actions/workflows/release.yml)

Pushing a `v*` tag resolves the version from the tag, builds debug-signed release APKs
(`./gradlew :app:assembleRelease` with injected `voice.versionName`/`voice.versionCode`), verifies the
signature with `apksigner`, and creates a GitHub Release with the APKs attached. No Play Store
publication and no additional signing secrets are required — the debug keystore from the repository
is used for signing.

## Play Store Artwork

Play Store artwork is generated from the `artwork/` Node project and written to `fastlane/metadata/android/*/images`.

Use:

```sh
./scripts/generate_artworks.sh
```

The generator uses:

- `artwork/public/logo.svg` for the Play Store icon.
- `artwork/public/raw/phone/*.png` for raw app screenshots.
- `artwork/src/templates/` and `artwork/src/styles/` for the rendered Play Store images.
- `fastlane/metadata/android/*/marketing.yml` for localized feature graphic text and screenshot captions.

Only locales with `marketing.yml` get localized generated images. The script removes old generated screenshot folders before writing the current output, so deleted tablet screenshots do not remain in the repository.

The `artwork/package-lock.json` file is intentionally not checked in. CI and local generation use `npm install` from `artwork/package.json` to keep this small tool out of regular lockfile update churn.

## Versioning

Voice uses [calendar versioning (CalVer)](https://calver.org/).

- Version name format: `YY.M.RELEASE`
  - `YY`: release year, last 2 digits (2025 → 25)
  - `M`: release month (1–12)
  - `RELEASE`: counter for releases within the same month (starting at 1)
- Tag format (used by CI): `YY.M.RELEASE-CODE`
  - `CODE` is the numeric Android versionCode derived from the version name using the formula below.

Examples:

- First release in September 2025 → version name `25.9.1`
- Tag includes version code → `25.9.1-5309001`

### Version code

The Android `versionCode` is calculated from the version name with:

`CODE = (YY + 28)(MM as 2 digits)(RELEASE as 3 digits)`

Example: `25.9.1` → `(25+28)=53`, `MM=09`, `RELEASE=001` → `5309001`.

This code is embedded in the git tag by `release.main.kts` and used by CI and Fastlane.

## Pages Deployment

To projects [Website](https://voice.woitaschek.de/) uses Github Pages and Mkdocs.
To deploy a new website, use dispatch a workflow
manually.

[👉 Dispatch Workflow](https://github.com/PaulWoitaschek/Voice/actions/workflows/deploy_pages.yml)
