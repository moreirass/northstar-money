# Northstar Money

Northstar Money is a private, local-first personal finance app for Android, built with Kotlin and Jetpack Compose. Financial data stays on the device unless you explicitly export it; the app has no account sign-in, cloud sync, bank connection, ads, or analytics.

> **Status: Alpha (`0.1.0`).** The offline feature set and data-safety foundations are implemented, but the app has not completed release hardening or production distribution. Keep regular encrypted backups and do not treat an alpha build as the only copy of important financial records.

## What works today

### Everyday finances

- Track cash, checking, savings, and credit accounts in multiple currencies
- Add and edit income, expenses, and transfers
- Record cross-currency transfers with separate sent and received amounts
- Search transactions, mark them cleared, and reconcile accounts against statements
- Recover recently deleted transactions and restore archived accounts
- Manage income and expense categories, including archive, restore, and reversible merges
- Store monetary values as integer minor units rather than floating-point numbers

### Planning and reporting

- Set monthly category budgets with rollover from the previous month
- Track savings goals and editable contribution histories
- Create and edit debt profiles with interest rates, minimum payments, and due dates
- Schedule recurring transactions, pause/resume them, and automatically post due occurrences
- View account totals, monthly cash flow, accessible charts, and a 30-day forecast
- Export transactions to CSV and a monthly summary to PDF
- Import validated CSV transactions atomically, with duplicate detection

### Privacy, safety, and accessibility

- Password-protected, portable full-database backups
- Validated, atomic restore with an encrypted on-device recovery copy and undo support
- Room schema history and tested migrations through database version 8
- Optional biometric or device-credential app lock
- A persistent control for masking monetary values on screen
- Optional daily financial-review reminders
- First-run onboarding and consistent loading, empty, and error states
- English and Portuguese localization
- Adaptive phone, tablet, and foldable layouts with bottom navigation or a navigation rail
- Large-text support, semantic descriptions, and accessible chart alternatives

## Technical overview

- **Language:** Kotlin 2.3
- **UI:** Jetpack Compose with Material 3 and Navigation Compose
- **Persistence:** Room 2.8 with a versioned local SQLite schema
- **Preferences:** DataStore
- **Background work:** WorkManager
- **Security:** Android Biometric APIs and password-based AES-GCM backup encryption
- **Serialization:** Kotlin Serialization
- **Architecture:** Compose UI → `FinanceViewModel` → repository → Room/DataStore
- **Android support:** Android 8.0 (API 26) and newer; compile/target SDK 36

## Project status

| Stage | Focus | Status |
| --- | --- | --- |
| P0 | Data safety — complete backups, migrations, validation, recovery, and surfaced errors | ✅ Implemented |
| P1 | Core completeness — editing, category/account management, rollover, recurring posting, and reconciliation | ✅ Implemented |
| P2 | UX and accessibility — navigation, adaptive layouts, localization, onboarding, and privacy controls | ✅ Implemented |
| P3 | Engineering — automated checks, lint, broader regression coverage, and CI/CD | 🟡 In progress |
| P4 | Optional advanced features — OCR, sync, and assisted categorization | ⚪ Not started |
| P5 | Commercial release — final branding, legal/privacy material, signing, and store delivery | ⚪ Not started |

The repository contains JVM tests plus Room and Compose instrumented tests. The checked-in GitHub Actions workflow is still a placeholder and does not yet build or test the app, so local verification remains required.

## Getting started

### Prerequisites

- JDK 17
- Android Studio with Android SDK 36 installed
- An Android 8.0+ device or emulator for running the app and instrumented tests

### Build the debug APK

```bash
git clone https://github.com/plot24/northstar-money.git
cd northstar-money
./gradlew assembleDebug
```

On Windows, use `gradlew.bat assembleDebug`. The APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

### Run tests and checks

```bash
# JVM unit tests
./gradlew testDebugUnitTest

# Instrumented Room and Compose tests (requires a connected device or emulator)
./gradlew connectedDebugAndroidTest

# Android lint
./gradlew lintDebug
```

## Repository layout

```text
app/src/main/java/com/northstar/money/
├── core/       # Room, DataStore, navigation, and design system
├── data/       # Repository, CSV import, backup/restore, and workers
├── domain/     # Money models and repository contract
└── feature/    # Finance state and ViewModel

app/src/test/          # JVM tests
app/src/androidTest/   # Room migration/repository and Compose UI tests
app/schemas/           # Exported Room schema history
docs/                  # Product, UX, architecture, database, and roadmap notes
```

The files in `docs/` describe the original product plan as well as architectural and UX decisions. Where those planning documents differ from the implementation, the source code and this README describe the current app.

## Contributing

This is a personal project under active development. Issues and suggestions are welcome. Before submitting a change, run the relevant JVM tests and, for database or UI changes, the corresponding instrumented tests.

## License

No license has been chosen. All rights reserved until a license is added.
