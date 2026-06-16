/*
 * Copyright (C) 2025 Kevin Buzeau
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.buzbuz.smartautoclicker.core.processing.data.processor

import com.buzbuz.smartautoclicker.core.processing.domain.model.ProcessedConditionResult

internal data class OffsetRepeatMatch(
    val instanceIndex: Int,
    val dx: Int,
    val dy: Int,
    val results: Map<Long, ProcessedConditionResult>,
)

internal class ConditionsResults {

    private val _results: MutableMap<Long, ProcessedConditionResult> = mutableMapOf()

    var fulfilled: Boolean? = null
        private set

    var offsetRepeatMatches: List<OffsetRepeatMatch> = emptyList()
        private set

    var offsetRepeatDx: Int = 0
        private set

    var offsetRepeatDy: Int = 0
        private set

    fun getImageConditionResult(conditionId: Long): ProcessedConditionResult.Image? =
        _results[conditionId]?.let { result -> result as? ProcessedConditionResult.Image }

    fun getFirstImageDetectedResult(): ProcessedConditionResult.Image? =
        _results.values.find { it is ProcessedConditionResult.Image && it.isFulfilled && it.condition.shouldBeDetected }
                as ProcessedConditionResult.Image?

    fun getAllTriggerConditionsResults(): List<ProcessedConditionResult.Trigger> =
        _results.mapNotNull { it.value as? ProcessedConditionResult.Trigger }

    fun getAllImageConditionsResults(): List<ProcessedConditionResult.Image> =
        _results.mapNotNull { it.value as? ProcessedConditionResult.Image }

    fun reset() {
        _results.clear()
        fulfilled = null
        offsetRepeatMatches = emptyList()
        offsetRepeatDx = 0
        offsetRepeatDy = 0
    }

    fun addResult(conditionId: Long, result: ProcessedConditionResult) {
        if (fulfilled != null) return
        _results[conditionId] = result
    }

    fun setResults(results: List<Pair<Long, ProcessedConditionResult>>, fulfilledState: Boolean) {
        _results.clear()
        results.forEach { (conditionId, result) -> _results[conditionId] = result }
        fulfilled = fulfilledState
    }

    fun setFulfilledState(state: Boolean) {
        fulfilled = state
    }

    fun setOffsetRepeatMatches(matches: List<OffsetRepeatMatch>) {
        offsetRepeatMatches = matches
        val primary = matches.firstOrNull()
        if (primary != null) {
            setResults(primary.results.map { (id, result) -> id to result }, fulfilledState = true)
            offsetRepeatDx = primary.dx
            offsetRepeatDy = primary.dy
        }
    }

    fun forOffsetRepeatMatch(match: OffsetRepeatMatch): ConditionsResults =
        ConditionsResults().apply {
            setResults(match.results.map { (id, result) -> id to result }, fulfilledState = true)
            offsetRepeatDx = match.dx
            offsetRepeatDy = match.dy
            offsetRepeatMatches = listOf(match)
        }
}
