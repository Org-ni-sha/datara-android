package com.capstone.datara.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Above this font scale the ring keeps its size and the hero puts its text below it. */
const val RING_LARGE_FONT_SCALE = 1.3f

/**
 * The forecast hero's ring: [progress] of the quota used (null = indeterminate ring while the
 * forecast is still learning), with [content] centred inside. Square, [size] wide, never wider
 * than the space it gets.
 */
@Composable
fun QuotaRing(progress: Float?, color: Color, trackColor: Color, description: String, modifier: Modifier = Modifier,
    size: Dp = 216.dp, stroke: Dp = 14.dp, content: @Composable BoxScope.() -> Unit = {}) {
    Box(modifier.widthIn(max = size).fillMaxWidth().aspectRatio(1f)
        .semantics(mergeDescendants = true) { contentDescription = description }, contentAlignment = Alignment.Center) {
        if (progress == null) {
            CircularProgressIndicator(Modifier.fillMaxSize(), color = color, trackColor = trackColor, strokeWidth = stroke, strokeCap = StrokeCap.Round)
        } else {
            val animated by animateFloatAsState(progress.coerceIn(0f, 1f), tween(600), label = "Quota ring")
            CircularProgressIndicator(progress = { animated }, modifier = Modifier.fillMaxSize(), color = color, trackColor = trackColor,
                strokeWidth = stroke, strokeCap = StrokeCap.Round)
        }
        Box(Modifier.padding(stroke + 12.dp), contentAlignment = Alignment.Center, content = content)
    }
}

/** True when the ring's centre text should move below it. */
@Composable
fun ringTextBelow(): Boolean = LocalDensity.current.fontScale > RING_LARGE_FONT_SCALE
