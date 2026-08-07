plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.hexcorp.ringr"
    compileSdk = 35

    fun gitVersionCode(): Int {
    return try {
        val out = providers.exec {
            commandLine("git", "rev-list", "--count", "HEAD")
        }.standardOutput.asText.get().trim()
        out.toInt()
    } catch (e: Exception) {
        1
    }
}

fun gitVersionName(): String {
    return try {
        val out = providers.exec {
            commandLine("git", "describe", "--tags", "--always")
        }.standardOutput.asText.get().trim()
        out.removePrefix("v")
    } catch (e: Exception) {
        "1.0"
    }
}

android {
    defaultConfig {
        applicationId = "com.hexcorp.ringr"
        minSdk = ...
        targetSdk = ...
        versionCode = gitVersionCode()
        versionName = gitVersionName()
    }
}
    }

    signingConfigs {
    if (System.getenv("KEYSTORE_PATH") != null) {
        create("release") {
            storeFile = file(System.getenv("KEYSTORE_PATH")!!)
            storePassword = System.getenv("STORE_PASSWORD") ?: ""
            keyAlias = System.getenv("KEY_ALIAS") ?: ""
            keyPassword = System.getenv("KEY_PASSWORD") ?: ""
            }
        }
    }

    splits {
        abi {
            isEnable = false
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.findByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
        }
    }

    buildFeatures {
        compose = true
    }

    packaging {
        jniLibs {
            useLegacyPackaging = true
            excludes += "**/libpython.zip.so"
        }
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)

    // Image loading for thumbnails
    implementation(libs.coil.compose)

    // Lottie for loading animation
    implementation(libs.lottie.compose)

    // NewPipeExtractor for YouTube metadata + stream extraction (F-Droid friendly)
    implementation("com.github.teamnewpipe:NewPipeExtractor:v0.26.4")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs_nio:2.1.5")

    implementation(libs.kotlinx.coroutines.android)

    debugImplementation(libs.androidx.ui.tooling)
}
