# zero-test-bridge — Agent Guide

Android library module. The seam between instrumentation tests and production code.

## Purpose

Bridges that let tests drive production state (clear/seed the DB, etc.) without `app/androidTest` reaching into production internals. The contracts (interfaces) and default implementations live here; the *wiring* lives on the prod side (`MainApplication`).

## Rules

1. **No dependencies on `app` or `zero-core`** — those modules depend on us, not the other way around. We depend on `zero-api` + `zero-database` (and any other prod modules we bridge).
2. **One interface per bridge concern** — `DatabaseTestBridge`, future `RemoteTestBridge`, etc. Don't merge them into a god-interface.
3. **`HasTestBridgeContainer` is the only handshake with the app** — production `Application` implements it. Tests cast and read. No setup, no factory, no DB-component access from the test module side.
4. **No test framework imports** — no JUnit, no Espresso, no AndroidX-test here. This module compiles for production too (it's a regular `implementation` dependency of `:app`). Test-runner-only utilities belong in `app/androidTest`.

## What Lives Here

- **`TestBridgeContainer`**: the bag of bridges (`database`, future `remote`, ...). Held by the production `Application`.
- **`HasTestBridgeContainer`**: interface implemented by the production `Application` so tests can access the container.
- **Per-domain bridge interfaces**: `DatabaseTestBridge`, etc.
- **Default implementations**: `DefaultDatabaseTestBridge`, etc. — wire from production repositories.

## Adding a New Bridge

1. Define `FooTestBridge` interface (suspend operations, return `Unit` or domain types).
2. Implement as `DefaultFooTestBridge(...)` taking the production collaborators it needs.
3. Add a `val foo: FooTestBridge` field to `TestBridgeContainer`.
4. Wire `DefaultFooTestBridge` in `MainApplication.testBridgeContainer`.
5. Use from `BaseE2eTest` via `container.foo`.
