# Implement In-Use Currency Repository with Transformer Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement `InUseCurrencyRepository` and expose a `transform` method on `DatabaseComponent` to wrap any `CurrencyRepository` with in-use logic.

**Architecture:** 
1. `CurrencyRepository.Transformer` functional interface added to `zero-api`.
2. `InUseCurrencyRepository` implemented as a decorator in `zero-database`.
3. `DatabaseComponent` exposes `CurrencyRepository.Transformer`.
4. `ApplicationComponent` uses the transformer to wrap the base `JavaCurrencyRepository`.

**Tech Stack:** Kotlin, Room, Coroutines/Flow, Dagger, kotlinx-datetime.

---

### Task 1: Update API and Room DAOs

**Files:**
- Modify: `zero-api/src/main/java/com/hluhovskyi/zero/currencies/CurrencyRepository.kt`
- Modify: `zero-database/src/main/java/com/hluhovskyi/zero/accounts/AccountRoom.kt`
- Modify: `zero-database/src/main/java/com/hluhovskyi/zero/transactions/TransactionRoom.kt`

- [ ] **Step 1: Add `Transformer` to `CurrencyRepository`**

```kotlin
// zero-api/src/main/java/com/hluhovskyi/zero/currencies/CurrencyRepository.kt

interface CurrencyRepository {
    // ... existing ...
    fun interface Transformer {
        fun transform(repository: CurrencyRepository): CurrencyRepository
    }
}
```

- [ ] **Step 2: Add `selectInUseCurrencyIds` to `AccountRoom`**

```kotlin
// zero-database/src/main/java/com/hluhovskyi/zero/accounts/AccountRoom.kt

@Query("SELECT DISTINCT currencyId FROM AccountEntity WHERE userId=:userId")
fun selectInUseCurrencyIds(userId: String): Flow<List<Id.Known>>
```

- [ ] **Step 3: Add `selectInUseCurrencyIds` to `TransactionRoom`**

```kotlin
// zero-database/src/main/java/com/hluhovskyi/zero/transactions/TransactionRoom.kt

@Query("""
    SELECT DISTINCT currencyId FROM TransactionEntity 
    WHERE userId = :userId 
      AND datetime(enteredDateTime) >= datetime(:since)
""")
fun selectInUseCurrencyIds(userId: String, since: String): Flow<List<Id.Known>>
```

---

### Task 2: Implement `InUseCurrencyRepository`

**Files:**
- Modify: `zero-database/src/main/java/com/hluhovskyi/zero/currencies/InUseCurrencyRepository.kt`

- [ ] **Step 1: Implement `InUseCurrencyRepository` logic**

```kotlin
// zero-database/src/main/java/com/hluhovskyi/zero/currencies/InUseCurrencyRepository.kt

package com.hluhovskyi.zero.currencies

import com.hluhovskyi.zero.accounts.AccountRoom
import com.hluhovskyi.zero.common.Currency
import com.hluhovskyi.zero.common.Id
import com.hluhovskyi.zero.common.coroutines.uncheckedCast
import com.hluhovskyi.zero.common.time.Clock
import com.hluhovskyi.zero.common.time.ZoneProvider
import com.hluhovskyi.zero.common.time.localDateTime
import com.hluhovskyi.zero.transactions.TransactionRoom
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.minus
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant

internal class InUseCurrencyRepository(
    private val accountRoom: () -> AccountRoom,
    private val transactionRoom: () -> TransactionRoom,
    private val baseRepository: CurrencyRepository,
    private val currentUserId: Flow<Id.Known>,
    private val clock: Clock,
    private val zoneProvider: ZoneProvider,
) : CurrencyRepository {

    override fun <T> query(criteria: CurrencyRepository.Criteria<T>): Flow<T> {
        return when (criteria) {
            is CurrencyRepository.Criteria.InUse -> queryInUse()
            else -> baseRepository.query(criteria)
        }.uncheckedCast()
    }

    private fun queryInUse(): Flow<List<Currency>> {
        return currentUserId.flatMapLatest { userId ->
            val timeZone = zoneProvider.timeZone()
            val thirtyDaysAgo = clock.localDateTime(timeZone)
                .toInstant(timeZone)
                .minus(30, DateTimeUnit.DAY, timeZone)
                .toLocalDateTime(timeZone)
                .toString()

            combine(
                accountRoom().selectInUseCurrencyIds(userId.value),
                transactionRoom().selectInUseCurrencyIds(userId.value, thirtyDaysAgo)
            ) { accountCurrencies, transactionCurrencies ->
                (accountCurrencies + transactionCurrencies).distinct()
            }
        }.flatMapLatest { ids ->
            baseRepository.query(CurrencyRepository.Criteria.All())
                .map { allCurrencies ->
                    allCurrencies.filter { it.id in ids }
                }
        }
    }
}
```

---

### Task 3: Update `DatabaseComponent`

**Files:**
- Modify: `zero-database/src/main/java/com/hluhovskyi/zero/DatabaseComponent.kt`

- [ ] **Step 1: Expose `transform` in `DatabaseComponent`**

```kotlin
// zero-database/src/main/java/com/hluhovskyi/zero/DatabaseComponent.kt

interface DatabaseComponent {
    // ...
    val currencyRepositoryTransformer: CurrencyRepository.Transformer

    fun transform(repository: CurrencyRepository): CurrencyRepository = 
        currencyRepositoryTransformer.transform(repository)
}
```

- [ ] **Step 2: Provide `CurrencyRepository.Transformer` and implement `transform` in `DatabaseComponent.Module`**

```kotlin
// zero-database/src/main/java/com/hluhovskyi/zero/DatabaseComponent.kt

        @Provides
        @DatabaseScope
        internal fun currencyRepositoryTransformer(
            database: Provider<MainDatabase>,
            @CurrentUserId currentUserId: Flow<Id.Known>,
            clock: Clock,
            zoneProvider: ZoneProvider,
        ): com.hluhovskyi.zero.currencies.CurrencyRepository.Transformer = 
            com.hluhovskyi.zero.currencies.CurrencyRepository.Transformer { baseRepository ->
                com.hluhovskyi.zero.currencies.InUseCurrencyRepository(
                    accountRoom = { database.get().account() },
                    transactionRoom = { database.get().transaction() },
                    baseRepository = baseRepository,
                    currentUserId = currentUserId,
                    clock = clock,
                    zoneProvider = zoneProvider,
                )
            }
```

---

### Task 4: Configure `ApplicationComponent`

**Files:**
- Modify: `app/src/main/java/com/hluhovskyi/zero/ApplicationComponent.kt`

- [ ] **Step 1: Update `currencyRepository` provider to use `databaseComponent.transform`**

```kotlin
// app/src/main/java/com/hluhovskyi/zero/ApplicationComponent.kt

        @Provides
        @ApplicationScope
        internal fun currencyRepository(
            localeProvider: LocaleProvider,
            currencyLoader: CurrencyLoader,
            databaseComponent: DatabaseComponent,
        ): CurrencyRepository {
            val baseRepository = JavaCurrencyRepository(
                localeProvider = localeProvider,
                currencyLoader = currencyLoader,
            )
            return databaseComponent.transform(baseRepository)
        }
```

---

### Task 5: Verification

- [ ] **Step 1: Verify compilation**
Run: `./gradlew :app:assembleDebug`
