# Room
-keepclassmembers class * extends androidx.room.RoomDatabase {
    <init>(...);
}
-dontwarn androidx.room.paging.**

# Hilt / Dagger
-keep class * extends dagger.hilt.android.internal.managers.ViewComponentManager

# Coroutines
-keepclassmembers class kotlinx.coroutines.** {
    *** *;
}
