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
