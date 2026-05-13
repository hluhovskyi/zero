# Icon Picker — Personal Finance Icon Expansion

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add 66 new vector drawables + wire 2 existing unused drawables into the icon picker, organized across 5 new categories, taking the picker from 18 → 82 icons. Fix the `flowers` miscategorization (Food & Drink → Shopping).

**Architecture:** Vector drawables in `app/src/main/res/drawable/`. Each is a 24dp Material Symbols icon wrapped in the project's standard `<vector ... android:tint="?attr/colorControlNormal">` template (matches existing icons). Registered in `PredefinedIconRepository` via the `icons` map. Ids declared in `KnownIconIds`.

**Tech Stack:** Android Vector Drawable XML, Kotlin. Material Symbols (filled, 24px) as the visual source for path data.

**Spec:** [2026-05-12-icon-picker-finance-icons-design.md](../specs/2026-05-12-icon-picker-finance-icons-design.md)

---

## Source for path data

All new drawables use the **filled, 24px** Material Symbols icon set. The existing icons (`ic_book_24`, `ic_gas_station_24`, etc.) follow exactly this same source — verify by checking an existing file matches the upstream.

**Sourcing strategy:**

- Use `curl` to fetch each icon's Android XML from the official Material Symbols repo (single, well-known URL pattern).
- URL template (filled, 24px, Android Vector XML — verified working):

  ```
  https://raw.githubusercontent.com/google/material-design-icons/master/symbols/android/<symbol_name>/materialsymbolsoutlined/<symbol_name>_fill1_24px.xml
  ```

- The upstream file already produces a `<vector>` with `width=24dp`, `viewportWidth=24`, and a single `<path>` element.
- **Adapt** the fetched XML to the project's wrapper:
  - Set `android:tint="?attr/colorControlNormal"` on the root `<vector>` (upstream omits this).
  - Set `android:fillColor="@android:color/white"` on the `<path>` (upstream uses `#e3e3e3` or similar).
  - Keep the `pathData` exactly as upstream.

If a particular symbol's URL 404s for the filled variant, fall back to the legacy Material Icons path:
```
https://raw.githubusercontent.com/google/material-design-icons/master/android/<category>/<icon>/materialicons/24px.xml
```
The `<category>` segments are documented in `google/material-design-icons` README; try `action`, `maps`, `social`, `places`, `home`, etc. as needed.

Each drawable, once adapted, looks like:
```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24"
    android:tint="?attr/colorControlNormal">
  <path
      android:fillColor="@android:color/white"
      android:pathData="<from upstream>"/>
</vector>
```

---

## Icon manifest

`(local_resource_basename → Material Symbols name → category)`

**Money & Banking (14 new):**
- `savings` → `savings` → Money & Banking
- `atm` → `local_atm` → Money & Banking
- `currency_exchange` → `currency_exchange` → Money & Banking
- `receipt_long` → `receipt_long` → Money & Banking
- `trending_up` → `trending_up` → Money & Banking
- `account_balance_wallet` → `account_balance_wallet` → Money & Banking
- `payments` → `payments` → Money & Banking
- `monetization_on` → `monetization_on` → Money & Banking
- `paid` → `paid` → Money & Banking
- `card_giftcard` → `card_giftcard` → Money & Banking
- `balance` → `balance` → Money & Banking
- `pie_chart` → `pie_chart` → Money & Banking
- `credit_score` → `credit_score` → Money & Banking
- `request_quote` → `request_quote` → Money & Banking

**Food & Drink (7 new):**
- `restaurant` → `restaurant` → Food & Drink
- `local_cafe` → `local_cafe` → Food & Drink
- `local_bar` → `local_bar` → Food & Drink
- `bakery_dining` → `bakery_dining` → Food & Drink
- `lunch_dining` → `lunch_dining` → Food & Drink
- `icecream` → `icecream` → Food & Drink
- `liquor` → `liquor` → Food & Drink

**Travel (4 new):**
- `flight` → `flight` → Travel
- `hotel` → `hotel` → Travel
- `luggage` → `luggage` → Travel
- `hiking` → `hiking` → Travel

**Shopping (4 new):**
- `checkroom` → `checkroom` → Shopping
- `redeem` → `redeem` → Shopping
- `devices` → `devices` → Shopping
- `local_mall` → `local_mall` → Shopping

