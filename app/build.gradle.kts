import java.io.File
import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.ksp)
    alias(libs.plugins.kotlin.compose)
}

val isPerfBuild = gradle.startParameter.taskNames.any { it.lowercase().contains("perf") }

val versionProps = Properties().apply {
    file("../version.properties").inputStream().use { load(it) }
}

val localProps = Properties().apply {
    val localPropsFile = rootProject.file("local.gradle.properties")
    if (localPropsFile.exists()) localPropsFile.inputStream().use { load(it) }
}

val releaseOutputDir: String? = System.getenv("RELEASE_OUTPUT_DIR") ?: localProps.getProperty("releaseOutputDir")
val bundleReleaseDir = layout.buildDirectory.dir("outputs/bundle/release")

tasks.configureEach {
    if (name == "bundleRelease") {
        doLast {
            if (releaseOutputDir != null) {
                val outputDir = bundleReleaseDir.get().asFile
                val destDir = File(releaseOutputDir)
                destDir.mkdirs()
                outputDir.listFiles()?.firstOrNull { it.name.endsWith(".aab") }?.let { aab ->
                    val versionCode = versionProps.getProperty("versionCode")
                    aab.copyTo(destDir.resolve("zero-$versionCode.aab"), overwrite = true)
                }
            }
        }
    }
}

android {
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.hluhovskyi.zero"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        versionCode = versionProps.getProperty("versionCode").toInt()
        versionName = versionProps.getProperty("versionName")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField(
            "String",
            "FEEDBACK_ENDPOINT",
            "\"${System.getenv("FEEDBACK_ENDPOINT") ?: localProps.getProperty("feedbackEndpoint") ?: ""}\"",
        )
        buildConfigField(
            "String",
            "FEEDBACK_INTEGRITY_PROJECT",
            "\"${System.getenv("FEEDBACK_INTEGRITY_PROJECT") ?: localProps.getProperty("feedbackIntegrityProject") ?: ""}\"",
        )
    }

    signingConfigs {
        create("release") {
            val keystorePath = System.getenv("KEYSTORE_FILE") ?: localProps.getProperty("keystoreFile")
            storeFile = keystorePath?.let { file(it) }
            storePassword = System.getenv("KEYSTORE_PASSWORD") ?: localProps.getProperty("keystorePassword") ?: ""
            keyAlias = System.getenv("KEY_ALIAS") ?: localProps.getProperty("keyAlias") ?: ""
            keyPassword = System.getenv("KEY_PASSWORD") ?: localProps.getProperty("keyPassword") ?: ""
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        create("perf") {
            initWith(getByName("release"))
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += "release"
            isProfileable = true
        }
    }
    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    namespace = "com.hluhovskyi.zero"

    testOptions {
        execution = "ANDROIDX_TEST_ORCHESTRATOR"
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
        optIn.add("kotlinx.serialization.ExperimentalSerializationApi")
        optIn.add("androidx.compose.material.ExperimentalMaterialApi")
    }
}

dependencies {
    lintChecks(project(":lint-rules"))

    implementation(libs.kotlinx.datetime)

    coreLibraryDesugaring(libs.desugar.jdk.libs)

    implementation(project(":zero-api"))
    implementation(project(":zero-ui"))
    implementation(project(":zero-database"))
    implementation(project(":zero-sync"))
    implementation(project(":zero-backup"))
    implementation(project(":zero-core"))
    implementation(project(":zero-crash"))
    implementation(project(":zero-image-loading"))
    implementation(project(":zero-remote"))

    implementation(libs.kotlinx.serialization.json)

    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.compose.materialNavigation)
    implementation(libs.androidx.compose.materialIconsExtended)

    implementation(libs.javax.inject)
    implementation(libs.dagger.runtime)
    ksp(libs.dagger.compiler)

    implementation(libs.timber)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime)
    implementation(libs.androidx.biometric)
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.androidx.compose.uiToolingPreview)
    "perfImplementation"(libs.androidx.compose.runtime.tracing)
    debugImplementation(libs.androidx.compose.uiTooling)

    implementation(project(":zero-test-bridge"))

    testImplementation(libs.junit)
    testImplementation(libs.mockito.kotlin)
    testImplementation(libs.kotlinx.coroutines.test)

    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.compose.uiTestJunit4)
    androidTestImplementation(libs.androidx.test.espresso.core)
    androidTestUtil(libs.androidx.test.orchestrator)
    debugImplementation(libs.androidx.compose.uiTestManifest)
}
