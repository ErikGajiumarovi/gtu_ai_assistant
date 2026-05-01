plugins {
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.serialization) apply false
}

allprojects {
    group = "com.gtu.aiassistant"
    version = "0.1.0-SNAPSHOT"
}
