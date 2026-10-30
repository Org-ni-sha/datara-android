package com.capstone.datara.ui.auth

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.capstone.datara.BuildConfig
import com.capstone.datara.ui.auth.components.DataraOrDivider
import com.capstone.datara.ui.auth.components.DataraSecondaryButton
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import java.security.MessageDigest
import java.security.SecureRandom
import kotlinx.coroutines.launch

/** A Google ID token plus the unhashed nonce Supabase needs to verify it. */
data class GoogleIdToken(val idToken: String, val rawNonce: String)

sealed interface GoogleSignInResult {
    data class Success(val token: GoogleIdToken) : GoogleSignInResult
    /** The user closed the account picker; show nothing. */
    data object Cancelled : GoogleSignInResult
    data class Failure(val message: String) : GoogleSignInResult
}

/** Google sign-in is offered only when a Web client ID is set in local.properties. */
val isGoogleSignInConfigured: Boolean
    get() = BuildConfig.GOOGLE_WEB_CLIENT_ID.isNotBlank()

/**
 * Shows the "Sign in with Google" sheet through Credential Manager and returns an ID token.
 *
 * Google receives the SHA-256 of a random nonce and embeds it in the token; Supabase is given the
 * raw nonce and checks the hash, so a token captured elsewhere cannot be replayed here.
 * [context] must be an Activity context, since the sheet is shown over it.
 */
suspend fun requestGoogleIdToken(context: Context): GoogleSignInResult {
    val rawNonce = randomNonce()
    val option = GetSignInWithGoogleOption.Builder(BuildConfig.GOOGLE_WEB_CLIENT_ID)
        .setNonce(sha256Hex(rawNonce))
        .build()
    val request = GetCredentialRequest.Builder()
        .addCredentialOption(option)
        .build()

    return try {
        val credential = CredentialManager.create(context).getCredential(context, request).credential
        if (credential is CustomCredential &&
            credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
        ) {
            val idToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
            GoogleSignInResult.Success(GoogleIdToken(idToken, rawNonce))
        } else {
            GoogleSignInResult.Failure(GOOGLE_GENERIC_ERROR)
        }
    } catch (e: GetCredentialCancellationException) {
        GoogleSignInResult.Cancelled
    } catch (e: NoCredentialException) {
        GoogleSignInResult.Failure("No Google account is available on this device.")
    } catch (e: GetCredentialException) {
        GoogleSignInResult.Failure(GOOGLE_GENERIC_ERROR)
    } catch (e: GoogleIdTokenParsingException) {
        GoogleSignInResult.Failure(GOOGLE_GENERIC_ERROR)
    }
}

/**
 * "or" divider plus the Google button, used by both Login and Register. Renders nothing when
 * Google sign-in isn't configured, so builds without a client ID keep working.
 */
@Composable
fun GoogleSignInSection(
    text: String,
    enabled: Boolean,
    viewModel: AuthViewModel
) {
    if (!isGoogleSignInConfigured) return
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    Column {
        DataraOrDivider()
        Spacer(modifier = Modifier.height(20.dp))
        DataraSecondaryButton(
            text = text,
            enabled = enabled,
            onClick = {
                viewModel.clearError()
                scope.launch {
                    when (val result = requestGoogleIdToken(context)) {
                        is GoogleSignInResult.Success -> viewModel.signInWithGoogle(result.token)
                        is GoogleSignInResult.Failure -> viewModel.showError(result.message)
                        GoogleSignInResult.Cancelled -> Unit
                    }
                }
            }
        )
    }
}

private const val GOOGLE_GENERIC_ERROR = "Couldn't sign in with Google. Please try again."

private fun randomNonce(): String {
    val bytes = ByteArray(32).also { SecureRandom().nextBytes(it) }
    return bytes.joinToString("") { "%02x".format(it) }
}

private fun sha256Hex(value: String): String =
    MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray())
        .joinToString("") { "%02x".format(it) }
