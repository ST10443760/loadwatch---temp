package com.example.loadwatch.core.authentication

// The signed-in identity. Implemented by Firebase Authentication with Google sign-in (FR-AUTH-01/02);
// until then PlaceholderAuthSession stands in so the rest of the app can be built against this interface.
interface AuthSession {

    // The Firebase UID of the signed-in user, or null when signed out
    suspend fun currentUserId(): String?
}
