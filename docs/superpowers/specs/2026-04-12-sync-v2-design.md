# Sync V2 — Design Spec

## Goal

Extend the sync engine to support Firestore as a remote backend with delta sync, while keeping the engine fully agnostic about what's behind any source or sink. The file export/import path (V1) stays intact.

## What Does Not Change

- `EntitySyncSource<T>`, `EntitySyncSink<T>`, `ConflictResolver<T>` — interfaces survive as-is (source gains one method, sink unchanged)
- `SyncEntity`, `SyncCategory`, `SyncAccount`, `SyncTransaction`, `SyncSnapshot` — unchanged
- `LastWriteWinsResolver` — unchanged; becomes an internal engine detail mapped from `ResolveStrategy`
- Lint rule (`SyncEntityFieldMustHaveSerialName`), JSON fixtures, `SyncBackwardCompatibilityTest` — unchanged
- File export/import (`export(): SyncSnapshot`, `import(snapshot)`) — stays on `SyncEngine`

---

## Architecture

### Attribution

A marker interface that labels a registry. The engine never inspects its contents — it's a lookup key only. Carries a stable string key for persistence (e.g. `lastSyncedAt` storage).

```kotlin
// zero-api
interface SyncAttribution {
    val key: String
}
```

```kotlin
// app — sealed, all variants in one place, lower modules never see these
sealed class KnownSyncAttribution(override val key: String) : SyncAttribution {
    object Database : KnownSyncAttribution("database")
    object Remote : KnownSyncAttribution("remote")
    object File : KnownSyncAttribution("file")
}
```

### SyncRegistry

One registry per attribution. Holds all sources and sinks for that attribution. All fields default to `Noop` — partial registries are intentional (e.g. Remote with only categories wired initially).

```kotlin
// zero-sync
class SyncRegistry(
    val attribution: SyncAttribution,
    val categorySource: EntitySyncSource<SyncCategory> = EntitySyncSource.Noop,
    val categorySink: EntitySyncSink<SyncCategory> = EntitySyncSink.Noop,
    val accountSource: EntitySyncSource<SyncAccount> = EntitySyncSource.Noop,
    val accountSink: EntitySyncSink<SyncAccount> = EntitySyncSink.Noop,
    val transactionSource: EntitySyncSource<SyncTransaction> = EntitySyncSource.Noop,
    val transactionSink: EntitySyncSink<SyncTransaction> = EntitySyncSink.Noop,
)
```

**Adding a new entity type** — add fields to `SyncRegistry`. Every existing registry instantiation breaks at compile time until wired. Cannot be forgotten.

**Noop source** — safe: `exportAll` returns empty list, nothing synced from that direction.

**Noop sink** — careful: resolved winners are silently discarded. Only acceptable when that direction intentionally does nothing (e.g. a read-only File registry for export-only). Never leave a sink Noop by accident on the destination side.

### ResolveStrategy

Public API concept — callers configure strategy by name, engine maps to internal `ConflictResolver` implementation. Callers never instantiate resolvers directly. One strategy applies to the whole syncer — if different entity types need different strategies, use separate syncers.

```kotlin
// zero-sync
sealed class ResolveStrategy {
    object LastWriteWins : ResolveStrategy()
    // KeepBoth — future, only valid for leaf entities (transactions)
}
```

### Syncer

A configured, ready-to-run sync between two attributions. Owns `lastSyncedAt` state and advances it only on full success.

```kotlin
// zero-sync
interface Syncer {
    suspend fun sync()
}
```

No `userId` parameter — `Syncer` reads the current user internally via `CurrentUserRepository`.

### SyncEngine

```kotlin
// zero-api
interface SyncEngine {
    // File backup — unchanged from V1
    suspend fun export(): SyncSnapshot
    suspend fun import(snapshot: SyncSnapshot)

    // V2 — creates a configured syncer between two registered attributions
    fun createSyncer(
        from: SyncAttribution,
        to: SyncAttribution,
        strategy: ResolveStrategy = ResolveStrategy.LastWriteWins,
    ): Syncer
}
```

