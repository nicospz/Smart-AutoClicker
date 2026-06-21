/*
 * Copyright (C) 2026 Nicolas Espinoza
 */
package com.buzbuz.smartautoclicker.core.base

/** Normalize a user-provided scenario category. Blank values are treated as uncategorized. */
fun String?.normalizeScenarioCategory(): String? =
    this?.trim()?.takeIf { it.isNotEmpty() }

/** @return true if both categories represent the same grouping (including uncategorized). */
fun String?.matchesScenarioCategory(other: String?): Boolean =
    normalizeScenarioCategory() == other.normalizeScenarioCategory()
