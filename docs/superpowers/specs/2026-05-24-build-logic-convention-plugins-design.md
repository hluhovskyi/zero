# build-logic Convention Plugins — Design

## Goal

Deduplicate the per-module Gradle KTS configuration introduced by the Groovy→KTS migration. Today every Android module repeats the same ~25 lines (`compileSdk`/`minSdk`/`compileOptions`/`buildTypes`/`composeCompiler`/`isPerfBuild`/`lint`/`testOptions`/`jvmTarget`). Extract that into precompiled convention plugins in a `build-logic` included build, so each module shrinks to plugin-apply + namespace + module-specific plugins + dependencies.

Behavior-preserving except one agreed normalization: **core-library desugaring is enabled uniformly** (it was ON in 6 Android modules, OFF in `zero-crash` + `zero-image-loading`; both gain it — a harmless bytecode change).

## Decisions (settled in brainstorming)

- **Precompiled script plugins** (`*.gradle.kts` in `build-logic/convention/src/main/kotlin/`), not class-based. Reuses the exact `android {}`/`kotlin {}`/`composeCompiler {}` DSL already validated in the modules; lowest risk under AGP 9 built-in Kotlin.
- **Catalog access via `VersionCatalogsExtension`** (`extensions.getByType<VersionCatalogsExtension>().named("libs").findVersion("…")`), NOT the `the<LibrariesForLibs>()` classpath hack. The hack is a known general Gradle gap (gradle/gradle#15383) for included builds; the API sidesteps it.
- **Desugaring normalized ON** everywhere.
- **4 conventions**: `zero.kotlin.jvm`, `zero.android.library`, `zero.android.library.compose`, `zero.android.application`.

## Architecture

```
build-logic/
  settings.gradle.kts          # repos + versionCatalogs.create("libs") from ../gradle/libs.versions.toml; include(":convention")
  convention/
    build.gradle.kts           # `kotlin-dsl`; compileOnly deps on AGP/Kotlin/Compose/KSP plugin artifacts
    src/main/kotlin/
      ConventionUtils.kt                        # catalog helpers + shared android/kotlin configurators
      zero.kotlin.jvm.gradle.kts
      zero.android.library.gradle.kts
      zero.android.library.compose.gradle.kts
      zero.android.application.gradle.kts
```

Root `settings.gradle.kts` gains `pluginManagement { includeBuild("build-logic") }` (before the `repositories` block).

### Shared helpers (`ConventionUtils.kt`)

- `Project.libs`: `extensions.getByType<VersionCatalogsExtension>().named("libs")`
- `VersionCatalog.intVersion(name)`: `findVersion(name).get().requiredVersion.toInt()`
- `Project.configureKotlinJvmTarget()`: configure the (built-in) Kotlin extension's `compilerOptions.jvmTarget = JVM_21`. Implemented by configuring the `kotlin` extension via the same DSL the modules use; if the extension type must be named explicitly, use the type AGP 9 built-in Kotlin exposes (verified during implementation against a green module build).
- `CommonExtension<*,*,*,*,*>.configureAndroidCommon(project)`: compileSdk, defaultConfig.minSdk, Java 21 compileOptions + `isCoreLibraryDesugaringEnabled = true`, `lint.targetSdk`, `testOptions.targetSdk`, and add the `coreLibraryDesugaring` dependency. Used by both library and application conventions.

### The 4 conventions

| Plugin id | Applies | Adds |
|---|---|---|
| `zero.kotlin.jvm` | `org.jetbrains.kotlin.jvm`, `java-library` | `kotlin { jvmToolchain(21) }` |
| `zero.android.library` | `com.android.library` | `configureAndroidCommon` + `jvmTarget` |
| `zero.android.library.compose` | (no re-apply of library; applies `com.android.library` + `org.jetbrains.kotlin.plugin.compose`) + shares `configureAndroidCommon` | `buildFeatures.compose=true`, `composeCompiler {}` + `isPerfBuild`, `perf` build type (`initWith release`) |
| `zero.android.application` | `com.android.application` | `configureAndroidCommon` (with applicationId, versionCode/Name from `version.properties`, signing, release+perf build types, packaging, buildConfig, composeCompiler), jvmTarget |

> `zero.android.library.compose` applies `com.android.library` itself and calls the same shared configurator rather than applying `zero.android.library` as a plugin (precompiled scripts can't cleanly apply sibling precompiled plugins by id without classpath gymnastics; sharing a helper function is cleaner).

### Catalog additions (`[libraries]`, build-logic classpath only)

```
android-gradlePlugin = { module = "com.android.tools.build:gradle", version.ref = "agp" }
kotlin-gradlePlugin = { module = "org.jetbrains.kotlin:kotlin-gradle-plugin", version.ref = "kotlin" }
compose-compiler-gradlePlugin = { module = "org.jetbrains.kotlin:compose-compiler-gradle-plugin", version.ref = "kotlin" }
ksp-gradlePlugin = { module = "com.google.devtools.ksp:com.google.devtools.ksp.gradle.plugin", version.ref = "ksp" }
```
`build-logic/convention/build.gradle.kts` declares these `compileOnly` so the precompiled scripts can use the `android {}` / `composeCompiler {}` DSL at compile time.

## Per-module result

Modules keep only what's theirs:
- **JVM** (`zero-api`, `zero-sync`, `zero-backup`, `zero-zenmoney`, `lint-rules`): `id("zero.kotlin.jvm")` + module plugins (`kotlin.serialization`, `ksp`) + deps. `lint-rules` uses `zero.kotlin.jvm`.
- **Android lib** (`zero-database`, `zero-remote`, `zero-crash`, `zero-test-bridge`): `id("zero.android.library")` + module plugins (`ksp`, `kotlin.serialization`, `androidx.room`) + `namespace` + module extras (`zero-crash` keeps `buildConfig=true` + `SENTRY_DSN` field + localProps loader; `zero-database` keeps `room { schemaDirectory }`) + deps.
- **Compose lib** (`zero-ui`, `zero-image-loading`, `zero-core`): `id("zero.android.library.compose")` + module plugins + `namespace` + overrides (`zero-ui` → `minSdk = 21`; `zero-image-loading` → `targetSdk = 32` in lint+testOptions) + module `optIn`s + deps.
- **App** (`app`): `id("zero.android.application")` + `ksp` + `kotlin.compose` + `namespace`, `applicationId`, signing/version specifics that stay in the module, + deps + the `bundleRelease` AAB-copy task.

### Variance handling
- `minSdk`/`targetSdk` deviations → plain reassignment in the module's `android {}` after the convention applies (last-write-wins).
- `optIn`s are module-specific → stay in the module's `kotlin { compilerOptions { } }` (convention sets `jvmTarget`; module adds opt-ins — both configure the same extension, additive).
- `zero-test-bridge` has no lint/testOptions targetSdk today; the convention adds them (harmless — sets them to the catalog value).

### Open implementation question (resolve against a green build, not by guessing)
The app's `android {}` is large and partly unique (signing, version props, AAB-copy, buildConfig fields). `zero.android.application` will bundle the *common* parts (sdk/java/desugar/jvmTarget/composeCompiler/perf build type); the genuinely app-specific parts (signing configs, applicationId, version props, buildConfigFields, the AAB-copy task) stay in `app/build.gradle.kts`. Exact split decided during implementation to keep the app file readable.

## Verification

Full matrix, same as the migration PR:
- `assembleDebug`, `assemblePerf`, `assembleRelease` (temp keystore)
- `testDebugUnitTest`, `lintDebug`, `spotlessCheck`
- e2e `connectedDebugAndroidTest`
- config cache stores + reuses
- **Net-config diff check:** for a representative module, confirm the effective merged config (e.g. via `./gradlew :zero-core:properties` or inspecting the assembled outputs) matches pre-refactor — the refactor must not change any module's resolved configuration except the desugaring normalization.

## Out of scope
Dependency version bumps, Material 2→3, any module's runtime code.
