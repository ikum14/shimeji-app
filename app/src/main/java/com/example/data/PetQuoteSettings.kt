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
    private const val MAX_LINES_PER_CATEGORY = 25 // cap biar file gak numpuk tak terbatas

    // Cache singkat biar gak baca file tiap kali speakBubble dipanggil beruntun cepat.
    private var cachedQuotes: Map<String, List<String>>? = null
    private var cachedAt: Long = 0L
    private const val CACHE_TTL_MS = 5_000L

    // Kalimat terakhir yang dikasih keluar per kategori, biar gak muncul 2x berturut-turut.
    private val lastGivenPerCategory = mutableMapOf<String, String>()

    private fun getQuotesFile(): File? {
        if (!VaultPathProvider.hasAllFilesAccess()) return null
        return File(VaultPathProvider.getObsidianVaultDir(), QUOTES_FILE_NAME)
    }

    private fun loadAll(): Map<String, List<String>> {
        val now = System.currentTimeMillis()
        cachedQuotes?.let { if (now - cachedAt < CACHE_TTL_MS) return it }

        val file = getQuotesFile()
        if (file == null || !file.exists()) {
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
     * Gak bakal ngasih kalimat yang SAMA PERSIS kayak yang barusan dikasih buat kategori
     * yang sama (kecuali cuma ada 1 pilihan doang, ya mau gimana lagi).
     */
    fun getQuote(category: String, defaultQuotes: List<String>): String {
        val key = category.lowercase()
        val custom = loadAll()[key]
        val pool = if (!custom.isNullOrEmpty()) custom else defaultQuotes
        if (pool.isEmpty()) return ""

        val lastGiven = lastGivenPerCategory[key]
        var pick = pool.random()
        if (pool.size > 1) {
            var attempts = 0
            while (pick == lastGiven && attempts < 5) {
                pick = pool.random()
                attempts++
            }
        }
        lastGivenPerCategory[key] = pick
        return pick
    }

    /**
     * Simpan kalimat hasil AI (Gemini) ke pet-quotes.md, di bawah kategori terkait, supaya
     * ke depannya kalimat itu ikut kepakai lagi sebagai template gratis (gak perlu manggil
     * AI lagi buat variasi yang udah pernah didapet). Dibatasin MAX_LINES_PER_CATEGORY biar
     * file gak numpuk tak terbatas -- kalau kepenuhan, baris paling lama di kategori itu
     * yang dibuang duluan.
     */
    fun appendGeneratedQuote(category: String, quote: String) {
        val trimmedQuote = quote.trim()
        if (trimmedQuote.isBlank()) return
        if (!VaultPathProvider.hasAllFilesAccess()) return

        val file = getQuotesFile() ?: return
        try {
            if (!file.exists()) ensureTemplateExists()

            val lines = file.readLines(Charsets.UTF_8).toMutableList()
            val headerText = "## ${category.replaceFirstChar { it.uppercase() }}"
            var headerIndex = lines.indexOfFirst {
                it.trim().startsWith("## ") && it.trim().removePrefix("## ").trim().equals(category, ignoreCase = true)
            }

            if (headerIndex == -1) {
                // Kategori belum ada di file -> tambahin section baru di akhir file
                if (lines.isNotEmpty() && lines.last().isNotBlank()) lines.add("")
                lines.add(headerText)
                lines.add(trimmedQuote)
            } else {
                // Kategori udah ada -> sisipin kalimat baru tepat di bawah heading-nya
                lines.add(headerIndex + 1, trimmedQuote)

                // Hitung berapa baris isi di section ini (sampai heading berikutnya / akhir file)
                var sectionEnd = headerIndex + 1
                while (sectionEnd < lines.size && !lines[sectionEnd].trim().startsWith("## ")) {
                    sectionEnd++
                }
                val sectionLines = (headerIndex + 1 until sectionEnd)
                    .map { lines[it] }
                    .filter { it.isNotBlank() }
                if (sectionLines.size > MAX_LINES_PER_CATEGORY) {
                    // Buang baris paling lama (paling bawah section = paling lama ditambahin,
                    // karena yang baru selalu disisipin di ATAS)
                    val excess = sectionLines.size - MAX_LINES_PER_CATEGORY
                    repeat(excess) {
                        val lastNonBlankIdx = (sectionEnd - 1 downTo headerIndex + 1)
                            .firstOrNull { lines[it].isNotBlank() }
                        if (lastNonBlankIdx != null) {
                            lines.removeAt(lastNonBlankIdx)
                            sectionEnd--
                        }
                    }
                }
            }

            file.writeText(lines.joinToString("\n"), Charsets.UTF_8)
            cachedQuotes = null // invalidate cache biar langsung kebaca ulang
        } catch (e: Exception) {
            // Gagal nulis, gak fatal -- kalimat AI-nya tetap kepakai sekali ini aja
        }
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
                Kalimat hasil AI juga bakal otomatis nambah ke sini seiring waktu.

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
