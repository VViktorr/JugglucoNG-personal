/*      This file is part of Juggluco, an Android app to receive and display         */
/*      glucose values from Freestyle Libre 2 and 3 sensors.                         */
/*                                                                                   */
/*      Copyright (C) 2021 Jaap Korthals Altes <jaapkorthalsaltes@gmail.com>         */
/*                                                                                   */
/*      Juggluco is free software: you can redistribute it and/or modify             */
/*      it under the terms of the GNU General Public License as published            */
/*      by the Free Software Foundation, either version 3 of the License, or         */
/*      (at your option) any later version.                                          */
/*                                                                                   */
/*      Juggluco is distributed in the hope that it will be useful, but              */
/*      WITHOUT ANY WARRANTY; without even the implied warranty of                   */
/*      MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.                         */
/*      See the GNU General Public License for more details.                         */
/*                                                                                   */
/*      You should have received a copy of the GNU General Public License            */
/*      along with Juggluco. If not, see <https://www.gnu.org/licenses/>.            */
/*                                                                                   */
/*      Fri Oct 11 12:22:15 CEST 2024                                                 */


package tk.glucodata.glucosecomplication

import android.content.ComponentName
import android.graphics.drawable.Icon
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceUpdateRequester
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService
import tk.glucodata.Applic
import tk.glucodata.Log

class ShortArrowValueDataSourceService : SuspendingComplicationDataSourceService() {

    private fun timeIcon(reading: GlucoseComplicationData.Reading?): Icon? {
        val at = reading?.timeMillis?.takeIf { it > 0L } ?: return null
        return Icon.createWithBitmap(
            ComplicationRenderer.timeBitmap(ICON_PX, at, ComplicationRenderer.ICON_TINT),
        )
    }

    private fun build(
        type: ComplicationType,
        reading: GlucoseComplicationData.Reading?,
    ): ComplicationData? {
        val tap = GlucoseComplicationData.tapAction()
        val icon = timeIcon(reading)
        val title = GlucoseComplicationData.readingTimeText(reading)
        return when (type) {
            ComplicationType.SHORT_TEXT -> GlucoseComplicationData.shortValueData(
                reading,
                "Compact glucose + trend",
                tap,
                icon,
                title = title,
                withArrow = true,
            )
            ComplicationType.LONG_TEXT -> GlucoseComplicationData.longTextData(
                reading,
                "Compact glucose + trend",
                tap,
                icon,
                title = title,
                withArrow = true,
            )
            ComplicationType.RANGED_VALUE -> GlucoseComplicationData.rangedValueData(
                reading,
                "Compact glucose + trend",
                tap,
                icon,
                withArrow = true,
            )
            else -> null
        }
    }

    override fun getPreviewData(type: ComplicationType): ComplicationData? =
        build(type, GlucoseComplicationData.previewReading())

    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData? {
        Log.d(LOG_ID, "onComplicationRequest() id: ${request.complicationInstanceId}")
        return build(request.complicationType, GlucoseComplicationData.currentReading())
    }

    companion object {
        private const val LOG_ID = "ShortArrowValueDataSourceService"
        private const val ICON_PX = 256

        private val updateRequester by lazy {
            ComplicationDataSourceUpdateRequester.create(
                Applic.app,
                ComponentName(Applic.app, ShortArrowValueDataSourceService::class.java),
            )
        }

        @JvmStatic
        fun update() {
            updateRequester.requestUpdateAll()
        }
    }
}
