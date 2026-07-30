package com.example.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Ukuran font bubble dialog pet, diatur manual pakai angka (sp) oleh Master lewat dashboard.
 * Disimpan persist + broadcast live lewat StateFlow biar overlay yang lagi jalan langsung
 * ke-update tanpa perlu restart service.
 */
object BubbleSettings {
    private const val PREFS_NAME = "pet_bubble_settings"
    private const val KEY_FONT_SIZE = "bubble_font_size"

    const val DEFAULT_FONT_SIZE = 13f
    const val MIN_FONT_SIZE = 8f
    const val MAX_FONT_SIZE = 24f

    val fontSizeSp = MutableStateFlow(DEFAULT_FONT_SIZE)

    /** Panggil sekali di awal (dashboard & overlay service) buat load nilai tersimpan. */
    fun init(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        fontSizeSp.value = prefs.getFloat(KEY_FONT_SIZE, DEFAULT_FONT_SIZE)
    }

    fun setFontSize(context: Context, sizeSp: Float) {
        val clamped = sizeSp.coerceIn(MIN_FONT_SIZE, MAX_FONT_SIZE)
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putFloat(KEY_FONT_SIZE, clamped)
            .apply()
        fontSizeSp.value = clamped
    }
}
