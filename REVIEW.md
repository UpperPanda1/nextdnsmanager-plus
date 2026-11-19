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
| After the fixes below | pass: **85 tests**, 0 lint issues, release APK 185,382 B (confirmed as the clean-build figure by review 2, §9), page checks **6 of 6** |
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
  *Review 2:* superseded — CI has run and passed for this commit; the runner split is deliberate (§9.2).
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

---

## 9. Review 2 (2026-11-19): verification of commit `cc867de` and a second adversarial pass

Done by a second session. Same rules: *verified* = run or read here; nothing was run on a phone. Code was **not**
changed in review 2 (the owner asked for a review); only docs and one comment in `gradle.properties` were changed.

### 9.1 Baseline, re-run

| Check | Result |
|---|---|
| `./gradlew clean test lint assembleDebug assembleRelease` at `cc867de` | BUILD SUCCESSFUL; **85 tests**, 0 failures; lint "No issues found" |
| Test count history | `@Test` count per commit: `5d7aa23` 83, `4fe69d6` 83, `cc867de` 85 — so "83 → 85" in §0 is right. CLAUDE.md §11 said the *first version* had 85 (wrong; fixed) |
| Page checks | 6 of 6 |
| Release APK (clean build) | **185,382 bytes**, SHA-256 `4e287525…18554a` |
| Reproducible | identical hash from the working tree, two fresh clones at different paths (JDK 21) **and a clone built with JDK 17** (open point (a) of `fdroid.md` §5 is now closed) |
| `-PrepoUrl` override | changes the APK (`58da7961…`); a hostile value (`https://evil.example/x"y`) stops the build — the validation works |
| CI on GitHub | **has run**: run 6 (`cc867de`) passed both jobs. §4.7 / §6 "CI unrun" were already out of date |

### 9.2 Review 1's claims, checked

| Claim (section) | Verdict | Evidence |
|---|---|---|
| Newest LLM Central branch is `claude/confident-brahmagupta-9iiv9x` (header) | **right** | `git ls-remote`: three branches; that one's head is 2026-11-16 15:25; `laughing-johnson-7mbk6s` stops at 2026-11-14, `master` at 2026-11-14 |
| 4.1 secure flag on Settings/About/Ping | **right, fixed**; small gap left | `Windows.fill` runs after `dialog.show()`; review 2 first thought the first frame(s) were drawn before `FLAG_SECURE` is set — **wrong** (§9.9: drawing is scheduled for the next frame, the flag is in place by then). Not covered: the status `AlertDialog`, the account `PopupWindow`/`PopupMenu` and the website's own JS `alert`/`confirm` boxes (shows account names / page text). Recommendation R3 |
| 4.2 `ExternalLinks` safe list | **right, fixed** | the three handlers use it; test + mutation check described. Nit: `downloads/Downloads.start` still builds its own `ACTION_VIEW` (safe: http(s) only), so "the one place" in its Javadoc is not literally true |
| 4.3 quota: docs advise against `setQuotaBytes(getDefaultQuotaBytes())` | **right** (quote verified word for word in the androidx.webkit **1.17.1 sources jar**: "If you wish to use automatic quota management, call useDefaultQuota() instead of supplying the output of getDefaultQuotaBytes()", "The default quota may not be a stable size across sessions") | the same docs: a quota "will take immediate effect, persist across restarts"; values outside WebView's range are clamped. LLM Central still has the old `min(...)` (worth back-porting there) |
| 4.4 wipe is fire-and-forget; a race is possible | **right, and now documented by Google**: `WebStorageCompat.deleteBrowsingData` Javadoc: "Deletion is not an atomic operation, so it may also delete data that is stored during deletion" | realistic trigger is narrow (the user must close Settings and tap the account within the deletion time). Recommendation R1 |
| 4.5 status icon rules | right (not re-tested against a live "in use" answer) | — |
| 4.7 `ubuntu-latest` "unpinned" | **misjudged**: deliberate | `ubuntu-latest` is now Ubuntu 26.04; App icon run 1 failed with "Playwright does not support chromium on ubuntu26.04-x64". The owner pinned the two Playwright jobs (`pages`, `icon`) to 24.04 and kept `android` on latest (commits `a5828dc`, `2e949e6`, `2e8a066`) |
| `fdroid.md` hash `b8cc974e…` | **not reproducible**: it was a build of the uncommitted tree; the APK embeds the commit hash. Replaced with `4e287525…` for `cc867de` | `META-INF/version-control-info.textproto` read from the APK |
| `fdroid.md` "use `-PrepoUrl=…github.repository` in the release workflow" | **risky advice, corrected** | F-Droid builds without `-P`; any difference = different APK (proven above) = failed reproducible-build check. `gradle.properties` comment said "CI can pass its own address that way": rewritten |
| `fdroid.md` "build-tools 34, not 35" (was UNVERIFIED) | **verified at the source**: LLM Central `RELEASE.md` table (tested 2026-09-30, same AGP/Gradle): apksigner 34 passes `apksigcopier compare`, 35 fails "CHUNKED_SHA512 digest mismatch", Gradle signing fails | still to be re-run for this app by the release workflow itself |
| Docs updated for the repo-URL feature | **incomplete**: PROJECT.md never mentioned `app.repoUrl` / `app.author` (no "how to change", no build-settings row) | fixed in review 2 |

