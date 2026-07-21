package io.github.togls.hypertweaks.logging.api

import org.junit.Assert.assertEquals
import org.junit.Test

class LogPolicyTest {
    @Test
    fun `mode matrix matches contract`() {
        val allowed = LogMode.entries.associateWith { mode ->
            LogLevel.entries.filter { level -> LogPolicy.allows(mode, level) }
        }

        assertEquals(emptyList<LogLevel>(), allowed.getValue(LogMode.OFF))
        assertEquals(
            listOf(LogLevel.INFO, LogLevel.WARN, LogLevel.ERROR),
            allowed.getValue(LogMode.BASIC),
        )
        assertEquals(LogLevel.entries, allowed.getValue(LogMode.DEBUG))
    }
}
