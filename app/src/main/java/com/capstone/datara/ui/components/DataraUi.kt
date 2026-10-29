package com.capstone.datara.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.capstone.datara.ui.auth.components.DataraIcons
import com.capstone.datara.ui.auth.components.DataraLogo

/** Shared native Material 3 building blocks for the signed-in experience. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DataraAppBar(title: String, onBack: (() -> Unit)? = null, actions: @Composable RowScope.() -> Unit = {}) {
    TopAppBar(title = {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (title == "DATAra") {
                DataraLogo(size = 28.dp, contentDescription = null)
                Spacer(Modifier.width(4.dp))
            }
            Text(title, style = MaterialTheme.typography.titleLarge, maxLines = 1,
                overflow = TextOverflow.Ellipsis, modifier = Modifier.semantics { heading() })
        }
    },
        navigationIcon = { if (onBack != null) IconButton(onClick = onBack) { Icon(DataraIcons.ArrowBack, "Back") } },
        actions = actions,
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background))
}

@Composable
fun PreviewMenu(options: List<Pair<String, () -> Unit>>) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) { Icon(DataraIcons.More, "Preview options") }
        DropdownMenu(expanded, onDismissRequest = { expanded = false }) {
            Text("UI PREVIEW", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
            options.forEach { (label, action) -> DropdownMenuItem(text = { Text(label) }, onClick = { expanded = false; action() }) }
        }
    }
}

@Composable
fun SampleLabel(text: String = "Sample data") {
    Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = RoundedCornerShape(8.dp)) {
        Text(text, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp))
    }
}

@Composable
fun TonalIcon(icon: ImageVector, positive: Boolean = false, modifier: Modifier = Modifier) {
    Surface(modifier.size(42.dp), shape = RoundedCornerShape(14.dp),
        color = if (positive) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.secondaryContainer,
        contentColor = if (positive) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onSecondaryContainer) {
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
