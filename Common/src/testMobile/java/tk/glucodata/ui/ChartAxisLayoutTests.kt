package tk.glucodata.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChartAxisLayoutTests {
    @Test fun stepFollowsUnitsAndAvailableHeight() {
        assertEquals(2f, chartYAxisStep(12f, 300f, 12f, 40f, true), 0.001f)
        assertEquals(50f, chartYAxisStep(240f, 300f, 12f, 40f, false), 0.001f)
        assertEquals(0.5f, chartYAxisStep(2f, 300f, 12f, 40f, true), 0.001f)
        assertEquals(10f, chartYAxisStep(30f, 300f, 12f, 40f, false), 0.001f)
    }

    @Test fun largeFontsOrShortPlotsReduceTickDensity() {
        val normal = chartYAxisStep(12f, 300f, 12f, 40f, true)
        assertTrue(chartYAxisStep(12f, 300f, 32f, 40f, true) > normal)
        assertTrue(chartYAxisStep(12f, 100f, 12f, 40f, true) > normal)
    }

    @Test fun ticksStayInsideFractionalViewportBounds() {
        assertEquals(listOf(3.5f, 4f, 4.5f, 5f), chartYAxisTicks(3.3f, 5.2f, 0.5f))
        assertEquals(listOf(100f, 150f, 200f), chartYAxisTicks(80f, 210f, 50f))
        assertTrue(chartYAxisTicks(Float.NaN, 10f, 1f).isEmpty())
        assertTrue(chartYAxisTicks(0f, 10f, 0f).isEmpty())
    }

    @Test fun extremaLabelsSeparateWithoutLeavingPlot() {
        for ((top, bottom) in listOf(0f to 1f, 149f to 151f, 299f to 300f)) {
            val (a, b) = chartExtremaLabelCenters(top, bottom, 300f, 20f, 8f)
            assertTrue(a >= 10f)
            assertTrue(b <= 290f)
            assertTrue(b - a >= 28f)
        }
        assertEquals(50f to 250f, chartExtremaLabelCenters(50f, 250f, 300f, 20f, 8f))
    }

    @Test fun veryShortPlotStillHasValidLabelCenters() {
        val (a, b) = chartExtremaLabelCenters(0f, 10f, 10f, 20f, 8f)
        assertEquals(5f, a, 0.001f)
        assertEquals(5f, b, 0.001f)
    }
}
