# Architecture Review

Catalog for an architecture pass over a diff: does it conform to the house patterns, and does it carry a structural smell? This is **not** a bug hunt (that's `/code-review`) and **not** style (that's spotless). It reasons about abstractions, boundaries, and responsibilities — so it must read the **touched modules**, not just the diff lines.

Findings are **advisory**, not blocking. Each finding names the symptom, the cause, and the smaller change that removes it — then lets a human decide.

Many house patterns are machine-enforced by custom lint detectors (see [Architecture — Lint Enforcement](architecture.md#lint-enforcement)) — don't re-flag what a detector already catches. This pass earns its keep on the smells lint can't express: the judgment calls in *Smell signatures* below.

## The method

- **Symptom → missing abstraction.** A good finding collapses several symptoms into one cause: a sentinel arg, a boolean flag, and a duplicated `when` are often three faces of one absent subtype split. Listing symptoms is noise; naming the abstraction is signal.
- **Read the module, not the line.** Most structural smells (a duplicated decision, a flag switched at three sites) span files the diff doesn't all touch. A diff-only read is half-blind — open the package(s) the change lives in.
- **Conformance first, then smells.** Check the change follows the house patterns below; then scan for the smell signatures. A change can compile, pass review, and still erode the architecture.
- **Cite, don't assert.** Quote the file:line that shows the symptom. No quote, no finding.

## House patterns — conform to these

- **Feature triad: Component → ViewModel → ViewProvider (+ optional UseCase).** A new screen that skips one, or puts logic in the wrong member, is off-pattern. Handlers (`fun interface OnXxxHandler`) carry screen-to-screen calls.
- **ViewModel is a thin projection — no derivation.** No `.filter`/`.sortedBy`/`.sumOf`/`map`/`if (raw.field != null)` in the ViewModel *or* ViewProvider. Pre-shape on the producer (sealed `Item` rows, computed `val` predicates/totals); the composable pattern-matches. Enforced by the `ViewProviderDerivation` lint — a finding here is a lint escape or a VM doing the derivation instead.
- **Orchestration lives in a UseCase, not the ViewModel.** A multi-step flow (load → transform → persist, with its state transitions) belongs in a UseCase; the ViewModel collects that state and dispatches actions. A ViewModel that sequences I/O is misplaced logic.
- **Side effects start in `attach()`, never in a constructor/`init`.** Resolve runtime state, launch collectors, kick off work from `attach()` (one-shot guard if it can re-run). Construction must be inert.
- **`@BindsInstance` for lightweight values only** — handler callbacks, `Id`s. Never repositories, domain objects, or formatters; those come through the `Dependencies` interface.
- **`internal` for impls** — `DefaultXxxViewModel`, `DefaultXxxUseCase`, `XxxViewProvider`. A `public` impl is a leak (Android Lint catches most).
- **VM coroutines on `dispatchers.io()`; main only for rendering + nav handlers.** Fix a blocking read at the source (`suspend fun` DAO query → Room dispatches on IO), not with a caller-side `flowOn`.
- **A feature with multiple bindings gets its own `@Component` + `@Scope`.** The parent sees a single feature-component binding — not a pile of raw `@Provides` for that feature in a shared module.
- **Pass Builders, not pre-built Components.** When something should be "provided higher up," lift the `@BindsInstance` entries to the caller; the component still receives `Builder`s.
- **Default handlers at the Builder `@Provides` layer.** When a handler does the same thing everywhere (e.g. navigate), set it as a default where the `Navigator` is in scope — don't thread it through every consumer, don't drop to `Noop`.

## House patterns — module boundaries

- **`zero-core` cannot import `app`** — no `Navigator`, no `Destinations`, no nav types. Cross-screen flow goes through handler callbacks.
- **`zero-sync` / `zero-backup` are pure Kotlin JVM** — no Android, no OkHttp, no `kotlinx-coroutines-android`. Enforced by encapsulation lint.
- **`zero-ui` holds dumb views** — shared Compose components, no domain types.
- **`zero-api` is pure interfaces + DTOs** — the contract layer; impls live in the platform modules.
- **An agnostic/producer module names no consumers** — not in code, not in comments. Consumer rationale goes in the commit/PR. A producer that special-cases "for the welcome screen" has the dependency backwards.
- **Theme tokens only — never a hardcoded hex.** Map design colors to `ZeroTheme.colors`; `ZeroThemeBypass` lint gates it.
- **Deps in the version catalog; shared build config in `build-logic` conventions.** An inline coordinate string or per-module config duplication is off-pattern.

## Smell signatures — flag these

- **Sentinel / ignored parameter.** A param passed only to satisfy a signature (a magic string the callee never reads, a `context` the implementation ignores). → The interface doesn't fit that implementer; it wants a different method/type.
- **Boolean (or enum) that is really a type discriminator.** A flag that travels *with* a polymorphic object and gets `when`/`if`'d at several call sites (an `isRecurring` on a budget, a `mode: String` switched in three places). → Make it a subtype; the type then *is* the flag.
- **Duplicated decision.** The same literal or condition decided in N places (a category's "is income" test repeated in the row, the total, and the filter). → One owner; everyone else reads it.
- **An abstraction one implementer fakes.** An impl that ignores a contract param, throws "not supported", or has a wildly different failure surface than its siblings (an in-memory provider next to one that does I/O and can fail mid-stream). → Split the abstraction along the real seam.
- **Speculative `*UseCase` interface.** A single-method `*UseCase` with one implementation and one caller in the same feature graph (often pre-declared by a plan). It earns no testability, no swapability, no contract. → Inline it into the VM (or call the repository directly); promote to a UseCase only on a real trigger — 2+ callers today, crosses a Dagger scope, ~300–400 LOC of mixed VM logic, or a documented module contract. See [architecture.md § When to introduce a UseCase](architecture.md#when-to-introduce-a-usecase).
- **Wrong-layer ownership.** A domain fact decided in a Composable/View (a `when (account.type)` deciding amount sign in UI), or business branching in `zero-ui`. → Push the decision down to where the data/domain lives.
- **Special-case-as-branch.** A conceptually separate operation living as an early-return branch inside a general engine (a one-off "clear all" fast-path buried in a per-row delete use case). → If it has different semantics, it's a different use case over the same engine.
- **Conflated responsibilities in one unit.** A class named for one job (`Parser`, `Mapper`, `Formatter`, `Repository`) that also does I/O, auth, or holds mutable state. → Name reveals intent; the extra job wants its own unit.
- **God interface / aggregate dependency.** One interface forcing unrelated consumers to depend on methods they don't use. → Split by consumer need.
- **Downstream shim instead of a producer fix.** A new adapter/Mapper that reshapes a value for one consumer when the producer could emit the canonical shape for all. → Fix at the producer.
- **Threading a value through every consumer.** A new constructor/builder arg added to many sites to carry one fact, when a default at the binding layer would do. → Hoist it.

## Output

For each finding:

```
[smell name] file:line
Symptom:  <the quoted line / the duplication, with paths>
Cause:    <the missing abstraction or misplaced responsibility>
Smaller change: <the one move that removes all the symptoms>
```

Lead with the highest-leverage finding (the one that collapses the most symptoms). Zero findings is a valid, common result for a well-scoped change — say so plainly rather than inventing one.