### 9.3 LLM Central reuse: what was taken, what was stripped

Compared file by file with `UpperPanda1/assist` @ `b9567c9`. Taken and adapted: `account/` (Account, AccountStore —
fully `synchronized`, `all()` returns a copy, needed because bridge calls arrive on WebView's JavaBridge thread —,
AccountsBridge), `web/` (TabManager, TabLru, Profiles, DesktopMode, PageZoom, FindInPage, LoadErrors, Scripts,
WebViewRecovery, DialogWebViewClient), `theme/` minus palettes, `ui/` (FindBar, SwitcherPopup, SettingsRestore,
UnloadedPlaceholder, AboutWindow, NativeTheme), `storage/` (AppCache, StorageBridge). Stripped, and a grep for
`duck|assistant|provider|chatgpt|gemini|palette|material you|tracker|llm` in `app/src` finds no code (only the app lock's
unlock "prompt" and `android.provider.*` imports): assistant services, chat database, providers, sign-in helpers
(`SignInHosts`, `SignInUserAgent`, `PopupWindows` — correctly kept back for AdGuard, CLAUDE.md §8), tracker/worker
blocking, user scripts, backup/export of logins, request log, Material You palettes, intro window, microphone and file
chooser. Nothing unneeded found.

Not ported but worth it: `wipe(profile, done)` (R1). Deliberately different and better here: fixed quota (4.3); one
`clearAllCaches` that also covers the default profile.

### 9.4 Cache management: verdict

**Structurally sound.** One profile per account; `LOAD_DEFAULT`; a fixed 20 MB HTTP quota set in `Profiles.attach` for
every account tab including the default profile (WebView keeps it across restarts); `clearCache(true)` per open tab and
a throw-away WebView per closed profile (cache only; cookies and storage untouched — Chromium `AwContents::ClearCache`,
per review 1); removed accounts deleted or wiped-then-deleted at next start; size measured off the main thread without
following symlinks. Weak points, all low: the wipe race (R1); `clearAllCaches` creates up to 19 WebViews one after
another on the main thread (each `new WebView` costs several ms, so ~20 accounts can stall a frame or more: R4); the
1.5 s re-measure (review 1, 4.6); the Ping window shares the `main` account's profile (decision B).

### 9.5 UI performance and battery

