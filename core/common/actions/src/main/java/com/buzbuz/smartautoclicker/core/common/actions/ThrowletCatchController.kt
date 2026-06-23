/*
 * Copyright (C) 2026 Nicolas Espinoza
 *
 * Optional bridge from scenario processing to the SAC Throwlet overlay rail.
 * Registered by LocalService while the accessibility service is active.
 */
package com.buzbuz.smartautoclicker.core.common.actions

enum class ThrowletCatchOperation {
    TOGGLE,
    HIDE,
    SHOW,
}

data class ThrowletCatchSession(
    val mode: ThrowletCatchMode = ThrowletCatchMode.CATCH,
    val lane: ThrowletCatchLane = ThrowletCatchLane.FULL,
    val pokemonNameOverride: String? = null,
    val manualSelectionOnly: Boolean = false,
)

enum class ThrowletCatchMode {
    CATCH,
    BUDDY,
}

enum class ThrowletCatchLane {
    FULL,
    TOP,
    BOTTOM,
}

fun interface ThrowletCatchController {
    suspend fun execute(operation: ThrowletCatchOperation, session: ThrowletCatchSession)
}

object ThrowletCatchControllers {
  @Volatile
  var instance: ThrowletCatchController? = null
}
