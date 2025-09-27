plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.example.wellnesstracker"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.wellnesstracker"
        minSdk = 24
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
        // Use Java 17 toolchain; Gradle can auto-download when configured in gradle.properties
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        // Enable desugaring to use java.time on minSdk < 26
        isCoreLibraryDesugaringEnabled = true
    }
    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        // Required for ActivityMainBinding used in MainActivity
        viewBinding = true
    }
}

// Configure Kotlin toolchain for Gradle to use JDK 17 (auto-download enabled in gradle.properties)
kotlin {
    jvmToolchain(17)
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)

    // Navigation components
    implementation(libs.navigation.ui.ktx)
    implementation(libs.navigation.fragment.ktx)

    // JSON serialization
    implementation(libs.gson)

    // Lifecycle - ViewModel and LiveData
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.livedata.ktx)

    // Fragment KTX helpers
    implementation(libs.androidx.fragment.ktx)

    // RecyclerView
    implementation(libs.androidx.recyclerview)

    // AndroidX Preference for settings screen
    implementation("androidx.preference:preference-ktx:1.2.1")

    // MPAndroidChart for mood trend line chart
    implementation("com.github.PhilJay:MPAndroidChart:v3.1.0")

    // Desugaring support for java.time
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.0.4")

    // WorkManager for background hydration reminders
    implementation("androidx.work:work-runtime-ktx:2.8.1")

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
