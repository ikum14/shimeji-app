package com.example.data

import android.content.Context
import android.os.Environment
import com.example.model.PetState
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Data Model representing Desktop Pet progress for Obsidian Integration
 */
data class PetProgressData(
    val petName: String = "Chibi Girl Shimeji",
    val level: Int = 5,
    val currentXp: Int = 420,
    val maxXp: Int = 1000,
    val emotion: String = "HAPPY", // HAPPY, IDLE, HELD, FALLING, SLEEPING
    val happinessLevel: Int = 95,
    val energyLevel: Int = 88,
    val positionX: Float = 200f,
    val positionY: Float = 650f,
    val physicsMode: String = "STAIR_STEP",
    val totalInteractions: Int = 38
)

/**
 * Exporter utility to read/write `pet_progress.md` with YAML front-matter
 * compatible with Obsidian Notes & Markdown readers on Android local storage.
 */
object ObsidianPetExporter {

    private const val FILE_NAME = "pet_progress.md"

    /**
     * Gets the file destination in the shared Obsidian vault (Download/Obsidian).
     * Jatuh balik ke folder privat app kalau izin "All files access" belum diberikan,
     * supaya app tidak crash meski belum sempat setting izin.
     */
    fun getTargetMarkdownFile(context: Context): File {
        val vaultDir = if (VaultPathProvider.hasAllFilesAccess()) {
            VaultPathProvider.getObsidianVaultDir()
        } else {
            context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS) ?: context.filesDir
        }
        if (!vaultDir.exists()) {
            vaultDir.mkdirs()
        }
        return File(vaultDir, FILE_NAME)
    }

    /**
     * Formats Pet Progress into standard YAML Front-Matter Markdown.
     */
    fun generateYamlFrontMatterContent(data: PetProgressData): String {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val timestamp = dateFormat.format(Date())

        return """
---
pet_name: "${data.petName}"
level: ${data.level}
xp: ${data.currentXp}
next_level_xp: ${data.maxXp}
emotion: "${data.emotion}"
happiness: ${data.happinessLevel}
energy: ${data.energyLevel}
position_x: ${data.positionX.toInt()}
position_y: ${data.positionY.toInt()}
physics_mode: "${data.physicsMode}"
total_interactions: ${data.totalInteractions}
last_sync: "$timestamp"
obsidian_vault_sync: true
tags:
  - shimeji
  - desktop_pet
  - obsidian_pet
  - android_pet
---

# 💖 Progress Desktop Pet Chibi Shimeji

> File ini diperbarui otomatis oleh **Desktop Pet Shimeji App** untuk integrasi **Obsidian Vault**.

## 📊 Status & Telemetri Pet
- **Level**: Level ${data.level} (XP: ${data.currentXp} / ${data.maxXp})
- **Emosi Status**: ${data.emotion} ✨
- **Tingkat Kebahagiaan**: ${data.happinessLevel}%
- **Tingkat Energi**: ${data.energyLevel}%
- **Koordinat Terakhir**: X = ${data.positionX.toInt()}px, Y = ${data.positionY.toInt()}px
- **Waktu Sync Terakhir**: $timestamp

## 📝 Catatan Log Interaksi Master
1. Karakter diangkat (Drag & Drop) dan dilepas di posisi atas.
2. Logika fisika `Stair Fall` berjalan otomatis menurunkan koordinat Y secara bertahap hingga menyentuh lantai.
3. Progress level, XP, dan status emosi berhasil tersimpan ke YAML front-matter ini.

---
*Pet Shimeji siap digunakan di dalam Obsidian Note kamu!*
""".trimIndent()
    }

    /**
     * Saves progress to local file `pet_progress.md` using Android Scoped Storage compliant storage.
     * Compatible with Android 11+ (API 30+) without requiring legacy storage permissions.
     */
    fun saveProgressToFile(context: Context, data: PetProgressData): File {
        val file = getTargetMarkdownFile(context)
        val content = generateYamlFrontMatterContent(data)
        
        try {
            // Android Scoped Storage write
            file.writeText(content, Charsets.UTF_8)
        } catch (e: Exception) {
            e.printStackTrace()
            // DocumentFile / ContentResolver fallback for Android 11+ restricted storage
            try {
                context.openFileOutput(FILE_NAME, Context.MODE_PRIVATE).use { output ->
                    output.write(content.toByteArray(Charsets.UTF_8))
                }
            } catch (ex: Exception) {
                ex.printStackTrace()
            }
        }
        return file
    }

    /**
     * Reads progress content if available.
     */
    fun readProgressFromFile(context: Context): String {
        val file = getTargetMarkdownFile(context)
        return if (file.exists()) {
            file.readText(Charsets.UTF_8)
        } else {
            "File belum dibuat. Tekan tombol 'Simpan Ke Obsidian .md' di bawah!"
        }
    }
}
