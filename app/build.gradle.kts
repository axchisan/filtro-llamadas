plugins {
    id("com.android.application")
}

android {
    namespace = "com.axchisan.filtro"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.axchisan.filtro"
        minSdk = 29
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            // Uso personal: se firma con la llave de depuración para instalarla por USB.
            signingConfig = signingConfigs.getByName("debug")
        }
    }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
}