**Entertainment (5 new):**
- `music_note` → `music_note` → Entertainment
- `headphones` → `headphones` → Entertainment
- `theater_comedy` → `theater_comedy` → Entertainment
- `tv` → `tv` → Entertainment
- `confirmation_number` → `confirmation_number` → Entertainment

**Education (3 new):**
- `school` → `school` → Education
- `menu_book` → `menu_book` → Education
- `calculate` → `calculate` → Education

**Health (5 new):**
- `medication` → `medication` → Health
- `medical_services` → `medical_services` → Health
- `fitness_center` → `fitness_center` → Health
- `spa` → `spa` → Health
- `psychology` → `psychology` → Health

**Bills & Utilities (6 new, new category):**
- `bolt` → `bolt` → Bills & Utilities
- `water_drop` → `water_drop` → Bills & Utilities
- `wifi` → `wifi` → Bills & Utilities
- `smartphone` → `smartphone` → Bills & Utilities
- `local_fire_department` → `local_fire_department` → Bills & Utilities
- `subscriptions` → `subscriptions` → Bills & Utilities

**Home (4 new, new category, reuses `ic_home_24`):**
- `chair` → `chair` → Home
- `bed` → `bed` → Home
- `cleaning_services` → `cleaning_services` → Home
- `build` → `build` → Home

**Transport (6 new, new category, reuses `ic_gas_station_24`):**
- `local_taxi` → `local_taxi` → Transport
- `train` → `train` → Transport
- `directions_bus` → `directions_bus` → Transport
- `directions_bike` → `directions_bike` → Transport
- `local_parking` → `local_parking` → Transport
- `two_wheeler` → `two_wheeler` → Transport

**Personal & Family (4 new, new category):**
- `pets` → `pets` → Personal & Family
- `child_care` → `child_care` → Personal & Family
- `family_restroom` → `family_restroom` → Personal & Family
- `volunteer_activism` → `volunteer_activism` → Personal & Family

**Work (4 new, new category):**
- `work` → `work` → Work
- `business_center` → `business_center` → Work
- `computer` → `computer` → Work
- `badge` → `badge` → Work

Total: 66 new + 2 reused = 68 drawables in picker. Plus 14 already-wired icons = 82 picker icons.

---

## Task 1 — Verify clean baseline

**Files:** none

- [ ] Step 1: Run the existing tests to confirm a clean baseline.

  ```bash
  ./gradlew testDebugUnitTest 2>&1 | tail -5
  ```

  Expected: `BUILD SUCCESSFUL`. If it fails, stop and report — do not proceed until baseline is green.

---

## Task 2 — Create all 66 new vector drawables

**Files:** Create 66 files under `app/src/main/res/drawable/ic_<basename>_24.xml`, one per row in the icon manifest above.

