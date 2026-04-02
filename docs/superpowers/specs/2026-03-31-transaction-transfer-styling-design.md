# Design Spec: Transaction Transfer Styling

Style "Transfer" transactions to be visually consistent with "Expense" and "Income" types, using a "double-entry" layout where the destination account is the primary focus.

## 1. Objectives
- Align Transfer items in the transaction list with the established visual language.
- Use the destination account as the primary entry.
- Show both sides of the transfer (Source and Destination) clearly.
- Reuse and generalize existing UI components for better maintainability.

## 2. Architecture & Data Model

### 2.1 Icon Extension (`zero-api`)
Add a new standard icon ID to `IconRepository` for transfers.
- `IconRepository.transferIconId(): Id.Known` maps to `"transfer_icon"`.

### 2.2 ViewModel Enhancement (`zero-core`)
Update `TransactionViewModel.Item.Transaction.Transfer` to include icon and styling data required by the new UI.

**New fields in `Transfer`:**
- `transferIcon: Image` (Neutral transfer icon)
- `transferColorScheme: ColorScheme` (Set to `ColorScheme.Grey` for a neutral look)

### 2.3 Data Resolution (`zero-core`)
Update `DefaultTransactionViewModel.resolve()` for transfers:
- Resolve `transferIcon` using `idToIcons[IconRepository.transferIconId()]`.
- Use `ColorScheme.Grey` for `transferColorScheme`.

## 3. User Interface (`zero-ui`)

### 3.1 Generic `TransactionView`
Refactor the existing `TransactionView` in `TransactionExpenseView.kt` to use more generic parameter names, allowing it to serve as the base for all transaction types.

**Parameters:**
- `primaryText`: String (e.g., Category or Target Account)
- `primaryAmount`: String (e.g., Signed amount)
- `secondaryText`: String (e.g., Source Account)
- `secondaryAmount`: String? (e.g., Converted amount or Source amount)
- `mainIcon`: @Composable (The large circular icon)
- `mainIconColorScheme`: ColorScheme?

### 3.2 New `TransactionTransferView`
Implement `TransactionTransferView` by wrapping the generic `TransactionView`:
- **Main Icon:** `transferIcon` with `transferColorScheme` (Grey).
- **Primary Text:** `targetAccountName`.
- **Primary Amount:** `+ [targetAmount]` (Formatted with currency symbol).
- **Secondary Text:** `accountName` (Source account).
- **Secondary Amount:** `- [amount]` (Formatted with currency symbol).

## 4. Implementation Details (`app`)
Update `PredefinedIconRepository` to map `IconRepository.transferIconId()` to the existing `ic_transfer_24` drawable resource.

## 5. Testing & Validation
- **Visual Check:** Verify that Transfers now look like Expenses/Incomes in the list.
- **Data Correctness:** Confirm the Destination account is in the bold top row and the Source account is in the secondary bottom row.
- **Double Entry:** Ensure both `+` and `-` amounts are displayed correctly, especially when currencies differ.
- **Regression:** Ensure Expense and Income styling remains unchanged after the `TransactionView` refactor.
