package io.github.togls.hypertweaks.ui.diagnostics

import io.github.togls.hypertweaks.logging.api.LogEvent

data class HookDiagnosticsUiState(
    val loading: Boolean = true,
    val framework: FrameworkDiagnostic = FrameworkDiagnostic(),
    val scope: List<String> = emptyList(),
    val configVersion: Long = 0L,
    val runningTargets: List<RunningTargetDiagnostic> = emptyList(),
    val featureInstalls: List<FeatureInstallDiagnostic> = emptyList(),
    val resolveResults: List<HookEventDiagnostic> = emptyList(),
    val recentCallbacks: List<HookEventDiagnostic> = emptyList(),
    val failureStage: HookEventDiagnostic? = null,
    val databaseStatus: String = "initializing",
    val errorMessage: String? = null,
)

data class FrameworkDiagnostic(
    val connected: Boolean = false,
    val apiVersion: Int? = null,
    val targetApiVersion: Int = 102,
    val name: String? = null,
    val version: String? = null,
)

data class RunningTargetDiagnostic(
    val processName: String,
    val pid: Int,
    val state: String,
    val loadedVersionCode: Long,
)

data class FeatureInstallDiagnostic(
    val featureId: String,
    val target: String?,
    val process: String?,
    val status: String,
    val timestampMillis: Long,
    val detail: String?,
)

data class HookEventDiagnostic(
    val component: String,
    val target: String?,
    val process: String?,
    val event: String,
    val timestampMillis: Long,
    val detail: String?,
)

internal object HookDiagnosticEventReducer {
    val EventNames = setOf(
        "target.resolve.started",
        "target.resolve.succeeded",
        "target.resolve.failed",
        "hook.install.started",
        "hook.install.succeeded",
        "hook.install.failed",
        "hook.install.skipped",
        "hook.callback.entered",
        "hook.callback.transformed",
        "hook.callback.bypassed",
        "hook.callback.failed",
        "config.snapshot.unavailable",
    )

    fun reduce(events: List<LogEvent>): ReducedHookDiagnostics {
        val orderedEvents = events.sortedByDescending(LogEvent::timestampMillis)
        return ReducedHookDiagnostics(
            featureInstalls = latestFeatureInstalls(orderedEvents),
            resolveResults = orderedEvents.filterEventPrefix("target.resolve.").take(MaximumRows)
                .map(::toDiagnostic)
                .toList(),
            recentCallbacks = orderedEvents.filterEventPrefix("hook.callback.").take(MaximumRows)
                .map(::toDiagnostic)
                .toList(),
            failureStage = orderedEvents.firstOrNull(::isFailure)?.let(::toDiagnostic),
        )
    }

    private fun latestFeatureInstalls(events: List<LogEvent>): List<FeatureInstallDiagnostic> {
        return events.asSequence()
            .filter { event -> event.event.startsWith("hook.install.") }
            .distinctBy(::featureInstallIdentity)
            .take(MaximumRows)
            .map(::toFeatureInstall)
            .toList()
    }

    private fun List<LogEvent>.filterEventPrefix(prefix: String): Sequence<LogEvent> {
        return asSequence().filter { event -> event.event.startsWith(prefix) }
    }

    private fun featureInstallIdentity(event: LogEvent): String {
        return listOf(featureId(event), event.packageName, event.processName).joinToString("|")
    }

    private fun toFeatureInstall(event: LogEvent): FeatureInstallDiagnostic {
        return FeatureInstallDiagnostic(
            featureId = featureId(event),
            target = event.packageName ?: event.fields["target"],
            process = event.processName ?: event.fields["process"],
            status = event.event.substringAfterLast('.'),
            timestampMillis = event.timestampMillis,
            detail = eventDetail(event),
        )
    }

    private fun toDiagnostic(event: LogEvent): HookEventDiagnostic {
        return HookEventDiagnostic(
            component = featureId(event),
            target = event.packageName ?: event.fields["target"],
            process = event.processName ?: event.fields["process"],
            event = event.event,
            timestampMillis = event.timestampMillis,
            detail = eventDetail(event),
        )
    }

    private fun featureId(event: LogEvent): String {
        return event.fields["feature"] ?: event.tag
    }

    private fun eventDetail(event: LogEvent): String? {
        return event.message
            ?: event.fields["reason"]
            ?: event.fields["detail"]
            ?: event.throwableType
    }

    private fun isFailure(event: LogEvent): Boolean {
        return event.event.endsWith(".failed") || event.event == "config.snapshot.unavailable"
    }

    private const val MaximumRows = 20
}

internal data class ReducedHookDiagnostics(
    val featureInstalls: List<FeatureInstallDiagnostic>,
    val resolveResults: List<HookEventDiagnostic>,
    val recentCallbacks: List<HookEventDiagnostic>,
    val failureStage: HookEventDiagnostic?,
)
