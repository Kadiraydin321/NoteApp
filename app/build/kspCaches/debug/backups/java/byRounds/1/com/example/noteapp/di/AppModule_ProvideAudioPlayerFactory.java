package com.example.noteapp.di;

import android.app.Application;
import com.example.noteapp.media.AudioPlayer;
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
public final class AppModule_ProvideAudioPlayerFactory implements Factory<AudioPlayer> {
  private final Provider<Application> appProvider;

  public AppModule_ProvideAudioPlayerFactory(Provider<Application> appProvider) {
    this.appProvider = appProvider;
  }

  @Override
  public AudioPlayer get() {
    return provideAudioPlayer(appProvider.get());
  }

  public static AppModule_ProvideAudioPlayerFactory create(Provider<Application> appProvider) {
    return new AppModule_ProvideAudioPlayerFactory(appProvider);
  }

  public static AudioPlayer provideAudioPlayer(Application app) {
    return Preconditions.checkNotNullFromProvides(AppModule.INSTANCE.provideAudioPlayer(app));
  }
}
