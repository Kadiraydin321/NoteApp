package com.example.noteapp

import android.app.Application
import com.example.noteapp.domain.repository.NoteRepository
import com.example.noteapp.widget.NotesWidgetProvider
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject

@HiltAndroidApp
class NoteApp : Application(), ImageLoaderFactory {

    @Inject
    lateinit var repository: NoteRepository

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.25)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizeBytes(64L * 1024 * 1024) // 64 MB
                    .build()
            }
            .crossfade(true)
            .respectCacheHeaders(false)
            .build()
    }

    override fun onCreate() {
        super.onCreate()
        observeNotesForWidgetSync()
        com.example.noteapp.worker.TrashCleanupScheduler.schedulePeriodicCleanup(this)
        com.example.noteapp.domain.util.AppShortcutManager.initDynamicShortcuts(this)
    }

    /**
     * Veritabanında (Room) herhangi bir not eklendiğinde, silindiğinde,
     * güncellendiğinde veya geri yüklendiğinde widget verilerini anında
     * ve otomatik olarak senkronize eder.
     */
    private fun observeNotesForWidgetSync() {
        repository.getActiveNotes()
            .drop(1) // İlk açılıştaki başlatma emisyonunu atla
            .onEach {
                NotesWidgetProvider.updateAllWidgets(this@NoteApp)
            }
            .launchIn(applicationScope)
    }
}
