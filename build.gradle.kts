// Top-level build file where you can add common configuration options

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    id("com.google.gms.google-services") version "4.4.2" apply false
}

// 🚫❌ NO buildscript { repositories {} } here anymore
// 🚫❌ NO allprojects { repositories {} } here anymore
// ✅ Repositories already handled inside settings.gradle.kts