| Area | Finding |
|---|---|
| Hidden tabs | `setVisibility(GONE)` **and** `onPause()`; renderer priority `IMPORTANT`, waived when not visible; LRU cap 1–10 ("Accounts kept loaded"); `onTrimMemory` closes hidden tabs. Good |
| Status icon | network callback only between `onStart`/`onStop`, 400 ms debounce, stale answers dropped by a generation counter, 6 s timeouts, 16 KB cap, no polling. Good. One small request per return to the app |
| Background work | cache measuring, `data:` decoding (10 MB cap) and the status test run on one background thread; nothing heavy on the main thread except R4 |
| **App in background** | *Corrected after reading the live dashboard code (`my.nextdns.io` `main.7f28998e.js`, 2026-11-19); see §9.8.* The dashboard limits itself: the Logs page's live stream (`EventSource` on `/profiles/{id}/logs/stream`) is **off until the user taps the stream button** and is switched off after **5 minutes without user activity**; the Setup status re-check stops after 60 s idle or when the page is hidden; the root-CA check runs only while the page is visible and focused. Worst case: a user who turned streaming on and pressed Home keeps one open stream for about 5 more minutes. R2 withdrawn |
| Favicons, console | favicon downloads off; console messages not logged. Good |

### 9.6 Recommendations (not applied; ranked)

| # | Change | Size | Risk |
|---|---|---|---|
| R1 | ✅ **Done 2026-11-19** (§9.9): `web/WipeGate` + `Profiles.forget(account, done)` + `TabManager.show`/`loadAfterWipe`; the account waits on the "Unloaded" screen until its container is empty, then loads | ~150 lines incl. docs | low; phone test in CHANGES.md |
| R2 | ~~pause timers on `onStop`~~ **withdrawn** (§9.8): `pauseTimers` stops JavaScript timers but not network loading, so it would also stop the dashboard's own 5-minute idle switch-off and could keep a stream open *longer* | — | — |
| R3 | ✅ **Done 2026-11-19, re-scoped** (§9.9): "before `show()`" was unnecessary (nothing is drawn before the flag is set); the real gap was the **status dialog**, now marked secure and updated by `applySecureFlag` | 3 code lines | very low |
| R4 | In `Profiles.clearAllCaches`, clear closed profiles one per posted message (`Handler.post` chain) instead of a tight loop | ~15 lines | low |
| R5 | CI step: fail if `app.repoUrl` ≠ `https://github.com/${{ github.repository }}` on the main repository (skip on forks) | ~8 lines YAML | none for the APK |
| R6 | Route `Downloads.start`'s web address through `ExternalLinks.open` | 5 lines | very low |
| R7 | CLAUDE.md §7 says "Every WebView: `Profiles.attach` first"; the Settings/About/Ping and throw-away cache WebViews don't (they use the default profile on purpose). The rule should say "every account tab" — **docs fixed** | — | — |

### 9.7 Docs fixed in review 2

`fdroid.md` (hash, JDK 17 result, `-PrepoUrl` warning, CI facts, a submission step list, verified signing evidence),
`gradle.properties` (comment), `PROJECT.md` (§6 quota rationale and wipe race, §7 how to change the repository
address, §8 build settings, §9 CI runners), `CLAUDE.md` (§7 rule wording, §9 facts, §11 TODO), `CHANGES.md` (entry),
this section. `handoff.md` was stale here and was brought up to date later the same day, at the owner's request.

### 9.8 Correction: background battery use (2026-11-19, after the owner's question)

Review 2 first said "a dashboard left on the Logs page can keep the radio busy until Android ends the process" and
proposed R2. That came from memory, not from the site. Reading the dashboard's code changed it:

| Dashboard part | What it does in the background | Evidence in `main.7f28998e.js` |
|---|---|---|
| Logs, live stream | off by default; on only after tapping the stream button; off after 5 min without activity | `new EventSource(…/logs/stream…)`; button toggles `p(…stream.id / null)`; `ww({timeout:3e5, onIdle: p(null)})` |
| Setup page, "is this device using NextDNS" | re-checks only while active; stops after 60 s idle or when hidden | `ww({timeout:6e4, …})` + `visibilitychange` → `"visible" === document.visibilityState` |
| Block-page root-CA check (every 60 s) | returns at once unless the page is visible **and** focused | `if ("visible" !== document.visibilityState \|\| !document.hasFocus()) return` |
| "5 minutes ago" texts (every 60 s) | updates text only, no network | `setInterval(o, 6e4)` with `fromNow()` |

