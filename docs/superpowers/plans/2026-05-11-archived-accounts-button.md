# Archived Accounts Button Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add an expandable "Archived" toggle at the bottom of the accounts list that hides archived accounts from the main view and shows them on demand.

**Architecture:** All changes are in `AccountViewProvider.kt` (UI-only) and `strings.xml`. The main grouped list filters to active accounts only (`archivedAt == null`). A collapsible footer renders archived accounts in a de-emphasised style (dashed border, greyed icon, "ARCHIVED" badge) with animated chevron rotation.

**Tech Stack:** Jetpack Compose, `AnimatedVisibility`, `animateFloatAsState`, `drawBehind`, `PathEffect.dashPathEffect`, Material Icons

---

### Task 1: String resources

**Files:**
- Modify: `zero-core/src/main/res/values/strings.xml`

- [ ] **Step 1: Add strings**

In `strings.xml`, after the last `account_` string, add:
```xml
<string name="account_archived_show">Show archived · %1$d</string>
<string name="account_archived_hide">Hide archived · %1$d</string>
<string name="account_archived_hidden_notice">HIDDEN FROM TOTALS &amp; LISTS</string>
<string name="account_archived_badge">ARCHIVED</string>
```

- [ ] **Step 2: Commit**
```bash
git add zero-core/src/main/res/values/strings.xml
git commit -m "feat: add archived accounts UI string resources"
git push
```

---

### Task 2: Accounts screen — filter + archived footer

**Files:**
- Modify: `zero-core/src/main/java/com/hluhovskyi/zero/accounts/AccountViewProvider.kt`

**Design spec (from `ui_kits/zero/index.html`):**

Toggle button row:
- Top border: 1dp `SurfaceContainer`; 16dp top padding; centered row
- Archive icon (`Icons.Filled.Archive`): 14dp, `Outline` tint
- Label: `stringResource(R.string.account_archived_show/hide, count)`, 12sp, weight 600, `Outline`, letterSpacing 0.4.sp
- Chevron (`Icons.Filled.KeyboardArrowDown`): 14dp, `Outline`, rotates 0→180° over 200ms when expanded

Expanded content:
- Notice label: `stringResource(R.string.account_archived_hidden_notice)`, 10sp, weight 700, `Outline`, letterSpacing 1.2.sp, uppercase, centered
- Archived account row (78% alpha):
  - No fill; dashed border 1dp `OutlineVariant`, 12dp corner radius, drawn via `drawBehind`
  - Icon container: 36×36dp, 10dp corner, `SurfaceContainer` fill, icon 20dp `Outline` tint
  - Name: 14sp, weight 600, `OnSurfaceVariant`
  - Sub-row: "ARCHIVED" badge (9sp, weight 700, `Outline`, `SurfaceContainer` bg, 4dp radius) + detail if present (11sp, `Outline`)
  - Balance: 13sp, weight 600, `Outline`
  - Row click → `AccountViewModel.Action.Select(account.id)`

- [ ] **Step 1: Separate active and archived accounts; add `showArchived` state**

Replace the existing `grouped` / `remember` block in `AccountView` and add the new state:

```kotlin
val grouped = remember(state.accounts) {
    state.accounts
        .filter { it.archivedAt == null }
        .groupBy { it.category }
        .entries
        .sortedBy { it.key.ordinal }
}
val archivedAccounts = remember(state.accounts) {
    state.accounts.filter { it.archivedAt != null }
}
var expandedItemId: Id.Known? by remember { mutableStateOf(null) }
var showArchived by remember { mutableStateOf(false) }
```

- [ ] **Step 2: Add archived footer `item` to `LazyColumn`**

After the `grouped.forEach { ... }` block inside `LazyColumn`, add:

```kotlin
if (archivedAccounts.isNotEmpty()) {
    item(key = "archived_footer") {
        ArchivedFooter(
            archivedAccounts = archivedAccounts,
            showArchived = showArchived,
            onToggle = { showArchived = !showArchived },
            onAccountClick = { account ->
                viewModel.perform(AccountViewModel.Action.Select(account.id))
            },
            imageLoader = imageLoader,
            amountFormatter = amountFormatter,
        )
    }
}
```

- [ ] **Step 3: Add `ArchivedFooter` composable**

Add after `CategoryHeader`:

