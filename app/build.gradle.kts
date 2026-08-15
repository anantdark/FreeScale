import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

// Sentry DSN committed obfuscated (XOR + Base64) — same fleet project as FitBuddy.
// Empty blob → CrashReporter stays uninitialized.
val sentryDsnMaskSeed = "fitbuddy.sentry.v1"
val sentryDsnBlobEscaped =
    "Dh0AEgZeS1YeF1RWQ0YbH0JXUVlMARYCAEAeQANZFUsYHUMGUlgSWjULUEwfQlJbQkZKG0EAXlhAVlsNCh5LABFAEBdXXRNfEhsNTBwLS00bQlRZQURNGkMBVF1HUUM="

// CI overrides versionCode/versionName per build via -PappVersionCode=<GITHUB_RUN_NUMBER>
// and -PappVersionName=0.1.<GITHUB_RUN_NUMBER> so every commit to main produces an
// installable update; local/dev builds keep the fallback.
val ciVersionCode = (project.findProperty("appVersionCode") as String?)?.toIntOrNull()
val ciVersionName = project.findProperty("appVersionName") as String?

// Release signing — local keystore.properties should point at a local/dev keystore
// (e.g. freescale-local.jks). The Play/CI release key lives only in GitHub RELEASE_* secrets.
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) {
        keystorePropertiesFile.inputStream().use { load(it) }
    }
}

android {
    namespace = "com.anant.freescale"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.anant.freescale"
        minSdk = 29
        targetSdk = 37
        versionCode = ciVersionCode ?: 1
        versionName = ciVersionName ?: "0.1.0-dev"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("boolean", "IS_FDROID", "false")
        buildConfigField("String", "SENTRY_DSN_BLOB", "\"$sentryDsnBlobEscaped\"")
        buildConfigField("String", "SENTRY_DSN_MASK", "\"$sentryDsnMaskSeed\"")
    }

    // github flavor: GitHub Releases / sideload. fdroid flavor: F-Droid owns updates.
    // isDefault → Studio Active Build Variant is githubDebug (not a stale unflavored debug).
    flavorDimensions += "distribution"
    productFlavors {
        create("github") {
            dimension = "distribution"
            isDefault = true
            buildConfigField("boolean", "IS_FDROID", "false")
        }
        create("fdroid") {
            dimension = "distribution"
            buildConfigField("boolean", "IS_FDROID", "true")
            // F-Droid builds from source with no -P overrides, so defaultConfig's CI-driven
            // version would resolve to 1/"0.1.0-dev". Fixed here, bumped by hand / workflow.
            versionCode = 1
            versionName = "0.1.1"
        }
    }

    signingConfigs {
        create("release") {
            if (keystorePropertiesFile.exists()) {
                storeFile = rootProject.file(keystoreProperties["storeFile"] as String)
                storePassword = keystoreProperties["storePassword"] as String
                keyAlias = keystoreProperties["keyAlias"] as String
                keyPassword = keystoreProperties["keyPassword"] as String
            }
        }
    }

    buildTypes {
        debug {
            // Side-by-side with release: com.anant.freescale.debug vs com.anant.freescale
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig = if (keystorePropertiesFile.exists()) {
                signingConfigs.getByName("release")
            } else {
                // No keystore.properties — sign with the Android debug key (local smoke tests).
                signingConfigs.getByName("debug")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    // Always install into the personal profile (user 0), never the work profile (user 10).
    installation {
        installOptions += listOf("--user", "0")
    }
    // Omit AGP dependency-metadata signing block (rejected by F-Droid's APK scanner).
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }
}

// Release APK: FreeScale-<versionName>.apk (not app-release.apk).
val fallbackApkVersionName = ciVersionName ?: "0.1.0-dev"
androidComponents {
    onVariants(selector().withBuildType("release")) { variant ->
        variant.outputs.forEach { output ->
            output.outputFileName.set(
                output.versionName.map { versionName ->
                    "FreeScale-${versionName ?: fallbackApkVersionName}.apk"
                },
            )
        }
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    // BOM pins 1.4.0; Expressive theme APIs are public from 1.5 alphas.
    implementation("androidx.compose.material3:material3:1.5.0-alpha21")
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.sentry.android)
    implementation(libs.okhttp)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}

// Baseline profiles are not bit-reproducible across machines (F-Droid RB).
tasks.whenTaskAdded {
    if (name.contains("ArtProfile")) {
        enabled = false
    }
}