- [ ] Step 1: For each manifest entry, fetch the upstream XML, adapt to the project wrapper, and save as `app/src/main/res/drawable/ic_<basename>_24.xml`.

  Use a single bash loop to fetch all 66, then post-process for the tint/fillColor wrapper. Recommended workflow (run from worktree root):

  ```bash
  # 1. Write the manifest as a tab-separated file: <basename>\t<symbol>
  cat > /tmp/icon_manifest.tsv <<'EOF'
  savings	savings
  atm	local_atm
  currency_exchange	currency_exchange
  receipt_long	receipt_long
  trending_up	trending_up
  account_balance_wallet	account_balance_wallet
  payments	payments
  monetization_on	monetization_on
  paid	paid
  card_giftcard	card_giftcard
  balance	balance
  pie_chart	pie_chart
  credit_score	credit_score
  request_quote	request_quote
  restaurant	restaurant
  local_cafe	local_cafe
  local_bar	local_bar
  bakery_dining	bakery_dining
  lunch_dining	lunch_dining
  icecream	icecream
  liquor	liquor
  flight	flight
  hotel	hotel
  luggage	luggage
  hiking	hiking
  checkroom	checkroom
  redeem	redeem
  devices	devices
  local_mall	local_mall
  music_note	music_note
  headphones	headphones
  theater_comedy	theater_comedy
  tv	tv
  confirmation_number	confirmation_number
  school	school
  menu_book	menu_book
  calculate	calculate
  medication	medication
  medical_services	medical_services
  fitness_center	fitness_center
  spa	spa
  psychology	psychology
  bolt	bolt
  water_drop	water_drop
  wifi	wifi
  smartphone	smartphone
  local_fire_department	local_fire_department
  subscriptions	subscriptions
  chair	chair
  bed	bed
  cleaning_services	cleaning_services
  build	build
  local_taxi	local_taxi
  train	train
  directions_bus	directions_bus
  directions_bike	directions_bike
  local_parking	local_parking
  two_wheeler	two_wheeler
  pets	pets
  child_care	child_care
  family_restroom	family_restroom
  volunteer_activism	volunteer_activism
  work	work
  business_center	business_center
  computer	computer
  badge	badge
  EOF
  ```

  Then for each entry, fetch and rewrite:

  ```bash
  while IFS=$'\t' read -r basename symbol; do
    out="app/src/main/res/drawable/ic_${basename}_24.xml"
    url="https://raw.githubusercontent.com/google/material-design-icons/master/symbols/android/${symbol}/materialsymbolsoutlined/${symbol}_fill1_24px.xml"
    raw=$(curl -fsSL "$url") || { echo "FAILED: $symbol ($url)" >&2; continue; }
    # Extract pathData (single path expected)
    pathdata=$(printf '%s' "$raw" | sed -nE 's/.*android:pathData="([^"]+)".*/\1/p' | head -1)
    if [ -z "$pathdata" ]; then echo "NO PATH: $symbol" >&2; continue; fi
    cat > "$out" <<EOF
  <vector xmlns:android="http://schemas.android.com/apk/res/android"
      android:width="24dp"
      android:height="24dp"
      android:viewportWidth="24"
      android:viewportHeight="24"
      android:tint="?attr/colorControlNormal">
    <path
        android:fillColor="@android:color/white"
        android:pathData="${pathdata}"/>
  </vector>
  EOF
  done < /tmp/icon_manifest.tsv
  ```

  Notes:
  - `sed -nE` here is a single command (no compound). One Bash call per loop step is fine, but using `bash -c '<loop>'` to keep it as one tool call is also acceptable; pick what is allowed.
  - If `set -o pipefail` is on and curl 404s, the loop continues thanks to `||`.

- [ ] Step 2: Verify all 66 files exist and contain non-empty `pathData`.

  ```bash
  ls app/src/main/res/drawable/ic_*_24.xml | wc -l
  ```

  Expected count: at least `28 (existing) + 66 (new) = 94`. If fewer, list missing names and re-fetch them.

  ```bash
  grep -L 'android:pathData="' app/src/main/res/drawable/ic_*_24.xml || echo "All have pathData"
  ```

  Expected output: `All have pathData`.

- [ ] Step 3: Spot-check one freshly created drawable matches the project wrapper format.

  Open one (e.g. `app/src/main/res/drawable/ic_savings_24.xml`) and confirm:
  - Root `<vector>` has `android:tint="?attr/colorControlNormal"`.
  - The `<path>` uses `android:fillColor="@android:color/white"`.
  - `pathData` is a non-empty string copied from upstream.

- [ ] Step 4: Commit the new drawables.

  ```bash
  git add app/src/main/res/drawable/ic_*_24.xml
  git commit -m "feat(icons): add 66 Material Symbols vector drawables for finance categories"
  ```

---

## Task 3 — Add new `Id.Known` constants in `KnownIconIds`

**Files:**
- Modify: `app/src/main/java/com/hluhovskyi/zero/icons/KnownIconIds.kt`

