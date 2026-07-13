plugins {
    id("riffle.android.application")
}

android {
    namespace = "com.riffle.app"

    defaultConfig {
        applicationId = "com.riffle.app"
        versionCode =
            providers.environmentVariable("RIFFLE_VERSION_CODE")
                .map(String::toInt)
                .getOrElse(1)
        versionName =
            providers.environmentVariable("RIFFLE_VERSION_NAME")
                .getOrElse("0.1.0-alpha01")
    }

    signingConfigs {
        create("release") {
            val storeFilePath = providers.environmentVariable("RIFFLE_SIGNING_STORE_FILE")
            val storePasswordValue = providers.environmentVariable("RIFFLE_SIGNING_STORE_PASSWORD")
            val keyAliasValue = providers.environmentVariable("RIFFLE_SIGNING_KEY_ALIAS")
            val keyPasswordValue = providers.environmentVariable("RIFFLE_SIGNING_KEY_PASSWORD")

            if (
                storeFilePath.isPresent &&
                storePasswordValue.isPresent &&
                keyAliasValue.isPresent &&
                keyPasswordValue.isPresent
            ) {
                storeFile = file(storeFilePath.get())
                storePassword = storePasswordValue.get()
                keyAlias = keyAliasValue.get()
                keyPassword = keyPasswordValue.get()
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
        }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }
}

dependencies {
    implementation(project(":core:domain"))

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.window)

    testImplementation(libs.junit)
    testImplementation(libs.org.json)

    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.test.ext.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
