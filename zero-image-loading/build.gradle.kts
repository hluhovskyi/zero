import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
}

val isPerfBuild = gradle.startParameter.taskNames.any { it.lowercase().contains("perf") }

android {
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }
    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        create("perf") {
            initWith(getByName("release"))
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    buildFeatures {
        compose = true
    }
    namespace = "com.hluhovskyi.zero"
    lint {
        targetSdk = 32
    }
    testOptions {
        targetSdk = 32
    }
    composeCompiler {
        stabilityConfigurationFiles.add(rootProject.layout.projectDirectory.file("stable_config.conf"))
        if (isPerfBuild) {
            includeSourceInformation = true
        }
        if (isPerfBuild || project.hasProperty("composeReports")) {
            reportsDestination = layout.buildDirectory.dir("compose_compiler")
            metricsDestination = layout.buildDirectory.dir("compose_compiler")
        }
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

    implementation(libs.androidx.compose.foundation)
    "perfImplementation"(libs.androidx.compose.runtime.tracing)
    implementation(libs.coil.compose)
}
