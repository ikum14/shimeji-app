package com.example.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import java.io.File

/**
 * Menentukan lokasi folder vault Obsidian di shared storage (Download/Obsidian),
 * bukan folder privat aplikasi, supaya nyambung dengan vault yang sudah ada di app Obsidian.
 *
 * Butuh izin "All files access" (MANAGE_EXTERNAL_STORAGE) di Android 11+ karena
 * folder Download/Obsidian ada di luar sandbox privat aplikasi.
 */
object VaultPathProvider {

    /** Ganti ini kalau nama folder vault Obsidian kamu berbeda. */
    private const val VAULT_FOLDER_NAME = "Obsidian"
    private const val APP_SUBFOLDER_NAME = "pet-virtual"

    fun getObsidianVaultDir(): File {
        val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val vaultDir = File(File(downloadDir, VAULT_FOLDER_NAME), APP_SUBFOLDER_NAME)
        if (!vaultDir.exists()) {
            vaultDir.mkdirs()
        }
        return vaultDir
    }

    fun hasAllFilesAccess(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            true // Di bawah Android 11 tidak butuh izin khusus ini
        }
    }

    fun requestAllFilesAccess(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                intent.data = Uri.parse("package:${context.packageName}")
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
            } catch (e: Exception) {
                val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
            }
        }
    }
}
