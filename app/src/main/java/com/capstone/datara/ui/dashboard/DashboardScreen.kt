package com.capstone.datara.ui.dashboard

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import androidx.compose.ui.text.style.TextAlign
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
import com.capstone.datara.ui.theme.DataraError
import com.capstone.datara.ui.theme.DataraInsightBorder
import com.capstone.datara.ui.theme.DataraInsightChip
import com.capstone.datara.ui.theme.DataraInsightSurface
import com.capstone.datara.ui.theme.DataraProgressFrame
import com.capstone.datara.ui.theme.DataraProgressStart
import com.capstone.datara.ui.theme.DataraPrimaryBlue
import com.capstone.datara.ui.theme.DataraSuccess
import java.util.Locale

sealed interface PetUiState {
    data object NoPromo : PetUiState
    data class Collecting(val daysCollected: Int, val daysRequired: Int = 7) : PetUiState
    data class Available(val timeRemaining: String, val exhaustionTime: String, val modelLabel: String) : PetUiState
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

data class ActivePromoUiModel(val name: String, val carrier: String, val expiresIn: String, val expiresOn: String)
data class DashboardInsightUiModel(val title: String, val message: String, val isPositive: Boolean = true)

private enum class DashboardGlyph { SIM, SIGNAL, SPEED }

data class DashboardUiState(
    val pet: PetUiState = PetUiState.NoPromo,
    val balance: DataBalanceUiModel? = null,
    val network: NetworkUiModel = NetworkUiModel(),
    val promo: ActivePromoUiModel? = null,
    val insight: DashboardInsightUiModel? = null,
    val unreadNotificationCount: Int = 0,
    val lastUpdated: String? = null,
    val previewNotice: String? = null,
    val dailyAverageGb: Double? = null
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
        userLabel = viewModel.currentUserEmail?.substringBefore("@")?.toDisplayName()
            ?: stringResource(R.string.profile_default_name),
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
        modifier = modifier.fillMaxSize(),
    ) { innerPadding, _ ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 14.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            item {
                DashboardHeader(
                    userLabel = userLabel,
                    lastUpdated = state.lastUpdated,
                    unreadCount = state.unreadNotificationCount,
                    onNotificationsClick = onNotificationsClick,
                    onSignOutClick = { showSignOutDialog = true }
                )
            }
            item { DashboardHero(state = state, onAddPromoClick = onAddPromoClick) }
            item { LiveNetworkHub(state.network) }
            item { PredictionStatusCard(state.pet) }
            item {
                PromoGreetingCard(
                    userLabel = userLabel,
                    promo = state.promo,
                    onAddPromoClick = onAddPromoClick
                )
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
    lastUpdated: String?,
    unreadCount: Int,
    onNotificationsClick: () -> Unit,
    onSignOutClick: () -> Unit
) {
    var profileMenuExpanded by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth().widthIn(max = 560.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            shape = RoundedCornerShape(11.dp),
            color = MaterialTheme.colorScheme.surface,
            border = androidx.compose.foundation.BorderStroke(1.dp, DataraPrimaryBlue)
        ) {
            Text(
                text = userLabel.take(2).uppercase(),
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 10.sp,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 11.dp)
            )
        }
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    userLabel,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.width(4.dp))
                Box(Modifier.size(5.dp).background(DataraSuccess, CircleShape))
            }
            Text(
                lastUpdated ?: stringResource(R.string.status_local_preview),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 10.sp
            )
        }
        IconButton(
            onClick = onNotificationsClick,
            modifier = Modifier
                .size(40.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(10.dp))
        ) {
            BellGlyph(unreadCount > 0, stringResource(R.string.dashboard_notifications))
        }
        Spacer(Modifier.width(8.dp))
        Box {
            Surface(
                modifier = Modifier.size(40.dp).clickable { profileMenuExpanded = true },
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                border = androidx.compose.foundation.BorderStroke(1.dp, DataraPrimaryBlue)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(userLabel.take(1).uppercase(), color = DataraPrimaryBlue, fontWeight = FontWeight.Black)
                    Box(
                        Modifier.align(Alignment.BottomEnd).padding(3.dp).size(5.dp).background(DataraSuccess, CircleShape)
                    )
                }
            }
            DropdownMenu(
                expanded = profileMenuExpanded,
                onDismissRequest = { profileMenuExpanded = false },
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.dashboard_sign_out), color = MaterialTheme.colorScheme.onSurface) },
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
private fun DashboardHero(state: DashboardUiState, onAddPromoClick: () -> Unit) {
    val balance = state.balance
    val used = balance?.consumedGb
    val total = balance?.totalGb
    val progress = if (balance != null && balance.totalGb > 0) {
        (balance.consumedGb / balance.totalGb).toFloat().coerceIn(0f, 1f)
    } else 0f
    val percentage = (progress * 100).toInt()
    val prediction = when (val pet = state.pet) {
        is PetUiState.Available -> pet.timeRemaining
        is PetUiState.Collecting -> "${pet.daysCollected}/${pet.daysRequired}d"
        PetUiState.Loading -> "..."
        is PetUiState.Error, PetUiState.NoPromo -> "--"
    }
    val healthyPace = state.insight?.isPositive != false
    val predictionFooter = when (val pet = state.pet) {
        is PetUiState.Available -> stringResource(R.string.dashboard_left_until, pet.exhaustionTime)
        else -> stringResource(R.string.dashboard_left)
    }
    val statusLabel = when (state.pet) {
        is PetUiState.Available -> if (balance?.isEstimated == true) {
            stringResource(R.string.dashboard_estimated)
        } else {
            stringResource(R.string.dashboard_optimal)
        }
        is PetUiState.Collecting -> stringResource(R.string.dashboard_status_learning)
        PetUiState.Loading -> stringResource(R.string.dashboard_status_updating)
        is PetUiState.Error -> stringResource(R.string.status_unavailable)
        PetUiState.NoPromo -> stringResource(R.string.dashboard_status_setup)
    }

    ReferenceCard(modifier = Modifier.fillMaxWidth().widthIn(max = 560.dp), corner = 20.dp) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            HeroMetric(
                label = stringResource(R.string.dashboard_total_used),
                value = used?.let { "${it.clean()} GB" } ?: "--",
                footer = total?.let { stringResource(R.string.dashboard_out_of_format, it.clean()) }
                    ?: stringResource(R.string.dashboard_no_promo_short),
                modifier = Modifier.weight(1f)
            )
            HeroMetric(
                label = stringResource(R.string.dashboard_predicted),
                value = prediction,
                footer = predictionFooter,
                modifier = Modifier.weight(1f)
            )
            HeroMetric(
                label = stringResource(R.string.dashboard_daily_average),
                value = state.dailyAverageGb?.let { "${it.clean()} GB" } ?: "--",
                footer = stringResource(R.string.dashboard_per_day),
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(Modifier.height(19.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.dashboard_consumption_rate, percentage),
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Black,
                fontSize = 16.sp
            )
            Spacer(Modifier.width(14.dp))
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceVariant, border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)) {
                Text(statusLabel, fontSize = 9.sp, modifier = Modifier.padding(horizontal = 13.dp, vertical = 2.dp))
            }
        }
        Spacer(Modifier.height(12.dp))
        HeroProgress(progress)
        Spacer(Modifier.height(18.dp))
        if (balance == null) {
            Button(
                onClick = onAddPromoClick,
                modifier = Modifier.align(Alignment.CenterHorizontally).height(42.dp),
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 22.dp),
                colors = ButtonDefaults.buttonColors(containerColor = DataraPrimaryBlue, contentColor = Color.White)
            ) {
                Text(stringResource(R.string.dashboard_add_active_promo), fontWeight = FontWeight.Black, fontSize = 14.sp)
            }
        } else {
            Surface(
                modifier = Modifier.align(Alignment.CenterHorizontally),
                shape = RoundedCornerShape(12.dp),
                color = if (healthyPace) DataraSuccess else DataraError
            ) {
                Text(
                    if (healthyPace) stringResource(R.string.dashboard_usage_pace_normal)
                    else stringResource(R.string.dashboard_usage_pace_high),
                    color = Color.White,
                    fontWeight = FontWeight.Black,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(horizontal = 22.dp, vertical = 11.dp)
                )
            }
        }
    }
}

