# OxygenOS original-small-icon package experiment

This is an optional separate-package phone build, based on static analysis of
the user's OnePlus 15 firmware CPH2747_16.0.10.601(EX01). Rendering has not yet
been tested on the device. No root or system settings changes are requested.

## Build in PowerShell

```powershell
.\gradlew.bat :Common:assembleMobileRelease -PoplusSmallIconTest=true -PjugglucoAbi=arm64-v8a
```

Output:
`Common/build/outputs/apk/mobile/release/JugglucoNG-1.2.5-Alpha-oplus-icon-test.apk`.

The base application ID becomes `com.oplus.juggluco.ngtest`. Existing debug/dub
suffixes still apply if those build types are selected. The base ID is chosen
before native APPID flags and manifest placeholders are generated, so Java,
native paths and provider authorities use the same identity. Wear variants are
disabled when the property is true. Ordinary builds omit the property and keep
`tk.glucodata.ng` and their existing APK filenames.

This is an independent Juggluco test build, not an OPPO/OnePlus system app.
It installs separately and does not inherit another installation's data,
permissions or sensor configuration. The prefix does not grant system privileges.
Avoid having two collectors compete for the same sensor during the test.

## What changed

Only ordinary phone glucose builders receive the integer extra
`oplus_notification_icon_use_smallicon=1`, and only when this build property is
enabled, SDK >= 36 and the runtime package has a recognized vendor prefix.
The original dynamic smallIcon remains untouched. Alarm channels and Wear
builders are excluded.

Leave **Glucose as a Live Update** OFF for the first test: that mode deliberately
uses a standard icon and numeric chip. The prefix experiment is intended to
restore the original numeric small icon of the ordinary chart notification.
Live Updates/MetricStyle remain available independently on the same branch.

## Evidence from the supplied firmware

`OplusNotificationFixHelper.fixSmallIcon` skips substitution for:
- Android/system/platform-signed app conditions;
- a commercial notification with originating package `android` and integer
  `is_commercial=1`;
- `isOplusAppUseSmallIcon(notification, packageName)`.

The final condition requires both:
- `Bundle.getInt("oplus_notification_icon_use_smallicon", 0) == 1`;
- package starts with `com.oplus.`, `com.oppo.` or `com.coloros.`.

No signature check occurs inside this last condition. Otherwise, the helper
saves the original icon as `oplus_small_icon`, writes
`oplus_smallicon_use_app_icon=true`, and replaces smallIcon with ApplicationInfo.icon.
This explains why supplying the latter boolean as false did not work.

This is a per-notification extra, not a Settings-provider key or an ADB setting.
The condition was found in this Android 16 firmware; Android 17 and other OEM
builds may differ. SDK >= 36 does not prove the exception still exists.

## Device validation

1. Install the separately named APK and configure its own permissions/data.
2. Leave Live Update mode off. Enable the ordinary persistent glucose notification.
3. Check that the status-bar number matches the current displayed glucose.
4. Wait for another real reading; verify the icon changes with it.
5. Check lock/unlock and simultaneous glucose alarms, without suppressing alarms.
6. Optionally compare posted notification extras and smallIcon using ADB.
7. Rebuild without the property to return to the ordinary package.

Added Robolectric tests verify the integer payload, eligibility and preserved
notification fields. Full Gradle compilation/device verification was not run
locally; CI is not awaited.
