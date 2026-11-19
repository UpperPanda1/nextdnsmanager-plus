# F-Droid submission: what it entails

Status of **NextDNS Manager+** (`org.rafee_m.nextdnsmanagerplus`, version 0.1.0 / code 1) against F-Droid's rules, and
the work that remains. First written 2026-11-19 (review 1, `REVIEW.md` §1–8); corrected and re-verified the same day
(review 2, `REVIEW.md` §9). Facts about F-Droid come from its documentation pages (links in §8); facts about this app
come from the code and from commands that were run. Anything not checked is marked **UNVERIFIED**.

**Short verdict:** technically a good candidate (one free dependency, no tracking, reproducible build proven at a
commit). **Not ready to submit:** store texts, screenshots, a release key + release workflow, a `v0.1.0` tag, a name
decision and a run of F-Droid's own tools are missing.

## 0. The submission in one list (what "submitting" actually means)

| # | Step | Who / where | Done? |
|---|---|---|---|
| 1 | Store texts and screenshots in `fastlane/metadata/android/en-US/` (§3.1) | this repo | icon only |
| 2 | Decide the public name (trademark, §3.2) | owner | no |
| 3 | Create a release key; GitHub secrets (§3.3, `RELEASE.md` §3) | owner | no |
| 4 | Release workflow: unsigned build → sign with `apksigner` 34, v1 off → `apksigcopier compare` → GitHub release (§3.3) | this repo (`.github/workflows/release.yml`) | **added** 2026-11-19; scripts tested locally, not yet run on GitHub |
| 5 | Tag `v0.1.0` on a clean commit; the workflow publishes the signed APK | owner | no |
| 6 | Fork `gitlab.com/fdroid/fdroiddata`, add `metadata/org.rafee_m.nextdnsmanagerplus.yml` (§3.5) | GitLab | no |
| 7 | Run `fdroid lint`, `rewritemeta`, `build`, `scanner` on it locally (§3.4) | own computer | no |
| 8 | Open the merge request; answer the reviewers (name, anti-feature, screenshots) | GitLab | no |
| 9 | After merge: every release = `versionCode` + 1, changelog file, tag (§3.6); F-Droid's bot picks it up | this repo | — |

## 1. How F-Droid works, in 6 lines

1. You do not upload an APK. You send a **recipe** (a YAML file) to `gitlab.com/fdroid/fdroiddata` as a merge request.
2. F-Droid's server **builds the app from your public source** at the tag the recipe names, using only free tools.
3. Either F-Droid signs the APK with its own key, **or** (reproducible builds) it checks that its build equals the APK
   you signed and publishes **yours** (`Binaries:` + `AllowedAPKSigningKeys:`).
4. Store text and pictures are read from your repository (`fastlane/metadata/android/<locale>/`).
5. A new tag with a higher `versionCode` is found automatically (`UpdateCheckMode: Tags`).
6. Reviewers read the merge request by hand: names, licences, trademarks, anti-features.

## 2. Checklist: what is already right

