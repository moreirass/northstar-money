# Northstar Money

A local-first personal finance app for Android, built with Kotlin and Jetpack Compose.

> **Status: Alpha.** The app builds and runs, and the core offline features work, but it is not yet ready for production use with real financial data. See [Roadmap](#roadmap--status) below.

## Overview

Northstar Money is a privacy-focused budgeting app that keeps all data on-device. No cloud sync, no bank linking, no ads — just accounts, budgets, and forecasting that stay under your control.

## Features

Currently working (offline core):

- Accounts, income, and expense tracking
- Transfers between accounts (double-entry, avoids `Float` rounding issues)
- Budgets and savings goals
- Debt tracking
- Recurring transactions
- Cash-flow forecasting
- Search
- CSV import/export
- Local biometric protection

## Tech Stack

- **Language:** Kotlin
- **UI:** Jetpack Compose
- **Database:** Room (local-first, versioned schema)
- **Preferences:** DataStore
- **Background work:** WorkManager
- **Security:** Biometric authentication

## Roadmap / Status

The project is being brought from alpha to a stable release in five stages:

| Stage | Focus | Status |
|-------|-------|--------|
| P0 | Data safety — backups, migrations, error handling | 🔴 In progress |
| P1 | Core feature completeness — editing, reconciliation | ⚪ Not started |
| P2 | UX & accessibility — navigation, translations, a11y | ⚪ Not started |
| P3 | Test coverage & engineering — CI/CD, lint, testing | ⚪ Not started |
| P4 | Premium features — OCR, sync, ML categorization | ⚪ Not started |
| P5 | Commercial release — branding, Play Store, legal | ⚪ Not started |

**⚠️ Do not rely on this app for real financial data until Stage P0 is complete.** Backups are currently incomplete and tied to the current installation.

## Getting Started

### Prerequisites

- Android Studio (latest stable)
- JDK 17+
- Android SDK

### Build

```bash
git clone https://github.com/plot24/northstar-money.git
cd northstar-money
./gradlew assembleDebug
```

The debug APK will be generated at `app/build/outputs/apk/debug/`.

### Run tests

```bash
./gradlew testDebugUnitTest
```

## Contributing

This is currently a personal project in active development. Issues and suggestions are welcome, but the codebase is still undergoing significant restructuring (see Roadmap above) before it's ready for external contributions.

## License

No license has been chosen yet. All rights reserved until a license is added.
