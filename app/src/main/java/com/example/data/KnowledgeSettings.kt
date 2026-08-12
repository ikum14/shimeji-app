package com.example.data

import android.content.Context

/**
 * Setting fitur "pet pinter": trivia Wikipedia acak & headline RSS feed. Dua-duanya
 * GRATIS (gak ada biaya API), beda dari Google Search grounding yang berbayar.
 * Ini fondasi Level 2/3 -- kalau Master nanti mau upgrade ke pencarian bebas (berbayar),
 * kodenya udah siap nampung, tinggal nyalain toggle baru tanpa bongkar ulang.
 */
object KnowledgeSettings {
    private const val PREFS_NAME = "pet_knowledge_prefs"

    private const val KEY_TRIVIA_ENABLED = "trivia_enabled"
    private const val KEY_RSS_ENABLED = "rss_enabled"
    private const val KEY_RSS_URL = "rss_url"
    private const val KEY_INTERVAL_MIN = "knowledge_interval_min"

    const val DEFAULT_RSS_URL = "https://news.google.com/rss?hl=id&gl=ID&ceid=ID:id"
    const val DEFAULT_INTERVAL_MIN = 30 // default: pet nyelipin 1 trivia/headline tiap 30 menit

    fun isTriviaEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getBoolean(KEY_TRIVIA_ENABLED, false)

    fun setTriviaEnabled(context: Context, value: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putBoolean(KEY_TRIVIA_ENABLED, value).apply()
    }

    fun isRssEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getBoolean(KEY_RSS_ENABLED, false)

    fun setRssEnabled(context: Context, value: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putBoolean(KEY_RSS_ENABLED, value).apply()
    }

    fun getRssUrl(context: Context): String =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getString(KEY_RSS_URL, DEFAULT_RSS_URL) ?: DEFAULT_RSS_URL

    fun setRssUrl(context: Context, url: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putString(KEY_RSS_URL, url.ifBlank { DEFAULT_RSS_URL }).apply()
    }

    fun getIntervalMinutes(context: Context): Int =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getInt(KEY_INTERVAL_MIN, DEFAULT_INTERVAL_MIN)

    fun setIntervalMinutes(context: Context, minutes: Int) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putInt(KEY_INTERVAL_MIN, minutes).apply()
    }
}
