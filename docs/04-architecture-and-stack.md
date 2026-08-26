# Northstar — Architecture and Technology Stack

## 1. Architecture alternatives

| Style | Strength | Limitation for Northstar |
|---|---|---|
| MVC | Simple conceptual start | Android controllers easily accumulate UI and business logic |
| MVP | Testable presenter boundary | Manual view contracts and lifecycle coordination are cumbersome with Compose |
| MVVM | Natural fit for ViewModel, Flow, and Compose | Can become “massive ViewModel” without domain boundaries |
| MVI | Predictable immutable state and event handling | A strict global reducer adds ceremony and can model one-off UI work awkwardly |
| Clean Architecture | Isolates business rules and infrastructure | Excessive abstraction is possible if applied mechanically |

## 2. Decision

Use **pragmatic Clean Architecture with MVVM and unidirectional data flow**.

- Compose renders immutable `UiState`.
- User actions call intent-named ViewModel methods.
- ViewModels coordinate use cases and convert domain results to UI state.
- Use cases contain reusable or financially significant business rules.
- Repository interfaces define domain-facing data contracts.
- Room is the local source of truth.
- DataStore owns small preferences, never transactional financial records.

This follows current Android guidance favoring Compose, screen-level ViewModels,
repositories, coroutines/Flow, UDF, a domain layer for complex logic, and a local
database as the offline-first source of truth.

## 3. Dependency direction

```text
Compose UI -> ViewModel -> Use case -> Repository interface
                                      ^
Room/Files/WorkManager -> Repository implementation
```

Domain code does not import Compose, Activity, Room entity, DAO, WorkManager, or
Android resource classes. UI does not call a DAO or filesystem directly.

## 4. Initial modularization

Start with one Android application module and enforce package/layer boundaries.
This keeps Gradle approachable while the domain is evolving. Extract modules only
when build performance or ownership boundaries justify the cost.

Expected later modules:

- `core:model`, `core:money`, `core:database`, `core:designsystem`
- `feature:transactions`, `feature:budgets`, `feature:reports`, etc.

## 5. State management

- Each destination has a screen-level ViewModel.
- ViewModels expose `StateFlow<ScreenUiState>`.
- Database streams use `stateIn(WhileSubscribed(5_000))` where appropriate.
- UI sends actions through functions such as `saveTransaction()`.
- Durable facts are persisted before navigation or success feedback.
- Transient control state stays in Compose when it does not affect business logic.
- `SavedStateHandle` stores identifiers and lightweight draft/navigation state.

## 6. Navigation

Single activity with type-safe Navigation Compose destinations. Navigation arguments
contain stable identifiers, not serialized domain objects. Top-level destinations
retain state. Deep links pass through the application-lock gate.

## 7. Dependency injection

Use Hilt because the application has many ViewModels, Room, WorkManager, and future
provider integrations. Constructor injection is the default. Scope only expensive or
stateful shared objects. Domain objects remain constructible in plain JVM tests.

If a toolchain incompatibility blocks Hilt during initial bootstrap, use a small
manual application container temporarily without changing repository interfaces.

## 8. Error handling

Errors are modeled by layer:

- Data layer maps storage/platform exceptions to typed data failures.
- Domain layer returns explicit validation outcomes for expected failures.
- Unexpected programmer defects are not converted into misleading user errors.
- ViewModels map recoverable failures to persistent UI state.
- User messages explain recovery; technical detail remains in privacy-safe logs.
- Financial multi-record writes are database transactions.

## 9. Offline and caching

- Room is authoritative for financial data.
- Data is written locally first.
- Future remote synchronization observes a durable change log/outbox.
- WorkManager retries deferred synchronization with constraints and backoff.
- UI never reads remote responses directly; remote data is normalized into Room.
- Derived totals are queried/calculated from authoritative records and cached only
  when profiling proves it necessary.

## 10. Technology stack

| Area | Selection | Reason |
|---|---|---|
| Language | Kotlin | First-class Android support, null safety, coroutines, expressive domain types |
| UI | Jetpack Compose + Material 3 | Current recommended declarative toolkit and adaptive UI support |
| Architecture | ViewModel + StateFlow + UDF | Lifecycle-aware, testable state ownership |
| Database | Room 2.8.4 | Stable SQLite abstraction, Flow queries, migrations, compile-time validation |
| Preferences | Proto DataStore | Typed versioned preferences and asynchronous I/O |
| DI | Hilt | Android-aware scopes and WorkManager/ViewModel integration |
| Async | Kotlin coroutines and Flow | Structured concurrency and reactive database streams |
| Navigation | Navigation Compose | Single-activity destination and deep-link support |
| Background work | WorkManager | Durable deferrable work under Android power constraints |
| Authentication | BiometricPrompt/device credential | Uses system authentication; stores no biometric data |
| Files | Storage Access Framework | User-controlled import/export without broad storage permission |
| Serialization | kotlinx.serialization | Kotlin-first typed backup/import metadata |
| Charts | Compose Canvas-based internal components | Accessibility and visual control without an opaque dependency |
| Tests | JUnit, coroutine test, Turbine, Room tests, Compose UI tests | Covers domain, streams, persistence, and interaction |
| Static analysis | Android Lint, Detekt, Ktlint | Consistent and enforceable code quality |
| CI/CD | GitHub Actions + Gradle | Reproducible lint, test, build, and signed-release stages |
| Crash reporting | Optional consent-based provider after MVP | Financial privacy is reviewed before adding an SDK |
| Analytics | No SDK in first build | Avoid collecting sensitive behavior before event/privacy design |

Use the stable Compose BOM rather than pinning mutually dependent Compose artifacts
individually. The initial compile/target SDK is API 36 after installation; minimum SDK
is API 26 to balance modern cryptography/background APIs with device coverage. Room
2.8.x itself requires at least API 23.

## 11. Security architecture

- Application databases and backups are protected with Android Keystore-backed keys.
- Keys, credentials, full transaction records, and balances never enter logs.
- Export uses user-initiated system pickers and explicit warnings.
- Backup format is versioned, authenticated, and restored transactionally.
- Recent-app preview protection and notification redaction are configurable.
- No custom cryptographic primitive is invented; platform/library primitives are used.
- Bank and cloud tokens, when introduced, are isolated behind dedicated providers.

## 12. Testing strategy

- Money, budget, recurrence, forecast, debt, and duplicate logic: exhaustive JVM tests.
- DAO query and migration behavior: instrumented Room tests.
- Repository behavior: fakes preferred over mocks.
- ViewModels: state transition and cancellation tests.
- Compose: semantics, navigation, large text, and core workflow tests.
- Backup/import: golden fixtures, corrupt inputs, and interrupted-operation tests.
- Performance: generated 100,000-transaction dataset and startup benchmarks.

## 13. Architecture decision rules

1. Do not add an interface solely to satisfy a diagram.
2. Add a use case when logic is reused, composes repositories, or encodes a financial rule.
3. Database entities never cross into UI state.
4. Do not precompute derived money values unless correctness and invalidation are defined.
5. External providers are replaceable adapters, not domain dependencies.

