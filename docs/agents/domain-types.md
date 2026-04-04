# Domain Types

All domain types live in `zero-api` (pure Kotlin, no Android dependencies).

## Id

Sealed type replacing nullable strings for entity identifiers.

```kotlin
sealed interface Id {
    object Unknown : Id           // null-equivalent
    data class Known(val value: String) : Id
}

// Construction
Id("abc")         // → Id.Known("abc")
Id(nullableStr)   // → Id.Known or Id.Unknown

// Extraction
id.valueOrNull()  // → String? (null if Unknown)
id.valueOrEmpty() // → String (empty string if Unknown)
(id as? Id.Known) // safe cast to get Known

// Convenience
fun Id.Known.Companion.transferIconId(): Id.Known  // well-known icon ID
```

**Rule:** Never use `null` for missing IDs — always `Id.Unknown`.

## Amount

Money value wrapping `BigDecimal`.

```kotlin
Amount.zero()                    // BigDecimal.ZERO
Amount(BigDecimal.TEN)           // from BigDecimal
Amount(42.5)                     // from Double
Amount(100L)                     // from Long
Amount(nullableBigDecimal)       // null → zero

amount1 + amount2                // addition
amount1 - amount2                // subtraction
amount.withRate(rate)            // multiply by rate
amount.value                     // raw BigDecimal
```

**Entity mapping:** `AmountEntity(value: BigDecimal)` for Room `@Embedded`.

## Rate

Currency exchange rate multiplier.

```kotlin
Rate.Same                        // 1.0 (same currency)
Rate(1.35)                       // from Double
Rate(BigDecimal.valueOf(1.35))   // from BigDecimal
Rate(nullableBigDecimal)         // null → Same

rate.value                       // raw BigDecimal
```

**Entity mapping:** `RateEntity(value: BigDecimal)` for Room `@Embedded`.

## Currency

```kotlin
data class Currency(
    override val id: Id.Known,
    val name: String,        // "US Dollar"
    val symbol: String       // "$"
) : Identifiable
```

## ColorScheme

Pair of domain colors for UI theming (categories, accounts).

```kotlin
data class ColorScheme(
    val primary: Color,
    val background: Color
) {
    companion object {
        val Grey: ColorScheme  // fallback — dark grey / light grey
    }
}

// Color
data class Color(
    val id: Id.Known,
    val value: ColorValue
)

// ColorValue — inline value class
@JvmInline
value class ColorValue(val hex: ULong) {
    fun isUnspecified(): Boolean  // true for 0x00000000UL
}

// Converting to Compose
ComposeColor(colorValue.hex.toInt())  // CORRECT: ARGB int
ComposeColor(colorValue.hex)          // WRONG: encodes colorspace bits
```

## Clock

Injectable time provider. Always use this instead of `LocalDateTime.now()`.

```kotlin
interface Clock {
    fun now(): ZonedDateTime
}

fun Clock.localDateTime(): LocalDateTime  // extension
```

## Identifiable

Marker interface for entities with an `Id.Known`.

```kotlin
interface Identifiable {
    val id: Id.Known
}
```

Used by `associateById()` Flow extension to build `Map<Id.Known, T>`.

## Flow Extensions

```kotlin
// Emit empty list before first real emission
fun <T> Flow<List<T>>.onStartWithEmptyList(): Flow<List<T>>

// If flow completes empty, emit empty list
fun <T> Flow<List<T>>.onEmptyReturnEmptyList(): Flow<List<T>>

// List<Identifiable> → Map<Id.Known, T>
fun <T : Identifiable> Flow<List<T>>.associateById(): Flow<Map<Id.Known, T>>
```

## Closeables

```kotlin
Closeables.empty()              // no-op closeable
Closeables.from { /* cleanup */ }
Closeables.of { coroutineScope.launch { ... } }  // cancels Job on close
```
