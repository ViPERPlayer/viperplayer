import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
    id("kotlin-parcelize")
}

android {
    namespace = "com.viperplayer"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.viperplayer"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        externalNativeBuild {
            cmake {
                arguments += listOf("-DANDROID_SUPPORT_FLEXIBLE_PAGE_SIZES=ON")
            }
        }
    }

    signingConfigs {
        create("release") {
            val signingKeyStorePath = System.getenv("SIGNING_KEY_STORE_PATH")
            val signingKeyStorePassword = System.getenv("SIGNING_STORE_PASSWORD")
            val signingKeyAlias = System.getenv("SIGNING_KEY_ALIAS")
            val signingKeyPassword = System.getenv("SIGNING_KEY_PASSWORD")

            if (signingKeyStorePath != null && signingKeyStorePassword != null && signingKeyAlias != null && signingKeyPassword != null) {
                storeFile = file(signingKeyStorePath)
                storePassword = signingKeyStorePassword
                keyAlias = signingKeyAlias
                keyPassword = signingKeyPassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            // Sign only when the release keystore env vars are configured (see the "release"
            // signingConfig); otherwise produce an unsigned release APK so R8/minify can be built and
            // verified locally / in CI without the private keystore.
            signingConfigs.getByName("release").takeIf { it.storeFile != null }?.let {
                signingConfig = it
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        compilerOptions {
            jvmTarget = JvmTarget.JVM_17
            optIn.add("androidx.compose.material3.ExperimentalMaterial3Api")
            optIn.add("androidx.compose.material3.ExperimentalMaterial3ExpressiveApi")
            optIn.add("kotlinx.coroutines.ExperimentalCoroutinesApi")
            optIn.add("androidx.media3.common.util.UnstableApi")
            optIn.add("kotlinx.coroutines.FlowPreview")
        }
    }

    buildFeatures {
        buildConfig = true
        compose = true
        aidl = true
        prefab = true
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    lint {
        disable += "UnsafeOptInUsageError"
    }
}

// Export the Room schema (schemas land in app/schemas/) so migrations can be tracked and tested;
// pairs with having dropped fallbackToDestructiveMigration.
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

// Dagger/Hilt (>= 2.57) unshades kotlin-metadata-jvm, so its Java annotation processor reads class
// metadata via whatever version is on the classpath. Force it to match the Kotlin version, otherwise
// Hilt's aggregating Java compile can't parse metadata newer than the version Dagger ships with
// (e.g. "Provided Metadata instance has version 2.4.0, while maximum supported version is 2.3.0").
configurations.all {
    resolutionStrategy {
        force("org.jetbrains.kotlin:kotlin-metadata-jvm:${libs.versions.kotlin.get()}")
    }
}

dependencies {
    // Plugin SDK
    implementation(project(":plugin-sdk"))
    
    // Core Android
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.service)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.palette.ktx)
    implementation(libs.zxing.core)

    // Compose
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)
    
    // Kotlinx
    implementation(libs.kotlinx.coroutines.guava)
    implementation(libs.kotlinx.serialization.json)

    // Ktor Client
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.ktor.serialization.json)

    // Room
    implementation(libs.room.runtime)
    ksp(libs.room.compiler)
    implementation(libs.room.ktx)

    // Media3 / ExoPlayer
    implementation("androidx.media3:media3-exoplayer:1.10.0")
    implementation("androidx.media3:media3-exoplayer-dash:1.10.0")
    implementation("androidx.media3:media3-exoplayer-hls:1.10.0") // HLS playback (e.g. a plugin)
    implementation("androidx.media3:media3-session:1.10.0")
    implementation("androidx.media3:media3-datasource:1.10.0")
    implementation("androidx.media3:media3-datasource-okhttp:1.10.0")
    implementation("androidx.media3:media3-cast:1.10.0")

    // Image loading
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    
    // DataStore for preferences
    implementation(libs.androidx.datastore.preferences)
    
    // Hilt
    implementation(libs.hilt.android)
    implementation(libs.hilt.navigation.compose)
    ksp(libs.hilt.compiler)
    
    // Logging
    implementation(libs.timber)

    // Dynamic theme
    implementation(libs.material.kolor)

    // Rebugger
    implementation("io.github.theapache64:rebugger:1.0.1")

    // xDL
    implementation(libs.xdl)
    
    // Testing
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
