import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.serialization)
}
group = "io.github.viperplayer"
version = "0.1.0-prototype"

android {
    namespace = "com.viperplayer.portable.android"
    compileSdk { version = release(37) }
    defaultConfig { minSdk = 26 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlin { compilerOptions { jvmTarget = JvmTarget.JVM_17 } }
}

dependencies {
    api(project(":portable-plugin-core"))
    api(libs.viper.plugin.sdk)
    implementation(libs.quickjs)
}
