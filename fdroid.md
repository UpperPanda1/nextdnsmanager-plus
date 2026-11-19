# F-Droid submission: what it entails

Status of **NextDNS Manager+** (`org.rafee_m.nextdnsmanager`, version 0.1.0 / code 1) against F-Droid's rules, and
the work that remains. Written 2026-11-19 after a review (see `REVIEW.md`). Facts about F-Droid come from its
current documentation pages (links at the end), read on 2026-11-19; facts about this app come from the code and from
commands run in that review. Anything not checked is marked **UNVERIFIED**.

**Short verdict:** the app is a good candidate technically (one free dependency, no tracking, reproducible build
shown). It is **not ready to submit**: store texts, a release workflow with a signing key, a tag, a name decision
and an F-Droid tool run are missing.

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
| No tracking SDKs ("strictly forbidden") | none: no Firebase/Sentry/analytics; WebView's own metrics switched off | `AndroidManifest.xml` has `WebView.MetricsOptOut`; 3 permissions only |
| Fork needs a **new application id** and "name, icon and string changes" | id `org.rafee_m.nextdnsmanager`; own icon from `artwork/icon.svg`; no file of the original is in the tree (claimed byte-by-byte in `PROJECT.md` §3) | id checked in the built APK (`aapt2 dump badging`). That it differs from the original's id: **UNVERIFIED** (the original's id was not looked up) |
| "A website wrapper should provide native features" | native top bar, several isolated accounts, app lock, status icon, find in page, downloads of `data:` files | Features in `PROJECT.md` §5 |
| Build from source with open tools | Gradle wrapper pinned by SHA-256, AGP 8.13.2, SDK 36 | `gradle/wrapper/gradle-wrapper.properties` |
| Reproducible build | **three** release builds (in-tree, and two fresh clones at different paths) gave the **same SHA-256** `b8cc974e…c209` on JDK 21 | run 2026-11-19, see §5 |
| `versionName`/`versionCode` readable | literal values in `app/build.gradle` (`0.1.0` / `1`) | the recipe's auto-update reads them from the tagged revision |
| No signing secrets in the tree | release signing only if `local.properties` has `signing.*` (git-ignored); the committed debug key is a debug key | `app/build.gradle` |
| Backup does not copy logins | `app_webview/` excluded in both backup rule files | `res/xml/*backup*.xml` |

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
(NextDNS), it needs a NextDNS account, it sends nothing anywhere except to `nextdns.io`, and the one automatic
request (`test.nextdns.io`, for the status icon) carries no account data (`settings.html` "Privacy" already says so).

### 3.2 Decide the name (trademark)

F-Droid's policy: apps "need to obtain necessary trademark and copyright permissions". "NextDNS" is a trademark of
its owner; the app's own About/Disclaimer text already says it is not affiliated. A reviewer may still ask. Options:
(a) keep "NextDNS Manager+" and be ready to answer, (b) a neutral name such as "DNS Manager+" with "for NextDNS" in
the description. The applicationId and the code package are not affected by this choice; **decide before the first
public release** because the applicationId is permanent. Also check whether the original *NextDNS Manager* is already
listed on F-Droid under a similar name (**UNVERIFIED**).

### 3.3 Signing key and release workflow (blocks reproducible publishing)

Nothing in `.github/workflows/` builds or signs a release. LLM Central's `release.yml` is the model
(`UpperPanda1/assist`, file `.github/workflows/release.yml`, with its `RELEASE.md` §3-4). What to copy:

1. Create a release key once (`keytool -genkeypair … -keyalg RSA -keysize 4096 -validity 10000`); back it up; keep it
   **outside** the repository (`*.jks`/`*.keystore` are git-ignored, except the already tracked debug key).
2. GitHub secrets: keystore (base64), keystore password, key alias, key password.
3. Workflow on a `v*` tag: build `assembleRelease` (unsigned), sign **with `apksigner` from build-tools 34**, publish the
   APK on the GitHub release, print the signer's SHA-256 (needed in 3.5).
   - Why 34: LLM Central's notes say build-tools 35 and Gradle's own signing fail F-Droid's `apksigcopier compare`.
     **UNVERIFIED here** (taken from `handoff.md` §7); confirm with F-Droid's tool in 3.4.
