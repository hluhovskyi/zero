plugins {
    `java-library`
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(libs.lint.api)
    implementation(libs.lint.checks)

    testImplementation(libs.lint.tests)
    testImplementation(libs.junit)
}
