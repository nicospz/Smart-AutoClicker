/*
 * Copyright (C) 2024 Kevin Buzeau
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

import android.graphics.Bitmap
import android.graphics.Rect
import android.util.Log

import com.buzbuz.smartautoclicker.core.detection.DetectionResult
import com.buzbuz.smartautoclicker.core.detection.ImageDetector
import com.buzbuz.smartautoclicker.core.domain.model.AND
import com.buzbuz.smartautoclicker.core.domain.model.ConditionOperator
import com.buzbuz.smartautoclicker.core.domain.model.CounterOperationValue
import com.buzbuz.smartautoclicker.core.domain.model.IN_AREA
import com.buzbuz.smartautoclicker.core.domain.model.OR
import com.buzbuz.smartautoclicker.core.domain.model.condition.Condition
import com.buzbuz.smartautoclicker.core.domain.model.condition.ImageCondition
import com.buzbuz.smartautoclicker.core.domain.model.condition.TriggerCondition
import com.buzbuz.smartautoclicker.core.domain.model.event.ImageEvent
import com.buzbuz.smartautoclicker.core.processing.data.processor.state.ProcessingState
import com.buzbuz.smartautoclicker.core.processing.data.scaling.ImageConditionScalingInfo
import com.buzbuz.smartautoclicker.core.processing.data.scaling.ScalingManager
import com.buzbuz.smartautoclicker.core.processing.domain.SmartProcessingListener
import com.buzbuz.smartautoclicker.core.processing.domain.model.ProcessedConditionResult

import kotlinx.coroutines.yield

internal class ConditionsVerifier(
    private val state: ProcessingState,
    private val imageDetector: ImageDetector,
    private val scalingManager: ScalingManager,
    private val bitmapSupplier: suspend (String, Int, Int) -> Bitmap?,
    private val progressListener: SmartProcessingListener? = null,
) {

    /** List of results for the last call to verifyConditions. */
    private val verificationResults: ConditionsResults = ConditionsResults()

    /**
     * Set only during a [verifyConditions], it contains the system time at verification start.
     * This allows to use the same reference time for all conditions during the same verification loop.
     */
    private var currentVerificationTsMs: Long? = null

    suspend fun verifyConditions(@ConditionOperator operator: Int, conditions: List<Condition>): ConditionsResults {
        verificationResults.reset()
        currentVerificationTsMs = System.currentTimeMillis()

        var verificationResult: ProcessedConditionResult
        for (condition in conditions) {
            verificationResult = verifyCondition(condition)
            verificationResults.addResult(condition.getValidId(), verificationResult)

            if (operator == OR && verificationResult.isFulfilled) {
                verificationResults.setFulfilledState(true)
                return verificationResults
            }
            if (operator == AND && !verificationResult.isFulfilled) {
                verificationResults.setFulfilledState(false)
                return verificationResults
            }

            yield()
        }

        verificationResults.setFulfilledState(operator == AND)
        return verificationResults
    }

    suspend fun verifyAnchoredImageEvent(event: ImageEvent): ConditionsResults {
        verificationResults.reset()
        currentVerificationTsMs = System.currentTimeMillis()

        val anchorCondition = event.conditions.find { it.id == event.anchorConditionId }
            ?: return event.conditions.toUnfulfilledImageResults(
                reason = "anchor condition ${event.anchorConditionId} not found in event ${event.id.databaseId}",
            )
        val anchorScaling = scalingManager.getImageConditionScalingInfo(anchorCondition)
            ?: return event.conditions.toUnfulfilledImageResults(
                reason = "no scaling info for anchor ${anchorCondition.id.databaseId}",
            )
        val anchorBitmap = bitmapSupplier(
            anchorCondition.path,
            anchorScaling.imageArea.width(),
            anchorScaling.imageArea.height(),
        ) ?: return event.conditions.toUnfulfilledImageResults(
            reason = "unable to load anchor bitmap ${anchorCondition.path}",
        )

        if (!anchorScaling.detectionArea.canContain(anchorScaling.imageArea)) {
            return event.conditions.toUnfulfilledImageResults(
                reason = "anchor detection area too small: anchorArea=${anchorScaling.imageArea}, " +
                        "detectionArea=${anchorScaling.detectionArea}",
            )
        }

        Log.i(
            TAG,
            "Anchored event ${event.id.databaseId} '${event.name}': anchor=${anchorCondition.id.databaseId} " +
                    "anchorOriginal=${anchorScaling.imageArea}, anchorSearch=${anchorScaling.detectionArea}, " +
                    "threshold=${anchorCondition.threshold}, children=${event.conditions.size - 1}",
        )

        val anchorOccurrences = imageDetector.detectConditionOccurrences(
            conditionBitmap = anchorBitmap,
            conditionWidth = anchorScaling.imageArea.width(),
            conditionHeight = anchorScaling.imageArea.height(),
            detectionArea = anchorScaling.detectionArea,
            threshold = anchorCondition.threshold,
        ).filter { it.isDetected }
            .deduplicateOverlappingOccurrences(anchorScaling.imageArea)
            .sortedWith(compareBy<DetectionResult> { it.position.y }.thenBy { it.position.x })

        Log.i(
            TAG,
            "Anchored event ${event.id.databaseId}: found ${anchorOccurrences.size} anchor occurrence(s): " +
                    anchorOccurrences.joinToString { occurrence ->
                        "center=${occurrence.position}, confidence=${occurrence.confidenceRate}"
                    },
        )

        if (anchorOccurrences.isEmpty()) {
            return event.conditions.toUnfulfilledImageResults(reason = "no detected anchor occurrence")
        }

        val childConditions = event.conditions.filterNot { it.id == anchorCondition.id }
        var lastCandidateResults: List<Pair<Long, ProcessedConditionResult>> = emptyList()

        for ((candidateIndex, anchorOccurrence) in anchorOccurrences.withIndex()) {
            val candidateResults = mutableListOf<Pair<Long, ProcessedConditionResult>>()
            val anchorResult = anchorCondition.toImageResult(anchorOccurrence)
            candidateResults += anchorCondition.getValidId() to anchorResult

            val anchorRuntimeArea = anchorOccurrence.toArea(anchorScaling.imageArea)
            var candidateFulfilled = true

            Log.i(
                TAG,
                "Anchored event ${event.id.databaseId}: evaluating candidate #$candidateIndex " +
                        "anchorCenter=${anchorOccurrence.position}, anchorRuntime=$anchorRuntimeArea, " +
                        "anchorConfidence=${anchorOccurrence.confidenceRate}",
            )

            for (childCondition in childConditions) {
                val childScaling = scalingManager.getImageConditionScalingInfo(childCondition)
                if (childScaling == null) {
                    candidateFulfilled = false
                    val invalidResult = childCondition.toInvalidConditionResult()
                    candidateResults += childCondition.getValidId() to invalidResult
                    Log.w(
                        TAG,
                        "Anchored event ${event.id.databaseId}: child ${childCondition.id.databaseId} invalid, " +
                                "no scaling info",
                    )
                    continue
                }

                val childDetectionArea = childCondition.getRelativeDetectionArea(
                    anchorOriginalArea = anchorScaling.imageArea,
                    anchorRuntimeArea = anchorRuntimeArea,
                    childScalingInfo = childScaling,
                )
                val childResult = if (childDetectionArea.canContain(childScaling.imageArea)) {
                    verifyImageConditionInArea(
                        condition = childCondition,
                        scaledConditionArea = childScaling,
                        detectionArea = childDetectionArea,
                        notifyProgress = false,
                    )
                } else {
                    childCondition.toNotDetectedResult()
                }

                Log.i(
                    TAG,
                    "Anchored event ${event.id.databaseId}: candidate #$candidateIndex child=${childCondition.id.databaseId} " +
                            "shouldBeDetected=${childCondition.shouldBeDetected}, childOriginal=${childScaling.imageArea}, " +
                            "childDetectionType=${childCondition.detectionType}, relativeSearch=$childDetectionArea, " +
                            "canSearch=${childDetectionArea.canContain(childScaling.imageArea)}, " +
                            "detected=${childResult.haveBeenDetected}, fulfilled=${childResult.isFulfilled}, " +
                            "confidence=${childResult.confidenceRate}, position=${childResult.position}, " +
                            "bestPosition=${childResult.bestPosition}",
                )

                candidateResults += childCondition.getValidId() to childResult
                if (!childResult.isFulfilled) candidateFulfilled = false
            }

            lastCandidateResults = candidateResults
            if (candidateFulfilled) {
                Log.i(TAG, "Anchored event ${event.id.databaseId}: candidate #$candidateIndex PASSED")
                verificationResults.setResults(candidateResults, fulfilledState = true)
                return verificationResults
            }

            Log.i(TAG, "Anchored event ${event.id.databaseId}: candidate #$candidateIndex failed")
            yield()
        }

        Log.i(TAG, "Anchored event ${event.id.databaseId}: no candidate passed")
        verificationResults.setResults(lastCandidateResults, fulfilledState = false)
        return verificationResults
    }

    private suspend fun verifyCondition(condition: Condition): ProcessedConditionResult =
        when (condition) {
            is ImageCondition -> verifyImageCondition(condition)
            is TriggerCondition -> condition.toConditionResult(verifyTriggerCondition(condition))
        }

    private fun verifyTriggerCondition(condition: TriggerCondition): Boolean =
        when (condition) {
            is TriggerCondition.OnBroadcastReceived -> verifyOnBroadcastReceived(condition)
            is TriggerCondition.OnCounterCountReached -> verifyOnCounterReached(condition)
            is TriggerCondition.OnTimerReached -> verifyOnTimerReached(condition)
        }

    private fun verifyOnBroadcastReceived(condition: TriggerCondition.OnBroadcastReceived): Boolean =
        state.isBroadcastReceived(condition)

    private fun verifyOnCounterReached(condition: TriggerCondition.OnCounterCountReached): Boolean =
        state.getCounterValue(condition.counterName)?.let { counterValue ->

            val operandValue = when (val operationValue = condition.counterValue) {
                is CounterOperationValue.Counter -> state.getCounterValue(operationValue.value) ?: 0
                is CounterOperationValue.Number -> operationValue.value
            }

            when (condition.comparisonOperation) {
                TriggerCondition.OnCounterCountReached.ComparisonOperation.GREATER ->
                    counterValue > operandValue

                TriggerCondition.OnCounterCountReached.ComparisonOperation.GREATER_OR_EQUALS ->
                    counterValue >= operandValue

                TriggerCondition.OnCounterCountReached.ComparisonOperation.EQUALS ->
                    counterValue == operandValue

                TriggerCondition.OnCounterCountReached.ComparisonOperation.LOWER_OR_EQUALS ->
                    counterValue <= operandValue

                TriggerCondition.OnCounterCountReached.ComparisonOperation.LOWER ->
                    counterValue < operandValue
            }
        } ?: false

    private fun verifyOnTimerReached(condition: TriggerCondition.OnTimerReached): Boolean {
        val currentTsMs = currentVerificationTsMs ?: return false
        val timerEndMs = state.getTimerEndMs(condition.getValidId()) ?: return false

        return if (currentTsMs > timerEndMs) {
            if (condition.restartWhenReached) state.setTimerStartToNow(condition)
            else state.setTimerToDisabled(condition.getValidId())
            true
        } else false
    }

    private suspend fun verifyImageCondition(condition: ImageCondition): ProcessedConditionResult.Image {
        progressListener?.onImageConditionProcessingStarted()

        val scaledConditionArea = scalingManager.getImageConditionScalingInfo(condition)
            ?: return condition.toInvalidConditionResult()

        val result = verifyImageConditionInArea(
            condition = condition,
            scaledConditionArea = scaledConditionArea,
            detectionArea = scaledConditionArea.detectionArea,
            notifyProgress = false,
        )

        progressListener?.onImageConditionProcessingCompleted(result)
        return result
    }

    private suspend fun verifyImageConditionInArea(
        condition: ImageCondition,
        scaledConditionArea: ImageConditionScalingInfo,
        detectionArea: Rect,
        notifyProgress: Boolean,
    ): ProcessedConditionResult.Image {
        if (notifyProgress) progressListener?.onImageConditionProcessingStarted()

        val bitmap = bitmapSupplier(
            condition.path,
            scaledConditionArea.imageArea.width(),
            scaledConditionArea.imageArea.height(),
        )

        val result = bitmap?.let { conditionBitmap ->
            val detectionResult = imageDetector.detectCondition(
                conditionBitmap = conditionBitmap,
                conditionWidth = scaledConditionArea.imageArea.width(),
                conditionHeight = scaledConditionArea.imageArea.height(),
                detectionArea = detectionArea,
                threshold = condition.threshold,
            )

            condition.toImageResult(detectionResult)
        } ?: condition.toInvalidConditionResult()

        if (notifyProgress) progressListener?.onImageConditionProcessingCompleted(result)
        return result
    }

    private fun ImageCondition.toImageResult(detectionResult: DetectionResult): ProcessedConditionResult.Image {
        val bestPosition = detectionResult.getBestPosition()

        return ProcessedConditionResult.Image(
            isFulfilled = detectionResult.isDetected == shouldBeDetected,
            haveBeenDetected = detectionResult.isDetected,
            condition = this,
            position = if (detectionResult.isDetected) bestPosition else null,
            bestPosition = bestPosition,
            confidenceRate = detectionResult.confidenceRate,
        )
    }

    private fun DetectionResult.getBestPosition() =
        if (confidenceRate == 0.0 && position.x == 0 && position.y == 0) null
        else scalingManager.scaleUpDetectionResult(position)

    private fun List<ImageCondition>.toUnfulfilledImageResults(reason: String): ConditionsResults {
        Log.i(TAG, "Anchored event unfulfilled: $reason")
        verificationResults.setResults(
            results = map { condition -> condition.getValidId() to condition.toInvalidConditionResult() },
            fulfilledState = false,
        )
        return verificationResults
    }

    private fun DetectionResult.toArea(conditionArea: Rect): Rect {
        val halfWidth = conditionArea.width() / 2
        val halfHeight = conditionArea.height() / 2
        return Rect(
            position.x - halfWidth,
            position.y - halfHeight,
            position.x - halfWidth + conditionArea.width(),
            position.y - halfHeight + conditionArea.height(),
        )
    }

    private fun List<DetectionResult>.deduplicateOverlappingOccurrences(conditionArea: Rect): List<DetectionResult> {
        val bestFirstOccurrences = sortedByDescending { occurrence -> occurrence.confidenceRate }
        val uniqueOccurrences = mutableListOf<DetectionResult>()

        bestFirstOccurrences.forEach { occurrence ->
            val occurrenceArea = occurrence.toArea(conditionArea)
            val overlapsExistingOccurrence = uniqueOccurrences.any { uniqueOccurrence ->
                Rect.intersects(occurrenceArea, uniqueOccurrence.toArea(conditionArea))
            }

            if (!overlapsExistingOccurrence) uniqueOccurrences += occurrence
            else Log.d(
                TAG,
                "Discarding overlapping anchor occurrence center=${occurrence.position}, " +
                        "confidence=${occurrence.confidenceRate}, area=$occurrenceArea",
            )
        }

        return uniqueOccurrences
    }

    private fun ImageCondition.getRelativeDetectionArea(
        anchorOriginalArea: Rect,
        anchorRuntimeArea: Rect,
        childScalingInfo: ImageConditionScalingInfo,
    ): Rect {
        val sourceArea = if (detectionType == IN_AREA) childScalingInfo.detectionArea else childScalingInfo.imageArea
        val relativeLeft = sourceArea.left - anchorOriginalArea.left
        val relativeTop = sourceArea.top - anchorOriginalArea.top

        return Rect(
            anchorRuntimeArea.left + relativeLeft,
            anchorRuntimeArea.top + relativeTop,
            anchorRuntimeArea.left + relativeLeft + sourceArea.width(),
            anchorRuntimeArea.top + relativeTop + sourceArea.height(),
        )
    }

    private fun Rect.canContain(conditionArea: Rect): Boolean =
        left >= 0 && top >= 0 && width() >= conditionArea.width() && height() >= conditionArea.height()

    private fun ImageCondition.toNotDetectedResult(): ProcessedConditionResult.Image =
        ProcessedConditionResult.Image(
            isFulfilled = !shouldBeDetected,
            haveBeenDetected = false,
            condition = this,
            confidenceRate = 0.0,
            position = null,
            bestPosition = null,
        )

    private fun ImageCondition.toInvalidConditionResult(): ProcessedConditionResult.Image =
        ProcessedConditionResult.Image(
            isFulfilled = false,
            haveBeenDetected = false,
            condition = this,
            confidenceRate = 0.0,
            position = null,
            bestPosition = null,
        )

    private fun TriggerCondition.toConditionResult(positive: Boolean): ProcessedConditionResult.Trigger =
        ProcessedConditionResult.Trigger(
            isFulfilled = positive,
            condition = this,
        )
}

private const val TAG = "AnchoredImageEvent"
