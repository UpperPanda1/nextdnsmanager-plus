#!/bin/bash
# SessionStart hook for Claude Code on the web (cloud sessions only).
#
# Every cloud session starts in a fresh container without the Android SDK, so the app can't be built
# or tested until it's installed. This script does that set-up automatically before Claude starts
# working, and is safe to run again (it skips what's already there). It changes nothing in the app.
#
# What it does (details in CLAUDE.md, "Build & verify"):
#   1. Installs the Android SDK command-line tools, accepts the licences, and installs the pieces
#      the build uses: platform android-36 (compileSdk), build-tools 35.0.0, platform-tools.
#   2. Tells Gradle where the SDK is (ANDROID_HOME for the session, sdk.dir in local.properties).
#   3. Adds a Gradle init script (outside the repo) that downloads Maven Central artifacts from
#      Google's official mirror: Maven Central answers this sandbox with HTTP 429 (too many requests).
#   4. Warms up Gradle: downloads Gradle itself and every dependency needed for unit tests and lint,
#      so the cached container can run `./gradlew test lint ...` without waiting for downloads.
set -euo pipefail

# Only in Claude Code on the web; never on a developer's own computer.
if [ "${CLAUDE_CODE_REMOTE:-}" != "true" ]; then
  exit 0
fi

PROJECT_DIR="${CLAUDE_PROJECT_DIR:-$(cd "$(dirname "$0")/../.." && pwd)}"
SDK_DIR="${ANDROID_HOME:-$HOME/android-sdk}"
SDKMANAGER="$SDK_DIR/cmdline-tools/latest/bin/sdkmanager"
# Command-line tools release (the same as in LLM Central).
TOOLS_ZIP_URL="https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip"
SDK_PACKAGES=("platforms;android-36" "build-tools;35.0.0" "platform-tools")

log() { echo "[session-start] $*" >&2; }

# 1a. Command-line tools (sdkmanager, apkanalyzer).
if [ ! -x "$SDKMANAGER" ]; then
  log "Installing Android SDK command-line tools into $SDK_DIR"
  mkdir -p "$SDK_DIR/cmdline-tools"
  tmp="$(mktemp -d)"
  curl -fsSL -o "$tmp/tools.zip" "$TOOLS_ZIP_URL"
  unzip -q "$tmp/tools.zip" -d "$tmp"
  rm -rf "$SDK_DIR/cmdline-tools/latest"
  mv "$tmp/cmdline-tools" "$SDK_DIR/cmdline-tools/latest"
  rm -rf "$tmp"
fi

# 1b. Licences + SDK packages (sdkmanager skips packages that are already installed).
missing=0
[ -d "$SDK_DIR/platforms/android-36" ] || missing=1
[ -d "$SDK_DIR/build-tools/35.0.0" ] || missing=1
[ -d "$SDK_DIR/platform-tools" ] || missing=1
if [ "$missing" = 1 ]; then
  log "Installing SDK packages: ${SDK_PACKAGES[*]}"
  yes | "$SDKMANAGER" --sdk_root="$SDK_DIR" --licenses >/dev/null 2>&1 || true
  "$SDKMANAGER" --sdk_root="$SDK_DIR" "${SDK_PACKAGES[@]}" >/dev/null
fi

# 2. Where the SDK is: for this session's shell commands, and for Gradle itself.
if [ -n "${CLAUDE_ENV_FILE:-}" ]; then
  {
    echo "export ANDROID_HOME=\"$SDK_DIR\""
    echo "export ANDROID_SDK_ROOT=\"$SDK_DIR\""
    echo "export PATH=\"\$PATH:$SDK_DIR/cmdline-tools/latest/bin:$SDK_DIR/platform-tools\""
  } >> "$CLAUDE_ENV_FILE"
fi
# local.properties is git-ignored; Android Studio writes the same line on a developer's computer.
if ! grep -qs '^sdk.dir=' "$PROJECT_DIR/local.properties"; then
  echo "sdk.dir=$SDK_DIR" >> "$PROJECT_DIR/local.properties"
fi

# 3. Maven Central mirror for Gradle (local to this container, never committed).
mkdir -p "$HOME/.gradle/init.d"
cat > "$HOME/.gradle/init.d/maven-central-mirror.gradle" <<'GRADLE'
// Written by .claude/hooks/session-start.sh (cloud sessions only). Maven Central rate-limits this
// machine (HTTP 429), so the same artifacts are downloaded from Google's official Maven Central mirror.
def mirror = 'https://maven-central.storage-download.googleapis.com/maven2/'
def redirect = { repos ->
    repos.withType(MavenArtifactRepository).configureEach { r ->
        if (r.url.toString().contains('repo.maven.apache.org') || r.url.toString().contains('repo1.maven.org')) {
            r.url = mirror
        }
    }
}
beforeSettings { settings ->
    redirect(settings.pluginManagement.repositories)
    redirect(settings.dependencyResolutionManagement.repositories)
}
// The Gradle Plugin Portal (listed first in settings.gradle) answers most of AGP's dependencies with a
// redirect to repo.maven.apache.org, which the rewrite above can't see; a 429 there fails the build
// instead of trying the next repository (seen 2026-10-31). So the mirror is asked first for plugins too.
settingsEvaluated { settings ->
    def repos = settings.pluginManagement.repositories
    def first = repos.maven { url = mirror; name = 'MavenCentralMirrorFirst' } // maven {} appends it…
    repos.remove(first)
    repos.addFirst(first)                                                   // …so move it to the front
}
GRADLE

# 4. Warm-up: fetch Gradle + all test/lint dependencies (one worker = gentle on the servers).
#    A network hiccup here must not block the session, so failure is only reported.
cd "$PROJECT_DIR"
chmod +x ./gradlew
export ANDROID_HOME="$SDK_DIR"
if ./gradlew --no-daemon --max-workers=1 -q testDebugUnitTest lintDebug >/dev/null 2>&1; then
  log "Ready: Android SDK at $SDK_DIR, Gradle dependencies downloaded, unit tests and lint pass."
else
  log "WARNING: Gradle warm-up failed (network or build problem). Run './gradlew test lint' to see why."
fi
