import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.ksp)
    alias(libs.plugins.androidx.room)
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
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    namespace = "com.hluhovskyi.zero"
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
    room {
        schemaDirectory("$projectDir/schemas")
    }
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_21
        optIn.add("kotlinx.coroutines.ExperimentalCoroutinesApi")
    }
}

dependencies {
    implementation(libs.kotlinx.datetime)

    coreLibraryDesugaring(libs.desugar.jdk.libs)

    implementation(project(":zero-api"))

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.dagger.runtime)
    ksp(libs.dagger.compiler)

    testImplementation(libs.junit)
    testImplementation(libs.mockito.kotlin)
    testImplementation(libs.kotlinx.coroutines.test)
}
