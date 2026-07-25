package io.github.togls.hypertweaks.feature.googlephotos.xposed

import io.github.togls.hypertweaks.feature.googlephotos.coordinate.Coordinate
import io.github.togls.hypertweaks.feature.googlephotos.logging.GooglePhotosDiagnosticsPolicy
import io.github.togls.hypertweaks.feature.googlephotos.resolver.ResolveDiagnostic
import io.github.togls.hypertweaks.feature.googlephotos.resolver.ResolveOutcome
import io.github.togls.hypertweaks.feature.googlephotos.resolver.ResolveStage
import io.github.togls.hypertweaks.logging.api.LogContext
import io.github.togls.hypertweaks.logging.api.Logger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GooglePhotosLocationLoggerTest {
    @Test
    fun resolverUsesUnifiedEventsWithSubtargetAndReason() {
        val recordingLogger = RecordingLogger()
        val logger = GooglePhotosLocationLogger(
            recordingLogger,
            GooglePhotosDiagnosticsPolicy.forBuild(debug = true),
        )

        logger.resolveDiagnostic(diagnostic(ResolveStage.STARTED, ResolveOutcome.ATTEMPTED))
        logger.resolveDiagnostic(diagnostic(ResolveStage.COMPLETED, ResolveOutcome.SELECTED))
        logger.resolveDiagnostic(diagnostic(ResolveStage.FAILED, ResolveOutcome.REJECTED))

        assertEquals(
            listOf(
                "target.resolve.started",
                "target.resolve.succeeded",
                "target.resolve.failed",
            ),
            recordingLogger.entries.map(LogEntry::event),
        )
        recordingLogger.entries.forEach { entry ->
            assertEquals("marker", entry.fields["subtarget"])
            assertTrue(entry.fields.containsKey("reason"))
        }
    }

    @Test
    fun markerCallbackUsesEnteredAndTransformedEventsInDebug() {
        val recordingLogger = RecordingLogger()
        val logger = GooglePhotosLocationLogger(
            recordingLogger,
            GooglePhotosDiagnosticsPolicy.forBuild(debug = true),
        )
        val coordinate = Coordinate(22.543096, 114.057865)

        val callCount = logger.markerInvoked("marker", "receiver", null, coordinate)
        logger.markerResult(
            event = "converted",
            callCount = callCount,
            session = null,
            result = MarkerConversionResult(
                MarkerConversionOutcome.CONVERTED,
                "WGS84_TO_GCJ02",
                coordinate,
                Coordinate(22.545, 114.060),
            ),
        )

        assertEquals(
            listOf("hook.callback.entered", "hook.callback.transformed"),
            recordingLogger.entries.map(LogEntry::event),
        )
        recordingLogger.entries.forEach { entry ->
            assertEquals("marker", entry.fields["subtarget"])
            assertTrue(entry.fields.containsKey("reason"))
        }
    }

    @Test
    fun releasePolicySuppressesHighFrequencyMarkerEvents() {
        val recordingLogger = RecordingLogger()
        val logger = GooglePhotosLocationLogger(
            recordingLogger,
            GooglePhotosDiagnosticsPolicy.forBuild(debug = false),
        )

        val callCount = logger.markerInvoked("marker", "receiver", null, null)
        logger.markerResult(
            "skipped",
            callCount,
            null,
            MarkerConversionResult.noPosition(),
        )

        assertTrue(recordingLogger.entries.isEmpty())
    }

    private fun diagnostic(
        stage: ResolveStage,
        outcome: ResolveOutcome,
    ): ResolveDiagnostic {
        return ResolveDiagnostic(
            target = "marker",
            stage = stage,
            outcome = outcome,
            detail = "test",
        )
    }
}

private data class LogEntry(
    val event: String,
    val fields: Map<String, String>,
)

private class RecordingLogger : Logger {
    val entries = mutableListOf<LogEntry>()

    override fun child(tag: String): Logger = this
    override fun withField(key: String, value: Any?): Logger = this
    override fun withFields(fields: Map<String, String>): Logger = this
    override fun withContext(context: LogContext): Logger = this

    override fun debug(
        event: String,
        message: String?,
        throwable: Throwable?,
        fields: Map<String, String>,
    ) = record(event, fields)

    override fun info(
        event: String,
        message: String?,
        throwable: Throwable?,
        fields: Map<String, String>,
    ) = record(event, fields)

    override fun warn(
        event: String,
        message: String?,
        throwable: Throwable?,
        fields: Map<String, String>,
    ) = record(event, fields)

    override fun error(
        event: String,
        message: String?,
        throwable: Throwable?,
        fields: Map<String, String>,
    ) = record(event, fields)

    private fun record(event: String, fields: Map<String, String>) {
        entries += LogEntry(event, fields)
    }
}
