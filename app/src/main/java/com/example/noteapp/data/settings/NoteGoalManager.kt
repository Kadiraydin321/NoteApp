package com.example.noteapp.data.settings

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Not bazlı özel kelime hedeflerini SharedPreferences üzerinde saklayan ve yöneten sınıf.
 */
@Singleton
class NoteGoalManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val prefs = context.getSharedPreferences("note_goals_prefs", Context.MODE_PRIVATE)

    fun getWordGoal(noteId: Long): Int? {
        if (noteId <= 0L) return null
        val goal = prefs.getInt("goal_$noteId", 0)
        return if (goal > 0) goal else null
    }

    fun setWordGoal(noteId: Long, goal: Int?) {
        if (noteId <= 0L) return
        if (goal == null || goal <= 0) {
            prefs.edit().remove("goal_$noteId").apply()
        } else {
            prefs.edit().putInt("goal_$noteId", goal).apply()
        }
    }

    fun removeGoal(noteId: Long) {
        if (noteId <= 0L) return
        prefs.edit().remove("goal_$noteId").apply()
    }

    fun hasGoal(noteId: Long): Boolean {
        return getWordGoal(noteId) != null
    }
}
