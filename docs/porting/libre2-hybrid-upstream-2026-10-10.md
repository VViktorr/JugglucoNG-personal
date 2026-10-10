# Upstream compatibility review — 2026-10-10

Branch: `libre2-hybridOK`.
Comparison snapshot: `personal-upstream-2026-10-05` (`a3e99301204fcf34e30ea8282d5b702783cff25d`).
Observed starting HEAD: `0cfa3f10d58b57b269cd2737a1cc9e5df3cc2d1a`.
Code-review result HEAD: `135461b164f561f5b1cb92e6c9f37f3f517ba987`.

The supplied known HEAD `1b4fa324349ae8e2a185824d6497a795a3ebdb6f` already had four later upstream-spacing commits on the branch when this review began. They were retained.

## Result

Reviewed the 110 differing common files (56 UI, 54 outside UI) and the tree inventory of 57 local-only files. No upstream-only files are missing. The snapshot is an ancestor of the local branch; tree differences alone are not unported upstream work.

Six code commits restore identified compatible upstream safeguards and correct two migration defects. No further compatible omission was identified in this comparison. Remaining differences below are retained local changes, not a backlog of 110 replacements.

## Commits

| Commit | Change |
| --- | --- |
| `320b77bba007b261618e664ab6db418bf7260f89` | Native write result/raw forwarding and removed-sensor guard |
| `626d0d8059d3cd0b5f1e42a2ff687dd949a5e68a` | Bounded/finally-released Libre2 wake lock, GATT cleanup and BLE error history |
| `b5fa3616c19277748f46e5b280d9c9890c2ef49e` | Journal lifecycle collector argument |
| `8a7f75e0140d64f1db1ab05f96106245e341ca8b` | Managed-driver reconnect protections beside phone Libre2 Hybrid |
| `aa1a90cce01d9a1b1be161aca7bb21aca6573039` | Migration-introduced duplicate setup import |
| `135461b164f561f5b1cb92e6c9f37f3f517ba987` | Managed disconnect cancellation and legacy Android CCCD write type |

## Preserved design decisions

- Libre2 packet/bootstrap generation, watchdog and manual-style recovery remain local.
- NFC trends reach Room history without appending into the Abbott live BLE cursor; WearSync2 does not mirror phone Libre2 chunks into that cursor.
- Explicit Direct-sensor routing controls ownership. Netinfo is telemetry and does not silently transfer BLE ownership.
- LibreView request handling, regional/device registration behavior, diagnostics and cleanup remain local.
- Local calibration/display lanes, glucose thresholds, merged history, widget supplementation and refresh ordering remain.
- Local Material Expressive design, app-selected dark mode, variable font weights, compact dashboard and journal placement remain. Reverting numeric styling differences solely to match the snapshot would undo this design.
- Local widgets and Wear complication text/rendering remain.

## Validation and limits

Read both tree inventories and per-file differences. Inspected the changed Kotlin/Java sources (90 files), current native storage/removed-sensor helpers, GATT deadline lock implementation, Applic wake-lock helpers, and Anytime/Sibionics driver lifecycle hooks. Scanned changed collector calls/imports. Verified the six changed files fetched from the result commit exactly match the reviewed contents, and the comparison from the observed starting HEAD contains exactly six code commits and six changed source files.

No Gradle, compiler or build was run, as requested. Android/BLE runtime behavior has not been exercised in this environment. Device checks for Hybrid recovery, managed-driver reconnect/pause/handoff and older-Android CCCD behavior remain validation work; they are not identified missing upstream changes.

## Common-file disposition

“Ported + retained” means the identified safeguard was restored while the other local differences remain. “Retained” means intentional local divergence.

