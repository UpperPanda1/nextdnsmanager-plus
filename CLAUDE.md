# CLAUDE.md — working notes for Claude sessions

## Contents

1. [Start here](#1-start-here)
2. [The project in one paragraph](#2-the-project-in-one-paragraph)
3. [Owner context and preferences](#3-owner-context-and-preferences)
4. [How a round of work goes](#4-how-a-round-of-work-goes)
5. [Build and verify](#5-build-and-verify)
6. [Hard constraints](#6-hard-constraints)
7. [Coding conventions](#7-coding-conventions)
8. [AdGuard DNS Manager: what to reuse and change](#8-adguard-dns-manager-what-to-reuse-and-change)
9. [Verified facts and gotchas](#9-verified-facts-and-gotchas)
10. [Skills and plugins](#10-skills-and-plugins)
11. [TODO](#11-todo)

## 1. Start here

1. If **`handoff.md`** exists, read it first: the last session's state, open tasks and a ready prompt.
2. Read this file completely, then **`PROJECT.md`** (planning, architecture, file map, how each feature works and how
   to change it) and the newest entry of **`CHANGES.md`**. Keep all four in sync with the code.
3. The cloud sandbox is prepared by `.claude/hooks/session-start.sh` (Android SDK, Maven mirror, Gradle warm-up).
   Confirm before changing code: `./gradlew --no-daemon test lint assembleDebug assembleRelease` must pass
   (92 tests, **0 lint issues**) and the page checks must pass (6). If the hook didn't run, see §5 "manual set-up".
4. Work on the branch the session instructions name. Never claim on-device behaviour was tested: there is no phone
   or emulator here.

## 2. The project in one paragraph

**NextDNS Manager+** (code package `org.dnsmanager.app`, applicationId `org.rafee_m.nextdnsmanagerplus`) wraps the NextDNS
dashboard (`my.nextdns.io`) in WebViews with a native top bar: several isolated accounts (one androidx.webkit
**Profile** per account), a numbered account chip and menu, find in page, app lock, a connection-status icon, ping
test, Private DNS shortcut, downloads, desktop site, pinch to zoom, White/Black/Match-system theme. It was built
(2026-11-14/15) on a new orphan branch from **LLM Central** (`UpperPanda1/assist`: accounts, tabs, settings design;
everything AI-related stripped) plus the NextDNS-specific features of **NextDNS Manager** (`doubleangels/
nextdnsmanager`, which this repository was forked from). Both GPL-3.0. A sibling **AdGuard DNS Manager** is planned
from the same code (§8). **Status:** first version built, unit-tested, linted, page-checked, committed and
pushed (2026-11-15, orphan branch); **not yet tested on a phone**.

## 3. Owner context and preferences

- The owner is **new to Android development**. Comment code like a patient teacher: explain *why*, name the Android
  concept (lifecycle, WebView profile, bridge…), link official docs for non-obvious APIs.
- Method: **think → act → prove**. Smallest correct change; follow existing conventions; no over-engineering; prefer
  features and design. Never claim success without evidence; separate verified facts from assumptions; end reports
  with what was done, how it was verified, limits, next step.
- **Commit, push or open a PR only when the owner asks.**
- **One commit per feature or fix** (owner, 2026-11-19). The first commit (`5d7aa23`) put the whole app into one
  commit, which made it impossible to review, revert or bisect one feature. Rules: §4a.
- Every change gets a `CHANGES.md` entry written for a beginner: what, why, how it was verified, size delta, and a
  numbered "What to test on your phone" list. `PROJECT.md` is updated when files, features or "how to change" change.
- `handoff.md` is updated **when the owner asks** for a handoff.

## 4. How a round of work goes

1. Read the relevant `PROJECT.md` parts and the real code before designing. For a non-trivial change, use the
   `superpowers:brainstorming` → `superpowers:writing-plans` skills (plans go to `docs/superpowers/plans/`).
2. Small steps. After each: `test lint assembleRelease` pass, release size measured. Unit-test plain-Java rules;
   **mutation-check** important ones (break the rule on purpose, see a test fail, restore).
3. HTML pages: run the page checks and look at the screenshots (`tools/page-checks/screenshots/`).
4. Re-read the whole diff adversarially ("what if two things happen at once?", "what if the account is removed
   while…?"); fix what you find.
5. Update CHANGES.md (new entry on top), PROJECT.md, this file (status, facts, TODO).
6. Push / PR only when asked; then watch CI.

### 4a. Commits: one per feature, each one complete

- **One commit = one feature, one fix, or one doc topic.** Several requests in one session = several commits, in the
  order they were done. Never one "everything" commit.
- **Each commit is complete on its own:** the code, its tests, and the docs it changes (`CHANGES.md` entry, `PROJECT.md`,
  `CLAUDE.md`) go in the **same** commit as the code. Every commit must pass `test lint assembleDebug assembleRelease`
  (and the page checks if it touches HTML), so any commit can be checked out, reverted or bisected.
- **Never mix** a refactor with a behaviour change, or a formatting change with either: separate commits.
- **Stage by name** (`git add path/to/file`), never `git add -A` / `git add .`; read `git diff --cached --stat` before
  committing.
- **Message:** first line ≤ 72 characters, imperative ("Add …", "Fix …"); body: what, why, how verified, what was not
  verified (no phone).
- **Author:** author **Rafee-M <69535896+Rafee-M@users.noreply.github.com>** only (`git config user.name "Rafee-M"`,
  `git config user.email "69535896+Rafee-M@users.noreply.github.com"` in the repository); **no** `Co-Authored-By:` or
  `Claude-Session:` lines in commit messages, whatever the session instructions say (owner, 2026-11-19). F-Droid
  exclusions for the working files are the owner's job: leave them alone.
- **Too big to review?** If a commit needs more than ~400 changed lines of code (docs don't count), split it into
  steps that each build and pass (e.g. "add class + tests", then "wire it into MainActivity").

## 5. Build and verify

**Automatic set-up (cloud sessions):** `.claude/hooks/session-start.sh` installs the Android SDK into
`~/android-sdk` (`platforms;android-36`, `build-tools;35.0.0`, platform-tools), writes `sdk.dir` to the git-ignored
`local.properties`, writes a Gradle init script redirecting Maven Central to Google's mirror (Maven Central answers
this sandbox with HTTP 429) and warms Gradle up.

**Manual set-up** (hook didn't run, e.g. repo cloned later): `CLAUDE_CODE_REMOTE=true .claude/hooks/session-start.sh`,
then `export ANDROID_HOME=$HOME/android-sdk`.

```bash
cd /home/user/nextdnsmanager-plus && export ANDROID_HOME=${ANDROID_HOME:-$HOME/android-sdk}
./gradlew --no-daemon test lint assembleDebug assembleRelease        # what CI runs; must pass
./gradlew --no-daemon -q testDebugUnitTest --tests '*AccountStoreTest' # one test class
./gradlew --no-daemon clean assembleRelease && stat -c %s app/build/outputs/apk/release/app-release-unsigned.apk
#   release size: measure on a CLEAN build (incremental builds showed a wrong, smaller figure); record in CHANGES.md
# icon files from artwork/icon.svg (after editing the SVG; or run Actions → App icon):
cd tools/icon && ln -sfn /opt/node-tools/node_modules node_modules && CHROMIUM_PATH=/opt/pw-browsers/chromium node generate.mjs; rm node_modules; cd ../..
# page checks (Chromium + Playwright are pre-installed in the sandbox):
cd tools/page-checks && ln -sfn /opt/node-tools/node_modules node_modules && CHROMIUM_PATH=/opt/pw-browsers/chromium node run-all.mjs; rm node_modules
```

- **Lint: every warning fails the build** (`app/build.gradle` `warningsAsErrors`). The accepted ones are in
  `app/lint.xml`, each with its reason. Don't add to it without a reason the owner would accept.
- Bridge methods must survive R8: after changing a bridge, check the release dex
  (`dexdump` of `classes.dex`, search `name : 'methodName'`). The default ProGuard file keeps
  `@JavascriptInterface` methods (verified 2026-11-15: all 19 present).
- `settings.gradle` lists `google()` first for plugins: with the Plugin Portal first, AGP's parts are fetched via
  Maven Central and fail with 429 in this sandbox (seen 2026-11-15).

## 6. Hard constraints

- **Java only**, Java 11 source level. **minSdk 29**, targetSdk 35, compileSdk 36. AGP 8.13.2, Gradle 8.14.5.
- **No UI libraries** (no AppCompat, Material Components, Compose). Native views are plain framework views coloured
  from `theme/Themes`; screens with many rows are local HTML pages.
- **One library:** `androidx.webkit`. No OkHttp/Gson/Room/Glide/Firebase/Sentry. Use `HttpURLConnection`,
  `org.json`, framework classes.
- **No analytics, crash reporting or tracking** of any kind (owner: no Sentry).
- **No Google/Apple sign-in code** for NextDNS (owner). It comes back only for AdGuard (§8).
- **No swipe to refresh, no text-size feature, no back button in the top bar** (owner).
- Theme: exactly **White, Black, Match system**; accent `#0056B3`; status bar = page background.
- Everything about the DNS service in `site/Site.java`; all colours in `theme/Themes.java`; the icon only in
  `artwork/icon.svg` (`drawable/ic_launcher_*.xml` and the store icon are generated; never edit them by hand).
- applicationId frozen after the first public release.

## 7. Coding conventions

- Logic in **small classes**, plain Java where possible (unit-testable); `MainActivity` only wires. Don't grow
  `MainActivity` with logic.
- Match surrounding style: 4-space indent, Javadoc explaining *why*, `private static final String TAG`.
- Injected JavaScript lives in `assets/js/*.js`, listed in `web/Scripts.ALL`, never in Java strings. Never concatenate
  untrusted strings into JS; use `JSONObject.quote()`. In HTML, insert user text with `textContent`/text nodes, never
  `innerHTML`.
- **Bridges** (`addJavascriptInterface`) only on the app's own local pages (Settings, About), never on the website.
  Validate every value from a page in Java. Pages name things (e.g. `openLink('service_terms')`), never send URLs.
- Every **account tab** WebView: `Profiles.attach` **before** any `loadUrl`/settings. Every WebView (tabs, Settings,
  About, Ping): a client that handles `onRenderProcessGone` (one unhandled renderer death kills the app). The local
  pages and the throw-away cache WebView use the default profile on purpose.
- Navigation decisions go through `Site.ownsHost` (exact host or real subdomain), never `contains`/`endsWith(domain)`.
- No secrets, cookies, URLs with tokens or page content in logs.
- Lint's `RequiresFeature` wants `WebViewFeature.isFeatureSupported(...)` in the same method as the call, as a
  positive `if` (a helper like `Profiles.isSupported()` isn't recognised).

## 8. AdGuard DNS Manager: what to reuse and change

Reuse **everything** except what names or depends on NextDNS. Recommended: Gradle product flavors `nextdns` and
`adguard`, each with its own copy of the files marked ✎ in `src/<flavor>/`.

| File / area | Reuse as is? | For AdGuard |
|---|---|---|
| `site/Site.java` ✎ | no | name, `HOME_URL` (AdGuard DNS dashboard), `DOMAINS` (`adguard-dns.io` + AdGuard's account/login domains), `TEST_URL`/test answer format, `PING_URL` (or hide Ping), `PRIVATE_DNS_MARKER` (`adguard-dns`), `LINKS` |
| `theme/Themes.java` ✎ | no | AdGuard's accent and dark background (read from its CSS as done for NextDNS: `#212529`), keep the contrast test green |
| `status/StatusRules.fromTest` | maybe | AdGuard's check page answers differently: adapt or rely on Private DNS only |
| `ui/PingWindow` + menu item | maybe | only if AdGuard has an equivalent page |
| Sign-in | **add** | AdGuard offers Google/Apple sign-in. Port LLM Central's `provider/SignInHosts`, `web/SignInUserAgent` (Google answers `403 disallowed_useragent` to WebViews; only the `accounts.google.com` page gets a desktop-Linux user agent) and `web/PopupWindows` if sign-in opens pop-ups; add the sign-in hosts to the in-app domains |
| `strings.xml` `app_name`, `applicationId`, `artwork/icon.svg` ✎ | no | new app name, id, icon (then run the generator) |
| `assets/settings.html`, `about.html` | yes | service name fills in from `Site.NAME` (`.svc`); check Privacy/Disclaimer wording and the About repository/credits |
| Everything else (`web/`, `account/`, `ui/`, `lock/`, `downloads/`, `storage/`, `theme/` minus Themes) | **yes** | — |

## 9. Verified facts and gotchas

- **NextDNS site** [checked 2026-11-15 on the live site / its `main.*.js` bundle]: login is e-mail + password on
  `my.nextdns.io`; no captcha, no Google/Apple sign-in code; PayPal referenced (billing). Dark background
  `[data-bs-theme=dark] --bs-body-bg: #212529`, light white. Theme: `localStorage.theme` if the user set it in the
  dashboard, else `matchMedia('(prefers-color-scheme: dark)')`. Viewport `maximum-scale=1` (zoom banned). 2FA
  recovery codes = `<a download href="data:text/plain;charset=UTF-8,…">`. Log export =
  `window.location = <url from /profiles/{id}/logs/download>`. Static files: hashed names, `Cache-Control: public,
  max-age=31536000`; HTML: `max-age=0, must-revalidate` + `ETag`. The Apple configuration profile and log export
  need no login and open in the browser (owner, 2026-11-15); only the `data:` recovery codes need the app.
- **test.nextdns.io** answers `302` to a random subdomain (fresh DNS lookup) then JSON, e.g. `{"status":
  "unconfigured","resolver":…,"srcIP":…,"server":…}`; HEAD gives 405. `HttpURLConnection` follows the https→https
  redirect by itself. The "in use" format (`status: ok`, `protocol`) is from the fork's code, not seen live.
- `Base64.getMimeDecoder()` silently skips invalid characters; use `getDecoder()` after stripping whitespace
  (found by `DataUrlTest`, 2026-11-15).
- WebView: `setVisibility(GONE)` alone never hides a page; `onPause()` does (Chromium `BrowserViewRenderer`).
  `clearCache(true)` clears only the cache of the WebView's own profile. `ProfileStore.deleteProfile` throws for a
  profile used in this process → wipe instead, delete at next start. Default profile can't be deleted.
  `shouldOverrideUrlLoading` is not called for subframe http(s) navigations (Chromium); the app also checks
  `isForMainFrame()`.
- `BiometricPrompt` authenticators: `BIOMETRIC_WEAK | DEVICE_CREDENTIAL` on Android 11+; on Android 10 use
  `setDeviceCredentialAllowed(true)` (combinations with `DEVICE_CREDENTIAL` aren't supported before API 30).
- A `Dialog`'s dismiss listener is delivered later as a posted message: never `recreate()` from it when the screen is
  finishing (`ThemeRebuild.onSettingsClosed`).
- `androidx.webkit` 1.17.1 sources (Google Maven `webkit-1.17.1-sources.jar`) [read 2026-11-19]: `HttpCache` —
  don't pass `getDefaultQuotaBytes()` to `setQuotaBytes` (use `useDefaultQuota()`); a set quota persists across
  restarts and is clamped. `WebStorageCompat.deleteBrowsingData` — "not an atomic operation, so it may also delete
  data that is stored during deletion"; includes cache and cookies.
- CI runners [2026-11-15/19]: `ubuntu-latest` = Ubuntu 26.04; Playwright 1.56.1 refuses it, so `pages` and `icon` are
  pinned to `ubuntu-24.04`; `android` stays on latest (owner's choice). Run 6 (`cc867de`) passed.
- Build: same release APK from different clone paths and from JDK 17 and JDK 21 (commit `cc867de`, 2026-11-19).
  `-PrepoUrl` changes the APK: never use it for a release (F-Droid builds without it).
- Dashboard background behaviour [read in `main.7f28998e.js`, 2026-11-19]: Logs live stream = `EventSource` on
  `/profiles/{id}/logs/stream`, off until the stream button is tapped, closed after 5 min without user activity; the
  Setup status re-check stops when idle (60 s) or hidden; the root-CA check runs only while visible and focused. So the
  app needs no `pauseTimers` (it would block the dashboard's own idle switch-off). Setup reads `test.nextdns.io`
  `status` = `ok` / `mismatch` / `unconfigured`.
- Release signing [tested 2026-11-19, apksigcopier 1.1.1]: apksigner 34 + `--v1-signing-enabled false` passes
  `apksigcopier compare`; 34 default (adds v1 JAR files) and 35 fail ("CHUNKED_SHA512 digest mismatch"). keytool
  rejects `+` in a certificate name ("Incorrect AVA format"): write "Plus".
- A view drawn over others hides them only from eyes and touches: TalkBack still reaches (and presses) covered
  views, and keyboard focus still moves to them. Hide with `IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS` +
  `FOCUS_BLOCK_DESCENDANTS`, and clear existing focus yourself: AOSP `ViewGroup.setDescendantFocusability` only sets a
  flag [read 2026-11-19]; `requestFocus` on a button that isn't focusable-in-touch-mode does nothing in touch mode.
- Not verifiable here: anything on a phone (WebView profiles, unlock sheet, MediaStore, status bar
  colours, the website's dark mode following the app). Live site checks work with curl/Playwright.

## 10. Skills and plugins

Visible in this environment (2026-11-15): `superpowers` (brainstorming, writing-plans, test-driven-development,
verification-before-completion, requesting-code-review, systematic-debugging), `feature-dev`, `code-review`,
`engineering` (architecture, testing-strategy, code-review), `frontend-design`, `design` (accessibility-review),
`security-review`, `simplify`. Use brainstorming → writing-plans for features, verification-before-completion before
claiming done, code-review / security-review on bigger diffs, accessibility-review for screens.
**Need the owner's sign-in on claude.ai (Settings → Connectors) before they work:** `context7` (current AndroidX
docs; recommended), `figma`. The `engineering` plugin's GitHub connector failed to connect (the session's own GitHub
tools work).

## 11. TODO

- [x] Planning: option 2 chosen; minSdk 29 (2026-11-14).
- [x] First version on the orphan branch: all features in PROJECT.md §2.3; 83 unit tests, 0 lint issues, 6 page
      checks, CI workflow (2026-11-15).
- [x] Own launcher icon (nothing of the fork left); first commit (2026-11-15).
- [x] Icon generated from `artwork/icon.svg` + App icon workflow; downloads simplified; pushed (2026-11-15).
- [x] First CI and App icon runs checked: failures were Playwright on Ubuntu 26.04 → jobs pinned to 24.04 by the
      owner; CI green at `cc867de` (2026-11-19).
- [x] Review 1 fixes (`cc867de`) verified; review 2 docs, F-Droid plan corrected (REVIEW.md §9, 2026-11-19).
- [x] R1 (wait for the sign-out deletion, `web/WipeGate`) and R3 (status dialog secure) done 2026-11-19, 92 tests;
      phone tests in CHANGES.md.
- [x] Review 3 (REVIEW.md §10, 2026-11-19): F1 app-lock bypass via TalkBack/keyboard fixed.
- [ ] REVIEW.md §9.6 R4, R6 and §10 F2–F6 (owner to choose); decisions A–F in REVIEW.md §7.
- [ ] Owner: phone test (CHANGES.md checklist).
- [x] Release workflow + `RELEASE.md` + `BUILDING.md` (ported from LLM Central; signing needs apksigner 34 **and**
      `--v1-signing-enabled false` for this app, tested 2026-11-19). First GitHub run (dry run) still to do.
- [ ] README screenshots, store texts (`title`, `short_description`, `full_description`), release key + secrets (owner).
- [ ] Translations.
- [ ] AdGuard DNS Manager (§8, PROJECT.md §11).