| Requirement (F-Droid source) | This app | Evidence |
|---|---|---|
| FLOSS licence | GPL-3.0 (`LICENSE`); recipe field `License: GPL-3.0-only` | LICENSE present. The code derives from LLM Central, which descends from GPL code, so `-only` is the safe label (from `handoff.md` §7; not re-derived) |
| Dependencies free, from a trusted Maven repo | one library: `androidx.webkit:webkit:1.17.1` (Apache-2.0), from Google's Maven | `app/build.gradle`, `settings.gradle` (`google()`, `mavenCentral()` only; `FAIL_ON_PROJECT_REPOS`) |
| No tracking SDKs ("strictly forbidden") | none: no Firebase/Sentry/analytics; WebView's own metrics switched off | `AndroidManifest.xml` has `WebView.MetricsOptOut`; 3 permissions only. Review 2 grep for LLM Central's AI/provider/tracker code: nothing left |
| Fork needs a **new application id** and "name, icon and string changes" | id `org.rafee_m.nextdnsmanagerplus`; own icon from `artwork/icon.svg`; no file of the original is in the tree (claimed byte-by-byte in `PROJECT.md` §3) | id checked in the built APK. That it differs from the original's id: **UNVERIFIED** (the original's id was not looked up) |
| "A website wrapper should provide native features" | native top bar, several isolated accounts, app lock, status icon, find in page, downloads of `data:` files | Features in `PROJECT.md` §5 |
| Build from source with open tools | Gradle wrapper pinned by SHA-256, AGP 8.13.2, Gradle 8.14.5, SDK 36 | `gradle/wrapper/gradle-wrapper.properties` |
| Reproducible build | **four** release builds of commit `cc867de` (working tree + two fresh clones at different paths on JDK 21, one more clone on JDK 17) gave the **same** SHA-256 `4e287525…18554a` | review 2, §5 |
| `versionName`/`versionCode` readable | literal values in `app/build.gradle` (`0.1.0` / `1`) | the recipe's auto-update reads them from the tagged revision |
| No signing secrets in the tree | release signing only if `local.properties` has `signing.*` (git-ignored); the committed debug key is a debug key | `app/build.gradle` |
| Backup does not copy logins | `app_webview/` excluded in both backup rule files | `res/xml/*backup*.xml` |
| CI builds the release on every push | `ci.yml` job `android` passed for `cc867de` (run 6, 2026-11-19) | GitHub Actions run list, read in review 2 |

## 3. What is still missing (in order)

### 3.1 Store texts and pictures (blocks the listing)

