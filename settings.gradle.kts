pluginManagement {
    repositories {
        google()
        gradlePluginPortal()
        mavenCentral()
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
    }
}

rootProject.name = "gtu-ai-assistant"

include(
    ":backend:app",
    ":backend:presentation",
    ":backend:domain",
    ":backend:application",
    ":backend:infrastructure",
    ":shared:api-models"
)
