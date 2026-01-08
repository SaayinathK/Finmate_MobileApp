plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
<<<<<<< HEAD
    id("kotlin-kapt") // Required for Room annotation processing
=======
    id("com.google.gms.google-services")
>>>>>>> 4d2b1dc2a7003542a5242f5614b846d15b4a2e97
}

android {
    namespace = "com.example.financetracker"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.financetracker"
        minSdk = 26
        targetSdk = 35
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
        isCoreLibraryDesugaringEnabled = true
    }

    kotlinOptions {
        jvmTarget = "11"
    }

    buildFeatures {
        viewBinding = true
    }

    packaging {
        resources {
            excludes += setOf(
                "META-INF/DEPENDENCIES",
                "META-INF/LICENSE",
                "META-INF/LICENSE.txt",
                "META-INF/license.txt",
                "META-INF/NOTICE",
                "META-INF/NOTICE.txt",
                "META-INF/notice.txt",
                "META-INF/ASL2.0"
            )
        }
    }
}

dependencies {
    // Core Android
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")

    // Material Design
    implementation("com.google.android.material:material:1.11.0")

    // Layout & UI
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.recyclerview:recyclerview:1.3.2")

    // Lifecycle
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.6.2")
    implementation("androidx.lifecycle:lifecycle-livedata-ktx:2.6.2")

<<<<<<< HEAD
    // Gson for JSON Serialization
    implementation("com.google.code.gson:gson:2.10.1")

    // MPAndroidChart
    implementation("com.github.PhilJay:MPAndroidChart:v3.1.0")

    // Room (ORM for SQLite)
    implementation("androidx.room:room-runtime:2.6.1")
    kapt("androidx.room:room-compiler:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")

    // XLSX handling
    implementation("org.apache.poi:poi:5.2.3")
    implementation("org.apache.poi:poi-ooxml:5.2.3")

=======
    // JSON
    implementation("com.google.code.gson:gson:2.10.1")

    // Charts
    implementation("com.github.PhilJay:MPAndroidChart:v3.1.0")

    // Apache POI for Excel
    implementation("org.apache.poi:poi:5.2.3")
    implementation("org.apache.poi:poi-ooxml:5.2.3")

    // Firebase Analytics
    implementation(platform("com.google.firebase:firebase-bom:33.13.0"))
    implementation("com.google.firebase:firebase-analytics")

    // Google Sign-In
    implementation("com.google.android.gms:play-services-auth:21.1.0")

    // Google Sheets API
    implementation("com.google.apis:google-api-services-sheets:v4-rev581-1.25.0") // ✅ Correct one
    implementation("com.google.api-client:google-api-client-android:1.35.2")
    implementation("com.google.api-client:google-api-client-gson:1.35.2")
    implementation("com.google.oauth-client:google-oauth-client-jetty:1.35.0")

    // Java 8+
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.3")

>>>>>>> 4d2b1dc2a7003542a5242f5614b846d15b4a2e97
    // Testing
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")

    // Retrofit for API communication
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

}

