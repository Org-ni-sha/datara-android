package com.capstone.datara.ui.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.capstone.datara.R
import com.capstone.datara.ui.auth.AuthViewModel
import com.capstone.datara.ui.components.DataraNoticeCard
import com.capstone.datara.ui.components.DataraPageHeader
import com.capstone.datara.ui.components.DataraStatusBadge
import com.capstone.datara.ui.components.DataraSurfaceCard
import com.capstone.datara.ui.theme.DATAraTheme
import com.capstone.datara.ui.theme.DataraBlueSoft
import com.capstone.datara.ui.theme.DataraDarkBg
import com.capstone.datara.ui.theme.DataraInputBorder
import com.capstone.datara.ui.theme.DataraNeonBlue
import com.capstone.datara.ui.theme.DataraTextSecondary
import java.util.Locale

enum class ProfileMode {
    VIEW,
    EDIT
}

data class ProfileUiState(
    val name: String,
    val email: String,
    val secondaryPhone: String = "",
    val mode: ProfileMode = ProfileMode.VIEW,
    val isPersisted: Boolean = false
)

@Composable
fun ProfileScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AuthViewModel = hiltViewModel()
) {
    val email = viewModel.currentUserEmail ?: ""
    val derivedName = email.substringBefore("@").toProfileDisplayName()
        .ifBlank { stringResource(R.string.profile_default_name) }
    ProfileContent(
        initialState = ProfileUiState(name = derivedName, email = email),
        onBack = onBack,
        modifier = modifier
    )
}

@Composable
private fun ProfileContent(
    initialState: ProfileUiState,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var savedName by rememberSaveable { mutableStateOf(initialState.name) }
    var savedPhone by rememberSaveable { mutableStateOf(initialState.secondaryPhone) }
    var draftName by rememberSaveable { mutableStateOf(savedName) }
    var draftPhone by rememberSaveable { mutableStateOf(savedPhone) }
    var modeName by rememberSaveable { mutableStateOf(initialState.mode.name) }
    var submitted by rememberSaveable { mutableStateOf(false) }
    var successVisible by rememberSaveable { mutableStateOf(false) }
    val mode = ProfileMode.valueOf(modeName)
    val nameValid = draftName.isNotBlank()
    val phoneValid = draftPhone.isBlank() || PHONE_PATTERN.matches(draftPhone.trim())

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = DataraDarkBg
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().widthIn(max = 720.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                DataraPageHeader(
                    eyebrow = stringResource(R.string.profile_eyebrow),
                    title = stringResource(R.string.profile_title),
                    subtitle = stringResource(R.string.profile_subtitle),
                    onBack = onBack,
                    backContentDescription = stringResource(R.string.action_back),
                    trailingContent = {
                        DataraStatusBadge(
                            text = stringResource(R.string.status_local_preview),
                            color = DataraBlueSoft
                        )
                    }
                )

                DataraNoticeCard(
                    title = stringResource(R.string.profile_local_notice_title),
                    message = stringResource(R.string.profile_local_notice_body),
                    accent = DataraBlueSoft
                )

                if (successVisible) {
                    DataraNoticeCard(
                        title = stringResource(R.string.profile_save_success),
                        message = stringResource(R.string.profile_local_notice_body),
                        accent = com.capstone.datara.ui.theme.DataraNeonGreen,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
                    )
                }

                DataraSurfaceCard {
                    Text(
                        text = if (mode == ProfileMode.EDIT) {
                            stringResource(R.string.profile_edit_title)
                        } else {
                            stringResource(R.string.profile_view_title)
                        },
                        style = MaterialTheme.typography.titleLarge
                    )
                    Spacer(Modifier.height(14.dp))
                    OutlinedTextField(
                        value = if (mode == ProfileMode.EDIT) draftName else savedName,
                        onValueChange = { draftName = it },
                        readOnly = mode == ProfileMode.VIEW,
                        label = { Text(stringResource(R.string.profile_name_label)) },
                        supportingText = {
                            Text(
                                if (submitted && !nameValid) {
                                    stringResource(R.string.profile_name_required)
                                } else {
                                    stringResource(R.string.profile_derived_name_support)
                                }
                            )
                        },
                        isError = submitted && !nameValid,
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = initialState.email,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(R.string.profile_email_label)) },
                        supportingText = { Text(stringResource(R.string.profile_email_support)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = if (mode == ProfileMode.EDIT) draftPhone else savedPhone,
                        onValueChange = { draftPhone = it.filterPhoneCharacters() },
                        readOnly = mode == ProfileMode.VIEW,
                        label = { Text(stringResource(R.string.profile_phone_label)) },
                        supportingText = {
                            Text(
                                if (submitted && !phoneValid) {
                                    stringResource(R.string.profile_phone_invalid)
                                } else {
                                    stringResource(R.string.profile_phone_support)
                                }
                            )
                        },
                        isError = submitted && !phoneValid,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(18.dp))

                    if (mode == ProfileMode.VIEW) {
                        Button(
                            onClick = {
                                draftName = savedName
                                draftPhone = savedPhone
                                submitted = false
                                successVisible = false
                                modeName = ProfileMode.EDIT.name
                            },
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = DataraNeonBlue)
                        ) {
                            Text(stringResource(R.string.action_edit))
                        }
                    } else {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedButton(
                                onClick = {
                                    draftName = savedName
                                    draftPhone = savedPhone
                                    submitted = false
                                    modeName = ProfileMode.VIEW.name
                                },
                                modifier = Modifier.weight(1f).height(52.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, DataraInputBorder)
                            ) {
                                Text(stringResource(R.string.action_cancel), color = DataraBlueSoft)
                            }
                            Button(
                                onClick = {
                                    submitted = true
                                    if (nameValid && phoneValid) {
                                        savedName = draftName.trim()
                                        savedPhone = draftPhone.trim()
                                        modeName = ProfileMode.VIEW.name
                                        successVisible = true
                                    }
                                },
                                modifier = Modifier.weight(1f).height(52.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = DataraNeonBlue)
                            ) {
                                Text(stringResource(R.string.action_save))
                            }
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
            }
        }
    }
}

private fun String.toProfileDisplayName(): String =
    split('.', '_', '-').filter(String::isNotBlank).joinToString(" ") { part ->
        part.replaceFirstChar { char ->
            if (char.isLowerCase()) char.titlecase(Locale.getDefault()) else char.toString()
        }
    }

private fun String.filterPhoneCharacters(): String = filter { it.isDigit() || it == '+' || it == ' ' || it == '-' }

private val PHONE_PATTERN = Regex("^\\+?[0-9][0-9 -]{6,15}$")

@Preview(showBackground = true, backgroundColor = 0xFF0C101A, heightDp = 900)
@Composable
private fun ProfileViewPreview() {
    DATAraTheme {
        ProfileContent(
            initialState = ProfileUiState(
                name = "Charlie Santos",
                email = "charlie@ustp.edu.ph",
                secondaryPhone = "+63 912 345 6789"
            ),
            onBack = {}
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0C101A, heightDp = 900)
@Composable
private fun ProfileEditPreview() {
    DATAraTheme {
        ProfileContent(
            initialState = ProfileUiState(
                name = "Charlie Santos",
                email = "charlie@ustp.edu.ph",
                mode = ProfileMode.EDIT
            ),
            onBack = {}
        )
    }
}
