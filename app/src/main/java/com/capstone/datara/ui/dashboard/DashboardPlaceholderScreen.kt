package com.capstone.datara.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.capstone.datara.ui.auth.AuthViewModel
import com.capstone.datara.ui.auth.components.DataraLogo
import com.capstone.datara.ui.auth.components.DataraPrimaryButton
import com.capstone.datara.ui.theme.DATAraTheme
import com.capstone.datara.ui.theme.DataraDarkBg
import com.capstone.datara.ui.theme.DataraTextPrimary
import com.capstone.datara.ui.theme.DataraTextSecondary

/**
 * Placeholder for the real dashboard (roadmap D5).
 *
 * D1 needs a signed-in destination for auto-login to be verifiable end to end — without one,
 * a restored session lands on an empty composable and there is no way to tell working session
 * persistence from a broken navigation graph. It deliberately implements none of D5's content
 * (PET hero card, balance gauge, live network strip, active promo card, floating bottom nav);
 * replace this whole file when D5 is built.
 */
@Composable
fun DashboardPlaceholderScreen(
    onSignedOut: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AuthViewModel = hiltViewModel()
) {
    DashboardPlaceholderContent(
        signedInEmail = viewModel.currentUserEmail,
        onSignOutClick = { viewModel.signOut(onComplete = onSignedOut) },
        modifier = modifier
    )
}

@Composable
private fun DashboardPlaceholderContent(
    signedInEmail: String?,
    onSignOutClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DataraDarkBg)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        DataraLogo(size = 88.dp)

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "You're signed in",
            style = MaterialTheme.typography.titleLarge,
            color = DataraTextPrimary
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = signedInEmail ?: "Session restored",
            style = MaterialTheme.typography.bodyMedium,
            color = DataraTextSecondary,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "The dashboard lands here in roadmap phase D5.",
            style = MaterialTheme.typography.bodyMedium,
            color = DataraTextSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(40.dp))

        DataraPrimaryButton(
            text = "Sign Out",
            onClick = onSignOutClick
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0C101A)
@Composable
private fun DashboardPlaceholderPreview() {
    DATAraTheme {
        DashboardPlaceholderContent(
            signedInEmail = "student@ustp.edu.ph",
            onSignOutClick = {}
        )
    }
}
