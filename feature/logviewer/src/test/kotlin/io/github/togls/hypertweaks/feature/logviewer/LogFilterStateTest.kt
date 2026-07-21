package io.github.togls.hypertweaks.feature.logviewer

import io.github.togls.hypertweaks.logging.api.LogLevel
import io.github.togls.hypertweaks.logging.api.LogSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LogFilterStateTest {
    @Test
    fun `filter maps to normalized repository query`() {
        val query = LogFilterState(
            source = LogSource.HOOK,
            levels = setOf(LogLevel.ERROR),
            packageName = "  com.example.target  ",
            keyword = " failure ",
            timeRange = LogTimeRange.DAY,
        ).toQuery(now = 100_000_000L).normalized()

        assertEquals(LogSource.HOOK, query.source)
        assertEquals(setOf(LogLevel.ERROR), query.levels)
        assertEquals("com.example.target", query.packageName)
        assertEquals("failure", query.keyword)
        assertEquals(13_600_000L, query.fromMillis)
    }

    @Test
    fun `default filter has no time lower bound`() {
        assertNull(LogFilterState().toQuery(now = 10L).fromMillis)
    }
}
