# Module Boundaries

```
app
 └── zero-core      (ViewModels, UseCases, Components)
      └── zero-ui   (Composables: CategoryIconView, etc.)
      └── zero-api  (Domain interfaces: ColorRepository, ColorScheme, Id, etc.)
zero-image-loading  (ImageLoader interface + Coil impl)
```

- `zero-core` cannot import from `app` — no `Navigator`, no navigation types
- `zero-ui` and `zero-api` are shared; no Android framework dependencies in `zero-api`
