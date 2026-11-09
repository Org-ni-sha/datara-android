package com.capstone.datara.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.capstone.datara.data.cycle.CycleStatus
import com.capstone.datara.ui.theme.HeroAlmost
import com.capstone.datara.ui.theme.HeroEnded
import com.capstone.datara.ui.theme.HeroHeadsUp
import com.capstone.datara.ui.theme.HeroOnTrack
import com.capstone.datara.ui.theme.HeroReached

/** One colour code for quota status, shared by every screen that shows a cycle. */
enum class QuotaTone(val label: String) {
    ON_TRACK("On track"), HEADS_UP("Heads-up"), ALMOST("Almost there"), REACHED("Quota reached"), ENDED("Ended")
}

/** Below this share used a cycle is on track; from [ALMOST_FRACTION] it is almost there. */
const val HEADS_UP_FRACTION = 0.75f
const val ALMOST_FRACTION = 0.90f

/**
 * Pure status tone: ended cycles are neutral whatever their usage; reached (by status or by
 * usage ≥ 100%) is soft red; otherwise teal below 75%, amber 75–89%, deeper amber from 90%.
 */
fun quotaStatusTone(usedFraction: Float, status: CycleStatus): QuotaTone = when {
    status == CycleStatus.ENDED -> QuotaTone.ENDED
    status == CycleStatus.REACHED || usedFraction >= 1f -> QuotaTone.REACHED
    usedFraction >= ALMOST_FRACTION -> QuotaTone.ALMOST
    usedFraction >= HEADS_UP_FRACTION -> QuotaTone.HEADS_UP
    else -> QuotaTone.ON_TRACK
}

@Immutable
data class ToneColors(val container: Color, val content: Color)

/** The tone's container/content pair on light surfaces (chips, icons, progress). */
@Composable
fun QuotaTone.colors(): ToneColors {
    val c = MaterialTheme.colorScheme
    return when (this) {
        QuotaTone.ON_TRACK -> ToneColors(c.secondaryContainer, c.onSecondaryContainer)
        QuotaTone.HEADS_UP -> ToneColors(c.tertiaryContainer, c.onTertiaryContainer)
        QuotaTone.ALMOST -> ToneColors(c.tertiary, c.onTertiary)
        QuotaTone.REACHED -> ToneColors(c.errorContainer, c.onErrorContainer)
        QuotaTone.ENDED -> ToneColors(c.surfaceContainerHighest, c.onSurfaceVariant)
    }
}

/** The tone's accent on light surfaces (progress bars and rings). */
@Composable
fun QuotaTone.accent(): Color {
    val c = MaterialTheme.colorScheme
    return when (this) {
        QuotaTone.ON_TRACK -> c.secondary
        QuotaTone.HEADS_UP, QuotaTone.ALMOST -> c.tertiary
        QuotaTone.REACHED -> c.error
        QuotaTone.ENDED -> c.outline
    }
}

/** The tone's accent on the navy (primary) hero; each ≥ 7:1 against it. */
fun QuotaTone.onPrimaryAccent(): Color = when (this) {
    QuotaTone.ON_TRACK -> HeroOnTrack
    QuotaTone.HEADS_UP -> HeroHeadsUp
    QuotaTone.ALMOST -> HeroAlmost
    QuotaTone.REACHED -> HeroReached
    QuotaTone.ENDED -> HeroEnded
}

/** Status chip for a quota tone; [text] defaults to the tone's label. */
@Composable
fun QuotaStatusChip(tone: QuotaTone, modifier: Modifier = Modifier, text: String = tone.label) {
    val colors = tone.colors()
    Surface(modifier, color = colors.container, contentColor = colors.content, shape = MaterialTheme.shapes.extraLarge) {
        Text(text, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
    }
}
