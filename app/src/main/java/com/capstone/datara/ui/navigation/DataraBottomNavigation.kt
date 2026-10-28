package com.capstone.datara.ui.navigation

import androidx.annotation.StringRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.capstone.datara.R
import kotlin.math.cos
import kotlin.math.sin

enum class DataraTopLevelDestination(@StringRes val labelRes: Int) {
    HOME(R.string.nav_home),
    HISTORY(R.string.nav_history),
    SETTINGS(R.string.nav_settings)
}

/**
 * Shared responsive shell for authenticated top-level destinations.
 * Compact widths retain the DATAra floating navigation bar; expanded widths use a rail.
 */
@Composable
fun DataraTopLevelScaffold(
    selected: DataraTopLevelDestination,
    onHomeClick: () -> Unit,
    onHistoryClick: () -> Unit,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable (innerPadding: PaddingValues, expanded: Boolean) -> Unit
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val expanded = maxWidth >= 700.dp
        if (expanded) {
            Row(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                DataraNavigationRail(
                    selected = selected,
                    onHomeClick = onHomeClick,
                    onHistoryClick = onHistoryClick,
                    onSettingsClick = onSettingsClick
                )
                Scaffold(
                    modifier = Modifier.weight(1f),
                    containerColor = MaterialTheme.colorScheme.background
                ) { innerPadding ->
                    content(innerPadding, true)
                }
            }
        } else {
            Scaffold(
                containerColor = MaterialTheme.colorScheme.background,
                bottomBar = {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .background(MaterialTheme.colorScheme.background)
                            .padding(horizontal = 24.dp, vertical = 6.dp)
                    ) {
                        DataraBottomNavigation(
                            selected = selected,
                            onHomeClick = onHomeClick,
                            onHistoryClick = onHistoryClick,
                            onSettingsClick = onSettingsClick
                        )
                    }
                }
            ) { innerPadding ->
                content(innerPadding, false)
            }
        }
    }
}

@Composable
fun DataraBottomNavigation(
    selected: DataraTopLevelDestination,
    onHomeClick: () -> Unit,
    onHistoryClick: () -> Unit,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(60.dp),
        shape = RoundedCornerShape(15.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        shadowElevation = 5.dp
    ) {
        Row(Modifier.fillMaxSize().padding(horizontal = 8.dp)) {
            NavigationItem(DataraTopLevelDestination.HOME, selected, onHomeClick)
            NavigationItem(DataraTopLevelDestination.HISTORY, selected, onHistoryClick)
            NavigationItem(DataraTopLevelDestination.SETTINGS, selected, onSettingsClick)
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.NavigationItem(
    destination: DataraTopLevelDestination,
    selected: DataraTopLevelDestination,
    onClick: () -> Unit
) {
    val isSelected = destination == selected
    val color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    Box(
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
            .clickable(role = Role.Tab, onClick = onClick)
            .semantics { role = Role.Tab },
        contentAlignment = Alignment.Center
    ) {
        if (isSelected) {
            Box(
                Modifier
                    .align(Alignment.TopCenter)
                    .size(width = 48.dp, height = 3.dp)
                    .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(bottomStart = 3.dp, bottomEnd = 3.dp))
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            NavigationGlyph(destination, isSelected)
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(destination.labelRes),
                color = color,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                fontSize = 9.sp,
                lineHeight = 10.sp
            )
        }
    }
}

@Composable
private fun DataraNavigationRail(
    selected: DataraTopLevelDestination,
    onHomeClick: () -> Unit,
    onHistoryClick: () -> Unit,
    onSettingsClick: () -> Unit
) {
    NavigationRail(
        modifier = Modifier
            .fillMaxHeight()
            .width(96.dp)
            .border(0.dp, MaterialTheme.colorScheme.outline),
        containerColor = MaterialTheme.colorScheme.surface,
        header = {
            Box(
                modifier = Modifier
                    .padding(vertical = 18.dp)
                    .size(46.dp)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f), RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    stringResource(R.string.app_name).take(1),
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.titleLarge
                )
            }
        }
    ) {
        RailItem(DataraTopLevelDestination.HOME, selected, onHomeClick)
        RailItem(DataraTopLevelDestination.HISTORY, selected, onHistoryClick)
        RailItem(DataraTopLevelDestination.SETTINGS, selected, onSettingsClick)
    }
}

@Composable
private fun RailItem(
    destination: DataraTopLevelDestination,
    selected: DataraTopLevelDestination,
    onClick: () -> Unit
) {
    val isSelected = destination == selected
    NavigationRailItem(
        modifier = Modifier.semantics { role = Role.Tab },
        selected = isSelected,
        onClick = onClick,
        icon = { NavigationGlyph(destination, isSelected) },
        label = { Text(stringResource(destination.labelRes), style = MaterialTheme.typography.labelSmall) },
        alwaysShowLabel = true,
        colors = NavigationRailItemDefaults.colors(
            selectedIconColor = MaterialTheme.colorScheme.primary,
            selectedTextColor = MaterialTheme.colorScheme.primary,
            indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
        )
    )
}

@Composable
private fun NavigationGlyph(destination: DataraTopLevelDestination, selected: Boolean) {
    val color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    Canvas(modifier = Modifier.size(20.dp)) {
        val stroke = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
        when (destination) {
            DataraTopLevelDestination.HOME -> {
                drawLine(color, Offset(size.width * .18f, size.height * .48f), Offset(size.width * .5f, size.height * .2f), stroke.width, StrokeCap.Round)
                drawLine(color, Offset(size.width * .5f, size.height * .2f), Offset(size.width * .82f, size.height * .48f), stroke.width, StrokeCap.Round)
                drawRect(color, Offset(size.width * .28f, size.height * .45f), Size(size.width * .44f, size.height * .36f), style = stroke)
            }
            DataraTopLevelDestination.HISTORY -> {
                drawCircle(color, radius = size.minDimension * .34f, style = stroke)
                drawLine(color, center, Offset(center.x, size.height * .31f), stroke.width, StrokeCap.Round)
                drawLine(color, center, Offset(size.width * .67f, size.height * .58f), stroke.width, StrokeCap.Round)
            }
            DataraTopLevelDestination.SETTINGS -> {
                drawCircle(color, radius = size.minDimension * .31f, style = stroke)
                drawCircle(color, radius = size.minDimension * .11f, style = stroke)
                repeat(8) { index ->
                    val angle = Math.toRadians((index * 45).toDouble())
                    val start = Offset(
                        center.x + cos(angle).toFloat() * size.width * .34f,
                        center.y + sin(angle).toFloat() * size.height * .34f
                    )
                    val end = Offset(
                        center.x + cos(angle).toFloat() * size.width * .43f,
                        center.y + sin(angle).toFloat() * size.height * .43f
                    )
                    drawLine(color, start, end, stroke.width, StrokeCap.Round)
                }
            }
        }
    }
}