@Composable
private fun HeroMetric(label: String, value: String, footer: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.height(76.dp),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 7.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 9.sp, maxLines = 1)
            Text(value, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Black, fontSize = 20.sp, maxLines = 1)
            Text(footer, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Medium, fontSize = 8.sp, maxLines = 1)
        }
    }
}

@Composable
private fun HeroProgress(progress: Float) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(30.dp)
            .background(MaterialTheme.colorScheme.background, CircleShape)
            .border(2.dp, DataraProgressFrame, CircleShape)
            .padding(5.dp)
            .semantics { progressBarRangeInfo = ProgressBarRangeInfo(progress, 0f..1f) }
    ) {
        if (progress > 0f) {
            Box(
                Modifier
                    .fillMaxWidth(progress)
                    .height(18.dp)
                    .background(
                        Brush.horizontalGradient(listOf(DataraProgressStart, DataraSuccess)),
                        CircleShape
                    )
            )
        }
        Row(Modifier.fillMaxSize().padding(horizontal = 10.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            repeat(6) { Box(Modifier.width(1.dp).height(9.dp).background(Color.White.copy(alpha = .9f))) }
        }
    }
}

@Composable
private fun LiveNetworkHub(network: NetworkUiModel) {
    ReferenceCard(modifier = Modifier.fillMaxWidth().widthIn(max = 560.dp), corner = 20.dp) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.dashboard_live_network).uppercase(), color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Black, fontSize = 13.sp)
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceVariant) {
                Text(
                    if (network.isMobileDataActive) stringResource(R.string.dashboard_mobile_active) else stringResource(R.string.dashboard_detecting),
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 8.sp,
                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            NetworkTile(DashboardGlyph.SIM, stringResource(R.string.dashboard_detected_carrier), network.carrier ?: "--")
            NetworkTile(DashboardGlyph.SIGNAL, stringResource(R.string.dashboard_carrier_signal), network.signalLabel ?: "--", network.signalLevel)
            NetworkTile(DashboardGlyph.SPEED, stringResource(R.string.dashboard_live_speed), network.speed ?: "--")
        }
    }
}

