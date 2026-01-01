# Budget Loop Requirements Checklist

## Decisions (Locked for MVP)
- [x] Timezone is user-selected.
- [x] Daily spending budget is set by the user.
- [x] Daily budget edits apply immediately to today's remaining balance.
- [x] Bank accounts are overview-only and do not affect the daily budget.
- [x] Spendings are attached to the credit card used.
- [x] Overspend allows negative carry to next day.
- [x] Month-end surplus is saved and does not carry into the next month.
- [x] Default currency is user-selected; non-default accounts are converted to default.
- [x] FX rates are manually entered by the user for MVP via a provider abstraction.
- [x] OCR receipt scanning is deferred.
- [x] Fixed expenses are deferred.
- [x] Local storage uses Room KMP.
- [x] Notifications: daily end-of-day report at 8pm local time (user-configurable).
- [x] Savings tracker includes automatic budget savings plus manual savings entries.

## Core Loop and Budgeting
- [ ] Daily budget uses the user-defined amount in default currency.
- [ ] Rollover daily remaining to the next day (negative allowed).
- [ ] Month-end reset records surplus savings and resets carry to zero.
- [ ] If a scheduled reset is missed, apply it on the next app start.
- [ ] Support manual corrections for balances and transactions with adjustment logs.

## Onboarding and Accounts
- [ ] Onboarding flow: welcome -> currency -> daily budget -> cards -> optional banks.
- [ ] Allow multiple banks with name, currency, and current balance (overview only).
- [ ] Allow multiple credit cards with limit, debt, and currency.
- [ ] Allow saving entries (manual savings with amount, date, note).

## Dashboard UI ("Tank")
- [ ] Full-screen vertical progress bar that fills bottom-to-top as budget remains.
- [ ] Minimal overlay text for remaining daily budget and total bank status.
- [ ] Color gradient by danger levels (green -> yellow -> red).

## Transactions and Corrections
- [ ] Add manual expense entries with amount, date, optional category, and card used.
- [ ] Correction mode edits balances without breaking monthly summaries (define approach).

## OCR and Receipt Scanning (Deferred)
- [ ] "Scan Receipt" extracts total amount and date from image.
- [ ] Provide a review/edit screen before saving.

## Offline, Sync, and Notifications
- [ ] App works offline; sync only for currency rates or optional backups.
- [ ] Prompt for manual FX rates for non-default cards/accounts and store rates with source.
- [ ] Send daily end-of-day report notification at 8pm local time (user-configurable).

## Technical Requirements (KMP)
- [ ] Storage: Room KMP schema defined for core entities.
- [ ] Background tasks: WorkManager (Android) and BGTaskScheduler (iOS).
- [ ] Networking: Ktor for currency conversion.
- [ ] FX provider interface with `ManualFXProvider` default and swappable `DefaultFXProvider`.
