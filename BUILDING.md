# Building NextDNS Manager+

From nothing to a running build, with Android Studio or the command line. The app is plain Java on the Android
framework with a single library (`androidx.webkit`), so there is not much to install. (Structure taken from LLM
Central's `BUILDING.md`; every fact below is this app's.)

**Contents:** [Requirements](#requirements) · [Get the source](#get-the-source) ·
[Android Studio](#option-a-android-studio) · [Command line](#option-b-command-line) ·
[Before you send a change](#before-you-send-a-change) · [Where things are](#where-things-are) ·
[Development tools](#development-tools) · [Reproducible builds](#reproducible-builds) ·
[Release builds and signing](#release-builds-and-signing) · [Troubleshooting](#troubleshooting)

## Requirements

| What | Version | Notes |
|---|---|---|
| JDK | **17 or newer** (17 and 21 give the identical APK, tested 2026-11-19) | Android Studio's bundled JDK works |
| Android SDK Platform | **36** (`platforms;android-36`) | `compileSdk = 36`; the app targets 35 and runs on Android 10 (API 29) and newer |
| Android SDK Build-Tools | **35.0.0** for building | the version AGP 8.13 uses; releases are *signed* with 34.0.0 (see [signing](#release-builds-and-signing)) |
| Gradle | 8.14.5, **downloaded by the wrapper** | always run `./gradlew`; the download is checked against a pinned SHA-256 |
| Android Gradle Plugin | 8.13.2 | downloaded automatically |
| Git | any recent | |
| Node.js *(optional)* | 18 or newer | only for the [development tools](#development-tools), never for the app |

No NDK, Kotlin or Google Play Services needed.

## Get the source

```bash
git clone <this repository's address> nextdnsmanager-plus
cd nextdnsmanager-plus
```

Use `git clone`, not the ZIP download: release builds record the Git commit they were built from, which is how a
published APK can be rebuilt byte for byte.

## Option A: Android Studio

1. Install [Android Studio](https://developer.android.com/studio) (any release that supports AGP 8.13).
2. **File → Open…** and pick the cloned folder (the one with `settings.gradle`). The first sync downloads Gradle and the
   plugin.
3. If the sync asks for SDK parts: **Tools → SDK Manager** → *SDK Platforms*: **API 36**; *SDK Tools* (tick *Show
   Package Details*): **Build-Tools 35.0.0**.
4. **Settings → Build, Execution, Deployment → Build Tools → Gradle → Gradle JDK** = 17 or newer.
5. Run configuration **app** on a phone (USB debugging) or emulator → **Run ▶**. The debug build installs as
   **"NextDNS Manager+ (Debug)"** (`org.rafee_m.nextdnsmanagerplus.debug`), next to a released copy, never over it.

> **If Studio offers to upgrade the Android Gradle Plugin or Gradle, decline.** The versions are pinned on purpose:
> F-Droid rebuilds the app from source and compares it byte for byte with the published APK.

## Option B: Command line

1. **JDK 17+**, e.g. [Eclipse Temurin](https://adoptium.net/).
2. **Android SDK** without Studio: the *Command line tools only* package from the
   [Android Studio page](https://developer.android.com/studio#command-line-tools-only), unpacked to
   `<sdk>/cmdline-tools/latest/`, then:
   ```bash
   <sdk>/cmdline-tools/latest/bin/sdkmanager --licenses
   <sdk>/cmdline-tools/latest/bin/sdkmanager "platforms;android-36" "build-tools;35.0.0" "platform-tools"
   ```
3. `export ANDROID_HOME=<sdk>`, or a git-ignored `local.properties` with `sdk.dir=<sdk>`.
4. Build:

| Command | What it does | Output |
|---|---|---|
| `./gradlew assembleDebug` | Debug APK | `app/build/outputs/apk/debug/app-debug.apk` |
| `./gradlew installDebug` | Builds and installs it on the connected phone | — |
| `./gradlew test` | Unit tests (JUnit 4, on your computer) | `app/build/reports/tests/testDebugUnitTest/` |
| `./gradlew lint` | Android lint (**every warning fails**) | `app/build/reports/lint-results-debug.html` |
| `./gradlew assembleRelease` | Release APK, shrunk by R8, **unsigned** | `app/build/outputs/apk/release/app-release-unsigned.apk` |

On Windows use `gradlew.bat`.

## Before you send a change

Run what CI runs on every push and pull request:

```bash
./gradlew test lint assembleDebug assembleRelease
```

It must pass with **0 lint issues** (warnings count as errors; accepted ones are listed with reasons in `app/lint.xml`).
If you changed an HTML page in `app/src/main/assets/` or a script in `assets/js/`, also run the page checks. One commit
per feature or fix (`CLAUDE.md` §4a).

## Where things are

| Path | What |
|---|---|
| `app/src/main/java/org/dnsmanager/app/` | The app: `MainActivity` (wiring only), then `account/`, `web/`, `ui/`, `theme/`, `site/` (everything about NextDNS), `status/`, `lock/`, `storage/`, `downloads/` |
| `app/src/main/assets/` | Local pages (`settings.html`, `about.html`) and scripts added to the dashboard (`js/`) |
| `app/src/test/` | Unit tests |
| `fastlane/metadata/` | Store texts, screenshots, changelogs (used by F-Droid and the release workflow) |
| `artwork/` | `icon.svg` (the app icon's only source); `artwork/readme/` = generated README banner and badges |
| `tools/` | Development tools (below) |
| `.github/workflows/` | CI (`ci.yml`), releases (`release.yml`), app icon (`icon.yml`) |
| `gradle.properties` | `app.repoUrl`, `app.author`: shown in About, read at build time |

Full file map and "how to change things": [PROJECT.md](PROJECT.md).

## Development tools

Optional helpers in `tools/`, each with its own `package.json` (Node.js 18+). None of them is part of the app.

| Tool | What it does | Run |
|---|---|---|
| `tools/page-checks/` | Opens Settings, About and the injected scripts in Chromium with stand-in bridges and checks them | `npm install && npx playwright install chromium && npm run check` |
| `tools/icon/` | Makes every launcher icon file and the store icon from `artwork/icon.svg` | `npm install && npm run generate` |
| `tools/readme-badges/` | Rebuilds the README banner and badges (`artwork/readme/`) in NextDNS blue; run after a version change | `npm ci && npm run generate` (needs a release build first, for the APK size) |

## Reproducible builds

The same commit must give the same unsigned APK on any computer: that is how F-Droid can publish the developer's
signed APK. Check it like this (no output from `cmp` = identical):

```bash
git clone <repo> a && git clone <repo> b
(cd a && ./gradlew --no-daemon assembleRelease) && (cd b && ./gradlew --no-daemon assembleRelease)
cmp a/app/build/outputs/apk/release/app-release-unsigned.apk b/app/build/outputs/apk/release/app-release-unsigned.apk
```

Rules that keep it true: no `-P` flags in release builds (`-PrepoUrl` changes the APK: `fdroid.md` §4); build a
**clean, committed** tree (the APK records the commit); don't upgrade AGP/Gradle without re-checking.

## Release builds and signing

`assembleRelease` makes an **unsigned** APK. Published releases are signed afterwards by the release workflow with
`apksigner` from **Build-Tools 34** and **v1 signing off**, because F-Droid checks published APKs by copying their
signature onto its own build, and any other combination fails that check for this app (tested: [RELEASE.md](RELEASE.md) §4).

To install your own release build on a phone, add to `local.properties` (never commit them) and run `assembleRelease`:

```properties
signing.storeFile=/path/to/your.keystore
signing.storePassword=…
signing.keyAlias=…
signing.keyPassword=…
```

Such a Gradle-signed APK is for your own phone only: it does **not** pass F-Droid's check. Debug builds are signed with
the committed `app/debug.keystore` (password `android`), so a debug APK from any machine installs over another one.
It is not a secret.

## Troubleshooting

| Problem | Fix |
|---|---|
| `SDK location not found` | Set `ANDROID_HOME`, or put `sdk.dir=<sdk>` in `local.properties` |
| "requires Java 17" / `Unsupported class file major version` | Gradle runs on an older JDK: install 17+, check Studio's *Gradle JDK* |
| Missing `android-36` or Build-Tools | `sdkmanager "platforms;android-36" "build-tools;35.0.0"` |
| `repoUrl must look like https://github.com/<owner>/<repo>` | `app.repoUrl` in `gradle.properties` (or a `-PrepoUrl` you passed) is malformed |
| Lint fails on a warning | Fix it; accept it only in `app/lint.xml` with a reason |
| "App not installed" for a debug APK | A debug build with a different key is installed; uninstall *NextDNS Manager+ (Debug)* first |
| Only one account possible in the app | The phone's *Android System WebView* is older than 119; update it |
| Page checks can't find a browser | `npx playwright install chromium` in `tools/page-checks/`, or set `CHROMIUM_PATH` |
