package io.github.togls.hypertweaks.ui.diagnostics

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.github.togls.hypertweaks.R
import io.github.togls.hypertweaks.ui.components.AppButton
import io.github.togls.hypertweaks.ui.components.AppInfoPreference
import io.github.togls.hypertweaks.ui.components.AppPreferenceGroup
import io.github.togls.hypertweaks.ui.components.AppScaffold
import io.github.togls.hypertweaks.ui.components.AppSpacing
import java.text.DateFormat
import java.util.Date

@Composable
fun HookDiagnosticsScreen(
    uiState: HookDiagnosticsUiState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AppScaffold(
        title = stringResource(R.string.hook_diagnostics_title),
        subtitle = stringResource(R.string.hook_diagnostics_subtitle),
        modifier = modifier,
    ) { padding ->
        DiagnosticsContent(uiState, onBack, onRefresh, padding)
    }
}

@Composable
private fun DiagnosticsContent(
    uiState: HookDiagnosticsUiState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    padding: PaddingValues,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(padding)
            .padding(horizontal = AppSpacing.large, vertical = AppSpacing.small),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.large),
    ) {
        DiagnosticActions(onBack, onRefresh)
        FrameworkSection(uiState)
        ScopeSection(uiState.scope)
        RunningTargetsSection(uiState.runningTargets)
        FeatureInstallsSection(uiState.featureInstalls)
        EventSection(stringResource(R.string.hook_diagnostics_resolve_results), uiState.resolveResults)
        EventSection(stringResource(R.string.hook_diagnostics_recent_callbacks), uiState.recentCallbacks)
        FailureSection(uiState.failureStage, uiState.errorMessage)
    }
}

@Composable
private fun DiagnosticActions(
    onBack: () -> Unit,
    onRefresh: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.small),
    ) {
        AppButton(
            text = stringResource(R.string.hook_diagnostics_back),
            onClick = onBack,
            modifier = Modifier.fillMaxWidth(),
        )
        AppButton(
            text = stringResource(R.string.hook_diagnostics_refresh),
            onClick = onRefresh,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun FrameworkSection(uiState: HookDiagnosticsUiState) {
    val framework = uiState.framework
    AppPreferenceGroup(title = stringResource(R.string.hook_diagnostics_framework)) {
        AppInfoPreference(
            title = stringResource(
                if (framework.connected) R.string.xposed_service_connected
                else R.string.xposed_service_disconnected,
            ),
            summary = framework.name?.let { name -> "$name ${framework.version.orEmpty()}".trim() },
        )
        DiagnosticValue(stringResource(R.string.hook_diagnostics_framework_api), framework.apiVersion)
        DiagnosticValue(stringResource(R.string.hook_diagnostics_target_api), framework.targetApiVersion)
        DiagnosticValue(stringResource(R.string.hook_diagnostics_config_version), uiState.configVersion)
        DiagnosticValue(stringResource(R.string.hook_diagnostics_database), uiState.databaseStatus)
    }
}

@Composable
private fun ScopeSection(scope: List<String>) {
    AppPreferenceGroup(title = stringResource(R.string.hook_diagnostics_scope)) {
        if (scope.isEmpty()) {
            EmptyDiagnostic()
        } else {
            scope.forEach { packageName -> AppInfoPreference(title = packageName) }
        }
    }
}

@Composable
private fun RunningTargetsSection(targets: List<RunningTargetDiagnostic>) {
    AppPreferenceGroup(title = stringResource(R.string.hook_diagnostics_running_targets)) {
        if (targets.isEmpty()) {
            EmptyDiagnostic()
        } else {
            targets.forEach { target ->
                AppInfoPreference(
                    title = target.processName,
                    summary = "pid=${target.pid} · ${target.state} · v${target.loadedVersionCode}",
                )
            }
        }
    }
}

@Composable
private fun FeatureInstallsSection(installs: List<FeatureInstallDiagnostic>) {
    AppPreferenceGroup(title = stringResource(R.string.hook_diagnostics_feature_installs)) {
        if (installs.isEmpty()) {
            EmptyDiagnostic()
        } else {
            installs.forEach { install ->
                AppInfoPreference(
                    title = "${install.featureId}: ${install.status}",
                    summary = diagnosticSummary(
                        install.target,
                        install.process,
                        install.timestampMillis,
                        install.detail,
                    ),
                )
            }
        }
    }
}

@Composable
private fun EventSection(
    title: String,
    events: List<HookEventDiagnostic>,
) {
    AppPreferenceGroup(title = title) {
        if (events.isEmpty()) {
            EmptyDiagnostic()
        } else {
            events.forEach { event ->
                AppInfoPreference(
                    title = "${event.component}: ${event.event}",
                    summary = diagnosticSummary(
                        event.target,
                        event.process,
                        event.timestampMillis,
                        event.detail,
                    ),
                )
            }
        }
    }
}

@Composable
private fun FailureSection(
    failure: HookEventDiagnostic?,
    errorMessage: String?,
) {
    AppPreferenceGroup(title = stringResource(R.string.hook_diagnostics_failure_stage)) {
        if (failure == null && errorMessage == null) {
            EmptyDiagnostic()
            return@AppPreferenceGroup
        }
        failure?.let { event ->
            AppInfoPreference(
                title = event.event,
                summary = diagnosticSummary(
                    event.target,
                    event.process,
                    event.timestampMillis,
                    event.detail,
                ),
            )
        }
        errorMessage?.let { message ->
            AppInfoPreference(
                title = stringResource(R.string.hook_diagnostics_read_error),
                summary = message,
            )
        }
    }
}

@Composable
private fun DiagnosticValue(
    title: String,
    value: Any?,
) {
    AppInfoPreference(
        title = title,
        summary = value?.toString() ?: stringResource(R.string.hook_diagnostics_unavailable),
    )
}

@Composable
private fun EmptyDiagnostic() {
    AppInfoPreference(title = stringResource(R.string.hook_diagnostics_empty))
}

private fun diagnosticSummary(
    target: String?,
    process: String?,
    timestampMillis: Long,
    detail: String?,
): String {
    return listOfNotNull(
        target,
        process,
        DateFormat.getDateTimeInstance().format(Date(timestampMillis)),
        detail,
    ).joinToString(" · ")
}
