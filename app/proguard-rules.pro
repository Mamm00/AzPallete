# Chapter 12.1: R8 rules for Jetpack Compose, Hilt, Room, Retrofit, OkHttp, Gson, and Serialization

# Preserve annotations & generic signatures for Room, Hilt, Retrofit, and Gson
-keepattributes *Annotation*
-keepattributes Signature,InnerClasses,EnclosingMethod

# Room entities and DAOs
-keepclassmembers class * {
    @androidx.room.* <fields>;
    @androidx.room.* <methods>;
}

# Domain / Serializable / EditStack / Snapshot models
-keep class com.alazhartex.shadecart.data.model.** { *; }
-keep class com.alazhartex.shadecart.data.repo.ShadeCartSnapshot { *; }
-keep class com.alazhartex.shadecart.data.repo.SwatchDraft { *; }

# Retrofit service interfaces & Gson DTOs (Chapters 8.1, 8.2, 10.2)
-keep class com.alazhartex.shadecart.data.remote.** { *; }
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}
-dontwarn retrofit2.Platform$Java8
-dontwarn kotlin.Unit

# OkHttp optional platform/TLS classes
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**
-dontwarn org.conscrypt.**
-dontwarn org.openjsse.**
-dontwarn org.bouncycastle.**

# Keep Material3 / Compose / WorkManager reflection hooks intact
-keepclassmembers class * extends androidx.work.CoroutineWorker {
    public <init>(...);
}
