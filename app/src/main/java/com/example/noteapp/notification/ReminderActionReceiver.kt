package com.example.noteapp.notification

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast
import com.example.noteapp.domain.repository.NoteRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class ReminderActionReceiver : BroadcastReceiver() {

    @Inject
    lateinit var repository: NoteRepository

    override fun onReceive(context: Context, intent: Intent) {
        val noteId = intent.getLongExtra(EXTRA_NOTE_ID, -1L)
        if (noteId == -1L) return

        if (intent.action == ACTION_UNPIN_STICKY) {
            NotificationHelper(context).cancelStickyNotification(noteId)
            Toast.makeText(context, "📌 Bildirim sabitlemesi kaldırıldı", Toast.LENGTH_SHORT).show()
            return
        }

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(noteId.toInt())

        when (intent.action) {
            ACTION_MARK_DONE -> {
                CoroutineScope(Dispatchers.IO).launch {
                    val note = repository.getNoteById(noteId)
                    if (note != null) {
                        // Hatırlatıcıyı tamamlandı olarak kapat
                        repository.insertNote(note.copy(reminderTime = null))
                    }
                }
                Toast.makeText(context, "✓ Hatırlatıcı tamamlandı", Toast.LENGTH_SHORT).show()
            }
            ACTION_SNOOZE -> {
                val title = intent.getStringExtra(EXTRA_NOTE_TITLE) ?: ""
                val content = intent.getStringExtra(EXTRA_NOTE_CONTENT) ?: ""
                val snoozeMinutes = intent.getIntExtra(EXTRA_SNOOZE_MINUTES, 15)
                val snoozeTime = System.currentTimeMillis() + (snoozeMinutes * 60 * 1000L)

                val scheduler = AndroidAlarmScheduler(context)
                scheduler.schedule(noteId, title, content, snoozeTime)

                CoroutineScope(Dispatchers.IO).launch {
                    val note = repository.getNoteById(noteId)
                    if (note != null) {
                        repository.insertNote(note.copy(reminderTime = snoozeTime))
                    }
                }
                val label = if (snoozeMinutes >= 60) "${snoozeMinutes / 60} saat" else "$snoozeMinutes dakika"
                Toast.makeText(context, "⏱ Hatırlatıcı $label ertelendi", Toast.LENGTH_SHORT).show()
            }
        }
    }

    companion object {
        const val ACTION_MARK_DONE = "com.example.noteapp.ACTION_REMINDER_MARK_DONE"
        const val ACTION_SNOOZE = "com.example.noteapp.ACTION_REMINDER_SNOOZE"
        const val ACTION_UNPIN_STICKY = "com.example.noteapp.ACTION_UNPIN_STICKY"
        const val EXTRA_NOTE_ID = "extra_note_id"
        const val EXTRA_NOTE_TITLE = "extra_note_title"
        const val EXTRA_NOTE_CONTENT = "extra_note_content"
        const val EXTRA_SNOOZE_MINUTES = "extra_snooze_minutes"
    }
}
