package com.example.data

import android.content.Context
import java.io.File

/**
 * Baca kalimat template pet dari file khusus di vault Obsidian (`pet-quotes.md`), supaya
 * Master bisa ganti sendiri kalimat-kalimatnya tanpa harus edit kode & compile ulang app.
 *
 * Format file (satu heading level-2 per kategori, satu kalimat per baris):
 *
 * ## Idle
 * Lagi santai aja nih di sini~
 * Bengong dulu ah...
 *
 * ## Tap
 * Kyaa~! Sakit tau~!
 *
 * ## Drag
 * Kyaaa~! Aku diangkat!
 *
 * ## Fall
 * Aaaaa! Turun tanggaaa~!
 *
 * ## Bosan
 * Bosan banget nih... Master mana ya?
 *
 * ## Kesal
 * Kesal deh! Dikacangin terus dari tadi!
 *
 * Kalau file belum ada / kategori kosong / belum diisi, otomatis fallback ke kalimat
 * default bawaan app (PetQuotes) -- gak perlu isi semua kategori, isi yang mau diganti aja.
 */
object PetQuoteSettings {
    private const val QUOTES_FILE_NAME = "pet-quotes.md"

    // Cache singkat biar gak baca file tiap kali speakBubble dipanggil beruntun cepat.
    private var cachedQuotes: Map<String, List<String>>? = null
    private var cachedAt: Long = 0L
    private const val CACHE_TTL_MS = 5_000L

    private fun getQuotesFile(): File? {
        if (!VaultPathProvider.hasAllFilesAccess()) return null
        val file = File(VaultPathProvider.getObsidianVaultDir(), QUOTES_FILE_NAME)
        return if (file.exists()) file else null
    }

    private fun loadAll(): Map<String, List<String>> {
        val now = System.currentTimeMillis()
        cachedQuotes?.let { if (now - cachedAt < CACHE_TTL_MS) return it }

        val file = getQuotesFile()
        if (file == null) {
            val empty = emptyMap<String, List<String>>()
            cachedQuotes = empty
            cachedAt = now
            return empty
        }

        val result = mutableMapOf<String, MutableList<String>>()
        var currentCategory: String? = null
        try {
            file.readLines(Charsets.UTF_8).forEach { rawLine ->
                val line = rawLine.trim()
                when {
                    line.startsWith("## ") -> {
                        currentCategory = line.removePrefix("## ").trim().lowercase()
                        result.putIfAbsent(currentCategory!!, mutableListOf())
                    }
                    line.isNotBlank() && !line.startsWith("#") && currentCategory != null -> {
                        result[currentCategory]?.add(line)
                    }
                }
            }
        } catch (e: Exception) {
            // Gagal baca -> anggap kosong, semua kategori fallback ke default
        }

        cachedQuotes = result
        cachedAt = now
        return result
    }

    /**
     * Ambil 1 kalimat random dari kategori tertentu di pet-quotes.md.
     * Kalau kategori itu kosong/belum diisi Master, fallback ke `defaultQuotes` bawaan app.
     */
    fun getQuote(category: String, defaultQuotes: List<String>): String {
        val custom = loadAll()[category.lowercase()]
        return if (!custom.isNullOrEmpty()) custom.random() else defaultQuotes.random()
    }

    /** Bikin file pet-quotes.md contoh di vault kalau belum ada, biar Master tinggal edit. */
    fun ensureTemplateExists() {
        if (!VaultPathProvider.hasAllFilesAccess()) return
        val file = File(VaultPathProvider.getObsidianVaultDir(), QUOTES_FILE_NAME)
        if (file.exists()) return
        try {
            file.writeText(
                """
                # Kalimat Template Pet

                Edit kalimat di bawah sesuka Master. Satu kalimat per baris.
                Biarin kosong / hapus kategori kalau mau pakai kalimat default bawaan app.

                ## Idle
                Lagi santai aja nih di sini~

                ## Tap
                Kyaa~! Sakit tau~!

                ## Drag
                Kyaaa~! Aku diangkat!

                ## Fall
                Aaaaa! Turun tanggaaa~!

                ## Bosan
                Bosan banget nih... Master mana ya?

                ## Kesal
                Kesal deh! Dikacangin terus dari tadi!
                """.trimIndent(),
                Charsets.UTF_8
            )
        } catch (e: Exception) {
            // Gak fatal kalau gagal bikin, tinggal fallback default terus
        }
    }
}
