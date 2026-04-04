package de.mm20.launcher2.google

import android.os.Bundle
import android.util.Log
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetCredentialResponse
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.auth.UserRecoverableAuthException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import kotlinx.coroutines.launch

class GoogleLoginActivity : AppCompatActivity() {

    private val googleApiHelper by lazy { GoogleApiHelper(applicationContext) }

    private var showError = mutableStateOf(false)

    private val consentLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            // Consent granted — token will work on next getAccessToken() call
            finish()
        } else {
            showError.value = true
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            val dark = isSystemInDarkTheme()

            LaunchedEffect(dark) {
                enableEdgeToEdge(
                    statusBarStyle = if (dark) SystemBarStyle.dark(0) else SystemBarStyle.light(0, 0x33000000.toInt()),
                    navigationBarStyle = if (dark) SystemBarStyle.dark(0) else SystemBarStyle.light(0, 0x33000000.toInt()),
                )
            }

            val error by showError

            LaunchedEffect(Unit) {
                if (!error) {
                    launchSignIn()
                }
            }

            MaterialTheme(
                colorScheme = if (dark) googleDark else googleLight
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surface),
                    contentAlignment = Alignment.Center
                ) {
                    if (error) {
                        Text(
                            text = stringResource(R.string.google_sign_in_error),
                            color = MaterialTheme.colorScheme.error
                        )
                    } else {
                        CircularProgressIndicator()
                    }
                }
            }
        }
    }

    private fun launchSignIn() {
        val credentialManager = CredentialManager.create(this)

        val clientId = BuildConfig.GOOGLE_CLIENT_ID
        if (clientId.isBlank()) {
            Log.e("GoogleLoginActivity", "google.clientId not set in local.properties")
            showError.value = true
            return
        }

        val googleIdOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false)
            .setAutoSelectEnabled(true)
            .setServerClientId(clientId)
            .build()

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()

        lifecycleScope.launch {
            try {
                val result = credentialManager.getCredential(this@GoogleLoginActivity, request)
                handleSignIn(result)
            } catch (e: GetCredentialCancellationException) {
                finish()
            } catch (e: GetCredentialException) {
                Log.e("GoogleLoginActivity", "Sign in failed", e)
                showError.value = true
            }
        }
    }

    private fun handleSignIn(result: GetCredentialResponse) {
        val credential = result.credential
        try {
            val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
            val email = googleIdTokenCredential.id
            googleApiHelper.setAccount(email)
            // Now request the Tasks scope token — may require user consent
            requestTasksConsent()
        } catch (e: Exception) {
            Log.e("GoogleLoginActivity", "Failed to extract Google credential", e)
            showError.value = true
        }
    }

    private fun requestTasksConsent() {
        lifecycleScope.launch {
            try {
                googleApiHelper.getAccessToken()
                // Token obtained without consent prompt — done
                finish()
            } catch (e: UserRecoverableAuthException) {
                // User needs to consent to the Tasks scope
                val intent = e.intent
                if (intent != null) {
                    consentLauncher.launch(intent)
                } else {
                    showError.value = true
                }
            }
        }
    }

    private val googleLight = lightColorScheme(
        primary = Color(0xFF1A73E8),
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFFD3E3FD),
        onPrimaryContainer = Color(0xFF001B3E),
        surface = Color(0xFFF9F9FC),
        onSurface = Color(0xFF1A1C1E),
        error = Color(0xFFBA1A1A),
        onError = Color(0xFFFFFFFF),
    )

    private val googleDark = darkColorScheme(
        primary = Color(0xFF8AB4F8),
        onPrimary = Color(0xFF003063),
        primaryContainer = Color(0xFF004690),
        onPrimaryContainer = Color(0xFFD3E3FD),
        surface = Color(0xFF1A1C1E),
        onSurface = Color(0xFFE2E2E5),
        error = Color(0xFFFFB4AB),
        onError = Color(0xFF690004),
    )
}
