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

    // Compile read-only BLE diagnostics and the offline authentication state model.
    // No AAPS pump drivers, closed-loop algorithms, Firebase or AAPS app dependency.
    sourceSets {
        getByName("main").java.srcDir("../pump/ypsopump/src/main/kotlin/app/aaps/pump/ypsopump/ble")
        getByName("main").java.srcDir("../pump/ypsopump/src/main/kotlin/app/aaps/pump/ypsopump/auth")
        getByName("main").java.srcDir("../pump/ypsopump/src/main/kotlin/app/aaps/pump/ypsopump/crypto")
    }

    flavorDimensions.add("standard")
    productFlavors {
        create("full") {
            dimension = "standard"
        }
    }
}

dependencies {
    // Only required because the shared BLE class has @Inject annotations for AAPS.
    implementation("javax.inject:javax.inject:1")
}
