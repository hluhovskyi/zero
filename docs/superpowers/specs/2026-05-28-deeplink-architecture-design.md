# Deep-link Architecture

**Date:** 2026-05-28
**Context:** Phase 4 (Google Drive backup) introduced a feature-specific `BackupDeepLinkSignal` plumbed through five layers (ApplicationComponent → ActivityComponent → MainActivityScreenComponent → ViewProvider → LaunchedEffect). That doesn't scale to a second deep-link. This spec replaces it with a single mechanism that all future internal triggers (notifications, app shortcuts, widgets) use without per-feature plumbing.

## Goal

One mechanism, one declaration per deep-linkable destination, zero new wiring layers per new deep-link.

## Mechanism: native Compose Navigation deep-links

Compose Navigation supports `composable(route, deepLinks = listOf(navDeepLink { uriPattern = "..." }))`. NavHost matches incoming `ACTION_VIEW` intents against the declared patterns and navigates automatically. We use this directly.

### URI scheme

`zero://<route>` where `<route>` is the existing `Destination.route` template (e.g. `zero://backup`, `zero://transactions/{transactionId}`). Internal-only — no app-links / DAL / `autoVerify`. Future graduation to `https://` is non-breaking.

### Producer flow (notifications, shortcuts, widgets)

```kotlin
val pendingIntent = PendingIntent.getActivity(
    context,
    requestCode,
    Intent(Intent.ACTION_VIEW, "zero://backup".toUri())
        .setPackage(context.packageName),
    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
)
```

No per-feature signal, no feature-specific intent extras.

### Consumer flow (destination registration)

```kotlin
navigatorScope.buildable(
    destination = Destinations.Backup,
    deepLinks = listOf("zero://backup"),
) { builder.onBackHandler { navigator.back() }.logging(logger) }
```

The deep-link URI is declared next to the destination — same call, same module, no separate plumbing.

## Generic plumbing

Three additions to the existing navigation abstraction:

1. **`NavigatorEntry.deepLinks: List<String>`** (default empty) — carries the URI patterns through to the NavHost layer.
2. **Optional `deepLinks: List<String>` param** on `NavigatorScope.composable/buildable/component` — destinations opt in by passing a non-empty list.
3. **URI translation in `MainActivityScreenViewProvider`** — maps each pattern string to `navDeepLink { uriPattern = it }` when registering Compose `composable(route, deepLinks = …)`.

Plus one manifest declaration on MainActivity:

```xml
<intent-filter>
    <action android:name="android.intent.action.VIEW" />
    <category android:name="android.intent.category.DEFAULT" />
    <data android:scheme="zero" />
</intent-filter>
```

That's everything generic. New deep-links add one URI pattern at the destination, one PendingIntent at the producer. No interface changes, no DI plumbing.

## Removed from Phase 4

- `app/src/main/java/com/hluhovskyi/zero/backup/BackupDeepLinkSignal.kt`
- `backupDeepLinkSignal` on `ApplicationComponent` (abstract val + bridge `@Provides`)
- Same field on `ActivityComponent.Dependencies` + `MainActivityScreenComponent.Dependencies`
- Same binding from `BackupApplicationComponent` (scheduler + notification presenter stay)
- `EXTRA_OPEN_SETTINGS_BACKUP` constant in `BackupNotificationPresenter`
- `handleBackupDeepLink()` + `onNewIntent` override in `MainActivity`
- `deepLinkRequests` + `onBackupDeepLink` constructor params on `MainActivityScreenViewProvider`
- The `LaunchedEffect(Unit) { deepLinkRequests.collect { ... } }` block

## Edge cases

- **Routes with args** — `zero://transactions/{transactionId}` is parsed identically to the in-app navigation route; NavHost extracts args from the URI. No special handling.
- **Process death** — the system preserves the PendingIntent's Intent across process recreation. NavHost replays it after graph setup. No queueing needed on our side.
- **Back stack** — Compose deep-link inserts the start destination (Home) below the deep-linked destination. Back from Backup goes to Home, which is desired.
- **Auto-cancel on tap** — handled by the existing `setAutoCancel(true)` on the notification builder.

## Out of scope

- `https://` app links + DAL / `assetlinks.json` hosting — graduating later is non-breaking (add new `uriPattern`s alongside `zero://` ones).
- Third-party app invocation (an external app calling `zero://`) — we deliberately set `setPackage(context.packageName)` on PendingIntents to keep this internal. Other apps that send a `zero://` intent without `setPackage` would still match by manifest declaration; if that becomes undesirable, drop `android:exported="true"` from the intent-filter (currently inherited from MainActivity's existing `exported`).
- Typed URI helpers (`Destinations.Backup.deepLinkUri()`) — YAGNI for now; one literal per deep-link is fine.

## Verification

- Tests + lint green (no new behavior to unit-test; deep-link wiring is exercised end-to-end).
- Manual: notification tap from a forced 3-failure state opens the Backup screen, back returns to Home.
