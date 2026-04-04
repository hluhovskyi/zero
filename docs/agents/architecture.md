# Architecture Patterns

## Component Pattern

Every feature is a Dagger `@Component` that wires together a ViewModel, optional UseCase, and ViewProvider. The component itself implements `AttachableViewComponent`.

```kotlin
@FeatureScope
@dagger.Component(
    modules = [FeatureComponent.Module::class],
    dependencies = [FeatureComponent.Dependencies::class]
)
abstract class FeatureComponent : AttachableViewComponent {

    override val tag: String = "FeatureComponent"

    // ViewModel drives attach() lifecycle
    internal abstract val viewModel: FeatureViewModel
    override fun attach(): Closeable = viewModel.attach()

    // What this component needs from the parent graph
    interface Dependencies {
        val someRepository: SomeRepository
        val imageLoader: ImageLoader
    }

    companion object {
        fun builder(dependencies: Dependencies): Builder =
            DaggerFeatureComponent.builder()
                .dependencies(dependencies)
                .onSavedHandler(OnSavedHandler.Noop)  // safe defaults
    }

    // Builder: Buildable<T> so NavigatorScope can rebuild per navigation
    @dagger.Component.Builder
    interface Builder : Buildable<FeatureComponent> {
        fun dependencies(dependencies: Dependencies): Builder

        @BindsInstance
        fun onSavedHandler(handler: OnSavedHandler): Builder
    }

    // Wiring: creates implementations and connects them
    @dagger.Module
    object Module {
        @Provides @FeatureScope
        fun viewModel(...): FeatureViewModel = DefaultFeatureViewModel(...)

        @Provides @FeatureScope
        fun viewProvider(...): ViewProvider = FeatureViewProvider(...)
    }
}
```

**Naming convention:**
- `FeatureComponent` — Dagger component
- `FeatureViewModel` — interface extending `AttachableActionStateModel<Action, State>`
- `DefaultFeatureViewModel` — implementation (always `internal`)
- `FeatureViewProvider` — Compose UI wrapper (always `internal`)
- `FeatureUseCase` / `DefaultFeatureUseCase` — optional, for complex business logic

## ViewModel Pattern

### Interface

```kotlin
interface FeatureViewModel :
    AttachableActionStateModel<FeatureViewModel.Action, FeatureViewModel.State> {

    sealed interface Action {
        data class SelectItem(val item: Item) : Action
        data object LoadMore : Action
    }

    data class State(
        val items: List<Item> = emptyList()
    )

    // Nested data types for UI items
    sealed interface Item { ... }
}
```

### Implementation

```kotlin
internal class DefaultFeatureViewModel(
    private val repository: SomeRepository,
    private val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.IO)
) : FeatureViewModel {

    private val mutableState = MutableStateFlow(FeatureViewModel.State())
    override val state: Flow<FeatureViewModel.State> = mutableState

    override fun perform(action: FeatureViewModel.Action) {
        when (action) {
            is FeatureViewModel.Action.SelectItem -> { /* handle */ }
            is FeatureViewModel.Action.LoadMore -> { /* handle */ }
        }
    }

    override fun attach(): Closeable = Closeables.of {
        coroutineScope.launch {
            // Combine multiple repository flows into state
            combine(
                repository.query(Criteria.All())
                    .onStartWithEmptyList()
                    .onEmptyReturnEmptyList(),
                otherRepository.query(...)
                    .onEmptyReturnEmptyList()
                    .associateById(),
            ) { items, lookup ->
                // Transform to UI state
            }.collectLatest { items ->
                mutableState.update { state ->
                    state.copy(items = items)
                }
            }
        }
    }
}
```

**Key patterns:**
- `MutableStateFlow` for state, exposed as `Flow<State>` (read-only)
- `attach()` returns `Closeables.of { coroutineScope.launch { ... } }` — the Job is cancelled on close
- `combine()` merges multiple repository flows reactively
- Use `onStartWithEmptyList()` and `onEmptyReturnEmptyList()` to handle initial/empty states
- Use `associateById()` to convert lists to maps for O(1) lookups
- `collectLatest` to update state (cancels previous collection if a new emission arrives)

