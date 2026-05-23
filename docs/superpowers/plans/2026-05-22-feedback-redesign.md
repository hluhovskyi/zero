# Feedback redesign — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Rebuild the Send Feedback bottom sheet to match the new design and tag each report with a `bug` / `idea` / `other` GitHub label selected by the user.

**Spec:** [docs/superpowers/specs/2026-05-22-feedback-redesign-design.md](../specs/2026-05-22-feedback-redesign-design.md)

**Tech Stack:** Kotlin, Jetpack Compose, Dagger, Node.js (cloud function).

---

## File Structure

**New:**
- `zero-api/src/main/java/com/hluhovskyi/zero/feedback/FeedbackType.kt`
- `zero-core/src/main/java/com/hluhovskyi/zero/feedback/OnFeedbackCloseHandler.kt`
- `zero-ui/src/main/res/drawable/ic_feedback_bug_24.xml`
- `zero-ui/src/main/res/drawable/ic_feedback_idea_24.xml`
- `zero-ui/src/main/res/drawable/ic_feedback_other_24.xml`
- `zero-ui/src/main/res/drawable/ic_close_24.xml`

**Modified:**
- `zero-core/src/main/java/com/hluhovskyi/zero/feedback/FeedbackReportFormatter.kt`
- `zero-core/src/main/java/com/hluhovskyi/zero/feedback/FeedbackViewModel.kt`
- `zero-core/src/main/java/com/hluhovskyi/zero/feedback/DefaultFeedbackViewModel.kt`
- `zero-core/src/main/java/com/hluhovskyi/zero/feedback/FeedbackSheetComponent.kt`
- `zero-core/src/main/java/com/hluhovskyi/zero/feedback/FeedbackViewProvider.kt`
- `zero-core/src/main/res/values/strings.xml`
- `zero-core/src/test/java/com/hluhovskyi/zero/feedback/FeedbackReportFormatterTest.kt`
- `zero-core/src/test/java/com/hluhovskyi/zero/feedback/DefaultFeedbackViewModelTest.kt`
- `app/src/main/java/com/hluhovskyi/zero/feedback/FeedbackComponent.kt`
- `functions/feedback/index.js`

---

## Task 1: API — FeedbackType enum

**Files:** new `zero-api/src/main/java/com/hluhovskyi/zero/feedback/FeedbackType.kt`.

- [ ] **Step 1: Create the enum**

```kotlin
package com.hluhovskyi.zero.feedback

enum class FeedbackType(val label: String) {
    Bug("bug"),
    Idea("idea"),
    Other("other"),
}
```

- [ ] **Step 2: Verify compile**

`./gradlew :zero-api:compileDebugKotlin 2>&1 | tail -10`

- [ ] **Step 3: Commit + push**

```
feat(feedback): add FeedbackType enum (bug/idea/other) in zero-api
```

---

## Task 2: Formatter — accept type, emit type label

**Files:** modify `FeedbackReportFormatter.kt`, `FeedbackReportFormatterTest.kt`.

- [ ] **Step 1: Change `format` signature to accept `type: FeedbackType`**

`FeedbackReportFormatter.format(description, snapshot)` → `format(type, description, snapshot)`. Inside the `labels` builder:

```kotlin
val labels = buildList {
    add("feedback")
    add(type.label)
    if (isDebugBuild) add("debug")
}
```

- [ ] **Step 2: Update `DefaultFeedbackViewModel` call site to pass `previous.type`**

(Type defined in Task 3 — temporarily pass `FeedbackType.Bug` here; Task 3 swaps it for `previous.type`.)

- [ ] **Step 3: Update `FeedbackReportFormatterTest`**

Add a `private val type = FeedbackType.Bug` field. Pass it to every `formatter().format(...)` call. Update the two label-assertion tests:

- `labels include feedback always` → expect `listOf("feedback", "bug")`.
- `labels include debug when isDebugBuild` → expect `listOf("feedback", "bug", "debug")`.

Add a new test:

```kotlin
@Test
fun `labels include selected type key`() {
    val report = formatter().format(FeedbackType.Idea, "desc", emptySnapshot)
    assertEquals(listOf("feedback", "idea"), report.labels)
}
```

- [ ] **Step 4: Run formatter tests**

