package com.dk.himachalfarmdirect.ui.auth

import android.app.Activity
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.dk.himachalfarmdirect.R
import com.dk.himachalfarmdirect.ui.theme.FarmColors
import com.google.firebase.FirebaseException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.concurrent.TimeUnit

/** Mobile number + OTP sign-in: no passwords to remember (Buyer Help → Registration and login). */
@Composable
fun LoginScreen(onSignedIn: (name: String) -> Unit, onHelp: () -> Unit) {
    val activity = LocalContext.current as Activity
    val auth = remember { FirebaseAuth.getInstance() }
    val scope = rememberCoroutineScope()

    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var otp by remember { mutableStateOf("") }
    var verificationId by remember { mutableStateOf<String?>(null) }
    var resendToken by remember { mutableStateOf<PhoneAuthProvider.ForceResendingToken?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun signIn(cred: PhoneAuthCredential) = scope.launch {
        busy = true
        runCatching { auth.signInWithCredential(cred).await() }
            .onSuccess { onSignedIn(name) }
            .onFailure { error = "That code didn't work. Check it and try again." }
        busy = false
    }

    fun sendOtp(resend: Boolean = false) {
        val digits = phone.filter { it.isDigit() }.takeLast(10)
        if (digits.length != 10) { error = "Enter your 10-digit mobile number"; return }
        busy = true; error = null
        val builder = PhoneAuthOptions.newBuilder(auth)
            .setPhoneNumber("+91$digits")
            .setTimeout(60L, TimeUnit.SECONDS)
            .setActivity(activity)
            .setCallbacks(object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
                override fun onVerificationCompleted(c: PhoneAuthCredential) { signIn(c) }   // auto-read SMS
                override fun onVerificationFailed(e: FirebaseException) {
                    busy = false
                    error = "Could not send the code. Check the number and your internet."
                }
                override fun onCodeSent(id: String, token: PhoneAuthProvider.ForceResendingToken) {
                    busy = false; verificationId = id; resendToken = token
                }
            })
        if (resend) resendToken?.let { builder.setForceResendingToken(it) }
        PhoneAuthProvider.verifyPhoneNumber(builder.build())
    }

    Column(
        Modifier.fillMaxSize().padding(24.dp).imePadding(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Image(painterResource(R.drawable.ic_logo), null, Modifier.size(88.dp))
        Spacer(Modifier.height(12.dp))
        Text("Himachal Farm Direct", style = MaterialTheme.typography.headlineSmall, color = FarmColors.Forest)
        Text("Sign in with your mobile number", color = FarmColors.Muted)
        Spacer(Modifier.height(24.dp))

        if (verificationId == null) {
            OutlinedTextField(name, { name = it }, label = { Text("Your name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                phone, { phone = it.filter(Char::isDigit).take(10) },
                label = { Text("Mobile number") }, prefix = { Text("+91 ") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(16.dp))
            Button(onClick = { sendOtp() }, enabled = !busy, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                if (busy) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp) else Text("Send OTP")
            }
        } else {
            Text("Enter the 6-digit code sent to +91 $phone", style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                otp, { otp = it.filter(Char::isDigit).take(6) }, label = { Text("OTP") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = { verificationId?.let { signIn(PhoneAuthProvider.getCredential(it, otp)) } },
                enabled = !busy && otp.length == 6, modifier = Modifier.fillMaxWidth().height(52.dp),
            ) { if (busy) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp) else Text("Verify & continue") }
            TextButton(onClick = { sendOtp(resend = true) }, enabled = !busy) { Text("Resend code") }
            TextButton(onClick = { verificationId = null; otp = "" }) { Text("Change number") }
        }
        error?.let { Spacer(Modifier.height(8.dp)); Text(it, color = MaterialTheme.colorScheme.error) }
        Spacer(Modifier.height(24.dp))
        TextButton(onClick = onHelp) { Text("Need help signing in?") }
    }
}
