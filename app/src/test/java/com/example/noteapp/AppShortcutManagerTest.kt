package com.example.noteapp

import com.example.noteapp.domain.util.AppShortcutManager
import com.example.noteapp.widget.NotesWidgetProvider
import org.junit.Assert.*
import org.junit.Test

class AppShortcutManagerTest {

    @Test
    fun shortcutConstants_areUniqueAndValid() {
        val ids = setOf(
            AppShortcutManager.SHORTCUT_NEW_NOTE,
            AppShortcutManager.SHORTCUT_CHECKLIST,
            AppShortcutManager.SHORTCUT_VOICE,
            AppShortcutManager.SHORTCUT_DRAW
        )
        assertEquals(4, ids.size)
        assertTrue(ids.all { it.isNotBlank() })
    }

    @Test
    fun resolveActionRoute_mapsAllActionTypesCorrectly() {
        assertEquals(
            "note_detail_screen",
            AppShortcutManager.resolveActionRoute(NotesWidgetProvider.ACTION_TYPE_TEXT)
        )
        assertEquals(
            "note_detail_screen?autoAction=checklist",
            AppShortcutManager.resolveActionRoute(NotesWidgetProvider.ACTION_TYPE_CHECKLIST)
        )
        assertEquals(
            "note_detail_screen?autoAction=voice",
            AppShortcutManager.resolveActionRoute(NotesWidgetProvider.ACTION_TYPE_VOICE)
        )
        assertEquals(
            "note_detail_screen?autoAction=image",
            AppShortcutManager.resolveActionRoute(NotesWidgetProvider.ACTION_TYPE_IMAGE)
        )
        assertEquals(
            "note_detail_screen?autoAction=draw",
            AppShortcutManager.resolveActionRoute(NotesWidgetProvider.ACTION_TYPE_DRAW)
        )
        assertEquals(
            "settings_screen",
            AppShortcutManager.resolveActionRoute(NotesWidgetProvider.ACTION_TYPE_SETTINGS)
        )
    }

    @Test
    fun resolveActionRoute_returnsFallbackForUnknownOrNull() {
        assertEquals("notes_screen", AppShortcutManager.resolveActionRoute(null))
        assertEquals("notes_screen", AppShortcutManager.resolveActionRoute(""))
        assertEquals("notes_screen", AppShortcutManager.resolveActionRoute("INVALID_ACTION"))
    }
}
