import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.kotlin.serialization)
    id("kotlin-parcelize")
    id("com.autonomousapps.dependency-analysis")
    id("com.google.gms.google-services")
}

val localProps = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

fun prop(key: String) = "\"${localProps.getProperty(key)}\""

android {
    namespace = "com.tabslify"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.paluss1122.tabslify"
        minSdk = 35
        targetSdk = 37
        versionCode = 1
        versionName = "1.0.0-beta01"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "SUPABASE_URL", prop("SUPABASE_URL"))
        buildConfigField("String", "SUPABASE_PUBLISHABLE_KEY", prop("SUPABASE_PUBLISHABLE_KEY"))
        buildConfigField("String", "LOCAL_DEVICE_NAME", prop("LOCAL_DEVICE_NAME"))
        buildConfigField("String", "DEBUG_SHA256", prop("DEBUG_SHA256"))
        buildConfigField("String", "RELEASE_SHA256", prop("RELEASE_SHA256"))
        buildConfigField("String", "VIRUSTOTAL_API_KEY", "\"${localProps.getProperty("VIRUSTOTAL_API_KEY", "")}\"")
    }

    signingConfigs {
        create("release") {
            storeFile = file("../keystore/my-release-key.jks")
            storePassword = localProps.getProperty("storePassword")
            keyAlias = "release-key"
            keyPassword = localProps.getProperty("keyPassword")
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".private"
            isDebuggable = false
            buildConfigField("boolean", "IS_DEV", "true")
        }
        create("fast") {
            initWith(getByName("debug"))
            isDebuggable = true
            matchingFallbacks += listOf("debug", "release")
        }
        release {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
            buildConfigField("boolean", "IS_DEV", "false")
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
    buildFeatures {
        buildConfig = true
        compose = true
    }
    androidResources {
        noCompress += listOf("task", "tflite")
    }
    packaging {
        resources {
            excludes += listOf(
                "/META-INF/DEPENDENCIES",
                "META-INF/LICENSE.md",
                "META-INF/LICENSE-notice.md",
                "META-INF/NOTICE.md",
                "dump_syms/**",
                "lib/**/dump_syms.bin"
            )

        }
        jniLibs {
            useLegacyPackaging = false
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.play.services.location)
    implementation(libs.androidx.media3.session)
    implementation(libs.androidx.compose.foundation.layout)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.runtime)
    implementation(libs.androidx.ui)
    implementation(libs.storage.kt)
    implementation(libs.postgrest.kt)
    implementation(libs.supabase.kt)
    implementation(libs.realtime.kt)
    implementation(libs.functions.kt)
    implementation(libs.coil.compose)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.room.runtime)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.ui)
    implementation(libs.androidx.media3.common)
    implementation(libs.androidx.biometric)
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.mediapipe.tasks.vision)
    implementation(libs.core)
    implementation(libs.zxing.android.embedded)
    debugImplementation(libs.androidx.compose.ui.tooling)
    "fastImplementation"(libs.leakcanary)
    "fastImplementation"(libs.leakcanary.android)
    ksp(libs.androidx.room.compiler)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.gson)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.osmdroid.android)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.autofill)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.ai)
    implementation(libs.multiplatform.markdown.renderer.android)
    implementation(libs.multiplatform.markdown.renderer.m3)
    implementation(libs.firebase.config)
    implementation(libs.firebase.appcheck.playintegrity)
    implementation(libs.firebase.appcheck.debug)
    implementation(libs.rhino)
    implementation(libs.firebase.messaging)
    implementation(libs.haze)
    implementation(libs.haze.blur)
}

tasks.configureEach {
    if (name.contains("Debug") &&
        (name.contains("ArtProfile") || name.contains("BaselineProfile"))
    ) {
        enabled = false
    }
}
