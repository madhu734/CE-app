plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

val releaseKeyPath = providers.environmentVariable("KEYSTORE_PATH").orNull
val releaseStorePassword = providers.environmentVariable("STORE_PASSWORD").orNull
val releaseKeyPassword = providers.environmentVariable("KEY_PASSWORD").orNull
val hasReleaseKey = listOf(releaseKeyPath, releaseStorePassword, releaseKeyPassword).all { !it.isNullOrBlank() }
val releaseKeyAlias = providers.environmentVariable("KEY_ALIAS").getOrElse("upload")
val appVersionCode = providers.environmentVariable("APP_VERSION_CODE").map {
    it.toIntOrNull()?.takeIf { code -> code in 1..2_100_000_000 }
        ?: error("APP_VERSION_CODE must be a positive Android version code")
}.getOrElse(2)
val appBuildNumber = providers.environmentVariable("APP_BUILD_NUMBER").orNull
val studioDebugKey = rootProject.file("debug.keystore")

android {
    namespace = "com.example"
    compileSdk = 35

    defaultConfig {
        applicationId = "org.crickenglish.app"
        minSdk = 24
        targetSdk = 35
        versionCode = appVersionCode
        versionName = "1.1" + (appBuildNumber?.let { ".$it" } ?: "")
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (studioDebugKey.isFile) {
            getByName("debug") {
                storeFile = studioDebugKey
                storePassword = "android"
                keyAlias = "androiddebugkey"
                keyPassword = "android"
            }
        }
        if (hasReleaseKey) {
            create("release") {
                storeFile = file(checkNotNull(releaseKeyPath))
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        release {
            if (hasReleaseKey) signingConfig = signingConfigs.getByName("release")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    testOptions.unitTests.isIncludeAndroidResources = true
}

tasks.withType<Test>().configureEach {
    systemProperty("robolectric.dependency.repo.url", providers.environmentVariable("ROBOLECTRIC_MAVEN_REPO")
        .getOrElse("https://repo.maven.apache.org/maven2"))
    systemProperty("crickEnglish.liveApi", providers.gradleProperty("liveApi").getOrElse("false"))
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.activity.compose)
    implementation(libs.core.ktx)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    implementation(libs.coil.compose)
    implementation(libs.coroutines.android)
    implementation(libs.okhttp)
    coreLibraryDesugaring(libs.desugar)

    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.compose.test)
    testImplementation(libs.junit)
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
    testImplementation(libs.robolectric)
    androidTestImplementation(libs.android.test.ext)
    androidTestImplementation(libs.android.test.runner)
    debugImplementation(libs.compose.test.manifest)
}
