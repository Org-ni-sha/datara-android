package com.capstone.datara.ui.auth

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material3.TextButton
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.animation.SizeTransform
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.capstone.datara.ui.auth.components.AuthCardScaffold
import com.capstone.datara.ui.auth.components.AuthFooterLink
import com.capstone.datara.ui.auth.components.DataraErrorBanner
import com.capstone.datara.ui.auth.components.DataraFieldLabel
import com.capstone.datara.ui.auth.components.DataraIcons
import com.capstone.datara.ui.auth.components.DataraPrimaryButton
import com.capstone.datara.ui.auth.components.DataraTextField
import com.capstone.datara.ui.auth.components.PasswordStrengthMeter
import com.capstone.datara.ui.theme.DATAraTheme
import com.capstone.datara.ui.theme.DataraFontFamily

/** The visible screens of the reset flow; email and code share the first one. */
private enum class ResetScreen { EmailAndCode, NewPassword, Done }

private val ResetPasswordStep.screen: ResetScreen
    get() = when (this) {
        ResetPasswordStep.ENTER_EMAIL, ResetPasswordStep.ENTER_CODE -> ResetScreen.EmailAndCode
        ResetPasswordStep.SET_NEW_PASSWORD -> ResetScreen.NewPassword
        ResetPasswordStep.SUCCESS -> ResetScreen.Done
    }

/**
 * Password reset by email code, on the navy card layout ([AuthCardScaffold]).
 *
 * Screen 1 holds the email and the 6-digit code; after sending, the email collapses into a
 * "Code sent to … · Change" summary and Verify plus a resend countdown appear. Screen 2
 * sets the new password; then a confirmation offers Log in.
 */
@Composable
fun ForgotPasswordScreen(
    onNavigateBack: () -> Unit,
    onNavigateToLogin: () -> Unit,
    viewModel: AuthViewModel = hiltViewModel()
) {
    val state by viewModel.passwordResetState.collectAsState()
    var email by rememberSaveable { mutableStateOf("") }
    var code by rememberSaveable { mutableStateOf("") }
    var newPassword by rememberSaveable { mutableStateOf("") }
    var confirmPassword by rememberSaveable { mutableStateOf("") }
    val focusManager = LocalFocusManager.current

    DisposableEffect(Unit) {
        onDispose { viewModel.clearPasswordResetState() }
    }

    val goBack: () -> Unit = {
        when (state.step) {
            // The code was already verified; going back means verifying again.
            ResetPasswordStep.SET_NEW_PASSWORD -> {
                code = ""
                viewModel.backToCodeStep()
            }
            ResetPasswordStep.SUCCESS -> onNavigateToLogin()
            else -> onNavigateBack()
        }
    }
    BackHandler(onBack = goBack)

    val (title, subtitle) = when (state.step) {
        ResetPasswordStep.ENTER_EMAIL ->
            "Reset password" to "We'll email a 6-digit code if this account exists."
        ResetPasswordStep.ENTER_CODE ->
            "Reset password" to "If an account exists for that email, we sent it a 6-digit code."
        ResetPasswordStep.SET_NEW_PASSWORD ->
            "New password" to "Choose a new password for your account."
        ResetPasswordStep.SUCCESS ->
            "Password updated" to "You can now log in with your new password."
    }

    AuthCardScaffold(
        title = title,
        subtitle = subtitle,
        onBack = goBack,
        footer = {
            if (state.step.screen == ResetScreen.EmailAndCode) {
                AuthFooterLink("Remember your password? ", "Log in", onNavigateToLogin)
            }
        }
    ) {
        AnimatedContent(
            targetState = state.step.screen,
            transitionSpec = { fadeIn(tween(250, delayMillis = 90)) togetherWith fadeOut(tween(90)) },
            label = "ResetScreen"
        ) { screen ->
            Column {
                when (screen) {
                    ResetScreen.EmailAndCode -> EmailAndCodeCard(
                        email = email,
                        onEmailChange = {
                            email = it
                            viewModel.clearResetError()
                        },
                        codeSent = state.step == ResetPasswordStep.ENTER_CODE,
                        sentTo = state.email,
                        code = code,
                        onCodeChange = {
                            code = it
                            viewModel.clearResetError()
                        },
                        isLoading = state.isLoading,
                        hasError = state.error != null,
                        resendSecondsLeft = state.resendSecondsLeft,
                        onSend = {
                            focusManager.clearFocus()
                            viewModel.sendResetCode(email)
                        },
                        // A different address needs a new code.
                        onChangeEmail = {
                            code = ""
                            viewModel.backToEmailStep()
                        },
                        onVerify = {
                            focusManager.clearFocus()
                            viewModel.verifyResetCode(code)
                        },
                        onResend = {
                            code = ""
                            viewModel.resendResetCode()
                        }
                    )

                    ResetScreen.NewPassword -> NewPasswordCard(
                        newPassword = newPassword,
                        onNewPasswordChange = {
                            newPassword = it
                            viewModel.clearResetError()
                        },
                        confirmPassword = confirmPassword,
                        onConfirmPasswordChange = {
                            confirmPassword = it
                            viewModel.clearResetError()
                        },
                        isLoading = state.isLoading,
                        onSave = {
                            focusManager.clearFocus()
                            viewModel.updateNewPassword(newPassword, confirmPassword)
                        }
                    )

                    ResetScreen.Done -> DoneCard(onLogIn = onNavigateToLogin)
                }
                DataraErrorBanner(state.error)
            }
        }
    }
}

