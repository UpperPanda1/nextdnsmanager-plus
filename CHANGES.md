# Changes

Newest first. Each entry: what changed, why, how it was verified, sizes, and what to test on a phone.

---

## 2026-11-19 — Second review: the fixes checked, F-Droid plan corrected (docs only)

**What.** A second session checked commit `cc867de` (the entry below) claim by claim, compared the code with LLM
Central's newest branch, and reviewed cache, speed and battery. **No app code changed.** Results are in `REVIEW.md` §9.

- **The fixes below hold up.** One small gap: Settings/About/Ping get the "secure" mark just *after* they appear
  (REVIEW.md §9.6 R3).
- **`fdroid.md` corrected.** Its build fingerprint (SHA-256) came from an uncommitted build that nobody can repeat;
  it now has the fingerprint of the real commit. Its advice to pass `-PrepoUrl` in a release build was **dangerous**:
  F-Droid builds without it, so the two APKs would differ and F-Droid would refuse to publish yours. That advice, and
  the same hint in `gradle.properties`, now say "never in a release". A numbered "what submitting means" list was added.
- **New facts written down:** CI has run and is green; `ubuntu-latest` is now Ubuntu 26.04, which Playwright doesn't
  support (why two jobs are pinned to 24.04); the androidx docs confirm both the cache-cap change and the sign-out race.
- **PROJECT.md** now explains how to change the repository address (it was missing). **CLAUDE.md**: the "Profiles.attach
  first" rule now says it is for account tabs; first-version test count corrected (83).
- **Six recommended code changes** (not made; your choice): REVIEW.md §9.6. The two that matter most: wait for the
  sign-out deletion to finish (R1), and set the "secure" mark before a window appears (R3). (A battery change, R2, was
  later withdrawn: REVIEW.md §9.8.)

**Why.** You asked for an adversarial check of the previous session's work, the docs and F-Droid readiness.

**Verified.** `./gradlew clean test lint assembleDebug assembleRelease`: 85 tests pass, 0 lint issues; 6 of 6 page
checks. The release APK is the **same** (SHA-256 `4e287525…18554a`) from the working tree, two fresh clones in
different folders (JDK 21) and a fresh clone built with **JDK 17**. Building with `-PrepoUrl` gives a different APK;
a malformed address stops the build. CI run 6 on GitHub passed. **Not verified:** anything on a phone.

**Size.** Release APK (clean build) **185,382 bytes**, unchanged by this entry (docs only). This also confirms the figure
in the entry below.

**What to test on your phone:** nothing new (no app change). The checklist of the entry below still applies.

---

## 2026-11-19 — Review fixes, repository address read at build time, F-Droid plan

**What.** A review of the whole project (`REVIEW.md`) found a few real problems; the small, certain ones are fixed here.
The big picture, every finding and the open decisions are in `REVIEW.md`; the F-Droid plan is in `fdroid.md`.

- **Repository address and author are read when the app is built** (`gradle.properties` → `BuildConfig` → About). When the
  repository moves, change one line (or build with `-PrepoUrl=https://github.com/<owner>/<repo>`). A wrong address stops the build.
