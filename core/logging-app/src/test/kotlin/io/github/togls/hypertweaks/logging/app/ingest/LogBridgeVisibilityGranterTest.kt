package io.github.togls.hypertweaks.logging.app.ingest

import org.junit.Assert.assertEquals
import org.junit.Test

class LogBridgeVisibilityGranterTest {
    @Test
    fun `grants only installed packages and reports isolated failures`() {
        val attemptedPackages = mutableListOf<String>()

        val summary = grantInstalledPackages(
            packages = linkedSetOf("installed", "missing", "query-failing", "failing"),
            isInstalled = { packageName ->
                if (packageName == "query-failing") throw SecurityException("query failed")
                packageName != "missing"
            },
            grant = { packageName ->
                attemptedPackages += packageName
                if (packageName == "failing") error("grant failed")
            },
        )

        assertEquals(listOf("installed", "failing"), attemptedPackages)
        assertEquals(4, summary.requestedPackageCount)
        assertEquals(2, summary.installedPackageCount)
        assertEquals(setOf("installed"), summary.grantedPackages)
        assertEquals(
            mapOf(
                "query-failing" to SecurityException::class.java.name,
                "failing" to IllegalStateException::class.java.name,
            ),
            summary.failures,
        )
    }
}
