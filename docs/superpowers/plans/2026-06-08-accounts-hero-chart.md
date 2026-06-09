# Accounts Chart-Forward Hero — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the Accounts net-worth header with the design's treatment B — a smaller number, a real 74dp net-worth trend chart (existing charts lib), a 1-year growth chip, and an inline assets/liabilities row with a `View trend` affordance.

**Architecture:** Compute a trailing-12-month net-worth series in `DefaultAccountUseCase` from the transaction ledger (`Criteria.All()` + the same `currencyConvertUseCase` the balance uses), reconstructed backward from the live balance. Two pure helpers in `NetWorthTrend.kt` carry the logic (unit-tested without DI). The view provider renders treatment B with `LineChart`/`SignedLineChart`.

**Tech Stack:** Kotlin, Jetpack Compose, Dagger, kotlinx.datetime, JUnit + Mockito.

Spec: `docs/superpowers/specs/2026-06-08-accounts-hero-chart-design.md`

---

## Task 1: Net-worth trend pure helpers (TDD)

**Files:**
- Create: `zero-core/src/main/java/com/hluhovskyi/zero/accounts/NetWorthTrend.kt`
- Test: `zero-core/src/test/java/com/hluhovskyi/zero/accounts/NetWorthTrendTest.kt`

Structural analog for the test: `zero-core/src/test/java/com/hluhovskyi/zero/accounts/detail/DefaultAccountDetailSpendingUseCaseTest.kt` (transaction factory helpers, `Amount(BigDecimal)`, `Rate(BigDecimal.ONE)`).

- [ ] **Step 1: Write the failing test** `NetWorthTrendTest.kt`

```kotlin
package com.hluhovskyi.zero.accounts

import com.hluhovskyi.zero.common.Amount
import com.hluhovskyi.zero.common.Id
import com.hluhovskyi.zero.common.Rate
import com.hluhovskyi.zero.transactions.TransactionRepository
import kotlinx.datetime.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.math.BigDecimal

class NetWorthTrendTest {

    @Test
    fun `income contributes positive, expense negative, transfer ignored`() {
        assertEquals(BigDecimal("100"), income("100").netWorthContribution()!!.second.value)
        assertEquals(BigDecimal("-40"), expense("40").netWorthContribution()!!.second.value)
        assertNull(transfer().netWorthContribution())
    }

    @Test
    fun `reconstruct ends at current net worth and walks deltas backward`() {
        // anchor month index 100; deltas: month 100 = +200, month 99 = +300
        val series = reconstructNetWorthTrend(
            currentNetWorth = Amount(BigDecimal("1000")),
            monthlyDeltas = mapOf(100 to Amount(BigDecimal("200")), 99 to Amount(BigDecimal("300"))),
            anchorMonthIndex = 100,
        )
        // nw[100]=1000, nw[99]=1000-200=800, nw[98]=800-300=500 -> oldest..newest
        assertEquals(listOf(BigDecimal("500"), BigDecimal("800"), BigDecimal("1000")), series.map { it.value })
    }

    @Test
    fun `no deltas yields a single current point`() {
        val series = reconstructNetWorthTrend(Amount(BigDecimal("1000")), emptyMap(), anchorMonthIndex = 100)
        assertEquals(listOf(BigDecimal("1000")), series.map { it.value })
    }

    @Test
    fun `window caps at 12 points even with older deltas`() {
        val deltas = (80..100).associateWith { Amount(BigDecimal("10")) }
        val series = reconstructNetWorthTrend(Amount(BigDecimal("1000")), deltas, anchorMonthIndex = 100)
        assertEquals(12, series.size)
        assertEquals(BigDecimal("1000"), series.last().value)
    }

    private fun income(v: String) = TransactionRepository.Transaction.Income(
        id = Id.Known("i"), amount = Amount(BigDecimal(v)), accountId = Id.Known("a"),
        currencyId = Id.Known("c"), dateTime = DT, updatedDateTime = DT,
        categoryId = Id.Known("cat"), rate = Rate(BigDecimal.ONE),
    )

    private fun expense(v: String) = TransactionRepository.Transaction.Expense(
        id = Id.Known("e"), amount = Amount(BigDecimal(v)), accountId = Id.Known("a"),
        currencyId = Id.Known("c"), dateTime = DT, updatedDateTime = DT,
        categoryId = Id.Known("cat"), rate = Rate(BigDecimal.ONE),
    )

    private fun transfer() = TransactionRepository.Transaction.Transfer(
        id = Id.Known("t"), amount = Amount(BigDecimal("50")), accountId = Id.Known("a"),
        currencyId = Id.Known("c"), dateTime = DT, updatedDateTime = DT,
        targetAccount = Id.Known("b"), targetAmount = Amount(BigDecimal("50")),
    )

    private companion object {
        val DT: LocalDateTime = LocalDateTime.parse("2026-05-10T10:00:00")
    }
}
```

