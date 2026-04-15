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
        all {
            val req = requested
            if (
                req is ModuleComponentSelector &&
                req.group == "androidx.media3" &&
                req.module.startsWith("media3-")
            ) {
                if (req.module == "media3-exoplayer-midi") {
                    useTarget(project(":lib-decoder-midi"))
                } else {
                    useTarget(project(":${req.module.replaceFirst("media3-", "lib-")}"))
                }
            }
        }
    }
}
