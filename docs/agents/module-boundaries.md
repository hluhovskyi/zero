# Module Boundaries

```
app
 └── zero-core      (ViewModels, UseCases, Components)
      └── zero-ui   (Design System, Composables: CategoryIconView, etc.)
      └── zero-api  (Domain interfaces: ColorRepository, ColorScheme, Id, etc.)
zero-image-loading  (ImageLoader interface + Coil impl)
zero-crash          (CrashComponent — Sentry crash reporting, attach-only)
```

- `zero-core` cannot import from `app` — no `Navigator`, no navigation types.
- `:zero-ui` is the host for the **Design System** (Theme, Color, Type, Shape) and dumb reusable Compose components. It has no dependencies on other `zero-*` modules — no domain types, no business logic.
- No Android framework dependencies in `zero-api`.

## zero-api Co-location Rule

**Any type referenced in a `zero-api` interface signature must itself live in `zero-api`** — placing it in the implementing module creates a circular dependency the moment any other module implements that interface. If you find yourself adding an import from `zero-sync` or `zero-database` inside `zero-api`, the type is in the wrong module.

## New Module Scaffolding

**Every new Gradle module needs a `module/.gitignore` containing `/build`** — without it, Gradle build outputs get staged by `git add` and the pre-commit hook blocks the commit.

## Mechanical Enforcement

The above rules are enforced by custom Android Lint rules in the `:lint-rules` module.
Run `./gradlew :zero-core:lintDebug` to check. Violations are errors (build fails).

The 4 enforced invariants:
- `Default*` classes must be `internal` (`DefaultImplMustBeInternal`)
- `*ViewProvider` classes must be `internal` (`ViewProviderMustBeInternal`)
- `*ViewProvider` must not inject `*Repository` or `*UseCase` (`ViewProviderMustNotInjectRepository`)
- `On*Handler` interfaces must be `fun interface` (`HandlerMustBeFunInterface`)
