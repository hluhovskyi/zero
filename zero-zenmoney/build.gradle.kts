plugins {
    `java-library`
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.ksp)
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(libs.kotlinx.datetime)
    implementation(project(":zero-api"))

    implementation(libs.kotlinx.coroutines.android)

    implementation(libs.dagger.runtime)
    ksp(libs.dagger.compiler)
}
