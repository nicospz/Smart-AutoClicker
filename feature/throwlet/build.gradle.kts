/*
 * Copyright (C) 2026 Nicolas Espinoza
 *
 * Throwlet gesture and buddy-crop storage with Supabase sync.
 */

import groovy.json.JsonOutput
import java.util.Properties

plugins {
    alias(libs.plugins.buzbuz.androidLibrary)
    alias(libs.plugins.buzbuz.flavour)
    alias(libs.plugins.buzbuz.kotlinSerialization)
    alias(libs.plugins.buzbuz.androidRoom)
    alias(libs.plugins.buzbuz.androidUnitTest)
    alias(libs.plugins.buzbuz.hilt)
}

fun String.asBuildConfigString(): String = JsonOutput.toJson(this)

val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.isFile) {
        file.inputStream().use(::load)
    }
}

fun privateProperty(name: String): String =
    localProperties.getProperty(name)?.takeIf { it.isNotBlank() }
        ?: providers.gradleProperty(name).orElse("").get()

fun privatePropertyWithFallback(primary: String, fallback: String): String =
    privateProperty(primary).ifBlank { privateProperty(fallback) }

val supabaseUrl = privateProperty("SUPABASE_URL")
val supabaseAnonKey = privateProperty("SUPABASE_ANON_KEY")
val supabaseGestureProfileId = privatePropertyWithFallback("SUPABASE_SYNC_PROFILE_ID", "SUPABASE_GESTURE_PROFILE_ID")
val supabaseGestureSyncSecret = privatePropertyWithFallback("SUPABASE_SYNC_SECRET", "SUPABASE_GESTURE_SYNC_SECRET")

android {
    namespace = "com.buzbuz.smartautoclicker.feature.throwlet"

    androidResources {
        noCompress += "tflite"
    }

    buildFeatures {
        buildConfig = true
    }

    defaultConfig {
        buildConfigField("String", "SUPABASE_URL", supabaseUrl.asBuildConfigString())
        buildConfigField("String", "SUPABASE_ANON_KEY", supabaseAnonKey.asBuildConfigString())
        buildConfigField("String", "SUPABASE_GESTURE_PROFILE_ID", supabaseGestureProfileId.asBuildConfigString())
        buildConfigField("String", "SUPABASE_GESTURE_SYNC_SECRET", supabaseGestureSyncSecret.asBuildConfigString())
    }
}

dependencies {
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.square.okhttp)
    implementation(libs.google.mediapipe.tasks.vision) {
        exclude(group = "com.google.protobuf", module = "protobuf-javalite")
    }
    implementation("com.google.mlkit:text-recognition:16.0.1")

    implementation(project(":core:common:base"))
    implementation(project(":core:common:actions"))
    implementation(project(":core:common:display"))
    implementation(project(":core:smart:detection"))

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
