# Welcome Screen + Home Composer

Branch: `worktree-welcome-screen`
Design: `Welcome Screen.html` from Claude Design archive `x6f4GBTvAWnNjaRcNMf0Gw`.

## Goal

When the user has no transactions, the Home tab shows a Welcome screen with an illustration, headline ("Your finances, one place."), a short instruction, and an "Import from another app" link. Once any transaction exists, the Home tab shows the existing TransactionComponent. The transaction component must remain almost untouched.

## Architecture

Two new features composed by a higher-level coordinator:

| Component | Module | Responsibility |
|-----------|--------|----------------|
| `WelcomeComponent` | zero-core/welcome | Pure UI: illustration + heading + "Import from another app" link. Emits `OnImportSelectedHandler`. |
| `HomeComponent` | zero-core/home | Owns `NewUserUseCase`; builds and renders `WelcomeComponent` or `TransactionComponent` based on `hasTransactions`. Adds the "Zero" title at the top. |
| `NewUserUseCase` | zero-api (interface) + zero-core (default) | Flow<Boolean> for `hasTransactions`. Backed by `TransactionRepository.Criteria.All` — emit `true` once the list is non-empty. |

`HomeComponent` is the new entry for `Destinations.Transaction.All`. The current `TransactionScreen` wrapper (which holds the ExtendedFloatingActionButton) keeps the FAB; only its inner content swaps from `TransactionComponent.AttachWithView()` to `HomeComponent.AttachWithView()`.

Composition pattern follows `AccountDetailComponent` — sub-components are provided in the parent's Module and attached via `AttachWithView()` in the parent's ViewProvider.

## Decision speed

`NewUserUseCase` returns a `Flow<Boolean>` with `hasTransactions` (true when ≥1 alive transaction exists). The Home `ViewModel.State` defaults to `hasTransactions = true` (assume returning user) so first-frame is the Transaction list — repeat users see no flash. New users see the Welcome screen as soon as the flow's first emission (empty list → false) arrives. No loading state is rendered.

## Files

### New — zero-api
- `zero-api/src/main/java/com/hluhovskyi/zero/transactions/NewUserUseCase.kt`
  - `fun query(): Flow<Boolean>` + `Noop` impl

### New — zero-core/welcome (canonical scaffold)
- `WelcomeComponent.kt`
- `WelcomeViewModel.kt` (interface + `Noop`)
- `DefaultWelcomeViewModel.kt`
- `WelcomeViewProvider.kt`

`WelcomeViewModel.Action` = `ImportSelected`. State holds nothing (Welcome is stateless). The action invokes the injected `OnImportSelectedHandler` on `Dispatchers.Main`.

### New — zero-core/home
- `HomeComponent.kt` — composes `WelcomeComponent.Builder` + `TransactionComponent.Builder`; depends on `NewUserUseCase`
- `HomeViewModel.kt` (interface + `Noop`)
- `DefaultHomeViewModel.kt` — collects `NewUserUseCase` into `State(hasTransactions: Boolean)`
- `HomeViewProvider.kt` — top "Zero" label + switcher

- `DefaultNewUserUseCase.kt` — implementation in zero-core (private to home or transactions package; place in `zero-core/.../transactions/` next to existing transaction code, or `zero-core/.../home/` — choose `home/` since it is a home-screen-specific aggregation)

### Modified — app/
- `MainActivityScreenComponent.kt`
  - Replace `transactionNavigationEntry` body: build `HomeComponent` instead of `TransactionComponent` directly; HomeComponent receives the same `OnTransactionSelectedHandler`, `TransactionFilterUseCase`, and a new `OnImportSelectedHandler` that calls `navigator.navigateTo(Destinations.Import)`
  - Add `homeComponentBuilder` + `welcomeComponentBuilder` to `Dependencies`
- `ActivityComponent.kt`
  - Add `HomeComponent.Dependencies` + `WelcomeComponent.Dependencies` to the parent class list
  - Add `homeComponentBuilder()` + `welcomeComponentBuilder()` provides in `Module`
  - Provide `NewUserUseCase` (via `@Provides` returning `DefaultNewUserUseCase`)
- `TransactionsScreen.kt` — **unchanged**. It still wraps the content with the FAB; only the `component` parameter type changes (already `Buildable<out AttachableViewComponent>`).

### Modified — strings
- `app/src/main/res/values/strings.xml`
  - `home_title` = "Zero"
  - `welcome_heading` = "Your finances,\none place."
  - `welcome_subtitle` = "Add your first transaction or bring in data from another app to get started."
  - `welcome_import_action` = "Import from another app"

