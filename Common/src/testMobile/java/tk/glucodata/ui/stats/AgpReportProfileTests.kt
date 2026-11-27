package tk.glucodata.ui.stats

import org.junit.Assert.assertEquals
import org.junit.Test
import tk.glucodata.ui.GlucosePoint
import java.time.Instant
import java.time.ZoneId

class AgpReportProfileTests {
    private val utc = ZoneId.of("UTC")
    private fun point(value: Float, minute: Int) = GlucosePoint(
        value, "", Instant.parse("2026-01-01T00:00:00Z").toEpochMilli() + minute * 60_000L
    )

    @Test fun reportUsesFifthAndNinetyFifthPercentiles() {
        val bin = AgpReportProfile.build((0..100).map { point(it + 50f, 0) }, utc).single()
        assertEquals(55f, bin.p05, 0.001f)
        assertEquals(75f, bin.p25, 0.001f)
        assertEquals(100f, bin.median, 0.001f)
        assertEquals(125f, bin.p75, 0.001f)
        assertEquals(145f, bin.p95, 0.001f)
    }

    @Test fun emptyBinsSplitTheProfileInsteadOfInterpolatingAcrossGaps() {
        val bins = AgpReportProfile.build(listOf(point(100f, 0), point(110f, 15), point(200f, 60)), utc)
        assertEquals(listOf(0, 1, 4), bins.map { it.index })
        assertEquals(listOf(2, 1), AgpReportProfile.segments(bins).map { it.size })
    }

    @Test fun binsUseTheRequestedLocalTimeZoneAndDiscardInvalidValues() {
        val bins = AgpReportProfile.build(listOf(point(120f, 0), point(Float.NaN, 0), point(0f, 0)), ZoneId.of("Europe/Warsaw"))
        assertEquals(4, bins.single().index)
        assertEquals(120f, bins.single().median, 0f)
    }

    @Test fun noDataProducesNoInventedProfile() {
        assertEquals(emptyList<AgpReportBin>(), AgpReportProfile.build(emptyList(), utc))
        assertEquals(emptyList<List<AgpReportBin>>(), AgpReportProfile.segments(emptyList()))
    }
}
