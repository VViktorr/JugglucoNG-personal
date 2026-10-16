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
import androidx.wear.watchface.complications.data.ComplicationText
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.ComplicationType.*
import androidx.wear.watchface.complications.data.MonochromaticImage
import androidx.wear.watchface.complications.data.MonochromaticImageComplicationData
import androidx.wear.watchface.complications.data.PhotoImageComplicationData
import androidx.wear.watchface.complications.data.SmallImage
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import androidx.wear.watchface.complications.data.SmallImageComplicationData
import androidx.wear.watchface.complications.data.SmallImageType
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceUpdateRequester
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService
import tk.glucodata.Applic
import tk.glucodata.CurrentDisplaySource
import tk.glucodata.Log
import tk.glucodata.MainActivity
import tk.glucodata.Notify
import tk.glucodata.R
import java.lang.Math.min

class ShortArrowValueDataSourceService: SuspendingComplicationDataSourceService()  {
    /**
     * Arrow plus the reading's time, drawn by ComplicationRenderer so the angle
     * and colour match the app rather than the legacy geometry.
     */
    private fun arrowTimeBitmap(rate: Float, timeMillis: Long): android.graphics.Bitmap =
        // Arrow over the reading's time. The time is the whole difference
        // between this complication and the plain Value + arrow.
        ComplicationRenderer.arrowWithTimeBitmap(
            ICON_PX, rate, timeMillis, ComplicationRenderer.ICON_TINT,
        )

    private val ICON_PX = 256

private var glview: WearComplicationValue? =null

    override fun onComplicationActivated( complicationInstanceId: Int, type: ComplicationType) {
        Log.d(LOG_ID, "onComplicationActivated(): $complicationInstanceId")
    }
    override fun onComplicationDeactivated(complicationInstanceId: Int) {
        Log.d(LOG_ID, "onComplicationDeactivated(): $complicationInstanceId")
    }
fun getview(type: ComplicationType):WearComplicationValue {
     if(glview==null) {
        val width:Int = 150
        val height:Int = 150
         glview= WearComplicationValue(width,height)
         }
      return glview as WearComplicationValue;
      }
    override fun getPreviewData(type: ComplicationType): ComplicationData? =
        build(type, GlucoseComplicationData.previewReading())

    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData? =
        build(request.complicationType, GlucoseComplicationData.currentReading())

    private fun build(type: ComplicationType, reading: GlucoseComplicationData.Reading?): ComplicationData? {
        val tap = GlucoseComplicationData.tapAction()
        val image = reading?.let { Icon.createWithBitmap(arrowTimeBitmap(it.rate, it.timeMillis)) }
        val textReading = GlucoseComplicationData.withTrend(reading)
        return when (type) {
            SHORT_TEXT -> GlucoseComplicationData.shortValueData(textReading, "Glucose+trend", tap, image)
            LONG_TEXT -> GlucoseComplicationData.longTextData(textReading, "Glucose+trend", tap, image)
            RANGED_VALUE -> GlucoseComplicationData.rangedValueData(textReading, "Glucose+trend", tap, image)
            SMALL_IMAGE, MONOCHROMATIC_IMAGE -> {
                val bitmap = reading?.let {
                    ComplicationRenderer.valueWithArrowBitmap(
                        ICON_PX, it.text, it.value, it.rate, it.isMmol,
                        if (type == MONOCHROMATIC_IMAGE) ComplicationRenderer.ICON_TINT
                        else ComplicationRenderer.valueColor(it.value, it.isMmol),
                        icon = type == MONOCHROMATIC_IMAGE,
                    )
                } ?: ComplicationRenderer.noValueBitmap(ICON_PX)
                val icon = Icon.createWithBitmap(bitmap)
                val description = PlainComplicationText.Builder("Glucose+trend").build()
                if (type == MONOCHROMATIC_IMAGE) {
                    MonochromaticImageComplicationData.Builder(
                        MonochromaticImage.Builder(icon).build(), description,
                    ).setTapAction(tap).build()
                } else {
                    SmallImageComplicationData.Builder(
                        SmallImage.Builder(icon, SmallImageType.PHOTO).build(), description,
                    ).setTapAction(tap).build()
                }
            }
            else -> null
        }
    }

    companion object {
        private const val LOG_ID = "ShortArrowValueDataSourceService"
   private val complicationDataSourceUpdateRequester = ComplicationDataSourceUpdateRequester.create( context=tk.glucodata.Applic.app, complicationDataSourceComponent = ComponentName(tk.glucodata.Applic.app,
    ShortArrowValueDataSourceService::class.java
   ))

        public fun update() {
            complicationDataSourceUpdateRequester.requestUpdateAll()
        }
    }
}
