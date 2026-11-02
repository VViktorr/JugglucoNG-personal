# main → main5 integration audit

The eligible integration scope has been reviewed and integrated over upstream **1.2.8-Alpha**, commit `a4fbdcce5c7312ca05fe0ef74a52848df0965ae8`.

Source `main`: `ba606f408428d2885421887c83c8531a3cd50b54`. The review covers all **376 paths** changed between the common ancestor `d731a75ad3a64f305bd640a39db8f31f1bdb30ac` and that source snapshot. `main4-port` (`6a76a2e6baef16ac1022c8471eeff43160d91f53`) was also inspected for its prior conflict resolutions. Its NFC-as-current notification selection, fixed-height header layout and MetricStyle code were not copied wholesale.

`main` and `main4` are not modified. This is a selective integration, not a merge that replaces 1.2.8 with the older fork.

## Integrated functionality

- Home widgets: configurable classic/chart/range/clock-weather widgets; resizing, font/background settings, time/value axes, refresh coalescing and stale-value invalidation. The phone chart uses the merged Room display timeline across sensor handovers. No new native/NFC supplement is enabled.
- Dashboard: stacked bars, configurable width ratio, sensor expiry timestamp, complete stale-status messages. The newer measured typography, multi-sensor layout, recorded lanes, expansion behavior and allocation-efficient uncertainty rendering are retained instead of older fixed 80dp cards.
- Charts: user scale limits, readable target-boundary and foreground axis labels, label collision handling, translucent label backgrounds and consistent control styling.
- Clinical PDF reports: report groups/pages, AGP percentiles, calendar ranges, meal profiles, injection annotations, fonts and Polish/default strings.
- Settings: category navigation combined with 1.2.8's complete adaptive control groups and localized deep-link search. The older topic-key search does not replace the newer index.
- Libre 2: the subsequently authorized hybrid callback preserves upstream lifecycle safeguards and the 150-second soft watchdog, adds bounded hard escalation and private diagnostics (see `libre2-hybrid-main5.md`). Diagnostic scan logging in `SensorBluetooth.java` leaves its roster/pause/reconnect logic intact. LibreView region/unit configuration, reauthentication, token/device-registration retry, timeout/cooldown handling and masked payload diagnostics are integrated for Libre 2.
- Shared display/infrastructure: current-value freshness, AOD status rendering, notification status typography, value-available alert, Health Connect journal activity import, Wear timestamp/framing/order checks, complication refresh coalescing and bitmap caching. The newer standard notification text/forwarding policy and independent smoothing/collapse settings are preserved.
- Support: manual phone/Wear builders, fork README, portable distribution path handling and relevant regression tools. App version, SDK, dependency versions and newer upstream CI remain from 1.2.8. No release was published or APK build requested.

## Explicit exclusions and holds

1. **Libre2 callback hold lifted by the user for the hybrid implementation.** The personal 120-second stream watchdog is not copied; upstream 150-second soft recovery precedes one bounded hard escalation. See `libre2-hybrid-main5.md` for exact timing, privacy changes and validation.
2. **Authorized NFC isolation for Libre 2.** The minute NFC trend now uses the personal `NfcRawHistory` sidecar instead of advancing the BLE poll cursor. Only storage and dispatch were integrated; `AlgNfcV`, `ScanNfcV`, native `libre2.cpp` and `g.cpp` retain 1.2.8. Fifteen-minute gap filling and Path + Raw are unchanged. Personal NFC-as-current selection and older Raw/export semantics remain excluded. See `nfc-ble-isolation-main5.md`.
3. **Other sensors stay at 1.2.8.** The complete driver tree, Libre3 source tree and sensor setup screens are preserved. Common `SuperGattCallback` remains original. In the shared `Libreview.java`, Libre3 calls explicitly delegate to preserved 1.2.8 method bodies, and its original config fetch is retained. The new Libre2 cloud behavior does not replace those methods.
4. **Personal MetricStyle and glucose Live Update are excluded**, including classes, legacy/Compose toggles, resources, receiver, permission and tests. Features already in 1.2.8 are retained.
5. Older AiDex/iCan/Ottai implementation and model-state changes do not replace newer 1.2.8 logic. In particular the newer AiDex state format, source ownership checks, transactional display-lock invalidation, calibration handling and sensor removal/setup paths remain original.

The source differences listed below have no unreviewed entries. The later user approvals cover the Libre2 hybrid callback and NFC/BLE storage isolation. Older NFC Raw/export and notification selection remain outside that approved scope.

## Validation

- `python tools/check-main5-preserved.py`: confirms **221 protected files** (the current manifest additionally pins the three authorized NFC isolation files; this follow-up was reviewed without running the checker) against recorded upstream SHA-256 values, and verifies original Libre3 cloud method bodies after internal renaming.
- Production Libre2 LibreView replay: unit/region selection, forced registration, finite auth/device retries, HTTP/JSON rate limits, timeouts and connection cleanup pass.
- Widget production-dispatch replay: both home and Glance renderers deliver an update racing with completion.
- Wear production-ingestion replay: all **16 scenarios** pass, including malformed frames, out-of-order timestamps, ownership/disabled gates and phone Libre2 handling. Native/Room/platform APIs use test doubles; no sensor is contacted.
- Focused JUnit checks for refresh scheduling, axis layout, AGP percentiles, clinical date/GMI/dose policy and report pagination: **27 tests** pass. Report DTO/resource/annotation dependencies are small JVM fixtures; production algorithms are compiled unchanged.
- Distribution tooling suite: **98 tests** pass. UI guardrail detector suite: **8 tests** pass; `scripts/ui-guardrails.py` also passes.
- Changed Java/Kotlin sources parse without new errors relative to 1.2.8; changed phone/Wear resource references resolve; `git diff --check` passes.
- Resource format audit introduces no new errors relative to 1.2.8. The baseline itself has malformed Polish HTML help/orphan-translation findings and missing translations; those are not represented as newly passing or repaired. The frozen NFC-related help is not rewritten.
- **No full Android/APK compilation, device BLE test, visual PDF export or runtime launcher/weather test was performed.** These focused checks do not establish device/runtime behavior.

## Per-path disposition

"Already identical" includes personal improvements already present in the newer upstream tree. Entries marked as preserved retain the newer implementation rather than taking the entire older file. The table records the source delta, not all files changed by this integration.

