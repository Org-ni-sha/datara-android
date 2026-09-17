package com.capstone.datara.ui.splash

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Spacer
import com.capstone.datara.ui.auth.components.DataraLogo
import com.capstone.datara.ui.theme.DATAraTheme
import com.capstone.datara.ui.theme.DataraDarkBg
import com.capstone.datara.ui.theme.DataraPrimaryBlue
import com.capstone.datara.ui.theme.DataraTextSecondary

/**
 * Shown while Supabase restores any persisted session. Without this the app would flash the
 * login screen for a moment before auto-login resolved, which reads as a bug to the user.
 */
@Composable
fun SplashScreen(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DataraDarkBg),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        DataraLogo(size = 96.dp)

        Spacer(modifier = Modifier.height(32.dp))

        CircularProgressIndicator(
            modifier = Modifier.size(28.dp),
            color = DataraPrimaryBlue,
            strokeWidth = 3.dp
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Restoring your session…",
            style = MaterialTheme.typography.bodyMedium,
            color = DataraTextSecondary
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0C101A)
@Composable
private fun SplashScreenPreview() {
    DATAraTheme {
        SplashScreen()
    }
}
