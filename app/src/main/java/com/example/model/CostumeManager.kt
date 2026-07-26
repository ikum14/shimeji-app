package com.example.model

import android.content.Context
import android.net.Uri
import android.util.Log
import com.example.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.io.FileOutputStream

data class CostumeItem(
    val id: String, // "default", "baju_sekolah", "gaun_pesta", "piyama", or absolute path to custom image/GIF
    val name: String,
    val drawableRes: Int? = null,
    val isCustom: Boolean = false,
    val customFilePath: String? = null
)

/**
 * Manages costume & custom gallery character selection for the Chibi Pet
 */
object CostumeManager {

    private const val TAG = "CostumeManager"
    private const val CUSTOM_DIR_NAME = "custom_pets"

    private val defaultCostumes = listOf(
        CostumeItem("default", "Default Chibi", R.drawable.img_chibi_pet_idle),
        CostumeItem("baju_sekolah", "Seragam Sekolah", R.drawable.img_costume_school),
        CostumeItem("gaun_pesta", "Gaun Pesta", R.drawable.img_costume_dress),
        CostumeItem("piyama", "Piyama Tidur", R.drawable.img_costume_pajamas),
        CostumeItem(
            id = "https://images.unsplash.com/photo-1578632767115-351597cf2477?w=500&auto=format&fit=crop&q=80",
            name = "🌸 Chibi Sakura (Anak)",
            isCustom = true
        ),
        CostumeItem(
            id = "https://images.unsplash.com/photo-1534447677768-be436bb09401?w=500&auto=format&fit=crop&q=80",
            name = "✨ Magical Girl (Anak)",
            isCustom = true
        ),
        CostumeItem(
            id = "https://images.unsplash.com/photo-1607604276583-eef5d076aa5f?w=500&auto=format&fit=crop&q=80",
            name = "🐾 Neko Girl (Anak)",
            isCustom = true
        ),
        CostumeItem(
            id = "https://images.unsplash.com/photo-1563089145-599997674d42?w=500&auto=format&fit=crop&q=80",
            name = "💃 Dewasa Goddess (Dewasa 11+)",
            isCustom = true
        ),
        CostumeItem(
            id = "https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=500&auto=format&fit=crop&q=80",
            name = "💖 Pita Merah & Gaun Tsundere (Dewasa 18+)",
            isCustom = true
        )
    )

    private val _listKarakter = MutableStateFlow<List<CostumeItem>>(defaultCostumes)
    val listKarakter = _listKarakter.asStateFlow()

    private val _kostumAktif = MutableStateFlow("default")
    val kostumAktif = _kostumAktif.asStateFlow()

    /**
     * Get effective costume ID or URL considering pet level phase:
     * - Level <= 10: Child Phase
     * - Level 11..18: Adult Phase
     * - Level > 18: Flirty / Tsundere Adult Phase
     */
    fun getEffectiveCostumeUrlOrId(costumeId: String, level: Int): String {
        if (level > 18) {
            // Level > 18 Flirty / Tsundere Adult Phase
            return when (costumeId) {
                "default", "baju_sekolah", "gaun_pesta", "piyama" ->
                    "https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=500&auto=format&fit=crop&q=80" // Flirty Tsundere
                else -> costumeId
            }
        }

        if (level <= 10) return costumeId

        // Level 11..18 Adult Phase
        return when (costumeId) {
            "https://images.unsplash.com/photo-1578632767115-351597cf2477?w=500&auto=format&fit=crop&q=80" ->
                "https://images.unsplash.com/photo-1563089145-599997674d42?w=500&auto=format&fit=crop&q=80"
            "https://images.unsplash.com/photo-1534447677768-be436bb09401?w=500&auto=format&fit=crop&q=80" ->
                "https://images.unsplash.com/photo-1618005182384-a83a8bd57fbe?w=500&auto=format&fit=crop&q=80"
            "https://images.unsplash.com/photo-1607604276583-eef5d076aa5f?w=500&auto=format&fit=crop&q=80" ->
                "https://images.unsplash.com/photo-1579783902614-a3fb3927b675?w=500&auto=format&fit=crop&q=80"
            "default", "baju_sekolah", "gaun_pesta", "piyama" ->
                "https://images.unsplash.com/photo-1563089145-599997674d42?w=500&auto=format&fit=crop&q=80" // Dewasa Goddess
            else -> costumeId
        }
    }

