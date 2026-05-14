# Duplicate Transaction Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let users duplicate a transaction from two entry points — long-press on a row in the transaction list, and the 3-dots menu on the edit screen. The duplicate flow opens the edit screen with prefilled data but treats the result as a brand-new transaction (fresh id). The header replaces "Edit Transaction" with a title "Duplicate from" and a small subtitle showing `<amount> <currency> · <date>` from the source.

**Architecture:** Reuse `TransactionEditComponent`. Add a new `duplicateFromTransactionId: Id` binding. When that arg is `Id.Known`, the use case loads the source transaction the same way as edit mode, but `transactionId` stays `Id.Unknown` so `save()` falls through to `idGenerator()` and creates a new record. A new `HeaderMode` carried in the view-model state drives the title/subtitle and which menu items show.

---

## File Map

| File | Change |
|------|--------|
| `zero-ui/…/ModalHeader.kt` | Add optional `subtitle: String?` param |
| `zero-core/…/transactions/edit/OnDuplicateHandler.kt` | **New** — handler interface (with `Id.Known` param) |
| `zero-core/…/transactions/edit/TransactionEditComponent.kt` | Add `@BindsInstance duplicateFromTransactionId` + `onDuplicateHandler` |
| `zero-core/…/transactions/edit/TransactionEditUseCase.kt` | Add `Action.Duplicate`; carry `originalTransactionId` to state |
| `zero-core/…/transactions/edit/DefaultTransactionEditUseCase.kt` | Load source in duplicate mode; trigger handler on `Duplicate` action |
| `zero-core/…/transactions/edit/TransactionEditViewModel.kt` | Add `HeaderMode` (New/Edit/DuplicateFrom); add `Action.Duplicate`; carry `originalTransactionId` |
| `zero-core/…/transactions/edit/DefaultTransactionEditViewModel.kt` | Compute `HeaderMode` and subtitle via formatters |
| `zero-core/…/transactions/edit/TransactionEditViewProvider.kt` | Render title/subtitle from `HeaderMode`; add `Duplicate` menu item in edit-mode dropdown |
| `zero-core/…/transactions/OnDuplicateTransactionHandler.kt` | **New** — list-side handler |
| `zero-core/…/transactions/TransactionComponent.kt` | Add `@BindsInstance onDuplicateTransactionHandler` |
| `zero-core/…/transactions/TransactionViewModel.kt` | Add `Action.DuplicateTransaction(id)` |
| `zero-core/…/transactions/DefaultTransactionViewModel.kt` | Wire `DuplicateTransaction` → handler |
| `zero-core/…/transactions/TransactionViewProvider.kt` | Add "Duplicate" item in long-press dropdown alongside Delete |
| `zero-core/src/main/res/values/strings.xml` | Add `transaction_duplicate_from_title`, `transaction_duplicate` |
| `app/…/navigation/Destinations.kt` | Add `Transaction.Item.Duplicate` destination |
| `app/…/screens/MainActivityScreenComponent.kt` | Wire duplicate navigation + duplicate-source navigation entry |

---

### Task 1: ModalHeader — optional subtitle

**Files:**
- Modify: `zero-ui/src/main/java/com/hluhovskyi/zero/ui/ModalHeader.kt`

- [ ] **Add `subtitle: String? = null` param**

  Replace the centered single-line `Text` with a `Column` (same `Modifier.weight(1f)`, `horizontalAlignment = Alignment.CenterHorizontally`) that always shows the title in current style, and conditionally renders the subtitle below when non-null:
  - Subtitle: `fontSize = 11.sp`, `fontWeight = FontWeight.Medium`, `color = OnSurfaceVariant`, `letterSpacing = 0.4.sp`, `textAlign = TextAlign.Center`.
  - Add `OnSurfaceVariant` import from `com.hluhovskyi.zero.ui.theme.OnSurfaceVariant`.

  Height stays `64.dp`. Use a small spacer (`Spacer(modifier = Modifier.height(2.dp))`) between title and subtitle.

- [ ] **Commit**
  ```bash
  git add zero-ui/src/main/java/com/hluhovskyi/zero/ui/ModalHeader.kt
  git commit -m "feat(ui): ModalHeader subtitle support"
  ```

---

### Task 2: Strings

