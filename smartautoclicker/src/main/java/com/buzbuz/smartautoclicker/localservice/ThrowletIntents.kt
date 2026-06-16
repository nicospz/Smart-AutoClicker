/*
 * Copyright (C) 2026 Nicolas Espinoza
 *
 * Throwlet broadcast intent actions for SAC scenario intent actions.
 * Start/stop Throwlet manually from scenario steps — not tied to projection lifecycle.
 */
package com.buzbuz.smartautoclicker.localservice

object ThrowletIntents {
    const val PACKAGE = "dev.nicospz.throwlet"
    const val EXTRA_SOURCE = "EXTRA_SOURCE"
    const val SOURCE_SAC = "smartautoclicker"

    private const val PREFIX = "dev.nicospz.catchhelper.action."

    const val START_CATCH_FULL = PREFIX + "START_CATCH_FULL"
    const val START_CATCH_TOP = PREFIX + "START_CATCH_TOP"
    const val START_CATCH_BOTTOM = PREFIX + "START_CATCH_BOTTOM"
    const val START_BUDDY_FULL = PREFIX + "START_BUDDY_FULL"
    const val START_BUDDY_TOP = PREFIX + "START_BUDDY_TOP"
    const val START_BUDDY_BOTTOM = PREFIX + "START_BUDDY_BOTTOM"
    const val STOP_FULL = PREFIX + "STOP_FULL"
    const val STOP_TOP = PREFIX + "STOP_TOP"
    const val STOP_BOTTOM = PREFIX + "STOP_BOTTOM"
    const val STOP_ALL = PREFIX + "STOP_ALL"
}
