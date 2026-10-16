package tk.glucodata.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNull
import org.junit.Test

class HistoryBucketReplacementTests {

    @Test
    fun plan_groupsReadingsIntoDistinctBucketsAndPreservesTimestampOrder() {
        val plan = HistoryBucketReplacement.plan(
            readings = listOf(
                HistoryReading(timestamp = 60_000L, sensorSerial = "sensor", value = 100f, rawValue = 100f, rate = null),
                HistoryReading(timestamp = 61_000L, sensorSerial = "sensor", value = 101f, rawValue = 101f, rate = null),
                HistoryReading(timestamp = 119_999L, sensorSerial = "sensor", value = 102f, rawValue = 102f, rate = null),
                HistoryReading(timestamp = 120_000L, sensorSerial = "sensor", value = 103f, rawValue = 103f, rate = null),
                HistoryReading(timestamp = 120_000L, sensorSerial = "sensor", value = 103f, rawValue = 103f, rate = null),
            ),
            bucketDurationMs = 60_000L,
        )

        requireNotNull(plan)
        assertEquals(listOf(HistoryBucketReplacement.BucketRange(1L, 2L)), plan.bucketRanges)
    }

    @Test
    fun plan_keepsNonAdjacentBucketsAsSeparateRanges() {
        val plan = HistoryBucketReplacement.plan(
            readings = listOf(
                HistoryReading(timestamp = 60_000L, sensorSerial = "sensor", value = 100f, rawValue = 100f, rate = null),
                HistoryReading(timestamp = 180_000L, sensorSerial = "sensor", value = 101f, rawValue = 101f, rate = null),
                HistoryReading(timestamp = 240_000L, sensorSerial = "sensor", value = 102f, rawValue = 102f, rate = null),
            ),
            bucketDurationMs = 60_000L,
        )

        requireNotNull(plan)
        assertEquals(
            listOf(
                HistoryBucketReplacement.BucketRange(1L, 1L),
                HistoryBucketReplacement.BucketRange(3L, 4L),
            ),
            plan.bucketRanges
        )
    }

    @Test
    fun collapseReadings_keepsOneBestReadingPerBucket() {
        val collapsed = HistoryBucketReplacement.collapseReadings(
            readings = listOf(
                HistoryReading(timestamp = 60_000L, sensorSerial = "sensor", value = 0f, rawValue = 100f, rate = null),
                HistoryReading(timestamp = 61_000L, sensorSerial = "sensor", value = 101f, rawValue = 101f, rate = null),
                HistoryReading(timestamp = 62_000L, sensorSerial = "sensor", value = 102f, rawValue = 102f, rate = 1f),
                HistoryReading(timestamp = 120_000L, sensorSerial = "sensor", value = 103f, rawValue = 103f, rate = null),
            ),
            bucketDurationMs = 60_000L,
        )

        assertEquals(listOf(62_000L, 120_000L), collapsed.map { it.timestamp })
        assertEquals(listOf(102f, 103f), collapsed.map { it.value })
    }

    @Test
    fun plan_returnsNullWhenNoValidTimestampsRemain() {
        val plan = HistoryBucketReplacement.plan(
            readings = listOf(
                HistoryReading(timestamp = 0L, sensorSerial = "sensor", value = 100f, rawValue = 100f, rate = null),
                HistoryReading(timestamp = -1L, sensorSerial = "sensor", value = 101f, rawValue = 101f, rate = null),
            ),
            bucketDurationMs = 60_000L,
        )

        assertNull(plan)
    }

    @Test
    fun overlap_keepsCompleteReadingWhenNextBatchHasOnlyRawInSameMinute() {
        val complete = HistoryReading(timestamp = 60_010_000L, sensorSerial = "sensor",
            value = 110f, rawValue = 105f, rate = null)
        val rawOnly = complete.copy(timestamp = 60_050_000L, value = 0f, rawValue = 107f)
        val result = HistoryBucketReplacement.retainBestReadings(
            listOf(complete), listOf(rawOnly), 60_000L)
        assertEquals(listOf(complete), result)
    }

