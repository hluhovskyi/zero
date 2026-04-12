# Bottom Sheet Drag Handle Design

## Background & Motivation

The project uses Material 1's `ModalBottomSheetLayout` through Accompanist Navigation. Unlike Material 3, it doesn't provide a built-in drag handle. A drag handle provides a visual cue that the sheet can be dragged up or down.

The user wants a drag handle that is visible when the sheet is not fully expanded (e.g., when it's `HalfExpanded`) and hidden once it is `Expanded`.

## Scope & Impact

This change affects all destinations registered with `NavigatorEntry.DisplayOption.PartiallyVisible.BottomSheet`. Currently, these are the following pickers:
- `CurrencyPicker`
- `CategoryPicker`
- `IconPicker`
- `ColorPicker`

## Proposed Solution

1. **New Component**: Create a `DragHandle` composable in `zero-ui`.
   - Size: 32x4 dp.
   - Shape: CircleShape.
   - Color: `OnSurfaceVariant` with 40% alpha.
   - Padding: 12 dp vertical.

2. **Integration**: Modify `MainActivityScreenViewProvider.kt` to include the `DragHandle` at the top of the bottom sheet content.
   - Use a `Column` to wrap the drag handle and the screen content.
   - Use `animateDpAsState` to hide the drag handle by reducing its height to 0.dp when `modalBottomSheetState.currentValue == ModalBottomSheetValue.Expanded`.
   - Ensure the screen content fills the remaining space using `Modifier.weight(1f)`.

## Alternatives Considered

- **Adding to each picker**: This would be redundant and harder to maintain.
- **Using a Box with Alignment.TopCenter**: This would overlap with the picker's content (e.g., the SearchBar).
- **Material 3 Migration**: Too big of a change for this task.

## Verification

- Verify on a device/emulator using `android-ui-inspector` (`./scripts/dump-ui.sh`).
- Check all 4 pickers to ensure the drag handle appears correctly.
- Test dragging the sheet from `HalfExpanded` to `Expanded` and confirm the handle hides.
