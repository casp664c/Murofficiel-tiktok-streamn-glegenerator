pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // Only used for RootEncoder (RTMP camera streaming); no other group may come from JitPack.
        maven("https://jitpack.io") {
            content { includeGroupByRegex("com\\.github\\.pedroSG94.*") }
        }
    }
}
rootProject.name = "LiveKeyStudio"
include(":android-app")
