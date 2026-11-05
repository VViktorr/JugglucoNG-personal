# Remaining sensor changes ported to main5 — 2026-11-05

Upstream snapshot: `ctqvva/JugglucoNG` at `8c181f65b0b037cf9f92fcb96c7bafbe6b49a6ae`.
Starting personal head: `60b9ca826448b243c4042051feab18c9d54f1b6a`.

This follow-up completes the sensor work intentionally omitted from the earlier selective integration, after explicit authorization to include other sensors.

| Area | Upstream | Included behavior |
| --- | --- | --- |
| Ottai | #739, #831 | Recoverable V3 setup, full BLE firmware revision parsing, authenticated regional firmware checks, explicit install confirmation, durable setup/OTA checkpoint and discard/retry, bounded credential recovery, storage capacity reserved before accepting extended wear, cleanup of stale bootstrap flags. |
| Dexcom | #826, #827 | Copy unsolicited G7 backfill into Room, retain the earliest received history boundary, backfill diagnostics, decode glucose flag bits and reject unavailable values, represent the no-trend byte as NaN rather than a large positive rate. |
| iCan | #834 | Already-stored/duplicate history records count as evidence of a working history lane; they no longer disable automatic or live-gap backfill for the callback lifetime. Protocol notes were updated with the upstream revision. |
| Ottai/iCan/MQ Clone wake | Remaining #818 | Live readings wake the existing stream instead of reconnecting every peer; history batches request full synchronization without reconnecting. New-sensor metadata is selected per destination host. Completes the shared counter fix from the earlier port. |
| Lumiflex/AiDex/LinX | #812 | Optional vendor cloud upload, account-scoped session/queue, observer management and controls. Only eligible locally owned AiDex-family sources can upload; Clone-owned sources are excluded. Disabled until the user configures and enables it. Vendor credential preference files are excluded from settings exports/imports. |

## Personal integration

- Kept the personal categorized settings screen, app font picker/licence link, Libre 2 patches, notification settings, widgets and instant font refresh. The Lumiflex entry is under Connections, conditional on an active AiDex-family sensor or an existing configuration.
- New Lumiflex screens use the existing app button/icon shapes and StyledSwitch. Their text follows the app font through the existing theme. Added Polish strings, including an explicit Hide readings label matching the cloud's hideState flag.
- New Ottai firmware dialogs use the app's existing button-shape helper.
- Shared packed-rate encoding/decoding was ported as one change, including watchdrip, so native NaN cannot be turned into a fake finite trend. The shared current-trend policy and drawn arrow geometry were not replaced.
- On completion, the managed-driver source directory, Dexcom source/native directory and Lumiflex cloud package match the selected upstream snapshot. UI entry points keep the personal differences described above.
- No new Libre 3/AiDex BLE driver change existed between the base and this snapshot. LibreView's shared final-value export fix was already included in the preceding port.

## Native and runtime requirements

Rebuild the native library with the application: Dexcom's backfill-state JNI method and the stream-only Clone wake JNI method must be present in the matching binary. Continue using the updated libjuice submodule from the preceding integration.

Lumiflex is opt-in. Signing in there invalidates the official application's session and vice versa (the vendor permits one session per account). No account was connected and no sensor/cloud operation was performed during this code port.

Ottai firmware transfer remains marked experimental upstream and requires its explicit UI confirmation. Importing this code does not initiate an update, activate a sensor or change any account.

## Validation

Source/merge review only; no compilation, tests, emulator or device runs. Upstream regression test sources were imported, not executed. Reviewed the JNI call sites, source-directory parity against the upstream snapshot, preserved personal paths, and diff/conflict-marker cleanliness.

Conflicts in default strings retain both personal widget strings and Lumiflex additions. The settings-screen conflict was resolved by adding the Lumiflex card to the personal Connections category, rather than replacing the screen with upstream's layout. The Clone wake conflict kept the already-integrated per-thread counter and added the remaining dispatcher/JNI/driver changes, including the corrected upstream test-method boundary.
