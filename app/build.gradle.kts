plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.bare.launcher"
    compileSdk = 35
    buildToolsVersion = "35.0.1"

    defaultConfig {
        applicationId = "com.bare.launcher"
        minSdk = 26
        targetSdk = 35
        versionCode = 49
        versionName = "0.49-compact-header"

        // v0.47: Doc export PRIMARY; Drive JSON next; paste.rs last. Selection is max versionCode.
        buildConfigField(
            "String",
            "UPDATE_JSON_URL",
            "\"https://docs.google.com/document/d/1Qn_N7-ZIB3yZzOSiNKzIU0AT3ZzTJxUb3PN2itGXPzg/export?format=txt\"",
        )
        buildConfigField(
            "String",
            "UPDATE_JSON_DRIVE_FILE_ID",
            "\"1TBoFYbGBX_JIK1lrUJjaIruDmxqKnP1H\"",
        )
        buildConfigField(
            "String",
            "UPDATE_JSON_FALLBACK_URL",
            "\"https://drive.usercontent.google.com/download?id=1TBoFYbGBX_JIK1lrUJjaIruDmxqKnP1H&export=download&confirm=t\"",
        )
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
        buildConfig = true
    }

    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.10.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.security:security-crypto:1.0.0")
    implementation("androidx.biometric:biometric:1.1.0")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
