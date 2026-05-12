# GitHub Actions CI/CD — Design Spec

**Date:** 2026-05-10
**Branch:** `feature/github-actions-cicd`

## Goal

Set up two GitHub Actions workflows from scratch:
- **CI** — run unit tests on every PR to `master`; block merge on failure
- **CD** — daily scheduled build that bumps the version, builds a signed release AAB, and uploads it to the Google Play internal testing track

No existing CI/CD, no existing Play Developer account, no keystore.

## Repository Structure

```
.github/
  workflows/
    ci.yml                   # unit tests on PRs
    cd.yml                   # daily: bump version → build → upload
  scripts/
    upload-to-play.sh        # Play Publishing API via curl

version.properties           # versionCode=N  versionName=1.0.N
```

`version.properties` is committed to the repo and is the canonical version source. The CD workflow reads it, increments `versionCode`, writes it back, and pushes a `chore: bump version to <N>` commit before building.

## CI Workflow

**File:** `.github/workflows/ci.yml`
**Trigger:** `pull_request` targeting `master`

Steps:
1. Checkout
2. Set up JDK 21
3. Cache Gradle dependencies
4. `./gradlew test`
5. Upload test reports as artifacts on failure

**Branch protection:** `master` requires this check to pass before merge. Configured once in GitHub → Settings → Branches (not in the workflow file).

## CD Workflow

**File:** `.github/workflows/cd.yml`
**Trigger:** `schedule` cron, daily at 02:00 UTC

Steps:
1. Checkout with a write token so the version bump commit can be pushed
2. Set up JDK 21 + Gradle cache
3. Read `version.properties`, increment `versionCode`, update `versionName` to `1.0.<versionCode>`, write back
4. Commit and push the version bump to `master`
5. Decode `KEYSTORE_BASE64` secret to `/tmp/zero-release.jks`
6. `./gradlew bundleRelease` — signing config reads keystore path and credentials from env vars
7. `.github/scripts/upload-to-play.sh` — uploads the AAB to Play
8. Delete the temp keystore file

## Signing Config

`app/build.gradle` release signing reads from environment variables:

| Env var            | GitHub Secret      |
|--------------------|--------------------|
| `KEYSTORE_FILE`    | decoded from `KEYSTORE_BASE64` |
| `KEYSTORE_PASSWORD`| `KEYSTORE_PASSWORD` |
| `KEY_ALIAS`        | `KEY_ALIAS`        |
| `KEY_PASSWORD`     | `KEY_PASSWORD`     |

The `.jks` file is never committed. `.gitignore` will include `*.jks` and `*.keystore`.

## upload-to-play.sh — Play Publishing API

Five sequential `curl` calls against the Android Publisher REST API. Tools required: `curl`, `jq`, `openssl` (all pre-installed on `ubuntu-latest`).

**Input:** path to the AAB, package name, service account JSON (from `PLAY_SERVICE_ACCOUNT_JSON` secret).

1. **Get OAuth token** — extract private key + client email from the service account JSON, build a signed JWT, POST to `https://oauth2.googleapis.com/token`, receive a bearer token
2. **Create edit** — `POST .../applications/{packageName}/edits` → `editId`
3. **Upload AAB** — `POST .../edits/{editId}/bundles` with the `.aab` as binary body
4. **Assign track** — `PUT .../edits/{editId}/tracks/internal` with the accepted `versionCode`
5. **Commit edit** — `POST .../edits/{editId}:commit` — makes the release live on internal track

If any step fails, the edit is never committed and Play state is unchanged.

## Required GitHub Secrets

| Secret | How to obtain |
|--------|---------------|
| `KEYSTORE_BASE64` | `base64 -i zero-release.jks \| pbcopy` |
| `KEYSTORE_PASSWORD` | chosen when generating the keystore |
| `KEY_ALIAS` | chosen when generating the keystore |
| `KEY_PASSWORD` | chosen when generating the keystore |
| `PLAY_SERVICE_ACCOUNT_JSON` | Google Cloud Console → IAM → Service Accounts → create key (JSON) |

## One-Time Manual Prerequisites

These must be done by the developer before the first CD run:

1. **Generate keystore** with `keytool`, back it up to a password manager (file + all four credentials)
2. **Create Google Play Developer account** (one-time $25 fee) at play.google.com/console
3. **Create the app** in Play Console with package name `com.hluhovskyi.zero`; upload a manual first release to unlock the API
4. **Create Google Cloud service account** with `Release Manager` role on the app; download JSON key
5. **Add all five GitHub Secrets** to the repo under Settings → Secrets → Actions
6. **Enable branch protection** on `master` requiring the CI check to pass

## Version Strategy

`version.properties` format:
```
versionCode=1
versionName=1.0.1
```

The CD workflow increments `versionCode` by 1 and sets `versionName` to `1.0.<versionCode>`. Each daily release produces a traceable `chore: bump version to <N>` commit on `master`, making it easy to correlate a broken build to its exact code snapshot.
