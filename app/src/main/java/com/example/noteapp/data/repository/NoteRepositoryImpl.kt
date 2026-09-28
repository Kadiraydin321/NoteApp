package com.example.noteapp.data.repository

import com.example.noteapp.data.local.NoteDao
import com.example.noteapp.domain.model.Category
import com.example.noteapp.domain.model.Note
import com.example.noteapp.domain.repository.NoteRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class NoteRepositoryImpl @Inject constructor(
    private val dao: NoteDao
) : NoteRepository {
    override fun getActiveNotes(): Flow<List<Note>> = dao.getActiveNotes()
    override suspend fun getActiveNotesList(): List<Note> = dao.getActiveNotesList()
    override suspend fun getPinnedNotesList(): List<Note> = dao.getPinnedNotesList()
    override fun getArchivedNotes(): Flow<List<Note>> = dao.getArchivedNotes()
    override fun getTrashNotes(): Flow<List<Note>> = dao.getTrashNotes()
    override fun getNotesByCategory(categoryId: Long): Flow<List<Note>> = dao.getNotesByCategory(categoryId)
    override suspend fun getNoteById(id: Long): Note? = dao.getNoteById(id)
    override suspend fun insertNote(note: Note): Long = dao.insertNote(note)
    override suspend fun updateNote(note: Note) = dao.updateNote(note)
    override suspend fun deleteNote(note: Note) = dao.deleteNote(note)
    override suspend fun emptyTrash() = dao.emptyTrash()

    override fun getAllCategories(): Flow<List<Category>> = dao.getAllCategories()
    override suspend fun insertCategory(category: Category): Long = dao.insertCategory(category)
    override suspend fun deleteCategory(category: Category) = dao.deleteCategory(category)
}
