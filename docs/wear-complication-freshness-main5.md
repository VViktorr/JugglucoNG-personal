# Wear complication freshness — 2026-11-02

User observation: the watch app matches the phone, while complications keep an older
value, including after changing the complication, then skip about five readings.

Confirmed code issue: GlucoseComplicationData.currentReading accepted the live
snapshot for the full Notify.glucosetimeout (330 seconds). Synced native history
was read only when the live snapshot was missing, invalid or expired. This can
hold a complication at a cached minute while the app already has newer history.
It matches the reported delay; without runtime logs it is not proof that this was
the sole cause on the user's watch.

The provider now compares live and synchronized candidates after shared display
resolution and returns the one with the newer display timestamp. Same-time ties
retain live. Both paths keep sensor selection, calibration, lanes, smoothing and
completed-interval behavior. Missing history cannot erase a valid live reading.

IconArrowDataSourceService now uses the same source as value complications.
WearComplicationValue.updateall also requests ArrowAboveValueDataSourceService,
which was omitted from the fan-out despite having an update method.

No compilation or tests were run, as requested. Review covered API/field names,
source dependencies and update call sites. Device validation is pending: compare
the phone, watch app and complication timestamps on successive readings; also
check direct sensor mode and configured smoothing. No sensor transport, alarm,
phone code or OngoingActivity implementation changed in this commit.
