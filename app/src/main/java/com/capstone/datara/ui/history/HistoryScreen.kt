package com.capstone.datara.ui.history

import androidx.annotation.StringRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.capstone.datara.R
import com.capstone.datara.ui.components.DataraNoticeCard
import com.capstone.datara.ui.navigation.DataraTopLevelDestination
import com.capstone.datara.ui.navigation.DataraTopLevelScaffold
import com.capstone.datara.ui.theme.DATAraTheme
import com.capstone.datara.ui.theme.DataraBlueSoft
import com.capstone.datara.ui.theme.DataraCardBg
import com.capstone.datara.ui.theme.DataraCardElevated
import com.capstone.datara.ui.theme.DataraDarkBg
import com.capstone.datara.ui.theme.DataraDivider
import com.capstone.datara.ui.theme.DataraError
import com.capstone.datara.ui.theme.DataraGreenSoft
import com.capstone.datara.ui.theme.DataraHeroStart
import com.capstone.datara.ui.theme.DataraInputBorder
import com.capstone.datara.ui.theme.DataraNeonBlue
import com.capstone.datara.ui.theme.DataraNeonGreen
import com.capstone.datara.ui.theme.DataraTextPrimary
import com.capstone.datara.ui.theme.DataraTextSecondary
import com.capstone.datara.ui.theme.DataraWarning
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.compose.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.compose.cartesian.data.lineModel
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import java.util.Locale

enum class HistoryRange(@StringRes val labelRes: Int) {
    DAY(R.string.history_range_day),
    WEEK(R.string.history_range_week),
    PROMO(R.string.history_range_promo)
}

enum class TrendDirection {
    DECREASING,
    INCREASING,
    STEADY
}

data class UsagePointUiModel(val label: String, val valueMb: Double)

data class UsageBreakdownUiModel(
    val period: String,
    val supportingText: String,
    val consumedMb: Double,
    val share: Float
)

data class UsageTrendUiModel(
    val label: String,
    val direction: TrendDirection
)

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
    HistoryContent(
        state = state,
        onHomeClick = onHomeClick,
        onSettingsClick = onSettingsClick,
        onReviewAccessClick = onReviewAccessClick,
        onRetryClick = onRetryClick,
        modifier = modifier
    )
}

