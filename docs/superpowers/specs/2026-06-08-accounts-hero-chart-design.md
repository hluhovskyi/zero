# Accounts hero — chart-forward net-worth card (treatment B)

## Goal

Replace the centered "big number + tiny sparkline" net-worth header on the Accounts
screen with the design's **treatment B — Chart-forward**: a smaller headline number, a
real 74dp net-worth trend chart, a 1-year growth chip, and an inline `ASSETS / LIAB.`
row paired with a `View trend` affordance.

Design source: `ui_kits/zero/Analytics Exploration.html` → `HeroChartForward`
(artboard *"B · Chart-forward — smaller number, 74px"*) in `accounts-hero.jsx`.

Scope guards from the brief:
- Implement **treatment B** only (not A "today" or C "two-column").
- `View trend` is **added as an affordance**, but the destination screen is **out of scope**
  (no-op click, TODO seam).
- Reuse the existing **charts library** (`zero-ui/.../chart/`); do not modify it.

## What changes

### 1. Net-worth trend data (zero-core only)

There is no net-worth history today — `DefaultAccountUseCase` computes only the *current*
`balance/assets/liabilities`. The chart needs a series. Compute it where the data already
lives: in `DefaultAccountUseCase`, from the transaction ledger.

- Add `transactionRepository.query(Criteria.All())` to the use case's `combine`. It returns
  every `Transaction` with `dateTime`, type, `amount`, `currencyId` (one batch under
  `NO_TRIGGER`).
- Bucket transactions by calendar month (`YearMonth` of `dateTime`). Per-month signed delta,
  converted to primary currency with the **same** `currencyConvertUseCase` the balance uses
  (so the trend is consistent with the displayed number):
  - `Income` → `+convertToPrimary(amount, currencyId)`
  - `Expense` → `−convertToPrimary(amount, currencyId)`
  - `Transfer` → `0` (a transfer moves money between accounts; net worth is unchanged)
- Reconstruct net worth at each month boundary by walking **backward** from the live total:
  `nw[last] = balance`, `nw[m-1] = nw[m] − monthlyDelta[m]`. Keep the trailing **12** points
  (current month back through 11 prior). Fewer points when history is shorter.
- Expose the result as `netWorthTrend: List<Amount>` on `AccountUseCase.State` and
  `AccountViewModel.State`. `DefaultAccountViewModel` copies it straight through.

This is a pure projection of data the use case already produces, so it lives on that use
case — no new repository criteria, no database/API module changes.

**FX note:** transfers are treated as net-zero, ignoring any FX spread between the two legs.
Acceptable for a trend sparkline; the live headline number remains the authoritative,
per-account-converted balance.

### 2. Growth / improvement metric (view layer)

Derived in the view provider from `netWorthTrend`:
- Positive net worth (`last ≥ 0`): growth **percent** `round((last − first) / |first| · 100)`,
  rendered as a green chip `"{pct}% · 1Y"` with an up-triangle.
- Negative net worth (`last < 0`): improvement **delta** `last − first`, rendered as a green
  chip `"+{formatted Δ} · 1Y"` (debt shrinking reads as improvement).
- Hide the chip when the series has `< 2` points or `first == 0` (no meaningful change).

### 3. NetWorthHeader redesign (zero-core `AccountViewProvider`)

Rewrite the private `NetWorthHeader` composable to treatment B. Background stays
`surfaceContainerLow`; padding `16dp top / 20dp horizontal / 14dp bottom`.

- **Top row** (`SpaceBetween`, top-aligned):
  - Left: `NET WORTH` label (11sp Bold, `onSurfaceVariant`, +letterSpacing, uppercase) above
    the balance number (**26sp**, `FontWeight.ExtraBold`, `primary` — or `error` when net
    worth is negative; letterSpacing −0.5sp).
  - Right: the growth/improvement chip (`secondary` text + icon on `secondary @ 14% alpha`,
    8dp radius).
- **Chart** (`fillMaxWidth().height(74.dp)`):
  - `last ≥ 0` → `LineChart(LineChartData(points), lineColor = secondary)`.
  - `last < 0` → `SignedLineChart(LineChartData(points))` (draws its own zero baseline, red
    while underwater).
- **Bottom row** (`SpaceBetween`, center-aligned, 12dp above):
  - Left: inline `ASSETS {green value}` + `LIAB. {red value}` (10sp Bold labels, 13sp Bold
    values; `secondary` / `error`), 18dp gap.
  - Right: `View trend` (12.5sp Bold `primary`) + trailing chevron. Clickable, **no-op** for
    now (screen out of scope).

The old centered column, the vertical divider, and the 32sp number are removed.

### 4. Strings

Add to `zero-core/.../values/strings.xml`:
- `account_view_trend` = "View trend"
- `account_net_worth_growth` = "%1$s%% · 1Y" (percent chip)
- `account_net_worth_improvement` = "+%1$s · 1Y" (delta chip)

Reuse existing `account_total_net_worth`, `account_assets`, `account_liabilities`.

## Components touched

| Unit | Change |
|------|--------|
| `AccountUseCase.State` | + `netWorthTrend: List<Amount>` |
| `DefaultAccountUseCase` | compute trend from `Criteria.All()` + `currencyConvertUseCase` |
| `AccountViewModel.State` | + `netWorthTrend: List<Amount>` |
| `DefaultAccountViewModel` | pass trend through |
| `AccountViewProvider` | rewrite `NetWorthHeader`; derive chip; map `Amount→Float`; render chart |
| `strings.xml` | 3 new strings |

## Testing

- **Use-case unit test** (`DefaultAccountUseCaseTest` or new): given accounts + dated
  transactions across several months, assert `netWorthTrend` (a) ends at the current
  `balance`, (b) has the expected length/points, (c) treats transfers as net-zero, and
  (d) degrades to a single point with no transactions.
- **Build gates:** `spotlessApply testDebugUnitTest lintDebug`.
- **UI:** `android-ui-inspector` confirms the hero renders treatment B (chart visible,
  number/chip/assets-liab/View-trend laid out), for both positive and negative net worth.

## Out of scope

- The Trend / Analytics destination screen.
- Treatments A and C.
- Any change to the charts library, the account list rows, or the data/Room layer.
- Historical FX rates (current rates are used, as elsewhere).