```kotlin
@Composable
private fun ArchivedFooter(
    archivedAccounts: List<Account>,
    showArchived: Boolean,
    onToggle: () -> Unit,
    onAccountClick: (Account) -> Unit,
    imageLoader: ImageLoader,
    amountFormatter: AmountFormatter,
) {
    val chevronRotation by animateFloatAsState(
        targetValue = if (showArchived) 180f else 0f,
        animationSpec = tween(200),
        label = "chevron",
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(top = 20.dp, bottom = 24.dp),
    ) {
        // Toggle row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .drawBehind {
                    val strokeWidth = 1.dp.toPx()
                    drawLine(
                        color = SurfaceContainer,
                        start = Offset(0f, 0f),
                        end = Offset(size.width, 0f),
                        strokeWidth = strokeWidth,
                    )
                }
                .padding(top = 16.dp)
                .clickable(onClick = onToggle)
                .padding(vertical = 10.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Filled.Archive,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = Outline,
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = if (showArchived) {
                    stringResource(R.string.account_archived_hide, archivedAccounts.size)
                } else {
                    stringResource(R.string.account_archived_show, archivedAccounts.size)
                },
                style = TextStyle(
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Outline,
                    letterSpacing = 0.4.sp,
                ),
            )
            Spacer(Modifier.width(6.dp))
            Icon(
                imageVector = Icons.Filled.KeyboardArrowDown,
                contentDescription = null,
                modifier = Modifier
                    .size(14.dp)
                    .rotate(chevronRotation),
                tint = Outline,
            )
        }

        // Expanded content
        AnimatedVisibility(visible = showArchived) {
            Column(modifier = Modifier.padding(top = 8.dp)) {
                Text(
                    text = stringResource(R.string.account_archived_hidden_notice),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    style = TextStyle(
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Outline,
                        letterSpacing = 1.2.sp,
                    ),
                    textAlign = TextAlign.Center,
                )
                archivedAccounts.forEach { account ->
                    ArchivedAccountRow(
                        account = account,
                        imageLoader = imageLoader,
                        amountFormatter = amountFormatter,
                        onClick = { onAccountClick(account) },
                    )
                }
            }
        }
    }
}
```

- [ ] **Step 4: Add `ArchivedAccountRow` composable**

Add after `ArchivedFooter`:

```kotlin
@Composable
private fun ArchivedAccountRow(
    account: Account,
    imageLoader: ImageLoader,
    amountFormatter: AmountFormatter,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 6.dp)
            .alpha(0.78f)
            .drawBehind {
                drawRoundRect(
                    color = OutlineVariant,
                    cornerRadius = CornerRadius(12.dp.toPx()),
                    style = Stroke(
                        width = 1.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f)),
                    ),
                )
            }
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(SurfaceContainer, RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center,
        ) {
            imageLoader.View(
                modifier = Modifier.size(20.dp),
                image = account.icon,
                tint = Outline,
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = account.name,
                style = TextStyle(
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = OnSurfaceVariant,
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Box(
                    modifier = Modifier
                        .background(SurfaceContainer, RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 1.dp),
                ) {
                    Text(
                        text = stringResource(R.string.account_archived_badge),
                        style = TextStyle(
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = Outline,
                            letterSpacing = 1.sp,
                        ),
                    )
                }
                if (account.details != null) {
                    Text(
                        text = account.details,
                        style = TextStyle(
                            fontSize = 11.sp,
                            color = Outline,
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        Text(
            text = amountFormatter.format(
                amount = account.balance,
                currencySymbol = account.currencySymbol,
            ),
            style = TextStyle(
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = Outline,
            ),
        )
    }
}
```

- [ ] **Step 5: Add new imports to `AccountViewProvider.kt`**

Add at the top of the imports section:
```kotlin
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.material.icons.filled.KeyboardArrowDown
import com.hluhovskyi.zero.ui.theme.Outline
import com.hluhovskyi.zero.ui.theme.OutlineVariant
import com.hluhovskyi.zero.ui.theme.SurfaceContainer
```

Note: `Box`, `Row`, `Column` and other layout imports are already present. Verify before adding duplicates.

- [ ] **Step 6: Build to verify compilation**
```bash
./gradlew :zero-core:assembleDebug 2>&1 | grep -E "error:|Error" | head -20
```
Expected: no errors.

- [ ] **Step 7: Run unit tests**
```bash
./gradlew testDebugUnitTest 2>&1 | tail -20
```
Expected: all tests PASS (no new tests needed — this is pure UI state with no business logic).

- [ ] **Step 8: Commit**
```bash
git add zero-core/src/main/java/com/hluhovskyi/zero/accounts/AccountViewProvider.kt
git commit -m "feat: add archived accounts expandable section to accounts screen"
git push
```
