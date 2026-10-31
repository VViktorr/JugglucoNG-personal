package tk.glucodata.ui.stats

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import tk.glucodata.data.journal.JournalEntryEntity
import tk.glucodata.ui.GlucosePoint

class LibreViewMealProfileTests {
    private val time = 1_700_000_000_000L
    private fun point(minutes: Long, value: Float) = GlucosePoint(value, "", time + minutes * 60_000L)
    private fun entry(type: String) = JournalEntryEntity(
        timestamp = time, sensorSerial = null, entryType = type, title = "Meal",
        note = null, amount = 30f, glucoseValueMgDl = null, durationMinutes = null,
        intensity = null, insulinPresetId = null, source = "manual", sourceRecordId = null,
        createdAt = time, updatedAt = time
    )

    @Test fun gapsRemainMissingAndFutureReadingsAreNotUsedAsPreMealValues() {
        val points = listOf(point(1, 150f), point(61, 180f), point(140, 200f), point(180, 120f))
        val sample = LibreViewMealProfile.build(points, listOf(entry("carbs"))).single()
        assertNull(sample.glucose[0])
        assertEquals(180f, sample.glucose[1]!!, 0f)
        assertNull(sample.glucose[2])
        assertEquals(120f, sample.glucose[3]!!, 0f)
    }

    @Test fun insulinDosesDoNotInventMeals() {
        assertEquals(emptyList<LibreViewMealSample>(), LibreViewMealProfile.build(listOf(point(0, 100f)), listOf(entry("insulin"))))
    }

    @Test fun nearestPriorReadingIsUsedWithinFifteenMinutes() {
        val sample = LibreViewMealProfile.build(listOf(point(-16, 50f), point(-5, 100f), point(2, 200f)), listOf(entry("carbs"))).single()
        assertEquals(100f, sample.glucose[0]!!, 0f)
    }
}
