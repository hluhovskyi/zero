# Kotlinx Datetime Migration Fixes Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fix 3 issues on the `feat/kotlinx-datetime-migration` branch: separate `Clock` from `ZoneProvider`, remove epoch sentinel defaults from State classes, and restore KDoc to `DateFormatter.kt`.

**Architecture:**
- **FIX 1:** Decouple `Clock` from `ZoneProvider`. `Clock` only provides `now(): Instant`. `localDateTime()` extension now requires an explicit `TimeZone`. Inject `ZoneProvider` where `localDateTime()` is needed.
- **FIX 2:** Change default `LocalDateTime` state fields from epoch (1970) to `null`. Update UI to handle nullability (guarding `DatePickerCard` etc.).
- **FIX 3:** Restore missing documentation to `DateFormatter.kt`.

**Tech Stack:** Kotlin, kotlinx-datetime, Dagger 2, Jetpack Compose.

---

## Task 1: Fix 1 - Decouple Clock from ZoneProvider

### Step 1.1: Update Clock.kt
**Files:**
- Modify: `zero-api/src/main/java/com/hluhovskyi/zero/common/time/Clock.kt`

- [ ] **Action:** Update `Clock` interface and `localDateTime` extension.

```kotlin
package com.hluhovskyi.zero.common.time

import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * Injectable time provider. Always use this instead of Clock.System.now() — keeps code testable
 * with fixed clocks.
 */
interface Clock {
    fun now(): Instant
}

/** Convenience extension — requires explicit timezone from ZoneProvider. */
fun Clock.localDateTime(timeZone: TimeZone): LocalDateTime = now().toLocalDateTime(timeZone)
```

### Step 1.2: Update ZoneBasedClock.kt
**Files:**
- Modify: `app/src/main/java/com/hluhovskyi/zero/common/time/ZoneBasedClock.kt`

- [ ] **Action:** Remove `timeZone()` implementation.

```kotlin
package com.hluhovskyi.zero.common.time

import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone

internal class ZoneBasedClock(
    private val zoneProvider: ZoneProvider,
) : Clock {
    override fun now(): Instant = kotlinx.datetime.Clock.System.now()
}
```

### Step 1.3: Update LocaleBasedDateFormatter.kt
**Files:**
- Modify: `app/src/main/java/com/hluhovskyi/zero/common/LocaleBasedDateFormatter.kt`

- [ ] **Action:** Inject `ZoneProvider` and use it.

```kotlin
internal class LocaleBasedDateFormatter(
    private val localeProvider: LocaleProvider,
    private val clock: Clock,
    private val zoneProvider: ZoneProvider,
) : DateFormatter {

    private val currentYear by lazy {
        clock.localDateTime(zoneProvider.timeZone()).year
    }
    // ...
}
```

### Step 1.4: Update ApplicationComponent.kt
**Files:**
- Modify: `app/src/main/java/com/hluhovskyi/zero/ApplicationComponent.kt`

- [ ] **Action:** Expose `zoneProvider` and update `dateFormatter` provision.

### Step 1.5: Update DatabaseComponent.kt
**Files:**
- Modify: `zero-database/src/main/java/com/hluhovskyi/zero/DatabaseComponent.kt`

- [ ] **Action:** Add `zoneProvider` to `Dependencies` and update `transactionRepository` and `categoryRepository` provisions.

### Step 1.6: Update RoomTransactionRepository.kt
**Files:**
- Modify: `zero-database/src/main/java/com/hluhovskyi/zero/transactions/RoomTransactionRepository.kt`

- [ ] **Action:** Inject `ZoneProvider` and update `clock.localDateTime()` calls.

### Step 1.7: Update RoomCategoryRepository.kt
**Files:**
- Modify: `zero-database/src/main/java/com/hluhovskyi/zero/categories/RoomCategoryRepository.kt`

- [ ] **Action:** Inject `ZoneProvider` and update `clock.localDateTime()` calls.

### Step 1.8: Update DefaultCategoriesQueryUseCase.kt
**Files:**
- Modify: `zero-core/src/main/java/com/hluhovskyi/zero/categories/DefaultCategoriesQueryUseCase.kt`

- [ ] **Action:** Inject `ZoneProvider` and update `clock.localDateTime()` calls.

### Step 1.9: Update DefaultImportUseCase.kt
**Files:**
- Modify: `zero-core/src/main/java/com/hluhovskyi/zero/imports/DefaultImportUseCase.kt`

