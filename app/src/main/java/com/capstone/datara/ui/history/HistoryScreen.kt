package com.capstone.datara.ui.history

import androidx.annotation.StringRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.capstone.datara.R
import com.capstone.datara.ui.navigation.DataraTopLevelDestination
import com.capstone.datara.ui.navigation.DataraTopLevelScaffold
import com.capstone.datara.ui.theme.DATAraTheme
import com.capstone.datara.ui.theme.DataraError
import com.capstone.datara.ui.theme.DataraInsightBorder
import com.capstone.datara.ui.theme.DataraInsightSurface
import com.capstone.datara.ui.theme.DataraPrimaryBlue
import com.capstone.datara.ui.theme.DataraSuccess
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.compose.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.compose.cartesian.data.lineModel
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import java.util.Locale

enum class HistoryRange(@StringRes val labelRes: Int) {
    DAY(R.string.history_range_day), WEEK(R.string.history_range_week), PROMO(R.string.history_range_promo)
}

enum class TrendDirection { DECREASING, INCREASING, STEADY }

data class UsagePointUiModel(val label: String, val valueMb: Double)
data class UsageBreakdownUiModel(val period: String, val supportingText: String, val consumedMb: Double, val share: Float)
data class UsageTrendUiModel(val label: String, val direction: TrendDirection)

data class HistorySummaryUiModel(
    val totalMb: Double,
    val averagePerDayMb: Double,
    val peakPeriod: String,
    val highestDay: String,
    val trend: UsageTrendUiModel,
    val points: List<UsagePointUiModel>,
    val breakdown: List<UsageBreakdownUiModel>,
    val lastUpdated: String
)

sealed interface HistoryUiState {
    data object Empty : HistoryUiState
    data object Loading : HistoryUiState
    data object PermissionRequired : HistoryUiState
    data object TrackingStopped : HistoryUiState
    data class Ready(val summary: HistorySummaryUiModel) : HistoryUiState
    data class Stale(val summary: HistorySummaryUiModel) : HistoryUiState
    data class Error(val message: String) : HistoryUiState
}

@Composable
fun HistoryScreen(
    onHomeClick: () -> Unit,
    onSettingsClick: () -> Unit = {},
    onReviewAccessClick: () -> Unit,
    modifier: Modifier = Modifier,
    state: HistoryUiState = HistoryUiState.Empty,
    onRetryClick: (() -> Unit)? = null
) {
    var selectedRangeName by rememberSaveable { mutableStateOf(HistoryRange.DAY.name) }
    val selectedRange = HistoryRange.valueOf(selectedRangeName)

    DataraTopLevelScaffold(
        selected = DataraTopLevelDestination.HISTORY,
        onHomeClick = onHomeClick,
        onHistoryClick = {},
        onSettingsClick = onSettingsClick,
        modifier = modifier
    ) { innerPadding, expanded ->
        BoxWithConstraints(Modifier.fillMaxSize().padding(innerPadding).background(MaterialTheme.colorScheme.background)) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxHeight()
                    .widthIn(max = if (expanded) 760.dp else maxWidth)
                    .fillMaxWidth()
                    .align(Alignment.TopCenter),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 22.dp, bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item { HistoryHeader(state) }

                when (state) {
                    is HistoryUiState.Ready -> {
                        item { UsageChartCard(state.summary, selectedRange) { selectedRangeName = it.name } }
                        historyDetails(state.summary)
                    }
                    is HistoryUiState.Stale -> {
                        item { UsageChartCard(state.summary, selectedRange) { selectedRangeName = it.name } }
                        item { HistoryNotice(stringResource(R.string.history_stale_notice)) }
                        historyDetails(state.summary)
                    }
                    HistoryUiState.Empty -> item {
                        EmptyHistoryCard(selectedRange) { selectedRangeName = it.name }
                    }
                    HistoryUiState.Loading -> item { LoadingHistoryCard() }
                    HistoryUiState.PermissionRequired -> item {
                        StateCard(
                            title = stringResource(R.string.history_permission_title),
                            message = stringResource(R.string.history_permission_body),
                            actionLabel = stringResource(R.string.history_review_access),
                            onAction = onReviewAccessClick
                        )
                    }
                    HistoryUiState.TrackingStopped -> item {
                        StateCard(
                            stringResource(R.string.history_tracking_stopped_title),
                            stringResource(R.string.history_tracking_stopped_body)
                        )
                    }
                    is HistoryUiState.Error -> item {
                        StateCard(
                            stringResource(R.string.history_error_title),
                            state.message,
                            onRetryClick?.let { stringResource(R.string.action_retry) },
                            onRetryClick
                        )
                    }
                }
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.historyDetails(summary: HistorySummaryUiModel) {
    item {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.history_detail_logs), color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Black, fontSize = 13.sp)
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surface, border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)) {
                Text(stringResource(R.string.history_local_records), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 9.sp, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
            }
        }
    }
    items(summary.breakdown, key = { it.period }) { HistoryLogCard(it) }
    item { HistoryNotice(stringResource(R.string.history_estimate_notice)) }
}

