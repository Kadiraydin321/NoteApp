package com.example.noteapp.di;

import android.app.Application;
import com.example.noteapp.media.AudioRecorder;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.Preconditions;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;
import javax.inject.Provider;

@ScopeMetadata("javax.inject.Singleton")
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
public final class AppModule_ProvideAudioRecorderFactory implements Factory<AudioRecorder> {
  private final Provider<Application> appProvider;

  public AppModule_ProvideAudioRecorderFactory(Provider<Application> appProvider) {
    this.appProvider = appProvider;
  }

  @Override
  public AudioRecorder get() {
    return provideAudioRecorder(appProvider.get());
  }

  public static AppModule_ProvideAudioRecorderFactory create(Provider<Application> appProvider) {
    return new AppModule_ProvideAudioRecorderFactory(appProvider);
  }

  public static AudioRecorder provideAudioRecorder(Application app) {
    return Preconditions.checkNotNullFromProvides(AppModule.INSTANCE.provideAudioRecorder(app));
  }
}
