plugins {
    alias(libs.plugins.android.application)
    // app/google-services.json(Firebase 설정 파일)을 읽어서 앱에 연결
    alias(libs.plugins.google.services)
}

android {
    namespace = "com.teamup.app"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.teamup.app"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        viewBinding = true
    }
    androidResources {
        // 앱별 언어 설정(Android 13+) 목록을 values-xx 폴더에서 자동 생성
        generateLocaleConfig = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.lifecycle.livedata.ktx)
    implementation(libs.material)

    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)

    testImplementation(libs.junit)
}