@Composable
private fun HistoryHeader(state: HistoryUiState) {
    val status = when (state) {
        is HistoryUiState.Ready -> stringResource(R.string.history_status_on_device)
        is HistoryUiState.Stale -> stringResource(R.string.history_status_stale)
        HistoryUiState.Loading -> stringResource(R.string.history_status_loading)
        is HistoryUiState.Error -> stringResource(R.string.status_unavailable)
        else -> stringResource(R.string.history_status_waiting)
    }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            stringResource(R.string.history_screen_heading),
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Black,
            fontSize = 25.sp,
            modifier = Modifier.weight(1f)
        )
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceVariant) {
            Row(Modifier.padding(horizontal = 9.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(5.dp).background(if (state is HistoryUiState.Ready) DataraSuccess else MaterialTheme.colorScheme.onSurfaceVariant, CircleShape))
                Spacer(Modifier.size(5.dp))
                Text(status, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 9.sp)
            }
        }
    }
}

@Composable
private fun UsageChartCard(summary: HistorySummaryUiModel, range: HistoryRange, onRangeSelected: (HistoryRange) -> Unit) {
    val chartDescription = stringResource(R.string.history_chart_summary, summary.points.size, formatUsage(summary.totalMb), summary.peakPeriod)
    ReferenceHistoryCard {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(range.totalLabel(), color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                Text(formatUsage(summary.totalMb), color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Black, fontSize = 23.sp)
            }
            HistoryRangeSelector(range, onRangeSelected)
        }
        Spacer(Modifier.height(12.dp))
        UsageLineChart(
            points = summary.points,
            modifier = Modifier.fillMaxWidth().height(170.dp).semantics { contentDescription = chartDescription }
        )
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("${stringResource(R.string.history_daily_average)}  ${formatUsage(summary.averagePerDayMb)}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 9.sp)
            Text("${stringResource(R.string.history_peak_usage)}  ${summary.peakPeriod}", color = DataraPrimaryBlue, fontWeight = FontWeight.Bold, fontSize = 9.sp)
        }
    }
}

@Composable
private fun HistoryRangeSelector(selected: HistoryRange, onSelected: (HistoryRange) -> Unit) {
    Row(
        modifier = Modifier.background(MaterialTheme.colorScheme.surfaceVariant, CircleShape).padding(3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        HistoryRange.entries.forEach { range ->
            val active = range == selected
            Box(
                modifier = Modifier
                    .background(if (active) MaterialTheme.colorScheme.surface else Color.Transparent, CircleShape)
                    .clickable(role = Role.Tab) { onSelected(range) }
                    .semantics { role = Role.Tab }
                    .padding(horizontal = 9.dp, vertical = 5.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(stringResource(range.labelRes), color = if (active) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold, fontSize = 8.sp, maxLines = 1)
            }
        }
    }
}

@Composable
private fun EmptyHistoryCard(range: HistoryRange, onRangeSelected: (HistoryRange) -> Unit) {
    ReferenceHistoryCard {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { HistoryRangeSelector(range, onRangeSelected) }
        Spacer(Modifier.height(24.dp))
        EmptyChartGlyph(
            description = stringResource(R.string.history_empty_chart_description),
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )
        Spacer(Modifier.height(16.dp))
        Text(stringResource(R.string.history_empty_title), color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Black, fontSize = 18.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
        Text(stringResource(R.string.history_empty_body), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, textAlign = TextAlign.Center)
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun LoadingHistoryCard() {
    ReferenceHistoryCard {
        CircularProgressIndicator(color = DataraPrimaryBlue, modifier = Modifier.size(28.dp))
        Spacer(Modifier.height(14.dp))
        Text(stringResource(R.string.history_loading_title), color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Black, fontSize = 18.sp)
        Text(stringResource(R.string.history_loading_body), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
    }
}

@Composable
private fun StateCard(title: String, message: String, actionLabel: String? = null, onAction: (() -> Unit)? = null) {
    ReferenceHistoryCard {
        Text(title, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Black, fontSize = 18.sp)
        Spacer(Modifier.height(6.dp))
        Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(16.dp))
            Button(onClick = onAction, colors = ButtonDefaults.buttonColors(containerColor = DataraPrimaryBlue)) { Text(actionLabel) }
        }
    }
}

@Composable
private fun HistoryLogCard(entry: UsageBreakdownUiModel) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(15.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        shadowElevation = 4.dp
    ) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 15.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(entry.period, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp)
                Spacer(Modifier.height(7.dp))
                Text(entry.supportingText, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold, fontSize = 11.sp)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(formatUsage(entry.consumedMb), color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Black, fontSize = 14.sp)
                Text("${(entry.share.coerceIn(0f, 1f) * 100).toInt()}%", color = DataraPrimaryBlue, fontWeight = FontWeight.Bold, fontSize = 9.sp)
            }
        }
    }
}

