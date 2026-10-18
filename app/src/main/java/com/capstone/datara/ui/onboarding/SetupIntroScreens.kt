package com.capstone.datara.ui.onboarding

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.capstone.datara.R
import com.capstone.datara.ui.components.DataraNoticeCard
import com.capstone.datara.ui.components.DataraPageHeader
import com.capstone.datara.ui.components.DataraStatusBadge
import com.capstone.datara.ui.components.DataraSurfaceCard
import com.capstone.datara.ui.theme.DATAraTheme
import com.capstone.datara.ui.theme.DataraBlueSoft
import com.capstone.datara.ui.theme.DataraDarkBg
import com.capstone.datara.ui.theme.DataraInputBorder
import com.capstone.datara.ui.theme.DataraNeonBlue
import com.capstone.datara.ui.theme.DataraTextPrimary
import com.capstone.datara.ui.theme.DataraTextSecondary
import com.capstone.datara.ui.theme.DataraWarning

enum class FrontendConnectionStatus {
    NOT_GRANTED,
    NEEDS_REVIEW,
    UNAVAILABLE
}

data class UsageAccessUiState(
    val status: FrontendConnectionStatus = FrontendConnectionStatus.NOT_GRANTED,
    val isOnboarding: Boolean = true
)

data class NotificationIntroUiState(
    val status: FrontendConnectionStatus = FrontendConnectionStatus.NOT_GRANTED,
    val isOnboarding: Boolean = true
)

@Composable
fun UsageAccessScreen(
    onBack: () -> Unit,
    onContinue: () -> Unit,
    onNotNow: () -> Unit,
    modifier: Modifier = Modifier,
    state: UsageAccessUiState = UsageAccessUiState()
) {
    var reviewAttempted by rememberSaveable { mutableStateOf(state.status == FrontendConnectionStatus.UNAVAILABLE) }

    SetupIntroLayout(
        modifier = modifier,
        header = {
            DataraPageHeader(
                eyebrow = stringResource(R.string.usage_access_eyebrow),
                title = stringResource(R.string.usage_access_title),
                subtitle = stringResource(R.string.usage_access_subtitle),
                onBack = onBack,
                backContentDescription = stringResource(R.string.action_back),
                trailingContent = {
                    DataraStatusBadge(
                        text = when {
                            reviewAttempted -> stringResource(R.string.status_unavailable)
                            state.status == FrontendConnectionStatus.NEEDS_REVIEW -> stringResource(R.string.status_needs_review)
                            else -> stringResource(R.string.status_not_granted)
                        },
                        color = DataraWarning
                    )
                }
            )
        },
        body = {
            SetupExplanationCard(
                title = stringResource(R.string.usage_access_special_title),
                body = stringResource(R.string.usage_access_special_body),
                step = "1"
            )
            SetupExplanationCard(
                title = stringResource(R.string.usage_access_measure_title),
                body = stringResource(R.string.usage_access_measure_body),
                step = "2"
            )
            SetupExplanationCard(
                title = stringResource(R.string.usage_access_privacy_title),
                body = stringResource(R.string.usage_access_privacy_body),
                step = "3"
            )

            AnimatedVisibility(visible = reviewAttempted) {
                DataraNoticeCard(
                    title = stringResource(R.string.usage_access_recovery_title),
                    message = stringResource(R.string.usage_access_recovery_body),
                    accent = DataraWarning,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
                )
            }
        },
        primaryLabel = if (state.isOnboarding) {
            stringResource(R.string.usage_access_continue)
        } else {
            stringResource(R.string.usage_access_review)
        },
        onPrimary = {
            if (state.isOnboarding) onContinue() else reviewAttempted = true
        },
        secondaryLabel = stringResource(R.string.action_not_now),
        onSecondary = onNotNow
    )
}

