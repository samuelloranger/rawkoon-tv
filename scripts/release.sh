#!/usr/bin/env bash
# Builds signed RawkoonTV release APKs (with the MPV and decoder extensions) and
# publishes them as a GitHub release, under the asset names the in-app updater
# looks for. Run from a clean checkout of the release branch:
#
#   RAWKOONTV_SIGNING_DIR=/path/to/signing scripts/release.sh v2.0.0
#
# The signing dir holds release.jks and signing.env (KEY_ALIAS, KEY_PASSWORD,
# KEY_STORE_PASSWORD). The extensions registry is read with `gh auth token`,
# so the gh account needs read:packages. Nothing secret is written to the repo.
set -euo pipefail

tag="${1:?usage: scripts/release.sh vX.Y.Z}"
[[ "$tag" =~ ^v[0-9]+\.[0-9]+\.[0-9]+$ ]] || { echo "tag must look like v1.2.3" >&2; exit 2; }
signing="${RAWKOONTV_SIGNING_DIR:?set RAWKOONTV_SIGNING_DIR}"
repo="$(gh repo view --json nameWithOwner -q .nameWithOwner)"

cd "$(git rev-parse --show-toplevel)"
[[ -z "$(git status --porcelain)" ]] || { echo "working tree is not clean" >&2; exit 2; }
git rev-parse -q --verify "refs/tags/$tag" >/dev/null || git tag -a "$tag" -m "RawkoonTV $tag"

set -a; . "$signing/signing.env"; set +a
export CI=true
SIGNING_KEY="$(base64 -w0 "$signing/release.jks")"; export SIGNING_KEY
export ORG_GRADLE_PROJECT_WholphinExtensionsUsername="$(gh api user -q .login)"
ORG_GRADLE_PROJECT_WholphinExtensionsPassword="$(gh auth token)"; export ORG_GRADLE_PROJECT_WholphinExtensionsPassword
trap 'rm -f app/ci.keystore' EXIT

rm -rf app/build/outputs/apk
./gradlew --no-daemon :app:assembleDefaultRelease

out="$(mktemp -d)"
for apk in app/build/outputs/apk/default/release/*.apk; do
  # RawkoonTV-default-release-2.0.0-0-gabc1234-54-arm64-v8a.apk -> RawkoonTV-arm64-v8a.apk
  short="$(basename "$apk" | sed -E 's/-default-release-[0-9]+\.[0-9]+\.[0-9]+(-[0-9]+-g[a-fA-F0-9]+)?-[0-9]+//')"
  cp "$apk" "$out/$short"
done
for apk in "$out"/*.apk; do
  grep -q "libmpv.so" <(unzip -l "$apk") || { echo "$apk has no MPV: extensions missing" >&2; exit 1; }
done
(cd "$out" && sha256sum ./*.apk)

git push origin "$tag"
gh release create "$tag" --repo "$repo" --verify-tag --latest --title "$tag" --generate-notes "$out"/*.apk
