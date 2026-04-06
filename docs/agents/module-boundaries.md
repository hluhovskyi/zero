# Module Boundaries

```
app
 └── zero-core      (ViewModels, UseCases, Components)
      └── zero-ui   (Design System, Composables: CategoryIconView, etc.)
      └── zero-api  (Domain interfaces: ColorRepository, ColorScheme, Id, etc.)
zero-image-loading  (ImageLoader interface + Coil impl)
```

- `zero-core` cannot import from `app` — no `Navigator`, no navigation types.
- `:zero-ui` is the host for the **Design System** (Theme, Color, Type, Shape) and dumb reusable Compose components. It has no dependencies on other `zero-*` modules — no domain types, no business logic.
- No Android framework dependencies in `zero-api`.

## Mechanical Enforcement

The above rules are enforced by custom Android Lint rules in the `:lint-rules` module.
Run `./gradlew :zero-core:lintDebug` to check. Violations are errors (build fails).

The 4 enforced invariants:
- `Default*` classes must be `internal` (`DefaultImplMustBeInternal`)
- `*ViewProvider` classes must be `internal` (`ViewProviderMustBeInternal`)
- `*ViewProvider` must not inject `*Repository` or `*UseCase` (`ViewProviderMustNotInjectRepository`)
- `On*Handler` interfaces must be `fun interface` (`HandlerMustBeFunInterface`)
