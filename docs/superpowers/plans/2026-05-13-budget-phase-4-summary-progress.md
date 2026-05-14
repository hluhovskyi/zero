# Budget Phase 4 — Summary Bar + Per-Category Progress

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

Roadmap: [budget-roadmap.md](2026-05-13-budget-roadmap.md). Prereq: Phases 1–3 merged.

**Goal:** Two pieces of visualization from `BudgetScreen.jsx`:
1. **`SummaryBar`** at the top of the populated Budget screen (lines 718–787): donut chart showing overall %, three columns (Spent / Remaining / Budget), and a status pill ("On track" or "N categories over budget").
2. **`BudgetCard`** per category replacing Phase 1's "dashed Set limit" rows when a budget is set (lines 618–700): category identity row + progress bar with traffic-light coloring + status text ("$X remaining" / "$X over limit").

After this phase the Budget screen reads as a real dashboard. List sorting also flips into the design's order: over-budget first, then in-progress by spend% descending, then unset rows last.

**Architecture:** Pure ViewProvider work — both composables consume existing `state.budgeted` (already plumbed through Phase 1's `BudgetQueryUseCase`). No new ViewModels or use cases; just derived state and rendering.

---

## Files

### New — zero-core
- `zero-core/.../budget/SummaryBar.kt` — `@Composable internal fun SummaryBar(state: BudgetSummary, modifier: Modifier = Modifier)`. Pulls just `state.budgeted` to compute totals.
- `zero-core/.../budget/BudgetCard.kt` — `@Composable internal fun BudgetCard(row: BudgetQueryUseCase.Budgeted, onTap: () -> Unit, modifier: Modifier = Modifier)`. (Phase 5 will add `onReallocate` / `onIncrease` and `onLongPress` parameters; this phase leaves them off.)
- `zero-core/.../budget/BudgetSummary.kt` — derived data class (see Task 1).

### Modified — zero-core
- `BudgetViewModel.kt` — add derived `BudgetSummary` to state
- `BudgetViewProvider.kt` — replace Phase 1's "dashed row only" list with conditional `BudgetCard` for set rows, retain unset row composable; render `SummaryBar` above list when `state.budgeted.any { it.budgetId != null }`

---

## Task 1: `BudgetSummary` derived model

**Files:**
- Create: `zero-core/.../budget/BudgetSummary.kt`

- [ ] **Step 1:** Plain data class — no need for an interface, used only by `SummaryBar` composable.

```kotlin
internal data class BudgetSummary(
    val totalBudgeted: Amount,
    val totalSpent: Amount,
    val overCount: Int,
    val overallPct: Float,  // 0f..1f; clipped at 1f
    val isOver: Boolean,
)

internal fun List<BudgetQueryUseCase.Budgeted>.toSummary(): BudgetSummary {
    val active = filter { it.budgetId != null }
    val totalBudgeted = active.fold(Amount.zero()) { acc, b -> acc + b.budgeted }
    val totalSpent = active.fold(Amount.zero()) { acc, b -> acc + b.spent }
    val overCount = active.count { it.spent > it.budgeted }
    val pct = if (totalBudgeted > Amount.zero()) {
        (totalSpent.value.toDouble() / totalBudgeted.value.toDouble()).toFloat().coerceIn(0f, 1f)
    } else 0f
    return BudgetSummary(totalBudgeted, totalSpent, overCount, pct, totalSpent > totalBudgeted)
}
```

- [ ] **Step 2: Unit test** — `BudgetSummaryTest.kt` covering: empty list → all zeros; all under → `isOver=false`; one over → `overCount=1, isOver` reflects total.

- [ ] **Step 3: Commit**

```bash
git commit -m "feat(budget): BudgetSummary derivation"
```

---

## Task 2: Sort `state.budgeted` by phase 4 order

**Files:**
- Modify: `DefaultBudgetViewModel.kt`

Design (lines 932–937):
```kotlin
val sorted = listOf(
    // 1. over-budget (set + spent > budgeted), keep relative order
    *active.filter { it.spent > it.budgeted },
    // 2. in-progress (set + spent <= budgeted), sort by pct desc
    *active.filter { it.spent <= it.budgeted }.sortedByDescending { (it.spent / it.budgeted).floatValue() },
    // 3. unset (Phase 6 will sort these by 3-month-avg; Phase 4 leaves them in repo order)
    *unset,
)
```

- [ ] **Step 1: Add sort logic** in the `collectLatest` block of `attach()` before emitting state.
- [ ] **Step 2: Tests** — `DefaultBudgetViewModelSortTest.kt`: 3 over, 2 in-progress at different pcts, 2 unset → assert exact order.
- [ ] **Step 3: Commit**

```bash
git commit -m "feat(budget): sort budget rows over → in-progress (pct desc) → unset"
```

---

## Task 3: `SummaryBar` composable

**Files:**
- Create: `zero-core/.../budget/SummaryBar.kt`

Layout (lines 718–787):

```
Row (background = Color(0xFF1A2E52), radius 20dp, padding 18/20, gap 18):
  // Donut
  Box (size 68dp):
    Canvas:
      drawCircle(radius=26dp, stroke=7dp, color=rgba(white, 0.08))           // track
      drawArc(strokeColor=fillColor, sweep=360*overallPct, startAngle=-90)   // fill
    // Center text
    Column (center):
      Text("${(overallPct*100).roundToInt()}%", 15sp/Bold, rgba(white, 0.92))
      Text("USED", 9sp/SemiBold uppercase, rgba(white, 0.45), letterSpacing 0.05em)

  // Numbers + status pill
  Column (weight 1):
    Row (spaceBetween, marginBottom 8dp):
      Column { Text("SPENT", 10sp/SemiBold uppercase, rgba(white,0.45)); Text(fmtAmt(totalSpent), 17sp/ExtraBold, rgba(white,0.92)) }
      Column (centered) { Text("REMAINING", ...); Text(if (isOver) "-${fmtAmt(abs(diff))}" else fmtAmt(diff), 17sp/ExtraBold, if (isOver) "#FF8A65" else "#5DDBA8") }
      Column (end) { Text("BUDGET", ...); Text(fmtAmt(totalBudgeted), 17sp/Bold, rgba(white,0.45)) }
    // Status pill
    StatusPill(overCount)
```

`fillColor` selection (design line 729):
- `overallPct > 1.0` (impossible since clipped, but using `isOver` instead): `#FF8A65`
- `overallPct > 0.85`: `#FFB74D`
- else: `#5DDBA8`

`StatusPill`:
- If `overCount > 0`: background `rgba(255,100,30,0.18)`, icon = warning triangle tint `#FF8A65`, text "{N} {category|categories} over budget"
- Else: background `rgba(93,219,168,0.15)`, icon = check-circle tint `#5DDBA8`, text "On track this month"

- [ ] **Step 1: Implement `SummaryBar`**
- [ ] **Step 2: Implement donut via `Canvas`** — use `drawCircle` (track) and `drawArc` (fill). `strokeCap = Stroke.Cap.Round`.
- [ ] **Step 3: Commit**

```bash
git commit -m "feat(budget): SummaryBar donut + status pill"
```

---

## Task 4: `BudgetCard` composable

**Files:**
- Create: `zero-core/.../budget/BudgetCard.kt`

Layout (lines 632–700):

```
Column (background = if isOver "#FFF8F6" else "#FFFFFF", radius 18, padding 16/16/14, marginBottom 10, border 1.5dp if isOver Error.copy(alpha=0.2) else transparent, clickable -> onTap):
  // Top row
  Row (alignItems centerVert, gap 12, marginBottom 12):
    CategoryIcon(size=38, primary=colorScheme.primary, bg=colorScheme.background)
    Column (weight 1):
      Row (spaceBetween, baseline):
        Text(name, 15sp/Bold, OnSurface)
        Row (gap 5):
          Text(fmtAmt(spent), 15sp/ExtraBold, if isOver Error else Primary)
          Icon(Edit, 13dp, OutlineVariant)  // visual affordance only
      Row (spaceBetween, marginTop 2):
        Text(statusText(), 12sp/SemiBold, statusColor())
        Text("of ${fmtAmt(budgeted)}", 12sp/Normal, OnSurfaceVariant)

  // Progress bar
  Box (height 8dp, background = SurfaceContainer, radius 4):
    // End marker (tiny vertical line at the right edge representing the limit)
    Box (right=0, top=-3dp, width 2dp, height 14dp, background OutlineVariant, radius 1, zIndex 2)
    // Fill
    Box (height fillMaxHeight, width fillMaxWidth.fraction(min(pct, 1f)), background = barColor, radius 4):
      if (isOver):
        // Marker dot at right edge of fill bar
        Box(right=-2, top=-2, size 12dp, radius circle, background Error, border 3dp around in card-bg color)
```

`statusText()` logic:
- `isOver` → `"{fmtAmt(spent - budgeted)} over limit"`
- `pct > 0.85` → `"{fmtAmt(budgeted - spent)} left — almost there"`
- else → `"{fmtAmt(budgeted - spent)} remaining"`

`statusColor()`:
- `isOver` → `Error`
- `pct > 0.85` → `#E65100` (orange)
- else → `OnSurfaceVariant`

`barColor()` (line 625):
- `isOver` → `Error`
- `pct > 0.85` → `#E65100`
- `pct > 0.65` → `#F9A825`
- else → `colorScheme.primary`

- [ ] **Step 1: Implement `BudgetCard`**
- [ ] **Step 2: Animated bar fill** — `animateFloatAsState` on `pct`, target value, 600ms easing cubic-bezier(0.4, 0, 0.2, 1). Initial value 0 so the bar grows on first composition (design line 1244–1250's `barGrow` animation).
- [ ] **Step 3: Commit**

```bash
git commit -m "feat(budget): BudgetCard with progress bar and traffic-light coloring"
```

---

## Task 5: Wire into `BudgetViewProvider`

**Files:**
- Modify: `BudgetViewProvider.kt`

- [ ] **Step 1: Add `SummaryBar` above the list** — render only when `state.budgeted.any { it.budgetId != null }`. Replaces the Phase 1 empty-state callout for the populated case; the dark callout stays for the all-unset case.

- [ ] **Step 2: Section label switching** — `"Categories"` when populated, `"Set spending limits"` when empty (line 1034–1036).

- [ ] **Step 3: Conditional row rendering** — for each row in `state.budgeted`:
  - `budgetId != null` → `BudgetCard(row, onTap = { perform(TapCategory(row.categoryId)) })`
  - `budgetId == null` → existing Phase 1 dashed-border "Set limit" row

- [ ] **Step 4: Commit**

```bash
git commit -m "feat(budget): integrate SummaryBar + BudgetCard into Budget screen"
```

---

## Task 6: Manual verification + PR

- [ ] **Step 1:** Open Budget screen with several budgets, mixed under/over. Confirm:
  - Donut shows correct overall %.
  - Spent / Remaining / Budget triple shows correct amounts; Remaining is green when under, orange and prefixed "-" when over.
  - Status pill toggles between "On track" and "N over budget".
  - Over-budget rows appear at the top, then in-progress sorted by pct desc, then unset rows at the bottom.
  - Tapping any row still routes to inline numpad (from Phase 3) or `BudgetEditComponent` (Phase 2 + Phase 6 long-press) — exact routing depends on Phase 3's decision.
- [ ] **Step 2: UI inspector** confirms layout bounds.
- [ ] **Step 3: PR**

Update Phase 4 in [roadmap status tracker](2026-05-13-budget-roadmap.md#phase-index--status-tracker).

---

## Self-Review

- [ ] User goals covered: #7 (summary), #8 (progress).
- [ ] No new use cases — all derivation from existing state.
- [ ] Sort matches design exactly (over → in-progress by pct → unset).
- [ ] Bar color stages match design (primary → yellow → orange → red).
