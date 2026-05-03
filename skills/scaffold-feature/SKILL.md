---
name: scaffold-feature
description: >
  Scaffold a new zero-core feature (Component + ViewModel + ViewProvider + Handlers).
  Use this whenever a plan adds a new screen or feature to zero-core, or whenever the user says
  "add a new screen", "create a new feature", "scaffold X", or starts implementing anything that
  follows the Component/ViewModel/ViewProvider pattern. Generates compilable stubs so plans
  describe only business logic — not structural boilerplate.
---

# Scaffold Feature

Generate the structural stub for a new zero-core feature.

## Inputs

Clarify before generating (infer from context if obvious):

- **`name`** — PascalCase, e.g. `AccountSummary`
- **`package`** — slash-separated subpath under `com/hluhovskyi/zero/`, e.g. `accounts/summary`
- **`handlers`** — which to generate: `back`, `edit`, `saved` (default: `back` only)

If any input is ambiguous, ask — do not guess.

## Output files

All files go under `zero-core/src/main/java/com/hluhovskyi/zero/<package>/`.

The package name in the Kotlin files uses dots: `com.hluhovskyi.zero.<package_with_dots>`.

---

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

---

### `Default<Name>ViewModel.kt`

The scope uses a **defaulted constructor parameter** — this lets tests inject a `TestScope` without changing the production path.

```kotlin
package com.hluhovskyi.zero.<package_dotted>

import com.hluhovskyi.zero.common.Closeables
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import java.io.Closeable

internal class Default<Name>ViewModel(
    // TODO: add dependencies (repositories, use cases, handlers)
    private val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.IO),
) : <Name>ViewModel {

    private val mutableState = MutableStateFlow(<Name>ViewModel.State())
    override val state: Flow<<Name>ViewModel.State> = mutableState

    override fun perform(action: <Name>ViewModel.Action) {
        // TODO: handle actions — dispatch handlers on Dispatchers.Main
    }

    override fun attach(): Closeable = Closeables.of {
        coroutineScope.launch {
            // TODO: launch data collection (combine flows, update mutableState)
        }
    }
}
```

---

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

---

### `<Name>Component.kt`

```kotlin
package com.hluhovskyi.zero.<package_dotted>

import com.hluhovskyi.zero.common.AttachableViewComponent
import com.hluhovskyi.zero.common.Buildable
import com.hluhovskyi.zero.common.ViewProvider
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
        // TODO: add repository/use-case dependencies needed by the ViewModel
        // Note: do NOT add DispatcherProvider — ViewModels manage their own CoroutineScope
    }

    companion object {
        fun builder(dependencies: Dependencies): Builder = Dagger<Name>Component.builder()
            .dependencies(dependencies)
            // TODO: wire default handler Noops for each @BindsInstance handler
    }

    @dagger.Component.Builder
    interface Builder : Buildable<<Name>Component> {
        fun dependencies(dependencies: Dependencies): Builder
        // TODO: add @BindsInstance methods — use Id (not Id.Known) for ID parameters
    }

    @dagger.Module
    object Module {

        @Provides
        @<Name>Scope
        fun viewModel(
            // TODO: inject dependencies from Dependencies interface
        ): <Name>ViewModel = Default<Name>ViewModel(
            // TODO: pass dependencies; coroutineScope uses default, do not pass it
        )

        @Provides
        @<Name>Scope
        fun viewProvider(viewModel: <Name>ViewModel): ViewProvider =
            <Name>ViewProvider(viewModel = viewModel)
    }
}
```

---

### Handler files

Generate one file per requested handler. Example for `back`:

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

For `edit`: method name `onEdit()`. For `saved`: method name `onSaved()`.

---

## Key invariants

- **`CoroutineScope` uses default** — `= CoroutineScope(Dispatchers.IO)` in the constructor. Don't inject it via Dagger; the default lets tests substitute a `TestScope`.
- **`attach()` pattern** — always `Closeables.of { coroutineScope.launch { ... } }`. Not `Closeable { job.cancel() }`.
- **Handler dispatch** — actions that trigger navigation call `coroutineScope.launch(Dispatchers.Main) { handler.onXxx() }`.
- **No `DispatcherProvider` in Dependencies** — ViewModels are self-contained with their own scope.
- **`internal` on implementations** — `Default<Name>ViewModel` and `<Name>ViewProvider` are both `internal`.
- **Embedded sub-components** — if the ViewProvider needs to render another `AttachableViewComponent`, call `subComponent.AttachWithView()` in the composable. Do NOT attach it in `Component.attach()`.

---

## After generating

1. Run `./gradlew :zero-core:compileDebugKotlin` — fix any errors before proceeding
2. Report the exact file paths created
3. The implementation plan now only needs to specify:
   - ViewModel state fields and their data sources
   - Action → handler mapping
   - ViewProvider layout (Compose structure)
   - Any use cases to extract
