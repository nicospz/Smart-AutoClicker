/*
 * Copyright (C) 2026 Nicolas Espinoza
 *
 * Throwlet Catch overlay control action.
 */
package com.buzbuz.smartautoclicker.core.domain.model.action

import com.buzbuz.smartautoclicker.core.base.identifier.Identifier
import com.buzbuz.smartautoclicker.core.database.entity.ThrowletCatchLaneType
import com.buzbuz.smartautoclicker.core.database.entity.ThrowletCatchModeType
import com.buzbuz.smartautoclicker.core.database.entity.ThrowletCatchOperationType

/** Start or stop the Throwlet helper overlay (catch or buddy mode). */
data class ThrowletCatch(
    override val id: Identifier,
    override val eventId: Identifier,
    override val name: String?,
    override var priority: Int,
    val operation: Operation,
    val mode: Mode = Mode.CATCH,
    val lane: Lane = Lane.FULL,
    val pokemonNameOverride: String? = null,
) : Action() {

    enum class Operation {
        TOGGLE,
        HIDE,
        SHOW;

        fun toEntity(): ThrowletCatchOperationType = ThrowletCatchOperationType.valueOf(name)
    }

    enum class Mode {
        CATCH,
        BUDDY;

        fun toEntity(): ThrowletCatchModeType = ThrowletCatchModeType.valueOf(name)
    }

    enum class Lane {
        FULL,
        TOP,
        BOTTOM;

        fun toEntity(): ThrowletCatchLaneType = ThrowletCatchLaneType.valueOf(name)
    }

    data class Session(
        val mode: Mode,
        val lane: Lane,
        val pokemonNameOverride: String?,
    )

    val session: Session
        get() = Session(mode = mode, lane = lane, pokemonNameOverride = pokemonNameOverride)

    override fun hashCodeNoIds(): Int =
        name.hashCode() + operation.hashCode() + mode.hashCode() + lane.hashCode() + pokemonNameOverride.hashCode()

    override fun deepCopy(): ThrowletCatch = copy(name = "" + name)
}