- [ ] **Step 2: Run test, verify it fails**

Run: `./gradlew :zero-core:testDebugUnitTest --tests "com.hluhovskyi.zero.accounts.NetWorthTrendTest"`
Expected: FAIL — unresolved references `netWorthContribution`, `reconstructNetWorthTrend`.

- [ ] **Step 3: Implement `NetWorthTrend.kt`**

```kotlin
package com.hluhovskyi.zero.accounts

import com.hluhovskyi.zero.common.Amount
import com.hluhovskyi.zero.common.Id
import com.hluhovskyi.zero.transactions.TransactionRepository
import kotlinx.datetime.LocalDateTime

/** Trailing months kept in the net-worth trend (matches the design's 12-month window). */
internal const val NET_WORTH_TREND_MONTHS = 12

/** Month bucket key: stable, comparable, gap-free across year boundaries. */
internal fun LocalDateTime.monthIndex(): Int = year * 12 + (monthNumber - 1)

/**
 * A transaction's effect on total net worth, in its own currency (caller converts to primary).
 * Income adds, Expense subtracts, Transfer is net-zero across accounts → `null`.
 */
internal fun TransactionRepository.Transaction.netWorthContribution(): Pair<Id.Known, Amount>? =
    when (this) {
        is TransactionRepository.Transaction.Income -> currencyId to amount
        is TransactionRepository.Transaction.Expense -> currencyId to (Amount.zero() - amount)
        is TransactionRepository.Transaction.Transfer -> null
    }

/**
 * Net worth at each month boundary, oldest → newest, last == [currentNetWorth].
 * Walks [monthlyDeltas] (signed primary deltas keyed by [monthIndex]) backward from the anchor.
 * Window: from the earliest delta month (clamped to the anchor) up to the anchor, capped to the
 * most recent [maxPoints] months. Empty deltas → a single current point (chart shows a lone dot).
 */
internal fun reconstructNetWorthTrend(
    currentNetWorth: Amount,
    monthlyDeltas: Map<Int, Amount>,
    anchorMonthIndex: Int,
    maxPoints: Int = NET_WORTH_TREND_MONTHS,
): List<Amount> {
    if (monthlyDeltas.isEmpty()) return listOf(currentNetWorth)
    val earliest = minOf(monthlyDeltas.keys.min(), anchorMonthIndex)
    val start = maxOf(anchorMonthIndex - (maxPoints - 1), earliest)
    val newestToOldest = ArrayList<Amount>(anchorMonthIndex - start + 1)
    var nw = currentNetWorth
    for (month in anchorMonthIndex downTo start) {
        newestToOldest.add(nw)
        nw -= monthlyDeltas[month] ?: Amount.zero()
    }
    return newestToOldest.asReversed().toList()
}
```

- [ ] **Step 4: Run test, verify it passes**

Run: `./gradlew :zero-core:testDebugUnitTest --tests "com.hluhovskyi.zero.accounts.NetWorthTrendTest"`
Expected: PASS (4 tests).

- [ ] **Step 5: Commit**

```bash
git add zero-core/src/main/java/com/hluhovskyi/zero/accounts/NetWorthTrend.kt zero-core/src/test/java/com/hluhovskyi/zero/accounts/NetWorthTrendTest.kt
git commit -m "feat(accounts): net-worth trend reconstruction helpers"
```

