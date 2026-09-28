package com.example.noteapp.data.local

import androidx.room.*
import com.example.noteapp.domain.model.Category
import com.example.noteapp.domain.model.Note
import kotlinx.coroutines.flow.Flow

@Dao
interface NoteDao {
    @Query("SELECT * FROM notes WHERE isDeleted = 0 AND isArchived = 0 ORDER BY isPinned DESC, timestamp DESC")
    fun getActiveNotes(): Flow<List<Note>>

    @Query("SELECT * FROM notes WHERE isDeleted = 0 AND isArchived = 0 ORDER BY isPinned DESC, timestamp DESC")
    suspend fun getActiveNotesList(): List<Note>

    @Query("SELECT * FROM notes WHERE isDeleted = 0 AND isArchived = 0 AND isPinned = 1 ORDER BY timestamp DESC")
    suspend fun getPinnedNotesList(): List<Note>

    @Query("SELECT * FROM notes WHERE isArchived = 1 AND isDeleted = 0 ORDER BY timestamp DESC")
    fun getArchivedNotes(): Flow<List<Note>>

    @Query("SELECT * FROM notes WHERE isDeleted = 1 ORDER BY timestamp DESC")
    fun getTrashNotes(): Flow<List<Note>>

    @Query("SELECT * FROM notes WHERE categoryId = :categoryId AND isDeleted = 0 AND isArchived = 0 ORDER BY timestamp DESC")
    fun getNotesByCategory(categoryId: Long): Flow<List<Note>>

    @Query("SELECT * FROM notes WHERE id = :id")
    suspend fun getNoteById(id: Long): Note?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNote(note: Note): Long

    @Update
    suspend fun updateNote(note: Note)

    @Delete
    suspend fun deleteNote(note: Note)

    @Query("DELETE FROM notes WHERE isDeleted = 1")
    suspend fun emptyTrash()

    // Categories
    @Query("SELECT * FROM categories ORDER BY name ASC")
    fun getAllCategories(): Flow<List<Category>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCategory(category: Category): Long

    @Delete
    suspend fun deleteCategory(category: Category)
}
