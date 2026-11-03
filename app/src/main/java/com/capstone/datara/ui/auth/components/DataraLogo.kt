package com.capstone.datara.ui.auth.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.capstone.datara.R

/**
 * [DataraLogo] size at which the mark matches the native splash icon exactly, so the session
 * loading screen and the welcome intro can take over from the system splash without a jump.
 * The splash draws `datara_splash_symbol` at 288dp, where the mark spans 752 × 0.58 of 1024
 * viewport units ≈ 122.7dp; in `datara_brand_symbol.png` the mark fills 866/1024 of the image,
 * so 122.7 / 0.846 ≈ 145dp. Both carry the same optical-centering offset. Recompute this if
 * either asset's scale changes.
 */
val SplashMatchedLogoSize = 145.dp

/** Supplied transparent brand mark; preserve its colors, proportions and clear space. */
@Composable
fun DataraLogo(modifier: Modifier = Modifier, size: Dp = 104.dp, contentDescription: String? = "DATAra logo") {
    Box(modifier.size(size + 24.dp), contentAlignment = Alignment.Center) {
        Image(painterResource(R.drawable.datara_brand_symbol), contentDescription,
            modifier = Modifier.size(size), contentScale = ContentScale.Fit)
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFF5F7FB)
@Composable
private fun DataraLogoPreview() { DataraLogo() }
