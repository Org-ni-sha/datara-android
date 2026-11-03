package com.capstone.datara.ui.theme

import androidx.compose.runtime.Composable

/**
 * App-wide theme. Authentication, launch and the signed-in quota screens share one light
 * scheme ([QuotaTheme]): off-white background, white surfaces and dark navy accents. There is
 * no dark theme or toggle.
 */
@Composable
fun DATAraTheme(content: @Composable () -> Unit) {
    QuotaTheme(content)
}
