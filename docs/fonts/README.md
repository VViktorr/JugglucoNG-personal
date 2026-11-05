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
- **Widgets → Widget font** is one shared selector above the widget types. Every family name is rendered in that family as an inline preview. It uses the existing `global_widget_font_family` key; the old per-kind JSON remains readable. It changes all widget providers together. Font weights, sizes, visibility and placement remain configured by the existing controls.

The app preference participates in SettingsRegistry/WearPrefsSync using the existing phone-to-watch protocol. Compose observes SharedPreferences updates, including incoming watch synchronization. A watch connected to an older phone retains its previous font until a supported font setting arrives. The widget font is phone-local and does not follow the app font.

## Implemented surfaces

- All Material/Expressive mobile typography styles, including emphasized styles, preserve their existing sizes, line heights and weights. IBM retains its original per-style variable widths. Other families use their own normal widths; unsupported axes are not forced onto them.
- Legacy Java TextViews in activities and shared Layout dialogs use the selected app family, preserving text size and weight/style. Existing installations do not receive a new legacy font until a selection is made.
- Dashboard chart labels, default-font report text, alarm screen text and floating/lock-screen overlay text follow the app choice. Explicit serif/Outfit report styles remain available.
- Notification/AOD bitmap text and numeric status icons use the app family. Trend arrow rendering is unchanged.
- All widget Canvas text uses the independent widget family, including weather, temperature, metadata and statistics. Legacy BASIC widget drawing and CHART widget axes use the same preference.
- The shared clock/weather layout preserves its existing weight, size and placement controls. Android deliberately ignores custom font resources when the launcher inflates RemoteViews in a restricted context. For an explicitly selected bundled widget family, the layout is applied locally on the main looper, its TextViews/TextClocks receive the widget typeface, and the complete header is drawn into a bitmap within the existing pixel budget. Both CLOCK_WEATHER and CHART use this path. The system-font option retains the native launcher TextClock.
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
