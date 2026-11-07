package com.capstone.datara.ui.quota

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.capstone.datara.ui.auth.components.DataraIcons
import com.capstone.datara.ui.theme.DataraMintContainer

/** Docked, full-width Material 3 navigation bar; it pads itself for the system navigation bar. */
@Composable
internal fun QuotaBottomBar(historySelected: Boolean, onHome: () -> Unit, onHistory: () -> Unit, onSettings: () -> Unit, settingsSelected: Boolean = false) {
    Column {
        HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)
        NavigationBar(containerColor = MaterialTheme.colorScheme.surface, tonalElevation = 0.dp) {
            listOf(Triple("Home", DataraIcons.Home, onHome), Triple("History", DataraIcons.History, onHistory),
                Triple("Settings", DataraIcons.Settings, onSettings)).forEachIndexed { index, item ->
                NavigationBarItem(selected = index == (if (settingsSelected) 2 else if (historySelected) 1 else 0),
                    onClick = item.third, icon = { Icon(item.second, null, modifier = Modifier.size(23.dp)) },
                    label = { Text(item.first, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    colors = NavigationBarItemDefaults.colors(selectedIconColor = MaterialTheme.colorScheme.primary,
                        selectedTextColor = MaterialTheme.colorScheme.primary, indicatorColor = DataraMintContainer,
                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant, unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant))
            }
        }
    }
}
