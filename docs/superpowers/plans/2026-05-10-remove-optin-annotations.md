# Remove @OptIn Annotations — Move to Build Args Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Audit all `@OptIn` annotations across the codebase and hoist them to module-level compiler arguments in each module's `build.gradle`, removing all per-file/per-function annotation noise.

**Architecture:** Each module's `android { kotlin { compilerOptions { optIn.add(...) } } }` block receives the set of experimental APIs used anywhere in that module. All corresponding `@OptIn(...)` annotations and their imports are then removed from Kotlin source files.

**Tech Stack:** Groovy Gradle DSL (`.gradle` files), Kotlin/Compose

---

## Opt-in Inventory (do not re-audit — use this table)

| Module | Annotation FQN |
|--------|---------------|
| `app` | `androidx.compose.material.ExperimentalMaterialApi` |
| `zero-core` | `kotlinx.coroutines.ExperimentalCoroutinesApi` |
| `zero-core` | `androidx.compose.foundation.ExperimentalFoundationApi` |
| `zero-core` | `androidx.compose.animation.ExperimentalAnimationApi` |
| `zero-database` | `kotlinx.coroutines.ExperimentalCoroutinesApi` |
| `zero-ui` | `androidx.compose.material.ExperimentalMaterialApi` |

Note: `app/build.gradle` already has `optIn.add("kotlinx.coroutines.ExperimentalCoroutinesApi")` and `optIn.add("kotlinx.serialization.ExperimentalSerializationApi")` in its `kotlin { compilerOptions { } }` block — only `ExperimentalMaterialApi` needs to be added there.

---

### Task 1: Add `ExperimentalMaterialApi` opt-in to `app/build.gradle`

**Files:**
- Modify: `app/build.gradle`

The existing block inside `android { }` looks like this:

```groovy
kotlin {
    compilerOptions {
        optIn.add("kotlinx.coroutines.ExperimentalCoroutinesApi")
        optIn.add("kotlinx.serialization.ExperimentalSerializationApi")
    }
}
```

- [ ] **Step 1: Add the missing opt-in**

In `app/build.gradle`, inside the `android { kotlin { compilerOptions { } } }` block, add:

```groovy
optIn.add("androidx.compose.material.ExperimentalMaterialApi")
```

Full updated block:

```groovy
kotlin {
    compilerOptions {
        optIn.add("kotlinx.coroutines.ExperimentalCoroutinesApi")
        optIn.add("kotlinx.serialization.ExperimentalSerializationApi")
        optIn.add("androidx.compose.material.ExperimentalMaterialApi")
    }
}
```

- [ ] **Step 2: Remove `@OptIn` annotations and unused imports from `app` sources**

Files to edit (remove the annotated line and the import):

**`app/src/main/java/com/hluhovskyi/zero/activity/MainActivityViewProvider.kt` line 25:**
Remove: `@OptIn(ExperimentalMaterialApi::class)`
Remove import: `import androidx.compose.material.ExperimentalMaterialApi`

**`app/src/main/java/com/hluhovskyi/zero/activity/screens/MainActivityScreenComponent.kt` lines 129, 179:**
Remove both: `@OptIn(ExperimentalMaterialApi::class)`
Remove import: `import androidx.compose.material.ExperimentalMaterialApi`

**`app/src/main/java/com/hluhovskyi/zero/activity/screens/MainActivityScreenViewProvider.kt` line 50:**
Remove: `@OptIn(ExperimentalMaterialApi::class)`
Remove import: `import androidx.compose.material.ExperimentalMaterialApi`

- [ ] **Step 3: Commit**

```bash
git add app/build.gradle \
    app/src/main/java/com/hluhovskyi/zero/activity/MainActivityViewProvider.kt \
    app/src/main/java/com/hluhovskyi/zero/activity/screens/MainActivityScreenComponent.kt \
    app/src/main/java/com/hluhovskyi/zero/activity/screens/MainActivityScreenViewProvider.kt
git commit -m "build(app): hoist ExperimentalMaterialApi opt-in to build args"
```

---

### Task 2: Add opt-ins to `zero-ui/build.gradle`

