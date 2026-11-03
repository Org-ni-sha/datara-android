package com.capstone.datara.ui.auth.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.capstone.datara.ui.auth.PasswordStrength
import com.capstone.datara.ui.theme.DataraFontFamily

/**
 * Authentication building blocks for the light auth flow. Colors come from
 * `MaterialTheme.colorScheme` (the shared light scheme: navy primary, white surfaces), and
 * shapes follow the quota design system: 16dp controls.
 */

private val ControlShape = RoundedCornerShape(16.dp)

/**
 * Field label with an optional trailing action (e.g. "Forgot password?"). A flow row, so at
 * large font sizes the action moves below the label whole instead of wrapping beside it.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DataraFieldLabel(
    label: String,
    modifier: Modifier = Modifier,
    trailingContent: (@Composable () -> Unit)? = null
) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        itemVerticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge.copy(
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold
            )
        )
        if (trailingContent != null) {
            trailingContent()
        }
    }
}

/**
 * Single-line auth field. [contentType] tells autofill services (Google Password Manager,
 * Bitwarden…) what the field holds, so they can fill it and offer to save new credentials.
 */
@Composable
fun DataraTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null,
    isPassword: Boolean = false,
    isError: Boolean = false,
    contentType: ContentType? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    singleLine: Boolean = true
) {
    var isFocused by remember { mutableStateOf(false) }
    var passwordVisible by remember { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme

    val animatedBorderColor by animateColorAsState(
        targetValue = when {
            isError -> colors.error
            isFocused -> colors.primary
            else -> colors.outline
        },
        animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing),
        label = "BorderColorAnimation"
    )
    val animatedIconTint by animateColorAsState(
        targetValue = if (isFocused) colors.primary else colors.onSurfaceVariant,
        animationSpec = tween(durationMillis = 200),
        label = "IconTintAnimation"
    )

    val visualTransformation = if (isPassword && !passwordVisible) {
        PasswordVisualTransformation()
    } else {
        VisualTransformation.None
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clip(ControlShape)
            .background(colors.surface)
            .border(
                width = if (isFocused || isError) 1.5.dp else 1.dp,
                color = animatedBorderColor,
                shape = ControlShape
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (leadingIcon != null) {
                Icon(
                    imageVector = leadingIcon,
                    contentDescription = null,
                    tint = animatedIconTint,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
            }

            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.CenterStart
            ) {
                if (value.isEmpty()) {
                    Text(
                        text = placeholder,
                        style = TextStyle(
                            fontFamily = DataraFontFamily,
                            color = colors.onSurfaceVariant,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Normal
                        )
                    )
                }

                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .onFocusChanged { isFocused = it.isFocused }
                        .then(
                            if (contentType != null) {
                                Modifier.semantics { this.contentType = contentType }
                            } else {
                                Modifier
                            }
                        ),
                    textStyle = TextStyle(
                        fontFamily = DataraFontFamily,
                        color = colors.onSurface,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium
                    ),
                    singleLine = singleLine,
                    visualTransformation = visualTransformation,
                    keyboardOptions = keyboardOptions,
                    keyboardActions = keyboardActions,
                    cursorBrush = SolidColor(colors.primary)
                )
            }

            if (isPassword && value.isNotEmpty()) {
                Text(
                    text = if (passwordVisible) "Hide" else "Show",
                    style = TextStyle(
                        fontFamily = DataraFontFamily,
                        color = colors.primary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(role = Role.Button) { passwordVisible = !passwordVisible }
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                )
            }
        }
    }
}

@Composable
fun DataraPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isLoading: Boolean = false
) {
    val colors = MaterialTheme.colorScheme
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.98f else 1f,
        animationSpec = tween(durationMillis = 100),
        label = "ButtonPressScale"
    )
    val active = enabled && !isLoading

    Box(
        modifier = modifier
            .scale(scale)
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clip(ControlShape)
            .background(if (active || isLoading) colors.primary else colors.primary.copy(alpha = 0.38f))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = active,
                role = Role.Button,
                onClick = onClick
            )
            .padding(horizontal = 20.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(22.dp),
                color = colors.onPrimary,
                strokeWidth = 2.5.dp
            )
        } else {
            Text(
                text = text,
                style = TextStyle(
                    fontFamily = DataraFontFamily,
                    color = colors.onPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.2.sp
                )
            )
        }
    }
}

/** Outlined button for secondary actions such as "Continue with Google". */
@Composable
fun DataraSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: (@Composable () -> Unit)? = null
) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clip(ControlShape)
            .background(colors.surface)
            .border(width = 1.dp, color = colors.outline, shape = ControlShape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (leadingIcon != null) {
                leadingIcon()
                Spacer(Modifier.width(12.dp))
            }
            Text(
                text = text,
                style = TextStyle(
                    fontFamily = DataraFontFamily,
                    color = colors.onSurface.copy(alpha = if (enabled) 1f else 0.38f),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
            )
        }
    }
}

/** "──── or ────" separator between the primary form and alternative sign-in options. */
@Composable
fun DataraOrDivider(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .height(1.dp)
                .background(MaterialTheme.colorScheme.outline)
        )
        Text(
            text = "or",
            style = MaterialTheme.typography.bodySmall.copy(
                color = MaterialTheme.colorScheme.onSurfaceVariant
            ),
            modifier = Modifier.padding(horizontal = 12.dp)
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .height(1.dp)
                .background(MaterialTheme.colorScheme.outline)
        )
    }
}

/** Animated error banner shared by the auth forms; announced politely to screen readers. */
@Composable
fun DataraErrorBanner(message: String?, modifier: Modifier = Modifier) {
    AnimatedVisibility(
        visible = message != null,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically(),
        modifier = modifier
    ) {
        Column {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = message.orEmpty(),
                color = MaterialTheme.colorScheme.onErrorContainer,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.errorContainer)
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .semantics { liveRegion = LiveRegionMode.Polite }
            )
        }
    }
}

/**
 * Three-segment strength bar under a new-password field. Hidden while the field is empty.
 * The rule that blocks submission lives in [com.capstone.datara.ui.auth.AuthValidator]; this is
 * guidance only.
 */
@Composable
fun PasswordStrengthMeter(strength: PasswordStrength?, modifier: Modifier = Modifier) {
    AnimatedVisibility(
        visible = strength != null,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically(),
        modifier = modifier
    ) {
        val current = strength ?: PasswordStrength.Weak
        val colors = MaterialTheme.colorScheme
        val activeColor by animateColorAsState(
            targetValue = when (current) {
                PasswordStrength.Weak -> colors.error
                PasswordStrength.Fair -> colors.primary
                PasswordStrength.Strong -> colors.tertiary
            },
            label = "StrengthColor"
        )
        Column(Modifier.padding(top = 8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                repeat(3) { index ->
                    Box(
                        Modifier
                            .weight(1f)
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(if (index <= current.ordinal) activeColor else colors.outline)
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = current.label,
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
            )
        }
    }
}
