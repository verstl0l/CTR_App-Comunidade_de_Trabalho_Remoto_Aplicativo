import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    id("com.google.gms.google-services")
}

// ✅ Le as chaves do local.properties
val localProps = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

android {
    namespace = "com.example.plataformaremota"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.plataformaremota"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true

        // ✅ Expoe as chaves como BuildConfig (seguro)
        buildConfigField("String", "CLOUDINARY_CLOUD_NAME", "\"${localProps["CLOUDINARY_CLOUD_NAME"] ?: ""}\"")
        buildConfigField("String", "CLOUDINARY_UPLOAD_PRESET", "\"${localProps["CLOUDINARY_UPLOAD_PRESET"] ?: ""}\"")
        buildConfigField("String", "ONESIGNAL_APP_ID", "\"${localProps["ONESIGNAL_APP_ID"] ?: ""}\"")
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
    }

    kotlinOptions {
        jvmTarget = "11"
    }

    // ✅ Habilita o BuildConfig (necessario pra usar BuildConfig.CLOUDINARY_*)
    buildFeatures {
        buildConfig = true
    }
}

dependencies {
    // Recyclerview
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    // AndroidX
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.constraintlayout)

    // Splash Screen
    implementation(libs.androidx.core.splashscreen)

    // Lifecycle
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)

    // ========== FIREBASE ==========
    implementation(platform("com.google.firebase:firebase-bom:33.15.0"))
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-firestore")

    // Cloudinary
    implementation("com.cloudinary:cloudinary-android:3.0.2")

    // Glide
    implementation("com.github.bumptech.glide:glide:4.16.0")

    // PhotoView
    implementation("com.github.chrisbanes:PhotoView:2.3.0")

    // OneSignal
    implementation("com.onesignal:OneSignal:5.0.0")

    // MPAndroidChart (graficos)
    implementation("com.github.PhilJay:MPAndroidChart:v3.1.0")

    // Crop de imagem
    implementation("com.github.yalantis:ucrop:2.2.11")

    // Trim de vídeo
    implementation("com.github.a914-gowtham:android-video-trimmer:1.8.0")

    // Testes
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}