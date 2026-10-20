pluginManagement {
    repositories { google(); mavenCentral(); gradlePluginPortal() }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories { google(); mavenCentral() }
    versionCatalogs { create("libs") { from(files("../gradle/libs.versions.toml")) } }
}
rootProject.name = "viper-portable-plugins"
include(":portable-plugin-core", ":portable-plugin-android")
project(":portable-plugin-core").projectDir = file("../portable-plugin-core")
project(":portable-plugin-android").projectDir = file("../portable-plugin-android")
