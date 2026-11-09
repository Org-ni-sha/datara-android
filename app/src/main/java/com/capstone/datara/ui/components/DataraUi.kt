package com.capstone.datara.ui.components

import android.app.Activity
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.capstone.datara.ui.auth.components.DataraIcons
import androidx.core.view.WindowCompat
import com.capstone.datara.R

/** Shared native Material 3 building blocks for the signed-in experience. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DataraAppBar(title: String, onBack: (() -> Unit)? = null, subtitle: String? = null, showLogo: Boolean = false,
    actions: @Composable RowScope.() -> Unit = {}) {
    // Light bar on the app background: navy text, dark status-bar icons (set by QuotaTheme)
    TopAppBar(title = {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (showLogo) Image(painterResource(R.drawable.datara_brand_symbol), null, Modifier.size(28.dp), contentScale = ContentScale.Fit)
            Column(Modifier.semantics(mergeDescendants = true) { heading() }) {
                Text(title, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, maxLines = 1,
                    overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    },
        navigationIcon = { if (onBack != null) IconButton(onClick = onBack) { Icon(DataraIcons.ArrowBack, "Back") } },
        actions = actions,
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background,
            titleContentColor = MaterialTheme.colorScheme.primary, navigationIconContentColor = MaterialTheme.colorScheme.primary,
            actionIconContentColor = MaterialTheme.colorScheme.primary))
}

/** Light navigation-bar icons while a navy bottom bar is on screen; restores the previous setting after. */
@Composable
fun LightNavigationBarIcons() {
    val view = LocalView.current
    if (view.isInEditMode) return
    DisposableEffect(view) {
        val window = (view.context as? Activity)?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        val old = controller?.isAppearanceLightNavigationBars
        controller?.isAppearanceLightNavigationBars = false
        onDispose { if (old != null) controller.isAppearanceLightNavigationBars = old }
    }
}

/** A small status chip: allowed (teal), needed (error) or recommended (soft blue). */
@Composable
fun StatusPill(text: String, tone: StatusTone, modifier: Modifier = Modifier) {
    val (container, content) = when (tone) {
        StatusTone.GOOD -> MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
        StatusTone.PROBLEM -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
        StatusTone.NEUTRAL -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
    }
    Surface(modifier, color = container, contentColor = content, shape = MaterialTheme.shapes.extraSmall) {
        Text(text, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp))
    }
}

enum class StatusTone { GOOD, PROBLEM, NEUTRAL }

@Composable
fun PreviewMenu(options: List<Pair<String, () -> Unit>>) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) { Icon(DataraIcons.More, "Preview options") }
        DropdownMenu(expanded, onDismissRequest = { expanded = false }) {
            Text("REVIEW ONLY · SAMPLE SCREENS", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
            options.forEach { (label, action) -> DropdownMenuItem(text = { Text(label) }, onClick = { expanded = false; action() }) }
        }
    }
}

@Composable
fun SampleLabel(text: String = "Sample data") {
    Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(8.dp)) {
        Text(text, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp))
    }
}

@Composable
fun TonalIcon(icon: ImageVector, positive: Boolean = false, modifier: Modifier = Modifier) {
    Surface(modifier.size(42.dp), shape = RoundedCornerShape(14.dp),
        color = if (positive) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.primaryContainer,
        contentColor = if (positive) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onPrimaryContainer) {
        Box(contentAlignment = Alignment.Center) { Icon(icon, null, modifier = Modifier.size(21.dp)) }
    }
}

@Composable
fun DataraActionButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Button(onClick = onClick, modifier = modifier.fillMaxWidth().heightIn(min = 56.dp), enabled = enabled,
        shape = RoundedCornerShape(16.dp), contentPadding = PaddingValues(horizontal = 24.dp, vertical = 16.dp)) {
        Text(text, style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.width(12.dp))
        Icon(DataraIcons.ArrowForward, null, modifier = Modifier.size(20.dp))
    }
}

@Composable
fun SectionTitle(title: String, subtitle: String? = null, action: (@Composable () -> Unit)? = null) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        action?.invoke()
    }
}
