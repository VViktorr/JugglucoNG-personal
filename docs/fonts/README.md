# Application and widget fonts

The font catalogue in `Common/src/main/java/tk/glucodata/FontCatalog.java` contains 14 choices: IBM Plex Sans, System, HarmonyOS Sans, Comfortaa, Ubuntu, Roboto Condensed, Oswald, Rubik, Poppins, Plus Jakarta Sans, Noto Sans, Inter, DM Sans and Roboto Flex.

## Sources and distribution

`bundled-fonts.json` records the exact upstream URLs, original filenames/weights and SHA-256 checksums of the 12 newly bundled families. All binaries are unmodified. The Google Fonts `METADATA.pb` files retained here identify the designers, copyright, upstream source repositories and variable axes. Downloads were made on 2026-11-05. The full upstream licence texts and copyright notices are packaged into **both phone and Wear APKs** under `Common/src/main/assets/font_licenses/`.

| Families | Distribution source | Licence |
| --- | --- | --- |
| Comfortaa, Roboto Condensed, Oswald, Rubik, Poppins, Plus Jakarta Sans, Noto Sans, Inter, DM Sans, Roboto Flex | https://github.com/google/fonts/tree/main/ofl | SIL Open Font License 1.1; each family's own copyright and full OFL retained |
| Ubuntu | https://github.com/google/fonts/tree/main/ufl/ubuntu | Ubuntu Font Licence 1.0; full UFL retained |
| HarmonyOS Sans | https://github.com/openharmony/resources/tree/master/fonts | Huawei HarmonyOS Sans Fonts License Agreement, from the same repository's LICENSE_Fonts |
| Existing IBM Plex Sans Variable | Existing app resource; https://github.com/IBM/plex/blob/master/LICENSE.txt | SIL OFL 1.1; original resource copyright (2019 IBM) additionally retained in NOTICE.txt |

Huawei's agreement explicitly permits embedding/bundling/redistributing **unmodified** copies with software other than font software. No font conversion, subsetting or modification has been performed. A prominent usage notice and the full agreement are accessible in the app's About area and watch settings. The font files are distributed as part of this application, not as a standalone font product. HarmonyOS Sans is **not OFL**.

Samsung One, MiSans and Apple SF Pro are not bundled.

## Two independent settings

- **Appearance and display → App font** sets `app_font_family` in the existing app preference file. Stable enum names, rather than list indices, are persisted. `app_use_system_font` remains readable and is also written for compatibility. If no new selection exists, all surfaces retain their previous defaults.
- **Widgets → Widget font** is one shared selector above the widget types. Every family name is rendered in that family as an inline preview. It uses the existing `global_widget_font_family` key; the old per-kind JSON remains readable. It immediately pushes an update to all widget providers when selected, without waiting for a reading or minute tick. Active chart-widget sessions apply the new display configuration before their background history/weather reload finishes. Font weights, sizes, visibility and placement remain configured by the existing controls.

The app preference participates in SettingsRegistry/WearPrefsSync using the existing phone-to-watch protocol. Compose observes SharedPreferences updates, including incoming watch synchronization. A watch connected to an older phone retains its previous font until a supported font setting arrives. The widget font is phone-local and does not follow the app font.

## Implemented surfaces

