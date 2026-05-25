import com.android.build.api.dsl.ApplicationExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.jetbrains.kotlin.compose.compiler.gradle.ComposeCompilerGradlePluginExtension

class ZeroAndroidApplicationConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        with(pluginManager) {
            apply("com.android.application")
            apply("org.jetbrains.kotlin.plugin.compose")
        }

        val isPerfBuild = gradle.startParameter.taskNames.any { it.lowercase().contains("perf") }

        extensions.configure<ApplicationExtension> {
            configureAndroidCommon(target)
            buildFeatures {
                compose = true
                buildConfig = true
            }
        }

        extensions.configure<ComposeCompilerGradlePluginExtension> {
            stabilityConfigurationFiles.add(rootProject.layout.projectDirectory.file("stable_config.conf"))
            if (isPerfBuild) {
                includeSourceInformation.set(true)
            }
            if (isPerfBuild || hasProperty("composeReports")) {
                reportsDestination.set(layout.buildDirectory.dir("compose_compiler"))
                metricsDestination.set(layout.buildDirectory.dir("compose_compiler"))
            }
        }

        configureKotlinAndroid()
    }
}