@Composable
private fun HistoryContent(
    state: HistoryUiState,
    onHomeClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onReviewAccessClick: () -> Unit,
    onRetryClick: (() -> Unit)?,
    modifier: Modifier = Modifier
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
        BoxWithConstraints(Modifier.fillMaxSize().padding(innerPadding)) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxHeight()
                    .widthIn(max = if (expanded) 940.dp else maxWidth)
                    .fillMaxWidth()
                    .align(Alignment.TopCenter),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item { HistoryHeader(state) }
                item {
                    RangeSelector(
                        selected = selectedRange,
                        onSelected = { selectedRangeName = it.name }
                    )
                }

                when (state) {
                    HistoryUiState.Empty -> item { EmptyHistoryCard() }
                    HistoryUiState.Loading -> item { LoadingHistoryCard() }
                    HistoryUiState.PermissionRequired -> item {
                        StateActionCard(
                            title = stringResource(R.string.history_permission_title),
                            message = stringResource(R.string.history_permission_body),
                            actionLabel = stringResource(R.string.history_review_access),
                            onAction = onReviewAccessClick
                        )
                    }
                    HistoryUiState.TrackingStopped -> item {
                        StateCard(
                            title = stringResource(R.string.history_tracking_stopped_title),
                            message = stringResource(R.string.history_tracking_stopped_body)
                        )
                    }
                    is HistoryUiState.Error -> item {
                        ErrorHistoryCard(state.message, onRetryClick)
                    }
                    is HistoryUiState.Ready -> readyHistoryContent(state.summary, selectedRange, stale = false)
                    is HistoryUiState.Stale -> readyHistoryContent(state.summary, selectedRange, stale = true)
                }
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.readyHistoryContent(
    summary: HistorySummaryUiModel,
    range: HistoryRange,
    stale: Boolean
) {
    if (stale) {
        item {
            DataraNoticeCard(
                title = stringResource(R.string.history_status_stale),
                message = stringResource(R.string.history_stale_notice),
                accent = DataraWarning,
                modifier = Modifier
            )
        }
    }
    item { UsageChartCard(summary, range) }
    item { SummaryMetrics(summary) }
    item { TrendInsightCard(summary.trend) }
    item { BreakdownHeader(range) }
    items(summary.breakdown, key = { it.period }) { entry -> BreakdownRow(entry) }
    item { EstimatedDataNotice() }
}

@Composable
private fun HistoryHeader(state: HistoryUiState) {
    val statusText = when (state) {
        HistoryUiState.Loading -> stringResource(R.string.history_status_loading)
        is HistoryUiState.Ready -> stringResource(R.string.history_status_on_device)
        is HistoryUiState.Stale -> stringResource(R.string.history_status_stale)
        is HistoryUiState.Error -> stringResource(R.string.status_unavailable)
        else -> stringResource(R.string.history_status_waiting)
    }
    val statusColor = when (state) {
        is HistoryUiState.Ready -> DataraNeonGreen
        is HistoryUiState.Stale, is HistoryUiState.Error -> DataraWarning
        else -> DataraTextSecondary
    }

    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.history_eyebrow),
                style = MaterialTheme.typography.labelLarge,
                color = DataraBlueSoft,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
            Spacer(Modifier.height(4.dp))
            Text(stringResource(R.string.history_title), style = MaterialTheme.typography.headlineMedium, color = DataraTextPrimary)
            Text(stringResource(R.string.history_subtitle), style = MaterialTheme.typography.bodyMedium, color = DataraTextSecondary)
        }
        Row(
            modifier = Modifier
                .background(DataraCardElevated, RoundedCornerShape(20.dp))
                .border(1.dp, DataraDivider, RoundedCornerShape(20.dp))
                .padding(horizontal = 10.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.size(7.dp).background(statusColor, CircleShape))
            Spacer(Modifier.width(6.dp))
            Text(statusText, style = MaterialTheme.typography.labelSmall, color = DataraTextSecondary)
        }
    }
}

@Composable
private fun RangeSelector(selected: HistoryRange, onSelected: (HistoryRange) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        HistoryRange.entries.forEach { range ->
            FilterChip(
                selected = selected == range,
                onClick = { onSelected(range) },
                label = { Text(stringResource(range.labelRes), maxLines = 1) },
                modifier = Modifier.weight(1f).height(48.dp),
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = DataraNeonBlue,
                    selectedLabelColor = Color.White,
                    containerColor = DataraCardBg,
                    labelColor = DataraTextSecondary
                )
            )
        }
    }
}

@Composable
private fun EmptyHistoryCard() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Brush.linearGradient(listOf(DataraHeroStart, DataraCardBg)), RoundedCornerShape(24.dp))
            .border(1.dp, DataraNeonBlue.copy(alpha = .22f), RoundedCornerShape(24.dp))
            .padding(horizontal = 24.dp, vertical = 42.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        EmptyChartGlyph(stringResource(R.string.history_empty_chart_description))
        Spacer(Modifier.height(22.dp))
        Text(stringResource(R.string.history_empty_title), style = MaterialTheme.typography.titleLarge, color = DataraTextPrimary)
        Spacer(Modifier.height(7.dp))
        Text(
            stringResource(R.string.history_empty_body),
            style = MaterialTheme.typography.bodyMedium,
            color = DataraTextSecondary,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        Spacer(Modifier.height(20.dp))
        Text(
            stringResource(R.string.history_empty_storage),
            style = MaterialTheme.typography.labelSmall,
            color = DataraBlueSoft,
            modifier = Modifier
                .background(DataraNeonBlue.copy(alpha = .1f), RoundedCornerShape(20.dp))
                .padding(horizontal = 12.dp, vertical = 6.dp)
        )
    }
}

@Composable
private fun LoadingHistoryCard() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(DataraCardBg, RoundedCornerShape(20.dp))
            .border(1.dp, DataraInputBorder, RoundedCornerShape(20.dp))
            .padding(22.dp)
    ) {
        CircularProgressIndicator(color = DataraNeonBlue, modifier = Modifier.size(28.dp))
        Spacer(Modifier.height(14.dp))
        Text(stringResource(R.string.history_loading_title), style = MaterialTheme.typography.titleLarge, color = DataraTextPrimary)
        Text(stringResource(R.string.history_loading_body), style = MaterialTheme.typography.bodyMedium, color = DataraTextSecondary)
    }
}

