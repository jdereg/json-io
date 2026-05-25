#!/usr/bin/env bash
# Re-vendor TOON spec conformance fixtures at a specified commit.
#
# Usage:
#   scripts/refresh-toon-fixtures.sh <commit-sha>
#
# Example:
#   scripts/refresh-toon-fixtures.sh 07161ccc84242a5571f14d33504fc0b4b84da0b2
#
# Requires: `gh` CLI (authenticated), `tar`, GNU sed or BSD sed.

set -euo pipefail

if [ $# -ne 1 ]; then
    echo "Usage: $0 <commit-sha>" >&2
    exit 1
fi

SHA="$1"
REPO="toon-format/spec"
DEST="$(cd "$(dirname "$0")/.." && pwd)/json-io/src/test/resources/toon-spec-fixtures"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

echo "Downloading tarball for $REPO @ $SHA ..."
gh api "repos/$REPO/tarball/$SHA" > "$TMP/spec.tar.gz"

echo "Extracting ..."
mkdir -p "$TMP/extract"
tar -xzf "$TMP/spec.tar.gz" -C "$TMP/extract" --strip-components=1

SRC="$TMP/extract/tests"
if [ ! -d "$SRC/fixtures" ]; then
    echo "Error: expected $SRC/fixtures to exist; spec repo layout may have changed." >&2
    exit 2
fi

echo "Replacing fixtures in $DEST ..."
rm -rf "$DEST/fixtures"
cp -r "$SRC/fixtures" "$DEST/fixtures"
cp "$SRC/fixtures.schema.json" "$DEST/fixtures.schema.json"
if [ -f "$SRC/README.md" ]; then
    cp "$SRC/README.md" "$DEST/upstream-README.md"
fi

DATE_ISO="$(gh api "repos/$REPO/commits/$SHA" --jq '.commit.author.date' | cut -d'T' -f1)"
VERSION="$(gh api "repos/$REPO/commits/$SHA" --jq '.commit.message' | head -1 | sed -E 's/.*v([0-9]+\.[0-9]+(\.[0-9]+)?).*/\1/')"

# Rewrite the pinned-version block in MANIFEST.md.
MANIFEST="$DEST/MANIFEST.md"
if [ -f "$MANIFEST" ]; then
    python3 - "$MANIFEST" "$VERSION" "$SHA" "$DATE_ISO" <<'PY'
import re, sys, pathlib
path, version, sha, date_iso = sys.argv[1:]
text = pathlib.Path(path).read_text()
text = re.sub(r"(\| Spec release \| \*\*)[^*]*(\*\* \|)", lambda m: m.group(1) + "v" + version + m.group(2), text)
text = re.sub(r"(\| Commit \| `)[^`]+(` \|)", lambda m: m.group(1) + sha + m.group(2), text)
text = re.sub(r"(\| Date \| )[^|]+( \|)", lambda m: m.group(1) + date_iso + " " + m.group(2), text)
pathlib.Path(path).write_text(text)
PY
fi

ENCODE_COUNT="$(ls "$DEST/fixtures/encode" 2>/dev/null | wc -l | tr -d ' ')"
DECODE_COUNT="$(ls "$DEST/fixtures/decode" 2>/dev/null | wc -l | tr -d ' ')"
echo "Done. Encode fixtures: $ENCODE_COUNT, Decode fixtures: $DECODE_COUNT."
echo "Inspect the diff with: git -C \"$(dirname "$DEST")\" status"
