plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)

    //KSP FOR THE ROOM
    alias(libs.plugins.ksp)
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
//    kotlinOptions {
//        jvmTarget = "11"
//    }
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
    // MAP LIBRE NORMAL MAP
//    implementation("org.maplibre.compose:maplibre-compose-android:0.12.1")
//    implementation("org.maplibre.compose:maplibre-compose-material3:0.12.1")
    implementation("com.dayanruben.maplibre-compose:maplibre-compose-android:0.6.21")
    implementation("com.dayanruben.maplibre-compose:maplibre-compose-material3:0.6.21")
}

configurations.all {
    resolutionStrategy {
        dependencySubstitution {
            // Replace debug variant with release variant
            substitute(module("com.dayanruben.maplibre-compose:maplibre-compose-android-debug"))
                .using(module("com.dayanruben.maplibre-compose:maplibre-compose-android:0.6.21"))
                .because("Debug variant conflicts with release")
        }
    }
}