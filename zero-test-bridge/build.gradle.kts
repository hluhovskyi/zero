plugins {
    id("zero.android.library")
}

android {
    namespace = "com.hluhovskyi.zero.testbridge"
}

dependencies {
    lintChecks(project(":lint-rules"))

    implementation(libs.kotlinx.datetime)
    implementation(libs.kotlinx.coroutines.android)
    implementation(project(":zero-api"))
    implementation(project(":zero-database"))
}