- [ ] Step 1: Append constants for every new icon plus the two existing-but-unwired icons. Use the existing camelCase convention (e.g. `accountBalanceWallet`, `directionsBus`), with `Id("<snake_case>")` matching the drawable basename.

  Full target file content:

  ```kotlin
  package com.hluhovskyi.zero.icons

  import com.hluhovskyi.zero.common.Id

  internal object KnownIconIds {
      val cash: Id.Known = Id("cash")
      val bank: Id.Known = Id("bank")
      val creditCard: Id.Known = Id("credit_card")
      val wallet: Id.Known = Id("wallet")
      val crypto: Id.Known = Id("crypto")
      val salary: Id.Known = Id("salary")
      val savings: Id.Known = Id("savings")
      val atm: Id.Known = Id("atm")
      val currencyExchange: Id.Known = Id("currency_exchange")
      val receiptLong: Id.Known = Id("receipt_long")
      val trendingUp: Id.Known = Id("trending_up")
      val accountBalanceWallet: Id.Known = Id("account_balance_wallet")
      val payments: Id.Known = Id("payments")
      val monetizationOn: Id.Known = Id("monetization_on")
      val paid: Id.Known = Id("paid")
      val cardGiftcard: Id.Known = Id("card_giftcard")
      val balance: Id.Known = Id("balance")
      val pieChart: Id.Known = Id("pie_chart")
      val creditScore: Id.Known = Id("credit_score")
      val requestQuote: Id.Known = Id("request_quote")

      val flowers: Id.Known = Id("flowers")
      val grocery: Id.Known = Id("grocery")
      val fastfood: Id.Known = Id("fastfood")
      val restaurant: Id.Known = Id("restaurant")
      val localCafe: Id.Known = Id("local_cafe")
      val localBar: Id.Known = Id("local_bar")
      val bakeryDining: Id.Known = Id("bakery_dining")
      val lunchDining: Id.Known = Id("lunch_dining")
      val icecream: Id.Known = Id("icecream")
      val liquor: Id.Known = Id("liquor")

      val car: Id.Known = Id("car")
      val carRepair: Id.Known = Id("car_repair")
      val beach: Id.Known = Id("beach")
      val flight: Id.Known = Id("flight")
      val hotel: Id.Known = Id("hotel")
      val luggage: Id.Known = Id("luggage")
      val hiking: Id.Known = Id("hiking")

      val diamond: Id.Known = Id("diamond")
      val shoppingCart: Id.Known = Id("shopping_cart")
      val checkroom: Id.Known = Id("checkroom")
      val redeem: Id.Known = Id("redeem")
      val devices: Id.Known = Id("devices")
      val localMall: Id.Known = Id("local_mall")

      val gameController: Id.Known = Id("game_controller")
      val movie: Id.Known = Id("movie")
      val musicNote: Id.Known = Id("music_note")
      val headphones: Id.Known = Id("headphones")
      val theaterComedy: Id.Known = Id("theater_comedy")
      val tv: Id.Known = Id("tv")
      val confirmationNumber: Id.Known = Id("confirmation_number")

      val book: Id.Known = Id("book")
      val school: Id.Known = Id("school")
      val menuBook: Id.Known = Id("menu_book")
      val calculate: Id.Known = Id("calculate")

      val health: Id.Known = Id("health")
      val medication: Id.Known = Id("medication")
      val medicalServices: Id.Known = Id("medical_services")
      val fitnessCenter: Id.Known = Id("fitness_center")
      val spa: Id.Known = Id("spa")
      val psychology: Id.Known = Id("psychology")

      val bolt: Id.Known = Id("bolt")
      val waterDrop: Id.Known = Id("water_drop")
      val wifi: Id.Known = Id("wifi")
      val smartphone: Id.Known = Id("smartphone")
      val localFireDepartment: Id.Known = Id("local_fire_department")
      val subscriptions: Id.Known = Id("subscriptions")

      val home: Id.Known = Id("home")
      val chair: Id.Known = Id("chair")
      val bed: Id.Known = Id("bed")
      val cleaningServices: Id.Known = Id("cleaning_services")
      val build: Id.Known = Id("build")

      val gasStation: Id.Known = Id("gas_station")
      val localTaxi: Id.Known = Id("local_taxi")
      val train: Id.Known = Id("train")
      val directionsBus: Id.Known = Id("directions_bus")
      val directionsBike: Id.Known = Id("directions_bike")
      val localParking: Id.Known = Id("local_parking")
      val twoWheeler: Id.Known = Id("two_wheeler")

      val pets: Id.Known = Id("pets")
      val childCare: Id.Known = Id("child_care")
      val familyRestroom: Id.Known = Id("family_restroom")
      val volunteerActivism: Id.Known = Id("volunteer_activism")

      val work: Id.Known = Id("work")
      val businessCenter: Id.Known = Id("business_center")
      val computer: Id.Known = Id("computer")
      val badge: Id.Known = Id("badge")
  }
  ```

- [ ] Step 2: Compile to verify nothing else broke.

  ```bash
  ./gradlew :app:compileDebugKotlin 2>&1 | tail -10
  ```

  Expected: `BUILD SUCCESSFUL`.

