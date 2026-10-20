package com.example.noteapp.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.noteapp.MainActivity

class NotificationHelper(private val context: Context) {

    companion object {
        const val CHANNEL_ID = "note_reminder_channel"
        const val CHANNEL_NAME = "Not Hatırlatıcıları"
    }

    init {
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Planlanan not hatırlatıcı bildirimleri"
                enableVibration(true)
            }
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    fun showReminderNotification(noteId: Long, title: String, content: String) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("noteId", noteId)
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            noteId.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Aksiyon 1: Tamamlandı Olarak İşaretle
        val doneIntent = Intent(context, ReminderActionReceiver::class.java).apply {
            action = ReminderActionReceiver.ACTION_MARK_DONE
            putExtra(ReminderActionReceiver.EXTRA_NOTE_ID, noteId)
        }
        val donePendingIntent = PendingIntent.getBroadcast(
            context,
            (noteId * 10 + 1).toInt(),
            doneIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Aksiyon 2: 15 Dakika Ertele
        val snooze15Intent = Intent(context, ReminderActionReceiver::class.java).apply {
            action = ReminderActionReceiver.ACTION_SNOOZE
            putExtra(ReminderActionReceiver.EXTRA_NOTE_ID, noteId)
            putExtra(ReminderActionReceiver.EXTRA_NOTE_TITLE, title)
            putExtra(ReminderActionReceiver.EXTRA_NOTE_CONTENT, content)
            putExtra(ReminderActionReceiver.EXTRA_SNOOZE_MINUTES, 15)
        }
        val snooze15PendingIntent = PendingIntent.getBroadcast(
            context,
            (noteId * 10 + 2).toInt(),
            snooze15Intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Aksiyon 3: 1 Saat Ertele
        val snooze60Intent = Intent(context, ReminderActionReceiver::class.java).apply {
            action = ReminderActionReceiver.ACTION_SNOOZE
            putExtra(ReminderActionReceiver.EXTRA_NOTE_ID, noteId)
            putExtra(ReminderActionReceiver.EXTRA_NOTE_TITLE, title)
            putExtra(ReminderActionReceiver.EXTRA_NOTE_CONTENT, content)
            putExtra(ReminderActionReceiver.EXTRA_SNOOZE_MINUTES, 60)
        }
        val snooze60PendingIntent = PendingIntent.getBroadcast(
            context,
            (noteId * 10 + 3).toInt(),
            snooze60Intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle(if (title.isNotBlank()) title else "Not Hatırlatıcısı")
            .setContentText(content)
            .setStyle(NotificationCompat.BigTextStyle().bigText(content))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .addAction(android.R.drawable.checkbox_on_background, "✓ Tamamlandı", donePendingIntent)
            .addAction(android.R.drawable.ic_popup_sync, "⏱ 15 Dk", snooze15PendingIntent)
            .addAction(android.R.drawable.ic_popup_sync, "⏱ 1 Saat", snooze60PendingIntent)
            .build()

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(noteId.toInt(), notification)
    }
}
