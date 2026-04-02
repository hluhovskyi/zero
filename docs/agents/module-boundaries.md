# Module Boundaries

```
app
 └── zero-core      (ViewModels, UseCases, Components)
      └── zero-ui   (Design System, Composables: CategoryIconView, etc.)
      └── zero-api  (Domain interfaces: ColorRepository, ColorScheme, Id, etc.)
zero-image-loading  (ImageLoader interface + Coil impl)
```

- `zero-core` cannot import from `app` — no `Navigator`, no navigation types.
- `:zero-ui` is the host for the **Design System** (Theme, Color, Type, Shape) and all shared atomic/molecular UI components. This prevents circular dependencies when `zero-core` components need theme access.
- `zero-ui` and `zero-api` are shared; no Android framework dependencies in `zero-api`.