- **Links that are not web links no longer reach other apps** (`web/ExternalLinks`): a `file:` link on a page could crash the app.
- **App lock now also hides Settings, About and the Ping test** from screenshots and the recent-apps card (they are separate windows).
- **Cache cap is a fixed 20 MB** instead of "the smaller of 20 MB and WebView's default" (the WebView docs advise against the latter).
- Removed dead code (an unused link name, unused CSS), fixed comments left over from LLM Central, "Play Store" wording
  (F-Droid users have none), WebView 119+ requirement written down (from LLM Central's docs).

**Verified.** Clean runs of `test lint assembleDebug assembleRelease`: 85 tests pass (2 new), 0 lint issues; 6 of 6 page
checks (the About check now also proves author and repository come from the app). Mutation check: allowing `file:` in
`ExternalLinks` makes its test fail. Build override and a hostile `-PrepoUrl` value were tried. Three release builds
(working tree + two fresh clones at different paths) gave the same SHA-256. **Not verified:** anything on a phone; JDK 17
reproducibility; the secure flag on real windows.

**Size.** Release APK 185,258 → 185,382 bytes (incremental build, measure on a clean build before quoting).

**What to test on your phone**

1. Settings → Security → turn App lock on. Then, with Settings open, take a screenshot: it must be blank or refused. Do the same in About and in the Ping test.
2. With the lock on, open recent apps: the card must be blank.
3. Settings → About: "Developed by" shows Rafee-M; "Source code on GitHub" opens the repository you expect.
4. Settings → Data → Clear cache: the size drops, every account stays signed in.
5. Settings looks as before (account badges, "Open" chip, dialogs; the CSS was trimmed).

---

## 2026-11-15 — Icon from one SVG (App icon workflow); simpler downloads

**Icon.** The icon now has one source, `artwork/icon.svg`. `tools/icon/generate.mjs` makes the launcher icon's two
vector layers (`res/drawable/ic_launcher_background.xml`, `ic_launcher_foreground.xml`, the foreground doubling as
the themed icon) and the 512 × 512 store icon (`fastlane/metadata/android/en-US/images/icon.png`), and refuses a
drawing outside the launcher's safe zone. New workflow **App icon** (`.github/workflows/icon.yml`, modelled on LLM
Central's): run by hand, it regenerates, commits to the chosen branch and starts CI; on a push that touches the icon
it checks the vector files match the SVG. How to change the icon: PROJECT.md §7 "Change the app icon". The icon
itself is unchanged (same path, same colours).

**Downloads.** The owner reported that the Apple configuration profile and the log export need no login and open in
the browser. So http(s) downloads now go to the browser (no DownloadManager, no cookie copying); the app only saves
what a browser can't fetch: `data:` files, i.e. the **2FA recovery codes**.

**Verified.** Generator: drawable files byte-identical to the hand-made ones in their paths, two runs give
identical output (all three files), preview looked at (circle, rounded square, themed). Workflow: valid YAML; its
check step simulated locally on the committed tree (no difference after regenerating). Clean build: tests pass,
0 lint issues (numbers below). Not verified: the workflow on GitHub (first run after this push).

**Size.** Release APK (clean build) 186,042 → **185,258 bytes**; download 98.2 KB. 83 tests, 0 lint issues, 6 page checks.

**What to test on your phone**

1. In the dashboard: Account → two-factor authentication → recovery codes → Download: "Saved to Downloads"; the file holds the codes.
2. Setup page → Apple configuration profile, and Logs → Download: the browser opens and downloads the file.

---

## 2026-11-15 — Own launcher icon; first commit

**What.** The launcher icon was the only thing left from the original NextDNS Manager (21 files, found by comparing
every file byte by byte with the fork). The owner wants the branch to contain nothing from the forked project, so it
is replaced by an original **vector adaptive icon**: a white shield with a "+" cut out, on the accent `#0056B3`
(`res/mipmap-anydpi/ic_launcher.xml`, `res/drawable/ic_launcher_background.xml`, `ic_launcher_foreground.xml`; the
foreground doubles as the Android 13+ themed-icon layer). With minSdk 29 every phone supports adaptive icons, so no
PNG copies are needed. The three lint exceptions for the old PNGs were removed from `app/lint.xml`.

Then everything was committed as the first commit of the orphan branch (a root commit: no parent). Not pushed yet:
the owner asked for a commit, and the session's permission check refused a push without an explicit request. handoff.md was rewritten into a
complete starting guide (documents, file locations, build, lint, F-Droid notes, session prompt).

**Verified.** Clean `test lint assembleDebug assembleRelease`: 83 tests pass, **0 lint issues** without the icon
exceptions; the APK's manifest points at the vector icon (`aapt dump badging`); the icon was rendered in Chromium
from the same path in a circle and a rounded square (looked at). A second byte-by-byte comparison finds no file of
the fork in the commit.

**Size.** Release APK (clean build) **268,054 → 186,042 bytes**; download size 161.7 → **98.4 KB**.

**What to test on your phone**

1. The launcher shows a blue icon with a white shield and a "+"; with themed icons on (Android 13+), the shield
   takes the theme colour.
2. Settings → About shows the same icon.

---

## 2026-11-14/15 — First version: NextDNS Manager+ rebuilt on LLM Central

**Branch:** `claude/gallant-bardeen-myrq5y`, a new orphan branch (no history from either project). **Not committed**
(owner's instruction). Work spread over three sessions (two were cut short; the last one re-checked all earlier work
before continuing).

### What and why

The owner chose **option 2**: start from LLM Central's code (multi-account, tabs, settings design, tests), strip
everything AI-related, and add the NextDNS-specific features of the original NextDNS Manager. Reasons and the full
comparison: PROJECT.md §2.1. Every requested item and where it lives: PROJECT.md §2.3.

**Added / built**

1. Several NextDNS accounts, each in its own WebView profile (separate logins), switched from a numbered chip.
2. One account manager in Settings: add, rename, drag to re-order, sign out, remove (last one protected).
3. Account menu: accounts with ✕ to unload, icon row (Desktop site, Reload, Hide status bar, Clear cache), Manage
   accounts.
4. Top bar: account chip · connection status icon · home · three dots (Find in page, Ping test, Private DNS settings,
   Settings). No back button; Android's Back works inside the page.
5. Settings rebuilt in LLM Central's Material 3 design with accent `#0056B3`: Accounts, Appearance (Theme), Behaviour
   (Pinch to zoom, Accounts kept loaded), Security (App lock), Data (Clear cache with size), About & legal (About,
   Privacy, NextDNS privacy policy, NextDNS user agreement, Permissions, Disclaimer).
6. Theme White / Black / Match system; the website switches along; status bar and top bar use the page's own
   background (white / `#212529`).
7. App lock with Android's own unlock sheet (fingerprint, face or PIN), 1-minute grace, hidden in recent apps and
   screenshots; switching it needs the unlock.
8. Connection status icon (green/orange/yellow/red, grey while checking) from Private DNS + `test.nextdns.io`.
9. Find in page (bar at the bottom, Ctrl+F).
10. Ping test window, Private DNS shortcut, downloads (http(s) with the account's login; `data:` files such as the
    2FA recovery codes saved to Downloads).
11. Cache rules: one capped cache per account (20 MB), reused across reloads and restarts, Clear cache empties every
    account but keeps all logins, removed accounts leave nothing (PROJECT.md §6).
12. About screen (LLM Central's design): icon, version, developer, source, Star, Report an issue, credits to both
    original projects.
13. Unit tests (83), page checks (6), lint with warnings as errors, CI workflow for GitHub.

**Removed (compared with the two sources)**

- From NextDNS Manager: Sentry, Firebase notifications, feedback forms, swipe to refresh, back button, refresh menu
  item, the AndroidX/Material/OkHttp/Gson libraries, the gms/foss flavor split, 14 translations (texts didn't fit
  the new screens).
- From LLM Central: all AI providers and duck.ai features, assistant, sharing, offline chats, prompt prefill, user
  scripts, tracker blocking, third-party cookie switch, Google/Apple sign-in helpers, palettes, Material You, text
  size, bar-at-the-bottom, intro screen, debug request log.

### Bugs found and fixed while checking (this session)

1. **2FA recovery codes couldn't be downloaded:** the dashboard offers them as a `data:` link, which the first
   download code refused. Added `downloads/DataUrl` + MediaStore saving.
2. **Broken `data:` files would have been saved as garbage:** Java's MIME Base64 decoder skips invalid characters.
   Switched to the strict decoder (found by `DataUrlTest`).
3. **Website could stay in the old colours** when the phone switched day/night while the app was open (only the
   native parts were repainted). The screen is now rebuilt, keeping the account and the page.
4. **A screen rebuild lost the page** the user was on (it went back to the dashboard's start). The page is now saved
   and reopened, accepting only `https` NextDNS pages (`TabManager.isStartUrl`, tested).
5. **Screen-reader-only text was visible** in Settings ("Work: position 2 of 3"): its CSS rule was cut while trimming
   the reused stylesheet. Restored; the dark-mode page check now asserts it stays hidden.
6. **Status icon flashed red** for a moment at start. Now grey until the first check.
7. **Sign out also forgot the account's Desktop-site choice.** Only Remove does now.
8. Frames inside the page are never redirected to the browser (defensive `isForMainFrame()` check).
9. Lint: outdated version checks, an unnecessary `-v26` folder, an unused icon, the wrong API annotation.

### How it was verified

| Check | Result |
|---|---|
| `./gradlew --no-daemon test lint assembleDebug assembleRelease` | BUILD SUCCESSFUL; **83 tests** pass (debug and release variants), **0 lint issues** with warnings counted as errors |
| Page checks (`tools/page-checks`, Chromium) | **6 of 6** pass; screenshots of Settings (light, dark), sub-screens and About looked at |
| Mutation checks | domain rule loosened → `SiteTest` + `TabManagerTest` fail; undeclared permission → `settings-permissions` fails; renamed theme option → `settings-options` fails; all restored |
| Release APK contents | all 19 bridge methods kept by R8; permissions exactly INTERNET, ACCESS_NETWORK_STATE, USE_BIOMETRIC; minSdk 29, targetSdk 35 |
| Live site facts | dashboard colours, cache headers, 2FA/log downloads, no captcha/Google sign-in, `test.nextdns.io` redirect + JSON (curl / Playwright) |
| CI workflow | valid YAML; same commands as run locally; **not yet run on GitHub** (nothing pushed) |

**Not verified:** anything on a phone (no device or emulator here). See the checklist below.

### Size

Release APK (unsigned, clean build): **268,054 bytes**; download size **161.7 KB** (`apkanalyzer apk download-size`).
The code file `classes.dex` (136 KB) is stored uncompressed, the Android build tools' default for minSdk 28+ (Android
uses it straight from the APK). Earlier incremental builds showed 221,774 bytes; only clean-build figures count.
(LLM Central 1.0.x: ~294 KB.)

### What to test on your phone

Install `app/build/outputs/apk/debug/app-debug.apk` (or the CI artifact once pushed).

1. Open the app: the dashboard loads; the status bar has the same colour as the top bar.
2. Sign in. Close the app completely and reopen: still signed in.
3. Settings → Accounts → Add account "Work" → Open: a fresh login page. Sign in with a second NextDNS account.
4. Chip → switch between 1 and 2: instant, each keeps its own login and page. Badges show 1 and 2.
5. Drag account 2 above 1 in Settings: the numbers and the menu order change.
6. Account menu: ✕ on a loaded account unloads it ("Unloaded. Tap to load" if it was on screen; tap loads it).
7. Account menu icons: Desktop site (page reloads wide; again = back), Reload, Hide status bar (and back), Clear
   cache (toast; still signed in everywhere).
8. Settings → Data shows the cache size; Clear cache → size drops; all accounts stay signed in.
9. Settings → Theme: White, Black, Match system. The app **and the dashboard** switch when Settings closes. With
   Match system, switch the phone's dark mode while the app is open: both follow.
10. Settings → Pinch to zoom on: the page reloads and can be pinch-zoomed; off: no zoom.
11. Settings → App lock on: the unlock sheet appears first. Leave the app for 2 minutes and return: locked. Return
    within a few seconds: not locked. Recent apps shows a blank card.
12. Three dots → Find in page: type a word, ↑/↓ move, count shows; Back closes the bar.
13. Three dots → Ping test: both pages load; Back closes. → Private DNS settings: Android's network settings open.
14. Status icon: with NextDNS as Private DNS → green shield; Private DNS off → red; tap → explanation.
15. Download the Apple configuration profile and (if 2FA is on) the recovery codes: both land in Downloads.
16. Turn on airplane mode, reload: "Couldn't load this page. Tap to retry"; airplane mode off, tap: page returns.
17. Android's Back goes back inside the dashboard, then leaves the app.
18. Settings → Sign out of an account: its page closes ("Unloaded. Tap to load" if it was on screen); opening it
    shows the login page; the other account stays signed in.
19. Settings → Remove an account: it disappears from the menu; the last account can't be removed.
20. About: version shows; Report an issue and Star open GitHub in the browser.
