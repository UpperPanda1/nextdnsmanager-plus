# Changes

Newest first. Each entry: what changed, why, how it was verified, sizes, and what to test on a phone.

---

## 2026-11-19 — CI: retry the Chromium install when Ubuntu's mirror fails

**What.** CI runs 9 and 10 failed in the `pages` job, before any check ran. The app build, tests and lint (`android`
job) passed both times. The failing step installs Chromium plus about ten font packages from Ubuntu's package
mirror, which answered "503 Service Unavailable" (run 10) and refused connections (run 9). Now `ci.yml` and `icon.yml`
refresh the package lists and try up to three times, waiting 30 s and then 60 s between tries.

**Why.** A short outage at Ubuntu shouldn't turn the CI red when nothing in the project is wrong.

**Verified.** Both job logs read (same cause). Both YAML files parse. The new step scripts were run locally with a
stand-in installer: failing twice then working → step passes after 3 tries; never working → step fails after 3.
**Not verified:** a real run on GitHub (the next push starts one).

**Also:** commit rule written down (handoff.md §6, CLAUDE.md §4a): author Rafee-M only, no co-author or session
lines; history rewritten to match.

**Size.** App unchanged.

---

## 2026-11-19 — App lock: nothing behind the cover is reachable with TalkBack or a keyboard (review 3, F1)

**What.** While the app is locked, a cover is drawn over the screen. A cover hides things from your eyes and your
fingers, but Android still lets **TalkBack** (the screen reader) and a **hardware keyboard** reach whatever is
underneath. Someone holding your unlocked phone with TalkBack on, or with a keyboard attached, could have moved to
the top bar behind the cover, opened ⋮ → Settings and signed out or removed accounts, or read the dashboard. Now,
while locked:

- everything under the cover is hidden from TalkBack and other accessibility services;
- keyboard focus can't move there, and if the page had the keyboard, that focus is taken away;
- Settings refuses to open (a second guard, in case anything else ever tries).

When you unlock, all of it is restored. The cover's own Unlock button stays reachable for TalkBack and keyboards.

**Why.** Found by the adversarial review of the screens (REVIEW.md §10, F1). While fixing it, the Android source showed
that blocking focus doesn't remove focus a view already has, so the fix clears it explicitly.

**Verified.** `test lint assembleDebug assembleRelease`: 92 tests pass, 0 lint issues; the compiled app calls
`LockScreen.covers` from `onCreate`. No unit test: accessibility and focus are Android behaviour that can't run on the
computer without an extra test library (the project allows none). **Not verified:** on a phone.

**Size.** Release APK (clean build) 187,098 → **187,302 bytes** (+204 bytes).

**What to test on your phone**

1. Settings → turn **App lock** on. Leave the app for more than a minute, come back: the cover shows.
2. Turn on **TalkBack** (Settings → Accessibility). Swipe right repeatedly on the cover. Expected: TalkBack reads only
   "Locked" and the "Unlock" button, never the account chip, the ⋮ button or dashboard text.
3. Unlock: TalkBack can reach the top bar and the page again. Turn TalkBack off.
4. Optional, with a Bluetooth/USB keyboard: on the cover, press Tab and arrow keys, then Enter. Expected: only Unlock
   reacts; typing never reaches the page.

---

## 2026-11-19 — App lock: the status dialog is hidden from screenshots too (R3)

**What.** With the app lock on, the app is "secure": screenshots and screen recordings show it blank. Each dialog is a
separate window and needs that mark itself. Settings, About and the Ping test had it; the small dialog you get by
tapping the **status icon** did not. Now it does, and it follows at once when the lock is switched on or off.

**Why — and what was dropped.** Recommendation R3 also said the mark should be set *before* a window appears, because
"its first frames are drawn before the mark". Re-checked in Android's own source code first (REVIEW.md §9.9): that was
**wrong**. Showing a window only schedules its first drawing for the next screen frame, and the mark is set before that.
So that part needed no change; the only real gap was the status dialog. Pop-up menus (account menu, ⋮) were never a
gap: they belong to the main window and inherit its mark. The dashboard opens no browser alert/confirm boxes (checked
in its code), so those aren't a gap either. The rule "call it right after `show()`" is now written in
`ui/Windows.setSecure`.

