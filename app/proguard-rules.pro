# SwiftShare ProGuard / R8 Rules

-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod,SourceFile,LineNumberTable

# Ktor & Coroutines
-dontwarn io.ktor.**
-keep class io.ktor.** { *; }
-dontwarn kotlinx.coroutines.**
-dontwarn org.slf4j.**

# Kotlinx Serialization
-dontwarn kotlinx.serialization.**
-keepclassmembers class * {
    @kotlinx.serialization.Serializable <fields>;
}
-keep class * implements kotlinx.serialization.KSerializer {
    <init>(...);
}
-keepclassmembers class * {
    *** Companion;
}
-keepclasseswithmembers class * {
    kotlinx.serialization.KSerializer serializer(...);
}

# Room entities & DAOs
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-keep @androidx.room.Dao interface * { *; }
-keepclassmembers class * {
    @androidx.room.PrimaryKey <fields>;
    @androidx.room.ColumnInfo <fields>;
}
-dontwarn androidx.room.**

# ZXing & ML Kit Barcode Scanning
-keep class com.google.zxing.** { *; }
-keep class com.google.mlkit.vision.barcode.** { *; }
-keep class com.google.mlkit.vision.common.** { *; }
-keep class com.google.android.gms.vision.** { *; }
-dontwarn com.google.mlkit.**

# CameraX
-keep class androidx.camera.core.** { *; }
-dontwarn androidx.camera.core.**
-keep class androidx.camera.lifecycle.** { *; }
-keep class androidx.camera.view.** { *; }
-keep class androidx.camera.camera2.** { *; }

# Compose runtime
-keep class androidx.compose.runtime.** { *; }