`./gradlew :zero-core:testDebugUnitTest --tests "com.hluhovskyi.zero.feedback.FeedbackReportFormatterTest" 2>&1 | tail -20`

- [ ] **Step 5: Commit + push**

```
feat(feedback): forward FeedbackType into report labels
```

---

## Task 3: ViewModel — type selection + close action

**Files:** modify `FeedbackViewModel.kt`, `DefaultFeedbackViewModel.kt`, `DefaultFeedbackViewModelTest.kt`. New `OnFeedbackCloseHandler.kt`.

- [ ] **Step 1: Create `OnFeedbackCloseHandler`**

Mirror `OnFeedbackSubmittedHandler.kt` exactly — same file shape, same `Noop`:

```kotlin
package com.hluhovskyi.zero.feedback

fun interface OnFeedbackCloseHandler {
    fun onFeedbackClose()

    companion object {
        val Noop: OnFeedbackCloseHandler = OnFeedbackCloseHandler { }
    }
}
```

- [ ] **Step 2: Extend `FeedbackViewModel`**

```kotlin
sealed interface Action {
    data class UpdateDescription(val text: String) : Action
    data class SelectType(val type: FeedbackType) : Action
    object Submit : Action
    object Close : Action
}

data class State(
    val description: String = "",
    val type: FeedbackType = FeedbackType.Bug,
    val isSubmitting: Boolean = false,
    val deviceInfoPreview: String = "",
    val errorMessage: String? = null,
)
```

- [ ] **Step 3: Wire actions in `DefaultFeedbackViewModel`**

- Constructor adds `private val onFeedbackCloseHandler: OnFeedbackCloseHandler` (next to the submitted handler).
- `perform` adds:
  - `SelectType` → `mutableState.update { it.copy(type = action.type) }`
  - `Close` → `onFeedbackCloseHandler.onFeedbackClose()`
- `submit()` passes `previous.type` to `reportFormatter.format(...)` (replaces the temporary `FeedbackType.Bug` from Task 2).

- [ ] **Step 4: Update `DefaultFeedbackViewModelTest`**

- Add a `RecordingCloseHandler` mirroring `RecordingSubmittedHandler`.
- Thread `onFeedbackCloseHandler = handler` through `newViewModel`.
- Add tests:
  - `SelectType updates state type` — perform `SelectType(Idea)`, assert state.
  - `Close invokes close handler` — perform `Close`, assert `closeHandler.callCount == 1`.

- [ ] **Step 5: Run viewmodel tests**

`./gradlew :zero-core:testDebugUnitTest --tests "com.hluhovskyi.zero.feedback.DefaultFeedbackViewModelTest" 2>&1 | tail -20`

- [ ] **Step 6: Commit + push**

```
feat(feedback): add SelectType/Close actions and close handler
```

---

## Task 4: String resources

**Files:** modify `zero-core/src/main/res/values/strings.xml`.

- [ ] **Step 1: Add strings near the existing `feedback_*` block (around line 222)**

Keep `feedback_title`, `feedback_submit`, `feedback_error_generic`. Remove `feedback_description_hint` (replaced by per-type hints). Add:

```xml
<string name="feedback_eyebrow">Found a bug or have an idea? Tell us what happened — we read every report.</string>
<string name="feedback_type_bug">Bug</string>
<string name="feedback_type_idea">Idea</string>
<string name="feedback_type_other">Other</string>
<string name="feedback_what_happened">What happened?</string>
<string name="feedback_hint_bug">Tap on a transaction to edit it — the amount field is blank instead of the current value…</string>
<string name="feedback_hint_idea">It would be great if I could split a transaction across two categories…</string>
<string name="feedback_hint_other">Tell us what\'s on your mind…</string>
<string name="feedback_close">Close</string>
<string name="feedback_privacy_footnote">Feedback is sent to the Zero team. No account data, balances or transactions are included.</string>
<string name="feedback_char_counter">%1$d/1000</string>
```

- [ ] **Step 2: Commit + push**

```
feat(feedback): add string resources for redesign (type pills, hints, footnote)
```

---

## Task 5: Vector drawables for type icons + close

**Files:** new XML files under `zero-ui/src/main/res/drawable/`.

For each file: 24×24 viewBox, single `<path>` element copied from the design.

