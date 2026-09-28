package com.example.noteapp.di;

import android.app.Application;
import com.example.noteapp.data.local.NoteDatabase;
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
public final class AppModule_ProvideNoteDatabaseFactory implements Factory<NoteDatabase> {
  private final Provider<Application> appProvider;

  public AppModule_ProvideNoteDatabaseFactory(Provider<Application> appProvider) {
    this.appProvider = appProvider;
  }

  @Override
  public NoteDatabase get() {
    return provideNoteDatabase(appProvider.get());
  }

  public static AppModule_ProvideNoteDatabaseFactory create(Provider<Application> appProvider) {
    return new AppModule_ProvideNoteDatabaseFactory(appProvider);
  }

  public static NoteDatabase provideNoteDatabase(Application app) {
    return Preconditions.checkNotNullFromProvides(AppModule.INSTANCE.provideNoteDatabase(app));
  }
}
