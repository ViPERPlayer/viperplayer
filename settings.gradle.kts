pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "ViPER Player"
include(":app")
include(":local")
include(":plugin-sdk")
include(":plugin-example")

// Media3
includeBuild("external/media") {
    dependencySubstitution {
        substitute(module("androidx.media3:media3-exoplayer")).using(project(":lib-exoplayer"))
        substitute(module("androidx.media3:media3-exoplayer-dash")).using(project(":lib-exoplayer-dash"))
        substitute(module("androidx.media3:media3-session")).using(project(":lib-session"))
        substitute(module("androidx.media3:media3-datasource")).using(project(":lib-datasource"))
        substitute(module("androidx.media3:media3-datasource-okhttp")).using(project(":lib-datasource-okhttp"))
        substitute(module("androidx.media3:media3-cast")).using(project(":lib-cast"))
    }
}
