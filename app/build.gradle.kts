plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

layout.buildDirectory = rootProject.layout.buildDirectory.dir("app")

android {
    namespace = "com.hp.vpn"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.hp.vpn"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
        ndk { abiFilters += listOf("arm64-v8a") }
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }
    sourceSets {
        getByName("main") {
            jniLibs.srcDir(rootProject.file("build/native/jniLibs"))
        }
    }
    buildFeatures { compose = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }
    packaging { jniLibs { useLegacyPackaging = true } }
    lint { disable += "QueryAllPackagesPermission" }
}

dependencies {
    implementation(files(rootProject.file("build/native/hp-proxy.aar")))
    implementation(platform("androidx.compose:compose-bom:2024.09.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.12.4")
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
}
