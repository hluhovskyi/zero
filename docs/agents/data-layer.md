# Data Layer

## Repository Pattern

All repositories live in `zero-api` (interface) and `zero-database` (Room implementation).

### Interface Convention

```kotlin
interface FeatureRepository {

    fun <T> query(criteria: Criteria<T>, trigger: Flow<*> = emptyFlow<Any>()): Flow<T>

    sealed interface Criteria<T> {
        class All : Criteria<List<Item>>
        data class ById(val id: Id.Known) : Criteria<Item>
    }

    suspend fun insert(item: Item)
    suspend fun insert(items: List<Item>)

    // Noop for testing and default builder values
    object Noop : FeatureRepository {
        override fun <T> query(criteria: Criteria<T>, trigger: Flow<*>): Flow<T> = emptyFlow()
        override suspend fun insert(item: Item) = Unit
        override suspend fun insert(items: List<Item>) = Unit
    }
}
```

**Rules:**
- `query()` always returns `Flow<T>` — reactive by default
- `Criteria` is a sealed interface with a type parameter `<T>` to make the return type type-safe
- `insert()` uses `OnConflictStrategy.REPLACE` — doubles as update
- Every repository has a `Noop` object for safe defaults in builders

### Existing Repositories

| Repository | Module | Key Criteria |
|-----------|--------|-------------|
| `TransactionRepository` | `zero-api` | `All`, `ById`, `After(dateTime)` |
| `AccountRepository` | `zero-api` | `All` |
| `CategoryRepository` | `zero-api` | `All`, `ById` |
| `CurrencyRepository` | `zero-api` | `All` |
| `ColorRepository` | `zero-api` | `All`, `ById` + synchronous `schemeFor(id)` |
| `IconRepository` | `zero-api` | `All` |

### Optional `trigger` Parameter

`query()` accepts an optional `trigger: Flow<*>` for pagination:

```kotlin
// Paginated: emits next page each time trigger fires
transactionRepository.query(
    TransactionRepository.Criteria.All(),
    trigger = loadMoreTrigger  // MutableSharedFlow<Unit>
)
```

Without a trigger, `Criteria.All` loads the first page only. The trigger flow drives subsequent page loads.

## Room Implementation

### Entity Pattern

```kotlin
@Entity(indices = [Index("userId")])
data class TransactionEntity(
    @PrimaryKey val id: Id.Known,
    val userId: Id.Known,
    val type: Type,
    val currencyId: Id.Known,
    val accountId: Id.Known,
    @Embedded(prefix = "amount_") val amount: AmountEntity,
    val enteredDateTime: LocalDateTime,
    val creationDateTime: LocalDateTime,
    val updatedDateTime: LocalDateTime,
) {
    enum class Type { EXPENSE, INCOME, TRANSFER }
}
```

**Conventions:**
- `@PrimaryKey` is always `Id.Known`
- `userId` for multi-user scoping
- `@Embedded` with prefix for composite types (`AmountEntity`, `RateEntity`)
- Timestamps: `enteredDateTime` (user-chosen), `creationDateTime` (first insert), `updatedDateTime` (last write)

### DAO Pattern

```kotlin
@Dao
interface FeatureRoom {
    // Reactive query (Room re-emits on table changes)
    @Query("SELECT * FROM Entity WHERE userId=:userId")
    fun selectAll(userId: String): Flow<List<Entity>>

    // One-shot query
    @Query("SELECT * FROM Entity WHERE id=:id AND userId=:userId LIMIT 1")
    suspend fun selectById(id: String, userId: String): Entity?

    // Insert/update (REPLACE strategy)
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: Entity)
}
```

**Room Flow reactivity:** Queries returning `Flow<T>` automatically re-emit when the underlying table changes. Queries returning `suspend fun` are one-shot.

### Repository Implementation

```kotlin
internal class RoomFeatureRepository(
    private val dao: () -> FeatureRoom,  // lazy to avoid DB init on main thread
    private val currentUserId: Flow<Id.Known>,
    private val clock: Clock
) : FeatureRepository {

    override fun <T> query(criteria: Criteria<T>, trigger: Flow<*>): Flow<T> =
        currentUserId.take(1).flatMapConcat { userId ->
            when (criteria) {
                is Criteria.All -> dao().selectAll(userId.value)
                    .map { entities -> entities.map { it.toDomain() } }
                is Criteria.ById -> flow {
                    dao().selectById(criteria.id.value, userId.value)
                        ?.toDomain()
                        ?.let { emit(it) }
                }
            }
        }.uncheckedCast()

    override suspend fun insert(item: Item) {
        requireCurrentUserId(currentUserId) { userId ->
            dao().insert(item.toEntity(userId))
        }
    }
}
```

**Pattern notes:**
- `currentUserId.take(1).flatMapConcat { ... }` — resolve user ID once per query
- `uncheckedCast()` — safe cast from concrete `Flow<List<X>>` to generic `Flow<T>`
- `requireCurrentUserId` — asserts user is logged in before writes
- DAO is lazy (`() -> FeatureRoom`) to avoid database initialization on the main thread
