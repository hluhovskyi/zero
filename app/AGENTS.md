# app — Agent Guide

Android application module. Entry point, navigation orchestration, and DI root.

## Rules

1. **Navigation lives here** — `Destinations`, `Navigator`, `MainActivityScreenComponent`. Feature modules (`zero-core`) cannot reference navigation types.
2. **Screen wiring in `MainActivityScreenComponent.Module`** — each screen is a `@Provides @IntoSet NavigatorEntry`. Use `navigatorScope.composable()` for custom layouts or `navigatorScope.buildable()` for component-only screens.
3. **Handler callbacks are wired here** — `onTransactionSavedHandler { navigator.back() }`, etc. This is where navigation actions connect to feature component handlers.
4. **DI root**: `ApplicationComponent → ActivityComponent → MainActivityScreenComponent → feature components`.

## What Lives Here

- **`ApplicationComponent`**: App-scoped DI. Database, repositories, image loading.
- **`ActivityComponent`**: Activity-scoped DI. Implements all feature `Dependencies` interfaces. Provides `Clock`, formatters, use cases.
- **`MainActivityScreenComponent`**: Navigation wiring. Creates `NavigatorEntry` for each screen, wires handler callbacks, manages `NavHostController`.
- **`Destinations`**: All route definitions with typed arguments.
- **Screen composables**: `TransactionsScreen.kt`, `CategoriesScreen.kt`, `AccountsScreen.kt` — thin wrappers that add FABs, backgrounds, etc.

## Adding a New Screen

1. Define `Destination` in `Destinations.kt`
2. Add `NavigatorEntry` in `MainActivityScreenComponent.Module` as `@Provides @IntoSet`
3. Wire the feature component's builder with handler callbacks
4. If needed, add bottom bar entry in `BottomBarComponent`
5. Ensure `ActivityComponent` implements the feature's `Dependencies` interface

## Navigation Patterns

```kotlin
// Simple screen (no arguments)
navigatorScope.composable(Destinations.Feature.All) {
    FeatureScreen(component = builder.build())
}

// Screen with arguments
navigatorScope.buildable(Destinations.Feature.Item.Edit) {
    builder
        .featureId(arguments.getValue(Destinations.Feature.Item.FeatureId))
        .onSavedHandler { navigator.back() }
        .build()
}

// Navigate to screen
navigator.navigateTo(Destinations.Feature.Item.Edit,
    Destinations.Feature.Item.FeatureId.withValue(id))

// Go back
navigator.back()
```
