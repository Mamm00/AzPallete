# Keep Room & Serialization classes intact
-keepattributes *Annotation*
-keepclassmembers class * {
    @androidx.room.* <fields>;
    @androidx.room.* <methods>;
}
-keep class com.alazhartex.shadecart.data.model.** { *; }
