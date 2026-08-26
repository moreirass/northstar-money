# Northstar — Functional Requirements

Status: Phase 2 baseline  
Product direction: EU-first, local-first, individual-first  
Initial platform: Android  

## 1. Purpose and scope

Northstar is a personal finance planning application that combines transaction
tracking, monthly budgeting, goals, debt visibility, and explainable cash-flow
insights. The first release must be fully useful without creating an account or
connecting a bank.

This specification distinguishes:

- **MVP** — required for the first public release.
- **Post-MVP** — planned after initial validation.
- **Premium** — a possible paid capability; final packaging remains a business decision.

The app is informational software. It must not describe forecasts or insights as
guaranteed outcomes, investment advice, tax advice, or credit advice.

## 2. Product-wide rules

### FR-CORE-001 — Local-first operation (MVP)

The user can complete every MVP workflow without internet access.

Acceptance criteria:

- Creating, viewing, editing, and deleting financial data works offline.
- Loss of connectivity never blocks access to local data.
- Features requiring connectivity identify themselves before use.
- A network failure cannot discard a locally completed change.

### FR-CORE-002 — Monetary precision (MVP)

Money is never stored using binary floating-point values.

Acceptance criteria:

- Stored amounts use integer minor units together with an ISO 4217 currency code.
- Formatting follows the selected locale and currency fraction rules.
- Calculations have explicit rounding behavior.
- Transfers and split transactions preserve their exact total.

### FR-CORE-003 — Time semantics (MVP)

Transactions have a user-facing local date and an optional exact timestamp.
Calendar-based calculations use the user's configured time zone.

### FR-CORE-004 — Audit metadata (MVP)

Mutable financial records contain creation and last-modified timestamps. Records
that may later synchronize also receive stable UUID identifiers.

### FR-CORE-005 — Safe destructive actions (MVP)

- Destructive actions require confirmation when their effect is not obvious.
- Deletion explains related records that will be affected.
- Transaction deletion offers a temporary undo action.
- Accounts with transactions are archived by default rather than silently erased.

## 3. Onboarding and settings

### FR-ONB-001 — First-run onboarding (MVP)

The onboarding flow collects only information needed to create a useful plan:

1. Preferred name, optional.
2. Country/region and display locale.
3. Base currency.
4. Month start day, defaulting to the first.
5. First financial account and opening balance.
6. Optional starter categories.
7. Optional first monthly budget.

The user can skip nonessential steps and revisit them later.

### FR-ONB-002 — Financial data disclaimer (MVP)

The app explains that projections are estimates and obtains acknowledgment before
showing predictive insights.

### FR-SET-001 — Preferences (MVP)

The user can configure:

- Locale and base currency.
- First day of week and budgeting month start.
- Light, dark, or system theme.
- App lock timeout.
- Notification categories.
- Default account and transaction type for quick entry.
- Whether negative balances and future transactions appear in dashboard totals.

Changing the base currency does not silently convert historical amounts.

## 4. Accounts

### FR-ACC-001 — Account management (MVP)

The user can create, edit, reorder, archive, restore, and view accounts.

Supported types:

- Cash
- Current/checking
- Savings
- Credit card
- Loan
- Other asset
- Other liability

Required fields:

- Name
- Type
- Currency
- Opening balance and opening date

Optional fields:

- Icon and color
- Institution name
- Notes
- Credit limit
- Include in net worth
- Include in available cash

### FR-ACC-002 — Account balance (MVP)

The cleared, uncleared, and projected balances are distinguishable. The current
ledger balance is derived from the opening balance and account transactions, not
stored as an independently editable total.

### FR-ACC-003 — Reconciliation (MVP)

The user can compare an account against a statement balance and date.

Acceptance criteria:

- The app shows the difference before confirmation.
- The user can review uncleared transactions.
- An optional adjustment transaction can resolve a difference.
- Completed reconciliation records are retained for auditability.

## 5. Transactions

### FR-TXN-001 — Transaction types (MVP)

The app supports income, expense, and account transfer transactions.

Common fields:

