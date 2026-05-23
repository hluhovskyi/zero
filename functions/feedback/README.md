# Feedback Cloud Function

Receives feedback reports from the Zero Android app, verifies the request via Play Integrity, and files a GitHub issue server-side.

Phase 1 of `docs/superpowers/specs/2026-05-12-feedback-infra-design.md` (issue #81).

## Endpoint

`POST /` — single handler. Body:

```json
{
    "title": "string (required)",
    "body":  "string (required)",
    "type":  "bug | idea | other (required)",
    "debug": "boolean (optional, default false)"
}
```

Headers: `X-Integrity-Token: <token from StandardIntegrityManager.request>`.

GitHub labels are computed server-side from `type` (the client never names a label): every issue gets `feedback`, plus the mapped type label (`bug`/`idea`/`other`), plus `debug` when `debug=true`. Unknown types are rejected with 400.

Responses:
- `201 { "issueUrl": "https://github.com/..." }` — issue created.
- `400` — payload missing or malformed.
- `401` — missing or undecodable token.
- `403` — Integrity verdict rejected (not Play-recognized, not meeting device integrity, or wrong package).
- `502` — GitHub call failed.

## Required env vars

| Name | Purpose |
|---|---|
| `PACKAGE_NAME` | Android package, e.g. `com.hluhovskyi.zero`. Used both for the Integrity decode call and to verify `verdict.appIntegrity.packageName`. |
| `GCP_PROJECT_NUMBER` | The Cloud project number whose Integrity API decodes tokens. Set when deploying the function. |
| `REPO_OWNER` | GitHub user/org owning the issue tracker. |
| `REPO_NAME` | Repository name. |
| `GITHUB_TOKEN` | PAT (or GitHub App token) with `issues:write`. Stored in Secret Manager and mounted as an env var. |

## IAM

The function's runtime service account needs **`roles/playintegrity.tokenDecoder`** on the GCP project that hosts the Integrity API.

## Deploy

**Code-only change** (the common case — you edited `index.js`): run `./deploy.sh`. It redeploys the current source and preserves all existing config (env vars, secret, service account), so no real values are needed.

**First-time deploy or config change** (placeholders only — never paste real values into commits): use the full command, which sets every flag explicitly.

```bash
gcloud functions deploy feedback \
    --gen2 \
    --runtime=nodejs20 \
    --region=<your-region> \
    --source=. \
    --entry-point=feedback \
    --trigger-http \
    --no-allow-unauthenticated \
    --service-account=<sa-email> \
    --set-env-vars=PACKAGE_NAME=<package>,REPO_OWNER=<owner>,REPO_NAME=<repo>,GCP_PROJECT_NUMBER=<number> \
    --set-secrets=GITHUB_TOKEN=<secret-name>:latest
```

## Manual smoke test

A real `X-Integrity-Token` only comes from a Play-Integrity-registered Android device, so end-to-end testing requires a debug build with the dev device registered as a Play Integrity test device (see `docs/agents/feedback-infra.md`). To capture a token, log it from `OkHttpFeedbackService` temporarily and replay via:

```bash
curl -X POST <function-url> \
    -H "Content-Type: application/json" \
    -H "X-Integrity-Token: <token>" \
    -d '{"title":"smoke test","body":"sent via curl","type":"bug","debug":true}'
```

Remove the temporary token logging before merging anything.

## Logging

Cloud Functions emit logs to Cloud Logging. **Never log request bodies** — user descriptions can contain PII. Log decision booleans (token present? Integrity ok?) and error class names only.
