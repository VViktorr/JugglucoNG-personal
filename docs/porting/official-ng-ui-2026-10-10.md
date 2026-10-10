# Official JugglucoNG UI port, 2026-10-10

Source: `ctqvva/JugglucoNG`, main at
`3d5f6b1a75833fd79780fe9d772934e2f553b7a3`.
Target before this series: `libre2-hybridOK` at
`d188fbaaa4eb84e140cf0ed9914eaf2a95c560aa`.
The old personal snapshot is used only as a three-way comparison base;
it is not treated as the current official source.

## Scope and review

Reviewed 109 differing paths under UI, resources and widgets. Of these,
102 also differ between the official source and the old snapshot.
Merged their compatible changes by comparing individual hunks, then resolved
conflicts and audited dependencies. The resulting series changes 101 source,
resource and test files, plus this report. Four small Java/Kotlin support files
are included for the UI; the port does not replace native code or drivers.

Changes include current shared top bars and settings controls, Hungarian
translations, stable navigation across orientation changes, journal marker
animation/hover/accessibility, sensor cards and G7 manual pairing, setup loading
indicators, floating-overlay freshness presentation and statistics display.
Statistics apply sealed display records only to their matching raw/auto lane,
using the lane helper already present locally; glucose calculation is unchanged.
The xDrip source-label compatibility option is disabled by default and changes
only the source label when explicitly enabled, retaining Libre3 and Dexcom labels.

## Intentionally retained differences

- The local 80 dp dashboard hero, sensor end date, progress behavior and local
  widget configuration remain. Seven older differing paths (legacy status,
  widget receiver, widget provider XML and legacy display layouts) have no new
  official changes relative to the snapshot and remain local.
- LibreView manual Libre2 re-registration, status polling and region/unit/model
  diagnostics remain alongside the new presentation.
- Local notification and floating-widget settings sheets remain.
- Native sensor handling, Libre2 Hybrid NFC/BLE glucose processing, LibreView
  transport, synchronization and the combined reconnect from the target base
  remain. Official managed source-resolution and live/history-selection changes
  are not substituted for these local paths.
- Ottai setup presentation, refresh/unbind actions and late session-rejection
  navigation are adapted to the retained local `listDevices` and `unbind` API.
  Official authoritative-binding snapshot and per-request result semantics are
  not claimed: they require a separate cloud-driver change. Two tests specific
  to that absent backend contract are excluded; UI session-navigation tests are
  retained.
- G7 manual pairing uses the existing parser's 55-character payload layout.
  Official native reuse of interrupted manual-pairing records is not part of
  this UI port.

## Validation and remaining checks

Parsed changed XML, checked duplicate resource names, checked new string
references, inspected source delimiter balance and merge-marker absence,
removed duplicate imports, and checked patch whitespace. Reviewed new UI calls
against the retained backend interfaces and confirmed that local protected
actions are present after merging.

No compilation or runtime tests were run, as requested. Remaining verification
is Android compilation when authorized, device checks of rotation/navigation,
chart markers, overlays, pairing and cloud setup, and regression checks of
Hybrid reconnect and local LibreView actions. No additional compatible UI hunk
was identified in the reviewed differing paths; preserved divergences above
are not missing-port tasks.
