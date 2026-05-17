# FAB collapsed-default + animated expansion — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Eliminate the FAB extended-to-collapsed flicker that appears when switching between bottom-bar tabs, and animate the expand/collapse transition.

**Architecture:** For Accounts and Categories, flip the relevant `State` defaults so the FAB starts collapsed and only flips to expanded when the presenter confirms it. For Transactions, detect `isEmpty` transitions on the UI side (Compose `snapshotFlow + drop(1)`) and skip the initial emission so a cold tab-mount never expands. Refactor `ZeroFab` from an `if (expanded) Extended else Round` composable swap into a single `FloatingActionButton` whose inner `Row` reveals/hides text via `AnimatedVisibility` + `animateContentSize()`.

**Tech Stack:** Jetpack Compose (androidx.compose.material v1), Kotlin coroutines, JUnit.

**Spec:** [docs/superpowers/specs/2026-05-15-fab-collapsed-default-design.md](../specs/2026-05-15-fab-collapsed-default-design.md)

---

## File Structure

**Modified:**
- `zero-ui/src/main/java/com/hluhovskyi/zero/ui/ZeroFab.kt` — refactor to single FAB with animated row.
- `zero-core/src/main/java/com/hluhovskyi/zero/accounts/AccountViewModel.kt` — flip `hasAddedAccount` default.
- `zero-core/src/main/java/com/hluhovskyi/zero/categories/CategoryViewModel.kt` — flip `hasAddedCategory` default.
- `zero-core/src/main/java/com/hluhovskyi/zero/transactions/TransactionViewProvider.kt` — UI-side `isEmpty` transition detection for `fabExpanded`.

No new files. No ViewModel state additions.

---

## Task 1: Account FAB — flip default

**Files:**
- Modify: `zero-core/src/main/java/com/hluhovskyi/zero/accounts/AccountViewModel.kt:24`

- [ ] **Step 1: Change the `hasAddedAccount` default from `false` to `true`**

In `AccountViewModel.State`, change:
```kotlin
val hasAddedAccount: Boolean = false,
```
to:
```kotlin
val hasAddedAccount: Boolean = true,
```

**Why:** `AccountViewProvider` derives `fabExpanded = !state.hasAddedAccount`. With the old default, the FAB renders expanded for one frame on tab mount before the real value arrives. Defaulting to `true` keeps the FAB collapsed until the presenter overrides it.

- [ ] **Step 2: Build the module to verify no compile break**

Run: `./gradlew :zero-core:compileDebugKotlin 2>&1 | tail -10`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add zero-core/src/main/java/com/hluhovskyi/zero/accounts/AccountViewModel.kt
git commit -m "fix(accounts): default hasAddedAccount=true to keep FAB collapsed on mount"
git push
```

---

## Task 2: Category FAB — flip default

**Files:**
- Modify: `zero-core/src/main/java/com/hluhovskyi/zero/categories/CategoryViewModel.kt:21`

- [ ] **Step 1: Change the `hasAddedCategory` default from `false` to `true`**

In `CategoryViewModel.State`, change:
```kotlin
val hasAddedCategory: Boolean = false,
```
to:
```kotlin
val hasAddedCategory: Boolean = true,
```

- [ ] **Step 2: Build the module**

Run: `./gradlew :zero-core:compileDebugKotlin 2>&1 | tail -10`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add zero-core/src/main/java/com/hluhovskyi/zero/categories/CategoryViewModel.kt
git commit -m "fix(categories): default hasAddedCategory=true to keep FAB collapsed on mount"
git push
```

---

## Task 3: Transaction FAB — UI-side transition detection

**Files:**
- Modify: `zero-core/src/main/java/com/hluhovskyi/zero/transactions/TransactionViewProvider.kt:121-123` (and imports near the top)

- [ ] **Step 1: Add the required imports**

Add to the imports block (keep alphabetical order in the `androidx.compose.runtime.*` and `kotlinx.coroutines.flow.*` groups):

```kotlin
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.drop
```

Only add the imports that aren't already present.

- [ ] **Step 2: Replace the `fabExpanded` derivation**

Change:
```kotlin
val fabExpanded = state.transactions.isEmpty() &&
    state.searchQuery.isBlank() &&
    !state.activeFilter.isActive
```
to:
```kotlin
val isEmpty = state.transactions.isEmpty() &&
    state.searchQuery.isBlank() &&
    !state.activeFilter.isActive
var fabExpanded by remember { mutableStateOf(false) }
LaunchedEffect(Unit) {
    snapshotFlow { isEmpty }
        .drop(1)
        .collect { fabExpanded = it }
}
```

**Why:**
- `fabExpanded` starts as `false` → collapsed on first composition. No tab-mount flicker.
- `snapshotFlow { isEmpty }` observes the derivation across recompositions.
- `.drop(1)` skips the initial emission (the value at the moment the flow starts collecting), so the synthetic-empty default never expands the FAB.
- Subsequent transitions (e.g., user deletes their last transaction) flip `fabExpanded`, and the new animated `ZeroFab` smoothly transitions between states.

