package com.capstone.datara.ui.history

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.capstone.datara.ui.quota.*
import com.capstone.datara.ui.theme.QuotaTheme
import com.capstone.datara.ui.components.*
import com.capstone.datara.ui.auth.components.DataraIcons
import com.capstone.datara.ui.notifications.NotificationBell
import androidx.compose.ui.Alignment
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.compose.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianChartModel
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianValueFormatter
import com.patrykandpatrick.vico.compose.cartesian.data.ColumnCartesianLayerModel
import com.patrykandpatrick.vico.compose.cartesian.layer.ColumnCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberColumnCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoScrollState
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoZoomState
import com.patrykandpatrick.vico.compose.common.Fill
import com.patrykandpatrick.vico.compose.common.ProvideVicoTheme
import com.patrykandpatrick.vico.compose.common.vicoTheme
import com.patrykandpatrick.vico.compose.common.component.rememberLineComponent
import java.time.LocalDate

@Composable
fun HistoryScreen(onHome: () -> Unit, onSettings: () -> Unit, modifier: Modifier = Modifier,
    onNotifications: () -> Unit = {}, unreadCount: Int = 0) {
    QuotaTheme {
        ProvideVicoTheme(vicoTheme.copy(lineColor = MaterialTheme.colorScheme.outlineVariant,
            textColor = MaterialTheme.colorScheme.onSurfaceVariant,
            columnCartesianLayerColors = listOf(MaterialTheme.colorScheme.tertiary))) {
            HistoryContent(onHome, onSettings, modifier, onNotifications, unreadCount)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HistoryContent(onHome: () -> Unit, onSettings: () -> Unit, modifier: Modifier,
    onNotifications: () -> Unit, unreadCount: Int) {
    val today = remember { LocalDate.now() }
    val cycles = remember(today) { historyCycles(today) }
    var windowName by rememberSaveable { mutableStateOf(HistoryWindow.WEEK.name) }
    var cycleId by rememberSaveable { mutableStateOf(cycles.first().id) }
    var detailsId by rememberSaveable { mutableStateOf<String?>(null) }
    var empty by rememberSaveable { mutableStateOf(false) }
    var readings by rememberSaveable { mutableStateOf(false) }
    var cycleMenu by remember { mutableStateOf(false) }
    val window = HistoryWindow.valueOf(windowName)
    val cycle = cycles.first { it.id == cycleId }
    val snapshot = remember(window, cycle, today) { historySnapshot(window, cycle, today) }
    val selected = cycles.firstOrNull { it.id == detailsId }
    BackHandler(selected != null) { detailsId = null }
    if (selected != null) {
        QuotaDetailsScreen(selected.state, onBack = { detailsId = null }, onEndCycle = {}, onCreateQuota = {}, readOnly = true)
        return
    }
    Scaffold(modifier = modifier, containerColor = MaterialTheme.colorScheme.background,
        topBar = { DataraAppBar("History", actions = {
            NotificationBell(unreadCount, onNotifications)
            PreviewMenu(listOf((if (empty) "Show sample history" else "Preview empty history") to { empty = !empty }))
        }) }, bottomBar = { QuotaBottomBar(true, onHome, {}, onSettings) }) { insets ->
        Column(Modifier.fillMaxSize().padding(insets).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)) {
            SectionTitle("Usage history", "Review mobile usage over time.")
            if (empty) {
                HistoryCard {
                    TonalIcon(DataraIcons.History, positive = true)
                    Text("No usage history yet", style = MaterialTheme.typography.headlineLarge)
                    Text("Usage trends and completed cycles will appear here once tracking is connected.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    DataraActionButton("Go to Home", onHome)
                }
            } else {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    HistoryWindow.entries.forEachIndexed { index, option ->
                        SegmentedButton(selected = window == option, onClick = { windowName = option.name; readings = false },
                            shape = SegmentedButtonDefaults.itemShape(index, HistoryWindow.entries.size), icon = {}) {
                            Text(option.label, style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
                if (window == HistoryWindow.CYCLE) Box {
                    OutlinedButton(onClick = { cycleMenu = true }, modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium) {
                        Text(cycle.state.name); Spacer(Modifier.width(12.dp)); Icon(DataraIcons.Chevron, null, modifier = Modifier.size(16.dp))
                    }
                    DropdownMenu(cycleMenu, onDismissRequest = { cycleMenu = false }) {
                        cycles.forEach { item -> DropdownMenuItem(text = { Text(item.state.name) }, onClick = { cycleId = item.id; cycleMenu = false }) }
                    }
                }
                Card(shape = MaterialTheme.shapes.large, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.onSurface)) {
                    Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text("MOBILE DATA USED", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                            Icon(DataraIcons.Data, null)
                        }
                        Text(formatQuotaData(snapshot.totalBytes), style = MaterialTheme.typography.displayLarge)
                        Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = MaterialTheme.shapes.medium) {
                            Text("${formatQuotaData(snapshot.averageDailyBytes)} average per day", modifier = Modifier.fillMaxWidth().padding(14.dp), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
                HistoryCard {
                    SectionTitle("Usage trend", if (window == HistoryWindow.DAY) "4-hour totals · MB" else "Daily totals · MB")
                    if (window != HistoryWindow.DAY) Text("${snapshot.points.first().label} – ${snapshot.points.last().label}",
                        style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    UsageChart(snapshot.points, window)
                    snapshot.peak?.let {
                        ListItem(leadingContent = { TonalIcon(DataraIcons.Data, positive = true) },
                            headlineContent = { Text("Peak ${if (window == HistoryWindow.DAY) "hour" else "day"}: ${it.label}") },
                            supportingContent = { Text(formatQuotaData(it.bytes)) })
                    }
                    TextButton(onClick = { readings = true }) {
                        Text("View exact readings"); Spacer(Modifier.width(8.dp)); Icon(DataraIcons.ArrowForward, null, modifier = Modifier.size(16.dp))
                    }
                }
                SectionTitle("Past quota cycles", "Completed sample cycles")
                cycles.forEach { item ->
                    Card(onClick = { detailsId = item.id }, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                        ListItem(leadingContent = { TonalIcon(DataraIcons.History) },
                            headlineContent = { Text(item.state.name, style = MaterialTheme.typography.titleMedium) },
                            supportingContent = { Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(if (item.state.scenario == HomeSampleState.QUOTA_REACHED) "Quota reached" else "Ended early")
                                Text("${formatQuotaData(item.state.usedBytes)} of ${formatQuotaData(item.state.amountBytes)} used")
                            } }, trailingContent = { Icon(DataraIcons.Chevron, null, modifier = Modifier.size(18.dp)) },
                            modifier = Modifier.padding(vertical = 6.dp))
                    }
                }
            }
            SampleLabel("Illustrative history")
        }
    }
    if (readings) ModalBottomSheet(onDismissRequest = { readings = false }, containerColor = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(0.8f).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionTitle("Exact readings", if (window == HistoryWindow.DAY) "Hourly sample usage" else "Daily sample usage")
            snapshot.points.forEach { point -> ListItem(headlineContent = { Text(point.label) }, trailingContent = { Text(formatQuotaData(point.bytes)) }) }
            TextButton(onClick = { readings = false }) { Text("Close") }
            Spacer(Modifier.navigationBarsPadding())
        }
    }
}

@Composable
private fun UsageChart(points: List<UsagePoint>, window: HistoryWindow) {
    val displayed = remember(points, window) {
        if (window == HistoryWindow.DAY) points.chunked(4).map { group -> UsagePoint(group.first().label, group.sumOf { it.bytes }) }
        else points
    }
    val model = remember(displayed) { CartesianChartModel(ColumnCartesianLayerModel.build { series(displayed.map { it.bytes / 1_000_000.0 }) }) }
    CartesianChartHost(
        chart = rememberCartesianChart(
            rememberColumnCartesianLayer(columnProvider = ColumnCartesianLayer.ColumnProvider.series(
                rememberLineComponent(fill = Fill(MaterialTheme.colorScheme.tertiary), thickness = 12.dp))),
            startAxis = VerticalAxis.rememberStart(valueFormatter = CartesianValueFormatter.decimal(suffix = " MB")),
            bottomAxis = HorizontalAxis.rememberBottom(valueFormatter = CartesianValueFormatter { _, value, _ ->
                displayed.getOrNull(value.toInt())?.label?.let {
                    if (window == HistoryWindow.DAY) it.replace(":00", "h") else it.substringAfterLast(" ")
                } ?: ""
            })),
        model = model,
        modifier = Modifier.fillMaxWidth().height(240.dp).semantics { contentDescription = "Mobile usage chart in MB. Exact readings are available below." },
        scrollState = rememberVicoScrollState(scrollEnabled = false), zoomState = rememberVicoZoomState(zoomEnabled = false))
}

@Composable
private fun HistoryCard(content: @Composable ColumnScope.() -> Unit) {
    Card(shape = MaterialTheme.shapes.large, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp), content = content)
    }
}

@Preview(widthDp = 360, heightDp = 800)
@Preview(name = "History large text", widthDp = 360, heightDp = 800, fontScale = 2f)
@Preview(name = "History landscape", widthDp = 800, heightDp = 360)
@Composable
private fun HistoryPreview() { HistoryScreen({}, {}) }
