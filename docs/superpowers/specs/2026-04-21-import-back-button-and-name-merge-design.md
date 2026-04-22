---
date: 2026-04-21
topic: Import flow — hardware back button + name-based icon/color merge
---

# Design: Import Back Button & Name-Based Merge

## Overview

Two independent improvements to the import flow:

1. **Hardware back button** — system back press on steps 1–3 should navigate within the import flow, not close it.
2. **Name-based merge** — when building the categories/accounts preview, match by name against existing data. If a match exists, use its icon and color scheme (categories) or icon (accounts) for the display.

---

## Fix 1: Hardware Back Button

### Problem

`Destinations.Import` is a single navigation route. All four sub-screens (SourceSelection, CategoriesReview, AccountsReview, TransactionsPreview) are rendered by `ImportViewProvider` via a state switch. The icon back button on each step screen calls `viewModel.perform(Action.Back)` — which routes correctly through `DefaultImportUseCase.Back`. The hardware/gesture back, however, has no `BackHandler`, so it pops the entire navigation route.

### Fix

Add one `BackHandler` in `ImportViewProvider.ImportView()`:

```kotlin
BackHandler(enabled = state !is ImportViewModel.State.SourceSelection &&
                        state !is ImportViewModel.State.FilePicker) {
    viewModel.perform(ImportViewModel.Action.Back)
}
```

- **Disabled** on `SourceSelection`: system back pops the route (closes import) — correct.
- **Disabled** on `FilePicker`: the OS file picker dialog handles its own back; on dismiss, `fileLauncher` returns `null` and already calls `viewModel.perform(Back)`.
- **Enabled** on `Loading`, `CategoriesReview`, `AccountsReview`, `TransactionsPreview`: delegates to the existing use case back logic.

No ViewModel or UseCase changes needed.

---

## Fix 2: Name-Based Icon/Color Merge

### Problem

Import preview always shows icon/color from the imported file (or fallback defaults). If a category named "Food" already exists in the app with a custom icon and color, the import preview shows a different-looking icon — confusing for the user.

### Approach

At file-parse time, fetch all existing categories and accounts. Build name→entity lookup maps. When constructing display models (`ImportCategory`, `ImportAccount`), check for a name match (case-insensitive). If found, substitute the existing entity's icon and color data.

This is purely presentational: it does **not** affect what gets imported. The stored `SyncSnapshot` delta is unchanged.

### Data Flow

```
SelectFile coroutine
  ├── parse snapshot → delta
  ├── fetch existing categories (CategoryRepository.All)
  ├── fetch existing accounts (AccountRepository.All)
  ├── fetch all icons (IconRepository.All)           ← already done in buildCategories
  ├── store lookups in InternalState
  └── buildCategories(delta, lookups) → CategoriesReview state

ConfirmCategories
  └── buildAccounts(delta, lookups) → AccountsReview state   ← uses stored lookups

ConfirmAccounts / Back from TransactionsPreview
  └── buildAccounts(delta, lookups) → AccountsReview state
```

### Changes

#### `ImportDisplayModels.kt`
- Add `icon: Image?` to `ImportAccount` (null = no match, show generic icon in UI)

#### `DefaultImportUseCase.kt`
- Add `categoryRepository: CategoryRepository` and `accountRepository: AccountRepository` constructor params
- `InternalState` gains:
  - `existingCategoryByName: Map<String, CategoryRepository.Category> = emptyMap()`
  - `existingAccountByName: Map<String, AccountRepository.Account> = emptyMap()`
  - `allIconsById: Map<Id.Known, Icon> = emptyMap()`
- `SelectFile` coroutine pre-fetches all three maps and stores them on `InternalState`
- `buildCategories()` — if import category name (lowercased) matches existing category name (lowercased), use existing `iconId` + `colorId` instead of the imported values
- Extract `buildAccounts()` suspend helper that uses stored `existingAccountByName` + `allIconsById` to populate `ImportAccount.icon`
- `ConfirmCategories`, `ConfirmAccounts`, and the `Back` from `TransactionsPreview` all create `ImportAccount` — refactor to call `buildAccounts()`. The two sync-only paths (`ConfirmCategories` and `Back`) must become coroutine launches since `buildAccounts` needs icon lookup; alternatively pre-build accounts in `ConfirmCategories` coroutine and store in `InternalState`.

  **Preferred**: make `ConfirmCategories` a coroutine (same pattern as `SelectFile`), build and store the full `List<ImportAccount>` on `InternalState`. `ConfirmAccounts` and `Back` from `TransactionsPreview` reuse the stored list.

#### `ImportComponent.kt`
- Add `categoryRepository: CategoryRepository` and `accountRepository: AccountRepository` to `Dependencies`
- Pass both to `DefaultImportUseCase` in `Module.useCase()`
- Pass `imageLoader` to `AccountsReviewComponent.Builder` in `Module.accountsReviewComponentBuilder()`

> `ApplicationComponent` already provides both repos and implements `ImportComponent.Dependencies` — no changes needed in `ApplicationComponent`.

#### `AccountsReviewComponent.kt`
- Add `imageLoader: ImageLoader` to `Dependencies` interface
- Add `@BindsInstance fun imageLoader(imageLoader: ImageLoader): Builder` to `Builder`
- Pass `imageLoader` to `AccountsReviewViewProvider` in `Module.viewProvider()`

#### `AccountsReviewViewProvider.kt`
- Accept `imageLoader: ImageLoader`
- In `AccountRow`: when `account.icon != null`, render it via `imageLoader.View(...)` instead of the generic `AccountBalance` icon

---

## Non-Goals

- No changes to what data is actually imported (delta is unchanged)
- No "resolve" UI (new/merge/skip) — that is the next feature
- No fuzzy/partial name matching — exact case-insensitive only
