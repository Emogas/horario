plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
android {
    namespace = "com.mogas.timemanager"
    compileSdk = 34
    defaultConfig {
        applicationId = "com.mogas.timemanager"
        minSdk = 27
        targetSdk = 34
        versionCode = (System.getenv("GITHUB_RUN_NUMBER") ?: "1").toInt()
        versionName = "1.1"
    }
    signingConfigs {
        create("fixed") {
            storeFile = file("mtm.keystore")
            storePassword = "mogas123"
            keyAlias = "mtm"
            keyPassword = "mogas123"
        }
    }
    buildTypes {
        getByName("debug") { signingConfig = signingConfigs.getByName("fixed") }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}
