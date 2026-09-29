package com.example.noteapp.di

import android.app.Application
import androidx.room.Room
import com.example.noteapp.data.local.NoteDao
import com.example.noteapp.data.local.NoteDatabase
import com.example.noteapp.data.repository.NoteRepositoryImpl
import com.example.noteapp.domain.repository.NoteRepository
import com.example.noteapp.media.AudioPlayer
import com.example.noteapp.media.AudioRecorder
import com.example.noteapp.notification.AlarmScheduler
import com.example.noteapp.notification.AndroidAlarmScheduler
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideNoteDatabase(app: Application): NoteDatabase {
        return NoteDatabase.getInstance(app)
    }

    @Provides
    @Singleton
    fun provideNoteDao(db: NoteDatabase): NoteDao {
        return db.noteDao
    }

    @Provides
    @Singleton
    fun provideNoteRepository(app: Application, dao: NoteDao): NoteRepository {
        return NoteRepositoryImpl(app, dao)
    }

    @Provides
    @Singleton
    fun provideAlarmScheduler(app: Application): AlarmScheduler {
        return AndroidAlarmScheduler(app)
    }

    @Provides
    @Singleton
    fun provideAudioRecorder(app: Application): AudioRecorder {
        return AudioRecorder(app)
    }

    @Provides
    @Singleton
    fun provideAudioPlayer(app: Application): AudioPlayer {
        return AudioPlayer(app)
    }
}
