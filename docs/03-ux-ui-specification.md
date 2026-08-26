# Northstar — UX/UI Specification

## 1. Experience principles

1. **Answer before asking.** Every primary screen begins with the financial answer
   the user came for, followed by supporting detail.
2. **Calm, not judgmental.** Use neutral language such as “€42 over plan,” never
   “bad spending.”
3. **Progressive disclosure.** Common actions stay prominent; accounting detail is
   available without dominating beginner workflows.
4. **Explain every number.** Aggregates and insights link to their inputs.
5. **One-handed capture.** Recording a transaction is optimized for thumb reach.
6. **Private in public.** Monetary values can be obscured instantly.

## 2. Navigation model

Phone navigation uses a Material 3 navigation bar with five destinations:

1. Home
2. Plan
3. Add (prominent central action)
4. Activity
5. More

`Add` opens a transaction sheet rather than replacing navigation state. `More`
contains Goals, Debts, Calendar, Reports, Import/Export, and Settings.

Expanded windows use a navigation rail. Large windows use a permanent drawer plus
a list-detail or supporting-pane layout where useful.

```text
Onboarding
   └─ Home
      ├─ Account detail ─ Transaction detail/edit
      ├─ Insight explanation
      ├─ Plan ─ Category budget detail
      ├─ Add transaction sheet
      ├─ Activity ─ Search/filter ─ Transaction detail/edit
      └─ More
         ├─ Calendar
         ├─ Goals ─ Goal detail/edit
         ├─ Debts ─ Debt detail/edit
         ├─ Reports ─ Report drill-down
         ├─ Import/Export
         └─ Settings
```

Back always reverses navigation. Closing an entry sheet with unsaved changes asks
whether to discard. Deep links unlock the app before revealing financial content.

## 3. Visual identity

Working personality: composed, intelligent, optimistic, precise.

### Color

Seed color: deep teal `#006B60`.

- Primary: confident actions and selected navigation.
- Secondary: blue-green supporting information.
- Tertiary: warm amber for goals and highlights.
- Error: Material red roles for destructive/error states.
- Income: semantic positive teal, paired with an icon/label.
- Expense: neutral foreground or error only when attention is required.

Dynamic color is supported on Android 12+, with a branded fallback scheme. Financial
meaning never depends on hue alone.

### Typography

Use the Material 3 type scale with the system sans font initially:

- Display/Headline: major balances and screen conclusions.
- Title: cards, groups, and destinations.
- Body: descriptions and transactions.
- Label: controls, metadata, and chart axes.

Monetary values use tabular numerals when the selected font supports them.

### Shape and elevation

- Cards: 16 dp corners.
- Sheets and large containers: 28 dp top corners.
- Buttons and inputs: Material 3 defaults.
- Prefer tonal separation to heavy shadows.

### Iconography

Use Material Symbols/Icons with text labels where meaning is not universal.
Custom category icons come from a constrained accessible set. Do not use icons as
decoration when they compete with financial information.

## 4. Shared interaction patterns

### Money visibility

An eye/visibility action in top-level screens toggles obscured values for the current
session. Obscured values retain approximate layout width to avoid visual jumping.

### Loading and empty states

- Local reads normally render immediately.
- Longer calculations use skeletons or a compact progress indicator.
- Empty states explain value and provide exactly one primary next action.
- Errors keep already available content visible and offer a recovery action.

### Feedback

- Snackbar with Undo for transaction deletion/archive actions.
- Inline validation for entry errors.
- Confirmation dialogs only for consequential or ambiguous actions.
- Haptics for successful keypad save and destructive confirmation when enabled.

### Motion

- Container transforms connect a selected card with its detail destination.
- Charts animate once when data meaningfully changes, not on every recomposition.
- Budget progress changes use short emphasized easing.
- Reduced-motion preference removes nonessential movement.

## 5. Screen specifications

### 5.1 Onboarding

Purpose: reach the first useful dashboard with minimum required setup.

```text
┌──────────────────────────────────┐
│ Northstar                    1/4 │
│                                  │
│ Make tomorrow's money clearer.   │
│                                  │
│ Region        Portugal       ▾   │
│ Currency      EUR (€)        ▾   │
│ Month starts  Day 1          ▾   │
│                                  │
│ Data stays on this device by     │
│ default.                          │
│                                  │
│              [ Continue ]        │
└──────────────────────────────────┘
```

Steps: preferences, first account, starter categories, optional budget. Back retains
entered data. A progress indicator names rather than disguises remaining work.

### 5.2 Home dashboard

Purpose: answer “How am I doing, and what needs attention?”

```text
┌──────────────────────────────────┐
│ Good morning, Sofia        (eye) │
│ Available now                    │
│ €2,480.00             ▼ 3%      │
│ [Income €2,900] [Spent €1,240]  │
│                                  │
│ Plan this month             58%  │
│ ███████████░░░░░░░               │
│ €840 remaining                   │
│                                  │
│ Heads-up                         │
│ Groceries are €18 above pace  >  │
│                                  │
│ Upcoming                         │
│ 18 Jul  Rent              €850   │
│                                  │
│ Recent activity             See  │
│ Continente  Groceries     -€42   │
│ Salary      Income      +€2,100  │
├──────┬──────┬──────┬──────┬─────┤
│ Home │ Plan │  +   │Activity│More│
└──────┴──────┴──────┴──────┴─────┘
```

