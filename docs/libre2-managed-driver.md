# Kotlin Libre 2 driver

Experimental and off by default. **No physical sensor has been used with it, and its glucose
values have not been compared with the vendor algorithm. Do not use its readings for treatment
decisions.** Turn it on in Settings → Sensor connection → Kotlin Libre 2 driver, restart the
app, then scan the sensor. The backend is chosen once at app start and never changes while the
app runs.

## What runs with the switch on

No vendor library is loaded or called for Libre 1 or Libre 2.

| Part | Where it runs |
|---|---|
| Gen2 NFC activation, enable streaming, FRAM read | Kotlin (`drivers/libre2`) |
| Gen2 BLE login and frame decryption | Kotlin |
| First-generation NFC payloads, BLE unlock payload, FRAM and BLE decryption | open native backend |
| Sensor family, warm-up and wear times, activation state | open native backend |
| Glucose, trend and history from scans and BLE frames | open native backend |

The open native backend is `Common/src/main/cpp/libre2_imp`, taken from Juggluco 12
(`libre2_imp/backend.cpp` and `processing/`, GPL-3.0-or-later). Two things were left out: its
Gen2 JNI session layer, which the Kotlin code replaces, and the scan-trend field this fork's
result type does not have. `libre2.cpp` picks the backend at run time (`libre2Independent()`).

Connection lifecycle, reconnects and the stream watchdog stay in `Libre2GattCallback`.
`Libre2Gen1Crypto` (Kotlin) reproduces the first-generation payloads and is covered by tests,
but the app uses the native backend for them.

With the switch off, everything goes through the vendor library as before. A build without
vendor libraries (such as a plain checkout) can only read Libre 1/2 with the switch on.

### Switching a running sensor

The two backends never receive each other's algorithm state. An empty file
`libre2imp.backend` in the sensor directory marks state saved by the open backend.

- Switch on → restart → scan. The open backend takes over a compatible saved state, or
  rebuilds it from the sensor memory and the saved start time. BLE frames are not processed
  until the state is usable; the stream reloads it after the scan.
- Switch off → restart → scan. The vendor algorithm ignores the open backend's state and
  initializes again from the sensor, as for a sensor it has not seen. The first scan gives no
  reading. BLE frames wait for that scan.

The glucose values of the two backends can differ. With the switch on there is no vendor
fallback anywhere: a failed Kotlin Gen2 NFC or BLE step is reported as the failure.

## Evidence

- Gen2 Speck/KDF/CTR/CRC, the BLE login request and the NFC enable request reproduce the
  synthetic vectors from the reverse-engineering notes and Juggluco's cipher self test.
- Gen2 session-key derivation, frame decryption, FRAM block decryption and reply checks are
  tested only against a simulated sensor written from the same protocol description. That
  shows internal consistency, not interoperability.
- First-generation payloads and BLE decryption (Kotlin) match the vendor-library fixtures in
  `Common/src/main/cpp` (`getStreamingPayload.h`, `getActivationPayload.h`,
  `getStreamingUnlockPayload.h`, the frame in `abbotttest.cpp`).
- The open glucose backend compiles and links here. Its readings have not been compared
  with the vendor algorithm or a sensor in this fork. Upstream reports testing mainly on
  European Libre 2.

## Choices that need a sensor to confirm

- The NFC request carries all 15 encrypted bytes, as Juggluco 12 does. The earlier wrapper
  sent a zero as the last byte.
- Root slots 1 and 2 have keys. A stored slot of 0 or 0xFFFF, or a 10-byte record without a
  slot, uses slot 1. Any other slot stops with "unsupported root slot".
- Over BLE the last two bytes of the 25-byte login reply are not checked; no checksum for
  them is known. The seven-byte echo of our random value is checked.
- The existing 10/12-byte provisioning record in the sensor file is reused as is, so a sensor
  paired by either path can log in with the other.

## Test checklist

Send the "Libre2Managed", "Gen2" and "Libre2GattCallback" log lines with each failure. They
carry no keys, unlock payloads or glucose values.

0. Any Libre 1/2, switch on, scan: a reading appears. Compare it and the next hours with a
   second reader or app before trusting it.
1. Gen2, switch on, scan a running sensor: readings arrive. A "Kotlin FRAM read failed" line
   means the Kotlin read was rejected.
2. Gen2, enable streaming by scan, then wait for BLE: the status line must not show
   "Kotlin: …". "Kotlin Gen2 session" in the log means the Kotlin login was accepted.
3. Toggle Bluetooth, walk out of range and back, restart the app: BLE logs in again each time.
4. Turn the switch off (vendor library present), restart, scan twice, reconnect: the vendor
   library takes over and readings resume.
5. Activate a new Gen2 sensor with the switch on.
6. First generation: with the switch on, scan, enable streaming, BLE reconnects and history.
7. Two sensors at once, one of each generation if available.
8. Switch on, delete or corrupt the sensor's saved state, scan: the state is rebuilt, no crash.
9. Scan in the last hour of wear and the day after with the switch on: every scan reads.
