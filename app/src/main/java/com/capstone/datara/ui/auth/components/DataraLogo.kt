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

/** Supplied transparent brand mark; preserve its colors, proportions and clear space. */
@Composable
fun DataraLogo(modifier: Modifier = Modifier, size: Dp = 104.dp, contentDescription: String? = "DATAra logo") {
    Box(modifier.size(size + 24.dp), contentAlignment = Alignment.Center) {
        Image(painterResource(R.drawable.datara_brand_symbol), contentDescription,
            modifier = Modifier.size(size), contentScale = ContentScale.Fit)
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0C101A)
@Composable
private fun DataraLogoPreview() { DataraLogo() }
