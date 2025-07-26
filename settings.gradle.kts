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
        // No other repositories should be here unless you have a specific reason
    }
}
rootProject.name = "iOS Galaxy Watch Sync" // Or your app's name
include(":app")