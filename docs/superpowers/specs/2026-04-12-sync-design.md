# Sync / Import / Export — Design Spec

**Date:** 2026-04-12
**Module:** `zero-sync`
**Status:** Approved for implementation

---

## Goals

- Export all user data to a portable JSON file that can be used to fully restore the app on a new device.
- Import data from such a file using delta merge (LWW), so the same operation works for both full restore and partial sync.
- Sync data with a remote backend (Firebase, future) using the same delta merge protocol.
- Support extensible conflict resolution per entity type via a `ConflictResolver<T>` strategy.

## Non-Goals

- Real-time collaborative editing (multi-user).
- Event sourcing / full audit trail.
- Field-level merge (CRDTs, vector clocks).
- Automatic deduplication by content (e.g., two "Groceries" categories created independently are treated as distinct entities).

---

## Core Principles

**Single merge mode.** There is no separate "full restore" vs "delta merge" mode. Delta merge against an empty local state produces a full restore. The merge logic is always the same: LWW by `updatedDateTime`.

**Client-first.** The backend is dumb storage. All conflict resolution happens on the client. The backend never mutates or reorders data.

**Tombstones, not hard deletes.** Deleted entities are marked with `deletedAt: <ISO datetime>` instead of being removed. This ensures deletions propagate correctly on merge.

**Idempotent.** Importing the same file twice produces the same state. Syncing the same remote data twice is a no-op.

---

## Export Format

### File structure

```json
{
  "version": 1,
  "userId": "<uuid>",
  "exportedAt": "2026-04-12T10:00:00",
  "transactions": [...],
  "accounts": [...],
  "categories": [...]
}
```

- `version`: integer, incremented when the format changes in a backward-incompatible way. Importers must reject files with an unsupported version.
- `userId`: hoist from all entities; injected on import from the current session.
- `exportedAt`: informational timestamp for the export operation itself.

### Transaction item

```json
{
  "id": "<uuid>",
  "type": "EXPENSE | INCOME | TRANSFER",
  "accountId": "<uuid>",
  "currencyId": "USD",
  "categoryId": "<uuid | null>",
  "amount": "123.45",
  "rate": "1.0",
  "targetAccountId": "<uuid | null>",
  "targetAmount": "<string | null>",
  "enteredDateTime": "2024-01-15T10:30:00",
  "creationDateTime": "2024-01-15T10:30:00",
  "updatedDateTime": "2024-01-15T10:30:00",
  "deletedAt": "<ISO datetime | null>"
}
```

- `amount` / `targetAmount` are **strings** to preserve `BigDecimal` precision. Never use JSON numbers for monetary values.
- `deletedAt` is `null` for live entities, an ISO datetime string for tombstones.
- `type = TRANSFER` uses `targetAccountId` + `targetAmount`; both are `null` for EXPENSE/INCOME.

### Account item

```json
{
  "id": "<uuid>",
  "currencyId": "USD",
  "name": "Wallet",
  "iconId": "<uuid>",
  "initialBalance": "0.00",
  "category": "OTHER",
  "details": "<string | null>",
  "creationDateTime": "...",
  "updatedDateTime": "...",
  "deletedAt": "<ISO datetime | null>"
}
```

### Category item

```json
{
  "id": "<uuid>",
  "name": "Groceries",
  "iconId": "<uuid | null>",
  "colorId": "<uuid | null>",
  "parentCategoryId": "<uuid | null>",
  "creationDateTime": "...",
  "updatedDateTime": "...",
  "deletedAt": "<ISO datetime | null>"
}
```

---

## Conflict Resolution

### Rule

For any two versions of the same entity (matched by `id`), the one with the **greater `updatedDateTime`** wins. This is Last-Write-Wins (LWW) by client clock.

Tombstones participate in LWW: a `deletedAt` timestamp is treated as the entity's `updatedDateTime` for deletion events. If a remote entity was deleted at T=12:00 and the local version was last updated at T=11:00, the tombstone wins.

### `ConflictResolver<T>`

```kotlin
fun interface ConflictResolver<T : SyncEntity> {
    /**
     * Called when the same entity ID exists in both local and incoming sets,
     * or when either side is null (entity only exists in one place).
     *
     * Returns:
     *   empty  — both discarded (e.g., both are tombstones past retention period)
     *   [one]  — one version wins
     *   [a, b] — both survive as distinct entities (content-based dedup case)
     */
    fun resolve(local: T?, incoming: T?): List<T>
}
```

**Default implementation:** `LastWriteWinsResolver` — compares `updatedDateTime`, returns the entity with the greater timestamp. If `local == null`, returns `incoming`. If `incoming == null`, returns `local`.

The resolver is registered per entity type. This allows future customization: e.g., a `ContentDeduplicatingCategoryResolver` that merges two independently-created categories with the same name.

---

## Module Structure

### New module: `zero-sync`

