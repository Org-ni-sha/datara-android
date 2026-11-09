package com.capstone.datara.ui.history

import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.semantics.selected
import kotlinx.coroutines.launch
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.input.nestedscroll.nestedScroll
import kotlinx.coroutines.delay
import com.patrykandpatrick.vico.compose.cartesian.decoration.HorizontalLine
import com.patrykandpatrick.vico.compose.cartesian.marker.DefaultCartesianMarker
import com.patrykandpatrick.vico.compose.cartesian.marker.rememberDefaultCartesianMarker
import com.patrykandpatrick.vico.compose.common.DashedShape
import com.patrykandpatrick.vico.compose.common.Insets
import com.patrykandpatrick.vico.compose.common.component.rememberShapeComponent
import com.patrykandpatrick.vico.compose.common.component.rememberTextComponent
import com.patrykandpatrick.vico.compose.common.data.ExtraStore
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.capstone.datara.ui.auth.components.DataraIcons
import com.capstone.datara.ui.components.*
import com.capstone.datara.ui.notifications.NotificationBell
import com.capstone.datara.ui.quota.*
import com.capstone.datara.ui.theme.ChartToday
import com.capstone.datara.ui.theme.QuotaTheme
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.compose.cartesian.data.columnModel
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.compose.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianLayerRangeProvider
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianValueFormatter
import com.patrykandpatrick.vico.compose.cartesian.data.ColumnCartesianLayerModel
import com.patrykandpatrick.vico.compose.cartesian.layer.ColumnCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberColumnCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoScrollState
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoZoomState
import com.patrykandpatrick.vico.compose.common.Fill
import com.patrykandpatrick.vico.compose.common.ProvideVicoTheme
import com.patrykandpatrick.vico.compose.common.component.rememberLineComponent
import com.patrykandpatrick.vico.compose.common.vicoTheme
import java.time.ZoneId

/** History over durable data (D7); see [HistoryViewModel]. */
@Composable
fun HistoryScreen(onHome: () -> Unit, onSettings: () -> Unit, modifier: Modifier = Modifier,
    onNotifications: () -> Unit = {}, unreadCount: Int = 0, viewModel: HistoryViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()
    HistoryScreen(state, onHome, onSettings, modifier, onNotifications, unreadCount, onRefresh = viewModel::refresh)
}

