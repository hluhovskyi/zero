# Swipe-Select Tile Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a generic vertical swipe-select tile to `zero-ui` (swipe up = next item, swipe down = previous, with a 3-face spinner animation and a `SwapVert` affordance in place of the dropdown arrow), then wire it into the transaction-edit Date and Account tiles.

**Architecture:** One stateless composable `SwipeSelectTile` in `zero-ui`. It owns no item list — the caller supplies `current` / `previous` / `next` face slots plus `onSelectNext` / `onSelectPrevious` callbacks and `canSelectNext` / `canSelectPrevious` flags. This callback shape fits both the bounded account list and the unbounded date sequence, and will fit categories later. The swipe decision is a pure function (`resolveSwipe`) so it is unit-testable; the gesture + animation live in the composable. Content ("faces") is intentionally minimal — out of scope per the brief.

**Tech Stack:** Kotlin, Jetpack Compose, Material 3, `ZeroTheme.colors`, `kotlinx.datetime`.

**Design reference:** `ui_kits/zero/Swipe Select Pattern.html` → `swipe-select.jsx` (chosen direction = `SwipeField`: label top-left, `⇅` top-right, swipe ±1 with bounce, `Spinner` over `items[index-1..index+1]` at center-opacity 1 / neighbor-opacity 0.3, `swUp`/`swDn` 0.18s ease).

---

## File Structure

- **Create** `zero-ui/src/main/java/com/hluhovskyi/zero/ui/SwipeSelectTile.kt` — the generic composable + `resolveSwipe` pure logic + `SwipeOutcome` enum.
- **Create** `zero-ui/src/test/java/com/hluhovskyi/zero/ui/SwipeSelectTileTest.kt` — unit tests for `resolveSwipe` (analog: `AmountKeypadTest.kt`).
- **Modify** `zero-core/.../transactions/edit/common/ExpenseIncomeForm.kt` — replace `DatePickerCard` (date) + `SelectorCard` (account) with `SwipeSelectTile`.
- **Modify** `zero-core/.../transactions/edit/transfer/TransferForm.kt` — replace the From/To `SelectorCard`s and the `DatePickerCard` with `SwipeSelectTile`.

**Not touched:** `SelectorCard.kt` and `DatePickerCard.kt` stay — `SelectorCard` is still used by `AccountEditViewProvider`; `DatePickerCard`'s `DatePickerDialog` is reused as the date tile's tap escape-hatch.

---

## Task 1: `resolveSwipe` pure logic (TDD)

**Files:**
- Create: `zero-ui/src/main/java/com/hluhovskyi/zero/ui/SwipeSelectTile.kt`
- Test: `zero-ui/src/test/java/com/hluhovskyi/zero/ui/SwipeSelectTileTest.kt`

Mirrors `useSwipe`'s release branch: total vertical delta < -commit → next; > +commit → previous; |delta| < tapSlop → tap; otherwise none (bounce). Negative dy = finger moved up = "next" (matches `d < -20 → go(1)` in the JSX). Edge flags gate commits so the tile bounces at a boundary instead of firing.

- [ ] **Step 1: Write the failing test**

```kotlin
package com.hluhovskyi.zero.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class SwipeSelectTileTest {

    private fun resolve(dy: Float, canPrev: Boolean = true, canNext: Boolean = true) =
        resolveSwipe(totalDy = dy, commitThreshold = 20f, tapSlop = 6f, canSelectPrevious = canPrev, canSelectNext = canNext)

    @Test fun `swipe up past threshold selects next`() {
        assertEquals(SwipeOutcome.Next, resolve(dy = -30f))
    }

    @Test fun `swipe down past threshold selects previous`() {
        assertEquals(SwipeOutcome.Previous, resolve(dy = 30f))
    }

    @Test fun `tiny movement is a tap`() {
        assertEquals(SwipeOutcome.Tap, resolve(dy = 3f))
    }

    @Test fun `mid drag that is neither tap nor commit does nothing`() {
        assertEquals(SwipeOutcome.None, resolve(dy = 12f))
    }

    @Test fun `swipe next at end bounces to none`() {
        assertEquals(SwipeOutcome.None, resolve(dy = -30f, canNext = false))
    }

    @Test fun `swipe previous at start bounces to none`() {
        assertEquals(SwipeOutcome.None, resolve(dy = 30f, canPrev = false))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :zero-ui:testDebugUnitTest --tests "com.hluhovskyi.zero.ui.SwipeSelectTileTest"`
