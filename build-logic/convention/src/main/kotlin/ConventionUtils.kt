import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.getByType
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinAndroidProjectExtension

// Must stay `internal`: a public `Project.libs` shadows the generated `libs` accessor in consumer modules.
internal val Project.libs: VersionCatalog
    get() = extensions.getByType<VersionCatalogsExtension>().named("libs")

internal fun VersionCatalog.intVersion(name: String): Int = findVersion(name).get().requiredVersion.toInt()

// Enables Compose source-info (trace markers) for perf builds only. The compose extension is global,
// so requested task names are the signal (config-cache-safe).
internal val Project.isPerfBuild: Boolean
    get() = gradle.startParameter.taskNames.any { it.contains("perf", ignoreCase = true) }

internal fun Project.configureKotlinAndroid() {
    extensions.configure<KotlinAndroidProjectExtension> {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_21)
            // kotlinx.serialization is used across most modules; opt in once here.
            optIn.add("kotlinx.serialization.ExperimentalSerializationApi")
        }
    }
}

// Library + application get separate configurators: AGP 9's CommonExtension<*,*,*,*,*> doesn't
// expose compileOptions/lint/testOptions through star projections.
internal fun LibraryExtension.configureAndroidCommon(project: Project) {
    val libs = project.libs
    compileSdk = libs.intVersion("compileSdk")
    defaultConfig {
        minSdk = libs.intVersion("minSdk")
    }
    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    lint {
        targetSdk = libs.intVersion("targetSdk")
    }
    testOptions {
        targetSdk = libs.intVersion("targetSdk")
    }
    project.dependencies.add(
        "coreLibraryDesugaring",
        libs.findLibrary("desugar-jdk-libs").get(),
    )
}

internal fun ApplicationExtension.configureAndroidCommon(project: Project) {
    val libs = project.libs
    compileSdk = libs.intVersion("compileSdk")
    defaultConfig {
        minSdk = libs.intVersion("minSdk")
        // App targetSdk goes in defaultConfig (AGP rejects testOptions.targetSdk on a non-library module).
        targetSdk = libs.intVersion("targetSdk")
    }
    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    project.dependencies.add(
        "coreLibraryDesugaring",
        libs.findLibrary("desugar-jdk-libs").get(),
    )
}
