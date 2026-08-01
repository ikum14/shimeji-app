package com.example.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Setting tampilan bubble dialog pet: warna background, warna teks, dan mode
 * "warna otomatis ikut mood" (kalau aktif, override warna custom Master dengan
 * preset warna sesuai emosi pet saat itu -- Senang/Bosan/Kesal).
 * Disimpan di SharedPreferences, dipublish lewat StateFlow biar Compose bubble
 * langsung update begitu Master ganti setting, tanpa perlu restart overlay.
 */
object BubbleStyleSettings {
    private const val PREFS_NAME = "pet_bubble_style_prefs"
    private const val KEY_BG_COLOR = "bg_color"
    private const val KEY_TEXT_COLOR = "text_color"
    private const val KEY_USE_MOOD_COLOR = "use_mood_color"

    const val DEFAULT_BG_COLOR = 0xFFFFFFFF.toInt()
    const val DEFAULT_TEXT_COLOR = 0xFF333333.toInt()

    // Preset palet warna background yang bisa dipilih Master di dashboard
    val BG_COLOR_PRESETS = listOf(
        0xFFFFFFFF.toInt(), // putih (default)
        0xFFFFF0F5.toInt(), // pink pastel
        0xFFE3F2FD.toInt(), // biru pastel
        0xFFFFF9C4.toInt(), // kuning pastel
        0xFFE8F5E9.toInt(), // hijau pastel
        0xFF2C2C2C.toInt()  // gelap
    )

    // Preset palet warna teks
    val TEXT_COLOR_PRESETS = listOf(
        0xFF333333.toInt(), // abu gelap (default)
        0xFF000000.toInt(), // hitam
        0xFFE91E63.toInt(), // pink
        0xFF1976D2.toInt(), // biru
        0xFFFFFFFF.toInt()  // putih (buat bubble gelap)
    )

    // Preset warna background otomatis per mood (dipakai kalau useMoodColor aktif)
    private val MOOD_BG_COLORS = mapOf(
        "Senang" to 0xFFFFF0F5.toInt(), // pink lembut
        "Bosan" to 0xFFE3F2FD.toInt(),   // biru lembut
        "Kesal" to 0xFFFFEBEE.toInt()    // merah lembut
    )

    private val _bgColor = MutableStateFlow(DEFAULT_BG_COLOR)
    val bgColor = _bgColor.asStateFlow()

    private val _textColor = MutableStateFlow(DEFAULT_TEXT_COLOR)
    val textColor = _textColor.asStateFlow()

    private val _useMoodColor = MutableStateFlow(false)
    val useMoodColor = _useMoodColor.asStateFlow()

    fun init(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        _bgColor.value = prefs.getInt(KEY_BG_COLOR, DEFAULT_BG_COLOR)
        _textColor.value = prefs.getInt(KEY_TEXT_COLOR, DEFAULT_TEXT_COLOR)
        _useMoodColor.value = prefs.getBoolean(KEY_USE_MOOD_COLOR, false)
    }

    fun setBgColor(context: Context, color: Int) {
        _bgColor.value = color
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putInt(KEY_BG_COLOR, color).apply()
    }

    fun setTextColor(context: Context, color: Int) {
        _textColor.value = color
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putInt(KEY_TEXT_COLOR, color).apply()
    }

    fun setUseMoodColor(context: Context, enabled: Boolean) {
        _useMoodColor.value = enabled
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_USE_MOOD_COLOR, enabled).apply()
    }

    /** Background efektif buat dipakai bubble saat ini: warna mood (kalau mode mood aktif) atau warna custom Master. */
    fun getEffectiveBgColor(mood: String): Int {
        return if (_useMoodColor.value) {
            MOOD_BG_COLORS[mood] ?: _bgColor.value
        } else {
            _bgColor.value
        }
    }
}