    fun initKarakter(context: Context) {
        val customDir = File(context.filesDir, CUSTOM_DIR_NAME)
        if (customDir.exists()) {
            val customFiles = customDir.listFiles()?.sortedByDescending { it.lastModified() } ?: emptyList()
            val customItems = customFiles.mapIndexed { index, file ->
                CostumeItem(
                    id = file.absolutePath,
                    name = "Karakter Kustom #${index + 1}",
                    isCustom = true,
                    customFilePath = file.absolutePath
                )
            }
            _listKarakter.value = defaultCostumes + customItems
        }
    }

    fun gantiKostum(namaKostum: String) {
        _kostumAktif.value = namaKostum
    }

    /**
     * Copy selected image/GIF from Uri to internal App Documents Directory (context.filesDir/custom_pets)
     * and add to listKarakter as new custom character option.
     */
    fun tambahKarakterDariGaleri(context: Context, sourceUri: Uri): CostumeItem? {
        return try {
            val customDir = File(context.filesDir, CUSTOM_DIR_NAME).apply { if (!exists()) mkdirs() }

            // Determine file extension (support GIF / PNG / JPG)
            val mimeType = context.contentResolver.getType(sourceUri)
            val ext = when {
                mimeType?.contains("gif", ignoreCase = true) == true -> "gif"
                mimeType?.contains("jpeg", ignoreCase = true) == true || mimeType?.contains("jpg", ignoreCase = true) == true -> "jpg"
                else -> "png"
            }

            val fileName = "custom_pet_${System.currentTimeMillis()}.$ext"
            val destFile = File(customDir, fileName)

            context.contentResolver.openInputStream(sourceUri)?.use { input ->
                FileOutputStream(destFile).use { output ->
                    input.copyTo(output)
                }
            }

            Log.i(TAG, "Custom character image copied to internal storage: ${destFile.absolutePath}")

            val newItem = CostumeItem(
                id = destFile.absolutePath,
                name = "Pet Galeri (${destFile.name.takeLast(8)})",
                isCustom = true,
                customFilePath = destFile.absolutePath
            )

            val updatedList = _listKarakter.value + newItem
            _listKarakter.value = updatedList

            // Automatically set as active costume
            _kostumAktif.value = newItem.id

            newItem
        } catch (e: Exception) {
            Log.e(TAG, "Error importing custom pet image from gallery", e)
            null
        }
    }

    /**
     * Unlock special Champion Crown costume when winning 3 times in a row in Mini-Game
     */
    fun unlockChampionCostume(): CostumeItem {
        val championId = "https://images.unsplash.com/photo-1579783900882-c0d3dad7b119?w=500&auto=format&fit=crop&q=80"
        val newItem = CostumeItem(
            id = championId,
            name = "🏆 Mahkota Emas Juara (Bonus 3x Win)",
            isCustom = true
        )
        if (_listKarakter.value.none { it.id == championId }) {
            _listKarakter.value = _listKarakter.value + newItem
        }
        _kostumAktif.value = championId
        return newItem
    }

    fun getCostumeDisplayName(namaKostum: String): String {
        val found = _listKarakter.value.find { it.id == namaKostum }
        if (found != null) return found.name

        return when (namaKostum) {
            "baju_sekolah" -> "Seragam Sekolah Sailor"
            "gaun_pesta" -> "Gaun Pesta Pink"
            "piyama" -> "Piyama Tidur Pastel"
            "default" -> "Pakaian Default"
            else -> if (namaKostum.contains("/")) "Karakter Galeri Kustom" else namaKostum
        }
    }
}