What the app adds: a tab hidden **inside** the app gets `onPause()` (the page sees "hidden"); when the whole app goes to
the background, Android hides the window and Chromium marks the page hidden too and throttles its timers. In the
worst case (stream on, Home pressed), about 5 more minutes of one open connection, then the dashboard closes it itself.
The same holds for debug and release APKs: this is run-time behaviour, not a build setting. **Not measured on a phone.**

Side result: the Setup page reads `status` values `ok`, `mismatch` and `unconfigured` from `test.nextdns.io`, which
confirms `ok` = "in use" (`Site.TEST_STATUS_IN_USE`); the `protocol` field was not seen in this code.

### 9.9 R1 and R3 re-checked before fixing (2026-11-19)

**R1 (sign-out deletion race): needed, narrow.** Re-traced: `forgetAccount` → `TabManager.unload` → `Profiles.forget`
starts asynchronous deletions and returned at once. Two ways to open the same container during them: (1) "Sign out"
then open that account (Settings → Open, the account menu, or the "Unloaded" screen); (2) "Remove" the account with
id `main`, then "Add account": `AccountStore.add` reuses the id `main` and so WebView's default container. The androidx
1.17.1 docs say the deletion "may also delete data that is stored during deletion". The login itself is typed by the
user seconds later, after the deletion, so the realistic damage is a half-deleted first page load (cookies or storage
it set), not a lost login. Fixed anyway because the gap is real, documented by Google, and the fix is small and
testable. Limitation: a screen rebuilt *during* a wipe (theme change in that second) shows "Unloaded" and loads on the
next tap instead of by itself.

**R3 (secure flag before a window appears): the premise was wrong; re-scoped.** AOSP `main` (read 2026-11-19):
`Dialog.show()` → `WindowManager.addView` → `ViewRootImpl.setView` only *schedules* the first layout and draw
(`requestLayout` → `Choreographer.postCallback(CALLBACK_TRAVERSAL)`); the window's surface is made later in
`relayoutWindow` during that traversal. `Windows.fill` runs in the same main-thread message right after `show()`, and
`addFlags` → `WindowManagerGlobal.updateViewLayout` → `root.setLayoutParams` hands the flag to the same
`ViewRootImpl` before that first traversal. So nothing is drawn unprotected; moving the call would change nothing.
What *is* unprotected: SurfaceFlinger makes a layer secure if it or its **parent** is
(`LayerSnapshotBuilder.cpp`: `parentSnapshot.isSecure || (requested.flags & eLayerSecure)`). The account menu and the ⋮
menu are child windows of the main window → covered. Dialogs are separate windows → each needs the flag: Settings,
About and Ping have it (`cc867de`); the **status dialog** (tap on the status icon) does not. The dashboard opens no
browser dialogs (`alert`/`confirm`/`prompt`: none in `main.7f28998e.js`; its `beforeunload` handler only cleans up),
so WebView's own dialogs are not a gap today. Fix: give the status dialog the flag, and keep it in step when the lock
is switched.

---

## 10. Review 3 (2026-11-19): adversarial pass over screens, UI and core logic

Scope: the files reviews 1–2 had not read line by line. Read in full: `lock/` (LockRules, AppLock), `ui/LockScreen`,
`site/Site`, `web/Scripts`, `web/PageZoom` + `zoom_viewport.js`, `web/DesktopMode` + `desktop_viewport.js`,
`theme/ThemeManager`, `theme/ContentInsets`, `theme/ThemeRebuild`, `ui/SwitcherPopup`, `ui/FindBar` (logic part),
`ui/SettingsWindow` (bridge), `status/StatusRules`, `web/WebViewRecovery`, `web/TabLru`, `web/LoadErrors`,
`account/AccountStore` (loading, names), `downloads/DataUrl`, `ui/PingWindow` (WebView set-up), `layout/activity_main.xml`.
Local pages: every HTML sink and bridge call in `settings.html` / `about.html` (flows are covered by the page checks).
Facts were checked in AOSP `main` (`ViewGroup.java`, `View.java`) and androidx.webkit 1.17.1 sources where it mattered.

### 10.1 Findings

