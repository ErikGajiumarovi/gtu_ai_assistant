plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(project(":backend:domain"))
    implementation(project(":backend:application"))

    implementation(libs.arrow.core)
    implementation(libs.ktor.server.core)
}
