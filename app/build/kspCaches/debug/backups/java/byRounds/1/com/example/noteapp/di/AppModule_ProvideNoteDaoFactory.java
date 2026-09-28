package com.example.noteapp.di;

import com.example.noteapp.data.local.NoteDao;
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
public final class AppModule_ProvideNoteDaoFactory implements Factory<NoteDao> {
  private final Provider<NoteDatabase> dbProvider;

  public AppModule_ProvideNoteDaoFactory(Provider<NoteDatabase> dbProvider) {
    this.dbProvider = dbProvider;
  }

  @Override
  public NoteDao get() {
    return provideNoteDao(dbProvider.get());
  }

  public static AppModule_ProvideNoteDaoFactory create(Provider<NoteDatabase> dbProvider) {
    return new AppModule_ProvideNoteDaoFactory(dbProvider);
  }

  public static NoteDao provideNoteDao(NoteDatabase db) {
    return Preconditions.checkNotNullFromProvides(AppModule.INSTANCE.provideNoteDao(db));
  }
}