/** "n•••@example.com": enough to recognise the address without showing it in full. */
private fun maskEmail(email: String): String {
    val at = email.indexOf('@')
    if (at <= 0) return email
    return email.first() + "•••" + email.substring(at)
}

/**
 * Screen 1 of the reset flow. Before sending: a full-width email and Send code, with the code
 * boxes already visible but locked. After sending: the email collapses into a "Code sent to …
 * · Change" summary, the boxes unlock and take focus, and Verify plus a resend link appear.
 */
@Composable
private fun EmailAndCodeCard(
    email: String,
    onEmailChange: (String) -> Unit,
    codeSent: Boolean,
    sentTo: String,
    code: String,
    onCodeChange: (String) -> Unit,
    isLoading: Boolean,
    hasError: Boolean,
    resendSecondsLeft: Int,
    onSend: () -> Unit,
    onChangeEmail: () -> Unit,
    onVerify: () -> Unit,
    onResend: () -> Unit
) {
    AnimatedContent(
        targetState = codeSent,
        transitionSpec = {
            (fadeIn(tween(250, delayMillis = 90)) togetherWith fadeOut(tween(90)))
                .using(SizeTransform(clip = false))
        },
        label = "EmailOrSummary"
    ) { sent ->
        if (sent) {
            SentToSummary(sentTo = sentTo, onChange = onChangeEmail)
        } else {
            Column {
                DataraFieldLabel(label = "Email address")
                Spacer(Modifier.height(8.dp))
                DataraTextField(
                    value = email,
                    onValueChange = onEmailChange,
                    placeholder = "you@example.com",
                    leadingIcon = DataraIcons.Email,
                    contentType = ContentType.EmailAddress,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Email,
                        imeAction = ImeAction.Send
                    ),
                    keyboardActions = KeyboardActions(onSend = { onSend() })
                )
                Spacer(Modifier.height(16.dp))
                DataraPrimaryButton(
                    text = "Send code",
                    onClick = onSend,
                    enabled = email.isNotBlank(),
                    isLoading = isLoading
                )
            }
        }
    }

    Spacer(Modifier.height(20.dp))
    DataraFieldLabel(label = "6-digit code")
    Spacer(Modifier.height(8.dp))
    // Always shown so the whole task is visible; locked (dimmed) until a code has been sent.
    SixDigitCodeInput(
        code = code,
        onCodeChange = onCodeChange,
        isError = hasError && codeSent,
        enabled = codeSent,
        onDone = { if (code.length == 6) onVerify() }
    )

    AnimatedContent(
        targetState = codeSent,
        transitionSpec = { fadeIn(tween(250)) togetherWith fadeOut(tween(120)) },
        label = "CodeFooter"
    ) { sent ->
        if (sent) {
            Column {
                ResendLink(resendSecondsLeft = resendSecondsLeft, isLoading = isLoading, onResend = onResend)
                Spacer(Modifier.height(8.dp))
                DataraPrimaryButton(
                    text = "Verify code",
                    onClick = onVerify,
                    enabled = code.length == 6,
                    // Resending clears the code, so the spinner only shows while verifying.
                    isLoading = isLoading && code.length == 6
                )
            }
        } else {
            Text(
                text = "Send a code to your email first.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}

/** The collapsed email after sending, with a way back to fix a typo. */
@Composable
private fun SentToSummary(sentTo: String, onChange: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = DataraIcons.Email,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = "Code sent to",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = maskEmail(sentTo),
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        TextButton(onClick = onChange) {
            Text(
                text = "Change",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ResendLink(resendSecondsLeft: Int, isLoading: Boolean, onResend: () -> Unit) {
    val canResend = resendSecondsLeft == 0 && !isLoading
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start,
        itemVerticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Didn't get it? ",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        TextButton(onClick = onResend, enabled = canResend) {
            Text(
                text = if (resendSecondsLeft > 0) "Resend in 0:%02d".format(resendSecondsLeft) else "Resend code",
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                color = if (canResend) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun NewPasswordCard(
    newPassword: String,
    onNewPasswordChange: (String) -> Unit,
    confirmPassword: String,
    onConfirmPasswordChange: (String) -> Unit,
    isLoading: Boolean,
    onSave: () -> Unit
) {
    val focusManager = LocalFocusManager.current
    DataraFieldLabel(label = "New password")
    Spacer(Modifier.height(8.dp))
    DataraTextField(
        value = newPassword,
        onValueChange = onNewPasswordChange,
        placeholder = "At least 8 characters",
        leadingIcon = DataraIcons.Lock,
        isPassword = true,
        contentType = ContentType.NewPassword,
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Password,
            imeAction = ImeAction.Next
        ),
        keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) })
    )
    PasswordStrengthMeter(AuthValidator.passwordStrength(newPassword))

    Spacer(Modifier.height(16.dp))

    // Kept here (unlike sign-up): the user is replacing a password they can't see or recall.
    DataraFieldLabel(label = "Confirm new password")
    Spacer(Modifier.height(8.dp))
    DataraTextField(
        value = confirmPassword,
        onValueChange = onConfirmPasswordChange,
        placeholder = "Re-enter new password",
        leadingIcon = DataraIcons.Lock,
        isPassword = true,
        isError = confirmPassword.isNotEmpty() && confirmPassword != newPassword,
        contentType = ContentType.NewPassword,
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Password,
            imeAction = ImeAction.Done
        ),
        keyboardActions = KeyboardActions(onDone = { onSave() })
    )

    Spacer(Modifier.height(24.dp))
    DataraPrimaryButton(
        text = "Save new password",
        onClick = onSave,
        enabled = newPassword.isNotBlank() && confirmPassword.isNotBlank(),
        isLoading = isLoading
    )
}

@Composable
private fun DoneCard(onLogIn: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.secondaryContainer)
                .semantics { contentDescription = "Success" },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = DataraIcons.CheckCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.size(34.dp)
            )
        }
        Spacer(Modifier.height(16.dp))
        Text(
            text = "Your password was changed. Use it the next time you log in.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(20.dp))
        DataraPrimaryButton(text = "Log in now", onClick = onLogIn)
    }
}