Trade-off accepted (per design spec): a cold mount onto a genuinely empty Transactions tab shows a collapsed FAB. The empty-state discovery hint is provided by the Welcome screen (`HomeViewProvider` routes there when `isNewUser=true`).

- [ ] **Step 3: Build**

Run: `./gradlew :zero-core:compileDebugKotlin 2>&1 | tail -10`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add zero-core/src/main/java/com/hluhovskyi/zero/transactions/TransactionViewProvider.kt
git commit -m "fix(transactions): expand FAB only on isEmpty transition, not on cold mount"
git push
```

---

## Task 4: ZeroFab — animated expansion

**Files:**
- Modify: `zero-ui/src/main/java/com/hluhovskyi/zero/ui/ZeroFab.kt` (entire file)

- [ ] **Step 1: Replace the file contents**

```kotlin
package com.hluhovskyi.zero.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.FloatingActionButton
import androidx.compose.material.FloatingActionButtonDefaults
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.hluhovskyi.zero.ui.theme.PrimaryContainer

@Composable
fun ZeroFab(
    onClick: () -> Unit,
    icon: ImageVector,
    contentDescription: String,
    expanded: Boolean,
    text: String,
    modifier: Modifier = Modifier,
) {
    val elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 6.dp)
    FloatingActionButton(
        modifier = modifier,
        onClick = onClick,
        backgroundColor = PrimaryContainer,
        contentColor = Color.White,
        elevation = elevation,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .animateContentSize()
                .padding(horizontal = if (expanded) 20.dp else 0.dp),
        ) {
            Icon(icon, contentDescription = contentDescription)
            AnimatedVisibility(visible = expanded) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(text)
                }
            }
        }
    }
}
```

**Why:**
- Single underlying composable (`FloatingActionButton`) means transitions never trigger a full remount — the visual swap that caused the perceived flicker.
- `animateContentSize()` animates the row's width as the text appears/disappears.
- `AnimatedVisibility` defaults to `fadeIn() + expandIn()` / `fadeOut() + shrinkOut()`, which is the desired horizontal text reveal.
- `padding(horizontal = ...)` toggles between extended-FAB padding (20dp matches Material's `ExtendedFloatingActionButton` content padding) and zero padding so the round-FAB content stays centered.

- [ ] **Step 2: Build the UI module**

Run: `./gradlew :zero-ui:compileDebugKotlin 2>&1 | tail -10`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Build the whole app**

Run: `./gradlew :app:assembleDebug 2>&1 | tail -10`
Expected: BUILD SUCCESSFUL. Catches any caller that depended on the old `ExtendedFloatingActionButton` import path.

- [ ] **Step 4: Commit**

```bash
git add zero-ui/src/main/java/com/hluhovskyi/zero/ui/ZeroFab.kt
git commit -m "feat(ui): animate ZeroFab expansion instead of swapping composables"
git push
```

---

## Task 5: Verify — tests, lint, UI inspector

- [ ] **Step 1: Run full unit-test suite**

Run: `./gradlew testDebugUnitTest 2>&1 | tail -20`
Expected: BUILD SUCCESSFUL. If any test fails because a `State()` constructor expectation changed, update the test to match the new defaults.

- [ ] **Step 2: Run lint**

Run: `./gradlew lintDebug 2>&1 | grep -E "error:|Error" | head -20`
Expected: No new errors.

- [ ] **Step 3: Install on emulator**

Run: `./scripts/install-app.sh`
Expected: App launches.

- [ ] **Step 4: UI-verify the fix**

Invoke `zero-project:android-ui-inspector` and:
1. Seed the app with at least one account, one category, and one transaction.
2. Switch tabs Transactions ↔ Accounts ↔ Categories ↔ Transactions repeatedly. Confirm the FAB stays collapsed (round) on every entry — no extended-then-collapsed flash.
3. On the Welcome flow (fresh install or `isNewUser=true`), confirm the static extended FAB still renders correctly.
4. From the Accounts tab on an empty state (no accounts), confirm the FAB expands smoothly via animation.
5. Delete the last account / last category and confirm the FAB animates expanded.

Capture a screenshot of each tab in collapsed state for the PR description.

---

## Self-Review

- **Spec coverage:** Part 1 (collapsed default) → Tasks 1, 2, 3. Part 2 (animated transition) → Task 4. Verification → Task 5. ✓
- **Placeholders:** None — every step has the exact code or command. ✓
- **Type consistency:** No new types introduced. Existing fields used unchanged. ✓
- **No churn:** Six static `expanded = true` callers (Welcome / TransactionEdit / AccountEdit / AccountDetail / CategoriesEdit / CategoryDetail) are untouched. ✓
