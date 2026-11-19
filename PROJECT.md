# NextDNS Manager+ — project guide

The one document that explains **what this app is, how it was planned, how it is built, what every file does, and
how to change each feature**. Start at the top for the big picture or jump to the part you need.

- For Claude sessions: [`CLAUDE.md`](CLAUDE.md) (working rules, commands, verified facts).
- What changed and when: [`CHANGES.md`](CHANGES.md).
- Where the last session stopped: [`handoff.md`](handoff.md).

## Contents

1. [The app in one page](#1-the-app-in-one-page)
2. [How the planning was done](#2-how-the-planning-was-done)
   - [2.1 Option 1 or option 2](#21-option-1-or-option-2)
   - [2.2 Minimum Android and WebView versions](#22-minimum-android-and-webview-versions)
   - [2.3 What was asked for, and where it is](#23-what-was-asked-for-and-where-it-is)
3. [What was reused, adapted and written new](#3-what-was-reused-adapted-and-written-new)
4. [How the app is put together](#4-how-the-app-is-put-together)
   - [4.1 The screen](#41-the-screen)
   - [4.2 The parts and who talks to whom](#42-the-parts-and-who-talks-to-whom)
   - [4.3 File map](#43-file-map)
5. [Features, one by one](#5-features-one-by-one)
   - [5.1 Top bar and three-dots menu](#51-top-bar-and-three-dots-menu)
   - [5.2 Accounts and their containers](#52-accounts-and-their-containers)
   - [5.3 Tabs: loading, switching, unloading](#53-tabs-loading-switching-unloading)
   - [5.4 Account menu](#54-account-menu)
   - [5.5 Settings](#55-settings)
   - [5.6 Theme and status bar colour](#56-theme-and-status-bar-colour)
   - [5.7 App lock](#57-app-lock)
   - [5.8 Connection status icon](#58-connection-status-icon)
   - [5.9 Find in page](#59-find-in-page)
   - [5.10 Ping test and Private DNS shortcut](#510-ping-test-and-private-dns-shortcut)
   - [5.11 Downloads](#511-downloads)
   - [5.12 Desktop site and Pinch to zoom](#512-desktop-site-and-pinch-to-zoom)
   - [5.13 Errors, crashes and links](#513-errors-crashes-and-links)
6. [Cache management](#6-cache-management)
7. [How to change things](#7-how-to-change-things)
8. [Versions and build settings](#8-versions-and-build-settings)
9. [Testing and CI](#9-testing-and-ci)
10. [Known limitations and what is not verified](#10-known-limitations-and-what-is-not-verified)
11. [AdGuard DNS Manager: the plan](#11-adguard-dns-manager-the-plan)
12. [Roadmap](#12-roadmap)

---

## 1. The app in one page

**NextDNS Manager+** is an Android app that shows the official NextDNS dashboard (`my.nextdns.io`) with a native
top bar and extras a browser doesn't have:

| Feature | In short |
|---|---|
| Several accounts | Each account is a separate NextDNS login, kept apart in its own WebView "profile" (cookies, storage, cache). Switch instantly from the top bar. |
| Account manager | One list in Settings: add, rename, drag to re-order, sign out, remove. Each account has a number badge (its place in the list). |
| Connection status | A shield/warning icon in the top bar: is this phone using NextDNS, and encrypted? |
| Find in page | Search the dashboard's text (three-dots menu, or Ctrl+F). |
| App lock | Fingerprint/face/PIN when the app opens; hidden in screenshots and recent apps. |
| Theme | White, Black or Match system; the website switches along; the status bar matches the page. |
| Account menu tools | Desktop site, Reload, Hide status bar, Clear cache, unload an account. |
| Ping test, Private DNS shortcut, downloads | From the original NextDNS Manager. |

Package `org.dnsmanager.app` (code), application id `org.rafee_m.nextdnsmanagerplus` (store identity), Java only, no UI
library, one library in total (`androidx.webkit`). Release APK 186 KB (98 KB download).

It is built so that an **AdGuard DNS Manager** can be made from the same code by changing one file
(`site/Site.java`) plus colours and texts: see [§11](#11-adguard-dns-manager-the-plan).

---

## 2. How the planning was done

### 2.1 Option 1 or option 2

Two starting points were compared (planning session, 2026-11-14):

| | Option 1: the NextDNS Manager fork | **Option 2: LLM Central (chosen)** |
|---|---|---|
| Size | 5,124 lines of Java, **no tests** | 20,184 lines, 257 unit tests, 18 page checks |
| Multi-account | none; one WebView, one cookie jar (a rewrite of its core) | done and phone-tested: one WebView profile per account, tab switching, memory limit |
| Find in page | none | done (`FindBar`) |
| Libraries | AppCompat, Material, OkHttp, Gson, Sentry, Firebase, biometric, … | `androidx.webkit` only |
| Duplicated code | `MainActivity` twice (foss/gms flavors) | — |
| Licence | GPL-3.0 | GPL-3.0 (code can move between them) |

**Decision (owner): option 2.** Strip LLM Central to what a DNS dashboard needs, then port the NextDNS-specific
features from the fork. The work happened on a new, empty (orphan) branch, so nothing of either history is mixed in.

How the stripping was decided: every LLM Central class was put in one of three piles.

- **Kept as is** (generic WebView plumbing, tested): tab LRU, renderer-crash recovery, load-error screen, desktop
  mode, pinch zoom, find bar, dialog client, script loader, About window, settings restore.
- **Rewritten smaller** (the idea kept, AI parts removed): `MainActivity` (3,033 → 914 lines), `TabManager` (duck.ai
  special cases removed), `Profiles` (cache rules extended), `AccountStore` (no providers), `SwitcherPopup`,
  `ThemeManager` (palettes and Material You removed), `settings.html`.
- **Dropped**: AI providers, duck.ai extras, assistant, share/"Ask AI", offline chats, prompt prefill, user scripts,
  tracker blocking, third-party cookie switch, sign-in pop-ups and Google/Apple sign-in user agent, palettes, Material
  You, text size, bar-at-the-bottom, first-start intro, debug request log. None of them helps a DNS dashboard; the
  owner asked for no Google/Apple sign-in (NextDNS uses e-mail + password; checked in the dashboard's code: no
  Google/Apple/captcha code at all).

### 2.2 Minimum Android and WebView versions

**minSdk 29 (Android 10). No WebView version is required**; features that need a newer WebView switch themselves off.

- Private DNS (what the status icon reads) exists since Android 9; the system unlock sheet with PIN fallback since
  Android 10 (`BiometricPrompt` + device credential); current WebView releases are only published for Android 10+.
- **Several accounts** need the WebView feature `MULTI_PROFILE`, which depends on the installed *Android System
  WebView* version, not on Android. Without it the app allows one account and says why (Settings → Accounts). The
  first WebView version with it is **119**, and it also needs multi-process WebView (from LLM Central's docs, which
  checked it against Chromium's tags on 2026-11-16; not re-checked here).
- The fork's minSdk 32 had no technical reason in its code.

### 2.3 What was asked for, and where it is

| Owner's request | Where / how |
|---|---|
| Option 2, only what is needed, NextDNS only | §2.1; [§4.3](#43-file-map) lists every file |
| No Google/Apple login | Dropped; `site/Site.DOMAINS` = `nextdns.io` only |
| Strip theming; status bar matches NextDNS dark | `theme/Themes.java`: two fixed schemes; dark surface `#212529` = the website's dark background ([§5.6](#56-theme-and-status-bar-colour)) |
| Disable swipe to refresh | There is none (no `SwipeRefreshLayout`); Reload is in the account menu |
| Account switcher: a number to tell accounts apart | `ui/AccountBadge`: round badge with the account's number, in the chip, the menu and Settings |
| Remove text zoom completely; pinch zoom as a setting | No text-size code anywhere; Settings → Behaviour → Pinch to zoom (`web/PageZoom`) |
| Keep the icon bar | Account menu: Desktop site, Reload, Hide status bar, Clear cache ([§5.4](#54-account-menu)) |
| No back button; Android's Back works | `MainActivity.onKeyDown`: find bar → page history → leave |
| Three dots: no refresh, add Find in page | `MainActivity.showOverflowMenu`: Find in page, Ping test, Private DNS settings, Settings |
| Account chip where LLM Central has it, replacing the back button | `res/layout/top_bar.xml`: chip on the left |
| Settings in LLM Central's structure, accent `#0056B3` | `assets/settings.html` + `theme/Themes.ACCENT` |
| One account manager (order, create, delete) | Settings → Accounts ([§5.2](#52-accounts-and-their-containers)) |
| Theme: White, Black, Match system; tells the website | [§5.6](#56-theme-and-status-bar-colour) |
| App lock | [§5.7](#57-app-lock) |
| No Sentry | No Sentry, no analytics, no Firebase |
| About screen from LLM Central: artwork, issue + star buttons, version | `assets/about.html` + `ui/AboutWindow`; icon = the launcher icon packaged by the build |
| Privacy policy, user agreement | Settings → About & legal (links by name, `Site.LINKS`) |
| Disclaimer and permission screens | Settings sub-screens; the permission list is checked against the manifest in CI |
| No feedback | Not included |
| Cache: clear all but logins, no ballooning, reuse on reload | [§6](#6-cache-management) |
| Lint, unit tests, CI | [§9](#9-testing-and-ci) |

---

## 3. What was reused, adapted and written new

| Origin | Files |
|---|---|
| **From LLM Central, unchanged logic** (package renamed, comments updated) | `web/TabLru`, `web/WebViewRecovery`, `web/LoadErrors` (retry part), `web/DesktopMode`, `web/PageZoom`, `web/FindInPage`, `web/DialogWebViewClient`, `web/Scripts`, `ui/FindBar`, `ui/UnloadedPlaceholder`, `ui/AboutWindow`, `theme/Scheme`, `theme/ThemeBridge`, `theme/ContentInsets`, `storage/AppCache` (size part), `assets/js/*.js`, `assets/lib/reorder.js`, Gradle wrapper, debug key, session hook |
| **From LLM Central, rewritten for this app** | `MainActivity`, `web/TabManager`, `web/Profiles`, `account/Account`, `account/AccountStore`, `account/AccountsBridge`, `ui/SwitcherPopup`, `ui/NativeTheme`, `ui/SettingsRestore`, `theme/ThemeManager`, `theme/ThemeRebuild`, `storage/StorageBridge`, `assets/settings.html`, `assets/about.html`, layouts |
| **From NextDNS Manager (the fork), as features** | connection status (`status/`), ping test (`ui/PingWindow`), Private DNS shortcut, app lock (`lock/`, now without the AndroidX biometric library), downloads (`downloads/`), privacy/terms links, disclaimer, permission list. Ideas and behaviour only: **no file of the fork is in this repository** (checked byte by byte, 2026-11-15) |
| **New** | `site/Site` (everything about the service in one file), `theme/Themes` (fixed colours), `ui/AccountBadge`, `ui/LockScreen`, `ui/SettingsWindow`, `ui/Windows`, `downloads/DataUrl`, `lock/LockRules`, `status/StatusRules`, `Prefs`, launcher icon, all page checks, CI workflow |

---

## 4. How the app is put together

### 4.1 The screen

```
┌─────────────────────────────────────────────┐  ← Android status bar (same colour as the bar below)
│ (1) Home ▾                  🛡   ⌂   ⋮      │  ← top bar: account chip · status · home · menu
├─────────────────────────────────────────────┤
│░░░░░░ loading bar (only while loading) ░░░░░│
│                                             │
│          my.nextdns.io dashboard            │  ← the current account's WebView ("tab")
│   (other loaded accounts: hidden WebViews)  │
│                                             │
├─────────────────────────────────────────────┤
│ 🔍 [search………]   3/12   ↑  ↓  ✕            │  ← find bar (only while searching)
└─────────────────────────────────────────────┘
  + over everything while locked: the lock cover (padlock, "Locked", Unlock)
```

The view tree (`res/layout/activity_main.xml`): `screen` (frame) → `mainRoot` (column: `topBar`, `tabContainer`)
+ the lock cover added at runtime. `tabContainer` holds the tab WebViews, the two black placeholder screens and the
loading bar. Settings, About and Ping test are full-screen dialogs on top (`ui/Windows`).

### 4.2 The parts and who talks to whom

```
                         MainActivity  (wiring only: creates parts, connects buttons, passes lifecycle on)
        ┌──────────────┬───────┴────────┬──────────────┬───────────────┬─────────────┐
   web/TabManager   ui/SwitcherPopup  ui/SettingsWindow  status/       lock/          downloads/
   (tabs)           (account menu)    (Settings page)    ConnectionStatus AppLock     Downloads
     │  └ web/Profiles (containers, cache)   │ bridges:       │              │ LockRules
     │  └ web/TabLru, LoadErrors, Recovery   │ AndroidSettings│ StatusRules  ui/LockScreen
     │  └ web/DesktopMode, PageZoom          │ AndroidAccounts → account/AccountsBridge → AccountStore
     └ site/Site  ← everything about NextDNS │ AndroidStorage → storage/StorageBridge
                                             │ AndroidTheme   → theme/ThemeBridge ← theme/Themes
```

Rules of the road (also in CLAUDE.md §7):

- **Logic lives in small classes**, mostly plain Java so it can be unit-tested on a computer. `MainActivity` only
  connects them. A new feature gets its own class.
- **Only `site/Site.java` knows NextDNS.** Everything else asks it (addresses, domains, test answer format, links).
- **Only `theme/Themes.java` holds colours.** Native views ask `ThemeManager.scheme()`, HTML pages get the same
  colours as CSS variables (`--md-primary` …) through `AndroidTheme.css()`.
- **Every setting is stored in one file** (`Prefs.FILE` = `app_prefs`); each feature owns its key name.
- **Bridges** (Java objects a page can call) are given only to the app's own local pages, never to the website.

### 4.3 File map

Java: `app/src/main/java/org/dnsmanager/app/` (5,836 lines). Tests: `app/src/test/java/org/dnsmanager/app/`.

| File | What it does | Tested by |
|---|---|---|
| `MainActivity.java` | The one screen: lifecycle, top bar, three-dots menu, tab set-up (`configureWebView`), page events (`TabClient`), Settings host, app lock flow | — (wiring) |
| `Prefs.java` | The settings file's name and shared keys (`max_live_tabs`, `last_account`) | — |
| **site/** | | |
| `Site.java` | Everything about NextDNS: name, home/test/ping addresses, owned domains, test answer format, legal links, script origin rules | `SiteTest` |
| **account/** | | |
| `Account.java` | One account: id + name; `profileName()` (`acct_<id>`, or the default profile for id `main`) | `AccountStoreTest` |
| `AccountStore.java` | The account list and all its rules (add, rename, remove, reorder, numbering, limits, safe ids); saved as JSON | `AccountStoreTest` |
| `AccountsBridge.java` | `AndroidAccounts` for the Settings page; every call answers with the fresh list | page check `settings-accounts` |
| **web/** | | |
| `TabManager.java` | One WebView per loaded account, show/unload/switch, retry screen, renderer-crash recovery, `isStartUrl`; no page while the account's container is being emptied (`loadAfterWipe`) | `TabManagerTest` |
| `WipeGate.java` | Which containers are being emptied (counter per container), which account to load when done, `Join` (run once after both deletions or the time limit) | `WipeGateTest` |
| `TabLru.java` | Which loaded tab closes first when over the limit | `TabLruTest` |
| `Profiles.java` | Account containers (WebView profiles), cache cap, Clear cache for all, sign-out/remove wipe (reports when done, `WipeGate`), start-up clean-up | — (Android-only; rules in [§6](#6-cache-management)) |
| `LoadErrors.java` | When the "Tap to retry" screen comes and goes | `LoadErrorsTest` |
| `WebViewRecovery.java` | Replace/discard a WebView whose renderer died | — |
| `DesktopMode.java` | Desktop site per account (user agent, client hints, viewport script) | `DesktopModeTest` |
| `PageZoom.java` | Pinch to zoom (lifts the page's zoom ban at document start) | page check `zoom-viewport` |
| `FindInPage.java` | Ctrl+F detection, highlight CSS, "3/12" text | `FindInPageTest` |
| `DialogWebViewClient.java` | Client for the app's own pages: links to the browser, renderer death closes the window | `DialogWebViewClientTest` |
| `ExternalLinks.java` | The one place that hands a link to another app; only https/http/mailto/tel pass (a `file:` link would crash the app) | `ExternalLinksTest` |
| `Scripts.java` | Loads `assets/js/*.js` | `ScriptsTest` |
| **theme/** | | |
| `Themes.java` | The two colour schemes (all colours of the app) | `ContrastTest` |
| `Scheme.java` | A scheme object + its CSS | `SchemeTest` |
| `ThemeManager.java` | White/Black/Match system setting, night override, window/status-bar colours, Hide status bar, insets | — |
| `ThemeRebuild.java` | When a theme change rebuilds the screen | `ThemeRebuildTest` |
| `ThemeBridge.java` | `AndroidTheme.css()` for local pages | page check `settings-dark` |
| `ContentInsets.java` | Padding rule with/without the status bar | `ContentInsetsTest` |
| **ui/** | | |
| `SwitcherPopup.java` | The account menu | — (view code) |
| `AccountBadge.java` | Numbered badge | `AccountBadgeTest` |
| `NativeTheme.java` | Colours top bar, toggles, loading bar | — |
| `SettingsWindow.java` | Opens Settings, `AndroidSettings` bridge, Back handling | page checks `settings-*` |
| `SettingsRestore.java` | Reopen Settings after a rebuild; safe section names | `SettingsRestoreTest` |
| `AboutWindow.java` | Opens About with name/version/icon | page check `about` |
| `PingWindow.java` | Ping test window (two pages) | — |
| `LockScreen.java` | The lock cover | — |
| `FindBar.java` | The find bar | — |
| `UnloadedPlaceholder.java` | Black "Tap to load" / "Tap to retry" screens | — |
| `Windows.java` | Makes a dialog full-screen; `setSecure` = the app lock's "secure" mark for one dialog (call right after `show()`) | — |
| **lock/** | | |
| `AppLock.java` | Setting + Android's unlock sheet | — |
| `LockRules.java` | When to lock (fresh start, away > 1 minute) | `LockRulesTest` |
| **status/** | | |
| `ConnectionStatus.java` | Listens to the network, runs the test request, paints the icon | — |
| `StatusRules.java` | Private DNS + test answer → state and colour | `StatusRulesTest` |
| **downloads/** | | |
| `Downloads.java` | data: files (2FA recovery codes) saved via MediaStore; http(s) files handed to the browser | `DownloadsTest` |
| `DataUrl.java` | Decodes `data:` addresses | `DataUrlTest` |
| **storage/** | | |
| `StorageBridge.java` | `AndroidStorage` for Settings: measure / clear cache | page check `settings-options` |
| `AppCache.java` | Cache folder size | `AppCacheTest` |

Other files:

| File | What it is |
|---|---|
| `app/src/main/assets/settings.html` | The Settings screen (HTML + CSS + JS in one file) |
| `app/src/main/assets/about.html` | The About screen |
| `app/src/main/assets/js/zoom_viewport.js`, `desktop_viewport.js`, `find_highlight.js` | Scripts added to the dashboard |
| `app/src/main/assets/lib/reorder.js` | Drag-to-reorder for the account list |
| `app/src/main/res/layout/activity_main.xml`, `top_bar.xml` | The screen and the top bar |
| `app/src/main/res/values/strings.xml` | Texts of the native parts |
| `app/src/main/res/drawable/ic_*.xml` | Icons (Material Icons, vector) |
| `app/src/main/res/mipmap-anydpi/ic_launcher.xml` + `drawable/ic_launcher_*.xml` | Launcher icon (own design: white shield with a "+" on `#0056B3`; vector adaptive icon, no PNGs needed at minSdk 29). The two `drawable` files are **generated** from `artwork/icon.svg` |
| `app/src/main/res/xml/*backup*.xml` | Backup: settings yes, logins/site data never |
| `app/lint.xml` | The lint warnings accepted on purpose, with reasons |
| `artwork/icon.svg` | **The app icon's source**: edit this to change the icon everywhere ([§7](#7-how-to-change-things)) |
| `tools/icon/generate.mjs` | Makes the launcher icon files and the store icon from `artwork/icon.svg` |
| `fastlane/metadata/android/en-US/images/icon.png` | 512 × 512 store icon (F-Droid), generated |
| `tools/page-checks/` | Browser checks of the HTML pages (README inside) |
| `.github/workflows/ci.yml` | CI |
| `.github/workflows/icon.yml` | "App icon" workflow: regenerates the icon files from the SVG (by hand) or checks them (on push) |
| `.github/workflows/release.yml` | Release: on a `v*` tag builds unsigned, signs with apksigner 34 (v1 off), runs F-Droid's `apksigcopier compare`, publishes; dry runs by hand or on PRs (`RELEASE.md`) |
| `fastlane/metadata/android/en-US/changelogs/<versionCode>.txt` | Release notes / F-Droid "What's new" (≤ 500 characters; the release workflow requires it) |
| `BUILDING.md`, `RELEASE.md`, `fdroid.md` | How to build; how to release and sign; the F-Droid submission |
| `tools/readme-badges/` → `artwork/readme/` | README banner + badges (light/dark), NextDNS blue `#0056B3`; ported from LLM Central. Run after a version change (needs a release build for the APK size) |
| `.claude/hooks/session-start.sh` | Sets up the Android SDK in Claude's cloud sandbox |

---

## 5. Features, one by one

Each part says what the user sees, how it works, and where to change it.

### 5.1 Top bar and three-dots menu

- **Sees:** chip `(n) Name ▾` on the left; status icon, home, three dots on the right. No back button: Android's Back
  goes back inside the page (find bar first, then page history, then leaves the app).
- **Three dots:** Find in page · Ping test · Private DNS settings · Settings. Refresh is not here (it is Reload in the
  account menu).
- **Code:** layout `res/layout/top_bar.xml`; buttons in `MainActivity.setupTopBar`; menu in
  `MainActivity.showOverflowMenu` (each item's id is its string resource); colours `ui/NativeTheme.styleTopBar`.
- **Fits 360 dp:** chip name max 120 dp + three 48 dp buttons (comment in `top_bar.xml`).

### 5.2 Accounts and their containers

- **Sees:** Settings → Accounts is the whole manager: rows `[n] Name  ⋮  ═`. Tap = open it. ⋮ = Open, Rename,
  Sign out, Remove (greyed out for the last account). ═ = drag (or arrow keys) to re-order. "Add account" below.
- **Rules** (`account/AccountStore`): at least one account; at most 20; the list order = menu order = badge numbers;
  names cleaned (spaces, 40 characters, whole emoji); ids are safe profile names (`main` or `a-xxxxxxxx`).
- **Containers** (`web/Profiles`): account `main` uses WebView's default profile; others `acct_<id>`. A profile holds
  that account's cookies (login), storage and cache, apart from all others.
- **Sign out** deletes the account's login and site data (the account stays). **Remove** also deletes the account and,
  at the next start, its folder. Both close the account's tab first (WebView must not use the data while it goes).
- **Old WebView** (no `MULTI_PROFILE`): only one account; "Add account" is hidden and a hint explains why.
- **Flow of a change:** page → `AndroidAccounts.x()` (`AccountsBridge`) → `AccountStore` (rules) → saved → main
  thread: `TabManager.updateAccounts` (close removed tabs, new names, top bar) → the page redraws from the returned list.

### 5.3 Tabs: loading, switching, unloading

- Each loaded account = one WebView in `tabContainer`; only the current one is visible; hidden ones are paused.
- **Accounts kept loaded** (Settings → Behaviour, 1–10, default 3): over the limit, the account used longest ago is
  closed (`TabLru`). A closed account keeps its login; picking it loads it again (from the cache, see §6).
- **Unload** (✕ on a loaded row in the account menu) frees one account's memory now; if it was on screen, the black
  "Unloaded. Tap to load" screen shows.
- **Start:** the account used last (`Prefs.LAST_ACCOUNT`); after a screen rebuild, the same account **and page**
  (`STATE_URL`, accepted only for `https://` pages of NextDNS: `TabManager.isStartUrl`).
- **Low memory** (`onTrimMemory`): hidden tabs close.

### 5.4 Account menu

Opened from the chip (`ui/SwitcherPopup`):

1. One row per account: badge with its number, name, ✕ if loaded. The current one is highlighted.
2. Icon row: **Desktop site** (this account; reloads), **Reload page**, **Hide status bar** (toggle; swipe down to peek),
   **Clear cache** (all accounts; logins stay; same action as in Settings).
3. **Manage accounts** → Settings scrolled to Accounts.

What each tap does is in `MainActivity.switcherHost`.

### 5.5 Settings

`assets/settings.html`, opened by `ui/SettingsWindow` as a full-screen window. Sections:

| Section | Rows |
|---|---|
| Accounts | the account manager (§5.2) |
| Appearance | Theme: Match system / White / Black |
| Behaviour | Pinch to zoom · Accounts kept loaded (1–10) |
| Security | App lock |
| Data | Clear cache (shows the current size) |
| About & legal | About · Privacy (what the app stores/sends) · NextDNS privacy policy · NextDNS user agreement · Permissions · Disclaimer |

- Privacy, Permissions and Disclaimer are sub-screens inside the page (`openSubView('name')`); Back returns.
- Links to NextDNS pages are sent **by name** (`openLink('service_privacy')`) and looked up in `Site.LINKS`, so the
  page can't open arbitrary addresses.
- **Back:** closes an open dialog → cancels a drag → leaves a sub-screen → closes Settings (`window.handleBack`).
- Rebuilt screen: Settings reopens at the same section (`ui/SettingsRestore`).

### 5.6 Theme and status bar colour

- **Setting:** White, Black, Match system (`ThemeManager.MODE_LIGHT/DARK/SYSTEM`, key `theme_mode`).
- **Colours:** `theme/Themes.java`. Light background white, dark background `#212529` (the NextDNS website's own dark
  background, read from its CSS), accent `#0056B3`. Top bar, window and Android's status/navigation bars all use the
  scheme's `surface`, so the status bar always matches the page (`ThemeManager.styleWindow`). Contrast is checked by
  `ContrastTest` (WCAG 4.5:1 for text).
- **How the website follows:** the dashboard picks light/dark with `prefers-color-scheme` unless the user chose a
  theme inside the dashboard (stored in its `localStorage` key `theme`; seen in its code). WebView answers
  `prefers-color-scheme` from the **app's theme**, so the app forces night mode on its own screen
  (`ThemeManager.nightOverride` in `attachBaseContext`) and the manifest theme `Theme.DeviceDefault.DayNight`
  follows it. Nothing is injected into the page.
- **When the screen rebuilds** (`theme/ThemeRebuild`): the theme is fixed when the screen is built, so a change
  (Settings, or the phone switching day/night under Match system) rebuilds it, never under open Settings. The
  current account and page come back.

### 5.7 App lock

- **Sees:** with App lock on, opening the app shows a cover and Android's unlock sheet (fingerprint, face, or the
  phone's PIN/pattern/password). Cancel leaves the cover with an Unlock button; Back leaves the app. The app also
  becomes "secure": blank in recent apps, no screenshots. Every dialog is its own window and gets the mark too
  (`ui/Windows.setSecure`: Settings, About, Ping test via `Windows.fill`; the status dialog in `showStatusDialog`), and
  `applySecureFlag` updates open dialogs when the lock is switched; pop-up menus inherit it from the main window.
  While the cover shows, the content under it (`mainRoot`) is also hidden from accessibility services and keyboard
  focus (`ui/LockScreen.covers`; a cover only hides from eyes and fingers), and `openSettings` refuses to open.
- **When** (`lock/LockRules`): at a fresh start, and after more than **1 minute** out of sight. A quick switch to
  another app doesn't ask again.
- **Switching it on or off** needs the phone's unlock first (nobody locks themselves out; only the owner turns it
  off). The switch in Settings only moves after Android confirmed (`window.onAppLock`).
- **No screen lock on the phone:** it can't be switched on (message). If the screen lock is removed later, the app
  opens with a message (only the phone's owner can remove it, so nothing is left to protect with).
- **Code:** `lock/AppLock` (framework `BiometricPrompt`, no library), `ui/LockScreen`, `MainActivity.lock/unlock/
  promptUnlock/changeAppLock`.

### 5.8 Connection status icon

| Icon | Meaning |
|---|---|
| green shield ✓ | this phone uses NextDNS, encrypted (DoH/DoT/DoQ) |
| orange ⚠ | uses NextDNS, unencrypted |
| yellow ⚠ | Private DNS is on, but another service |
| red ⚠ | not using NextDNS |
| grey ⚠ | checking (first moment after start) |

- **How** (`status/ConnectionStatus` + `status/StatusRules`): Android's Private DNS state at once, then a request to
  `test.nextdns.io` (it redirects to a random subdomain to force a fresh DNS lookup and answers JSON). A positive test
  answer wins, so router/VPN set-ups show correctly. Runs only while the app is visible; a newer network change
  discards an older answer.
- **Tap** = explanation + "Private DNS settings" button.

### 5.9 Find in page

Three-dots → Find in page, or Ctrl+F on a keyboard. The bar at the bottom uses WebView's own find (Chromium's engine);
matches are coloured with the accent where the WebView supports `::search-text` (WebView 144+). Back closes the bar.
Code: `ui/FindBar`, `web/FindInPage`, `assets/js/find_highlight.js`.

### 5.10 Ping test and Private DNS shortcut

- **Ping test** (`ui/PingWindow`): `ping.nextdns.io` (server speed) above `test.nextdns.io` (what NextDNS sees), in a
  full-screen window, default profile, no login. Links outside NextDNS open in the browser.
- **Private DNS settings:** Android has no direct screen for it; the app opens the network settings
  (`Settings.ACTION_WIRELESS_SETTINGS`) where it sits.

### 5.11 Downloads

WebView doesn't download anything by itself: without `downloads/Downloads` a download button does nothing.

| What | How |
|---|---|
| **2FA recovery codes** (a `data:` link: the codes are inside the link, checked in the dashboard's code) | `downloads/DataUrl` decodes it; saved to Downloads via MediaStore as "NextDNS download.txt" (WebView doesn't pass the page's own file name). Only the app can do this: no browser could fetch a link the page made itself. **This is the reason the download code exists.** |
| A file on a server: Apple configuration profile, log export | opened in the phone's **browser**, which downloads it. These need no login (owner, 2026-11-15), so the app doesn't download them itself. |

No storage permission is needed on Android 10+.

### 5.12 Desktop site and Pinch to zoom

- **Desktop site** (per account, account menu): computer user agent and client hints, page width 1024 px. The tab
  reloads. `web/DesktopMode`.
- **Pinch to zoom** (Settings, all accounts, off by default): the dashboard forbids zooming (`maximum-scale=1`);
  `assets/js/zoom_viewport.js` lifts that at document start; open tabs reload once. `web/PageZoom`. There is no text
  size setting (removed on purpose).

### 5.13 Errors, crashes and links

- **Page can't load** (offline, server down): black "Couldn't load this page. Tap to retry" screen until a page is
  really drawn (`web/LoadErrors`).
- **Renderer crash** (WebView's page process died): a hidden tab closes; the visible one is rebuilt on the same page;
  three crashes within a minute → closed with a message (`TabManager.onRendererGone`). Every WebView of the app
  handles this, or Android would end the whole app.
- **Links:** `https` pages on `nextdns.io` (and subdomains) stay in the app; everything else (help pages on other
  sites, mail links, PayPal…) opens in the phone's apps. Frames inside the page are never redirected.
  Third-party cookies are off.

---

## 6. Cache management

The owner's requirements: Clear cache empties every account's cache but keeps all logins; the cache must not grow
without bound; reloading must reuse the cache instead of storing copies.

**Where things live** (per account profile, inside `app_webview/` and `cache/WebView/`):

| Data | Cleared by "Clear cache"? | Cleared by "Sign out" / "Remove"? |
|---|---|---|
| Cookies (= the login) | **no** | yes |
| Site storage (localStorage, IndexedDB) | no | yes |
| HTTP cache (the site's scripts, styles, fonts, images) | **yes** | yes |
| Compiled-code cache | yes (part of the cache) | yes |

**The rules and where they are enforced:**

1. **One cache per account, kept for the account's lifetime.** Each profile has one cache folder; unloading a tab,
   reloading it or restarting the app reuses the same folder, so nothing is stored twice. The app never clears the
   cache by itself. Tabs use `WebSettings.LOAD_DEFAULT` = normal browser caching (`MainActivity.configureWebView`).
2. **Reuse is real** (checked 2026-11-15 on the live site): the dashboard's scripts and styles have content-hashed
   names and `Cache-Control: public, max-age=31536000` (one year), so a reload takes them from the phone without
   asking the server; the HTML page itself is `max-age=0, must-revalidate` with an `ETag`, so it is checked with a
   small request and answered "unchanged" (304) when it is.
3. **A cap per account:** `Profiles.MAX_CACHE_BYTES` = 20 MB HTTP cache (WebView deletes the oldest entries itself;
   the dashboard needs about 1.5 MB). The code caches are half that each, so one account uses at most about 40 MB.
   Set in `Profiles.attach` for every profile, the default one included (needs WebView feature `HTTP_CACHE_MANAGER`;
   older WebViews use their own limit). A fixed number, not `min(default, 20 MB)`: the androidx docs say the default
   "may not be a stable size across sessions" and advise against feeding it back into `setQuotaBytes` (checked in the
   1.17.1 sources). The quota is kept by WebView across restarts; values outside its range are clamped.
4. **Clear cache clears all accounts** (`Profiles.clearAllCaches`): open tabs via `clearCache(true)` (Chromium: cache
   only, that WebView's profile); every other profile through a short-lived invisible WebView put into it. Called by
   both the account menu icon and Settings.
5. **Removed accounts leave nothing:** the profile is deleted (or wiped if in use, then deleted at the next start by
   `Profiles.cleanUp`). The wipe runs in the background (the androidx docs: deletion "may also delete data that is
   stored during deletion"), so **no page loads into a container while it is being emptied** (2026-11-19, REVIEW.md
   R1): `Profiles.forget(account, done)` marks the container in `web/WipeGate` until both deletions (storage, cookies)
   report back (or 10 s pass); `TabManager.show` meanwhile shows the "Unloaded" screen and `loadAfterWipe` loads the
   account when the gate opens, if it was asked for and is still on screen. Matters for "Sign out" then open again,
   and for "Remove the first account" then "Add", which reuses its (default) container.
6. The Settings row shows the total cache size (`storage/AppCache.sizeBytes`, the same figure as Android's App info).

**Not verified on a phone:** the effect of the 20 MB cap and the clearing (no device here). How to check on a phone:
Settings → Data shows the size before/after Clear cache; Android's App info → Storage → Cache should match.

---

## 7. How to change things

**Change a text:** native parts → `app/src/main/res/values/strings.xml`; Settings → `assets/settings.html`; About →
`assets/about.html`. The service's name in pages comes from `Site.NAME` (elements with class `svc`).

**Change the app icon** (launcher, themed icon, About screen and store icon all follow):

1. Edit `artwork/icon.svg` (any editor, e.g. Inkscape or a text editor). Keep its rules, written at the top of the
   file: a 108 × 108 canvas, `<rect id="background">` with one colour, `<g id="foreground">` with `<path>`s only
   (optionally `transform="translate(x y) scale(s)"`), everything important within the middle 66 × 66.
2. Generate the files, either way:
   - **GitHub:** Actions → **App icon** → Run workflow → pick your branch. It generates the files, commits them to
     that branch and starts CI, whose artifact has an APK with the new icon. The run's artifact `icon-files` has
     `preview.png` (circle, rounded square, themed).
   - **Locally:** `cd tools/icon && npm install && npx playwright install chromium && npm run generate`, then look at
     `tools/icon/preview.png` and commit the changed files.
3. A push that changes the SVG without the generated files fails the App icon check, so they can't drift apart.

The generator refuses a drawing that reaches outside the safe zone or uses something Android's vector format can't
take (it says what). The About screen needs nothing: it draws the launcher icon.

**Change a colour:** `theme/Themes.java` (both schemes), then the fallback values at the top of `settings.html` and
`about.html` (used only outside the app), then `res/values*/colors.xml` (start-up only). Run the tests:
`ContrastTest` fails if text gets unreadable.

**Add a simple switch to Settings** (example: "Keep screen on"):

1. `settings.html`: copy the "Pinch to zoom" row, new input id `keep_screen_on`.
2. Same file, `loadSettings()`: `document.getElementById('keep_screen_on').checked = !!settings.keep_screen_on;` and
   under `DOMContentLoaded`: on `change` call `S.setKeepScreenOn(e.target.checked)`.
3. `ui/SettingsWindow.Bridge.getSettingsJson`: `o.put("keep_screen_on", prefs.getBoolean("keep_screen_on", false));`
   and a `@JavascriptInterface public void setKeepScreenOn(boolean on)` that saves it and calls a new `Host` method.
4. `MainActivity.settingsHost`: implement that method (apply the setting).
5. Add the row to a page check (`tools/page-checks/settings-options.mjs`), run tests and checks.

**Add a three-dots menu item:** a string in `strings.xml`, add it to the array in `MainActivity.showOverflowMenu`,
handle it in the same method.

**Add an account-menu icon:** a vector icon in `res/drawable/`, a method in `SwitcherPopup.Host`, a button in
`SwitcherPopup.show` (`addEvenly(icons, iconButton(...))`), implement it in `MainActivity.switcherHost`.

**Add a Settings sub-screen:** copy the `<div id="disclaimerView" hidden>` block with a new id `xyzView`, add a row
with `onclick="openSubView('xyz')"`.

**Add a permission:** add it to `AndroidManifest.xml` **and** a row to Settings → Permissions
(`#permissionList`, `data-permission="…"`); the page check fails if the two differ.

**Change the repository address or author** (About screen links, repository name, "Developed by"): `app.repoUrl` /
`app.author` in `gradle.properties`, then commit and tag. Read at build time (`app/build.gradle` → `BuildConfig` →
`ui/AboutWindow` → `about.html`); the build fails on a malformed value; GitHub addresses only. `-PrepoUrl=…` is for test
builds only, **never** a release (F-Droid builds without it: `fdroid.md` §4). Also change the static mentions in
`README.md` and the F-Droid recipe.

**Change the README banner or badges:** `tools/readme-badges/generate.mjs` (`BRAND` = one hex re-colours everything;
`WEBVIEW_FOR_ACCOUNTS`; texts of the banner in `banner()`); then `cd tools/readme-badges && npm ci && npm run generate`
after a release build, and look at the README in light and dark. Never edit `artwork/readme/*.svg` by hand.

**Change the cache cap:** `Profiles.MAX_CACHE_BYTES`. **Change the lock grace time:** `LockRules.GRACE_MS`.
**Change the account limit:** `AccountStore.MAX_ACCOUNTS`.

**Add a script for the dashboard:** a file in `assets/js/`, a constant + `ALL` entry in `web/Scripts.java`
(`ScriptsTest` checks both), add it where needed (document-start scripts use `Site.originRules()`).

---

## 8. Versions and build settings

| Setting | Value | Why |
|---|---|---|
| minSdk | 29 (Android 10) | §2.2 |
| targetSdk | 35 | raising it changes Android behaviour; test on a phone first |
| compileSdk | 36 | |
| Android Gradle Plugin / Gradle | 8.13.2 / 8.14.5 (checksum pinned) | same as LLM Central |
| Java | source/target 11; builds with JDK 17 or 21 | |
| Libraries | `androidx.webkit:webkit:1.17.1`; tests: JUnit 4.13.2, `org.json` | |
| Release | R8 shrinking + resource shrinking, no dependency metadata (reproducible) | |
| applicationId | `org.rafee_m.nextdnsmanagerplus` (debug: `.debug` suffix, "NextDNS Manager+ (Debug)") | decide before the first public release, then never change |
| Lint | every warning fails the build; accepted ones in `app/lint.xml` with reasons | |
| Repository address, author | `gradle.properties` `app.repoUrl`, `app.author` → `BuildConfig.REPO_URL` / `AUTHOR` | committed, not `git remote`: same APK from every clone (§7) |
| Reproducible | same SHA-256 from different clone paths and from JDK 17 and 21 (review 2, 2026-11-19, `fdroid.md` §5) | F-Droid |

---

## 9. Testing and CI

| Layer | What | Command |
|---|---|---|
| Unit tests | 92 JUnit tests of the plain-Java rules (accounts, site, status, lock, colours/contrast, theme rebuild, downloads/data URLs, tabs LRU, start URLs, load errors, desktop mode, find, scripts, cache size, insets, badge, external links, wipe gate) | `./gradlew test` |
| Lint | Android lint, warnings = errors | `./gradlew lint` |
| Builds | debug + release (R8) | `./gradlew assembleDebug assembleRelease` |
| Page checks | 6 Chromium checks of Settings, About and the zoom script with stand-in bridges (accounts flow, options, permissions = manifest, dark colours = `Themes.java`, 360 px width, About links) | `tools/page-checks` (README) |
| CI | `.github/workflows/ci.yml`: job `android` (all of the above + APK artifact + size, `ubuntu-latest`) and job `pages` (page checks + screenshots, `ubuntu-24.04`: Playwright 1.56.1 does not support `ubuntu-latest` = 26.04) on every push and PR; passed for `cc867de` | GitHub Actions |
| Icon check | `.github/workflows/icon.yml`: on a push touching the icon, regenerates the vector files from `artwork/icon.svg` and fails if they differ | GitHub Actions |
| Release | `.github/workflows/release.yml`: version/tag/changelog/repository-address check, tests, lint, unsigned build, signing, F-Droid signature check; publishes only for a tag. Scripts tested locally 2026-11-19; not yet run on GitHub | GitHub Actions |

Proven to catch mistakes (mutation checks, 2026-11-15): loosening the domain rule fails `SiteTest` and
`TabManagerTest`; an undeclared permission fails `settings-permissions`; a renamed theme option fails
`settings-options`; the strict Base64 rule was found by `DataUrlTest`.

**What no automated test covers:** anything on a real phone (WebView profiles, unlock sheet, saving to Downloads,
status bar colours, cache size). See the phone checklist in `CHANGES.md`.

---

## 10. Known limitations and what is not verified

- **Not tested on a phone or emulator** (none available to Claude). Everything Android-side is verified only by
  compiling, lint, and reading the Android/Chromium behaviour it relies on.
- **Payments:** PayPal (referenced by the dashboard) opens its checkout as a pop-up, which goes to the browser, not
  the app. Paying may need a browser. Not tested.
- **data: downloads** are named "NextDNS download.txt", not the page's name (WebView doesn't pass it).
- **Translations:** English only for now (the fork had 14 languages; its texts don't fit the new screens).
- **No push notifications** (the fork's Firebase notifications were dropped with Firebase).
- **`MULTI_PROFILE` minimum WebView version** 119 per LLM Central's docs (not re-checked here); the app checks at runtime.
- **Status protocol list** `DOH, DOT, DOQ, DOH3`: the first three are from the fork; `DOH3` is an assumption.
- **The test answer for "using NextDNS"** (`status: ok`, `protocol`) is from the fork's code; only the
  "unconfigured" answer was seen live (this sandbox doesn't use NextDNS).
- **The website following a forced White/Black** relies on WebView answering `prefers-color-scheme` from the
  app's theme (documented WebView behaviour for apps targeting Android 13+, which this app does; the same approach
  is used in LLM Central). Not seen on a phone for this app yet.
- **CI** has not run on GitHub yet (nothing pushed); the commands were run locally.

---

## 11. AdGuard DNS Manager: the plan

Same code, different `site/Site.java`. What changes (also listed for Claude in CLAUDE.md §8):

| Area | NextDNS | AdGuard DNS (to check when building it) |
|---|---|---|
| `Site.NAME`, `HOME_URL` | NextDNS, `https://my.nextdns.io/` | AdGuard DNS, `https://adguard-dns.io/…/dashboard/` |
| `Site.DOMAINS` | `nextdns.io` | `adguard-dns.io` **plus its login domain(s)** (AdGuard's account pages) |
| Sign-in | e-mail + password on the site | AdGuard offers Google/Apple sign-in → port LLM Central's `provider/SignInHosts` + `web/SignInUserAgent` (Google blocks WebView sign-in without them) and add the sign-in domains |
| Status test | `test.nextdns.io` JSON | AdGuard has its own check page; adapt `StatusRules.fromTest` or use Private DNS only (`PRIVATE_DNS_MARKER` = `adguard-dns`) |
| Ping | `ping.nextdns.io` | replace or hide the menu item |
| Legal links | `Site.LINKS` | AdGuard's |
| Colours | `Themes.java` (NextDNS blue, `#212529`) | AdGuard's green and its dark background (read from its CSS like here) |
| Name, id, icon | `strings.xml app_name`, `applicationId`, `artwork/icon.svg` (+ generator) | new ones (a different applicationId = a separate app) |
| Texts naming the service | `.svc` spans fill in by themselves | check `strings.xml` and the Privacy/Disclaimer texts |

Recommended route when it starts: make the service a Gradle **product flavor** (`nextdns`, `adguard`), each with its
own `Site.java`, `Themes.java`, icon and strings in `src/<flavor>/`, so one codebase builds both apps.

---

## 12. Roadmap

1. Owner's phone test (checklist in `CHANGES.md`), fix findings.
2. First commit and push; let CI run on GitHub; scan its log for warnings.
3. README screenshots, store texts, release signing and a release workflow (LLM Central's `release.yml` and
   `RELEASE.md` are the model).
4. Translations.
5. AdGuard DNS Manager (§11).
