plugins {
    id("zero.kotlin.jvm")
    alias(libs.plugins.kotlin.serialization)
    // Standalone lint for a JVM module — version comes from the AGP already on the classpath.
    id("com.android.lint")
}

dependencies {
    implementation(libs.kotlinx.datetime)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)

    lintChecks(project(":lint-rules"))
}
