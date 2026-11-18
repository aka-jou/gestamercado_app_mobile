plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "mx.gestamercado"
    compileSdk = 34

    defaultConfig {
        applicationId = "mx.gestamercado"
        minSdk = 26
        targetSdk = 34
        versionCode = 2
        versionName = "1.1"
        // URL del backend Spring Boot. Vacía = modo simulado (datos en memoria).
        // Ejemplo: ./gradlew assembleDebug -PgestaApiUrl=http://10.0.2.2:8080
        val apiUrl = (project.findProperty("gestaApiUrl") as String?) ?: ""
        buildConfigField("String", "API_URL", "\"$apiUrl\"")
        manifestPlaceholders["usesCleartext"] = "false"
    }

    buildTypes {
        debug {
            // Permite http:// hacia un backend local durante el desarrollo.
            manifestPlaceholders["usesCleartext"] = "true"
        }
        release {
            isMinifyEnabled = false
            // Firma con la clave de depuración para poder instalar el APK directamente.
            signingConfig = signingConfigs.getByName("debug")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.09.03")
    implementation(composeBom)
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
    implementation("com.google.zxing:core:3.5.3")
    testImplementation("junit:junit:4.13.2")
}
