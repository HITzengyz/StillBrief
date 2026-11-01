#!/usr/bin/env bash
set -euo pipefail
# Build with ./gradlew assembleRelease first. Keep .signing/ private and backed up.
repo_dir="$(cd "$(dirname "$0")/.." && pwd)"
signing_dir="${STILLBRIEF_SIGNING_DIR:-$repo_dir/.signing}"
: "${ANDROID_HOME:?Set ANDROID_HOME to your Android SDK}"
apksigner="$ANDROID_HOME/build-tools/${ANDROID_BUILD_TOOLS:-35.0.0}/apksigner"
input_apk="$repo_dir/app/build/outputs/apk/release/app-release-unsigned.apk"
output_apk="${1:-$repo_dir/app/build/outputs/apk/release/StillBrief-release.apk}"
"$apksigner" sign --ks "$signing_dir/legacy.keystore" --ks-key-alias androiddebugkey --ks-pass pass:android \
  --next-signer --ks "$signing_dir/release.jks" --ks-key-alias stillbrief --ks-pass "file:$signing_dir/release-password.txt" \
  --lineage "$signing_dir/release.lineage" --rotation-min-sdk-version 28 --v4-signing-enabled false \
  --debuggable-apk-permitted false --out "$output_apk" "$input_apk"
"$apksigner" verify --verbose --print-certs "$output_apk"
