import java.util.Properties

plugins {
    id("zero.android.library")
    alias(libs.plugins.ksp)
}

val localProps =
    Properties().apply {
        val localPropsFile = rootProject.file("local.gradle.properties")
        if (localPropsFile.exists()) localPropsFile.inputStream().use { load(it) }
    }

android {
    namespace = "com.hluhovskyi.zero.crash"

    defaultConfig {
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")

        buildConfigField(
            "String",
            "SENTRY_DSN",
            "\"${System.getenv("SENTRY_DSN") ?: localProps.getProperty("sentryDsn") ?: ""}\"",
        )
    }

    buildFeatures {
        buildConfig = true
    }
}

dependencies {
    lintChecks(project(":lint-rules"))

    implementation(project(":zero-api"))

    implementation(libs.dagger.runtime)
    ksp(libs.dagger.compiler)

    implementation(libs.javax.inject)

    implementation(libs.sentry.android)
}
