package com.sitelens.photos

object DriveAuthorization {
    const val NO_RESULT = "Google sign-in did not finish. If you did not close it, the app's Google OAuth registration or test-user access may need setup. Try again and check the Google account you selected."
    fun readToken(hasResultData: Boolean, parseToken: () -> String?): String {
        check(hasResultData) { NO_RESULT }
        // Google may return an ApiException in the result intent even when Android
        // reports RESULT_CANCELED. Always parse that intent to retain the real error.
        return parseToken()?.takeIf { it.isNotBlank() }
            ?: error("Google did not grant Drive access. Retry and approve Drive access for SiteLens.")
    }
    fun failure(status: Int?): String = when (status) {
        10 -> "Google rejected this app's configuration (code 10). Register com.sitelens.photos with this APK's signing SHA-1 in Google Cloud, enable Drive API, and add your account as an OAuth test user."
        7 -> "Google could not connect (code 7). Check your internet connection and try again."
        16, 12501 -> "Google sign-in was canceled or closed (code $status). Retry to choose an account and allow Drive access. If you did not cancel, check the app's OAuth registration and test-user access."
        17 -> "Google sign-in is unavailable (code 17). Update Google Play services, then try again."
        null -> NO_RESULT
        else -> "Google sign-in failed (code $status). Retry and check the app's OAuth registration, test-user account, and Google Play services."
    }
}
