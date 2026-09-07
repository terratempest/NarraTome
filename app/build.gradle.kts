plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.hilt.android)
    alias(libs.plugins.ksp)
}

val signingRequired = providers.gradleProperty("requireReleaseSigning").orNull == "true"
val uploadStoreFile = providers.environmentVariable("NARRATOME_STORE_FILE").orNull
val uploadStorePassword = providers.environmentVariable("NARRATOME_STORE_PASSWORD").orNull
val uploadKeyAlias = providers.environmentVariable("NARRATOME_KEY_ALIAS").orNull
val uploadKeyPassword = providers.environmentVariable("NARRATOME_KEY_PASSWORD").orNull
val signingAvailable = listOf(uploadStoreFile, uploadStorePassword, uploadKeyAlias, uploadKeyPassword)
    .all { !it.isNullOrBlank() }
val privacyPolicyUrl = providers.environmentVariable("NARRATOME_PRIVACY_POLICY_URL").orElse("").get()
val developerContact = providers.environmentVariable("NARRATOME_DEVELOPER_CONTACT").orElse("").get()
if (signingRequired) {
    check(signingAvailable) { "Signed release requires NARRATOME_STORE_FILE, NARRATOME_STORE_PASSWORD, NARRATOME_KEY_ALIAS and NARRATOME_KEY_PASSWORD." }
    check(file(uploadStoreFile!!).isFile) { "Release keystore file does not exist." }
}

fun resourceText(value: String) = value.replace("\\", "\\\\").replace("'", "\\'").replace("\"", "\\\"")

android {
    namespace = "com.narratome"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.narratome"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        resValue("string", "privacy_policy_url", resourceText(privacyPolicyUrl))
        resValue("string", "developer_contact", resourceText(developerContact))
    }

    signingConfigs {
        if (signingAvailable) create("upload") {
            storeFile = file(uploadStoreFile!!)
            storePassword = uploadStorePassword
            keyAlias = uploadKeyAlias
            keyPassword = uploadKeyPassword
        }
    }

    buildTypes {
        release {
            if (signingAvailable) signingConfig = signingConfigs.getByName("upload")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        resValues = true
        compose = true
        buildConfig = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

kotlin {
    jvmToolchain(17)
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)

    val composeBom = platform(libs.compose.bom)
    implementation(composeBom)
    androidTestImplementation(composeBom)
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    implementation(libs.activity.compose)
    implementation(libs.navigation.compose)
    implementation(libs.lifecycle.runtime.ktx)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.compose)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    implementation(libs.room.paging)
    ksp(libs.room.compiler)

    implementation(libs.retrofit)
    implementation(libs.retrofit.kotlinx.serialization)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)

    implementation(libs.datastore.preferences)
    implementation(libs.security.crypto)

    implementation(libs.media3.exoplayer)
    implementation(libs.media3.datasource.okhttp)
    implementation(libs.media3.exoplayer.hls)
    implementation(libs.media3.session)
    implementation(libs.media3.ui)

    implementation(libs.work.runtime.ktx)
    implementation(libs.hilt.work)
    implementation(libs.androidx.concurrent.futures)
    ksp(libs.androidx.hilt.compiler)

    implementation(libs.coil.compose)
    implementation(libs.paging.runtime)
    implementation(libs.paging.compose)

    debugImplementation(libs.androidx.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(libs.truth)
    testImplementation(libs.mockwebserver)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(composeBom)
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.test.manifest)
}
