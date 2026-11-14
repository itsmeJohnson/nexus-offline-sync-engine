-keepclassmembers class * {
    @kotlinx.serialization.Serializable <fields>;
}

-keep class com.nexus.sync.data.proto.** { *; }

-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Dao interface * { *; }

-keep class * extends dagger.hilt.internal.GeneratedComponent

-keep class * extends androidx.work.CoroutineWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}
