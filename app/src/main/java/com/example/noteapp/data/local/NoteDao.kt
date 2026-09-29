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

    @Query("SELECT * FROM notes")
    suspend fun getAllNotes(): List<Note>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNotes(notes: List<Note>): List<Long>

    @Query("DELETE FROM notes")
    suspend fun deleteAllNotes()

    // Categories
    @Query("SELECT * FROM categories ORDER BY name ASC")
    fun getAllCategories(): Flow<List<Category>>

    @Query("SELECT * FROM categories")
    suspend fun getAllCategoriesList(): List<Category>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCategory(category: Category): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCategories(categories: List<Category>)

    @Query("DELETE FROM categories")
    suspend fun deleteAllCategories()

    @Query("UPDATE notes SET isDeleted = 1 WHERE id IN (:noteIds)")
    suspend fun moveNotesToTrash(noteIds: List<Long>)

    @Query("UPDATE notes SET categoryId = :categoryId WHERE id IN (:noteIds)")
    suspend fun updateNotesCategory(noteIds: List<Long>, categoryId: Long?)

    @Query("DELETE FROM notes WHERE id IN (:noteIds)")
    suspend fun deleteNotesPermanently(noteIds: List<Long>)

    @Query("UPDATE notes SET categoryId = NULL WHERE categoryId = :categoryId")
    suspend fun clearCategoryFromNotes(categoryId: Long)

    @Delete
    suspend fun deleteCategory(category: Category)
}
