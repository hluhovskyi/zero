# Feedback Submitted Screen Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Show a "Feedback sent" confirmation in the feedback sheet after a successful submit, with a Done button that dismisses, replacing the current silent navigate-back.

**Architecture:** Add an in-sheet `submitted` boolean to `FeedbackViewModel.State` (mirrors the existing `isSubmitting` flag). Submit success flips it instead of calling the submitted handler; a new `Action.Done` calls the handler (which still does `navigator.back()`). `FeedbackViewProvider` branches to a `SentView` when `submitted`.

**Tech Stack:** Kotlin, Jetpack Compose, JUnit. Spec: `docs/superpowers/specs/2026-05-31-feedback-submitted-screen-design.md`.

---

### Task 1: ViewModel state, action, and submit/Done behavior

**Files:**
- Modify: `zero-core/src/main/java/com/hluhovskyi/zero/feedback/FeedbackViewModel.kt`
- Modify: `zero-core/src/main/java/com/hluhovskyi/zero/feedback/DefaultFeedbackViewModel.kt`
- Test: `zero-core/src/test/java/com/hluhovskyi/zero/feedback/DefaultFeedbackViewModelTest.kt`

- [ ] **Step 1: Update + add the failing tests**

In `DefaultFeedbackViewModelTest`, replace the existing `Success invokes handler and clears submitting` test body with success-shows-confirmation semantics, and add a Done test:

```kotlin
    @Test
    fun `Success shows confirmation, clears submitting, and does not invoke handler`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val service = ScriptedFeedbackService(FeedbackSubmitResult.Success("url"))
        val handler = RecordingSubmittedHandler()
        val viewModel = newViewModel(service, handler, CoroutineScope(dispatcher))

        viewModel.perform(FeedbackViewModel.Action.UpdateDescription("description"))
        viewModel.perform(FeedbackViewModel.Action.Submit)
        advanceUntilIdle()

        assertEquals(1, service.callCount)
        assertEquals(0, handler.callCount)
        val state = viewModel.state.first()
        assertFalse(state.isSubmitting)
        assertTrue(state.submitted)
        assertNull(state.errorMessage)
    }

    @Test
    fun `Done after successful submit invokes submitted handler`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val service = ScriptedFeedbackService(FeedbackSubmitResult.Success("url"))
        val handler = RecordingSubmittedHandler()
        val viewModel = newViewModel(service, handler, CoroutineScope(dispatcher))

        viewModel.perform(FeedbackViewModel.Action.UpdateDescription("description"))
        viewModel.perform(FeedbackViewModel.Action.Submit)
        advanceUntilIdle()
        viewModel.perform(FeedbackViewModel.Action.Done)

        assertEquals(1, handler.callCount)
    }
```

Add `import org.junit.Assert.assertTrue` if missing.

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :zero-core:testDebugUnitTest --tests "*DefaultFeedbackViewModelTest*"`
Expected: FAIL — `submitted` is unresolved and `Action.Done` does not exist.

- [ ] **Step 3: Add `submitted` to State and `Done` to Action**

In `FeedbackViewModel.kt`, add `object Done : Action` to the `Action` sealed interface and `val submitted: Boolean = false` to `State` (place it next to `isSubmitting`).

- [ ] **Step 4: Update `DefaultFeedbackViewModel`**

In `submit()`, change the `Success` branch from calling the handler to flipping state:

```kotlin
                is FeedbackSubmitResult.Success -> {
                    mutableState.update { it.copy(isSubmitting = false, submitted = true) }
                }
```

In `perform(...)`, add the `Done` branch:

```kotlin
            FeedbackViewModel.Action.Done -> onFeedbackSubmittedHandler.onFeedbackSubmitted()
