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
    implementation(libs.pdfbox)
    implementation(libs.poi.ooxml)

    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}
