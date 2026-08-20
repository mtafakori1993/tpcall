plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "ir.tafakoritech.tpcall"
    compileSdk {
        version = release(37) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "ir.tafakoritech.tpcall"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "BASE_URL", "\"http://192.168.1.200:9090\"")
        buildConfigField("String", "WS_URL", "\"ws://192.168.1.200:9090/api/v1/ws\"")
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
        compose = true
        buildConfig = true
    }
    testOptions {
        unitTests {
            isReturnDefaultValues = true
        }
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.webrtc.android)
    implementation(libs.androidx.core.telecom)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}

// Pin transitive androidx/kotlin versions to artifacts that are available in the
// offline Gradle cache of the build environment (Google Maven is unreachable here).
configurations.configureEach {
    resolutionStrategy {
        force(
            "androidx.activity:activity:1.13.0",
            "androidx.activity:activity-compose:1.13.0",
            "androidx.activity:activity-ktx:1.13.0",
            "androidx.annotation:annotation:1.10.0",
            "androidx.annotation:annotation-experimental:1.4.1",
            "androidx.arch.core:core-common:2.2.0",
            "androidx.arch.core:core-runtime:2.2.0",
            "androidx.autofill:autofill:1.0.0",
            "androidx.collection:collection:1.5.0",
            "androidx.collection:collection-ktx:1.5.0",
            "androidx.concurrent:concurrent-futures:1.2.0",
            "androidx.concurrent:concurrent-futures-ktx:1.2.0",
            "androidx.core:core:1.19.0",
            "androidx.core:core-ktx:1.19.0",
            "androidx.core:core-viewtree:1.0.0",
            "androidx.customview:customview-poolingcontainer:1.0.0",
            "androidx.documentfile:documentfile:1.0.0",
            "androidx.emoji2:emoji2:1.4.0",
            "androidx.graphics:graphics-path:1.0.1",
            "androidx.lifecycle:lifecycle-common:2.11.0",
            "androidx.lifecycle:lifecycle-common-java8:2.11.0",
            "androidx.lifecycle:lifecycle-livedata:2.11.0",
            "androidx.lifecycle:lifecycle-livedata-core:2.11.0",
            "androidx.lifecycle:lifecycle-process:2.11.0",
            "androidx.lifecycle:lifecycle-runtime:2.11.0",
            "androidx.lifecycle:lifecycle-runtime-compose:2.11.0",
            "androidx.lifecycle:lifecycle-runtime-ktx:2.11.0",
            "androidx.lifecycle:lifecycle-service:2.11.0",
            "androidx.lifecycle:lifecycle-viewmodel:2.11.0",
            "androidx.lifecycle:lifecycle-viewmodel-android:2.11.0",
            "androidx.lifecycle:lifecycle-viewmodel-compose:2.11.0",
            "androidx.lifecycle:lifecycle-viewmodel-ktx:2.11.0",
            "androidx.lifecycle:lifecycle-viewmodel-savedstate:2.11.0",
            "androidx.profileinstaller:profileinstaller:1.4.0",
            "androidx.savedstate:savedstate:1.4.0",
            "androidx.savedstate:savedstate-android:1.4.0",
            "androidx.startup:startup-runtime:1.1.1",
            "androidx.tracing:tracing:1.2.0",
            "androidx.transition:transition:1.6.0",
            "androidx.versionedparcelable:versionedparcelable:1.1.1",
            "androidx.window:window:1.5.0",
            "androidx.window:window-core:1.5.0",
            "org.jetbrains:annotations:23.0.0",
            "org.jetbrains.kotlin:kotlin-stdlib:2.2.10",
            "org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2",
            "org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2",
            "org.jetbrains.kotlinx:kotlinx-serialization-core:1.7.3",
            "com.squareup.okio:okio:3.6.0",
            "com.squareup.okio:okio-jvm:3.6.0",
            "androidx.test:core:1.7.0",
            "androidx.test:monitor:1.8.0",
            "androidx.test:runner:1.7.0",
            "androidx.test.espresso:espresso-core:3.7.0",
            "androidx.test.espresso:espresso-idling-resource:3.7.0",
            "androidx.test.ext:junit:1.3.0"
        )
    }
}