- [ ] **Step 1: `ic_feedback_bug_24.xml`**

Path data (from design `TYPES[0].path`):
`M20,8h-2.81a5.985,5.985 0,0 0,-1.82 -1.96L17,4.41 15.59,3l-2.17,2.17a6.007,6.007 0,0 0,-2.83 0L8.41,3 7,4.41l1.62,1.63C7.88,6.55 7.26,7.22 6.81,8H4v2h2.09c-0.05,0.33 -0.09,0.66 -0.09,1v1H4v2h2v1c0,0.34 0.04,0.67 0.09,1H4v2h2.81c1.04,1.79 2.97,3 5.19,3s4.15,-1.21 5.19,-3H20v-2h-2.09c0.05,-0.33 0.09,-0.66 0.09,-1v-1h2v-2h-2v-1c0,-0.34 -0.04,-0.67 -0.09,-1H20V8zM14,16h-4v-2h4v2zM14,12h-4v-2h4v2z`

Wrap as a standard tintable Material drawable:

```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24"
    android:tint="?attr/colorControlNormal">
    <path android:fillColor="@android:color/white" android:pathData="..."/>
</vector>
```

- [ ] **Step 2: `ic_feedback_idea_24.xml`**

Path from `TYPES[1].path`:
`M9,21c0,0.55 0.45,1 1,1h4c0.55,0 1,-0.45 1,-1v-1H9v1zM12,2C8.14,2 5,5.14 5,9c0,2.38 1.19,4.47 3,5.74V17c0,0.55 0.45,1 1,1h6c0.55,0 1,-0.45 1,-1v-2.26c1.81,-1.27 3,-3.36 3,-5.74 0,-3.86 -3.14,-7 -7,-7zM14.85,13.1L14,13.7V16h-4v-2.3l-0.85,-0.6A4.997,4.997 0,0 1,7 9c0,-2.76 2.24,-5 5,-5s5,2.24 5,5c0,1.63 -0.8,3.16 -2.15,4.1z`

- [ ] **Step 3: `ic_feedback_other_24.xml`**

Path from `TYPES[2].path`:
`M21,6h-2v9H6v2c0,0.55 0.45,1 1,1h11l4,4V7c0,-0.55 -0.45,-1 -1,-1zM17,12V3c0,-0.55 -0.45,-1 -1,-1H3c-0.55,0 -1,0.45 -1,1v14l4,-4h10c0.55,0 1,-0.45 1,-1z`

- [ ] **Step 4: `ic_close_24.xml`** (Material standard close)

Path: `M19,6.41L17.59,5 12,10.59 6.41,5 5,6.41 10.59,12 5,17.59 6.41,19 12,13.41 17.59,19 19,17.59 13.41,12z`

- [ ] **Step 5: Build the UI module**

`./gradlew :zero-ui:compileDebugResources 2>&1 | tail -10`

- [ ] **Step 6: Commit + push**

```
feat(ui): add bug/idea/other/close vector drawables
```

---

## Task 6: ViewProvider — rewrite to the new design

**Files:** replace `zero-core/src/main/java/com/hluhovskyi/zero/feedback/FeedbackViewProvider.kt`.

- [ ] **Step 1: Replace the file with the new composable tree**

Reference: `ui_kits/zero/Send Feedback.html` `FeedbackSheet` for visual structure; `BudgetEditViewProvider` for project's Compose conventions.

