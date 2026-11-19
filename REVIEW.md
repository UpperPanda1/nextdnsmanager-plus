# Review of NextDNS Manager+ (2026-11-19)

Adversarial review of the docs, the code, the previous session's audit notes, the cache design, and build /
F-Droid readiness. Compared against **LLM Central**: `UpperPanda1/assist`, newest branch
`claude/confident-brahmagupta-9iiv9x` (2026-11-16) and `master` (2026-11-14).

**How to read this:** *Verified* = I ran or read it. *Unverified* = not provable here (no phone, no emulator).
"Fixed" = changed in this working tree, not committed (CLAUDE.md: commit only when asked).

## 0. Baseline and what changed

| Check | Result |
|---|---|
| `./gradlew test lint assembleDebug assembleRelease` before changes | pass: 83 tests, 0 lint issues, release APK **185,258 B** (matches the docs) |
| After the fixes below | pass: **85 tests**, 0 lint issues, release APK 185,382 B (not a clean-build figure), page checks **6 of 6** |
| Reproducible build | 3 builds (working tree + two fresh clones at different paths) → identical SHA-256 (JDK 21 only; see `fdroid.md` §5) |
| Previous session's patch | **not in the tree and not in the upload** (only the notes). I re-checked every claim against the code (section 3) and redid the fixes that held up |

## 1. Features: what the app needs

Every feature traces to an owner request in `PROJECT.md` §2.3. Judged against "what does a DNS dashboard wrapper need":

