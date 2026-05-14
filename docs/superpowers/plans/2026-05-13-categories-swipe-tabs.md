# Categories Screen — Swipe Between Tabs

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development to execute. Steps use checkbox (`- [ ]`) syntax.

**Goal:** Make the Expense / Income tabs on the categories screen swipeable. Tapping a `SegmentedToggle` segment still switches; in addition, a horizontal swipe across the list area moves between tabs with a smooth page transition.

**Note on Transfer:** The categories screen has only Expense and Income tabs — there is no `CategoryType.TRANSFER` (transfers don't have categories). The user's request mentioned "expense/income/transfer", but Transfer doesn't apply to this surface; the swipe is between the existing two tabs.

**Architecture:** Wrap the per-tab list area in `HorizontalPager` (Compose Foundation 1.7.8 — `ExperimentalFoundationApi` is already opted in). The view model exposes data for **both** tabs in parallel (`categoriesByType` map) so swiping shows the correct content on each page rather than the current-tab content sliding under both halves.

---

## File Map

| File | Change |
|------|--------|
| `zero-core/…/categories/CategoryViewModel.kt` | Replace `categories` / `grandTotal` with per-type maps |
| `zero-core/…/categories/DefaultCategoryViewModel.kt` | Emit data for all tabs; drop selectedTab filtering |
| `zero-core/…/categories/CategoryViewProvider.kt` | Use `HorizontalPager`; sync with `selectedTab` |

---

### Task 1: View-model state — expose data per tab

**Files:**
- Modify: `zero-core/src/main/java/com/hluhovskyi/zero/categories/CategoryViewModel.kt`
- Modify: `zero-core/src/main/java/com/hluhovskyi/zero/categories/DefaultCategoryViewModel.kt`

- [ ] **`CategoryViewModel.State` — replace single-tab fields with maps**

  Replace:
  ```kotlin
  val categories: List<CategoryItem> = emptyList(),
  val grandTotal: Amount = Amount.zero(),
  ```
  with:
  ```kotlin
  val categoriesByType: Map<CategoryType, List<CategoryItem>> = emptyMap(),
  val grandTotalByType: Map<CategoryType, Amount> = emptyMap(),
  ```

  Helper extensions inside the interface (default-implemented) or top-level convenience accessors aren't needed — view code reads the maps directly.

- [ ] **`DefaultCategoryViewModel.attach()` — compute per-tab data**

  In the existing `combine(...)`, drop `mutableState.map { it.selectedTab }` from the inputs (it no longer filters). For each `CategoryType` (`EXPENSE`, `INCOME`) build the sorted active-then-inactive list using the same logic that currently runs once, and assemble `categoriesByType` and `grandTotalByType` maps. Emit both via `mutableState.update`.

  Keep `Action.SelectTab` behavior unchanged — it still updates `selectedTab`. The selected tab now drives only which pager page is visible, not what data is computed.

- [ ] **Commit**
  ```bash
  git add zero-core/src/main/java/com/hluhovskyi/zero/categories/CategoryViewModel.kt \
          zero-core/src/main/java/com/hluhovskyi/zero/categories/DefaultCategoryViewModel.kt
  git commit -m "feat(categories): expose per-tab data from view model"
  ```

---

### Task 2: View — `HorizontalPager` for swipe

**Files:**
- Modify: `zero-core/src/main/java/com/hluhovskyi/zero/categories/CategoryViewProvider.kt`

- [ ] **Use `HorizontalPager` for the list area**

  - Compute `val tabs = listOf(CategoryType.EXPENSE, CategoryType.INCOME)` once.
  - Create `val pagerState = rememberPagerState(initialPage = 0) { tabs.size }`.
  - `LaunchedEffect(state.selectedTab)` → if `pagerState.currentPage != tabs.indexOf(state.selectedTab)`, call `pagerState.animateScrollToPage(...)`.
  - `LaunchedEffect(pagerState.currentPage)` → if `tabs[pagerState.currentPage] != state.selectedTab`, dispatch `Action.SelectTab(tabs[pagerState.currentPage])`.

- [ ] **Restructure layout**

  The title and `SegmentedToggle` stay in a fixed top area (above the pager) so swipe doesn't move the header. The pager then occupies the remaining vertical space. Each page renders its own `LazyColumn` using `state.categoriesByType[tabs[page]] ?: emptyList()` and `state.grandTotalByType[tabs[page]] ?: Amount.zero()` (the same active / inactive partition + cards already used today, factored into a `@Composable CategoryPage(...)`).

  The FAB stays in the outer `Box` (overlays the pager) and continues to use `state.selectedTab` for `onAddCategory.onAdd(...)`.

- [ ] **Factor the per-tab list into a helper composable**

  Extract the current `LazyColumn` body (active items, inactive section, the inactive items) into `@Composable private fun CategoryPage(active, inactive, ...)`. The existing `ActiveCategoryCard` / `InactiveCategoryCard` composables are untouched.

- [ ] **Commit**
  ```bash
  git add zero-core/src/main/java/com/hluhovskyi/zero/categories/CategoryViewProvider.kt
  git commit -m "feat(categories): swipe between expense/income tabs"
  ```

---

## Verification

- [ ] `./gradlew testDebugUnitTest 2>&1 | tail -20` — green.
- [ ] `./gradlew lintDebug 2>&1 | grep -E "error:|Error" | head -20` — no errors.
- [ ] `zero-project:android-ui-inspector`:
  - Open the Categories screen.
  - Swipe right-to-left → switches from Expense to Income (verify by checking visible category names + `SegmentedToggle` selection state).
  - Swipe left-to-right → switches back to Expense.
  - Tap the Income segment → page animates to Income.
  - Tap Expense → page animates back. FAB action still creates a category for the active tab.

---

## Execution Handoff

Use `superpowers:subagent-driven-development` to execute.
