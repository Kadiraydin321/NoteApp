package com.example.noteapp.data.repository;

import com.example.noteapp.data.local.NoteDao;
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
public final class NoteRepositoryImpl_Factory implements Factory<NoteRepositoryImpl> {
  private final Provider<NoteDao> daoProvider;

  public NoteRepositoryImpl_Factory(Provider<NoteDao> daoProvider) {
    this.daoProvider = daoProvider;
  }

  @Override
  public NoteRepositoryImpl get() {
    return newInstance(daoProvider.get());
  }

  public static NoteRepositoryImpl_Factory create(Provider<NoteDao> daoProvider) {
    return new NoteRepositoryImpl_Factory(daoProvider);
  }

  public static NoteRepositoryImpl newInstance(NoteDao dao) {
    return new NoteRepositoryImpl(dao);
  }
}
