plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(project(":backend:domain"))

    implementation(libs.arrow.core)
    implementation(libs.coroutines.core)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.cio)
    implementation(libs.exposed.core)
    implementation(libs.exposed.jdbc)
    implementation(libs.exposed.java.time)
    implementation(libs.postgresql)
    implementation(libs.koog.agents)
    implementation(libs.argon2.jvm)
    implementation(libs.java.jwt)

    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}
