package com.example.model

import android.content.Context
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

/**
 * Nyimpen gambar/GIF kustom per "pose slot" (idle diam, idle ngantuk, tap ringan, dll) --
 * TERPISAH dari sistem kostum (CostumeManager). Kostum nentuin "baju"-nya, pose slot ini
 * nentuin "frame/gambar mana yang dipakai buat momen tertentu" (idle, tap, drag, hide).
 *
 * SATU SLOT BISA NAMPUNG BANYAK GAMBAR -- pas dipakai, salah satunya dipilih RANDOM,
 * biar keliatan lebih hidup/variatif (persis kayak sistem kalimat template pet-quotes.md).
 * Kalau satu slot belum diisi Master sama sekali, otomatis fallback ke sprite default
 * bawaan app -- jadi Master bisa upload kapan aja aset-nya siap, gak harus sekaligus.
 */
object PoseSpriteManager {

    enum class PoseSlot(val key: String, val label: String, val group: String) {
        IDLE_DIAM("idle_diam", "Diam", "😌 Idle"),
        IDLE_NGANTUK("idle_ngantuk", "Ngantuk", "😌 Idle"),
        IDLE_TIDUR("idle_tidur", "Tidur (pakai kasur)", "😌 Idle"),
        HIDE_PINTU("hide_pintu", "Pintu (transisi sembunyi/muncul)", "🚪 Hide"),
        HIDE_NGINTIP("hide_ngintip", "Ngintip (pas ada trigger)", "🚪 Hide"),
        TAP_RINGAN("tap_ringan", "Reaksi Ringan", "👆 Tap"),
        TAP_BERLEBIHAN("tap_berlebihan", "Reaksi Berlebihan", "👆 Tap"),
        DRAG_PASRAH("drag_pasrah", "Pasrah (awal di-drag)", "✋ Drag"),
        DRAG_BERONTAK("drag_berontak", "Berontak (di-drag kelamaan)", "✋ Drag")
    }

    private const val TAG = "PoseSpriteManager"
    private const val PREFS_NAME = "pet_pose_sprites"
    private const val KEY_DATA = "pose_data_json"
    private const val CUSTOM_DIR_NAME = "custom_pose_sprites"

    // slot.key -> daftar absolute path gambar buat slot itu
    private val _poseFiles = MutableStateFlow<Map<String, List<String>>>(emptyMap())
    val poseFiles = _poseFiles.asStateFlow()

    fun init(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = prefs.getString(KEY_DATA, null)
        if (json == null) {
            _poseFiles.value = emptyMap()
            return
        }
        try {
            val obj = JSONObject(json)
            val map = mutableMapOf<String, List<String>>()
            obj.keys().forEach { key ->
                val arr = obj.getJSONArray(key)
                val paths = (0 until arr.length())
                    .map { arr.getString(it) }
                    .filter { File(it).exists() }
                if (paths.isNotEmpty()) map[key] = paths
            }
            _poseFiles.value = map
        } catch (e: Exception) {
            Log.e(TAG, "Gagal baca data pose tersimpan", e)
            _poseFiles.value = emptyMap()
        }
    }

    private fun persist(context: Context) {
        val obj = JSONObject()
        _poseFiles.value.forEach { (key, paths) ->
            obj.put(key, JSONArray(paths))
        }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_DATA, obj.toString()).apply()
    }

    /** Tambah SATU gambar baru ke daftar slot ini (gak nimpa yang lama, nambah pilihan). */
    fun addPoseImage(context: Context, slot: PoseSlot, sourceUri: Uri): Boolean {
        return try {
            val dir = File(context.filesDir, CUSTOM_DIR_NAME).apply { if (!exists()) mkdirs() }
            val mimeType = context.contentResolver.getType(sourceUri)
            val ext = when {
                mimeType?.contains("gif", ignoreCase = true) == true -> "gif"
                mimeType?.contains("jpeg", ignoreCase = true) == true ||
                    mimeType?.contains("jpg", ignoreCase = true) == true -> "jpg"
                else -> "png"
            }
            val destFile = File(dir, "${slot.key}_${System.currentTimeMillis()}.$ext")
            context.contentResolver.openInputStream(sourceUri)?.use { input ->
                FileOutputStream(destFile).use { output -> input.copyTo(output) }
            }

            val current = _poseFiles.value[slot.key] ?: emptyList()
            _poseFiles.value = _poseFiles.value + (slot.key to (current + destFile.absolutePath))
            persist(context)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Gagal simpan pose ${slot.key}", e)
            false
        }
    }

    /** Hapus SATU gambar spesifik dari daftar slot (bukan seluruh slot). */
    fun removePoseImage(context: Context, slot: PoseSlot, path: String) {
        File(path).delete()
        val current = _poseFiles.value[slot.key] ?: return
        val updated = current - path
        _poseFiles.value = if (updated.isEmpty()) {
            _poseFiles.value - slot.key
        } else {
            _poseFiles.value + (slot.key to updated)
        }
        persist(context)
    }

    fun getPoseImagePaths(slot: PoseSlot): List<String> = _poseFiles.value[slot.key] ?: emptyList()

    /** Ambil 1 gambar RANDOM dari daftar slot ini, buat dipakai pas render. Null kalau slot kosong. */
    fun getRandomPoseImagePath(slot: PoseSlot): String? = getPoseImagePaths(slot).randomOrNull()
}
