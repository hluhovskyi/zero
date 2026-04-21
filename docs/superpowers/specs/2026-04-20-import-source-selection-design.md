# Import Source Selection — Design Spec

**Scope:** Step 0 of the import flow — the source selection screen only. Visual redesign only; no error handling changes.
**Reference:** Claude Design export `zero-design-system/project/Import Flow.html`

---

## What changes

The existing `SourceSelectionViewProvider` is a plain unstyled column with text rows. This spec replaces it with the polished card-based layout from the design.

---

## Visual layout

```
┌─────────────────────────────────────────┐
│  [×]      Import Data                   │  ← ModalHeader (existing component)
├─────────────────────────────────────────┤
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
- Background: `Surface` (#FAF8FD)
- Card background: `SurfaceContainerLowest` (#FFFFFF), `borderRadius = 12.dp`
- Card title: `OnSurface`, 15sp, SemiBold
- Card description: `OnSurfaceVariant`, 13sp
- Chevron: `OutlineVariant`
- Header close/title: `PrimaryContainer` (handled by `ModalHeader`)

**Source card icon specs:**

| Source | Icon | Icon bg | Icon tint |
|---|---|---|---|
| Zero Backup | `Icons.Filled.Backup` | `Color(0xFFE8EEFF)` | `PrimaryContainer` |
| ZenMoney CSV | `Icons.Filled.Description` | `Color(0xFFE8F5E9)` | `Color(0xFF1B5E20)` |

Icon container: 52×52dp, `RoundedCornerShape(14.dp)`, icon size 28dp.

**Hint row** (non-clickable, below the source cards):
- Row background: `SurfaceContainerLow`, `RoundedCornerShape(12.dp)`, padding `14.dp`/`16.dp`
- Small icon container: 32×32dp, `RoundedCornerShape(8.dp)`, `SurfaceContainer` bg
- `Icons.Filled.Add`, 18dp, `Outline` tint
- Label: "More sources coming soon", 13sp, `OnSurfaceVariant`

---

## Files changed

| File | Change |
|---|---|
| `zero-core/.../imports/sourceselection/SourceSelectionViewProvider.kt` | Full visual redesign |

All other files unchanged.

---

## Out of scope

- Error handling / snackbars
- Steps 1–4 (categories, accounts, transactions, done screens)
- The unified step-progress header (applies to steps 1–4, not step 0)
