# Backup

Android Auto Backup rules live in `app/src/main/res/xml/backup_rules.xml` (SDK ≤30) and `data_extraction_rules.xml` (SDK 31+), wired on `<application>` in the manifest. Explicit Google Drive backup (the `zero-backup` module) arrives in later phases.

## Non-obvious conventions

- **An `<include>` turns the block into an allowlist** — once any path is included, *everything else* (all prefs, files, caches) is silently dropped from the backup. Only `zero.db` is included today, so it's the only thing backed up.
- **Never `<exclude>` a path that's already outside the allowlist** — e.g. a `sharedpref` file when only a database is included. It's redundant *and* fails the `FullBackupContent` lint check, because the path sits under no `<include>`. This is why the device-bound `com.google.android.gms.appid.xml` has no exclude. `<exclude>` is valid *only* to carve a sub-path out of an include — the `zero.db-wal`/`-shm` excludes do exactly that, since `zero.db` is a path *prefix* that would otherwise sweep them in.
- **`zero_secure_prefs` is already excluded by the allowlist — keep it that way.** `AndroidSecureKeyValueStore` (file `zero_secure_prefs`) is `EncryptedSharedPreferences` wrapped by a Keystore-backed `MasterKey`. It holds **only a small `connected` flag** so `isSignedIn` survives process death — **no token, no refresh token, no secret** (Drive auth mints short-lived access tokens on demand; see Auth below). Keystore keys are **device-bound**, so backing the file up would push an undecryptable blob to a new device. Because only `zero.db` is included, this file is dropped implicitly; **never add it to an `<include>`.** The correct UX on phone swap is to re-sign-in.

## Auth (Google Drive)

Drive backup auth (`zero-auth/GoogleOAuthTokenProvider`) uses the **Google Identity Authorization API** (`Identity.getAuthorizationClient`, `play-services-auth`): one-time `drive.appdata` consent, then mint short-lived **access tokens** on demand (silent afterwards, including in the background via Play services). There is **no refresh token, no token-endpoint exchange, no Web client ID, no client secret, and no `HttpExecutor`** — the app is identified solely by its Android OAuth client (package + signing-cert SHA-1). This is the WhatsApp/Viber model.

- **A spec that pairs an OAuth *refresh-token* flow with "public client / no backend / no client secret" is self-contradictory — flag it at planning time.** Minting a refresh token needs a confidential client (server secret) or a PKCE browser flow; a serverless public client can't. `GetGoogleIdOption` / Credential Manager only *authenticate* (ID token) — they never return a Drive grant. This was missed for the Phase 2 spec; only the real-Drive smoke test caught it (every `getAccessToken()` → `AuthExpired`), because the fake-`HttpExecutor` unit tests accepted anything.
- **Play-services-backed auth is not unit-testable — it's manual-smoke-test gated.** Prefer integration tests against `MockWebServer` simulating real OAuth error shapes (`invalid_grant`, `unauthorized_client`) over fakes that accept anything.

## Restore entry point

The backup-detail **Restore** button navigates to Import (`navigator.navigateTo(Destinations.Import)`) and the user taps **Google Drive** on source selection — it does **not** pre-select Drive. `ImportUseCase` is `@ImportScope` (built fresh per navigation), so `perform(SelectSource)` on a shared instance never reaches the rendered screen without an invasive app-scoped refactor. Drive's `requiresFile = false` skips the file picker → all-new fast path → `RestoreSuccess`. The full Drive round-trip can't be verified headlessly (interactive OAuth).
