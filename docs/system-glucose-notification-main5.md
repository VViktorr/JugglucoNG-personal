# New system glucose presentation (phone)

This is a new implementation on main5, not a port of GlucoseLiveUpdate or its
old receiver/policy/preferences. Both new settings default off and are on the
Floating glucose screen, independent of enabling the overlay or its permission.

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