**Files:**
- Modify: `zero-core/src/main/res/values/strings.xml`

- [ ] **Add new strings** under the existing `<!-- Transaction Edit -->` block (near `transaction_edit_title`):
  ```xml
  <string name="transaction_duplicate_from_title">Duplicate from</string>
  <string name="transaction_duplicate">Duplicate</string>
  ```

- [ ] **Commit**
  ```bash
  git add zero-core/src/main/res/values/strings.xml
  git commit -m "strings: duplicate-from title and duplicate action"
  ```

---

### Task 3: Use-case layer — load source transaction in duplicate mode

**Files:**
- Create: `zero-core/src/main/java/com/hluhovskyi/zero/transactions/edit/OnDuplicateHandler.kt`
- Modify: `zero-core/src/main/java/com/hluhovskyi/zero/transactions/edit/TransactionEditUseCase.kt`
- Modify: `zero-core/src/main/java/com/hluhovskyi/zero/transactions/edit/DefaultTransactionEditUseCase.kt`
- Modify: `zero-core/src/main/java/com/hluhovskyi/zero/transactions/edit/TransactionEditComponent.kt`

- [ ] **Create `OnDuplicateHandler`** (parallel to `OnDiscardHandler`):
  ```kotlin
  package com.hluhovskyi.zero.transactions.edit

  import com.hluhovskyi.zero.common.Id

  fun interface OnDuplicateHandler {
      fun onDuplicate(transactionId: Id.Known)

      object Noop : OnDuplicateHandler {
          override fun onDuplicate(transactionId: Id.Known) = Unit
      }
  }
  ```

- [ ] **`TransactionEditUseCase.kt` — add `Duplicate` action**

  In the `Action` sealed interface add `object Duplicate : Action` (next to `Delete`).

- [ ] **`TransactionEditComponent.kt` — bind new arg + handler**

  Per [DI](docs/agents/dependency-injection.md):
  - Add a `@BindsInstance fun duplicateFromTransactionId(@DuplicateFromTransactionId id: Id): Builder` method to the Builder. Define a new qualifier `@Qualifier annotation class DuplicateFromTransactionId` next to existing `@PreSelectedCategoryId`.
  - Add `@BindsInstance fun onDuplicateHandler(handler: OnDuplicateHandler): Builder`.
  - In `companion object builder()`, default `duplicateFromTransactionId(Id.Unknown)` and `onDuplicateHandler(OnDuplicateHandler.Noop)`.
  - Inject both into the `useCase` provider and pass through to `DefaultTransactionEditUseCase`.

- [ ] **`DefaultTransactionEditUseCase.kt` — load source + handle action**

  - Add constructor params: `private val duplicateFromTransactionId: Id = Id.Unknown`, `private val onDuplicateHandler: OnDuplicateHandler`.
  - Replace the existing `if (transactionId is Id.Known)` block in `attach()` with: `val loadFromId = (duplicateFromTransactionId as? Id.Known) ?: (transactionId as? Id.Known)` and run the existing loading branch when `loadFromId != null` (use `loadFromId` everywhere `transactionId` was used inside that branch).
  - Add handling for `Action.Duplicate` (mirrors the `Discard` branch):
    ```kotlin
    is TransactionEditUseCase.Action.Duplicate -> {
        coroutineScope.launch(context = Dispatchers.Main) {
            (transactionId as? Id.Known)?.let { id -> onDuplicateHandler.onDuplicate(id) }
        }
    }
    ```

  Save behavior is unchanged: `save()` already does `(transactionId as? Id.Known) ?: idGenerator()`, so duplicate-mode (transactionId = Unknown) produces a brand-new id automatically.

- [ ] **Commit**
  ```bash
  git add zero-core/src/main/java/com/hluhovskyi/zero/transactions/edit/OnDuplicateHandler.kt \
          zero-core/src/main/java/com/hluhovskyi/zero/transactions/edit/TransactionEditUseCase.kt \
          zero-core/src/main/java/com/hluhovskyi/zero/transactions/edit/TransactionEditComponent.kt \
          zero-core/src/main/java/com/hluhovskyi/zero/transactions/edit/DefaultTransactionEditUseCase.kt
  git commit -m "feat(transaction-edit): duplicate source loading + handler"
  ```

---

