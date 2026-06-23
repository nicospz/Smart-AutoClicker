/*
 * Copyright (C) 2026 Nicolas Espinoza
 */
package com.buzbuz.smartautoclicker.core.tasker

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val json = Json { ignoreUnknownKeys = true }

fun List<TaskerVariable>.toJsonString(): String? {
    val valid = filter { it.normalizedName().isNotEmpty() }
    if (valid.isEmpty()) return null
    return json.encodeToString(valid)
}

fun String?.toTaskerVariables(): List<TaskerVariable> {
    if (isNullOrBlank()) return emptyList()
    return runCatching { json.decodeFromString<List<TaskerVariable>>(this) }.getOrDefault(emptyList())
}
