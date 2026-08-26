# Northstar Money

**A private, local-first personal finance app for Android.**

Built with Kotlin and Jetpack Compose. Your financial data stays on the device unless you explicitly export it — no account sign-in, no cloud sync, no bank connection, no ads, no analytics.

![Status](https://img.shields.io/badge/status-alpha-orange)
![Version](https://img.shields.io/badge/version-0.1.0-blue)
![Kotlin](https://img.shields.io/badge/Kotlin-2.3-7F52FF?logo=kotlin&logoColor=white)
![Min SDK](https://img.shields.io/badge/API-26%2B-brightgreen)
![License](https://img.shields.io/badge/license-unlicensed-lightgrey)

> ⚠️ **Alpha build.** The offline feature set and data-safety foundations are implemented, but release hardening and production distribution are not complete. Keep regular encrypted backups and don't treat this build as the only copy of important financial records.

> 🧪 **About this project.** This is a personal hobby project built for fun and to learn Android development, Kotlin, and Jetpack Compose. Development is done with heavy assistance from AI tools (for code generation, testing, and problem-solving). It is not a professionally audited product — use it at your own discretion, especially with real financial data.

---

## Contents

- [What works today](#what-works-today)
- [Technical overview](#technical-overview)
- [Project status](#project-status)
- [Getting started](#getting-started)
- [Repository layout](#repository-layout)
- [Contributing](#contributing)
- [License](#license)

---

## What works today

### 💰 Everyday finances

- Track cash, checking, savings, and credit accounts in multiple currencies
- Add and edit income, expenses, and transfers
- Record cross-currency transfers with separate sent and received amounts
- Search transactions, mark them cleared, and reconcile accounts against statements
- Recover recently deleted transactions and restore archived accounts
- Manage income/expense categories — archive, restore, and reversible merges
- Store monetary values as integer minor units, never floating-point

### 📊 Planning and reporting

- Monthly category budgets with rollover from the previous month
- Savings goals with editable contribution histories
- Debt profiles with interest rates, minimum payments, and due dates
- Recurring transactions — schedule, pause/resume, and auto-post due occurrences
- Account totals, monthly cash flow, accessible charts, and a 30-day forecast
- Export transactions to CSV and a monthly summary to PDF
- Atomic CSV import with validation and duplicate detection

### 🔒 Privacy, safety, and accessibility

- Password-protected, portable full-database backups
- Validated, atomic restore with an encrypted on-device recovery copy and undo support
- Room schema history with tested migrations through database version 8
- Optional biometric or device-credential app lock
- Persistent control for masking monetary values on screen
- Optional daily financial-review reminders
- First-run onboarding and consistent loading/empty/error states
- English and Portuguese localization
- Adaptive phone, tablet, and foldable layouts (bottom nav or navigation rail)
- Large-text support, semantic descriptions, and accessible chart alternatives

---

## Technical overview

| Layer | Technology |
|---|---|
| Language | Kotlin 2.3 |
| UI | Jetpack Compose · Material 3 · Navigation Compose |
| Persistence | Room 2.8 · versioned local SQLite schema |
| Preferences | DataStore |
| Background work | WorkManager |
| Security | Android Biometric APIs · password-based AES-GCM backup encryption |
| Serialization | Kotlin Serialization |
| Android support | API 26+ (Android 8.0) · compile/target SDK 36 |

**Architecture:** Compose UI → `FinanceViewModel` → Repository → Room / DataStore

---

## Project status

| Stage | Focus | Status |
|---|---|---|
| **P0** | Data safety — backups, migrations, validation, recovery, surfaced errors | ✅ Implemented |
| **P1** | Core completeness — editing, category/account management, rollover, recurring posting, reconciliation | ✅ Implemented |
| **P2** | UX & accessibility — navigation, adaptive layouts, localization, onboarding, privacy controls | ✅ Implemented |
| **P3** | Engineering — automated checks, lint, broader regression coverage, CI/CD | 🟡 In progress |
| **P4** | Optional advanced features — receipts, OCR, Excel export, widgets, multi-currency history | ⚪ Not started |
| **P5** | Commercial release — branding, legal/privacy material, signing, store delivery | ⚪ Not started |

The repository contains JVM tests plus Room and Compose instrumented tests. The checked-in GitHub Actions workflow is still a placeholder and does not yet build or test the app, so local verification remains required.

---

## Getting started

### Prerequisites

- JDK 17
- Android Studio with Android SDK 36 installed
- An Android 8.0+ device or emulator (for running the app and instrumented tests)

### Build the debug APK

```bash
git clone https://github.com/moreirass/northstar-money.git
cd northstar-money
./gradlew assembleDebug
```

> On Windows, use `gradlew.bat assembleDebug`.

The APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

### Run tests and checks

```bash
# JVM unit tests
./gradlew testDebugUnitTest

# Instrumented Room and Compose tests (requires a connected device or emulator)
./gradlew connectedDebugAndroidTest

# Android lint
./gradlew lintDebug
```

---

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

> Files in `docs/` describe the original product plan and architectural/UX decisions. Where planning documents differ from the implementation, the source code and this README reflect the current app.

---

## Contributing

This is a personal project under active development. Issues and suggestions are welcome. Before submitting a change, run the relevant JVM tests and, for database or UI changes, the corresponding instrumented tests.

---

## License

No license has been chosen yet. All rights reserved until a license is added.
