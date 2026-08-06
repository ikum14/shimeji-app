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

    enum class UnlockSlot(val key: String, val label: String) {
        EVOLUSI_DEWASA("evolusi_dewasa", "Evolusi Dewasa (Level 11-18)"),
        EVOLUSI_DEWASA_PLUS("evolusi_dewasa_plus", "Evolusi Dewasa+ (Level 19+)"),
        MAHKOTA_JUARA("mahkota_juara", "Mahkota Juara (Menang Suwit 3x)")
    }

    private const val PREFS_UNLOCK = "pet_unlock_costumes"
    private const val KEY_UNLOCK_DATA = "unlock_data_json"
    private const val CUSTOM_UNLOCK_DIR = "custom_unlock_costumes"

    // slot.key -> absolute path gambar (satu slot cuma satu gambar, beda dari PoseSpriteManager)
    private val _unlockImages = MutableStateFlow<Map<String, String>>(emptyMap())
    val unlockImages = _unlockImages.asStateFlow()

    /** Panggil ini di titik yang sama dengan initKarakter(context), misal MainActivity/Application.onCreate. */
    fun initUnlockSlots(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_UNLOCK, Context.MODE_PRIVATE)
        val json = prefs.getString(KEY_UNLOCK_DATA, null) ?: return
        try {
            val obj = org.json.JSONObject(json)
            val map = mutableMapOf<String, String>()
            obj.keys().forEach { key ->
                val path = obj.getString(key)
                if (File(path).exists()) map[key] = path
            }
            _unlockImages.value = map
        } catch (e: Exception) {
            Log.e(TAG, "Gagal baca data unlock costume tersimpan", e)
        }
    }

    private fun persistUnlockSlots(context: Context) {
        val obj = org.json.JSONObject()
        _unlockImages.value.forEach { (key, path) -> obj.put(key, path) }
        context.getSharedPreferences(PREFS_UNLOCK, Context.MODE_PRIVATE)
            .edit().putString(KEY_UNLOCK_DATA, obj.toString()).apply()
    }

    /** Upload/replace gambar buat 1 slot hadiah. Nimpa & hapus gambar lama slot ini kalau ada. */
    fun setUnlockImage(context: Context, slot: UnlockSlot, sourceUri: Uri): Boolean {
        return try {
            val dir = File(context.filesDir, CUSTOM_UNLOCK_DIR).apply { if (!exists()) mkdirs() }
            val mimeType = context.contentResolver.getType(sourceUri)
            val ext = when {
                mimeType?.contains("gif", ignoreCase = true) == true -> "gif"
                mimeType?.contains("jpeg", ignoreCase = true) == true ||
                    mimeType?.contains("jpg", ignoreCase = true) == true -> "jpg"
                else -> "png"
            }
            _unlockImages.value[slot.key]?.let { File(it).delete() }

            val destFile = File(dir, "${slot.key}_${System.currentTimeMillis()}.$ext")
            context.contentResolver.openInputStream(sourceUri)?.use { input ->
                FileOutputStream(destFile).use { output -> input.copyTo(output) }
            }

            _unlockImages.value = _unlockImages.value + (slot.key to destFile.absolutePath)
            persistUnlockSlots(context)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Gagal simpan unlock image ${slot.key}", e)
            false
        }
    }

    /** Hapus gambar custom slot ini -> balik pakai fallback bawaan (Unsplash). */
    fun clearUnlockImage(context: Context, slot: UnlockSlot) {
        _unlockImages.value[slot.key]?.let { File(it).delete() }
        _unlockImages.value = _unlockImages.value - slot.key
        persistUnlockSlots(context)
    }

    fun getUnlockImagePath(slot: UnlockSlot): String? = _unlockImages.value[slot.key]

    private const val PREFS_REMOVED_DEFAULTS = "pet_removed_defaults"
    private const val KEY_REMOVED_IDS = "removed_default_ids"

    /** Baca daftar id kostum bawaan (baju_sekolah/gaun_pesta/piyama/foto stok) yang udah dihapus Master dari lemari. */
    private fun loadRemovedDefaultIds(context: Context): Set<String> {
        val prefs = context.getSharedPreferences(PREFS_REMOVED_DEFAULTS, Context.MODE_PRIVATE)
        val json = prefs.getString(KEY_REMOVED_IDS, null) ?: return emptySet()
        return try {
            val arr = org.json.JSONArray(json)
            (0 until arr.length()).map { arr.getString(it) }.toSet()
        } catch (e: Exception) {
            emptySet()
        }
    }

    private fun persistRemovedDefaultIds(context: Context, ids: Set<String>) {
        val arr = org.json.JSONArray()
        ids.forEach { arr.put(it) }
        context.getSharedPreferences(PREFS_REMOVED_DEFAULTS, Context.MODE_PRIVATE)
            .edit().putString(KEY_REMOVED_IDS, arr.toString()).apply()
    }

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
                    getUnlockImagePath(UnlockSlot.EVOLUSI_DEWASA_PLUS)
                        ?: "https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=500&auto=format&fit=crop&q=80" // fallback Flirty Tsundere
                else -> costumeId
            }
        }

        if (level <= 10) return costumeId

        // Level 11..18 Adult Phase -- semua kostum awal digabung ke 1 slot upload EVOLUSI_DEWASA
        return when (costumeId) {
            "https://images.unsplash.com/photo-1578632767115-351597cf2477?w=500&auto=format&fit=crop&q=80",
            "https://images.unsplash.com/photo-1534447677768-be436bb09401?w=500&auto=format&fit=crop&q=80",
            "https://images.unsplash.com/photo-1607604276583-eef5d076aa5f?w=500&auto=format&fit=crop&q=80",
            "default", "baju_sekolah", "gaun_pesta", "piyama" ->
                getUnlockImagePath(UnlockSlot.EVOLUSI_DEWASA)
                    ?: "https://images.unsplash.com/photo-1563089145-599997674d42?w=500&auto=format&fit=crop&q=80" // fallback Dewasa Goddess
            else -> costumeId
        }
    }

    fun initKarakter(context: Context) {
        val removedIds = loadRemovedDefaultIds(context)
        val visibleDefaults = defaultCostumes.filter { it.id !in removedIds }

        val customDir = File(context.filesDir, CUSTOM_DIR_NAME)
        val customItems = if (customDir.exists()) {
            val customFiles = customDir.listFiles()?.sortedByDescending { it.lastModified() } ?: emptyList()
            customFiles.mapIndexed { index, file ->
                CostumeItem(
                    id = file.absolutePath,
                    name = "Karakter Kustom #${index + 1}",
                    isCustom = true,
                    customFilePath = file.absolutePath
                )
            }
        } else emptyList()

        _listKarakter.value = visibleDefaults + customItems
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
        val championId = getUnlockImagePath(UnlockSlot.MAHKOTA_JUARA)
            ?: "https://images.unsplash.com/photo-1579783900882-c0d3dad7b119?w=500&auto=format&fit=crop&q=80" // fallback
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

    /**
     * Hapus item dari lemari kostum. Bisa hapus:
     * - Karakter kustom upload Master dari galeri (ada customFilePath) -> file fisik ikut kehapus
     * - Kostum bawaan app (baju_sekolah/gaun_pesta/piyama/foto stok Unsplash) -> gak ada file fisik,
     *   cukup ditandai "disembunyikan" (persist) biar gak muncul lagi walau app ditutup-buka
     * TIDAK bisa dihapus: "Default Chibi" (fallback utama, wajib selalu ada), dan item hadiah unlock
     * (misal Mahkota Juara) -- itu bukan bagian dari lemari kostum biasa, dikelola lewat CostumeManager.UnlockSlot.
     */
    fun hapusKarakter(context: Context, item: CostumeItem): Boolean {
        if (item.id == "default") return false

        return try {
            when {
                item.customFilePath != null -> File(item.customFilePath).delete()
                defaultCostumes.any { it.id == item.id } -> {
                    val removed = loadRemovedDefaultIds(context) + item.id
                    persistRemovedDefaultIds(context, removed)
                }
                else -> return false // item lain (hadiah unlock) gak bisa dihapus dari sini
            }

            _listKarakter.value = _listKarakter.value.filter { it.id != item.id }
            if (_kostumAktif.value == item.id) {
                _kostumAktif.value = "default" // kostum yang dihapus lagi dipakai -> balik ke default
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Gagal hapus karakter kustom", e)
            false
        }
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

