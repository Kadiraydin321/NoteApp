package com.example.noteapp.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.noteapp.data.settings.AppSettingsManager
import com.example.noteapp.data.settings.TrashRetentionPolicy
import com.example.noteapp.domain.repository.NoteRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

/**
 * Saklama süresi (Retention Policy: 7/14/30 gün) dolmuş çöp notlarını ve bağlı dosyalarını
 * arka planda periyodik olarak temizleyen WorkManager işçi sınıfı.
 */
class TrashCleanupWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface TrashCleanupEntryPoint {
        fun repository(): NoteRepository
        fun settingsManager(): AppSettingsManager
    }

    override suspend fun doWork(): Result {
        return try {
            val entryPoint = EntryPointAccessors.fromApplication(
                applicationContext,
                TrashCleanupEntryPoint::class.java
            )
            val repository = entryPoint.repository()
            val settingsManager = entryPoint.settingsManager()

            val currentSettings = settingsManager.settings.value
            val policy = currentSettings.trashRetentionPolicy

            if (policy == TrashRetentionPolicy.NEVER || policy.days <= 0) {
                return Result.success()
            }

            val now = System.currentTimeMillis()
            val threshold = now - (policy.days.toLong() * 24L * 60L * 60L * 1000L)
            repository.cleanUpOldTrashNotes(threshold)

            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }

    companion object {
        const val WORK_NAME = "com.example.noteapp.trash_cleanup_work"
        const val TAG = "TrashCleanupWorker"
    }
}