Expected: FAIL — `resolveSwipe` / `SwipeOutcome` unresolved.

- [ ] **Step 3: Write minimal implementation** (top of `SwipeSelectTile.kt`)

```kotlin
package com.hluhovskyi.zero.ui

import kotlin.math.abs

internal enum class SwipeOutcome { Previous, Next, Tap, None }

/** Decide what a released vertical drag means. Up (negative dy) = next, down = previous. */
internal fun resolveSwipe(
    totalDy: Float,
    commitThreshold: Float,
    tapSlop: Float,
    canSelectPrevious: Boolean,
    canSelectNext: Boolean,
): SwipeOutcome = when {
    totalDy < -commitThreshold && canSelectNext -> SwipeOutcome.Next
    totalDy > commitThreshold && canSelectPrevious -> SwipeOutcome.Previous
    abs(totalDy) < tapSlop -> SwipeOutcome.Tap
    else -> SwipeOutcome.None
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :zero-ui:testDebugUnitTest --tests "com.hluhovskyi.zero.ui.SwipeSelectTileTest"`
Expected: PASS (6 tests).

- [ ] **Step 5: Commit**

```bash
git add zero-ui/src/main/java/com/hluhovskyi/zero/ui/SwipeSelectTile.kt zero-ui/src/test/java/com/hluhovskyi/zero/ui/SwipeSelectTileTest.kt
git commit -m "feat(zero-ui): add resolveSwipe swipe-direction logic"
```

---

## Task 2: `SwipeSelectTile` composable

**Files:**
- Modify: `zero-ui/src/main/java/com/hluhovskyi/zero/ui/SwipeSelectTile.kt`

The visual chrome matches `SwipeField`: `surfaceContainerLow` rounded tile, uppercase label top-left, `Icons.Filled.SwapVert` top-right (the "two swap arrows" replacing `ArrowDropDown`). A fixed `RowHeight` (40dp) viewport clips a column of three stacked faces (previous / current / next) translated by `-RowHeight + drag`. A single `awaitEachGesture` tracks the drag into an `Animatable` and, on release, calls `resolveSwipe`; commits animate the column one row in the swipe direction, fire the callback, then `snapTo(0)` (the just-revealed neighbor is the new current, so the snap is invisible). Neighbor faces render at alpha 0.3, current at 1f. `None` animates back to 0 (bounce). `Tap` invokes optional `onClick`.

- [ ] **Step 1: Append the composable** (after `resolveSwipe`)

