/*
 * Copyright (C) 2026 Nicolas Espinoza
 */
package com.buzbuz.smartautoclicker.core.processing.data.processor

import android.util.Log
import com.buzbuz.smartautoclicker.core.domain.model.AND
import com.buzbuz.smartautoclicker.core.domain.model.ConditionOperator
import com.buzbuz.smartautoclicker.core.domain.model.OR
import com.buzbuz.smartautoclicker.core.domain.model.condition.Condition
import com.buzbuz.smartautoclicker.core.domain.model.condition.ImageCondition
import com.buzbuz.smartautoclicker.core.domain.model.condition.TriggerCondition
import com.buzbuz.smartautoclicker.core.domain.model.event.ImageEvent
import com.buzbuz.smartautoclicker.core.domain.model.event.TriggerEvent
import com.buzbuz.smartautoclicker.core.base.interfaces.sortedByPriority
import com.buzbuz.smartautoclicker.core.processing.domain.model.ProcessedConditionResult
import kotlin.math.roundToLong

internal object ConditionProcessingLog {

    private const val TABLE_PREFIX = "TABLE\t"

    fun logScenarioCatalog(
        scenarioName: String,
        triggerEvents: List<TriggerEvent>,
        imageEvents: List<ImageEvent>,
    ) {
        logTableRow(
            "SCENARIO", "start",
            scenarioName.ifBlank { "?" },
            triggerEvents.size.toString(),
            imageEvents.size.toString(),
            "", "", "", "", "",
        )
        var eventOrder = 0
        var conditionOrder = 0
        triggerEvents.forEach { event ->
            eventOrder += 1
            logTableRow(
                "EVENT", eventOrder.toString(),
                event.getValidId().toString(),
                "TRG",
                event.name,
                yn(event.enabledOnStart),
                operatorLabel(event.conditionOperator),
                event.conditions.size.toString(),
                "", "", "",
            )
            event.conditions.forEach { condition ->
                conditionOrder += 1
                logConditionDefinition(conditionOrder, event.name, condition, "TRG")
            }
        }
        imageEvents.sortedByPriority().forEach { event ->
            eventOrder += 1
            logTableRow(
                "EVENT", eventOrder.toString(),
                event.getValidId().toString(),
                "IMG",
                event.name,
                yn(event.enabledOnStart),
                operatorLabel(event.conditionOperator),
                event.conditions.size.toString(),
                "", "", "",
            )
            event.conditions.forEach { condition ->
                conditionOrder += 1
                logConditionDefinition(conditionOrder, event.name, condition, "IMG")
            }
        }
        logTableRow(
            "SCENARIO", "end",
            scenarioName.ifBlank { "?" },
            eventOrder.toString(),
            conditionOrder.toString(),
            "", "", "", "", "", "",
        )
    }

    private fun logConditionDefinition(order: Int, eventName: String, condition: Condition, type: String) {
        logTableRow(
            "COND_DEF", order.toString(),
            condition.getValidId().toString(),
            eventName,
            condition.name,
            type,
            "", "", "", "", "", "",
        )
    }

    fun batchStarted(eventContext: String?, @ConditionOperator operator: Int, conditionCount: Int) {
        logTableRow(
            "BATCH", "start",
            eventContext ?: "unknown",
            operatorLabel(operator),
            conditionCount.toString(),
            "", "", "", "", "", "",
        )
    }

    fun batchCompleted(eventContext: String?, fulfilled: Boolean?, durationNs: Long) {
        logTableRow(
            "BATCH", "end",
            eventContext ?: "unknown",
            if (fulfilled == true) "Y" else "N",
            durationNs.toMs().toString(),
            "", "", "", "", "", "",
        )
    }

    fun conditionProcessed(
        eventContext: String?,
        condition: Condition,
        result: ProcessedConditionResult,
        durationNs: Long,
        contextSuffix: String = "",
        bitmapLoadNs: Long? = null,
        detectionNs: Long? = null,
    ) {
        val event = shortenEvent(eventContext)
        val notes = contextSuffix.trim()
        when (result) {
            is ProcessedConditionResult.Image -> logTableRow(
                "COND", result.condition.getValidId().toString(),
                event,
                condition.name,
                "IMG",
                yn(result.isFulfilled),
                yn(result.haveBeenDetected),
                result.confidenceRate.formatConfidence(),
                durationNs.toMs().toString(),
                detectionNs?.toMs()?.toString() ?: "-",
                bitmapLoadNs?.toMs()?.toString() ?: "-",
                notes,
            )
            is ProcessedConditionResult.Trigger -> logTableRow(
                "COND", result.condition.getValidId().toString(),
                event,
                condition.name,
                "TRG",
                yn(result.isFulfilled),
                "-",
                "-",
                durationNs.toMs().toString(),
                "-",
                "-",
                result.condition.triggerSubtype().ifBlank { notes },
            )
        }
    }

    private fun logTableRow(vararg columns: String) {
        Log.i(TAG, TABLE_PREFIX + columns.joinToString("\t") { it.sanitize() })
    }

    private fun String.sanitize(): String =
        replace('\t', ' ').replace('\n', ' ').trim()

    private fun shortenEvent(eventContext: String?): String {
        if (eventContext.isNullOrBlank()) return "?"
        val withoutPrefix = eventContext.substringAfter(':').substringBefore("(id=")
        return withoutPrefix.ifBlank { eventContext }.take(28)
    }

    private fun yn(value: Boolean): String = if (value) "Y" else "N"

    private fun TriggerCondition.triggerSubtype(): String = when (this) {
        is TriggerCondition.OnBroadcastReceived -> "broadcast:$intentAction"
        is TriggerCondition.OnCounterCountReached -> "counter:$counterName $comparisonOperation"
        is TriggerCondition.OnTimerReached -> "timer:${durationMs}ms"
    }

    private fun operatorLabel(@ConditionOperator operator: Int): String = when (operator) {
        AND -> "AND"
        OR -> "OR"
        else -> operator.toString()
    }

    private fun Long.toMs(): Long = (this / 1_000_000.0).roundToLong()

    private fun Double.formatConfidence(): String = String.format("%.0f", this)
}

private const val TAG = "SacConditionProcessing"
