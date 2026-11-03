# New system glucose presentation (phone)

This is a new implementation on main5, not a port of GlucoseLiveUpdate or its
old receiver/policy/preferences. Both settings default off. Live Update has its own switch/navigation row in
Appearance and display, immediately above Floating glucose. Its detail screen
contains the Live Update master switch, the independent MetricStyle switch and
the system settings link. These options are independent of the overlay and its permission.

| Live Update | MetricStyle | Presentation with a fresh reading |
| --- | --- | --- |
| Off | Off | Existing 1.2.8 chart/custom notification |
| On (API 36+) | Off | Standard BigText system template; request promoted ongoing and use the current value as short critical text |
| Off | On (API 37+) | MetricStyle with glucose as the critical metric; no promotion request |
| On | On | MetricStyle plus promotion request and short critical text |

MetricStyle alone does not request a status-bar chip. Android/OEM and user
settings decide whether a requested Live Update is promoted. The screen includes
a link to system Live Update permission/settings on Android 16+.
The POST_PROMOTED_NOTIFICATIONS declaration is phone-only. No runtime permission
prompt is used for this declaration. An existing user-modified channel with
minimum importance can prevent promotion; the app does not overwrite it.

SystemGlucoseNotification transforms only the normal phone glucose notification,
after the existing renderer has resolved current data and applied periodic
announcement rules. It reuses the existing notification/channel/actions and
foreground lifecycle through recoverBuilder. It does not publish another ID,
create a second service, or change alarm/Wear notification rendering.
The normal renderer still runs; this deliberately prioritizes preserving 1.2.8
publication behavior over skipping chart rendering in the first implementation.

The transformation removes all custom RemoteViews, grouping and colorization,
sets an ongoing standard system template and derives the displayed number/unit
from the resolved CurrentDisplaySource snapshot. Trend text uses the existing
GlucoseNotificationText formatter. Metric values are integer mg/dL or one-decimal
mmol/L. There is one metric: the main display value. Existing peers/secondary
chart lanes are not represented in this compact system template.

MetricStyle and API36 methods live in separate version-gated classes. A failed
MetricStyle application/build falls back to BigText when Live Update is also
selected, or to the original notification otherwise. Other presentation failures
return the original notification. Stale/missing/nonfinite/future readings never
enter this presentation; existing main5 loss/status/expiry handling remains in
charge and returns to the normal notification. New settings invalidate published
content and schedule the existing status refresh. Their notification_ key prefix
is already included by NotificationFingerprint's presentation preference hash.

The system templates always need readable standard notification content. Unlike
the default custom-chart mode, selecting them exposes the glucose text in the
system notification independently of the standard forwarding-text setting.
The existing public notification visibility is retained. No overlay setting is
changed and no historical personal preference is automatically migrated.

No build, automated tests or device run was performed, per user instruction.
Review covered source wiring, SDK gates, existing freshness/publication paths,
resource names and the four settings combinations. Real OEM promotion and layout
must still be observed on the target Android 16/17 device.

References:
- https://developer.android.com/develop/ui/views/notifications/live-update
- https://developer.android.com/develop/ui/views/notifications/metric-style
- https://developer.android.com/reference/android/app/Notification.MetricStyle

## Settings relocation (2026-11-02)

Moved both controls out of Floating glucose into settings/live-update.
The category row uses SettingsNavSwitchItem like Floating glucose and lock screen
AOD; the detail screen uses the shared scaffold and master switch card.
The existing preference keys, API 36/37 gates and independent switch behaviour
are retained. Preference listeners keep category/detail values synchronised.
Settings search now finds Live Update and MetricStyle at the new route.

Statically reviewed the route, row order, preference references and removal of
the old controls. No compilation or tests were run, per user instruction.

## Readable notification content (2026-11-02)

Use dedicated default and Polish reading labels rather than the shared glucose
axis label. Standard Live Update shows the value, trend and unit once in the
title (e.g. 194 ↓ mg/dL), with Glucose/Glukoza as its body.
MetricStyle uses Glucose/Glukoza plus trend as the title and Current reading /
Bieżący odczyt as the metric label. Its native metric supplies the value and unit,
so those are no longer repeated in the title or body. Inherited subtext and
content-info are cleared. If MetricStyle is unavailable with Live Update enabled,
the clean standard presentation is restored.

The same notification ID and publication lifecycle remain in use. No extra
notification or chart was added. The status chip still receives the glucose
value and MetricStyle retains critical metric 0. System/OEM controls final layout.

