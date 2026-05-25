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

/**
 * The project's version catalog. The type-safe `libs` accessor is not generated for
 * included builds (gradle/gradle#15383), so convention plugins read the catalog through
 * this API instead.
 */
// MUST be internal: a public `Project.libs` extension leaks onto consumer modules'
// buildscript classpath and shadows the generated type-safe `libs` accessor, breaking
// `libs.*` library references in every module that applies a convention plugin.
internal val Project.libs: VersionCatalog
    get() = extensions.getByType<VersionCatalogsExtension>().named("libs")

internal fun VersionCatalog.intVersion(name: String): Int =
    findVersion(name).get().requiredVersion.toInt()

/** Pins the Kotlin JVM target for an Android module (AGP 9 built-in Kotlin). */
internal fun Project.configureKotlinAndroid() {
    extensions.configure<KotlinAndroidProjectExtension> {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_21)
        }
    }
}

/**
 * Common Android config shared by the library + compose conventions. AGP 9's
 * `CommonExtension<*,*,*,*,*>` doesn't expose `compileOptions`/`lint`/`testOptions` through
 * star projections, so the concrete extension types each get their own (identical) configurator.
 */
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
        // Application sets targetSdk in defaultConfig; libraries set it in lint/testOptions
        // (AGP rejects testOptions.targetSdk on a non-library module).
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
