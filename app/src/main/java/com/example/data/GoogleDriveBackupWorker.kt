package com.example.data

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.io.File
import java.util.Calendar
import java.util.concurrent.TimeUnit

/**
 * Background WorkManager worker to upload pet_progress.md to Google Drive automatically at midnight / periodically.
 */
class GoogleDriveBackupWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        Log.d(TAG, "Running scheduled Google Drive backup worker...")

        val file = File(context.getExternalFilesDir(null), "pet_progress.md")
        if (!file.exists()) {
            Log.w(TAG, "pet_progress.md file does not exist locally.")
            return Result.failure()
        }

        val uploadResult = GoogleDriveBackupManager.uploadBackupToDrive(context, file)
        return if (uploadResult.isSuccess) {
            Log.i(TAG, "Scheduled backup succeeded: ${uploadResult.getOrNull()}")
            Result.success()
        } else {
            Log.e(TAG, "Scheduled backup failed", uploadResult.exceptionOrNull())
            Result.retry()
        }
    }

    companion object {
        private const val TAG = "DriveBackupWorker"
        const val WORK_NAME = "MidnightGoogleDriveBackupWorker"

        /**
         * Schedule daily backup around 00:00 AM (Midnight)
         */
        fun scheduleMidnightBackup(context: Context) {
            val currentDate = Calendar.getInstance()
            val dueDate = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
            }

            if (dueDate.before(currentDate)) {
                dueDate.add(Calendar.HOUR_OF_DAY, 24)
            }

            val timeDiff = dueDate.timeInMillis - currentDate.timeInMillis

            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val dailyWorkRequest = PeriodicWorkRequestBuilder<GoogleDriveBackupWorker>(24, TimeUnit.HOURS)
                .setInitialDelay(timeDiff, TimeUnit.MILLISECONDS)
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                dailyWorkRequest
            )

            Log.i(TAG, "Midnight backup scheduled in ${timeDiff / 1000 / 60} minutes.")
        }
    }
}
