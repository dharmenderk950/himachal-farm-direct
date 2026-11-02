package com.dk.himachalfarmdirect.ui.common

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dk.himachalfarmdirect.ui.theme.FarmColors

/** 10-digit Indian mobile → "98765 43210"; anything else unchanged. */
fun prettyPhone(p: String): String {
    val d = p.filter(Char::isDigit).takeLast(10)
    return if (d.length == 10) "+91 ${d.take(5)} ${d.drop(5)}" else p
}

fun callNumber(context: Context, phone: String) {
    val d = phone.filter(Char::isDigit).takeLast(10)
    runCatching { context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:+91$d"))) }
}

fun whatsAppNumber(context: Context, phone: String, text: String) {
    val d = phone.filter(Char::isDigit).takeLast(10)
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/91$d?text=${Uri.encode(text)}"))) }
        .onFailure { Toast.makeText(context, "WhatsApp is not installed", Toast.LENGTH_SHORT).show() }
}

/** Contact panel with big Call and WhatsApp buttons. Shows nothing if no number is known. */
@Composable
fun ContactPanel(title: String, name: String, phone: String, whatsappText: String, extra: String = "") {
    if (phone.filter(Char::isDigit).length < 10) return
    val context = LocalContext.current
    Column(
        Modifier.fillMaxWidth().padding(top = 10.dp).background(FarmColors.Mint, RoundedCornerShape(12.dp)).padding(12.dp),
    ) {
        Text(title, color = FarmColors.Muted)
        Text("$name  ·  ${prettyPhone(phone)}", fontWeight = FontWeight.SemiBold, color = FarmColors.Forest)
        if (extra.isNotBlank()) Text(extra, color = FarmColors.Ink)
        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { callNumber(context, phone) }) {
                Icon(Icons.Default.Call, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Call")
            }
            OutlinedButton(onClick = { whatsAppNumber(context, phone, whatsappText) },
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF128C7E))) {
                Icon(Icons.Default.Chat, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("WhatsApp")
            }
        }
    }
}
