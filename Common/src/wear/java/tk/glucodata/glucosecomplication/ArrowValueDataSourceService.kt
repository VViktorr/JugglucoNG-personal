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
import androidx.wear.watchface.complications.data.MonochromaticImage
import androidx.wear.watchface.complications.data.MonochromaticImageComplicationData
import androidx.wear.watchface.complications.data.PhotoImageComplicationData
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.SmallImage
import androidx.wear.watchface.complications.data.SmallImageComplicationData
import androidx.wear.watchface.complications.data.SmallImageType
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceUpdateRequester
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService
import tk.glucodata.Applic
import tk.glucodata.Log

class ArrowValueDataSourceService : SuspendingComplicationDataSourceService() {

    private fun description() = PlainComplicationText.Builder("Glucose + trend").build()

    private fun valueArrowBitmap(
        reading: GlucoseComplicationData.Reading?,
        monochrome: Boolean,
    ) = if (reading == null) {
        ComplicationRenderer.valueBitmap(
            if (monochrome) ICON_PX else VALUE_ARROW_PX,
            "—", 0f, false,
            if (monochrome) ComplicationRenderer.ICON_TINT else 0xFFE3E2DE.toInt(),
            icon = monochrome,
        )
    } else {
        ComplicationRenderer.valueWithArrowBitmap(
            if (monochrome) ICON_PX else VALUE_ARROW_PX,
            reading.text,
            reading.value,
            reading.rate,
            reading.isMmol,
            if (monochrome) ComplicationRenderer.ICON_TINT
            else ComplicationRenderer.valueColor(reading.value, reading.isMmol),
            icon = monochrome,
        )
    }

    private fun build(
        type: ComplicationType,
        reading: GlucoseComplicationData.Reading?,
    ): ComplicationData? {
        val tap = GlucoseComplicationData.tapAction()
        return when (type) {
            ComplicationType.MONOCHROMATIC_IMAGE -> {
                val icon = Icon.createWithBitmap(valueArrowBitmap(reading, monochrome = true))
                MonochromaticImageComplicationData.Builder(
                    MonochromaticImage.Builder(icon).build(),
                    description(),
                ).setTapAction(tap).build()
            }

            ComplicationType.PHOTO_IMAGE -> {
                val icon = Icon.createWithBitmap(valueArrowBitmap(reading, monochrome = false))
                PhotoImageComplicationData.Builder(icon, description())
                    .setTapAction(tap)
                    .build()
            }

            ComplicationType.SMALL_IMAGE -> {
                val icon = Icon.createWithBitmap(valueArrowBitmap(reading, monochrome = false))
                SmallImageComplicationData.Builder(
                    SmallImage.Builder(icon, SmallImageType.PHOTO).build(),
                    description(),
                ).setTapAction(tap).build()
            }

            // The arrow is in the required text. Optional images are omitted
            // here so a face that renders both does not show two arrows.
            ComplicationType.SHORT_TEXT -> GlucoseComplicationData.shortValueData(
                reading, "Glucose + trend", tap, withArrow = true,
            )
            ComplicationType.LONG_TEXT -> GlucoseComplicationData.longTextData(
                reading, "Glucose + trend", tap, withArrow = true,
            )
            ComplicationType.RANGED_VALUE -> GlucoseComplicationData.rangedValueData(
                reading, "Glucose + trend", tap, withArrow = true,
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
        private const val LOG_ID = "ArrowValueDataSourceService"
        private const val VALUE_ARROW_PX = 384
        private const val ICON_PX = 256

        private val updateRequester by lazy {
            ComplicationDataSourceUpdateRequester.create(
                Applic.app,
                ComponentName(Applic.app, ArrowValueDataSourceService::class.java),
            )
        }

        @JvmStatic
        fun update() {
            updateRequester.requestUpdateAll()
        }
    }
}
