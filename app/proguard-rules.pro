# R8 rules for Jetpack Compose, Hilt, Room, Retrofit, OkHttp, Gson, Serialization, Glance & ZXing

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
-keep class com.alazhartex.shadecart.data.backup.** { *; }
-keep class com.alazhartex.shadecart.data.nearby.** { *; }
-keep class com.alazhartex.shadecart.data.repo.ShadeCartSnapshot { *; }
-keep class com.alazhartex.shadecart.data.repo.SwatchDraft { *; }
-keep class com.alazhartex.shadecart.domain.model.** { *; }
-keep class com.alazhartex.shadecart.domain.export.** { *; }
-keep class com.alazhartex.shadecart.data.sync.** { *; }
-keep class com.alazhartex.shadecart.ui.screens.edit.DetectedSwatchEditable** { *; }

# Retrofit service interfaces & Gson DTOs
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

# Keep Material3 / Compose / WorkManager / Glance reflection hooks intact
-keepclassmembers class * extends androidx.work.CoroutineWorker {
    public <init>(...);
}
-keepclassmembers class * extends androidx.glance.appwidget.GlanceAppWidgetReceiver {
    public <init>(...);
}
-dontwarn com.google.zxing.**
-dontwarn com.google.android.gms.**
-dontwarn com.google.mlkit.**
