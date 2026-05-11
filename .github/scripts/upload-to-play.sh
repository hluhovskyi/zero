#!/usr/bin/env bash
set -euo pipefail

AAB_PATH="$1"
PACKAGE_NAME="$2"

# --- Extract service account credentials ---
private_key=$(echo "$PLAY_SERVICE_ACCOUNT_JSON" | jq -r '.private_key')
client_email=$(echo "$PLAY_SERVICE_ACCOUNT_JSON" | jq -r '.client_email')

# --- Build JWT for OAuth ---
now=$(date +%s)
exp=$((now + 3600))

header_b64=$(printf '{"alg":"RS256","typ":"JWT"}' | base64 -w 0 | tr -d '=' | tr '+/' '-_')
payload_b64=$(printf '{"iss":"%s","scope":"https://www.googleapis.com/auth/androidpublisher","aud":"https://oauth2.googleapis.com/token","exp":%d,"iat":%d}' \
  "$client_email" "$exp" "$now" | base64 -w 0 | tr -d '=' | tr '+/' '-_')

signing_input="${header_b64}.${payload_b64}"

key_file=$(mktemp)
echo "$private_key" > "$key_file"
signature=$(printf '%s' "$signing_input" | openssl dgst -sha256 -sign "$key_file" | base64 -w 0 | tr -d '=' | tr '+/' '-_')
rm "$key_file"

jwt="${signing_input}.${signature}"

# --- Exchange JWT for access token ---
token_response=$(curl -s -X POST "https://oauth2.googleapis.com/token" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=urn%3Aietf%3Aparams%3Aoauth%3Agrant-type%3Ajwt-bearer&assertion=${jwt}")
access_token=$(echo "$token_response" | jq -r '.access_token')

if [[ "$access_token" == "null" || -z "$access_token" ]]; then
  echo "ERROR: Failed to get access token: $token_response"
  exit 1
fi

base_url="https://androidpublisher.googleapis.com/androidpublisher/v3/applications/${PACKAGE_NAME}"

# --- 1. Create edit ---
edit_response=$(curl -s -X POST "${base_url}/edits" \
  -H "Authorization: Bearer ${access_token}" \
  -H "Content-Type: application/json" \
  -d '{}')
edit_id=$(echo "$edit_response" | jq -r '.id')

if [[ "$edit_id" == "null" || -z "$edit_id" ]]; then
  echo "ERROR: Failed to create edit: $edit_response"
  exit 1
fi
echo "Edit created: $edit_id"

# --- 2. Upload AAB ---
upload_response=$(curl -s -X POST \
  "https://androidpublisher.googleapis.com/upload/androidpublisher/v3/applications/${PACKAGE_NAME}/edits/${edit_id}/bundles?uploadType=media" \
  -H "Authorization: Bearer ${access_token}" \
  -H "Content-Type: application/octet-stream" \
  --data-binary "@${AAB_PATH}")
version_code=$(echo "$upload_response" | jq -r '.versionCode')

if [[ "$version_code" == "null" || -z "$version_code" ]]; then
  echo "ERROR: Failed to upload AAB: $upload_response"
  exit 1
fi
echo "AAB uploaded, versionCode: $version_code"

# --- 3. Assign to internal track ---
track_response=$(curl -s -X PUT "${base_url}/edits/${edit_id}/tracks/internal" \
  -H "Authorization: Bearer ${access_token}" \
  -H "Content-Type: application/json" \
  -d "{\"releases\":[{\"versionCodes\":[\"${version_code}\"],\"status\":\"completed\"}]}")
echo "Track assigned: $(echo "$track_response" | jq -r '.track')"

# --- 4. Commit edit ---
commit_response=$(curl -s -X POST "${base_url}/edits/${edit_id}:commit" \
  -H "Authorization: Bearer ${access_token}")
echo "Edit committed: $(echo "$commit_response" | jq -r '.id')"
