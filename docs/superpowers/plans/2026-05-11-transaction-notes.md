# Transaction Notes Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add an optional free-text notes field to transactions, stored in the database and editable on the transaction edit screen.

**Architecture:** Notes flow through all layers — `TransactionEntity` → `TransactionRepository.Transaction` / `SyncTransaction` → `TransactionEditUseCase` → `TransactionEditViewModel` → `TransactionEditViewProvider`. The field is nullable everywhere (`String?`) and defaults to `null` so existing records are unaffected. A Room migration (4 → 5) adds the column with `NULL` default.

**Tech Stack:** Kotlin, Room (SQLite migration), Jetpack Compose (`OutlinedTextField`)

---

## File Map

| File | Change |
|------|--------|
| `zero-database/…/TransactionEntity.kt` | Add `notes: String? = null` |
| `zero-database/…/TransactionMigrations.kt` | New — `MIGRATION_4_5` |
| `zero-database/…/MainDatabase.kt` | Bump version 4 → 5 |
| `zero-database/…/DatabaseComponent.kt` | Register `MIGRATION_4_5` |
| `zero-database/…/RoomTransactionRepository.kt` | Map `notes` in `toRepository()` / `toEntity()` |
| `zero-database/…/RoomTransactionSyncSource.kt` | Map `notes` in `toSyncModel()` |
| `zero-database/…/RoomTransactionSyncSink.kt` | Map `notes` in `toEntity()` |
| `zero-api/…/TransactionRepository.kt` | Add `notes: String?` to Expense / Income / Transfer |
| `zero-api/…/SyncTransaction.kt` | Add `@SerialName("notes") val notes: String? = null` |
| `zero-core/…/TransactionEditUseCase.kt` | Add `ChangeNotes` action; add `notes` to each State subtype |
| `zero-core/…/DefaultTransactionEditUseCase.kt` | Handle action, load on attach, save on Save |
| `zero-core/…/TransactionEditViewModel.kt` | Add `ChangeNotes` action; add `notes` to State |
| `zero-core/…/DefaultTransactionEditViewModel.kt` | Map notes; delegate `ChangeNotes` |
| `zero-core/…/TransactionEditViewProvider.kt` | Add `OutlinedTextField` for notes below the sub-form |

---

### Task 1: Database — entity column + migration

**Files:**
- Modify: `zero-database/src/main/java/com/hluhovskyi/zero/transactions/TransactionEntity.kt`
- Create: `zero-database/src/main/java/com/hluhovskyi/zero/transactions/TransactionMigrations.kt`
- Modify: `zero-database/src/main/java/com/hluhovskyi/zero/MainDatabase.kt`
- Modify: `zero-database/src/main/java/com/hluhovskyi/zero/DatabaseComponent.kt`

- [ ] **Add `notes` column to `TransactionEntity`**

  In `TransactionEntity.kt`, add after `deletedAt`:
  ```kotlin
  val notes: String? = null,
  ```

- [ ] **Create `TransactionMigrations.kt`**

  ```kotlin
  package com.hluhovskyi.zero.transactions

  import androidx.room.migration.Migration
  import androidx.sqlite.db.SupportSQLiteDatabase

  internal val MIGRATION_4_5 = object : Migration(4, 5) {
      override fun migrate(db: SupportSQLiteDatabase) {
          db.execSQL("ALTER TABLE TransactionEntity ADD COLUMN notes TEXT")
      }
  }
  ```

- [ ] **Bump database version in `MainDatabase.kt`**

  Change:
  ```kotlin
  private const val MAIN_DATABASE_VERSION = 4
  ```
  To:
  ```kotlin
  private const val MAIN_DATABASE_VERSION = 5
  ```

- [ ] **Register migration in `DatabaseComponent.kt`**

  Change:
  ```kotlin
  .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
  ```
  To:
  ```kotlin
  .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
  ```

  Add import: `import com.hluhovskyi.zero.transactions.MIGRATION_4_5`

- [ ] **Commit**

  ```bash
  git add zero-database/src/main/java/com/hluhovskyi/zero/transactions/TransactionEntity.kt \
          zero-database/src/main/java/com/hluhovskyi/zero/transactions/TransactionMigrations.kt \
          zero-database/src/main/java/com/hluhovskyi/zero/MainDatabase.kt \
          zero-database/src/main/java/com/hluhovskyi/zero/DatabaseComponent.kt
  git commit -m "feat: add notes column to TransactionEntity (migration 4→5)"
  ```

---

### Task 2: API layer — domain model + sync model

**Files:**
- Modify: `zero-api/src/main/java/com/hluhovskyi/zero/transactions/TransactionRepository.kt`
- Modify: `zero-api/src/main/java/com/hluhovskyi/zero/sync/SyncTransaction.kt`

