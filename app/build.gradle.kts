plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "ir.v2rayng.compose"
    compileSdk = 35

    defaultConfig {
        applicationId = "ir.blob.vpn"
        minSdk = 26
        targetSdk = 35
        versionCode = 12
        versionName = "1.2"
        // the server identity is assembled in C (src/main/cpp/blobgate.c) and handed over JNI
        externalNativeBuild {
            cmake {
                arguments += listOf("-DANDROID_STL=none")
                abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64", "x86")
            }
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    signingConfigs {
        create("ci") {
            storeFile = rootProject.file("ci-release.jks")
            storePassword = "blob-tmp-2026"
            keyAlias = "blob"
            keyPassword = "blob-tmp-2026"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            val ks = rootProject.file("ci-release.jks")
            if (ks.exists()) {
                signingConfig = signingConfigs.getByName("ci")
            }
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
}

dependencies {
    implementation(files("libs/libv2ray.aar"))
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
}
