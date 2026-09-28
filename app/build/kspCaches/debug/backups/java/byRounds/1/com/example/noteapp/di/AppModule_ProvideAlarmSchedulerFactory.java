package com.example.noteapp.di;

import android.app.Application;
import com.example.noteapp.notification.AlarmScheduler;
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
public final class AppModule_ProvideAlarmSchedulerFactory implements Factory<AlarmScheduler> {
  private final Provider<Application> appProvider;

  public AppModule_ProvideAlarmSchedulerFactory(Provider<Application> appProvider) {
    this.appProvider = appProvider;
  }

  @Override
  public AlarmScheduler get() {
    return provideAlarmScheduler(appProvider.get());
  }

  public static AppModule_ProvideAlarmSchedulerFactory create(Provider<Application> appProvider) {
    return new AppModule_ProvideAlarmSchedulerFactory(appProvider);
  }

  public static AlarmScheduler provideAlarmScheduler(Application app) {
    return Preconditions.checkNotNullFromProvides(AppModule.INSTANCE.provideAlarmScheduler(app));
  }
}
