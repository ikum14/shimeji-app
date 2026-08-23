package com.example.data

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Debouncer buat Obsidian file sync — mencegah file I/O yang terlalu sering.
 *
 * OPTIMIZATION: Alih-alih write langsung setiap kali ada perubahan pet (mood, level, XP),
 * kumpulin dulu semua perubahan dan hanya write ke disk tiap 30 detik (atau saat
 * dipaksa sync melalui flush()).
 *
 * Ini banyak ngurangi disk I/O load, terutama waktu pet lagi banyak interaksi
 * (level up, mood change, idle chatter, dll bertubi-tubi dalam 1 menit).
 */
object ObsidianSyncDebouncer {
    private var debouncerScope: CoroutineScope? = null
    private var debouncePendingJob: Job? = null

    private var pendingData: PetProgressData? = null
    private val DEBOUNCE_INTERVAL_MS = 30_000L // 30 detik

    /**
     * Initialize debouncer. Call this ONCE at service onCreate.
     */
    fun init() {
        if (debouncerScope == null) {
            debouncerScope = CoroutineScope(Dispatchers.IO)
        }
    }

    /**
     * Queue perubahan pet progress buat di-sync ke Obsidian nanti, dengan debounce.
     * Kalau ada pending data belum di-write, update aja (replace).
     * Kalau belum ada pending job, schedule job debounce baru.
     */
    fun queueSync(context: Context, data: PetProgressData) {
        pendingData = data
        if (debouncePendingJob?.isActive != true) {
            // Schedule new debounce job
            debouncePendingJob = debouncerScope?.launch {
                delay(DEBOUNCE_INTERVAL_MS)
                flushSync(context)
            }
        }
        // Kalau job sudah aktif, just update pendingData -- akan di-write saat job kelar
    }

    /**
     * Force sync immediately (jangan nunggu debounce interval).
     * Dipanggil saat app akan tertutup atau service destroy.
     */
    fun flushSync(context: Context) {
        debouncePendingJob?.cancel()
        debouncePendingJob = null
        if (pendingData != null) {
            try {
                ObsidianPetExporter.saveProgressToFile(context, pendingData!!)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            pendingData = null
        }
    }

    /**
     * Cleanup resources. Call saat service onDestroy atau app closing.
     */
    fun shutdown(context: Context) {
        flushSync(context)
        debouncerScope?.cancel()
        debouncerScope = null
    }
}
