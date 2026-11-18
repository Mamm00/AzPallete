plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.alazhartex.shadecart"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.alazhartex.shadecart"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        versionCode = 4
        versionName = "2.0.0"

        val syncBaseUrl = (System.getenv("ALAZHARTEX_SYNC_BASE_URL") ?: "")
            .replace("\"", "\\\"")
        buildConfigField("String", "SYNC_BASE_URL", "\"$syncBaseUrl\"")

        testInstrumentationRunner = "com.alazhartex.shadecart.HiltTestRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    sourceSets {
        getByName("androidTest").assets.srcDirs("$projectDir/schemas")
    }

    signingConfigs {
        // Optional release signing via ALAZHARTEX_KEYSTORE_* CI secrets; falls back to debug signing when unset.
        create("release") {
            val ksPath = System.getenv("ALAZHARTEX_KEYSTORE_PATH")
            if (!ksPath.isNullOrBlank()) {
                storeType = System.getenv("ALAZHARTEX_KEYSTORE_TYPE") ?: "PKCS12"
                storeFile = file(ksPath)
                storePassword = System.getenv("ALAZHARTEX_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("ALAZHARTEX_KEY_ALIAS")
                keyPassword = System.getenv("ALAZHARTEX_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            // Chapter 12.1: Enable R8 code shrinking and resource shrinking for production releases.
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = if (!System.getenv("ALAZHARTEX_KEYSTORE_PATH").isNullOrBlank()) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
        debug {
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
        animationsDisabled = true
    }

    lint {
        lintConfig = rootProject.file("lint.xml")
        abortOnError = true
        checkReleaseBuilds = false
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

kotlin {
    jvmToolchain(17)
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.incremental", "true")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)

    // Glance AppWidget
    implementation(libs.androidx.glance.appwidget)
    implementation(libs.androidx.glance.material3)

    // Hilt DI
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)
    implementation(libs.hilt.ext.work)
    ksp(libs.hilt.ext.compiler)

    // Room persistence
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // WorkManager & DataStore
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.datastore.preferences)

    // CameraX (1.6.2 stable)
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.compose)
    implementation(libs.androidx.camera.viewfinder.compose)
    implementation(libs.androidx.camera.viewfinder.core)

    // Imaging, Cropper, QR & Dominant Color Palette
    implementation(libs.canhub.image.cropper)
    implementation(libs.androidx.palette.ktx)
    implementation(libs.androidx.exifinterface)
    implementation(libs.coil.compose)
    implementation(libs.zxing.core)

    // Networking: Retrofit + OkHttp + Gson
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.gson)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.gson)

    // ML Kit Vision, Scanners & Nearby P2P
    implementation(libs.mlkit.text.recognition)
    implementation(libs.mlkit.subject.segmentation)
    implementation(libs.mlkit.document.scanner)
    implementation(libs.mlkit.code.scanner)
    implementation(libs.play.services.base)
    implementation(libs.play.services.nearby)

    // Coroutines & JSON
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.guava)
    implementation(libs.kotlinx.coroutines.play.services)
    implementation(libs.kotlinx.serialization.json)

    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    // JVM Unit Tests
    testImplementation(libs.junit)
    testImplementation(libs.truth)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.androidx.room.testing)
    testImplementation(libs.okhttp.mockwebserver)

    // Instrumented Compose UI & Room Migration Tests
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.rules)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.espresso.intents)
    androidTestImplementation(libs.androidx.room.testing)
    androidTestImplementation(libs.androidx.work.testing)
    androidTestImplementation(libs.truth)
    androidTestImplementation(libs.hilt.android.testing)
    kspAndroidTest(libs.hilt.compiler)
}
