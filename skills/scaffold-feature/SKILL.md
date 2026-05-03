---
name: scaffold-feature
description: >
  Scaffold a new zero-core feature (Component + ViewModel + ViewProvider + Handlers).
  Use this whenever a plan adds a new screen or feature to zero-core. Generates compilable
  stubs so plans describe only business logic — not structural boilerplate.
---

# Scaffold Feature

Generate the structural stub for a new zero-core feature.

## Inputs

Clarify before generating (infer from context if obvious):

- **`name`** — PascalCase, e.g. `AccountSummary`
- **`package`** — subpath under `com.hluhovskyi.zero`, e.g. `accounts/summary`
- **`handlers`** — which to generate: `back`, `edit`, `saved` (default: `back` only)

If any input is ambiguous, ask — do not guess.

## Output files

All files go under `zero-core/src/main/java/com/hluhovskyi/zero/<package>/`.

### `<Name>ViewModel.kt`

```kotlin
package com.hluhovskyi.zero.<package_dotted>

import com.hluhovskyi.zero.common.AttachableActionStateModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

interface <Name>ViewModel : AttachableActionStateModel<<Name>ViewModel.Action, <Name>ViewModel.State> {

    sealed interface Action {
        // TODO: define actions
    }

    data class State(
        // TODO: define state fields
    )

    object Noop : <Name>ViewModel {
        override val state: Flow<State> = emptyFlow()
        override fun perform(action: Action) = Unit
        override fun attach(): java.io.Closeable = java.io.Closeable { }
    }
}
```

### `Default<Name>ViewModel.kt`

```kotlin
package com.hluhovskyi.zero.<package_dotted>

import com.hluhovskyi.zero.common.AttachableActionStateModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.Closeable

internal class Default<Name>ViewModel(
    // TODO: add dependencies
    private val coroutineScope: CoroutineScope,
) : <Name>ViewModel {

    private val _state = MutableStateFlow(<Name>ViewModel.State())
    override val state = _state.asStateFlow()

    override fun perform(action: <Name>ViewModel.Action) {
        // TODO: handle actions
    }

    override fun attach(): Closeable {
        val job = coroutineScope.launch {
            // TODO: launch data collection
        }
        return Closeable { job.cancel() }
    }
}
```

### `<Name>ViewProvider.kt`

```kotlin
package com.hluhovskyi.zero.<package_dotted>

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.hluhovskyi.zero.common.ViewProvider

internal class <Name>ViewProvider(
    private val viewModel: <Name>ViewModel,
) : ViewProvider {

    @Composable
    override fun View() {
        val state by viewModel.state.collectAsState(initial = <Name>ViewModel.State())
        // TODO: implement layout
    }
}
```

### `<Name>Component.kt`

```kotlin
package com.hluhovskyi.zero.<package_dotted>

import com.hluhovskyi.zero.common.AttachableViewComponent
import com.hluhovskyi.zero.common.Buildable
import com.hluhovskyi.zero.common.ViewProvider
import com.hluhovskyi.zero.common.coroutines.DispatcherProvider
import dagger.BindsInstance
import dagger.Provides
import java.io.Closeable
import javax.inject.Scope

@Scope
@Retention(AnnotationRetention.SOURCE)
private annotation class <Name>Scope

private const val TAG = "<Name>Component"

@<Name>Scope
@dagger.Component(
    modules = [<Name>Component.Module::class],
    dependencies = [<Name>Component.Dependencies::class],
)
abstract class <Name>Component : AttachableViewComponent {

    internal abstract val viewModel: <Name>ViewModel

    override val tag: String = TAG
    override fun attach(): Closeable = viewModel.attach()

    interface Dependencies {
        val dispatcherProvider: DispatcherProvider
        // TODO: add repository/use-case dependencies
    }

    companion object {
        fun builder(dependencies: Dependencies): Builder = Dagger<Name>Component.builder()
            .dependencies(dependencies)
            // TODO: set default handler noops
    }

    @dagger.Component.Builder
    interface Builder : Buildable<<Name>Component> {
        fun dependencies(dependencies: Dependencies): Builder
        // TODO: add @BindsInstance methods for Id and handlers
    }

    @dagger.Module
    object Module {

        @Provides
        @<Name>Scope
        fun viewModel(
            dispatcherProvider: DispatcherProvider,
            // TODO: add other dependencies
        ): <Name>ViewModel = Default<Name>ViewModel(
            coroutineScope = TODO("provide scope from dispatcherProvider"),
        )

        @Provides
        @<Name>Scope
        fun viewProvider(viewModel: <Name>ViewModel): ViewProvider =
            <Name>ViewProvider(viewModel = viewModel)
    }
}
```

### Handler files (one per requested handler)

```kotlin
// On<Name>BackHandler.kt
package com.hluhovskyi.zero.<package_dotted>

fun interface On<Name>BackHandler {
    fun onBack()
    companion object {
        val Noop = On<Name>BackHandler { }
    }
}
```

## Invariants to enforce in generated code

- `Default<Name>ViewModel` launches in `coroutineScope` (from `DispatcherProvider`) inside `attach()`, not in a constructor-level `init {}` block
- `Component.attach()` only attaches the ViewModel. If the ViewProvider later embeds another `AttachableViewComponent`, use `subComponent.AttachWithView()` in the composable — do NOT attach it in `Component.attach()`
- All `Default*` and `*ViewProvider` classes are `internal`
- `@BindsInstance` for IDs uses `Id`, not `Id.Known`
- Scope annotation is `private` in the component file

## After generating

1. Run `./gradlew :zero-core:compileDebugKotlin` — fix any compilation errors before proceeding
2. Report the exact file paths created
3. The implementation plan now only needs to specify:
   - ViewModel state fields and their data sources (which repository/use-case feeds each field)
   - Action → handler mapping
   - ViewProvider layout (Compose structure)
   - Any use cases to extract
