/*
 * Copyright (C) 2026 Nicolas Espinoza
 */
package com.buzbuz.smartautoclicker.core.tasker

import kotlinx.serialization.Serializable

@Serializable
data class TaskerVariable(
    val name: String,
    val value: String,
) {
    fun normalizedName(): String = name.trim().removePrefix("%").removeSuffix("%")
}