| # | Finding | Severity | Status |
|---|---|---|---|
| F1 | **App lock could be passed with TalkBack or a keyboard.** The cover is a view drawn over `mainRoot` (top bar + pages). Android hides covered views from neither accessibility services (TalkBack swipes to, and presses, every view) nor keyboard focus (Tab/arrows). So ⋮ → Settings → Sign out / Remove account, and the page itself, were reachable without unlocking; a page that already had keyboard focus kept receiving typed keys (`setDescendantFocusability` doesn't take existing focus away: AOSP `ViewGroup.setDescendantFocusability` only sets a flag; the cover button's `requestFocus` does nothing in touch mode). | medium | ✅ fixed 2026-11-19 (`ui/LockScreen.covers`: content hidden from accessibility, focus blocked and cleared while locked; `openSettings` refuses while locked) |
| F2 | `downloads/DataUrl` saves any *well-formed* MIME type the page names (e.g. an APK type) to Downloads as "NextDNS download.bin"; MediaStore may then add the type's extension. Only a page on `*.nextdns.io` can do this. Suggest: save unknown types as `application/octet-stream`. | low | open (owner) |
| F3 | `status/StatusRules` (= review 1, 4.5, confirmed): a Private DNS host merely *containing* "nextdns" counts as ours; the dashboard's own code shows three test answers `ok` / `mismatch` / `unconfigured`, and `mismatch` (NextDNS in use, other configuration) falls back to the Private DNS state. | low | open (needs a real "in use" sample) |
| F4 | `zoom_viewport.js` defines `off()` that nothing calls (switching off removes the script and reloads). Harmless dead code. | nit | open |
| F5 | Android 15+ forces edge-to-edge: the Ping window pads itself (`fitsSystemWindows`), the Settings/About HTML pages don't. LLM Central's identical dialogs showed no overlap on the owner's Android 16 phone. | unverified | phone check |
| F6 | The account menu is `WRAP_CONTENT` wide; a 40-character name is ellipsized (single line) and the window is limited to the screen, so it should fit at 360 dp. | unverified | phone check |

### 10.2 Checked and correct

- **Lock rules:** locks on a fresh process and after > 60 s away; turning the lock on *and* off needs the phone's
  unlock; Back on the cover leaves the app; Settings/About/Ping/status/menus/find bar are closed before locking; a
  rebuilt screen doesn't reopen Settings while locked; Ctrl+F is ignored while locked.
- **Navigation:** `Site.ownsHost` = exact host or real subdomain; a trailing dot or empty host fails closed (browser).
  Injected scripts come only from assets and run only on `https://nextdns.io` / `https://*.nextdns.io`.
- **Zoom + desktop scripts:** each writes the viewport only when it differs (no observer loop); the zoom script checks
  the desktop flag on every run, so their order doesn't matter; switching reloads the tab; desktop mode keeps `; wv`
  and restores the phone client hints.
- **Theme and insets:** Android 11–14 frames already exclude the bars (no double padding); Android 15+ padded by the
  listener; Android 10 keeps the default fit; hiding the status bar gives the page the cut-out strip on purpose.
- **Account menu / find bar:** actions dismiss first; rows scroll inside half the screen; late find results after
  closing are ignored; highlight CSS is built from hex colours only and passed through `JSONObject.quote`.
- **Local pages:** no `innerHTML`; user text is inserted as text nodes (`el()`); the only `document.write` is the
  theme CSS made in Java from fixed colours. Settings bridge: theme mode checked against the list, tabs clamped 1–10,
  links by name only, the lock switch always asks for the unlock.
- **Robustness:** all 5 WebView clients handle renderer death; recovery may attach the new WebView before
  `setProfile` (androidx docs allow "attaching it to the view hierarchy" first); the LRU never closes the tab on screen;
  the retry screen goes only when a new page draws without error; a malformed saved account list falls back safely;
  `DataUrl` uses the strict Base64 decoder, a 10 MB cap and refuses bad escapes.

### 10.3 Not read line by line

The JavaScript flow inside `settings.html` beyond its sinks and bridge calls (covered by 5 page checks), `about.html`
beyond the link code, purely visual classes (`NativeTheme`, `AccountBadge`, `UnloadedPlaceholder`), layout XML
appearance, strings. Nothing ran on a phone.
