# Budget Loop MVP Spec

## Scope
Budget Loop MVP is a Kotlin Multiplatform app (Android + iOS) that tracks daily spending with a rollover budget, a month-end surplus summary, a savings tracker, and manual account updates. Bank accounts are overview-only. The app is offline-first and uses live currency rates only when needed.

## Locked Decisions
- Timezone is user-selected.
- Daily spending budget is set by the user.
- Daily budget edits apply immediately to today's remaining balance.
- Bank accounts are overview-only and do not affect the daily budget.
- Spendings are attached to the credit card used.
- Overspend carries forward as a negative daily balance.
- Month-end surplus is saved and does not carry into the next month.
- Default currency is user-selected; non-default accounts are converted to default.
- FX rates are manually entered by the user for MVP via a provider abstraction.
- OCR receipt scanning is deferred from MVP.
- Fixed expenses are deferred from MVP.
- Local storage uses Room KMP.
- Notifications: daily end-of-day report at 8pm local time (user-configurable).
- Savings tracker includes automatic budget savings plus manual savings entries.

## Core Calculations
All budget math is performed in the default currency.

Definitions:
- `dailyBudget` = user-defined amount in default currency.
- `bankTotal` = sum of bank account balances converted to default currency (overview only).
- `cardDebtTotal` = sum of credit card debts converted to default currency (overview only).
- `spentToday` = sum of today's transactions in default currency.

Daily starting allowance:
```
dailyRemaining = dailyBudget + carryFromYesterday
```

Derived remaining (for immediate budget edits):
```
dailyRemaining = dailyBudget + carryFromYesterday - spentToday
```

End-of-day rollover:
- At day change (midnight in user-selected timezone), set `carryFromYesterday = dailyRemaining` (negative allowed).

Month-end reset:
- If the previous day is the last day of the month, record `monthlySaved = max(carryFromYesterday, 0)` as a savings entry.
- Reset `carryFromYesterday = 0` to start the new month clean.
- Show "You saved [Amount] this month!" dialog on next app open.

Total bank status (for the dashboard overlay):
```
netStatus = bankTotal - cardDebtTotal
```

## User Flows
Onboarding:
1) Welcome -> select default currency.
2) Set daily budget (default currency).
3) Add credit cards (name, limit, debt, currency).
4) Optional: add bank accounts (name, balance, currency).
5) Enter the Tank dashboard.

Daily usage:
- Add expense (amount, date, optional category, card).
- Expense reduces `dailyRemaining` and increases selected card debt.
- Correction mode lets the user set a new bank balance or card debt; create an adjustment entry.
- Settings: user can edit daily budget and report time; changes apply immediately.

Month-end reset:
- Monthly summary dialog appears after month rollover.
- Surplus is recorded in the savings tracker and carry resets to zero.

## Currency Conversion
- Default currency chosen during onboarding and editable in Settings.
- When adding a non-default account or card, prompt for a manual FX rate and store it with a timestamp.
- If no rate is provided, block saving and show a "requires FX rate" message.
- FX rates are resolved through a provider interface for hot swapping:
  - `ManualFXProvider` (MVP) returns user-entered rates.
  - `DefaultFXProvider` (future) can fetch live rates.

## Data Model (Room KMP)
Minimum entities:
- `Settings(id, defaultCurrency, timezone, dailyBudget, reportTime)`
- `BankAccount(id, name, balance, currency)`
- `CreditCard(id, name, limit, debt, currency)`
- `Transaction(id, amount, amountDefault, currency, cardId, date, note, category)`
- `BalanceAdjustment(id, accountType, accountId, oldBalance, newBalance, date, note)`
- `FxRate(id, fromCurrency, toCurrency, rate, asOf, source)`
- `DailyBudgetState(id, date, dailyBudget, carry, remaining, monthlySaved)`
- `SavingsEntry(id, amount, date, source, note)`

Notes:
- Store `Transaction.amount` in the card currency and persist `amountDefault` using the FX rate at entry time for auditability.
- `DailyBudgetState` is updated on reset events and can be used to show history.
- `SavingsEntry.source` values: `month_end`, `manual`.

## Background Tasks
Expect/actual implementations:
- `DailyResetScheduler.schedule()` triggers end-of-day rollover and month-end summary logic.
- `DailyReportScheduler.schedule()` triggers the 8pm end-of-day report notification.
- Android: WorkManager periodic task (daily).
- iOS: BGTaskScheduler with best-effort timing.
- If a task is missed, run the reset on next app start.

## Notifications
- End-of-day report notification at 8pm local time with `dailyRemaining` and today's spent total.
- Monthly summary is an in-app dialog, not a push notification.

## Deferred (Non-MVP)
- OCR receipt scanning and receipt image storage.
- Cloud sync and authentication.
- Fixed expenses and recurring bill automation.
