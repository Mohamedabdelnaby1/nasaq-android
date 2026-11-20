plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Tajawal TTFs and OFL are bundled in src/main/res/font and assets.
android {
    namespace = "com.yawmi.diabetes"
    compileSdk = 36
    buildToolsVersion = "36.0.0"
    defaultConfig {
        applicationId = "com.yawmi.diabetes"
        minSdk = 26
        targetSdk = 36
        versionCode = 21
        versionName = "0.18.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_1_8; targetCompatibility = JavaVersion.VERSION_1_8; isCoreLibraryDesugaringEnabled = true }
    kotlinOptions { jvmTarget = "1.8" }
    buildFeatures { compose = true; buildConfig = true }
    buildTypes { release { isMinifyEnabled = true; proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro") } }
    bundle { language { enableSplit = false } }
    lint { abortOnError = true }
    testOptions {
        unitTests.isIncludeAndroidResources = true
        unitTests.all {
            it.systemProperty("robolectric.dependency.repo.url","https://repo.maven.apache.org/maven2")
            it.systemProperty("maven.repo.local",rootProject.layout.projectDirectory.dir(".tools/robolectric-cache").asFile.absolutePath)
            it.maxHeapSize="2g"
        }
    }
}
dependencies {
    implementation("com.google.mlkit:text-recognition:16.0.1")
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.5")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.activity:activity-compose:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.10.0")
    implementation("androidx.compose.ui:ui:1.9.5")
    implementation("androidx.compose.ui:ui-tooling-preview:1.9.5")
    implementation("androidx.compose.foundation:foundation:1.9.5")
    implementation("androidx.compose.animation:animation:1.9.5")
    implementation("androidx.compose.material3:material3:1.4.0")
    debugImplementation("androidx.compose.ui:ui-tooling:1.9.5")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
    testImplementation("org.robolectric:robolectric:4.15.1")
    testImplementation("androidx.compose.ui:ui-test-junit4:1.9.5")
    debugImplementation("androidx.compose.ui:ui-test-manifest:1.9.5")
}
