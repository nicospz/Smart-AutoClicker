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
package com.buzbuz.smartautoclicker.core.dumb.data

import android.util.Log

import com.buzbuz.smartautoclicker.core.base.DatabaseListUpdater
import com.buzbuz.smartautoclicker.core.base.extensions.mapList
import com.buzbuz.smartautoclicker.core.base.identifier.DATABASE_ID_INSERTION
import com.buzbuz.smartautoclicker.core.base.identifier.Identifier
import com.buzbuz.smartautoclicker.core.dumb.data.database.DumbActionEntity
import com.buzbuz.smartautoclicker.core.dumb.data.database.DumbDatabase
import com.buzbuz.smartautoclicker.core.dumb.data.database.DumbScenarioDao
import com.buzbuz.smartautoclicker.core.dumb.data.database.DumbScenarioStatsEntity
import com.buzbuz.smartautoclicker.core.dumb.data.database.DumbScenarioEntity
import com.buzbuz.smartautoclicker.core.dumb.data.database.DumbScenarioSyncMeta
import com.buzbuz.smartautoclicker.core.dumb.data.database.DumbScenarioWithActions
import com.buzbuz.smartautoclicker.core.dumb.domain.model.DumbAction
import com.buzbuz.smartautoclicker.core.dumb.domain.model.DumbScenario
import com.buzbuz.smartautoclicker.core.dumb.domain.model.toDomain
import com.buzbuz.smartautoclicker.core.dumb.domain.model.toEntity

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.lang.Exception
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DumbScenarioDataSource @Inject constructor(
    database: DumbDatabase,
) {

    private val dumbScenarioDao: DumbScenarioDao = database.dumbScenarioDao()

    /** Updater for a list of actions. */
    private val dumbActionsUpdater = DatabaseListUpdater<DumbAction, DumbActionEntity>()

    val getAllDumbScenarios: Flow<List<DumbScenario>> =
        dumbScenarioDao.getDumbScenariosWithActionsFlow()
            .mapList { it.toDomain() }

    suspend fun getDumbScenario(dbId: Long): DumbScenario? =
        dumbScenarioDao.getDumbScenariosWithAction(dbId)
            ?.toDomain()

    suspend fun getDumbScenarioWithActionsBySyncId(syncId: String): DumbScenarioWithActions? =
        dumbScenarioDao.getDumbScenarioEntityBySyncId(syncId)?.id?.let { dbId ->
            dumbScenarioDao.getDumbScenariosWithAction(dbId)
        }

    suspend fun getAllDumbScenarioSyncMeta(): List<DumbScenarioSyncMeta> =
        dumbScenarioDao.getAllSyncMeta()

    suspend fun getDumbScenarioDatabaseIdBySyncId(syncId: String): Long? =
        dumbScenarioDao.getDumbScenarioEntityBySyncId(syncId)?.id

    fun getDumbScenarioFlow(dbId: Long): Flow<DumbScenario?> =
        dumbScenarioDao.getDumbScenariosWithActionFlow(dbId)
            .map { it?.toDomain() }

    fun getAllDumbActionsExcept(scenarioDbId: Long): Flow<List<DumbAction>> =
        dumbScenarioDao.getAllDumbActionsExcept(scenarioDbId)
            .mapList { it.toDomain() }

    suspend fun addDumbScenario(scenario: DumbScenario) {
        Log.d(TAG, "Add dumb scenario $scenario")
        val now = System.currentTimeMillis()
        val entity = scenario.toEntity().copy(
            syncId = scenario.syncId.ifBlank { UUID.randomUUID().toString() },
            updatedAtMs = if (scenario.updatedAtMs > 0L) scenario.updatedAtMs else now,
            deletedAtMs = null,
        )
        updateDumbScenarioActions(
            scenarioDbId = dumbScenarioDao.addDumbScenario(entity),
            actions = scenario.dumbActions,
        )
    }

    suspend fun addDumbScenarioCopy(scenarioDbId: Long, copyName: String): Long? =
        dumbScenarioDao.getDumbScenariosWithAction(scenarioDbId)?.let { scenarioWithActions ->
            addDumbScenarioCopy(scenarioWithActions, copyName)
        }

    suspend fun addDumbScenarioCopy(scenarioWithActions: DumbScenarioWithActions, copyName: String? = null): Long? {
        Log.d(TAG, "Add dumb scenario to copy ${scenarioWithActions.scenario}")

        return try {
            val scenarioId = dumbScenarioDao.addDumbScenario(
                scenarioWithActions.scenario.copy(
                    id = DATABASE_ID_INSERTION,
                    name = copyName ?: scenarioWithActions.scenario.name,
                )
            )

            dumbScenarioDao.addDumbActions(
                scenarioWithActions.dumbActions.map { dumbAction ->
                    dumbAction.copy(
                        id = DATABASE_ID_INSERTION,
                        dumbScenarioId = scenarioId,
                    )
                }
            )

            scenarioId
        } catch (ex: Exception) {
            Log.e(TAG, "Error while inserting scenario copy", ex)
            null
        }
    }

    suspend fun markAsUsed(scenarioDbId: Long) {
        val previousStats = dumbScenarioDao.getScenarioStats(scenarioDbId)
        if (previousStats != null) {
            dumbScenarioDao.updateScenarioStats(
                previousStats.copy(
                    lastStartTimestampMs = System.currentTimeMillis(),
                    startCount = previousStats.startCount + 1,
                )
            )
        } else {
            dumbScenarioDao.addScenarioStats(
                DumbScenarioStatsEntity(
                    id = DATABASE_ID_INSERTION,
                    scenarioId = scenarioDbId,
                    lastStartTimestampMs = System.currentTimeMillis(),
                    startCount = 1,
                )
            )
        }
    }

    suspend fun updateDumbScenario(scenario: DumbScenario) {
        Log.d(TAG, "Update dumb scenario $scenario")
        val now = System.currentTimeMillis()
        val scenarioEntity = scenario.toEntity().copy(
            updatedAtMs = now,
            deletedAtMs = null,
        )

        dumbScenarioDao.updateDumbScenario(scenarioEntity)
        updateDumbScenarioActions(scenarioEntity.id, scenario.dumbActions)
    }

    suspend fun upsertDumbScenarioBySyncId(
        scenarioWithActions: DumbScenarioWithActions,
        syncId: String,
        updatedAtMs: Long,
    ): Long? {
        val existing = dumbScenarioDao.getDumbScenarioEntityBySyncId(syncId)
        val baseScenario = scenarioWithActions.toDomain(asDomain = true)
        val dumbScenario = if (existing != null) {
            baseScenario.copy(
                id = Identifier(databaseId = existing.id),
                syncId = syncId,
                updatedAtMs = updatedAtMs,
                deletedAtMs = null,
            )
        } else {
            baseScenario.copy(
                syncId = syncId,
                updatedAtMs = updatedAtMs,
                deletedAtMs = null,
            )
        }
        return if (existing != null) {
            updateDumbScenario(dumbScenario)
            dumbScenarioDao.updateSyncTimestamps(existing.id, updatedAtMs, null)
            existing.id
        } else {
            val now = System.currentTimeMillis()
            val entity = dumbScenario.toEntity().copy(
                syncId = syncId,
                updatedAtMs = updatedAtMs,
                deletedAtMs = null,
            )
            val scenarioId = dumbScenarioDao.addDumbScenario(entity)
            updateDumbScenarioActions(scenarioId, dumbScenario.dumbActions)
            scenarioId
        }
    }

    suspend fun deleteDumbScenarioBySyncId(syncId: String): Boolean {
        val entity = dumbScenarioDao.getDumbScenarioEntityBySyncId(syncId) ?: return false
        dumbScenarioDao.deleteDumbScenario(entity.id)
        return true
    }

    suspend fun updateDumbScenarioFavorite(scenarioDbId: Long, isFavorite: Boolean) {
        dumbScenarioDao.updateDumbScenarioFavorite(
            dumbScenarioId = scenarioDbId,
            isFavorite = isFavorite,
            updatedAtMs = System.currentTimeMillis(),
        )
    }

    private suspend fun updateDumbScenarioActions(scenarioDbId: Long, actions: List<DumbAction>) {
        val updater = DatabaseListUpdater<DumbAction, DumbActionEntity>()
        updater.refreshUpdateValues(
            currentEntities = dumbScenarioDao.getDumbActions(scenarioDbId),
            newItems = actions,
            mappingClosure = { action -> action.toEntity(scenarioDbId = scenarioDbId) }
        )

        Log.d(TAG, "Dumb actions updater: $dumbActionsUpdater")

        updater.executeUpdate(
            addList = dumbScenarioDao::addDumbActions,
            updateList = dumbScenarioDao::updateDumbActions,
            removeList = dumbScenarioDao::deleteDumbActions,
        )
    }

    suspend fun deleteDumbScenario(scenario: DumbScenario) {
        Log.d(TAG, "Delete dumb scenario $scenario")

        dumbScenarioDao.deleteDumbScenario(scenario.id.databaseId)
    }
}

private const val TAG = "DumbScenarioDataSource"