| File | Disposition | Reason |
| --- | --- | --- |
| `BUILD.ps1` | Retained | Local transient injection/cleanup of proprietary Libre libraries. |
| `Common/build.gradle` | Retained | Local Compose/Material 3 versions and Android minor SDK. |
| `Common/src/main/cpp/SensorGlucoseData.hpp` | Ported + retained | Ported write-result/raw forwarding; retained Hybrid cursor diagnostics and storage logic. |
| `Common/src/main/cpp/config.h` | Retained | Local Libre runtime/path configuration. |
| `Common/src/main/cpp/g.cpp` | Retained | Local Libre 2 JNI generation entry point, sensor thresholds, and NFC trend export to Room without advancing live polls. |
| `Common/src/main/cpp/libre2.cpp` | Ported + retained | Ported removed-sensor guard; retained Libre runtime, diagnostics and Room-based NFC recovery. |
| `Common/src/main/cpp/sensoren.hpp` | Retained | Preserve existing native sensor info.dat rather than reinitializing Abbott metadata. |
| `Common/src/main/cpp/share/nfcdata.cpp` | Retained | Local formatting/include arrangement; no missing runtime NFC implementation identified. |
| `Common/src/main/cpp/share/savehistory.cpp` | Retained | Retain diagnostic counters and Room-only replacement for saveScanTrend; do not write NFC trends into live BLE polls. |
| `Common/src/main/cpp/watch/netinfo.cpp` | Retained | Transport telemetry does not own physical BLE; explicit ownership routing remains authoritative. |
| `Common/src/main/java/tk/glucodata/AlgNfcV.java` | Retained | Local original Libre NFC bootstrap and immediate tag-loss/security handling. |
| `Common/src/main/java/tk/glucodata/Gen2.java` | Retained | Local original Libre Gen2 command path. |
| `Common/src/main/java/tk/glucodata/GlucoseWidget.java` | Retained | Local widget sizing/configuration, refresh lifecycle and stale-state presentation. |
| `Common/src/main/java/tk/glucodata/HistoryRepositoryAccess.kt` | Retained | Local bridge for merged multi-sensor widget display history. |
| `Common/src/main/java/tk/glucodata/HistoryRepositoryBridge.kt` | Retained | Local optional merged-history widget bridge. |
| `Common/src/main/java/tk/glucodata/Libre2GattCallback.java` | Ported + retained | Ported bounded/finally-released wake lock; preserved Hybrid watchdog, hard recovery, bootstrap and packet decoding. |
| `Common/src/main/java/tk/glucodata/MessageReceiver.kt` | Retained | Local explicit Direct-sensor ownership command semantics. |
| `Common/src/main/java/tk/glucodata/MessageSender.kt` | Retained | Local telemetry-only netinfo semantics. |
| `Common/src/main/java/tk/glucodata/Natives.java` | Retained | Local Libre2 JNI declaration and user-visible AVAILABLE alarm integration. |
| `Common/src/main/java/tk/glucodata/NotificationHistorySource.kt` | Retained | Local native widget supplement with sensor identity. |
| `Common/src/main/java/tk/glucodata/RemoteGlucose.java` | Retained | Local configurable widget rendering, layout and font settings. |
| `Common/src/main/java/tk/glucodata/ScanNfcV.java` | Retained | Local resolved sensor identity and expired-sensor dialog. |
| `Common/src/main/java/tk/glucodata/SensorBluetooth.java` | Retained | Preserve phone Libre2 connection exception, staggered reconnects and scan diagnostics. |
| `Common/src/main/java/tk/glucodata/SensorOwnershipRuntime.kt` | Retained | Local explicit routing reconciliation; automatic preference does not independently change ownership. |
| `Common/src/main/java/tk/glucodata/SuperGattCallback.java` | Ported + retained | Ported managed-driver cancellation/coalescing, transport-only retirement, autoConnect, deadlines, scan fallback, error history and cleanup. Preserved phone Libre2 Hybrid and newer CCCD APIs. |
| `Common/src/main/java/tk/glucodata/WearSync2.kt` | Retained | Local phone Libre2 native mirror exclusion prevents poisoning the Abbott live stream cursor; Room persistence remains. |
| `Common/src/main/java/tk/glucodata/WidgetDisplaySource.kt` | Retained | Local merged display timeline and native supplement, with display-minute precedence. |
| `Common/src/main/java/tk/glucodata/alerts/AlertConfig.kt` | Retained | Local user-visible AVAILABLE alert; AMOUNT remains hidden. |
| `Common/src/main/java/tk/glucodata/alerts/AlertRepository.kt` | Retained | Local AVAILABLE native-backed configuration without forced startup disable. |
| `Common/src/main/res/layout/arrowandvalue.xml` | Retained | Retained resources and registrations for local widgets, status, complications and force-dark behavior. |
| `Common/src/main/res/layout/text.xml` | Retained | Retained resources and registrations for local widgets, status, complications and force-dark behavior. |
| `Common/src/main/res/values-pl/strings.xml` | Retained | Retained resources and registrations for local widgets, status, complications and force-dark behavior. |
| `Common/src/main/res/values/strings.xml` | Retained | Retained resources and registrations for local widgets, status, complications and force-dark behavior. |
| `Common/src/mobile/AndroidManifest.xml` | Retained | Retained resources and registrations for local widgets, status, complications and force-dark behavior. |
| `Common/src/mobile/java/tk/glucodata/Libreview.java` | Retained | Retained local LibreView connection diagnostics, request stages and guaranteed HTTP cleanup. |
| `Common/src/mobile/java/tk/glucodata/WatchInterop.kt` | Retained | Local explicit routing reconciliation and phone BLE rearming on Direct-off. |
| `Common/src/mobile/java/tk/glucodata/data/HistoryRepository.kt` | Retained | Local merged multi-sensor display history for widgets. |
| `Common/src/mobile/java/tk/glucodata/data/HistorySync.kt` | Retained | Local widget refresh after asynchronous NFC/Room merge. |
| `Common/src/mobile/java/tk/glucodata/data/MobileHistoryRepositoryBridge.kt` | Retained | Local widget bridge implementation. |
| `Common/src/mobile/java/tk/glucodata/service/FloatingGlucoseService.kt` | Retained | Local force-dark opt-out for the floating overlay. |
| `Common/src/mobile/java/tk/glucodata/ui/AlarmActivity.kt` | Retained | Retained local expressive/shared UI components, geometry, typography, theme and lifecycle adaptations. |
| `Common/src/mobile/java/tk/glucodata/ui/ApiSourceSettingsScreen.kt` | Retained | Retained local expressive/shared UI components, geometry, typography, theme and lifecycle adaptations. |
| `Common/src/mobile/java/tk/glucodata/ui/AppUpdateCards.kt` | Retained | Retained local expressive/shared UI components, geometry, typography, theme and lifecycle adaptations. |
| `Common/src/mobile/java/tk/glucodata/ui/AppUpdatesScreen.kt` | Retained | Retained local expressive/shared UI components, geometry, typography, theme and lifecycle adaptations. |
| `Common/src/mobile/java/tk/glucodata/ui/CgmReadiness.kt` | Retained | Retained local expressive/shared UI components, geometry, typography, theme and lifecycle adaptations. |
| `Common/src/mobile/java/tk/glucodata/ui/CloneHistoryRecoverySheet.kt` | Retained | Retained local expressive/shared UI components, geometry, typography, theme and lifecycle adaptations. |
| `Common/src/mobile/java/tk/glucodata/ui/CloneSettingsContent.kt` | Retained | Retained local expressive/shared UI components, geometry, typography, theme and lifecycle adaptations. |
| `Common/src/mobile/java/tk/glucodata/ui/DashboardChart.kt` | Retained | Local app-theme-aware rendering, measured journal chip collision/pile layout, bounded labels and lifecycle collection. |
| `Common/src/mobile/java/tk/glucodata/ui/DashboardChartPipeline.kt` | Retained | Local sealed display value applies only to its calibration lane. |
| `Common/src/mobile/java/tk/glucodata/ui/DashboardComponents.kt` | Retained | Local lifecycle-aware collection, compact sensor/glucose cards, expiry timestamp and status palette. |
| `Common/src/mobile/java/tk/glucodata/ui/DashboardScreen.kt` | Retained | Local FontScaleCap wrappers, lifecycle collection and native post-extension sensor end date. |
| `Common/src/mobile/java/tk/glucodata/ui/DataSmoothingSettingsScreen.kt` | Retained | Retained local expressive/shared UI components, geometry, typography, theme and lifecycle adaptations. |
| `Common/src/mobile/java/tk/glucodata/ui/DisplaySettingsScreens.kt` | Retained | Retained local expressive/shared UI components, geometry, typography, theme and lifecycle adaptations. |
| `Common/src/mobile/java/tk/glucodata/ui/ExchangeSettingsScreens.kt` | Retained | Retained local expressive/shared UI components, geometry, typography, theme and lifecycle adaptations. |
| `Common/src/mobile/java/tk/glucodata/ui/ExpressiveSettingsScreen.kt` | Retained | Retained local expressive/shared UI components, geometry, typography, theme and lifecycle adaptations. |
| `Common/src/mobile/java/tk/glucodata/ui/GlucoseMeterSettingsScreen.kt` | Retained | Retained local expressive/shared UI components, geometry, typography, theme and lifecycle adaptations. |
| `Common/src/mobile/java/tk/glucodata/ui/GlucosePaletteSettings.kt` | Retained | Retained local expressive/shared UI components, geometry, typography, theme and lifecycle adaptations. |
| `Common/src/mobile/java/tk/glucodata/ui/GlucoseRangeDisplaySettings.kt` | Retained | Retained local expressive/shared UI components, geometry, typography, theme and lifecycle adaptations. |
| `Common/src/mobile/java/tk/glucodata/ui/HistoryBrowseScreen.kt` | Retained | Retained local expressive/shared UI components, geometry, typography, theme and lifecycle adaptations. |
| `Common/src/mobile/java/tk/glucodata/ui/HistoryDataTransfer.kt` | Retained | Retained local expressive/shared UI components, geometry, typography, theme and lifecycle adaptations. |
| `Common/src/mobile/java/tk/glucodata/ui/InsulinPenScanSheet.kt` | Retained | Retained local expressive/shared UI components, geometry, typography, theme and lifecycle adaptations. |
| `Common/src/mobile/java/tk/glucodata/ui/JugglucoTheme.kt` | Retained | Local Material Expressive theme, actual app-dark state and FontScaleCap. |
| `Common/src/mobile/java/tk/glucodata/ui/MainNavigation.kt` | Retained | Retained local expressive/shared UI components, geometry, typography, theme and lifecycle adaptations. |
| `Common/src/mobile/java/tk/glucodata/ui/MirrorSettingsScreen.kt` | Retained | Retained local expressive/shared UI components, geometry, typography, theme and lifecycle adaptations. |
| `Common/src/mobile/java/tk/glucodata/ui/MobileComposeHost.kt` | Retained | Retained local expressive/shared UI components, geometry, typography, theme and lifecycle adaptations. |
| `Common/src/mobile/java/tk/glucodata/ui/PredictionModelProfileScreen.kt` | Retained | Retained local expressive/shared UI components, geometry, typography, theme and lifecycle adaptations. |
| `Common/src/mobile/java/tk/glucodata/ui/ReadingRow.kt` | Retained | Local draw-phase row aging and divider fading with existing tested timing semantics. |
| `Common/src/mobile/java/tk/glucodata/ui/SensorCard.kt` | Retained | Retained local expressive/shared UI components, geometry, typography, theme and lifecycle adaptations. |
| `Common/src/mobile/java/tk/glucodata/ui/SensorScreen.kt` | Retained | Retained local expressive/shared UI components, geometry, typography, theme and lifecycle adaptations. |
| `Common/src/mobile/java/tk/glucodata/ui/alerts/AlertSettingsScreen.kt` | Retained | Retained local expressive/shared UI components, geometry, typography, theme and lifecycle adaptations. |
| `Common/src/mobile/java/tk/glucodata/ui/alerts/GlobalAlertSettingsCard.kt` | Retained | Retained local expressive/shared UI components, geometry, typography, theme and lifecycle adaptations. |
| `Common/src/mobile/java/tk/glucodata/ui/alerts/QuietWindowCard.kt` | Retained | Retained local expressive/shared UI components, geometry, typography, theme and lifecycle adaptations. |
| `Common/src/mobile/java/tk/glucodata/ui/calibration/CalibrationBottomSheet.kt` | Retained | Retained local expressive/shared UI components, geometry, typography, theme and lifecycle adaptations. |
| `Common/src/mobile/java/tk/glucodata/ui/calibration/CalibrationListScreen.kt` | Retained | Retained local expressive/shared UI components, geometry, typography, theme and lifecycle adaptations. |
| `Common/src/mobile/java/tk/glucodata/ui/calibration/CalibrationModelTableScreen.kt` | Retained | Retained local expressive/shared UI components, geometry, typography, theme and lifecycle adaptations. |
| `Common/src/mobile/java/tk/glucodata/ui/components/CompactSheetDragHandle.kt` | Retained | Retained local expressive/shared UI components, geometry, typography, theme and lifecycle adaptations. |
| `Common/src/mobile/java/tk/glucodata/ui/journal/JournalCalculationsSettingsScreen.kt` | Retained | Retained local expressive/shared UI components, geometry, typography, theme and lifecycle adaptations. |
| `Common/src/mobile/java/tk/glucodata/ui/journal/JournalCobState.kt` | Ported + retained | Fixed initialValue argument while retaining lifecycle collection and existing COB calculation. |
| `Common/src/mobile/java/tk/glucodata/ui/journal/JournalCompose.kt` | Retained | Local expressive layout and compact journal chip presentation. |
| `Common/src/mobile/java/tk/glucodata/ui/journal/JournalScreen.kt` | Retained | Retained local expressive/shared UI components, geometry, typography, theme and lifecycle adaptations. |
| `Common/src/mobile/java/tk/glucodata/ui/journal/JournalSettingsScreen.kt` | Retained | Retained local expressive/shared UI components, geometry, typography, theme and lifecycle adaptations. |
| `Common/src/mobile/java/tk/glucodata/ui/setup/DexcomSetupWizard.kt` | Retained | Retained local expressive/shared UI components, geometry, typography, theme and lifecycle adaptations. |
| `Common/src/mobile/java/tk/glucodata/ui/setup/LibreSetupWizard.kt` | Retained | Retained local expressive/shared UI components, geometry, typography, theme and lifecycle adaptations. |
| `Common/src/mobile/java/tk/glucodata/ui/setup/SensorSetupStatusScreen.kt` | Ported + retained | Removed migration-introduced duplicate import; retained local expressive setup presentation. |
| `Common/src/mobile/java/tk/glucodata/ui/setup/SibionicsSetupWizard.kt` | Retained | Retained local expressive/shared UI components, geometry, typography, theme and lifecycle adaptations. |
| `Common/src/mobile/java/tk/glucodata/ui/stats/StatsLayoutEditor.kt` | Retained | Retained local expressive/shared UI components, geometry, typography, theme and lifecycle adaptations. |
| `Common/src/mobile/java/tk/glucodata/ui/stats/StatsMetrics.kt` | Retained | Local expressive/localized metrics and consistent pinned-pill text measurement. |
| `Common/src/mobile/java/tk/glucodata/ui/stats/StatsRangeControls.kt` | Retained | Retained local expressive/shared UI components, geometry, typography, theme and lifecycle adaptations. |
| `Common/src/mobile/java/tk/glucodata/ui/stats/StatsReportExporter.kt` | Retained | Local shared report-style swatch API and range typography. |
| `Common/src/mobile/java/tk/glucodata/ui/stats/StatsScreen.kt` | Retained | Local shared tab header, expressive shapes, app-theme colors and report-preview palette. |
| `Common/src/mobile/java/tk/glucodata/ui/stats/StatsSections.kt` | Retained | Retained local expressive/shared UI components, geometry, typography, theme and lifecycle adaptations. |
| `Common/src/mobile/java/tk/glucodata/ui/stats/StatsViewModel.kt` | Retained | Local content-sensitive history signature detects edits beyond edge samples. |
| `Common/src/mobile/java/tk/glucodata/ui/stats/StatsVisualizations.kt` | Retained | Retained local expressive/shared UI components, geometry, typography, theme and lifecycle adaptations. |
| `Common/src/mobile/java/tk/glucodata/ui/theme/Type.kt` | Retained | Local multi-weight variable font families and expressive text styles. |
| `Common/src/mobile/java/tk/glucodata/ui/util/LegacySensorStatus.kt` | Retained | Local Libre2 warm-up countdown alongside Libre3 status. |
| `Common/src/mobile/java/tk/glucodata/ui/viewmodel/DashboardViewModel.kt` | Retained | Local sensor status clock and native post-extension end timestamp. |
| `Common/src/mobile/java/tk/glucodata/widget/ExpressiveAppWidget.kt` | Retained | Local widget appearance/configuration, chart window, weather and layout. |
| `Common/src/mobile/java/tk/glucodata/widget/ExpressiveWidgetReceiver.kt` | Retained | Local explicit launcher updates with coalescing. |
| `Common/src/mobile/res/layout/glucose_widget.xml` | Retained | Retained resources and registrations for local widgets, status, complications and force-dark behavior. |
| `Common/src/mobile/res/values/styles.xml` | Retained | Retained resources and registrations for local widgets, status, complications and force-dark behavior. |
| `Common/src/mobile/res/xml/expressive_widget_info.xml` | Retained | Retained resources and registrations for local widgets, status, complications and force-dark behavior. |
| `Common/src/mobile/res/xml/glucose_widget_info.xml` | Retained | Retained resources and registrations for local widgets, status, complications and force-dark behavior. |
| `Common/src/testWear/java/tk/glucodata/glucosecomplication/MultiSensorComplicationTests.kt` | Retained | Local coverage for trend glyphs and required text on watch faces. |
| `Common/src/wear/AndroidManifest.xml` | Retained | Retained resources and registrations for local widgets, status, complications and force-dark behavior. |
| `Common/src/wear/java/tk/glucodata/glucosecomplication/ArrowDataSourceService.kt` | Retained | Local shared complication builder, required text support and current renderer. |
| `Common/src/wear/java/tk/glucodata/glucosecomplication/ArrowValueDataSourceService.kt` | Retained | Local shared complication builder with required trend text and renderer. |
| `Common/src/wear/java/tk/glucodata/glucosecomplication/ComplicationRenderer.kt` | Retained | Local finite-rate/dimension guard prevents invalid arrow coordinates. |
| `Common/src/wear/java/tk/glucodata/glucosecomplication/GlucoseComplicationData.kt` | Retained | Local sensor identity fallback and trend glyphs in required text. |
| `Common/src/wear/java/tk/glucodata/glucosecomplication/MultiSensorDataSourceService.kt` | Retained | Local shared trend glyph mapping. |
| `Common/src/wear/java/tk/glucodata/glucosecomplication/ShortArrowValueDataSourceService.kt` | Retained | Local compact trend/time complication builder and current renderer. |

