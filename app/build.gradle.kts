plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val buildNumber = (project.findProperty("buildNumber") as String?)?.toIntOrNull() ?: 1

android {
    namespace = "com.thousandlabs.dlock"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.thousandlabs.dlock"
        minSdk = 29
        targetSdk = 35
        versionCode = buildNumber
        versionName = "1.$buildNumber"
        vectorDrawables { useSupportLibrary = true }
    }

    // Clé de signature fixe : chaque nouvelle version s'installe par-dessus l'ancienne sans perdre les réglages.
    signingConfigs {
        create("release") {
            storeFile = file("../signing/dlock.p12")
            storePassword = "ThousandLabsDLock"
            keyAlias = "dlock"
            keyPassword = "ThousandLabsDLock"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("release")
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
    packaging {
        resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" }
    }
    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.10.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.foundation:foundation")

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
}
