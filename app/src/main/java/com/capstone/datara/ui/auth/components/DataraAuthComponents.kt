package com.capstone.datara.ui.auth.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.capstone.datara.ui.theme.DataraError
import com.capstone.datara.ui.theme.DataraFontFamily
import com.capstone.datara.ui.theme.DataraIconTint
import com.capstone.datara.ui.theme.DataraInputBg
import com.capstone.datara.ui.theme.DataraInputBorder
import com.capstone.datara.ui.theme.DataraInputBorderFocused
import com.capstone.datara.ui.theme.DataraNeonBlue
import com.capstone.datara.ui.theme.DataraPrimaryBlue
import com.capstone.datara.ui.theme.DataraPrimaryBlueDark
import com.capstone.datara.ui.theme.DataraTextPlaceholder
import com.capstone.datara.ui.theme.DataraTextPrimary

@Composable
fun DataraFieldLabel(
    label: String,
    modifier: Modifier = Modifier,
    trailingContent: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge.copy(
                color = DataraTextPrimary,
                fontWeight = FontWeight.SemiBold
            )
        )
        if (trailingContent != null) {
            trailingContent()
        }
    }
}

@Composable
fun DataraTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null,
    isPassword: Boolean = false,
    isError: Boolean = false,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    singleLine: Boolean = true
) {
    var isFocused by remember { mutableStateOf(false) }
    var passwordVisible by remember { mutableStateOf(false) }

    val targetBorderColor = when {
        isError -> DataraError
        isFocused -> DataraInputBorderFocused
        else -> DataraInputBorder
    }

    val animatedBorderColor by animateColorAsState(
        targetValue = targetBorderColor,
        animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing),
        label = "BorderColorAnimation"
    )

    val animatedIconTint by animateColorAsState(
        targetValue = if (isFocused) DataraNeonBlue else DataraIconTint,
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
            .height(54.dp)
            .clip(RoundedCornerShape(percent = 50))
            .background(DataraInputBg)
            .border(
                width = if (isFocused || isError) 1.5.dp else 1.dp,
                color = animatedBorderColor,
                shape = RoundedCornerShape(percent = 50)
            )
            .padding(horizontal = 20.dp),
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
                Spacer(modifier = Modifier.width(14.dp))
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
                            color = DataraTextPlaceholder,
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
                        .onFocusChanged { isFocused = it.isFocused },
                    textStyle = TextStyle(
                        fontFamily = DataraFontFamily,
                        color = DataraTextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium
                    ),
                    singleLine = singleLine,
                    visualTransformation = visualTransformation,
                    keyboardOptions = keyboardOptions,
                    keyboardActions = keyboardActions,
                    cursorBrush = SolidColor(DataraNeonBlue)
                )
            }

            if (isPassword && value.isNotEmpty()) {
                Text(
                    text = if (passwordVisible) "Hide" else "Show",
                    style = TextStyle(
                        fontFamily = DataraFontFamily,
                        color = DataraNeonBlue,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    modifier = Modifier
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            passwordVisible = !passwordVisible
                        }
                        .padding(start = 8.dp, end = 4.dp, top = 4.dp, bottom = 4.dp)
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
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.98f else 1f,
        animationSpec = tween(durationMillis = 100),
        label = "ButtonPressScale"
    )

    val buttonGradient = Brush.horizontalGradient(
        colors = if (enabled && !isLoading) {
            listOf(DataraPrimaryBlue, DataraPrimaryBlueDark)
        } else {
            listOf(DataraPrimaryBlue.copy(alpha = 0.5f), DataraPrimaryBlueDark.copy(alpha = 0.5f))
        }
    )

    Box(
        modifier = modifier
            .scale(scale)
            .fillMaxWidth()
            .height(54.dp)
            .shadow(
                elevation = if (enabled && !isLoading) 10.dp else 0.dp,
                shape = RoundedCornerShape(percent = 50),
                spotColor = DataraPrimaryBlue.copy(alpha = 0.45f)
            )
            .clip(RoundedCornerShape(percent = 50))
            .background(buttonGradient)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled && !isLoading,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(22.dp),
                color = Color.White,
                strokeWidth = 2.5.dp
            )
        } else {
            Text(
                text = text,
                style = TextStyle(
                    fontFamily = DataraFontFamily,
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                )
            )
        }
    }
}