- [ ] Step 3: Commit.

  ```bash
  git add app/src/main/java/com/hluhovskyi/zero/icons/KnownIconIds.kt
  git commit -m "feat(icons): declare KnownIconIds for new finance picker icons"
  ```

---

## Task 4 — Register icons + categories in `PredefinedIconRepository`

**Files:**
- Modify: `app/src/main/java/com/hluhovskyi/zero/icons/PredefinedIconRepository.kt`

The end-state of the file. Replace the existing `Categories` object and `icons` map block. Keep `systemIcons`, `query()`, `iconFor()`, and `iconOf()` exactly as-is.

- [ ] Step 1: Replace the `Categories` private object with the expanded set:

  ```kotlin
  private object Categories {
      val moneyBanking = IconCategory("money_banking", "Money & Banking")
      val foodDrink = IconCategory("food_drink", "Food & Drink")
      val travel = IconCategory("travel", "Travel")
      val shopping = IconCategory("shopping", "Shopping")
      val entertainment = IconCategory("entertainment", "Entertainment")
      val education = IconCategory("education", "Education")
      val health = IconCategory("health", "Health")
      val billsUtilities = IconCategory("bills_utilities", "Bills & Utilities")
      val home = IconCategory("home", "Home")
      val transport = IconCategory("transport", "Transport")
      val personalFamily = IconCategory("personal_family", "Personal & Family")
      val work = IconCategory("work", "Work")
  }
  ```

