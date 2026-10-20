#!/usr/bin/env bash
# Builds signed RawkoonTV release APKs and publishes them as a GitHub release, under the asset
# names both updaters look for (RawkoonTV-<abi>.apk, RawkoonTV.apk and a .sha256 for each).
# Run from a clean checkout of the branch to release:
#
#   RAWKOONTV_SIGNING_DIR=/path/to/signing scripts/release.sh v3.0.0
#
# The signing dir holds release.jks and signing.env (KEY_ALIAS, KEY_PASSWORD,
# KEY_STORE_PASSWORD). Nothing secret is written to the repo. Pushing the tag instead runs
# the same build in CI (.github/workflows/release.yml).
set -euo pipefail

tag="${1:?usage: scripts/release.sh vX.Y.Z}"
[[ "$tag" =~ ^v[0-9]+\.[0-9]+\.[0-9]+$ ]] || { echo "tag must look like v1.2.3" >&2; exit 2; }
signing="${RAWKOONTV_SIGNING_DIR:?set RAWKOONTV_SIGNING_DIR}"
[[ -f "$signing/release.jks" && -f "$signing/signing.env" ]] || { echo "$signing needs release.jks and signing.env" >&2; exit 2; }
# From the origin remote: gh's own default for a fork is its parent repo.
repo="$(git remote get-url origin | sed -E 's#^(https://github.com/|git@github.com:)##; s#\.git$##')"

cd "$(git rev-parse --show-toplevel)"
[[ -z "$(git status --porcelain)" ]] || { echo "working tree is not clean" >&2; exit 2; }
git rev-parse -q --verify "refs/tags/$tag" >/dev/null || git tag -a "$tag" -m "RawkoonTV $tag"

# The version comes from the tag, so the build must see it.
./gradlew --no-daemon testDebugUnitTest
rm -rf app/build/outputs/apk/release
./gradlew --no-daemon assembleRelease -Prawkoontv.signingDir="$signing"

out="$(mktemp -d)"
cp app/build/outputs/apk/release/RawkoonTV*.apk "$out"/
(cd "$out" && for apk in *.apk; do sha256sum "$apk" > "$apk.sha256"; done && cat ./*.sha256)

git ls-remote --exit-code --tags origin "refs/tags/$tag" >/dev/null || git push origin "$tag"
# The title must be the bare tag: the first RawkoonTV reads the release's name as its version.
gh release create "$tag" --repo "$repo" --verify-tag --latest --title "$tag" --generate-notes "$out"/*
