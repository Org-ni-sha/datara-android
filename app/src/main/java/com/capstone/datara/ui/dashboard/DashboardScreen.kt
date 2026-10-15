package com.capstone.datara.ui.dashboard

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
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
import androidx.hilt.navigation.compose.hiltViewModel
import com.capstone.datara.R
import com.capstone.datara.ui.auth.AuthViewModel
import com.capstone.datara.ui.components.DataraSignOutDialog
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
import com.capstone.datara.ui.theme.DataraHeroMiddle
import com.capstone.datara.ui.theme.DataraHeroStart
import com.capstone.datara.ui.theme.DataraInputBorder
import com.capstone.datara.ui.theme.DataraNeonBlue
import com.capstone.datara.ui.theme.DataraNeonGreen
import com.capstone.datara.ui.theme.DataraPrimaryBlue
import com.capstone.datara.ui.theme.DataraTextPrimary
import com.capstone.datara.ui.theme.DataraTextSecondary
import com.capstone.datara.ui.theme.DataraWarning
import java.util.Locale

sealed interface PetUiState {
    data object NoPromo : PetUiState
    data class Collecting(val daysCollected: Int, val daysRequired: Int = 7) : PetUiState
    data class Available(
        val timeRemaining: String,
        val exhaustionTime: String,
        val modelLabel: String
    ) : PetUiState
    data object Loading : PetUiState
    data class Error(val message: String) : PetUiState
}

data class DataBalanceUiModel(
    val remainingGb: Double,
    val totalGb: Double,
    val consumedGb: Double,
    val isEstimated: Boolean = true
)

data class NetworkUiModel(
    val carrier: String? = null,
    val signalLabel: String? = null,
    val signalLevel: Int = 0,
    val speed: String? = null,
    val isMobileDataActive: Boolean = false
)

data class ActivePromoUiModel(
    val name: String,
    val carrier: String,
    val expiresIn: String,
    val expiresOn: String
)

data class DashboardInsightUiModel(
    val title: String,
    val message: String,
    val isPositive: Boolean = true
)

data class DashboardUiState(
    val pet: PetUiState = PetUiState.NoPromo,
    val balance: DataBalanceUiModel? = null,
    val network: NetworkUiModel = NetworkUiModel(),
    val promo: ActivePromoUiModel? = null,
    val insight: DashboardInsightUiModel? = null,
    val unreadNotificationCount: Int = 0,
    val lastUpdated: String? = null,
    val previewNotice: String? = null
)

@Composable
fun DashboardScreen(
    onSignedOut: () -> Unit,
    onHistoryClick: () -> Unit = {},
    onSettingsClick: () -> Unit = {},
    onAddPromoClick: () -> Unit = {},
    onNotificationsClick: () -> Unit = {},
    modifier: Modifier = Modifier,
    state: DashboardUiState = DashboardUiState(),
    viewModel: AuthViewModel = hiltViewModel()
) {
    DashboardContent(
        userLabel = viewModel.currentUserEmail?.substringBefore("@")?.toDisplayName() ?: stringResource(R.string.profile_default_name),
        state = state,
        onAddPromoClick = onAddPromoClick,
        onNotificationsClick = onNotificationsClick,
        onHistoryClick = onHistoryClick,
        onSettingsClick = onSettingsClick,
        onSignOutClick = { viewModel.signOut(onComplete = onSignedOut) },
        modifier = modifier
    )
}