@Composable
private fun NetworkTile(glyph: DashboardGlyph, label: String, value: String, signalLevel: Int = 0) {
    Column(
        modifier = Modifier
            .width(78.dp)
            .height(78.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(14.dp))
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(14.dp))
            .padding(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Surface(shape = RoundedCornerShape(4.dp), color = MaterialTheme.colorScheme.surface, border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)) {
            Box(Modifier.size(width = 28.dp, height = 25.dp), contentAlignment = Alignment.Center) {
                if (glyph == DashboardGlyph.SIM) {
                    Text("SIM", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Black, fontSize = 8.sp)
                } else {
                    DashboardGlyphIcon(glyph = glyph, color = MaterialTheme.colorScheme.onSurface, signalLevel = signalLevel)
                }
            }
        }
        Text(label, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold, fontSize = 8.sp, textAlign = TextAlign.Center, maxLines = 1)
        Text(
            value,
            color = DataraPrimaryBlue,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 8.sp,
            maxLines = 1
        )
    }
}

@Composable
private fun PredictionStatusCard(pet: PetUiState) {
    val title: String
    val message: String
    when (pet) {
        is PetUiState.Available -> {
            title = stringResource(R.string.dashboard_prediction_ready)
            message = stringResource(
                R.string.dashboard_prediction_ready_body,
                pet.exhaustionTime,
                pet.modelLabel
            )
        }
        is PetUiState.Collecting -> {
            title = stringResource(R.string.dashboard_pet_collecting_title)
            message = stringResource(
                R.string.dashboard_pet_collecting_body,
                pet.daysCollected.coerceAtMost(pet.daysRequired),
                pet.daysRequired
            )
        }
        PetUiState.Loading -> {
            title = stringResource(R.string.dashboard_pet_loading_title)
            message = stringResource(R.string.dashboard_pet_loading_body)
        }
        is PetUiState.Error -> {
            title = stringResource(R.string.dashboard_pet_error_title)
            message = pet.message
        }
        PetUiState.NoPromo -> {
            title = stringResource(R.string.dashboard_pet_no_promo_title)
            message = stringResource(R.string.dashboard_pet_no_promo_body)
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 560.dp)
            .background(DataraInsightSurface, RoundedCornerShape(20.dp))
            .border(1.dp, DataraInsightBorder, RoundedCornerShape(20.dp))
            .padding(14.dp),
        verticalAlignment = Alignment.Top
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surface,
            border = androidx.compose.foundation.BorderStroke(1.dp, DataraInsightBorder)
        ) {
            Text(
                "i",
                color = DataraPrimaryBlue,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center,
                modifier = Modifier.width(36.dp).padding(vertical = 8.dp)
            )
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.dashboard_prediction_status),
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Black,
                    fontSize = 12.sp,
                    modifier = Modifier.weight(1f)
                )
                Surface(shape = CircleShape, color = DataraInsightChip) {
                    Text(
                        title,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 8.sp,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(message, color = MaterialTheme.colorScheme.onSurface, fontSize = 10.sp, lineHeight = 14.sp)
        }
    }
}

