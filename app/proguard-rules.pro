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

# Keep Argon2kt - native JNI bridge, the library resolves these classes/methods from C code.
-keep class com.lambdapioneer.argon2kt.** { *; }

# com.passmanager.crypto.** and com.passmanager.domain.model.** used to be kept wholesale.
# That handed an attacker a fully labelled map of the security-critical layer (EncryptedChannel,
# AesGcmCipher, X25519KeyExchange, Argon2KdfProvider, ...) inside an otherwise obfuscated APK.
# Nothing in those packages is resolved by name: instances come from constructors and Hilt
# bindings, and the @Serializable carriers (crypto.model.KdfParams, domain.model.ItemPayload and
# its subclasses) survive obfuscation through the kotlinx.serialization rules above - the
# generated $$serializer classes plus the `Companion` / `serializer(...)` members are kept, and
# @SerialName discriminators are string literals that R8 never rewrites.
# The two exceptions below are enums whose constant names are persisted outside the APK.

# ItemCategory.name / .dbKey is written to the Room `category` column, to the DataStore group
# filter and to the desktop wire protocol, then resolved back by ItemCategory.fromString.
-keepclassmembers enum com.passmanager.domain.model.ItemCategory {
    <fields>;
}

# VaultSortOrder.name is persisted in DataStore (AppPreferences.vaultListSort) and compared by name.
-keepclassmembers enum com.passmanager.domain.model.VaultSortOrder {
    <fields>;
}

# Prevent logging in release builds. `w` is stripped as well: warning text in this app carries
# pairing/vault diagnostics that must not reach logcat on a user's device.
# `e` is NOT stripped: it is the one level R8 must leave callable so a crash can still be traced.
# In practice release builds stay quiet anyway - AppLogger.e/.w return early on !BuildConfig.DEBUG,
# so the only surviving call sites are the few that use android.util.Log directly.
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
    public static *** w(...);
}