```kotlin
package com.hluhovskyi.zero.feedback

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.Text
import androidx.compose.material.TextField
import androidx.compose.material.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hluhovskyi.zero.R
import com.hluhovskyi.zero.common.ViewProvider
import com.hluhovskyi.zero.ui.theme.Error
import com.hluhovskyi.zero.ui.theme.OnSurface
import com.hluhovskyi.zero.ui.theme.OnSurfaceVariant
import com.hluhovskyi.zero.ui.theme.Outline
import com.hluhovskyi.zero.ui.theme.PrimaryContainer
import com.hluhovskyi.zero.ui.theme.SurfaceContainer
import com.hluhovskyi.zero.ui.theme.SurfaceContainerLow

internal class FeedbackViewProvider(
    private val viewModel: FeedbackViewModel,
) : ViewProvider {

    @Composable
    override fun View() {
        FeedbackView(viewModel = viewModel)
    }
}

private const val MAX_CHARS = 1000
private const val WARN_CHARS = 900

private data class TypeOption(
    val type: FeedbackType,
    val iconRes: Int,
    val labelRes: Int,
    val hintRes: Int,
)

private val TYPES = listOf(
    TypeOption(FeedbackType.Bug,   com.hluhovskyi.zero.ui.R.drawable.ic_feedback_bug_24,   R.string.feedback_type_bug,   R.string.feedback_hint_bug),
    TypeOption(FeedbackType.Idea,  com.hluhovskyi.zero.ui.R.drawable.ic_feedback_idea_24,  R.string.feedback_type_idea,  R.string.feedback_hint_idea),
    TypeOption(FeedbackType.Other, com.hluhovskyi.zero.ui.R.drawable.ic_feedback_other_24, R.string.feedback_type_other, R.string.feedback_hint_other),
)

@Composable
private fun FeedbackView(viewModel: FeedbackViewModel) {
    val state by viewModel.state.collectAsState(initial = FeedbackViewModel.State())

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(bottom = 20.dp),
    ) {
        Header(onClose = { viewModel.perform(FeedbackViewModel.Action.Close) })

        Text(
            text = stringResource(R.string.feedback_eyebrow),
            style = TextStyle(fontSize = 13.sp, color = OnSurfaceVariant, lineHeight = 19.sp),
            modifier = Modifier.padding(top = 4.dp, bottom = 16.dp),
        )

        TypePillRow(
            selected = state.type,
            enabled = !state.isSubmitting,
            onSelect = { viewModel.perform(FeedbackViewModel.Action.SelectType(it)) },
        )

        Spacer(Modifier.height(16.dp))

        DescriptionCard(
            value = state.description,
            type = state.type,
            enabled = !state.isSubmitting,
            onChange = { viewModel.perform(FeedbackViewModel.Action.UpdateDescription(it)) },
        )

        val errorMessage = state.errorMessage
        if (errorMessage != null) {
            Text(
                text = errorMessage,
                style = TextStyle(fontSize = 12.sp, color = Error),
                modifier = Modifier.padding(top = 4.dp),
            )
        }

        SendButton(
            isSubmitting = state.isSubmitting,
            enabled = state.description.isNotBlank() && !state.isSubmitting,
            onClick = { viewModel.perform(FeedbackViewModel.Action.Submit) },
        )

        Text(
            text = stringResource(R.string.feedback_privacy_footnote),
            style = TextStyle(fontSize = 11.sp, color = Outline, lineHeight = 16.sp, textAlign = TextAlign.Center),
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        )
    }
}

@Composable
private fun Header(onClose: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().height(56.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onClose, modifier = Modifier.size(48.dp)) {
            Icon(
                painter = painterResource(com.hluhovskyi.zero.ui.R.drawable.ic_close_24),
                contentDescription = stringResource(R.string.feedback_close),
                tint = PrimaryContainer,
            )
        }
        Text(
            text = stringResource(R.string.feedback_title),
            style = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Bold, color = PrimaryContainer, textAlign = TextAlign.Center),
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.size(48.dp))
    }
}

@Composable
private fun TypePillRow(selected: FeedbackType, enabled: Boolean, onSelect: (FeedbackType) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        TYPES.forEach { option ->
            val isSelected = option.type == selected
            Column(
                modifier = Modifier
                    .weight(1f)
                    .background(
                        color = if (isSelected) Color.White else SurfaceContainerLow,
                        shape = RoundedCornerShape(14.dp),
                    )
                    .border(
                        width = 1.5.dp,
                        color = if (isSelected) PrimaryContainer else Color.Transparent,
                        shape = RoundedCornerShape(14.dp),
                    )
                    .clickable(enabled = enabled) { onSelect(option.type) }
                    .padding(vertical = 12.dp, horizontal = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(
                    painter = painterResource(option.iconRes),
                    contentDescription = null,
                    tint = if (isSelected) PrimaryContainer else OnSurfaceVariant,
                    modifier = Modifier.size(22.dp),
                )
                Text(
                    text = stringResource(option.labelRes),
                    style = TextStyle(
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isSelected) PrimaryContainer else OnSurfaceVariant,
                    ),
                )
            }
        }
    }
}

@Composable
private fun DescriptionCard(value: String, type: FeedbackType, enabled: Boolean, onChange: (String) -> Unit) {
    val hintRes = TYPES.first { it.type == type }.hintRes
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(SurfaceContainerLow, shape = RoundedCornerShape(16.dp))
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Text(
            text = stringResource(R.string.feedback_what_happened),
            style = TextStyle(
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = OnSurfaceVariant,
                letterSpacing = 1.2.sp,
            ),
            modifier = Modifier.padding(bottom = 8.dp),
        )
        TextField(
            value = value,
            onValueChange = { if (it.length <= MAX_CHARS) onChange(it) },
            placeholder = {
                Text(
                    text = stringResource(hintRes),
                    style = TextStyle(fontSize = 15.sp, color = Outline, lineHeight = 22.sp),
                )
            },
            enabled = enabled,
            modifier = Modifier.fillMaxWidth().height(132.dp),
            textStyle = TextStyle(fontSize = 15.sp, color = OnSurface, lineHeight = 22.sp),
            colors = TextFieldDefaults.textFieldColors(
                backgroundColor = Color.Transparent,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                disabledIndicatorColor = Color.Transparent,
            ),
            contentPadding = PaddingValues(0.dp),
        )
        Text(
            text = stringResource(R.string.feedback_char_counter, value.length),
            style = TextStyle(
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = if (value.length > WARN_CHARS) Error else Outline,
            ),
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            textAlign = TextAlign.End,
        )
    }
}

@Composable
private fun SendButton(isSubmitting: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp)
            .background(
                color = if (enabled || isSubmitting) PrimaryContainer else SurfaceContainer,
                shape = RoundedCornerShape(16.dp),
            )
            .clickable(enabled = enabled) { onClick() }
            .padding(vertical = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (isSubmitting) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
        } else {
            Text(
                text = stringResource(R.string.feedback_submit),
                style = TextStyle(
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (enabled) Color.White else Outline,
                ),
            )
        }
    }
}
```

