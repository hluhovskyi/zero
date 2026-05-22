# Feedback Infra

How the abuse-protected feedback pipe is wired. Phase 1 of [issue #81](https://github.com/hluhovskyi/zero/issues/81); design lives in [docs/superpowers/specs/2026-05-12-feedback-infra-design.md](../superpowers/specs/2026-05-12-feedback-infra-design.md).

## Surface

`zero-api` defines:

- `FeedbackReport(title, body, labels)`
- `FeedbackSubmitResult` — `Success(issueUrl)` or `Failure` (single generic failure; transport details stay inside `zero-remote`).
- `FeedbackService.submit(report): FeedbackSubmitResult`

`zero-remote` provides the only implementation, exposed via `RemoteComponent.feedbackService`. The lint rule `RemoteComponentEncapsulation` enforces that no OkHttp / Play Integrity / Json types leak through the component's public surface.

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

## Debug builds (Play Integrity test devices)

There is no special debug code path — `OkHttpFeedbackService` is the only binding in all build types. To make debug installs work end-to-end:

1. Open Play Console → App integrity → Integrity API → **Test devices**.
2. Add the Google Account associated with your dev device.
3. Configure the test device response: enable **`MEETS_DEVICE_INTEGRITY`** and set `appRecognitionVerdict` = **`PLAY_RECOGNIZED`**.
4. Install your debug build on that device. Play Integrity will mint valid tokens, and the cloud function will accept them.

Without this setup, debug installs receive a `null` token and `FeedbackService.submit` returns `Failure` with a `Timber.w` log — no crash, no spam to the issue tracker.

## Convention: tag debug submissions

Phase 2 callers should append `"debug"` to `report.labels` when `BuildConfig.DEBUG` so the issue tracker is filterable. Phase 1 only documents the convention; no debug-detection code in the submitter itself.

## Crash reporting config

Adjacent to feedback but a separate pipe. See [docs/superpowers/specs/2026-05-16-crash-analytics-design.md](../superpowers/specs/2026-05-16-crash-analytics-design.md). Sentry DSN flows the same way as `FEEDBACK_ENDPOINT` but is **read by `zero-crash/build.gradle`**, not `app/build.gradle`.

| Where | `SENTRY_DSN` |
|---|---|
| Local dev | `local.gradle.properties` → `sentryDsn=https://...` (gitignored, read by `zero-crash/build.gradle`) |
| CD release builds | `${{ vars.SENTRY_DSN }}` in `.github/workflows/cd.yml` — GitHub Variable, not Secret (DSN ships in the AAB anyway) |

No Cloud Function involvement. No `SENTRY_AUTH_TOKEN` needed in v1 because the app currently ships `minifyEnabled false`; if mapping upload is ever required, that adds the Sentry Gradle plugin + an auth-token secret.

## Verifying changes

- Unit tests: `./gradlew :zero-remote:testDebugUnitTest`
- Lint (encapsulation rule + standard checks): `./gradlew :app:lintDebug`
- End-to-end: install the debug build on a registered test device, drive `feedbackService.submit(...)` from a temporary entry point, confirm the issue lands in the configured repo.
