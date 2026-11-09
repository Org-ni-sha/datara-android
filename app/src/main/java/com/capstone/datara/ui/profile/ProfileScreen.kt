package com.capstone.datara.ui.profile

import androidx.compose.foundation.layout.*
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
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
import com.capstone.datara.ui.settings.AvatarInitial

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
                        Box(Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) { DataraActionButton(if (profile.isSaving) "Saving…" else "Save name", { save() }, enabled = !profile.isSaving) }
                    }
                    bottomBar()
                }
            }) { insets ->
            Column(Modifier.fillMaxSize().padding(insets).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)) {
                if (profile.isLoading) LinearProgressIndicator(Modifier.fillMaxWidth().semantics { contentDescription = "Loading your profile" })
                Card(shape = MaterialTheme.shapes.large, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer)) {
                    Row(Modifier.fillMaxWidth().padding(20.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        AvatarInitial(displayName, size = 68.dp)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(displayName.ifBlank { "Your profile" }, style = MaterialTheme.typography.titleLarge)
                            Text(profile.username?.let { "@$it" } ?: "Your DATAra profile",
                                style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
                SectionTitle("Display name", "Optional. Home greets you by this name, or by @username if it’s empty.")
                OutlinedTextField(draft, { draft = it; error = null; onNameEdited() }, label = { Text("Display name") },
                    placeholder = { Text("Enter your name") }, isError = error != null,
                    singleLine = true, modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium,
                    supportingText = { Text(error ?: "${DisplayNameValidator.MIN_LENGTH} to ${DisplayNameValidator.MAX_LENGTH} characters") },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { if (!profile.isSaving) save() }))
                Card(shape = MaterialTheme.shapes.large, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                    ListItem(colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow), leadingContent = { TonalIcon(DataraIcons.Email) }, headlineContent = { Text(email ?: "Email unavailable") },
                        overlineContent = { Text("Sign-in email") }, supportingContent = { Text("This is the email you sign in with. It can’t be changed here.") })
                }
                if (profile.loadFailed) {
                    Text("Couldn’t load your saved name. Check your connection; you can still set a new one.",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                AnimatedVisibility(visible = profile.nameSaved) { Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = MaterialTheme.shapes.medium) {
                    Text("Name saved.", modifier = Modifier.padding(16.dp).semantics { liveRegion = LiveRegionMode.Polite },
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
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
