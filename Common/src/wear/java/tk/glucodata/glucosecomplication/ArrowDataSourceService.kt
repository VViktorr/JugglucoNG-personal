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
import androidx.wear.watchface.complications.data.LongTextComplicationData
import androidx.wear.watchface.complications.data.MonochromaticImage
import androidx.wear.watchface.complications.data.MonochromaticImageComplicationData
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import androidx.wear.watchface.complications.data.SmallImage
import androidx.wear.watchface.complications.data.SmallImageComplicationData
import androidx.wear.watchface.complications.data.SmallImageType
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceUpdateRequester
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService
import tk.glucodata.Applic
import tk.glucodata.Log

class ArrowDataSourceService : SuspendingComplicationDataSourceService() {

    private fun description() = PlainComplicationText.Builder("Glucose trend").build()

    private fun arrowText(reading: GlucoseComplicationData.Reading?): String =
        reading?.let { GlucoseComplicationData.trendArrowText(it.rate) }
            ?.takeIf { it.isNotEmpty() } ?: "—"

    private fun arrowIcon(reading: GlucoseComplicationData.Reading?): Icon {
        val bitmap = reading?.rate?.takeIf { it.isFinite() }?.let { rate ->
            ComplicationRenderer.arrowBitmap(ICON_SIZE, rate, ComplicationRenderer.ICON_TINT)
        } ?: ComplicationRenderer.valueBitmap(
            ICON_SIZE, "—", 0f, false, ComplicationRenderer.ICON_TINT, icon = true,
        )
        return Icon.createWithBitmap(bitmap)
    }

    private fun build(
        type: ComplicationType,
        reading: GlucoseComplicationData.Reading?,
    ): ComplicationData? {
        val tap = GlucoseComplicationData.tapAction()
        val icon = arrowIcon(reading)
        return when (type) {
            ComplicationType.MONOCHROMATIC_IMAGE ->
                MonochromaticImageComplicationData.Builder(
                    MonochromaticImage.Builder(icon).build(),
                    description(),
                ).setTapAction(tap).build()

            ComplicationType.SMALL_IMAGE ->
                SmallImageComplicationData.Builder(
                    SmallImage.Builder(icon, SmallImageType.ICON).build(),
                    description(),
                ).setTapAction(tap).build()

            ComplicationType.SHORT_TEXT ->
                ShortTextComplicationData.Builder(
                    PlainComplicationText.Builder(arrowText(reading)).build(),
                    description(),
                ).setTapAction(tap).build()

            ComplicationType.LONG_TEXT ->
                LongTextComplicationData.Builder(
                    PlainComplicationText.Builder(arrowText(reading)).build(),
                    description(),
                ).setTapAction(tap).build()

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
        private const val LOG_ID = "ArrowDataSourceService"
        private const val ICON_SIZE = 256

        private val updateRequester by lazy {
            ComplicationDataSourceUpdateRequester.create(
                Applic.app,
                ComponentName(Applic.app, ArrowDataSourceService::class.java),
            )
        }

        @JvmStatic
        fun update() {
            updateRequester.requestUpdateAll()
        }
    }
}
