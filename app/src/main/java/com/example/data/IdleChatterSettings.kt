package com.example.data

import android.content.Context

/**
 * Setting interval "ngoceh" pet (seberapa sering ganti kalimat template pas idle).
 * Disimpan di SharedPreferences supaya tetap kepakai walau app ditutup/HP restart.
 */
object IdleChatterSettings {
    private const val PREFS_NAME = "pet_idle_chatter_prefs"
    private const val KEY_INTERVAL_SEC = "chatter_interval_sec"

    const val MIN_INTERVAL_SEC = 10f
    const val MAX_INTERVAL_SEC = 120f
    const val DEFAULT_INTERVAL_SEC = 30f

    fun getIntervalSeconds(context: Context): Float {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getFloat(KEY_INTERVAL_SEC, DEFAULT_INTERVAL_SEC)
            .coerceIn(MIN_INTERVAL_SEC, MAX_INTERVAL_SEC)
    }

    fun getIntervalMs(context: Context): Long {
        return (getIntervalSeconds(context) * 1000).toLong()
    }

    fun setIntervalSeconds(context: Context, seconds: Float) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putFloat(KEY_INTERVAL_SEC, seconds.coerceIn(MIN_INTERVAL_SEC, MAX_INTERVAL_SEC))
            .apply()
    }
}