```kotlin
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hluhovskyi.zero.ui.theme.ZeroTheme
import kotlinx.coroutines.launch

private val RowHeight = 40.dp
private val SwipeEasing = CubicBezierEasing(0.34f, 0.1f, 0.2f, 1f)

/**
 * Generic vertical swipe-to-select tile. Swipe up → next, down → previous; bounces at edges.
 * The caller owns the data and supplies the [current]/[previous]/[next] faces plus the
 * commit callbacks — content is out of scope here. Reused for Date, Account (categories next).
 */
@Composable
fun SwipeSelectTile(
    label: String,
    onSelectPrevious: () -> Unit,
    onSelectNext: () -> Unit,
    modifier: Modifier = Modifier,
    canSelectPrevious: Boolean = true,
    canSelectNext: Boolean = true,
    onClick: (() -> Unit)? = null,
    previous: (@Composable () -> Unit)? = null,
    next: (@Composable () -> Unit)? = null,
    current: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val rowPx = with(density) { RowHeight.toPx() }
    val commitThreshold = with(density) { 14.dp.toPx() }
    val tapSlop = with(density) { 5.dp.toPx() }

    val scope = rememberCoroutineScope()
    val drag = remember { Animatable(0f) }

    // Keep the gesture lambda reading fresh state without re-keying pointerInput.
    val latch = rememberUpdatedState(
        SwipeCallbacks(canSelectPrevious, canSelectNext, onSelectPrevious, onSelectNext, onClick),
    )

    Column(
        modifier = modifier
            .background(ZeroTheme.colors.surfaceContainerLow, RoundedCornerShape(16.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label.uppercase(),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = ZeroTheme.colors.onSurfaceVariant,
                letterSpacing = 1.sp,
            )
            Icon(
                imageVector = Icons.Filled.SwapVert,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = ZeroTheme.colors.outline,
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(RowHeight)
                .padding(top = 4.dp)
                .clipToBounds()
                .pointerInput(Unit) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        var total = 0f
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull() ?: break
                            if (!change.pressed) break
                            val dy = change.positionChange().y
                            total += dy
                            change.consume()
                            scope.launch { drag.snapTo((drag.value + dy).coerceIn(-rowPx, rowPx)) }
                        }
                        val cb = latch.value
                        when (
                            resolveSwipe(total, commitThreshold, tapSlop, cb.canPrevious, cb.canNext)
                        ) {
                            SwipeOutcome.Next -> scope.launch {
                                drag.animateTo(-rowPx, tween(180, easing = SwipeEasing))
                                cb.onNext(); drag.snapTo(0f)
                            }
                            SwipeOutcome.Previous -> scope.launch {
                                drag.animateTo(rowPx, tween(180, easing = SwipeEasing))
                                cb.onPrevious(); drag.snapTo(0f)
                            }
                            SwipeOutcome.Tap -> { cb.onClick?.invoke(); scope.launch { drag.snapTo(0f) } }
                            SwipeOutcome.None -> scope.launch { drag.animateTo(0f, tween(180, easing = SwipeEasing)) }
                        }
                    }
                },
        ) {
            Column(
                modifier = Modifier.graphicsLayer { translationY = -rowPx + drag.value },
            ) {
                SwipeFace(alpha = 0.3f) { previous?.invoke() }
                SwipeFace(alpha = 1f) { current() }
                SwipeFace(alpha = 0.3f) { next?.invoke() }
            }
        }
    }
}

@Composable
private fun SwipeFace(alpha: Float, content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .height(RowHeight)
            .fillMaxWidth()
            .alpha(alpha),
        contentAlignment = Alignment.CenterStart,
    ) { content() }
}

private data class SwipeCallbacks(
    val canPrevious: Boolean,
    val canNext: Boolean,
    val onPrevious: () -> Unit,
    val onNext: () -> Unit,
    val onClick: (() -> Unit)?,
)
```

> Note for executor: add the missing `import androidx.compose.foundation.layout.Arrangement` and `import androidx.compose.runtime.remember` — Android Studio / the compiler will flag them; resolve to the standard Compose packages.

- [ ] **Step 2: Compile zero-ui**

Run: `./gradlew :zero-ui:compileDebugKotlin 2>&1 | tail -20`
Expected: BUILD SUCCESSFUL. Fix any unresolved imports per the note above.

- [ ] **Step 3: Commit**

```bash
git add zero-ui/src/main/java/com/hluhovskyi/zero/ui/SwipeSelectTile.kt
git commit -m "feat(zero-ui): add SwipeSelectTile generic swipe-select component"
```

---

## Task 3: Wire the Account tile (ExpenseIncomeForm + TransferForm)

**Files:**
- Modify: `zero-core/src/main/java/com/hluhovskyi/zero/transactions/edit/common/ExpenseIncomeForm.kt`
- Modify: `zero-core/src/main/java/com/hluhovskyi/zero/transactions/edit/transfer/TransferForm.kt`

Replace each account `SelectorCard` with `SwipeSelectTile`. Compute the selected index from the list; previous/next faces from the neighbors; commit by selecting the neighbor item. Face = bold account name (`Text`, `maxLines = 1`, ellipsis) — content kept minimal per scope.

- [ ] **Step 1: ExpenseIncomeForm — replace the account `SelectorCard`**

Define a private face helper at file scope:

```kotlin
@Composable
private fun AccountFace(name: String) {
    Text(
        text = name,
        fontSize = 14.sp,
        fontWeight = FontWeight.Bold,
        color = ZeroTheme.colors.primaryContainer,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}
```

Replace the `SelectorCard(...)` account block with:

