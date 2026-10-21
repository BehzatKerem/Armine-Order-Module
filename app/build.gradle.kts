plugins {
    // AGP 9+ built-in Kotlin: org.jetbrains.kotlin.android plugin'i ayrı uygulanmaz.
    alias(libs.plugins.android.application)
}

val releaseKeystoreFile = System.getenv("KEYSTORE_FILE")
val releaseStorePassword = System.getenv("KEYSTORE_PASSWORD")
val releaseKeyAlias = System.getenv("KEY_ALIAS")
val releaseKeyPassword = System.getenv("KEY_PASSWORD")
val hasReleaseKeystore = listOf(
    releaseKeystoreFile,
    releaseStorePassword,
    releaseKeyAlias,
    releaseKeyPassword,
).all { !it.isNullOrBlank() }

android {
    namespace = "com.example.armineordermodule"
    compileSdk {
        // androidx.core:1.19.0 derleme için en az compileSdk 37 gerektirir.
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.example.armineordermodule"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        // GitHub Action bu dört ortam değişkenini secret'lardan doldurur.
        // Yerelde yoksa release derlemesi debug anahtarıyla imzalanır.
        if (hasReleaseKeystore) {
            create("release") {
                storeFile = file(releaseKeystoreFile!!)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        release {
            signingConfig = if (hasReleaseKeystore) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
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

    buildFeatures {
        buildConfig = true
    }
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(files("libs/lcrrgxmodule-release.aar"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.okhttp)
    implementation("androidx.recyclerview:recyclerview:1.4.0")
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