**Files:**
- Modify: `zero-ui/build.gradle`

- [ ] **Step 1: Add `kotlin { compilerOptions { } }` block to `zero-ui/build.gradle`**

Inside the `android { }` block (after the existing `testOptions { }` block), add:

```groovy
kotlin {
    compilerOptions {
        optIn.add("androidx.compose.material.ExperimentalMaterialApi")
    }
}
```

- [ ] **Step 2: Remove `@OptIn` annotations and unused imports from `zero-ui` sources**

**`zero-ui/src/main/java/com/hluhovskyi/zero/ui/SegmentedToggle.kt` line 23:**
Remove: `@OptIn(ExperimentalMaterialApi::class)`
Remove import: `import androidx.compose.material.ExperimentalMaterialApi`

**`zero-ui/src/main/java/com/hluhovskyi/zero/ui/SelectorCard.kt` line 35:**
Remove: `@OptIn(ExperimentalMaterialApi::class)`
Remove import: `import androidx.compose.material.ExperimentalMaterialApi`

**`zero-ui/src/main/java/com/hluhovskyi/zero/ui/TextFieldDropdownMenu.kt` line 16:**
Remove: `@OptIn(ExperimentalMaterialApi::class)`
Remove import: `import androidx.compose.material.ExperimentalMaterialApi`

- [ ] **Step 3: Commit**

```bash
git add zero-ui/build.gradle \
    zero-ui/src/main/java/com/hluhovskyi/zero/ui/SegmentedToggle.kt \
    zero-ui/src/main/java/com/hluhovskyi/zero/ui/SelectorCard.kt \
    zero-ui/src/main/java/com/hluhovskyi/zero/ui/TextFieldDropdownMenu.kt
git commit -m "build(zero-ui): hoist ExperimentalMaterialApi opt-in to build args"
```

---

### Task 3: Add opt-ins to `zero-database/build.gradle`

**Files:**
- Modify: `zero-database/build.gradle`

- [ ] **Step 1: Add `kotlin { compilerOptions { } }` block to `zero-database/build.gradle`**

Inside the `android { }` block (after the existing `testOptions { }` block), add:

```groovy
kotlin {
    compilerOptions {
        optIn.add("kotlinx.coroutines.ExperimentalCoroutinesApi")
    }
}
```

- [ ] **Step 2: Remove `@OptIn` annotations and unused imports from `zero-database` sources**

**`zero-database/src/main/java/com/hluhovskyi/zero/categories/RoomCategoryRepository.kt` line 27:**
Remove: `@OptIn(ExperimentalCoroutinesApi::class)` (function-level annotation on `getAll`)
Remove import: `import kotlinx.coroutines.ExperimentalCoroutinesApi`

**`zero-database/src/main/java/com/hluhovskyi/zero/currencies/InUseCurrencyRepository.kt` line 21:**
Remove: `@OptIn(ExperimentalCoroutinesApi::class)` (class-level annotation)
Remove import: `import kotlinx.coroutines.ExperimentalCoroutinesApi`

**`zero-database/src/main/java/com/hluhovskyi/zero/transactions/RoomTransactionRepository.kt` line 37:**
Remove: `@OptIn(ExperimentalCoroutinesApi::class)` (function-level annotation)
Remove import: `import kotlinx.coroutines.ExperimentalCoroutinesApi`

**`zero-database/src/test/java/com/hluhovskyi/zero/transactions/RoomTransactionRepositoryPaginationTest.kt` line 29:**
Remove: `@OptIn(ExperimentalCoroutinesApi::class)` (class-level annotation)
Remove import: `import kotlinx.coroutines.ExperimentalCoroutinesApi`

- [ ] **Step 3: Commit**

```bash
git add zero-database/build.gradle \
    zero-database/src/main/java/com/hluhovskyi/zero/categories/RoomCategoryRepository.kt \
    zero-database/src/main/java/com/hluhovskyi/zero/currencies/InUseCurrencyRepository.kt \
    zero-database/src/main/java/com/hluhovskyi/zero/transactions/RoomTransactionRepository.kt \
    zero-database/src/test/java/com/hluhovskyi/zero/transactions/RoomTransactionRepositoryPaginationTest.kt
git commit -m "build(zero-database): hoist ExperimentalCoroutinesApi opt-in to build args"
```

