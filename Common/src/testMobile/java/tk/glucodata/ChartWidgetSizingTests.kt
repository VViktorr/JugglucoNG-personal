package tk.glucodata

import org.junit.Assert.*
import org.junit.Test
import tk.glucodata.widget.ChartWidgetSizing
import tk.glucodata.widgets.WidgetConfig

class ChartWidgetSizingTests {
    private val base = WidgetConfig(showWeather = false)

    @Test fun increasingMainOrDetailTextReservesSpaceAboveChart() {
        val normal = ChartWidgetSizing.headerHeightDp(400f, base, 1f)
        val largeMain = ChartWidgetSizing.headerHeightDp(400f, base.copy(glucoseScale = 180), 1f)
        val largeDetail = ChartWidgetSizing.headerHeightDp(400f, base.copy(detailScale = 180), 1f)
        assertTrue(largeMain > normal)
        assertTrue(largeDetail > normal)
        assertTrue(ChartWidgetSizing.chartHeightDp(400f, largeMain) >= 60f)
    }

    @Test fun shortTwoRowWidgetKeepsAChartEvenWithLargeText() {
        val config = base.copy(glucoseScale = 180, detailScale = 180)
        val header = ChartWidgetSizing.headerHeightDp(144f, config, 1.5f)
        assertEquals(60f, ChartWidgetSizing.chartHeightDp(144f, header), 0.01f)
        assertTrue(header > 0f)
    }

    @Test fun systemFontScaleAndVisibleWeatherAlsoReserveHeaderSpace() {
        val normal = ChartWidgetSizing.headerHeightDp(600f, base, 1f)
        assertTrue(ChartWidgetSizing.headerHeightDp(600f, base, 1.5f) > normal)
        val weather = base.copy(showWeather = true, weatherScale = 200, weatherIconScale = 220)
        assertTrue(ChartWidgetSizing.headerHeightDp(600f, weather, 1f) > normal)
        assertEquals(normal, ChartWidgetSizing.headerHeightDp(
            600f, weather.copy(showWeatherIcon = false, showTemperature = false), 1f), 0.01f)
    }

    @Test fun verySmallHostNeverAllocatesNegativeChartSpace() {
        for (height in listOf(0f, 40f, 80f, 120f)) {
            val header = ChartWidgetSizing.headerHeightDp(height, base, 2f)
            assertEquals(0f, ChartWidgetSizing.chartHeightDp(height, header), 0.01f)
        }
    }
}