@Composable
fun NotificationIntroScreen(
    onBack: () -> Unit,
    onContinue: () -> Unit,
    onNotNow: () -> Unit,
    modifier: Modifier = Modifier,
    state: NotificationIntroUiState = NotificationIntroUiState()
) {
    var reviewAttempted by rememberSaveable { mutableStateOf(state.status == FrontendConnectionStatus.UNAVAILABLE) }

    SetupIntroLayout(
        modifier = modifier,
        header = {
            DataraPageHeader(
                eyebrow = stringResource(R.string.notification_intro_eyebrow),
                title = stringResource(R.string.notification_intro_title),
                subtitle = stringResource(R.string.notification_intro_subtitle),
                onBack = onBack,
                backContentDescription = stringResource(R.string.action_back),
                trailingContent = {
                    DataraStatusBadge(
                        text = stringResource(R.string.status_not_connected),
                        color = DataraWarning
                    )
                }
            )
        },
        body = {
            SetupExplanationCard(
                title = stringResource(R.string.notification_intro_pet_title),
                body = stringResource(R.string.notification_intro_pet_body),
                step = "1"
            )
            SetupExplanationCard(
                title = stringResource(R.string.notification_intro_milestone_title),
                body = stringResource(R.string.notification_intro_milestone_body),
                step = "2"
            )
            SetupExplanationCard(
                title = stringResource(R.string.notification_intro_tracking_title),
                body = stringResource(R.string.notification_intro_tracking_body),
                step = "3"
            )

            AnimatedVisibility(visible = reviewAttempted) {
                DataraNoticeCard(
                    title = stringResource(R.string.notification_intro_unavailable_title),
                    message = stringResource(R.string.notification_intro_unavailable_body),
                    accent = DataraWarning,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
                )
            }
        },
        primaryLabel = if (state.isOnboarding) {
            stringResource(R.string.notification_intro_continue)
        } else {
            stringResource(R.string.notification_intro_review)
        },
        onPrimary = {
            if (state.isOnboarding) onContinue() else reviewAttempted = true
        },
        secondaryLabel = stringResource(R.string.action_not_now),
        onSecondary = onNotNow
    )
}

@Composable
private fun SetupIntroLayout(
    header: @Composable () -> Unit,
    body: @Composable () -> Unit,
    primaryLabel: String,
    onPrimary: () -> Unit,
    secondaryLabel: String,
    onSecondary: () -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = DataraDarkBg
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 720.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                header()
                Spacer(Modifier.height(4.dp))
                body()
                Spacer(Modifier.height(6.dp))
                Button(
                    onClick = onPrimary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = DataraNeonBlue)
                ) {
                    Text(primaryLabel)
                }
                OutlinedButton(
                    onClick = onSecondary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, DataraInputBorder)
                ) {
                    Text(secondaryLabel, color = DataraBlueSoft)
                }
                Spacer(Modifier.height(12.dp))
            }
        }
    }
}

@Composable
private fun SetupExplanationCard(title: String, body: String, step: String) {
    DataraSurfaceCard {
        DataraStatusBadge(text = step, color = DataraBlueSoft)
        Spacer(Modifier.height(12.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, color = DataraTextPrimary)
        Spacer(Modifier.height(5.dp))
        Text(body, style = MaterialTheme.typography.bodyMedium, color = DataraTextSecondary)
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0C101A, heightDp = 900)
@Composable
private fun UsageAccessPreview() {
    DATAraTheme {
        UsageAccessScreen(onBack = {}, onContinue = {}, onNotNow = {})
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0C101A, heightDp = 900)
@Composable
private fun UsageAccessUnavailablePreview() {
    DATAraTheme {
        UsageAccessScreen(
            onBack = {},
            onContinue = {},
            onNotNow = {},
            state = UsageAccessUiState(
                status = FrontendConnectionStatus.UNAVAILABLE,
                isOnboarding = false
            )
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0C101A, heightDp = 900)
@Composable
private fun NotificationIntroPreview() {
    DATAraTheme {
        NotificationIntroScreen(onBack = {}, onContinue = {}, onNotNow = {})
    }
}

