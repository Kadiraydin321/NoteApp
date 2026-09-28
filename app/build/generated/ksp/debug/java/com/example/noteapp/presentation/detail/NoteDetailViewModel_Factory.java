package com.example.noteapp.presentation.detail;

import android.app.Application;
import androidx.lifecycle.SavedStateHandle;
import com.example.noteapp.domain.repository.NoteRepository;
import com.example.noteapp.media.AudioPlayer;
import com.example.noteapp.media.AudioRecorder;
import com.example.noteapp.notification.AlarmScheduler;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;
import javax.inject.Provider;

@ScopeMetadata
@QualifierMetadata
@DaggerGenerated
@Generated(
    value = "dagger.internal.codegen.ComponentProcessor",
    comments = "https://dagger.dev"
)
@SuppressWarnings({
    "unchecked",
    "rawtypes",
    "KotlinInternal",
    "KotlinInternalInJava",
    "cast"
})
public final class NoteDetailViewModel_Factory implements Factory<NoteDetailViewModel> {
  private final Provider<Application> appProvider;

  private final Provider<NoteRepository> repositoryProvider;

  private final Provider<AlarmScheduler> alarmSchedulerProvider;

  private final Provider<AudioRecorder> audioRecorderProvider;

  private final Provider<AudioPlayer> audioPlayerProvider;

  private final Provider<SavedStateHandle> savedStateHandleProvider;

  public NoteDetailViewModel_Factory(Provider<Application> appProvider,
      Provider<NoteRepository> repositoryProvider, Provider<AlarmScheduler> alarmSchedulerProvider,
      Provider<AudioRecorder> audioRecorderProvider, Provider<AudioPlayer> audioPlayerProvider,
      Provider<SavedStateHandle> savedStateHandleProvider) {
    this.appProvider = appProvider;
    this.repositoryProvider = repositoryProvider;
    this.alarmSchedulerProvider = alarmSchedulerProvider;
    this.audioRecorderProvider = audioRecorderProvider;
    this.audioPlayerProvider = audioPlayerProvider;
    this.savedStateHandleProvider = savedStateHandleProvider;
  }

  @Override
  public NoteDetailViewModel get() {
    return newInstance(appProvider.get(), repositoryProvider.get(), alarmSchedulerProvider.get(), audioRecorderProvider.get(), audioPlayerProvider.get(), savedStateHandleProvider.get());
  }

  public static NoteDetailViewModel_Factory create(Provider<Application> appProvider,
      Provider<NoteRepository> repositoryProvider, Provider<AlarmScheduler> alarmSchedulerProvider,
      Provider<AudioRecorder> audioRecorderProvider, Provider<AudioPlayer> audioPlayerProvider,
      Provider<SavedStateHandle> savedStateHandleProvider) {
    return new NoteDetailViewModel_Factory(appProvider, repositoryProvider, alarmSchedulerProvider, audioRecorderProvider, audioPlayerProvider, savedStateHandleProvider);
  }

  public static NoteDetailViewModel newInstance(Application app, NoteRepository repository,
      AlarmScheduler alarmScheduler, AudioRecorder audioRecorder, AudioPlayer audioPlayer,
      SavedStateHandle savedStateHandle) {
    return new NoteDetailViewModel(app, repository, alarmScheduler, audioRecorder, audioPlayer, savedStateHandle);
  }
}
