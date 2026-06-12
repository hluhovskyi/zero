plugins {
    id("zero.android.library")
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.hluhovskyi.zero.auth"

    defaultConfig {
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }
}

kotlin {
    compilerOptions {
        optIn.add("kotlinx.coroutines.ExperimentalCoroutinesApi")
    }
}

dependencies {
    implementation(project(":zero-api"))

    implementation(libs.kotlinx.coroutines.android)

    implementation(libs.play.services.auth)
    implementation(libs.androidx.activity)

    implementation(libs.dagger.runtime)
    ksp(libs.dagger.compiler)

    implementation(libs.timber)

    lintChecks(project(":lint-rules"))

    testImplementation(libs.junit)
    testImplementation(libs.mockito.kotlin)
    testImplementation(libs.kotlinx.coroutines.test)
}