`SyncComponent.Dependencies`:

```kotlin
interface SyncComponent {
    interface Dependencies {
        val registries: List<SyncRegistry>
        val currentUserRepository: CurrentUserRepository
    }
}
```

---

## How Resolution Works (Engine-Internal)

The engine reads existing state from the **destination registry's source**, resolves, then writes winners to the destination sink (write-through — sink has no resolver knowledge):

```kotlin
// createSyncer(from = Remote, to = Database) internally per entity type:
val incoming = remoteRegistry.categorySource.exportSince(userId, lastSyncedAt)
val existing = databaseRegistry.categorySource.exportAll(userId).associateBy { it.id }
val toWrite = incoming.flatMap { entity ->
    val storedEntity = existing[entity.id]
    resolver.resolve(storedEntity, entity).filter { it != storedEntity }
}
databaseRegistry.categorySink.syncUpsert(toWrite)  // plain write-through, unchanged signature
```

`EntitySyncSink.syncUpsert(entities: List<T>)` — **signature unchanged from V1**. No resolver parameter. Sink is dumb.

This also removes pipeline ordering dependency — `Remote→DB` and `DB→Remote` can run independently because each reads the destination source for current state before resolving.

---

## Delta Sync

`EntitySyncSource` gains `exportSince` with a default fallback:

```kotlin
interface EntitySyncSource<T : SyncEntity> {
    suspend fun exportAll(userId: Id.Known): List<T>
    suspend fun exportSince(userId: Id.Known, since: LocalDateTime): List<T> = exportAll(userId)
}
```

Existing Room sources work without change. Firestore sources override `exportSince` with a native delta query.

`Syncer` tracks `lastSyncedAt` per `(from.key, to.key, userId)` in a new `SyncStateEntity` Room table. On each `sync()`:

1. Read `lastSyncedAt` for this `(from, to, userId)` triple
2. Call `fromRegistry.source.exportSince(userId, lastSyncedAt)` — or `exportAll` if no prior sync
3. Resolve and write winners to destination sink
4. Advance `lastSyncedAt` only if step 3 fully succeeded — never advance on partial failure

---

## Firestore Remote Implementation

### Module

New module `:zero-firebase` (Android module, depends on `:zero-api`). Contains Firestore `EntitySyncSource` and `EntitySyncSink` implementations. Never imported by `zero-sync` — only wired at `app` level.

### Data Model

```
Firestore:
  users/{userId}/categories/{categoryId}   → SyncCategory fields
  users/{userId}/accounts/{accountId}      → SyncAccount fields
  users/{userId}/transactions/{txId}       → SyncTransaction fields
```

### Delta Query

```kotlin
class FirestoreCategorySource(
    private val firestore: FirebaseFirestore,
) : EntitySyncSource<SyncCategory> {

    override suspend fun exportAll(userId: Id.Known): List<SyncCategory> =
        firestore.collection("users/${userId.value}/categories")
            .get().await().toSyncCategories()

    override suspend fun exportSince(userId: Id.Known, since: LocalDateTime): List<SyncCategory> =
        firestore.collection("users/${userId.value}/categories")
            .whereGreaterThan("updatedDateTime", since.toString())
            .get().await().toSyncCategories()
}
```

### Conflict Resolution

Firestore's own last-write-wins (server timestamp) is **ignored**. Client-side resolution runs in the engine before anything is written to Firestore. The Firestore sink is write-through — it trusts the incoming entity is already the winner.

### Tombstones

Never hard-delete from Firestore. Set `deletedAt` field on the document. Hard-deletes would make tombstones invisible to other devices and permanently corrupt sync state on any device that hasn't seen the deletion.

### Race Condition

Two devices may write different winners simultaneously if clocks are skewed. Both use identical LWW logic so they converge to the same winner in the next sync cycle. Acceptable — solving this requires a server-side arbiter (out of scope).

### Batch Writes

