package com.example.data

import android.content.Context
import org.json.JSONObject
import java.io.File

/**
 * Ngelacak file .md baru di vault Obsidian (folder utama & subfolder-nya) yang belum pernah
 * "dibacain" pet secara lengkap lewat TTS. Selain 3 file yang udah dipakai buat hal lain
 * (biodata.md, pet-quotes.md, pet_progress.md), SEMUA file .md dianggap kandidat bacaan.
 *
 * File yang udah dibaca gak bakal dibacain ulang -- kecuali file-nya diedit lagi (lastModified
 * berubah dari terakhir kali dibaca), baru dianggap "baru" lagi.
 */
object VaultReadingManager {
    private const val PREFS_NAME = "pet_vault_reading"
    private const val KEY_READ_MAP = "read_files_json" // relativePath -> lastModified saat dibaca

    private val RESERVED_NAMES = setOf("biodata.md", "pet-quotes.md", "pet_progress.md")

    private fun loadReadMap(context: Context): MutableMap<String, Long> {
        val json = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_READ_MAP, null) ?: return mutableMapOf()
        return try {
            val obj = JSONObject(json)
            val map = mutableMapOf<String, Long>()
            obj.keys().forEach { key -> map[key] = obj.getLong(key) }
            map
        } catch (e: Exception) {
            mutableMapOf()
        }
    }

    private fun saveReadMap(context: Context, map: Map<String, Long>) {
        val obj = JSONObject()
        map.forEach { (key, value) -> obj.put(key, value) }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_READ_MAP, obj.toString()).apply()
    }

    /**
     * Cari 1 file .md yang belum dibaca (atau udah diedit ulang sejak terakhir dibaca).
     * Return null kalau gak ada yang baru, atau kalau izin "Semua Akses File" belum diaktifin.
     * Sengaja cuma balikin 1 file per panggilan (bukan list) -- biar pet baca satu-satu,
     * gak numpuk semuanya jadi 1 sesi baca panjang yang bikin bosan.
     */
    fun findNextUnreadFile(context: Context): File? {
        if (!VaultPathProvider.hasAllFilesAccess()) return null
        val vaultDir = VaultPathProvider.getObsidianVaultDir()
        if (!vaultDir.exists()) return null
        val readMap = loadReadMap(context)

        val candidates = try {
            vaultDir.walkTopDown()
                .filter { it.isFile && it.extension.equals("md", ignoreCase = true) }
                .filter { it.name !in RESERVED_NAMES }
                .sortedBy { it.lastModified() } // file paling lama nunggu, dibaca duluan
                .toList()
        } catch (e: Exception) {
            return null
        }

        for (file in candidates) {
            val relPath = file.relativeTo(vaultDir).path
            val lastRead = readMap[relPath]
            if (lastRead == null || lastRead != file.lastModified()) {
                return file
            }
        }
        return null
    }

    /** Tandain file ini udah dibaca, jadi gak bakal dibacain ulang lagi (kecuali diedit). */
    fun markAsRead(context: Context, file: File) {
        val vaultDir = VaultPathProvider.getObsidianVaultDir()
        val relPath = file.relativeTo(vaultDir).path
        val map = loadReadMap(context)
        map[relPath] = file.lastModified()
        saveReadMap(context, map)
    }
}
