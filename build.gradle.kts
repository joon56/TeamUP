// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    // AGP 9 내장 Kotlin이 이 버전의 Kotlin 컴파일러를 쓰도록 지정 (모듈에는 적용하지 않음)
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.google.services) apply false
}
