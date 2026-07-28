package com.example.service

import android.app.Notification
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.example.data.TtsSpeaker
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Data model for captured messaging notifications
 */
data class IncomingPetNotification(
    val appName: String,
    val packageName: String,
    val senderName: String,
    val messageText: String,
    val timestamp: Long = System.currentTimeMillis()
) {
    fun toSpeechBubbleText(): String {
        val appSymbol = when {
            packageName.contains("whatsapp") -> "💬 [WhatsApp]"
            packageName.contains("telegram") -> "✈️ [Telegram]"
            packageName.contains("instagram") -> "📸 [Instagram]"
            packageName.contains("discord") -> "🎮 [Discord]"
            else -> "🔔 [$appName]"
        }
        val cleanText = messageText.take(50) + if (messageText.length > 50) "..." else ""
        return "$appSymbol $senderName:\n\"$cleanText\""
    }
}

/**
 * Global bus for passing incoming notifications to UI & Overlay Service
 */
object NotificationBus {
    private val _notifications = MutableSharedFlow<IncomingPetNotification>(extraBufferCapacity = 64)
    val notifications = _notifications.asSharedFlow()

    var lastNotification: IncomingPetNotification? = null
        private set

    fun emitNotification(notification: IncomingPetNotification) {
        lastNotification = notification
        _notifications.tryEmit(notification)
    }
}

/**
 * Android NotificationListenerService to intercept WhatsApp, Telegram, etc.
 */
class PetNotificationListenerService : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        val sbnNotNull = sbn ?: return
        val packageName = sbnNotNull.packageName ?: return

        // Filter for chat / messaging packages or general apps
        val isTargetApp = packageName.contains("whatsapp") ||
                packageName.contains("telegram") ||
                packageName.contains("instagram") ||
                packageName.contains("discord") ||
                packageName.contains("mms") ||
                packageName.contains("messaging")

        val extras: Bundle = sbnNotNull.notification.extras ?: return
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""

        // Lewati notifikasi yang bukan dari app chat target, dan lewati group-summary
        // (WhatsApp/Telegram sering kirim 1 notifikasi "ringkasan" tanpa isi pesan asli)
        val isGroupSummary = (sbnNotNull.notification.flags and Notification.FLAG_GROUP_SUMMARY) != 0
        if (!isTargetApp || isGroupSummary) return

        if (text.isNotBlank()) {
            val appLabel = when {
                packageName.contains("whatsapp") -> "WhatsApp"
                packageName.contains("telegram") -> "Telegram"
                packageName.contains("instagram") -> "Instagram"
                packageName.contains("discord") -> "Discord"
                else -> packageName.substringAfterLast('.')
            }

            val senderName = if (title.isNotBlank()) title else "Teman"

            val incomingNotification = IncomingPetNotification(
                appName = appLabel,
                packageName = packageName,
                senderName = senderName,
                messageText = text
            )

            NotificationBus.emitNotification(incomingNotification)

            // Broadcast intent to PetOverlayService
            val intent = Intent(ACTION_NOTIFICATION_RECEIVED).apply {
                putExtra(EXTRA_SPEECH_TEXT, incomingNotification.toSpeechBubbleText())
                setPackage(this@PetNotificationListenerService.packageName)
            }
            sendBroadcast(intent)
        }
    }

    companion object {
        const val ACTION_NOTIFICATION_RECEIVED = "com.example.service.PET_NOTIFICATION_RECEIVED"
        const val EXTRA_SPEECH_TEXT = "extra_speech_text"
    }
}
