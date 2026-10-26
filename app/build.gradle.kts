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
        versionCode = 1
        // CI commit marker: the log screen prints this, so a report always says which build ran
        versionName = "0.1.0-" + (System.getenv("GITHUB_SHA")?.take(7) ?: "local")
    }

    signingConfigs {
        create("ci") {
            storeFile = rootProject.file("ci-release.jks")
            // the same certificate on every build, otherwise installing an update means uninstalling
            // the app first and Android throws away the keystore key with all the stored data
            storePassword = System.getenv("RELEASE_STORE_PASSWORD") ?: "blob-tmp-2026"
            keyAlias = "blob"
            keyPassword = System.getenv("RELEASE_KEY_PASSWORD") ?: System.getenv("RELEASE_STORE_PASSWORD") ?: "blob-tmp-2026"
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