---

### Task 4: Add opt-ins to `zero-core/build.gradle`

**Files:**
- Modify: `zero-core/build.gradle`

- [ ] **Step 1: Add `kotlin { compilerOptions { } }` block to `zero-core/build.gradle`**

Inside the `android { }` block (after the existing `testOptions { }` block), add:

```groovy
kotlin {
    compilerOptions {
        optIn.add("kotlinx.coroutines.ExperimentalCoroutinesApi")
        optIn.add("androidx.compose.foundation.ExperimentalFoundationApi")
        optIn.add("androidx.compose.animation.ExperimentalAnimationApi")
    }
}
```

- [ ] **Step 2: Remove `@OptIn` annotations and unused imports from `zero-core` main sources**

**`zero-core/src/main/java/com/hluhovskyi/zero/accounts/detail/DefaultAccountDetailSpendingUseCase.kt` line 22:**
Remove: `@OptIn(ExperimentalCoroutinesApi::class)`
Remove import: `import kotlinx.coroutines.ExperimentalCoroutinesApi`

**`zero-core/src/main/java/com/hluhovskyi/zero/categories/DefaultCategorySpendingUseCase.kt` lines 25, 33:**
Remove both: `@OptIn(ExperimentalCoroutinesApi::class)`
Remove import: `import kotlinx.coroutines.ExperimentalCoroutinesApi`

**`zero-core/src/main/java/com/hluhovskyi/zero/categories/picker/CategoryPickerViewProvider.kt` line 49:**
Remove: `@OptIn(ExperimentalFoundationApi::class)`
Remove import: `import androidx.compose.foundation.ExperimentalFoundationApi`

**`zero-core/src/main/java/com/hluhovskyi/zero/colors/ColorPickerViewProvider.kt` line 28:**
Remove: `@OptIn(ExperimentalFoundationApi::class)`
Remove import: `import androidx.compose.foundation.ExperimentalFoundationApi`

**`zero-core/src/main/java/com/hluhovskyi/zero/icons/IconAndColorPicker.kt` line 49:**
Remove: `@OptIn(ExperimentalFoundationApi::class)`
Remove import: `import androidx.compose.foundation.ExperimentalFoundationApi`

**`zero-core/src/main/java/com/hluhovskyi/zero/transactions/DefaultTransactionViewModel.kt` line 129:**
Remove: `@OptIn(ExperimentalCoroutinesApi::class)`
Remove import: `import kotlinx.coroutines.ExperimentalCoroutinesApi`

**`zero-core/src/main/java/com/hluhovskyi/zero/transactions/edit/TransactionEditViewProvider.kt` line 61:**
Remove: `@OptIn(ExperimentalAnimationApi::class)`
Remove import: `import androidx.compose.animation.ExperimentalAnimationApi`

**`zero-core/src/main/java/com/hluhovskyi/zero/transactions/TransactionViewProvider.kt` line 88:**
Remove: `@OptIn(ExperimentalFoundationApi::class)`
Remove import: `import androidx.compose.foundation.ExperimentalFoundationApi`

- [ ] **Step 3: Remove `@OptIn` annotations and unused imports from `zero-core` test sources**

**`zero-core/src/test/java/com/hluhovskyi/zero/accounts/detail/DefaultAccountDetailSpendingUseCaseTest.kt` line 27:**
Remove: `@OptIn(ExperimentalCoroutinesApi::class)`
Remove import: `import kotlinx.coroutines.ExperimentalCoroutinesApi`

**`zero-core/src/test/java/com/hluhovskyi/zero/accounts/detail/DefaultAccountDetailViewModelTest.kt` line 33:**
Remove: `@OptIn(ExperimentalCoroutinesApi::class)`
Remove import: `import kotlinx.coroutines.ExperimentalCoroutinesApi`

