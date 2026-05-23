#!/usr/bin/env bash
# Redeploys the feedback Cloud Function with the current source.
#
# This is the QUICK path for a code-only change (e.g. editing index.js).
# It deploys new source and leaves all existing config untouched:
# gcloud preserves env vars, secrets, and the service account when the
# corresponding --set-* flags are omitted. To CHANGE config — or for a
# first-time deploy when the function doesn't exist yet — use the full
# command in README.md instead.
#
# No account-identifying values (SA email, project number, secret name)
# live in this script; they persist from the deployed function.
set -euo pipefail

NAME="feedback"

PROJECT="$(gcloud config get-value project 2>/dev/null)"
# Region isn't a top-level field; it lives in the resource name
# (projects/<p>/locations/<region>/functions/<name>) — segment(3).
REGION="$(gcloud functions list --filter="name~${NAME}" --format='value(name.segment(3))' 2>/dev/null | head -1)"

if [ -z "$REGION" ]; then
  echo "Function '${NAME}' not found in project '${PROJECT:-<unset>}'." >&2
  echo "For a first-time deploy (which must set env vars, secret, and service" >&2
  echo "account), use the full 'gcloud functions deploy' command in README.md." >&2
  exit 1
fi

HERE="$(cd "$(dirname "$0")" && pwd)"

echo "▶ redeploying '${NAME}' to ${REGION} (project ${PROJECT}) — config preserved..."
gcloud functions deploy "$NAME" \
  --gen2 \
  --region="$REGION" \
  --runtime=nodejs20 \
  --source="$HERE" \
  --entry-point="$NAME" \
  --trigger-http