@Composable
private fun DashboardContent(
    userLabel: String,
    state: DashboardUiState,
    onAddPromoClick: () -> Unit,
    onNotificationsClick: () -> Unit,
    onHistoryClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onSignOutClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showSignOutDialog by rememberSaveable { mutableStateOf(false) }

    DataraTopLevelScaffold(
        selected = DataraTopLevelDestination.HOME,
        onHomeClick = {},
        onHistoryClick = onHistoryClick,
        onSettingsClick = onSettingsClick,
        modifier = modifier
    ) { innerPadding, expanded ->
        BoxWithConstraints(
            modifier = Modifier.fillMaxSize().padding(innerPadding)
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxHeight()
                    .widthIn(max = if (expanded) 940.dp else maxWidth)
                    .fillMaxWidth()
                    .align(Alignment.TopCenter),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    start = 20.dp,
                    end = 20.dp,
                    top = 18.dp,
                    bottom = 24.dp
                ),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    DashboardHeader(
                        userLabel = userLabel,
                        unreadCount = state.unreadNotificationCount,
                        onNotificationsClick = onNotificationsClick,
                        onSignOutClick = { showSignOutDialog = true }
                    )
                }
                if (state.previewNotice != null) {
                    item { PreviewNotice(state.previewNotice) }
                }
                item { PetHeroCard(state.pet, state.lastUpdated, onAddPromoClick) }
                item { DataBalanceCard(state.balance, onAddPromoClick) }
                item { LiveNetworkSection(state.network) }
                item { ActivePromoCard(state.promo, onAddPromoClick) }
                state.insight?.let { insight -> item { UsageInsightCard(insight) } }
            }
        }
    }

    if (showSignOutDialog) {
        DataraSignOutDialog(
            title = stringResource(R.string.dashboard_sign_out_title),
            message = stringResource(R.string.dashboard_sign_out_message),
            confirmLabel = stringResource(R.string.dashboard_sign_out),
            dismissLabel = stringResource(R.string.action_cancel),
            onDismiss = { showSignOutDialog = false },
            onConfirm = {
                showSignOutDialog = false
                onSignOutClick()
            }
        )
    }
}

@Composable
private fun DashboardHeader(
    userLabel: String,
    unreadCount: Int,
    onNotificationsClick: () -> Unit,
    onSignOutClick: () -> Unit
) {
    var profileMenuExpanded by remember { mutableStateOf(false) }

    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.dashboard_brand),
                style = MaterialTheme.typography.labelLarge,
                color = DataraBlueSoft,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text = stringResource(R.string.dashboard_greeting, userLabel),
                style = MaterialTheme.typography.headlineMedium,
                color = DataraTextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = stringResource(R.string.dashboard_outlook),
                style = MaterialTheme.typography.bodyMedium,
                color = DataraTextSecondary
            )
        }

        IconButton(
            onClick = onNotificationsClick,
            modifier = Modifier.size(48.dp),
            colors = IconButtonDefaults.iconButtonColors(
                containerColor = DataraCardElevated,
                contentColor = DataraTextPrimary
            )
        ) {
            BellGlyph(
                showUnread = unreadCount > 0,
                contentDescription = if (unreadCount > 0) {
                    stringResource(R.string.dashboard_notifications_unread, unreadCount)
                } else {
                    stringResource(R.string.dashboard_notifications)
                }
            )
        }
        Spacer(Modifier.width(8.dp))
        Box {
            IconButton(
                onClick = { profileMenuExpanded = true },
                modifier = Modifier
                    .size(48.dp)
                    .background(
                        Brush.linearGradient(listOf(DataraPrimaryBlue, DataraNeonBlue)),
                        CircleShape
                    )
            ) {
                Text(
                    text = userLabel.firstOrNull()?.uppercaseChar()?.toString() ?: "D",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            }
            DropdownMenu(
                expanded = profileMenuExpanded,
                onDismissRequest = { profileMenuExpanded = false },
                containerColor = DataraCardElevated
            ) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.dashboard_sign_out), color = DataraTextPrimary) },
                    onClick = {
                        profileMenuExpanded = false
                        onSignOutClick()
                    }
                )
            }
        }
    }
}

@Composable
private fun PreviewNotice(message: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(DataraBlueSoft.copy(alpha = 0.08f), RoundedCornerShape(16.dp))
            .border(1.dp, DataraBlueSoft.copy(alpha = 0.22f), RoundedCornerShape(16.dp))
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        StatusDot(stringResource(R.string.status_design_preview), DataraBlueSoft)
        Spacer(Modifier.width(10.dp))
        Text(message, style = MaterialTheme.typography.bodyMedium, color = DataraTextSecondary)
    }
}

