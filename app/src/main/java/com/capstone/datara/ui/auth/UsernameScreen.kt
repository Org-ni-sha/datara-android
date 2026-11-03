package com.capstone.datara.ui.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.capstone.datara.ui.auth.components.AuthCardScaffold
import com.capstone.datara.ui.auth.components.DataraErrorBanner
import com.capstone.datara.ui.auth.components.DataraFieldLabel
import com.capstone.datara.ui.auth.components.DataraIcons
import com.capstone.datara.ui.auth.components.DataraPrimaryButton
import com.capstone.datara.ui.auth.components.DataraTextField
import com.capstone.datara.ui.theme.DATAraTheme

/**
 * Asks a signed-in user to choose a username, on the navy card layout ([AuthCardScaffold]).
 * There is no back button: the auth back stack is cleared on the way here, and the only ways out
 * are saving a username or signing out ("Use a different account").
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

    AuthCardScaffold(
        title = "Choose a username",
        subtitle = "You can log in with it instead of your email.",
        onBack = null,
        footer = {
            TextButton(
                onClick = { viewModel.signOut(onSignedOut) },
                enabled = !uiState.isSigningOut && !uiState.isSaving,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            ) {
                Text(
                    text = if (uiState.isSigningOut) "Signing out…" else "Use a different account",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onBackground
                )
            }
        }
    ) {
        DataraFieldLabel(label = "Username")
        Spacer(Modifier.height(8.dp))
        DataraTextField(
            value = uiState.input,
            onValueChange = viewModel::onInputChange,
            placeholder = "e.g. juan_dc",
            leadingIcon = DataraIcons.Person,
            contentType = ContentType.NewUsername,
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

        Spacer(Modifier.height(8.dp))
        UsernameHelperText(uiState)

        DataraErrorBanner(uiState.error)

        Spacer(Modifier.height(20.dp))
        DataraPrimaryButton(
            text = "Continue",
            onClick = {
                focusManager.clearFocus()
                viewModel.submit()
            },
            enabled = uiState.canSubmit,
            isLoading = uiState.isSaving
        )
    }
}

/** One line under the field: the format rule, a live availability result, or the hint. */
@Composable
private fun UsernameHelperText(uiState: UsernameUiState) {
    val (text, color) = when {
        uiState.formatError != null -> uiState.formatError to MaterialTheme.colorScheme.error
        uiState.availability == UsernameAvailability.Taken ->
            "That username is taken. Try another." to MaterialTheme.colorScheme.error
        uiState.availability == UsernameAvailability.Available ->
            "Username is available." to MaterialTheme.colorScheme.tertiary
        uiState.availability == UsernameAvailability.Checking ->
            "Checking availability…" to MaterialTheme.colorScheme.onSurfaceVariant
        else -> "3–20 characters: letters, numbers and underscores." to MaterialTheme.colorScheme.onSurfaceVariant
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
        Column(Modifier.background(MaterialTheme.colorScheme.background).padding(16.dp)) {
            UsernameHelperText(UsernameUiState(input = "juan", availability = UsernameAvailability.Available))
            UsernameHelperText(UsernameUiState(input = "j!", formatError = "Use only letters, numbers and underscores."))
        }
    }
}
