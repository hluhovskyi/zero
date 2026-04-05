# Code Style

## Imports vs Fully Qualified Class Names
Always use imports instead of fully qualified class names in the code. This improves readability and is the standard convention for the project.

## Use `Clock` for Timestamps
Never call `LocalDateTime.now()` directly. Inject `Clock` and use `clock.localDateTime()`. This keeps code testable with fixed clocks.

## Use `Id.Unknown` Instead of `null`
For missing or unset identifiers, use `Id.Unknown` — never `null`. This is the null-equivalent across the codebase.
