pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
        maven { url = uri("https://jitpack.io") } // 🔥 For libraries hosted on JitPack
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS) // ⚡ Best practice now
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") } // 🔥 JitPack if you need extra libraries (like MPAndroidChart)
    }
}

rootProject.name = "FinanceTracker" // ✅ No spaces in project name - good convention

include(":app") // ✅ Main App module
