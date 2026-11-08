plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

fun resolveGitVersionCode(): Int {
    val propCode = project.findProperty("versionCode")?.toString()?.toIntOrNull()
    if (propCode != null && propCode > 0) return propCode
    return try {
        val process = ProcessBuilder("git", "rev-list", "--count", "HEAD")
            .directory(rootProject.projectDir)
            .redirectErrorStream(true)
            .start()
        val output = process.inputStream.bufferedReader().readText().trim()
        process.waitFor()
        (output.toIntOrNull() ?: 0) + 200
    } catch (_: Exception) {
        200
    }
}
val resolvedVersionCode = resolveGitVersionCode()

android {
    namespace = "com.tuyenmai.openkey.android"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.tuyenmai.openkey.android"
        minSdk = 29
        targetSdk = 35
        versionCode = resolvedVersionCode
        versionName = "1.0.$resolvedVersionCode"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        getByName("debug") {
            storeFile = file(System.getenv("OPENKEY_KEYSTORE_PATH") ?: "openkey-debug.keystore")
            storePassword = System.getenv("OPENKEY_STORE_PASSWORD") ?: "android"
            keyAlias = System.getenv("OPENKEY_KEY_ALIAS") ?: "androiddebugkey"
            keyPassword = System.getenv("OPENKEY_KEY_PASSWORD") ?: "android"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("debug")
        }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
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

    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }

    buildFeatures {
        compose = true
    }

    testOptions {
        unitTests {
            isReturnDefaultValues = true
        }
    }
}

dependencies {
    implementation(project(":openkey-core"))

    implementation(platform("androidx.compose:compose-bom:2024.10.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.7")
    implementation("androidx.savedstate:savedstate-ktx:1.2.1")

    testImplementation("junit:junit:4.13.2")
    testImplementation("androidx.arch.core:core-testing:2.2.0")
}