- Account
- Amount and currency
- Local transaction date
- Payee or source
- Category, except for ordinary transfers
- Note
- Cleared status
- Optional tags

### FR-TXN-002 — Fast entry (MVP)

The primary transaction flow prioritizes amount, account, category, and date.
Optional detail stays collapsed initially.

Acceptance criteria:

- A transaction can be recorded from any primary destination.
- The amount keypad is usable one-handed.
- The last-used account and relevant category suggestions are available.
- Save is disabled until required values are valid.
- Duplicate taps cannot create duplicate records.

### FR-TXN-003 — Transfers (MVP)

A transfer is represented as one logical operation connecting two account entries.

- Editing either side updates the logical transfer consistently.
- Deleting a transfer removes both account effects after confirmation.
- Same-currency transfers preserve equal absolute amounts.
- Cross-currency transfers record both amounts and the effective exchange rate.

### FR-TXN-004 — Split transaction (Post-MVP)

One transaction may allocate its total across multiple categories.

- Split components must sum exactly to the parent total.
- Rounding differences must be visible and resolved before saving.

### FR-TXN-005 — Transaction status (MVP)

Transactions can be uncleared or cleared. A future synchronized version may add
pending and reconciled states without changing transaction identity.

### FR-TXN-006 — Attachments (Post-MVP/Premium)

The user can attach receipt images or documents. Attachments are encrypted at rest,
have size/type limits, and can be removed independently of the transaction.

### FR-TXN-007 — Bulk actions (Post-MVP)

Selected transactions can be categorized, tagged, marked cleared, moved when safe,
or deleted with an impact summary.

## 6. Categories, tags, and rules

### FR-CAT-001 — Categories (MVP)

- Income and expense categories are distinct.
- Categories may have one optional parent group.
- Users can create, rename, reorder, recolor, archive, and restore categories.
- A category referenced by transactions is archived rather than hard-deleted.
- Merging categories reassigns dependent transactions after confirmation.

### FR-TAG-001 — Tags (MVP)

Transactions can have zero or more tags. Tags support cross-category grouping such
as `Holiday`, `Reimbursable`, or `Tax 2026`.

### FR-RULE-001 — Categorization rules (Post-MVP)

Users can create ordered rules based on normalized payee text, amount range,
account, or transaction direction. A preview shows affected transactions before a
rule is applied retroactively.

## 7. Recurring transactions and subscriptions

### FR-REC-001 — Recurring schedules (MVP)

The user can create recurring income, expense, or transfer templates with:

- Start date and optional end date
- Daily, weekly, monthly, or yearly frequency
- Interval
- Monthly day or end-of-month behavior
- Account, amount, payee, category, and tags
- Reminder lead time

### FR-REC-002 — Occurrence generation (MVP)

- Upcoming occurrences appear in the calendar and forecast without immediately
  affecting the current ledger balance.
- The user can post, skip, or edit one occurrence.
- Editing asks whether the change applies once, from this occurrence forward, or
  to the entire schedule when mathematically possible.
- Generation is idempotent: one occurrence cannot be posted twice.

### FR-SUB-001 — Subscription tracking (Post-MVP)

The app can mark a recurring expense as a subscription and record provider,
billing cycle, renewal date, trial end, and optional cancellation link.

### FR-SUB-002 — Subscription detection (Post-MVP/Premium)

The app suggests possible subscriptions based on repeated payee and amount patterns.
It never changes transaction data without confirmation and explains the evidence
for every suggestion.

## 8. Monthly budgets

### FR-BUD-001 — Budget creation (MVP)

The user can assign a planned amount to expense categories for a budgeting period.
Budget periods normally follow calendar months but respect the configured month
start day.

### FR-BUD-002 — Budget metrics (MVP)

For each category, the app displays:

- Planned amount
- Actual eligible spending
- Remaining amount
- Percentage consumed
- Spending pace relative to elapsed period

Transfers are excluded from spending. Refund treatment must be consistent and
configurable later; the MVP treats a categorized inflow as negative category spend.

### FR-BUD-003 — Rollover (MVP)

