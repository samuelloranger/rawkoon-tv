#!/usr/bin/env bash
# Builds the signed RawkoonTV release APKs locally, checks them, then pushes the tag. The
# release workflow (.github/workflows/release.yml) is the only thing that publishes: it rebuilds
# from the tag and attaches RawkoonTV-<abi>.apk, RawkoonTV.apk and a .sha256 for each. Run from
# a clean checkout of the commit to release:
#
#   RAWKOONTV_SIGNING_DIR=/path/to/signing scripts/release.sh v3.0.0 [--dry-run]
#
# The signing dir holds release.jks and signing.env (KEY_ALIAS, KEY_PASSWORD,
# KEY_STORE_PASSWORD). Nothing secret is written to the repo. --dry-run stops after the checks
# and leaves no tag behind.
set -euo pipefail

tag="${1:?usage: scripts/release.sh vX.Y.Z [--dry-run]}"
dry="${2:-}"
[[ "$tag" =~ ^v[0-9]+\.[0-9]+\.[0-9]+$ ]] || { echo "tag must look like v1.2.3" >&2; exit 2; }
[[ -z "$dry" || "$dry" == "--dry-run" ]] || { echo "unknown option $dry" >&2; exit 2; }
signing="${RAWKOONTV_SIGNING_DIR:?set RAWKOONTV_SIGNING_DIR}"
[[ -f "$signing/release.jks" && -f "$signing/signing.env" ]] || { echo "$signing needs release.jks and signing.env" >&2; exit 2; }

cd "$(git rev-parse --show-toplevel)"
[[ -z "$(git status --porcelain)" ]] || { echo "working tree is not clean" >&2; exit 2; }
# The signing dir is the only key source here: a CI-style key in the shell must not win.
unset SIGNING_KEY

# The version comes from the tag, so the build must see it. A tag that already exists is only
# reused when it points at HEAD, so an old tag on an old commit is never pushed.
created=
if git rev-parse -q --verify "refs/tags/$tag" >/dev/null; then
  [[ "$(git rev-parse "$tag^{commit}")" == "$(git rev-parse HEAD)" ]] \
    || { echo "tag $tag exists on another commit than HEAD; delete it or check out its commit" >&2; exit 2; }
else
  git tag -a "$tag" -m "RawkoonTV $tag"
  created=1
fi
# A tag this run created goes away on any failure, and after a dry run; it stays only once pushed.
cleanup() {
  if [[ -n "$created" ]]; then git tag -d "$tag" >/dev/null 2>&1 || true; fi
}
trap cleanup EXIT

./gradlew --no-daemon testDebugUnitTest
rm -rf app/build/outputs/apk/release
./gradlew --no-daemon assembleRelease -Prawkoontv.signingDir="$signing"

scripts/verify-signature.sh app/build/outputs/apk/release/RawkoonTV*.apk

[[ -z "$dry" ]] || { echo "dry run: APKs verified, no tag pushed"; exit 0; }
git push origin "$tag"
created=
echo "Pushed $tag: the release workflow publishes it."
