# Gradle Groovy → Kotlin DSL Migration Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Convert all 15 Gradle build scripts from Groovy DSL (`.gradle`) to Kotlin DSL (`.gradle.kts`), extract the `buildscript.ext` `deps`/`versions` maps into a `gradle/libs.versions.toml` version catalog, and fold in the low-risk modernizations agreed with the user (group A idioms + explicit `jvmTarget` + parallel/config-cache + dead-property cleanup).

**Architecture:** The `ext.deps` nested-map pattern (`deps.android.compose.ui`) has no clean KTS equivalent, so it becomes a type-safe version catalog (`libs.androidx.compose.ui`). Modules are converted to KTS+catalog **first** while the root keeps its `ext` block alive for any not-yet-converted Groovy module; the root and `settings.gradle` convert **last**, when nothing references `ext` anymore. Each task ends with a `./gradlew help` configuration gate (cheap, evaluates every script); the final task runs the full build/test/lint/e2e matrix.

**Tech Stack:** Gradle 9.3.1, AGP 9.1.0, Kotlin 2.3.20, KSP, Room, Compose, Spotless/ktlint, version catalog.

**Scope — included modernizations (decided with the user):**
- **Group A idioms** (canonical spelling of existing behavior): `tasks.configureEach{name==…}` → `tasks.named(…)`; nested `android{ kotlin{} }` → top-level `kotlin{}`; drop dead `vectorDrawables { useSupportLibrary = true }` (native since API 21, minSdk is 23); `repositoriesMode.set(x)` → `repositoriesMode = x`; `packagingOptions`→`packaging`; `${buildDir}`→`layout.buildDirectory`; `toLowerCase()`→`lowercase()`.
- **Explicit Kotlin `jvmTarget = JVM_21`** everywhere (was relying on the default); pure-JVM modules standardize on `kotlin { jvmToolchain(21) }` (matches the existing `lint-rules` form) instead of a `java {}` source/target block.
- **`gradle.properties`:** enable `org.gradle.parallel=true`; attempt `org.gradle.configuration-cache` and keep it **only if green** (see Task 8 — `isPerfBuild` task-name detection and `:app` property loading may block it; if so, leave it off and document, do NOT rework perf detection here); remove the dead `android.uniquePackageNames=false` (removed in AGP 8.0) after confirming no warning relies on it; re-evaluate `android.r8.strictFullModeForKeepRules=false`.