Each category can independently carry a positive or negative remainder into the
next period. The UI shows the current allocation separately from rollover.

### FR-BUD-004 — Budget copy (MVP)

The user can copy the previous period's plan, actual spending, or selected category
amounts into a new period, with a preview before saving.

### FR-BUD-005 — Overspending (MVP)

Overspending never blocks transaction entry. It creates a visible state and may
offer actions to move available budget, adjust the target, or acknowledge it.

### FR-BUD-006 — Available versus forecast money (MVP)

The interface distinguishes money currently available from income expected later.
Forecast income cannot be presented as already spendable.

## 9. Savings goals

### FR-GOAL-001 — Goal management (MVP)

The user can create amount-based savings goals with:

- Name, icon, and color
- Target amount and currency
- Optional target date
- Linked account or tracked contributions
- Priority and notes

### FR-GOAL-002 — Goal progress (MVP)

The app shows current saved amount, remaining amount, progress percentage, and the
required periodic contribution when a target date exists.

### FR-GOAL-003 — Goal forecast (Post-MVP/Premium)

The app estimates an achievement date based on explicit assumptions. Users can
compare contribution scenarios without modifying the real goal.

## 10. Debt tracking

### FR-DEBT-001 — Debt overview (MVP)

Loan and credit accounts may record original principal, current principal, annual
interest rate, minimum payment, due day, and optional term.

The MVP provides balance and payment progress but does not claim to replace lender
statements.

### FR-DEBT-002 — Repayment strategies (Post-MVP/Premium)

Users can compare avalanche, snowball, and custom-order strategies. Calculations
must disclose whether fees, variable rates, and compounding behavior are modeled.

## 11. Dashboard

### FR-DASH-001 — Financial snapshot (MVP)

The dashboard displays a scannable summary containing:

- Available cash
- Net worth, when enabled
- Current-period income and expenses
- Budget consumed and remaining
- Upcoming obligations
- Recent transactions
- Goal progress
- Actionable insight cards

Every summary value links to its underlying detail or calculation explanation.

### FR-DASH-002 — Personalization (Post-MVP)

The user can reorder or hide dashboard sections. Essential risk warnings cannot be
permanently hidden without an explicit preference.

### FR-DASH-003 — Sensitive data mode (MVP)

A single action obscures monetary amounts on the current screen. App previews in
the Android recent-apps screen are protected according to the security setting.

## 12. Calendar

### FR-CAL-001 — Financial calendar (MVP)

Month and agenda views show posted transactions, scheduled occurrences, expected
income, goal deadlines, subscription renewals, and debt due dates using visually
distinct states.

### FR-CAL-002 — Day summary (MVP)

Selecting a date shows daily inflows, outflows, events, and projected end-of-day
balance without requiring navigation away from the calendar.

## 13. Reports and charts

### FR-REP-001 — Core reports (MVP)

- Income versus expense over time
- Spending by category
- Account balance trend
- Budget plan versus actual
- Net-worth trend when enabled

### FR-REP-002 — Filters (MVP)

Reports can filter by date range, account, category, and tag. Filter state is
visible and can be cleared in one action.

### FR-REP-003 — Chart accessibility (MVP)

Every chart has a nonvisual text/table representation. Information is never
communicated by color alone.

### FR-REP-004 — Drill-down (MVP)

Selecting a chart segment opens the transactions behind the value. Aggregate
values use the same query rules as the resulting list.

## 14. Search

### FR-SRCH-001 — Transaction search (MVP)

Search covers payee, note, category, account, and tag. Filters include transaction
type, amount range, date range, cleared status, and attachment presence.

### FR-SRCH-002 — Search performance (MVP)

Search remains responsive with at least 100,000 local transactions. Index and
query decisions will be validated with benchmark data rather than assumed.

## 15. Import and export

### FR-IMP-001 — CSV import (MVP)

The import flow supports:

1. File selection through Android's system document picker.
2. Encoding and delimiter detection with manual override.
3. Header and column mapping.
4. Date, decimal separator, amount sign, and currency configuration.
5. Preview and validation.
6. Duplicate candidate review.
7. Atomic import with a result summary.

