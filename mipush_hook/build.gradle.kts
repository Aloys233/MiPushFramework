plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

val hookedLib = file("libs/miuipushsdkshared_3_7_9_hooked.jar")
val originalLib = file("libs/miuipushsdkshared_3_7_9.jar")
val mipushLib = if (hookedLib.exists()) hookedLib else originalLib
extra["mipushLib"] = mipushLib

android {
    namespace = "com.nihility.mipush_hook"
    compileSdk = 37

    defaultConfig {
        minSdk = 21

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
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
}

dependencies {
    api(files(mipushLib))
    api("org.aspectj:aspectjrt:1.9.22.1")
    implementation("androidx.startup:startup-runtime:1.1.1")

    implementation("androidx.core:core-ktx:1.10.1")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.8.0")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
}