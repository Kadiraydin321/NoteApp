package com.example.noteapp.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.noteapp.domain.repository.NoteRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class AlarmReceiver : BroadcastReceiver() {

    @Inject
    lateinit var repository: NoteRepository

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action == Intent.ACTION_BOOT_COMPLETED ||
            action == Intent.ACTION_MY_PACKAGE_REPLACED ||
            action == "android.intent.action.QUICKBOOT_POWERON" ||
            action == "com.htc.intent.action.QUICKBOOT_POWERON"
        ) {
            val pendingResult = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val notes = repository.getActiveNotesList()
                    val now = System.currentTimeMillis()
                    val scheduler = AndroidAlarmScheduler(context)
                    notes.forEach { note ->
                        val reminder = note.reminderTime
                        if (reminder != null && reminder > now) {
                            scheduler.schedule(note.id, note.title, note.content, reminder)
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                    pendingResult.finish()
                }
            }
            return
        }

        val noteId = intent.getLongExtra("noteId", -1L)
        val title = intent.getStringExtra("title") ?: ""
        val content = intent.getStringExtra("content") ?: ""

        if (noteId != -1L) {
            val notificationHelper = NotificationHelper(context)
            notificationHelper.showReminderNotification(noteId, title, content)
        }
    }
}