```
zero-sync/
  src/main/java/com/hluhovskyi/zero/sync/
    DefaultSyncEngine.kt     — internal implementation of SyncEngine
    SyncSnapshot.kt          — top-level JSON envelope (version, userId, exportedAt, entity lists)
    SyncSerializer.kt        — JSON serialization / deserialization
    LastWriteWinsResolver.kt — default ConflictResolver implementation
    SyncPipeline.kt          — bundles Source + Sink + Resolver for one entity type
    SyncComponent.kt         — wires DefaultSyncEngine from a Dependencies interface
  src/test/resources/fixtures/sync/
    v1-transaction-expense.json
    v1-transaction-income.json
    v1-transaction-transfer.json
    v1-transaction-tombstone.json
    v1-account.json
    v1-account-tombstone.json
    v1-category.json
    v1-category-tombstone.json
    v1-full-snapshot.json
  AGENTS.md
  build.gradle
```

### Additions to `zero-api`

`zero-api` gains a `kotlinx.serialization` dependency (pure Kotlin multiplatform, no Android deps). All sync types live in `com.hluhovskyi.zero.sync`:

```kotlin
// Marker interface for all sync entity types
interface SyncEntity {
    val id: Id.Known
    val updatedDateTime: LocalDateTime
    val deletedAt: LocalDateTime?
}

// Sync data classes — @Serializable so zero-database can map to them
// and zero-sync can serialize them, with no circular dependency
@Serializable data class SyncCategory(...)
@Serializable data class SyncAccount(...)
@Serializable data class SyncTransaction(...)

// Source / sink interfaces implemented by zero-database (and future zero-firebase)
interface EntitySyncSource<T : SyncEntity> {
    suspend fun exportAll(userId: Id.Known): List<T>
    suspend fun exportSince(userId: Id.Known, since: LocalDateTime): List<T>
}

interface EntitySyncSink<T : SyncEntity> {
    suspend fun syncUpsert(entities: List<T>)  // raw upsert, preserves all timestamps
}

// Strategy interface — also lives here so resolvers in zero-sync can be typed
fun interface ConflictResolver<T : SyncEntity> {
    fun resolve(local: T?, incoming: T?): List<T>
}

// Engine interface — implemented internally by DefaultSyncEngine in zero-sync
// Exposed here so zero-core can depend on it for SyncViewModel/SyncUseCase
interface SyncEngine {
    suspend fun export(userId: Id.Known): SyncSnapshot
    suspend fun import(snapshot: SyncSnapshot, userId: Id.Known)
}
```

`zero-database` implements `EntitySyncSource<T>` and `EntitySyncSink<T>` via dedicated `SyncDao`s — raw `@Insert(onConflict = REPLACE)`, no timestamp overwriting, no ID generation. The existing repositories are untouched.

### Dependency graph

```
app → zero-core     → zero-api
app → zero-database → zero-api   (implements EntitySyncSource/Sink for each entity type)
app → zero-sync     → zero-api   (uses all sync interfaces and data classes)
app → zero-firebase (future) → zero-api   (implements EntitySyncSource/Sink against Firestore)
```

No circular dependencies. `zero-core` can see the sync interfaces in `zero-api` but has no reason to use them — misuse would be caught in code review.

---

## Sync Engine

`SyncEngine` is an interface in `zero-api`. The implementation `DefaultSyncEngine` is `internal` to `zero-sync`, enforced by the existing `DefaultImplMustBeInternal` lint rule.

```kotlin
// zero-api — visible to zero-core for SyncViewModel/SyncUseCase
interface SyncEngine {
    suspend fun export(userId: Id.Known): SyncSnapshot
    suspend fun import(snapshot: SyncSnapshot, userId: Id.Known)
}

// zero-sync — internal, constructed by SyncComponent
internal class DefaultSyncEngine(
    private val pipelines: List<SyncPipeline<*>>,
) : SyncEngine { ... }
```

### `SyncPipeline<T>`

Bundles the local source, local sink, and conflict resolver for one entity type:

```kotlin
data class SyncPipeline<T : SyncEntity>(
    val localSource: EntitySyncSource<T>,
    val localSink: EntitySyncSink<T>,
    val resolver: ConflictResolver<T>,
)
```

### Processing order

Entities are processed in dependency order to avoid foreign-key-style dangling references:

1. Categories
2. Accounts
3. Transactions

### `SyncComponent` — DI wiring

`SyncComponent` lives in `zero-sync` and follows the manual DI pattern. It receives sync sources and sinks through a `Dependencies` interface, which `DatabaseComponent` satisfies. Nothing is constructed inline in `app`.

```kotlin
class SyncComponent(private val dependencies: Dependencies) {

    interface Dependencies {
        val categorySyncSource: EntitySyncSource<SyncCategory>
        val categorySyncSink: EntitySyncSink<SyncCategory>
        val accountSyncSource: EntitySyncSource<SyncAccount>
        val accountSyncSink: EntitySyncSink<SyncAccount>
        val transactionSyncSource: EntitySyncSource<SyncTransaction>
        val transactionSyncSink: EntitySyncSink<SyncTransaction>
    }

    val syncEngine: SyncEngine by lazy {
        DefaultSyncEngine(
            pipelines = listOf(
                SyncPipeline(dependencies.categorySyncSource, dependencies.categorySyncSink, LastWriteWinsResolver()),
                SyncPipeline(dependencies.accountSyncSource, dependencies.accountSyncSink, LastWriteWinsResolver()),
                SyncPipeline(dependencies.transactionSyncSource, dependencies.transactionSyncSink, LastWriteWinsResolver()),
            )
        )
    }
}
```

