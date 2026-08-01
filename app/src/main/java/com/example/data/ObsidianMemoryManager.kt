package com.example.data

import android.content.Context
import android.os.Environment
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class UserMemoryData(
    val userName: String = "Master",
    val userHobby: String = "Koding",
    val rawContent: String = "",
    val isLoadedFromObsidian: Boolean = false,
    val lastUpdated: String = ""
)

/**
 * Manages reading & parsing Obsidian Vault notes (e.g., `biodata.md`) for Pet Memory System.
 */
object ObsidianMemoryManager {

    private const val TAG = "ObsidianMemoryManager"
    private const val BIODATA_FILE_NAME = "biodata.md"

    private val _userMemory = MutableStateFlow(UserMemoryData())
    val userMemory = _userMemory.asStateFlow()

    /**
     * Target `biodata.md` di dalam vault Obsidian master (Download/obsidian/pet-virtual),
     * supaya nyambung ke biodata yang beneran kamu tulis & baca lewat app Obsidian.
     * Jatuh balik ke folder privat app kalau izin "All files access" belum diberikan.
     */
    fun getBiodataFile(context: Context): File {
        val vaultDir = if (VaultPathProvider.hasAllFilesAccess()) {
            VaultPathProvider.getObsidianVaultDir()
        } else {
            context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS) ?: context.filesDir
        }
        if (!vaultDir.exists()) {
            vaultDir.mkdirs()
        }
        return File(vaultDir, BIODATA_FILE_NAME)
    }

    /**
     * Generate sample `biodata.md` if file does not exist
     */
    fun ensureSampleBiodataExists(context: Context): File {
        val file = getBiodataFile(context)
        if (!file.exists()) {
            val defaultContent = """
                # 📝 Biodata & Catatan Harian Master (Obsidian Vault)

                ---
                Nama: Alex
                Hobi: Koding
                Pekerjaan: Developer
                Catatan: Suka minum kopi dan belajar hal baru tentang AI!
                ---

                > File ini dibaca oleh **Desktop Pet Shimeji Memory System**.
                > Ubah teks di atas untuk menyesuaikan sapaan pet pada Anda!
            """.trimIndent()
            file.writeText(defaultContent, Charsets.UTF_8)
        }
        return file
    }

    /**
     * Read and parse `biodata.md` or any markdown file for `Nama:` and `Hobi:` keywords
     */
    fun loadMemoryFromObsidian(context: Context): UserMemoryData {
        val file = ensureSampleBiodataExists(context)
        try {
            val content = file.readText(Charsets.UTF_8)
            var extractedName = "Master"
            var extractedHobby = "Koding"

            content.lines().forEach { line ->
                val trimmed = line.trim()
                val lower = trimmed.lowercase()

                if (lower.startsWith("nama:") || lower.startsWith("nama =") || lower.startsWith("name:")) {
                    val value = trimmed.substringAfter(":").substringAfter("=").trim()
                        .removePrefix("[").removeSuffix("]")
                    if (value.isNotBlank()) {
                        extractedName = value
                    }
                } else if (lower.startsWith("hobi:") || lower.startsWith("hobi =") || lower.startsWith("hobby:")) {
                    val value = trimmed.substringAfter(":").substringAfter("=").trim()
                        .removePrefix("[").removeSuffix("]")
                    if (value.isNotBlank()) {
                        extractedHobby = value
                    }
                }
            }

            val nowStr = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()).format(Date())
            val memory = UserMemoryData(
                userName = extractedName,
                userHobby = extractedHobby,
                rawContent = content,
                isLoadedFromObsidian = true,
                lastUpdated = nowStr
            )

            _userMemory.value = memory
            Log.i(TAG, "Memory loaded from Obsidian: Name=$extractedName, Hobby=$extractedHobby")
            return memory
        } catch (e: Exception) {
            Log.e(TAG, "Error loading memory from Obsidian markdown file", e)
            return _userMemory.value
        }
    }

    /**
     * Baca SEMUA file .md di folder vault pet-virtual (kecuali pet_progress.md & pet-quotes.md,
     * karena itu data teknis internal pet / script kalimat -- bukan "pengetahuan" yang relevan
     * buat AI), digabung jadi satu konteks teks buat "otak" pet.
     *
     * Dipanggil ulang setiap kali pet mau ngomong (real-time, TIDAK di-cache) supaya file
     * baru/perubahan terbaru di Obsidian langsung kepakai tanpa perlu buka dashboard dulu.
     */
    fun readVaultContext(context: Context, maxChars: Int = 6000): String {
        val vaultDir = if (VaultPathProvider.hasAllFilesAccess()) {
            VaultPathProvider.getObsidianVaultDir()
        } else {
            context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS) ?: context.filesDir
        }
        if (!vaultDir.exists()) return ""

        val mdFiles = vaultDir.listFiles { file ->
            file.isFile && file.extension.equals("md", ignoreCase = true) &&
                file.name != "pet_progress.md" && file.name != "pet-quotes.md"
        }?.sortedBy { it.name } ?: return ""

        val builder = StringBuilder()
        for (file in mdFiles) {
            try {
                val content = file.readText(Charsets.UTF_8).trim()
                if (content.isBlank()) continue
                builder.append("### ${file.name}\n")
                builder.append(content)
                builder.append("\n\n")
            } catch (e: Exception) {
                Log.e(TAG, "Gagal baca ${file.name} buat konteks vault", e)
            }
        }

        val combined = builder.toString().trim()
        return if (combined.length > maxChars) {
            combined.take(maxChars) + "\n...(dipotong, isi vault kepanjangan)"
        } else {
            combined
        }
    }

    /**
     * Save/Update custom content directly to `biodata.md`
     */
    fun saveBiodataToFile(context: Context, newContent: String): Boolean {
        return try {
            val file = getBiodataFile(context)
            file.writeText(newContent, Charsets.UTF_8)
            loadMemoryFromObsidian(context)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save biodata.md", e)
            false
        }
    }

    /**
     * Personalize speech quotes using parsed memory (e.g. Kak [Nama], [Hobi])
     */
    fun personalizeQuote(baseQuote: String): String {
        val current = _userMemory.value
        val nameToUse = if (current.userName.isNotBlank() && current.userName != "Master") {
            "Kak ${current.userName}"
        } else {
            "Master"
        }

        var result = baseQuote.replace("Master", nameToUse)

        // Inject personalized hobby line randomly or if hobby exists
        if (current.userHobby.isNotBlank() && (1..3).random() == 1) {
            result = "Semangat ${current.userHobby}-nya hari ini, $nameToUse! 💖"
        }

        return result
    }
}
