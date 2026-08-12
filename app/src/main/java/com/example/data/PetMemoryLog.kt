package com.example.data

import android.content.Context
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Nyimpen riwayat singkat apa yang barusan diomongin pet (dari Gemini), disimpan di
 * pet-memory.md di root vault Obsidian. Beda dari pet-quotes.md (yang isinya pool
 * kalimat buat DIPILIH ulang), file ini urut waktu (kronologis) dan dipakai buat kasih
 * konteks "riwayat" ke Gemini biar pet keliatan inget obrolan sebelumnya, bukan random
 * tiap saat kayak sebelumnya.
 *
 * Cuma nyimpen MAX_ENTRIES entri terbaru -- entri lama otomatis kebuang biar file gak
 * kepanjangan & konteks yang dikirim ke Gemini gak kegedean/nyampah.
 */
object PetMemoryLog {
    private const val TAG = "PetMemoryLog"
    private const val FILE_NAME = "pet-memory.md"
    private const val MAX_ENTRIES = 30
    private val timeFormat = SimpleDateFormat("dd MMM HH:mm", Locale("id", "ID"))

    private fun getFile(): File? {
        if (!VaultPathProvider.hasAllFilesAccess()) return null
        val vaultDir = VaultPathProvider.getObsidianVaultDir()
        if (!vaultDir.exists()) vaultDir.mkdirs()
        return File(vaultDir, FILE_NAME)
    }

    /** Catat 1 baris riwayat baru -- dipanggil tiap Gemini berhasil generate kalimat. */
    fun append(context: Context, text: String) {
        try {
            val file = getFile() ?: return
            val existing = if (file.exists()) readEntries(file) else emptyList()
            val timestamp = timeFormat.format(Date())
            val newEntries = (existing + "[$timestamp] $text").takeLast(MAX_ENTRIES)
            writeEntries(file, newEntries)
        } catch (e: Exception) {
            Log.e(TAG, "Gagal nyimpen memori pet", e)
        }
    }

    /** Ambil N entri terakhir sebagai 1 blok teks, buat disisipin ke prompt Gemini. */
    fun getRecentContext(maxEntries: Int = 8): String {
        return try {
            val file = getFile() ?: return ""
            if (!file.exists()) return ""
            readEntries(file).takeLast(maxEntries).joinToString("\n")
        } catch (e: Exception) {
            Log.e(TAG, "Gagal baca memori pet", e)
            ""
        }
    }

    private fun readEntries(file: File): List<String> {
        return file.readLines(Charsets.UTF_8)
            .map { it.trim() }
            .filter { it.startsWith("[") } // baris riwayat, bukan header markdown/kosong
    }

    private fun writeEntries(file: File, entries: List<String>) {
        val content = buildString {
            appendLine("# Riwayat Obrolan Pet")
            appendLine()
            appendLine("File ini diperbarui otomatis -- catatan singkat apa yang barusan diomongin pet, biar dia keliatan inget obrolan sebelumnya. Cuma nyimpen ${MAX_ENTRIES} entri terakhir.")
            appendLine()
            entries.forEach { appendLine(it) }
        }
        file.writeText(content, Charsets.UTF_8)
    }
}
