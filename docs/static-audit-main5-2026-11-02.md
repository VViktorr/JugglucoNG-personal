# Static audit of main5 — 2026-11-02

Scope: source inspection and consistency checks only. No compilation, Gradle tasks,
test suites or device tests were run. Baseline: ee1fc73833db5895bc34a581381cb5ed8b92ee56.

## Corrected
- Eight lines in Common/src/mobile/res/values-pl/html.xml contained Windows-1250
  bytes inside an otherwise UTF-8 resource. Re-encoded only those lines to UTF-8.
  The corrected text matches main; the remaining difference from main is whitespace.
- Removed three tracked editor/backup files from active source directories:
  Common/src/mobile/java/tk/glucodata/Menus.java.bak,
  Common/src/wear/java/tk/glucodata/watchface/data/watchface/WatchFaceColorPalette.kt.bak,
  Common/src/mobile/res/values-pl/.!66135!html.xml.
  No build/runtime dependency was found. Historical cleanup scripts and architecture
  documentation mention the backup names; these are not build inputs.
  The editor copy also contained an unfinished CDATA section.

## Inspected
- No duplicate literal composable routes in MainNavigation.kt.
- All literal settings destinations found in the phone Kotlin sources resolve to
  a registered route. Dynamic destinations are outside this literal-only check.
- No repeated resource declarations within the same source set and qualifier
  among main/mobile/wear values XML files.
- Resource values XML across source sets parses after the encoding correction,
  excluding the removed editor copy.
- No duplicate Java package + filename pairs within the configured phone or Wear
  source combinations (main/dex/libre3 plus the respective variant sources).
  This is not a Kotlin symbol/type check or a compiler substitute.
- The previous settings reorganization gives each top-level option one category.
  The search index uses those categories. Compatibility route aliases and legacy
  phone/Wear settings are not redundant copies to delete indiscriminately.
- Main-style button shapes are shared through AppButtonStyle.kt. Group-specific
  card corners and newer adaptive layouts are intentional.
- Native/JNI inputs, sensor logic and stored user preferences were not removed.

## Still requires device/build confirmation
- The native startup fix and all subsequent commits need the user's build and
  runtime confirmation. Static inspection does not establish crash-free behavior.
- Wear bitmap icons: the compatibility change is present, but actual display and
  refresh on the user's watch are unconfirmed.
- Notification permission requests are still conditional on the service/Bluetooth
  startup path. Battery exemption also follows that existing path. This audit
  does not change the permission/startup sequence.
- Existing warning messages (deprecated APIs, native static-self-init patterns)
  are not proof of a runtime failure; they were not rewritten speculatively.