@Composable
private fun ErrorHistoryCard(message: String, onRetryClick: (() -> Unit)?) {
    Column {
        StateCard(title = stringResource(R.string.history_error_title), message = message)
        if (onRetryClick != null) {
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = onRetryClick,
                colors = ButtonDefaults.buttonColors(containerColor = DataraNeonBlue)
            ) {
                Text(stringResource(R.string.action_retry))
            }
        }
    }
}

@Composable
private fun StateActionCard(title: String, message: String, actionLabel: String, onAction: () -> Unit) {
    Column {
        StateCard(title, message)
        Spacer(Modifier.height(12.dp))
        Button(onClick = onAction, colors = ButtonDefaults.buttonColors(containerColor = DataraNeonBlue)) {
            Text(actionLabel)
        }
    }
}

@Composable
private fun StateCard(title: String, message: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(DataraCardBg, RoundedCornerShape(20.dp))
            .border(1.dp, DataraInputBorder, RoundedCornerShape(20.dp))
            .padding(22.dp)
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge, color = DataraTextPrimary)
        Spacer(Modifier.height(6.dp))
        Text(message, style = MaterialTheme.typography.bodyMedium, color = DataraTextSecondary)
    }
}

@Composable
private fun UsageChartCard(summary: HistorySummaryUiModel, range: HistoryRange) {
    val total = formatUsage(summary.totalMb)
    val chartDescription = stringResource(
        R.string.history_chart_summary,
        summary.points.size,
        total,
        summary.peakPeriod
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(DataraCardBg, RoundedCornerShape(24.dp))
            .border(1.dp, DataraInputBorder, RoundedCornerShape(24.dp))
            .padding(18.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = when (range) {
                        HistoryRange.DAY -> stringResource(R.string.history_total_today)
                        HistoryRange.WEEK -> stringResource(R.string.history_last_seven_days)
                        HistoryRange.PROMO -> stringResource(R.string.history_current_promo_cycle)
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = DataraTextSecondary,
                    fontWeight = FontWeight.Bold
                )
                Text(total, style = MaterialTheme.typography.displayLarge, color = DataraTextPrimary)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(stringResource(R.string.history_updated), style = MaterialTheme.typography.labelSmall, color = DataraTextSecondary)
                Text(summary.lastUpdated, style = MaterialTheme.typography.labelLarge, color = DataraGreenSoft)
            }
        }
        Spacer(Modifier.height(18.dp))
        UsageLineChart(
            points = summary.points,
            modifier = Modifier
                .fillMaxWidth()
                .height(210.dp)
                .semantics {
                    contentDescription = chartDescription
                }
        )
        Spacer(Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            summary.points
                .filterIndexed { index, _ -> index == 0 || index == summary.points.lastIndex || index == summary.points.lastIndex / 2 }
                .forEach { point ->
                    Text(point.label, style = MaterialTheme.typography.labelSmall, color = DataraTextSecondary)
                }
        }
    }
}

@Composable
private fun UsageLineChart(points: List<UsagePointUiModel>, modifier: Modifier = Modifier) {
    val modelProducer = remember { CartesianChartModelProducer() }
    LaunchedEffect(points) {
        modelProducer.runTransaction { lineModel { series(y = points.map { it.valueMb }) } }
    }
    CartesianChartHost(
        chart = rememberCartesianChart(
            rememberLineCartesianLayer(),
            startAxis = VerticalAxis.rememberStart(),
            bottomAxis = HorizontalAxis.rememberBottom()
        ),
        modelProducer = modelProducer,
        modifier = modifier
    )
}

