# Transaction Transfer Styling Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Style "Transfer" transactions to be visually consistent with "Expense" and "Income" types using a "destination-primary" double-entry layout.

**Architecture:**
1. Generalize `TransactionView` to accept generic parameters.
2. Extend the ViewModel to provide necessary icon and color scheme for Transfers.
3. Implement `TransactionTransferView` using the generalized `TransactionView`.

**Tech Stack:** Kotlin, Jetpack Compose, Android

## Visual Layout

The `TransactionTransferView` will follow this double-entry structure:

```text
+-------+  Destination Account Name          + Target Amount
| (⇄)   |  (Bold, Primary)                   (Bold, Positive)
|       |
+-------+  Source Account Name               - Source Amount
(Neutral)  (Secondary)                       (Secondary, Negative)
```

---

### Task 1: Extend Icon Repository

**Files:**
- Modify: `zero-api/src/main/java/com/hluhovskyi/zero/icons/IconRepository.kt`
- Modify: `app/src/main/java/com/hluhovskyi/zero/icons/PredefinedIconRepository.kt`

- [ ] **Step 1: Add transferIconId to IconRepository**

```kotlin
// zero-api/src/main/java/com/hluhovskyi/zero/icons/IconRepository.kt
fun transferIconId(): Id.Known = Id("transfer_icon")
```

- [ ] **Step 2: Map transferIconId in PredefinedIconRepository**

```kotlin
// app/src/main/java/com/hluhovskyi/zero/icons/PredefinedIconRepository.kt
iconOf(
    id = IconRepository.transferIconId(),
    resourceName = "ic_transfer_24",
    description = "Transfer"
),
```

- [ ] **Step 3: Commit**

```bash
git add zero-api/src/main/java/com/hluhovskyi/zero/icons/IconRepository.kt app/src/main/java/com/hluhovskyi/zero/icons/PredefinedIconRepository.kt
git commit -m "feat: add transfer icon to repository"
```

---

### Task 2: Update TransactionViewModel Data Model

**Files:**
- Modify: `zero-core/src/main/java/com/hluhovskyi/zero/transactions/TransactionViewModel.kt`

- [ ] **Step 1: Add icon and color fields to Transfer data class**

```kotlin
// zero-core/src/main/java/com/hluhovskyi/zero/transactions/TransactionViewModel.kt
data class Transfer(
    // ... existing fields
    val transferIcon: Image,
    val transferColorScheme: ColorScheme,
) : Transaction
```

- [ ] **Step 2: Commit**

```bash
git add zero-core/src/main/java/com/hluhovskyi/zero/transactions/TransactionViewModel.kt
git commit -m "feat: enhance Transfer data model with transfer icon and color scheme"
```

---

### Task 3: Update Data Resolution in DefaultTransactionViewModel

**Files:**
- Modify: `zero-core/src/main/java/com/hluhovskyi/zero/transactions/DefaultTransactionViewModel.kt`

- [ ] **Step 1: Resolve new fields in Transfer mapping**

```kotlin
// zero-core/src/main/java/com/hluhovskyi/zero/transactions/DefaultTransactionViewModel.kt
// In resolve() for Transfer:
transferIcon = idToIcons[IconRepository.transferIconId()]?.image ?: Image.empty(),
transferColorScheme = ColorScheme.Grey,
```

- [ ] **Step 2: Commit**

```bash
git add zero-core/src/main/java/com/hluhovskyi/zero/transactions/DefaultTransactionViewModel.kt
git commit -m "feat: resolve transfer icon and color scheme in ViewModel"
```

---

### Task 4: Rework and Extend ViewModel Tests

**Files:**
- Rename/Modify: `zero-core/src/test/java/com/hluhovskyi/zero/transactions/DefaultTransactionViewModelLoadMoreTest.kt` -> `DefaultTransactionViewModelTest.kt`

- [ ] **Step 1: Rename the test file and class**

- [ ] **Step 2: Add test case for Transfer mapping**
Verify that repository `Transfer` items are correctly mapped to ViewModel `Transfer` items with resolved icons and grey color scheme.

- [ ] **Step 3: Run tests and verify PASS**

- [ ] **Step 4: Commit**

```bash
git add zero-core/src/test/java/com/hluhovskyi/zero/transactions/DefaultTransactionViewModelTest.kt
git commit -m "test: rename to DefaultTransactionViewModelTest and add mapping tests"
```

---

### Task 5: Generalize TransactionView in zero-ui

**Files:**
- Modify: `zero-ui/src/main/java/com/hluhovskyi/zero/transaction/TransactionExpenseView.kt`

- [ ] **Step 1: Refactor TransactionView to use generic parameters (remove secondaryIcon)**

- [ ] **Step 2: Update TransactionExpenseView and TransactionIncomeView to wrap TransactionView**

- [ ] **Step 3: Commit**

```bash
git add zero-ui/src/main/java/com/hluhovskyi/zero/transaction/TransactionExpenseView.kt
git commit -m "refactor: generalize TransactionView and remove secondaryIcon"
```

---

### Task 6: Implement TransactionTransferView

**Files:**
- Modify: `zero-ui/src/main/java/com/hluhovskyi/zero/transaction/TransactionExpenseView.kt`

- [ ] **Step 1: Add TransactionTransferView implementation**

- [ ] **Step 2: Commit**

```bash
git add zero-ui/src/main/java/com/hluhovskyi/zero/transaction/TransactionExpenseView.kt
git commit -m "feat: add TransactionTransferView component"
```

---

### Task 7: Use TransactionTransferView in TransactionViewProvider

**Files:**
- Modify: `zero-core/src/main/java/com/hluhovskyi/zero/transactions/TransactionViewProvider.kt`

- [ ] **Step 1: Update TransactionTransferView usage in LazyColumn**

- [ ] **Step 2: Remove old TransactionTransferView definition from end of file**

- [ ] **Step 3: Commit**

```bash
git add zero-core/src/main/java/com/hluhovskyi/zero/transactions/TransactionViewProvider.kt
git commit -m "feat: use enhanced TransactionTransferView in UI"
```
