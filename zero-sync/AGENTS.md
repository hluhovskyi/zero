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

## Backward Compatibility Rules

- Every field on every `@Serializable` class in this module (and in `zero-api`'s sync package) MUST have `@SerialName`. This is enforced by the `SyncEntityFieldMustHaveSerialName` lint rule — build will fail without it.
- When changing a JSON key (breaking change): increment `version` in `SyncSnapshot`, add a migration path, add new fixture files (`v2-*.json`). **Never modify existing fixture files.**
- When adding optional fields: no version bump needed; use nullable types with `null` defaults.

## Testing

- `zero-sync` unit tests use in-memory fakes for `EntitySyncSource`/`Sink`. No Room.
- `SyncBackwardCompatibilityTest` reads every `src/test/resources/fixtures/sync/*.json` file and verifies deserialization. If you add a new entity field, add a fixture that exercises it.
- Round-trip tests live in `SyncSerializerRoundTripTest`. Add a case for any new edge value (new enum variant, new nullable field, extreme BigDecimal).
