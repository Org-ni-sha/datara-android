package com.capstone.datara.ui.auth

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
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
import com.capstone.datara.ui.theme.DataraDarkBg
import com.capstone.datara.ui.theme.DataraError
import com.capstone.datara.ui.theme.DataraErrorBg
import com.capstone.datara.ui.theme.DataraNeonBlue
import com.capstone.datara.ui.theme.DataraNeonGreen
import com.capstone.datara.ui.theme.DataraTextPrimary
import com.capstone.datara.ui.theme.DataraTextSecondary

/**
 * Asks a signed-in user to choose a username. There is no back button: the auth back stack is
 * cleared on the way here, and the only ways out are saving a username or signing out.
 */
@Composable
fun UsernameScreen(
    onUsernameSaved: () -> Unit,
    onSignedOut: () -> Unit,
    viewModel: UsernameViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val focusManager = LocalFocusManager.current

    LaunchedEffect(uiState.isSaved) {
        if (uiState.isSaved) onUsernameSaved()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DataraDarkBg)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 28.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            DataraLogo(size = 96.dp)

            Spacer(modifier = Modifier.height(18.dp))

            Text(
                text = "CHOOSE A USERNAME",
                style = MaterialTheme.typography.headlineMedium.copy(
                    color = DataraTextPrimary,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 22.sp,
                    letterSpacing = 1.sp
                ),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "You can log in with it instead of your email.",
                style = MaterialTheme.typography.bodyMedium.copy(color = DataraTextSecondary),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(28.dp))

            DataraFieldLabel(label = "Username")
            Spacer(modifier = Modifier.height(8.dp))
            DataraTextField(
                value = uiState.input,
                onValueChange = viewModel::onInputChange,
                placeholder = "e.g. juan_dc",
                leadingIcon = DataraIcons.Person,
                isError = uiState.formatError != null ||
                    uiState.availability == UsernameAvailability.Taken,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.None,
                    autoCorrectEnabled = false,
                    keyboardType = KeyboardType.Ascii,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(
                    onDone = {
                        focusManager.clearFocus()
                        viewModel.submit()
                    }
                )
            )

            Spacer(modifier = Modifier.height(8.dp))
            UsernameHelperText(uiState)

            AnimatedVisibility(
                visible = uiState.error != null,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column {
                    Spacer(modifier = Modifier.height(16.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(color = DataraErrorBg, shape = RoundedCornerShape(14.dp))
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        Text(
                            text = uiState.error.orEmpty(),
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
                text = "Continue",
                onClick = {
                    focusManager.clearFocus()
                    viewModel.submit()
                },
                enabled = uiState.canSubmit,
                isLoading = uiState.isSaving
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = if (uiState.isSigningOut) "Signing out…" else "Use a different account",
                color = DataraNeonBlue,
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = DataraNeonBlue,
                    fontWeight = FontWeight.Bold
                ),
                modifier = Modifier
                    .clickable(enabled = !uiState.isSigningOut && !uiState.isSaving) {
                        viewModel.signOut(onSignedOut)
                    }
                    .padding(8.dp)
            )
        }
    }
}

/** One line under the field: the format rule, a live availability result, or the hint. */
@Composable
private fun UsernameHelperText(uiState: UsernameUiState) {
    val (text, color) = when {
        uiState.formatError != null -> uiState.formatError to DataraError
        uiState.availability == UsernameAvailability.Taken ->
            "That username is taken. Try another." to DataraError
        uiState.availability == UsernameAvailability.Available ->
            "Username is available." to DataraNeonGreen
        uiState.availability == UsernameAvailability.Checking ->
            "Checking availability…" to DataraTextSecondary
        else -> "3–20 characters: letters, numbers and underscores." to DataraTextSecondary
    }
    Text(
        text = text,
        color = color,
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
            .semantics { liveRegion = LiveRegionMode.Polite }
    )
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
fun UsernameHelperPreview() {
    DATAraTheme {
        Column(Modifier.background(DataraDarkBg).padding(16.dp)) {
            UsernameHelperText(UsernameUiState(input = "juan", availability = UsernameAvailability.Available))
            UsernameHelperText(UsernameUiState(input = "j!", formatError = "Use only letters, numbers and underscores."))
        }
    }
}
