# Implementation Plan: Fix Keyboard Overlap in Bottom Sheets and Pickers

The keyboard overlaps with bottom sheets and pickers, especially when searching. This plan adds `imePadding()` to ensure content shifts up when the keyboard is visible, and forced expansion to full screen for better visibility.

## Objective
Ensure that bottom sheets and pickers are fully visible when the keyboard is open, avoiding overlap with content like search bars and lists.

## Proposed Changes

### 1. `zero-core`: Add `imePadding()` to Picker Views
Add `imePadding()` to the root container of each picker view that can be shown in a bottom sheet. This will push the content up when the keyboard is visible.

- `zero-core/src/main/java/com/hluhovskyi/zero/categories/picker/CategoryPickerViewProvider.kt`: Add `imePadding()` to the root `Column`.
- `zero-core/src/main/java/com/hluhovskyi/zero/currencies/picker/CurrencyPickerViewProvider.kt`: Add `imePadding()` to the root `Column`.
- `zero-core/src/main/java/com/hluhovskyi/zero/icons/IconPickerViewProvider.kt`: Wrap `LazyVerticalGrid` in a `Box` with `imePadding()` and `fillMaxSize()`.
- `zero-core/src/main/java/com/hluhovskyi/zero/colors/ColorPickerViewProvider.kt`: Wrap `LazyVerticalGrid` in a `Box` with `imePadding()` and `fillMaxSize()`.
- `zero-core/src/main/java/com/hluhovskyi/zero/imports/categories/ImportCategoryPickerViewProvider.kt`: Add `imePadding()` to the root `Column`.

### 2. `app`: Add `imePadding()` to Bottom Sheet Host (Global Fix)
Add `imePadding()` globally in `MainActivityScreenViewProvider.kt` to all bottom sheet destinations. This ensures any future bottom sheets also handle the keyboard correctly.

- `app/src/main/java/com/hluhovskyi/zero/activity/screens/MainActivityScreenViewProvider.kt`: Wrap `entry.view.invoke()` in a `Box` with `Modifier.fillMaxSize().imePadding()` for `PartiallyVisible.BottomSheet` destinations.

### 3. Add `imePadding()` to other Edit Screens
To be consistent with `TransactionEditView`, add `imePadding()` to other screens where the keyboard might appear.

- `zero-core/src/main/java/com/hluhovskyi/zero/categories/edit/CategoriesEditViewProvider.kt`: Add `imePadding()`, `fillMaxSize()`, and `verticalScroll()`.
- `zero-core/src/main/java/com/hluhovskyi/zero/accounts/edit/AccountEditViewProvider.kt`: Add `imePadding()` to the root `Box`.
- `zero-core/src/main/java/com/hluhovskyi/zero/imports/ImportViewProvider.kt`: Wrap in a `Box` with `imePadding()`.

## Verification & Testing
1.  Open `TransactionEdit` screen.
2.  Tap on the Category picker.
3.  Tap on the Search bar in the category picker.
4.  **Verify**: The keyboard appears, and the category picker content shifts up, keeping the search bar and some categories visible.
5.  Repeat for the Currency picker.
6.  Open the Icon picker from `CategoryEdit` and verify it also behaves correctly if the keyboard was open.