- [ ] Step 2: Replace the `icons` map literal with the full insertion-ordered registry (Money & Banking → Food & Drink → Travel → Shopping → Entertainment → Education → Health → Bills & Utilities → Home → Transport → Personal & Family → Work). `flowers` moves to `Categories.shopping`.

  ```kotlin
  private val icons: Map<Id.Known, Icon> = mapOf(
      // Money & Banking
      iconOf(id = KnownIconIds.cash, resourceName = "ic_cash_24", description = "Cash", category = Categories.moneyBanking),
      iconOf(id = KnownIconIds.bank, resourceName = "ic_bank_24", description = "Bank", category = Categories.moneyBanking),
      iconOf(id = KnownIconIds.creditCard, resourceName = "ic_credit_card_24", description = "Credit card", category = Categories.moneyBanking),
      iconOf(id = KnownIconIds.wallet, resourceName = "ic_wallet_24", description = "Wallet", category = Categories.moneyBanking),
      iconOf(id = KnownIconIds.crypto, resourceName = "ic_crypto_24", description = "Crypto", category = Categories.moneyBanking),
      iconOf(id = KnownIconIds.salary, resourceName = "ic_salary_24", description = "Salary", category = Categories.moneyBanking),
      iconOf(id = KnownIconIds.savings, resourceName = "ic_savings_24", description = "Savings", category = Categories.moneyBanking),
      iconOf(id = KnownIconIds.atm, resourceName = "ic_atm_24", description = "ATM", category = Categories.moneyBanking),
      iconOf(id = KnownIconIds.currencyExchange, resourceName = "ic_currency_exchange_24", description = "Currency exchange", category = Categories.moneyBanking),
      iconOf(id = KnownIconIds.receiptLong, resourceName = "ic_receipt_long_24", description = "Receipt", category = Categories.moneyBanking),
      iconOf(id = KnownIconIds.trendingUp, resourceName = "ic_trending_up_24", description = "Investments", category = Categories.moneyBanking),
      iconOf(id = KnownIconIds.accountBalanceWallet, resourceName = "ic_account_balance_wallet_24", description = "Account balance", category = Categories.moneyBanking),
      iconOf(id = KnownIconIds.payments, resourceName = "ic_payments_24", description = "Payments", category = Categories.moneyBanking),
      iconOf(id = KnownIconIds.monetizationOn, resourceName = "ic_monetization_on_24", description = "Money", category = Categories.moneyBanking),
      iconOf(id = KnownIconIds.paid, resourceName = "ic_paid_24", description = "Paid", category = Categories.moneyBanking),
      iconOf(id = KnownIconIds.cardGiftcard, resourceName = "ic_card_giftcard_24", description = "Gift card", category = Categories.moneyBanking),
      iconOf(id = KnownIconIds.balance, resourceName = "ic_balance_24", description = "Balance", category = Categories.moneyBanking),
      iconOf(id = KnownIconIds.pieChart, resourceName = "ic_pie_chart_24", description = "Portfolio", category = Categories.moneyBanking),
      iconOf(id = KnownIconIds.creditScore, resourceName = "ic_credit_score_24", description = "Credit score", category = Categories.moneyBanking),
      iconOf(id = KnownIconIds.requestQuote, resourceName = "ic_request_quote_24", description = "Invoice", category = Categories.moneyBanking),

      // Food & Drink
      iconOf(id = KnownIconIds.grocery, resourceName = "ic_grocery_store_24", description = "Grocery", category = Categories.foodDrink),
      iconOf(id = KnownIconIds.fastfood, resourceName = "ic_fastfood_24", description = "Fast food", category = Categories.foodDrink),
      iconOf(id = KnownIconIds.restaurant, resourceName = "ic_restaurant_24", description = "Restaurant", category = Categories.foodDrink),
      iconOf(id = KnownIconIds.localCafe, resourceName = "ic_local_cafe_24", description = "Cafe", category = Categories.foodDrink),
      iconOf(id = KnownIconIds.localBar, resourceName = "ic_local_bar_24", description = "Bar", category = Categories.foodDrink),
      iconOf(id = KnownIconIds.bakeryDining, resourceName = "ic_bakery_dining_24", description = "Bakery", category = Categories.foodDrink),
      iconOf(id = KnownIconIds.lunchDining, resourceName = "ic_lunch_dining_24", description = "Lunch", category = Categories.foodDrink),
      iconOf(id = KnownIconIds.icecream, resourceName = "ic_icecream_24", description = "Ice cream", category = Categories.foodDrink),
      iconOf(id = KnownIconIds.liquor, resourceName = "ic_liquor_24", description = "Liquor", category = Categories.foodDrink),

      // Travel
      iconOf(id = KnownIconIds.car, resourceName = "ic_car_24", description = "Car", category = Categories.travel),
      iconOf(id = KnownIconIds.carRepair, resourceName = "ic_car_repair_24", description = "Car repair", category = Categories.travel),
      iconOf(id = KnownIconIds.beach, resourceName = "ic_beach_24", description = "Beach", category = Categories.travel),
      iconOf(id = KnownIconIds.flight, resourceName = "ic_flight_24", description = "Flight", category = Categories.travel),
      iconOf(id = KnownIconIds.hotel, resourceName = "ic_hotel_24", description = "Hotel", category = Categories.travel),
      iconOf(id = KnownIconIds.luggage, resourceName = "ic_luggage_24", description = "Luggage", category = Categories.travel),
      iconOf(id = KnownIconIds.hiking, resourceName = "ic_hiking_24", description = "Hiking", category = Categories.travel),

      // Shopping
      iconOf(id = KnownIconIds.diamond, resourceName = "ic_diamond_24", description = "Diamond", category = Categories.shopping),
      iconOf(id = KnownIconIds.shoppingCart, resourceName = "ic_shopping_cart_24", description = "Shopping cart", category = Categories.shopping),
      iconOf(id = KnownIconIds.flowers, resourceName = "ic_florist_24", description = "Flowers", category = Categories.shopping),
      iconOf(id = KnownIconIds.checkroom, resourceName = "ic_checkroom_24", description = "Clothing", category = Categories.shopping),
      iconOf(id = KnownIconIds.redeem, resourceName = "ic_redeem_24", description = "Gift", category = Categories.shopping),
      iconOf(id = KnownIconIds.devices, resourceName = "ic_devices_24", description = "Electronics", category = Categories.shopping),
      iconOf(id = KnownIconIds.localMall, resourceName = "ic_local_mall_24", description = "Mall", category = Categories.shopping),

      // Entertainment
      iconOf(id = KnownIconIds.gameController, resourceName = "ic_game_controller_24", description = "Game controller", category = Categories.entertainment),
      iconOf(id = KnownIconIds.movie, resourceName = "ic_movie_24", description = "Movie", category = Categories.entertainment),
      iconOf(id = KnownIconIds.musicNote, resourceName = "ic_music_note_24", description = "Music", category = Categories.entertainment),
      iconOf(id = KnownIconIds.headphones, resourceName = "ic_headphones_24", description = "Headphones", category = Categories.entertainment),
      iconOf(id = KnownIconIds.theaterComedy, resourceName = "ic_theater_comedy_24", description = "Theater", category = Categories.entertainment),
      iconOf(id = KnownIconIds.tv, resourceName = "ic_tv_24", description = "TV", category = Categories.entertainment),
      iconOf(id = KnownIconIds.confirmationNumber, resourceName = "ic_confirmation_number_24", description = "Tickets", category = Categories.entertainment),

      // Education
      iconOf(id = KnownIconIds.book, resourceName = "ic_book_24", description = "Book", category = Categories.education),
      iconOf(id = KnownIconIds.school, resourceName = "ic_school_24", description = "School", category = Categories.education),
      iconOf(id = KnownIconIds.menuBook, resourceName = "ic_menu_book_24", description = "Textbook", category = Categories.education),
      iconOf(id = KnownIconIds.calculate, resourceName = "ic_calculate_24", description = "Calculator", category = Categories.education),

      // Health
      iconOf(id = KnownIconIds.health, resourceName = "ic_health_24", description = "Health", category = Categories.health),
      iconOf(id = KnownIconIds.medication, resourceName = "ic_medication_24", description = "Medication", category = Categories.health),
      iconOf(id = KnownIconIds.medicalServices, resourceName = "ic_medical_services_24", description = "Medical", category = Categories.health),
      iconOf(id = KnownIconIds.fitnessCenter, resourceName = "ic_fitness_center_24", description = "Fitness", category = Categories.health),
      iconOf(id = KnownIconIds.spa, resourceName = "ic_spa_24", description = "Spa", category = Categories.health),
      iconOf(id = KnownIconIds.psychology, resourceName = "ic_psychology_24", description = "Therapy", category = Categories.health),

      // Bills & Utilities
      iconOf(id = KnownIconIds.bolt, resourceName = "ic_bolt_24", description = "Electricity", category = Categories.billsUtilities),
      iconOf(id = KnownIconIds.waterDrop, resourceName = "ic_water_drop_24", description = "Water", category = Categories.billsUtilities),
      iconOf(id = KnownIconIds.wifi, resourceName = "ic_wifi_24", description = "Internet", category = Categories.billsUtilities),
      iconOf(id = KnownIconIds.smartphone, resourceName = "ic_smartphone_24", description = "Phone", category = Categories.billsUtilities),
      iconOf(id = KnownIconIds.localFireDepartment, resourceName = "ic_local_fire_department_24", description = "Gas", category = Categories.billsUtilities),
      iconOf(id = KnownIconIds.subscriptions, resourceName = "ic_subscriptions_24", description = "Subscriptions", category = Categories.billsUtilities),

      // Home
      iconOf(id = KnownIconIds.home, resourceName = "ic_home_24", description = "Home", category = Categories.home),
      iconOf(id = KnownIconIds.chair, resourceName = "ic_chair_24", description = "Furniture", category = Categories.home),
      iconOf(id = KnownIconIds.bed, resourceName = "ic_bed_24", description = "Bed", category = Categories.home),
      iconOf(id = KnownIconIds.cleaningServices, resourceName = "ic_cleaning_services_24", description = "Cleaning", category = Categories.home),
      iconOf(id = KnownIconIds.build, resourceName = "ic_build_24", description = "Repairs", category = Categories.home),

      // Transport
      iconOf(id = KnownIconIds.gasStation, resourceName = "ic_gas_station_24", description = "Fuel", category = Categories.transport),
      iconOf(id = KnownIconIds.localTaxi, resourceName = "ic_local_taxi_24", description = "Taxi", category = Categories.transport),
      iconOf(id = KnownIconIds.train, resourceName = "ic_train_24", description = "Train", category = Categories.transport),
      iconOf(id = KnownIconIds.directionsBus, resourceName = "ic_directions_bus_24", description = "Bus", category = Categories.transport),
      iconOf(id = KnownIconIds.directionsBike, resourceName = "ic_directions_bike_24", description = "Bike", category = Categories.transport),
      iconOf(id = KnownIconIds.localParking, resourceName = "ic_local_parking_24", description = "Parking", category = Categories.transport),
      iconOf(id = KnownIconIds.twoWheeler, resourceName = "ic_two_wheeler_24", description = "Scooter", category = Categories.transport),

      // Personal & Family
      iconOf(id = KnownIconIds.pets, resourceName = "ic_pets_24", description = "Pets", category = Categories.personalFamily),
      iconOf(id = KnownIconIds.childCare, resourceName = "ic_child_care_24", description = "Childcare", category = Categories.personalFamily),
      iconOf(id = KnownIconIds.familyRestroom, resourceName = "ic_family_restroom_24", description = "Family", category = Categories.personalFamily),
      iconOf(id = KnownIconIds.volunteerActivism, resourceName = "ic_volunteer_activism_24", description = "Donations", category = Categories.personalFamily),

      // Work
      iconOf(id = KnownIconIds.work, resourceName = "ic_work_24", description = "Work", category = Categories.work),
      iconOf(id = KnownIconIds.businessCenter, resourceName = "ic_business_center_24", description = "Business", category = Categories.work),
      iconOf(id = KnownIconIds.computer, resourceName = "ic_computer_24", description = "Computer", category = Categories.work),
      iconOf(id = KnownIconIds.badge, resourceName = "ic_badge_24", description = "Badge", category = Categories.work),
  )
  ```

