# ImageLoader

## Interface

Single abstract method — `View(uri, contentDescription, modifier, scale, tint)`. Two top-level extension functions provide convenience overloads:

- `View(uri, ..., modifier = Modifier)` — for URI-based loading with defaults
- `View(image, ..., tint = null)` — for `Image` domain objects

Keep the interface method abstract (no body). Kotlin's `DefaultImpls` mechanism can dispatch to the interface body instead of the class override for `@Composable` methods with default parameters — making the method abstract forces correct dispatch.