Cards are ordered: liquidity, plan, urgent insight, upcoming, recent activity,
goals. Tapping any aggregate reveals its calculation or filtered records.

### 5.3 Add transaction sheet

Purpose: record an expense, income, or transfer quickly and accurately.

```text
┌──────────────────────────────────┐
│          ─────                   │
│ Expense | Income | Transfer      │
│                                  │
│              € 42.50             │
│                                  │
│ Account        Main account   ▾  │
│ Category       Groceries      ▾  │
│ Payee          Continente        │
│ Date           Today          ▾  │
│ + Note, tags and status          │
│                                  │
│  1    2    3                     │
│  4    5    6      [ Save ]       │
│  7    8    9                     │
│  .    0    ⌫                     │
└──────────────────────────────────┘
```

Changing type preserves compatible input. Transfer replaces category with a
destination account and exposes a second currency amount when required.

### 5.4 Plan

Purpose: allocate and monitor the monthly plan.

```text
┌──────────────────────────────────┐
│ Plan                 July 2026 ▾ │
│ €2,000 planned • €840 remaining  │
│ [███████████░░░░░░░] 58%         │
│                                  │
│ Essentials                       │
│ Housing       €850 / €850    ✓   │
│ Groceries     €318 / €400   80%  │
│ Transport      €90 / €160   56%  │
│                                  │
│ Lifestyle                        │
│ Dining        €142 / €120  +€22  │
│                                  │
│ [ Copy last month ]   [ Edit ]   │
└──────────────────────────────────┘
```

Rows state actual/planned and remaining status. Overspent rows show the amount, not
only a red bar. Category detail explains eligible transactions and rollover.

### 5.5 Activity

Purpose: browse, search, filter, and reconcile the ledger.

Top app bar contains search and filter. Transactions group by local date and show
payee, category, account indicator, status, and amount. A sticky summary reflects
active filters. Swipe gestures are optional shortcuts; all actions remain accessible
without gestures.

### 5.6 Account detail

Purpose: understand one account and reconcile it.

Header separates ledger, cleared, and projected balances. Actions: add transaction,
reconcile, edit. The list can show a running balance. Credit accounts show limit and
utilization without implying credit advice.

### 5.7 Calendar

Purpose: show when money moves and identify low-balance periods.

Month cells use small labeled indicators for posted, scheduled, and goal events.
Selecting a day opens a bottom pane with totals and events. Agenda mode is the
accessible alternative and default at large text sizes.

### 5.8 Goals

Purpose: show whether savings targets are on track.

Goal cards contain saved/target, target date, progress, and required monthly amount.
Detail presents contributions, forecast assumptions, and scenario controls without
modifying the saved plan until confirmed.

### 5.9 Debts

Purpose: consolidate balances and payment progress.

Debt cards show current balance, interest rate when provided, minimum payment, and
next due date. Future strategy comparison uses separate avalanche/snowball tabs with
assumption disclosure.

### 5.10 Reports

Purpose: transform records into understandable trends.

Report landing page offers Income vs expense, Categories, Budget vs actual, Balance,
and Net worth. Date and scope filters remain visible. Each chart has “View as table”
and selecting a data point opens the underlying transactions.

### 5.11 Search and filters

Search begins immediately after a short debounce. Filter chips show Account,
Category, Date, Amount, Type, Status, Tags, and Attachments. Applied filters survive
detail navigation but reset when explicitly cleared.

### 5.12 Import

Four explicit steps: choose file, map fields, preview/resolve, result. Invalid rows
remain exportable as an error report. No data is committed until the confirmation
step succeeds atomically.

### 5.13 Export and backup

Export distinguishes readable reports from restorable encrypted backups. The UI
never describes CSV/PDF as a backup. Restore shows file date, schema version, record
counts, and destructive impact before authentication and confirmation.

### 5.14 Settings

Groups: Profile & region, Appearance, Finance preferences, Notifications, Privacy &
security, Data & backup, Accessibility, About. Potentially dangerous settings include
plain-language consequences.

## 6. Adaptive layouts

- Compact: bottom navigation and full-screen details.
- Medium: navigation rail; list-detail for activity and accounts.
- Expanded: permanent drawer; dashboard grid; supporting detail pane.
- Avoid orientation locking and device-type checks; respond to available width.
- Keyboard, mouse, and focus traversal are supported on large screens/ChromeOS.

## 7. Accessibility acceptance

- Minimum 48 dp interactive targets.
- WCAG AA contrast for essential text and controls.
- 200% font scaling without clipped values or unreachable actions.
- TalkBack announces amount sign/type, currency, label, and state in a natural order.
- Charts expose summaries and structured data alternatives.
- Error messages are announced and focus moves only when recovery requires it.
- Motion and haptic feedback are never the sole confirmation channel.

## 8. UX success metrics

- First account created within three minutes of launch.
- First expense recorded in under ten seconds after familiarity.
- A user can explain “available now” after onboarding without documentation.
- A user can find the transactions behind any dashboard total in two actions.
- No destructive operation can be completed accidentally with one ambiguous tap.

