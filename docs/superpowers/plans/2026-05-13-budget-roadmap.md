# Budget Implementation — Roadmap

Branch: `worktree-budget-implementation`
Design: `ui_kits/zero/BudgetScreen.jsx` + `Components.jsx` from Claude Design archive `ZWcjTa2_AydRGtUCPjE0Sg`.

This is the index across multiple PRs. Each phase is its own plan file and ships an independently mergeable PR.

## Goal

Let the user set a monthly spending limit per expense category, see how the month's spend tracks against each limit, and recover when a category goes over (reallocate or increase). Ship in 6 phases so each PR is reviewable and the foundation lands before any flow-heavy UI.

## Phase Index & Status Tracker

Update the **Status** column when a phase merges. Acceptable values:
`☐ Pending` · `▶ In progress (PR #N)` · `✅ Merged (PR #N)`.

| # | Plan | Ships | Status |
|---|------|-------|--------|
| 1 | [Foundation](2026-05-13-budget-phase-1-foundation.md) | `BudgetEntity` + Room + repository + sync + `BudgetComponent` skeleton + Budget tab destination + empty-state UI (matches design's no-budget month) | ▶ In progress (PR #201) |
| 2 | [Single-category edit](2026-05-13-budget-phase-2-single-edit.md) | `BudgetEditComponent` bottom sheet — NumPad + "Last month" chip → persist one budget | ▶ In progress |
| 3 | [Bulk setup + copy-from-last-month](2026-05-13-budget-phase-3-bulk-setup.md) | `BudgetBulkSetupComponent` full-screen flow; "Copy from {prev}" cards on screen and inside flow; inline numpad auto-advance | ☐ Pending |
| 4 | [Summary + progress](2026-05-13-budget-phase-4-summary-progress.md) | `SummaryBar` donut, `BudgetCard` progress bar, sort order (over → in-progress by % → unset) | ▶ In progress (PR #234) |
| 5 | [Over-budget actions](2026-05-13-budget-phase-5-over-budget-actions.md) | `BudgetOverComponent` — Choice → Reallocate sub-view → Increase sub-view | ☐ Pending |
| 6 | [Ordering + remove](2026-05-13-budget-phase-6-ordering-remove.md) | Sort unset categories by 3-month avg spend; long-press → remove for this month | ☐ Pending |
| 7 | _Notification dot on Budget tab — when over budget_ | Not yet planned | ☐ Pending |
| 8 | _Income budgets_ | Not yet planned (no design) | ☐ Pending |

**Out of scope of this roadmap** (separate plans when designed):
- **Phase 7 — Notification dot on Budget tab** when any category is over budget. Requires extending `BottomBarViewModel.Item` with `hasAlert`, plus a new `BudgetOverAnyUseCase`. Plan when prioritized.
- **Phase 8 — Income budgets.** The schema is income-ready (Phase 1 ships `BudgetType` column), but UI is not designed. Plan when designs land.

## Cross-Cutting Decisions

### Cadence — monthly v1, schema flexible

Schema stores `periodStart: LocalDate` and `periodEnd: LocalDate` per budget row, not a `monthKey: String`. V1 always passes first-of-month / last-of-month, but the column shape lets weekly, yearly, or custom ranges land later with no migration. The repository exposes `Criteria.ForPeriod(from, to)` rather than `Criteria.ForMonth(year, month)` so the API is cadence-agnostic from day 1.

### Type — `BudgetType.EXPENSE` only, schema-ready for income

`BudgetEntity.type: String` with values `EXPENSE` / `INCOME`. Phase 1 ships the column with default `EXPENSE` and never writes `INCOME`. Phase 8 (when designed) adds the UI without a migration.

### Spend calculation — reuse `CategorySpendingUseCase`

A category's "spent" for a period is the existing `CategorySpendingUseCase` aggregation: expense transactions only, multi-currency converted to primary via `CurrencyConvertUseCase`. Same logic the Category detail screen already uses. No new aggregation path.

### Reallocate — no audit log

Reallocate is `source.budgeted -= X; target.budgeted += X` in one repository call. No `BudgetReallocation` table, no history. Two row updates, sync handles via normal LWW.

### Category ordering for "unset" rows — trailing 3-month average

`BudgetCategoryOrderingUseCase` (Phase 6) ranks categories by avg spend over the previous 3 calendar months. Set rows in Phase 4 sort by `spent / budgeted` desc; unset rows sort by 3-month-avg desc. Cold-start users with <3 months data fall back to alphabetical.

### Multi-currency

Budget amounts are stored in the user's **primary currency** at write time. We never store FX. When primary currency changes, existing budget rows remain in the old currency value (no auto-conversion) — the user re-sets them. This avoids drift between budget display and the converted "spent" total.

## Data Model

```
BudgetEntity(
  id: Id.Known                 // PRIMARY KEY
  userId: Id.Known              // INDEX
  categoryId: Id.Known          // INDEX
  type: String = "EXPENSE"      // EXPENSE / INCOME
  amount: BigDecimal            // in primary currency
  periodStart: LocalDate        // inclusive
  periodEnd: LocalDate          // inclusive
  creationDateTime: LocalDateTime
  updatedDateTime: LocalDateTime
  deletedAt: LocalDateTime?     // soft-delete, like CategoryEntity
)
```

Uniqueness invariant (enforced by repository, not DB): at most one alive `BudgetEntity` per `(userId, categoryId, periodStart, periodEnd, type)`. Repository upserts replace the existing row.

## Components Map

Each phase introduces at most one new feature component, named to match the `scaffold-feature` skill's conventions:

| Component | Module path | Phase | Lifecycle |
|-----------|-------------|-------|-----------|
| `BudgetComponent` | `zero-core/.../budget/` | 1 (skeleton) → 4 (summary+cards) → 6 (ordering+remove) | Tab destination, persistent |
| `BudgetEditComponent` | `zero-core/.../budget/edit/` | 2 | Bottom sheet — opens for single category |
| `BudgetBulkSetupComponent` | `zero-core/.../budget/bulksetup/` | 3 | Full-screen modal — opens for "Create budget" |
| `BudgetOverComponent` | `zero-core/.../budget/over/` | 5 | Bottom sheet — opens when user taps Reallocate or Increase on a card. Internal nav between Choice → Reallocate → Increase modes via state. |

`BudgetRepository` (zero-api + zero-database) and `BudgetQueryUseCase` (zero-api + zero-core) are shared across all phases, introduced in Phase 1.

## Session Handoff

This roadmap is committed in the planning session. Each phase plan is self-contained — a future session reads only its phase file plus this roadmap and executes. No phase reads another phase's plan.

When starting Phase N execution:
1. Re-enter the worktree (or create a fresh phase branch from master).
2. Open this roadmap + `2026-05-13-budget-phase-N-*.md`.
3. Invoke `superpowers:subagent-driven-development` and execute.
