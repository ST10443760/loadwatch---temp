package com.example.loadwatch.core.authentication

import kotlinx.coroutines.flow.StateFlow

// The signed-in Firebase identity (FR-AUTH-01 to 04). Firebase persists the session on the device, so it
// survives force-stop and relaunch (FR-AUTH-02).
interface AuthSession {

    // The Firebase UID of the signed-in user, or null when signed out. Updates as the auth state changes.
    val signedInUserId: StateFlow<String?>

    fun currentUserId(): String? = signedInUserId.value

    // A Firebase ID token for the API's Authorization header, or null when signed out.
    // Firebase returns a cached token and refreshes it once it is close to expiry; forceRefresh always
    // fetches a new one.
    suspend fun idToken(forceRefresh: Boolean = false): String?

    // Exchanges a Google ID token from Credential Manager for a Firebase session. Throws on failure.
    suspend fun signInWithGoogleIdToken(googleIdToken: String)

    suspend fun signOut()
}
