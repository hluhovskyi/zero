# Bottom Sheet Drag Handle Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement a drag handle for all bottom sheet pickers that hides when the sheet is fully expanded.

**Architecture:** Add a new `DragHandle` component to `zero-ui` and integrate it into `MainActivityScreenViewProvider` using a `Column` layout for bottom sheet destinations. Animate the handle's height to zero based on the bottom sheet state.

**Tech Stack:** Jetpack Compose, Accompanist Navigation Material, Material 1.

---

### Task 1: Create `DragHandle` component in `zero-ui`

**Files:**
- Create: `zero-ui/src/main/java/com/hluhovskyi/zero/ui/DragHandle.kt`

- [ ] **Step 1: Implement `DragHandle` composable**

```kotlin
package com.hluhovskyi.zero.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.hluhovskyi.zero.ui.theme.OnSurfaceVariant

@Composable
fun DragHandle(
    modifier: Modifier = Modifier,
    color: Color = OnSurfaceVariant.copy(alpha = 0.4f),
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(width = 32.dp, height = 4.dp)
                .background(color = color, shape = CircleShape)
        )
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add zero-ui/src/main/java/com/hluhovskyi/zero/ui/DragHandle.kt
git commit -m "ui: add DragHandle component"
```

### Task 2: Integrate `DragHandle` in `MainActivityScreenViewProvider`

**Files:**
- Modify: `app/src/main/java/com/hluhovskyi/zero/activity/screens/MainActivityScreenViewProvider.kt`

- [ ] **Step 1: Add necessary imports**

```kotlin
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.layout.Column
import androidx.compose.material.ModalBottomSheetValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.alpha
import com.hluhovskyi.zero.ui.DragHandle
```

- [ ] **Step 2: Wrap bottom sheet content in `Column` with `DragHandle`**

Locate the `NavigatorEntry.DisplayOption.PartiallyVisible.BottomSheet` case in `MainActivityScreenViewProvider.kt` and replace the `Box` with a `Column` that includes the animated `DragHandle`.

```kotlin
// In MainActivityScreenViewProvider.kt

is NavigatorEntry.DisplayOption.PartiallyVisible.BottomSheet -> {
    bottomSheet(
        route = entry.route,
        arguments = navArguments,
    ) { backStackEntry ->
        val targetValue = modalBottomSheetState.targetValue
        val isExpanded = targetValue == ModalBottomSheetValue.Expanded
        val dragHandleHeight by animateDpAsState(
            targetValue = if (isExpanded) 0.dp else 24.dp,
            label = "DragHandleHeight"
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .imePadding(),
        ) {
            if (dragHandleHeight > 0.dp) {
                DragHandle(
                    modifier = Modifier
                        .height(dragHandleHeight)
                        .alpha(dragHandleHeight / 24.dp)
                )
            }
            Box(modifier = Modifier.weight(1f)) {
                entry.view.invoke(
                    BundleArguments(
                        bundle = backStackEntry.arguments,
                        destination = entry.destination,
                    ),
                )
            }
        }
    }
}
```

- [ ] **Step 3: Run project and verify pickers**

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/hluhovskyi/zero/activity/screens/MainActivityScreenViewProvider.kt
git commit -m "ui: add drag handle to pickers that hides when expanded"
```

### Task 3: Validation with `android-ui-inspector`

- [ ] **Step 1: Verify on device**
Run `./scripts/dump-ui.sh` and inspect the layout of a picker (e.g., Category Picker) in both HalfExpanded and Expanded states.

- [ ] **Step 2: Finalize**
Confirm that the drag handle is visible in HalfExpanded state and disappears when Expanded.