---

## Task 2: Thread `netWorthTrend` through state + use case + DI

**Files:**
- Modify: `zero-core/.../accounts/AccountUseCase.kt`
- Modify: `zero-core/.../accounts/AccountViewModel.kt`
- Modify: `zero-core/.../accounts/DefaultAccountViewModel.kt`
- Modify: `zero-core/.../accounts/DefaultAccountUseCase.kt`
- Modify: `zero-core/.../accounts/AccountComponent.kt`

- [ ] **Step 1: Add the field to both states**

In `AccountUseCase.State` and `AccountViewModel.State`, add (import `com.hluhovskyi.zero.common.Amount` already present in both):

```kotlin
val netWorthTrend: List<Amount> = emptyList(),
```

In `DefaultAccountViewModel.attachOnMain()` `mutableState.update { ... copy(...) }`, add:

```kotlin
netWorthTrend = useCaseState.netWorthTrend,
```

- [ ] **Step 2: Compute the trend in `DefaultAccountUseCase`**

Add constructor params after `currencyConvertUseCase` (keep `private val`):

```kotlin
private val clock: com.hluhovskyi.zero.common.time.Clock,
private val zoneProvider: com.hluhovskyi.zero.common.time.ZoneProvider,
```

Add a 5th source to the existing `combine(...)`:

```kotlin
transactionRepository.query(TransactionRepository.Criteria.All())
    .onEmpty { emit(emptyList()) },
```

(import `kotlinx.coroutines.flow.onEmpty` is already used.) Update the lambda arity to receive `allTransactions: List<TransactionRepository.Transaction>` as the new last arg. Inside the lambda, after `balance/assets/liabilities` are computed, before building `State`:

```kotlin
val deltasByMonth: Map<Int, Amount> = allTransactions
    .mapNotNull { tx ->
        tx.netWorthContribution()?.let { (currencyId, signed) ->
            tx.dateTime.monthIndex() to currencyConvertUseCase.convertToPrimary(signed, currencyId)
        }
    }
    .groupBy({ it.first }, { it.second })
    .mapValues { (_, deltas) -> deltas.fold(Amount.zero(), Amount::plus) }
val anchorMonthIndex = clock.localDateTime(zoneProvider.timeZone()).monthIndex()
val netWorthTrend = reconstructNetWorthTrend(balance, deltasByMonth, anchorMonthIndex)
```

Add `netWorthTrend = netWorthTrend,` to the `AccountUseCase.State(...)` constructor call.
Add imports: `com.hluhovskyi.zero.common.time.localDateTime` (extension), `kotlinx.datetime.LocalDateTime` is not needed directly.

> `combine` with 5 flows: switch to the `combine(f1, f2, f3, f4, f5) { a, b, c, d, e -> ... }` overload (vararg lambda receives `Array<*>`? No — the 5-arg typed overload exists in kotlinx.coroutines). Keep the typed 5-arg form.

- [ ] **Step 3: Wire DI in `AccountComponent`**

In `Dependencies`, add:

```kotlin
val clock: Clock
val zoneProvider: ZoneProvider
```

(imports `com.hluhovskyi.zero.common.time.Clock`, `com.hluhovskyi.zero.common.time.ZoneProvider`.)

In `Module.useCase(...)`, add params `clock: Clock, zoneProvider: ZoneProvider,` and pass `clock = clock, zoneProvider = zoneProvider,` to `DefaultAccountUseCase(...)`. Analog: `AccountDetailComponent.kt:124-125` already does exactly this wiring.

- [ ] **Step 4: Build to verify wiring compiles (Dagger codegen)**

Run: `./gradlew :zero-core:compileDebugKotlin :app:kaptDebugKotlin 2>&1 | tail -20`
Expected: BUILD SUCCESSFUL (Dagger resolves `clock`/`zoneProvider` from the parent graph, as it does for `AccountDetailComponent`).

- [ ] **Step 5: Commit**

