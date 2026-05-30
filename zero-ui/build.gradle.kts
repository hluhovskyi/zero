plugins {
    id("zero.android.library.compose")
}

android {
    namespace = "com.hluhovskyi.zero"

    defaultConfig {
        minSdk = 21

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }
}

kotlin {
    compilerOptions {
        optIn.add("androidx.compose.material3.ExperimentalMaterial3Api")
    }
}

dependencies {
    lintChecks(project(":lint-rules"))

    implementation(libs.kotlinx.datetime)

    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.materialIconsExtended)
    "perfImplementation"(libs.androidx.compose.runtime.tracing)

    testImplementation(libs.junit)
}