- [ ] Step 3: Compile.

  ```bash
  ./gradlew :app:compileDebugKotlin 2>&1 | tail -10
  ```

  Expected: `BUILD SUCCESSFUL`. If `MissingResource` or unresolved-id errors appear, list the missing drawables and check Task 2 outputs.

- [ ] Step 4: Run unit tests to make sure existing tests that reference `KnownIconIds` (e.g. `DefaultTransactionViewModelTest`, `DefaultAccountEditViewModelTest`, `DefaultImportUseCaseTest`) still pass.

  ```bash
  ./gradlew testDebugUnitTest 2>&1 | tail -10
  ```

  Expected: `BUILD SUCCESSFUL`. If any test fails, read its assertions — most likely it lookups an `Id.Known` that still exists in `KnownIconIds`, so no behavioral change is expected. If a test asserted the number of icons or the contents of a specific category, update the test to match new state (and re-confirm with the user before merging).

- [ ] Step 5: Commit.

  ```bash
  git add app/src/main/java/com/hluhovskyi/zero/icons/PredefinedIconRepository.kt
  git commit -m "feat(icons): register expanded finance icon catalog with 5 new categories"
  ```

---

## Task 5 — Verification

- [ ] Step 1: Full test pass.

  ```bash
  ./gradlew testDebugUnitTest 2>&1 | tail -5
  ```

  Expected: `BUILD SUCCESSFUL`.

