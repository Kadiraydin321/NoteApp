package com.example.noteapp.worker

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/**
 * WorkManager periyodik ve anlık çöp temizleme zamanlayıcı yöneticisi.
 */
object TrashCleanupScheduler {

    /**
     * Günde 1 kez çalışan periyodik çöp kutusu temizleme görevini kaydeder.
     * Batarya durumu uygun olduğunda otomatik devreye girer.
     */
    fun schedulePeriodicCleanup(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiresBatteryNotLow(true)
            .build()

        val periodicWorkRequest = PeriodicWorkRequestBuilder<TrashCleanupWorker>(
            1, TimeUnit.DAYS
        )
            .setConstraints(constraints)
            .addTag(TrashCleanupWorker.TAG)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            TrashCleanupWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            periodicWorkRequest
        )
    }

    /**
     * Kullanıcı Ayarlar panelinden talep ettiğinde çöp temizliğini arka planda anında çalıştırır.
     */
    fun runImmediateCleanup(context: Context) {
        val oneTimeWorkRequest = OneTimeWorkRequestBuilder<TrashCleanupWorker>()
            .addTag(TrashCleanupWorker.TAG)
            .build()

        WorkManager.getInstance(context).enqueue(oneTimeWorkRequest)
    }
}
