plugins {
    id("zero.android.library.compose")
}

android {
    namespace = "com.hluhovskyi.zero"

    defaultConfig {
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }

    lint {
        targetSdk = 32
    }
    testOptions {
        targetSdk = 32
    }
}

dependencies {
    lintChecks(project(":lint-rules"))

    implementation(project(":zero-api"))

    implementation(libs.androidx.compose.foundation)
    "perfImplementation"(libs.androidx.compose.runtime.tracing)
    implementation(libs.coil.compose)
}
