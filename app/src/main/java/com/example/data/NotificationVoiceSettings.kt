package com.example.data

import android.content.Context
import android.content.Intent
import android.provider.Settings

enum class VoiceReadMode {
    OFF,           // Suara mati total
    SENDER_ONLY,   // Cuma bilang "Pesan dari [nama pengirim]"
    FULL_MESSAGE   // Baca nama pengirim + isi pesan lengkap
}

/**
 * Pengaturan suara buat notifikasi masuk (WhatsApp/Telegram/dll).
 * Disimpan di SharedPreferences supaya tetap kepilih walau app ditutup/HP restart.
 */
object NotificationVoiceSettings {
    private const val PREFS_NAME = "pet_notification_voice_prefs"
    private const val KEY_MODE = "voice_read_mode"
    private const val KEY_VOICE_NAME = "selected_voice_name"

    fun getMode(context: Context): VoiceReadMode {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_MODE, VoiceReadMode.SENDER_ONLY.name)
        return try {
            VoiceReadMode.valueOf(raw ?: VoiceReadMode.SENDER_ONLY.name)
        } catch (e: Exception) {
            VoiceReadMode.SENDER_ONLY
        }
    }

    fun setMode(context: Context, mode: VoiceReadMode) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_MODE, mode.name)
            .apply()
    }

    fun getSelectedVoiceName(context: Context): String? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_VOICE_NAME, null)
    }

    fun setSelectedVoiceName(context: Context, voiceName: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_VOICE_NAME, voiceName)
            .apply()
    }

    /** Cek apakah izin "Notification Access" sudah diberikan (wajib biar bisa baca notif WA/Telegram/dll). */
    fun hasNotificationAccess(context: Context): Boolean {
        val enabledListeners = Settings.Secure.getString(
            context.contentResolver, "enabled_notification_listeners"
        ) ?: return false
        return enabledListeners.contains(context.packageName)
    }

    fun requestNotificationAccess(context: Context) {
        val intent = Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }
}