@Composable
private fun PromoGreetingCard(userLabel: String, promo: ActivePromoUiModel?, onAddPromoClick: () -> Unit) {
    ReferenceCard(modifier = Modifier.fillMaxWidth().widthIn(max = 560.dp), corner = 20.dp) {
        Text(stringResource(R.string.dashboard_greeting_evening, userLabel), color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Black, fontSize = 15.sp)
        Text(stringResource(R.string.dashboard_promo_support), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 9.sp)
        Spacer(Modifier.height(10.dp))
        Surface(
            modifier = Modifier.fillMaxWidth().clickable(onClick = onAddPromoClick),
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.surface,
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        ) {
            if (promo == null) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.dashboard_no_active_promo), color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Text(stringResource(R.string.dashboard_add), color = DataraPrimaryBlue, fontWeight = FontWeight.Bold)
                }
            } else {
                Column(Modifier.padding(10.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(stringResource(R.string.dashboard_current_active_promo), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 8.sp)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(5.dp).background(DataraSuccess, CircleShape))
                            Spacer(Modifier.width(4.dp))
                            Text(stringResource(R.string.dashboard_active_status), color = DataraSuccess, fontSize = 8.sp)
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                            Column(Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(promo.carrier.take(2).uppercase(), color = DataraPrimaryBlue, fontWeight = FontWeight.Black)
                                Text(promo.name, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 7.sp)
                            }
                        }
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(promo.name, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Black, fontSize = 15.sp)
                            Text(stringResource(R.string.dashboard_expires, promo.expiresOn), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 9.sp)
                        }
                        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surface, border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary)) {
                            Text(stringResource(R.string.dashboard_view_details), color = DataraPrimaryBlue, fontWeight = FontWeight.Bold, fontSize = 8.sp, modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReferenceCard(modifier: Modifier = Modifier, corner: androidx.compose.ui.unit.Dp, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(corner),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        shadowElevation = 5.dp
    ) {
        Column(Modifier.padding(horizontal = 18.dp, vertical = 20.dp), content = content)
    }
}

@Composable
private fun DashboardGlyphIcon(
    glyph: DashboardGlyph,
    color: Color,
    modifier: Modifier = Modifier.size(18.dp),
    signalLevel: Int = 0
) {
    val inactiveColor = MaterialTheme.colorScheme.onSurfaceVariant
    Canvas(modifier) {
        val line = 1.8.dp.toPx()
        val stroke = Stroke(width = line, cap = StrokeCap.Round)
        when (glyph) {
            DashboardGlyph.SIGNAL -> repeat(4) { index ->
                val barWidth = size.width * .12f
                val gap = size.width * .1f
                val height = size.height * (.22f + index * .18f)
                drawRoundRect(
                    color = if (index < signalLevel.coerceIn(0, 4)) DataraSuccess else inactiveColor,
                    topLeft = Offset(size.width * .13f + index * (barWidth + gap), size.height - height - size.height * .12f),
                    size = Size(barWidth, height),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(barWidth / 2)
                )
            }
            DashboardGlyph.SPEED -> {
                drawArc(color, 190f, 160f, false, style = stroke)
                drawLine(color, center, Offset(size.width * .72f, size.height * .32f), line, StrokeCap.Round)
                drawCircle(color, line, center)
            }
            DashboardGlyph.SIM -> Unit
        }
    }
}

@Composable
private fun BellGlyph(showUnread: Boolean, description: String) {
    val iconColor = MaterialTheme.colorScheme.onSurface
    Canvas(Modifier.size(21.dp).semantics { contentDescription = description }) {
        val line = 1.7.dp.toPx()
        drawArc(iconColor, 195f, 150f, false, Offset(size.width * .22f, size.height * .2f), Size(size.width * .56f, size.height * .62f), style = Stroke(line, cap = StrokeCap.Round))
        drawLine(iconColor, Offset(size.width * .22f, size.height * .72f), Offset(size.width * .78f, size.height * .72f), line, StrokeCap.Round)
        drawCircle(iconColor, size.width * .06f, Offset(size.width * .5f, size.height * .84f))
        if (showUnread) drawCircle(Color(0xFF0CAF3B), size.width * .11f, Offset(size.width * .82f, size.height * .18f))
    }
}

private fun Double.clean(): String = if (this % 1.0 == 0.0) String.format(Locale.US, "%.0f", this) else String.format(Locale.US, "%.1f", this)

private fun String.toDisplayName(): String = split('.', '_', '-').filter(String::isNotBlank).joinToString(" ") {
    it.replaceFirstChar { char -> if (char.isLowerCase()) char.titlecase(Locale.getDefault()) else char.toString() }
}

@Composable
private fun previewDashboardState() = DashboardUiState(
    pet = PetUiState.Available("8hrs", "Tonight", "Global model"),
    balance = DataBalanceUiModel(4.2, 14.0, 9.8),
    promo = ActivePromoUiModel("EasySurf 99", "TM", "3 days", "July 7, 2026"),
    insight = DashboardInsightUiModel("Smart tip", "Your current streaming pattern suggests you’ll hit your daily cap by an hour; use only one app at a time to extend data!"),
    unreadNotificationCount = 1,
    lastUpdated = "04/08/2026 • 11:59AM",
    dailyAverageGb = 1.5
)

@Preview(showBackground = true, backgroundColor = 0xFFFAFAFA, heightDp = 920)
@Composable
private fun DashboardReferencePreview() {
    DATAraTheme {
        DashboardContent("Charlie", previewDashboardState(), {}, {}, {}, {}, {})
    }
}
