Budget Loop

Version: 0.6 - Architecture & Logic Defined

1. Core Concept & Value Proposition

The "Loop" Logic: A daily budgeting system where unspent money rolls over to the next day.

The Weekly Reset: At the end of the week, the surplus does not carry over. instead, the user receives a "Reward/Summary" dialog: "You saved [Amount] this week!"

Goal: To gamify saving and help users recover from financial difficulty by focusing on daily wins rather than monthly complexity.

2. Visual Identity & User Interface

The "Tank" (Main Dashboard):

Concept: A full-screen, immersive vertical progress bar that fills the screen (including status bar).

Behavior: Fills from bottom to top. As money is spent, the "liquid" or bar level drops.

Overlays: Minimalist text floating over the background displaying:

Remaining Daily Budget.

Total Bank Status.

Aesthetics: Minimalist, perhaps subtle gradients or solid colors that change based on "danger" levels (e.g., Green -> Yellow -> Red).

3. User Flow

Welcome Screen:

Logo display.

Slogan: "Let's start the Loop."

Financial Setup (Onboarding):

Input Banks: User manually adds banks (Name + Current Balance).

Input Credit Cards: User adds credit card limits/current debt.

Fixed Expenses: User defines monthly recurring costs (Rent, Bills) to calculate the "True Daily Limit."

Currency Normalization:

User selects Default Currency.

Network Requirement: If user has multi-currency accounts, the app fetches rates to convert everything to the default currency.

Dashboard: The user enters the "Tank" view.

4. Key Features

Correction Mode: The ability to retrospectively edit bank balances or transactions if spending went "off the record."

Smart Entry (OCR):

User taps "Scan Receipt."

App uses Machine Learning to extract Total Amount and Date from the image to auto-fill the transaction.

Offline First: The app works primarily without internet, syncing only for currency rates or optional backups.

5. Technical Architecture (Kotlin Multiplatform)

Core Stack

Language: Kotlin

UI Framework: Compose Multiplatform (Android + iOS support).

Persistence: SQLDelight or Room (KMP) for local database.

Networking: Ktor (Currency conversion).

Platform Specific Implementations (Expect/Actual)

A. Background Tasks (The "Loop" Reset)

Requirement: At 00:00 (Midnight), calculate remaining budget and add to next day.

Common Interface: interface DailyResetScheduler { fun schedule() }

Android Implementation: WorkManager (PeriodicWorkRequest).

iOS Implementation: BGTaskScheduler (BackgroundTasks Framework).

B. OCR / Receipt Scanning

Requirement: Scan image to text.

Common Interface: interface ReceiptScanner { suspend fun scan(image: ImageBitmap): ReceiptData }

Android Implementation: Google ML Kit (Text Recognition).

iOS Implementation: Apple Vision Framework.

C. Notification Handling

Requirement: Notify user of weekly savings or low budget.

Implementation: Local Notifications (KMP libraries available, or native bridge).