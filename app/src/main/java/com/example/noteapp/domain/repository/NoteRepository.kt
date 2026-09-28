package com.example.noteapp.domain.repository

import com.example.noteapp.domain.model.Category
import com.example.noteapp.domain.model.Note
import kotlinx.coroutines.flow.Flow

interface NoteRepository {
    fun getActiveNotes(): Flow<List<Note>>
    suspend fun getActiveNotesList(): List<Note>
    suspend fun getPinnedNotesList(): List<Note>
    fun getArchivedNotes(): Flow<List<Note>>
    fun getTrashNotes(): Flow<List<Note>>
    fun getNotesByCategory(categoryId: Long): Flow<List<Note>>
    suspend fun getNoteById(id: Long): Note?
    suspend fun insertNote(note: Note): Long
    suspend fun updateNote(note: Note)
    suspend fun deleteNote(note: Note)
    suspend fun emptyTrash()

    suspend fun getAllNotes(): List<Note>
    suspend fun insertNotes(notes: List<Note>): List<Long>
    suspend fun deleteAllNotes()

    fun getAllCategories(): Flow<List<Category>>
    suspend fun getAllCategoriesList(): List<Category>
    suspend fun insertCategory(category: Category): Long
    suspend fun insertCategories(categories: List<Category>)
    suspend fun deleteAllCategories()
    suspend fun deleteCategory(category: Category)
}
