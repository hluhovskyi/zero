# zero-database — Agent Guide

Android library module. Room database implementation — DAOs, Entities, and Repository implementations.

## Rules

1. **Depends only on `zero-api`** — implements interfaces defined there. Cannot import from `zero-core` or `app`.
2. **`@Insert(onConflict = OnConflictStrategy.REPLACE)`** — all inserts use REPLACE strategy, doubling as upserts. No separate update methods.
3. **Reactive queries return `Flow<T>`** — Room auto-re-emits when table changes. Use for list screens.
4. **One-shot queries use `suspend fun`** — for by-ID lookups and pagination cursors.
5. **Entities use `@Embedded` with prefix** for composite types — `@Embedded(prefix = "amount_") val amount: AmountEntity`.
6. **User-scoped data** — all queries filter by `userId`. Use `currentUserId.take(1).flatMapConcat { ... }` pattern.
7. **Lazy DAO access** — repositories receive `() -> FeatureRoom` (lambda), not `FeatureRoom` directly, to avoid DB init on main thread.
8. **Hard Encapsulation**: Interface `DatabaseComponent` MUST NOT return Room DAOs (`@Dao`) or Entities (`@Entity`). Custom logic (decorators/transformers) must be provided by the component directly using `zero-api` types.

## What Lives Here

- **`DatabaseComponent`**: Dagger component providing all repository implementations
- **Per-entity packages**: `transactions/`, `accounts/`, `categories/`, `currencies/`, `colors/`, `icons/`
- **Each package**: `EntityRoom` (DAO), `Entity` (Room entity), `RoomEntityRepository` (implementation)

## Entity Conventions

```kotlin
@Entity(indices = [Index("userId")])
data class FeatureEntity(
    @PrimaryKey val id: Id.Known,
    val userId: Id.Known,
    // ... fields
    @Embedded(prefix = "amount_") val amount: AmountEntity,
    val enteredDateTime: LocalDateTime,
    val creationDateTime: LocalDateTime,
    val updatedDateTime: LocalDateTime,
)
```

- `@PrimaryKey` is always `Id.Known`
- `userId` for multi-user scoping, indexed
- Timestamps: `enteredDateTime` (user-chosen), `creationDateTime` (first write), `updatedDateTime` (every write)
- Use `AmountEntity` / `RateEntity` for `@Embedded` — these are the Room-compatible wrappers

## Adding a New Entity

1. Create `FeatureEntity` data class with `@Entity`
2. Create `FeatureRoom` DAO interface with `@Dao`
3. Add DAO getter to `ZeroDatabase` abstract class
4. Create `RoomFeatureRepository` implementing the interface from `zero-api`
5. Wire in `DatabaseComponent.Module` and expose via `DatabaseComponent.Dependencies`
6. If schema changes: Room auto-migration handles additive changes; destructive changes need a `Migration`
