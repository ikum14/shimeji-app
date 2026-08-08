package com.example.data

import android.content.Context

/**
 * Satu-satunya sumber data level & XP pet, dipakai bareng oleh PetOverlayService
 * (overlay yang jalan terus di background) dan MainDashboardScreen (UI utama).
 * Sebelumnya keduanya punya angka sendiri-sendiri yang nggak pernah sinkron.
 */
object PetProgressStore {
    private const val PREFS_NAME = "pet_progress_store"
    private const val KEY_LEVEL = "level"
    private const val KEY_XP = "xp"
    private const val KEY_NAME = "pet_name"
    private const val DEFAULT_NAME = "Chibi Girl Shimeji"

    const val MAX_XP_PER_LEVEL = 100

    fun getLevel(context: Context): Int =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getInt(KEY_LEVEL, 1)

    fun getXp(context: Context): Int =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getInt(KEY_XP, 0)

    /** Nama pet -- sumber tunggal, dipakai pas export ke Obsidian biar gak ketimpa balik ke default. */
    fun getName(context: Context): String =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getString(KEY_NAME, DEFAULT_NAME) ?: DEFAULT_NAME

    fun saveName(context: Context, name: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_NAME, name.ifBlank { DEFAULT_NAME })
            .apply()
    }

    fun save(context: Context, level: Int, xp: Int) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_LEVEL, level)
            .putInt(KEY_XP, xp)
            .apply()
    }

    /** Balikin level & XP ke titik awal (Level 1, 0 XP). Dipakai tombol "Reset Level" di dashboard. */
    fun reset(context: Context) {
        save(context, 1, 0)
    }
}