```bash
git add zero-core/src/main/java/com/hluhovskyi/zero/accounts/
git commit -m "feat(accounts): expose net-worth trend from use case"
```

---

## Task 3: Strings for the hero

**Files:**
- Modify: `zero-core/src/main/res/values/strings.xml`

- [ ] **Step 1: Add strings** near the existing `account_*` entries (line ~17):

```xml
<string name="account_view_trend">View trend</string>
<string name="account_net_worth_growth">%1$s%% · 1Y</string>
<string name="account_net_worth_improvement">+%1$s · 1Y</string>
```

- [ ] **Step 2: Commit**

```bash
git add zero-core/src/main/res/values/strings.xml
git commit -m "feat(accounts): strings for chart-forward hero"
```

---

## Task 4: Redesign `NetWorthHeader` (treatment B)

**Files:**
- Modify: `zero-core/src/main/java/com/hluhovskyi/zero/accounts/AccountViewProvider.kt`

Chart usage reference: `zero-ui/.../chart/ChartsGalleryScreen.kt:69` — `LineChart(LineChartData(points), ZeroTheme.colors.secondary, Modifier.fillMaxWidth().height(74.dp))`; `:58` for `SignedLineChart`.

- [ ] **Step 1: Update the call site in `AccountView`**

Replace the `NetWorthHeader(...)` call (currently lines ~106-119) with one that also derives the trend points, the negative flag, and the chip text. Insert before the `LazyColumn` `item { NetWorthHeader(...) }` body:

```kotlin
item {
    val symbol = state.currency?.symbol.orEmpty()
    val trend = state.netWorthTrend
    val points = trend.map { it.value.toFloat() }
    val first = trend.firstOrNull()?.value
    val last = trend.lastOrNull()?.value
    val isNegative = (last?.signum() ?: 0) < 0
    val growthChip: String? = when {
        first == null || last == null || trend.size < 2 || first.signum() == 0 -> null
        isNegative -> (last - first).takeIf { it.signum() > 0 }
            ?.let { stringResource(R.string.account_net_worth_improvement, amountFormatter.format(Amount(it), symbol)) }
        else -> (((last.toDouble() - first.toDouble()) / kotlin.math.abs(first.toDouble())) * 100)
            .toInt().takeIf { it > 0 }
            ?.let { stringResource(R.string.account_net_worth_growth, it.toString()) }
    }
    NetWorthHeader(
        balance = amountFormatter.format(state.balance, symbol),
        assets = amountFormatter.format(state.assets, symbol),
        liabilities = amountFormatter.format(state.liabilities, symbol),
        trendPoints = points,
        isNegative = isNegative,
        growthChip = growthChip,
    )
}
```

Add imports: `com.hluhovskyi.zero.common.Amount`, `com.hluhovskyi.zero.ui.chart.LineChart`, `com.hluhovskyi.zero.ui.chart.SignedLineChart`, `com.hluhovskyi.zero.ui.chart.LineChartData`, `androidx.compose.foundation.layout.height`, `androidx.compose.foundation.layout.fillMaxWidth` (present).

- [ ] **Step 2: Rewrite `NetWorthHeader` and add helper composables**

Replace the whole `NetWorthHeader` composable (lines ~180-267) with treatment B:

