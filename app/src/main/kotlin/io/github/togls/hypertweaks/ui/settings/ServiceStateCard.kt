package io.github.togls.hypertweaks.ui.settings

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.github.togls.hypertweaks.R
import io.github.togls.hypertweaks.ui.components.AppButton
import io.github.togls.hypertweaks.ui.components.AppInfoPreference
import io.github.togls.hypertweaks.ui.components.AppPreferenceGroup
import io.github.togls.hypertweaks.ui.components.AppSpacing
import io.github.togls.hypertweaks.ui.components.AppSwitchPreference

@Composable
fun ServiceStateCard(
    serviceConnected: Boolean,
    message: String,
    systemServerFeaturesEnabled: Boolean,
    onSystemServerFeaturesEnabledChange: (Boolean) -> Unit,
    onReloadClick: () -> Unit,
    modifier: Modifier = Modifier,
    showDebugInfo: Boolean = false,
) {
    val statusTitle = if (serviceConnected) {
        stringResource(R.string.xposed_service_connected)
    } else {
        stringResource(R.string.xposed_service_disconnected)
    }
    val statusSummary = message.ifBlank {
        stringResource(R.string.status_waiting_service)
    }

    AppPreferenceGroup(modifier = modifier.fillMaxWidth()) {
        AppInfoPreference(
            title = statusTitle,
            summary = statusSummary.takeIf { showDebugInfo },
        )
        AppSwitchPreference(
            title = stringResource(R.string.system_server_features_title),
            summary = stringResource(R.string.system_server_features_summary),
            checked = systemServerFeaturesEnabled,
            enabled = serviceConnected,
            onCheckedChange = onSystemServerFeaturesEnabledChange,
        )
        if (showDebugInfo) {
            AppButton(
                text = stringResource(R.string.action_reload_config),
                onClick = onReloadClick,
                modifier = Modifier.padding(
                    start = AppSpacing.large,
                    end = AppSpacing.large,
                    bottom = AppSpacing.large,
                ),
            )
        }
    }
}
