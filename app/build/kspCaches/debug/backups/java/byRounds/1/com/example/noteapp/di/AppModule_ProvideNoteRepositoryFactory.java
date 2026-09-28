package com.example.noteapp.di;

import com.example.noteapp.data.local.NoteDao;
import com.example.noteapp.domain.repository.NoteRepository;
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
public final class AppModule_ProvideNoteRepositoryFactory implements Factory<NoteRepository> {
  private final Provider<NoteDao> daoProvider;

  public AppModule_ProvideNoteRepositoryFactory(Provider<NoteDao> daoProvider) {
    this.daoProvider = daoProvider;
  }

  @Override
  public NoteRepository get() {
    return provideNoteRepository(daoProvider.get());
  }

  public static AppModule_ProvideNoteRepositoryFactory create(Provider<NoteDao> daoProvider) {
    return new AppModule_ProvideNoteRepositoryFactory(daoProvider);
  }

  public static NoteRepository provideNoteRepository(NoteDao dao) {
    return Preconditions.checkNotNullFromProvides(AppModule.INSTANCE.provideNoteRepository(dao));
  }
}
