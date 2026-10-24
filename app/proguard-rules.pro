# SwiftShare ProGuard / R8 Rules

# Preserve Ktor CIO and Coroutines
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod
-dontwarn io.ktor.**
-keep class io.ktor.** { *; }

# Preserve Kotlinx Serialization
-keepattributes *Annotation*,InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class * {
    @kotlinx.serialization.Serializable <fields>;
}

# Preserve Room entities & DAOs
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**

# ZXing & ML Kit
-keep class com.google.zxing.** { *; }
-keep class com.google.mlkit.** { *; }