Firestore sink uses `WriteBatch` for atomicity per entity type. If a batch partially fails, `lastSyncedAt` must not advance for that direction.

---

## App-Level Wiring

All attribution wiring happens in `ApplicationComponent`. Lower-level components receive only `Syncer` — no knowledge of attribution, pipelines, or entity types.

```kotlin
// ApplicationComponent.Module
@Provides
fun syncEngine(...): SyncEngine = SyncComponent.factory(
    object : SyncComponent.Dependencies {
        override val registries = listOf(
            SyncRegistry(
                attribution = KnownSyncAttribution.Database,
                categorySource = dbSources.categories,
                categorySink = dbSinks.categories,
                accountSource = dbSources.accounts,
                accountSink = dbSinks.accounts,
                transactionSource = dbSources.transactions,
                transactionSink = dbSinks.transactions,
            ),
            SyncRegistry(
                attribution = KnownSyncAttribution.Remote,
                categorySource = firestoreSources.categories,
                categorySink = firestoreSinks.categories,
                accountSource = firestoreSources.accounts,
                accountSink = firestoreSinks.accounts,
                transactionSource = firestoreSources.transactions,
                transactionSink = firestoreSinks.transactions,
            ),
        )
        override val currentUserRepository = currentUserRepository
    }
).create()

@Provides
fun remoteSyncer(syncEngine: SyncEngine): Syncer =
    syncEngine.createSyncer(
        from = KnownSyncAttribution.Remote,
        to = KnownSyncAttribution.Database,
    )
```

`Syncer` is injectable anywhere — `WorkManager` task, ViewModel, push notification handler.

---

## Migration from V1

1. Add `key: String` to `SyncAttribution` interface — update `KnownSyncAttribution`
2. Add `SyncStateEntity` Room table + migration for `lastSyncedAt` persistence
3. Add `SyncRegistry`, `ResolveStrategy`, `SyncStrategies`, `Syncer` to `zero-sync`
4. `SyncEngine` interface gains `createSyncer` — `export()`/`import()` stay unchanged
5. `DefaultSyncEngine` reimplemented to use registry lookup — `SyncPipeline` stays internal
6. `SyncComponent.Dependencies` shape changes — update `ApplicationComponent` wiring
7. Add `:zero-firebase` module with Firestore implementations
8. `EntitySyncSink.syncUpsert` signature — **unchanged**, no migration needed

File export/import continues to work unchanged throughout — V1 behaviour unaffected.

---

## Plan B — Own Backend

The sync layer is backend-agnostic by design. Switching from Firestore to a custom backend means replacing `:zero-firebase` implementations with `:zero-backend` implementations. `SyncEngine`, `SyncRegistry`, `Syncer`, and all Room code are untouched.

**Cost inflection point:** Firestore is cheaper than self-hosted until roughly 50k–100k MAU. A VPS + Postgres becomes cheaper at that scale. The interface swap is the only migration work.

**To keep migration cheap, avoid these Firestore-specific patterns:**
- Do not use Firestore real-time listeners as a core sync mechanism — treat sync as pull-based, listeners as an optional optimization on top of `Syncer.sync()`
- Do not rely on Firestore's offline cache — all offline state lives in Room; Firestore is a dumb remote store
- Delta query (`whereGreaterThan("updatedDateTime", since)`) maps directly to `GET /entities?since=...` on any backend — no lock-in

**Auth:** Firebase Auth UIDs must never become the primary user identity in the data model. Store Firebase Auth UID as a lookup field only; use app-internal `Id.Known` as the canonical `userId` everywhere. This decouples auth provider from data identity — switching auth providers becomes a lookup swap, not a data migration.

## Open Questions

- **Firestore auth**: Firebase Auth UID vs app-internal `Id.Known` — mapping layer lives in `:zero-firebase`, never leaks into `zero-sync` or `zero-api`.
- **Real-time listeners**: Firestore `addSnapshotListener` could trigger `sync()` automatically on remote changes. Out of scope for V2 but compatible with the `Syncer` interface as-is.
