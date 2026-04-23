# zero-sync — Module Guide

Portable JSON export/import and delta sync engine for all user data.

## Responsibility

- Serialize/deserialize `SyncSnapshot` (the export file format).
- Merge incoming entities against local state via `ConflictResolver<T>`.
- Orchestrate pull → merge → push cycles against any `EntitySyncSource`/`Sink`.
- **No Room, no Android dependencies.** Pure Kotlin JVM module.

## Key Invariants

- **Never modify `updatedDateTime` on write.** Timestamps from the source are preserved exactly. Any code that overwrites timestamps breaks LWW.
- **Tombstones are entities.** A `deletedAt != null` entity is a valid sync record. Do not skip or filter tombstones during sync.
- **Processing order is mandatory.** Categories → Accounts → Transactions. Changing this order causes dangling references on import.
- **`lastSyncedAt` advances only on full success.** If any push chunk fails, `lastSyncedAt` must not advance. The next sync retries from the previous high-water mark.
- **Same-device export → import always produces an empty delta.** Equal `updatedDateTime` → LWW keeps local → `computeDelta`'s filter removes it → all three lists are empty. This is correct behavior, not a bug. Callers must handle the empty-delta case explicitly (e.g., navigate to an "up to date" state) rather than passing it to review screens where the user sees "0 items".

## Backward Compatibility Rules

- Every field on every `@Serializable` class in this module (and in `zero-api`'s sync package) MUST have `@SerialName`. This is enforced by the `SyncEntityFieldMustHaveSerialName` lint rule — build will fail without it.
- When changing a JSON key (breaking change): increment `version` in `SyncSnapshot`, add a migration path, add new fixture files (`v2-*.json`). **Never modify existing fixture files.**
- When adding optional fields: no version bump needed; use nullable types with `null` defaults.

## Planned Improvements

- **High-level file I/O on `SyncEngine`.** Currently callers receive/pass `SyncSnapshot` and must handle serialization themselves. The goal is to add `exportTo(userId, uri: Uri.NonEmpty)` and `importFrom(uri: Uri.NonEmpty, userId)` so no caller ever touches JSON. Blocked on: extending `ResourceResolver` (in `zero-api`/`zero-core`) to support write/OutputStream access alongside the existing read path. The read side already works via `UriRequest` — see `ZenMoneyImportSourceUseCase` for the pattern. Avoid `java.io.OutputStream` directly in the interface (JVM-only, breaks KMP); route through `ResourceResolver` instead.

## Testing

- `zero-sync` unit tests use in-memory fakes for `EntitySyncSource`/`Sink`. No Room.
- `SyncBackwardCompatibilityTest` reads every `src/test/resources/fixtures/sync/*.json` file and verifies deserialization. If you add a new entity field, add a fixture that exercises it.
- Round-trip tests live in `SyncSerializerRoundTripTest`. Add a case for any new edge value (new enum variant, new nullable field, extreme BigDecimal).
