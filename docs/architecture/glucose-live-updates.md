# Optional glucose Live Updates (Android 16 and 17)

Enable **Glucose as a Live Update** / **Glikemia jako Live Update** in the phone's
notification settings screen. It is available in release builds on API 36+ and
defaults to off. It replaces the ordinary ongoing custom chart notification,
using the same notification ID/channel and foreground publication lifecycle.
It does not post another notification or change glucose alarm delivery.

- Android 16: standard ongoing system notification, with the short numeric glucose
  supplied through `setShortCriticalText`.
- Android 17: one typed `MetricStyle` metric, with critical index 0.
  mg/dL is an integer; mmol/L uses one fractional digit. Both include a unit in
  the notification. Short critical text remains set for OEM compatibility.
- The primary value and timestamp come from the renderer's resolved display
  snapshot, including its configured smoothing/calibration/view mode.
- Non-finite, non-positive, missing, future and expired readings are not promoted.
  Existing Notify freshness deadlines replace an expired reading with its
  ordinary stale/status notification; startup status notifications are not promoted.
- API 37 template references are isolated in a nested API-gated class. If that
  template is unavailable, the standard API 36 readout remains. A failure to build
  the Live Update falls back to the existing custom notification.
- There are no custom RemoteViews, group summary or colorized notification in this
  mode. The promotion request uses the documented extra rather than the setter
  added in SDK 36.1, so base SDK 36 OEMs can also consume it.
- No gate on `canPostPromotedNotifications()`: OEM behavior varies; a disabled or
  unsupported promotion can leave a standard system notification. No code attempts
  to grant itself permission, change app-ops or force the promoted flag.
- The Unpin action and delete intent disable only this optional mode and request
  a stored-data visual refresh. Sensor monitoring and alarm state keep running.
  The user must explicitly enable the mode again.
- Wear and alarm channels retain their existing rendering. The promotion permission
  and unpin receiver are declared only in the mobile manifest.

## Device checks before calling this verified

1. On API 35 or lower, the toggle is absent and notifications behave as before.
2. On Android 16, opt in with a current reading: verify a system notification and,
   where supported, the numeric status-bar chip. On Oplus firmware, allow Live
   Updates/Live Alerts in the app's system notification settings if offered.
3. On Android 17, verify MetricStyle, integer mg/dL and one-decimal mmol/L, and
   confirm the value matches the main screen for each display mode.
4. Disable system promotion: verify that an ordinary notification still appears.
5. Stop incoming readings: after the existing freshness threshold, verify that
   the numeric promoted reading is replaced by the stale/status notification.
6. Tap Unpin (or dismiss where allowed): verify the chart layout returns and the
   next reading does not re-enable Live Updates. Re-enable the toggle explicitly.
7. Switch the option off/on while monitoring; verify only one ongoing glucose
   notification exists, and alarms still use their existing notification.
8. Repeat on OnePlus/OxygenOS and OPPO/ColorOS. SDK support alone does not prove
   that a specific SystemUI will render a chip.

The local environment used for this change has no usable Android SDK/JDK, so no
APK build or device rendering test was run. CI is not awaited.

## References

- https://developer.android.com/develop/ui/views/notifications/live-update
- https://developer.android.com/develop/ui/views/notifications/metric-style
- https://developer.android.com/reference/android/app/Notification.Builder
- https://developer.android.com/reference/android/app/Notification.MetricStyle
