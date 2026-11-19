---
tags:
  - paypals
  - build
  - gradle
  - cicd
  - config
aliases:
  - Build Config
  - CI-CD
---

# Build Configuration, Project Files & CI/CD

> [!note] Navigation
> Back to [[00 - Overview]] | Related: [[02 - Manifest, Navigation & Menus]] · [[03 - Layouts & Resources]]

---

## Gradle & Dependency Files

### root build.gradle.kts
- **Path**: `build.gradle.kts`
- **Role**: Top-level Gradle build file declaring the Android Application plugin and Google Services plugin with `apply false` from [[01 - Build, Config & CI-CD#libs.versions.toml|libs.versions.toml]] so subprojects can opt in cleanly.

### app build.gradle.kts
- **Path**: `app/build.gradle.kts`
- **Role**: Module configuration for `:app`.
- **Key Settings**:
  - `namespace = "com.sdu.paypals"`, `applicationId = "com.sdu.paypals"`, `compileSdk = 37`, `minSdk = 24`, `targetSdk = 37`, Java 11 compatibility, and `viewBinding = true`.
  - Dynamic release versioning via `-PversionName` (defaults to `"1.0.0"`) and `-PversionCode` (computed automatically from `MAJOR * 10000 + MINOR * 100 + PATCH`). Used directly by [[01 - Build, Config & CI-CD#release.yml|release.yml]].
  - `release` build type uses `debug` signing config for CI APK generation and R8 optimization scoped to `androidx.**`, `kotlin.**`, `kotlinx.**`.
  - Dependencies declared via [[01 - Build, Config & CI-CD#libs.versions.toml|libs.versions.toml]]: AndroidX Activity/AppCompat/Core/ConstraintLayout/Navigation/SplashScreen, Material 3, Firebase BoM (`firebase-auth`, `firebase-firestore`, `firebase-database`), ZXing (`core`, `zxing-android-embedded`), JUnit, and Espresso.

### libs.versions.toml
- **Path**: `gradle/libs.versions.toml`
- **Role**: Central Gradle Version Catalog defining library/plugin coordinates and pinned versions (AGP `9.4.1`, Kotlin/AndroidX libs, Firebase BoM `33.10.0`, ZXing Core `3.5.3` & Embedded `4.3.0`, Core SplashScreen `1.0.1`, JUnit `4.13.2`, Espresso `3.7.0`).
- **Consumed by**: [[01 - Build, Config & CI-CD#root build.gradle.kts|build.gradle.kts]] and [[01 - Build, Config & CI-CD#app build.gradle.kts|app/build.gradle.kts]].

### settings.gradle.kts
- **Path**: `settings.gradle.kts`
- **Role**: Configures plugin repositories (`google()`, `mavenCentral()`, `gradlePluginPortal()`), applies Foojay toolchain resolver convention (`1.0.0`), enforces `RepositoriesMode.FAIL_ON_PROJECT_REPOS`, sets `rootProject.name = "PayPals"`, and includes `:app`.

### gradle.properties
- **Path**: `gradle.properties`
- **Role**: Sets JVM heap (`-Xmx2048m`, UTF-8), enables `org.gradle.configuration-cache=true`, and enforces `kotlin.code.style=official`.

### gradle-daemon-jvm.properties
- **Path**: `gradle/gradle-daemon-jvm.properties`
- **Role**: Auto-generated Gradle daemon JVM toolchain descriptor specifying Foojay discovery URLs across platforms and `toolchainVersion=25`.

### gradle-wrapper.properties & Wrapper Scripts
- **Paths**: `gradle/wrapper/gradle-wrapper.properties`, `gradle/wrapper/gradle-wrapper.jar`, `gradlew`, `gradlew.bat`
- **Role**: Pins Gradle Wrapper distribution `gradle-9.6.0-bin.zip` with SHA-256 verification and provides cross-platform execution scripts for local builds and [[ExampleUnitTest]] / [[ExampleInstrumentedTest]] runs.

---

## Secrets, Ignores, ProGuard & Firebase Config

### root .gitignore
- **Path**: `.gitignore`
- **Role**: Standard Kotlin/Android ignore rules preventing sensitive files (`google-services.json`, `**/google-services.json`, `app/google-services.json`, `.env*`, `*.jks`, `*.keystore`, `keystore.properties`) and build/IDE artifacts (`.gradle/`, `build/`, `local.properties`, `release-artifacts/`, `*.apk`, `*.aab`, `.idea/`, `.kotlin/`) from being committed.

### app .gitignore
- **Path**: `app/.gitignore`
- **Role**: Module-level ignore file excluding `/build`, `google-services.json`, and `.env*` inside `app/`.

### google-services.json
- **Path**: `app/google-services.json` *(gitignored locally)*
- **Role**: Firebase Android client configuration required by `com.google.gms.google-services` in [[01 - Build, Config & CI-CD#app build.gradle.kts|app/build.gradle.kts]] to initialize [[FirebaseProvider]], Authentication, and Cloud Firestore. On CI, reconstructed from GitHub secret `GOOGLE_SERVICES_JSON` inside [[01 - Build, Config & CI-CD#release.yml|release.yml]].

### rules.keep
- **Path**: `app/src/main/keepRules/rules.keep`
- **Role**: R8 / ProGuard keep-rule file template combined by AGP during release optimization configured in [[01 - Build, Config & CI-CD#app build.gradle.kts|app/build.gradle.kts]].

### local.properties
- **Path**: `local.properties` *(gitignored local SDK path file)*
- **Role**: Local Android SDK pointer used by Android Gradle Plugin during local builds.

---

## Documentation & CI/CD Workflows

### release.yml
- **Path**: `.github/workflows/release.yml`
- **Role**: Manual `workflow_dispatch` GitHub Actions workflow (`PayPals Release APK`).
- **Pipeline Steps**:
  1. Accepts `version` (validated against `MAJOR.MINOR.PATCH` semantic versioning) and optional `release_notes`.
  2. Sets up JDK 17 (`temurin`) and Gradle.
  3. Writes `secrets.GOOGLE_SERVICES_JSON` to `app/google-services.json`.
  4. Runs unit tests via `gradle testDebugUnitTest -PversionName=...` (executing [[ExampleUnitTest]]).
  5. Builds APKs via `gradle assembleRelease assembleDebug -PversionName=...` and copies output to `release-artifacts/PayPals-v<version>.apk`.
  6. Uploads workflow artifact and publishes a tagged GitHub Release (`softprops/action-gh-release@v2`).

### README.md
- **Path**: `README.md`
- **Role**: Repository onboarding instructions explaining prerequisites (Android Studio, JDK 17+, Firebase Auth + Cloud Firestore) and how to download and place `google-services.json` inside `app/`.