@Composable
fun HistoryScreen(state: HistoryUiState, onHome: () -> Unit, onSettings: () -> Unit, modifier: Modifier = Modifier,
    onNotifications: () -> Unit = {}, unreadCount: Int = 0, zone: ZoneId = ZoneId.systemDefault(), onRefresh: () -> Unit = {}) {
    QuotaTheme {
        ProvideVicoTheme(vicoTheme.copy(lineColor = MaterialTheme.colorScheme.outlineVariant,
            textColor = MaterialTheme.colorScheme.onSurfaceVariant,
            columnCartesianLayerColors = listOf(MaterialTheme.colorScheme.secondary))) {
            HistoryContent(state, onHome, onSettings, modifier, onNotifications, unreadCount, zone, onRefresh)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HistoryContent(state: HistoryUiState, onHome: () -> Unit, onSettings: () -> Unit, modifier: Modifier,
    onNotifications: () -> Unit, unreadCount: Int, zone: ZoneId, onRefresh: () -> Unit) {
    val data = (state as? HistoryUiState.Ready)?.data
    // Two tabs: Usage (24 hours / 7 days) and Quota cycles (one cycle's chart plus every cycle)
    var cyclesTab by rememberSaveable { mutableStateOf(false) }
    var windowName by rememberSaveable { mutableStateOf(HistoryWindow.WEEK.name) }
    var cycleId by rememberSaveable { mutableStateOf<String?>(null) }
    var detailsId by rememberSaveable { mutableStateOf<String?>(null) }
    var readings by rememberSaveable { mutableStateOf(false) }
    val scroll = rememberScrollState()
    val scope = rememberCoroutineScope()
    val window = if (cyclesTab) HistoryWindow.CYCLE else HistoryWindow.valueOf(windowName).takeIf { it != HistoryWindow.CYCLE } ?: HistoryWindow.WEEK
    val cycle = data?.cycles?.let { list -> list.firstOrNull { it.cycle.cycleId == cycleId } ?: list.firstOrNull() }
    val snapshot = remember(window, data, cycle, zone) { data?.let { HistoryAggregation.snapshot(window, it, cycle, zone) } }
    val selected = data?.cycles?.firstOrNull { it.cycle.cycleId == detailsId }
    BackHandler(selected != null) { detailsId = null }
    if (selected != null) {
        QuotaDetailsScreen(selected.toDetails(), onBack = { detailsId = null }, onEndCycle = {}, onCreateQuota = {},
            readOnly = true, readOnlyNote = "From History · Read-only details")
        return
    }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    var refreshing by remember { mutableStateOf(false) }
    LaunchedEffect(refreshing) { if (refreshing) { delay(700); refreshing = false } }
    Scaffold(modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection), containerColor = MaterialTheme.colorScheme.background,
        topBar = { DataraCollapsingAppBar("History", scrollBehavior, actions = { NotificationBell(unreadCount, onNotifications) }) },
        bottomBar = { QuotaBottomBar(true, onHome, {}, onSettings) }) { insets ->
        PullToRefreshBox(isRefreshing = refreshing, onRefresh = { refreshing = true; onRefresh() }, modifier = Modifier.fillMaxSize().padding(insets)) {
        Column(Modifier.fillMaxSize().verticalScroll(scroll).padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Text("Mobile data measured on this phone.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            when {
                data == null -> Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(Modifier.semantics { contentDescription = "Loading history" })
                }
                data.isEmpty -> HistoryCard {
                    TonalIcon(DataraIcons.History, positive = true)
                    Text("No usage history yet", style = MaterialTheme.typography.headlineLarge)
                    Text("History fills in from tracked mobile usage, one hour at a time. Make sure tracking is on, then check back in an hour.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    DataraActionButton("Go to Home", onHome)
                }
                else -> {
                    if (data.measuredHours < 24) CollectingNote(data.measuredHours)
                    PrimaryTabRow(selectedTabIndex = if (cyclesTab) 1 else 0, containerColor = MaterialTheme.colorScheme.background) {
                        Tab(selected = !cyclesTab, onClick = { cyclesTab = false; readings = false }, modifier = Modifier.heightIn(min = 48.dp),
                            text = { Text("Usage", style = MaterialTheme.typography.titleSmall) })
                        Tab(selected = cyclesTab, onClick = { cyclesTab = true; readings = false }, modifier = Modifier.heightIn(min = 48.dp),
                            text = { Text("Quota cycles", style = MaterialTheme.typography.titleSmall) })
                    }
                    if (!cyclesTab) {
                        // Two equal halves across the width (Material 3 segmented button)
                        val ranges = listOf(HistoryWindow.DAY, HistoryWindow.WEEK)
                        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                            ranges.forEachIndexed { index, option ->
                                SegmentedButton(selected = window == option, onClick = { windowName = option.name; readings = false },
                                    shape = SegmentedButtonDefaults.itemShape(index, ranges.size),
                                    modifier = Modifier.heightIn(min = 48.dp),
                                    colors = SegmentedButtonDefaults.colors(activeContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                                        activeContentColor = MaterialTheme.colorScheme.onSecondaryContainer)) {
                                    Text(option.label, style = MaterialTheme.typography.labelLarge, maxLines = 1)
                                }
                            }
                        }
                        if (snapshot != null) {
                            TotalCard(snapshot, window)
                            TrendCard(snapshot, window, highlightLast = true) { readings = true }
                        }
                    } else if (cycle == null) HistoryCard {
                        Text("No quota yet", style = MaterialTheme.typography.titleMedium)
                        Text("Set a quota on Home. Each cycle, with its daily usage and how the forecast did, will show here.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        CycleSummary(cycle) { detailsId = cycle.cycle.cycleId }
                        if (snapshot != null) TrendCard(snapshot, window, highlightLast = cycle.cycle.endedAt == null) { readings = true }
                        SectionTitle("All cycles", if (data.cycles.size > 1) "Tap a cycle to show it above" else "Your cycles will collect here")
                        data.cycles.forEach { item ->
                            CycleCard(item, selected = item.cycle.cycleId == cycle.cycle.cycleId) {
                                cycleId = item.cycle.cycleId
                                scope.launch { scroll.animateScrollTo(0) }
                            }
                        }
                    }
                }
            }
        }
        }
    }
    if (readings && snapshot != null) ModalBottomSheet(onDismissRequest = { readings = false }, containerColor = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(0.8f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionTitle("Exact readings", if (window == HistoryWindow.DAY) "Hourly mobile usage" else "Daily mobile usage")
            snapshot.points.forEach { point ->
                ListItem(headlineContent = { Text(point.label) },
                    supportingContent = if (point.isPartial) ({ Text("${point.measuredHours} of ${point.totalHours} hours measured") }) else null,
                    trailingContent = { Text(point.bytes?.let(::formatQuotaData) ?: "Not measured",
                        color = if (point.isMeasured) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant) })
            }
            TextButton(onClick = { readings = false }) { Text("Close") }
            Spacer(Modifier.navigationBarsPadding())
        }
    }
}

@Composable
private fun CollectingNote(hours: Int) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = MaterialTheme.shapes.medium) {
        Text(if (hours == 0) "Collecting: no complete hour measured yet. Charts fill in as tracking runs."
            else "Collecting: $hours ${if (hours == 1) "hour" else "hours"} measured so far. Charts fill in as tracking runs.",
            modifier = Modifier.fillMaxWidth().padding(14.dp), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun TotalCard(snapshot: HistorySnapshot, window: HistoryWindow) {
    Card(shape = MaterialTheme.shapes.large, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer)) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("MOBILE DATA USED", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                Icon(DataraIcons.Data, null)
            }
            Text(formatQuotaData(snapshot.totalBytes), style = MaterialTheme.typography.displayLarge)
            if (snapshot.isPartial) Text("Measured ${snapshot.measuredHours} of ${snapshot.totalHours} hours. Totals count measured hours only.",
                style = MaterialTheme.typography.bodyMedium)
            if (window != HistoryWindow.DAY && snapshot.hasData) Surface(color = MaterialTheme.colorScheme.surfaceContainerLowest, shape = MaterialTheme.shapes.medium) {
                Text("${formatQuotaData(snapshot.averageDailyBytes)} average per measured day", modifier = Modifier.fillMaxWidth().padding(14.dp),
                    style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun TrendCard(snapshot: HistorySnapshot, window: HistoryWindow, highlightLast: Boolean, onReadings: () -> Unit) {
    val unit = remember(snapshot) { chartUnit(snapshot.points) }
    HistoryCard {
        SectionTitle("Usage trend", "${if (window == HistoryWindow.DAY) "Hourly" else "Daily"} totals · ${unit.name}")
        if (snapshot.points.isNotEmpty() && window != HistoryWindow.DAY) Text("${snapshot.points.first().label} – ${snapshot.points.last().label}",
            style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (snapshot.hasData) {
            UsageChart(snapshot.points, window, unit, highlightLast,
                average = if (window != HistoryWindow.DAY) snapshot.averageDailyBytes.toDouble() / unit.bytes else null)
            ChartLegend(window, highlightLast)
            if (snapshot.points.any { !it.isMeasured }) Text("Blank spaces are periods that were not measured, not zero usage.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else Text("No measured usage in this period yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        snapshot.peak?.let {
            ListItem(leadingContent = { TonalIcon(DataraIcons.Data, positive = true) },
                headlineContent = { Text("Peak ${if (window == HistoryWindow.DAY) "hour" else "day"}: ${it.label}") },
                supportingContent = { Text(formatQuotaData(it.bytes!!)) },
                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow))
        }
        if (snapshot.points.isNotEmpty()) TextButton(onClick = onReadings) {
            Text("View exact readings"); Spacer(Modifier.width(8.dp)); Icon(DataraIcons.ArrowForward, null, modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
private fun CycleSummary(item: CycleHistory, onDetails: () -> Unit) {
    val details = item.toDetails()
    val tone = item.tone()
    HistoryCard {
        QuotaStatusChip(tone, text = item.statusLabel)
        Text("${formatQuotaData(item.usage.usedBytes)} of ${formatQuotaData(item.cycle.quotaBytes)}", style = MaterialTheme.typography.headlineMedium)
        LinearProgressIndicator(progress = { details.progress.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth().height(8.dp),
            color = tone.accent(), trackColor = tone.colors().container, drawStopIndicator = {})
        Text("${formatCycleDate(item.cycle.startAt)} – ${item.cycle.endedAt?.let(::formatCycleDate) ?: "now"}",
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (item.usage.isIncomplete) Text("Some time in this cycle was not measured, so usage may be higher.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        item.pet?.let { PetComparisonText(it) }
        if (item.cycle.reachedAt != null && item.pet == null) Text("No forecast was logged before the quota was reached.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        TextButton(onClick = onDetails, contentPadding = PaddingValues(horizontal = 0.dp)) {
            Text("View cycle details"); Spacer(Modifier.width(8.dp)); Icon(DataraIcons.ArrowForward, null, modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
private fun PetComparisonText(pet: PetComparison) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = MaterialTheme.shapes.medium) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Forecast vs actual", style = MaterialTheme.typography.titleSmall)
            Text("Predicted on ${formatCycleDate(pet.predictedAt)}: reach ${formatCycleDate(pet.predictedReachAt)}", style = MaterialTheme.typography.bodyMedium)
            Text("Reached ${formatCycleDate(pet.reachedAt)}", style = MaterialTheme.typography.bodyMedium)
            Text(petErrorText(pet.errorMillis), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.secondary)
        }
    }
}

/**
 * The chart's unit: GB once any reading reaches 1 GB, matching formatQuotaData, so the axis and
 * the totals above it use the same unit.
 */
internal fun chartUnit(points: List<UsagePoint>): QuotaUnit =
    if (points.any { (it.bytes ?: 0L) >= QuotaUnit.GB.bytes }) QuotaUnit.GB else QuotaUnit.MB

internal fun petErrorText(errorMillis: Long): String = when {
    kotlin.math.abs(errorMillis) < 60_000 -> "On time"
    errorMillis > 0 -> "${formatDuration(errorMillis)} later than predicted"
    else -> "${formatDuration(-errorMillis)} earlier than predicted"
}

/** The cycle's shared status colour (same rule as the Home ring and Quota Details). */
internal fun CycleHistory.tone(): QuotaTone = quotaStatusTone(
    if (cycle.quotaBytes > 0) usage.usedBytes.toFloat() / cycle.quotaBytes else 0f, cycle.status)

@Composable
private fun CycleCard(item: CycleHistory, selected: Boolean, onClick: () -> Unit) {
    val tone = item.tone()
    val progress = if (item.cycle.quotaBytes > 0) (item.usage.usedBytes.toFloat() / item.cycle.quotaBytes).coerceIn(0f, 1f) else 0f
    // The cycle shown above gets a soft blue fill and a primary outline
    Card(onClick = onClick, shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow),
        border = if (selected) BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null,
        modifier = Modifier.semantics { this.selected = selected }) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                ToneIcon(DataraIcons.History, tone)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(item.name, style = MaterialTheme.typography.titleMedium)
                    Text("Started ${formatCycleDate(item.cycle.startAt)}", style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (selected) Text("Showing", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            }
            LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().height(6.dp),
                color = tone.accent(), trackColor = tone.colors().container, drawStopIndicator = {})
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp),
                itemVerticalAlignment = Alignment.CenterVertically) {
                QuotaStatusChip(tone, text = item.statusLabel)
                Text("${formatQuotaData(item.usage.usedBytes)} of ${formatQuotaData(item.cycle.quotaBytes)} used", style = MaterialTheme.typography.bodyMedium)
            }
            item.pet?.let { Text("Forecast: ${petErrorText(it.errorMillis)}", style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

@Composable
private fun ChartLegend(window: HistoryWindow, highlightLast: Boolean) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (highlightLast) LegendKey(ChartToday, if (window == HistoryWindow.DAY) "This hour" else "Today")
        if (window != HistoryWindow.DAY) LegendKey(MaterialTheme.colorScheme.primary, "Daily average", dashed = true)
        Text("Tap a bar for its value", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun LegendKey(color: androidx.compose.ui.graphics.Color, label: String, dashed: Boolean = false) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        if (dashed) Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) { repeat(3) { Box(Modifier.size(5.dp, 2.dp).background(color)) } }
        else Box(Modifier.size(10.dp).background(color, MaterialTheme.shapes.extraSmall))
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun UsageChart(points: List<UsagePoint>, window: HistoryWindow, unit: QuotaUnit, highlightLast: Boolean, average: Double?) {
    // Unmeasured periods get no x value, so they render as blank space rather than zero-height bars
    val measured = remember(points) { points.withIndex().filter { it.value.isMeasured } }
    // A model producer (not a fixed model) so Vico grows the bars in and animates between ranges
    val modelProducer = remember { CartesianChartModelProducer() }
    // The model arrives a frame after the window changes, so everything that reads x values (axis
    // labels, marker text, today's index, the x range) travels inside the model's extras with it.
    // Reading the current `points` instead crashed on fast tab switches: an old x had no new label.
    LaunchedEffect(measured, unit, window) {
        if (measured.isNotEmpty()) modelProducer.runTransaction {
            columnModel { series(x = measured.map { it.index }, y = measured.map { it.value.bytes!!.toDouble() / unit.bytes }) }
            extras { store ->
                store[ChartAxisLabels] = points.map { if (window == HistoryWindow.DAY) it.label.substringBefore(":") else it.label.substringAfterLast(" ") }
                store[ChartMarkerLabels] = points.map { "${it.label} · ${it.bytes?.let(::formatQuotaData) ?: "Not measured"}" }
                store[ChartLabelSpacing] = if (window == HistoryWindow.DAY) 4 else if (points.size > 10) 3 else 1
            }
        }
    }
    val description = remember(points, unit) {
        "Mobile usage chart in ${unit.name}, ${measured.size} of ${points.size} periods measured. Exact readings are available below."
    }
    val thickness = if (window == HistoryWindow.DAY) 6.dp else 12.dp
    val barShape = RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp)
    val c = MaterialTheme.colorScheme
    // Gradient bars (teal fading down); the current hour / today in the logo blue
    val bar = rememberLineComponent(fill = Fill(Brush.verticalGradient(listOf(c.secondary, c.secondary.copy(alpha = 0.45f)))),
        thickness = thickness, shape = barShape)
    val todayBar = rememberLineComponent(fill = Fill(Brush.verticalGradient(listOf(ChartToday, ChartToday.copy(alpha = 0.55f)))),
        thickness = thickness, shape = barShape)
    val columnProvider = remember(bar, todayBar, highlightLast) {
        object : ColumnCartesianLayer.ColumnProvider {
            override fun getColumn(entry: ColumnCartesianLayerModel.Entry, seriesIndex: Int, extraStore: ExtraStore) =
                if (highlightLast && entry.x.toInt() == extraStore.getOrNull(ChartAxisLabels)?.lastIndex) todayBar else bar
            override fun getWidestSeriesColumn(seriesIndex: Int, extraStore: ExtraStore) = bar
        }
    }
    val markerLabel = rememberTextComponent(style = MaterialTheme.typography.labelMedium.copy(color = c.inverseOnSurface),
        padding = Insets(8.dp, 4.dp), background = rememberShapeComponent(Fill(c.inverseSurface), RoundedCornerShape(8.dp)))
    val marker = rememberDefaultCartesianMarker(label = markerLabel, valueFormatter = remember {
        DefaultCartesianMarker.ValueFormatter { context, targets ->
            val x = targets.firstOrNull()?.x?.toInt()
            x?.let { context.model.extraStore.getOrNull(ChartMarkerLabels)?.getOrNull(it) } ?: ""
        }
    }, guideline = rememberLineComponent(fill = Fill(c.outline), thickness = 1.dp))
    val averageLine = rememberLineComponent(fill = Fill(c.primary), thickness = 2.dp,
        shape = DashedShape(dashLength = 6.dp, gapLength = 4.dp))
    val decorations = remember(average, averageLine) {
        if (average == null || average <= 0.0) emptyList() else listOf(HorizontalLine(y = { average }, line = averageLine))
    }
    CartesianChartHost(
        chart = rememberCartesianChart(
            rememberColumnCartesianLayer(columnProvider = columnProvider,
                rangeProvider = remember { WindowRange }),
            startAxis = VerticalAxis.rememberStart(valueFormatter = remember(unit) { CartesianValueFormatter.decimal(suffix = " ${unit.name}") }),
            bottomAxis = HorizontalAxis.rememberBottom(
                // Never blank: Vico throws on a blank axis label
                valueFormatter = remember {
                    CartesianValueFormatter { context, value, _ ->
                        context.model.extraStore.getOrNull(ChartAxisLabels)?.getOrNull(value.toInt())?.takeIf { it.isNotBlank() } ?: "·"
                    }
                },
                itemPlacer = remember { HorizontalAxis.ItemPlacer.aligned(spacing = { it.getOrNull(ChartLabelSpacing) ?: 1 }) }),
            marker = marker, decorations = decorations,
            getXStep = { _, _, _ -> 1.0 }),
        modelProducer = modelProducer,
        modifier = Modifier.fillMaxWidth().height(240.dp).semantics { contentDescription = description },
        scrollState = rememberVicoScrollState(scrollEnabled = false), zoomState = rememberVicoZoomState(zoomEnabled = false))
}

private val ChartAxisLabels = ExtraStore.Key<List<String>>()
private val ChartMarkerLabels = ExtraStore.Key<List<String>>()
private val ChartLabelSpacing = ExtraStore.Key<Int>()

/** x from 0 to the window's last period, y from 0, taken from the model's own extras (see UsageChart). */
private object WindowRange : CartesianLayerRangeProvider {
    override fun getMinX(minX: Double, maxX: Double, extraStore: ExtraStore) = 0.0
    override fun getMaxX(minX: Double, maxX: Double, extraStore: ExtraStore) =
        extraStore.getOrNull(ChartAxisLabels)?.lastIndex?.toDouble() ?: maxX
    override fun getMinY(minY: Double, maxY: Double, extraStore: ExtraStore) = 0.0
}

@Composable
private fun HistoryCard(content: @Composable ColumnScope.() -> Unit) {
    Card(shape = MaterialTheme.shapes.large, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp), content = content)
    }
}

@Preview(widthDp = 360, heightDp = 800)
@Preview(name = "History large text", widthDp = 360, heightDp = 800, fontScale = 2f)
@Composable
private fun HistoryEmptyPreview() {
    HistoryScreen(HistoryUiState.Ready(HistoryData(emptyList(), emptyList(), emptyList(), 0)), {}, {})
}