- [ ] Step 2: Lint must be clean — no new errors.

  ```bash
  ./gradlew lintDebug 2>&1 | grep -E "error:|Error" | head -20
  ```

  Expected: empty output (or only pre-existing errors that also existed on master before this branch).

- [ ] Step 3: UI inspection via `zero-project:android-ui-inspector`. Install the app on the assigned emulator (`./scripts/acquire-emulator.sh` set `.emulator-serial`), navigate to **Settings → Categories → tap any category → tap the icon** (or **Accounts → edit account → tap icon**) to open the picker, then dump UI:

  ```bash
  ./gradlew :app:installDebug
  ./scripts/dump-ui.sh
  ```

  Confirm:
  - 12 section headers render: Money & Banking, Food & Drink, Travel, Shopping, Entertainment, Education, Health, Bills & Utilities, Home, Transport, Personal & Family, Work.
  - "Flowers" appears in Shopping (not Food & Drink).
  - Search "card" returns multiple matches across categories.
  - Tapping a new icon highlights it.
  - Scrolling the grid is smooth (no missing-resource crashes).

- [ ] Step 4: If everything passes, plan is complete. Otherwise file a follow-up subtask describing the failure.

---

## Self-review notes

- **Spec coverage:** Each of the 12 categories in the spec has its icons listed in the manifest and a registry entry in Task 4. Drawable creation is in Task 2. The `flowers` move is in Task 4 Step 2.
- **Placeholder scan:** No TBDs. All code blocks are concrete. Path data is fetched at execute time from a verified URL pattern.
- **Type/name consistency:** `KnownIconIds` field names in Task 3 match exactly the references used in Task 4. Resource basenames (Task 2 manifest) match `resourceName` strings in Task 4.
- **Out of scope reconfirmation:** No changes to `IconPickerViewModel`, `IconAndColorPicker`, or `iconFor(AccountCategory)`.
