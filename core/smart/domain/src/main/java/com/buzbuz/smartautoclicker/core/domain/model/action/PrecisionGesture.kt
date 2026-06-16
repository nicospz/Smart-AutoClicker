/*
 * Copyright (C) 2026
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.buzbuz.smartautoclicker.core.domain.model.action

import com.buzbuz.smartautoclicker.core.base.identifier.Identifier
import com.buzbuz.smartautoclicker.core.common.actions.precision.PRECISION_GESTURE_HELPER_MODE
import com.buzbuz.smartautoclicker.core.common.actions.precision.PrecisionGesturePayload

data class PrecisionGesture(
    override val id: Identifier,
    override val eventId: Identifier,
    override val name: String?,
    override var priority: Int = 0,
    val payloadHex: String? = null,
    val eventCount: Int? = null,
    val durationMs: Long? = null,
    val helperMode: String? = PRECISION_GESTURE_HELPER_MODE,
) : Action() {

    override fun isComplete(): Boolean =
        !name.isNullOrBlank() && PrecisionGesturePayload.validate(payloadHex, eventCount, durationMs)

    override fun hashCodeNoIds(): Int =
        listOf(name, priority, payloadHex, eventCount, durationMs, helperMode).hashCode()

    override fun deepCopy(): Action =
        copy(name = name?.let { "" + it }, payloadHex = payloadHex?.let { "" + it })
}
