plugins {
    alias(libs.plugins.android.application)
    // app/google-services.json(Firebase 설정 파일)을 읽어서 앱에 연결
    alias(libs.plugins.google.services)
}

android {
    namespace = "com.modo.app"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.modo.app"
        minSdk = 26
        targetSdk = 37
        versionCode = 3
        versionName = "0.2.1"
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
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.material)

    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)
    // 회의록 AI 요약 (Firebase AI Logic · Gemini Developer API). App Check 필수 → 개발용 디버그 공급자
    implementation(libs.firebase.ai)
    implementation(libs.firebase.appcheck.debug)
    // Google 로그인 (Credential Manager → Firebase Auth)
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services.auth)
    implementation(libs.googleid)

    testImplementation(libs.junit)
    // 단위 테스트에서 진짜 org.json 사용 (안드로이드 jar의 org.json은 빈 껍데기)
    testImplementation(libs.org.json)
}