**Scope — explicitly OUT (separate follow-up PRs):**
- **`build-logic` convention plugins** — the per-module config dedupe. Deferred to the immediate next PR because of per-module variance (minSdk/targetSdk/desugar deviations) and the catalog-access workaround.
- **Dependency version bumps** (`core-ktx`, `appcompat`, `fragment`, `coil`, `kotlinx-serialization`, mixed `lifecycle`, …) and **Material 2 → Material 3** — functional changes with real breakage risk; keep this PR bisectable.
- **Reworking `isPerfBuild`** (perf-tracing trigger, recently touched in #259) — do not change its task-name semantics here.

---

## Translation Rules (authoritative — apply to every module)

| Groovy | KTS |
|---|---|
| `id 'com.android.library'` (no version) | `alias(libs.plugins.android.library)` |
| `id 'com.android.application'` | `alias(libs.plugins.android.application)` |
| `id 'com.google.devtools.ksp'` | `alias(libs.plugins.ksp)` |
| `id 'org.jetbrains.kotlin.jvm'` | `alias(libs.plugins.kotlin.jvm)` |
| `id 'org.jetbrains.kotlin.plugin.serialization'` | `alias(libs.plugins.kotlin.serialization)` |
| `id 'org.jetbrains.kotlin.plugin.compose'` | `alias(libs.plugins.kotlin.compose)` |
| `id 'androidx.room'` | `alias(libs.plugins.androidx.room)` |
| `id 'java-library'` | `` `java-library` `` |
| `compileSdk versions.compileSdk` | `compileSdk = libs.versions.compileSdk.get().toInt()` |
| `minSdk versions.minSdk` | `minSdk = libs.versions.minSdk.get().toInt()` |
| `minSdk 21` | `minSdk = 21` |
| `targetSdk versions.targetSdk` (lint/testOptions) | `targetSdk = libs.versions.targetSdk.get().toInt()` |
| `targetSdk 32` | `targetSdk = 32` |
| `testInstrumentationRunner "x"` | `testInstrumentationRunner = "x"` |
| `consumerProguardFiles "consumer-rules.pro"` | `consumerProguardFiles("consumer-rules.pro")` |
| `minifyEnabled false` | `isMinifyEnabled = false` |
| `proguardFiles getDefaultProguardFile('proguard-android-optimize.txt'), 'proguard-rules.pro'` | `proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")` |
| `perf { initWith release }` | `create("perf") { initWith(getByName("release")) }` |
| `coreLibraryDesugaringEnabled true` | `isCoreLibraryDesugaringEnabled = true` |
| `sourceCompatibility JavaVersion.VERSION_21` | `sourceCompatibility = JavaVersion.VERSION_21` |
| `compose true` (buildFeatures) | `compose = true` |
| `namespace 'x'` | `namespace = "x"` |
| `lint { abortOnError true }` | `lint { abortOnError = true }` |
| `checkReleaseBuilds false` | `checkReleaseBuilds = false` |
| `execution 'ANDROIDX_TEST_ORCHESTRATOR'` | `execution = "ANDROIDX_TEST_ORCHESTRATOR"` |
| `room { schemaDirectory "$projectDir/schemas" }` | `room { schemaDirectory("$projectDir/schemas") }` |
| `stabilityConfigurationFiles = [rootProject.layout.projectDirectory.file("stable_config.conf")]` | `stabilityConfigurationFiles.add(rootProject.layout.projectDirectory.file("stable_config.conf"))` |
| `def isPerfBuild = gradle.startParameter.taskNames.any { it.toLowerCase().contains("perf") }` | `val isPerfBuild = gradle.startParameter.taskNames.any { it.lowercase().contains("perf") }` |
| `implementation deps.X` | `implementation(libs.…)` (see alias map) |
| `implementation project(':zero-api')` | `implementation(project(":zero-api"))` |
| `ksp deps.dagger.compiler` | `ksp(libs.dagger.compiler)` |
| `coreLibraryDesugaring deps.android.tools.desugar` | `coreLibraryDesugaring(libs.desugar.jdk.libs)` |
| `lintChecks project(":lint-rules")` | `lintChecks(project(":lint-rules"))` |
| `perfImplementation deps.X` | `"perfImplementation"(libs.…)` — **quoted**; the `perf` build type's config has no generated accessor |
| `"androidx.test:orchestrator:1.5.1"` (androidTestUtil) | `androidTestUtil(libs.androidx.test.orchestrator)` |
| `repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)` | `repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS` (group A) |
| `tasks.configureEach { task -> if (task.name == "x") { task.doLast {…} } }` | `tasks.named("x") { doLast {…} }` (group A — lazy, no graph walk) |
| `android { kotlin { compilerOptions {…} } }` | top-level `kotlin { compilerOptions {…} }` (group A — canonical location) |
| `vectorDrawables { useSupportLibrary true }` | **delete** (group A — native since API 21, minSdk 23) |

**Kotlin `jvmTarget` (group B, applies to every module):** the existing files never set it. In every `kotlin { compilerOptions { … } }` block add `jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21` (import `org.jetbrains.kotlin.gradle.dsl.JvmTarget` and use `jvmTarget = JvmTarget.JVM_21`). For a module that previously had **no** `kotlin {}` block (e.g. `zero-image-loading`), add a top-level `kotlin { compilerOptions { jvmTarget = JvmTarget.JVM_21 } }`.

`buildConfig = true` is already valid KTS — copy verbatim. Pure-JVM modules use `kotlin { jvmToolchain(21) }` (drop the `java {}` source/target block) — this sets both Java and Kotlin targets and matches the existing `lint-rules` form.

### Dependency alias map (`deps.*` → `libs.*`)

```
deps.kotlinxDatetime                     libs.kotlinx.datetime
deps.kotlin.coroutines                   libs.kotlinx.coroutines.android
deps.kotlin.serialization                libs.kotlinx.serialization.json
deps.test.coroutines                     libs.kotlinx.coroutines.test
deps.test.junit                          libs.junit
deps.test.mockito                        libs.mockito.kotlin
deps.android.tools.desugar               libs.desugar.jdk.libs
deps.dagger.runtime                      libs.dagger.runtime
deps.dagger.compiler                     libs.dagger.compiler
deps.javax.inject                        libs.javax.inject
deps.room.runtime                        libs.androidx.room.runtime
deps.room.ktx                            libs.androidx.room.ktx
deps.room.compiler                       libs.androidx.room.compiler
deps.android.core.ktx                    libs.androidx.core.ktx
deps.android.core.appcompat              libs.androidx.appcompat
deps.android.core.lifecycle              libs.androidx.lifecycle.runtime
deps.android.compose.foundation          libs.androidx.compose.foundation
deps.android.compose.navigation          libs.androidx.navigation.compose
deps.android.compose.materialNavigation  libs.androidx.compose.materialNavigation
deps.android.compose.ui                  libs.androidx.compose.ui
deps.android.compose.material            libs.androidx.compose.material
deps.android.compose.materialIconsExtended libs.androidx.compose.materialIconsExtended
deps.android.compose.uiPreview           libs.androidx.compose.uiToolingPreview
deps.android.compose.uiTooling           libs.androidx.compose.uiTooling
deps.android.compose.activity            libs.androidx.activity.compose
deps.android.compose.viewModel           libs.androidx.lifecycle.viewmodel.compose
deps.android.compose.tracing             libs.androidx.compose.runtime.tracing
deps.timber                              libs.timber
deps.biometric                           libs.androidx.biometric
deps.fragment                            libs.androidx.fragment.ktx
deps.coil                                libs.coil.compose
deps.okhttp.client                       libs.okhttp.client
deps.okhttp.mockwebserver                libs.okhttp.mockwebserver
deps.playIntegrity                       libs.play.integrity
deps.sentry                              libs.sentry.android
```

Inline strings (app androidTest / lint-rules) → catalog aliases:
```
"androidx.test.ext:junit:1.3.0"                  libs.androidx.test.ext.junit
"androidx.compose.ui:ui-test-junit4:$compose"    libs.androidx.compose.uiTestJunit4
"androidx.test.espresso:espresso-core:3.7.0"     libs.androidx.test.espresso.core
"androidx.test:orchestrator:1.5.1"               libs.androidx.test.orchestrator
"androidx.compose.ui:ui-test-manifest:$compose"  libs.androidx.compose.uiTestManifest
"com.android.tools.lint:lint-api:32.1.0"         libs.lint.api
"com.android.tools.lint:lint-checks:32.1.0"      libs.lint.checks
"com.android.tools.lint:lint-tests:32.1.0"       libs.lint.tests
"junit:junit:4.13.2"                             libs.junit
```

> **Catalog naming note:** plain `ui`/`material` artifacts use camelCase siblings (`uiTooling`, `materialNavigation`) rather than dashed children, so `ui` and `material` stay pure leaves with zero accessor-collision risk. If `./gradlew help` ever fails with "cannot generate accessor"/prefix collision, that's the signal — but the names below are already collision-free.

---

## Task 1: Create the version catalog

**Files:** Create `gradle/libs.versions.toml`

- [ ] **Step 1: Write the catalog**

```toml
[versions]
minSdk = "23"
compileSdk = "36"
targetSdk = "35"
compose = "1.10.5"
room = "2.8.4"
kotlin = "2.3.20"
ksp = "2.3.6"
agp = "9.1.0"
spotless = "7.0.2"
coroutines = "1.10.2"
serialization = "1.6.0"
datetime = "0.6.2"
coreKtx = "1.10.1"
appcompat = "1.6.1"
lifecycle = "2.8.7"
navigationCompose = "2.9.5"
materialIconsExtended = "1.7.8"
activityCompose = "1.11.0"
lifecycleViewmodelCompose = "2.9.4"
desugar = "2.0.3"
dagger = "2.56.2"
javaxInject = "1"
timber = "5.0.1"
biometric = "1.1.0"
fragment = "1.6.2"
coil = "2.1.0"
okhttp = "4.12.0"
playIntegrity = "1.4.0"
sentry = "8.42.0"
junit = "4.13.2"
mockitoKotlin = "5.4.0"
testExtJunit = "1.3.0"
espresso = "3.7.0"
orchestrator = "1.5.1"
lint = "32.1.0"

[libraries]
kotlinx-coroutines-android = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-android", version.ref = "coroutines" }
kotlinx-coroutines-test = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-test", version.ref = "coroutines" }
kotlinx-serialization-json = { module = "org.jetbrains.kotlinx:kotlinx-serialization-json", version.ref = "serialization" }
kotlinx-datetime = { module = "org.jetbrains.kotlinx:kotlinx-datetime", version.ref = "datetime" }
androidx-core-ktx = { module = "androidx.core:core-ktx", version.ref = "coreKtx" }
androidx-appcompat = { module = "androidx.appcompat:appcompat", version.ref = "appcompat" }
androidx-lifecycle-runtime = { module = "androidx.lifecycle:lifecycle-runtime-ktx", version.ref = "lifecycle" }
androidx-lifecycle-viewmodel-compose = { module = "androidx.lifecycle:lifecycle-viewmodel-compose", version.ref = "lifecycleViewmodelCompose" }
androidx-compose-foundation = { module = "androidx.compose.foundation:foundation", version.ref = "compose" }
androidx-compose-ui = { module = "androidx.compose.ui:ui", version.ref = "compose" }
androidx-compose-uiTooling = { module = "androidx.compose.ui:ui-tooling", version.ref = "compose" }
androidx-compose-uiToolingPreview = { module = "androidx.compose.ui:ui-tooling-preview", version.ref = "compose" }
androidx-compose-uiTestJunit4 = { module = "androidx.compose.ui:ui-test-junit4", version.ref = "compose" }
androidx-compose-uiTestManifest = { module = "androidx.compose.ui:ui-test-manifest", version.ref = "compose" }
androidx-compose-material = { module = "androidx.compose.material:material", version.ref = "compose" }
androidx-compose-materialNavigation = { module = "androidx.compose.material:material-navigation", version.ref = "compose" }
androidx-compose-materialIconsExtended = { module = "androidx.compose.material:material-icons-extended", version.ref = "materialIconsExtended" }
androidx-compose-runtime-tracing = { module = "androidx.compose.runtime:runtime-tracing", version.ref = "compose" }
androidx-navigation-compose = { module = "androidx.navigation:navigation-compose", version.ref = "navigationCompose" }
androidx-activity-compose = { module = "androidx.activity:activity-compose", version.ref = "activityCompose" }
androidx-biometric = { module = "androidx.biometric:biometric", version.ref = "biometric" }
androidx-fragment-ktx = { module = "androidx.fragment:fragment-ktx", version.ref = "fragment" }
androidx-room-runtime = { module = "androidx.room:room-runtime", version.ref = "room" }
androidx-room-compiler = { module = "androidx.room:room-compiler", version.ref = "room" }
androidx-room-ktx = { module = "androidx.room:room-ktx", version.ref = "room" }
androidx-test-ext-junit = { module = "androidx.test.ext:junit", version.ref = "testExtJunit" }
androidx-test-espresso-core = { module = "androidx.test.espresso:espresso-core", version.ref = "espresso" }
androidx-test-orchestrator = { module = "androidx.test:orchestrator", version.ref = "orchestrator" }
desugar-jdk-libs = { module = "com.android.tools:desugar_jdk_libs", version.ref = "desugar" }
dagger-runtime = { module = "com.google.dagger:dagger", version.ref = "dagger" }
dagger-compiler = { module = "com.google.dagger:dagger-compiler", version.ref = "dagger" }
javax-inject = { module = "javax.inject:javax.inject", version.ref = "javaxInject" }
timber = { module = "com.jakewharton.timber:timber", version.ref = "timber" }
coil-compose = { module = "io.coil-kt:coil-compose", version.ref = "coil" }
okhttp-client = { module = "com.squareup.okhttp3:okhttp", version.ref = "okhttp" }
okhttp-mockwebserver = { module = "com.squareup.okhttp3:mockwebserver", version.ref = "okhttp" }
play-integrity = { module = "com.google.android.play:integrity", version.ref = "playIntegrity" }
sentry-android = { module = "io.sentry:sentry-android", version.ref = "sentry" }
junit = { module = "junit:junit", version.ref = "junit" }
mockito-kotlin = { module = "org.mockito.kotlin:mockito-kotlin", version.ref = "mockitoKotlin" }
lint-api = { module = "com.android.tools.lint:lint-api", version.ref = "lint" }
lint-checks = { module = "com.android.tools.lint:lint-checks", version.ref = "lint" }
lint-tests = { module = "com.android.tools.lint:lint-tests", version.ref = "lint" }

[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
android-library = { id = "com.android.library", version.ref = "agp" }
androidx-room = { id = "androidx.room", version.ref = "room" }
kotlin-jvm = { id = "org.jetbrains.kotlin.jvm", version.ref = "kotlin" }
kotlin-serialization = { id = "org.jetbrains.kotlin.plugin.serialization", version.ref = "kotlin" }
kotlin-compose = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
ksp = { id = "com.google.devtools.ksp", version.ref = "ksp" }
spotless = { id = "com.diffplug.spotless", version.ref = "spotless" }
```

- [ ] **Step 2: Verify the catalog parses**

Run: `./gradlew help -q`
Expected: BUILD SUCCESSFUL. Gradle parses `libs.versions.toml` on every invocation and generates accessors; a syntax error or duplicate alias fails here. (Build still uses Groovy `ext` for everything else — catalog is defined but unused.)

- [ ] **Step 3: Commit**

```bash
git add gradle/libs.versions.toml
git commit -m "build: add libs.versions.toml version catalog"
```

---

## Task 2: Convert pure-Kotlin/JVM modules

These have no `android {}` block. **Root build.gradle stays Groovy** (still provides `ext.deps` to the not-yet-converted modules).

**Files (create `.kts`, delete `.gradle` for each):**
- `zero-api/build.gradle`, `zero-sync/build.gradle`, `zero-backup/build.gradle`, `zero-zenmoney/build.gradle`, `lint-rules/build.gradle`

- [ ] **Step 1: Write each `.gradle.kts`**

All pure-JVM modules standardize on `kotlin { jvmToolchain(21) }` (group B) — drop the old `java { sourceCompatibility/targetCompatibility }` blocks.

`zero-api/build.gradle.kts`, `zero-backup/build.gradle.kts` (identical content):
```kotlin
plugins {
    alias(libs.plugins.kotlin.jvm)
    `java-library`
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(libs.kotlinx.datetime)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
```
> `zero-api` has **no** `testImplementation` lines (its Groovy file lists only the three `implementation`s). `zero-backup` has both test lines. Match each source file exactly.

`zero-sync/build.gradle.kts` — same as `zero-backup` but plugin order is `kotlin.jvm`, `kotlin.serialization`, `java-library`, deps add `implementation(project(":zero-api"))` first. `kotlin { jvmToolchain(21) }`. Reproduce from `zero-sync/build.gradle` using the rules.

`zero-zenmoney/build.gradle.kts`:
```kotlin
plugins {
    `java-library`
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.ksp)
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(libs.kotlinx.datetime)
    implementation(project(":zero-api"))

    implementation(libs.kotlinx.coroutines.android)

    implementation(libs.dagger.runtime)
    ksp(libs.dagger.compiler)
}
```

`lint-rules/build.gradle.kts` (already used `jvmToolchain` — just drop its `java {}` block):
```kotlin
plugins {
    `java-library`
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(libs.lint.api)
    implementation(libs.lint.checks)

    testImplementation(libs.lint.tests)
    testImplementation(libs.junit)
}
```

- [ ] **Step 2: Delete the Groovy originals**

```bash
git rm zero-api/build.gradle zero-sync/build.gradle zero-backup/build.gradle zero-zenmoney/build.gradle lint-rules/build.gradle
```

- [ ] **Step 3: Verify configuration**

Run: `./gradlew help -q`
Expected: BUILD SUCCESSFUL. (Mixed Groovy/KTS is supported; converted modules read `libs.*`, the rest still read `ext.deps`.)

- [ ] **Step 4: Commit**

```bash
git add -A && git commit -m "build: migrate pure-JVM modules to Kotlin DSL"
```

---

## Task 3: Convert simple Android-library modules

`zero-database`, `zero-remote`, `zero-crash`, `zero-test-bridge`. **Root stays Groovy.**

- [ ] **Step 1: Write each `.gradle.kts`** (apply the translation table; full reference below for `zero-database`)

> For all four modules: put `kotlin { compilerOptions { … } }` at **top level** (not nested in `android {}`), add `jvmTarget = JvmTarget.JVM_21` as the first line of `compilerOptions`, and add `import org.jetbrains.kotlin.gradle.dsl.JvmTarget`. `zero-test-bridge` has no `kotlin {}` today — add a top-level `kotlin { compilerOptions { jvmTarget = JvmTarget.JVM_21 } }`.

`zero-database/build.gradle.kts`:
```kotlin
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.ksp)
    alias(libs.plugins.androidx.room)
}

android {
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    namespace = "com.hluhovskyi.zero"
    lint {
        targetSdk = libs.versions.targetSdk.get().toInt()
    }
    testOptions {
        targetSdk = libs.versions.targetSdk.get().toInt()
    }
    room {
        schemaDirectory("$projectDir/schemas")
    }
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_21
        optIn.add("kotlinx.coroutines.ExperimentalCoroutinesApi")
    }
}

dependencies {
    implementation(libs.kotlinx.datetime)

    coreLibraryDesugaring(libs.desugar.jdk.libs)

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

`zero-remote/build.gradle.kts` — plugins `android.library`, `ksp`, `kotlin.serialization`; `namespace = "com.hluhovskyi.zero.remote"`; `compileOptions` has desugaring; two `optIn` lines (coroutines + serialization); deps per its Groovy file (`coreLibraryDesugaring`, `project(":zero-api")`, coroutines/serialization, `okhttp.client`, `play.integrity`, dagger, timber, test junit/mockito/coroutines, `okhttp.mockwebserver`).

`zero-crash/build.gradle.kts` — plugins `android.library`, `ksp`. **Has the localProps loader** (see Task 5 Step 1 for the exact KTS Properties idiom — `import java.util.Properties` at top); `namespace = "com.hluhovskyi.zero.crash"`; `buildFeatures { buildConfig = true }`; `buildConfigField` for `SENTRY_DSN` (KTS form below); no desugaring in `compileOptions`; deps: lintChecks, `project(":zero-api")`, dagger, `javax.inject`, `sentry.android`.

```kotlin
buildConfigField("String", "SENTRY_DSN",
    "\"${System.getenv("SENTRY_DSN") ?: localProps.getProperty("sentryDsn") ?: ""}\"")
```

`zero-test-bridge/build.gradle.kts` — only `alias(libs.plugins.android.library)`; `namespace = "com.hluhovskyi.zero.testbridge"`; `defaultConfig { minSdk = … }`; `compileOptions` with desugaring; deps: lintChecks, `coreLibraryDesugaring`, `kotlinx.datetime`, `kotlinx.coroutines.android`, `project(":zero-api")`, `project(":zero-database")`.

- [ ] **Step 2: Delete Groovy originals**

```bash
git rm zero-database/build.gradle zero-remote/build.gradle zero-crash/build.gradle zero-test-bridge/build.gradle
```

- [ ] **Step 3: Verify** — `./gradlew help -q` → BUILD SUCCESSFUL
- [ ] **Step 4: Commit** — `git add -A && git commit -m "build: migrate simple Android-library modules to Kotlin DSL"`

---

## Task 4: Convert Compose Android-library modules

`zero-ui`, `zero-image-loading`, `zero-core`. All carry the `isPerfBuild` flag + `composeCompiler {}` block. **Root stays Groovy.**

- [ ] **Step 1: Write each `.gradle.kts`.** Shared shape (top of file + composeCompiler):
```kotlin
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
    // zero-core ALSO has: alias(libs.plugins.ksp)
}

