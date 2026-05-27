import java.util.Properties

plugins {
    id("zero.android.library")
    alias(libs.plugins.ksp)
    alias(libs.plugins.kotlin.serialization)
}

val localProps =
    Properties().apply {
        val localPropsFile = rootProject.file("local.gradle.properties")
        if (localPropsFile.exists()) localPropsFile.inputStream().use { load(it) }
    }

android {
    namespace = "com.hluhovskyi.zero.auth"

    defaultConfig {
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")

        buildConfigField(
            "String",
            "DRIVE_OAUTH_CLIENT_ID",
            "\"${System.getenv("DRIVE_OAUTH_CLIENT_ID") ?: localProps.getProperty("driveOauthClientId") ?: ""}\"",
        )
    }

    buildFeatures {
        buildConfig = true
    }
}

kotlin {
    compilerOptions {
        optIn.add("kotlinx.coroutines.ExperimentalCoroutinesApi")
        optIn.add("kotlinx.serialization.ExperimentalSerializationApi")
    }
}

dependencies {
    implementation(project(":zero-api"))

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)

    implementation(libs.androidx.credentials)
    runtimeOnly(libs.androidx.credentials.playServicesAuth)
    implementation(libs.googleid)

    implementation(libs.dagger.runtime)
    ksp(libs.dagger.compiler)

    implementation(libs.timber)

    testImplementation(libs.junit)
    testImplementation(libs.mockito.kotlin)
    testImplementation(libs.kotlinx.coroutines.test)
}