- [ ] **Add `notes: String?` to all three `Transaction` subtypes in `TransactionRepository.kt`**

  Add `val notes: String? = null` to `Transaction.Expense`, `Transaction.Income`, and `Transaction.Transfer` (after existing fields in each data class).

- [ ] **Add `notes` to `SyncTransaction.kt`**

  Add after `deletedAt`:
  ```kotlin
  @SerialName("notes") val notes: String? = null,
  ```
  The `= null` default ensures backward-compat when deserialising older snapshots (the serializer already has `ignoreUnknownKeys = true` and `encodeDefaults = true`).

- [ ] **Commit**

  ```bash
  git add zero-api/src/main/java/com/hluhovskyi/zero/transactions/TransactionRepository.kt \
          zero-api/src/main/java/com/hluhovskyi/zero/sync/SyncTransaction.kt
  git commit -m "feat: add notes field to TransactionRepository.Transaction and SyncTransaction"
  ```

---

### Task 3: Database — repository mapping

**Files:**
- Modify: `zero-database/src/main/java/com/hluhovskyi/zero/transactions/RoomTransactionRepository.kt`
- Modify: `zero-database/src/main/java/com/hluhovskyi/zero/transactions/RoomTransactionSyncSource.kt`
- Modify: `zero-database/src/main/java/com/hluhovskyi/zero/transactions/RoomTransactionSyncSink.kt`

- [ ] **Map `notes` in `RoomTransactionRepository.toRepository()`**

  In each branch of the `when (type)` expression in `TransactionEntity.toRepository()`, add `notes = notes` to the `TransactionRepository.Transaction.*` constructor call.

- [ ] **Map `notes` in `RoomTransactionRepository.toEntity()`**

  In each branch of `TransactionRepository.Transaction.toEntity()`, add `notes = notes` to the `TransactionEntity(...)` constructor call.

- [ ] **Map `notes` in `RoomTransactionSyncSource.toSyncModel()`**

  Add `notes = notes` to the `SyncTransaction(...)` call.

- [ ] **Map `notes` in `RoomTransactionSyncSink.toEntity()`**

  Add `notes = notes` to the `TransactionEntity(...)` call.

- [ ] **Commit**

  ```bash
  git add zero-database/src/main/java/com/hluhovskyi/zero/transactions/RoomTransactionRepository.kt \
          zero-database/src/main/java/com/hluhovskyi/zero/transactions/RoomTransactionSyncSource.kt \
          zero-database/src/main/java/com/hluhovskyi/zero/transactions/RoomTransactionSyncSink.kt
  git commit -m "feat: wire notes through database repository and sync mappings"
  ```

---

### Task 4: Core — use case

**Files:**
- Modify: `zero-core/src/main/java/com/hluhovskyi/zero/transactions/edit/TransactionEditUseCase.kt`
- Modify: `zero-core/src/main/java/com/hluhovskyi/zero/transactions/edit/DefaultTransactionEditUseCase.kt`

- [ ] **Add `ChangeNotes` action to `TransactionEditUseCase.Action`**

  In `TransactionEditUseCase.kt`, inside `sealed interface Action`, add:
  ```kotlin
  data class ChangeNotes(val notes: String) : Action
  ```

- [ ] **Add `notes` to each `TransactionEditUseCase.State` subtype**

  Add `val notes: String = ""` to `State.Expense`, `State.Income`, and `State.Transfer` data classes.

- [ ] **Add `notes` to `CompositeState` in `DefaultTransactionEditUseCase`**

  Add `val notes: String = ""` to `CompositeState`.

- [ ] **Propagate `notes` from `CompositeState` to each `State` branch in `DefaultTransactionEditUseCase.state`**

  In the `.map { state -> ... }` block, add `notes = state.notes` to each `TransactionEditUseCase.State.*` constructor call.

- [ ] **Handle `ChangeNotes` in `DefaultTransactionEditUseCase.perform()`**

  Add:
  ```kotlin
  is TransactionEditUseCase.Action.ChangeNotes -> {
      mutableState.update { state -> state.copy(notes = action.notes) }
  }
  ```

- [ ] **Load `notes` from existing transaction in `attach()`**

  In the `mutableState.update { state -> ... }` block inside `attach()` where the transaction is loaded, add `notes = transaction.notes ?: ""` to `partialState.copy(...)`. This applies to all three branches (Expense, Income, Transfer).

  The `partialState` is built from `state.copy(amount = ..., ...)` before the `when (transaction)` branch. Add `notes = transaction.notes ?: ""` inside that initial `state.copy(...)`:
  ```kotlin
  val partialState = state.copy(
      amount = transaction.amount.value.toString(),
      selectedCurrency = currencyToSelect ?: state.selectedCurrency,
      selectedAccount = accountToSelect ?: state.selectedAccount,
      localDateTime = transaction.dateTime,
      notes = transaction.notes ?: "",
  )
  ```

