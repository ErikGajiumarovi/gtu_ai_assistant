pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
    }
}

rootProject.name = "gtu-ai-assistant"

include(
    ":backend:app",
    ":backend:presentation",
    ":backend:domain",
    ":backend:application",
    ":backend:infrastructure"
)