```

- [ ] **Step 5: Run tests to verify they pass**

Run: `./gradlew :zero-core:testDebugUnitTest --tests "*DefaultFeedbackViewModelTest*"`
Expected: PASS (all tests in the class).

- [ ] **Step 6: Commit**

```bash
git add zero-core/src/main/java/com/hluhovskyi/zero/feedback/FeedbackViewModel.kt zero-core/src/main/java/com/hluhovskyi/zero/feedback/DefaultFeedbackViewModel.kt zero-core/src/test/java/com/hluhovskyi/zero/feedback/DefaultFeedbackViewModelTest.kt
git commit -m "feedback: model submitted confirmation state + Done action"
```

---

### Task 2: SentView confirmation UI + strings

**Files:**
- Modify: `zero-core/src/main/res/values/strings.xml`
- Modify: `zero-core/src/main/java/com/hluhovskyi/zero/feedback/FeedbackViewProvider.kt`

- [ ] **Step 1: Add strings**

After the `feedback_char_counter` string in `zero-core/src/main/res/values/strings.xml`:

```xml
    <string name="feedback_sent_title">Feedback sent</string>
    <string name="feedback_sent_body">Thanks — we\'ll review it within a few days. We may follow up by email if we need more detail.</string>
    <string name="feedback_done">Done</string>
```

- [ ] **Step 2: Branch the view on `submitted`**

In `FeedbackViewProvider.kt`, in `FeedbackView`, at the top after collecting `state`, short-circuit to the confirmation:

```kotlin
    if (state.submitted) {
        SentView(onDone = { viewModel.perform(FeedbackViewModel.Action.Done) })
        return
    }
```

(Place it before the `focusRequester`/`LaunchedEffect` so the keyboard isn't requested on the confirmation screen — move those two lines below the early return, or guard the early return above them.)

- [ ] **Step 3: Add the `SentView` composable**

Add to `FeedbackViewProvider.kt`. Match the design `SentSheet`: 72dp `secondaryContainer` circle with a 40dp `Icons.Filled.CheckCircle` tinted `secondary`, ExtraBold 22sp `primary` title, 14sp centered `onSurfaceVariant` body, full-width `primaryContainer` Done button.

```kotlin
@Composable
private fun SentView(onDone: () -> Unit) {
    val colors = ZeroTheme.colors
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.weight(1f))
        Box(
            modifier = Modifier
                .size(72.dp)
                .background(colors.secondaryContainer, shape = CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.CheckCircle,
                contentDescription = null,
                tint = colors.secondary,
                modifier = Modifier.size(40.dp),
            )
        }
        Text(
            text = stringResource(R.string.feedback_sent_title),
            style = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = colors.primary),
            modifier = Modifier.padding(top = 20.dp, bottom = 6.dp),
        )
        Text(
            text = stringResource(R.string.feedback_sent_body),
            style = TextStyle(fontSize = 14.sp, color = colors.onSurfaceVariant, lineHeight = 21.sp, textAlign = TextAlign.Center),
        )
        Spacer(Modifier.weight(1f))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.primaryContainer, shape = RoundedCornerShape(16.dp))
                .clickable { onDone() }
                .padding(vertical = 16.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = stringResource(R.string.feedback_done),
                style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White),
            )
        }
        Spacer(Modifier.height(32.dp))
    }
}
```

Add imports: `androidx.compose.foundation.shape.CircleShape`, `androidx.compose.material.icons.filled.CheckCircle`. (`Box`, `clickable`, `CircleShape`, `Icons`, `Color`, `Spacer`, `weight`, `size` are already imported or trivially addable.)

- [ ] **Step 4: Build to verify compile**

Run: `./gradlew :zero-core:compileDebugKotlin 2>&1 | tail -20`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add zero-core/src/main/res/values/strings.xml zero-core/src/main/java/com/hluhovskyi/zero/feedback/FeedbackViewProvider.kt
git commit -m "feedback: add Feedback sent confirmation screen"
```

---

## Verification (after both tasks)

- `./gradlew testDebugUnitTest lintDebug 2>&1 | tail -25` — green.
- UI inspector: trigger feedback (shake / debug entry), type text, send → confirm the "Feedback sent" screen renders (check-circle, title, body, Done), and Done dismisses back to Transactions.
