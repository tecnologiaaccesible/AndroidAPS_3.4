plugins {
    id("com.android.application")
    id("kotlin-android")
    id("android-app-dependencies")
}

android {
    namespace = "app.aaps.ypsolab"

    defaultConfig {
        applicationId = "app.aaps.ypsopump.lab"
        minSdk = Versions.minSdk
        targetSdk = Versions.targetSdk
        versionCode = 1
        versionName = "0.1.0-readonly"
    }

    // Match the only AAPS pump-module flavor required for this standalone app.
    flavorDimensions.add("standard")
    productFlavors {
        create("full") {
            dimension = "standard"
        }
    }
}

dependencies {
    // Reuse the tested read-only BLE diagnostics; do not import AAPS's :app.
    implementation(project(":pump:ypsopump"))
}