```kotlin
val accounts = form.accounts
val selectedIndex = accounts.indexOf(form.selectedAccount)
SwipeSelectTile(
    modifier = Modifier.weight(1f),
    label = stringResource(R.string.transaction_edit_account_label),
    canSelectPrevious = selectedIndex > 0,
    canSelectNext = selectedIndex in 0 until accounts.lastIndex,
    onSelectPrevious = { perform(TransactionEditViewModel.Action.SelectAccount(accounts[selectedIndex - 1])) },
    onSelectNext = { perform(TransactionEditViewModel.Action.SelectAccount(accounts[selectedIndex + 1])) },
    previous = accounts.getOrNull(selectedIndex - 1)?.let { acc -> { AccountFace(acc.name) } },
    next = accounts.getOrNull(selectedIndex + 1)?.let { acc -> { AccountFace(acc.name) } },
    current = { AccountFace(form.selectedAccount?.name ?: "") },
)
```

Add imports: `com.hluhovskyi.zero.ui.SwipeSelectTile`, `com.hluhovskyi.zero.ui.theme.ZeroTheme`, `androidx.compose.material3.Text`, `androidx.compose.ui.text.font.FontWeight`, `androidx.compose.ui.text.style.TextOverflow`, `androidx.compose.ui.unit.sp`. Remove the now-unused `SelectorCard` import.

- [ ] **Step 2: TransferForm — replace both From/To `SelectorCard`s**

Reuse the same `AccountFace` pattern (add a private helper in this file too). From tile uses `form.accounts` / `form.selectedAccount` / `SelectAccount`; To tile uses `form.targetAccounts` / `form.selectedTargetAccount` / `SelectTargetAccount`. Keep the `SwapHoriz` box between them unchanged. Pattern for the From tile:

```kotlin
val fromAccounts = form.accounts
val fromIndex = fromAccounts.indexOf(form.selectedAccount)
SwipeSelectTile(
    modifier = Modifier.weight(1f),
    label = stringResource(R.string.transfer_edit_from_label),
    canSelectPrevious = fromIndex > 0,
    canSelectNext = fromIndex in 0 until fromAccounts.lastIndex,
    onSelectPrevious = { perform(TransactionEditViewModel.Action.SelectAccount(fromAccounts[fromIndex - 1])) },
    onSelectNext = { perform(TransactionEditViewModel.Action.SelectAccount(fromAccounts[fromIndex + 1])) },
    previous = fromAccounts.getOrNull(fromIndex - 1)?.let { acc -> { AccountFace(acc.name) } },
    next = fromAccounts.getOrNull(fromIndex + 1)?.let { acc -> { AccountFace(acc.name) } },
    current = { AccountFace(form.selectedAccount?.name ?: "") },
)
```

Mirror it for the To tile (`targetAccounts` / `selectedTargetAccount` / `SelectTargetAccount`). Update imports as in Step 1; remove `SelectorCard` import if no longer used in this file.

- [ ] **Step 3: Compile zero-core**