- All Material/Expressive mobile typography styles, including emphasized styles, preserve their existing sizes, line heights and weights. IBM retains its original per-style variable widths. Other families use their own normal widths; unsupported axes are not forced onto them.
- Legacy Java TextViews in activities and shared Layout dialogs use the selected app family, preserving text size and weight/style. Existing installations do not receive a new legacy font until a selection is made.
- Dashboard chart labels, default-font report text, alarm screen text and floating/lock-screen overlay text follow the app choice. Explicit serif/Outfit report styles remain available.
- Notification/AOD bitmap text and numeric status icons use the app family. Trend arrow rendering is unchanged.
- All widget Canvas text uses the independent widget family, including weather, temperature, metadata and statistics. Legacy BASIC widget drawing and CHART widget axes use the same preference.
- The shared clock/weather layout preserves its existing weight, size and placement controls. Android deliberately ignores custom font resources when the launcher inflates RemoteViews in a restricted context. For an explicitly selected bundled widget family, clock, date, glucose and detail text are drawn directly onto the header bitmap with the selected typeface within the existing pixel budget. Text is fitted using full font metrics and glyph bounds, including descenders and accents. This avoids detached TextView layout omissions and clipping. Both CLOCK_WEATHER and CHART use this path. The system-font option retains the native launcher TextClock.
- Custom-font clock/date bitmaps refresh on system minute, time/date/time-zone/locale changes and screen-on/unlock broadcasts while the app process is alive, independently of sensor readings. No exact alarm or new foreground service is used. An active Glance session also observes a separate clock revision so cached headers redraw even when readings and settings are unchanged. After a force stop, bitmap clocks resume when the app runs again; the native system-font clock remains autonomous. Local rendering failures are logged with the WidgetFonts tag and keep the native layout as a safe fallback.
- Wear Material typography observes the synchronized family; app-rendered complication text also uses it. System-rendered complications remain controlled by the watch face.
- Full font licences are accessible through **About → Font licences** and watch settings.

## Platform limits and fallbacks

- Android owns the standard LiveUpdate/MetricStyle templates and `setShortCriticalText` capsule text. These APIs accept strings, not custom typefaces; those strings keep the device font. The capsule's numeric bitmap icon follows the app family where supported. Custom notification bitmap text and the app's own overlay are covered. Notification RemoteViews fields that cannot carry a custom typeface continue with their safe existing font; no unsupported RemoteViews setter is called.
- Legacy native NanoVG/OpenGL chart text uses fonts embedded in the native renderer. This change does not rewrite that renderer or its native assets.
- Static fonts use the nearest shipped weight when the family does not supply an exact one (for example Ubuntu/HarmonyOS at 600). Android may synthesize style differences. Other glyphs/scripts fall back through Android's normal font fallback chain.
- Every bundled new family contains all checked Polish letters, including Ł/ł, and digits; see `glyph-coverage.txt`. Urbanist was excluded at the user's request because its upstream font lacks Ł/ł. Its binaries, resources, metadata and licence are not bundled. There are 14 available choices. No glyph was inserted or font binary altered.
- A font-loading failure uses the system typeface; an unknown saved family falls back to the prior default. No network font provider is needed at runtime.
- Active bitmap notifications/AOD/complications pick up the new family on their next normal redraw; widgets are explicitly refreshed when their selector changes. Restart/reopen an already open legacy standalone dialog if its contents do not relayout.

## RemoteViews restriction reference

Android CTS explicitly checks that RemoteViews ignore custom font files: https://android.googlesource.com/platform/cts/+/a21f4a13d2fa84132139721d565fe102278e6ae5/tests/tests/text/src/android/text/cts/FontResourceTest.java . Merely setting fontFamily in the widget XML does not bypass this restriction.

## Validation status

Source review, resource/manifest inspection and original font name/cmap/axis inspection only. **No build, test suite or emulator/device run was performed**, as requested. Device verification of font sizes, OEM RemoteViews inflation and Wear rendering remains with the user.

A complete changed-file inventory is in `changed-files.txt`.

Native clock layouts select the nearest supported weight for older intermediate weight preferences, so no value is hidden by an unmatched weight. Bitmap headers retain freshness/status rules and do not change the glucose source.

### Font inspection, 2026-11-05

All 23 bundled catalogue font files (13 bundled families plus the device-system choice) contain `ĄąĆćĘęŁłŃńÓóŚśŹźŻż`, digits and the widget punctuation `.,−:+%°`. All 22 files in the source manifest match their recorded SHA-256 checksums; IBM Plex Sans is the pre-existing application resource. Every bundled variable family's weight axis covers widget weights 300–700. Static family XML files select their supplied weights using Android's normal nearest-weight matching. Urbanist remains excluded. Device-system glyph coverage depends on the device and cannot be inspected from APK resources.

