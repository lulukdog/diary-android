import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

fun readLocalSecret(name: String): String {
    val fromEnv = System.getenv(name)?.trim().orEmpty()
    if (fromEnv.isNotEmpty()) return fromEnv
    val envFile = rootProject.file(".env")
    if (!envFile.exists()) return ""
    val props = Properties()
    envFile.bufferedReader(Charsets.UTF_8).use { reader ->
        reader.lineSequence().forEach { line ->
            val trimmed = line.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("#") || "=" !in trimmed) return@forEach
            val key = trimmed.substringBefore("=").trim()
            val value = trimmed.substringAfter("=").trim().removeSurrounding("\"").removeSurrounding("'")
            if (key.isNotEmpty()) props.setProperty(key, value)
        }
    }
    return props.getProperty(name).orEmpty().trim()
}

fun resolveUpdateManifestUrl(): String {
    val configured = readLocalSecret("UPDATE_MANIFEST_URL")
    if (configured.isNotEmpty()) return configured
    return "https://raw.githubusercontent.com/lulukdog/diary-android/main/release/version.json"
}

fun asJavaString(value: String): String {
    val escaped = buildString {
        value.forEach { ch ->
            when (ch) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                else -> append(ch)
            }
        }
    }
    return "\"$escaped\""
}

android {
    namespace = "app.diary"
    compileSdk = 37

    defaultConfig {
        applicationId = "app.diary"
        minSdk = 26
        targetSdk = 37
        versionCode = 3
        versionName = "1.0.2"
        buildConfigField("String", "DEEPSEEK_API_KEY", asJavaString(readLocalSecret("DEEPSEEK_API_KEY")))
        buildConfigField("String", "UPDATE_MANIFEST_URL", asJavaString(resolveUpdateManifestUrl()))
    }

    signingConfigs {
        create("release") {
            storeFile = rootProject.file("keystore/diary-release.jks")
            storePassword = "diary-demo"
            keyAlias = "diary"
            keyPassword = "diary-demo"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
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

    lint {
        checkReleaseBuilds = false
        abortOnError = false
        disable += setOf("InsecureBaseConfiguration", "ObsoleteSdkInt")
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.activity:activity-compose:1.11.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.4")
    implementation("androidx.navigation:navigation-compose:2.9.5")

    val composeBom = platform("androidx.compose:compose-bom:2026.09.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")

    implementation("androidx.room:room-runtime:2.8.5")
    implementation("androidx.room:room-ktx:2.8.5")
    ksp("androidx.room:room-compiler:2.8.5")

    implementation("androidx.security:security-crypto:1.1.0")
    implementation("androidx.exifinterface:exifinterface:1.4.2")
    implementation("com.squareup.okhttp3:okhttp:4.12.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")

    testImplementation("junit:junit:4.13.2")
}
