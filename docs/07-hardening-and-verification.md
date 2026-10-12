# Financial integrity, security and performance hardening

This change implements the findings from the repository review. It keeps the app local-first and adds no cloud sync, bank connection, analytics or account system.

## Financial behavior

- Transaction entries and CSV exports retain signed amounts. Expense summaries and charts use positive magnitudes, so subtracting an expense cannot increase the monthly net amount.
- Transaction projections carry stable account/category IDs and the destination account of transfers. Account filtering no longer depends on display names.
- Summaries use only exchange rates in the selected reporting currency. Switching currency schedules new conversion work. Missing conversions are counted and disclosed instead of being interpreted as zero-value activity with a complete total.
- Foreign opening balances have their own persisted historical conversions. Budgets include converted foreign expenses and keep their allocation currency.
- Monthly summaries have both start and end boundaries and the main summary updates at local midnight. Future months do not enter the current month's cash flow.
- Recurrence day/month anchors survive short months and leap years. Forecasts use available foreign quotes as estimates and disclose missing conversions. Old dates are skipped arithmetically rather than traversed on the UI thread.
- Goals and budget entry fields use the selected currency's fraction digits, including JPY.

## Database upgrade

Room version **10** adds budget currencies, recurrence anchors and opening-balance exchange rates. The upgrade tags legacy budgets with the installation's reporting currency without changing their numeric amounts. Existing nested category merges are flattened to their final active category; future nested merges and archiving a merge target are rejected until incoming merges are undone. Rates retained for the wrong transaction date are removed and refreshed.

Version 10's exported schema is checked in. Instrumented tests cover the version 9 upgrade, currencies, real signed transactions, pagination and category merge integrity.

Older backups did not record a reporting currency. When that metadata is unavailable, restore uses the current installation currency for old budget allocations; the user should select the original currency before restoring such a backup. New backups record currencies explicitly. Historical recurrence anchors that were already lost before the upgrade cannot be reconstructed; the stored next date becomes the initial anchor.

## Privacy and file safety

- App lock re-authenticates after leaving the Activity, including after opening an external picker. Form state is retained while its dialogs and accessibility content are hidden until unlock.
- Android 8–10 use a supported biometric/device-credential combination. Enabling app lock first checks that authentication is available.
- Window capture is protected while app lock or money masking is enabled. The widget hides amounts and merchant details in either privacy mode and queries only three recent transactions when visible.
- Receipt and CSV reads happen off the UI thread and enforce byte limits while reading. The CSV parser also caps fields, columns and rows during parsing.
- Textual CSV fields are neutralized for spreadsheet formula interpretation; numeric signed amounts retain their original value.
- Backup validation verifies real monetary currencies, entry cardinality, transfer accounts, category references, rate ownership/date and converted amounts before replacement.
- Restore captures its recovery snapshot inside the same Room writer transaction as database replacement, preventing workers from inserting movements into the gap between those operations.
- Password cleanup runs in `finally`. Suspend result helpers propagate cancellation. OCR retains its bitmap until ML Kit finishes, even after cancellation.

Input limits are **8 MiB per selected receipt**, **16 MiB per CSV**, **100,000 CSV rows**, **4,096 characters per field** and **64 MiB per encrypted backup**. Stored receipt images remain capped at 1,000,000 bytes. Restore also caps the total receipt content at 32 MiB and the total database records at 500,000. Oversized imports fail without replacing the database.

## Performance and user experience

- The main transaction list loads 200 records at a time, with month/category filtering in SQL and an explicit load-more action. CSV and Excel exports fetch the complete transaction set independently of the visible page.
- Category totals and budget trends are SQL aggregates, so paging does not truncate their totals. Recovery lists are observed only while the More area is open.
- Receipt observers load metadata, not image BLOBs or raw OCR text. Widget observers run only when a widget exists.
- Production financial writes queue exchange-rate work. Requests have bounded concurrency, a bounded cache and duplicate-request coordination. Today's quotes expire after five minutes.
- PDF reports wrap lines and paginate all budgets. Excel XML and encrypted JSON exports write directly to their output streams. Receipt content uses compact Base64 in new backups while old numeric arrays remain readable.
- Forms and onboarding advance only after successful writes; failed saves retain their input. Save callbacks prevent duplicate submissions.
- Home labels and accessibility descriptions use translated resources, and fixed-height rows/cards use minimum heights for larger text.
- Release builds enable R8 and resource shrinking. CI compiles the app, runs JVM tests and lint, builds release, and schedules instrumented tests on API 29 and 35.

## Verification

The JVM regression suite covers signed cash flow, conversions, calendar anchors, bounded input, CSV neutralization, authenticated streaming encryption, legacy receipt encoding, backup invariants and OCR invalid dates. Existing tests remain part of the verification.

The actual DAO SQL was also exercised against an in-memory SQLite database built from the exported Room schema, checking monthly boundaries, currency changes, opening balances, budgets, missing conversion counts, pagination, category totals and trends.

Local commands:

```text
gradlew.bat testDebugUnitTest
gradlew.bat compileDebugAndroidTestKotlin
gradlew.bat lintDebug
gradlew.bat assembleRelease
gradlew.bat connectedDebugAndroidTest
```

Instrumented execution needs an Android device/emulator. CI configuration is not evidence that a GitHub run has already passed. The repository remains an alpha application; a release build alone does not validate biometric lifecycle behavior on every device or commercial distribution readiness.
