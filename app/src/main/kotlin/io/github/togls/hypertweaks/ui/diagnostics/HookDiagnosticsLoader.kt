package io.github.togls.hypertweaks.ui.diagnostics

import io.github.libxposed.service.XposedService
import io.github.togls.hypertweaks.core.config.RemotePreferenceKeys
import io.github.togls.hypertweaks.logging.app.AppLogRuntime
import io.github.togls.hypertweaks.logging.app.LogDatabaseState
import io.github.togls.hypertweaks.service.XposedServiceStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal class HookDiagnosticsLoader(
    private val serviceProvider: () -> XposedService? = { XposedServiceStore.service.value },
) {
    suspend fun load(): HookDiagnosticsUiState = withContext(Dispatchers.IO) {
        val serviceResult = loadServiceDiagnostics(serviceProvider())
        val eventResult = loadHookEvents()
        val reducedEvents = HookDiagnosticEventReducer.reduce(eventResult.events)
        HookDiagnosticsUiState(
            loading = false,
            framework = serviceResult.framework,
            scope = serviceResult.scope,
            configVersion = serviceResult.configVersion,
            runningTargets = serviceResult.runningTargets,
            featureInstalls = reducedEvents.featureInstalls,
            resolveResults = reducedEvents.resolveResults,
            recentCallbacks = reducedEvents.recentCallbacks,
            failureStage = reducedEvents.failureStage,
            databaseStatus = eventResult.databaseStatus,
            errorMessage = listOfNotNull(serviceResult.errorMessage, eventResult.errorMessage)
                .joinToString("\n")
                .ifBlank { null },
        )
    }

    private fun loadServiceDiagnostics(service: XposedService?): ServiceDiagnostics {
        if (service == null) return ServiceDiagnostics()
        return runCatching { readConnectedService(service) }
            .getOrElse { error ->
                AppLogRuntime.logger.error(
                    event = "diagnostics.framework.read.failed",
                    throwable = error,
                )
                ServiceDiagnostics(
                    framework = FrameworkDiagnostic(connected = true),
                    errorMessage = error.message ?: error.javaClass.name,
                )
            }
    }

    private fun readConnectedService(service: XposedService): ServiceDiagnostics {
        val apiVersion = service.apiVersion
        val preferences = service.getRemotePreferences(RemotePreferenceKeys.GroupName)
        return ServiceDiagnostics(
            framework = FrameworkDiagnostic(
                connected = true,
                apiVersion = apiVersion,
                targetApiVersion = XposedService.API_102,
                name = service.frameworkName,
                version = service.frameworkVersion,
            ),
            scope = service.scope.sorted(),
            configVersion = preferences.getLong(RemotePreferenceKeys.HookConfigVersion, 0L),
            runningTargets = readRunningTargets(service, apiVersion),
        )
    }

    private fun readRunningTargets(
        service: XposedService,
        apiVersion: Int,
    ): List<RunningTargetDiagnostic> {
        if (apiVersion < XposedService.API_102) return emptyList()
        return service.runningTargets.map { target ->
            RunningTargetDiagnostic(
                processName = target.processName,
                pid = target.pid,
                state = target.state.name.lowercase(),
                loadedVersionCode = target.loadedVersionCode,
            )
        }.sortedBy(RunningTargetDiagnostic::processName)
    }

    private suspend fun loadHookEvents(): EventDiagnostics {
        return when (val databaseState = AppLogRuntime.databaseState.value) {
            is LogDatabaseState.Ready -> readHookEvents(databaseState)
            LogDatabaseState.Initializing -> EventDiagnostics(databaseStatus = "initializing")
            LogDatabaseState.Recovering -> EventDiagnostics(databaseStatus = "recovering")
            is LogDatabaseState.Failed -> EventDiagnostics(
                databaseStatus = "failed",
                errorMessage = databaseState.message,
            )
        }
    }

    private suspend fun readHookEvents(
        databaseState: LogDatabaseState.Ready,
    ): EventDiagnostics {
        return runCatching {
            databaseState.repository.recentHookEvents(
                events = HookDiagnosticEventReducer.EventNames,
                limit = MaximumDiagnosticEvents,
            )
        }.fold(
            onSuccess = { events -> EventDiagnostics(events, "ready") },
            onFailure = { error ->
                AppLogRuntime.logger.error(
                    event = "diagnostics.events.read.failed",
                    throwable = error,
                )
                EventDiagnostics(
                    databaseStatus = "failed",
                    errorMessage = error.message ?: error.javaClass.name,
                )
            },
        )
    }

    private companion object {
        const val MaximumDiagnosticEvents = 250
    }
}

private data class ServiceDiagnostics(
    val framework: FrameworkDiagnostic = FrameworkDiagnostic(),
    val scope: List<String> = emptyList(),
    val configVersion: Long = 0L,
    val runningTargets: List<RunningTargetDiagnostic> = emptyList(),
    val errorMessage: String? = null,
)

private data class EventDiagnostics(
    val events: List<io.github.togls.hypertweaks.logging.api.LogEvent> = emptyList(),
    val databaseStatus: String,
    val errorMessage: String? = null,
)
