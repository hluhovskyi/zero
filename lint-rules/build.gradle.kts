plugins {
    id("zero.kotlin.jvm")
}

dependencies {
    implementation(libs.lint.api)
    implementation(libs.lint.checks)

    testImplementation(libs.lint.tests)
    testImplementation(libs.junit)
}