val isPerfBuild = gradle.startParameter.taskNames.any { it.lowercase().contains("perf") }

android {
    // …compileSdk/defaultConfig/buildTypes/compileOptions/buildFeatures/namespace/lint/testOptions per source…
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

// top-level (group A: moved out of android {}; group B: explicit jvmTarget)
kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_21
        // + the module's optIn.add(…) lines
    }
}
```

Per-module specifics (reproduce the rest from each Groovy source via the rules):
- **zero-ui** — `buildTypes` has `release` + `create("perf") { initWith(getByName("release")) }`; `defaultConfig { minSdk = 21 }` (literal, not `versions.minSdk`); `compileOptions` desugaring; top-level `kotlin {}` with `jvmTarget` + one optIn (`androidx.compose.material.ExperimentalMaterialApi`); deps incl. `"perfImplementation"(libs.androidx.compose.runtime.tracing)`.
- **zero-image-loading** — `buildTypes` release + perf; **no desugaring** in compileOptions; `lint { targetSdk = 32 }` and `testOptions { targetSdk = 32 }` (literal 32, not `versions.targetSdk`); had **no** `kotlin {}` block → add top-level `kotlin { compilerOptions { jvmTarget = JvmTarget.JVM_21 } }` (no optIns); deps: lintChecks, `project(":zero-api")`, `androidx.compose.foundation`, `"perfImplementation"(libs.androidx.compose.runtime.tracing)`, `coil.compose`.
- **zero-core** — adds `alias(libs.plugins.ksp)`; `buildTypes` release + perf; desugaring on; top-level `kotlin {}` with `jvmTarget` + three optIns (coroutines + foundation + animation — match source exactly); deps are the longest list (see `zero-core/build.gradle`), incl. `"perfImplementation"(libs.androidx.compose.runtime.tracing)` and `ksp(libs.dagger.compiler)`.

- [ ] **Step 2: Delete Groovy originals**

```bash
git rm zero-ui/build.gradle zero-image-loading/build.gradle zero-core/build.gradle
```

- [ ] **Step 3: Verify** — `./gradlew help -q` → BUILD SUCCESSFUL
- [ ] **Step 4: Commit** — `git add -A && git commit -m "build: migrate Compose library modules to Kotlin DSL"`

---

## Task 5: Convert the app module

The most complex file: Properties loading, `tasks.configureEach` AAB copy, signing configs, `buildConfigField`, perf build type with `isProfileable`, `packaging`. **Root stays Groovy.**

**Files:** Create `app/build.gradle.kts`, delete `app/build.gradle`

- [ ] **Step 1: Write `app/build.gradle.kts`**

```kotlin
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

