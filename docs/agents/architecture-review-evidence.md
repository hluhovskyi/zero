# Architecture Review — Evidence & Enforcement

Each rule in [architecture-review.md](architecture-review.md), grounded in a **canonical reference** (what it looks like done right in this codebase) and its **enforcement** (the custom lint detector that already checks it, where one exists).

**Why this matters for the review pass:** a rule with a lint detector is already machine-checked — don't spend the advisory pass re-flagging it; CI will. Spend the judgment on the rows marked **manual** — the structural smells lint can't see (sentinel params, boolean-as-type, special-case-as-branch, conflated responsibilities). Custom detectors live in `lint-rules/src/main/kotlin/com/hluhovskyi/zero/lint/`.

## House patterns

- **Feature triad (Component → ViewModel → ViewProvider + UseCase + Handler).** Reference: `zero-core/.../categories/edit/` — `CategoryEditComponent`, `CategoryEditViewModel` + `DefaultCategoryEditViewModel`, `CategoriesEditViewProvider`, `CategoryEditIconUseCase`, `OnCategorySavedHandler`. Enforced (partial): `ScopedComponentBuilderDetector`, `HandlerFunInterfaceDetector`.
- **ViewModel/ViewProvider no derivation; pre-shaped sealed rows.** Reference: `transactions/TransactionViewModel.kt:50` (`sealed interface Item`). Enforced: `StateCollectionDerivationDetector`, `ViewProviderDependencyDetector`.
- **Orchestration in a UseCase, not the ViewModel.** Reference: a `Default*UseCase` that sequences I/O + state (`imports/DefaultImportUseCase.kt`); the ViewModel is a thin projection (`home/DefaultHomeViewModel.kt`). Manual — no detector for "logic in the wrong member."
- **Side effects in `attach()`, never the constructor.** Reference: `home/DefaultHomeViewModel.kt:23` (`attachOnMain()` launches the collector); `presets/PresetsComponent.kt:93` (`PresetsAttachable.attach()` returns the `Closeable`). Enforced (lifecycle): `UnhandledCloseableDetector`, `UnhandledJobDetector`.
- **`@BindsInstance` for lightweight values only.** Reference: `categories/edit/CategoryEditComponent.kt:68` (binds handlers, not domain objects). Manual.
- **`internal` impls.** Reference: every `Default*ViewModel` / `*ViewProvider`. Enforced: `DefaultImplVisibilityDetector`, `ViewProviderVisibilityDetector`.
- **VM coroutines on `dispatchers.io()`; main for render + nav.** Reference: `home/DefaultHomeViewModel.kt`, `transactions/DefaultTransactionViewModel.kt`, `accounts/DefaultAccountViewModel.kt`. Manual.
- **Feature gets its own `@Component` + `@Scope`.** Reference: `categories/edit/CategoryEditComponent.kt:29` (`@CategoryEditScope`) + `:34` (`@dagger.Component`). Enforced: `ScopedComponentBuilderDetector`.
- **Default handlers at the binding layer (Navigator in scope).** Reference: `app/.../MainActivityScreenComponent.kt:227,349,356` (`onEditCategoriesHandler { navigator.navigateTo(...) }`). Manual.

## Module boundaries

- **`zero-core` cannot import `app`; cross-screen via handlers.** Reference: the `OnXxxHandler` fun interfaces in every feature package; `zero-core` has no `Navigator`/`Destinations` import. Enforced: `HandlerFunInterfaceDetector`, `KmpReadinessDetector`.
- **`zero-sync` / `zero-backup` are pure Kotlin JVM.** Reference: `zero-sync/build.gradle.kts:2` + `zero-backup/build.gradle.kts:2` apply `id("zero.kotlin.jvm")` (no Android plugin). Enforced: `RemoteComponentEncapsulationDetector`, `KmpReadinessDetector`.
- **`zero-database` encapsulation.** Enforced: `DatabaseComponentEncapsulationDetector`.
- **Theme tokens only — never hardcoded hex.** Reference: every `ViewProvider` reads `ZeroTheme.colors.*`. Enforced: `ZeroThemeBypassDetector`.
- **No hardcoded user-facing strings in composables.** Enforced: `HardcodedComposableStringDetector`, `UppercaseStringResourceDetector`.
- **Deps in the version catalog.** Reference: `gradle/libs.versions.toml`; modules reference `libs.*`. Manual (convention).
- **Test seam stays out of production paths.** Enforced: `TestBridgeBoundaryDetector`, `TestBridgeProductionPurityDetector`.

## Smell signatures — how the codebase guards each (and where it's manual)

- **Sentinel / ignored parameter.** **Manual** — no detector. (Review judgment.)
- **Boolean-as-type-discriminator.** Partial guard: `SealedSubtypeDuplicatePropertyDetector` nudges toward sealed modeling; the codebase prefers sealed `Item`/`State` over flags (`TransactionViewModel.kt:50`). The "flag switched at N sites" case is **manual**.
- **Duplicated decision.** **Manual** — pre-shaped `val`/sealed types on the producer are the intended fix (`StateCollectionDerivationDetector` pushes derivation off consumers, which removes one common source).
- **Abstraction one implementer fakes.** **Manual.**
- **Wrong-layer ownership (domain logic in a View).** Partial: `StateCollectionDerivationDetector` / `ViewProviderDependencyDetector` catch derivation/deps in views; cross-layer *ownership* of a decision is **manual**.
- **Special-case-as-branch.** **Manual.**
- **Conflated responsibilities in one unit.** **Manual.**
- **God interface / threaded value / downstream shim.** **Manual** — these are the "default at the binding layer" and "fix at the producer" judgment calls.

## Takeaway for the pass

Visibility, derivation, theme, module-encapsulation, handler-shape, scoped-builder, and closeable/job handling are **already enforced** — CI fails on them, so a manual finding there is redundant. The architecture-review pass earns its keep on the **Manual** rows: the structural, judgment-call smells no detector can express.