An interrupted or rejected import must not leave a partially applied batch.

### FR-IMP-002 — Duplicate detection (MVP)

The app flags likely duplicates using a documented combination of account, date,
amount, currency, normalized payee, and import identity. Users make the final
decision when confidence is not exact.

### FR-EXP-001 — CSV export (MVP)

The user can export selected accounts and date ranges. The export uses explicit
UTF-8 encoding and documents its columns.

### FR-EXP-002 — PDF report (MVP)

The user can generate a readable monthly summary containing major totals, budgets,
and selected charts. Sensitive exports are created only after explicit action and
shared through Android's secure share mechanism.

### FR-EXP-003 — Excel export (Post-MVP/Premium)

The app exports a structured workbook with transactions, accounts, budgets, and
summary sheets. Formula cells and types must be valid in common spreadsheet tools.

## 16. Notifications and background work

### FR-NOT-001 — Notification categories (MVP)

Separate Android notification channels exist for:

- Upcoming bills and recurring transactions
- Budget warnings
- Goal reminders
- Backup reminders
- Insights

Each is independently configurable. Notifications do not reveal sensitive amounts
on the lock screen unless the user opts in.

### FR-NOT-002 — Reliable scheduling (MVP)

Background work uses Android-supported deferred work. The app explains that exact
delivery may depend on system battery policies and requests exact-alarm capability
only if a later feature genuinely requires it.

## 17. Security, privacy, backup, and access

### FR-SEC-001 — Application lock (MVP)

The user can enable biometric or device-credential unlock using Android's system
authentication UI. The app never stores biometric material.

### FR-SEC-002 — Encryption (MVP)

Sensitive database and backup material is encrypted at rest using keys protected by
the Android Keystore. Key loss and device migration behavior must be documented and
tested before release.

### FR-SEC-003 — Privacy controls (MVP)

- No advertising SDK is included.
- Analytics is disabled by default until an explicit product decision and consent
  design are approved.
- Logs must not contain transaction descriptions, balances, tokens, or keys.
- The app provides a clear delete-all-data operation.
- Permissions are requested only at the moment their feature needs them.

### FR-BKP-001 — Encrypted local backup (MVP)

The user can export and restore a versioned encrypted backup through the system
document picker.

- Backup creation never overwrites a file without system confirmation.
- Restore validates integrity and version compatibility before replacing data.
- A failed restore preserves the current database.
- The app periodically reminds users who have no recent backup.

### FR-SYNC-001 — Cloud synchronization (Post-MVP/Premium)

Synchronization will support multiple devices, conflicts, tombstones, retries, and
end-to-end data protection appropriate to the selected service. Local data remains
the immediately available source for the UI.

### FR-BANK-001 — Bank synchronization (Post-MVP/Premium)

Bank connectivity is isolated behind a provider-neutral contract. The app stores no
bank credentials, clearly displays consent scope and expiration, and never merges a
candidate transaction without deterministic matching or user review.

## 18. Widgets and platform integration

### FR-WID-001 — Android widgets (Post-MVP)

Optional widgets provide quick transaction entry and a privacy-aware budget summary.
Widgets hide amounts while the app's sensitive-data mode is enabled.

### FR-SHORT-001 — App shortcuts (Post-MVP)

Static or dynamic shortcuts can open expense, income, and transfer entry without
bypassing the configured app lock.

## 19. Explainable intelligence

### FR-AI-001 — Categorization suggestion (MVP)

The app suggests a category using confirmed historical mappings and local rules.
It displays that the value is a suggestion and learns only after user confirmation.

### FR-AI-002 — Spending pace (MVP)

The app compares eligible spending with both the budget consumed and the proportion
of the period elapsed. It avoids warning when insufficient data exists.

### FR-AI-003 — Financial health score (MVP)

The monthly score uses separately visible components such as:

- Spending relative to income
- Budget adherence
- Cash buffer
- Debt payment consistency
- Goal contribution consistency

The score is educational, not a credit score. Users can inspect component weights
and disable it.

### FR-AI-004 — Cash-flow projection (MVP)

