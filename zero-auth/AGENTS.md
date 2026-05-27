# zero-auth — Module Guide

Google authorization for Drive. Holds the Android-side implementation of `OAuthTokenProvider`
(in `zero-api/auth/`). Generic over Google scopes — Drive backup is one consumer; future
integrations (Calendar, Sheets, ...) can build their own `AuthComponent` with different scopes
without changing this module.

## Responsibility

- Implement `OAuthTokenProvider` against the **Google Identity Authorization API**
  (`Identity.getAuthorizationClient`, `play-services-auth`): request the configured scopes and
  mint short-lived **access tokens** on demand.
- Launch the one-time consent `PendingIntent` on the foreground activity (via
  `ActivityResultRegistry`).

## Encapsulation

- **No networking, no OkHttp, no `HttpExecutor`.** Auth no longer hits an HTTP endpoint itself —
  Play services brokers the tokens. (Drive REST networking lives in `zero-backup` + `zero-remote`.)
- **No UI** beyond the system consent sheet — token UI (sign-in CTA, status row) lives in `zero-core`.
- All implementation classes are `internal`; the public surface is `AuthComponent`.

## Key Invariants

- **No refresh token, no client secret, no Web client ID, no `buildConfigField`.** The app is
  identified by its Android OAuth client (package + signing-cert SHA-1); the OS presents the
  signature. Minting a refresh token would require a backend secret — out of scope (there is no
  Zero server). Access tokens live in memory only; the long-lived grant is held by Google.
- `SecureKeyValueStore` holds only a small "connected" flag so `isSignedIn` survives process
  death — there is no secret on device.
- OAuth scopes are a compile-time constant inside `AuthComponent.Module` (a `@Provides`
  returning `listOf("...drive.appdata")`), not a runtime parameter.

## Testing

No unit tests — the provider talks to Play services (Authorization API) for both sign-in and
access-token minting, neither of which is unit-testable. Validated on-device in the manual
smoke test.
