#!/usr/bin/env bash
# Builds the signed Android release artifacts.
#
# The API origin is a build input, not a source default: release builds refuse to
# fall back to a placeholder host, so this script requires a real HTTPS origin.
#
#   GMS_API_BASE_URL=https://api.example.school ./tool/build_release.sh
set -euo pipefail

cd "$(dirname "${BASH_SOURCE[0]}")/.."

: "${GMS_API_BASE_URL:?set GMS_API_BASE_URL to the deployment origin, e.g. https://api.school.example}"
if [[ "$GMS_API_BASE_URL" != https://* ]]; then
  echo "GMS_API_BASE_URL must start with https:// (got: $GMS_API_BASE_URL)" >&2
  exit 1
fi
if [[ "$GMS_API_BASE_URL" == */ ]]; then
  echo "GMS_API_BASE_URL must not end with a slash" >&2
  exit 1
fi

flutter pub get
flutter build appbundle --release \
  --dart-define=ENVIRONMENT=production \
  --dart-define=API_BASE_URL="$GMS_API_BASE_URL"
flutter build apk --release \
  --dart-define=ENVIRONMENT=production \
  --dart-define=API_BASE_URL="$GMS_API_BASE_URL"

AAB=build/app/outputs/bundle/release/app-release.aab
APK=build/app/outputs/flutter-apk/app-release.apk
for artifact in "$AAB" "$APK"; do
  [[ -s "$artifact" ]] || { echo "missing artifact: $artifact" >&2; exit 1; }
done

SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-/usr/local/share/android-commandlinetools}}"
BUILD_TOOLS="$SDK/build-tools/35.0.0"
"$BUILD_TOOLS/apksigner" verify --print-certs "$APK" | sed -n '1,4p'
"$BUILD_TOOLS/aapt2" dump badging "$APK" | grep -E "^package:|^sdkVersion:|^targetSdkVersion:"

shasum -a 256 "$AAB" "$APK"
echo "API origin: $GMS_API_BASE_URL"
