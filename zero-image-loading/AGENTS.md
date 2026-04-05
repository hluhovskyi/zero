# zero-image-loading — Agent Guide

Android library module. Image loading abstraction with Coil implementation.

## Rules

1. **No dependencies on other zero-* modules** — standalone module.
2. **Keep `ImageLoader` interface abstract** — no default method bodies on `@Composable` methods (DefaultImpls dispatch bug).
3. **Tint via `rememberAsyncImagePainter`** — Coil's `AsyncImage` doesn't support tint directly. Use the painter approach.

## What Lives Here

- `ImageLoader` interface — `@Composable fun View(uri, contentDescription, modifier, scale, tint)`
- Extension: `ImageLoader.View(image: Image, ...)` — convenience for domain `Image` objects
- `CoilImageLoader` — implementation using Coil
