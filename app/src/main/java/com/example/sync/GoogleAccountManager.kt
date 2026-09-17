package com.example.sync

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.UserRecoverableAuthException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/**
 * Google account session for Drive backup.
 *
 * UX: user taps "Google ile Giriş" → picks their own Google account →
 * grants Drive file scope → we store email + can fetch OAuth access tokens.
 *
 * We deliberately use Drive file scope (app-created files only), not full drive.
 */
class GoogleAccountManager private constructor(private val context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private val _accountEmail = MutableStateFlow(prefs.getString(KEY_EMAIL, null))
    val accountEmail: StateFlow<String?> = _accountEmail.asStateFlow()

    val isSignedIn: Boolean
        get() = lastSignedInAccount() != null || !_accountEmail.value.isNullOrBlank()

    fun signInClient(): GoogleSignInClient {
        val options = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(Scope(DRIVE_FILE_SCOPE))
            .build()
        return GoogleSignIn.getClient(context, options)
    }

    fun signInIntent(): Intent = signInClient().signInIntent

    fun lastSignedInAccount(): GoogleSignInAccount? =
        GoogleSignIn.getLastSignedInAccount(context)

    fun handleSignInResult(data: Intent?): Result<GoogleSignInAccount> {
        return try {
            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            val account = task.result
            persist(account)
            Result.success(account)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun persist(account: GoogleSignInAccount) {
        val email = account.email
        prefs.edit()
            .putString(KEY_EMAIL, email)
            .putString(KEY_DISPLAY, account.displayName)
            .apply()
        _accountEmail.value = email
    }

    fun signOut(onDone: (() -> Unit)? = null) {
        signInClient().signOut().addOnCompleteListener {
            prefs.edit().remove(KEY_EMAIL).remove(KEY_DISPLAY).apply()
            _accountEmail.value = null
            onDone?.invoke()
        }
    }

    /**
     * Blocking-safe token fetch on IO dispatcher.
     * Throws [UserRecoverableAuthException] if the user must approve extra consent
     * (caller should launch the recoverable intent).
     */
    suspend fun getAccessToken(): String = withContext(Dispatchers.IO) {
        val account = lastSignedInAccount()
            ?: throw IllegalStateException("Google hesabı bağlı değil. Önce giriş yap.")
        val email = account.account?.name
            ?: account.email
            ?: throw IllegalStateException("Hesap e-postası okunamadı.")
        // oauth2: scope form required by GoogleAuthUtil
        GoogleAuthUtil.getToken(context, email, "oauth2:$DRIVE_FILE_SCOPE")
    }

    fun clearTokenCache() {
        try {
            val account = lastSignedInAccount() ?: return
            val email = account.account?.name ?: account.email ?: return
            GoogleAuthUtil.clearToken(context, email)
        } catch (_: Exception) {
        }
    }

    fun displayName(): String? = prefs.getString(KEY_DISPLAY, null)

    companion object {
        private const val PREFS = "kalkan_google_account"
        private const val KEY_EMAIL = "email"
        private const val KEY_DISPLAY = "display"
        /** App-created files only — no access to the rest of the user's Drive. */
        const val DRIVE_FILE_SCOPE = "https://www.googleapis.com/auth/drive.file"

        @Volatile
        private var instance: GoogleAccountManager? = null

        fun getInstance(context: Context): GoogleAccountManager {
            return instance ?: synchronized(this) {
                instance ?: GoogleAccountManager(context.applicationContext).also { instance = it }
            }
        }
    }
}
