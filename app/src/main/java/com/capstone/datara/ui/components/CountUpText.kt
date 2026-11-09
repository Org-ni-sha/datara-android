package com.capstone.datara.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import java.util.Locale

/**
 * A value that starts with a number ("20 days", "28.21 GB", "1,000 MB", "4 of 6"), split so the
 * number can count up while the rest stays put. Times ("14:00") and signed offsets are left alone.
 */
internal data class CountUpParts(val number: Double, val decimals: Int, val grouped: Boolean, val suffix: String) {
    fun format(value: Double): String {
        val pattern = if (grouped) "%,.${decimals}f" else "%.${decimals}f"
        return String.format(Locale.US, pattern, value) + suffix
    }

    companion object {
        private val NUMBER = Regex("""^(\d{1,3}(?:,\d{3})+|\d+)(\.\d+)?(.*)$""", RegexOption.DOT_MATCHES_ALL)

        fun parse(text: String): CountUpParts? {
            val match = NUMBER.matchEntire(text) ?: return null
            val (whole, fraction, suffix) = match.destructured
            if (suffix.startsWith(":")) return null
            val number = (whole.replace(",", "") + fraction).toDoubleOrNull() ?: return null
            return CountUpParts(number, (fraction.length - 1).coerceAtLeast(0), whole.contains(','), suffix)
        }
    }
}

/**
 * Text whose leading number counts up from zero the first time it shows, then animates between
 * values. It follows the system animation scale (off means it shows the value at once), and
 * accessibility services and tests only ever see the final [text].
 */
@Composable
fun CountUpText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    color: Color = Color.Unspecified,
    textAlign: TextAlign? = null,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
    durationMillis: Int = 700,
) {
    val parts = remember(text) { CountUpParts.parse(text) }
    val animated = remember { Animatable(0f) }
    LaunchedEffect(parts?.number) {
        parts?.let { animated.animateTo(it.number.toFloat(), tween(durationMillis, easing = FastOutSlowInEasing)) }
    }
    // Snap the last frame to the exact text, so rounding never leaves "28.20 GB" for "28.21 GB"
    val shown = when {
        parts == null -> text
        animated.isRunning -> parts.format(animated.value.toDouble())
        animated.value.toDouble() == parts.number.toFloat().toDouble() -> text
        else -> parts.format(animated.value.toDouble())
    }
    Text(shown, modifier.clearAndSetSemantics { this.text = AnnotatedString(text) }, color = color, style = style,
        textAlign = textAlign, maxLines = maxLines, overflow = overflow)
}
