import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val localProperties = Properties().apply {
    val localFile = rootProject.file("local.properties")
    if (localFile.exists()) {
        localFile.inputStream().use(::load)
    }
}

fun escapeForBuildConfig(value: String): String {
    return value.replace("\\", "\\\\").replace("\"", "\\\"")
}

android {
    namespace = "com.sjbit.seniorshield"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.sjbit.seniorshield"
        minSdk = 27
        targetSdk = 34
        versionCode = 12
        versionName = "1.4.1-translation-localread-fix"
        buildConfigField(
            "String",
            "APP_VERSION_LABEL",
            "\"SeniorShield v1.4.1-translation-localread-fix\""
        )
        buildConfigField(
            "String",
            "HF_API_TOKEN",
            "\"${escapeForBuildConfig(localProperties.getProperty("hf.api.token", ""))}\""
        )
        buildConfigField(
            "String",
            "SARVAM_API_KEY",
            "\"${escapeForBuildConfig(localProperties.getProperty("sarvam.api.key", ""))}\""
        )
        buildConfigField(
            "String",
            "HF_CUSTOM_MODEL_ID",
            "\"ClutchKrishna/scam-detector-v2\""
        )
        buildConfigField(
            "String",
            "HF_MODEL_ID",
            "\"ealvaradob/bert-finetuned-phishing\""
        )
        buildConfigField(
            "String",
            "HF_ZERO_SHOT_MODEL_ID",
            "\"joeddav/xlm-roberta-large-xnli\""
        )
        buildConfigField(
            "String",
            "HF_TRANSLATE_EN_HI_MODEL_ID",
            "\"Helsinki-NLP/opus-mt-en-hi\""
        )
        buildConfigField(
            "String",
            "HF_TRANSLATE_EN_KN_MODEL_ID",
            "\"Helsinki-NLP/opus-mt-en-kn\""
        )
        buildConfigField(
            "String",
            "HF_TRANSLATE_HI_EN_MODEL_ID",
            "\"Helsinki-NLP/opus-mt-hi-en\""
        )
        buildConfigField(
            "String",
            "HF_TRANSLATE_KN_EN_MODEL_ID",
            "\"Helsinki-NLP/opus-mt-kn-en\""
        )
        buildConfigField(
            "String",
            "LIVE_MODEL_API_URL",
            "\"${escapeForBuildConfig(localProperties.getProperty("live.model.api.url", "https://advantage-shots-yea-sustained.trycloudflare.com/predict"))}\""
        )
        buildConfigField(
            "String",
            "N8N_SCAN_WEBHOOK_URL",
            "\"${escapeForBuildConfig(localProperties.getProperty("n8n.scan.webhook.url", "https://iloveyouljk.app.n8n.cloud/webhook-test/9afa4b6c-e8e1-4ec2-8017-635df824a6f2"))}\""
        )

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
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

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.09.03")

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")
    implementation("androidx.navigation:navigation-compose:2.8.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    implementation(composeBom)
    androidTestImplementation(composeBom)
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    implementation("androidx.core:core-splashscreen:1.0.1")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
}