@Composable
private fun HistoryNotice(message: String) {
    Text(
        message,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = 10.sp,
        modifier = Modifier.fillMaxWidth().background(DataraInsightSurface, RoundedCornerShape(12.dp)).border(1.dp, DataraInsightBorder, RoundedCornerShape(12.dp)).padding(12.dp)
    )
}

@Composable
private fun ReferenceHistoryCard(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        shadowElevation = 5.dp
    ) {
        Column(Modifier.padding(18.dp), content = content)
    }
}

@Composable
private fun UsageLineChart(points: List<UsagePointUiModel>, modifier: Modifier = Modifier) {
    val producer = remember { CartesianChartModelProducer() }
    LaunchedEffect(points) { producer.runTransaction { lineModel { series(y = points.map { it.valueMb }) } } }
    CartesianChartHost(
        chart = rememberCartesianChart(
            rememberLineCartesianLayer(),
            startAxis = VerticalAxis.rememberStart(),
            bottomAxis = HorizontalAxis.rememberBottom()
        ),
        modelProducer = producer,
        modifier = modifier
    )
}

@Composable
private fun EmptyChartGlyph(description: String, modifier: Modifier = Modifier) {
    val outline = MaterialTheme.colorScheme.outline
    val primary = MaterialTheme.colorScheme.primary
    Canvas(modifier.size(62.dp).semantics { contentDescription = description }) {
        val stroke = Stroke(2.dp.toPx(), cap = StrokeCap.Round)
        drawLine(outline, Offset(size.width * .1f, size.height * .85f), Offset(size.width * .9f, size.height * .85f), stroke.width)
        drawLine(outline, Offset(size.width * .12f, size.height * .15f), Offset(size.width * .12f, size.height * .85f), stroke.width)
        drawLine(primary, Offset(size.width * .18f, size.height * .68f), Offset(size.width * .42f, size.height * .48f), stroke.width, StrokeCap.Round)
        drawLine(primary, Offset(size.width * .42f, size.height * .48f), Offset(size.width * .62f, size.height * .58f), stroke.width, StrokeCap.Round)
        drawLine(primary, Offset(size.width * .62f, size.height * .58f), Offset(size.width * .86f, size.height * .28f), stroke.width, StrokeCap.Round)
    }
}

@Composable
private fun HistoryRange.totalLabel(): String = when (this) {
    HistoryRange.DAY -> stringResource(R.string.history_total_today)
    HistoryRange.WEEK -> stringResource(R.string.history_last_seven_days)
    HistoryRange.PROMO -> stringResource(R.string.history_current_promo_cycle)
}

@Composable
private fun formatUsage(mb: Double): String = if (mb >= 1024) {
    stringResource(R.string.data_amount_gb, String.format(Locale.US, "%.2f", mb / 1024.0))
} else {
    stringResource(R.string.data_amount_mb, String.format(Locale.US, "%.0f", mb))
}

@Preview(showBackground = true, backgroundColor = 0xFFFAFAFA, heightDp = 920)
@Composable
private fun HistoryPreview() {
    DATAraTheme {
        HistoryScreen(onHomeClick = {}, onReviewAccessClick = {}, state = HistoryUiState.Ready(
            HistorySummaryUiModel(
                totalMb = 25600.0,
                averagePerDayMb = 1536.0,
                peakPeriod = "8–10 PM",
                highestDay = "Saturday",
                trend = UsageTrendUiModel("Steady", TrendDirection.STEADY),
                points = listOf(UsagePointUiModel("1", 1.0), UsagePointUiModel("2", 5.0), UsagePointUiModel("3", 2.0), UsagePointUiModel("4", 6.0)),
                breakdown = listOf(UsageBreakdownUiModel("May 07, 2026", "12:59 PM", 1536.0, .35f)),
                lastUpdated = "Just now"
            )
        ))
    }
}