| Feature | Verdict | Note |
|---|---|---|
| Several isolated accounts (Profiles, tabs, LRU) | **core** | the reason to use LLM Central as the base |
| Account chip, badge, manager in Settings | core | |
| Connection status icon | core | only automatic network call (`test.nextdns.io`); disclosed in Settings → Privacy |
| App lock + secure flag | core | the secure flag had a gap (fixed, 4.1) |
| Find in page, theme White/Black/System | keep | requested |
| 2FA recovery-code download (`data:` URL) | **needed** | the one thing no browser can do for the app |
| Ping window, Private DNS shortcut | optional | both from the original app; the ping window shares the `main` account's cookie jar (open decision 2) |
| Desktop site, pinch zoom, hide status bar, "accounts kept loaded" | optional | all requested; the dashboard is responsive, so *Desktop site* is the least necessary (owner's call, not removed) |
| Translations, push notifications | **regressions vs the original** | the original had 14 languages and Firebase push; dropped on purpose (`PROJECT.md` §10) |

**Leftover LLM Central code:** a reference scan of every method and string found **none** unused, apart from the items
fixed in 4.5. No AI, provider, tracker or palette code remains (verified by grep: `assistant|palette|IntroWindow` hits
were comments only, now fixed).

**Worth taking from LLM Central's newer branch:** its docs say several accounts need *Android System WebView 119+*
(checked there against Chromium tags on 2026-11-16). This repo said "unknown"; README and PROJECT.md now say 119+
(taken from that source, not re-checked). The same branch adds a `done` callback to `Profiles.wipe` (finding 4.4).

## 2. Repository address and author

| Place | Before | Now |
|---|---|---|
| About: GitHub links, repo name, "Developed by" | hard-coded in `about.html` (`UpperPanda1/nextdnsmanager-plus`, `Rafee-M`) | **read at build time**: `gradle.properties` (`app.repoUrl`, `app.author`) or `-PrepoUrl=…` → `BuildConfig` → About. Build fails on a malformed address (proven with a hostile value) |
| README credits, About "Built on" | static text | unchanged (cannot be build-time in a README) |
| LLM Central link | `UpperPanda1/assist` | **kept as you stated**; see decision A |

Why not `git remote`: AGP embeds only the commit hash; a remote URL differs per clone (SSH/HTTPS/local) and would make
the same commit build to different APKs. Proven: my two clones had a local-path origin and still matched the tree
byte for byte. **Decision A (yours):** the notes say LLM Central moved to `Rafee-M/llm-central`; I could not read that
repo (attaching it was denied), so I could not confirm it exists. The notes also say LLM Central has no `RELEASE.md`:
**`UpperPanda1/assist` has one** (read), so that claim is false for that repo and `PROJECT.md`/`handoff.md` were left
as they are. Tell me the final address and it is a one-line change in `about.html` and `README.md`.

## 3. The previous session's notes, claim by claim

| Claim | Found in the tree | Status now |
|---|---|---|
| Link `assist` → `llm-central` | not applied | **open (decision A)** |
| Stale comments (FindBar, Scheme, AboutWindow, DialogWebViewClient, PageZoom, about/settings "Material You", Site §7→§8) | all present | **fixed** (all 8) |
| `RELEASE.md` does not exist | exists in `UpperPanda1/assist` | claim **wrong** for that repo; docs left |
| Dead `shouldOverrideUrlLoading(WebView,String)`; unused CSS (`.avatar .tile .tonal-btn .icon-btn.tonal .badge.small .row.dim .field-label textarea`) | present | **fixed**; CSS removal checked by the page checks (6/6). `.field` is **used** (JS-made input) and was kept |
| `limitCache` passes `min(default, 20 MB)` | present | **fixed** (4.3) |
| FLAG_SECURE only on the main window | confirmed (`Windows.fill` sets nothing) | **fixed** (4.1) |
| `BUILDING.md` | absent | not created; `fdroid.md` §5 holds the build/reproducibility recipe |
| Finding 1: odd URL schemes → `ACTION_VIEW`, possible uncaught `FileUriExposedException` | confirmed in `MainActivity`, `PingWindow`, `DialogWebViewClient`; the catch covered only `ActivityNotFoundException`/`SecurityException` | **fixed** (4.2) |
| Finding 2: Ping window shares the default profile | confirmed (`PingWindow.page` never calls `Profiles.attach`) | **open (decision B)** |
| Finding 3: re-measure after 1.5 s | confirmed | **open, cosmetic** (4.6) |
| "No unused strings, drawables, bridge methods" | strings: confirmed (scan). `Site.LINKS["service_help"]` was unused: **missed by the notes** | removed |

## 4. Findings

### 4.1 Secure flag did not cover Settings, About, Ping (fixed) — medium, verified in code
`applySecureFlag` set `FLAG_SECURE` on the activity window only. Settings/About/Ping are separate `Dialog` windows, so
with the lock on they could be screenshotted and shown in Recents (the lock is even switched *inside* Settings).
Fix: `Windows.setSecure` applied in `Windows.fill`, and `MainActivity.applySecureFlag` updates dialogs that are open.
**Unverified on a phone:** needs the checklist item "screenshot Settings with the lock on".

### 4.2 Non-web link schemes could crash the app (fixed) — medium-low, verified in code, not reproduced
A page link such as `file:///…` went to `startActivity(ACTION_VIEW)`; on targetSdk ≥ 24 Android throws
`FileUriExposedException` (a `RuntimeException`, not caught). Needs a hostile page on `*.nextdns.io`, hence low risk.
Fix: new plain-Java `web/ExternalLinks` (https/http/mailto/tel only) used by all three handlers; `ExternalLinksTest`
(2 tests). Mutation check: adding `file` to the list makes the test fail; restored.

### 4.3 Cache cap passed the default quota (fixed) — low, verified against docs
`min(getDefaultQuotaBytes(), 20 MB)`. The `androidx.webkit.HttpCache` docs: the default "may not be a stable size across
sessions" and `useDefaultQuota()` is the way to ask for automatic; passing the default into `setQuotaBytes` is advised
against. Now a fixed 20 MB.

### 4.4 Wipe is fire-and-forget (open) — low, verified in code; LLM Central's newer branch fixed the same code
`Profiles.forget → wipe` starts asynchronous deletions and returns. "Sign out" followed within milliseconds by a new login
(or Remove `main` then Add, which reuses the id `main`) could let the deletion remove the new cookies. LLM Central's newest
branch gives `wipe` a `done` callback for exactly this reason. Suggested: same callback, and show the placeholder until
`done`. Not changed: it needs an on-device test to be sure it is not worse.

### 4.5 Status icon can say "green" while the test says "not using NextDNS" (open) — low-medium, verified in code
`StatusRules.fromPrivateDns` uses `contains("nextdns")` on the Private DNS host (a lookalike host counts as ours), and
`combine` only lets a *positive* test override; `fromTest` returns `null` both for "unconfigured" and for a broken
answer, so an explicit "unconfigured" never overrides a green Private DNS state. Also the "in use" answer format
(`status: ok`, `protocol`) is **unverified**: only "unconfigured" was ever seen live. Suggested: match the host with
`endsWith("nextdns.io")`, make an explicit "unconfigured" authoritative. Left alone: changing it without a live
"in use" sample risks a wrong red icon for real users.

### 4.6 "Clear cache" size shown before deletion finished (open, cosmetic)
`StorageBridge` re-measures once at 1.5 s; removal is asynchronous. Suggest a second measure at ~5 s.

### 4.7 Smaller points
- **Back handling relies on `onKeyDown(KEYCODE_BACK)`.** With targetSdk 35 it works; at targetSdk 36 predictive back is
  on by default and this override is, to my knowledge, no longer called. **Unverified**; test before raising targetSdk
  (`lint.xml` already says raise it only after a phone test).
- **CI:** `ubuntu-latest` is unpinned (the pages job uses 24.04); action versions (`checkout@v7`, `setup-java@v6`,
  `upload-artifact@v7`) could not be verified by me and the first CI run is still unchecked (CLAUDE.md TODO).
- **`.gitignore`** has a stray `.zip` line (matches a file literally named `.zip`) and ignores `*.keystore` while
  `app/debug.keystore` is tracked (works because it was force-added; a future `git add` of a new keystore is ignored).
- **Error text** told users to update WebView "from the Play Store": wrong for F-Droid users; reworded.

## 5. Cache management

**Design (verified in code and against LLM Central's research, `docs/superpowers/plans/2026-11-16-cache-review.md`):**
one profile per account; `LOAD_DEFAULT`; HTTP cache quota 20 MB per profile; Chromium adds two code caches at 0.5× the
quota (confirmed in `aw_content_browser_client.cc`: `0.5 × http_cache_quota`) → about 40 MB worst case per account;
"Clear cache" = `clearCache(true)` on open tabs plus a throw-away WebView per closed profile; sign-out/remove wipe
the profile; leftover `acct_` profiles deleted at start.

**Verdict: structurally sound and simpler than LLM Central's**, and the right size for this site (LLM Central measured
2–8 MB HTTP + 3–23 MB code cache per visit for large sites; the NextDNS dashboard is far smaller, "about 1.5 MB" per
`PROJECT.md`, measured on the live site 2026-11-15 — not re-measured). Points checked:

| Point | Result |
|---|---|
| Cap actually applied to every profile incl. default | yes, in `Profiles.attach` (needs `MULTI_PROFILE` + `HTTP_CACHE_MANAGER`); older WebViews use their own limit — documented |
| Cap value | now fixed (4.3) |
| Clear cache keeps logins | `clearCache(true)` removes only the cache type (Chromium `AwContents::ClearCache` → `DATA_TYPE_CACHE`, read in source); cookies/storage untouched |
| Closed accounts cleared | temp WebView per profile; the removal is issued on the browser context's `BrowsingDataRemover`, **not tied to the WebView** (read in source). Whether `destroy()` right after can cancel an in-flight removal: **unverified** (the remover's implementation was not read). Phone check: Settings → Data size before/after with a *closed* account |
| Idle accounts never cleaned (LLM Central finding C9: the total is a sum over accounts) | by design here, as in LLM Central after the owner removed its 7-day clean-up; bounded by 20 accounts × ~40 MB, realistic use ~a few MB each |
| Removed accounts | deleted, or wiped then deleted next start (handled: `deleteProfile` throws for a profile used in this run) |
| Race on wipe | 4.4 |
| Tests | the Android parts (`Profiles`) have no unit tests (stubs); `AppCache` has; rules are in the page checks and the phone list |

