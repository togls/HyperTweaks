package io.github.togls.hypertweaks.feature.ime.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.togls.hypertweaks.feature.ime.data.NavBarButton

@Composable
fun NavBarButtonSelector(
    title: String,
    description: String,
    selected: NavBarButton,
    enabled: Boolean,
    onSelectedChange: (NavBarButton) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                )

                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(modifier = Modifier.padding(horizontal = 6.dp))

            Column {
                OutlinedButton(
                    enabled = enabled,
                    onClick = { expanded = true },
                ) {
                    Text(text = stringResource(selected.displayNameRes))
                }

                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                ) {
                    NavBarButton.entries.forEach { option ->
                        DropdownMenuItem(
                            text = {
                                Text(text = stringResource(option.displayNameRes))
                            },
                            onClick = {
                                expanded = false
                                onSelectedChange(option)
                            },
                        )
                    }
                }
            }
        }
    }
}