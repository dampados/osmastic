plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)

    //KSP FOR THE ROOM
    alias(libs.plugins.ksp)

    // HILT bc viewModel() factory bc context for the ROOM
//    id("com.google.dagger.hilt.android")
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.example.osmastic"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        applicationId = "com.example.osmastic"
        minSdk = 29
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
        }
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material3.adaptive.navigation.suite)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.navigation.compose)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    //new ones:
    implementation("org.osmdroid:osmdroid-android:6.1.18")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.4")
    //ROOM LIBS + KSP
    implementation(libs.androidx.room.runtime)
    ksp(libs.androidx.room.compiler) // Use KSP, not kapt or annotationProcessor
    implementation(libs.androidx.room.ktx) // For coroutine support
    // OH MY GOD BUILT IN ICONS ARE BACK
    implementation("androidx.compose.material:material-icons-extended")
    // datastore from jetpack compose for shared prefs
    implementation("androidx.datastore:datastore-preferences:1.1.0")
    // BONUS PACK FOR SHORT AND LONG TAP
//    implementation("com.github.MKergall:osmbonuspack:6.9.0")
    // HILT
    implementation("com.google.dagger:hilt-android:2.57.1")
    ksp("com.google.dagger:hilt-compiler:2.57.1")
    implementation("androidx.hilt:hilt-navigation-compose:1.2.0")
    // MESHTASTIC INTENT (AIDL) ATAK LIKE CONNECTION
    // Replace 'v2.7.13' with the specific version you need
    val meshtasticVersion = "2.7.13-open.3"

    // The core AIDL interface and Intent constants
    implementation("com.github.meshtastic.Meshtastic-Android:meshtastic-android-api:$meshtasticVersion")

    // Data models (DataPacket, MeshUser, NodeInfo, etc.)
    implementation("com.github.meshtastic.Meshtastic-Android:meshtastic-android-model:$meshtasticVersion")

    // Protobuf definitions (PortNum, Telemetry, etc.)
    implementation("com.github.meshtastic.Meshtastic-Android:meshtastic-android-proto:$meshtasticVersion")
}