Notes:
- `TextField.contentPadding` is `androidx.compose.material.TextFieldDefaults.TextFieldWithoutLabelPadding` in Material 2; if `contentPadding` is not a parameter on `TextField` in the project's Compose version, use `BasicTextField` instead with the same outer card layout.
- `letterSpacing = 1.2.sp` approximates the design's `0.12em` cap tracking at 10sp.
- The drag handle is provided by `MainActivityScreenViewProvider`; do NOT add another one here.

- [ ] **Step 2: Compile**

`./gradlew :zero-core:compileDebugKotlin 2>&1 | tail -20`

If `TextField` rejects `contentPadding`, switch to `BasicTextField` (same surrounding card). Do not introduce a third indirection.

- [ ] **Step 3: Commit + push**

```
feat(feedback): redesign Send Feedback sheet (type pills, single text card)
```

---

## Task 7: DI wiring — close handler + sheet component

**Files:** modify `FeedbackSheetComponent.kt`, `app/src/main/java/com/hluhovskyi/zero/feedback/FeedbackComponent.kt`.

- [ ] **Step 1: `FeedbackSheetComponent`**

Add an `onFeedbackCloseHandler: OnFeedbackCloseHandler` constructor parameter and forward it to `DefaultFeedbackViewModel`.

- [ ] **Step 2: `FeedbackComponent.Module` (app)**

Add a `@Provides @FeedbackScope` for `OnFeedbackCloseHandler` that returns `OnFeedbackCloseHandler { navigator.back() }`. Wire it into the `feedbackSheetComponent(...)` `@Provides`.

Follow the pattern already used by `OnFeedbackSubmittedHandler` immediately above it (default handler at the Module layer, not threaded per-screen).

- [ ] **Step 3: Build the app**

`./gradlew :app:assembleDebug 2>&1 | tail -20`

- [ ] **Step 4: Commit + push**

```
feat(feedback): wire OnFeedbackCloseHandler default via Navigator.back()
```

---

## Task 8: Cloud function — label allowlist + deploy

**Files:** modify `functions/feedback/index.js`, `functions/feedback/README.md`.

- [ ] **Step 1: Filter labels in the handler**

