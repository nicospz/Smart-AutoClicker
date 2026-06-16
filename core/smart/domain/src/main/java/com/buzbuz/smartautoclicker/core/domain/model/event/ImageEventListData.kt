/*
 * Copyright (C) 2026
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.buzbuz.smartautoclicker.core.domain.model.event

import com.buzbuz.smartautoclicker.core.base.identifier.Identifier
import com.buzbuz.smartautoclicker.core.domain.model.condition.ImageCondition

/** Lightweight image event details for scenario list screens. */
data class ImageEventListData(
    val id: Identifier,
    val name: String,
    val actionsCount: Int,
    val conditionsCount: Int,
    val firstCondition: ImageCondition?,
)
