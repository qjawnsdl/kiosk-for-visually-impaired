plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.example.bus1"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.bus1"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
}

dependencies {

    implementation ("androidx.appcompat:appcompat:1.6.1")
    implementation ("com.google.android.material:material:1.9.0")
    implementation ("androidx.activity:activity:1.7.2")
    implementation ("androidx.constraintlayout:constraintlayout:2.1.4")

    // CameraX 라이브러리
    implementation ("androidx.camera:camera-core:1.2.0")
    implementation ("androidx.camera:camera-camera2:1.2.0")
    implementation ("androidx.camera:camera-lifecycle:1.2.0")
    implementation ("androidx.camera:camera-view:1.2.0")


    // ML Kit 포즈 감지 라이브러리
    implementation ("com.google.mlkit:pose-detection:17.0.0")
    implementation ("com.google.mlkit:pose-detection-common:17.0.0")
    implementation ("com.google.mlkit:vision-common:18.5.0")
    implementation ("com.google.mlkit:common:16.5.0")

    // 테스트 라이브러리
    testImplementation ("junit:junit:4.13.2")
    androidTestImplementation ("androidx.test.ext:junit:1.1.5")
    androidTestImplementation ("androidx.test.espresso:espresso-core:3.5.1")
}