## 6. Code quality, tests, build

**Good:** logic in small plain-Java classes; `Site` is the only NextDNS knowledge; bridges only on local pages with
names not URLs; every WebView has an `onRenderProcessGone` handler (the throw-away cache WebView never loads a page);
`isStartUrl` host parsing held up against `@`, `:`, `\` and suffix tricks; lint = errors; dependency repos restricted;
no secrets in the tree; backup excludes logins.

**Gaps:** no unit test touches `MainActivity`/`Profiles`/`TabManager` Android behaviour (needs a device or Robolectric,
which would add a dependency — not recommended); `StatusRules` positive path untested against real data; CI unrun.

**Reproducibility:** proven for one JDK and one machine (`fdroid.md` §5). Build files read no clock, host name or
random value (the same property the notes claim; consistent with the identical hashes). Gradle wrapper is checksum
pinned.

**F-Droid:** see `fdroid.md` (not ready to submit; list of missing items and a draft recipe).

## 7. Open decisions for the owner

- **A.** Final LLM Central address (`UpperPanda1/assist` vs `Rafee-M/llm-central`) → README and About "Built on".
- **B.** Ping window: accept the shared cookie jar of account `main`, or give it its own profile (small change in
  `PingWindow`, `Profiles`; needs `MULTI_PROFILE`, so a fallback is required).
- **C.** GPL credit chain (LLM Central ← duckAssist/gptAssist): add the upstream names to README/About?
- **D.** Working files (`CLAUDE.md`, `handoff.md`, `.claude/`, this file) in the public repo: keep or remove.
- **E.** Name vs trademark (`fdroid.md` §3.2).
- **F.** Status icon rule (4.5) once a real "in use" test answer is available.

## 8. Not verified (needs a phone, an SDK host, or the other repo)

Everything on-device: lock-screen secure flag, Settings page look after the CSS clean-up, About links, Clear cache
numbers, back key at targetSdk 36, `HttpCache` quota behaviour. Also: `Rafee-M/llm-central` (unreadable), `org.json:json:20260814`
exists (it resolved in the build here, so it does exist on the mirror used), CI on GitHub, JDK 17 reproducibility.
