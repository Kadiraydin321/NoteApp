package com.example.noteapp.data.repository

import android.content.Context
import com.example.noteapp.data.local.NoteDao
import com.example.noteapp.domain.model.Category
import com.example.noteapp.domain.model.Note
import com.example.noteapp.domain.repository.NoteRepository
import com.example.noteapp.widget.NotesWidgetProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class NoteRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: NoteDao
) : NoteRepository {
    override fun getActiveNotes(): Flow<List<Note>> = dao.getActiveNotes()
    override suspend fun getActiveNotesList(): List<Note> = dao.getActiveNotesList()
    override suspend fun getPinnedNotesList(): List<Note> = dao.getPinnedNotesList()
    override fun getArchivedNotes(): Flow<List<Note>> = dao.getArchivedNotes()
    override fun getTrashNotes(): Flow<List<Note>> = dao.getTrashNotes()
    override fun getNotesByCategory(categoryId: Long): Flow<List<Note>> = dao.getNotesByCategory(categoryId)
    override suspend fun getNoteById(id: Long): Note? = dao.getNoteById(id)

    override suspend fun insertNote(note: Note): Long {
        val id = dao.insertNote(note)
        NotesWidgetProvider.updateAllWidgets(context)
        return id
    }

    override suspend fun updateNote(note: Note) {
        dao.updateNote(note)
        NotesWidgetProvider.updateAllWidgets(context)
    }

    override suspend fun deleteNote(note: Note) {
        dao.deleteNote(note)
        NotesWidgetProvider.updateAllWidgets(context)
    }

    override suspend fun emptyTrash() {
        dao.emptyTrash()
        NotesWidgetProvider.updateAllWidgets(context)
    }

    override suspend fun getAllNotes(): List<Note> = dao.getAllNotes()

    override suspend fun insertNotes(notes: List<Note>): List<Long> {
        val ids = dao.insertNotes(notes)
        NotesWidgetProvider.updateAllWidgets(context)
        return ids
    }

    override suspend fun deleteAllNotes() {
        dao.deleteAllNotes()
        NotesWidgetProvider.updateAllWidgets(context)
    }

    override fun getAllCategories(): Flow<List<Category>> = dao.getAllCategories()
    override suspend fun getAllCategoriesList(): List<Category> = dao.getAllCategoriesList()
    override suspend fun insertCategory(category: Category): Long = dao.insertCategory(category)
    override suspend fun insertCategories(categories: List<Category>) = dao.insertCategories(categories)
    override suspend fun deleteAllCategories() {
        dao.deleteAllCategories()
        NotesWidgetProvider.updateAllWidgets(context)
    }
    override suspend fun deleteCategory(category: Category) {
        dao.clearCategoryFromNotes(category.id)
        dao.deleteCategory(category)
        NotesWidgetProvider.updateAllWidgets(context)
    }

    override suspend fun moveNotesToTrash(noteIds: List<Long>) {
        dao.moveNotesToTrash(noteIds)
        NotesWidgetProvider.updateAllWidgets(context)
    }

    override suspend fun updateNotesCategory(noteIds: List<Long>, categoryId: Long?) {
        dao.updateNotesCategory(noteIds, categoryId)
        NotesWidgetProvider.updateAllWidgets(context)
    }

    override suspend fun deleteNotesPermanently(noteIds: List<Long>) {
        dao.deleteNotesPermanently(noteIds)
        NotesWidgetProvider.updateAllWidgets(context)
    }
}
