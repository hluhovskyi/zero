# Icon Picker — Expanded Personal Finance Icons

GitHub issue: [#53 Extend icon picker with different icons](https://github.com/hluhovskyi/zero/issues/53)

## Problem

The icon picker (`IconAndColorPicker`) currently exposes only 18 icons across 7 categories. Most categories have 1-3 icons, so users can't pick meaningful icons for common personal-finance categories (bills, rent, transport, etc.). The `flowers` icon is also miscategorized under Food & Drink.

## Goal

Expand the predefined icon set to ~82 icons across 12 categories, covering the common personal-finance domain (accounts, bills, transport, home, family, work, etc.) and fix the `flowers` miscategorization.

## Non-goals

- No changes to the picker UI/layout, search, or color scheme handling.
- No persistence/migration work — `PredefinedIconRepository` is a static, in-memory repository; moving `flowers` between categories does not affect any saved user data (user-selected icons are referenced by `Id`, not by category).
- No new public API surface on `IconCategory`/`Icon`.

## Final category layout

| # | Category | Existing icons (kept) | New icons |
|---|---|---|---|
| 1 | Money & Banking | cash, bank, credit_card, wallet, crypto, salary | savings, atm, currency_exchange, receipt_long, trending_up, account_balance_wallet, payments, monetization_on, paid, card_giftcard, balance, pie_chart, credit_score, request_quote |
| 2 | Food & Drink | grocery, fastfood *(flowers removed → Shopping)* | restaurant, local_cafe, local_bar, bakery_dining, lunch_dining, icecream, liquor |
| 3 | Travel | car, car_repair, beach | flight, hotel, luggage, hiking |
| 4 | Shopping | diamond, shopping_cart, **flowers** *(moved in)* | checkroom, redeem, devices, local_mall |
| 5 | Entertainment | game_controller, movie | music_note, headphones, theater_comedy, tv, confirmation_number |
| 6 | Education | book | school, menu_book, calculate |
| 7 | Health | health | medication, medical_services, fitness_center, spa, psychology |
| 8 | **Bills & Utilities** *(new)* | — | bolt, water_drop, wifi, smartphone, local_fire_department, subscriptions |
| 9 | **Home** *(new)* | (uses existing `ic_home_24`) | chair, bed, cleaning_services, build |
| 10 | **Transport** *(new)* | (uses existing `ic_gas_station_24`) | local_taxi, train, directions_bus, directions_bike, local_parking, two_wheeler |
| 11 | **Personal & Family** *(new)* | — | pets, child_care, family_restroom, volunteer_activism |
| 12 | **Work** *(new)* | — | work, business_center, computer, badge |

- **New drawables:** 66 (Material Symbols 24dp, fill style)
- **Re-used drawables:** 2 (`ic_home_24`, `ic_gas_station_24`)
- **Total icons in picker after change:** 82 (up from 18)

## Touched files

1. `app/src/main/res/drawable/ic_<name>_24.xml` — 66 new vector drawables. Each follows the existing pattern:
   ```xml
   <vector xmlns:android="http://schemas.android.com/apk/res/android"
       android:width="24dp"
       android:height="24dp"
       android:viewportWidth="24"
       android:viewportHeight="24"
       android:tint="?attr/colorControlNormal">
     <path android:fillColor="@android:color/white" android:pathData="..."/>
   </vector>
   ```
   `pathData` for each comes from the corresponding Material Symbols 24px (filled) icon.

2. `app/src/main/java/com/hluhovskyi/zero/icons/KnownIconIds.kt` — add an `Id.Known` constant per new icon, snake_case ids matching the resource basename (e.g. `Id("savings")`, `Id("local_taxi")`).

3. `app/src/main/java/com/hluhovskyi/zero/icons/PredefinedIconRepository.kt`
   - Add 5 new `IconCategory` values in the private `Categories` object: `billsUtilities`, `home`, `transport`, `personalFamily`, `work`.
   - Move `flowers` mapping from `Categories.foodDrink` to `Categories.shopping`.
   - Register every new icon in the `icons` map. Ordering within each category follows the table above — the picker preserves insertion order when grouping into sections.

4. (Possibly) `zero-core/src/test/java/com/hluhovskyi/zero/icons/...` — only if a test currently asserts the count or category membership of predefined icons. To check during implementation; tests that just look up by `Id` won't break.

## Icon source / path data

All path data is taken from the official Material Symbols 24px filled set (`material-symbols` / `material-design-icons` repo on GitHub). The same source the existing 18 icons come from (verified by inspecting `ic_book_24.xml`, `ic_gas_station_24.xml`, etc.).

## Verification

1. `./gradlew testDebugUnitTest` — full unit test pass.
2. `./gradlew lintDebug` — no new lint errors (Android Studio's vector lint validates `pathData`).
3. `./scripts/dump-ui.sh` / `android-ui-inspector` — open the icon picker on device and visually confirm:
   - All 12 sections render in order, with the right section titles.
   - Search filtering still works (type "card" → multiple results across categories).
   - Tapping a new icon selects it (border + tint change).
   - Flowers appears under Shopping, not Food & Drink.
4. Manual: open the picker from an Account edit screen and a Category edit screen to make sure both wiring paths still work.

## Out of scope / future work

- Rebalancing categories (e.g. car → Transport) is out of scope; current `car`/`car_repair` stay under Travel to avoid touching anything that might affect user data semantics in unexpected ways. Future cleanup can reorganize once we're sure no migration concerns exist.
- Per-account-type default icon mapping (`iconFor(AccountCategory)`) is unchanged — new account-type icons exist in the picker but aren't auto-selected for any `AccountCategory`.