- [ ] **Action:** Inject `ZoneProvider` and update `clock.localDateTime()` calls.

### Step 1.10: Update DefaultTransactionViewModel.kt
**Files:**
- Modify: `zero-core/src/main/java/com/hluhovskyi/zero/transactions/DefaultTransactionViewModel.kt`

- [ ] **Action:** Inject `ZoneProvider` and update `clock.localDateTime()` calls.

### Step 1.11: Update DefaultTransactionEditUseCase.kt
**Files:**
- Modify: `zero-core/src/main/java/com/hluhovskyi/zero/transactions/edit/DefaultTransactionEditUseCase.kt`

- [ ] **Action:** Inject `ZoneProvider` and update all `clock.localDateTime()` calls.

### Step 1.12: Update Dagger wiring for Use Cases and ViewModels
**Files:**
- Modify: `zero-core/src/main/java/com/hluhovskyi/zero/categories/CategoryComponent.kt`
- Modify: `zero-core/src/main/java/com/hluhovskyi/zero/imports/ImportComponent.kt`
- Modify: `zero-core/src/main/java/com/hluhovskyi/zero/transactions/TransactionComponent.kt`
- Modify: `zero-core/src/main/java/com/hluhovskyi/zero/transactions/edit/TransactionEditComponent.kt`

- [ ] **Action:** Pass `zoneProvider` to constructors.

### Step 1.13: Update Tests
**Files:**
- Modify: `zero-core/src/test/java/com/hluhovskyi/zero/categories/DefaultCategoriesQueryUseCaseTest.kt`
- Modify: `zero-core/src/test/java/com/hluhovskyi/zero/transactions/DefaultTransactionViewModelTest.kt`

- [ ] **Action:** Add `fakeZoneProvider` and update tests.

---

## Task 2: Fix 2 - Remove Epoch Sentinel Defaults

### Step 2.1: Update State interfaces
**Files:**
- Modify: `zero-core/src/main/java/com/hluhovskyi/zero/transactions/edit/TransactionEditViewModel.kt`
- Modify: `zero-core/src/main/java/com/hluhovskyi/zero/transactions/edit/expense/TransactionEditExpenseViewModel.kt`
- Modify: `zero-core/src/main/java/com/hluhovskyi/zero/transactions/edit/income/TransactionEditIncomeViewModel.kt`
- Modify: `zero-core/src/main/java/com/hluhovskyi/zero/transactions/edit/transfer/TransactionEditTransferViewModel.kt`
- Modify: `zero-core/src/main/java/com/hluhovskyi/zero/transactions/preview/TransactionPreviewViewModel.kt`

- [ ] **Action:** Change `date: LocalDateTime` or `dateTime: LocalDateTime` to nullable with `null` default.

### Step 2.2: Update ViewProviders to handle nullability
**Files:**
- Modify: `zero-core/src/main/java/com/hluhovskyi/zero/transactions/edit/expense/TransactionEditExpenseViewProvider.kt`
- Modify: `zero-core/src/main/java/com/hluhovskyi/zero/transactions/edit/income/TransactionEditIncomeViewProvider.kt`
- Modify: `zero-core/src/main/java/com/hluhovskyi/zero/transactions/edit/transfer/TransactionEditTransferViewProvider.kt`
- Modify: `zero-core/src/main/java/com/hluhovskyi/zero/transactions/preview/TransactionPreviewViewProvider.kt`

- [ ] **Action:** Guard `DatePickerCard` and other date usages with `state.date?.let { ... }`.

---

## Task 3: Fix 3 - Restore DateFormatter KDoc

### Step 3.1: Update DateFormatter.kt
**Files:**
- Modify: `zero-api/src/main/java/com/hluhovskyi/zero/common/DateFormatter.kt`

- [ ] **Action:** Replace content with the provided one including KDoc.

---

## Task 4: Verification

- [ ] **Step 4.1: Compilation**
Run: `./gradlew :zero-api:compileReleaseKotlin :app:compileReleaseKotlin :zero-core:compileReleaseKotlin :zero-database:compileReleaseKotlin`

- [ ] **Step 4.2: Tests**
Run: `./gradlew :zero-core:test :zero-database:test :app:test`

- [ ] **Step 4.3: Commit**
Commit all changes with message: "fix: separate Clock from ZoneProvider, remove epoch defaults, restore DateFormatter KDoc"
