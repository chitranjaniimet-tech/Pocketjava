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
        // Official standalone R8 Maven repository. PocketJava embeds D8/R8 APIs
        // because Java bytecode must be converted to DEX on-device at runtime.
        maven("https://storage.googleapis.com/r8-releases/raw")
    }
}

rootProject.name = "PocketForge"
include(":app")
