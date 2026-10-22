package com.example.loadwatch.core.authentication

import android.content.Context
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.exceptions.ClearCredentialException
import com.example.loadwatch.core.common.LogTags
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

// Firebase Authentication with Google as the only provider. Never logs tokens or user ids.
@Singleton
class FirebaseAuthSession @Inject constructor(
    private val firebaseAuth: FirebaseAuth,
    @param:ApplicationContext private val context: Context,
) : AuthSession {

    private val userId = MutableStateFlow(firebaseAuth.currentUser?.uid)
    override val signedInUserId: StateFlow<String?> = userId.asStateFlow()

    init {
        firebaseAuth.addAuthStateListener { auth ->
            val uid = auth.currentUser?.uid
            if (uid != userId.value) {
                Log.i(LogTags.AUTH, if (uid == null) "Auth state changed: signed out" else "Auth state changed: signed in")
            }
            userId.value = uid
        }
        Log.i(LogTags.AUTH, "Restored session: ${if (userId.value == null) "signed out" else "signed in"}")
    }

    override suspend fun idToken(forceRefresh: Boolean): String? {
        val user = firebaseAuth.currentUser ?: return null
        return user.getIdToken(forceRefresh).await().token
    }

    override suspend fun signInWithGoogleIdToken(googleIdToken: String) {
        Log.i(LogTags.AUTH, "Exchanging the Google credential for a Firebase session")
        val credential = GoogleAuthProvider.getCredential(googleIdToken, null)
        firebaseAuth.signInWithCredential(credential).await()
        userId.value = firebaseAuth.currentUser?.uid
        Log.i(LogTags.AUTH, "Firebase sign-in succeeded")
    }

    override suspend fun signOut() {
        firebaseAuth.signOut()
        userId.value = null
        // Clears the remembered Google account choice so the next sign-in shows the account picker again
        try {
            CredentialManager.create(context).clearCredentialState(ClearCredentialStateRequest())
        } catch (e: ClearCredentialException) {
            Log.w(LogTags.AUTH, "Could not clear the credential state: ${e.type}")
        }
        Log.i(LogTags.AUTH, "Signed out")
    }
}
