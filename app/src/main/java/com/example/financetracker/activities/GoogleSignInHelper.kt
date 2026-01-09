package com.example.financetracker.helpers

import android.app.Activity
import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.tasks.Task

class GoogleSignInHelper(private val context: Context) {

    companion object {
        const val RC_SIGN_IN = 1001
    }

    private val googleSignInClient: GoogleSignInClient

    init {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(
                com.google.android.gms.common.api.Scope("https://www.googleapis.com/auth/spreadsheets")
            )
            .build()

        googleSignInClient = GoogleSignIn.getClient(context, gso)
    }

    fun signIn(activity: Activity) {
        val signInIntent = googleSignInClient.signInIntent
        activity.startActivityForResult(signInIntent, RC_SIGN_IN)
    }

    fun getSignedInAccountFromIntent(data: Intent?): Task<GoogleSignInAccount> {
        return GoogleSignIn.getSignedInAccountFromIntent(data)
    }

    fun getLastSignedInAccount(): GoogleSignInAccount? {
        return GoogleSignIn.getLastSignedInAccount(context)
    }

    fun signOut(onComplete: () -> Unit) {
        googleSignInClient.signOut().addOnCompleteListener {
            onComplete()
        }
    }
}
