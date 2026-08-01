package com.example.data

import android.content.Context
import kotlin.math.abs

/**
 * Setting interval "ngoceh" pet (seberapa sering ganti kalimat template pas idle).
 * Disimpan di SharedPreferences supaya tetap kepakai walau app ditutup/HP restart.
 *
 * Bukan slider bebas geser detik demi detik -- pilihannya sudah ditentukan (30 detik,
 * lalu loncat per menit), biar bar-nya gak kepanjangan/ribet.
 */
object IdleChatterSettings {
    private const val PREFS_NAME = "pet_idle_chatter_prefs"
    private const val KEY_INTERVAL_SEC = "chatter_interval_sec"

    /** Pilihan interval yang tersedia: 30 detik, lalu 1/2/3/4/5/10/15/20/25/30 menit. */
    val INTERVAL_STEPS_SEC = listOf(30f, 60f, 120f, 180f, 240f, 300f, 600f, 900f, 1200f, 1500f, 1800f)
    const val DEFAULT_INTERVAL_SEC = 60f

    /** Ambil interval tersimpan, dibulatin ke pilihan terdekat kalau nilai lama gak persis cocok. */
    fun getIntervalSeconds(context: Context): Float {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val stored = prefs.getFloat(KEY_INTERVAL_SEC, DEFAULT_INTERVAL_SEC)
        return INTERVAL_STEPS_SEC.minByOrNull { abs(it - stored) } ?: DEFAULT_INTERVAL_SEC
    }

    fun getIntervalMs(context: Context): Long {
        return (getIntervalSeconds(context) * 1000).toLong()
    }

    fun setIntervalSeconds(context: Context, seconds: Float) {
        val nearest = INTERVAL_STEPS_SEC.minByOrNull { abs(it - seconds) } ?: DEFAULT_INTERVAL_SEC
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putFloat(KEY_INTERVAL_SEC, nearest)
            .apply()
    }

    /** Index posisi interval tertentu di INTERVAL_STEPS_SEC, buat dipakai slider berbasis index. */
    fun indexOf(seconds: Float): Int {
        val idx = INTERVAL_STEPS_SEC.indexOf(seconds)
        return if (idx == -1) INTERVAL_STEPS_SEC.indexOf(DEFAULT_INTERVAL_SEC) else idx
    }
}
