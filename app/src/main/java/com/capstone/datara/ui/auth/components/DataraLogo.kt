package com.capstone.datara.ui.auth.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.capstone.datara.R
import com.capstone.datara.ui.theme.DataraNeonBlue
import com.capstone.datara.ui.theme.DataraPrimaryBlue

@Composable
fun DataraLogo(
    modifier: Modifier = Modifier,
    size: Dp = 104.dp
) {
    Box(
        modifier = modifier.size(size + 24.dp),
        contentAlignment = Alignment.Center
    ) {
        // Ambient Neon Glow behind the logo
        Box(
            modifier = Modifier
                .size(size + 20.dp)
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            DataraNeonBlue.copy(alpha = 0.28f),
                            DataraPrimaryBlue.copy(alpha = 0.10f),
                            Color.Transparent
                        )
                    ),
                    shape = CircleShape
                )
        )

        // Official High-Resolution DATAra Logo Asset
        Image(
            painter = painterResource(id = R.drawable.datara_logo),
            contentDescription = "DATAra Logo",
            modifier = Modifier
                .size(size)
                .shadow(
                    elevation = 16.dp,
                    shape = RoundedCornerShape(26.dp),
                    spotColor = DataraNeonBlue.copy(alpha = 0.4f),
                    ambientColor = DataraPrimaryBlue.copy(alpha = 0.2f)
                )
                .clip(RoundedCornerShape(26.dp))
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0C101A)
@Composable
fun DataraLogoPreview() {
    DataraLogo()
}