Only `images/icon.png` (512 × 512) exists. F-Droid reads this layout (limits from its "All About Descriptions,
Graphics and Screenshots" page):

```
fastlane/metadata/android/en-US/
├── title.txt               ≤ 50 characters          e.g. "NextDNS Manager+"   (see 3.2: name)
├── short_description.txt   ≤ 80 characters, mandatory
├── full_description.txt    ≤ 4000 characters, mandatory
├── changelogs/1.txt        file name = versionCode, ≤ 500 characters
└── images/
    ├── icon.png            done
    └── phoneScreenshots/1.png, 2.png …   (PNG or JPEG; none exist yet)
```

Put in the full description what F-Droid reviewers look for: it is an unofficial client for a proprietary service
(NextDNS), it needs a NextDNS account, it sends nothing anywhere except to NextDNS's own addresses, and the one
automatic request (`test.nextdns.io`, for the status icon, only while the app is on screen) carries no account data
(`settings.html` "Privacy" already says so). Mention that several accounts need Android System WebView 119+.

### 3.2 Decide the name (trademark)

F-Droid's policy: apps "need to obtain necessary trademark and copyright permissions". "NextDNS" is a trademark of
its owner; the app's own About/Disclaimer text already says it is not affiliated. A reviewer may still ask. Options:
(a) keep "NextDNS Manager+" and be ready to answer, (b) a neutral name such as "DNS Manager+" with "for NextDNS" in
the description. The applicationId and the code package are not affected by this choice; **decide before the first
public release**. Also check whether the original *NextDNS Manager* is already listed on F-Droid under a similar
name (**UNVERIFIED**).

### 3.3 Signing key and release workflow (blocks reproducible publishing)

**Done (2026-11-19):** `.github/workflows/release.yml` and [`RELEASE.md`](RELEASE.md), ported from LLM Central's. How to
use them: `RELEASE.md` §3–4. What they do, and what the port changed:

1. Create a release key once (`keytool -genkeypair … -keyalg RSA -keysize 4096 -validity 10000`); back it up; keep it
   **outside** the repository (`*.jks`/`*.keystore` are git-ignored, except the already tracked debug key).
2. GitHub secrets: keystore (base64), keystore password, key alias, key password.
3. Workflow on a `v*` tag: `./gradlew assembleRelease` (unsigned), sign **with `apksigner` from build-tools 34.0.0 and
   `--v1-signing-enabled false`**, run `apksigcopier compare` (1.1.1), publish `NextDNS-Manager-Plus-<version>.apk` on
   the GitHub release, print the signer's SHA-256 (needed in 3.5).
   - **Tested on this app's APK (2026-11-19):** 34 + v1 off → `apksigcopier compare` passes; 34 with default options →
     fails ("CHUNKED_SHA512 digest mismatch", apksigner adds v1 JAR files); 35 + v1 off → fails. LLM Central's workflow
     (34, v1 not switched off) passed for LLM Central but **would fail for this app**; the port adds the switch.
4. **Do not pass `-PrepoUrl` in the release build.** F-Droid builds the tag without it, so the About links come from
   `gradle.properties`. A release built with any other value is a different APK (proven: §4) and F-Droid's check fails.
   If the repository moved, change `app.repoUrl` in `gradle.properties`, commit, then tag.

### 3.4 Run F-Droid's own tools locally (finds surprises before a human does)

```bash
python3 -m venv ~/fdroid-venv && . ~/fdroid-venv/bin/activate
pip install fdroidserver
# in a clone of gitlab.com/fdroid/fdroiddata, with metadata/org.rafee_m.nextdnsmanagerplus.yml written (3.5):
fdroid readmeta
fdroid rewritemeta org.rafee_m.nextdnsmanagerplus
fdroid lint org.rafee_m.nextdnsmanagerplus
fdroid build -v -l org.rafee_m.nextdnsmanagerplus        # F-Droid's own build of the tagged source
fdroid scanner org.rafee_m.nextdnsmanagerplus            # looks for binaries / non-free or tracking libraries
```

Things the scanner/reviewer may touch in this tree (none verified for this app yet):
- `gradle/wrapper/gradle-wrapper.jar` (a committed binary). LLM Central's `RELEASE.md` reports that F-Droid's clean-up
  removes the signing block from `build.gradle` and the Gradle wrapper, and that its build of the cleaned tree still
  matched; this app's `build.gradle` has the same signing shape.
- `app/debug.keystore` (committed on purpose, password `android`, so debug builds from different computers install over
  each other). The release build never reads it (Gradle only opens a keystore when it signs). If F-Droid objects,
  remove it and let CI create the debug key.

### 3.5 The recipe (draft; **UNVERIFIED**: validate with `fdroid lint` and `fdroid build`)

File `metadata/org.rafee_m.nextdnsmanagerplus.yml` in your fork of `fdroiddata`:

```yaml
Categories:
  - Connectivity            # pick from F-Droid's current category list (UNVERIFIED that this name is on it)
License: GPL-3.0-only
AuthorName: Rafee-M
WebSite: https://github.com/UpperPanda1/nextdnsmanager-plus
SourceCode: https://github.com/UpperPanda1/nextdnsmanager-plus
IssueTracker: https://github.com/UpperPanda1/nextdnsmanager-plus/issues

AntiFeatures:
  NonFreeNet:
    en-US: Only works with the proprietary NextDNS service (an account there is required).

RepoType: git
Repo: https://github.com/UpperPanda1/nextdnsmanager-plus.git

Binaries: https://github.com/UpperPanda1/nextdnsmanager-plus/releases/download/v%v/NextDNS-Manager-Plus-%v.apk
AllowedAPKSigningKeys: <sha256 of YOUR release certificate, lower case, no colons, from the release run's summary>

Builds:
  - versionName: 0.1.0
    versionCode: 1
    commit: v0.1.0
    subdir: app
    gradle:
      - yes

AutoUpdateMode: Version
UpdateCheckMode: Tags
CurrentVersion: 0.1.0
CurrentVersionCode: 1
```

Notes on the choices:
- **`NonFreeNet`** is F-Droid's label for apps that "promote or depend entirely on a proprietary network service". It is
  a label shown to users, not a rejection. Anti-features must be declared; an undeclared one gets a rejection.
- **Not** `Tracking`: the app reports nothing; the status request goes to the service the user already chose.
- **`Binaries` + `AllowedAPKSigningKeys`** = the reproducible-build route: F-Droid builds, compares with your signed
  APK (signature copying), and publishes yours. Without them F-Droid signs with its own key (users then cannot update
  from a GitHub APK to the F-Droid one). The `Binaries` URL pattern must match the release asset's name exactly, so
  name the asset in the workflow (step 3.3) and the recipe together.
- **The repository address appears five times** (`WebSite`, `SourceCode`, `IssueTracker`, `Repo`, `Binaries`): if the
  repository moves, change them in the recipe **and** `app.repoUrl` in `gradle.properties` in the same release.
- `subdir: app` because the Gradle module is `app`. The F-Droid build must not need `local.properties`; it does not
  (SDK path is supplied by F-Droid's build server).

### 3.6 Release routine (every version)

1. Raise `versionCode` by 1 and set `versionName` in `app/build.gradle` (literal numbers, no computing).
2. Write `fastlane/metadata/android/en-US/changelogs/<versionCode>.txt` (≤ 500 characters).
3. `CHANGES.md` entry; CI green.
4. Check `app.repoUrl` in `gradle.properties` is the repository's current address (§4).
5. Tag `v<versionName>` on a **clean** commit and push it; the release workflow builds, signs, checks, publishes.
6. F-Droid's bot finds the tag (`UpdateCheckMode: Tags`) and proposes the update; usually no human step.

## 4. The repository address inside the APK

The About screen's GitHub links, the repository name and "Developed by" are **not** written in the page: they are read
when the app is built.

| What | Where | Rule |
|---|---|---|
| `app.repoUrl`, `app.author` | `gradle.properties` | the single source of truth |
| local override | `-PrepoUrl=https://github.com/<owner>/<repo>` | test builds only; **never** for a release (below) |
| validation | `app/build.gradle` | build **fails** unless the address is `https://github.com/<owner>/<repo>` (a trailing `/` or `.git` is removed); author: letters, digits, `_ . -` and spaces, 1–60 characters. Proven in review 2: `-PrepoUrl='https://evil.example/x"y'` stops the build |
| use | `BuildConfig.REPO_URL` / `AUTHOR` → `AboutWindow.infoJson` → `assets/about.html` | the page re-checks the shape before using it for links, and inserts text with `textContent` |

**Why not `git remote get-url origin`:** AGP already embeds the commit hash (`META-INF/version-control-info.textproto`,
contents seen in review 2: `revision: "cc867de…"`, `local_root_path: "$PROJECT_DIR"`), and a clone's remote differs
between computers (HTTPS vs SSH, forks, local paths), which would make the same commit build to different APKs.

**Why never `-PrepoUrl` for a release:** proven in review 2 — the same commit built with
`-PrepoUrl=https://github.com/someone/moved-repo` gave SHA-256 `58da7961…` instead of `4e287525…`. F-Droid builds
without the flag, so the two would not match.

**When the repository moves:** edit `app.repoUrl` (one line), commit, tag. GitHub redirects a renamed or transferred
repository, so links in already-installed versions keep working unless someone creates a new repository under the old
name. Other places that name the repository are static text: `README.md` (credits), `about.html` "Built on" links
(LLM Central, the original app), the recipe in 3.5. The check accepts **GitHub only**: a move to Codeberg or GitLab
needs a change to the check in `app/build.gradle` and to the GitHub-specific links in `about.html` (`/issues/new/choose`,
`/stargazers`).

**Suggested safeguard (not added yet):** a CI step that fails when `app.repoUrl` differs from
`https://github.com/${{ github.repository }}` on the main repository (and skips on forks). It turns "forgot to edit
`gradle.properties` after a move" into a red ✗ instead of a wrong About screen, without changing the APK.

## 5. Reproducibility: what was proven and what was not

Review 2, 2026-11-19 (JDK 21, Gradle 8.14.5, AGP 8.13.2, one machine), commit **`cc867de`**: `./gradlew clean test
lint assembleDebug assembleRelease` in the working tree, plus `git clone` into two different directories and
`./gradlew assembleRelease` in each:

```
4e28752540d869190ee6da540ca1251397e1d2b37565c3f336da0f196d18554a   working tree (clean build)
4e28752540d869190ee6da540ca1251397e1d2b37565c3f336da0f196d18554a   clone A  scratchpad/repA
4e28752540d869190ee6da540ca1251397e1d2b37565c3f336da0f196d18554a   clone B  scratchpad/deeper/path/repB
```

Release APK size 185,382 bytes (clean build).

*Correction:* review 1 recorded `b8cc974e…c209`. That was a build of the then-uncommitted tree; the APK embeds the
commit hash, so nobody can reproduce that figure from any commit. The hash above is the one for `cc867de`.

The only VCS data in the APK is the commit hash, so **every new commit changes the APK**: F-Droid must build exactly
the tagged commit. A build from a **dirty** tree is not comparable.

**JDK 17: proven.** A fourth fresh clone built with OpenJDK 17.0.21 (`JAVA_HOME` and `org.gradle.java.home` set to it)
gave the **same** `4e287525…18554a`. This matches LLM Central's earlier result (same AGP and Gradle; `RELEASE.md`).

**Not proven yet:** (b) a different computer/OS; (c) F-Droid's own build server result (`fdroid build`); (d) that the
*signed* APK matches F-Droid's build under `apksigcopier compare`.

Recipe to repeat: `git clone <repo> a && git clone <repo> b && (cd a && ./gradlew --no-daemon assembleRelease) &&
(cd b && ./gradlew --no-daemon assembleRelease) && cmp a/app/build/outputs/apk/release/app-release-unsigned.apk
b/app/build/outputs/apk/release/app-release-unsigned.apk` (no output from `cmp` = identical). Each clone needs a
`local.properties` with `sdk.dir=…` (or `ANDROID_HOME` set).

## 6. CI facts that matter for a release workflow

- `ubuntu-latest` is now **Ubuntu 26.04**. Playwright 1.56.1 refuses it ("Playwright does not support chromium on
  ubuntu26.04-x64", App icon run 1, 2026-11-15). That is why the owner pinned the `pages` and `icon` jobs to
  `ubuntu-24.04` and left `android` on `ubuntu-latest` (runs 2–4). A release workflow that only runs Gradle and
  `apksigner` can use `ubuntu-latest`; anything with Playwright must stay on 24.04 until Playwright supports 26.04.
- Run 6 (`cc867de`) passed both jobs.

## 7. Risks, ranked

| # | Risk | Why it matters | Action |
|---|---|---|---|
| 1 | Name/trademark ("NextDNS") | reviewers can ask | decide in 3.2 before the first release |
| 2 | No phone test yet | F-Droid does not test, but a broken first release hurts users | owner's phone checklist in `CHANGES.md` |
| 3 | `-PrepoUrl` used in a release build | F-Droid's build would differ → not published | 3.3 step 4; the CI safeguard in §4 |
| 4 | Release workflow not yet run on GitHub | runner differences (SDK path, Python) | dry run with the test key first (`RELEASE.md` §4); the signing path itself passed `apksigcopier compare` locally |
| 5 | Another OS / F-Droid's build server | not yet compared (JDK 17 and 21 are identical, §5) | `fdroid build` in §3.4 |
| 6 | Committed binaries (wrapper jar, debug keystore) | scanner findings | run `fdroid scanner`; drop the debug keystore if asked |
| 7 | App only useful with a NextDNS account | label `NonFreeNet`; reviewers may ask for screenshots | screenshots + honest description |

## 8. Sources

- Inclusion Policy: https://f-droid.org/docs/Inclusion_Policy/
- Anti-Features: https://f-droid.org/docs/Anti-Features/
- Reproducible Builds: https://f-droid.org/docs/Reproducible_Builds/
- Build Metadata Reference: https://f-droid.org/docs/Build_Metadata_Reference/
- Descriptions, graphics, screenshots: https://f-droid.org/docs/All_About_Descriptions_Graphics_and_Screenshots/
  (the F-Droid pages were read by review 1 on 2026-11-19; review 2 did not re-read them)
- LLM Central's tested release notes: `UpperPanda1/assist`, branch `claude/confident-brahmagupta-9iiv9x` (newest,
  2026-11-16) → `RELEASE.md` (signing table, reproducibility row), `.github/workflows/release.yml` (read in review 2)
