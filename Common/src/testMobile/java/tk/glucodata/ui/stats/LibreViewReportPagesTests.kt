package tk.glucodata.ui.stats

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class LibreViewReportPagesTests {
    private val first = LocalDate.of(2026, 9, 23)
    private val last = LocalDate.of(2026, 10, 20)

    @Test fun defaultsPreserveTheOriginalThreePages() {
        val pages = LibreViewReportPages.plan(LibreViewReportGroup.defaults, first, last)
        assertEquals(listOf(LibreViewReportGroup.AGP, LibreViewReportGroup.SNAPSHOT, LibreViewReportGroup.DAILY_TRENDS), pages.map { it.group })
    }

    @Test fun fullTwentyEightDayReportHasTheExpectedGroupPagination() {
        val pages = LibreViewReportPages.plan(LibreViewReportGroup.entries.toSet(), first, last)
        assertEquals(23, pages.size)
        assertEquals(4, pages.count { it.group == LibreViewReportGroup.WEEKLY_SUMMARY })
        assertEquals(2, pages.count { it.group == LibreViewReportGroup.MONTHLY_SUMMARY })
        assertEquals(10, pages.count { it.group == LibreViewReportGroup.DAILY_LOG })
        assertEquals(2, pages.count { it.group == LibreViewReportGroup.MEAL_PATTERNS })
    }

    @Test fun selectingOneGroupDoesNotAddOtherPages() {
        val pages = LibreViewReportPages.plan(setOf(LibreViewReportGroup.DEVICE), first, last)
        assertEquals(listOf(LibreViewReportGroup.DEVICE), pages.map { it.group })
    }

    @Test fun partialWeeksAndDaysAreNotLostOrDuplicated() {
        val pages = LibreViewReportPages.plan(setOf(LibreViewReportGroup.DAILY_LOG), first, last)
        assertEquals(1, pages.last().dates.size)
        val dates = pages.flatMap { it.dates }
        assertEquals(28, dates.size)
        assertEquals(28, dates.distinct().size)
        assertEquals(first, dates.first())
        assertEquals(last, dates.last())
    }

    @Test fun settingsDistinguishFirstUseFromExplicitEmptySelection() {
        assertEquals(LibreViewReportGroup.defaults, LibreViewReportGroup.fromKeys(null))
        assertTrue(LibreViewReportGroup.fromKeys(emptySet()).isEmpty())
        assertEquals(setOf(LibreViewReportGroup.SNAPSHOT), LibreViewReportGroup.fromKeys(listOf("snapshot", "future_key")))
    }

    @Test(expected = IllegalArgumentException::class)
    fun emptySelectionCannotCreateAnEmptyPdf() {
        LibreViewReportPages.plan(emptySet(), first, last)
    }
}