## ViewProvider Pattern

```kotlin
internal class FeatureViewProvider(
    private val viewModel: FeatureViewModel,
    private val imageLoader: ImageLoader
) : ViewProvider {

    @Composable
    override fun View() {
        val state by viewModel.state.collectAsState(initial = FeatureViewModel.State())

        LazyColumn {
            items(state.items) { item ->
                ItemRow(item, imageLoader) {
                    viewModel.perform(FeatureViewModel.Action.SelectItem(item))
                }
            }
        }
    }
}
```

**Rules:**
- ViewProvider collects state via `collectAsState()`
- User interactions call `viewModel.perform(Action)`
- No business logic in ViewProvider — only rendering

## attach() Lifecycle

`attach()` starts subscriptions and returns a `Closeable` that cancels them.

**In Compose**, the `AttachWithView()` extension handles this automatically:
```kotlin
// NavigatorScope.composable — for screens with custom layout
navigatorScope.composable(Destinations.Feature.All) {
    FeatureScreen(component = componentBuilder.build())
}

// NavigatorScope.buildable — for simple component rendering
navigatorScope.buildable(Destinations.Feature.Edit) {
    componentBuilder.featureId(arguments.getValue(FeatureId)).build()
}
```

`AttachWithView()` calls `attach()` in a `DisposableEffect` and `close()` on dispose. Components built via `buildable()` are retained across recomposition.

## Handler Pattern (Screen Communication)

Screens communicate through handler callbacks, not shared state.

```kotlin
// Define handler (fun interface for lambda construction)
fun interface OnFeatureSavedHandler {
    fun onSaved()
    object Noop : OnFeatureSavedHandler { override fun onSaved() = Unit }
}

// Accept via @BindsInstance
@BindsInstance
fun onFeatureSavedHandler(handler: OnFeatureSavedHandler): Builder

// Wire in navigation (MainActivityScreenComponent.Module)
componentBuilder
    .onFeatureSavedHandler { navigator.back() }
    .build()

// Call from ViewModel/UseCase after action completes
repository.insert(item)
launch(Dispatchers.Main) {
    onFeatureSavedHandler.onSaved()
}
```

**Important:** Handlers that trigger navigation must dispatch on `Dispatchers.Main`.

## UseCase Pattern (Optional)

For screens with complex business logic (e.g., transaction editing with multiple types), extract a UseCase:

```kotlin
interface FeatureEditUseCase :
    ActionStateModel<FeatureEditUseCase.Action, FeatureEditUseCase.State> {

    sealed interface Action {
        data class ChangeField(val value: String) : Action
        data object Save : Action
    }

    sealed interface State {
        data class TypeA(...) : State
        data class TypeB(...) : State
    }
}
```

The UseCase handles complex state aggregation and repository interactions. The ViewModel delegates to it.

## Flow Composition Patterns

### Combining multiple data sources

```kotlin
combine(
    transactionRepository.query(TransactionRepository.Criteria.All()),
    accountRepository.query(AccountRepository.Criteria.All())
        .onEmptyReturnEmptyList()
        .associateById(),
    currencyRepository.query(CurrencyRepository.Criteria.All())
        .onEmptyReturnEmptyList()
        .associateById(),
) { transactions, accounts, currencies ->
    transactions.mapNotNull { tx ->
        val account = accounts[tx.accountId] ?: return@mapNotNull null
        val currency = currencies[tx.currencyId] ?: return@mapNotNull null
        // build UI item
    }
}
```

### Reactive + paginated data

The transaction list combines a reactive Room Flow (for new/updated items) with a one-shot paginated flow (for historical data), merging them by ID with explicit `updatedDateTime` comparison.