### Task 4: View-model — HeaderMode + Duplicate action

**Files:**
- Modify: `zero-core/src/main/java/com/hluhovskyi/zero/transactions/edit/TransactionEditViewModel.kt`
- Modify: `zero-core/src/main/java/com/hluhovskyi/zero/transactions/edit/DefaultTransactionEditViewModel.kt`
- Modify: `zero-core/src/main/java/com/hluhovskyi/zero/transactions/edit/TransactionEditComponent.kt`

- [ ] **`TransactionEditViewModel.kt`**

  - Add `Action.Duplicate : Action` (alongside `Delete`).
  - Replace `val isEditMode: Boolean = false` in `State` with:
    ```kotlin
    val headerMode: HeaderMode = HeaderMode.New,
    ```
  - Add nested sealed interface:
    ```kotlin
    sealed interface HeaderMode {
        object New : HeaderMode
        object Edit : HeaderMode
        data class DuplicateFrom(val subtitle: String) : HeaderMode
    }
    ```

- [ ] **`DefaultTransactionEditViewModel.kt`**

  - Constructor: replace `isEditMode: Boolean` flag with two flags: `isEditMode: Boolean`, `isDuplicateMode: Boolean`. Inject `amountFormatter: AmountFormatter` and `dateFormatter: DateFormatter`.
  - In `state.map`, compute `headerMode`:
    - If `isDuplicateMode` → `HeaderMode.DuplicateFrom(formatSubtitle(state))`
    - Else if `isEditMode` → `HeaderMode.Edit`
    - Else → `HeaderMode.New`
  - `formatSubtitle(state)`: compute `"$amount $currencySymbol · $date"` where:
    - amount from `state.amount` parsed via `Amount(state.amount.toBigDecimalOrNull())` and formatted with `amountFormatter.format(amount, currencySymbol)`
    - currencySymbol: for Expense/Income use `selectedCurrency?.currencySymbol.orEmpty()`; for Transfer use `sourceCurrencySymbol`
    - date string: `dateFormatter.format(state.date.date, DayConfig.WithoutZero, MonthConfig.Readable, YearConfig.Default)`
    - Joiner: `" · "`. If amount string is blank, omit the amount segment (just the date).
  - Map `Action.Duplicate` → `TransactionEditUseCase.Action.Duplicate`.

- [ ] **`TransactionEditComponent.kt` Module**

  - Inject `AmountFormatter`, `DateFormatter` (already in `Dependencies`? — if not, add them to the `Dependencies` interface). Verify.
  - Change `viewModel(...)` provider to pass `isEditMode = transactionId is Id.Known`, `isDuplicateMode = duplicateFromTransactionId is Id.Known`, plus formatters.

- [ ] **Dependencies check**

  Open `TransactionEditComponent.Dependencies` and add `val amountFormatter: AmountFormatter` and `val dateFormatter: DateFormatter` if missing. Verify both are exposed by `MainActivityScreenComponent` (they should be — already used by `TransactionComponent`).

- [ ] **Commit**
  ```bash
  git add zero-core/src/main/java/com/hluhovskyi/zero/transactions/edit/TransactionEditViewModel.kt \
          zero-core/src/main/java/com/hluhovskyi/zero/transactions/edit/DefaultTransactionEditViewModel.kt \
          zero-core/src/main/java/com/hluhovskyi/zero/transactions/edit/TransactionEditComponent.kt
  git commit -m "feat(transaction-edit): HeaderMode + Duplicate action"
  ```

---

### Task 5: Edit-screen UI — render mode-aware header + dropdown

**Files:**
- Modify: `zero-core/src/main/java/com/hluhovskyi/zero/transactions/edit/TransactionEditViewProvider.kt`

- [ ] **Title + subtitle from `headerMode`**

  Replace the existing `title = if (state.isEditMode) … else …` with:
  ```kotlin
  val title = when (state.headerMode) {
      is TransactionEditViewModel.HeaderMode.New -> stringResource(R.string.transaction_new_title)
      is TransactionEditViewModel.HeaderMode.Edit -> stringResource(R.string.transaction_edit_title)
      is TransactionEditViewModel.HeaderMode.DuplicateFrom -> stringResource(R.string.transaction_duplicate_from_title)
  }
  val subtitle = (state.headerMode as? TransactionEditViewModel.HeaderMode.DuplicateFrom)?.subtitle
  ```

  Pass both to `ModalHeader(title = title, subtitle = subtitle, …)`.