- [ ] **Pass `notes` when saving in `DefaultTransactionEditUseCase.save()`**

  In each `TransactionRepository.Transaction.*` constructor call inside `save()`, add `notes = state.notes`.

- [ ] **Commit**

  ```bash
  git add zero-core/src/main/java/com/hluhovskyi/zero/transactions/edit/TransactionEditUseCase.kt \
          zero-core/src/main/java/com/hluhovskyi/zero/transactions/edit/DefaultTransactionEditUseCase.kt
  git commit -m "feat: add notes to TransactionEditUseCase"
  ```

---

### Task 5: Core — view model

**Files:**
- Modify: `zero-core/src/main/java/com/hluhovskyi/zero/transactions/edit/TransactionEditViewModel.kt`
- Modify: `zero-core/src/main/java/com/hluhovskyi/zero/transactions/edit/DefaultTransactionEditViewModel.kt`

- [ ] **Add `ChangeNotes` action to `TransactionEditViewModel.Action`**

  In `TransactionEditViewModel.kt`, inside `sealed interface Action`, add:
  ```kotlin
  data class ChangeNotes(val notes: String) : Action
  ```

- [ ] **Add `notes` to `TransactionEditViewModel.State`**

  Add `val notes: String = ""` to the `State` data class.

- [ ] **Map `notes` in `DefaultTransactionEditViewModel.state`**

  Add `notes = state.notes` to the `TransactionEditViewModel.State(...)` constructor call in the `.map { state -> ... }` in `DefaultTransactionEditViewModel`.

  `state` here is `TransactionEditUseCase.State`; access `notes` via the shared property. All three `TransactionEditUseCase.State` subtypes now have `notes`, so use a common accessor:
  ```kotlin
  notes = when (state) {
      is TransactionEditUseCase.State.Expense -> state.notes
      is TransactionEditUseCase.State.Income -> state.notes
      is TransactionEditUseCase.State.Transfer -> state.notes
  },
  ```

- [ ] **Delegate `ChangeNotes` in `DefaultTransactionEditViewModel.perform()`**

  Add to the `when (action)` block:
  ```kotlin
  is TransactionEditViewModel.Action.ChangeNotes ->
      TransactionEditUseCase.Action.ChangeNotes(action.notes)
  ```

- [ ] **Commit**

  ```bash
  git add zero-core/src/main/java/com/hluhovskyi/zero/transactions/edit/TransactionEditViewModel.kt \
          zero-core/src/main/java/com/hluhovskyi/zero/transactions/edit/DefaultTransactionEditViewModel.kt
  git commit -m "feat: add notes to TransactionEditViewModel"
  ```

---

### Task 6: UI — notes text field in TransactionEditViewProvider

**Files:**
- Modify: `zero-core/src/main/java/com/hluhovskyi/zero/transactions/edit/TransactionEditViewProvider.kt`

- [ ] **Add the notes field to `TransactionEditView`**

  After the `item { when (state.selectedTransactionType) { ... } }` block and before the closing `}` of `LazyColumn`, add a new `item`:

  ```kotlin
  item {
      OutlinedTextField(
          modifier = Modifier
              .fillMaxWidth()
              .padding(horizontal = 24.dp)
              .padding(top = 16.dp),
          value = state.notes,
          label = { Text(text = "Notes") },
          onValueChange = { notes ->
              viewModel.perform(TransactionEditViewModel.Action.ChangeNotes(notes))
          },
          minLines = 2,
      )
  }
  ```

  Add imports if missing:
  ```kotlin
  import androidx.compose.material.OutlinedTextField
  ```
  (`Text`, `fillMaxWidth`, `padding` are already imported.)

- [ ] **Commit**

  ```bash
  git add zero-core/src/main/java/com/hluhovskyi/zero/transactions/edit/TransactionEditViewProvider.kt
  git commit -m "feat: render notes text field on transaction edit screen"
  ```

---

### Task 7: Verification

- [ ] **Build**

  ```bash
  ./gradlew assembleDebug 2>&1 | tail -20
  ```
  Expected: `BUILD SUCCESSFUL`

- [ ] **Tests**

  ```bash
  ./gradlew testDebugUnitTest 2>&1 | tail -20
  ```
  Expected: all tests pass.

- [ ] **Lint**

  ```bash
  ./gradlew lintDebug 2>&1 | grep -E "error:|Error" | head -20
  ```
  Expected: no errors.

- [ ] **UI inspection**

  Deploy to device and open the transaction edit screen. Run `zero-project:android-ui-inspector` to confirm the `OutlinedTextField` with label "Notes" is visible and focusable below the transaction type sub-form.
