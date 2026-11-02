package com.dk.himachalfarmdirect.messaging

import android.app.PendingIntent
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.dk.himachalfarmdirect.MainActivity
import com.dk.himachalfarmdirect.R
import com.dk.himachalfarmdirect.data.repo.UserRepository
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Receives: order updates, admin replies to support tickets (spec E) and
 * "new farmer near you" alerts (spec A-13). The `channel` data key picks the channel.
 */
@AndroidEntryPoint
class FarmMessagingService : FirebaseMessagingService() {

    @Inject lateinit var users: UserRepository
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNewToken(token: String) {
        scope.launch { runCatching { users.updateFcmToken(token) } }
    }

    override fun onMessageReceived(msg: RemoteMessage) {
        val title = msg.notification?.title ?: msg.data["title"] ?: return
        val body = msg.notification?.body ?: msg.data["body"].orEmpty()
        val channel = msg.data["channel"] ?: "general"
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("open", msg.data["open"])   // e.g. "tickets", "orders", "nearby"
        }
        val pi = PendingIntent.getActivity(this, title.hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val n = NotificationCompat.Builder(this, channel)
            .setSmallIcon(R.drawable.ic_logo)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setContentIntent(pi)
            .build()
        runCatching { NotificationManagerCompat.from(this).notify(System.currentTimeMillis().toInt(), n) }
    }
}
