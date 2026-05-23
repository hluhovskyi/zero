# zero-auth — Module Guide

Google sign-in / OAuth flow. Holds the Android-side implementation of `OAuthTokenProvider`
(in `zero-api/auth/`). Generic over Google scopes — Drive backup is one consumer; future
integrations (Calendar, Sheets, ...) can build their own `AuthComponent` with different scopes
without changing this module.

## Responsibility

- Implement `OAuthTokenProvider` against Android Credential Manager + Google ID + the OAuth 2.0
  token endpoint.
- Persist refresh tokens via `SecureKeyValueStore` (interface from `zero-api/security/`).
- Use `HttpExecutor` (interface from `zero-api/http/`) for token exchange and revoke calls —
  do not import OkHttp directly.

## Encapsulation

- **No networking concerns** (those live in `zero-remote`). This module never imports OkHttp.
- **No UI** — token UI (sign-in CTA, status row) lives in `zero-core`.
- All implementation classes are `internal`; the public surface is `AuthComponent`.

## Key Invariants

- Refresh tokens are written through `SecureKeyValueStore`, never to plain SharedPreferences.
- Access tokens live in memory only — never persist them.
- OAuth scopes are a compile-time constant inside `AuthComponent.Module` (a `@Provides`
  returning `listOf("...drive.appdata")`), not a runtime parameter. Future Google integrations
  needing different scopes either recompile or build a sibling component.
