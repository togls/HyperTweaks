package io.github.togls.hypertweaks.feature.ime.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.github.togls.hypertweaks.feature.ime.data.NavBarButton
import io.github.togls.hypertweaks.ui.components.AppDropdownPreference

@Composable
fun NavBarButtonSelector(
    title: String,
    description: String,
    selected: NavBarButton,
    enabled: Boolean,
    onSelectedChange: (NavBarButton) -> Unit,
    modifier: Modifier = Modifier,
) {
    AppDropdownPreference(
        title = title,
        summary = description,
        options = NavBarButton.entries,
        selected = selected,
        optionLabel = { stringResource(it.displayNameRes) },
        enabled = enabled,
        onSelectedChange = onSelectedChange,
        modifier = modifier,
    )
}
