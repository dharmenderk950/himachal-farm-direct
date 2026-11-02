package com.dk.himachalfarmdirect.ui.help

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.ConfirmationNumber
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.dk.himachalfarmdirect.BuildConfig
import com.dk.himachalfarmdirect.data.config.AppConfig
import com.dk.himachalfarmdirect.data.config.AppConfigRepository
import com.dk.himachalfarmdirect.data.repo.AppMode
import com.dk.himachalfarmdirect.data.repo.OrderRepository
import com.dk.himachalfarmdirect.data.repo.SupportRepository
import com.dk.himachalfarmdirect.data.repo.UserRepository
import com.dk.himachalfarmdirect.ui.common.BackBar
import com.dk.himachalfarmdirect.ui.common.EmptyState
import com.dk.himachalfarmdirect.ui.theme.FarmColors
import com.dk.himachalfarmdirect.util.friendly
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class HelpViewModel @Inject constructor(
    private val config: AppConfigRepository,
    private val support: SupportRepository,
    private val users: UserRepository,
    orders: OrderRepository,
) : ViewModel() {
    val cfg = config.config
    val tickets = support.myTickets().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val recentOrders = orders.myOrders().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val sending = MutableStateFlow(false)
    val created = MutableStateFlow<String?>(null)
    val error = MutableStateFlow<String?>(null)

    init { viewModelScope.launch { config.refresh() } }

    fun submit(topic: String, subject: String, message: String, orderId: String) = viewModelScope.launch {
        if (subject.isBlank() || message.isBlank()) { error.value = "Please fill in the subject and describe the problem"; return@launch }
        sending.value = true; error.value = null
        val role = if (users.mode.first() == AppMode.SELLER) "seller" else "buyer"
        runCatching { support.createTicket(role, topic, subject.trim(), message.trim(), orderId) }
            .onSuccess { created.value = it }
            .onFailure { error.value = it.friendly() }
        sending.value = false
    }
}

/** Opens the device's email app with a pre-filled support email (spec E). */
fun openSupportEmail(context: Context, cfg: AppConfig, subject: String = "Himachal Farm Direct – Support request") {
    val body = "Hello,\n\nPlease describe your problem here:\n\n\n\n----\nApp version: ${BuildConfig.VERSION_NAME}\n" +
        "Android: ${Build.VERSION.RELEASE} (${Build.MODEL})\n"
    val uri = Uri.parse("mailto:${cfg.supportEmail}?subject=${Uri.encode(subject)}&body=${Uri.encode(body)}")
    val intent = Intent(Intent.ACTION_SENDTO, uri).apply {
        putExtra(Intent.EXTRA_EMAIL, arrayOf(cfg.supportEmail))
        putExtra(Intent.EXTRA_SUBJECT, subject)
        putExtra(Intent.EXTRA_TEXT, body)
    }
    try { context.startActivity(Intent.createChooser(intent, "Send email with")) }
    catch (_: ActivityNotFoundException) { Toast.makeText(context, "No email app found. Write to ${cfg.supportEmail}", Toast.LENGTH_LONG).show() }
}