Statically reviewed fresh/stale handling, API gates, resource availability in
shared source sets, standard/metric content and fallback. No compilation or tests
were run, per user instruction; visual behaviour needs verification on device.

## Status chip duplicate value (2026-11-02)

The original notification's small icon paints the glucose number. Recovering it
for Live Update while also supplying short critical text caused a bitmap number
beside a second number in the system font (matching the user's screenshots).
When Live Update is selected, explicitly replace the inherited small icon with
a white trend arrow on a transparent background using the shared renderer.
A nonfinite trend uses the app's standard icon instead of an invented direction.
The existing hide-status-icon preference still selects a transparent icon.
MetricStyle-only and ordinary chart notifications keep their prior icon policy.

No new notification ID, preference or transport changes. Static review covered
both Live Update styles, unknown trend, the existing hide preference and API
gates. No compilation or tests were run, per user instruction. Final System UI
appearance still requires confirmation on device.

## Supported capsule presentation options (2026-11-03)

Added phone settings with matching default/Polish strings and search keywords:
- show/hide trend (notification text and trend icon);
- icon selection: trend, standard or transparent;
- optional trend and unit in short critical text;
- text order: value then arrow, or arrow then value;
- show/hide reading timestamp in the notification;
- MetricStyle critical metric on/off (setCriticalMetric(0) or METRIC_INDEX_NONE).

Default presentation remains arrow icon, value-only chip text, reading time shown,
and MetricStyle critical metric enabled. The old global hide-icon preference is
respected until the dedicated icon choice is saved. Boolean/int preference changes
invalidate notification presentation and schedule its normal refresh. Choice state
uses preference listeners and retains identity across navigation. New keys share
the notification_ prefix already hashed by the notification fingerprint.

When MetricStyle critical metric is enabled, short-text formatting controls are
hidden because system metric presentation can override them. Disabling it requests
the formatted short critical text with the independently enabled Live Update.
Final selection and truncation remain OEM-controlled. The time toggle affects the
notification header, not a new countdown or a fabricated reading time.

No controls for native capsule font size, screen position, geometry or background
opacity were added: the public system template provides no such controls.
Left/right here means the ordering of value and trend inside short critical text,
not moving the entire capsule. Transparent icons can still reserve system space.
These limits are explained on the settings screen.

Static review covered key validation/defaults, formatter combinations, unknown trend,
resource references, API gates, stale fallback and preservation of the existing
single notification lifecycle. No compilation or tests were run, per user instruction.
Device rendering and the MetricStyle text fallback need user validation.

References:
- https://developer.android.com/develop/ui/views/notifications/live-update
- https://developer.android.com/reference/android/app/Notification.MetricStyle

## Minimal readable MetricStyle layout (2026-11-03)

User requested only value and trend on the lock screen, noting that the previous
description in the title was rendered larger on their phone. Replace the old
Glucose / Current reading / unit descriptions with a minimal presentation:
- default header layout puts the display-formatted number in contentTitle and
  the trend arrow in an unlabelled, unitless FixedText metric;
- the new Value in the larger header switch can be disabled to put value and
  trend together in one unlabelled FixedText metric, with the app name as title;
- unknown/disabled trend falls back to the combined value metric and app title;
- no notification timestamp is shown for MetricStyle. Original time/freshness
  data is retained, and the ordinary Live Update time preference still applies.

Header layout never selects the arrow as a critical metric; it uses
METRIC_INDEX_NONE so Live Update can request the configured value short text.
The existing system-critical-metric switch is disabled in header layout, and
short-text controls are available instead. The combined layout retains the
critical-metric option. API 37 FixedText supports the display string directly,
preserving decimal separators and the configured trend visibility.

The system app identity/header can remain visible. No absolute font size, spans,
custom RemoteViews, hidden-title characters or extra notification were introduced.
The selected template slots seek better readability; their actual font sizes and
placement require user confirmation on the target device. A failed MetricStyle
render restores standard Live Update content and its show-time preference.

Static review covered both layouts, known/unknown/disabled trend, mg/dL and mmol/L
display strings, critical metric handling, API gating and fallback.
No compilation or tests run, per user instruction.

References:
- https://developer.android.com/reference/android/app/Notification.Metric.FixedText
- https://developer.android.com/reference/android/app/Notification.MetricStyle

## Capsule simplified to exactly two layouts (2026-11-03)

This supersedes the earlier multi-control capsule settings. The capsule now has
one two-option choice:
- Arrow icon + value system text (default).
- Value icon + arrow system text.

The value icon reuses the phone StatusIcon renderer with the display-formatted
primary string, so there is no integer conversion or loss of decimal formatting.
The text contains only the trend glyph in that layout. Unknown trend uses a dash
in text, never a false flat arrow; the opposite layout keeps the value text and
uses the standard icon if trend is unavailable. Both are phone-only, API 36+.

A new capsule-layout key isolates these complete layouts from legacy show-trend,
unit, icon-hide and text-order flags. Those old capsule controls and their
unreferenced strings/search keywords are removed; previously saved obsolete flags
are not read by this presentation. Their disk entries need not be deleted.
The system notification reading timestamp is hidden without changing the real
reading time or freshness handling.

MetricStyle always uses METRIC_INDEX_NONE so a critical native metric cannot
replace the chosen short text (especially the arrow-only text in value-icon mode).
Its minimal lock-screen layout and separate larger-header option remain; the
header control is moved out of the capsule section. No change to Wear OS.

Static review checked shared-flavour StatusIcon method availability, the two
complete layouts, unknown trend, existing preference invalidation, XML references,
and the MetricStyle fallback. No compilation or tests were run, per instruction.
Actual bitmap scaling and OEM chip rendering still require device validation.

## Fix invalid MetricStyle label and inconsistent trend icon (2026-11-03)

The user screenshots still show the standard Live Update title with units and
Glucose body, and report that the arrow does not update. Source inspection found
a definite bug in our minimal MetricStyle: its Metric label was empty.
Android 17 Metric constructors require a nonblank label (IllegalArgumentException:
Metric label is required), so the exception triggered the standard fallback.

Replace the invalid layout with one FixedText metric containing the formatted
glucose value and the trend glyph as its required nonblank label (a dash for
unknown trend). Keep the app name as the required notification title; no unit,
Glucose body, Current reading description or timestamp in successful MetricStyle.
There is one visible number, plus the trend. The old larger-header experiment
and switch are removed rather than retaining a misleading or duplicate layout.
Actual System UI font sizing remains system-controlled.

Add a logged MetricStyle fallback reason so a remaining device/API incompatibility
can be diagnosed instead of silently changing style.

The screenshot also shows a flat textual arrow beside a diagonal bitmap icon.
The icon had used continuous TrendArrowAngle rotation while text/metric used the
seven ExchangeTrend states. Use StatusIcon to paint the exact ExchangeTrend glyph
for the arrow-icon layout; the opposite layout uses that same glyph as system text,
and MetricStyle uses it as its label. The reading fingerprint in Notify already
includes rate and timestamp, so changing trend is not intentionally deduplicated.
This corrects inconsistent direction thresholds; without device traces it is not
proof of the cause of any separate stalled refresh. Unknown trend is not flat.

Both capsule choices and default layout from the preceding commit remain.
No build or tests run; source checks covered nonblank labels, shared arrow
selection, new resource references, publication fingerprint and fallback logging.

Android source confirming the constructor requirement:
https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android17-release/core/java/android/app/Notification.java

## Separate Live Update and MetricStyle settings (2026-11-03)

The Live Update detail route now contains two distinct sections. Each has its
own master switch and its own two-option capsule selector (arrow icon/value text
or value icon/arrow text), without additional capsule controls.

The existing notification_system_capsule_layout_v1 key now belongs only to Live
Update. MetricStyle uses notification_system_metric_capsule_layout_v1. A one-time,
synchronised initialisation copies the former shared choice into the new key,
preserving appearance; subsequent saves touch only the selected mode's key.
Both selectors use keyed preference state and listeners. Preference changes
invalidate and refresh the same existing notification as before.

When only Live Update is on, its layout is applied. With both modes on, successful
MetricStyle uses its own layout, with Live Update providing promotion. If
MetricStyle fails and standard Live Update is restored, the Live Update layout
is also restored. MetricStyle alone still does not request promotion; its stored
capsule setting takes effect when Live Update is enabled with it. The two modes
do not create two notifications or move the system capsule.

Static review covered the four enable combinations, one-time migration,
independent preference saves, fallback layout restoration, API gates and
default/Polish descriptions. No compilation or tests run, per user instruction.
Device rendering and notification behaviour require user confirmation.
