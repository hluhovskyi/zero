import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.ksp)
    alias(libs.plugins.kotlin.compose)
}

val isPerfBuild = gradle.startParameter.taskNames.any { it.lowercase().contains("perf") }

android {
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()

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
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    buildFeatures {
        compose = true
    }
    namespace = "com.hluhovskyi.zero"
    lint {
        targetSdk = libs.versions.targetSdk.get().toInt()
        abortOnError = true
        checkReleaseBuilds = false
    }
    testOptions {
        targetSdk = libs.versions.targetSdk.get().toInt()
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
        optIn.add("kotlinx.coroutines.ExperimentalCoroutinesApi")
        optIn.add("androidx.compose.foundation.ExperimentalFoundationApi")
        optIn.add("androidx.compose.animation.ExperimentalAnimationApi")
    }
}

dependencies {
    implementation(libs.kotlinx.datetime)
    coreLibraryDesugaring(libs.desugar.jdk.libs)
    lintChecks(project(":lint-rules"))
    implementation(project(":zero-api"))
    implementation(project(":zero-sync"))

    implementation(project(":zero-ui"))
    implementation(project(":zero-image-loading"))

    implementation(libs.dagger.runtime)
    ksp(libs.dagger.compiler)

    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material)
    implementation(libs.androidx.compose.materialIconsExtended)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    "perfImplementation"(libs.androidx.compose.runtime.tracing)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)

    testImplementation(libs.junit)
    testImplementation(libs.mockito.kotlin)
    testImplementation(libs.kotlinx.coroutines.test)
}
