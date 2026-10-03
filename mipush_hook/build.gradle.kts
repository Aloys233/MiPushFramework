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

    sourceSets {
        // 本模块的切面类(com/nihility/**、com/xiaomi/**、Hook 等)必须由 AspectJ(ajc)编译,
        // 才会生成 aspectOf()/hasAspect() 与 advice 派发代码;交给 javac 会导致被织入的 SDK
        // 在运行时抛 NoSuchMethodError: MethodHooker.aspectOf()。
        // 因此这里不编译 main 源码,所有类统一由根项目 weaveMiPushHook 生成的 hooked jar 提供。
        getByName("main") {
            java.setSrcDirs(emptyList<String>())
        }
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