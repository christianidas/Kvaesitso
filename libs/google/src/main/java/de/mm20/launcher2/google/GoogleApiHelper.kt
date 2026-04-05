package de.mm20.launcher2.google

import android.accounts.Account
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.util.Log
import androidx.activity.result.ActivityResultLauncher
import androidx.core.content.edit
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.google.android.gms.auth.GoogleAuthException
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.UserRecoverableAuthException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

class GoogleApiHelper(val context: Context) {

    private val preferences by lazy {
        createPreferences()
    }

    private fun createPreferences(catchErrors: Boolean = true): SharedPreferences {
        try {
            val masterKey =
                MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
            return EncryptedSharedPreferences.create(
                context,
                "google",
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: IOException) {
            if (!catchErrors) throw e
            File(context.filesDir, "../shared_prefs/google.xml").delete()
            return createPreferences(false)
        }
    }

    fun getAccountEmail(): String? = preferences.getString("account_email", null)

    fun isSignedIn(): Boolean = getAccountEmail() != null

    /**
     * Returns an access token, or null if unavailable.
     * Throws [UserRecoverableAuthException] if the user needs to grant consent —
     * callers should launch the exception's intent to prompt the user.
     */
    @Throws(UserRecoverableAuthException::class)
    suspend fun getAccessToken(): String? {
        val email = getAccountEmail() ?: return null
        return withContext(Dispatchers.IO) {
            try {
                GoogleAuthUtil.getToken(
                    context,
                    Account(email, "com.google"),
                    "oauth2:$TASKS_SCOPE"
                )
            } catch (e: UserRecoverableAuthException) {
                throw e
            } catch (e: GoogleAuthException) {
                Log.e("GoogleApiHelper", "Auth error getting token", e)
                null
            } catch (e: IOException) {
                Log.e("GoogleApiHelper", "IO error getting token", e)
                null
            }
        }
    }

    /**
     * Returns an access token, or null if unavailable or consent not yet granted.
     * Does not throw — safe for background use.
     */
    suspend fun getAccessTokenOrNull(): String? {
        return try {
            getAccessToken()
        } catch (e: UserRecoverableAuthException) {
            null
        }
    }

    fun invalidateToken(token: String) {
        try {
            GoogleAuthUtil.clearToken(context, token)
        } catch (e: Exception) {
            Log.w("GoogleApiHelper", "Could not invalidate cached token", e)
        }
    }

    fun signOut() {
        preferences.edit {
            remove("account_email")
        }
    }

    fun getLoginIntent(): Intent = Intent(context, GoogleLoginActivity::class.java)

    internal fun setAccount(email: String) {
        preferences.edit {
            putString("account_email", email)
        }
    }

    /**
     * Returns an access token for the given scopes, or null if unavailable.
     * Throws [UserRecoverableAuthException] if the user needs to grant consent.
     */
    @Throws(UserRecoverableAuthException::class)
    suspend fun getAccessTokenForScopes(vararg scopes: String): String? {
        val email = getAccountEmail() ?: return null
        val scopeString = scopes.joinToString(" ")
        return withContext(Dispatchers.IO) {
            try {
                GoogleAuthUtil.getToken(
                    context,
                    Account(email, "com.google"),
                    "oauth2:$scopeString"
                )
            } catch (e: UserRecoverableAuthException) {
                throw e
            } catch (e: GoogleAuthException) {
                Log.e("GoogleApiHelper", "Auth error getting token", e)
                null
            } catch (e: IOException) {
                Log.e("GoogleApiHelper", "IO error getting token", e)
                null
            }
        }
    }

    /**
     * Returns an access token for the given scopes, or null if unavailable or consent not yet granted.
     * Does not throw — safe for background use.
     */
    suspend fun getAccessTokenForScopesOrNull(vararg scopes: String): String? {
        return try {
            getAccessTokenForScopes(*scopes)
        } catch (e: UserRecoverableAuthException) {
            null
        }
    }

    companion object {
        const val TASKS_SCOPE = "https://www.googleapis.com/auth/tasks"
        const val HOME_SCOPE_RUN = "https://www.googleapis.com/auth/home.run"
        const val HOME_SCOPE_READ = "https://www.googleapis.com/auth/home.read"
    }
}
