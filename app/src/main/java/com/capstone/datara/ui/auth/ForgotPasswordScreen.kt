package com.capstone.datara.ui.auth

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.capstone.datara.ui.auth.components.DataraFieldLabel
import com.capstone.datara.ui.auth.components.DataraIcons
import com.capstone.datara.ui.auth.components.DataraLogo
import com.capstone.datara.ui.auth.components.DataraPrimaryButton
import com.capstone.datara.ui.auth.components.DataraTextField
import com.capstone.datara.ui.theme.DATAraTheme
import com.capstone.datara.ui.theme.DataraCardBg
import com.capstone.datara.ui.theme.DataraDarkBg
import com.capstone.datara.ui.theme.DataraError
import com.capstone.datara.ui.theme.DataraErrorBg
import com.capstone.datara.ui.theme.DataraFontFamily
import com.capstone.datara.ui.theme.DataraInputBorder
import com.capstone.datara.ui.theme.DataraInputBorderFocused
import com.capstone.datara.ui.theme.DataraNeonBlue
import com.capstone.datara.ui.theme.DataraNeonGreen
import com.capstone.datara.ui.theme.DataraTextPlaceholder
import com.capstone.datara.ui.theme.DataraTextPrimary
import com.capstone.datara.ui.theme.DataraTextSecondary