```kotlin
@Composable
private fun NetWorthHeader(
    balance: String,
    assets: String,
    liabilities: String,
    trendPoints: List<Float>,
    isNegative: Boolean,
    growthChip: String?,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(ZeroTheme.colors.surfaceContainerLow)
            .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            Column {
                Text(
                    text = stringResource(R.string.account_total_net_worth).uppercase(),
                    style = TextStyle(
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = ZeroTheme.colors.onSurfaceVariant,
                        letterSpacing = 1.sp,
                    ),
                )
                Text(
                    text = balance,
                    style = TextStyle(
                        fontSize = 26.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (isNegative) ZeroTheme.colors.error else ZeroTheme.colors.primary,
                        letterSpacing = (-0.5).sp,
                    ),
                )
            }
            if (growthChip != null) GrowthChip(text = growthChip)
        }
        if (isNegative) {
            SignedLineChart(
                data = LineChartData(trendPoints),
                modifier = Modifier.fillMaxWidth().height(74.dp),
            )
        } else {
            LineChart(
                data = LineChartData(trendPoints),
                lineColor = ZeroTheme.colors.secondary,
                modifier = Modifier.fillMaxWidth().height(74.dp),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                MiniStat(label = stringResource(R.string.account_assets), value = assets, valueColor = ZeroTheme.colors.secondary)
                MiniStat(label = stringResource(R.string.account_liabilities), value = liabilities, valueColor = ZeroTheme.colors.error)
            }
            ViewTrend()
        }
    }
}

@Composable
private fun GrowthChip(text: String) {
    Row(
        modifier = Modifier
            .background(ZeroTheme.colors.secondary.copy(alpha = 0.14f), RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Icon(
            imageVector = Icons.Filled.ArrowDropUp,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = ZeroTheme.colors.secondary,
        )
        Text(
            text = text,
            style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Bold, color = ZeroTheme.colors.secondary),
        )
    }
}

@Composable
private fun MiniStat(label: String, value: String, valueColor: androidx.compose.ui.graphics.Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = label.uppercase(),
            style = TextStyle(
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = ZeroTheme.colors.onSurfaceVariant,
                letterSpacing = 0.8.sp,
            ),
        )
        Text(
            text = value,
            style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Bold, color = valueColor),
        )
    }
}

@Composable
private fun ViewTrend() {
    Row(
        // Trend screen is out of scope; affordance is present but inert for now.
        modifier = Modifier.clickable {},
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = stringResource(R.string.account_view_trend),
            style = TextStyle(fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = ZeroTheme.colors.primary),
        )
        Icon(
            imageVector = Icons.Filled.ChevronRight,
            contentDescription = null,
            modifier = Modifier.size(14.dp),
            tint = ZeroTheme.colors.primary,
        )
    }
}
```

Add imports: `androidx.compose.material.icons.filled.ArrowDropUp`, `androidx.compose.material.icons.filled.ChevronRight`, `androidx.compose.foundation.layout.height`. (`Spacer`, `width` may become unused — remove if spotless/lint flags them.)

- [ ] **Step 3: Build + format + lint**

Run: `./gradlew :zero-core:compileDebugKotlin 2>&1 | tail -20`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add zero-core/src/main/java/com/hluhovskyi/zero/accounts/AccountViewProvider.kt
git commit -m "feat(accounts): chart-forward net-worth hero (treatment B)"
```

---

## Task 5: Full verification

- [ ] **Step 1: Build gates (single invocation)**

Run: `./gradlew spotlessApply testDebugUnitTest lintDebug 2>&1 | tail -25`
Expected: all pass. If spotless reformats files, `git add` + commit them.

- [ ] **Step 2: UI inspection** — invoke `zero-project:android-ui-inspector`. Acquire the emulator first (`./scripts/emulator/acquire`). Confirm the hero renders treatment B: `NET WORTH` label, ~26sp number, growth chip (when data grows), a visible 74dp line chart, inline `ASSETS`/`LIAB.`, and `View trend`. Spot-check a negative-net-worth account set → red number + signed chart.

- [ ] **Step 3: Commit any spotless changes**

```bash
git commit -am "style: spotless"   # only if spotless changed files
```

---

## Self-review notes

- **Spec coverage:** trend data (Task 2), growth/improvement chip with hide rules (Task 4 Step 1), header layout B (Task 4 Step 2), strings (Task 3), tests (Task 1 + Task 5), negative net worth via `SignedLineChart` (Task 4). All spec sections covered.
- **Type consistency:** `netWorthTrend: List<Amount>` (state) → `points: List<Float>` (view) → `LineChartData(points)`. `reconstructNetWorthTrend` / `netWorthContribution` / `monthIndex` names match across Task 1 and Task 2. `growthChip: String?` param name matches between call site and composable.
- **Chip honesty:** green up-chip shows only when net worth actually grew (pct > 0) or debt actually shrank (delta > 0); hidden otherwise — avoids a misleading up-arrow on a decline.