@Composable
private fun PetHeroCard(
    pet: PetUiState,
    lastUpdated: String?,
    onAddPromoClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.linearGradient(listOf(DataraHeroStart, DataraHeroMiddle, DataraCardBg)),
                RoundedCornerShape(24.dp)
            )
            .border(1.dp, DataraNeonBlue.copy(alpha = 0.28f), RoundedCornerShape(24.dp))
            .padding(22.dp)
    ) {
        Column {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.dashboard_pet_label),
                    style = MaterialTheme.typography.labelSmall,
                    color = DataraBlueSoft,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.7.sp,
                    modifier = Modifier.weight(1f)
                )
                StatusDot(text = pet.statusLabel(), color = pet.statusColor())
            }

            Spacer(Modifier.height(22.dp))

            when (pet) {
                is PetUiState.Available -> {
                    Text(
                        text = pet.timeRemaining,
                        color = DataraTextPrimary,
                        fontSize = 42.sp,
                        lineHeight = 48.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-1).sp
                    )
                    Text(
                        text = stringResource(R.string.dashboard_pet_remaining, pet.exhaustionTime),
                        style = MaterialTheme.typography.bodyMedium,
                        color = DataraTextSecondary
                    )
                }
                is PetUiState.Collecting -> {
                    Text(stringResource(R.string.dashboard_pet_collecting_title), style = MaterialTheme.typography.headlineMedium, color = DataraTextPrimary)
                    Text(
                        stringResource(
                            R.string.dashboard_pet_collecting_body,
                            pet.daysCollected.coerceAtMost(pet.daysRequired),
                            pet.daysRequired
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = DataraTextSecondary
                    )
                }
                PetUiState.Loading -> {
                    Text(stringResource(R.string.dashboard_pet_loading_title), style = MaterialTheme.typography.headlineMedium, color = DataraTextPrimary)
                    Text(stringResource(R.string.dashboard_pet_loading_body), style = MaterialTheme.typography.bodyMedium, color = DataraTextSecondary)
                }
                is PetUiState.Error -> {
                    Text(stringResource(R.string.dashboard_pet_error_title), style = MaterialTheme.typography.headlineMedium, color = DataraTextPrimary)
                    Text(pet.message, style = MaterialTheme.typography.bodyMedium, color = DataraTextSecondary)
                }
                PetUiState.NoPromo -> {
                    Text(stringResource(R.string.dashboard_pet_no_promo_title), style = MaterialTheme.typography.headlineMedium, color = DataraTextPrimary)
                    Text(stringResource(R.string.dashboard_pet_no_promo_body), style = MaterialTheme.typography.bodyMedium, color = DataraTextSecondary)
                    Spacer(Modifier.height(18.dp))
                    Button(
                        onClick = onAddPromoClick,
                        colors = ButtonDefaults.buttonColors(containerColor = DataraPrimaryBlue)
                    ) {
                        Text(stringResource(R.string.dashboard_add_active_promo))
                    }
                }
            }

            if (lastUpdated != null) {
                Spacer(Modifier.height(18.dp))
                Text(
                    text = stringResource(R.string.dashboard_updated, lastUpdated),
                    style = MaterialTheme.typography.labelSmall,
                    color = DataraTextSecondary
                )
            }
        }
    }
}

@Composable
private fun DataBalanceCard(balance: DataBalanceUiModel?, onAddPromoClick: () -> Unit) {
    DashboardCard {
        SectionHeading(
            title = stringResource(R.string.dashboard_balance_title),
            trailing = if (balance?.isEstimated == true) stringResource(R.string.dashboard_estimated) else null
        )
        Spacer(Modifier.height(18.dp))

        if (balance == null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                EmptyMetricGlyph(stringResource(R.string.dashboard_no_balance_description))
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.dashboard_balance_unavailable), style = MaterialTheme.typography.titleMedium, color = DataraTextPrimary)
                    Text(stringResource(R.string.dashboard_balance_setup_body), style = MaterialTheme.typography.bodyMedium, color = DataraTextSecondary)
                }
                TextButton(onClick = onAddPromoClick) {
                    Text(stringResource(R.string.dashboard_setup), color = DataraBlueSoft)
                }
            }
        } else {
            val consumedFraction = if (balance.totalGb > 0) {
                (balance.consumedGb / balance.totalGb).toFloat().coerceIn(0f, 1f)
            } else 0f
            val remaining = formatGb(balance.remainingGb)
            val consumed = formatGb(balance.consumedGb)
            val total = formatGb(balance.totalGb)

            Row(verticalAlignment = Alignment.Bottom) {
                Text(remaining, style = MaterialTheme.typography.displayLarge, color = DataraTextPrimary)
                Text(
                    stringResource(R.string.dashboard_remaining_suffix),
                    style = MaterialTheme.typography.bodyMedium,
                    color = DataraTextSecondary,
                    modifier = Modifier.padding(bottom = 5.dp)
                )
            }
            Spacer(Modifier.height(14.dp))
            BalanceProgress(progress = consumedFraction)
            Spacer(Modifier.height(10.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(stringResource(R.string.dashboard_used_format, consumed), style = MaterialTheme.typography.labelSmall, color = DataraTextSecondary)
                Text(stringResource(R.string.dashboard_total_format, total), style = MaterialTheme.typography.labelSmall, color = DataraTextSecondary)
            }
        }
    }
}

