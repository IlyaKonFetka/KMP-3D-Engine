plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.library)
}

kotlin {
    jvm()
    androidTarget {
        compilations.all {
            kotlinOptions { jvmTarget = "11" }
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.coroutines.core)
        }
        jvmMain.dependencies {
            implementation(libs.lwjgl.core)
            implementation(libs.lwjgl.opengl)
        }
        // androidMain uses android.opengl from platform SDK — no extra deps needed
    }
}

android {
    namespace = "com.example.engine3d"
    compileSdk = 34
    defaultConfig { minSdk = 21 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}
