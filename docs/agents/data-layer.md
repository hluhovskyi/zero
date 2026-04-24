# Data Layer

## Non-obvious conventions

- **`Criteria<T>` is typed** — the type parameter on `Criteria` makes `query()` return the right type. `Criteria.All : Criteria<List<T>>` vs `Criteria.ById : Criteria<T>`. The `uncheckedCast()` at the end of repository implementations is safe because of this.
- **`trigger` parameter on `query()`** — optional `Flow<*>` that drives pagination. Without it, `Criteria.All` loads only the first page. Each emission loads the next page. Used with `MutableSharedFlow<Unit>` from the ViewModel.
- **Lazy DAO access** — repositories take `() -> FeatureRoom` (lambda), not `FeatureRoom` directly. Room database initialization is expensive and must not happen on the main thread.
- **User-scoped queries** — all queries filter by `userId`. Pattern: `currentUserId.take(1).flatMapConcat { userId -> ... }`.
- **Insert = upsert** — `@Insert(onConflict = OnConflictStrategy.REPLACE)` everywhere. No separate update methods.
- **Timestamps** — entities have `enteredDateTime` (user-chosen date), `creationDateTime` (first insert), `updatedDateTime` (every write). The reactive `selectAfter` query uses `updatedDateTime` to catch both new and edited items.
- **Room Flow reactivity** — queries returning `Flow<T>` re-emit automatically on table changes. Queries returning `suspend fun` are one-shot. Choose based on whether the caller needs live updates.
- **Scope data concerns to the lowest layer that can fully resolve them** — if filtering, state-tracking, or mutation propagation can be handled entirely inside the repository, do not expose the raw state in API types or let it leak into ViewModels. Callers must not adapt to DB mechanics. Example: soft-delete filters live in SQL and `paginatedFlow`; `TransactionRepository.Transaction` carries no `deletedAt` field. When one-shot paged flows need to react to in-session mutations (which Room won't re-fire for), keep a `MutableSharedFlow` inside the repository implementation and `merge()` it with the load-more trigger — never surface the signal upward.
