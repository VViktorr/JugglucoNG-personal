package tk.glucodata.ui.stats

import android.graphics.RectF

/** Letter coordinates measured from the supplied report. Also used by layout QA. */
internal object LibreViewReportLayout {
    const val WIDTH = 612
    const val HEIGHT = 792
    private val frames = mapOf(
        "agp.ranges" to RectF(38f, 134f, 302f, 313f),
        "agp.metrics" to RectF(310f, 134f, 574f, 313f),
        "agp.profile" to RectF(38f, 319f, 574f, 528f),
        "agp.plot" to RectF(121f, 371f, 529f, 501f),
        "agp.daily" to RectF(38f, 535f, 574f, 648f),
        "insights.notes" to RectF(38f, 319f, 574f, 461f),
        "insights.profile" to RectF(38f, 468f, 574f, 683f),
        "insights.plot" to RectF(121f, 526f, 529f, 640f),
        "snapshot.glucose" to RectF(244f, 143f, 445f, 254f),
        "snapshot.lows" to RectF(244f, 297f, 445f, 408f),
        "snapshot.coverage" to RectF(244f, 484f, 445f, 562f),
        "trends.glucose" to RectF(163f, 147f, 567f, 309f),
        "trends.carbs" to RectF(163f, 355f, 567f, 439f),
        "trends.insulin" to RectF(163f, 467f, 567f, 505f),
        "monthly.calendar" to RectF(175f, 128f, 578f, 419f),
        "meals.panels" to RectF(140f, 120f, 547f, 197f),
        "meals.table" to RectF(140f, 246f, 547f, 349f),
        "device.settings" to RectF(38f, 130f, 351f, 310f),
        "device.phone" to RectF(359f, 150f, 574f, 310f)
    )
    fun frame(name: String): RectF = RectF(checkNotNull(frames[name]) { name })
}