## Layout — WelcomeViewProvider

Centered Column inside fillMaxSize, bottom padding ~72dp to leave room for the FAB:

```
Box (fillMaxSize, contentAlignment = Center)
  Column (horizontalAlignment = CenterHorizontally, padding 32dp horizontal)
    WelcomeIllustration()                          // 200x160 stacked cards
    Spacer 32dp
    Text  "Your finances,\none place."             // 24sp, ExtraBold, Primary, centered, lineHeight 1.2
    Spacer 10dp
    Text  subtitle                                  // 15sp, OnSurfaceVariant, centered, maxWidth ~260dp
    Spacer 28dp
    Row (clickable → ImportSelected)
      Icon ic_import_24, 16dp, tint PrimaryContainer
      Spacer 6dp
      Text "Import from another app"              // 14sp SemiBold PrimaryContainer
```

`WelcomeIllustration` composable inside the same file. Approximation of the HTML design — three rotated rounded-rect cards plus a small SecondaryContainer "+" badge in the bottom-right. Use existing theme colors (`PrimaryContainer`, `SecondaryContainer`, `Secondary`, a hardcoded `#C8D8FE` mid-card matches the design). No animation in v1.

## Layout — HomeViewProvider

```
Column (fillMaxSize)
  Text "Zero"                                 // 22sp ExtraBold Primary, padding 20dp/12dp
  Box (weight 1f)
    if (state.hasTransactions) transactionComponent.AttachWithView()
    else                       welcomeComponent.AttachWithView()
```

The "Zero" title sits above both branches. TransactionComponent remains responsible for its own search bar + filter row below the title.

## NewUserUseCase

`zero-api` interface:
```kotlin
interface NewUserUseCase {
    fun hasTransactions(): Flow<Boolean>
    object Noop : NewUserUseCase { override fun hasTransactions() = flowOf(true) }
}
```

`DefaultNewUserUseCase`:
```kotlin
internal class DefaultNewUserUseCase(
    private val transactionRepository: TransactionRepository,
) : NewUserUseCase {
    override fun hasTransactions(): Flow<Boolean> =
        transactionRepository.query(TransactionRepository.Criteria.All())
            .map { it.isNotEmpty() }
            .distinctUntilChanged()
}
```

Per [Data Layer](docs/agents/data-layer.md) `Criteria.All` already exists and uses the same Room flow Transaction list uses, so we add no new DAO query.

## HomeComponent wiring

Follows `AccountDetailComponent` pattern. Module provides:
- `welcomeComponent`: from `welcomeComponentBuilder` + `onImportSelectedHandler`
- `transactionComponent`: from `transactionComponentBuilder` + injected `OnTransactionSelectedHandler` + `TransactionFilterUseCase`
- `viewModel`: `DefaultHomeViewModel(newUserUseCase)`
- `viewProvider`: `HomeViewProvider(viewModel, welcomeComponent, transactionComponent)`

Builder `@BindsInstance`:
- `onTransactionSelectedHandler(OnTransactionSelectedHandler)`
- `onImportSelectedHandler(OnImportSelectedHandler)`
- `transactionFilterUseCase(TransactionFilterUseCase)`

Defaults wired in `companion object builder()` to `Noop`.

## Tests

- `DefaultNewUserUseCaseTest` — emits `false` when repo is empty, `true` once a transaction exists, distinctUntilChanged collapses duplicate emissions.
- `DefaultHomeViewModelTest` — `hasTransactions = false` after collection starts with empty stream.

(Welcome and Home ViewProviders are visual — covered by manual UI inspection, not unit tests.)

## Verification

1. `./gradlew testDebugUnitTest` — all pass
2. `./gradlew lintDebug` — no new errors
3. Build a debug APK or install; launch app:
   - With existing transactions: Home tab shows transaction list + "Zero" header + FAB (existing behaviour preserved, only "Zero" title is new).
   - With cleared transactions: Home tab shows Welcome illustration + heading + Import link + FAB.
   - Tap Import link → opens existing Import screen.
   - Tap FAB on either state → opens Add transaction (unchanged path).
4. `android-ui-inspector` — confirm both Welcome and Transaction states render with correct bounds; the "Zero" title is visible at top.

## Non-goals

- Animations on the Welcome illustration (design shows floatUp/pulse; v1 is static).
- Customising the FAB design — addressed separately per task instructions.
- ImportSheet scanning UX from the design — Import opens the existing Import screen.
