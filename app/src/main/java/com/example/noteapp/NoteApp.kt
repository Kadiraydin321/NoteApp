package com.example.noteapp

import android.app.Application
import com.example.noteapp.data.sample.SampleDataLoader
import com.example.noteapp.domain.repository.NoteRepository
import com.example.noteapp.widget.NotesWidgetProvider
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class NoteApp : Application() {

    @Inject
    lateinit var repository: NoteRepository

    @Inject
    lateinit var sampleDataLoader: SampleDataLoader

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onCreate() {
        super.onCreate()
        observeNotesForWidgetSync()
        applicationScope.launch {
            sampleDataLoader.populateIfEmpty(force = false)
        }
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