/**
 * 6 individual boxes for entering the OTP code.
 * Handles auto-advancing, pasting, deleting, and highlights the active digit. When [enabled] is
 * false the boxes are shown dimmed and cannot be typed in. The boxes share the
 * available width (up to 48dp each), so they fit inside the card on small screens.
 */
@Composable
fun SixDigitCodeInput(
    code: String,
    onCodeChange: (String) -> Unit,
    isError: Boolean,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onDone: () -> Unit = {}
) {
    var isFocused by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(enabled) {
        // Focus the code as soon as it can be typed, so the number keyboard is ready for it.
        if (enabled) focusRequester.requestFocus()
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
        enabled = enabled,
        modifier = modifier
            .fillMaxWidth()
            .alpha(if (enabled) 1f else 0.45f)
            .focusRequester(focusRequester)
            .onFocusChanged { isFocused = it.isFocused }
            .semantics { this.contentType = ContentType.SmsOtpCode },
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Number,
            imeAction = ImeAction.Done
        ),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
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
                        isError -> MaterialTheme.colorScheme.error
                        isCurrentActiveBox -> MaterialTheme.colorScheme.primary
                        isFilled -> MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                        else -> MaterialTheme.colorScheme.outline
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .widthIn(max = 48.dp)
                            .heightIn(min = 54.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surface)
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
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center
                                )
                            )
                        } else if (isCurrentActiveBox) {
                            Box(
                                modifier = Modifier
                                    .width(2.dp)
                                    .height(20.dp)
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.85f))
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