**Verified.** `test lint assembleDebug assembleRelease`: 92 tests pass, 0 lint issues. The compiled debug app calls
`Windows.setSecure` from `showStatusDialog` (new), `applySecureFlag` (now also covering the status dialog) and
`Windows.fill` (checked with `dexdump`). No unit test: window flags are Android behaviour that can't run on the computer
without an extra test library (the project allows none). **Not verified:** on a phone.

**Size.** Release APK (clean build) 186,990 → **187,098 bytes** (+108 bytes).

**What to test on your phone**

1. Settings → turn **App lock** on.
2. Tap the **status icon** (top bar) and take a screenshot while its dialog is open. Expected: the screenshot is black,
   or Android says the app doesn't allow screenshots.
3. Open the account menu (the numbered chip) and take a screenshot. Expected: also blank.
4. Turn App lock **off**, open the status dialog again, take a screenshot. Expected: the dialog is visible now.

---

## 2026-11-19 — Signing out: the account waits until its old data is really gone (R1)

**What.** "Sign out" and "Remove" empty the account's container (cookies, saved site data, cache). Android does this in
the background, and its documentation warns that data saved *while* it deletes may be deleted too. Until now the app
didn't wait: if you signed out and opened the same account again at once, or removed your first account and added a
new one (the new one moves into the first one's container), the fresh page could load into a container that was still
being emptied. Now:

- The app remembers which containers are being emptied (`web/WipeGate`, new).
- `Profiles.forget` reports back when both deletions are finished (or after 10 seconds at the latest, so a missing
  answer from WebView can never lock an account).
- While that runs, opening the account shows the "Unloaded" screen instead of a page; as soon as the container is
  empty, the page loads by itself.

**Why.** Review finding 4.4 / recommendation R1, re-checked first (REVIEW.md §9.9): the race is real but narrow. The
login itself is typed seconds later, after the deletion, so the realistic damage was a half-deleted first page load,
not a lost login. Fixed because Google documents the behaviour and the fix is small and testable.

**How it was found.** `MainActivity.forgetAccount` → `Profiles.forget` started the deletions with empty callbacks
(`() -> { }`, `removeAllCookies(null)`); `AccountStore.add` reuses the id `main` (the default container) after it was
removed. LLM Central's newest branch already waits the same way (`Profiles.wipe(profile, done)`).

**Verified.** 7 new unit tests (`WipeGateTest`: closed while deleting, the account asked for meanwhile is loaded once,
two overlapping deletions open only after the last, a time limit that fires after the answers changes nothing).
Mutation checks: ignoring the overlap counter fails a test; letting the time limit run "done" a second time fails a
test; both restored. `test lint assembleDebug assembleRelease`: 92 tests pass, 0 lint issues. **Not verified:** on a
phone (how long WebView takes to delete; whether the "Unloaded" screen is noticeable at all).
Limitation: if the screen is rebuilt during the deletion (a theme change in that same second), the account loads on the
next tap instead of by itself.

**Size.** Release APK (clean build) 185,382 → **186,990 bytes** (+1.6 KB).

**What to test on your phone**

1. Add a second account and sign in to it.
2. Settings → Accounts → ⋮ on that account → **Sign out**, then at once tap **Open** on it. Expected: the "Unloaded"
   screen shows for a moment (maybe too short to see), then the NextDNS login page loads. Sign in: it must stay signed in
   after closing and reopening the app.
3. With two accounts: remove the **first** one, then at once **Add account** and open the new one. Expected: as in step 2;
   the new account starts signed out.
4. Sign out of the account on screen and do nothing: it stays on "Unloaded. Tap to load" (it must not load by itself);
   tap it: it loads.

---

## 2026-11-19 — handoff.md brought up to date, shorter start prompt

**What.** `handoff.md` had the old branch, the old APK size and "check the first CI run". Now it has today's state
(branch, build, reproducibility, CI, release workflow, open tasks, F-Droid status). The start prompt (§10) was shortened
from ~45 to ~20 lines: it no longer repeats CLAUDE.md's rules (Claude Code loads CLAUDE.md by itself, so the old prompt
made every session read it twice and kept two copies of the rules that had started to differ), no longer hard-codes
numbers that change, starts the baseline build in the background, and states what "done" means, including one commit
per feature and "facts about the website come from the live site, not memory".

