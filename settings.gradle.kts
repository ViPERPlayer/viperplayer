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
include(":plugin-sdk")
include(":plugin-example")

// Media3
(gradle as ExtensionAware).extra["androidxMediaModulePrefix"] = "media3-"
apply(from = file("external/media/core_settings.gradle"))
