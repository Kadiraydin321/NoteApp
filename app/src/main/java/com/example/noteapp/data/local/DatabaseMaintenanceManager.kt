package com.example.noteapp.data.local

import android.content.Context
import androidx.sqlite.db.SimpleSQLiteQuery
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

data class MaintenanceResult(
    val initialSizeKb: Long,
    val finalSizeKb: Long,
    val freedBytes: Long,
    val freedCacheBytes: Long,
    val executionTimeMs: Long
)

@Singleton
class DatabaseMaintenanceManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: NoteDatabase
) {
    suspend fun getStorageInfo(): Pair<Long, Long> = withContext(Dispatchers.IO) {
        val dbFile = context.getDatabasePath(NoteDatabase.DATABASE_NAME)
        val dbSize = if (dbFile.exists()) dbFile.length() else 0L
        val cacheSize = getFolderSize(context.cacheDir)
        Pair(dbSize, cacheSize)
    }

    suspend fun optimizeDatabase(): MaintenanceResult = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        val dbFile = context.getDatabasePath(NoteDatabase.DATABASE_NAME)
        val initialSize = if (dbFile.exists()) dbFile.length() else 0L

        // 1. Önbellek ve geçici dışa aktarım dosyalarını temizle
        val initialCacheSize = getFolderSize(context.cacheDir)
        cleanDirectory(context.cacheDir)
        val finalCacheSize = getFolderSize(context.cacheDir)
        val freedCache = (initialCacheSize - finalCacheSize).coerceAtLeast(0L)

        // 2. SQLite WAL Checkpoint, VACUUM ve ANALYZE
        try {
            val sdb = database.openHelper.writableDatabase
            sdb.query(SimpleSQLiteQuery("PRAGMA wal_checkpoint(FULL)")).use { it.moveToFirst() }
            sdb.query(SimpleSQLiteQuery("VACUUM")).use { it.moveToFirst() }
            sdb.query(SimpleSQLiteQuery("PRAGMA optimize")).use { it.moveToFirst() }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        val finalSize = if (dbFile.exists()) dbFile.length() else 0L
        val freedDb = (initialSize - finalSize).coerceAtLeast(0L)
        val duration = System.currentTimeMillis() - startTime

        MaintenanceResult(
            initialSizeKb = initialSize / 1024L,
            finalSizeKb = finalSize / 1024L,
            freedBytes = freedDb,
            freedCacheBytes = freedCache,
            executionTimeMs = duration
        )
    }

    private fun getFolderSize(dir: File): Long {
        var size = 0L
        dir.listFiles()?.forEach { file ->
            size += if (file.isDirectory) getFolderSize(file) else file.length()
        }
        return size
    }

    private fun cleanDirectory(dir: File) {
        dir.listFiles()?.forEach { file ->
            if (file.isDirectory) {
                cleanDirectory(file)
            } else {
                file.delete()
            }
        }
    }
}
