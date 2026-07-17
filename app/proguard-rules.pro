# ============================================================================
# ViPER Player — R8 keep rules
# ============================================================================
# Enabled with isMinifyEnabled=true on the release build. Note: a green release
# build proves the R8 *config* is valid; it does NOT prove the absence of
# over-stripping — a device smoke-test of the release APK is the final gate.

# ---- JNI native bridge -----------------------------------------------------
# The ~70 external methods are resolved from C++ by their mangled JVM name, so
# both the class name and the native method names must survive R8, or the
# System.loadLibrary("viper") bindings fail with UnsatisfiedLinkError.
-keepclasseswithmembernames,includedescriptorclasses class * {
    native <methods>;
}
-keep class com.viperplayer.data.player.ViperNativeDriver { *; }

# ---- kotlinx.serialization -------------------------------------------------
# Keep the compiler-generated $serializer classes and the serializer()/Companion
# accessors for every @Serializable type (all app models live under
# com.viperplayer.**), plus @Serializable fields and enum values().
-keepattributes *Annotation*, InnerClasses
-keepclassmembers class com.viperplayer.** { *** Companion; }
-keepclasseswithmembers class com.viperplayer.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.viperplayer.**$$serializer { *; }
-keepclassmembers @kotlinx.serialization.Serializable class com.viperplayer.** {
    <fields>;
}
-keepclassmembers enum com.viperplayer.** {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# ---- Parcelable (nav args, MediaItem domain models, AIDL marshalling) -------
-keepclassmembers class * implements android.os.Parcelable {
    public static final ** CREATOR;
}

# ---- Plugin SDK: the AIDL IPC boundary + its Parcelable models -------------
# plugin-sdk/consumer-rules.pro is empty, so the host app must keep the whole
# IPC surface itself; renaming/removing it breaks marshalling to the separate
# plugin APKs (testsource/testsource/local/othersource).
-keep class com.viperplayer.plugin.** { *; }

# ---- Room ------------------------------------------------------------------
-keep class * extends androidx.room.RoomDatabase { <init>(); }

# ---- Suppress warnings for optional transitive deps the app never links ----
# errorprone/javax.annotation are referenced by guava-style annotations pulled in transitively
# (e.g. via kotlinx-coroutines-guava); the classes aren't on the runtime path, so silence them.
-dontwarn com.google.errorprone.annotations.**
-dontwarn javax.annotation.**
-dontwarn kotlinx.atomicfu.**
-dontwarn io.netty.**
-dontwarn org.slf4j.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
-dontwarn org.xmlpull.**
-dontwarn java.lang.management.**
