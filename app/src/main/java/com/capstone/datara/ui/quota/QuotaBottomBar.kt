package com.capstone.datara.ui.quota

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.capstone.datara.ui.auth.components.DataraIcons
import com.capstone.datara.ui.components.LightNavigationBarIcons
import com.capstone.datara.ui.theme.DataraBrandNavy
import com.capstone.datara.ui.theme.DataraMintContainer
import com.capstone.datara.ui.theme.DataraOnBrand

/** Docked, full-width navy Material 3 navigation bar; it pads itself for the system navigation bar. */
@Composable
internal fun QuotaBottomBar(historySelected: Boolean, onHome: () -> Unit, onHistory: () -> Unit, onSettings: () -> Unit, settingsSelected: Boolean = false) {
    LightNavigationBarIcons()
    // Navy bar: mint pill behind a navy icon, white label; unselected white at 70% (about 9:1 on navy, WCAG AA)
    run {
        NavigationBar(containerColor = DataraBrandNavy, tonalElevation = 0.dp) {
            listOf(Triple("Home", DataraIcons.Home, onHome), Triple("History", DataraIcons.History, onHistory),
                Triple("Settings", DataraIcons.Settings, onSettings)).forEachIndexed { index, item ->
                NavigationBarItem(selected = index == (if (settingsSelected) 2 else if (historySelected) 1 else 0),
                    onClick = item.third, icon = { Icon(item.second, null, modifier = Modifier.size(23.dp)) },
                    label = { Text(item.first, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    colors = NavigationBarItemDefaults.colors(selectedIconColor = MaterialTheme.colorScheme.primary,
                        selectedTextColor = DataraOnBrand, indicatorColor = DataraMintContainer,
                        unselectedIconColor = DataraOnBrand.copy(alpha = 0.7f), unselectedTextColor = DataraOnBrand.copy(alpha = 0.7f)))
            }
        }
    }
}