| Path | Disposition |
| --- | --- |
| `.github/workflows/ci.yml` | Preserved newer upstream CI jobs and checks; manual builders added separately without starting APK compilation |
| `.github/workflows/personal-apk.yml` | Manual APK builder adapted to main5; no release publishing or automatic build triggered |
| `.github/workflows/personal-wear-apk.yml` | Manual APK builder adapted to main5; no release publishing or automatic build triggered |
| `.gitignore` | Integrated personal support/tooling delta |
| `.greptile/config.json` | Integrated personal support/tooling delta |
| `Common/build.gradle` | Preserved original 1.2.8 version, SDK and dependency versions; personal 2.0.0 version bump is not a feature port |
| `Common/proguard-rules.my` | Integrated personal regression/tooling/support file |
| `Common/src/libre3/java/tk/glucodata/Libre3GattCallback.java` | Preserved original 1.2.8 per user: other sensor implementations and common BLE callback lifecycle are not replaced |
| `Common/src/main/cpp/NfcRawHistory.hpp` | Integrated separate NFC minute-trend storage only; not exported into the upstream Raw lane |
| `Common/src/main/cpp/SensorGlucoseData.hpp` | Minimal sidecar writer added; all newer upstream sensor metadata/storage logic retained |
| `Common/src/main/cpp/datbackup.cpp` | Integrated personal delta over 1.2.8 |
| `Common/src/main/cpp/g.cpp` | Original native storage and NFC/history exporters preserved; original Path/Raw export unchanged after NFC sidecar isolation |
| `Common/src/main/cpp/libre2.cpp` | Deferred with NFC/native history lane integration; original 1.2.8 retained |
| `Common/src/main/cpp/logjava.cpp` | Integrated personal delta over 1.2.8 |
| `Common/src/main/cpp/net/libreview/libreview.cpp` | Integrated personal delta over 1.2.8 |
| `Common/src/main/cpp/net/libreview/newlibre3.cpp` | Preserved original 1.2.8 per user: other sensor implementations and common BLE callback lifecycle are not replaced |
| `Common/src/main/cpp/net/libreview/putsensor.cpp` | Only Libre2 registration diagnostics masked; Libre3 registration unchanged |
| `Common/src/main/cpp/settings/javasettings.cpp` | Integrated personal delta over 1.2.8 |
| `Common/src/main/cpp/share/savehistory.cpp` | Libre2 trend isolated from savepoll; other sensor dispatch and 15-minute history stay upstream |
| `Common/src/main/cpp/watch/netinfo.cpp` | Integrated personal delta over 1.2.8 |
| `Common/src/main/cpp/watch/watchvalue.cpp` | Use immutable sensor roster snapshot and current epoch time |
| `Common/src/main/java/tk/glucodata/AlgNfcV.java` | Deferred with NFC/native history lane integration; original 1.2.8 retained |
| `Common/src/main/java/tk/glucodata/CloneRecoveryWake.kt` | Integrated personal delta over 1.2.8 |
| `Common/src/main/java/tk/glucodata/CommonCanvas.kt` | Integrated personal delta over 1.2.8 |
| `Common/src/main/java/tk/glucodata/CurrentDisplaySource.kt` | Preserved causal filter and added current-value freshness bound |
| `Common/src/main/java/tk/glucodata/CurrentGlucoseSource.kt` | Integrated personal delta over 1.2.8 |
| `Common/src/main/java/tk/glucodata/CustomGlucoseNotification.java` | Status rendering merged; upstream standard-text/watch forwarding policy preserved |
| `Common/src/main/java/tk/glucodata/DataSmoothing.kt` | Integrated personal delta over 1.2.8 |
| `Common/src/main/java/tk/glucodata/DisplayTrendSource.kt` | Integrated personal delta over 1.2.8 |
| `Common/src/main/java/tk/glucodata/ExchangeTrend.java` | Already identical to main |
| `Common/src/main/java/tk/glucodata/GlucoseLiveUpdate.java` | Excluded: personal MetricStyle/Live Update feature and assets/tests |
| `Common/src/main/java/tk/glucodata/GlucoseLiveUpdateDismissReceiver.java` | Excluded: personal MetricStyle/Live Update feature and assets/tests |
| `Common/src/main/java/tk/glucodata/GlucoseLiveUpdatePolicy.java` | Excluded: personal MetricStyle/Live Update feature and assets/tests |
| `Common/src/main/java/tk/glucodata/GlucosePoint.java` | Already identical to main |
| `Common/src/main/java/tk/glucodata/GlucoseWidget.java` | Integrated in prior widget stage; preserve newer upstream freshness, calibration and manifest capabilities; NFC supplement excluded |
| `Common/src/main/java/tk/glucodata/HealthConnectAccess.kt` | Integrated personal delta over 1.2.8 |
| `Common/src/main/java/tk/glucodata/HistoryRepositoryAccess.kt` | Already identical to main |
| `Common/src/main/java/tk/glucodata/HistoryRepositoryBridge.kt` | Already identical to main |
| `Common/src/main/java/tk/glucodata/LeadingTrailingRefresh.kt` | Integrated personal delta over 1.2.8 |
| `Common/src/main/java/tk/glucodata/Libre2GattCallback.java` | Authorized hybrid: upstream 150-second soft recovery, one additional hard recovery after 150 further seconds, private detailed diagnostics; NFC sidecar isolation integrated separately |
| `Common/src/main/java/tk/glucodata/MessageReceiver.kt` | Integrated personal delta over 1.2.8 |
| `Common/src/main/java/tk/glucodata/MessageSender.kt` | Integrated personal delta over 1.2.8 |
| `Common/src/main/java/tk/glucodata/Natives.java` | Merged country/auth/alert bridge |
| `Common/src/main/java/tk/glucodata/NightPost.java` | Integrated personal delta over 1.2.8 |
| `Common/src/main/java/tk/glucodata/NotificationChartDrawer.java` | Integrated in prior widget stage; preserve newer upstream freshness, calibration and manifest capabilities; NFC supplement excluded |
| `Common/src/main/java/tk/glucodata/NotificationChartModelSource.kt` | Integrated personal delta over 1.2.8 |
| `Common/src/main/java/tk/glucodata/NotificationHistorySource.kt` | Deferred with NFC/native history lane integration; original 1.2.8 retained |
| `Common/src/main/java/tk/glucodata/NotificationPredictionSeries.java` | Already identical to main |
| `Common/src/main/java/tk/glucodata/NotificationValueBitmap.java` | Status typography and bitmap bounds |
| `Common/src/main/java/tk/glucodata/Notify.java` | Status presentation/diagnostics merged; NFC history selection deferred, MetricStyle excluded |
| `Common/src/main/java/tk/glucodata/RemoteGlucose.java` | Already identical to main |
| `Common/src/main/java/tk/glucodata/ScanNfcV.java` | Deferred with NFC/native history lane integration; original 1.2.8 retained |
| `Common/src/main/java/tk/glucodata/SensorBluetooth.java` | Only instanceof Libre2GattCallback scan diagnostics; all 1.2.8 roster, pause and reconnect mechanisms unchanged |
| `Common/src/main/java/tk/glucodata/SensorOwnership.kt` | Integrated personal delta over 1.2.8 |
| `Common/src/main/java/tk/glucodata/SuperGattCallback.java` | Preserved original 1.2.8 per user: other sensor implementations and common BLE callback lifecycle are not replaced |
| `Common/src/main/java/tk/glucodata/SyncedWearCalibrationProvider.kt` | Integrated personal delta over 1.2.8 |
| `Common/src/main/java/tk/glucodata/UiRefreshBus.kt` | Integrated personal delta over 1.2.8 |
| `Common/src/main/java/tk/glucodata/WearHistoryLanes.kt` | Deferred with NFC/history lane changes; not added without corresponding native storage changes |
| `Common/src/main/java/tk/glucodata/WearSync2.kt` | Timestamp units, framing validation, ordered exchange and coalesced UI refresh integrated; raw-only history lane changes deferred with NFC/native store |
| `Common/src/main/java/tk/glucodata/WidgetDisplaySource.kt` | Integrated in prior widget stage; preserve newer upstream freshness, calibration and manifest capabilities; NFC supplement excluded |
| `Common/src/main/java/tk/glucodata/accessibility/AODOverlayService.kt` | AOD freshness: preserved upstream status and arrow guards, merged personal current resolver |
| `Common/src/main/java/tk/glucodata/alerts/AlertConfig.kt` | Integrated personal delta over 1.2.8 |
| `Common/src/main/java/tk/glucodata/alerts/AlertRepository.kt` | Integrated personal delta over 1.2.8 |
| `Common/src/main/java/tk/glucodata/chart/HistoryChartModel.kt` | Already identical to main |
| `Common/src/main/java/tk/glucodata/chart/HistoryChartModelBuilder.kt` | Preserve chart/smoothing metadata and exact lane deltas |
| `Common/src/main/java/tk/glucodata/chart/UncertaintyBands.kt` | Already identical to main |
| `Common/src/main/java/tk/glucodata/drivers/ManagedBluetoothSensorDriver.kt` | Preserve newer 1.2.8 driver and algorithm implementation/tests |
| `Common/src/main/java/tk/glucodata/drivers/ManagedSensorRuntime.kt` | Already identical to main |
| `Common/src/main/java/tk/glucodata/drivers/aidex/AiDexAlgorithmController.kt` | Already identical to main |
| `Common/src/main/java/tk/glucodata/drivers/aidex/AiDexAlgorithmReferences.kt` | Preserve newer 1.2.8 driver and algorithm implementation/tests |
| `Common/src/main/java/tk/glucodata/drivers/aidex/AiDexAlgorithmRuntime.kt` | Preserve newer 1.2.8 driver and algorithm implementation/tests |
| `Common/src/main/java/tk/glucodata/drivers/aidex/AiDexAlgorithmSelection.kt` | Already identical to main |
| `Common/src/main/java/tk/glucodata/drivers/aidex/AiDexDriver.kt` | Preserve newer 1.2.8 driver and algorithm implementation/tests |
| `Common/src/main/java/tk/glucodata/drivers/aidex/AiDexManagedSensorIdentityAdapter.kt` | Preserve newer 1.2.8 driver and algorithm implementation/tests |
| `Common/src/main/java/tk/glucodata/drivers/aidex/AiDexRetainedCalibration.kt` | Already identical to main |
| `Common/src/main/java/tk/glucodata/drivers/aidex/AiDexSampleJournal.kt` | Already identical to main |
| `Common/src/main/java/tk/glucodata/drivers/aidex/AiDexSourceJournalBackup.kt` | Already identical to main |
| `Common/src/main/java/tk/glucodata/drivers/aidex/adaptive2/AdaptiveV2Estimate.kt` | Already identical to main |
| `Common/src/main/java/tk/glucodata/drivers/aidex/adaptive2/AdaptiveV2Estimator.kt` | Already identical to main |
| `Common/src/main/java/tk/glucodata/drivers/aidex/adaptive2/AdaptiveV2Modes.kt` | Already identical to main |
| `Common/src/main/java/tk/glucodata/drivers/aidex/adaptive2/AdaptiveV2Observation.kt` | Already identical to main |
| `Common/src/main/java/tk/glucodata/drivers/aidex/adaptive2/AdaptiveV2State.kt` | Already identical to main |
| `Common/src/main/java/tk/glucodata/drivers/aidex/adaptive2/AiDexAdaptiveV2.kt` | Preserve newer 1.2.8 driver and algorithm implementation/tests |
| `Common/src/main/java/tk/glucodata/drivers/aidex/native/ble/AiDexBleManager.kt` | Preserve newer 1.2.8 driver and algorithm implementation/tests |
| `Common/src/main/java/tk/glucodata/drivers/aidex/native/ble/AiDexHistoryPolicy.kt` | Already identical to main |
| `Common/src/main/java/tk/glucodata/drivers/icanhealth/ICanHealthBleManager.kt` | Preserve newer 1.2.8 driver and algorithm implementation/tests |
| `Common/src/main/java/tk/glucodata/drivers/icanhealth/ICanHealthConstants.kt` | Preserve newer 1.2.8 driver and algorithm implementation/tests |
| `Common/src/main/java/tk/glucodata/drivers/ottai/OttaiManagedSensorIdentityAdapter.kt` | Already identical to main |
| `Common/src/main/java/tk/glucodata/drivers/ottai/OttaiRegistry.kt` | Already identical to main |
| `Common/src/main/java/tk/glucodata/drivers/sibionics/SibionicsConstants.kt` | Already identical to main |
| `Common/src/main/java/tk/glucodata/settings/Settings.java` | Excluded: only personal MetricStyle toggle and its wiring |
| `Common/src/main/res/layout/arrowandvalue.xml` | Notification/widget layout delta |
| `Common/src/main/res/layout/notification_phone_compact.xml` | Notification/widget layout delta |
| `Common/src/main/res/layout/notification_phone_expanded.xml` | Notification/widget layout delta |
| `Common/src/main/res/layout/notification_phone_status.xml` | Notification/widget layout delta |
| `Common/src/main/res/layout/text.xml` | Notification/widget layout delta |
| `Common/src/main/res/values-be/strings.xml` | Existing newer strings preserved; no missing eligible personal entries |
| `Common/src/main/res/values-de/strings.xml` | Existing newer strings preserved; no missing eligible personal entries |
| `Common/src/main/res/values-fr/strings.xml` | Existing newer strings preserved; no missing eligible personal entries |
| `Common/src/main/res/values-hu/strings.xml` | Existing newer strings preserved; no missing eligible personal entries |
| `Common/src/main/res/values-it/strings.xml` | Existing newer strings preserved; no missing eligible personal entries |
| `Common/src/main/res/values-mn/strings.xml` | Existing newer strings preserved; no missing eligible personal entries |
| `Common/src/main/res/values-nl/strings.xml` | Existing newer strings preserved; no missing eligible personal entries |
| `Common/src/main/res/values-pl/notification_live_update.xml` | Excluded: personal MetricStyle/Live Update feature and assets/tests |
| `Common/src/main/res/values-pl/strings.xml` | Resource entries merged by name; preserve conflicting newer translations and exclude MetricStyle/Live Update/NFC additions |
| `Common/src/main/res/values-pt/strings.xml` | Existing newer strings preserved; no missing eligible personal entries |
| `Common/src/main/res/values-ru/strings.xml` | Existing newer strings preserved; no missing eligible personal entries |
| `Common/src/main/res/values-so/strings.xml` | Existing newer strings preserved; no missing eligible personal entries |
| `Common/src/main/res/values-sv/strings.xml` | Existing newer strings preserved; no missing eligible personal entries |
| `Common/src/main/res/values-tr/strings.xml` | Existing newer strings preserved; no missing eligible personal entries |
| `Common/src/main/res/values-uk/strings.xml` | Existing newer strings preserved; no missing eligible personal entries |
| `Common/src/main/res/values-zh/strings.xml` | Existing newer strings preserved; no missing eligible personal entries |
| `Common/src/main/res/values/font_certs.xml` | Font resource support retained |
| `Common/src/main/res/values/notification_live_update.xml` | Excluded: personal MetricStyle/Live Update feature and assets/tests |
| `Common/src/main/res/values/strings.xml` | Resource entries merged by name; preserve conflicting newer translations and exclude MetricStyle/Live Update/NFC additions |
| `Common/src/mobile/AndroidManifest.xml` | Integrated in prior widget stage; preserve newer upstream freshness, calibration and manifest capabilities; NFC supplement excluded |
| `Common/src/mobile/assets/report_fonts/Inter-OFL.txt` | Already identical to main |
| `Common/src/mobile/assets/report_fonts/Liberation-OFL.txt` | Already identical to main |
| `Common/src/mobile/java/tk/glucodata/Libreview.java` | Personal Libre2 cloud diagnostics/auth/registration integrated; explicit Libre3 delegation to original 1.2.8 methods; original Libre3 config fetch retained |
| `Common/src/mobile/java/tk/glucodata/MobileHealthAccess.kt` | Integrated personal delta over 1.2.8 |
| `Common/src/mobile/java/tk/glucodata/WidgetStatusText.kt` | Integrated in prior widget stage; preserve newer upstream freshness, calibration and manifest capabilities; NFC supplement excluded |
| `Common/src/mobile/java/tk/glucodata/data/AiDexHistoryImport.kt` | Preserved newer 1.2.8 driver ownership, before/after display-lock and sensor setup/removal behavior per user scope |
| `Common/src/mobile/java/tk/glucodata/data/ExportPackageExporter.kt` | Preserved newer 1.2.8 driver ownership, before/after display-lock and sensor setup/removal behavior per user scope |
| `Common/src/mobile/java/tk/glucodata/data/HistoryBucketReplacement.kt` | Deferred with NFC/native history lane integration; original 1.2.8 retained |
| `Common/src/mobile/java/tk/glucodata/data/HistoryRepository.kt` | Only widget merged-history read helper integrated; original newer transactional/history invalidation retained; NFC lane writes deferred |
| `Common/src/mobile/java/tk/glucodata/data/HistorySync.kt` | Deferred with NFC/native history lane integration; original 1.2.8 retained |
| `Common/src/mobile/java/tk/glucodata/data/MobileHistoryRepositoryBridge.kt` | Only widget merged-history read helper integrated; original newer transactional/history invalidation retained; NFC lane writes deferred |
| `Common/src/mobile/java/tk/glucodata/data/ReadingUncertaintyDao.kt` | Already identical to main |
| `Common/src/mobile/java/tk/glucodata/data/RecordedDisplayVoiding.kt` | Preserved newer 1.2.8 driver ownership, before/after display-lock and sensor setup/removal behavior per user scope |
| `Common/src/mobile/java/tk/glucodata/data/SettingsExporter.kt` | Already identical to main |
| `Common/src/mobile/java/tk/glucodata/ui/AiDexAlgorithmSheet.kt` | Already identical to main |
| `Common/src/mobile/java/tk/glucodata/ui/AlarmScreen.kt` | Personal control styling reapplied; newer 1.2.8 layout/search/interaction retained |
| `Common/src/mobile/java/tk/glucodata/ui/ApiSourceSettingsScreen.kt` | Personal control styling reapplied; newer 1.2.8 layout/search/interaction retained |
| `Common/src/mobile/java/tk/glucodata/ui/AppUpdateCards.kt` | Integrated personal UI delta over 1.2.8 |
| `Common/src/mobile/java/tk/glucodata/ui/AppUpdatesScreen.kt` | Integrated personal UI delta over 1.2.8 |
| `Common/src/mobile/java/tk/glucodata/ui/CgmReadiness.kt` | Integrated personal UI delta over 1.2.8 |
| `Common/src/mobile/java/tk/glucodata/ui/ChartAxisLayout.kt` | Integrated personal UI delta over 1.2.8 |
| `Common/src/mobile/java/tk/glucodata/ui/ChartScaleLimits.kt` | Integrated personal UI delta over 1.2.8 |
| `Common/src/mobile/java/tk/glucodata/ui/ChartScalePreferences.kt` | Integrated personal UI delta over 1.2.8 |
| `Common/src/mobile/java/tk/glucodata/ui/CloneHistoryRecoverySheet.kt` | Personal control styling reapplied; newer 1.2.8 layout/search/interaction retained |
| `Common/src/mobile/java/tk/glucodata/ui/CloneSettingsContent.kt` | Personal control styling reapplied; newer 1.2.8 layout/search/interaction retained |
| `Common/src/mobile/java/tk/glucodata/ui/DashboardChart.kt` | Personal scale limits and foreground target/axis labels; preserve expanded backdrop, recorded lanes and allocation-efficient ribbons |
| `Common/src/mobile/java/tk/glucodata/ui/DashboardChartPipeline.kt` | Preserve chart/smoothing metadata and exact lane deltas |
| `Common/src/mobile/java/tk/glucodata/ui/DashboardComponents.kt` | Bar preferences, sensor expiry and status wrapping integrated; newer adaptive multi-sensor typography/expanded layout retained instead of fixed 80dp cards |
| `Common/src/mobile/java/tk/glucodata/ui/DashboardScreen.kt` | Bar preferences, sensor expiry and status wrapping integrated; newer adaptive multi-sensor typography/expanded layout retained instead of fixed 80dp cards |
| `Common/src/mobile/java/tk/glucodata/ui/DataSmoothingSettingsScreen.kt` | Personal control styling reapplied; newer 1.2.8 layout/search/interaction retained |
| `Common/src/mobile/java/tk/glucodata/ui/DebugSettingsScreen.kt` | Integrated personal UI delta over 1.2.8 |
| `Common/src/mobile/java/tk/glucodata/ui/DisplaySettingsScreens.kt` | Personal control styling reapplied; newer 1.2.8 layout/search/interaction retained |
| `Common/src/mobile/java/tk/glucodata/ui/ExchangeSettingsScreens.kt` | Personal control styling reapplied; newer 1.2.8 layout/search/interaction retained |
| `Common/src/mobile/java/tk/glucodata/ui/ExpressiveSettingsScreen.kt` | Category navigation merged with newer complete control groups, adaptive controls, and localized deep-link search; personal topic-key index replaced by newer full search index |
| `Common/src/mobile/java/tk/glucodata/ui/GlucoseMeterSettingsScreen.kt` | Integrated personal UI delta over 1.2.8 |
| `Common/src/mobile/java/tk/glucodata/ui/GlucosePaletteSettings.kt` | Integrated personal UI delta over 1.2.8 |
| `Common/src/mobile/java/tk/glucodata/ui/GlucoseRangeDisplaySettings.kt` | Chart scale bounds integrated; adaptive trailing controls retained |
| `Common/src/mobile/java/tk/glucodata/ui/GlucoseUncertaintyDisplay.kt` | Personal control styling reapplied; newer 1.2.8 layout/search/interaction retained |
| `Common/src/mobile/java/tk/glucodata/ui/GlucoseUncertaintyRibbon.kt` | Personal control styling reapplied; newer 1.2.8 layout/search/interaction retained |
| `Common/src/mobile/java/tk/glucodata/ui/HeroReadingLayout.kt` | Already identical to main |
| `Common/src/mobile/java/tk/glucodata/ui/HistoryBrowseScreen.kt` | Integrated personal UI delta over 1.2.8 |
| `Common/src/mobile/java/tk/glucodata/ui/HistoryDataTransfer.kt` | Integrated personal UI delta over 1.2.8 |
| `Common/src/mobile/java/tk/glucodata/ui/HybridSettingsContent.kt` | Integrated personal UI delta over 1.2.8 |
| `Common/src/mobile/java/tk/glucodata/ui/InsulinPenScanSheet.kt` | Integrated personal UI delta over 1.2.8 |
| `Common/src/mobile/java/tk/glucodata/ui/InsulinPenSettingsScreen.kt` | Personal control styling reapplied; newer 1.2.8 layout/search/interaction retained |
| `Common/src/mobile/java/tk/glucodata/ui/LibreViewSettingsScreen.kt` | Integrated personal UI delta over 1.2.8 |
| `Common/src/mobile/java/tk/glucodata/ui/MQAccountFields.kt` | Original 1.2.8 MQ sensor/account UI retained per user scope |
| `Common/src/mobile/java/tk/glucodata/ui/MQAccountSettingsScreen.kt` | Original 1.2.8 MQ sensor/account UI retained per user scope |
| `Common/src/mobile/java/tk/glucodata/ui/MQFollowerSettingsScreen.kt` | Original 1.2.8 MQ sensor/account UI retained per user scope |
| `Common/src/mobile/java/tk/glucodata/ui/MainNavigation.kt` | Widgets route and asynchronous journal Health Connect activity import; upstream navigation/search kept |
| `Common/src/mobile/java/tk/glucodata/ui/MirrorSettingsScreen.kt` | Personal control styling reapplied; newer 1.2.8 layout/search/interaction retained |
| `Common/src/mobile/java/tk/glucodata/ui/NightscoutSettingsScreen.kt` | Personal control styling reapplied; newer 1.2.8 layout/search/interaction retained |
| `Common/src/mobile/java/tk/glucodata/ui/OutboundApiSettingsScreen.kt` | Personal control styling reapplied; newer 1.2.8 layout/search/interaction retained |
| `Common/src/mobile/java/tk/glucodata/ui/PredictionModelProfileScreen.kt` | Integrated personal UI delta over 1.2.8 |
| `Common/src/mobile/java/tk/glucodata/ui/ReadingRow.kt` | Integrated personal UI delta over 1.2.8 |
| `Common/src/mobile/java/tk/glucodata/ui/SensorCard.kt` | Integrated personal UI delta over 1.2.8 |
| `Common/src/mobile/java/tk/glucodata/ui/SensorTraceLog.kt` | Integrated personal UI delta over 1.2.8 |
| `Common/src/mobile/java/tk/glucodata/ui/SmsDestinationEditor.kt` | Integrated personal UI delta over 1.2.8 |
| `Common/src/mobile/java/tk/glucodata/ui/alerts/AlertButtonStyle.kt` | Integrated personal UI delta over 1.2.8 |
| `Common/src/mobile/java/tk/glucodata/ui/alerts/AlertSettingsScreen.kt` | Integrated personal UI delta over 1.2.8 |
| `Common/src/mobile/java/tk/glucodata/ui/alerts/CommonAlertSettings.kt` | Integrated personal UI delta over 1.2.8 |
| `Common/src/mobile/java/tk/glucodata/ui/alerts/CustomAlertDialog.kt` | Integrated personal UI delta over 1.2.8 |
| `Common/src/mobile/java/tk/glucodata/ui/alerts/GlobalAlertSettingsCard.kt` | Integrated personal UI delta over 1.2.8 |
| `Common/src/mobile/java/tk/glucodata/ui/alerts/QuietWindowCard.kt` | Personal control styling reapplied; newer 1.2.8 layout/search/interaction retained |
| `Common/src/mobile/java/tk/glucodata/ui/alerts/SoundPicker.kt` | Integrated personal UI delta over 1.2.8 |
| `Common/src/mobile/java/tk/glucodata/ui/alerts/TalkerSettingsScreen.kt` | Integrated personal UI delta over 1.2.8 |
| `Common/src/mobile/java/tk/glucodata/ui/calibration/CalibrationBottomSheet.kt` | Personal control styling reapplied; newer 1.2.8 layout/search/interaction retained |
| `Common/src/mobile/java/tk/glucodata/ui/calibration/CalibrationListScreen.kt` | Integrated personal UI delta over 1.2.8 |
| `Common/src/mobile/java/tk/glucodata/ui/components/AppSlider.kt` | Already identical to main |
| `Common/src/mobile/java/tk/glucodata/ui/components/AppTopBar.kt` | Integrated personal UI delta over 1.2.8 |
| `Common/src/mobile/java/tk/glucodata/ui/components/ColorSwatchButton.kt` | Integrated personal UI delta over 1.2.8 |
| `Common/src/mobile/java/tk/glucodata/ui/components/NoSensorCard.kt` | Integrated personal UI delta over 1.2.8 |
| `Common/src/mobile/java/tk/glucodata/ui/components/SensorSelectionCards.kt` | Integrated personal UI delta over 1.2.8 |
| `Common/src/mobile/java/tk/glucodata/ui/components/SensorTypePicker.kt` | Integrated personal UI delta over 1.2.8 |
| `Common/src/mobile/java/tk/glucodata/ui/components/SettingsComponents.kt` | Personal control styling reapplied; newer 1.2.8 layout/search/interaction retained |
| `Common/src/mobile/java/tk/glucodata/ui/components/StyledSwitch.kt` | Contrast colors combined with upstream interaction source |
| `Common/src/mobile/java/tk/glucodata/ui/journal/JournalActionMenus.kt` | Integrated personal UI delta over 1.2.8 |
| `Common/src/mobile/java/tk/glucodata/ui/journal/JournalCalculationsSettingsScreen.kt` | Integrated personal UI delta over 1.2.8 |
| `Common/src/mobile/java/tk/glucodata/ui/journal/JournalCompose.kt` | Personal control styling reapplied; newer 1.2.8 layout/search/interaction retained |
| `Common/src/mobile/java/tk/glucodata/ui/journal/JournalFabMenuPopup.kt` | Integrated personal UI delta over 1.2.8 |
| `Common/src/mobile/java/tk/glucodata/ui/journal/JournalScreen.kt` | Integrated personal UI delta over 1.2.8 |
| `Common/src/mobile/java/tk/glucodata/ui/journal/JournalSettingsScreen.kt` | Personal control styling reapplied; newer 1.2.8 layout/search/interaction retained |
| `Common/src/mobile/java/tk/glucodata/ui/setup/AiDexKeyManagementScreen.kt` | Original 1.2.8 setup UI retained along with sensor implementations per user scope |
| `Common/src/mobile/java/tk/glucodata/ui/setup/AiDexSetupWizard.kt` | Original 1.2.8 setup UI retained along with sensor implementations per user scope |
| `Common/src/mobile/java/tk/glucodata/ui/setup/AnytimeSetupWizard.kt` | Original 1.2.8 setup UI retained along with sensor implementations per user scope |
| `Common/src/mobile/java/tk/glucodata/ui/setup/DexcomSetupWizard.kt` | Original 1.2.8 setup UI retained along with sensor implementations per user scope |
| `Common/src/mobile/java/tk/glucodata/ui/setup/ICanHealthSetupWizard.kt` | Original 1.2.8 setup UI retained along with sensor implementations per user scope |
| `Common/src/mobile/java/tk/glucodata/ui/setup/InlineQrScannerCard.kt` | Original 1.2.8 setup UI retained along with sensor implementations per user scope |
| `Common/src/mobile/java/tk/glucodata/ui/setup/LibreSetupWizard.kt` | Original 1.2.8 setup UI retained along with sensor implementations per user scope |
| `Common/src/mobile/java/tk/glucodata/ui/setup/MQSetupWizard.kt` | Original 1.2.8 setup UI retained along with sensor implementations per user scope |
| `Common/src/mobile/java/tk/glucodata/ui/setup/OttaiSetupWizard.kt` | Original 1.2.8 setup UI retained along with sensor implementations per user scope |
| `Common/src/mobile/java/tk/glucodata/ui/setup/SibionicsSetupWizard.kt` | Original 1.2.8 setup UI retained along with sensor implementations per user scope |
| `Common/src/mobile/java/tk/glucodata/ui/stats/AgpReportProfile.kt` | Already identical to main |
| `Common/src/mobile/java/tk/glucodata/ui/stats/ClinicalReportPolicy.kt` | Already identical to main |
| `Common/src/mobile/java/tk/glucodata/ui/stats/LibreViewMealProfile.kt` | Already identical to main |
| `Common/src/mobile/java/tk/glucodata/ui/stats/LibreViewReportGroup.kt` | Already identical to main |
| `Common/src/mobile/java/tk/glucodata/ui/stats/LibreViewReportLayout.kt` | Already identical to main |
| `Common/src/mobile/java/tk/glucodata/ui/stats/LibreViewStyleReport.kt` | Already identical to main |
| `Common/src/mobile/java/tk/glucodata/ui/stats/ReportGroupPicker.kt` | Already identical to main |
| `Common/src/mobile/java/tk/glucodata/ui/stats/StatsLayoutEditor.kt` | Already identical to main |
| `Common/src/mobile/java/tk/glucodata/ui/stats/StatsMetrics.kt` | Already identical to main |
| `Common/src/mobile/java/tk/glucodata/ui/stats/StatsModels.kt` | Already identical to main |
| `Common/src/mobile/java/tk/glucodata/ui/stats/StatsRangeControls.kt` | Personal control styling reapplied; newer 1.2.8 layout/search/interaction retained |
| `Common/src/mobile/java/tk/glucodata/ui/stats/StatsReportExporter.kt` | Already identical to main |
| `Common/src/mobile/java/tk/glucodata/ui/stats/StatsScreen.kt` | Already identical to main |
| `Common/src/mobile/java/tk/glucodata/ui/stats/StatsViewModel.kt` | Clinical report calendar/targets integrated in prior report stage; preserve upstream repository resolution |
| `Common/src/mobile/java/tk/glucodata/ui/util/AppButtonStyle.kt` | Already identical to main |
| `Common/src/mobile/java/tk/glucodata/ui/util/ConnectedButtonGroup.kt` | Pill shape/borders combined with upstream enabled state and animations |
| `Common/src/mobile/java/tk/glucodata/ui/util/GlucosePointInterop.kt` | Already identical to main |
| `Common/src/mobile/java/tk/glucodata/ui/viewmodel/DashboardViewModel.kt` | Bar preferences, sensor expiry and status wrapping integrated; newer adaptive multi-sensor typography/expanded layout retained instead of fixed 80dp cards |
| `Common/src/mobile/java/tk/glucodata/ui/viewmodel/OrderedSensorActions.kt` | Already identical to main |
| `Common/src/mobile/java/tk/glucodata/ui/viewmodel/SensorViewModel.kt` | Preserved newer 1.2.8 driver ownership, before/after display-lock and sensor setup/removal behavior per user scope |
| `Common/src/mobile/java/tk/glucodata/widget/ChartWidgetSizing.kt` | Already identical to main |
| `Common/src/mobile/java/tk/glucodata/widget/ExpressiveAppWidget.kt` | Already identical to main |
| `Common/src/mobile/java/tk/glucodata/widget/ExpressiveWidgetReceiver.kt` | Already identical to main |
| `Common/src/mobile/java/tk/glucodata/widgets/ClockWeatherWidgetViews.kt` | Already identical to main |
| `Common/src/mobile/java/tk/glucodata/widgets/GlucoseWidgets.kt` | Already identical to main |
| `Common/src/mobile/java/tk/glucodata/widgets/WidgetConfig.kt` | Already identical to main |
| `Common/src/mobile/java/tk/glucodata/widgets/WidgetData.kt` | Integrated in prior widget stage; preserve newer upstream freshness, calibration and manifest capabilities; NFC supplement excluded |
| `Common/src/mobile/java/tk/glucodata/widgets/WidgetRenderer.kt` | Already identical to main |
| `Common/src/mobile/java/tk/glucodata/widgets/WidgetSettingsScreen.kt` | Already identical to main |
| `Common/src/mobile/java/tk/glucodata/widgets/WidgetUpdater.kt` | Already identical to main |
| `Common/src/mobile/java/tk/glucodata/widgets/WidgetWeatherSource.kt` | Already identical to main |
| `Common/src/mobile/res/drawable/widget_preview_basic_static.png` | Already identical to main |
| `Common/src/mobile/res/drawable/widget_preview_bg.xml` | Already identical to main |
| `Common/src/mobile/res/drawable/widget_preview_chart.xml` | Already identical to main |
| `Common/src/mobile/res/drawable/widget_preview_clock_weather_static.png` | Already identical to main |
| `Common/src/mobile/res/drawable/widget_preview_expressive_static.png` | Already identical to main |
| `Common/src/mobile/res/drawable/widget_preview_range_static.png` | Already identical to main |
| `Common/src/mobile/res/font/report_arial_compatible_bold.ttf` | Already identical to main |
| `Common/src/mobile/res/font/report_arial_compatible_regular.ttf` | Already identical to main |
| `Common/src/mobile/res/font/report_inter_bold.ttf` | Already identical to main |
| `Common/src/mobile/res/font/report_inter_regular.ttf` | Already identical to main |
| `Common/src/mobile/res/layout/expressive_clock_ibm_300.xml` | Already identical to main |
| `Common/src/mobile/res/layout/expressive_clock_ibm_500.xml` | Already identical to main |
| `Common/src/mobile/res/layout/expressive_clock_ibm_600.xml` | Already identical to main |
| `Common/src/mobile/res/layout/expressive_clock_ibm_700.xml` | Already identical to main |
| `Common/src/mobile/res/layout/expressive_clock_system_300.xml` | Already identical to main |
| `Common/src/mobile/res/layout/expressive_clock_system_500.xml` | Already identical to main |
| `Common/src/mobile/res/layout/expressive_clock_system_600.xml` | Already identical to main |
| `Common/src/mobile/res/layout/expressive_clock_system_700.xml` | Already identical to main |
| `Common/src/mobile/res/layout/expressive_widget_system_clock.xml` | Already identical to main |
| `Common/src/mobile/res/layout/expressive_widget_system_clock_ibm.xml` | Already identical to main |
| `Common/src/mobile/res/layout/glucose_widget.xml` | Already identical to main |
| `Common/src/mobile/res/layout/home_widget.xml` | Already identical to main |
| `Common/src/mobile/res/layout/home_widget_clock_weather.xml` | Already identical to main |
| `Common/src/mobile/res/layout/home_widget_clock_weather_ibm.xml` | Already identical to main |
| `Common/src/mobile/res/layout/home_widget_clock_weather_ibm_main_system_detail.xml` | Already identical to main |
| `Common/src/mobile/res/layout/home_widget_clock_weather_system_main_ibm_detail.xml` | Already identical to main |
| `Common/src/mobile/res/layout/widget_preview_basic.xml` | Already identical to main |
| `Common/src/mobile/res/layout/widget_preview_clock_weather.xml` | Already identical to main |
| `Common/src/mobile/res/layout/widget_preview_expressive.xml` | Already identical to main |
| `Common/src/mobile/res/layout/widget_preview_range.xml` | Already identical to main |
| `Common/src/mobile/res/values-pl/agp_report.xml` | Already identical to main |
| `Common/src/mobile/res/values-pl/html.xml` | Help resource left at 1.2.8 while NFC-related changes are frozen |
| `Common/src/mobile/res/values-pl/strings.xml` | Resource entries merged by name; preserve conflicting newer translations and exclude MetricStyle/Live Update/NFC additions |
| `Common/src/mobile/res/values/agp_report.xml` | Already identical to main |
| `Common/src/mobile/res/values/font_certs.xml` | Font resource support retained |
| `Common/src/mobile/res/values/strings.xml` | Resource entries merged by name; preserve conflicting newer translations and exclude MetricStyle/Live Update/NFC additions |
| `Common/src/mobile/res/values/widget_text_styles.xml` | Already identical to main |
| `Common/src/mobile/res/xml/expressive_widget_info.xml` | Already identical to main |
| `Common/src/mobile/res/xml/glucose_widget_info.xml` | Already identical to main |
| `Common/src/mobile/res/xml/home_widget_clock_weather_info.xml` | Already identical to main |
| `Common/src/mobile/res/xml/home_widget_range_info.xml` | Already identical to main |
| `Common/src/test/java/tk/glucodata/ExchangeTrendTests.java` | Already identical to main |
| `Common/src/test/java/tk/glucodata/GlucoseLiveUpdatePolicyTests.java` | Excluded: personal MetricStyle/Live Update feature and assets/tests |
| `Common/src/test/java/tk/glucodata/LeadingTrailingRefreshTests.kt` | Integrated personal regression/tooling/support file |
| `Common/src/test/java/tk/glucodata/LegacyOngoingNotificationTests.kt` | Not added: coupled to deferred NFC-as-current selection or older notification policy |
| `Common/src/test/java/tk/glucodata/LiveCalibrationPolicyTests.kt` | Already identical to main |
| `Common/src/test/java/tk/glucodata/NotificationChartSmoothingTests.kt` | Already identical to main |
| `Common/src/test/java/tk/glucodata/NotificationStartupRestoreTests.kt` | Preserved newer upstream regression expectations for freshness, standard text, sensor handling and optimized uncertainty rendering |
| `Common/src/test/java/tk/glucodata/OngoingNotificationHistoryTests.kt` | Not added: coupled to deferred NFC-as-current selection or older notification policy |
| `Common/src/test/java/tk/glucodata/TrendConsumerPointListTests.kt` | Preserved newer upstream regression expectations for freshness, standard text, sensor handling and optimized uncertainty rendering |
| `Common/src/test/java/tk/glucodata/WearHistoryLanesTests.kt` | Deferred with NFC/history lane changes; not added without corresponding native storage changes |
| `Common/src/test/java/tk/glucodata/WidgetCurrentFreshnessTests.kt` | Already identical to main |
| `Common/src/test/java/tk/glucodata/chart/UncertaintyBandsTests.kt` | Already identical to main |
| `Common/src/test/java/tk/glucodata/data/NativeNfcHistoryTests.kt` | Deferred with NFC/native history lane integration; original 1.2.8 retained |
| `Common/src/test/java/tk/glucodata/drivers/aidex/AiDexAlgorithmControllerTest.kt` | Preserve newer 1.2.8 driver and algorithm implementation/tests |
| `Common/src/test/java/tk/glucodata/drivers/aidex/AiDexSampleJournalTest.kt` | Already identical to main |
| `Common/src/test/java/tk/glucodata/drivers/aidex/adaptive2/AiDexAdaptiveV2Test.kt` | Preserve newer 1.2.8 driver and algorithm implementation/tests |
| `Common/src/test/java/tk/glucodata/drivers/aidex/native/ble/AiDexHistoryPolicyTests.kt` | Already identical to main |
| `Common/src/test/java/tk/glucodata/drivers/icanhealth/ICanHealthTimelinePolicyTests.kt` | Preserve newer 1.2.8 driver and algorithm implementation/tests |
| `Common/src/test/java/tk/glucodata/drivers/ottai/OttaiRemovalNativeShellTests.kt` | Already identical to main |
| `Common/src/test/resources/arch/composable-sharedprefs-allowlist.txt` | Remove stale Glance preference exception after moving reads to WidgetConfigStore |
| `Common/src/test/resources/java/OngoingHistoryHarness.java` | Not added: coupled to deferred NFC-as-current selection or older notification policy |
| `Common/src/test/resources/java/OngoingTimerHarness.java` | Not added: coupled to deferred NFC-as-current selection or older notification policy |
| `Common/src/test/resources/native/measured_history.cpp` | Deferred with NFC/native history lane integration; original 1.2.8 retained |
| `Common/src/test/resources/native/nfc_raw_history.cpp` | Deferred with NFC/native history lane integration; original 1.2.8 retained |
| `Common/src/test/resources/native/sensor_snapshot.cpp` | Not imported: coupled to out-of-scope callback lifetime changes for other sensors |
| `Common/src/test/resources/native/wear_raw_history.cpp` | Deferred with NFC/history lane changes; not added without corresponding native storage changes |
| `Common/src/testMobile/java/tk/glucodata/ChartWidgetSizingTests.kt` | Already identical to main |
| `Common/src/testMobile/java/tk/glucodata/ClockWeatherWidgetWiringTests.kt` | Already identical to main |
| `Common/src/testMobile/java/tk/glucodata/CustomGlucoseNotificationTests.kt` | Preserved newer upstream regression expectations for freshness, standard text, sensor handling and optimized uncertainty rendering |
| `Common/src/testMobile/java/tk/glucodata/ExternalCurrentReadingTests.kt` | Already identical to main |
| `Common/src/testMobile/java/tk/glucodata/LegacyWidgetSizingWiringTests.kt` | Already identical to main |
| `Common/src/testMobile/java/tk/glucodata/NotificationUncertaintyRibbonTests.kt` | Preserved newer upstream regression expectations for freshness, standard text, sensor handling and optimized uncertainty rendering |
| `Common/src/testMobile/java/tk/glucodata/RedesignedWidgetsWiringTests.kt` | Already identical to main |
| `Common/src/testMobile/java/tk/glucodata/data/HistoryBucketReplacementTests.kt` | Deferred with NFC/native history lane integration; original 1.2.8 retained |
| `Common/src/testMobile/java/tk/glucodata/data/RecordedDisplayVoidingTests.kt` | Preserved newer 1.2.8 driver ownership, before/after display-lock and sensor setup/removal behavior per user scope |
| `Common/src/testMobile/java/tk/glucodata/drivers/aidex/AiDexAlgorithmRuntimeTests.kt` | Preserve newer 1.2.8 driver and algorithm implementation/tests |
| `Common/src/testMobile/java/tk/glucodata/ui/ChartAxisLayoutTests.kt` | Integrated personal regression/tooling/support file |
| `Common/src/testMobile/java/tk/glucodata/ui/DashboardReadingDeltaTests.kt` | Already identical to main |
| `Common/src/testMobile/java/tk/glucodata/ui/GlucoseUncertaintyTests.kt` | Already identical to main |
| `Common/src/testMobile/java/tk/glucodata/ui/HeroReadingLayoutTests.kt` | Already identical to main |
| `Common/src/testMobile/java/tk/glucodata/ui/setup/OttaiSetupFlowTests.kt` | Preserved newer 1.2.8 driver ownership, before/after display-lock and sensor setup/removal behavior per user scope |
| `Common/src/testMobile/java/tk/glucodata/ui/stats/AgpReportProfileTests.kt` | Already identical to main |
| `Common/src/testMobile/java/tk/glucodata/ui/stats/ClinicalReportPolicyTests.kt` | Already identical to main |
| `Common/src/testMobile/java/tk/glucodata/ui/stats/LibreViewMealProfileTests.kt` | Already identical to main |
| `Common/src/testMobile/java/tk/glucodata/ui/stats/LibreViewReportPagesTests.kt` | Already identical to main |
| `Common/src/testMobile/java/tk/glucodata/ui/viewmodel/OrderedSensorActionsTests.kt` | Already identical to main |
| `Common/src/wear/AndroidManifest.xml` | Integrated personal delta over 1.2.8 |
| `Common/src/wear/java/tk/glucodata/glucosecomplication/ArrowAboveValueDataSourceService.kt` | Integrated personal delta over 1.2.8 |
| `Common/src/wear/java/tk/glucodata/glucosecomplication/ArrowValueDataSourceService.kt` | Integrated personal delta over 1.2.8 |
| `Common/src/wear/java/tk/glucodata/glucosecomplication/ChartComplicationBase.kt` | Integrated personal delta over 1.2.8 |
| `Common/src/wear/java/tk/glucodata/glucosecomplication/ComplicationRenderer.kt` | Bitmap/spark caches integrated with thread-local paint AND bounds and upstream collapse-minutes policy |
| `Common/src/wear/java/tk/glucodata/glucosecomplication/GlucoseComplicationData.kt` | Integrated personal delta over 1.2.8 |
| `Common/src/wear/java/tk/glucodata/glucosecomplication/NumberDataSourceService.kt` | Integrated personal delta over 1.2.8 |
| `Common/src/wear/java/tk/glucodata/glucosecomplication/ShortArrowValueDataSourceService.kt` | Integrated personal delta over 1.2.8 |
| `Common/src/wear/java/tk/glucodata/ui/WearGlucoseStore.kt` | Integrated personal delta over 1.2.8 |
| `Common/src/wear/java/tk/glucodata/ui/screens/ChartScreen.kt` | Integrated personal delta over 1.2.8 |
| `Common/src/wear/java/tk/glucodata/ui/screens/MainScreen.kt` | Integrated personal delta over 1.2.8 |
| `Common/src/wear/java/tk/glucodata/ui/screens/RecentReadingsScreen.kt` | Integrated personal delta over 1.2.8 |
| `Common/src/wear/java/tk/glucodata/ui/screens/SensorScreen.kt` | Integrated personal delta over 1.2.8 |
| `Common/src/wear/res/values-pl/strings.xml` | Resource entries merged by name; preserve conflicting newer translations and exclude MetricStyle/Live Update/NFC additions |
| `Common/src/wear/res/values/strings.xml` | Resource entries merged by name; preserve conflicting newer translations and exclude MetricStyle/Live Update/NFC additions |
| `README.md` | Personal fork identity/build information; retain current upstream support/credits; no claims about deferred Libre2 watchdog |
| `debug.sh` | Integrated personal regression/tooling/support file |
| `docs/aidex-adaptive-v2.md` | Preserve newer 1.2.8 driver and algorithm implementation/tests |
| `docs/architecture/glucose-live-updates.md` | Excluded: personal MetricStyle/Live Update feature and assets/tests |
| `docs/architecture/hero-delta-layout-validation.md` | Already identical to main |
| `docs/architecture/notification-modernization.md` | Preserved newer upstream standard-text/forwarding policy; no obsolete no-standard-text override |
| `docs/assets/download-android.svg` | Preserved upstream generated asset, no longer linked as a fork download |
| `docs/assets/hero-delta/height-comparison-large.png` | Already identical to main |
| `docs/assets/hero-delta/height-comparison.png` | Already identical to main |
| `docs/assets/hero-delta/pair-render.png` | Already identical to main |
| `docs/integration-main2.md` | Superseded historical port notes; current main5 audit replaces them |
| `docs/integration-main3.md` | Superseded historical port notes; current main5 audit replaces them |
| `docs/libreview-report-layout.md` | Already identical to main |
| `scripts/aidex/AiDexReplay.kt` | Already identical to main |
| `scripts/aidex/preview-ui.py` | Already identical to main |
| `scripts/aidex/replay.py` | Preserve newer 1.2.8 driver and algorithm implementation/tests |
| `scripts/aidex/report.py` | Already identical to main |
| `scripts/aidex/tests/test_replay.py` | Preserve newer 1.2.8 driver and algorithm implementation/tests |
| `scripts/aidex/tests/test_report.py` | Already identical to main |
| `scripts/dist/dist.py` | Integrated personal regression/tooling/support file |
| `scripts/dist/tests/test_readme_downloads.py` | Fork environment isolation combined with newer upstream stale-button regression test |
| `tools/check-translations.py` | Integrated personal support/tooling delta |
| `tools/notification-native-view-probe/README.md` | Already identical to main |
| `tools/notification-native-view-probe/evidence/status-font-ibm-compact.png` | Already identical to main |
| `tools/notification-native-view-probe/src/tk/glucodata/ProductionPreview.java` | Already identical to main |
| `tools/test-air-driver.py` | Integrated personal support/tooling delta |
| `tools/test-libre2-connect.py` | Authorized hybrid: upstream 150-second soft recovery, one additional hard recovery after 150 further seconds, private detailed diagnostics; NFC sidecar isolation integrated separately |
| `tools/test-libre3-connect.py` | Preserved original 1.2.8 per user: other sensor implementations and common BLE callback lifecycle are not replaced |
| `tools/test-libreview-config.py` | Integrated personal regression/tooling/support file |
| `tools/test-sensor-lifecycle.py` | Not imported: coupled to out-of-scope callback lifetime changes for other sensors |
| `tools/test-wear-sync2.py` | Integrated personal regression/tooling/support file |
| `tools/test-widget-refresh.py` | Already identical to main |
| `tools/tests/history_recovery_regression.py` | Deferred with NFC/native history lane integration; original 1.2.8 retained |
| `tools/tests/sensor_history_sync_regression.py` | Deferred with NFC/native history lane integration; original 1.2.8 retained |
| `tools/tests/stale_notification_regression.py` | Not added: coupled to deferred NFC-as-current selection or older notification policy |

## Authorized Libre 2 follow-up

See [Libre 2 hybrid design and validation](libre2-hybrid-main5.md). Only the callback was removed from the original-file freeze manifest; the remaining 223 protected sensor/NFC/native files retain their original hashes. Shared Java/C++ log sinks now redact identities, credentials, byte dumps and pointer values before writes. This changes diagnostic text for all callers, not other sensor algorithms. No full APK build was requested.

## Authorized NFC/BLE storage isolation

See [NFC isolation and unchanged Path/Raw behavior](nfc-ble-isolation-main5.md). This follow-up modifies only the authorized storage files and updates their manifest entries. Compilation and tests were not run, as instructed.
