/*
 * Copyright (C) 2024 Kevin Buzeau
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
plugins {
    alias(libs.plugins.buzbuz.androidLibrary)
    alias(libs.plugins.buzbuz.flavour)
    alias(libs.plugins.buzbuz.hilt)
}

android {
    namespace = "com.buzbuz.smartautoclicker.core.common.actions"
}

val buildGestureHelper = tasks.register<Exec>("buildGestureHelper") {
    group = "build"
    description = "Cross-compile gesture-helper.cpp into assets/helper/arm64-v8a/"
    workingDir = projectDir
    commandLine("bash", "scripts/build-gesture-helper.sh")
    inputs.file("src/main/cpp/gesture-helper.cpp")
    outputs.file("src/main/assets/helper/arm64-v8a/gesture-helper")
}

tasks.named("preBuild") {
    dependsOn(buildGestureHelper)
}

dependencies {
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.androidx.core.ktx)
    implementation(libs.shizuku.api)
    implementation(libs.shizuku.provider)

    implementation(project(":core:common:base"))
    implementation(project(":core:common:permissions"))
    implementation(project(":core:common:ui"))
}
