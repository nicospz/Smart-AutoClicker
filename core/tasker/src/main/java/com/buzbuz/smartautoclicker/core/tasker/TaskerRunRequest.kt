/*
 * Copyright (C) 2026 Nicolas Espinoza
 */
package com.buzbuz.smartautoclicker.core.tasker

data class TaskerRunRequest(
    val taskName: String,
    val variables: List<TaskerVariable> = emptyList(),
    val waitForCompletion: Boolean = false,
    val timeoutMs: Long = DEFAULT_TIMEOUT_MS,
) {
    companion object {
        const val DEFAULT_TIMEOUT_MS = 60_000L
    }
}
