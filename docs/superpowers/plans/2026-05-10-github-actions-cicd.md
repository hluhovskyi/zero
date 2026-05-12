# GitHub Actions CI/CD Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add two GitHub Actions workflows — CI (unit tests on PRs) and CD (daily version bump + signed AAB upload to Play internal track) — plus the Play Publishing API upload script.

**Architecture:** `version.properties` is the canonical version source, read by Gradle and bumped by the CD workflow. Signing reads from env vars injected at CI time. The Play upload is a self-contained bash script using five `curl` calls against the Android Publisher REST API.

**Tech Stack:** GitHub Actions, Gradle (Groovy DSL), bash, curl, jq, openssl

---

## File Map

| Action | File |
|--------|------|
| Create | `version.properties` |
| Modify | `.gitignore` |
| Modify | `app/build.gradle` |
| Create | `.github/workflows/ci.yml` |
| Create | `.github/workflows/cd.yml` |
| Create | `.github/scripts/upload-to-play.sh` |

---

### Task 1: version.properties + wire into app/build.gradle

**Files:**
- Create: `version.properties`
- Modify: `app/build.gradle`

- [ ] **Step 1: Create `version.properties` at the repo root**

```properties
versionCode=1
versionName=1.0.1
```

- [ ] **Step 2: Replace hardcoded version in `app/build.gradle`**

Replace the `defaultConfig` block (lines 10–21). Add the properties loader before the `android` block and update `versionCode`/`versionName` to read from it:

```groovy
plugins {
    id 'com.android.application'
    id 'com.google.devtools.ksp'
    id 'org.jetbrains.kotlin.plugin.compose'
}

def versionProps = new Properties()
file('../version.properties').withInputStream { versionProps.load(it) }

android {
    compileSdk versions.compileSdk

    defaultConfig {
        applicationId "com.hluhovskyi.zero"
        minSdk versions.minSdk
        targetSdk versions.targetSdk
        versionCode versionProps['versionCode'].toInteger()
        versionName versionProps['versionName']

        testInstrumentationRunner "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary true
        }
    }
    // ... rest of file unchanged
```

- [ ] **Step 3: Verify Gradle still evaluates cleanly**

```bash
./gradlew assembleDebug
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add version.properties app/build.gradle
git commit -m "feat: read versionCode and versionName from version.properties"
git push
```

---

### Task 2: Signing config in app/build.gradle

**Files:**
- Modify: `app/build.gradle`

- [ ] **Step 1: Add `signingConfigs` block and wire into release `buildType`**

