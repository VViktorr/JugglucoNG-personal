# Selective upstream integration into main5 — 2026-11-05

Source: ctqvva/JugglucoNG main at `8c181f65b0b037cf9f92fcb96c7bafbe6b49a6ae`.
Personal starting point: `a7a39cccaebf3fe41cab39eb5b7916b495335bd5`.

This is a selective port, not a merge of the whole upstream branch. Existing personal patches were merged in place; no replacement branch or PR was created.

## Imported changes

| Area | Upstream PRs | Result |
| --- | --- | --- |
| Wear transport | #821, #822, #823, #824, #825 | Drop the unused phone-to-watch legacy stream; remove its periodic wake; limit history-chunk waits to 5 seconds while handoff retains 60 seconds; move native settings/network work to one serialized IO dispatcher; resolve peers within one node snapshot. |
| Journal and chart labels | #702, #842 | Group crowded additive labels by identity; pin food marks to the nearby curve; reveal individual grouped doses; slide the form with the keyboard; preserve sheet height on keyboard dismissal; hold-to-repeat steppers; initial amount/step suggestions with upstream confirmation rules. |
| Additional sensor display | #828 | Peer reading rows, arrow inputs and forecasts follow the same current-reading smoothing window as the main row. With smoothing off the peer data passes through unchanged. |
| Clone transport | #816, #819, #820, #829 | Bound sender handshake/ack waits at 60 seconds; web requests wake only source hosts; release the LAN multicast lock only after authenticated route confirmation; preserve route recovery and bounded probes. |
| Shared Clone wake counter | Relevant native part of #818 | Count stream wakes per sender thread instead of a shared unlocked static counter; retain other wake flags when promoting a full update. No new managed-driver JNI wake API or driver call sites were imported. |
| Published native mirror values | #806 | Persist per-sample provenance for final published virtual readings, avoid recalibrating/resmoothing them, recover canonical sensor aliases, and mask provenance bits in native consumers/exports. Legacy automatic samples retain the old calibration path. |

References: https://github.com/ctqvva/JugglucoNG/pulls (PR numbers above).

## Personal behavior retained

- Font catalogue and independent app/widget preferences, inline font previews, immediate widget refresh and custom bitmap clock rendering.
- LiveUpdate/MetricStyle settings, notifications, overlays and drawn trend-arrow appearance.
- Personal Libre 2 BLE/NFC patches and sensor-expiry preservation. Libre2GattCallback.java, SensorBluetooth.java, NfcRawHistory and libre2.cpp were not changed in this port.
- WearSync2's existing UI refresh coalescing, millisecond-to-second fix and protection against publishing replayed history as older current values.
- Personal Material/Expressive control shapes. Journal step buttons use the existing appIconButtonShapes() helper.
- The main row's delta/history policy and the shared DisplayTrendSource. Peer smoothing is applied only to the existing enabled smoothing window; the shared current-trend renderer is unchanged.

## Initial exclusions — completed in the follow-up

The sensor changes below were initially omitted, then imported after the user's next instruction. See [the sensor follow-up](2026-11-05-other-sensors.md) for the final scope and native requirements.

### Original exclusion list

- Dexcom, iCan and Ottai driver fixes, Lumiflex cloud upload and follower UI.
- The Ottai/iCan/MQ driver-call changes from #818. Their scope remains unchanged; only the shared per-sender counter improvement was selected.
- Upstream CI workflow changes. No build/test workflow was launched.

## Native dependency and compatibility

The libjuice submodule now uses `https://github.com/ctqvva/libjuice.git` pinned at `4625a1b53062ee6707d4ea8cd544c58a580c852b`. It includes the buffer-overflow and poll-array bound fixes required by the LAN-route API, along with consent checks every 12–18 seconds and expiry after 60 seconds. A missing peer may therefore take longer to detect than with the previous 30-second consent expiry.

After pulling on another checkout, run:

```powershell
git submodule sync --recursive
git submodule update --init --recursive
```

Rebuild the native library together with the app; the new resolved-mirror JNI method requires the matching native binary. Build/update both phone and Wear variants when using them together.

The new provenance bit preserves the 20-byte sample record size. Old records remain readable by the new code. Older Clone receivers whose validity check reads the entire glucose word cannot interpret the newly marked **virtual-mirror** samples; use compatible builds on both Clone endpoints when forwarding those sources. Ordinary unmarked physical-sensor samples retain their previous representation.

## Review and validation

Source/diff review only. Conflicts in JournalCompose.kt were resolved by retaining the new stepper behavior and personal control shapes. The CloneReceptionSafetyTests merge kept only tests for imported behavior, including the upstream correction to method boundaries. Upstream regression test sources were carried with the corresponding features, but were not executed.

No Gradle, Kotlin/C++ compilation, test runner, emulator or device validation was run, as requested. Static review checked call sites for the new JNI/representation arguments, the unchanged protected font/widget/Libre 2 paths, and whitespace/conflict-marker cleanliness. Runtime validation remains outstanding.
