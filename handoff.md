# Handoff — start here

Everything a new session needs to get going, plus a ready prompt (§10). Updated 2026-11-15. The owner updates this
file only on request.

## Contents

1. [State right now](#1-state-right-now)
2. [The documents and what each is for](#2-the-documents-and-what-each-is-for)
3. [Where things are](#3-where-things-are)
4. [Set up, build, test, lint](#4-set-up-build-test-lint)
5. [CI](#5-ci)
6. [Rules that must not be broken](#6-rules-that-must-not-be-broken)
7. [F-Droid considerations](#7-f-droid-considerations)
8. [Open tasks](#8-open-tasks)
9. [Known limitations](#9-known-limitations)
10. [Prompt for a new session](#10-prompt-for-a-new-session)

## 1. State right now

| | |
|---|---|
| App | **NextDNS Manager+**: the NextDNS dashboard with several accounts, numbered account chip/menu, find in page, app lock, connection status, ping test, downloads, White/Black/Match-system theme |
| Repository | `UpperPanda1/nextdnsmanager-plus` (GitHub fork of `doubleangels/nextdnsmanager`) |
| Branch | `claude/gallant-bardeen-myrq5y`: an **orphan** branch. Its history starts with our first commit; nothing of the fork's history or files is in it (checked byte by byte). `main` still holds the old fork. |
| Build | `./gradlew --no-daemon test lint assembleDebug assembleRelease` passes: **83 unit tests, 0 lint issues** |
| Page checks | **6 of 6** pass |
| Release APK | **185,258 bytes** (clean build), 98.2 KB download |
| Phone test | **not done yet**: 20-step checklist in CHANGES.md (entry "First version") + 2 icon steps |
| Commits | starts with a root commit (no parent); pushed to `origin` 2026-11-15 |
| CI | `.github/workflows/ci.yml`; first run on GitHub happens at the first push: check it |

## 2. The documents and what each is for

| File | Read it for | Keep it updated when |
|---|---|---|
| `handoff.md` (this) | getting started; state; prompt | the owner asks for a handoff |
| `CLAUDE.md` | working rules, commands, hard constraints, verified facts, AdGuard reuse table, TODO | rules, facts or status change |
| `PROJECT.md` | **the map**: planning decisions (§2), what was reused (§3), architecture and file map (§4), every feature (§5), cache rules (§6), **how to change things** (§7), versions (§8), tests (§9), limitations (§10), AdGuard plan (§11) | files, features or "how to change" change |
| `CHANGES.md` | what each session changed, how it was verified, sizes, phone-test steps | every change (new entry on top) |
| `README.md` | the public description | features change |
| `tools/page-checks/README.md` | what each page check proves, how to run them | a check is added |

## 3. Where things are

```
app/
  build.gradle            versions, minSdk 29, lint = warnings are errors, signing (debug key committed)
  lint.xml                the lint warnings accepted on purpose, each with its reason
  proguard-rules.pro      R8 rules (the default file keeps @JavascriptInterface methods)
  src/main/
    AndroidManifest.xml   3 permissions (INTERNET, ACCESS_NETWORK_STATE, USE_BIOMETRIC), one activity
    java/org/dnsmanager/app/
      MainActivity.java   the one screen: wiring only (top bar, menus, tabs set-up, Settings host, lock flow)
      Prefs.java          the settings file
      site/Site.java      EVERYTHING about NextDNS (addresses, domains, test format, legal links)
      account/            account list + rules (AccountStore), Settings bridge (AccountsBridge)
      web/                tabs (TabManager, TabLru), profiles + cache (Profiles), desktop site, pinch zoom,
                          find rules, load errors, crash recovery, script loader, dialog client
      theme/              colours (Themes = all colours), Scheme, ThemeManager, ThemeRebuild, insets
      ui/                 account menu, badge, Settings/About/Ping windows, lock cover, find bar, placeholders
      lock/               app lock (AppLock) + rules (LockRules)
      status/             connection status icon (ConnectionStatus) + rules (StatusRules)
      downloads/          2FA recovery codes (data: files) to Downloads; other files → browser
      storage/            cache size + Settings bridge
    assets/
      settings.html       the Settings screen (HTML/CSS/JS in one file)
      about.html          the About screen
      js/                 scripts added to the dashboard (zoom, desktop, find highlight)
      lib/reorder.js      drag-to-reorder
    res/                  layouts (activity_main, top_bar), strings, vector icons, launcher icon, backup rules
  src/test/java/…         unit tests (same packages), MapPrefs = settings stand-in
artwork/icon.svg          THE APP ICON: edit this, then run Actions → App icon (PROJECT.md §7)
tools/icon/               icon generator (artwork/icon.svg → launcher + store icon files)
fastlane/metadata/        store icon (generated); store texts go here later (§7 of this file)
tools/page-checks/        Chromium checks of the HTML pages (README inside)
.github/workflows/        ci.yml (build/test/lint/page checks), icon.yml (App icon)
.claude/hooks/            cloud-session set-up (Android SDK, Maven mirror)
```

The full table of every file, what it does and which test covers it: **PROJECT.md §4.3**. How to change texts,
colours, settings, menu items, permissions, scripts, limits: **PROJECT.md §7**.

## 4. Set up, build, test, lint

**Cloud session (Claude):** `.claude/hooks/session-start.sh` runs at start and installs the Android SDK
(`~/android-sdk`), writes `local.properties` and a Gradle mirror for Maven Central (the sandbox gets HTTP 429 from
it). If it didn't run: `CLAUDE_CODE_REMOTE=true .claude/hooks/session-start.sh`.

**Own computer:** Android Studio (any recent) opens the folder as is; or install JDK 17+ and the Android SDK
(platform 36, build-tools 35) and put `sdk.dir=...` in `local.properties`.

```bash
export ANDROID_HOME=${ANDROID_HOME:-$HOME/android-sdk}
./gradlew --no-daemon test lint assembleDebug assembleRelease   # everything CI runs; must pass
./gradlew --no-daemon -q testDebugUnitTest --tests '*AccountStoreTest'   # one test class
./gradlew --no-daemon clean assembleRelease && stat -c %s app/build/outputs/apk/release/app-release-unsigned.apk
```

- **APK size:** measure on a **clean** build only (an incremental build once showed a wrong, smaller figure).
- **Lint:** every warning fails the build. Report: `app/build/reports/lint-results-debug.html`. To accept a warning,
  add it to `app/lint.xml` **with the reason** (only if the owner would agree).
- **Unit tests** run on the computer (no phone). Android classes are stubs there, so tested logic is plain Java.
- **Page checks** (HTML pages in Chromium with stand-in bridges):
  - sandbox: `cd tools/page-checks && ln -sfn /opt/node-tools/node_modules node_modules && CHROMIUM_PATH=/opt/pw-browsers/chromium node run-all.mjs; rm node_modules`
  - own computer: `cd tools/page-checks && npm install && npx playwright install chromium && npm run check`
  - screenshots in `tools/page-checks/screenshots/` (git-ignored): look at them after UI changes.
- **Installing on a phone:** `app/build/outputs/apk/debug/app-debug.apk` (package `org.rafee_m.nextdnsmanager.debug`,
  installs next to a release build), or the CI artifact `apks-<commit>`.

## 5. CI

`.github/workflows/ci.yml` runs on every push and pull request:

- job **android**: `test lint assembleDebug assembleRelease`, release size in the run summary, APKs as artifact
  `apks-<commit>`, test/lint reports as artifact when it fails;
- job **pages**: the page checks, screenshots as artifact.

`.github/workflows/icon.yml` (**App icon**): run it by hand after editing `artwork/icon.svg` to regenerate and
commit the icon files (it then starts CI); on a push it only checks that the files match the SVG.

After a push: open the run; a red ✗ names the failing step. Also scan the log for `##[warning]` lines (keep zero).

## 6. Rules that must not be broken

Summary of CLAUDE.md §6–7 (read those for details):

- Java only, minSdk 29, no UI libraries, **one library** (`androidx.webkit`), no analytics/crash reporting/Firebase.
- Logic in small plain-Java classes with unit tests; `MainActivity` only wires.
- NextDNS knowledge only in `site/Site.java`; colours only in `theme/Themes.java`.
- Bridges only on the app's own local pages; pages send names, never URLs; validate in Java; user text via
  `textContent`.
- Every WebView: `Profiles.attach` first, `onRenderProcessGone` handled.
- No swipe to refresh, no text size, no back button in the top bar; theme exactly White/Black/Match system.
- Never claim phone behaviour was tested. Commit/push/PR only when the owner asks.

## 7. F-Droid considerations

Not submitted yet. What is already right, and what is still to do (LLM Central's `RELEASE.md` in
`UpperPanda1/assist` is the tested model to copy from):

**Already in place**

- **Free dependencies only:** `androidx.webkit` (Apache-2.0); no Google Play Services, Firebase, Sentry or other
  proprietary/tracking code. `fdroid scanner` should find nothing (not run yet for this app).
- **Own identity**, as F-Droid requires for forks: new name ("NextDNS Manager+"), new applicationId
  (`org.rafee_m.nextdnsmanager`), new icon (own vector icon; no artwork of the original app).
- **Reproducible-build settings:** `dependenciesInfo { includeInApk = false; includeInBundle = false }`; release
  builds unsigned unless `local.properties` has signing values; the signing block has the same shape as LLM
  Central's, whose F-Droid-cleaned tree was verified to build.
- **Licence:** GPL-3.0 (`LICENSE`). Use `GPL-3.0-only` in the recipe: the code comes from LLM Central, which F-Droid
  must list as GPL-3.0-only (it contains gptAssist code).

**Still to do (in this order)**

1. **Store texts and images** in `fastlane/metadata/android/en-US/`: `title.txt`, `short_description.txt` (≤ 80
   characters), `full_description.txt`, `images/icon.png` (512 × 512; **already generated** from `artwork/icon.svg`), `images/phoneScreenshots/1.png …`,
   `changelogs/<versionCode>.txt`.
2. **Release workflow** (copy LLM Central's `release.yml`): build unsigned, sign with **`apksigner` from build-tools
   34** (35 and Gradle's own signing fail F-Droid's `apksigcopier compare`, tested in LLM Central), publish on a `v*`
   tag. Signing key and passwords only as GitHub secrets.
3. **Check reproducibility:** two clean clones, Java 17 and 21 → byte-identical unsigned APKs (only
   `META-INF/version-control-info.textproto` may differ for a working-copy build).
4. **Run F-Droid's tools locally** (fdroidserver in a venv): `fdroid lint`, `fdroid rewritemeta`, `fdroid scanner`.
5. **Recipe** in a fork of `gitlab.com/fdroid/fdroiddata`: `AntiFeatures: NonFreeNet` (the app depends on the
   proprietary NextDNS service; a label, not a rejection), `License: GPL-3.0-only`, `Binaries` +
   `AllowedAPKSigningKeys` (our key's SHA-256) so F-Droid publishes our signed APK after checking its own build
   matches, `AutoUpdateMode`/`UpdateCheckMode: Tags`.
6. Every release: `versionCode` + 1, `versionName`, a changelog file, a tag.

## 8. Open tasks

1. Check the first CI and App icon runs on GitHub (Actions tab); fix anything they report.
2. Owner's phone test (CHANGES.md checklists); fix findings.
3. Open questions (PROJECT.md §10): PayPal pop-up payments go to the browser; data: downloads are named "NextDNS
   download.txt"; translations.
4. README screenshots, store texts, release workflow, F-Droid (§7).
5. AdGuard DNS Manager: CLAUDE.md §8 and PROJECT.md §11 (product flavors; sign-in helpers from LLM Central).

## 9. Known limitations

Full list: PROJECT.md §10. In short: nothing has run on a phone yet; PayPal checkout opens in the browser; English
only; no push notifications; the "using NextDNS" test answer format comes from the original app's code (only the
"not using" answer was seen live).

## 10. Prompt for a new session

Copy the box, fill in the last line, paste it as the first message of a new session:

```text
You are continuing work on NextDNS Manager+ (Android, Java, WebView) in this repository, branch
claude/gallant-bardeen-myrq5y.

1. READ FIRST (in this order, before anything else)
   - handoff.md: state, where things are, how to build, open tasks.
   - CLAUDE.md: rules, commands, hard constraints, verified facts. Follow it exactly.
   - PROJECT.md: read the contents list, then only the sections your task needs (file map §4.3, features §5,
     cache §6, how to change things §7).
   - The newest entry of CHANGES.md.
   Then confirm the baseline: ./gradlew --no-daemon test lint assembleDebug assembleRelease passes (83 tests,
   0 lint issues) and the page checks pass (6). Report it if not, before changing anything.

2. THINK
   - Restate the task and what "done" means. Read the real code involved before designing.
   - Non-trivial change: use the superpowers:brainstorming skill, then superpowers:writing-plans.
   - Ask a short question only when a decision is truly the owner's; otherwise state your choice and reason.

3. ACT
   - Smallest correct change, existing conventions (CLAUDE.md §7): logic in small plain-Java classes with unit
     tests, MainActivity only wires, NextDNS facts in site/Site.java, colours in theme/Themes.java, no new libraries.
   - Comment for a beginner: say why, name the Android concept, link official docs.

4. PROVE
   - Run the full build/test/lint command and the page checks; look at the screenshots after UI changes.
   - Add/adjust unit tests for every rule you touch; mutation-check important ones (break, see a failure, restore).
   - Re-read the whole diff adversarially and fix what you find.
   - Never claim something works on a phone; list what remains unverified.

5. DOCUMENT
   - CHANGES.md: new entry on top (what, why, how verified, clean-build APK size, numbered phone-test steps).
   - PROJECT.md / CLAUDE.md: update what your change affects (file map, features, how-to, facts, TODO).
   - Commit, push or open a PR only when I ask.

6. REPORT: what was done, how it was verified, limitations, recommended next step.

TASK: <describe the task here>
```
