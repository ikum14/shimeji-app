package com.example.data

import android.content.Context

/**
 * Setting suara TTS yang berlaku ke SEMUA engine (Google TTS, VoxSherpa, NekoSpeak, dll) --
 * ini API standar Android, bukan fitur khusus satu engine tertentu:
 * - Pitch (tinggi nada): 0.5 (berat/rendah) - 2.0 (tinggi/cempreng), default 1.0
 * - Speed (kecepatan bicara): 0.5 (lambat) - 2.0 (cepat), default 1.0
 * - Jeda antar kalimat (ms): 0 - 1500, dikasih tiap ketemu tanda titik/tanya/seru
 * - Jeda di posisi emoji yang dihapus: true/false -- biar "beat" emosinya kerasa
 *   walau emoji-nya sendiri gak dibaca literal
 */
object TtsVoiceSettings {
    private const val PREFS_NAME = "pet_tts_voice_settings"
    private const val KEY_PITCH = "pitch"
    private const val KEY_SPEED = "speed"
    private const val KEY_PAUSE_MS = "pause_ms"
    private const val KEY_PAUSE_AT_EMOJI = "pause_at_emoji"

    const val MIN_PITCH = 0.5f
    const val MAX_PITCH = 2.0f
    const val DEFAULT_PITCH = 1.0f

    const val MIN_SPEED = 0.5f
    const val MAX_SPEED = 2.0f
    const val DEFAULT_SPEED = 1.0f

    const val MIN_PAUSE_MS = 0f
    const val MAX_PAUSE_MS = 1500f
    const val DEFAULT_PAUSE_MS = 250f

    fun getPitch(context: Context): Float =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getFloat(KEY_PITCH, DEFAULT_PITCH)

    fun setPitch(context: Context, value: Float) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putFloat(KEY_PITCH, value.coerceIn(MIN_PITCH, MAX_PITCH)).apply()
    }

    fun getSpeed(context: Context): Float =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getFloat(KEY_SPEED, DEFAULT_SPEED)

    fun setSpeed(context: Context, value: Float) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putFloat(KEY_SPEED, value.coerceIn(MIN_SPEED, MAX_SPEED)).apply()
    }

    fun getPauseMs(context: Context): Long =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getFloat(KEY_PAUSE_MS, DEFAULT_PAUSE_MS).toLong()

    fun setPauseMs(context: Context, value: Float) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putFloat(KEY_PAUSE_MS, value.coerceIn(MIN_PAUSE_MS, MAX_PAUSE_MS)).apply()
    }

    fun getPauseAtEmoji(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getBoolean(KEY_PAUSE_AT_EMOJI, true)

    fun setPauseAtEmoji(context: Context, value: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_PAUSE_AT_EMOJI, value).apply()
    }
}