Run: `./gradlew :zero-core:compileDebugKotlin 2>&1 | tail -20`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add zero-core/src/main/java/com/hluhovskyi/zero/transactions/edit/common/ExpenseIncomeForm.kt zero-core/src/main/java/com/hluhovskyi/zero/transactions/edit/transfer/TransferForm.kt
git commit -m "feat(transactions): swipe-select account tiles in edit forms"
```

---

## Task 4: Wire the Date tile (ExpenseIncomeForm + TransferForm)

**Files:**
- Modify: `zero-core/src/main/java/com/hluhovskyi/zero/transactions/edit/common/ExpenseIncomeForm.kt`
- Modify: `zero-core/src/main/java/com/hluhovskyi/zero/transactions/edit/transfer/TransferForm.kt`

Replace `DatePickerCard` with `SwipeSelectTile`. Date is unbounded: previous = −1 day, next = +1 day (both always enabled). `onClick` opens the existing `DatePickerDialog` (the far-date escape hatch, preserving today's behavior). Face = formatted `"MMM dd, yyyy"` text (matches the old `DatePickerCard` content).

- [ ] **Step 1: Add a date-tile helper composable** (private, in `common/`; `ExpenseIncomeForm.kt`)

```kotlin
@Composable
private fun DateSwipeTile(
    modifier: Modifier,
    label: String,
    date: LocalDateTime,
    onDateSelected: (LocalDateTime) -> Unit,
) {
    val context = LocalContext.current
    fun shift(days: Long): LocalDateTime =
        date.toJavaLocalDateTime().toLocalDate().plusDays(days).let {
            LocalDateTime(it.year, it.monthValue, it.dayOfMonth, 0, 0, 0)
        }
    fun face(d: LocalDateTime): @Composable () -> Unit = {
        Text(
            text = d.toJavaLocalDateTime().format(DateTimeFormatter.ofPattern("MMM dd, yyyy")),
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = ZeroTheme.colors.primaryContainer,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
    SwipeSelectTile(
        modifier = modifier,
        label = label,
        onSelectPrevious = { onDateSelected(shift(-1)) },
        onSelectNext = { onDateSelected(shift(1)) },
        onClick = {
            DatePickerDialog(
                context,
                { _, year, month, dayOfMonth -> onDateSelected(LocalDateTime(year, month + 1, dayOfMonth, 0, 0, 0)) },
                date.year, date.monthNumber - 1, date.dayOfMonth,
            ).show()
        },
        previous = face(shift(-1)),
        next = face(shift(1)),
        current = face(date),
    )
}
```

Add imports: `android.app.DatePickerDialog`, `androidx.compose.ui.platform.LocalContext`, `kotlinx.datetime.LocalDateTime`, `kotlinx.datetime.toJavaLocalDateTime`, `java.time.format.DateTimeFormatter`, plus the `Text`/`FontWeight`/`TextOverflow`/`sp`/`ZeroTheme` imports from Task 3.

- [ ] **Step 2: ExpenseIncomeForm — use `DateSwipeTile`**

Replace the `DatePickerCard(...)` call inside `form.date?.let { date -> ... }` with:

```kotlin
DateSwipeTile(
    modifier = Modifier.weight(1f),
    label = stringResource(R.string.transaction_edit_date_label),
    date = date,
    onDateSelected = { perform(TransactionEditViewModel.Action.ChangeDate(it)) },
)
```

Remove the `DatePickerCard` import. (`DatePickerCard` stays in zero-ui — unused there is fine for a UI library.)

- [ ] **Step 3: TransferForm — use the same tile**

Add a copy of the `DateSwipeTile` helper to `TransferForm.kt` (or, if cleaner, lift it to a shared file in `transactions/edit/common/`; executor's call — a shared `DateSwipeTile.kt` in `common/` is preferred to avoid duplication). Replace the `DatePickerCard(...)` (full-width) with `DateSwipeTile(modifier = Modifier.fillMaxWidth(), ...)`.

- [ ] **Step 4: Compile + unit tests + lint**

Run: `./gradlew :zero-core:compileDebugKotlin :zero-ui:testDebugUnitTest lintDebug 2>&1 | tail -25`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add zero-core/src/main/java/com/hluhovskyi/zero/transactions/edit/
git commit -m "feat(transactions): swipe-select date tile with calendar tap fallback"
```

---

## Task 5: Device verification

- [ ] **Step 1:** Acquire emulator: `./scripts/emulator/acquire`
- [ ] **Step 2:** Build + install + launch the transaction-edit screen; invoke `zero-project:android-ui-inspector` to confirm: the Date and Account tiles show the `SwapVert` icon (no dropdown arrow), swiping up/down changes the selected value with the slide animation, the date tile's tap opens the calendar dialog, and tiles bounce at the account list edges.
- [ ] **Step 3:** Capture a screenshot for the PR.

---

## Self-Review

- **Spec coverage:** generic component (Task 2) ✓; swipe up/down → next/prev (Task 1 + 2) ✓; SwapVert replaces ArrowDropDown (Task 2) ✓; animation (Task 2) ✓; reused for date + account (Tasks 3–4) ✓; categories-ready via callback API (Task 2 design) ✓; content out of scope = minimal faces ✓.
- **Type consistency:** `resolveSwipe` / `SwipeOutcome` / `SwipeSelectTile(label, onSelectPrevious, onSelectNext, canSelectPrevious, canSelectNext, onClick, previous, next, current)` referenced identically across tasks ✓.
- **Placeholders:** none — every code step is concrete; the one executor judgment call (shared vs duplicated `DateSwipeTile`) is explicitly flagged with a preferred answer.
