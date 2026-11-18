#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
command -v java >/dev/null || { echo 'JDK 17+ is required.' >&2; exit 1; }
if [ -z "${ANDROID_HOME:-}${ANDROID_SDK_ROOT:-}" ] && [ ! -f local.properties ]; then
  echo 'Install Android SDK 36 and set ANDROID_HOME or local.properties.' >&2; exit 1
fi
version=8.13
if [ ! -x ".tools/gradle-$version/bin/gradle" ]; then
  mkdir -p .tools
  url="https://services.gradle.org/distributions/gradle-$version-bin.zip"
  curl --fail --location --proto '=https' "$url" -o ".tools/gradle-$version-bin.zip"
  expected=$(curl --fail --location --proto '=https' "$url.sha256")
  printf '%s  %s\n' "$expected" ".tools/gradle-$version-bin.zip" | sha256sum --check -
  unzip -q ".tools/gradle-$version-bin.zip" -d .tools
fi
if [ "$#" -eq 0 ]; then set -- :app:assembleDebug :app:lintDebug; fi
exec ".tools/gradle-$version/bin/gradle" "$@" --no-daemon
