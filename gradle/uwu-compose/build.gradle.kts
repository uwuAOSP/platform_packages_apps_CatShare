import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "org.uwuaosp.compose"
    compileSdk = 36

    defaultConfig {
        minSdk = 29
    }

    sourceSets {
        getByName("main") {
            manifest.srcFile("../../../../../uwu-sdk/uwuCompose/AndroidManifest.xml")
            kotlin.setSrcDirs(listOf("../../../../../uwu-sdk/uwuCompose/src"))
            res.setSrcDirs(listOf("../../../../../uwu-sdk/uwuCompose/res"))
        }
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_11)
        freeCompilerArgs.add("-Xjvm-default=all")
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation("androidx.compose.foundation:foundation")
    implementation(libs.androidx.compose.material.icons.extended)
    implementation("androidx.compose.material3:material3:1.5.0-alpha16")
    implementation("androidx.compose.runtime:runtime-saveable")
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
}