- [ ] **`trailingContent` — show menu only in Edit mode**

  Gate the existing `if (state.isEditMode)` on `state.headerMode is HeaderMode.Edit`. Inside the dropdown, add a **Duplicate** item ABOVE Delete:
  - Tap closes menu then `viewModel.perform(TransactionEditViewModel.Action.Duplicate)`.
  - Use `Icons.Outlined.ContentCopy` (verify import path — `androidx.compose.material.icons.outlined.ContentCopy`) at `Modifier.size(18.dp)`, `tint = OnSurface`.
  - Label: `stringResource(R.string.transaction_duplicate)`, normal text color.
  - Same `Row` layout as Delete entry.

- [ ] **Commit**
  ```bash
  git add zero-core/src/main/java/com/hluhovskyi/zero/transactions/edit/TransactionEditViewProvider.kt
  git commit -m "feat(transaction-edit): mode-aware header + duplicate menu item"
  ```

---

### Task 6: List-side handler + view-model action

**Files:**
- Create: `zero-core/src/main/java/com/hluhovskyi/zero/transactions/OnDuplicateTransactionHandler.kt`
- Modify: `zero-core/src/main/java/com/hluhovskyi/zero/transactions/TransactionComponent.kt`
- Modify: `zero-core/src/main/java/com/hluhovskyi/zero/transactions/TransactionViewModel.kt`
- Modify: `zero-core/src/main/java/com/hluhovskyi/zero/transactions/DefaultTransactionViewModel.kt`

- [ ] **Create `OnDuplicateTransactionHandler`** (mirror `OnTransactionSelectedHandler`):
  ```kotlin
  package com.hluhovskyi.zero.transactions

  import com.hluhovskyi.zero.common.Id

  fun interface OnDuplicateTransactionHandler {
      fun onDuplicate(transactionId: Id.Known)

      object Noop : OnDuplicateTransactionHandler {
          override fun onDuplicate(transactionId: Id.Known) = Unit
      }
  }
  ```

- [ ] **`TransactionComponent.kt`**

  - Add `@BindsInstance fun onDuplicateTransactionHandler(handler: OnDuplicateTransactionHandler): Builder` to the Builder.
  - Default in `companion object builder()` → `.onDuplicateTransactionHandler(OnDuplicateTransactionHandler.Noop)`.
  - Inject into `viewModel` provider and pass to `DefaultTransactionViewModel`.

- [ ] **`TransactionViewModel.kt`**

  Add `data class DuplicateTransaction(val id: Id.Known) : Action` alongside `DeleteTransaction`.

- [ ] **`DefaultTransactionViewModel.kt`**

  Accept new constructor param `onDuplicateTransactionHandler: OnDuplicateTransactionHandler`. Handle `DuplicateTransaction` by calling `onDuplicateTransactionHandler.onDuplicate(action.id)`.

- [ ] **Commit**
  ```bash
  git add zero-core/src/main/java/com/hluhovskyi/zero/transactions/OnDuplicateTransactionHandler.kt \
          zero-core/src/main/java/com/hluhovskyi/zero/transactions/TransactionComponent.kt \
          zero-core/src/main/java/com/hluhovskyi/zero/transactions/TransactionViewModel.kt \
          zero-core/src/main/java/com/hluhovskyi/zero/transactions/DefaultTransactionViewModel.kt
  git commit -m "feat(transactions): duplicate action + handler"
  ```

---

### Task 7: List UI — Duplicate dropdown item

**Files:**
- Modify: `zero-core/src/main/java/com/hluhovskyi/zero/transactions/TransactionViewProvider.kt`

- [ ] **Add Duplicate `DropdownMenuItem` above Delete** in the long-press dropdown:
  - Icon: `Icons.Outlined.ContentCopy`, `Modifier.size(20.dp)`, `tint = OnSurfaceVariant` (or default), spacer 8.dp.
  - Label: `stringResource(R.string.transaction_duplicate)`.
  - `onClick`: `viewModel.perform(TransactionViewModel.Action.DuplicateTransaction(transaction.id)); expandedItemId = null`.

