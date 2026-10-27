# Room Database & Entities
-keepclassmembers class * extends androidx.room.RoomDatabase {
    <init>(...);
}
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao interface * { *; }
-keep class * extends androidx.room.migration.Migration { *; }
-keep class com.example.noteapp.domain.model.** { *; }
-keep class com.example.noteapp.data.local.** { *; }
-dontwarn androidx.room.paging.**
-dontwarn androidx.room.**

# Hilt / Dagger
-keep class * extends dagger.hilt.android.internal.managers.ViewComponentManager
-dontwarn dagger.hilt.**

# Coroutines
-keepclassmembers class kotlinx.coroutines.** {
    *** *;
}
-keepclassmembernames class kotlinx.coroutines.internal.FastServiceLoader {
    private final java.util.List exceptions;
}
-keepclassmembers class kotlinx.coroutines.internal.MainDispatcherFactory {
    public *** createDispatcher(...);
}
-dontwarn kotlinx.coroutines.**

# Kotlinx Serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.SerializationKt
-keepclassmembers class * {
    @kotlinx.serialization.Serializable <fields>;
    @kotlinx.serialization.SerialName <fields>;
}
-keepclassmembers class * {
    public static final ** Companion;
}
-keepclassmembers class * {
    public static final ** INSTANCE;
}
-keepclasseswithmembers class * {
    kotlinx.serialization.KSerializer serializer(...);
}
-keepclassmembers class * implements kotlinx.serialization.KSerializer {
    <fields>;
    <init>(...);
}
-keepnames class kotlinx.serialization.internal.** { *; }
-dontwarn kotlinx.serialization.**

# Biometric
-keep class androidx.biometric.** { *; }
-dontwarn androidx.biometric.**

# Coil Image Loader
-keep class coil.** { *; }
-dontwarn coil.**

# ML Kit
-keep class com.google.mlkit.** { *; }
-dontwarn com.google.mlkit.**

# WorkManager
-keep class * extends androidx.work.Worker {
    <init>(...);
}
-keep class * extends androidx.work.ListenableWorker {
    <init>(...);
}
-dontwarn androidx.work.**
