package com.example.service

import android.app.Notification
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
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

    // Cegah notifikasi yang SAMA PERSIS (WhatsApp/Telegram kadang "refresh" notifikasi
    // tanpa pesan baru beneran) dibacain berkali-kali padahal gak ada pesan baru.
    private var lastEmittedKey: String? = null
    private var lastEmittedAt: Long = 0L
    private val DEDUPE_WINDOW_MS = 5 * 60_000L // 5 menit

    override fun onListenerConnected() {
        super.onListenerConnected()
    }

    /** Koneksi listener notifikasi putus (misal app-nya ke-restart paksa/force-close) --
     * Android gak selalu otomatis nyambungin ulang, jadi kita minta rebind manual di sini
     * biar Master gak perlu bolak-balik matiin-nyalain izin di Settings HP lagi. */
    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        try {
            requestRebind(android.content.ComponentName(this, PetNotificationListenerService::class.java))
        } catch (e: Exception) {
            // Gak fatal kalau gagal -- Master masih bisa rebind manual lewat toggle izin di Settings
        }
    }

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

        // Lewati notifikasi "placeholder" -- WhatsApp/Telegram kadang kirim notif isinya cuma
        // status koneksi/sinkronisasi, BUKAN pesan beneran dari orang, tapi teksnya gak
        // kosong jadi kelolosin cek isBlank() biasa. Deteksi lewat kata kunci umum.
        val placeholderKeywords = listOf(
            "memeriksa pesan", "checking for new messages", "checking messages",
            "menyinkronkan", "syncing messages", "syncing", "menghubungkan",
            "connecting", "mencoba menyambungkan", "reconnecting",
            "sedang memuat", "loading messages"
        )
        val lowerText = text.lowercase()
        val isPlaceholder = placeholderKeywords.any { lowerText.contains(it) }
        if (isPlaceholder) return

        if (text.isNotBlank()) {
            // Dedupe: kalau isi & pengirimnya SAMA PERSIS kayak notif terakhir yang
            // barusan dibacain (dalam 5 menit terakhir), anggap ini repost sistem,
            // bukan pesan baru -- jangan dibacain ulang.
            val dedupeKey = "$packageName|$title|$text"
            val now = System.currentTimeMillis()
            if (dedupeKey == lastEmittedKey && (now - lastEmittedAt) < DEDUPE_WINDOW_MS) {
                return
            }
            lastEmittedKey = dedupeKey
            lastEmittedAt = now

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
        }
    }
}
