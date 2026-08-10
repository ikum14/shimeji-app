package com.example.data

import android.content.Context
import kotlin.math.abs

/**
 * Setting kapan tiap tahap mood pet berubah (detik sejak terakhir diinteraksi).
 * Disimpan di SharedPreferences, dipakai PetOverlayService buat jadwal mood
 * (Bosan/Kesal/Marah/Ngantuk/Tidur/Bangun/Hide). Nilai LANGSUNG kepakai tanpa restart app,
 * dibaca fresh tiap tick timer idle.
 */
object MoodTimingSettings {
    private const val PREFS_NAME = "pet_mood_timing_prefs"

    private const val KEY_BOSAN = "mood_bosan_sec"
    private const val KEY_KESAL = "mood_kesal_sec"
    private const val KEY_MARAH = "mood_marah_sec"
    private const val KEY_NGANTUK = "mood_ngantuk_sec"
    private const val KEY_TIDUR = "mood_tidur_sec"
    private const val KEY_BANGUN = "mood_bangun_sec"
    private const val KEY_HIDE = "mood_hide_sec"

    /** Pilihan waktu yang tersedia: tiap 30 detik sampai 6 menit, lalu loncat lebih jarang
     * sampai 30 menit -- biar bar slider-nya gak kepanjangan/susah digeser presisi. */
    val STEPS_SEC = listOf(
        30f, 60f, 90f, 120f, 150f, 180f, 210f, 240f, 270f, 300f, 330f, 360f,
        420f, 480f, 540f, 600f, 900f, 1200f, 1500f, 1800f
    )

    const val DEFAULT_BOSAN = 60f
    const val DEFAULT_KESAL = 90f
    const val DEFAULT_MARAH = 120f
    const val DEFAULT_NGANTUK = 150f
    const val DEFAULT_TIDUR = 180f
    const val DEFAULT_BANGUN = 240f
    const val DEFAULT_HIDE = 360f

    private fun getSec(context: Context, key: String, default: Float): Float {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val stored = prefs.getFloat(key, default)
        return STEPS_SEC.minByOrNull { abs(it - stored) } ?: default
    }

    private fun setSec(context: Context, key: String, seconds: Float) {
        val nearest = STEPS_SEC.minByOrNull { abs(it - seconds) } ?: seconds
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putFloat(key, nearest).apply()
    }

    fun getBosanSec(context: Context) = getSec(context, KEY_BOSAN, DEFAULT_BOSAN)
    fun setBosanSec(context: Context, v: Float) = setSec(context, KEY_BOSAN, v)

    fun getKesalSec(context: Context) = getSec(context, KEY_KESAL, DEFAULT_KESAL)
    fun setKesalSec(context: Context, v: Float) = setSec(context, KEY_KESAL, v)

    fun getMarahSec(context: Context) = getSec(context, KEY_MARAH, DEFAULT_MARAH)
    fun setMarahSec(context: Context, v: Float) = setSec(context, KEY_MARAH, v)

    fun getNgantukSec(context: Context) = getSec(context, KEY_NGANTUK, DEFAULT_NGANTUK)
    fun setNgantukSec(context: Context, v: Float) = setSec(context, KEY_NGANTUK, v)

    fun getTidurSec(context: Context) = getSec(context, KEY_TIDUR, DEFAULT_TIDUR)
    fun setTidurSec(context: Context, v: Float) = setSec(context, KEY_TIDUR, v)

    fun getBangunSec(context: Context) = getSec(context, KEY_BANGUN, DEFAULT_BANGUN)
    fun setBangunSec(context: Context, v: Float) = setSec(context, KEY_BANGUN, v)

    fun getHideSec(context: Context) = getSec(context, KEY_HIDE, DEFAULT_HIDE)
    fun setHideSec(context: Context, v: Float) = setSec(context, KEY_HIDE, v)

    /** Index posisi suatu nilai detik di STEPS_SEC, buat dipakai slider berbasis index. */
    fun indexOf(seconds: Float): Int {
        val idx = STEPS_SEC.indexOf(seconds)
        return if (idx == -1) 0 else idx
    }
}
