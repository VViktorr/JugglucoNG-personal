package tk.glucodata.ui.stats

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class ClinicalReportPolicyTests {
    @Test fun sevenDaysIncludeTodayAndSixPriorDates() {
        val zone = ZoneId.of("Europe/Warsaw")
        val now = Instant.parse("2026-10-20T09:03:00Z").toEpochMilli()
        val range = ClinicalReportPolicy.range(7, now, zone)
        assertEquals(Instant.parse("2026-10-13T22:00:00Z").toEpochMilli(), range.startMillis)
        assertEquals(now, range.endMillis)
        val first = Instant.ofEpochMilli(range.startMillis).atZone(zone).toLocalDate()
        val last = Instant.ofEpochMilli(range.endMillis).atZone(zone).toLocalDate()
        assertEquals(11, LibreViewReportPages.plan(LibreViewReportGroup.entries.toSet(), first, last).size)
    }

    @Test fun calendarDaysRemainCorrectAcrossDaylightSavingChange() {
        val zone = ZoneId.of("Europe/Warsaw")
        val now = Instant.parse("2026-10-26T10:00:00Z").toEpochMilli()
        val range = ClinicalReportPolicy.range(2, now, zone)
        assertEquals(Instant.parse("2026-10-24T22:00:00Z").toEpochMilli(), range.startMillis)
    }

    @Test fun gmiSiUnitsFollowThePublishedEquation() {
        assertEquals(52.67f, ClinicalReportPolicy.gmiMmolMol(153f), 0.1f)
    }

    @Test fun treatmentMeanUsesDaysWithEntriesInsteadOfUnrecordedDays() {
        val time = Instant.parse("2026-10-14T10:00:00Z").toEpochMilli()
        val entries = listOf(time to 6f, (time + 3_600_000L) to 16f, (time + 86_400_000L) to 6f)
        assertEquals(14f, ClinicalReportPolicy.recordedDailyMean(entries, ZoneId.of("UTC"))!!, 0f)
        assertEquals(null, ClinicalReportPolicy.recordedDailyMean(emptyList(), ZoneId.of("UTC")))
    }
}