**Why.** Owner request: update the handoff and make the prompt efficient.

**Verified.** Every number in §1 compared with this session's runs (85 tests, 0 lint issues, 6 page checks, 185,382
bytes, hashes, CI run list). Docs only.

---

## 2026-11-19 — New README with banner and badges in NextDNS blue

**What.** The README was rewritten in LLM Central's style: a banner, "Get it on GitHub" button, chips (Android 10+,
WebView 119+, release, APK size, licence), "Why", features in four groups, a table **"What the app adds, and what WebView
decides"** (which features depend on the phone's Android System WebView version, what stays the website's job, battery),
download, privacy, build links, credits.

- **`tools/readme-badges/`** (from LLM Central) draws the badges; `BRAND` is now NextDNS blue `#0056B3` (every colour is
  derived from it), a new **WebView 119+** chip was added, and the generator now also draws the **banner** from
  `artwork/icon.svg`, so the banner's shield can never drift from the app icon. Output: `artwork/readme/` (17 SVGs).
- F-Droid and Obtainium buttons are prepared but commented out until the app is listed.

**Why.** Owner request: badges like LLM Central's, NextDNS colours, key features and the WebView/app split highlighted.

**Verified.** Every number in the README read from the code (20 accounts, 1–10 kept loaded, default 3, lock after 60 s,
20 MB cache); three wording errors found and fixed before committing (the menu's Clear cache clears all accounts; the
dashboard following the theme is unverified on a phone, so "asks the dashboard to match"; the status request also runs
when the app opens). All 17 SVGs parse as XML; banner and badges rendered in Chromium on GitHub's light and dark
backgrounds and looked at. A first banner was empty (the generator read the icon's *comment*); fixed. **Not verified:**
how GitHub itself renders the page (it will be visible after the push).

**Size.** App unchanged.

**What to test:** open the repository page on GitHub in light and dark mode; the banner and chips should switch.

---

## 2026-11-19 — Release workflow, RELEASE.md, BUILDING.md

**What.** The app can now be released the way F-Droid wants, by pushing a tag:
- **`.github/workflows/release.yml`** (copied from LLM Central and adapted): checks the version, tag, changelog and the
  repository address shown in About; runs tests and lint; builds the unsigned APK; signs it; runs F-Droid's own
  signature check; publishes `NextDNS-Manager-Plus-<version>.apk` on GitHub. "Run workflow" by hand = a dry run that
  publishes nothing.
- **`RELEASE.md`**: the beginner's guide: what is left before the first release, making the signing key, the four
  GitHub secrets, each release step. **`BUILDING.md`**: how to build with Android Studio or the command line, and how to
  check that a build is reproducible.
- `fastlane/…/changelogs/1.txt`: the first release's notes (the workflow refuses to release without them).

**Why it differs from LLM Central's.** Tested on this app's APK: F-Droid's check (`apksigcopier compare`) **fails** with
LLM Central's exact signing command, because `apksigner` adds old-style "v1" signature files to this app. With v1
switched off it **passes**; with build-tools 35 it fails either way. Copied unchanged, the first release would have been
refused by F-Droid. Also fixed while testing: the test key's name may not contain `+` (`keytool` refuses it).

**Verified.** The workflow's own scripts, taken from the YAML and run here: the check step in four cases (good tag passes;
wrong tag, moved repository + tag fail; a fork's dry run only warns); signing with a test key, F-Droid's check and the
summary all pass; the temporary key is deleted. YAML parses. **Not verified:** a run on GitHub (it needs the file on the
default branch, a pull request, or a tag), a real key, F-Droid's build server.

**Size.** App unchanged (no app code).

**What to test:** nothing on the phone. On GitHub, after creating the key (RELEASE.md §3): Actions → Release → Run
workflow → `test`, then `real`; read the fingerprint on the summary page.

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
