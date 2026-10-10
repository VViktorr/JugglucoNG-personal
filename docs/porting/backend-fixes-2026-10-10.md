# Compatible official NG fixes for Libre2 Hybrid, 2026-10-10

Base branch: `libre2-hybridOK` at
`9fd0effa9a54e9cfb9d6182a6bace5ad8f23b477`.
This series supplements the earlier UI port; it is not a claim that all
upstream backend differences should replace local Hybrid behavior.

## Completed scope

- **#603 / 35952d8:** classify phone Libre2 using the local five-field read-only
  snapshot, excluding managed direct-stream shells and respecting a sixth family
  field if present. Skip both shell/auth-state preparation and native batch
  mirroring before they can affect the phone BLE cursor. Keep skip diagnostics.
  Room storage, current selection, outbound exchange and watch native history
  remain unchanged.
- **#604 / cde9d1b:** require written note content in AAPS/Nightscout imports,
  keep sender metadata on real treatments, delete emptied imported treatments,
  and sweep imported notes consisting only of an AAPS sender stamp once in
  bounded chunks. Preserve written notes and treatment entries.
- **#560 / 11ddd0d:** add a named native current-reading lookup for registered
  Clone sensors. Reuse the native formatter/calibration, require matching identity
  and fresh data, and avoid retained local callback/raw values for Clone only.
  Non-Clone source precedence remains unchanged.
- **#499 / a0f2c4d:** finish explicit stock/resolved publication contracts across
  external drivers and virtual followers. Add opt-in incoming-sample preference
  for pending asynchronous history writes. Carry recorded view modes into
  history/export, gate sealed values by lane, and prevent cross-lane updates of
  unsealed records. Existing chart/row/prediction/statistics guards already
  present locally are retained. Include CSV/JSON sealed-record metadata.
- **#599 / 2193854:** add authoritative current-account binding lookup and
  request-scoped cloud failures for Ottai. Connect the existing UI to these
  APIs; preserve progress on expired-session replies, cloud-only unbinding,
  and the local BLE lifecycle. Restore the two backend-specific UI tests that
  the earlier UI-only port excluded. Translated messages were already present
  and match current official values in all 16 locales.

## Preserved local behavior

The combined Libre2 reconnect, native Libre2 packet processing and glucose
algorithm, local LibreView transport/options, Hybrid NFC/history precedence and
freshness checks remain. The resolver's normal path keeps its existing behavior;
incoming-sample preference is enabled explicitly by external publication only.
No official broad source-resolution or managed-family/native snapshot rewrite
was substituted for local code. Ottai changes do not perform cloud operations
while porting; they change application code for later user-triggered operations.

## Validation and remaining work

Reviewed upstream commit patches against local files and three-way merge bases.
Resolved conflicts by function, checked protected source regions, API declarations
and callers, Kotlin/Java delimiter balance, merge-marker absence and whitespace.
Added/retained upstream regressions for Clone identity/freshness, external
publication/calibration, lane revision/export, AAPS parsing, and Ottai binding and
failure races. These tests were not executed.

No compilation was run, as requested. Remaining verification: Android phone/Wear
compilation when authorized; phone -> watch -> phone ownership with continuing
Libre2 BLE readings; Clone peer/alias/freshness checks; Raw/Auto toggles with
consistent rows, statistics and exports; external calibrated publication; AAPS
import cleanup; and Ottai account refresh/unbind with expired sessions.

The five requested fixes are completed or retained where already present.
Unrelated upstream backend differences and the separate Ottai history/live
publication-claim rewrite (#602) were not included in this scoped series.