**`zero-core/src/test/java/com/hluhovskyi/zero/categories/DefaultCategoriesQueryUseCaseTest.kt` line 29:**
Remove: `@OptIn(ExperimentalCoroutinesApi::class)`
Remove import: `import kotlinx.coroutines.ExperimentalCoroutinesApi`

**`zero-core/src/test/java/com/hluhovskyi/zero/categories/detail/DefaultCategoryDetailViewModelTest.kt` line 36:**
Remove: `@OptIn(ExperimentalCoroutinesApi::class)`
Remove import: `import kotlinx.coroutines.ExperimentalCoroutinesApi`

**`zero-core/src/test/java/com/hluhovskyi/zero/imports/DefaultImportUseCaseTest.kt` line 36:**
Remove: `@OptIn(ExperimentalCoroutinesApi::class)`
Remove import: `import kotlinx.coroutines.ExperimentalCoroutinesApi`

**`zero-core/src/test/java/com/hluhovskyi/zero/imports/sourceselection/DefaultSourceSelectionViewModelTest.kt` line 16:**
Remove: `@OptIn(ExperimentalCoroutinesApi::class)`
Remove import: `import kotlinx.coroutines.ExperimentalCoroutinesApi`

**`zero-core/src/test/java/com/hluhovskyi/zero/transactions/DefaultTransactionViewModelTest.kt` line 50:**
Remove: `@OptIn(ExperimentalCoroutinesApi::class)`
Remove import: `import kotlinx.coroutines.ExperimentalCoroutinesApi`

- [ ] **Step 4: Commit**

```bash
git add zero-core/build.gradle \
    zero-core/src/main/java/com/hluhovskyi/zero/accounts/detail/DefaultAccountDetailSpendingUseCase.kt \
    zero-core/src/main/java/com/hluhovskyi/zero/categories/DefaultCategorySpendingUseCase.kt \
    zero-core/src/main/java/com/hluhovskyi/zero/categories/picker/CategoryPickerViewProvider.kt \
    zero-core/src/main/java/com/hluhovskyi/zero/colors/ColorPickerViewProvider.kt \
    zero-core/src/main/java/com/hluhovskyi/zero/icons/IconAndColorPicker.kt \
    zero-core/src/main/java/com/hluhovskyi/zero/transactions/DefaultTransactionViewModel.kt \
    zero-core/src/main/java/com/hluhovskyi/zero/transactions/edit/TransactionEditViewProvider.kt \
    zero-core/src/main/java/com/hluhovskyi/zero/transactions/TransactionViewProvider.kt \
    zero-core/src/test/java/com/hluhovskyi/zero/accounts/detail/DefaultAccountDetailSpendingUseCaseTest.kt \
    zero-core/src/test/java/com/hluhovskyi/zero/accounts/detail/DefaultAccountDetailViewModelTest.kt \
    zero-core/src/test/java/com/hluhovskyi/zero/categories/DefaultCategoriesQueryUseCaseTest.kt \
    zero-core/src/test/java/com/hluhovskyi/zero/categories/detail/DefaultCategoryDetailViewModelTest.kt \
    zero-core/src/test/java/com/hluhovskyi/zero/imports/DefaultImportUseCaseTest.kt \
    zero-core/src/test/java/com/hluhovskyi/zero/imports/sourceselection/DefaultSourceSelectionViewModelTest.kt \
    zero-core/src/test/java/com/hluhovskyi/zero/transactions/DefaultTransactionViewModelTest.kt
git commit -m "build(zero-core): hoist ExperimentalCoroutinesApi, ExperimentalFoundationApi, ExperimentalAnimationApi opt-ins to build args"
```

---

### Task 5: Verify — Build and lint

- [ ] **Step 1: Run unit tests**

```bash
./gradlew testDebugUnitTest 2>&1 | tail -20
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 2: Run lint**

```bash
./gradlew lintDebug 2>&1 | grep -E "error:|Error" | head -20
```

Expected: no errors output

- [ ] **Step 3: Verify no @OptIn remains in source**

```bash
grep -r "@OptIn" --include="*.kt" \
    app/src zero-core/src zero-database/src zero-ui/src
```

Expected: no output (zero matches)
