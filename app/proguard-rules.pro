# Add project specific ProGuard rules here.

# Keep Hilt entry points
-keep class * extends dagger.hilt.android.HiltAndroidApp
-keep @dagger.hilt.android.AndroidEntryPoint class *

# Keep Room entities and DAOs
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-keep @androidx.room.Dao class *

# Keep kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class com.passmanager.**$$serializer { *; }
-keepclassmembers class com.passmanager.** {
    *** Companion;
}
-keepclasseswithmembers class com.passmanager.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Keep Argon2kt
-keep class com.lambdapioneer.argon2kt.** { *; }

# Keep crypto model classes
-keep class com.passmanager.crypto.** { *; }
-keep class com.passmanager.domain.model.** { *; }

# Prevent logging in release builds
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
}
