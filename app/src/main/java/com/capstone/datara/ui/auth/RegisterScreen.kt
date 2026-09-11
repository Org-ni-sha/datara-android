package com.capstone.datara.ui.auth

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.draw.clip
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
import com.capstone.datara.ui.theme.DataraInputBorder
import com.capstone.datara.ui.theme.DataraNeonBlue
import com.capstone.datara.ui.theme.DataraTextPrimary
import com.capstone.datara.ui.theme.DataraTextSecondary

@Composable
fun RegisterScreen(
    onRegisterSuccess: () -> Unit,
    onNavigateToLogin: () -> Unit,
    viewModel: AuthViewModel = hiltViewModel()
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    val uiState by viewModel.uiState.collectAsState()
    val focusManager = LocalFocusManager.current

    LaunchedEffect(uiState) {
        if (uiState is AuthUiState.Success) {
            onRegisterSuccess()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            viewModel.clearError()
        }
    }

    BackHandler {
        viewModel.clearError()
        onNavigateToLogin()
    }

    Column(
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
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(DataraCardBg)
                    .border(width = 1.dp, color = DataraInputBorder, shape = CircleShape)
                    .clickable(
                        onClick = {
                            viewModel.clearError()
                            onNavigateToLogin()
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
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 28.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // Logo with ambient glow
            DataraLogo(size = 96.dp)

            Spacer(modifier = Modifier.height(18.dp))

            // Headline
            Text(
                text = "CREATE ACCOUNT",
                style = MaterialTheme.typography.headlineMedium.copy(
                    color = DataraTextPrimary,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 22.sp,
                    letterSpacing = 1.sp
                )
            )

            Spacer(modifier = Modifier.height(28.dp))

            // Email Field
            DataraFieldLabel(label = "Email Address")
            Spacer(modifier = Modifier.height(8.dp))
            DataraTextField(
                value = email,
                onValueChange = {
                    email = it
                    viewModel.clearError()
                },
                placeholder = "Email",
                leadingIcon = DataraIcons.Email,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Email,
                    imeAction = ImeAction.Next
                ),
                keyboardActions = KeyboardActions(
                    onNext = { focusManager.moveFocus(FocusDirection.Down) }
                )
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Password Field
            DataraFieldLabel(label = "Password")
            Spacer(modifier = Modifier.height(8.dp))
            DataraTextField(
                value = password,
                onValueChange = {
                    password = it
                    viewModel.clearError()
                },
                placeholder = "*********",
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
            DataraFieldLabel(label = "Confirm Password")
            Spacer(modifier = Modifier.height(8.dp))
            DataraTextField(
                value = confirmPassword,
                onValueChange = {
                    confirmPassword = it
                    viewModel.clearError()
                },
                placeholder = "*********",
                leadingIcon = DataraIcons.Lock,
                isPassword = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(
                    onDone = {
                        focusManager.clearFocus()
                        viewModel.register(email, password, confirmPassword)
                    }
                )
            )

            // Animated Error Banner
            AnimatedVisibility(
                visible = uiState is AuthUiState.Error,
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
                            text = (uiState as? AuthUiState.Error)?.message.orEmpty(),
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

            // Sign Up Button
            DataraPrimaryButton(
                text = "Sign Up",
                onClick = {
                    focusManager.clearFocus()
                    viewModel.register(email, password, confirmPassword)
                },
                enabled = email.isNotBlank() && password.isNotBlank() && confirmPassword.isNotBlank(),
                isLoading = uiState is AuthUiState.Loading
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Login Link
            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Already have an account? ",
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
                        onClick = {
                            viewModel.clearError()
                            onNavigateToLogin()
                        }
                    )
                )
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
fun RegisterScreenPreview() {
    DATAraTheme {
        RegisterScreen(
            onRegisterSuccess = {},
            onNavigateToLogin = {}
        )
    }
}
