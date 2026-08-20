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
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        // Aliyun mirrors Google Maven. The build environment cannot reach
        // dl.google.com directly (it returns 404 for every artifact), so the
        // mirror is needed to fetch androidx artifacts that are not already in
        // the local Gradle cache (e.g. androidx.core:core-telecom).
        maven("https://maven.aliyun.com/repository/google")
        mavenCentral()
    }
}

rootProject.name = "TP Call"
include(":app")
 