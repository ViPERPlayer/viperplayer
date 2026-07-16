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

# ---- protobuf-lite: MessageSchema resolves generated message fields reflectively by name ----
# protobuf-lite's MessageSchema reads each generated message's instance fields by their
# exact source name at runtime (Class.getDeclaredField("id_") -> Unsafe.objectFieldOffset),
# so R8 must NOT rename or strip them. The <fields> keep below is the load-bearing rule.
-keepclassmembers class * extends com.google.protobuf.GeneratedMessageLite { <fields>; }
-keep class * extends com.google.protobuf.GeneratedMessageLite { *; }
-dontwarn com.google.protobuf.**

# ---- gRPC (generated stubs + runtime) --------------------------------------
# Generated service/coroutine stubs live in the proto packages (no java_package
# override on the vendored protos, so protoc uses the proto package directly).
-keep class io.grpc.** { *; }
-keep class account.v1.** { *; }
-keep class common.v1.** { *; }
-keep class library.v1.** { *; }
-dontwarn io.grpc.**
-dontwarn com.google.errorprone.annotations.**
-dontwarn javax.annotation.**

# ---- Suppress warnings for optional transitive deps the app never links ----
-dontwarn kotlinx.atomicfu.**
-dontwarn io.netty.**
-dontwarn org.slf4j.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
-dontwarn org.xmlpull.**
-dontwarn java.lang.management.**
