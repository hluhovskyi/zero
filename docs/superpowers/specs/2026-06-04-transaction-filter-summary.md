# Transaction Filter Summary — Spec

## Goal

When the transaction list is narrowed by a search query **or** an active filter, show a
navy summary card at the top of the list aggregating the visible transactions. The card's
three stat columns adapt to what's present (mixed / expenses-only / income-only / none),
per the design's `FilterSummaryCard`.

Design source: `ui_kits/zero/index.html` → `FilterSummaryCard` + `TransactionsScreen`.

## When it shows

- Visible when `searchQuery.isNotBlank() || activeFilter.isActive`, **and** at least one
  transaction is resolved. Hidden otherwise (clean list = no card).
- Search with zero results already shows the existing empty state — card stays hidden.

## Card content

Header row: `{count} transaction(s)` (left) + date span (right, e.g. `Apr 5 – Apr 17`;
single day shows one date). `count` counts all visible transactions incl. transfers.

Three columns, adaptive — amounts are converted to the **primary currency** (consistent
with the per-date `Item.Summary` totals). Money stats exclude transfers.

| Case (income? expense?) | Col 1 | Col 2 | Col 3 |
|---|---|---|---|
| both | **Net** (+/– green/orange) | Out (–) | In (+ green) |
| expense only | **Spent** (–) | Avg | Largest |
| income only | **Received** (+ green) | Avg | Largest |
| neither (transfers only) | Net `$0` | Out `—` | In `—` |

- `Net = totalIn − totalOut`; `Avg = (totalIn + totalOut) / moneyCount`;
  `Largest = max single income/expense amount`.
- "neither" is the **no-income-and-no-expense** branch the user explicitly asked to keep.
  Expenses-only (Spent/Avg/Largest) is the **no-income** branch.

## Visual

Reuse the Budget `SummaryBar` navy island palette exactly (`zero-core/budget/SummaryBar.kt`):
`SummaryBg #1A2E52`, `GreenAccent #5DDBA8`, `RedAccent #FF8A65`, bright/dim white text.
Card: radius 12dp, padding 13×16dp, scrolls as the first list item. Column labels are
10sp uppercase caps; values 17sp ExtraBold. Sign prefixes (+/–) are View presentation.

## Types (no new UseCase — mirrors the existing in-VM per-date summary)

`TransactionViewModel.FilterSummary` (new), carried on `State.filterSummary: FilterSummary?`:

```
data class FilterSummary(
    count: Int,
    dateSpan: DateSpan?,          // null when count == 0 (never emitted then)
    currencySymbol: String,
    columns: List<Column>,        // always 3
)
  Column(label: Label, amount: Amount?, emphasis: Emphasis)   // amount null → "—"
  DateSpan(start: LocalDate, end: LocalDate)
  enum Label { Net, Out, In, Spent, Avg, Largest, Received }
  enum Emphasis { Positive, Negative, Neutral, Faint }
```

ViewModel pre-computes the variant (which columns, which emphasis); the View maps
`Label → stringResource`, `Emphasis → Color`, and derives the +/– sign from the label.
This honors the UseCase/ViewModel/View split (no formatters or Compose colors in the VM,
no aggregation in the View).

## Computation (DefaultTransactionViewModel)

Inside the existing `combine` block: capture the resolved `List<Item.Transaction>` before
grouping, and when the show-condition holds compute `FilterSummary` from it using a shared
`primaryAmount()` helper. Refactor the per-date fold's expense/income branches to call the
same helper (removes the existing 2× inline conversion duplication).