Right after the payload validation in `index.js`, before the `octokit.issues.create` call:

```js
const ALLOWED_LABELS = new Set(['feedback', 'debug', 'bug', 'idea', 'other']);
const safeLabels = Array.isArray(labels)
    ? labels.filter(l => typeof l === 'string' && ALLOWED_LABELS.has(l)).slice(0, 5)
    : [];
```

Then pass `labels: safeLabels` to `octokit.issues.create`.

- [ ] **Step 2: Update `functions/feedback/README.md`**

Add a short note in the request body section: "`labels` is filtered server-side to the allowlist (`feedback`, `debug`, `bug`, `idea`, `other`); unknown labels are silently dropped."

- [ ] **Step 3: Verify the GitHub labels exist**

Run (do **not** chain commands — one Bash call per command):
- `gh label list --repo hluhovskyi/zero | grep -E '^(bug|idea|other|feedback|debug)\s'`

For any of `bug`, `idea`, `other` missing, ask the user to create them via:
`gh label create <name> --repo hluhovskyi/zero --color <hex> --description <desc>`

Do not auto-create; let the user pick colors.

- [ ] **Step 4: Deploy**

Stop and ask the user to run the deploy themselves — the exact `gcloud functions deploy ...` command from `functions/feedback/README.md` requires real values (region, SA email, project number, secret name) that vary by environment and shouldn't be hardcoded in a script. Provide the command for them to copy. Do not run it.

- [ ] **Step 5: Commit + push** (code + README only; deploy is separate)

```
feat(functions/feedback): allowlist labels (feedback/debug/bug/idea/other)
```

---

## Task 9: Verify — tests, lint, UI inspector

- [ ] **Step 1: Full unit-test suite**

`./gradlew testDebugUnitTest 2>&1 | tail -20`

Expected: BUILD SUCCESSFUL. If a test fails because it constructed `FeedbackViewModel.State()` positionally, add the new `type` argument explicitly.

- [ ] **Step 2: Lint**

`./gradlew lintDebug 2>&1 | grep -E "error:|Error" | head -20`

Expected: no new errors.

- [ ] **Step 3: Acquire an emulator**

`./scripts/emulator/acquire`

- [ ] **Step 4: Install the debug build**

`./scripts/install-app.sh`

- [ ] **Step 5: UI verification with `android-ui-inspector`**

Invoke `zero-project:android-ui-inspector` and:
1. From the Transactions tab, navigate to the Feedback sheet (shake gesture via `./scripts/ui/adb.sh shell input keyevent KEYCODE_..` — or use a debug entry point if the shake doesn't fire on the emulator).
2. Confirm the header shows close icon + centered "Send Feedback".
3. Tap each pill (Bug → Idea → Other) and confirm the selected pill turns white with `PrimaryContainer` border, the others stay grey.
4. Type in the textarea — the char counter increments. Past 900 chars, verify counter turns red.
5. With empty text, verify the Send button is grey/disabled. With text, verify it's `PrimaryContainer` (deep navy) with white "Send Feedback" label.
6. Tap the close icon — sheet should dismiss.

Capture one screenshot per pill state for the PR description.

---

## Self-Review

- **Spec coverage:** `FeedbackType` (Task 1), formatter labels (Task 2), ViewModel + close (Task 3), strings (Task 4), drawables (Task 5), ViewProvider (Task 6), DI wiring (Task 7), function allowlist + deploy (Task 8), verification (Task 9). ✓
- **Type analogs:** `OnFeedbackCloseHandler` follows `OnFeedbackSubmittedHandler` shape exactly. `FeedbackType` is a plain enum, no analog needed. Vector drawables follow Material standard layout (`ic_currency_24.xml` in the same folder). ✓
- **Plan length:** ~330 lines incl. ViewProvider code — under the 400-line limit set by `docs/agents/superpowers-workflow.md`. ✓
- **No churn:** the existing `OnFeedbackSubmittedHandler` / `Navigator.back()` plumbing, the `MainActivityScreenViewProvider` drag handle / scrim, and `OkHttpFeedbackService` are unchanged. ✓
- **User instructions respected:** screenshot/diagnostics toggles dropped. Bug/Idea/Other types implemented with GitHub labels. Cloud function deploy left to the user (no batched IAM/SA work). ✓
