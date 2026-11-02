package com.dk.himachalfarmdirect.ui.auth

import android.annotation.SuppressLint
import android.app.Activity
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.NoCredentialException
import com.dk.himachalfarmdirect.R
import com.dk.himachalfarmdirect.ui.theme.FarmColors
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * Easiest first: one-tap "Continue with Google" (free, no SMS, works on Firebase Spark plan).
 */
@Composable
fun LoginScreen(onSignedIn: (name: String) -> Unit, onHelp: () -> Unit) {
    val activity = LocalContext.current as Activity
    val auth = remember { FirebaseAuth.getInstance() }
    val scope = rememberCoroutineScope()

    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    // ---------------- Google
    fun googleSignIn() = scope.launch {
        error = null
        val clientId = webClientId(activity)
        if (clientId == null) {
            error = "Google login is not switched on yet. Admin: enable Google in Firebase → Authentication → Sign-in method, then update google-services.json."
            return@launch
        }
        busy = true
        try {
            val option = GetSignInWithGoogleOption.Builder(clientId).build()
            val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
            val result = CredentialManager.create(activity).getCredential(activity, request)
            val cred = result.credential
            if (cred is CustomCredential && cred.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                val google = GoogleIdTokenCredential.createFrom(cred.data)
                auth.signInWithCredential(GoogleAuthProvider.getCredential(google.idToken, null)).await()
                onSignedIn(google.displayName ?: auth.currentUser?.displayName ?: "")
            } else error = "Couldn't read the Google account. Please try again."
        } catch (_: GetCredentialCancellationException) {
            // user closed the sheet — nothing to show
        } catch (_: NoCredentialException) {
            error = "No Google account on this phone. Add one in phone Settings → Accounts → Add account → Google, then try again."
        } catch (e: Exception) {
            error = "Google login failed: ${e.message?.take(140) ?: "unknown error"}"
        } finally { busy = false }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp).imePadding(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Spacer(Modifier.height(32.dp))
        Image(painterResource(R.drawable.ic_logo), null, Modifier.size(96.dp))
        Spacer(Modifier.height(12.dp))
        Text("Himachal Farm Direct", style = MaterialTheme.typography.headlineSmall, color = FarmColors.Forest)
        Text("Fresh from farmers near you", color = FarmColors.Muted)
        Spacer(Modifier.height(32.dp))

        // Big, obvious, one tap.
        OutlinedButton(
            onClick = { googleSignIn() }, enabled = !busy,
            border = BorderStroke(1.5.dp, FarmColors.Line),
            modifier = Modifier.fillMaxWidth().height(58.dp),
        ) {
            if (busy) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
            else {
                GoogleG()
                Spacer(Modifier.width(12.dp))
                Text("Continue with Google", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = FarmColors.Ink)
            }
        }
        Text("One tap — uses the Google account already on your phone", style = MaterialTheme.typography.bodyMedium,
            color = FarmColors.Muted, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 6.dp))

        error?.let {
            Spacer(Modifier.height(12.dp))
            Text(it, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
        }
        Spacer(Modifier.height(24.dp))
        TextButton(onClick = onHelp) { Text("Need help signing in?") }
    }
}

/**
 * The Google web client ID is generated into resources from google-services.json only after
 * Google sign-in is enabled in Firebase; looked up at runtime so the app builds either way.
 */
@SuppressLint("DiscouragedApi")
private fun webClientId(activity: Activity): String? {
    val id = activity.resources.getIdentifier("default_web_client_id", "string", activity.packageName)
    return if (id == 0) null else activity.getString(id).takeIf { it.isNotBlank() }
}

@Composable
private fun GoogleG() {
    Box(Modifier.size(26.dp).background(Color.White, CircleShape), contentAlignment = Alignment.Center) {
        Text("G", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = Color(0xFF4285F4))
    }
}
