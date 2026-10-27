package com.example.noteapp.data.repository

import android.content.Context
import com.example.noteapp.data.local.NoteDao
import com.example.noteapp.data.local.NoteDraftEntity
import com.example.noteapp.data.security.NoteCryptoManager
import com.example.noteapp.domain.model.Category
import com.example.noteapp.domain.model.Note
import com.example.noteapp.domain.model.NoteHistory
import com.example.noteapp.domain.repository.NoteRepository
import com.example.noteapp.domain.util.NoteDraft
import com.example.noteapp.widget.NotesWidgetProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class NoteRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: NoteDao,
    private val cryptoManager: NoteCryptoManager
) : NoteRepository {
    override fun getActiveNotes(): Flow<List<Note>> = dao.getActiveNotes()
    override suspend fun getActiveNotesList(): List<Note> = dao.getActiveNotesList().map { note ->
        if (cryptoManager.isEncrypted(note.content)) {
            note.copy(content = cryptoManager.decrypt(note.content))
        } else {
            note
        }
    }
    override suspend fun getPinnedNotesList(): List<Note> = dao.getPinnedNotesList().map { note ->
        if (cryptoManager.isEncrypted(note.content)) {
            note.copy(content = cryptoManager.decrypt(note.content))
        } else {
            note
        }
    }
    override fun getArchivedNotes(): Flow<List<Note>> = dao.getArchivedNotes()
    override fun getTrashNotes(): Flow<List<Note>> = dao.getTrashNotes()
    override fun getNotesByCategory(categoryId: Long): Flow<List<Note>> = dao.getNotesByCategory(categoryId)

    override suspend fun getNoteById(id: Long): Note? {
        val note = dao.getNoteById(id) ?: return null
        return if (cryptoManager.isEncrypted(note.content)) {
            note.copy(content = cryptoManager.decrypt(note.content))
        } else {
            note
        }
    }

    override suspend fun getNoteByTitle(title: String): Note? {
        val note = dao.getNoteByTitle(title) ?: return null
        return if (cryptoManager.isEncrypted(note.content)) {
            note.copy(content = cryptoManager.decrypt(note.content))
        } else {
            note
        }
    }

    override suspend fun getNotesLinkingTo(noteTitle: String, excludeId: Long): List<Note> {
        if (noteTitle.isBlank()) return emptyList()
        val linkToken = "[[$noteTitle]]"
        return dao.getNotesLinkingTo(linkToken, noteTitle, excludeId).map { note ->
            if (cryptoManager.isEncrypted(note.content)) {
                note.copy(content = cryptoManager.decrypt(note.content))
            } else {
                note
            }
        }
    }

    override suspend fun insertNote(note: Note): Long {
        val safeNote = if (note.isLocked && note.content.isNotBlank() && !cryptoManager.isEncrypted(note.content)) {
            note.copy(content = cryptoManager.encrypt(note.content))
        } else note
        val id = dao.insertNote(safeNote)
        NotesWidgetProvider.sendRefreshBroadcast(context)
        return id
    }

    override suspend fun updateNote(note: Note) {
        val safeNote = if (note.isLocked && note.content.isNotBlank() && !cryptoManager.isEncrypted(note.content)) {
            note.copy(content = cryptoManager.encrypt(note.content))
        } else if (!note.isLocked && cryptoManager.isEncrypted(note.content)) {
            note.copy(content = cryptoManager.decrypt(note.content))
        } else note
        dao.updateNote(safeNote)
        NotesWidgetProvider.sendRefreshBroadcast(context)
    }

    override suspend fun deleteNote(note: Note) {
        dao.deleteNote(note)
        NotesWidgetProvider.sendRefreshBroadcast(context)
    }

    override suspend fun emptyTrash() {
        dao.emptyTrash()
        NotesWidgetProvider.sendRefreshBroadcast(context)
    }

    override suspend fun getAllNotes(): List<Note> = dao.getAllNotes().map { note ->
        if (cryptoManager.isEncrypted(note.content)) {
            note.copy(content = cryptoManager.decrypt(note.content))
        } else {
            note
        }
    }

    override suspend fun insertNotes(notes: List<Note>): List<Long> {
        val safeNotes = notes.map { note ->
            if (note.isLocked && note.content.isNotBlank()) {
                note.copy(content = cryptoManager.encrypt(note.content))
            } else note
        }
        val ids = dao.insertNotes(safeNotes)
        NotesWidgetProvider.sendRefreshBroadcast(context)
        return ids
    }

    override suspend fun deleteAllNotes() {
        dao.deleteAllNotes()
        NotesWidgetProvider.sendRefreshBroadcast(context)
    }

    override fun getAllCategories(): Flow<List<Category>> = dao.getAllCategories()
    override suspend fun getAllCategoriesList(): List<Category> = dao.getAllCategoriesList()
    override suspend fun insertCategory(category: Category): Long = dao.insertCategory(category)
    override suspend fun insertCategories(categories: List<Category>) = dao.insertCategories(categories)
    override suspend fun deleteAllCategories() {
        dao.deleteAllCategories()
        NotesWidgetProvider.sendRefreshBroadcast(context)
    }
    override suspend fun deleteCategory(category: Category) {
        dao.clearCategoryFromNotes(category.id)
        dao.deleteCategory(category)
        NotesWidgetProvider.sendRefreshBroadcast(context)
    }

    override suspend fun moveNotesToTrash(noteIds: List<Long>, deletedAt: Long) {
        dao.moveNotesToTrash(noteIds, deletedAt)
        NotesWidgetProvider.sendRefreshBroadcast(context)
    }

    override suspend fun cleanUpOldTrashNotes(threshold: Long) {
        try {
            val expiredNotes = dao.getExpiredTrashNotes(threshold)
            for (note in expiredNotes) {
                for (attachmentPath in note.attachments) {
                    try {
                        val file = java.io.File(attachmentPath)
                        if (file.exists()) file.delete()
                    } catch (_: Exception) {}
                }
                if (!note.backgroundImage.isNullOrEmpty()) {
                    try {
                        val bgFile = java.io.File(note.backgroundImage)
                        if (bgFile.exists()) bgFile.delete()
                    } catch (_: Exception) {}
                }
            }
        } catch (_: Exception) {}
        dao.deleteExpiredTrashNotes(threshold)
        NotesWidgetProvider.sendRefreshBroadcast(context)
    }

    override suspend fun updateNotesCategory(noteIds: List<Long>, categoryId: Long?) {
        dao.updateNotesCategory(noteIds, categoryId)
        NotesWidgetProvider.sendRefreshBroadcast(context)
    }

    override suspend fun deleteNotesPermanently(noteIds: List<Long>) {
        dao.deleteNotesPermanently(noteIds)
        NotesWidgetProvider.sendRefreshBroadcast(context)
    }

    // Note Version History
    override fun getHistoryForNote(noteId: Long): Flow<List<NoteHistory>> = dao.getHistoryForNote(noteId)
    override suspend fun getHistoryListForNote(noteId: Long): List<NoteHistory> = dao.getHistoryListForNote(noteId)
    override suspend fun getHistoryById(historyId: Long): NoteHistory? = dao.getHistoryById(historyId)
    override suspend fun insertNoteHistory(history: NoteHistory): Long = dao.insertNoteHistory(history)
    override suspend fun pruneOldHistory(noteId: Long, keepCount: Int) = dao.pruneOldHistory(noteId, keepCount)
    override suspend fun deleteHistoryForNote(noteId: Long) = dao.deleteHistoryForNote(noteId)
    override suspend fun getAllNoteHistory(): List<NoteHistory> = dao.getAllNoteHistory()
    override suspend fun insertNoteHistories(histories: List<NoteHistory>) = dao.insertNoteHistories(histories)

    // Folders
    override fun getNotesByFolder(folderId: Long): Flow<List<Note>> = dao.getNotesByFolder(folderId)
    override suspend fun getNotesByFolderList(folderId: Long): List<Note> = dao.getNotesByFolderList(folderId).map { note ->
        if (cryptoManager.isEncrypted(note.content)) {
            note.copy(content = cryptoManager.decrypt(note.content))
        } else {
            note
        }
    }
    override suspend fun updateNotesFolder(noteIds: List<Long>, folderId: Long?) {
        dao.updateNotesFolder(noteIds, folderId)
        NotesWidgetProvider.updateAllWidgets(context)
    }

    // Draft Persistence & Crash Recovery
    override suspend fun getDraft(noteId: Long?): NoteDraft? {
        val targetId = noteId?.takeIf { it > 0L } ?: 0L
        val entity = dao.getDraft(targetId) ?: return null
        val normalizedId = if (entity.noteId <= 0L) null else entity.noteId
        val decryptedContent = if (cryptoManager.isEncrypted(entity.content)) {
            cryptoManager.decrypt(entity.content)
        } else {
            entity.content
        }
        return NoteDraft(
            noteId = normalizedId,
            title = entity.title,
            content = decryptedContent,
            timestamp = entity.timestamp
        )
    }

    override suspend fun saveDraft(noteId: Long?, title: String, content: String, timestamp: Long) {
        val targetId = noteId?.takeIf { it > 0L } ?: 0L
        if (title.isBlank() && content.isBlank()) {
            dao.deleteDraft(targetId)
            return
        }
        dao.insertDraft(
            NoteDraftEntity(
                noteId = targetId,
                title = title,
                content = content,
                timestamp = timestamp
            )
        )
    }

    override suspend fun deleteDraft(noteId: Long?) {
        val targetId = noteId?.takeIf { it > 0L } ?: 0L
        dao.deleteDraft(targetId)
    }

    override suspend fun getAllDrafts(): List<NoteDraft> {
        return dao.getAllDrafts().map { entity ->
            val normalizedId = if (entity.noteId <= 0L) null else entity.noteId
            val content = if (cryptoManager.isEncrypted(entity.content)) {
                cryptoManager.decrypt(entity.content)
            } else {
                entity.content
            }
            NoteDraft(
                noteId = normalizedId,
                title = entity.title,
                content = content,
                timestamp = entity.timestamp
            )
        }
    }

    override suspend fun deleteAllDrafts() {
        dao.deleteAllDrafts()
    }
}
