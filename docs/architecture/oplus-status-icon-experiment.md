# OxygenOS/ColorOS status icon experiment

This is an opt-in experiment, not a confirmed fix. In notification settings on
Android 16+, enable "OxygenOS/ColorOS: glucose icon (experimental)". The preference
defaults to false and refreshes notification surfaces when changed.

Only builders for the ordinary phone glucose channel receive
`oplus_smallicon_use_app_icon=false`. Existing smallIcon, grouping, timestamps,
service lifecycle, alarm builders and Wear behavior remain unchanged.
This extra is not a Settings-provider key or an ADB switch.

Evidence: Glyph reads this extra in its [StatusBarHooks](https://github.com/Mangi-11/Glyph/blob/master/app/src/main/java/com/fankes/coloros/notify/hook/systemui/StatusBarHooks.kt).
Its [system-server hook](https://github.com/Mangi-11/Glyph/blob/master/app/src/main/java/com/fankes/coloros/notify/hook/systemserver/SystemServerHooks.kt)
preserves the icon before OplusNotificationFixHelper.fixSmallIcon processes it.
Neither source proves that the helper honors a false value supplied by an app.
A device may overwrite or ignore the request.

## Device check

1. With the option off, observe the status-bar icon and the real reading in the
   notification card. Record the exact OS build and app version.
2. Enable the option and wait for the refreshed notification. Compare the status
   bar again, then verify it follows the next actual reading.
3. Repeat after locking/unlocking and restarting the app, and while an independent
   alert is present. Do not disable alerts to obtain a preferred icon.
4. Turn the option off and confirm the previous behavior returns.

A visible number must follow real updates; a static number is not success.
Record no change as an unsuccessful experiment, not a working fix.
Robolectric tests cover the request and untouched metadata, not Oplus behavior.
