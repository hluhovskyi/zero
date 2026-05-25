plugins {
    `kotlin-dsl`
}

dependencies {
    compileOnly(libs.android.gradlePlugin)
    compileOnly(libs.kotlin.gradlePlugin)
    compileOnly(libs.compose.compiler.gradlePlugin)
}

gradlePlugin {
    plugins {
        register("zeroKotlinJvm") {
            id = "zero.kotlin.jvm"
            implementationClass = "ZeroKotlinJvmConventionPlugin"
        }
    }
}