The basic widget now also fits the glucose value using full vertical font extents and keeps time descenders inside its bitmap. Clock/chart header text and range/weather text use full extents rather than only ascent/descent. This is source and font-file inspection; Android rendering, launcher behavior and device font fallback still require device verification. No app build or test suite was run for this inspection.

Clock/weather and chart headers use two aligned rows: clock, weather, glucose and trend in the upper half; date and reading details in the lower half. Weather and arrow use the same upper-row center as text. Visible glyph bounds determine optical centering across families. Weather icon/temperature are measured as one non-overlapping group; horizontal weather shifting and arrow scaling are constrained to their allocated slots. Arrow padding is reserved for both native and bitmap headers, independently of bitmap pixel density.

The shared two-row header is used by both CLOCK_WEATHER and CHART. Missing/stale readings show an em dash in the value slot and the source status (or localized no-value fallback) instead of delta/unit/reading time in the second row. Freshness rules continue to suppress the trend arrow.

### Widget settings source audit, 2026-11-05

The smooth fitting change in `3601a079` was reverted after device feedback showed it reduced all elements without resolving the controls. Hard limits remain only to prevent clipping at the actual slot boundary. Compact BASIC keeps the user's scale outside its base-size minimum, so reducing glucose/time size is no longer cancelled by that minimum.

Widget settings now keep an independent continuous drag position. Previously every movement fed an integer preference snapped to 5% back into a continuous Material slider, which could reset the thumb offset during the gesture. The new WidgetSlider holds the raw position until release, saves the existing rounded preferences during movement, then commits the last position and requests a final refresh. All eight controls use this wrapper (main/detail size, radius, opacity, weather size, icon size, temperature size and weather shift). Device confirmation is still required; the prior source audit did not establish actual launcher behavior.

Every slider still saves and refreshes on movement, with an additional final refresh when released. Existing preference ranges and visibility choices remain unchanged.

| Settings reviewed | BASIC | CLOCK_WEATHER / CHART | RANGE |
| --- | --- | --- | --- |
| Family, weight | Shared family + configured weight | Shared family + configured weight | Shared family + configured weight |
| Main / detail sizes | Value + time | Clock/value/arrow + date/details/status | Value/TIR/arrow + details/stat labels |
| Background, opacity, corner radius | Own bitmap background | Clock bitmap / Glance chart container | Own bitmap background |
| Visibility | Arrow, reading time | Arrow, date, delta, unit, reading time, weather/icon/temperature | Arrow, delta, unit, reading time |
| Weather scale / component scales / shift | Not exposed | Applied to shared clock/chart header | Not exposed |
| Graph period / target band | Not exposed | CHART history load + chart rendering | Not exposed |
| Statistics period | Not exposed | Not exposed | Snapshot statistics and history window |

Settings are persisted in WidgetConfigStore (BASIC reads the same JSON in RemoteGlucose). BASIC explicitly clears its render cache/throttle; CLOCK_WEATHER/RANGE serialize updates with a final rerender when requests coalesce; CHART observes the settings revision and reloads history as needed. Missing/stale readings retain source status and suppress fresh-only details/arrows. This is a source audit, not a claim of device validation. Launcher delivery, touch behavior and rendering still require phone verification; no build or test suite was run.

### Native header restored after comparison with 1.2.5-ok

ClockWeatherWidgetViews no longer rasterizes the clock, date, glucose and details. All family selections use their existing XML TextClock/TextView layouts, following the native IBM/system approach in 1.2.5-ok. The two-row placement, source status, selected weights, direct SP size controls and font choices remain. Weather and trend keep their existing bitmap layer. This also applies to the chart header. Native TextClock handles time updates; minute bitmap refresh is disabled. Launcher acceptance of bundled XML font resources must be confirmed on the device; the selection does not guarantee a custom font on every launcher. No build or device test was performed for this restoration.

