/*
 * Copyright (C) 2026 Nicolas Espinoza
 */
package com.buzbuz.smartautoclicker.feature.backup.domain

import android.graphics.Point
import com.buzbuz.smartautoclicker.core.database.CLICK_DATABASE_VERSION
import com.buzbuz.smartautoclicker.core.database.entity.CompleteScenario
import com.buzbuz.smartautoclicker.core.dumb.data.database.DUMB_DATABASE_VERSION
import com.buzbuz.smartautoclicker.core.dumb.data.database.DumbScenarioWithActions
import com.buzbuz.smartautoclicker.feature.backup.data.dumb.DumbScenarioBackup
import com.buzbuz.smartautoclicker.feature.backup.data.dumb.DumbScenarioSerializer
import com.buzbuz.smartautoclicker.feature.backup.data.smart.ScenarioBackup
import com.buzbuz.smartautoclicker.feature.backup.data.smart.ScenarioSerializer
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

object BackupSerialization {

    private val smartSerializer = ScenarioSerializer()
    private val dumbSerializer = DumbScenarioSerializer()

    fun encodeSmartScenario(completeScenario: CompleteScenario, screenSize: Point): String {
        val backup = ScenarioBackup(
            version = CLICK_DATABASE_VERSION,
            screenWidth = screenSize.x,
            screenHeight = screenSize.y,
            scenario = completeScenario,
        )
        return ByteArrayOutputStream().use { stream ->
            smartSerializer.serialize(backup, stream)
            stream.toString(Charsets.UTF_8.name())
        }
    }

    fun decodeSmartScenario(payloadJson: String): ScenarioBackup? =
        smartSerializer.deserialize(ByteArrayInputStream(payloadJson.toByteArray(Charsets.UTF_8)))

    fun encodeDumbScenario(scenarioWithActions: DumbScenarioWithActions, screenSize: Point): String {
        val backup = DumbScenarioBackup(
            version = DUMB_DATABASE_VERSION,
            screenWidth = screenSize.x,
            screenHeight = screenSize.y,
            dumbScenario = scenarioWithActions,
        )
        return ByteArrayOutputStream().use { stream ->
            dumbSerializer.serialize(backup, stream)
            stream.toString(Charsets.UTF_8.name())
        }
    }

    fun decodeDumbScenario(payloadJson: String): DumbScenarioBackup? =
        dumbSerializer.deserialize(ByteArrayInputStream(payloadJson.toByteArray(Charsets.UTF_8)))
}
