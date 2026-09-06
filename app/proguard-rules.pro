# Add project specific ProGuard rules here.
# Keep application data models and Room entities
-keep class com.example.data.** { *; }
-keep class com.example.data.db.** { *; }

# Keep Moshi and reflection models
-keepclassmembers class * {
    @com.squareup.moshi.Json *;
    @com.squareup.moshi.JsonClass *;
}
-dontwarn com.squareup.moshi.**

# Keep Room annotations and database classes
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**

# Keep Firebase App Check and Firestore
-keep class com.google.firebase.** { *; }
-dontwarn com.google.firebase.**

# Keep BCrypt and Security Engine
-keep class at.favre.lib.crypto.** { *; }
-keep class at.favre.lib.bytes.** { *; }
-keep class com.example.security.** { *; }

# Jetpack Compose and Kotlin Coroutines optimization
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod
-dontwarn kotlinx.coroutines.**

