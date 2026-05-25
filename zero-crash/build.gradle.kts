import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.ksp)
}

val localProps =
    Properties().apply {
        val localPropsFile = rootProject.file("local.gradle.properties")
        if (localPropsFile.exists()) localPropsFile.inputStream().use { load(it) }
    }

android {
    compileSdk =
        libs.versions.compileSdk
            .get()
            .toInt()

    defaultConfig {
        minSdk =
            libs.versions.minSdk
                .get()
                .toInt()

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")

        buildConfigField(
            "String",
            "SENTRY_DSN",
            "\"${System.getenv("SENTRY_DSN") ?: localProps.getProperty("sentryDsn") ?: ""}\"",
        )
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    buildFeatures {
        buildConfig = true
    }
    namespace = "com.hluhovskyi.zero.crash"
    lint {
        targetSdk =
            libs.versions.targetSdk
                .get()
                .toInt()
    }
    testOptions {
        targetSdk =
            libs.versions.targetSdk
                .get()
                .toInt()
    }
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_21
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
