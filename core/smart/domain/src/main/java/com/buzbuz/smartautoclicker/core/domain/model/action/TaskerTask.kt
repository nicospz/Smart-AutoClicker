/*
 * Copyright (C) 2026 Nicolas Espinoza
 */
package com.buzbuz.smartautoclicker.core.domain.model.action

import com.buzbuz.smartautoclicker.core.base.identifier.Identifier

/** Run a user-defined Tasker task. */
data class TaskerTask(
    override val id: Identifier,
    override val eventId: Identifier,
    override val name: String?,
    override var priority: Int,
    val taskName: String? = null,
    val waitForCompletion: Boolean = false,
    val variablesJson: String? = null,
) : Action() {

    override fun isComplete(): Boolean {
        if (!super.isComplete()) return false
        return !taskName.isNullOrBlank()
    }

    override fun hashCodeNoIds(): Int =
        name.hashCode() + taskName.hashCode() + waitForCompletion.hashCode() + variablesJson.hashCode()

    override fun deepCopy(): TaskerTask = copy(name = "" + name)
}
