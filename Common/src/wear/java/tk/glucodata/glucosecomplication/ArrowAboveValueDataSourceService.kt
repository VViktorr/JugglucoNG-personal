package tk.glucodata.glucosecomplication

import android.content.ComponentName
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceUpdateRequester
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService
import tk.glucodata.Applic
import tk.glucodata.R

/** Text-only option: faces that display titles can place the trend above glucose. */
class ArrowAboveValueDataSourceService : SuspendingComplicationDataSourceService() {
    override fun getPreviewData(type: ComplicationType): ComplicationData? =
        build(type, GlucoseComplicationData.previewReading())

    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData? =
        build(request.complicationType, GlucoseComplicationData.currentReading())

    private fun build(type: ComplicationType, reading: GlucoseComplicationData.Reading?): ComplicationData? {
        val arrow = reading?.let { GlucoseComplicationData.trendText(it.rate) }
        val label = getString(R.string.wear_arrow_above_value_label)
        val description = listOfNotNull(label, arrow, reading?.text).joinToString(", ")
        return when (type) {
            ComplicationType.SHORT_TEXT -> GlucoseComplicationData.shortValueData(
                reading, description, title = arrow,
            )
            ComplicationType.LONG_TEXT -> GlucoseComplicationData.longTextData(
                reading, description, title = arrow,
            )
            else -> null
        }
    }

    companion object {
        fun update() {
            ComplicationDataSourceUpdateRequester.create(
                Applic.app, ComponentName(Applic.app, ArrowAboveValueDataSourceService::class.java),
            ).requestUpdateAll()
        }
    }
}
