plugins {
    alias(libs.plugins.kotlin.jvm)
    application
}

kotlin {
    jvmToolchain(21)
}

application {
    mainClass.set("com.gtu.aiassistant.app.MainKt")
}

dependencies {
    implementation(project(":backend:domain"))
    implementation(project(":backend:application"))
    implementation(project(":backend:presentation"))
    implementation(project(":backend:infrastructure"))

    implementation(libs.arrow.core)
    implementation(libs.coroutines.core)
    implementation(libs.koin.core)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.cio)
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.netty)
}
