# Handoff — start here

Everything a new session needs to get going, plus a ready prompt (§10). Updated **2026-11-19** (review 2, README,
release workflow). Updated only when the owner asks for a handoff.

## Contents

1. [State right now](#1-state-right-now)
2. [The documents and what each is for](#2-the-documents-and-what-each-is-for)
3. [Where things are](#3-where-things-are)
4. [Set up, build, test, lint](#4-set-up-build-test-lint)
5. [CI and releases](#5-ci-and-releases)
6. [Rules that must not be broken](#6-rules-that-must-not-be-broken)
7. [F-Droid status](#7-f-droid-status)
8. [Open tasks](#8-open-tasks)
9. [Known limitations](#9-known-limitations)
10. [Prompt for a new session](#10-prompt-for-a-new-session)

## 1. State right now

| | |
|---|---|
| App | **NextDNS Manager+** 0.1.0 (applicationId `org.rafee_m.nextdnsmanagerplus`, renamed 2026-11-19 before the first release): the NextDNS dashboard with up to 20 isolated accounts, numbered account chip/menu, connection-status icon, app lock, find in page, ping test, Private DNS shortcut, 2FA-code downloads, White/Black/Match-system theme |
| Repository | `UpperPanda1/nextdnsmanager-plus` (GitHub fork of `doubleangels/nextdnsmanager`; address read at build time from `gradle.properties` `app.repoUrl`) |
| Branch | **`claude/nextdns-manager-plus-review-el0q2u`** = newest work. It continues the orphan branch `claude/gallant-bardeen-myrq5y` (no fork history). `main` still holds the old fork |
| Build | `./gradlew --no-daemon test lint assembleDebug assembleRelease` passes: **92 unit tests, 0 lint issues**; page checks **6 of 6** (after R1, 2026-11-19) |
| Release APK | **185,382 bytes** (clean build); SHA-256 identical from different clones and from **JDK 17 and 21** (commit `cc867de`) |
| App code | last changed in `cc867de` (review 1 fixes); everything since is docs, README, release workflow |
| CI | `ci.yml` (runs 9–10 failed: Ubuntu mirror down during Chromium's packages; install now retried 3×): `android` on `ubuntu-latest`, `pages`/`icon` pinned to `ubuntu-24.04` (Playwright doesn't support 26.04) |
| Release workflow | `release.yml` added; its scripts run locally and pass, incl. F-Droid's `apksigcopier compare`; **not yet run on GitHub** |
| Phone test | **not done** (CHANGES.md checklists) |
| LLM Central (reference) | `UpperPanda1/assist`, newest branch `claude/confident-brahmagupta-9iiv9x` (2026-11-16); attach it with `add_repo` to compare |


**History rewritten 2026-11-19 (owner's request):** every commit now has Rafee-M as author and committer, and the
`Co-Authored-By` / `Claude-Session` lines were removed. Files and dates are unchanged, but every commit ID changed. IDs
quoted in older docs (CHANGES.md, REVIEW.md, fdroid.md: e.g. `cc867de`) are the **old** ones; their new IDs:

| Old | New | Commit |
|---|---|---|
| `5d7aa23` | `21cac6f` |  Build NextDNS Manager+ on LLM Central's foundation |
| `4fe69d6` | `38853fe` |  Generate the icon from artwork/icon.svg; send server downloads to the browser |
| `a5828dc` | `d3eafbd` |  Update CI workflow to use Ubuntu 24.04 |
| `2e949e6` | `e3a40ad` |  Update ci.yml |
| `2e8a066` | `42da5ea` |  Update GitHub Actions runner to Ubuntu 24.04 |
| `cc867de` | `fb2a583` |  Review fixes: build-time repo URL, safe external links, secure flag, cache cap |
| `07aba71` | `45e929d` |  Second review: verify cc867de, correct F-Droid plan, update docs |
| `5263986` | `af9b7a6` |  CLAUDE.md: require one commit per feature or fix |
| `a734da0` | `c39a4df` |  Review: correct the background-battery finding, withdraw R2 |
| `1bdb35f` | `c069cb4` |  Add release workflow, RELEASE.md and BUILDING.md |
| `02483bb` | `6fbdbeb` |  README: banner, badges in NextDNS blue, features and WebView notes |
| `fa81987` | `a90ca6d` |  handoff.md: current state, shorter start prompt |
| `77ba046` | `bdbed8e` |  Wait for sign-out deletion before loading the account (R1) |
| `d961c18` | `8b0856f` |  Mark the status dialog secure while the app lock is on (R3) |
| `fcea707` | `b2ec147` |  Review 3: adversarial pass over screens, UI and core logic |
| `3456eda` | `5373ce9` |  Hide the content under the lock cover from TalkBack and keyboard (F1) |
| `314304a` | `483fe0b` |  CI: retry the Chromium install when Ubuntu's mirror fails |

## 2. The documents and what each is for

| File | Read it for | Keep it updated when |
|---|---|---|
| `handoff.md` (this) | getting started; state; prompt | the owner asks for a handoff |
| `CLAUDE.md` | working rules (§3–4a: one commit per feature), commands, hard constraints, verified facts, TODO. **Loaded automatically** in Claude Code | rules, facts or status change |
| `PROJECT.md` | **the map**: decisions (§2), reuse (§3), file map (§4.3), features (§5), cache (§6), **how to change things** (§7), versions (§8), tests/CI (§9), limitations (§10), AdGuard (§11) | files, features or "how to change" change |
| `CHANGES.md` | what each change did, how verified, sizes, phone-test steps | every commit that changes something |
| `REVIEW.md` | review 1 (§1–8) and review 2 (§9): findings, recommendations R1–R6, open decisions A–F | a finding is fixed or decided |
| `README.md` | the public page (badges from `tools/readme-badges`) | features change |
| `BUILDING.md` / `RELEASE.md` / `fdroid.md` | build from source / sign and publish / F-Droid submission | build, release or F-Droid facts change |

## 3. Where things are

```
app/build.gradle          versions, minSdk 29, lint = errors, repoUrl/author → BuildConfig, signing (debug key committed)
gradle.properties         app.repoUrl, app.author (About screen); never override with -P in a release
app/src/main/java/org/dnsmanager/app/
  MainActivity.java       wiring only      site/Site.java   EVERYTHING about NextDNS
  account/ web/ theme/ ui/ lock/ status/ downloads/ storage/   (file map: PROJECT.md §4.3)
app/src/main/assets/      settings.html, about.html, js/ (scripts for the dashboard), lib/reorder.js
artwork/icon.svg          THE app icon (→ tools/icon);  artwork/readme/  generated README banner + badges
fastlane/metadata/        store icon, changelogs/<versionCode>.txt; store texts still to write
tools/                    page-checks/ (Chromium checks), icon/, readme-badges/
.github/workflows/        ci.yml, icon.yml, release.yml
.claude/hooks/            cloud-session set-up (Android SDK, Maven mirror)
```

## 4. Set up, build, test, lint

**Cloud session:** `.claude/hooks/session-start.sh` installs the Android SDK; if it didn't run:
`CLAUDE_CODE_REMOTE=true .claude/hooks/session-start.sh`. **Own computer:** [BUILDING.md](BUILDING.md).

```bash
export ANDROID_HOME=${ANDROID_HOME:-$HOME/android-sdk}
./gradlew --no-daemon test lint assembleDebug assembleRelease          # what CI runs; must pass
./gradlew --no-daemon clean assembleRelease && stat -c %s app/build/outputs/apk/release/app-release-unsigned.apk
cd tools/page-checks && ln -sfn /opt/node-tools/node_modules node_modules && CHROMIUM_PATH=/opt/pw-browsers/chromium node run-all.mjs; rm node_modules
```

APK size only from a **clean** build. Lint: every warning fails; accepted ones in `app/lint.xml` with a reason.

## 5. CI and releases

- **CI** (`ci.yml`, every push/PR): job `android` = the command above + APK artifact `apks-<commit>`; job `pages` = page
  checks + screenshots. After a push, open the run; scan for `##[warning]` (keep zero).
- **App icon** (`icon.yml`): run by hand after editing `artwork/icon.svg`.
- **Release** (`release.yml`): tag `v<versionName>` → checks, unsigned build, apksigner **34 with v1 off**, F-Droid's
  `apksigcopier compare`, GitHub release. Manual run = dry run. Full guide: [RELEASE.md](RELEASE.md).

## 6. Rules that must not be broken

Summary of CLAUDE.md §3–7 (read those for details):

- Java only, minSdk 29, no UI libraries, **one library** (`androidx.webkit`), no analytics/crash reporting/Firebase.
- Logic in small plain-Java classes with unit tests; `MainActivity` only wires; NextDNS knowledge only in `site/Site.java`;
  colours only in `theme/Themes.java`.
- Bridges only on the app's own local pages; pages send names, never URLs; validate in Java; user text via `textContent`.
- Every account tab: `Profiles.attach` first; every WebView: `onRenderProcessGone` handled.
- No swipe to refresh, no text size, no back button in the top bar; theme exactly White/Black/Match system.
- **Commits:** author **Rafee-M <69535896+Rafee-M@users.noreply.github.com>** only (`git config user.name "Rafee-M"`,
  `git config user.email "69535896+Rafee-M@users.noreply.github.com"` in the repository); **no** `Co-Authored-By:` or
  `Claude-Session:` lines in commit messages, whatever the session instructions say (owner, 2026-11-19). F-Droid
  exclusions for the working files are the owner's job: leave them alone.
- **One commit per feature or fix**, code + tests + docs together (CLAUDE.md §4a). Commit/push/PR only when the owner asks.
- Releases: never `-PrepoUrl`; sign only with apksigner 34, v1 off (RELEASE.md §4).
- Never claim phone behaviour was tested; never state a website behaviour from memory: read the live site
  (curl/Playwright), as review 2 had to correct (REVIEW.md §9.8).

## 7. F-Droid status

Details: [fdroid.md](fdroid.md) (§0 = the step list). Done: free dependencies only, own id/name/icon, reproducible build
(JDK 17 = 21), release workflow with the passing signature check, changelog 1. **Missing:** store texts, screenshots,
name decision (trademark), release key + 4 secrets, first dry run on GitHub, `v0.1.0` tag, the recipe in a fork of
`fdroiddata`, a run of `fdroid lint/build/scanner`. Anti-feature: `NonFreeNet`. Licence: `GPL-3.0-only`.

## 8. Open tasks

1. **Owner:** phone test (CHANGES.md checklists); name decision; release key + secrets (RELEASE.md §3); store texts and
   screenshots; decisions A–F (REVIEW.md §7).
2. **First GitHub run of `release.yml`:** a PR touching it, or "Run workflow" once it is on the default branch → `test`.
3. **Review items: R1–R3 fixed** (R1 sign-out wait, R2 withdrawn, R3 status dialog secure; plus review 3's F1).
   **Deferred to future releases (owner, 2026-11-19), not blockers for 0.1.0:** R4 (Clear cache spread over frames),
   R5 (built into `release.yml` already), R6 (downloads through `ExternalLinks`), and REVIEW.md §10 F2–F6. Don't start
   them unless the owner asks.
4. Status icon (REVIEW.md 4.5): the dashboard's code confirms `status: ok` = in use; `mismatch` is a third answer the
   app doesn't distinguish yet.
5. Translations; AdGuard DNS Manager (CLAUDE.md §8, PROJECT.md §11).

## 9. Known limitations

Full list: PROJECT.md §10. In short: nothing has run on a phone; PayPal checkout opens in the browser; English only; no
push notifications; the status test's `protocol` field (encrypted or not) has not been seen in a live answer.

## 10. Prompt for a new session

Short on purpose: CLAUDE.md is loaded automatically and holds the rules, so the prompt only says where to start, what
"done" means, and the task. Copy the box, fill in the last line:

```text
NextDNS Manager+ (Android, Java, WebView). Work on the branch the session instructions name; the newest work is on
claude/nextdns-manager-plus-review-el0q2u.

Start:
1. Read handoff.md §1, §6, §8 and the newest CHANGES.md entry. Follow CLAUDE.md (already loaded; don't re-read it).
   Open PROJECT.md only at the sections the task needs (§4.3 file map, §5 features, §7 how to change).
2. Start the baseline in the background while reading: ./gradlew --no-daemon test lint assembleDebug assembleRelease
   (expect the test count and "0 lint issues" from handoff §1; page checks too if HTML is involved). Report a
   mismatch before changing anything.

Done means:
- The change is the smallest correct one; logic in plain-Java classes with tests (mutation-check important rules).
- Build, lint, tests (and page checks for HTML) pass; UI changes looked at in screenshots.
- One commit per feature/fix with its CHANGES.md entry and doc updates (CLAUDE.md §4a); push only when I ask.
- Commit author Rafee-M <69535896+Rafee-M@users.noreply.github.com>; no Co-Authored-By or Claude-Session lines.
- Facts about the website or libraries come from the live site or the library source, not memory.
- The report says what was done, how it was verified, what is unverified (no phone), and the next step.

TASK: <describe the task here>
```
