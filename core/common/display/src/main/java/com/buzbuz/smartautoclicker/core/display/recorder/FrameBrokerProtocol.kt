/*
 * Copyright (C) 2026 Nicolas Espinoza
 *
 * Shared localhost frame-broker protocol for Throwlet screen capture piggyback.
 */
package com.buzbuz.smartautoclicker.core.display.recorder

object FrameBrokerProtocol {
    const val HOST = "127.0.0.1"
    const val PORT = 49322
    const val TOKEN = "throwlet-frame-v1"

    fun statusCommand(): String = "STATUS token=$TOKEN"
    fun frameCommand(): String = "FRAME token=$TOKEN"
    fun cropPickCommand(
        left: Int? = null,
        top: Int? = null,
        right: Int? = null,
        bottom: Int? = null,
    ): String = buildString {
        append("CROP_PICK token=$TOKEN")
        if (left != null && top != null && right != null && bottom != null) {
            append(" left=$left top=$top right=$right bottom=$bottom")
        }
    }
}
