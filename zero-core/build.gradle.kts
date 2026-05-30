plugins {
    id("zero.android.library.compose")
    alias(libs.plugins.ksp)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.hluhovskyi.zero"

    defaultConfig {
        consumerProguardFiles("consumer-rules.pro")
    }

    lint {
        abortOnError = true
        checkReleaseBuilds = false
    }
}

kotlin {
    compilerOptions {
        optIn.add("kotlinx.coroutines.ExperimentalCoroutinesApi")
        optIn.add("androidx.compose.foundation.ExperimentalFoundationApi")
        optIn.add("androidx.compose.animation.ExperimentalAnimationApi")
    }
}

dependencies {
    implementation(libs.kotlinx.datetime)
    implementation(libs.kotlinx.serialization.json)
    lintChecks(project(":lint-rules"))
    implementation(project(":zero-api"))
    implementation(project(":zero-sync"))

    implementation(project(":zero-ui"))
    implementation(project(":zero-image-loading"))

    implementation(libs.dagger.runtime)
    ksp(libs.dagger.compiler)

    implementation(libs.timber)

    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material)
    implementation(libs.androidx.compose.material3)
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