// group A: tasks.named (lazy) instead of tasks.configureEach + name string-match
tasks.named("bundleRelease") {
    doLast {
        if (releaseOutputDir != null) {
            val outputDir = File("${layout.buildDirectory.get().asFile}/outputs/bundle/release")
            val destDir = File(releaseOutputDir)
            destDir.mkdirs()
            outputDir.listFiles()?.firstOrNull { it.name.endsWith(".aab") }?.let { aab ->
                val versionCode = versionProps.getProperty("versionCode")
                copy {
                    from(aab)
                    into(destDir)
                    rename { "zero-$versionCode.aab" }
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
        // group A: dropped `vectorDrawables { useSupportLibrary = true }` — native since API 21 (minSdk 23)

        buildConfigField("String", "FEEDBACK_ENDPOINT",
            "\"${System.getenv("FEEDBACK_ENDPOINT") ?: localProps.getProperty("feedbackEndpoint") ?: ""}\"")
        buildConfigField("String", "FEEDBACK_INTEGRITY_PROJECT",
            "\"${System.getenv("FEEDBACK_INTEGRITY_PROJECT") ?: localProps.getProperty("feedbackIntegrityProject") ?: ""}\"")
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

// top-level (group A: moved out of android {}; group B: explicit jvmTarget)
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
```

- [ ] **Step 2: Delete the Groovy original** — `git rm app/build.gradle`
- [ ] **Step 3: Verify** — `./gradlew help -q` → BUILD SUCCESSFUL
- [ ] **Step 4: Commit** — `git add -A && git commit -m "build: migrate :app to Kotlin DSL"`

---

## Task 6: Convert root build.gradle + settings.gradle (last — removes `ext`)

Now every module reads `libs.*`; the `ext` block is dead and can go.

**Files:** Create `build.gradle.kts` & `settings.gradle.kts`; delete `build.gradle` & `settings.gradle`

- [ ] **Step 1: Write `settings.gradle.kts`**
```kotlin
pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
    }
}
dependencyResolutionManagement {
    repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS
    repositories {
        google()
        mavenCentral()
    }
}
rootProject.name = "Zero"
include(":app")
include(":zero-api")
include(":zero-database")
include(":zero-core")
include(":zero-ui")
include(":zero-image-loading")
include(":zero-crash")
include(":lint-rules")
include(":zero-sync")
include(":zero-backup")
include(":zero-remote")
include(":zero-test-bridge")
```

- [ ] **Step 2: Write `build.gradle.kts`** (the `buildscript.ext` block is dropped entirely)
```kotlin
plugins {
    alias(libs.plugins.spotless)
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.androidx.room) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
}

spotless {
    kotlin {
        target("**/*.kt")
        targetExclude("**/build/**/*.kt", "**/.worktrees/**/*.kt")
        ktlint("1.5.0").editorConfigOverride(
            mapOf(
                "ktlint_standard_function-naming" to "disabled",
                "ktlint_standard_filename" to "disabled",
                "ktlint_standard_multiline-expression-wrapping" to "disabled",
                "ktlint_standard_argument-list-wrapping" to "disabled",
            ),
        )
    }
    kotlinGradle {
        target("**/*.gradle.kts")
        targetExclude("**/build/**", "**/.worktrees/**")
        ktlint("1.5.0")
    }
    format("misc") {
        target("**/.gitignore")
        leadingTabsToSpaces()
        trimTrailingWhitespace()
        endWithNewline()
    }
}
```
> Note: `kotlin.plugin.serialization` keeps **no** `apply false` — preserves the original root behavior (it was applied at root). The `format("misc")` target drops `**/*.gradle` (no Groovy files remain) but keeps `**/.gitignore`.

- [ ] **Step 3: Delete Groovy originals** — `git rm build.gradle settings.gradle`
- [ ] **Step 4: Verify configuration** — `./gradlew help -q` → BUILD SUCCESSFUL (now 100% KTS)
- [ ] **Step 5: Commit** — `git add -A && git commit -m "build: migrate root build + settings to Kotlin DSL"`

---

## Task 7: gradle.properties modernization + config-cache attempt

Now that the build is 100% KTS, modernize `gradle.properties`. Do each as its own verification — do **not** assume; confirm with build output.

**Files:** Modify `gradle.properties`

- [ ] **Step 1: Remove the dead `android.uniquePackageNames` flag**

`android.uniquePackageNames=false` was removed in AGP 8.0; under AGP 9 it does nothing. First confirm it's inert — run `./gradlew help -q --warning-mode all 2>&1 | grep -i "uniquePackageNames"` (no meaningful output expected), then delete the line from `gradle.properties`. Re-run `./gradlew help -q` → BUILD SUCCESSFUL.

- [ ] **Step 2: Re-evaluate `android.r8.strictFullModeForKeepRules=false`**

Check whether anything depends on it: `./gradlew help -q --warning-mode all 2>&1 | grep -i "strictFullMode"`. If inert/deprecated under AGP 9, remove it; if it still affects R8 behavior, **keep it** (it's an intentional opt-out). Document the decision in the commit message.

- [ ] **Step 3: Enable parallel builds**

Add `org.gradle.parallel=true` (multi-module project; safe). Run `./gradlew help -q` → BUILD SUCCESSFUL.

- [ ] **Step 4: Attempt the configuration cache — keep ONLY if green**

Run a representative build with the cache forced on (single command):
```bash
./gradlew assembleDebug --configuration-cache 2>&1 | tail -30
```
- **If it reports "Configuration cache entry stored" with no problems:** add `org.gradle.configuration-cache=true` to `gradle.properties`, then re-run `./gradlew assembleDebug --configuration-cache` to confirm a cache **hit** ("reused").
- **If it reports problems** (likely culprits: `gradle.startParameter.taskNames` in `isPerfBuild`, or `:app` reading `version.properties`/`local.gradle.properties` at configuration time): do **not** enable it. Leave `org.gradle.configuration-cache` unset and add a short comment in `gradle.properties` noting it's deferred to the convention-plugin/perf-rework PR (reworking `isPerfBuild` is explicitly out of scope here). Capture the reported problems in the commit message.

- [ ] **Step 5: Commit**

```bash
git add gradle.properties && git commit -m "build: modernize gradle.properties (parallel, drop dead AGP flags, config-cache)"
```

---

## Task 8: Format, full verification matrix, docs

The full verification gate the user requires: **spotlessApply → debug build → perf build → release build (temp keystore) → unit tests → lint → spotlessCheck → e2e tests**. Run each as its own `Bash` call (no `&&` chaining per repo convention). Fix any failure before moving on.

- [ ] **Step 1: Apply spotless formatting to the new KTS files**

Run: `./gradlew spotlessApply`
Then `git diff --stat` to see formatting touch-ups. Re-stage anything that changed. (`kotlinGradle` now formats every `*.gradle.kts`; this normalizes the freshly-written files.)

- [ ] **Step 2: Debug build**

Run: `./gradlew assembleDebug 2>&1 | tail -20`
Expected: BUILD SUCCESSFUL — proves every `libs.*` alias resolves to the same coordinates the `ext` map had.

- [ ] **Step 3: Perf build**

Run: `./gradlew assemblePerf 2>&1 | tail -20`
Expected: BUILD SUCCESSFUL — confirms the `perf` build type, the quoted `"perfImplementation"` config, `isProfileable`, and `matchingFallbacks` all survived the migration.

- [ ] **Step 4: Release build with a temporary keystore**

The release `signingConfig` reads `KEYSTORE_FILE`/`KEYSTORE_PASSWORD`/`KEY_ALIAS`/`KEY_PASSWORD` from env (falling back to `local.gradle.properties`). With none set, `storeFile` is null and a release build can't sign. Generate a throwaway keystore, then build with the env vars — mirroring `.github/workflows/cd.yml`.

Generate the keystore (single command, no chaining):
```bash
keytool -genkeypair -v -keystore /tmp/zero-verify.jks -storepass verify123 -keypass verify123 -alias verify -keyalg RSA -keysize 2048 -validity 1 -dname "CN=Zero Verify, OU=Dev, O=Zero, L=NA, S=NA, C=NA"
```
Then the signed release build (env inline on the gradle call):
```bash
KEYSTORE_FILE=/tmp/zero-verify.jks KEYSTORE_PASSWORD=verify123 KEY_ALIAS=verify KEY_PASSWORD=verify123 ./gradlew assembleRelease 2>&1 | tail -25
```
Expected: BUILD SUCCESSFUL with a signed `app/build/outputs/apk/release/*.apk`. Then remove the temp keystore: `rm -f /tmp/zero-verify.jks`.
> The temp keystore is a local artifact only — never commit it, never reuse the CD release keystore.

- [ ] **Step 5: Unit tests**

Run: `./gradlew testDebugUnitTest 2>&1 | tail -20`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Lint**

Run: `./gradlew lintDebug 2>&1 | grep -E "error:|Error" | head -20`
Expected: no output (no lint errors).

- [ ] **Step 7: Spotless check**

Run: `./gradlew spotlessCheck 2>&1 | tail -15`
Expected: BUILD SUCCESSFUL (Step 1 already applied formatting).

- [ ] **Step 8: E2E (instrumented) tests**

Requires the session's pinned emulator. Acquire it now (UI/instrumented verification point):
```bash
./scripts/emulator/acquire
```
Then run the e2e suite via the project wrapper (handles `.emulator-serial` → `ANDROID_SERIAL`):
```bash
./scripts/run-android-tests.sh 2>&1 | tail -30
```
Expected: BUILD SUCCESSFUL — `:app:connectedDebugAndroidTest` green. This is the strongest signal that the migrated `:app` (custom configs, KSP, Compose) assembles, installs, and runs on device. If a test flakes, re-run once per the e2e flake rule before treating it as a real failure.

- [ ] **Step 9: Sanity-check version parity**

Find the commit that removed the Groovy root, then show its pre-delete content (robust to commit-count drift):
```bash
git show "$(git log --diff-filter=D --format=%H -1 -- build.gradle)^:build.gradle"
```
Eyeball every version string against `gradle/libs.versions.toml`. Confirm no version drifted during transcription. This is the no-version-bump gate.

- [ ] **Step 10: Document the catalog**

Add one line to `AGENTS.md` (near "Cross-Cutting Rules" or "Module Map"): dependencies/versions are centralized in `gradle/libs.versions.toml` (version catalog) — add new libs there and reference via `libs.*`, never inline coordinate strings. Per repo doc rules, one sentence.

- [ ] **Step 11: Commit**

```bash
git add -A && git commit -m "build: format KTS, document version catalog"
```

---

## Self-Review Notes

- **Spec coverage:** all 15 files (catalog + 13 modules + root + settings) + `gradle.properties` have a task; every `deps.*`/`versions.*`/inline-string reference has a catalog alias.
- **Included modernizations are all spelled out:** group A (`tasks.named`, top-level `kotlin {}`, dropped `useSupportLibrary`, `repositoriesMode =`, `packaging`, `layout.buildDirectory`, `lowercase()`), explicit `jvmTarget = JVM_21` + JVM `jvmToolchain(21)`, and the `gradle.properties` cleanup (Task 7). The `kotlin {}` block moves to **top level** in every module — that's both the group-A idiom and the safe location under KTS.
- **Deliberately deferred (separate PRs):** `build-logic` convention plugins; dependency version bumps; Material 2→3; reworking `isPerfBuild` (only enable config-cache in Task 7 if it's already green without that rework).
- **No version bumps in this PR.** Task 8 Step 9 is the parity gate.
- **Type consistency:** `JvmTarget` import (`org.jetbrains.kotlin.gradle.dsl.JvmTarget`) is required in every module that sets `jvmTarget`; `"perfImplementation"(…)` stays quoted; perf build type uses `create("perf")`.