/** Only callable when the admin has set and verified a WhatsApp number in Remote Config. */
fun openSupportWhatsApp(context: Context, digits: String) {
    val text = Uri.encode("Hello, I need help with Himachal Farm Direct (app v${BuildConfig.VERSION_NAME}).")
    try { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$digits?text=$text"))) }
    catch (_: ActivityNotFoundException) { Toast.makeText(context, "WhatsApp is not installed", Toast.LENGTH_LONG).show() }
}

@Composable
fun HelpCentreScreen(signedIn: Boolean, onBack: () -> Unit, openArticle: (String) -> Unit, openForm: (String) -> Unit, openTickets: () -> Unit) {
    val vm: HelpViewModel = hiltViewModel()
    val cfg by vm.cfg.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var section by remember { mutableStateOf(HelpContent.BUYER) }

    Scaffold(topBar = { BackBar("Help Centre", onBack) }) { pad ->
        LazyColumn(Modifier.padding(pad).fillMaxSize(), contentPadding = PaddingValues(bottom = 32.dp)) {
            item {
                Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(HelpContent.BUYER to "Buyer", HelpContent.SELLER to "Seller", HelpContent.GENERAL to "General").forEach { (s, label) ->
                        FilterChip(section == s, { section = s }, label = { Text(label) })
                    }
                }
            }
            items(HelpContent.section(section), key = { it.id }) { a ->
                Row(Modifier.fillMaxWidth().clickable { openArticle(a.id) }.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(a.title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = FarmColors.Muted)
                }
                HorizontalDivider(Modifier.padding(horizontal = 16.dp))
            }
            item { ContactCard(cfg, onEmail = { openSupportEmail(context, cfg) }, onWhatsApp = { d -> openSupportWhatsApp(context, d) }) }
            if (signedIn) item {
                Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { openForm("") }, Modifier.fillMaxWidth().height(52.dp)) {
                        Icon(Icons.Default.EditNote, null); Spacer(Modifier.width(8.dp)); Text("Raise a support request")
                    }
                    OutlinedButton(onClick = openTickets, Modifier.fillMaxWidth().height(52.dp)) {
                        Icon(Icons.Default.ConfirmationNumber, null); Spacer(Modifier.width(8.dp)); Text("My support tickets")
                    }
                }
            }
        }
    }
}