    @Test
    fun overlap_keepsNewerReadingWhenBothHaveSameQuality() {
        val old = HistoryReading(timestamp = 60_010_000L, sensorSerial = "sensor",
            value = 110f, rawValue = 105f, rate = null)
        val newer = old.copy(timestamp = 60_050_000L, value = 112f, rawValue = 107f)
        assertEquals(listOf(newer), HistoryBucketReplacement.retainBestReadings(
            listOf(old), listOf(newer), 60_000L))
    }

    @Test
    fun overlap_updatesSameTimestampWithoutErasingAnAvailableLane() {
        val old = HistoryReading(timestamp = 60_010_000L, sensorSerial = "sensor",
            value = 110f, rawValue = 105f, rate = 1f)
        val incoming = old.copy(value = 112f, rawValue = 0f, rate = null)
        assertEquals(listOf(old.copy(value = 112f)), HistoryBucketReplacement.retainBestReadings(
            listOf(old), listOf(incoming), 60_000L))
    }

    @Test
    fun overlap_doesNotCombineLanesFromDifferentTimestamps() {
        val autoOnly = HistoryReading(timestamp = 60_010_000L, sensorSerial = "sensor",
            value = 110f, rawValue = 0f, rate = null)
        val rawOnly = autoOnly.copy(timestamp = 60_050_000L, value = 0f, rawValue = 105f)
        assertEquals(listOf(autoOnly), HistoryBucketReplacement.retainBestReadings(
            listOf(autoOnly), listOf(rawOnly), 60_000L))
    }

    @Test
    fun overlap_prefersCompleteIncomingOverExistingRawOnly() {
        val rawOnly = HistoryReading(timestamp = 60_010_000L, sensorSerial = "sensor",
            value = 0f, rawValue = 105f, rate = null)
        val complete = rawOnly.copy(timestamp = 60_050_000L, value = 112f, rawValue = 107f)
        assertEquals(listOf(complete), HistoryBucketReplacement.retainBestReadings(
            listOf(rawOnly), listOf(complete), 60_000L))
    }

    @Test
    fun batchBoundary_keepsTheThousandthAndNextReadingInTheSameMinuteTogether() {
        val readings = (1..999).map { minute ->
            HistoryReading(timestamp = minute * 60_000L, sensorSerial = "sensor",
                value = 110f, rawValue = 105f, rate = null)
        }.toMutableList()
        assertFalse(HistoryBucketReplacement.shouldFlushBefore(readings, 60_010_000L, 1000, 60_000L))
        readings.add(readings.last().copy(timestamp = 60_010_000L))
        assertFalse(HistoryBucketReplacement.shouldFlushBefore(readings, 60_050_000L, 1000, 60_000L))
        readings.add(readings.last().copy(timestamp = 60_050_000L, value = 0f))
        assertTrue(HistoryBucketReplacement.shouldFlushBefore(readings, 60_060_000L, 1000, 60_000L))
        val collapsed = HistoryBucketReplacement.collapseReadings(readings, 60_000L)
        assertEquals(1000, collapsed.size)
        assertEquals(110f, collapsed.last().value, 0f)
    }

    @Test
    fun singleLiveAutoOnlyWriteDoesNotEraseRecordedRawInTheSameMinute() {
        val complete = HistoryReading(timestamp = 60_010_000L, sensorSerial = "sensor",
            value = 110f, rawValue = 105f, rate = null)
        val incoming = complete.copy(timestamp = 60_050_000L, value = 112f, rawValue = 0f, rate = 0f)
        assertEquals(listOf(complete), HistoryBucketReplacement.retainBestReadings(
            listOf(complete), listOf(incoming), 60_000L))
    }

    @Test
    fun singleLiveAutoUpdateAtTheSameTimePreservesRecordedRaw() {
        val complete = HistoryReading(timestamp = 60_010_000L, sensorSerial = "sensor",
            value = 110f, rawValue = 105f, rate = null)
        val incoming = complete.copy(value = 112f, rawValue = 0f, rate = 0f)
        assertEquals(listOf(complete.copy(value = 112f, rate = 0f)),
            HistoryBucketReplacement.retainBestReadings(
                listOf(complete), listOf(incoming), 60_000L))
    }
}
