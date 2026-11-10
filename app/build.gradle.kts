import java.util.regex.Pattern
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.aboutlibraries)
}

android {
    namespace = "com.gamso.skullkingscore"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.gamso.skullkingscore"
        minSdk = 26
        targetSdk = 37
        // Overridden by the CI from the release tag (e.g. -PversionName=1.4.1 -PversionCode=10401).
        // versionCode = major * 10000 + minor * 100 + patch.
        versionCode = providers.gradleProperty("versionCode").map(String::toInt).getOrElse(10401)
        versionName = providers.gradleProperty("versionName").getOrElse("1.4.1")
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // The release signing config is only set up when the keystore is provided (CI or local machine).
    val keystorePath = providers.environmentVariable("SIGNING_KEYSTORE_FILE").orNull
    signingConfigs {
        if (!keystorePath.isNullOrBlank()) {
            create("release") {
                storeFile = file(keystorePath)
                storePassword = providers.environmentVariable("SIGNING_KEYSTORE_PASSWORD").orNull
                keyAlias = providers.environmentVariable("SIGNING_KEY_ALIAS").orNull
                keyPassword = providers.environmentVariable("SIGNING_KEY_PASSWORD").orNull
            }
        }
    }

    buildTypes {
        debug {
            // Code coverage (JaCoCo) of the unit and instrumented tests: see the README.
            enableUnitTestCoverage = true
            enableAndroidTestCoverage = true
        }
        release {
            signingConfigs.findByName("release")?.let { signingConfig = it }
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    // Lint's Kotlin analysis crashes on the instrumented test sources (Kotlin 2.4): only production code is checked.
    lint {
        ignoreTestSources = true
    }

    testCoverage {
        jacocoVersion = "0.8.13"
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }

    // No dependency metadata block in the APK / AAB: it is encrypted with a Google key, nobody else can read it.
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    // The exported database schemas, read by the migration tests.
    sourceSets {
        getByName("androidTest").assets.directories.add("$projectDir/schemas")
    }
}

// Open source licenses screen: the list and the license texts (SPDX) are generated from the dependencies at build
// time. Nothing is fetched from GitHub, and the app itself never uses the network.
aboutLibraries {
    collect {
        fetchRemoteLicense = false
        fetchRemoteFunding = false
    }
    // Only what ships in the app is listed. A new dependency shows up by itself; these are left out because the
    // release build does not contain them. To check one, look for its package in
    // app/build/outputs/mapping/release/mapping.txt (R8 lists every class it kept).
    library {
        exclusionPatterns.addAll(
            listOf(
                // Annotations only: the optimizer removes them all from the release build.
                "javax\\.inject:.*",
                "jakarta\\.inject:.*",
                "com\\.google\\.code\\.findbugs:.*",
                "org\\.jspecify:.*",
                "org\\.jetbrains:annotations",
                "com\\.google\\.guava:listenablefuture",
                // AndroidX modules that nothing uses: no class left in the release build.
                "androidx\\.(autofill|fragment|loader|viewpager):.*",
                // Bills of materials only fix versions, they hold no code.
                ".*:.*-bom",
                // Debug builds only: coverage agent, Compose tooling, Dagger lint rules.
                "org\\.jacoco:.*",
                "androidx\\.compose\\.ui:ui-(tooling|tooling-data|test-manifest)",
                "com\\.google\\.dagger:dagger-lint-aar",
            ).map { Pattern.compile(it) },
        )
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(project(":core-scoring"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.aboutlibraries.core)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.icons.core)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.room.runtime)
    ksp(libs.androidx.room.compiler)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(kotlin("test"))
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)

    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(kotlin("test"))
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.espresso.core)
    androidTestImplementation(libs.androidx.room.testing)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