@Composable
private fun ContactCard(cfg: AppConfig, onEmail: () -> Unit, onWhatsApp: (String) -> Unit) {
    Column(
        Modifier.padding(16.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(FarmColors.Mint).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("Contact the administrator", style = MaterialTheme.typography.titleMedium, color = FarmColors.Forest)
        Text("Support hours: ${cfg.supportHours}", color = FarmColors.Muted)
        Text("Email: ${cfg.supportEmail}")
        Button(onClick = onEmail, Modifier.fillMaxWidth()) { Icon(Icons.Default.Email, null); Spacer(Modifier.width(8.dp)); Text("Email support") }
        if (cfg.whatsappIdentifier.isNotBlank()) Text("WhatsApp ID: ${cfg.whatsappIdentifier}")
        val digits = cfg.whatsappDigits
        if (digits != null) {
            OutlinedButton(onClick = { onWhatsApp(digits) }, Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Chat, null); Spacer(Modifier.width(8.dp)); Text("Chat on WhatsApp")
            }
        } else {
            Text("WhatsApp chat will be available soon. Please use email or a support request.",
                color = FarmColors.Muted, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
fun HelpArticleScreen(articleId: String, signedIn: Boolean, onBack: () -> Unit, openForm: (String) -> Unit) {
    val vm: HelpViewModel = hiltViewModel()
    val cfg by vm.cfg.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val a = HelpContent.byId(articleId)
    Scaffold(topBar = { BackBar(a?.title ?: "Help", onBack) }) { pad ->
        Column(Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(16.dp)) {
            Text(a?.body ?: "Article not found.", style = MaterialTheme.typography.bodyLarge)
            val url = when (articleId) { "g_privacy" -> cfg.privacyUrl; "g_terms" -> cfg.termsUrl; else -> "" }
            if (url.isNotBlank()) OutlinedButton(onClick = {
                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
            }, Modifier.padding(top = 12.dp)) { Text("Read the full document") }
            Spacer(Modifier.height(24.dp))
            Text("Still need help?", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                if (signedIn) Button(onClick = { openForm(a?.title.orEmpty()) }) { Text("Support request") }
                OutlinedButton(onClick = { openSupportEmail(context, cfg, "Help: ${a?.title.orEmpty()}") }) { Text("Email us") }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SupportFormScreen(presetSubject: String, onBack: () -> Unit, openTickets: () -> Unit) {
    val vm: HelpViewModel = hiltViewModel()
    val sending by vm.sending.collectAsStateWithLifecycle()
    val created by vm.created.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    val orders by vm.recentOrders.collectAsStateWithLifecycle()
    var topic by remember { mutableStateOf(if (presetSubject.contains("Complaint", true)) "Complaint" else HelpContent.ticketTopics.first()) }
    var topicOpen by remember { mutableStateOf(false) }
    var subject by remember { mutableStateOf(presetSubject) }
    var message by remember { mutableStateOf("") }
    var orderId by remember { mutableStateOf("") }
    var orderOpen by remember { mutableStateOf(false) }

    Scaffold(topBar = { BackBar("Support request", onBack) }) { pad ->
        if (created != null) {
            Column(Modifier.padding(pad)) {
                EmptyState("🎫", "Ticket ${created} created",
                    "Note this number. The administrator will reply here and you'll get a notification.", "View my tickets", openTickets)
            }
            return@Scaffold
        }
        Column(Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            ExposedDropdownMenuBox(topicOpen, { topicOpen = it }) {
                OutlinedTextField(topic, {}, readOnly = true, label = { Text("Topic") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(topicOpen) },
                    modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable))
                ExposedDropdownMenu(topicOpen, { topicOpen = false }) {
                    HelpContent.ticketTopics.forEach { t -> DropdownMenuItem(text = { Text(t) }, onClick = { topic = t; topicOpen = false }) }
                }
            }
            if (orders.isNotEmpty()) ExposedDropdownMenuBox(orderOpen, { orderOpen = it }) {
                val sel = orders.firstOrNull { it.id == orderId }
                OutlinedTextField(sel?.let { "${it.farmerName} · #${it.id.takeLast(6).uppercase()}" } ?: "No order (optional)", {}, readOnly = true,
                    label = { Text("Related order") }, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(orderOpen) },
                    modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable))
                ExposedDropdownMenu(orderOpen, { orderOpen = false }) {
                    DropdownMenuItem(text = { Text("No order") }, onClick = { orderId = ""; orderOpen = false })
                    orders.take(15).forEach { o ->
                        DropdownMenuItem(text = { Text("${o.farmerName} · #${o.id.takeLast(6).uppercase()}") }, onClick = { orderId = o.id; orderOpen = false })
                    }
                }
            }
            OutlinedTextField(subject, { subject = it.take(100) }, label = { Text("Subject") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(message, { message = it.take(2000) }, label = { Text("Describe the problem") }, minLines = 5, modifier = Modifier.fillMaxWidth())
            Text("Don't write OTPs, UPI PINs or bank passwords here.", color = FarmColors.Muted, style = MaterialTheme.typography.bodyMedium)
            error?.let { Text(it, color = FarmColors.Danger) }
            Button(onClick = { vm.submit(topic, subject, message, orderId) }, enabled = !sending, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                Text(if (sending) "Sending…" else "Submit")
            }
        }
    }
}

private val ticketStatus = mapOf("OPEN" to "Open", "IN_PROGRESS" to "In progress", "RESOLVED" to "Resolved", "CLOSED" to "Closed")

@Composable
fun TicketsScreen(onBack: () -> Unit, openForm: () -> Unit) {
    val vm: HelpViewModel = hiltViewModel()
    val tickets by vm.tickets.collectAsStateWithLifecycle()
    val fmt = remember { SimpleDateFormat("d MMM yyyy, h:mm a", Locale("en", "IN")) }
    Scaffold(topBar = { BackBar("My support tickets", onBack) }) { pad ->
        if (tickets.isEmpty()) Column(Modifier.padding(pad)) { EmptyState("🎫", "No tickets", "Problems you report will appear here.", "Raise a request", openForm) }
        else LazyColumn(Modifier.padding(pad), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(tickets, key = { it.id }) { t ->
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).border(1.dp, FarmColors.Line, RoundedCornerShape(14.dp)).padding(14.dp)) {
                    Row {
                        Text(t.ticketNo, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        Text(ticketStatus[t.status] ?: t.status, color = if (t.status == "RESOLVED" || t.status == "CLOSED") FarmColors.Leaf else FarmColors.Harvest, fontWeight = FontWeight.SemiBold)
                    }
                    Text("${t.topic} · ${t.subject}", style = MaterialTheme.typography.titleMedium)
                    t.createdAt?.let { Text(fmt.format(it.toDate()), color = FarmColors.Muted) }
                    Text(t.message, modifier = Modifier.padding(top = 4.dp))
                    if (t.adminReply.isNotBlank()) Column(Modifier.padding(top = 8.dp).fillMaxWidth().background(FarmColors.Mint, RoundedCornerShape(10.dp)).padding(10.dp)) {
                        Text("Administrator reply", fontWeight = FontWeight.SemiBold, color = FarmColors.Forest)
                        Text(t.adminReply)
                    }
                }
            }
        }
    }
}