The 30-day projection combines current cleared/eligible balance, scheduled
transactions, and explicitly expected income.

- Confirmed and estimated events are visually distinct.
- The lowest projected balance and date are shown.
- The user can inspect every event contributing to the projection.
- Uncertainty is communicated rather than represented as false precision.

### FR-AI-005 — Duplicate payment insight (MVP)

The app flags similar payments within a configurable time window and explains the
matching attributes. It never deletes a transaction automatically.

### FR-AI-006 — Expense anomaly insight (MVP)

The app may flag a transaction or category total that is materially different from
the user's own history. It must disclose the comparison period and suppress results
when history is insufficient.

### FR-AI-007 — Savings recommendation (Post-MVP/Premium)

Recommendations use budget surplus and recurring cash-flow assumptions. They cannot
initiate transfers and must preserve a user-configured safety buffer.

### FR-AI-008 — Optional generative explanations (Future/Premium)

If introduced, cloud-generated explanations are opt-in. The app shows what data
will leave the device, minimizes that data, and labels generated content. Core
calculations remain deterministic and independently testable.

## 20. Accessibility and internationalization

### FR-A11Y-001 — Accessible interaction (MVP)

- Interactive targets meet Material accessibility sizing guidance.
- TalkBack labels describe purpose and state, not merely icon names.
- Focus order follows the visual workflow.
- Dynamic text scaling does not hide essential actions or values.
- Reduced-motion preferences are respected.
- Error messages identify the problem and recovery action.

### FR-I18N-001 — International readiness (MVP)

- User-visible text is externalized.
- Layouts tolerate expansion and right-to-left direction.
- Dates, numbers, and currency use locale-aware formatting.
- Financial records retain their original currency.
- English is the first shipping language; Portuguese is the recommended second
  language for the initial market validation.

## 21. Non-functional requirements

### NFR-001 — Reliability

- Database writes affecting multiple records are transactional.
- Process death during editing cannot create partial financial records.
- Schema migrations are tested using realistic historical fixtures.
- Backup restore is exercised in automated and release testing.

### NFR-002 — Performance

Initial engineering targets on a representative mid-range device:

- Warm launch to interactive dashboard: under 1 second.
- Common local list/filter response: under 100 ms after data is loaded.
- Transaction save feedback: under 200 ms.
- Smooth scrolling at the device refresh rate for ordinary lists.

These are targets to measure, not claims to assume.

### NFR-003 — Maintainability

- Business rules are independent of Android UI classes.
- Feature boundaries prevent unrelated screens from depending on one another.
- All monetary and budgeting algorithms have unit tests.
- Public module contracts use stable domain types rather than database entities.

### NFR-004 — Compatibility

The exact minimum and target Android SDK versions will be selected in Phase 5 using
current official Android distribution and API guidance. Phone layouts are required
for MVP; adaptive tablet/foldable layouts are designed during Phase 3 and may ship
incrementally.

## 22. MVP release boundary

The first public release includes:

- Onboarding and settings
- Accounts and reconciliation
- Income, expenses, and transfers
- Categories and tags
- Recurring schedules
- Monthly category budgets and rollover
- Savings goals
- Basic debt visibility
- Dashboard, calendar, search, reports, and accessible charts
- CSV import/export and PDF summary
- Configurable notifications
- Local encrypted backup/restore and application lock
- Rules-based categorization, duplicates, spending pace, anomaly indicators,
  health score, and 30-day cash-flow projection

Anything marked Post-MVP, Premium, or Future is excluded unless a later milestone
explicitly promotes it through a scope decision.

## 23. Definition of done for a feature

A feature is complete only when:

1. Product rules and empty/loading/error/success states are implemented.
2. Accessibility behavior is verified.
3. Business logic has automated tests.
4. Persistence or navigation behavior has integration tests where appropriate.
5. Sensitive data behavior has been reviewed.
6. Offline behavior is verified.
7. Analytics, if later enabled, contains no sensitive financial data.
8. User-facing documentation or onboarding is updated.
9. Requirement IDs are referenced in the implementation milestone.