`DatabaseComponent` constructs `RoomXxxSyncSource`/`Sink` internally and exposes them **only as interface types** (`EntitySyncSource<T>`, `EntitySyncSink<T>`). No `Room*` implementation class name ever crosses a component boundary. `ApplicationComponent` is the mediator: it bridges `DatabaseComponent`'s interface-typed properties into `SyncComponent.Dependencies`, then constructs `DefaultSyncComponent`. `SyncComponent` sees only interfaces throughout.

---

## Delta Sync Protocol (Backend)

### High-water mark

The client stores `lastSyncedAt: LocalDateTime` in a dedicated `SyncStateEntity` table (not piggybacked on `ConfigurationEntity` — isolated to reduce corruption risk). On each sync cycle:

1. **Pull:** fetch from remote all entities where `updatedDateTime > lastSyncedAt`.
2. **Merge:** apply `ConflictResolver` for each entity against local state.
3. **Push:** send all local entities where `updatedDateTime > lastSyncedAt` to remote, in dependency order (categories → accounts → transactions). Each entity type is fully confirmed before the next begins.
4. **Advance:** only after all pushes are confirmed, set `lastSyncedAt = now()`.

`lastSyncedAt` must **never advance until the push is fully confirmed.** If the push fails at any point, `lastSyncedAt` stays at the previous value and the next sync retries from there. All operations are idempotent — pushing the same entity twice is safe.

### Chunked push

Push is chunked (e.g., 200 entities per request) to avoid large fragile payloads. `lastSyncedAt` only advances after **all** chunks across **all** entity types are confirmed. A failure in any chunk causes the entire cycle to be retried from `lastSyncedAt`.

### First sync

If `lastSyncedAt == null` (first ever sync), pull everything from remote. This is identical to a full import from a remote snapshot.

### Idempotency

All operations are safe to retry. LWW ensures merging the same entity twice produces the same result. Pushing the same entity twice is a no-op on the backend (upsert by ID).

---

## Backward Compatibility

- The `version` field in the snapshot allows future format migrations. An importer that sees `version = 2` but only understands `version = 1` must surface an error rather than silently dropping data.
- New optional fields added to entity items are ignored by older importers (JSON forward-compatibility). Existing required fields must not be removed or renamed across versions.
- When the format changes in a breaking way, increment `version` and provide a migration path.

### `@SerialName` on every field (lint-enforced)

Every field on every `SyncSnapshot` data class must carry an explicit `@SerialName("fieldName")` annotation. This decouples JSON keys from Kotlin property names — renaming a Kotlin property (e.g., `targetAccountId → transferAccountId`) does not silently change the serialized key and break existing backups.

This is enforced by a custom lint rule: **`SyncEntityFieldMustHaveSerialName`** (in `:lint-rules`). Violations are errors (build fails). The rule targets all classes annotated with `@Serializable` inside the `zero-sync` module.

**Developer workflow when changing a sync entity:**
1. Change the Kotlin property name freely — `@SerialName` keeps the JSON key stable.
2. If the JSON key itself must change (breaking), increment `version` and add a migration.
3. Update the committed fixture files (see Testing section below).

---

## Testing Approach

### Unit tests

- `SyncEngine`: in-memory `EntitySyncSource`/`Sink` fakes, no Room required.
- `LastWriteWinsResolver`: pure, no dependencies.
- `SyncSerializer`: serialize → deserialize round-trip per entity type, including edge cases (null fields, max `BigDecimal` precision, tombstones).
- Conflict tests: two snapshots with overlapping entity IDs at different `updatedDateTime` values, verify correct LWW winner.

### Backward compatibility tests (`SyncBackwardCompatibilityTest`)

Committed JSON fixture files in `zero-sync/src/test/resources/fixtures/sync/`:

```
v1-transaction-expense.json
v1-transaction-income.json
v1-transaction-transfer.json
v1-transaction-tombstone.json
v1-account.json
v1-account-tombstone.json
v1-category.json
v1-category-tombstone.json
v1-full-snapshot.json
```

`SyncBackwardCompatibilityTest` reads every fixture file and asserts:
1. Deserialization succeeds without error.
2. All fields are populated with expected values (no silent nulling of unknown fields).

When adding a new format version, add a new fixture set (`v2-*.json`) — never modify existing ones. This guarantees historical backups remain readable indefinitely.

### Round-trip tests

Serialize a known entity instance to JSON, deserialize back, assert structural equality. Catches precision loss (`BigDecimal`), timezone shifts, or enum serialization issues.

### Integration tests

In-memory Room DB: full export → wipe local state → import → verify DB state is identical to pre-export state. Covers the complete pipeline end-to-end.
