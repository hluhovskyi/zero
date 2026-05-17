# FAB: collapsed default + animated expansion

## Problem

When the user switches bottom-bar tabs (Transactions / Accounts / Categories), the
floating action button flickers briefly from its extended (icon + text) form into
its collapsed (icon-only) form. The flicker is visible whenever the destination
tab eventually resolves to "collapsed" — i.e. for users who already have data.

### Root cause

Each of the three tab screens derives `fabExpanded` from view-model state:

| Screen | Expression |
|---|---|
| `TransactionViewProvider` | `transactions.isEmpty() && searchQuery.isBlank() && !activeFilter.isActive` |
| `AccountViewProvider` | `!state.hasAddedAccount` |
| `CategoryViewProvider` | `!state.hasAddedCategory` |

The ViewModel default `State()` values evaluate every expression to `true`:

- `TransactionViewModel.State(transactions = emptyList(), ...)` → expanded
- `AccountViewModel.State(hasAddedAccount = false, ...)` → expanded
- `CategoryViewModel.State(hasAddedCategory = false, ...)` → expanded

`collectAsState(initial = State())` returns that default on first composition,
so the FAB renders **expanded** for one frame even on tabs where the user has
data. When the real state arrives, `fabExpanded` flips to `false` and `ZeroFab`
hard-swaps `ExtendedFloatingActionButton` for `FloatingActionButton` — two
different composables, instant remount, visible flicker.

## Requirements

1. The FAB must default to **collapsed** on tab entry. It may transition to
   expanded only after the screen has confirmed the empty/welcoming state.
2. The expand/collapse transition must animate smoothly (bonus, but in scope).
3. No behavioural change to the six static `expanded = true` callers
   (Welcome / Transaction edit / Account edit + detail / Category edit + detail).

## Design

### Part 1 — Collapsed default (primary fix)

Invert the relevant ViewModel default values so the default `State()` evaluates
the expanded condition to `false`. Each ViewModel owns the derivation, so this
is a one-line change per file with no presenter logic to follow.

**`AccountViewModel.State`:**
```kotlin
val hasAddedAccount: Boolean = true  // was: false
```
The presenter already sets this to the real boolean once accounts load, so the
only behavioural difference is the initial render.

**`CategoryViewModel.State`:**
```kotlin
val hasAddedCategory: Boolean = true  // was: false
```
Same rationale.

**`TransactionViewModel.State`** — left unchanged. Instead of adding a
"data loaded" flag, `TransactionViewProvider` detects emptiness on the UI
side and only animates the FAB to expanded when `isEmpty` *transitions* from
false → true during the session. The initial emission is skipped.

```kotlin
val isEmpty = state.transactions.isEmpty() &&
    state.searchQuery.isBlank() &&
    !state.activeFilter.isActive

var fabExpanded by remember { mutableStateOf(false) }
LaunchedEffect(Unit) {
    snapshotFlow { isEmpty }
        .drop(1)  // skip initial value so cold mount doesn't expand
        .collect { fabExpanded = it }
}
```

On first composition `fabExpanded=false` → collapsed. The `drop(1)` skips
the initial synthetic emission. Subsequent real changes (user deletes their
last transaction, or filter/search clears down to an empty result) update
`fabExpanded` and the animated text reveal in `ZeroFab` fires.

**Trade-off:** if a user lands on the Transactions tab with a genuinely
empty list (cold mount, no data in DB), the FAB stays collapsed instead of
showing the "create your first transaction" expanded hint. Acceptable: the
discovery FAB lives on the Welcome screen (`HomeViewProvider` routes to
`welcomeComponent` when `isNewUser=true`), which is statically
`expanded=true` regardless.

### Part 2 — Animated transition

Refactor `ZeroFab` so the FAB body is a single composable that animates its
content, replacing the current `if (expanded) ... else ...` branch on two
distinct composables.

```kotlin
@Composable
fun ZeroFab(
    onClick: () -> Unit,
    icon: ImageVector,
    contentDescription: String,
    expanded: Boolean,
    text: String,
    modifier: Modifier = Modifier,
) {
    val elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 6.dp)
    FloatingActionButton(
        modifier = modifier,
        onClick = onClick,
        backgroundColor = PrimaryContainer,
        contentColor = Color.White,
        elevation = elevation,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.animateContentSize().padding(horizontal = 16.dp),
        ) {
            Icon(icon, contentDescription = contentDescription)
            AnimatedVisibility(visible = expanded) {
                Row {
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text)
                }
            }
        }
    }
}
```

Notes:

- `FloatingActionButton` is the underlying Material widget regardless of state.
  Its container grows/shrinks because its content Row uses `animateContentSize()`.
- `AnimatedVisibility` defaults are `fadeIn() + expandIn()` / `fadeOut() + shrinkOut()`
  which is exactly the desired horizontal text reveal.
- Padding mimics what `ExtendedFloatingActionButton` does internally
  (16dp horizontal). For the collapsed state the row's intrinsic content is
  just the icon, so `FloatingActionButton` clamps to its 56dp minimum.
- The six static `expanded = true` callers are unaffected at steady state —
  they always show the icon + text form. They never animate because `expanded`
  never changes.

### What we are NOT doing

- We are not introducing Material3. The codebase uses `androidx.compose.material`
  v1 throughout; migrating is out of scope.
- We are not adding a delay/debounce. Defaulting the state to "collapsed"
  is sufficient — no timing hacks required.
- We are not touching the three "empty-state" semantics. The FAB still becomes
  expanded when the screen is genuinely empty.

## Test plan

- **Unit:** existing `testDebugUnitTest` continues to pass. The state-default
  change should not regress any presenter test; if a test relied on the old
  defaults, fix it to reflect intent.
- **Lint:** `./gradlew lintDebug` clean.
- **UI inspector:** open the app on a populated database, switch tabs between
  Transactions ↔ Accounts ↔ Categories repeatedly. Confirm no extended-to-
  collapsed flicker on entry. Then verify the animated expansion still triggers
  on a fresh install (no data) and reverses smoothly when data is added.

## Risks

- **`animateContentSize()` jank:** `FloatingActionButton` has a fixed 56dp
  height. The width animation should be smooth, but on the first animation we
  may see a slight stutter on cold start. Acceptable; if it becomes a real
  issue we can switch to a custom `Surface` with explicit width animation.