@Composable
private fun LiveNetworkSection(network: NetworkUiModel) {
    Column {
        SectionHeading(stringResource(R.string.dashboard_live_network))
        Spacer(Modifier.height(10.dp))
        BoxWithConstraints {
            if (maxWidth < 500.dp) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    NetworkSignalCard(network, Modifier.fillMaxWidth())
                    NetworkSpeedCard(network, Modifier.fillMaxWidth())
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    NetworkSignalCard(network, Modifier.weight(1f))
                    NetworkSpeedCard(network, Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun NetworkSignalCard(network: NetworkUiModel, modifier: Modifier) {
    NetworkMetricCard(
        label = stringResource(R.string.dashboard_carrier_signal),
        value = network.carrier ?: stringResource(R.string.dashboard_detecting),
        supporting = network.signalLabel ?: stringResource(R.string.dashboard_waiting_signal),
        modifier = modifier,
        glyph = {
            SignalGlyph(
                level = network.signalLevel,
                description = stringResource(R.string.dashboard_signal_description, network.signalLevel)
            )
        }
    )
}

@Composable
private fun NetworkSpeedCard(network: NetworkUiModel, modifier: Modifier) {
    NetworkMetricCard(
        label = stringResource(R.string.dashboard_live_speed),
        value = network.speed ?: "—",
        supporting = if (network.isMobileDataActive) {
            stringResource(R.string.dashboard_mobile_active)
        } else {
            stringResource(R.string.dashboard_no_reading)
        },
        modifier = modifier,
        glyph = { SpeedGlyph(stringResource(R.string.dashboard_speed_description)) }
    )
}

@Composable
private fun NetworkMetricCard(
    label: String,
    value: String,
    supporting: String,
    modifier: Modifier,
    glyph: @Composable () -> Unit
) {
    Column(
        modifier = modifier
            .background(DataraCardBg, RoundedCornerShape(18.dp))
            .border(1.dp, DataraInputBorder, RoundedCornerShape(18.dp))
            .padding(16.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = DataraTextSecondary, fontSize = 10.sp)
            glyph()
        }
        Spacer(Modifier.height(14.dp))
        Text(value, style = MaterialTheme.typography.titleMedium, color = DataraTextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(supporting, style = MaterialTheme.typography.labelSmall, color = DataraTextSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun ActivePromoCard(promo: ActivePromoUiModel?, onAddPromoClick: () -> Unit) {
    DashboardCard {
        SectionHeading(stringResource(R.string.dashboard_active_promo), trailing = promo?.carrier)
        Spacer(Modifier.height(16.dp))
        if (promo == null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .background(DataraNeonBlue.copy(alpha = 0.12f), RoundedCornerShape(14.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("+", color = DataraBlueSoft, fontSize = 26.sp, fontWeight = FontWeight.Light)
                }
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.dashboard_no_active_promo), style = MaterialTheme.typography.titleMedium, color = DataraTextPrimary)
                    Text(stringResource(R.string.dashboard_choose_promo), style = MaterialTheme.typography.bodyMedium, color = DataraTextSecondary)
                }
                TextButton(onClick = onAddPromoClick) {
                    Text(stringResource(R.string.dashboard_add), color = DataraBlueSoft)
                }
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(50.dp)
                        .background(DataraNeonBlue.copy(alpha = 0.14f), RoundedCornerShape(15.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(promo.carrier.take(2).uppercase(), color = DataraBlueSoft, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(promo.name, style = MaterialTheme.typography.titleMedium, color = DataraTextPrimary)
                    Text(stringResource(R.string.dashboard_expires, promo.expiresOn), style = MaterialTheme.typography.bodyMedium, color = DataraTextSecondary)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(promo.expiresIn, style = MaterialTheme.typography.labelLarge, color = DataraWarning)
                    Text(stringResource(R.string.dashboard_validity_remaining), style = MaterialTheme.typography.labelSmall, color = DataraTextSecondary)
                }
            }
        }
    }
}

@Composable
private fun UsageInsightCard(insight: DashboardInsightUiModel) {
    val accent = if (insight.isPositive) DataraNeonGreen else DataraWarning
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(accent.copy(alpha = 0.08f), RoundedCornerShape(18.dp))
            .border(1.dp, accent.copy(alpha = 0.22f), RoundedCornerShape(18.dp))
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(36.dp).background(accent.copy(alpha = 0.14f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text("i", color = accent, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(12.dp))
        Column {
            Text(insight.title, style = MaterialTheme.typography.titleMedium, color = DataraTextPrimary)
            Text(insight.message, style = MaterialTheme.typography.bodyMedium, color = DataraTextSecondary)
        }
    }
}

@Composable
private fun DashboardCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(DataraCardBg, RoundedCornerShape(20.dp))
            .border(1.dp, DataraInputBorder, RoundedCornerShape(20.dp))
            .padding(18.dp),
        content = content
    )
}

@Composable
private fun SectionHeading(title: String, trailing: String? = null) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = DataraTextPrimary, modifier = Modifier.weight(1f))
        if (trailing != null) {
            Text(
                trailing,
                style = MaterialTheme.typography.labelSmall,
                color = DataraBlueSoft,
                modifier = Modifier
                    .background(DataraNeonBlue.copy(alpha = 0.1f), RoundedCornerShape(20.dp))
                    .padding(horizontal = 9.dp, vertical = 4.dp)
            )
        }
    }
}

@Composable
private fun BalanceProgress(progress: Float) {
    val safeProgress = progress.coerceIn(0f, 1f)
    val progressColor = when {
        safeProgress >= .9f -> DataraError
        safeProgress >= .75f -> DataraWarning
        else -> DataraNeonGreen
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(9.dp)
            .background(DataraDivider, CircleShape)
            .semantics { progressBarRangeInfo = ProgressBarRangeInfo(safeProgress, 0f..1f) }
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(safeProgress)
                .height(9.dp)
                .background(Brush.horizontalGradient(listOf(DataraPrimaryBlue, progressColor)), CircleShape)
        )
    }
}

@Composable
private fun StatusDot(text: String, color: Color) {
    Row(
        modifier = Modifier
            .background(color.copy(alpha = .1f), RoundedCornerShape(20.dp))
            .padding(horizontal = 8.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(6.dp).background(color, CircleShape))
        Spacer(Modifier.width(5.dp))
        Text(text, style = MaterialTheme.typography.labelSmall, color = color, fontSize = 10.sp)
    }
}

@Composable
private fun BellGlyph(showUnread: Boolean, contentDescription: String) {
    Canvas(Modifier.size(22.dp).semantics { this.contentDescription = contentDescription }) {
        val strokeWidth = 1.8.dp.toPx()
        drawArc(
            color = DataraTextPrimary,
            startAngle = 195f,
            sweepAngle = 150f,
            useCenter = false,
            topLeft = Offset(size.width * .22f, size.height * .2f),
            size = Size(size.width * .56f, size.height * .62f),
            style = Stroke(strokeWidth, cap = StrokeCap.Round)
        )
        drawLine(DataraTextPrimary, Offset(size.width * .22f, size.height * .72f), Offset(size.width * .78f, size.height * .72f), strokeWidth, StrokeCap.Round)
        drawCircle(DataraTextPrimary, radius = size.width * .07f, center = Offset(size.width * .5f, size.height * .84f))
        if (showUnread) {
            drawCircle(DataraNeonGreen, radius = size.width * .12f, center = Offset(size.width * .8f, size.height * .2f))
        }
    }
}

@Composable
private fun SignalGlyph(level: Int, description: String) {
    Canvas(Modifier.size(22.dp).semantics { contentDescription = description }) {
        val barWidth = size.width * .12f
        val gap = size.width * .09f
        repeat(4) { index ->
            val height = size.height * (.25f + index * .18f)
            drawRoundRect(
                color = if (index < level) DataraNeonGreen else DataraDivider,
                topLeft = Offset(index * (barWidth + gap), size.height - height),
                size = Size(barWidth, height),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(barWidth / 2)
            )
        }
    }
}

@Composable
private fun SpeedGlyph(description: String) {
    Canvas(Modifier.size(22.dp).semantics { contentDescription = description }) {
        drawArc(DataraBlueSoft, 190f, 160f, false, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
        drawLine(DataraBlueSoft, center, Offset(size.width * .72f, size.height * .34f), 2.dp.toPx(), StrokeCap.Round)
        drawCircle(DataraBlueSoft, radius = 2.dp.toPx(), center = center)
    }
}

@Composable
private fun EmptyMetricGlyph(description: String) {
    Canvas(Modifier.size(38.dp).semantics { contentDescription = description }) {
        drawCircle(DataraDivider, radius = size.minDimension / 2, style = Stroke(3.dp.toPx()))
        drawArc(DataraNeonBlue, -90f, 70f, false, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round))
    }
}

@Composable
private fun PetUiState.statusLabel(): String = when (this) {
    is PetUiState.Available -> modelLabel
    is PetUiState.Collecting -> stringResource(R.string.dashboard_status_learning)
    PetUiState.Loading -> stringResource(R.string.dashboard_status_updating)
    is PetUiState.Error -> stringResource(R.string.status_unavailable)
    PetUiState.NoPromo -> stringResource(R.string.dashboard_status_setup)
}

private fun PetUiState.statusColor(): Color = when (this) {
    is PetUiState.Available -> DataraGreenSoft
    is PetUiState.Collecting, PetUiState.Loading -> DataraWarning
    is PetUiState.Error -> DataraError
    PetUiState.NoPromo -> DataraBlueSoft
}

private fun String.toDisplayName(): String =
    split('.', '_', '-').filter { it.isNotBlank() }.joinToString(" ") {
        it.replaceFirstChar { char -> if (char.isLowerCase()) char.titlecase(Locale.getDefault()) else char.toString() }
    }

@Composable
private fun formatGb(value: Double): String = stringResource(
    R.string.data_amount_gb,
    String.format(Locale.US, "%.1f", value)
)

@Composable
private fun previewDashboardState(): DashboardUiState = DashboardUiState(
    pet = PetUiState.Available(
        timeRemaining = stringResource(R.string.dashboard_preview_time_remaining),
        exhaustionTime = stringResource(R.string.dashboard_preview_exhaustion),
        modelLabel = stringResource(R.string.dashboard_preview_global_model)
    ),
    balance = DataBalanceUiModel(remainingGb = 4.2, totalGb = 8.0, consumedGb = 3.8),
    network = NetworkUiModel(
        carrier = "Globe",
        signalLabel = stringResource(R.string.dashboard_preview_signal),
        signalLevel = 4,
        speed = stringResource(R.string.dashboard_preview_speed),
        isMobileDataActive = true
    ),
    promo = ActivePromoUiModel(
        name = "Go+99",
        carrier = "Globe",
        expiresIn = stringResource(R.string.dashboard_preview_expiry_remaining),
        expiresOn = stringResource(R.string.dashboard_preview_expiry_date)
    ),
    insight = DashboardInsightUiModel(
        title = stringResource(R.string.dashboard_insight_preview_title),
        message = stringResource(R.string.dashboard_insight_preview_body)
    ),
    unreadNotificationCount = 2,
    lastUpdated = stringResource(R.string.dashboard_preview_updated)
)

@Preview(showBackground = true, backgroundColor = 0xFF0C101A, heightDp = 920)
@Composable
private fun DashboardReadyPreview() {
    DATAraTheme {
        DashboardContent(
            userLabel = "Charlie",
            state = previewDashboardState(),
            onAddPromoClick = {},
            onNotificationsClick = {},
            onHistoryClick = {},
            onSettingsClick = {},
            onSignOutClick = {}
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0C101A, heightDp = 920)
@Composable
private fun DashboardEmptyPreview() {
    DATAraTheme {
        DashboardContent(
            userLabel = "Alex",
            state = DashboardUiState(),
            onAddPromoClick = {},
            onNotificationsClick = {},
            onHistoryClick = {},
            onSettingsClick = {},
            onSignOutClick = {}
        )
    }
}

