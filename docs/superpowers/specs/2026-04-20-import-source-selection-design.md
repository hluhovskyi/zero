# Import Source Selection — Design Spec

**Scope:** Step 0 of the import flow — the source selection screen only.
**Reference:** Claude Design export `zero-design-system/project/Import Flow.html`

---

## What changes

The existing `SourceSelectionViewProvider` is a plain unstyled column with text rows. This spec replaces it with the polished card-based layout from the design, including error handling.

---

## Visual layout

```
┌─────────────────────────────────────────┐
│  [×]      Import Data                   │  ← ModalHeader (existing component)
├─────────────────────────────────────────┤
│  ┌── error banner (only when error) ──┐ │
│  │ [!]  File couldn't be read         │ │
│  │      The file appears corrupted…   │ │
│  │      [Try Again]  [Dismiss]        │ │
│  └────────────────────────────────────┘ │
│  Choose a data source. Zero will        │
│  preview what will be imported before   │
│  anything is saved.                     │
│                                         │
│  ┌─────────────────────────────────┐    │
│  │ [📦]  Zero Backup          [›]  │    │
│  │       Restore from a .zero      │    │
│  │       backup file               │    │
│  └─────────────────────────────────┘    │
│  ┌─────────────────────────────────┐    │
│  │ [📄]  ZenMoney CSV         [›]  │    │
│  │       Import transactions from  │    │
│  │       a ZenMoney export         │    │
│  └─────────────────────────────────┘    │
│                                         │
│  ┌─────────────────────────────────┐    │
│  │ [+]  More sources coming soon   │    │
│  └─────────────────────────────────┘    │
└─────────────────────────────────────────┘
```

**Design tokens** (all exist in `zero-ui/theme/Color.kt`):
- Card background: `SurfaceContainerLowest`, `RoundedCornerShape(12.dp)`
- Card title: `OnSurface`, 15sp, SemiBold
- Card description: `OnSurfaceVariant`, 13sp
- Chevron: `OutlineVariant`
- Header: handled by existing `ModalHeader`

**Source card icon specs:**

| Source | Icon | Icon bg | Icon tint |
|---|---|---|---|
| Zero Backup | `Icons.Filled.Backup` | `Color(0xFFE8EEFF)` | `PrimaryContainer` |
| ZenMoney CSV | `Icons.Filled.Description` | `Color(0xFFE8F5E9)` | `Color(0xFF1B5E20)` |

Icon container: 52×52dp, `RoundedCornerShape(14.dp)`, icon size 28dp.

**Hint row** (non-clickable):
- Background: `SurfaceContainerLow`, `RoundedCornerShape(12.dp)`, padding 14dp vertical / 16dp horizontal
- Icon container: 32×32dp, `RoundedCornerShape(8.dp)`, `SurfaceContainer` bg, `Icons.Filled.Add` 18dp `Outline` tint
- Label: "More sources coming soon", 13sp, `OnSurfaceVariant`

---

## Error banner

Shown above the subtitle when `state.error` is non-null. Matches design exactly.

**Visual:**
- Background: `ErrorContainer` (`#FFDAD6`), `RoundedCornerShape(12.dp)`, padding 14dp/16dp
- Leading icon container: 36×36dp, `RoundedCornerShape(10.dp)`, `Color(0xFFFFEBEE)` bg, error circle-X icon, `Error` tint
- Title: 14sp, Bold, `Error` color
- Detail: 13sp, `Color(0xFF93000A)`, lineHeight 1.45
- "Try Again" button: `Error` bg, `RoundedCornerShape(8.dp)`, 6dp/14dp padding, white bold 13sp text
- "Dismiss" button: transparent bg, `Error` color, 13sp SemiBold

**Actions:**
- "Try Again" → re-opens file picker for the same source (dispatches `Action.Retry`)
- "Dismiss" → clears error (dispatches `Action.DismissError`)

---

## State and logic changes

**`ImportUseCase.State.SourceSelection`** gets an `error` field:
```kotlin
data class SourceSelection(
    val sources: List<Source>,
    val error: String? = null,
) : State
```

**`ImportUseCase.Action`** gets two new actions:
```kotlin
object DismissError : Action
object Retry : Action
```

**`DefaultImportUseCase` — `SelectFile` handler** wraps parsing in try/catch:
```kotlin
try {
    val snapshot = parser.parse(action.uri)
    val delta = syncEngine.delta(snapshot, userId)
    // existing success path
} catch (e: Exception) {
    mutableState.update {
        InternalState(
            selectedSource = mutableState.value.selectedSource,
            screen = ImportUseCase.State.SourceSelection(
                sources = parsers.map { it.source },
                error = "Couldn't read file. Check the format and try again.",
            )
        )
    }
}
```

**`DismissError`** — clears error, stays on SourceSelection.
**`Retry`** — re-emits `FilePicker` state (re-opens file picker for same source).

`ImportViewModel.State.SourceSelection` mirrors the use case state (adds `error: String?`).
`ImportViewModel.Action` mirrors the new use case actions.
`ImportUseCase.Noop` implements the new actions as no-ops.

---

## Files changed

| File | Change |
|---|---|
| `zero-core/.../imports/ImportUseCase.kt` | `SourceSelection` state + `DismissError` / `Retry` actions |
| `zero-core/.../imports/DefaultImportUseCase.kt` | try/catch in `SelectFile`; handle `DismissError` and `Retry` |
| `zero-core/.../imports/ImportViewModel.kt` | Mirror new state field and actions |
| `zero-core/.../imports/DefaultImportViewModel.kt` | Map new state field and actions |
| `zero-core/.../imports/sourceselection/SourceSelectionViewProvider.kt` | Full visual redesign + error banner |
| `zero-core/.../imports/sourceselection/SourceSelectionViewModel.kt` | Add `error` to state; add `DismissError` / `Retry` actions |
| `zero-core/.../imports/sourceselection/DefaultSourceSelectionViewModel.kt` | Map new fields and actions |

---

## Out of scope

- Steps 1–4 (categories, accounts, transactions, done screens)
- The unified step-progress header (applies to steps 1–4, not step 0)
- Per-error-type messages (single generic message for now)