- [ ] **Commit**
  ```bash
  git add zero-core/src/main/java/com/hluhovskyi/zero/transactions/TransactionViewProvider.kt
  git commit -m "feat(transactions-list): duplicate menu item on long-press"
  ```

---

### Task 8: Navigation + wiring

**Files:**
- Modify: `app/src/main/java/com/hluhovskyi/zero/activity/navigation/Destinations.kt`
- Modify: `app/src/main/java/com/hluhovskyi/zero/activity/screens/MainActivityScreenComponent.kt`

- [ ] **Add `Duplicate` destination** under `Transaction.Item`:
  ```kotlin
  object Duplicate : Item, Destination by destinationOf("transactions/{transactionId}/duplicate", TransactionId)
  ```
  Per [Navigation](docs/agents/navigation.md).

- [ ] **`MainActivityScreenComponent.kt`** changes:

  1. **`homeNavigationEntry`** — on `transactionComponentBuilder`, add:
     ```kotlin
     .onDuplicateTransactionHandler { transactionId ->
         navigator.navigateTo(
             Destinations.Transaction.Item.Duplicate,
             Destinations.Transaction.Item.TransactionId.withValue(transactionId),
         )
     }
     ```

  2. **`transactionItemEditNavigationEntry`** — add `.onDuplicateHandler { transactionId -> navigator.navigateTo(Destinations.Transaction.Item.Duplicate, …) }` (same navigation as above).

  3. **New `transactionItemDuplicateNavigationEntry`** (mirrors `transactionItemEditNavigationEntry`):
     ```kotlin
     @Provides @IntoSet @MainActivityScreenScope
     fun transactionItemDuplicateNavigationEntry(
         componentBuilder: TransactionEditComponent.Builder,
         transactionEditCategoryUseCase: TransactionEditCategoryUseCase,
         transactionEditCurrencyUseCase: TransactionEditCurrencyUseCase,
         navigatorScope: NavigatorScope,
         logger: Logger,
     ): NavigatorEntry = navigatorScope.buildable(Destinations.Transaction.Item.Duplicate) {
         componentBuilder
             .transactionId(Id.Unknown)
             .duplicateFromTransactionId(arguments.getValue(Destinations.Transaction.Item.TransactionId))
             .onTransactionSavedHandler { navigator.back() }
             .onEditCategoriesHandler { navigator.navigateTo(Destinations.Category.All) }
             .onDiscardHandler { navigator.back() }
             .transactionEditCategoryUseCase(transactionEditCategoryUseCase)
             .transactionEditCurrencyUseCase(transactionEditCurrencyUseCase)
             .logging(logger)
     }
     ```

  4. Check if any other transaction list site needs the duplicate handler (search `transactionComponentBuilder` callers). Wire identical handler in each.

- [ ] **Examine other `transactionComponentBuilder` use sites**

  ```bash
  grep -n "transactionComponentBuilder" app/src/main/java/com/hluhovskyi/zero/activity/screens/MainActivityScreenComponent.kt
  ```
  Wire `onDuplicateTransactionHandler` in each.

- [ ] **Commit**
  ```bash
  git add app/src/main/java/com/hluhovskyi/zero/activity/navigation/Destinations.kt \
          app/src/main/java/com/hluhovskyi/zero/activity/screens/MainActivityScreenComponent.kt
  git commit -m "feat(navigation): duplicate destination + wiring"
  ```

---

## Verification

- [ ] `./gradlew testDebugUnitTest 2>&1 | tail -20` — all green.
- [ ] `./gradlew lintDebug 2>&1 | grep -E "error:|Error" | head -20` — no errors.
- [ ] Install and run; use `zero-project:android-ui-inspector` to verify:
  - List long-press shows Duplicate + Delete items.
  - Tapping Duplicate opens screen with title "Duplicate from" and the source amount + date as subtitle.
  - Fields are prefilled (amount, account, category, currency, date, notes).
  - Save button creates a new transaction (verify by going back; both old and new records exist).
  - Edit-screen 3-dots menu shows Duplicate above Delete. Tapping Duplicate opens duplicate screen for that record.
  - Duplicate screen 3-dots menu is hidden (no Delete on a not-yet-saved record).

---

## Execution Handoff

Use `superpowers:subagent-driven-development` to execute.
