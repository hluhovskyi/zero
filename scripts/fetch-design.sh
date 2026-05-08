#!/usr/bin/env bash
# Usage: fetch-design.sh <hash>
# Downloads and extracts a Claude Design archive to /tmp/design-<hash>/
set -eo pipefail

HASH="${1:?Usage: fetch-design.sh <hash>}"
ARCHIVE="/tmp/design-${HASH}.tar.gz"
OUT="/tmp/design-${HASH}"

curl -s \
  -H "x-api-key: $ANTHROPIC_API_KEY" \
  -H "anthropic-version: 2023-06-01" \
  "https://api.anthropic.com/v1/design/h/${HASH}" \
  -o "${ARCHIVE}"

mkdir -p "${OUT}"
tar xzf "${ARCHIVE}" -C "${OUT}"

echo "Extracted to ${OUT}"
