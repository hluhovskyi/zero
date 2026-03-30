# Transaction List — Neutral Icons Redesign

## Overview
Redesign transaction item visuals to match the "Updated Transactions List - Neutral Icons" Stitch design. Scope is limited to transaction item rows only (no header, filter tabs, or navigation changes).

## Changes

### Icon Container
- **Shape:** Squircle — `RoundedCornerShape(30%)` on the existing 40dp box (~12dp effective radius)
- **Background:** Neutral `#EFEDF2` (`surface_container` from design system)
- **Remove:** Dynamic `categoryColor` background — the category color is no longer used for the icon container

### Icon Tint
- Wrap the icon composable with a `LocalContentColor` override set to `#44464F` (`on_surface_variant`)
- This neutralizes the icon color regardless of the image resource's intrinsic tint

### Amount Colors
- **Expense:** `#BA1A1A` (design system `error` — red)
- **Income:** `#006C4A` (design system `secondary` — green)
- Fixes the existing `TODO` in `TransactionExpenseView` where expense color was `unspecified`

## Files

| File | Change |
|------|--------|
| `zero-ui/src/main/java/com/hluhovskyi/zero/transaction/TransactionExpenseView.kt` | All changes: container shape/color, icon tint wrapper, amount colors |

## Out of Scope
- Header, filter tabs, bottom navigation
- Status badges (VERIFIED, Processing, etc.)
- Transfer item layout
- Date summary row styling
- Data model changes
