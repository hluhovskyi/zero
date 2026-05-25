# build-logic Convention Plugins Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Move the repeated per-module Gradle KTS config into precompiled convention plugins in a `build-logic` included build, slimming every module to plugin-apply + namespace + module-specific plugins + dependencies.

**Architecture:** `build-logic` is a standalone included build (`pluginManagement { includeBuild("build-logic") }`) whose `:convention` module produces precompiled script plugins (`zero.kotlin.jvm`, `zero.android.library`, `zero.android.library.compose`, `zero.android.application`). Catalog values are read inside the plugins via `VersionCatalogsExtension` (the type-safe `libs` accessor isn't available to included builds — gradle/gradle#15383). Desugaring is normalized ON across all Android modules. Reference: docs/superpowers/specs/2026-05-24-build-logic-convention-plugins-design.md.

**Tech Stack:** Gradle 9.3.1, AGP 9.1.0 (built-in Kotlin), Kotlin 2.3.20, `kotlin-dsl`, version catalog.

> **Two AGP-9-built-in-Kotlin uncertainties to resolve by compiling, not guessing** (flagged at each task): (a) whether the `kotlin {}` DSL accessor is generated inside a precompiled script that only applies `com.android.library`; (b) the exact member access for `lint.targetSdk` / `testOptions.targetSdk` on `CommonExtension`. Each Android task ends with a real build so these surface immediately. The validated module DSL (already on master) is the source of truth for the blocks.

---

## Task 1: Catalog — plugin artifacts for the build-logic classpath

**Files:** Modify `gradle/libs.versions.toml`

- [ ] **Step 1: Add three plugin-artifact libraries** under `[libraries]` (KSP isn't needed — no convention applies it):

```toml
android-gradlePlugin = { module = "com.android.tools.build:gradle", version.ref = "agp" }
kotlin-gradlePlugin = { module = "org.jetbrains.kotlin:kotlin-gradle-plugin", version.ref = "kotlin" }
compose-compiler-gradlePlugin = { module = "org.jetbrains.kotlin:compose-compiler-gradle-plugin", version.ref = "kotlin" }
```

- [ ] **Step 2: Verify the catalog still parses** — `./gradlew help -q` → BUILD SUCCESSFUL.
- [ ] **Step 3: Commit** — `git add gradle/libs.versions.toml` then `git commit -m "build: add gradle-plugin artifacts to catalog for build-logic"`.

---

## Task 2: Scaffold build-logic + the `zero.kotlin.jvm` convention (proves the plumbing)

JVM-only first: no AGP API uncertainty, so a green result here means the whole included-build + catalog-access mechanism works.

**Files:**
- Create `build-logic/settings.gradle.kts`
- Create `build-logic/convention/build.gradle.kts`
- Create `build-logic/convention/src/main/kotlin/ConventionUtils.kt`
- Create `build-logic/convention/src/main/kotlin/zero.kotlin.jvm.gradle.kts`
- Modify root `settings.gradle.kts` (add `includeBuild`)
- Create `build-logic/.gitignore` (`/build`, `/convention/build`)

- [ ] **Step 1: `build-logic/settings.gradle.kts`**
```kotlin
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
    versionCatalogs {
        create("libs") {
            from(files("../gradle/libs.versions.toml"))
        }
    }
}
rootProject.name = "build-logic"
include(":convention")
```

- [ ] **Step 2: `build-logic/convention/build.gradle.kts`** (build-logic's OWN scripts DO get the `libs` accessor — only the precompiled plugins don't):
```kotlin
plugins {
    `kotlin-dsl`
}

dependencies {
    compileOnly(libs.android.gradlePlugin)
    compileOnly(libs.kotlin.gradlePlugin)
    compileOnly(libs.compose.compiler.gradlePlugin)
}
```

- [ ] **Step 3: `build-logic/convention/src/main/kotlin/ConventionUtils.kt`** — shared catalog + android helpers:
```kotlin
import com.android.build.api.dsl.CommonExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.getByType

val Project.libs: VersionCatalog
    get() = extensions.getByType<VersionCatalogsExtension>().named("libs")

fun VersionCatalog.intVersion(name: String): Int =
    findVersion(name).get().requiredVersion.toInt()

/**
 * Common Android config shared by the library + application conventions.
 * Member access (lint.targetSdk / testOptions.targetSdk) mirrors the validated
 * module build files on master — if a member doesn't resolve on CommonExtension,
 * check the exact DSL against `git show HEAD:zero-database/build.gradle.kts`.
 */
fun CommonExtension<*, *, *, *, *>.configureAndroidCommon(project: Project) {
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
```

- [ ] **Step 4: `build-logic/convention/src/main/kotlin/zero.kotlin.jvm.gradle.kts`**
```kotlin
plugins {
    id("org.jetbrains.kotlin.jvm")
    `java-library`
}

kotlin {
    jvmToolchain(21)
}
```

- [ ] **Step 5: Wire the included build into root `settings.gradle.kts`** — add `includeBuild("build-logic")` as the first line inside `pluginManagement { }`, before `repositories`:
```kotlin
pluginManagement {
    includeBuild("build-logic")
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
    }
}
```

- [ ] **Step 6: `build-logic/.gitignore`** — two lines: `/build` and `/convention/build`.

- [ ] **Step 7: Smoke-test by converting ONE JVM module.** Replace `zero-api/build.gradle.kts` plugins+kotlin blocks so it reads:
```kotlin
plugins {
    id("zero.kotlin.jvm")
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    implementation(libs.kotlinx.datetime)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
}
```

- [ ] **Step 8: Verify the whole plumbing compiles** — `./gradlew :zero-api:compileKotlin 2>&1 | tail -20`. Expected: BUILD SUCCESSFUL (this forces build-logic to compile and applies `zero.kotlin.jvm`). If build-logic fails to compile, fix here before touching anything else.
- [ ] **Step 9: Commit** — `git add -A` then `git commit -m "build: scaffold build-logic + zero.kotlin.jvm convention"`.

---

## Task 3: Apply `zero.kotlin.jvm` to remaining JVM modules

**Files:** `zero-sync`, `zero-backup`, `zero-zenmoney`, `lint-rules` `build.gradle.kts`

- [ ] **Step 1: Slim each module.** Replace the `plugins {}` + `kotlin { jvmToolchain(21) }` blocks: drop `kotlin.jvm`, `java-library`, and the `kotlin {}` block; add `id("zero.kotlin.jvm")` as the first plugin. Keep module-specific plugins (`alias(libs.plugins.kotlin.serialization)` in sync/backup; `alias(libs.plugins.ksp)` in zenmoney) and the entire `dependencies {}` block unchanged. `lint-rules` keeps only `id("zero.kotlin.jvm")`. Example — `zero-sync/build.gradle.kts`:
```kotlin
plugins {
    id("zero.kotlin.jvm")
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    implementation(project(":zero-api"))
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.datetime)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
```

- [ ] **Step 2: Verify** — `./gradlew :zero-sync:compileKotlin :zero-backup:compileKotlin :zero-zenmoney:compileKotlin :lint-rules:compileKotlin 2>&1 | tail -15` → BUILD SUCCESSFUL.
- [ ] **Step 3: Commit** — `git add -A` then `git commit -m "build: apply zero.kotlin.jvm to JVM modules"`.

---

## Task 4: `zero.android.library` convention + simple Android libs

**Files:**
- Create `build-logic/convention/src/main/kotlin/zero.android.library.gradle.kts`
- Modify `zero-database`, `zero-remote`, `zero-crash`, `zero-test-bridge` `build.gradle.kts`

- [ ] **Step 1: `zero.android.library.gradle.kts`**
```kotlin
import com.android.build.api.dsl.LibraryExtension
import org.gradle.kotlin.dsl.configure
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.library")
}

extensions.configure<LibraryExtension> {
    configureAndroidCommon(project)
    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_21
    }
}
```
> **AGP-built-in-Kotlin check:** if the top-level `kotlin { }` accessor does NOT resolve in this precompiled script, replace that block with `extensions.configure<org.jetbrains.kotlin.gradle.dsl.KotlinAndroidProjectExtension> { compilerOptions { jvmTarget.set(JvmTarget.JVM_21) } }`. Confirm which compiles in Step 3; do not guess.

- [ ] **Step 2: Slim the four modules.** Each: drop the `android {}` lines now in the convention (compileSdk, minSdk, compileOptions, lint, testOptions, the release buildType) and the `coreLibraryDesugaring` dependency; apply `id("zero.android.library")`; keep module-specific plugins, `namespace`, module extras, and deps. Per module:
  - **zero-database** — `id("zero.android.library")` + `alias(libs.plugins.ksp)` + `alias(libs.plugins.androidx.room)`; keep `android { namespace = "com.hluhovskyi.zero"; room { schemaDirectory("$projectDir/schemas") } }`; keep its `kotlin { compilerOptions { optIn.add("kotlinx.coroutines.ExperimentalCoroutinesApi") } }` (module opt-in — additive to the convention's jvmTarget); keep deps (drop the `coreLibraryDesugaring` line).
  - **zero-remote** — `+ ksp + kotlin.serialization`; `namespace = "com.hluhovskyi.zero.remote"`; keep its two `optIn`s; deps minus desugar line.
  - **zero-crash** — `+ ksp`; keep the `Properties` localProps loader, `android { namespace = "com.hluhovskyi.zero.crash"; defaultConfig { buildConfigField(... SENTRY_DSN ...) }; buildFeatures { buildConfig = true } }`; no `kotlin {}` opt-in block needed (convention sets jvmTarget); deps unchanged (crash had no desugar line — it now gains desugaring via the convention, per the normalization decision).
  - **zero-test-bridge** — only `id("zero.android.library")`; `android { namespace = "com.hluhovskyi.zero.testbridge" }`; deps minus desugar line. (Gains lint/testOptions targetSdk + release buildType + desugar from the convention — all harmless.)

  Example — `zero-database/build.gradle.kts`:
```kotlin
plugins {
    id("zero.android.library")
    alias(libs.plugins.ksp)
    alias(libs.plugins.androidx.room)
}

android {
    namespace = "com.hluhovskyi.zero"
    defaultConfig {
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }
    room {
        schemaDirectory("$projectDir/schemas")
    }
}

kotlin {
    compilerOptions {
        optIn.add("kotlinx.coroutines.ExperimentalCoroutinesApi")
    }
}

dependencies {
    implementation(libs.kotlinx.datetime)
    implementation(project(":zero-api"))
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.dagger.runtime)
    ksp(libs.dagger.compiler)
    testImplementation(libs.junit)
    testImplementation(libs.mockito.kotlin)
    testImplementation(libs.kotlinx.coroutines.test)
}
```
> `testInstrumentationRunner` + `consumerProguardFiles` are per-module defaultConfig settings (not in the convention) — keep them in each module that had them. zero-test-bridge had neither.

- [ ] **Step 3: Verify** — `./gradlew :zero-database:assembleDebug :zero-remote:assembleDebug :zero-crash:assembleDebug :zero-test-bridge:assembleDebug 2>&1 | tail -20` → BUILD SUCCESSFUL. Resolve the kotlin-accessor question (Step 1 note) here.
- [ ] **Step 4: Commit** — `git add -A` then `git commit -m "build: add zero.android.library convention + apply to simple libs"`.

---

## Task 5: `zero.android.library.compose` convention + compose libs

**Files:**
- Create `build-logic/convention/src/main/kotlin/zero.android.library.compose.gradle.kts`
- Modify `zero-ui`, `zero-image-loading`, `zero-core` `build.gradle.kts`

- [ ] **Step 1: `zero.android.library.compose.gradle.kts`** — applies the library plugin + compose, shares `configureAndroidCommon`, adds compose + perf build type + composeCompiler:
```kotlin
import com.android.build.api.dsl.LibraryExtension
import org.gradle.kotlin.dsl.configure
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.plugin.compose")
}

val isPerfBuild = gradle.startParameter.taskNames.any { it.lowercase().contains("perf") }

extensions.configure<LibraryExtension> {
    configureAndroidCommon(project)
    buildFeatures {
        compose = true
    }
    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        create("perf") {
            initWith(getByName("release"))
        }
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
    }
}
```
> Use the same kotlin-accessor resolution settled in Task 4. `composeCompiler {}` resolves because `compose-compiler-gradlePlugin` is on the build-logic classpath and the plugin is applied here.

- [ ] **Step 2: Slim the three modules.** Apply `id("zero.android.library.compose")` + module plugins; keep `namespace`, module `optIn`s, deviation overrides, deps (minus desugar/compose-plugin/buildType/composeCompiler lines now in the convention).
  - **zero-ui** — `id("zero.android.library.compose")`; `android { namespace = "com.hluhovskyi.zero"; defaultConfig { minSdk = 21; testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"; consumerProguardFiles("consumer-rules.pro") } }` (the `minSdk = 21` override comes AFTER the convention's catalog default — last-write-wins); `kotlin { compilerOptions { optIn.add("androidx.compose.material.ExperimentalMaterialApi") } }`; deps incl. `"perfImplementation"(libs.androidx.compose.runtime.tracing)`.
  - **zero-image-loading** — `id("zero.android.library.compose")`; `android { namespace = "com.hluhovskyi.zero"; defaultConfig { testInstrumentationRunner = ...; consumerProguardFiles(...) }; lint { targetSdk = 32 }; testOptions { targetSdk = 32 } }` (the `32` overrides the convention default); no module `kotlin {}` block (had none); deps. (Now gains desugaring via convention — accepted.)
  - **zero-core** — `id("zero.android.library.compose")` + `alias(libs.plugins.ksp)`; `android { namespace = "com.hluhovskyi.zero"; defaultConfig { consumerProguardFiles(...) }; lint { abortOnError = true; checkReleaseBuilds = false } }` (these extra lint flags layer on top of the convention's `lint { targetSdk }`); `kotlin { compilerOptions { optIn.add("kotlinx.coroutines.ExperimentalCoroutinesApi"); optIn.add("androidx.compose.foundation.ExperimentalFoundationApi"); optIn.add("androidx.compose.animation.ExperimentalAnimationApi") } }`; full deps.

- [ ] **Step 3: Verify** — `./gradlew :zero-ui:assembleDebug :zero-image-loading:assembleDebug :zero-core:assembleDebug 2>&1 | tail -20` → BUILD SUCCESSFUL. Confirm the `minSdk = 21` / `targetSdk = 32` overrides take effect (no error, builds clean).
- [ ] **Step 4: Commit** — `git add -A` then `git commit -m "build: add zero.android.library.compose convention + apply to compose libs"`.

---

## Task 6: `zero.android.application` convention + app

The app's `android {}` has unique parts (signing, version props, AAB-copy, buildConfig fields). The convention bundles only the *common* Android config + compose + perf; everything app-specific stays in `app/build.gradle.kts`.

**Files:**
- Create `build-logic/convention/src/main/kotlin/zero.android.application.gradle.kts`
- Modify `app/build.gradle.kts`

- [ ] **Step 1: `zero.android.application.gradle.kts`** — mirror the compose convention but for `ApplicationExtension`, common config only:
```kotlin
import com.android.build.api.dsl.ApplicationExtension
import org.gradle.kotlin.dsl.configure
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

val isPerfBuild = gradle.startParameter.taskNames.any { it.lowercase().contains("perf") }

extensions.configure<ApplicationExtension> {
    configureAndroidCommon(project)
    buildFeatures {
        compose = true
        buildConfig = true
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
    }
}
```
> `configureAndroidCommon` sets `lint`/`testOptions.targetSdk` for the app too (the app didn't set them before — harmless additions, same catalog value as `targetSdk` already used in `defaultConfig`).

- [ ] **Step 2: Slim `app/build.gradle.kts`.** Apply `id("zero.android.application")` + `alias(libs.plugins.ksp)`. **Remove** from the module: `compileSdk`, `defaultConfig.minSdk`/`targetSdk` (now from convention — but KEEP `applicationId`, `versionCode`, `versionName`, `testInstrumentationRunner`, `buildConfigField`s), `compileOptions`, `buildFeatures`, `composeCompiler`, the top-level `kotlin {}` block (convention sets jvmTarget; app's opt-ins move into a kept `kotlin { compilerOptions { optIn... } }` block). **Keep** in the module: imports, `isPerfBuild` is NOT needed (convention handles composeCompiler), `versionProps`/`localProps` loaders, the `bundleRelease` AAB-copy `tasks.configureEach` block, `signingConfigs`, the full `buildTypes { release { signingConfig... }; create("perf") { ... isProfileable ... } }` (app's perf type has signing/profileable specifics — keep in module), `packaging`, `namespace`, `testOptions { execution = "ANDROIDX_TEST_ORCHESTRATOR" }`, the `kotlin { compilerOptions { optIn... } }` opt-ins, and all `dependencies`.

  Resulting `app/build.gradle.kts` plugins + kotlin blocks:
```kotlin
plugins {
    id("zero.android.application")
    alias(libs.plugins.ksp)
}
// ... versionProps / localProps / bundleRelease task unchanged ...
android {
    defaultConfig {
        applicationId = "com.hluhovskyi.zero"
        versionCode = versionProps.getProperty("versionCode").toInt()
        versionName = versionProps.getProperty("versionName")
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "FEEDBACK_ENDPOINT", /* unchanged */ "")
        buildConfigField("String", "FEEDBACK_INTEGRITY_PROJECT", /* unchanged */ "")
    }
    signingConfigs { /* unchanged release block */ }
    buildTypes {
        release { signingConfig = signingConfigs.getByName("release"); isMinifyEnabled = false; proguardFiles(...) }
        create("perf") { initWith(getByName("release")); signingConfig = signingConfigs.getByName("debug"); matchingFallbacks += "release"; isProfileable = true }
    }
    packaging { resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" } }
    namespace = "com.hluhovskyi.zero"
    testOptions { execution = "ANDROIDX_TEST_ORCHESTRATOR" }
}
kotlin {
    compilerOptions {
        optIn.add("kotlinx.coroutines.ExperimentalCoroutinesApi")
        optIn.add("kotlinx.serialization.ExperimentalSerializationApi")
        optIn.add("androidx.compose.material.ExperimentalMaterialApi")
    }
}
// dependencies { } unchanged
```
> The app keeps its own `buildTypes` (release+perf) because they carry signing/`isProfileable` specifics the convention doesn't model. The convention's `compose`/`buildConfig`/`composeCompiler`/sdk/java/desugar/jvmTarget still apply. Keep the exact `buildConfigField` string interpolations from the current `app/build.gradle.kts` (copy them verbatim).

- [ ] **Step 3: Verify** — `./gradlew :app:assembleDebug 2>&1 | tail -20` → BUILD SUCCESSFUL.
- [ ] **Step 4: Commit** — `git add -A` then `git commit -m "build: add zero.android.application convention + apply to app"`.

---

## Task 7: Spotless config, full verification matrix, docs

- [ ] **Step 1: Extend spotless targets to build-logic.** In root `build.gradle.kts`, the `spotless { kotlin { target("**/*.kt") } }` already covers `build-logic/**/*.kt` (ConventionUtils.kt) and `kotlinGradle { target("**/*.gradle.kts") }` covers the precompiled `*.gradle.kts`. Confirm the `targetExclude`s still exclude `**/build/**`. Run `./gradlew spotlessApply`, then `git diff --stat`; re-stage formatting.

- [ ] **Step 2: Build matrix** (each its own command):
  - `./gradlew assembleDebug 2>&1 | tail -15` → SUCCESS
  - `./gradlew assemblePerf 2>&1 | tail -15` → SUCCESS (proves perf build type from conventions + app)
  - keystore: `keytool -genkeypair -v -keystore /tmp/zero-verify.jks -storepass verify123 -keypass verify123 -alias verify -keyalg RSA -keysize 2048 -validity 1 -dname "CN=Zero Verify, OU=Dev, O=Zero, L=NA, S=NA, C=NA"`
  - `KEYSTORE_FILE=/tmp/zero-verify.jks KEYSTORE_PASSWORD=verify123 KEY_ALIAS=verify KEY_PASSWORD=verify123 ./gradlew assembleRelease 2>&1 | tail -20` → SUCCESS; then `rm -f /tmp/zero-verify.jks`

- [ ] **Step 3: Tests + lint + spotless**
  - `./gradlew testDebugUnitTest 2>&1 | tail -15` → SUCCESS
  - `./gradlew lintDebug 2>&1 | grep -E "error:|Error" | head` → no output
  - `./gradlew spotlessCheck 2>&1 | tail -10` → SUCCESS

- [ ] **Step 4: Config cache** — `./gradlew assembleDebug --configuration-cache 2>&1 | tail -10` stores; re-run → "Reusing configuration cache". (Conventions read `gradle.startParameter.taskNames` like the modules did — already proven cache-safe.)

- [ ] **Step 5: Net-config diff check.** For one compose module and the app, confirm the resolved config is unchanged vs master except desugaring. Compare the per-module effective namespace/minSdk/targetSdk and that `assembleDebug`/`assemblePerf` produced the same variant set. Spot-check: `git show HEAD~6:zero-ui/build.gradle.kts` had `minSdk = 21` → confirm `./gradlew :zero-ui:assembleDebug` still compiles with it.

- [ ] **Step 6: E2E** — `./scripts/emulator/acquire` (unlock if needed: `./scripts/ui/adb shell wm dismiss-keyguard`), then `./scripts/run-android-tests.sh 2>&1 | tail -15` → 7/7 pass.

- [ ] **Step 7: Docs.** Update AGENTS.md rule 8 to mention convention plugins: deps live in `gradle/libs.versions.toml`; shared module config lives in `build-logic` convention plugins (`zero.android.library`, `zero.android.library.compose`, `zero.android.application`, `zero.kotlin.jvm`) — a module applies the matching `id("zero.…")` and only declares its namespace/extras/deps. One or two sentences.

- [ ] **Step 8: Commit** — `git add -A` then `git commit -m "build: format build-logic, document convention plugins"`.

---

## Self-Review Notes

- **Spec coverage:** all 4 conventions, build-logic scaffold, catalog additions, includeBuild wiring, per-module slimming (all 13), desugaring normalization, and the verification matrix have tasks. ✓
- **Risk sequencing:** JVM convention first (Task 2) proves plumbing before any AGP API uncertainty; the `kotlin {}`-accessor question is resolved at the first Android build (Task 4) with an explicit fallback, then reused.
- **Type consistency:** `configureAndroidCommon(project)`, `Project.libs`, `VersionCatalog.intVersion` defined once in Task 2 and reused verbatim in Tasks 4–6.
- **Behavior:** only intended change is desugaring ON for zero-crash + zero-image-loading; minSdk(21)/targetSdk(32) deviations preserved as overrides; app keeps its signing/perf/AAB specifics.
- **No version bumps.**
