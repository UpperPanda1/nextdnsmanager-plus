# Releasing NextDNS Manager+ (GitHub + F-Droid)

Written for a first-time Android publisher, modelled on LLM Central's `RELEASE.md` (`UpperPanda1/assist`, newest branch
`claude/confident-brahmagupta-9iiv9x`, read 2026-11-19). It covers:

1. what is still needed before the first public release;
2. the store files;
3. the one-time signing-key set-up;
4. how to publish a release on GitHub (and why signing is done the way it is);
5. F-Droid (the details are in [`fdroid.md`](fdroid.md));
6. what was checked.

The workflow is `.github/workflows/release.yml`. **Status 2026-11-19:** its scripts were run locally on this app's APK
(version/tag/changelog/address check in four cases; signing with a test key; F-Droid's `apksigcopier compare`: passed).
It has **not run on GitHub yet**: the first run will be a dry run (§4).

---

## 1. Before the first public release: to-do list

| # | What | Why | Status |
|---|---|---|---|
| 1 | Own app icon (`artwork/icon.svg`) and store icon (`fastlane/…/images/icon.png`) | F-Droid requires forks to change name, icon and strings | ✅ done |
| 2 | `changelogs/1.txt` | the workflow refuses to release without the versionCode's changelog | ✅ done (342 characters) |
| 3 | `title.txt`, `short_description.txt` (≤ 80), `full_description.txt` (≤ 4000) | F-Droid listing; texts suggested in `fdroid.md` §3.1 | **to do** |
| 4 | Screenshots `fastlane/…/images/phoneScreenshots/1.png …` | F-Droid's listing; the README can show them too | **to do (phone)** |
| 5 | Decide the public name ("NextDNS" is a trademark) | reviewers may ask: `fdroid.md` §3.2 | **owner** |
| 6 | Phone test (`CHANGES.md` checklists) | nothing has run on a phone yet | **owner** |
| 7 | Signing key + 4 GitHub secrets (§3) | needed for a real release | **owner (once)** |
| 8 | Dry run with the test key, then with the real key (§4) | proves the workflow on GitHub; gives the fingerprint for F-Droid | after 7 |
| 9 | `app.repoUrl` in `gradle.properties` = the repository you release from | the workflow stops a real release otherwise (§4) | check before tagging |

## 2. Store files

Paths from F-Droid's "All About Descriptions, Graphics and Screenshots" (as in LLM Central):

```
fastlane/metadata/android/en-US/
├── title.txt               app name, max 50 characters
├── short_description.txt   one line, max 80 characters, no final "."
├── full_description.txt    max 4000 characters, simple HTML allowed
├── changelogs/
│   └── 1.txt               "what's new" for versionCode 1, max 500   (done; next release: 2.txt)
└── images/
    ├── icon.png            512×512 store icon                         (done: made by tools/icon)
    ├── featureGraphic.png  optional, 1024×500
    └── phoneScreenshots/1.png, 2.png …                               (to do)
```

## 3. One-time set-up: your signing key

Android only installs an **update** signed with the **same key** as the installed app. **If you lose this key, you can
never update the app again**; users would have to uninstall it and lose their accounts. Back it up (password manager
plus an offline copy).

1. **Create the key** on your computer (`keytool` comes with any JDK):
   ```bash
   keytool -genkeypair -v -keystore nextdnsmanager-release.jks -alias nextdnsmanager \
     -keyalg RSA -keysize 4096 -validity 10000
   ```
   When it asks for your name, type e.g. **NextDNS Manager Plus**: avoid `+` and `,` (special characters in
   certificate names; `keytool` refuses `+` with "Incorrect AVA format", seen 2026-11-19). **Never commit the `.jks`**;
   keep it outside the repository folder (`*.jks` is git-ignored anyway).
2. **Add four secrets** to the repository you release from: Settings → Secrets and variables → Actions → *New
   repository secret*:

   | Secret | Value |
   |---|---|
   | `RELEASE_KEYSTORE_BASE64` | `base64 -w0 nextdnsmanager-release.jks` (macOS: `base64 -i …`; Windows PowerShell: `[Convert]::ToBase64String([IO.File]::ReadAllBytes("nextdnsmanager-release.jks"))`) |
   | `RELEASE_KEYSTORE_PASSWORD` | the keystore password |
   | `RELEASE_KEY_ALIAS` | `nextdnsmanager` |
   | `RELEASE_KEY_PASSWORD` | **the same password**: current `keytool` makes a PKCS12 keystore with one password for store and key (LLM Central, checked 2026-10-18) |

3. **Note the fingerprint** for F-Droid's `AllowedAPKSigningKeys`: the workflow prints it on the run's summary page
   (`Signer #1 certificate SHA-256 digest`), or `keytool -list -v -keystore nextdnsmanager-release.jks` (SHA256 line,
   colons removed, lower case).

## 4. Publishing a release on GitHub

**Dry run (publishes nothing):** Actions → **Release** → *Run workflow* → key:
- `test`: a throw-away key made in the run. Checks version, changelog, build, signing and F-Droid's check.
- `real`: your four secrets. Run it once after §3 to read **your** fingerprint before the first tag.

The result is a signed APK as a 7-day download on the run page. ("Run workflow" appears only once `release.yml` is on
the repository's **default branch**; until then, a pull request that changes `release.yml` or `app/build.gradle` runs
the same dry run with a test key.)

**Every release:**
1. `app/build.gradle`: raise **`versionCode`** by 1 and set **`versionName`** (literal values; the workflow and F-Droid
   read them as text).
2. Write `fastlane/metadata/android/en-US/changelogs/<versionCode>.txt` (≤ 500 characters): release notes and
   F-Droid's "What's new".
3. `CHANGES.md` entry; README if features changed; run `tools/readme-badges` (version and APK size chips). CI green.
4. Check `app.repoUrl` in `gradle.properties` is this repository's address.
5. **Tag** `v<versionName>` exactly (e.g. `v0.1.0`) on a clean commit: on github.com, Releases → *Draft a new release* →
   "Choose a tag" → `v0.1.0` → *Publish release*; or `git tag v0.1.0 && git push origin v0.1.0`.
6. Watch **Actions → Release**. It:
   1. checks the tag matches `versionName`, the changelog exists (≤ 500), and `app.repoUrl` is this repository;
   2. runs tests and lint and builds the unsigned APK (exactly what F-Droid builds: no `-P` flags);
   3. signs it with `apksigner` 34, v1 off;
   4. runs F-Droid's check (`apksigcopier compare`);
   5. shows the APK's SHA-256, the unsigned APK's SHA-256 and the key fingerprint on the summary page;
   6. creates the release "NextDNS Manager+ 0.1.0" with `NextDNS-Manager-Plus-0.1.0.apk` (notes = changelog + SHA-256).
7. Install that APK on your phone. It is a different app from the debug build (`org.rafee_m.nextdnsmanagerplus` vs
   `….debug`), so both can be installed; accounts and settings don't move between them.

**Why signing is done this way (tested on this app's APK, 2026-11-19).** F-Droid checks the developer's APK by copying
its signature onto its own build of the same commit (`apksigcopier`, version 1.1.1):

| Signed with | F-Droid check |
|---|---|
| `apksigner` 34, `--v1-signing-enabled false` | **passes** |
| `apksigner` 34, default options | fails ("CHUNKED_SHA512 digest mismatch"): apksigner adds v1 JAR signature files, and copying them breaks the v3 digest |
| `apksigner` 35, `--v1-signing-enabled false` | fails (same message; F-Droid documents this for 35) |
| Gradle's own signing (`signing.*` in `local.properties`) | fails in LLM Central's test ("APK Signing Block offset < central directory offset"); not re-tested here |

LLM Central's workflow uses apksigner 34 *without* the v1 switch, which passed **for LLM Central** (2026-09-30); for this
app it does not, hence the difference. With v1 off, the APK is signed with scheme **v3** only, which every Android
version this app supports (10+) verifies.

**If a tag was wrong:** delete the release and the tag on GitHub (and locally: `git tag -d v0.1.0`), fix, tag again.
Never re-use a versionCode that users may have installed.

## 5. F-Droid

Everything about the submission (checklist, recipe draft, anti-feature, reproducibility evidence, risks) is in
[`fdroid.md`](fdroid.md). In short: after the first GitHub release, fork `gitlab.com/fdroid/fdroiddata`, add
`metadata/org.rafee_m.nextdnsmanagerplus.yml` with `Binaries:` pointing at `NextDNS-Manager-Plus-%v.apk` and
`AllowedAPKSigningKeys:` = your fingerprint (§3), run `fdroid lint` / `fdroid build`, open the merge request. Later
releases are picked up from the tags automatically.

## 6. What was checked

| Claim | How |
|---|---|
| Same unsigned APK from different clones and from JDK 17 and 21 | builds of commit `cc867de`, 2026-11-19 (`fdroid.md` §5) |
| Signing table above | `apksigner` 34.0.0 / 35.0.0 and `apksigcopier` 1.1.1 on this app's release APK, 2026-11-19 |
| The workflow's scripts | its `run:` blocks extracted from the YAML and executed locally: check step (good tag → pass; wrong tag → fail; moved repository + tag → fail; fork dry run → warning), sign (test key) → compare → summary, all passed; keystore deleted afterwards |
| Not checked | a run on GitHub (runner image, `sdkmanager` path, secrets), F-Droid's own build server, a real key |