Insert the `signingConfigs` block immediately before `buildTypes`. Update the release build type to reference it. The config reads from env vars so local builds without them are unaffected (debug builds don't use this config).

```groovy
    signingConfigs {
        release {
            def keystoreFile = System.getenv("KEYSTORE_FILE")
            storeFile keystoreFile ? file(keystoreFile) : null
            storePassword System.getenv("KEYSTORE_PASSWORD") ?: ""
            keyAlias System.getenv("KEY_ALIAS") ?: ""
            keyPassword System.getenv("KEY_PASSWORD") ?: ""
        }
    }

    buildTypes {
        release {
            signingConfig signingConfigs.release
            minifyEnabled false
            proguardFiles getDefaultProguardFile('proguard-android-optimize.txt'), 'proguard-rules.pro'
        }
    }
```

- [ ] **Step 2: Verify debug build still works (env vars absent locally)**

```bash
./gradlew assembleDebug
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/build.gradle
git commit -m "feat: add release signing config from env vars"
git push
```

---

### Task 3: Update .gitignore

**Files:**
- Modify: `.gitignore`

- [ ] **Step 1: Add keystore patterns to `.gitignore`**

Append to `.gitignore`:

```
*.jks
*.keystore
```

- [ ] **Step 2: Commit**

```bash
git add .gitignore
git commit -m "chore: ignore keystore files"
git push
```

---

### Task 4: CI workflow

**Files:**
- Create: `.github/workflows/ci.yml`

- [ ] **Step 1: Create `.github/workflows/` directory and `ci.yml`**

```yaml
name: CI

on:
  pull_request:
    branches: [ master ]

jobs:
  test:
    runs-on: ubuntu-latest

    steps:
      - uses: actions/checkout@v4

      - uses: actions/setup-java@v4
        with:
          java-version: '21'
          distribution: 'temurin'

      - uses: gradle/actions/setup-gradle@v3

      - name: Run unit tests
        run: ./gradlew test

      - name: Upload test reports
        if: failure()
        uses: actions/upload-artifact@v4
        with:
          name: test-reports
          path: '**/build/reports/tests/'
```

- [ ] **Step 2: Commit**

```bash
git add .github/workflows/ci.yml
git commit -m "feat: add CI workflow — unit tests on PRs"
git push
```

- [ ] **Step 3: Enable branch protection (manual — one time)**

In GitHub → repo Settings → Branches → Add rule for `master`:
- Check **Require status checks to pass before merging**
- Search for and add `test` (the job name from ci.yml)
- Check **Require branches to be up to date before merging**

---

### Task 5: CD workflow

**Files:**
- Create: `.github/workflows/cd.yml`

- [ ] **Step 1: Create `.github/workflows/cd.yml`**

```yaml
name: CD

on:
  schedule:
    - cron: '0 2 * * *'
  workflow_dispatch:

permissions:
  contents: write

jobs:
  deploy:
    runs-on: ubuntu-latest

    steps:
      - uses: actions/checkout@v4
        with:
          token: ${{ secrets.GITHUB_TOKEN }}

      - uses: actions/setup-java@v4
        with:
          java-version: '21'
          distribution: 'temurin'

      - uses: gradle/actions/setup-gradle@v3

      - name: Bump version
        run: |
          VERSION_CODE=$(grep 'versionCode' version.properties | cut -d'=' -f2)
          NEW_VERSION_CODE=$((VERSION_CODE + 1))
          printf "versionCode=%s\nversionName=1.0.%s\n" "$NEW_VERSION_CODE" "$NEW_VERSION_CODE" > version.properties
          echo "NEW_VERSION_CODE=$NEW_VERSION_CODE" >> "$GITHUB_ENV"

      - name: Commit and push version bump
        run: |
          git config user.name "github-actions[bot]"
          git config user.email "github-actions[bot]@users.noreply.github.com"
          git add version.properties
          git commit -m "chore: bump version to $NEW_VERSION_CODE"
          git push

      - name: Decode keystore
        run: echo "${{ secrets.KEYSTORE_BASE64 }}" | base64 --decode > /tmp/zero-release.jks

      - name: Build release AAB
        env:
          KEYSTORE_FILE: /tmp/zero-release.jks
          KEYSTORE_PASSWORD: ${{ secrets.KEYSTORE_PASSWORD }}
          KEY_ALIAS: ${{ secrets.KEY_ALIAS }}
          KEY_PASSWORD: ${{ secrets.KEY_PASSWORD }}
        run: ./gradlew bundleRelease

      - name: Upload to Play Store
        env:
          PLAY_SERVICE_ACCOUNT_JSON: ${{ secrets.PLAY_SERVICE_ACCOUNT_JSON }}
        run: |
          AAB_PATH=$(find app/build/outputs/bundle/release -name "*.aab" | head -1)
          bash .github/scripts/upload-to-play.sh "$AAB_PATH" "com.hluhovskyi.zero"

      - name: Cleanup keystore
        if: always()
        run: rm -f /tmp/zero-release.jks
```

> **Note on branch protection:** The `github-actions[bot]` push to `master` will be blocked if branch protection requires PRs. In GitHub → Branches → rule for `master`, add `github-actions[bot]` to the bypass list, or store a PAT as `GH_PAT` secret and replace `secrets.GITHUB_TOKEN` with `secrets.GH_PAT` in the checkout and push steps.

- [ ] **Step 2: Commit**

```bash
git add .github/workflows/cd.yml
git commit -m "feat: add CD workflow — daily version bump and Play upload"
git push
```

---

### Task 6: upload-to-play.sh

**Files:**
- Create: `.github/scripts/upload-to-play.sh`

The script takes two arguments: `$1` = path to `.aab`, `$2` = package name. Reads `PLAY_SERVICE_ACCOUNT_JSON` from the environment.

- [ ] **Step 1: Create `.github/scripts/upload-to-play.sh`**

```bash
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
```

- [ ] **Step 2: Make script executable**

```bash
chmod +x .github/scripts/upload-to-play.sh
```

- [ ] **Step 3: Commit**

```bash
git add .github/scripts/upload-to-play.sh
git commit -m "feat: add Play Store upload script via Publishing API"
git push
```
