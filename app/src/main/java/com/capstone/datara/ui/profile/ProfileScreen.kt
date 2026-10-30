package com.capstone.datara.ui.profile

import androidx.compose.foundation.layout.*
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.capstone.datara.ui.auth.components.DataraIcons
import com.capstone.datara.ui.components.*
import com.capstone.datara.ui.theme.QuotaTheme

@Composable
fun ProfileScreen(profile: ProfileUiState, email: String?, onBack: () -> Unit,
    onSaveName: (String) -> String?, onNameEdited: () -> Unit = {}, bottomBar: @Composable () -> Unit = {}) {
    QuotaTheme {
        val displayName = profile.name
        // Keyed on the stored name so the field fills in once the profile loads.
        var draft by rememberSaveable(displayName) { mutableStateOf(displayName) }
        var error by rememberSaveable { mutableStateOf<String?>(null) }
        val focus = LocalFocusManager.current
        val save = {
            error = onSaveName(draft)
            if (error == null) { draft = draft.trim(); focus.clearFocus() }
        }
        Scaffold(modifier = Modifier.imePadding(), containerColor = MaterialTheme.colorScheme.background,
            topBar = { DataraAppBar("Profile", onBack) }, bottomBar = {
                Column {
                    Surface(color = MaterialTheme.colorScheme.background) {
                        Box(Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) { DataraActionButton(if (profile.isSaving) "Saving…" else "Save name", { if (!profile.isSaving) save() }) }
                    }
                    bottomBar()
                }
            }) { insets ->
            Column(Modifier.fillMaxSize().padding(insets).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp)) {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Row(Modifier.fillMaxWidth().padding(24.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary) {
                            Box(Modifier.size(68.dp), contentAlignment = Alignment.Center) {
                                if (displayName.isBlank()) Icon(DataraIcons.Person, null, modifier = Modifier.size(32.dp))
                                else Text(displayName.take(1).uppercase(), style = MaterialTheme.typography.headlineLarge)
                            }
                        }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(displayName.ifBlank { "Your profile" }, style = MaterialTheme.typography.titleLarge)
                            Text(profile.username?.let { "@$it" } ?: "Your DATAra profile",
                                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                SectionTitle("Display name", "Choose the name shown in the app.")
                OutlinedTextField(draft, { draft = it; error = null; onNameEdited() }, label = { Text("Display name") },
                    placeholder = { Text("Enter your name") }, isError = error != null,
                    singleLine = true, modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium,
                    supportingText = { Text(error ?: "${DisplayNameValidator.MIN_LENGTH} to ${DisplayNameValidator.MAX_LENGTH} characters") },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { save() }))
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    ListItem(leadingContent = { TonalIcon(DataraIcons.Email) }, headlineContent = { Text(email ?: "Email unavailable") },
                        overlineContent = { Text("ACCOUNT EMAIL") }, supportingContent = { Text("Your sign-in email is read-only here.") })
                }
                if (profile.loadFailed) {
                    Text("Couldn’t load your saved name. Check your connection; you can still set a new one.",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                AnimatedVisibility(visible = profile.nameSaved) { Surface(color = MaterialTheme.colorScheme.tertiaryContainer, shape = MaterialTheme.shapes.medium) {
                    Text("Name saved.", modifier = Modifier.padding(16.dp).semantics { liveRegion = LiveRegionMode.Polite },
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onTertiaryContainer)
                } }
                AnimatedVisibility(visible = profile.saveError != null) { Surface(color = MaterialTheme.colorScheme.errorContainer, shape = MaterialTheme.shapes.medium) {
                    Text(profile.saveError.orEmpty(), modifier = Modifier.padding(16.dp).semantics { liveRegion = LiveRegionMode.Polite },
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onErrorContainer)
                } }
            }
        }
    }
}

@Preview(widthDp = 360, heightDp = 800)
@Preview(name = "Profile large text", widthDp = 360, heightDp = 800, fontScale = 2f)
@Composable
private fun ProfilePreview() {
    ProfileScreen(ProfileUiState(name = "Student", username = "student_01"), "student@example.com", {}, { null })
}
