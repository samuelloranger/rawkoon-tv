#!/usr/bin/env bash
# Checks that each given APK is signed with the first RawkoonTV's release certificate, the only
# key installed copies accept. Used by scripts/release.sh and the release workflow. apksigner comes
# from $APKSIGNER, else the newest build-tools under the Android SDK.
#
#   scripts/verify-signature.sh app/build/outputs/apk/release/*.apk
set -euo pipefail

# The first RawkoonTV's release certificate (public).
expected_cert="a2719edc5c9d1a7551b0a41d027d40551e14daf12f359ff5fa1ae38aa983ad8b"

(( $# > 0 )) || { echo "usage: scripts/verify-signature.sh <apk>..." >&2; exit 2; }

apksigner="${APKSIGNER:-}"
if [[ -z "$apksigner" ]]; then
  sdk="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Android/Sdk}}"
  apksigner="$(ls -d "$sdk"/build-tools/*/apksigner 2>/dev/null | sort -V | tail -1 || true)"
fi
[[ -n "$apksigner" && -x "$apksigner" ]] || { echo "apksigner not found: set APKSIGNER or ANDROID_HOME" >&2; exit 2; }

for apk in "$@"; do
  certs="$("$apksigner" verify --print-certs "$apk")"
  grep -q "certificate SHA-256 digest: $expected_cert" <<<"$certs" \
    || { echo "$apk is not signed with the RawkoonTV key" >&2; exit 1; }
  echo "ok: $apk"
done
