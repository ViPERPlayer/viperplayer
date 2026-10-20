plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    `java-library`
}

kotlin { jvmToolchain(17) }
group = "io.github.viperplayer"
version = "0.1.0-prototype"

dependencies {
    api(libs.kotlinx.coroutines.core)
    api(libs.kotlinx.serialization.json)
    // Android supplies the Android variant; never package desktop JNI libraries in an APK.
    compileOnly(libs.quickjs.jvm)
    testImplementation(libs.quickjs.jvm)
    testImplementation(libs.junit)
}
