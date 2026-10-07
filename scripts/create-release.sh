#!/bin/bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
DIST="$ROOT/dist"
VERSION="0.2.1"
TAG="v$VERSION"
DMG="$DIST/LidSleepX-$VERSION.dmg"
NOTES="$ROOT/RELEASE_NOTES.md"
REPO="wises231/kmp-LidSleepX"

if ! command -v gh >/dev/null 2>&1; then
  printf 'Install the GitHub CLI first: brew install gh\n' >&2
  exit 1
fi

if ! gh auth status >/dev/null 2>&1; then
  printf 'Authenticate first: gh auth login\n' >&2
  exit 1
fi

if [[ ! -f "$DMG" ]]; then
  printf 'Missing artifact: %s\nRun scripts/package-macos.sh first.\n' "$DMG" >&2
  exit 1
fi

# The app reads the Releases page.
# Therefore this release must stay a full release, not a pre-release.
gh release create "$TAG" "$DMG" \
  --repo "$REPO" \
  --title "LidSleepX $VERSION (internal test)" \
  --notes-file "$NOTES"

printf 'Created release %s in %s\n' "$TAG" "$REPO"
