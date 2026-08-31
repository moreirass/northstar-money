# Northstar — Project Structure and Roadmap

## 1. Initial structure

```text
budgeter/
├─ app/
│  ├─ schemas/                         Room schema history
│  └─ src/
│     ├─ main/
│     │  ├─ java/com/northstar/money/
│     │  │  ├─ NorthstarApp.kt
│     │  │  ├─ MainActivity.kt
│     │  │  ├─ core/
│     │  │  │  ├─ database/           Room database, DAOs, entities, converters
│     │  │  │  ├─ datastore/          Typed user preferences
│     │  │  │  ├─ designsystem/       Theme and reusable accessible components
│     │  │  │  ├─ money/              Currency-safe money utilities
│     │  │  │  ├─ navigation/         Destinations and root navigation
│     │  │  │  └─ common/             Result/error/time abstractions
│     │  │  ├─ data/
│     │  │  │  ├─ mapper/             Entity/domain conversion
│     │  │  │  ├─ repository/         Offline-first repository implementations
│     │  │  │  ├─ backup/             Versioned encrypted backup adapter
│     │  │  │  ├─ importexport/       CSV/PDF processing
│     │  │  │  └─ worker/             Background/reminder work
│     │  │  ├─ domain/
│     │  │  │  ├─ model/              Android-independent business models
│     │  │  │  ├─ repository/         Domain-facing contracts
│     │  │  │  └─ usecase/            Financial and orchestration rules
│     │  │  ├─ feature/
│     │  │  │  ├─ onboarding/
│     │  │  │  ├─ dashboard/
│     │  │  │  ├─ transactions/
│     │  │  │  ├─ accounts/
│     │  │  │  ├─ budgets/
│     │  │  │  ├─ recurring/
│     │  │  │  ├─ goals/
│     │  │  │  ├─ debts/
│     │  │  │  ├─ calendar/
│     │  │  │  ├─ reports/
│     │  │  │  ├─ search/
│     │  │  │  └─ settings/
│     │  │  └─ di/                    Dependency bindings
│     │  └─ res/
│     ├─ test/                         Plain JVM tests
│     └─ androidTest/                  Room and Compose/device tests
├─ build-logic/                        Added only when repeated Gradle config warrants it
├─ docs/
├─ gradle/libs.versions.toml
├─ settings.gradle.kts
└─ build.gradle.kts
```

Each feature package owns its screen, ViewModel, UI state, and feature components.
It does not own shared persistence or reach into another feature's UI package.

## 2. Milestones

### M0 — Reproducible foundation

Objectives: Gradle wrapper, application module, Compose theme, navigation shell,
quality tasks, and CI-ready build.

Files: root Gradle files, version catalog, manifest, application/activity, theme,
navigation, initial unit test.

Concepts: Gradle, Android lifecycle, Compose, resources.  
Difficulty: Medium.  
Estimated solo learner time: 2–4 days.

Testing: clean build, unit tests, lint, dark/light preview, process recreation smoke test.

### M1 — Data and domain foundation

Objectives: money types, Room schema, DataStore, repositories, seed categories,
transaction invariants.

Files: entities, DAOs, database, mappers, models, repository contracts/implementations.

Concepts: SQLite/Room, Flow, coroutines, transactions, integer money.  
Difficulty: High.  
Estimated time: 1–2 weeks.

Testing: DAO queries, foreign keys, migrations, transfer balance, rounding, 100k fixture.

### M2 — Onboarding, accounts, and transactions

Objectives: first-run setup, account list/detail, fast income/expense/transfer entry,
editing, deletion/undo, search foundation.

Difficulty: High.  
Estimated time: 2–3 weeks.

Testing: validation, duplicate taps, cross-currency transfer, app restart, large text,
TalkBack semantics, empty states.

### M3 — Budgeting

Objectives: monthly plan, allocations, rollover, category detail, plan/actual queries.

Concepts: date periods, aggregate SQL, domain use cases.  
Difficulty: High.  
Estimated time: 2 weeks.

Testing: month boundaries, custom start day, refunds, rollover modes, overspending.

### M4 — Recurring, calendar, goals, and debt

Objectives: recurrence engine, occurrence posting, calendar, goal progress, basic debt.

Difficulty: High.  
Estimated time: 2–3 weeks.

Testing: leap years, end-of-month, time zones, idempotency, skipped events, payoff math.

### M5 — Dashboard, reports, and explainable insights

Objectives: dashboard queries, accessible charts, 30-day forecast, spending pace,
duplicate insights, health score.

Difficulty: High.  
Estimated time: 2–3 weeks.

Testing: calculation fixtures, insufficient history, drill-down consistency, chart tables.

### M6 — Import, export, backup, security, notifications

Objectives: atomic CSV import, CSV/PDF export, encrypted backup/restore, biometric lock,
notification channels, scheduled work.

Difficulty: Very high.  
Estimated time: 3–4 weeks.

Testing: hostile/corrupt files, locale formats, key loss, restore rollback, redacted
notifications, background constraints.

### M7 — Release hardening

Objectives: accessibility audit, performance benchmarks, privacy review, migration
tests, store assets, signed release pipeline.

Difficulty: High.  
Estimated time: 2–3 weeks plus beta feedback.

Testing: full regression matrix, API levels, screen sizes, rotations, process death,
offline mode, backup recovery, baseline profile, Play pre-launch report.

## 3. Commercial completion criteria

“Functionally complete” means all promoted MVP requirements have implementation and
tests, the release build succeeds, critical workflows pass on an emulator/device,
there are no known data-loss or high-severity security defects, and privacy/export/
restore behavior is documented. Multi-device, cloud, banking, shared-space, ML
categorization, and advanced subscription/anomaly functionality is outside the
current roadmap and is not part of commercial completion.
