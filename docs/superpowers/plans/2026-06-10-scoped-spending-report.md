# Scoped Spending report — implementation plan

Design: `ui_kits/zero/Analytics Exploration.html` → screens **2 (Scoped Spending report · inherits
the filter)** and **2b (Multi · 3 categories × 2 accounts, By category / account)**. Entry point:
the existing filter-summary card on the Transactions screen.

## Goal

Tapping **"See breakdown for this filter"** on the Transactions `FilterSummaryCard` opens a new
full-screen **Spending breakdown** report that *inherits the active `TransactionFilter`*. The report
shows the scoped total, the filter as read-only context chips, and a breakdown of the scoped spend —
**By category** always, with a **By category / By account** `SegmentedToggle** when the scoped
transactions span 2+ accounts (the design's "extra dimension earns a By-account split" rule).

Out of scope (note in PR): the single-account daily-spend strip (screen 2's extra), search-only
breakdown, per-row drill-down navigation, the standalone Analytics hub/tab.

## What already exists (reuse, don't rebuild)

- **Entry point** — `transactions/FilterSummaryCard.kt` + `OnShowBreakdownHandler` (currently
  `Noop`). The card is rendered in `TransactionViewProvider` with `state.activeFilter` in scope.
- **Scoped query** — `TransactionRepository.Criteria.Filtered(from,to,type,categoryIds,accountIds)`
  returns the scoped `List<Transaction>`. `TransactionFilter.toFilteredCriteria(today)` already maps
  filter→criteria (private in `DefaultTransactionViewModel`; replicate the small mapping).
- **Aggregation reference** — `DefaultCategorySpendingUseCase.aggregate` /
  `DefaultTransactionViewModel.computeFilterSummary`: convert each tx to primary via
  `CurrencyConvertUseCase.convertToPrimary`, group + sum + count.
- **UI** — `zero-ui`: `DetailTopBar(title,onBack,trailing)`, `DonutChart(data,content)`,
  `SegmentedToggle`, `CategoryIcon`, `ZeroTheme.colors`, `UiColorScheme`/`toUi()`. Colors stay
  domain `ColorScheme` in the VM (categories + accounts both carry `colorId` → `schemeForOrGrey`).
- **Structural template** — `categories/detail/CategoryDetail{Component,ViewModel,ViewProvider}`:
  a navigated detail screen with an `@BindsInstance` arg + `OnBackHandler` + a UI-shape ViewModel.

## New feature — package `com.hluhovskyi.zero.transactions.breakdown`

Model after the `CategoryDetail` trio. No nested `TransactionComponent` (the report is charts only).

### `BreakdownViewModel.kt` (interface) — UI shape only
- `Action`: `object Back`, `data class SelectDimension(val dimension: Dimension)`.
- `enum class Dimension { Category, Account }`.
- `data class State(`
  - `totalAmount: Amount = zero`, `currencySymbol: String = ""`, `transactionCount: Int = 0`,
  - `context: Context` — nested substate (per `feedback_group_view_state_into_substates`):
    `periodLabel: String?`, `typeLabel: String?`, `categoryCount: Int`, `accountCount: Int`,
    `dateRange: DateRange?` (rendered as chips).
  - `showAccountDimension: Boolean = false` (true when `accountCount >= 2`),
  - `selectedDimension: Dimension = Category`,
  - `rows: List<Row>` — already projected for the *selected* dimension,
  - `segments: List<Segment>` — donut segments for the selected dimension.`)`
- `data class Row(name, amount: Amount, transactionCount, sharePercent: Int, colorScheme: ColorScheme,
  icon: Image)` and `data class Segment(value: Float, colorScheme: ColorScheme)`. **No Compose
  `Color`, no formatters** (ViewProviderDerivation lint).
- `Noop`.

### `DefaultBreakdownViewModel.kt`
Constructor deps mirror `DefaultCategoryDetailViewModel` + `filter: TransactionFilter`,
`onBackHandler: OnBackHandler`, `transactionRepository`, `categoriesQueryUseCase`, `accountRepository`,
`colorRepository`, `iconRepository`, `currencyConvertUseCase`, `currencyPrimaryUseCase`, `zonedClock`,
`dispatchers`. Holds `MutableStateFlow<State>`; `selectedDimension` is the only user-writable field
(`perform(SelectDimension)`), everything else derived in `attachOnMain`.

`attachOnMain`: resolve `filter.period` → range against `zonedClock.localDateTime().date`; query
`Criteria.Filtered(...)`; `combine` with categories/accounts/icons/colors maps. Then, **expenses only**
(spending report): convert to primary, group by `categoryId` and by `accountId` → `(Amount,count)`;
total = Σ expenses; sort desc; compute `sharePercent`; resolve `ColorScheme`/icon/name per group.
Recompute `rows`/`segments` for `selectedDimension` (combine the dimension flow with state so the
toggle re-projects without re-querying). `Back` → `onBackHandler`.

### `BreakdownViewProvider.kt` (internal)
`Column` in a `Scaffold`/`Box` with `ZeroTheme.colors.surface` bg, scrollable:
1. `DetailTopBar(title = stringResource(spending), onBack = { perform(Back) })`.
2. **FILTERED BY** label + context chips row (period/type/"N categories"/"N accounts"/date-range) —
   small rounded `surfaceLow` chips, read-only (no remove X). Build a local `ContextChip` composable.
3. Scoped total: big `primary` amount via `AmountFormatter.Style.Short` + "spent" + subtitle line
   (`{count} transactions · {accountCount} accounts · {range}`).
4. If `state.showAccountDimension`: `SegmentedToggle(items=Dimension.entries, selected, onSelect,
   labelMapping = byCategory/byAccount string)`.
5. Split block (`surfaceContainerLowest`, 18dp radius): `DonutChart` (center = total Short) + legend
   (dot, name, %) + divider + rows (`CategoryIcon` from `row.colorScheme.toUi()` + `row.icon`, name,
   amount, share progress bar, "{n} txns"). View only formats + renders; zero arithmetic
   (`feedback_no_calculations_in_views`).

### Handler
Extend `OnShowBreakdownHandler.onShowBreakdown()` → `onShowBreakdown(filter: TransactionFilter)`;
update `Noop` + the `TransactionViewProvider` call site to pass `state.activeFilter`.

## Navigation wiring (`app`)

- `Destinations.Transaction`: add `object Breakdown : Transaction, Destination by
  destinationOf("transactions/breakdown")` (no URL args — filter is too rich for `Argument<Id>`).
- **Filter carrier** — `@MainActivityScreenScope class ScopedSpendingArgs @Inject constructor()` with
  `var filter: TransactionFilter = TransactionFilter.All`. Mirrors `DefaultTransactionFilterUseCase`'s
  in-memory `TransactionFilter` relay (the existing precedent for passing a filter object screen-local
  rather than via URL). Inject the one scoped instance into both providers below.
- `homeTabComponent`: add `.onShowBreakdownHandler { filter -> scopedSpendingArgs.filter = filter;
  navigator.navigateTo(Destinations.Transaction.Breakdown) }`.
- New `@IntoSet breakdownNavigationEntry(builder: BreakdownComponent.Builder, args: ScopedSpendingArgs,
  navigatorScope)` → `navigatorScope.buildable(Destinations.Transaction.Breakdown) {
  builder.filter(args.filter).onBackHandler { navigator.back() }.logging(logger) }`.
- `BreakdownComponent` provides its `Dependencies` from the activity graph (same deps the
  `CategoryDetailComponent.Dependencies` lists + `accountRepository`, `colorRepository`,
  `iconRepository`). Expose any missing ones on the component `Dependencies` interface; the activity
  component already provides them (used by `DefaultTransactionViewModel`).

## Strings (`zero-core` `strings.xml`)
`breakdown_title` ("Spending"), `breakdown_filtered_by` ("Filtered by"), `breakdown_dimension_category`
("By category"), `breakdown_dimension_account` ("By account"), `breakdown_spent` ("spent"),
`breakdown_subtitle` ("%1$d transactions · %2$s"), `breakdown_chip_categories` (plural),
`breakdown_chip_accounts` (plural), `breakdown_row_txns` (plural). Reuse filter-period/type labels if
present.

## Verification
- `./gradlew spotlessApply testDebugUnitTest lint 2>&1 | tail -25` — fix any failure.
- `android-ui-inspector`: apply a multi-category + multi-account filter on Transactions, tap **See
  breakdown**, confirm the report renders the scoped total, context chips, donut + rows, and the
  **By category / By account** toggle switches the breakdown. Verify back returns to the filtered list.
- A focused unit test for the VM aggregation (scoped total, per-category + per-account grouping,
  `showAccountDimension` gate) if it fits the existing `*ViewModelTest` pattern.

## Structural notes for review
- New abstraction: `ScopedSpendingArgs` carrier + `OnShowBreakdownHandler` signature change → run
  `pr-architecture-review` on the branch before flipping the PR.