4. Build with the repository's real address: `./gradlew assembleRelease -PrepoUrl=https://github.com/${{ github.repository }}`
   (the About screen's links are built into the APK; see 4). Use the **same** value as in `gradle.properties`
   unless the repository really moved, otherwise the APK F-Droid builds will not match yours.

### 3.4 Run F-Droid's own tools locally (finds surprises before a human does)

```bash
python3 -m venv ~/fdroid-venv && . ~/fdroid-venv/bin/activate
pip install fdroidserver
# in a clone of gitlab.com/fdroid/fdroiddata, with metadata/org.rafee_m.nextdnsmanager.yml written (3.5):
fdroid readmeta
fdroid rewritemeta org.rafee_m.nextdnsmanager
fdroid lint org.rafee_m.nextdnsmanager
fdroid build -v -l org.rafee_m.nextdnsmanager        # F-Droid's own build of the tagged source
fdroid scanner org.rafee_m.nextdnsmanager            # looks for binaries / non-free or tracking libraries
```

Things the scanner/reviewer may touch in this tree (none verified yet):
- `gradle/wrapper/gradle-wrapper.jar` (a committed binary; F-Droid normally accepts the official wrapper whose checksum it
  knows, and `distributionSha256Sum` is pinned here);
- `app/debug.keystore` (committed on purpose, password `android`, so debug builds on different computers install over
  each other). It is not used for release. If F-Droid objects, remove it and let CI create the debug key.

### 3.5 The recipe (draft; **UNVERIFIED**: validate with `fdroid lint` and `fdroid build`)

File `metadata/org.rafee_m.nextdnsmanager.yml` in your fork of `fdroiddata`:

```yaml
Categories:
  - Connectivity            # or Security / Internet: pick what F-Droid's current list offers
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
  from a GitHub APK to the F-Droid one). The `Binaries` URL pattern must match your release asset name exactly.
- `subdir: app` because the Gradle module is `app`. The F-Droid build must not need `local.properties`; it does not
  (SDK path is supplied by F-Droid's build server).

### 3.6 Release routine (every version)

1. Raise `versionCode` by 1 and set `versionName` in `app/build.gradle` (literal numbers, no computing).
2. Write `fastlane/metadata/android/en-US/changelogs/<versionCode>.txt` (≤ 500 characters).
3. `CHANGES.md` entry; CI green.
4. Tag `v<versionName>` on a **clean** commit and push it; the release workflow builds, signs, publishes.
5. F-Droid's bot finds the tag (`UpdateCheckMode: Tags`) and proposes the update; usually no human step.

## 4. The repository address inside the APK

The About screen's GitHub links and "Developed by" are **not** written in the page any more: they are read when the app
is built.

| What | Where | Rule |
|---|---|---|
| `app.repoUrl`, `app.author` | `gradle.properties` | the single source; also override with `-PrepoUrl=https://github.com/<owner>/<repo>` |
| validation | `app/build.gradle` | build **fails** unless the address is `https://github.com/<owner>/<repo>` (a trailing `/` or `.git` is removed) |
| use | `BuildConfig.REPO_URL` / `AUTHOR` → `AboutWindow.infoJson` → `assets/about.html` | the page re-checks the shape before using it for links |

It deliberately does **not** run `git remote get-url origin`: AGP already embeds the commit hash
(`META-INF/version-control-info.textproto`), and a clone's remote differs between computers (HTTPS vs SSH, forks,
local paths), which would make the same commit build to different APKs. A committed property keeps the build
reproducible. **When the repository moves:** edit `app.repoUrl` in `gradle.properties` (one line), commit, tag. Other
places that name the repository are static text: `README.md` (credits), `about.html` "Built on" links, the recipe in 3.5.

## 5. Reproducibility: what was proven and what was not

Run on 2026-11-19 (JDK 21, Gradle 8.14.5, AGP 8.13.2, one machine): `git clone` of the same commit into two different
directories, `./gradlew assembleRelease` in each, `sha256sum` of `app-release-unsigned.apk`:

```
b8cc974ed4314f68f4922c2c6f1561ecc3146d9c0dcde50b739b6529cc23c209   (clone A, shallow path)
b8cc974ed4314f68f4922c2c6f1561ecc3146d9c0dcde50b739b6529cc23c209   (clone B, deeper path)
b8cc974ed4314f68f4922c2c6f1561ecc3146d9c0dcde50b739b6529cc23c209   (the working tree, clean)
```

The only VCS data in the APK is the commit hash (`revision: "…"`), so **every new commit changes the APK**: F-Droid must
build exactly the tagged commit. A build from a **dirty** tree is not comparable.

**Not proven (do these before the first release):** (a) JDK 17 (the CI uses 17, my run used 21): build with both and
compare; (b) a different computer/OS; (c) F-Droid's own build server result (`fdroid build`); (d) that the
*signed* APK matches F-Droid's build under `apksigcopier compare`.

Recipe to repeat (a): `git clone <repo> a && git clone <repo> b && (cd a && ./gradlew --no-daemon assembleRelease) &&
(cd b && ./gradlew --no-daemon assembleRelease) && cmp a/app/build/outputs/apk/release/app-release-unsigned.apk
b/app/build/outputs/apk/release/app-release-unsigned.apk` (no output from `cmp` = identical).

## 6. Risks, ranked

| # | Risk | Why it matters | Action |
|---|---|---|---|
| 1 | Name/trademark ("NextDNS") | reviewers can ask; applicationId is permanent | decide in 3.2 before the first release |
| 2 | No phone test yet | F-Droid does not test, but a broken first release hurts users | owner's phone checklist in `CHANGES.md` |
| 3 | Signing path untested (build-tools 34 claim) | signature copying fails on any mismatch | dry-run workflow with a throw-away key, then `apksigcopier compare` |
| 4 | JDK 17 vs 21 reproducibility unknown | F-Droid's server may use a different JDK | §5 recipe |
| 5 | Committed binaries (wrapper jar, debug keystore) | scanner findings | run `fdroid scanner`; drop the debug keystore if asked |
| 6 | The About links are fixed at build time | a moved repo needs a new release | by design (4); document in the release routine |
| 7 | App only useful with a NextDNS account | label `NonFreeNet`; reviewers may ask for a test account or screenshots | screenshots + honest description |

## 7. Sources (read 2026-11-19)

- Inclusion Policy: https://f-droid.org/docs/Inclusion_Policy/
- Anti-Features: https://f-droid.org/docs/Anti-Features/
- Reproducible Builds: https://f-droid.org/docs/Reproducible_Builds/
- Build Metadata Reference: https://f-droid.org/docs/Build_Metadata_Reference/
- Descriptions, graphics, screenshots: https://f-droid.org/docs/All_About_Descriptions_Graphics_and_Screenshots/
- LLM Central's tested release notes: `UpperPanda1/assist` → `RELEASE.md`, `.github/workflows/release.yml`