## Local-only files

All 57 local-only files are retained. They add UI helpers, widgets/resources and tests; none represents a missing upstream file.

- `Common/src/mobile/java/tk/glucodata/WidgetStatusText.kt`
- `Common/src/mobile/java/tk/glucodata/ui/ForceDark.kt`
- `Common/src/mobile/java/tk/glucodata/ui/JournalChipLayout.kt`
- `Common/src/mobile/java/tk/glucodata/ui/StatusPalette.kt`
- `Common/src/mobile/java/tk/glucodata/ui/components/AppTopBar.kt`
- `Common/src/mobile/java/tk/glucodata/ui/components/ConnectedButtonShapes.kt`
- `Common/src/mobile/java/tk/glucodata/ui/components/IconTile.kt`
- `Common/src/mobile/java/tk/glucodata/ui/components/TabScreenHeader.kt`
- `Common/src/mobile/java/tk/glucodata/ui/stats/StatsHistorySignature.kt`
- `Common/src/mobile/java/tk/glucodata/widgets/GlucoseWidgets.kt`
- `Common/src/mobile/java/tk/glucodata/widgets/WidgetConfig.kt`
- `Common/src/mobile/java/tk/glucodata/widgets/WidgetData.kt`
- `Common/src/mobile/java/tk/glucodata/widgets/WidgetRenderer.kt`
- `Common/src/mobile/java/tk/glucodata/widgets/WidgetSettingsScreen.kt`
- `Common/src/mobile/java/tk/glucodata/widgets/WidgetUpdater.kt`
- `Common/src/mobile/java/tk/glucodata/widgets/WidgetWeatherSource.kt`
- `Common/src/mobile/res/drawable/widget_preview_basic_static.png`
- `Common/src/mobile/res/drawable/widget_preview_bg.xml`
- `Common/src/mobile/res/drawable/widget_preview_chart.xml`
- `Common/src/mobile/res/drawable/widget_preview_clock_weather_static.png`
- `Common/src/mobile/res/drawable/widget_preview_expressive_static.png`
- `Common/src/mobile/res/drawable/widget_preview_range_static.png`
- `Common/src/mobile/res/layout/expressive_clock_ibm_300.xml`
- `Common/src/mobile/res/layout/expressive_clock_ibm_500.xml`
- `Common/src/mobile/res/layout/expressive_clock_ibm_600.xml`
- `Common/src/mobile/res/layout/expressive_clock_ibm_700.xml`
- `Common/src/mobile/res/layout/expressive_clock_system_300.xml`
- `Common/src/mobile/res/layout/expressive_clock_system_500.xml`
- `Common/src/mobile/res/layout/expressive_clock_system_600.xml`
- `Common/src/mobile/res/layout/expressive_clock_system_700.xml`
- `Common/src/mobile/res/layout/expressive_widget_system_clock.xml`
- `Common/src/mobile/res/layout/expressive_widget_system_clock_ibm.xml`
- `Common/src/mobile/res/layout/home_widget.xml`
- `Common/src/mobile/res/layout/home_widget_clock_weather.xml`
- `Common/src/mobile/res/layout/home_widget_clock_weather_ibm.xml`
- `Common/src/mobile/res/layout/home_widget_clock_weather_ibm_main_system_detail.xml`
- `Common/src/mobile/res/layout/home_widget_clock_weather_system_main_ibm_detail.xml`
- `Common/src/mobile/res/layout/widget_preview_basic.xml`
- `Common/src/mobile/res/layout/widget_preview_clock_weather.xml`
- `Common/src/mobile/res/layout/widget_preview_expressive.xml`
- `Common/src/mobile/res/layout/widget_preview_range.xml`
- `Common/src/mobile/res/values/widget_text_styles.xml`
- `Common/src/mobile/res/xml/home_widget_clock_weather_info.xml`
- `Common/src/mobile/res/xml/home_widget_range_info.xml`
- `Common/src/test/java/tk/glucodata/WearOwnershipWiringTests.kt`
- `Common/src/testMobile/java/tk/glucodata/ClockWeatherWidgetWiringTests.kt`
- `Common/src/testMobile/java/tk/glucodata/LegacyWidgetDimmingWiringTests.kt`
- `Common/src/testMobile/java/tk/glucodata/LegacyWidgetSizingWiringTests.kt`
- `Common/src/testMobile/java/tk/glucodata/Libre2HybridWiringTests.kt`
- `Common/src/testMobile/java/tk/glucodata/Libre2NfcLiveRecoveryWiringTests.kt`
- `Common/src/testMobile/java/tk/glucodata/RedesignedWidgetsWiringTests.kt`
- `Common/src/testMobile/java/tk/glucodata/data/HistorySyncWidgetRefreshWiringTests.kt`
- `Common/src/testMobile/java/tk/glucodata/ui/JournalChipLayoutTests.kt`
- `Common/src/testMobile/java/tk/glucodata/ui/ReadingRowFadeTests.kt`
- `Common/src/testMobile/java/tk/glucodata/ui/stats/StatsHistorySignatureTests.kt`
- `Common/src/testMobile/java/tk/glucodata/ui/util/LegacySensorStatusTests.kt`
- `Common/src/testWear/java/tk/glucodata/glucosecomplication/ComplicationProviderWiringTests.kt`
