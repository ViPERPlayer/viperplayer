import com.google.protobuf.gradle.id
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
    alias(libs.plugins.protobuf)
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

        // Last.fm scrobbling API credentials. These are PLACEHOLDERS — built-in scrobbling will not
        // function until a real API key + shared secret are supplied here (create one at
        // https://www.last.fm/api/account/create). Keep real values out of source control (e.g. read
        // them from a gitignored gradle property / env var). The placeholder is recognised at runtime
        // (see LastfmApi.PLACEHOLDER / isConfigured) so the UI can explain that setup is required.
        buildConfigField("String", "LASTFM_API_KEY", "\"REPLACE_WITH_REAL_VALUE\"")
        buildConfigField("String", "LASTFM_API_SECRET", "\"REPLACE_WITH_REAL_VALUE\"")

        // ViPER backend base URL — kept for any REST features (library sync scaffolding, etc.).
        // PLACEHOLDER — features that read it stay disabled until a real HTTPS URL is supplied.
        buildConfigField("String", "VIPER_BACKEND_URL", "\"REPLACE_WITH_REAL_VALUE\"")

        // ViPER backend gRPC endpoint (account sign-in + library sync; github.com/iscle/viper-backend).
        // The app talks gRPC/protobuf over HTTP/2. Format is "host:port" (e.g. "api.viper.player:9090").
        // PLACEHOLDER — account features stay disabled (AccountBackendConfig.isConfigured == false) until
        // a real host:port is supplied here. Transport defaults to TLS (prod); to target a dev h2c server
        // with no TLS, prefix with "plaintext://" (e.g. "plaintext://10.0.2.2:9090").
        buildConfigField("String", "VIPER_BACKEND_GRPC", "\"REPLACE_WITH_REAL_VALUE\"")

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
        // Toast/event messages are resolved via context.getString(...) inside LaunchedEffect+collect
        // coroutine lambdas (where the @Composable stringResource() cannot be called, and several are
        // parameterized by the emitted event) — a legitimate pattern this check false-flags.
        disable += "LocalContextGetResourceValueCall"
        // Generated gRPC/protobuf sources (build/generated/source/proto/**) are machine-generated and
        // not our code to fix; lint skips generated sources by default, but keep this explicit so the
        // codegen can never trip abortOnError with a NewApi/etc. warning we don't control.
        checkGeneratedSources = false
    }
}

// Export the Room schema (schemas land in app/schemas/) so migrations can be tracked and tested;
// pairs with having dropped fallbackToDestructiveMigration.
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

// gRPC/protobuf codegen for the ViPER account + library backend transport.
// The com.google.protobuf plugin auto-provisions the protoc compiler and the grpc-java/grpc-kotlin
// codegen plugins from Maven Central (no manual protoc install — works on CI). Protos are vendored
// under src/main/proto (source of truth: viper-backend). We generate the LITE runtime (javalite +
// kotlin-lite) which is required on Android (small method count, no full protobuf-java reflection).
protobuf {
    protoc {
        artifact = libs.protobuf.protoc.get().toString()
    }
    plugins {
        // grpc-java: generates the service stubs (…Grpc.java)
        id("grpc") {
            artifact = libs.grpc.protoc.gen.java.get().toString()
        }
        // grpc-kotlin: generates the coroutine stubs (…GrpcKt.kt)
        id("grpckt") {
            artifact = "${libs.grpc.protoc.gen.kotlin.get()}:jdk8@jar"
        }
    }
    generateProtoTasks {
        all().forEach { task ->
            // Emit the LITE Java + Kotlin message code…
            task.builtins {
                id("java") { option("lite") }
                id("kotlin") { option("lite") }
            }
            // …plus the gRPC service + coroutine stubs (also lite).
            task.plugins {
                id("grpc") { option("lite") }
                id("grpckt") { option("lite") }
            }
        }
    }
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

    // Built-in plugins (embedded in the app; discovered like any other plugin, just in-process)
    implementation(project(":local"))
    
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
    implementation(libs.kotlinx.coroutines.play.services)
    implementation(libs.kotlinx.serialization.json)

    // ML Kit — on-device lyrics translation (models download on demand; no API key)
    implementation(libs.mlkit.translate)
    implementation(libs.mlkit.language.id)

    // Reorderable — drag-to-reorder for the playlist edit-mode list
    implementation(libs.reorderable)

    // Ktor Client
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.ktor.serialization.json)

    // gRPC / protobuf — ViPER account + library backend transport (LITE runtime for Android).
    // grpc-okhttp is the Android-friendly HTTP/2 transport (minSdk 26 OK). protobuf-lite +
    // grpc-protobuf-lite keep the generated message/marshaller code small. The kotlin + stub
    // libraries back the generated coroutine stubs (…GrpcKt).
    implementation(libs.grpc.okhttp)
    implementation(libs.grpc.protobuf.lite)
    implementation(libs.grpc.stub)
    implementation(libs.grpc.kotlin.stub)
    implementation(libs.protobuf.kotlin.lite)
    // javax.annotation.Generated is referenced by grpc-java-generated stubs on Android.
    compileOnly(libs.javax.annotation.api)

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

    // CameraX — QR scanner for "Join a session" (decoded with the existing zxing.core)
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    
    // Testing
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