@Composable
fun ForgotPasswordScreen(
    onNavigateBack: () -> Unit,
    onNavigateToLogin: () -> Unit,
    viewModel: AuthViewModel = hiltViewModel()
) {
    val state by viewModel.passwordResetState.collectAsState()
    var email by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    val focusManager = LocalFocusManager.current

    DisposableEffect(Unit) {
        onDispose {
            viewModel.clearPasswordResetState()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DataraDarkBg)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
    ) {
        // Back Navigation Button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(DataraCardBg)
                    .border(width = 1.dp, color = DataraInputBorder, shape = CircleShape)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {
                            when (state.step) {
                                ResetPasswordStep.SET_NEW_PASSWORD -> viewModel.backToCodeStep()
                                ResetPasswordStep.ENTER_CODE -> viewModel.backToEmailStep()
                                else -> onNavigateBack()
                            }
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = DataraIcons.ArrowBack,
                    contentDescription = "Back",
                    tint = DataraTextPrimary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            when (state.step) {
                // STEP 1: Enter Email
                ResetPasswordStep.ENTER_EMAIL -> {
                    Spacer(modifier = Modifier.height(24.dp))
                    DataraLogo(size = 96.dp)
                    Spacer(modifier = Modifier.height(20.dp))

                    Text(
                        text = "RESET PASSWORD",
                        style = MaterialTheme.typography.headlineMedium.copy(
                            color = DataraTextPrimary,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 22.sp,
                            letterSpacing = 1.sp
                        )
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = "Enter your registered email and we'll send you a 6-digit verification code to reset your password.",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = DataraTextSecondary,
                            textAlign = TextAlign.Center,
                            lineHeight = 20.sp
                        ),
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )

                    Spacer(modifier = Modifier.height(28.dp))

                    DataraFieldLabel(label = "Email Address")
                    Spacer(modifier = Modifier.height(8.dp))
                    DataraTextField(
                        value = email,
                        onValueChange = { email = it },
                        placeholder = "Email",
                        leadingIcon = DataraIcons.Email,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Email,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                focusManager.clearFocus()
                                viewModel.sendResetCode(email)
                            }
                        )
                    )

                    AnimatedVisibility(
                        visible = state.error != null,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically()
                    ) {
                        Column {
                            Spacer(modifier = Modifier.height(16.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(
                                        color = DataraErrorBg,
                                        shape = RoundedCornerShape(14.dp)
                                    )
                                    .padding(horizontal = 16.dp, vertical = 12.dp)
                            ) {
                                Text(
                                    text = state.error.orEmpty(),
                                    color = DataraError,
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontWeight = FontWeight.Medium,
                                        textAlign = TextAlign.Center
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(28.dp))

                    DataraPrimaryButton(
                        text = "Send 6-Digit Code",
                        onClick = {
                            focusManager.clearFocus()
                            viewModel.sendResetCode(email)
                        },
                        enabled = email.isNotBlank(),
                        isLoading = state.isLoading
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    Row(
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Remember your password? ",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = DataraTextSecondary
                            )
                        )
                        Text(
                            text = "Log in",
                            color = DataraNeonBlue,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = DataraNeonBlue,
                                fontWeight = FontWeight.Bold
                            ),
                            modifier = Modifier.clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = onNavigateToLogin
                            )
                        )
                    }
                }

                // STEP 2: Enter 6-Digit Code (Separate screen with 6 small boxes)
                ResetPasswordStep.ENTER_CODE -> {
                    Spacer(modifier = Modifier.height(24.dp))
                    DataraLogo(size = 88.dp)
                    Spacer(modifier = Modifier.height(18.dp))

                    Text(
                        text = "VERIFY CODE",
                        style = MaterialTheme.typography.headlineMedium.copy(
                            color = DataraTextPrimary,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 22.sp,
                            letterSpacing = 1.sp
                        )
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = "Enter the 6-digit code sent to\n${state.email}",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = DataraTextSecondary,
                            textAlign = TextAlign.Center,
                            lineHeight = 20.sp
                        )
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = "Wrong email? Change",
                        color = DataraNeonBlue,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontWeight = FontWeight.SemiBold
                        ),
                        modifier = Modifier.clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {
                                code = ""
                                viewModel.backToEmailStep()
                            }
                        )
                    )

                    Spacer(modifier = Modifier.height(32.dp))

                    // 6 Small Digit Boxes Input
                    DataraFieldLabel(
                        label = "6-Digit Code",
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    SixDigitCodeInput(
                        code = code,
                        onCodeChange = { code = it },
                        isError = state.error != null,
                        onDone = {
                            focusManager.clearFocus()
                            if (code.length == 6) {
                                viewModel.verifyResetCode(code)
                            }
                        }
                    )

                    AnimatedVisibility(
                        visible = state.error != null,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically()
                    ) {
                        Column {
                            Spacer(modifier = Modifier.height(16.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(
                                        color = DataraErrorBg,
                                        shape = RoundedCornerShape(14.dp)
                                    )
                                    .padding(horizontal = 16.dp, vertical = 12.dp)
                            ) {
                                Text(
                                    text = state.error.orEmpty(),
                                    color = DataraError,
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontWeight = FontWeight.Medium,
                                        textAlign = TextAlign.Center
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(28.dp))

                    DataraPrimaryButton(
                        text = "Verify Code",
                        onClick = {
                            focusManager.clearFocus()
                            viewModel.verifyResetCode(code)
                        },
                        enabled = code.length == 6,
                        isLoading = state.isLoading
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    Row(
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Didn't receive the code? ",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = DataraTextPlaceholder
                            )
                        )
                        Text(
                            text = "Resend",
                            color = DataraNeonBlue,
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = DataraNeonBlue,
                                fontWeight = FontWeight.Bold
                            ),
                            modifier = Modifier.clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = {
                                    code = ""
                                    viewModel.resendResetCode()
                                }
                            )
                        )
                    }
                }

                // STEP 3: Set New Password (Only unlocks after code verification succeeds!)
                ResetPasswordStep.SET_NEW_PASSWORD -> {
                    Spacer(modifier = Modifier.height(24.dp))
                    DataraLogo(size = 88.dp)
                    Spacer(modifier = Modifier.height(18.dp))

                    Text(
                        text = "NEW PASSWORD",
                        style = MaterialTheme.typography.headlineMedium.copy(
                            color = DataraTextPrimary,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 22.sp,
                            letterSpacing = 1.sp
                        )
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = "Code verified! Please create a new password for your account.",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = DataraTextSecondary,
                            textAlign = TextAlign.Center,
                            lineHeight = 20.sp
                        )
                    )

                    Spacer(modifier = Modifier.height(28.dp))

                    // New Password Field
                    DataraFieldLabel(label = "New Password")
                    Spacer(modifier = Modifier.height(8.dp))
                    DataraTextField(
                        value = newPassword,
                        onValueChange = { newPassword = it },
                        placeholder = "Min. 6 characters",
                        leadingIcon = DataraIcons.Lock,
                        isPassword = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Next
                        ),
                        keyboardActions = KeyboardActions(
                            onNext = { focusManager.moveFocus(FocusDirection.Down) }
                        )
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Confirm Password Field
                    DataraFieldLabel(label = "Confirm New Password")
                    Spacer(modifier = Modifier.height(8.dp))
                    DataraTextField(
                        value = confirmPassword,
                        onValueChange = { confirmPassword = it },
                        placeholder = "Re-enter new password",
                        leadingIcon = DataraIcons.Lock,
                        isPassword = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                focusManager.clearFocus()
                                viewModel.updateNewPassword(newPassword, confirmPassword)
                            }
                        )
                    )

                    AnimatedVisibility(
                        visible = state.error != null,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically()
                    ) {
                        Column {
                            Spacer(modifier = Modifier.height(16.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(
                                        color = DataraErrorBg,
                                        shape = RoundedCornerShape(14.dp)
                                    )
                                    .padding(horizontal = 16.dp, vertical = 12.dp)
                            ) {
                                Text(
                                    text = state.error.orEmpty(),
                                    color = DataraError,
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontWeight = FontWeight.Medium,
                                        textAlign = TextAlign.Center
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(28.dp))

                    DataraPrimaryButton(
                        text = "Save New Password",
                        onClick = {
                            focusManager.clearFocus()
                            viewModel.updateNewPassword(newPassword, confirmPassword)
                        },
                        enabled = newPassword.isNotBlank() && confirmPassword.isNotBlank(),
                        isLoading = state.isLoading
                    )
                }

                // STEP 4: Success Screen
                ResetPasswordStep.SUCCESS -> {
                    Spacer(modifier = Modifier.height(32.dp))

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(20.dp))
                            .background(DataraCardBg)
                            .border(
                                width = 1.dp,
                                color = DataraNeonGreen.copy(alpha = 0.35f),
                                shape = RoundedCornerShape(20.dp)
                            )
                            .padding(28.dp)
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(60.dp)
                                    .clip(CircleShape)
                                    .background(DataraNeonGreen.copy(alpha = 0.12f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = DataraIcons.CheckCircle,
                                    contentDescription = "Success",
                                    tint = DataraNeonGreen,
                                    modifier = Modifier.size(36.dp)
                                )
                            }

                            Spacer(modifier = Modifier.height(18.dp))

                            Text(
                                text = "Password Reset Complete!",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    color = DataraTextPrimary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 18.sp
                                )
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            Text(
                                text = "Your password has been successfully updated. You can now log in to DATAra with your new password.",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    color = DataraTextSecondary,
                                    textAlign = TextAlign.Center,
                                    lineHeight = 20.sp
                                )
                            )

                            Spacer(modifier = Modifier.height(26.dp))

                            DataraPrimaryButton(
                                text = "Log In Now",
                                onClick = onNavigateToLogin
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

/**
 * 6 Small individual boxes for entering the OTP code.
 * Handles auto-advancing, pasting, deleting, and highlights the active digit.
 */
@Composable
fun SixDigitCodeInput(
    code: String,
    onCodeChange: (String) -> Unit,
    isError: Boolean,
    modifier: Modifier = Modifier,
    onDone: () -> Unit = {}
) {
    var isFocused by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        // Automatically request focus when entering the code screen
        focusRequester.requestFocus()
    }

    BasicTextField(
        value = code,
        onValueChange = { newValue ->
            val digitsOnly = newValue.filter { it.isDigit() }.take(6)
            onCodeChange(digitsOnly)
            if (digitsOnly.length == 6) {
                onDone()
            }
        },
        modifier = modifier
            .fillMaxWidth()
            .focusRequester(focusRequester)
            .onFocusChanged { isFocused = it.isFocused },
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Number,
            imeAction = ImeAction.Done
        ),
        keyboardActions = KeyboardActions(
            onDone = { onDone() }
        ),
        decorationBox = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically
            ) {
                for (index in 0 until 6) {
                    val char = code.getOrNull(index)?.toString().orEmpty()
                    val isCurrentActiveBox = isFocused && (index == code.length || (index == 5 && code.length == 6))
                    val isFilled = index < code.length

                    val boxBorderColor = when {
                        isError -> DataraError
                        isCurrentActiveBox -> DataraNeonBlue
                        isFilled -> DataraInputBorderFocused
                        else -> DataraInputBorder
                    }

                    Box(
                        modifier = Modifier
                            .width(44.dp)
                            .height(54.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(DataraCardBg)
                            .border(
                                width = if (isCurrentActiveBox || isError) 1.5.dp else 1.dp,
                                color = boxBorderColor,
                                shape = RoundedCornerShape(12.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (char.isNotEmpty()) {
                            Text(
                                text = char,
                                style = TextStyle(
                                    fontFamily = DataraFontFamily,
                                    color = DataraTextPrimary,
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center
                                )
                            )
                        } else if (isCurrentActiveBox) {
                            // Active blinking indicator
                            Box(
                                modifier = Modifier
                                    .width(2.dp)
                                    .height(20.dp)
                                    .background(DataraNeonBlue.copy(alpha = 0.85f))
                            )
                        }
                    }
                }
            }
        }
    )
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
fun ForgotPasswordScreenPreview() {
    DATAraTheme {
        ForgotPasswordScreen(
            onNavigateBack = {},
            onNavigateToLogin = {}
        )
    }
}
