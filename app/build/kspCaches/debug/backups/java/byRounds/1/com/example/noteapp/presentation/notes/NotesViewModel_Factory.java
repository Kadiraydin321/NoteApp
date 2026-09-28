package com.example.noteapp.presentation.notes;

import com.example.noteapp.domain.repository.NoteRepository;
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
public final class NotesViewModel_Factory implements Factory<NotesViewModel> {
  private final Provider<NoteRepository> repositoryProvider;

  public NotesViewModel_Factory(Provider<NoteRepository> repositoryProvider) {
    this.repositoryProvider = repositoryProvider;
  }

  @Override
  public NotesViewModel get() {
    return newInstance(repositoryProvider.get());
  }

  public static NotesViewModel_Factory create(Provider<NoteRepository> repositoryProvider) {
    return new NotesViewModel_Factory(repositoryProvider);
  }

  public static NotesViewModel newInstance(NoteRepository repository) {
    return new NotesViewModel(repository);
  }
}
