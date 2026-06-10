plugins {
    id("zero.kotlin.jvm")
    alias(libs.plugins.kotlin.serialization)
    // Standalone lint for a JVM module — version comes from the AGP already on the classpath.
    id("com.android.lint")
}

dependencies {
    implementation(project(":zero-api"))
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.datetime)

    lintChecks(project(":lint-rules"))

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
