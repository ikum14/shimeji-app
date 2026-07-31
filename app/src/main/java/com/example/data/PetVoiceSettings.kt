package com.example.data

import android.content.Context

/**
 * Setting mute keseluruhan buat suara pet (dialog pet SENDIRI + notifikasi WA/Telegram).
 * Beda dari NotificationVoiceSettings (yang cuma ngatur mode baca notifikasi) --
 * ini master switch yang matiin/nyalain SEMUA suara TtsSpeaker sekaligus.
 * Disimpan di SharedPreferences supaya tetap kepilih walau app ditutup/HP restart.
 */
object PetVoiceSettings {
    private const val PREFS_NAME = "pet_voice_mute_prefs"
    private const val KEY_MUTED = "is_muted"

    fun isMuted(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_MUTED, false)
    }

    fun setMuted(context: Context, muted: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_MUTED, muted)
            .apply()
    }
}
