plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.jovoc.dailyaffirmationwomen"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.jovoc.dailyaffirmationwomen"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    // AndroidX Core extensions for system APIs
    implementation("androidx.core:core-ktx:1.13.1")

    // Activity integration with Jetpack Compose (setContent)
    implementation("androidx.activity:activity-compose:1.9.0")

    // Jetpack Compose foundational UI elements and gestures
    implementation("androidx.compose.ui:ui:1.6.8")

    // Jetpack Compose graphics primitives and color models
    implementation("androidx.compose.ui:ui-graphics:1.6.8")

    // Jetpack Compose tooling preview support for Android Studio
    implementation("androidx.compose.ui:ui-tooling-preview:1.6.8")

    // Material 3 design system components and tokens
    implementation("androidx.compose.material3:material3:1.2.1")

    // ViewModel utilities for Jetpack Compose lifecycle integration
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.1")

    // Lifecycle runtime KTX for lifecycleScope and state collection
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.1")

    // Kotlin Coroutines for asynchronous background tasks without blocking UI thread
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // JVM local unit testing framework
    testImplementation("junit:junit:4.13.2")

    // Built-in org.json JVM shadow library for unit testing JSON parsing without Android mocking
    testImplementation("org.json:json:20231013")

    // Debug-only tools for Compose layout inspection
    debugImplementation("androidx.compose.ui:ui-tooling:1.6.8")
    debugImplementation("androidx.compose.ui:ui-test-manifest:1.6.8")
}

// Automatically copy debug APK to /home/haku/Dropbox/sync with a 3-character random alphanumeric suffix
val copyDebugApkToDropbox by tasks.registering {
    description = "Copies debug APK to /home/haku/Dropbox/sync with a random 3-character alphanumeric suffix."
    group = "distribution"

    doLast {
        val debugApk = layout.buildDirectory.file("outputs/apk/debug/app-debug.apk").get().asFile
        if (debugApk.exists()) {
            val syncDir = File("/home/haku/Dropbox/sync")
            if (!syncDir.exists()) {
                syncDir.mkdirs()
            }
            val allowedChars = ('a'..'z') + ('0'..'9')
            val randomSuffix = (1..3).map { allowedChars.random() }.joinToString("")
            val appSlug = rootProject.name.replace(" ", "-")
            val targetFile = File(syncDir, "$appSlug-apk-debug-$randomSuffix.apk")
            debugApk.copyTo(targetFile, overwrite = true)
            logger.lifecycle("[Dropbox Sync] Debug APK copied to: ${targetFile.absolutePath} (${targetFile.length()} bytes)")
        }
    }
}

tasks.matching { it.name == "assembleDebug" || it.name == "packageDebug" }.configureEach {
    finalizedBy(copyDebugApkToDropbox)
}

