package com.dk.himachalfarmdirect.data.config

import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.remoteConfigSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Spec E: administrator contact details live in Firebase Remote Config, so the admin can
 * change them from the Firebase console without publishing a new APK.
 *
 * WhatsApp: the owner-supplied identifier "dkc@1978" is shown as text only. The WhatsApp
 * button appears ONLY when `support_whatsapp_number` holds a valid international number
 * AND `support_whatsapp_verified` is true (set by the admin after confirming the number
 * receives WhatsApp messages).
 */
data class AppConfig(
    val supportEmail: String = "dharmenderk950@hotmail.com",
    val whatsappIdentifier: String = "dkc@1978",
    val whatsappNumber: String = "+919418031378",
    val whatsappVerified: Boolean = false,
    val radiiKm: List<Int> = listOf(5, 10, 20, 50),
    val supportHours: String = "10 AM – 6 PM, Mon–Sat",
    val privacyUrl: String = "",
    val termsUrl: String = "",
) {
    /** E.164 without "+", the format wa.me expects. */
    val whatsappDigits: String? get() {
        val digits = whatsappNumber.filter { it.isDigit() }
        val looksValid = whatsappNumber.trim().startsWith("+") && digits.length in 10..15
        return if (whatsappVerified && looksValid) digits else null
    }
}

@Singleton
class AppConfigRepository @Inject constructor() {
    private val rc = FirebaseRemoteConfig.getInstance()
    private val _config = MutableStateFlow(AppConfig())
    val config: StateFlow<AppConfig> = _config

    init {
        rc.setConfigSettingsAsync(remoteConfigSettings { minimumFetchIntervalInSeconds = 3600 })
        rc.setDefaultsAsync(
            mapOf(
                "support_email" to "dharmenderk950@hotmail.com",
                "support_whatsapp_identifier" to "dkc@1978",
                "support_whatsapp_number" to "+919418031378",
                "support_whatsapp_verified" to false,
                "search_radii_km" to "5,10,20,50",
                "support_hours" to "10 AM – 6 PM, Mon–Sat",
                "privacy_policy_url" to "",
                "terms_url" to "",
            )
        )
        publish()
    }

    /** Uses cached values instantly; refreshes in the background when the network allows. */
    suspend fun refresh() {
        runCatching { rc.fetchAndActivate().await() }
        publish()
    }

    private fun publish() {
        _config.value = AppConfig(
            supportEmail = rc.getString("support_email").ifBlank { "dharmenderk950@hotmail.com" },
            whatsappIdentifier = rc.getString("support_whatsapp_identifier"),
            whatsappNumber = rc.getString("support_whatsapp_number"),
            whatsappVerified = rc.getBoolean("support_whatsapp_verified"),
            radiiKm = rc.getString("search_radii_km").split(",")
                .mapNotNull { it.trim().toIntOrNull() }.filter { it in 1..200 }
                .ifEmpty { listOf(5, 10, 20, 50) },
            supportHours = rc.getString("support_hours"),
            privacyUrl = rc.getString("privacy_policy_url"),
            termsUrl = rc.getString("terms_url"),
        )
    }
}
