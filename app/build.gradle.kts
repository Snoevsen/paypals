plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.google.services)
}

android {
    namespace = "com.sdu.paypals"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.sdu.paypals"
        minSdk = 24
        targetSdk = 37

        val propVersionName = project.findProperty("versionName")
            ?.toString()
            ?.trim()
            ?.removePrefix("v")
            ?.takeIf { it.isNotEmpty() }
            ?: "1.0.0"

        val propVersionCode = project.findProperty("versionCode")
            ?.toString()
            ?.toIntOrNull()
            ?: run {
                val parts = propVersionName.split(".").mapNotNull { it.toIntOrNull() }
                if (parts.size == 3) {
                    (parts[0] * 10000 + parts[1] * 100 + parts[2]).coerceAtLeast(1)
                } else {
                    1
                }
            }

        versionCode = propVersionCode
        versionName = propVersionName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("debug")
            optimization {
                enable = true
                packageScope = setOf("androidx.**", "kotlin.**", "kotlinx.**")
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
}

dependencies {
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.savedstate)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.navigation.fragment.ktx)
    implementation(libs.androidx.navigation.ui.ktx)
    implementation(libs.material)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)
    implementation(libs.firebase.database)
    implementation(libs.zxing.core)
    implementation(libs.zxing.android.embedded)
    implementation(libs.androidx.core.splashscreen)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)

}