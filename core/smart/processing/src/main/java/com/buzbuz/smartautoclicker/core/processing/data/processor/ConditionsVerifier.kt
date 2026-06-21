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
import android.os.SystemClock

import com.buzbuz.smartautoclicker.core.detection.DetectionResult
import com.buzbuz.smartautoclicker.core.detection.ImageDetector
import com.buzbuz.smartautoclicker.core.domain.model.AND
import com.buzbuz.smartautoclicker.core.domain.model.ConditionOperator
import com.buzbuz.smartautoclicker.core.domain.model.CounterOperationValue
import com.buzbuz.smartautoclicker.core.domain.model.OR
import com.buzbuz.smartautoclicker.core.domain.model.condition.Condition
import com.buzbuz.smartautoclicker.core.domain.model.condition.ImageCondition
import com.buzbuz.smartautoclicker.core.domain.model.condition.TriggerCondition
import com.buzbuz.smartautoclicker.core.domain.model.event.ImageEvent
import com.buzbuz.smartautoclicker.core.domain.model.event.OffsetRepeatMatchMode
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

    /** Event label for detailed processing logs. */
    private var currentEventContext: String? = null

    /** Bitmap load / detection timings for the last image condition check. */
    private var lastImageTimings: ImageConditionTimings? = null

    private data class ImageConditionTimings(
        val bitmapLoadNs: Long,
        val detectionNs: Long,
    )

    /**
     * Set only during a [verifyConditions], it contains the system time at verification start.
     * This allows to use the same reference time for all conditions during the same verification loop.
     */
    private var currentVerificationTsMs: Long? = null

    suspend fun verifyConditions(
        @ConditionOperator operator: Int,
        conditions: List<Condition>,
        eventContext: String? = currentEventContext,
    ): ConditionsResults {
        verificationResults.reset()
        currentVerificationTsMs = System.currentTimeMillis()
        currentEventContext = eventContext

        val batchStartNs = SystemClock.elapsedRealtimeNanos()
        ConditionProcessingLog.batchStarted(eventContext, operator, conditions.size)

        var verificationResult: ProcessedConditionResult
        for (condition in conditions) {
            val conditionStartNs = SystemClock.elapsedRealtimeNanos()
            verificationResult = verifyCondition(condition)
            val imageTimings = lastImageTimings.also { lastImageTimings = null }
            ConditionProcessingLog.conditionProcessed(
                eventContext = eventContext,
                condition = condition,
                result = verificationResult,
                durationNs = SystemClock.elapsedRealtimeNanos() - conditionStartNs,
                bitmapLoadNs = imageTimings?.bitmapLoadNs,
                detectionNs = imageTimings?.detectionNs,
            )
            verificationResults.addResult(condition.getValidId(), verificationResult)

            if (operator == OR && verificationResult.isFulfilled) {
                verificationResults.setFulfilledState(true)
                ConditionProcessingLog.batchCompleted(eventContext, true, SystemClock.elapsedRealtimeNanos() - batchStartNs)
                currentEventContext = null
                return verificationResults
            }
            if (operator == AND && !verificationResult.isFulfilled) {
                verificationResults.setFulfilledState(false)
                ConditionProcessingLog.batchCompleted(eventContext, false, SystemClock.elapsedRealtimeNanos() - batchStartNs)
                currentEventContext = null
                return verificationResults
            }

            yield()
        }

        val fulfilled = operator == AND
        verificationResults.setFulfilledState(fulfilled)
        ConditionProcessingLog.batchCompleted(eventContext, fulfilled, SystemClock.elapsedRealtimeNanos() - batchStartNs)
        currentEventContext = null
        return verificationResults
    }

    suspend fun verifyOffsetRepeatImageEvent(event: ImageEvent, eventContext: String? = null): ConditionsResults {
        val resolvedEventContext = eventContext ?: "image:${event.name}(id=${event.id.databaseId})"
        verificationResults.reset()
        currentVerificationTsMs = System.currentTimeMillis()
        currentEventContext = resolvedEventContext

        val batchStartNs = SystemClock.elapsedRealtimeNanos()
        ConditionProcessingLog.batchStarted(
            eventContext = resolvedEventContext,
            operator = event.conditionOperator,
            conditionCount = event.conditions.size,
        )

        val screenBounds = scalingManager.getScaledScreenBounds()
        val matches = mutableListOf<OffsetRepeatMatch>()

        for (instanceIndex in 0..event.offsetRepeatCount) {
            val dxScreen = event.offsetRepeatX * instanceIndex
            val dyScreen = event.offsetRepeatY * instanceIndex
            val dxScaled = scalingManager.scaleDownOffset(dxScreen)
            val dyScaled = scalingManager.scaleDownOffset(dyScreen)
            val splitSearchPadding = if (event.offsetRepeatY == SPLIT_SCREEN_Y_OFFSET_PX && instanceIndex > 0) {
                scalingManager.scaleDownOffset(SPLIT_SCREEN_SEARCH_PADDING_PX)
            } else {
                0
            }

            val instanceResults = mutableListOf<Pair<Long, ProcessedConditionResult>>()
            var instanceFulfilled = when (event.conditionOperator) {
                AND -> true
                OR -> false
                else -> false
            }

            for (condition in event.conditions) {
                val conditionStartNs = SystemClock.elapsedRealtimeNanos()
                val instanceSuffix = "instance=$instanceIndex offset=($dxScreen,$dyScreen)"
                val baseScaling = scalingManager.getImageConditionScalingInfo(condition)
                val result = if (baseScaling == null) {
                    condition.toInvalidConditionResult()
                } else {
                    val imageArea = baseScaling.imageArea.translated(dxScaled, dyScaled)
                    val detectionArea = baseScaling.detectionArea
                        .translated(dxScaled, dyScaled)
                        .padded(splitSearchPadding)
                        .clampedTo(screenBounds)
                    when {
                        !imageArea.fitsIn(screenBounds) ->
                            condition.toNotDetectedResult()
                        detectionArea.canContain(imageArea) ->
                            verifyImageConditionInArea(
                                condition = condition,
                                scaledConditionArea = baseScaling.copy(imageArea = imageArea, detectionArea = detectionArea),
                                detectionArea = detectionArea,
                                notifyProgress = false,
                                logContextSuffix = instanceSuffix,
                            )
                        else -> condition.toNotDetectedResult()
                    }
                }

                ConditionProcessingLog.conditionProcessed(
                    eventContext = resolvedEventContext,
                    condition = condition,
                    result = result,
                    durationNs = SystemClock.elapsedRealtimeNanos() - conditionStartNs,
                    contextSuffix = instanceSuffix,
                    bitmapLoadNs = lastImageTimings?.bitmapLoadNs,
                    detectionNs = lastImageTimings?.detectionNs,
                )
                lastImageTimings = null
                instanceResults += condition.getValidId() to result

                if (event.conditionOperator == OR && result.isFulfilled) {
                    instanceFulfilled = true
                    break
                }
                if (event.conditionOperator == AND && !result.isFulfilled) {
                    instanceFulfilled = false
                    break
                }
            }

            if (instanceFulfilled) {
                val match = OffsetRepeatMatch(
                    instanceIndex = instanceIndex,
                    dx = dxScreen,
                    dy = dyScreen,
                    results = instanceResults.toMap(),
                )
                matches += match

                if (event.offsetRepeatMatchMode == OffsetRepeatMatchMode.FIRST_MATCH) {
                    verificationResults.setOffsetRepeatMatches(matches)
                    ConditionProcessingLog.batchCompleted(
                        eventContext = resolvedEventContext,
                        fulfilled = true,
                        durationNs = SystemClock.elapsedRealtimeNanos() - batchStartNs,
                    )
                    currentEventContext = null
                    return verificationResults
                }
            }

            yield()
        }

        if (matches.isNotEmpty()) {
            verificationResults.setOffsetRepeatMatches(matches)
            ConditionProcessingLog.batchCompleted(
                eventContext = resolvedEventContext,
                fulfilled = true,
                durationNs = SystemClock.elapsedRealtimeNanos() - batchStartNs,
            )
        } else {
            verificationResults.setFulfilledState(false)
            ConditionProcessingLog.batchCompleted(
                eventContext = resolvedEventContext,
                fulfilled = false,
                durationNs = SystemClock.elapsedRealtimeNanos() - batchStartNs,
            )
        }

        currentEventContext = null
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
        logContextSuffix: String = "",
    ): ProcessedConditionResult.Image {
        if (notifyProgress) progressListener?.onImageConditionProcessingStarted()

        val bitmapLoadStartNs = SystemClock.elapsedRealtimeNanos()
        val bitmap = bitmapSupplier(
            condition.path,
            scaledConditionArea.imageArea.width(),
            scaledConditionArea.imageArea.height(),
        )
        val bitmapLoadNs = SystemClock.elapsedRealtimeNanos() - bitmapLoadStartNs

        val result = bitmap?.let { conditionBitmap ->
            val detectionStartNs = SystemClock.elapsedRealtimeNanos()
            val detectionResult = imageDetector.detectCondition(
                conditionBitmap = conditionBitmap,
                conditionWidth = scaledConditionArea.imageArea.width(),
                conditionHeight = scaledConditionArea.imageArea.height(),
                detectionArea = detectionArea,
                threshold = condition.threshold,
            )
            val detectionNs = SystemClock.elapsedRealtimeNanos() - detectionStartNs
            lastImageTimings = ImageConditionTimings(bitmapLoadNs = bitmapLoadNs, detectionNs = detectionNs)
            condition.toImageResult(detectionResult)
        } ?: run {
            lastImageTimings = ImageConditionTimings(bitmapLoadNs = bitmapLoadNs, detectionNs = 0L)
            condition.toInvalidConditionResult()
        }

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

    private fun Rect.canContain(conditionArea: Rect): Boolean =
        left >= 0 && top >= 0 && width() >= conditionArea.width() && height() >= conditionArea.height()

    private fun Rect.fitsIn(bounds: Rect): Boolean =
        left >= bounds.left && top >= bounds.top && right <= bounds.right && bottom <= bounds.bottom

    private fun Rect.translated(dx: Int, dy: Int): Rect =
        Rect(left + dx, top + dy, right + dx, bottom + dy)

    private fun Rect.padded(padding: Int): Rect =
        if (padding <= 0) this else Rect(left - padding, top - padding, right + padding, bottom + padding)

    private fun Rect.clampedTo(bounds: Rect): Rect =
        Rect(
            left.coerceIn(bounds.left, bounds.right),
            top.coerceIn(bounds.top, bounds.bottom),
            right.coerceIn(bounds.left, bounds.right),
            bottom.coerceIn(bounds.top, bounds.bottom),
        )

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
