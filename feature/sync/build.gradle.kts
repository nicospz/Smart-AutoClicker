/*
 * Copyright (C) 2026 Nicolas Espinoza
 */

import groovy.json.JsonOutput
import java.util.Properties

plugins {
    alias(libs.plugins.buzbuz.androidLibrary)
    alias(libs.plugins.buzbuz.flavour)
    alias(libs.plugins.buzbuz.kotlinSerialization)
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
val supabaseSyncProfileId = privatePropertyWithFallback("SUPABASE_SYNC_PROFILE_ID", "SUPABASE_GESTURE_PROFILE_ID")
val supabaseSyncSecret = privatePropertyWithFallback("SUPABASE_SYNC_SECRET", "SUPABASE_GESTURE_SYNC_SECRET")

android {
    namespace = "com.buzbuz.smartautoclicker.feature.sync"

    buildFeatures {
        buildConfig = true
    }

    defaultConfig {
        buildConfigField("String", "SUPABASE_URL", supabaseUrl.asBuildConfigString())
        buildConfigField("String", "SUPABASE_ANON_KEY", supabaseAnonKey.asBuildConfigString())
        buildConfigField("String", "SUPABASE_SYNC_PROFILE_ID", supabaseSyncProfileId.asBuildConfigString())
        buildConfigField("String", "SUPABASE_SYNC_SECRET", supabaseSyncSecret.asBuildConfigString())
    }
}

dependencies {
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.datastore)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.square.okhttp)

    implementation(project(":core:common:base"))
    implementation(project(":core:common:bitmaps"))
    implementation(project(":core:common:display"))
    implementation(project(":core:common:settings"))
    implementation(project(":core:smart:database"))
    implementation(project(":core:smart:domain"))
    implementation(project(":core:dumb"))
    implementation(project(":feature:backup"))
    implementation(project(":feature:throwlet"))

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