@Composable
private fun SummaryMetrics(summary: HistorySummaryUiModel) {
    BoxWithConstraints {
        if (maxWidth < 500.dp) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                AverageCard(summary, Modifier.fillMaxWidth())
                PeakCard(summary, Modifier.fillMaxWidth())
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                AverageCard(summary, Modifier.weight(1f))
                PeakCard(summary, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun AverageCard(summary: HistorySummaryUiModel, modifier: Modifier) {
    SummaryMetricCard(
        eyebrow = stringResource(R.string.history_daily_average),
        value = formatUsage(summary.averagePerDayMb),
        supporting = stringResource(R.string.history_per_day),
        accent = DataraBlueSoft,
        modifier = modifier
    )
}

@Composable
private fun PeakCard(summary: HistorySummaryUiModel, modifier: Modifier) {
    SummaryMetricCard(
        eyebrow = stringResource(R.string.history_peak_usage),
        value = summary.peakPeriod,
        supporting = summary.highestDay,
        accent = DataraWarning,
        modifier = modifier
    )
}

@Composable
private fun SummaryMetricCard(
    eyebrow: String,
    value: String,
    supporting: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .background(DataraCardBg, RoundedCornerShape(18.dp))
            .border(1.dp, DataraInputBorder, RoundedCornerShape(18.dp))
            .padding(16.dp)
    ) {
        Box(Modifier.size(8.dp).background(accent, CircleShape))
        Spacer(Modifier.height(12.dp))
        Text(eyebrow, style = MaterialTheme.typography.labelSmall, color = DataraTextSecondary, fontSize = 10.sp)
        Text(value, style = MaterialTheme.typography.titleLarge, color = DataraTextPrimary, maxLines = 1)
        Text(supporting, style = MaterialTheme.typography.labelSmall, color = DataraTextSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun TrendInsightCard(trend: UsageTrendUiModel) {
    val accent = when (trend.direction) {
        TrendDirection.DECREASING -> DataraNeonGreen
        TrendDirection.INCREASING -> DataraWarning
        TrendDirection.STEADY -> DataraBlueSoft
    }
    val glyph = when (trend.direction) {
        TrendDirection.DECREASING -> "↘"
        TrendDirection.INCREASING -> "↗"
        TrendDirection.STEADY -> "→"
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(accent.copy(alpha = .07f), RoundedCornerShape(18.dp))
            .border(1.dp, accent.copy(alpha = .2f), RoundedCornerShape(18.dp))
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(38.dp).background(accent.copy(alpha = .14f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(glyph, color = accent, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(12.dp))
        Column {
            Text(stringResource(R.string.history_usage_trend), style = MaterialTheme.typography.labelSmall, color = DataraTextSecondary)
            Text(trend.label, style = MaterialTheme.typography.titleMedium, color = DataraTextPrimary)
        }
    }
}

@Composable
private fun BreakdownHeader(range: HistoryRange) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(stringResource(R.string.history_usage_breakdown), style = MaterialTheme.typography.titleLarge, color = DataraTextPrimary)
            Text(
                if (range == HistoryRange.DAY) stringResource(R.string.history_hourly_totals) else stringResource(R.string.history_daily_totals),
                style = MaterialTheme.typography.bodyMedium,
                color = DataraTextSecondary
            )
        }
        Text(stringResource(R.string.history_local_records), style = MaterialTheme.typography.labelSmall, color = DataraBlueSoft)
    }
}

@Composable
private fun BreakdownRow(entry: UsageBreakdownUiModel) {
    val share = entry.share.coerceIn(0f, 1f)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(DataraCardBg, RoundedCornerShape(18.dp))
            .border(1.dp, DataraInputBorder, RoundedCornerShape(18.dp))
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(entry.period, style = MaterialTheme.typography.titleMedium, color = DataraTextPrimary)
                Text(entry.supportingText, style = MaterialTheme.typography.labelSmall, color = DataraTextSecondary)
            }
            Text(formatUsage(entry.consumedMb), style = MaterialTheme.typography.titleMedium, color = DataraBlueSoft)
        }
        Spacer(Modifier.height(12.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(6.dp)
                .background(DataraDivider, CircleShape)
                .semantics { progressBarRangeInfo = ProgressBarRangeInfo(share, 0f..1f) }
        ) {
            Box(
                Modifier
                    .fillMaxWidth(share)
                    .height(6.dp)
                    .background(Brush.horizontalGradient(listOf(DataraNeonBlue, DataraBlueSoft)), CircleShape)
            )
        }
    }
}

@Composable
private fun EstimatedDataNotice() {
    Text(
        stringResource(R.string.history_estimate_notice),
        style = MaterialTheme.typography.labelSmall,
        color = DataraTextSecondary,
        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
    )
}

@Composable
private fun EmptyChartGlyph(description: String) {
    Canvas(Modifier.size(72.dp).semantics { contentDescription = description }) {
        repeat(3) { index ->
            val y = size.height * (.25f + index * .25f)
            drawLine(DataraDivider, Offset(0f, y), Offset(size.width, y), 1.dp.toPx())
        }
        val points = listOf(
            Offset(size.width * .08f, size.height * .72f),
            Offset(size.width * .3f, size.height * .52f),
            Offset(size.width * .52f, size.height * .61f),
            Offset(size.width * .76f, size.height * .28f),
            Offset(size.width * .94f, size.height * .38f)
        )
        points.zipWithNext().forEach { (start, end) -> drawLine(DataraBlueSoft, start, end, 4.dp.toPx(), StrokeCap.Round) }
        points.forEach {
            drawCircle(DataraDarkBg, 5.dp.toPx(), it)
            drawCircle(DataraBlueSoft, 3.dp.toPx(), it)
        }
    }
}

@Composable
private fun formatUsage(valueMb: Double): String = if (valueMb >= 1024) {
    stringResource(R.string.data_amount_gb, String.format(Locale.US, "%.1f", valueMb / 1024.0))
} else {
    stringResource(R.string.data_amount_mb, String.format(Locale.US, "%.0f", valueMb))
}

@Composable
private fun previewHistoryState(): HistoryUiState = HistoryUiState.Ready(
    HistorySummaryUiModel(
        totalMb = 1780.0,
        averagePerDayMb = 254.0,
        peakPeriod = stringResource(R.string.history_preview_peak),
        highestDay = stringResource(R.string.history_preview_day),
        trend = UsageTrendUiModel(
            label = stringResource(R.string.history_preview_trend),
            direction = TrendDirection.DECREASING
        ),
        points = listOf(
            UsagePointUiModel("12 AM", 80.0),
            UsagePointUiModel("4 AM", 45.0),
            UsagePointUiModel("8 AM", 190.0),
            UsagePointUiModel("12 PM", 150.0),
            UsagePointUiModel("4 PM", 310.0),
            UsagePointUiModel("8 PM", 520.0),
            UsagePointUiModel("Now", 485.0)
        ),
        breakdown = listOf(
            UsageBreakdownUiModel("8:00 PM – now", stringResource(R.string.history_preview_peak_period), 485.0, .92f),
            UsageBreakdownUiModel("4:00 PM – 8:00 PM", stringResource(R.string.history_preview_afternoon), 310.0, .59f),
            UsageBreakdownUiModel("12:00 PM – 4:00 PM", stringResource(R.string.history_preview_midday), 150.0, .29f)
        ),
        lastUpdated = stringResource(R.string.history_preview_now)
    )
)

@Preview(showBackground = true, backgroundColor = 0xFF0C101A, heightDp = 940)
@Composable
private fun HistoryReadyPreview() {
    DATAraTheme {
        HistoryContent(
            state = previewHistoryState(),
            onHomeClick = {},
            onSettingsClick = {},
            onReviewAccessClick = {},
            onRetryClick = null
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0C101A, heightDp = 940)
@Composable
private fun HistoryEmptyPreview() {
    DATAraTheme {
        HistoryContent(
            state = HistoryUiState.Empty,
            onHomeClick = {},
            onSettingsClick = {},
            onReviewAccessClick = {},
            onRetryClick = null
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0C101A, heightDp = 800)
@Composable
private fun HistoryPermissionPreview() {
    DATAraTheme {
        HistoryContent(
            state = HistoryUiState.PermissionRequired,
            onHomeClick = {},
            onSettingsClick = {},
            onReviewAccessClick = {},
            onRetryClick = null
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0C101A, heightDp = 800)
@Composable
private fun HistoryLoadingPreview() {
    DATAraTheme {
        HistoryContent(
            state = HistoryUiState.Loading,
            onHomeClick = {},
            onSettingsClick = {},
            onReviewAccessClick = {},
            onRetryClick = null
        )
    }
}
