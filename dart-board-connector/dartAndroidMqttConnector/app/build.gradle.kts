plugins {
    alias(libs.plugins.androidApplication)
}

// Version: bei jeder Code-Änderung erhöhen (CI lehnt PRs ab, deren Version schon als
// Release-Tag android-connector-v<version> existiert), siehe .github/workflows/android-connector.yml.
// versionCode wird abgeleitet (MAJOR*10000 + MINOR*100 + PATCH), damit Updates installierbar bleiben.
val appVersion = "1.0.0"
val appVersionCode = appVersion.split(".").map(String::toInt).let { (major, minor, patch) ->
    major * 10000 + minor * 100 + patch
}

// Release-Signatur nur, wenn die CI den Keystore bereitstellt; lokal entsteht ein unsigniertes Release-APK
val keystoreFile: String? = System.getenv("ANDROID_KEYSTORE_FILE")

android {
    namespace = "it.tobaben.dartandroidmqttconnector"
    compileSdk = 34

    defaultConfig {
        applicationId = "it.tobaben.dartandroidmqttconnector"
        minSdk = 29
        targetSdk = 34
        versionCode = appVersionCode
        versionName = appVersion

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (keystoreFile != null) {
            create("release") {
                storeFile = file(keystoreFile)
                storePassword = System.getenv("ANDROID_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("ANDROID_KEY_ALIAS")
                keyPassword = System.getenv("ANDROID_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            if (keystoreFile != null) {
                signingConfig = signingConfigs.getByName("release")
            }
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
}

dependencies {

    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
    implementation("org.eclipse.paho:org.eclipse.paho.client.mqttv3:1.2.5")
}