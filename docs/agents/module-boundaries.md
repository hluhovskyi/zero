# Module Boundaries

```
app
 ├── zero-core        (ViewModels, UseCases, Components)
 │    ├── zero-ui     (Design System, dumb Compose components, charts)
 │    ├── zero-api    (Domain interfaces: ColorRepository, ColorScheme, Id, etc.)
 │    └── zero-image-loading  (ImageLoader interface + Coil impl)
 ├── zero-database    (Room impls of zero-api repositories)
 ├── zero-sync        (pure Kotlin JVM — JSON export/import + LWW delta sync)
 ├── zero-backup      (pure Kotlin JVM — backup orchestration + Drive REST client)
 ├── zero-auth        (Google OAuth via Play services)
 ├── zero-remote      (server-side HTTP: feedback, exchange rates)
 ├── zero-crash       (CrashComponent — Sentry crash reporting, attach-only)
 └── zero-test-bridge (e2e test seam; ships in the APK, no test-framework deps)
```

- `zero-core` cannot import from `app` — no `Navigator`, no navigation types.
- `:zero-ui` is the host for the **Design System** (Theme, Color, Type, Shape) and dumb reusable Compose components. It has no dependencies on other `zero-*` modules — no domain types, no business logic.
- No Android framework dependencies in `zero-api`, `zero-sync`, or `zero-backup` — they are KMP-bound (enforced by `KmpReadiness` lint).

## zero-api Co-location Rule

**Any type referenced in a `zero-api` interface signature must itself live in `zero-api`** — placing it in the implementing module creates a circular dependency the moment any other module implements that interface. If you find yourself adding an import from `zero-sync` or `zero-database` inside `zero-api`, the type is in the wrong module.

## New Module Scaffolding

**Every new Gradle module needs a `module/.gitignore` containing `/build`** — without it, Gradle build outputs get staged by `git add` and the pre-commit hook blocks the commit.

## Mechanical Enforcement

Boundary and architecture rules are enforced by the custom lint registry in `:lint-rules`. The full rule list lives in [Architecture — Lint Enforcement](architecture.md#lint-enforcement) — the single source, pinned to `ZeroIssueRegistry` by `DocsConsistencyTest`; don't restate it here. Run `./gradlew lint` to check; violations are errors (build fails).

**New modules must wire `lintChecks(project(":lint-rules"))`** (JVM modules also apply `id("com.android.lint")`) — without it, none of the custom rules run for that module.
