package com.capstone.datara.ui.quota

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.capstone.datara.ui.auth.components.DataraIcons

@Composable
internal fun QuotaBottomBar(historySelected: Boolean, onHome: () -> Unit, onHistory: () -> Unit, onSettings: () -> Unit, settingsSelected: Boolean = false) {
    Surface(Modifier.navigationBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth(),
        shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surface, shadowElevation = 2.dp) {
        NavigationBar(containerColor = MaterialTheme.colorScheme.surface, tonalElevation = 0.dp, windowInsets = WindowInsets(0,0,0,0)) {
            listOf(Triple("Home", DataraIcons.Home, onHome), Triple("History", DataraIcons.History, onHistory),
                Triple("Settings", DataraIcons.Settings, onSettings)).forEachIndexed { index, item ->
                NavigationBarItem(selected = index == (if (settingsSelected) 2 else if (historySelected) 1 else 0),
                    onClick = item.third, icon = { Icon(item.second, null, modifier = Modifier.size(23.dp)) },
                    label = { Text(item.first, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    colors = NavigationBarItemDefaults.colors(selectedIconColor = MaterialTheme.colorScheme.primary,
                        selectedTextColor = MaterialTheme.colorScheme.primary, indicatorColor = MaterialTheme.colorScheme.tertiaryContainer))
            }
        }
    }
}