### Unified header and fixed scaling reference

The native-header experiment is superseded: every clock/chart header field now uses the selected typeface in a single bitmap. Text is fitted at a fixed reference maximum (main/details 180%, weather icon combined 440%, temperature combined 400%), then multiplied by selected/reference. A slider change no longer changes the fitted reference and therefore cannot be cancelled by refitting to the same bounds. Range text uses the same 180% reference. At fixed widget dimensions, text and font, 150% is 1.875 times 80%; maximum settings occupy the allocated slot, while lower settings leave headroom. This necessarily means that 100% is not the largest text that can occupy the slot.

Clock arrow padding reserves the maximum arrow width regardless of selected size; the arrow scales linearly. Weather component multipliers retain their entire configured range without a combined 300% cap. The weather shift maps to the available horizontal space between clock and glucose. Existing visibility/freshness/status handling and immediate/final settings refresh remain. Every header is refreshed on minute/time/date/timezone/unlock events while the application process is alive. This uses the existing dynamic receiver, not an exact alarm or a new background service. Launcher rendering and process-lifetime clock behavior still need device verification. Native XML family layouts remain as resources but are no longer the active header path.

### Restore readable 100% and unify the detail row

The fixed-maximum reference introduced in 642f6141 made 100% too small, especially for weather with combined reference scales 400/440. This normalization is removed. Main text and weather use the original 100% baseline; requested growth is constrained only by actual available bounds. On small hosts this can reach a physical size limit before a slider's maximum. No claim is made that unlimited growth can fit into a fixed two-row widget.

Date and reading details/status now share one Paint size and baseline. Their combined measured width, one gap and full font extents determine a single fit factor for the whole lower row. Date no longer has an independently shrinking half-width slot. The lower-row 100% baseline is increased to min(width*0.050, height*0.23), while preserving detailScale and system font scale. Hidden date/details release their space; stale status uses the same row sizing and no fresh-only arrow/details are displayed. Both clock and chart use this shared renderer.

The reattached 20:38 trace and logcat are byte-for-byte copies of the earlier logs and show bitmap=false; they cannot validate the later bitmap build. Screenshots demonstrate the small 100% appearance and mismatched date/detail sizes. Changes are source-reviewed; no new build/device test was run.

### Follow-up from 2026-11-06 device logs

The new trace shows bitmap=true and changed main 70–120%, weather 70–110%, temperature 70–195% reaching the header. Header digits were still capped using the full font's top/bottom, including unrelated glyph/script extents. Header main text and weather now use the actual string glyph bounds for vertical fitting. Width fits with Paint.textScaleX rather than cancelling height changes via textSize; wide strings may become horizontally condensed at large settings. The actual row height remains the final vertical limit. This does not change glucose values, rate, freshness or trend angles.

Weather shift is again a dp offset from 44% of widget width, constrained to the original 27–54% center range, rather than a percentage mapping of free slot space. Large shifts can again place weather near the clock, as in the previous control. Weather icon and temperature are still measured together with a gap. Shared date/detail sizing and baseline remain unchanged. Source review and diff checks only; Android rendering still requires device confirmation.

### Copy individual settings between widget types

Each shared control offers “Apply to other widgets”. The store copies only that JSON field into each compatible target's existing configuration. Font weight copies the paired primary/secondary weights because the UI exposes one shared weight. Main/detail sizes, radius, background, opacity, arrow and time apply to all four types; range colors/delta/unit exclude BASIC; date/weather and all weather component settings apply between CLOCK_WEATHER and CHART. Controls with no compatible other type do not offer a copy button. Family selection remains global. Existing target configurations are preserved except for the selected field, then all providers refresh immediately. A localized toast acknowledges the action.

Weather shift supports 0–80dp (previously 0–40dp) and a center as far left as 18% of width instead of 27%, while keeping the weather group within the widget's left edge. Large weather/clock sizes can still compete for available horizontal space. Old saved shifts remain unchanged. No build or tests run for this change.
