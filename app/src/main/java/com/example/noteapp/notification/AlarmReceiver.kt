package com.example.noteapp.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class AlarmReceiver : BroadcastReceiver {
    constructor() : super()

    override fun onReceive(context: Context, intent: Intent) {
        val noteId = intent.getLongExtra("noteId", -1L)
        val title = intent.getStringExtra("title") ?: ""
        val content = intent.getStringExtra("content") ?: ""

        if (noteId != -1L) {
            val notificationHelper = NotificationHelper(context)
            notificationHelper.showReminderNotification(noteId, title, content)
        }
    }
}
