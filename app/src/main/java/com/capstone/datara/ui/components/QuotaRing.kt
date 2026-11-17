package com.capstone.datara.ui.components

import android.provider.Settings
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.animation.core.withInfiniteAnimationFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Above this font scale the ring keeps its size and the hero puts its text below it. */
const val RING_LARGE_FONT_SCALE = 1.3f

/**
 * The forecast hero's ring: [progress] of the quota used (null = the turning "collecting" ring
 * while the forecast is still learning), with [content] centred inside. Square, [size] wide,
 * never wider than the space it gets.
 */
@Composable
fun QuotaRing(progress: Float?, color: Color, trackColor: Color, description: String, modifier: Modifier = Modifier,
    size: Dp = 216.dp, stroke: Dp = 14.dp, content: @Composable BoxScope.() -> Unit = {}) {
    Box(modifier.widthIn(max = size).fillMaxWidth().aspectRatio(1f)
        .semantics(mergeDescendants = true) { contentDescription = description }, contentAlignment = Alignment.Center) {
        if (progress == null) {
            CollectingRing(color, trackColor, stroke, Modifier.fillMaxSize())
        } else {
            val animated by animateFloatAsState(progress.coerceIn(0f, 1f), tween(600), label = "Quota ring")
            CircularProgressIndicator(progress = { animated }, modifier = Modifier.fillMaxSize(), color = color, trackColor = trackColor,
                strokeWidth = stroke, strokeCap = StrokeCap.Round)
        }
        Box(Modifier.padding(stroke + 12.dp), contentAlignment = Alignment.Center, content = content)
    }
}

/** One turn of the collecting ring, and the length of its arc. */
internal const val COLLECTING_TURN_MS = 1_600L
internal const val COLLECTING_SWEEP_DEGREES = 90f

/**
 * Where the collecting arc starts, in degrees clockwise from 12 o'clock, at frame time [frameMillis].
 * Derived from the clock rather than from when the animation started, so the arc never jumps: a
 * full turn ends where it began, and if Home rebuilds the ring it carries on from the same spot.
 */
internal fun collectingArcStart(frameMillis: Long): Float =
    (frameMillis.mod(COLLECTING_TURN_MS)).toFloat() / COLLECTING_TURN_MS * 360f

/**
 * The "collecting" ring: a quarter arc turning at a steady speed over the full track. It replaces
 * Material's indeterminate indicator, whose arc grows and shrinks on a multi-second cycle and
 * restarts from the beginning whenever it is recomposed, which showed as a jump at our size. With
 * animations turned off in the system settings it holds still.
 */
@Composable
private fun CollectingRing(color: Color, trackColor: Color, stroke: Dp, modifier: Modifier) {
    val context = LocalContext.current
    val still = remember(context) {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
    var start by remember { mutableFloatStateOf(0f) }
    if (!still) LaunchedEffect(Unit) {
        while (true) withInfiniteAnimationFrameMillis { start = collectingArcStart(it) }
    }
    Canvas(modifier) {
        val width = stroke.toPx()
        val inset = width / 2
        val arcSize = Size(size.width - width, size.height - width)
        drawArc(trackColor, 0f, 360f, useCenter = false, topLeft = Offset(inset, inset), size = arcSize, style = Stroke(width))
        // Read in the draw phase only, so each frame redraws the ring without recomposing the hero
        drawArc(color, start - 90f, COLLECTING_SWEEP_DEGREES, useCenter = false, topLeft = Offset(inset, inset), size = arcSize,
            style = Stroke(width, cap = StrokeCap.Round))
    }
}

/** True when the ring's centre text should move below it. */
@Composable
fun ringTextBelow(): Boolean = LocalDensity.current.fontScale > RING_LARGE_FONT_SCALE
