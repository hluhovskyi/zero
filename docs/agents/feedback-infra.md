# Feedback Infra

How the abuse-protected feedback pipe is wired. Phase 1 of [issue #81](https://github.com/hluhovskyi/zero/issues/81); design lives in [docs/superpowers/specs/2026-05-12-feedback-infra-design.md](../superpowers/specs/2026-05-12-feedback-infra-design.md).

## Surface (traps only — read `zero-api/feedback/` for the types)

- **Client never names GitHub labels.** `FeedbackReport` carries `type: FeedbackType` and `isDebug: Boolean`; the function maps both to GitHub labels. Don't add label strings on the Android side.
- **`RemoteComponentEncapsulation` lint rule** blocks OkHttp / Play Integrity / Json types from leaking through `zero-remote`'s public surface. If you need a new transport detail in `zero-api`, add it as a domain type, not a re-export.

## Distribution gate

Operational. Existing CD (`.github/workflows/cd.yml`) uploads release builds to the Play **Internal Testing** track. Do not promote to Production while abuse risk is unmitigated. The on-device Play Integrity check (below) means a leaked AAB cannot mint valid tokens, so the cloud function rejects sideloaded copies.

## Secrets matrix

| Where | `FEEDBACK_ENDPOINT` | `FEEDBACK_INTEGRITY_PROJECT` | Other |
|---|---|---|---|
| Local dev | `local.gradle.properties` (gitignored) | `local.gradle.properties` | Dev device registered as Play Integrity test device |
| CD release builds | GitHub Actions secret of same name | GitHub Actions secret of same name | n/a |
| Cloud Function env | n/a | n/a | `GITHUB_TOKEN`, `REPO_OWNER`, `REPO_NAME`, `PACKAGE_NAME`, `GCP_PROJECT_NUMBER` |

There is **no `FEEDBACK_SECRET`**. Authentication uses Play Integrity exclusively.

`local.gradle.properties` example (never commit real values):

```
feedbackEndpoint=https://<region>-<project>.cloudfunctions.net/feedback
feedbackIntegrityProject=<gcp-project-number>
```

## Cloud function

Deployed from `functions/feedback/`. See `functions/feedback/README.md` for deploy command, IAM step (`roles/playintegrity.tokenDecoder`), and env var details.

The function decodes the `X-Integrity-Token` via Google's Play Integrity API, validates the three verdicts (`PLAY_RECOGNIZED`, `MEETS_DEVICE_INTEGRITY`, expected package name), then files the GitHub issue server-side using a PAT it owns. The PAT never reaches the AAB.

**Must stay `--allow-unauthenticated`** (`allUsers` → `roles/run.invoker`). The app authenticates with the in-code Play Integrity token, not a Google IAM bearer token. Lock it to IAM and Cloud Run returns a platform **403** (`Empty Authorization header value`) before `index.js` runs — every report fails and the integrity logs stay empty, so it mimics a verdict rejection but isn't one. Play Integrity is the gate; IAM invocation auth must stay open.

**Label naming is server-side.** The request body is `{title, body, type, debug}`. The function builds `labels = ["feedback", TYPE_TO_LABEL[type], ...maybeDebug]` and rejects unknown `type` with 400. To rename a GitHub label (or split one type into multiple labels), change `TYPE_TO_LABEL` in `index.js` and redeploy — no app release needed. The labels named in `TYPE_TO_LABEL` must exist on the issue tracker (`bug`, `idea`, `other`); unknown labels make Octokit's `issues.create` fail and the function returns 502.

## Debug builds (Play Integrity test devices)

There is no special debug code path — `OkHttpFeedbackService` is the only binding in all build types. To make debug installs work end-to-end:

1. Open Play Console → App integrity → Integrity API → **Test devices**.
2. Add the Google Account associated with your dev device.
3. Configure the test device response: enable **`MEETS_DEVICE_INTEGRITY`** and set `appRecognitionVerdict` = **`PLAY_RECOGNIZED`**.
4. Install your debug build on that device. Play Integrity will mint valid tokens, and the cloud function will accept them.

Without this setup, debug installs receive a `null` token and `FeedbackService.submit` returns `Failure` with a `Timber.w` log — no crash, no spam to the issue tracker.

## Crash reporting config

Adjacent to feedback but a separate pipe. See [docs/superpowers/specs/2026-05-16-crash-analytics-design.md](../superpowers/specs/2026-05-16-crash-analytics-design.md). Sentry DSN flows the same way as `FEEDBACK_ENDPOINT` but is **read by `zero-crash/build.gradle`**, not `app/build.gradle`.

| Where | `SENTRY_DSN` |
|---|---|
| Local dev | `local.gradle.properties` → `sentryDsn=https://...` (gitignored, read by `zero-crash/build.gradle`) |
| CD release builds | `${{ vars.SENTRY_DSN }}` in `.github/workflows/cd.yml` — GitHub Variable, not Secret (DSN ships in the AAB anyway) |

No Cloud Function involvement. No `SENTRY_AUTH_TOKEN` needed in v1 because the app currently ships `minifyEnabled false`; if mapping upload is ever required, that adds the Sentry Gradle plugin + an auth-token secret.
