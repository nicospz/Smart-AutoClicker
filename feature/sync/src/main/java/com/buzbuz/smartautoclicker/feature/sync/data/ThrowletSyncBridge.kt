/*
 * Copyright (C) 2026 Nicolas Espinoza
 */
package com.buzbuz.smartautoclicker.feature.sync.data

import android.content.Context
import com.buzbuz.smartautoclicker.feature.throwlet.needle.CatchNeedleManifest
import com.buzbuz.smartautoclicker.feature.throwlet.needle.CatchNeedleRecord
import com.buzbuz.smartautoclicker.feature.throwlet.needle.CatchNeedleRect
import com.buzbuz.smartautoclicker.feature.throwlet.needle.CatchNeedleSize
import com.buzbuz.smartautoclicker.feature.throwlet.sync.SupabaseSyncRepository
import com.buzbuz.smartautoclicker.feature.throwlet.sync.SupabaseSyncResult
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Singleton
class ThrowletSyncBridge @Inject constructor(
    @ApplicationContext private val context: Context,
    private val legacySyncRepository: SupabaseSyncRepository,
    private val api: SacSupabaseClient,
) {
    suspend fun syncAll(): ThrowletSyncCounts = withContext(Dispatchers.IO) {
        val gestures = legacySyncRepository.syncGestures()
        val buddyCrops = legacySyncRepository.syncBuddyCrops()
        val catchNeedles = syncCatchNeedles()
        ThrowletSyncCounts(
            gestures = gestures,
            buddyCrops = buddyCrops,
            catchNeedlesPushed = catchNeedles.remotePushed,
            catchNeedlesPulled = catchNeedles.remotePulled,
        )
    }

    suspend fun syncCatchNeedles(): CatchNeedleSyncCounts = withContext(Dispatchers.IO) {
        if (!api.isConfigured) return@withContext CatchNeedleSyncCounts(skipped = true)
        val localRecords = CatchNeedleManifest.readRecords(context)
        val remoteNeedles = api.listCatchNeedles()
        val localById = localRecords.associateBy { it.id }
        val remoteById = remoteNeedles.associateBy { it.needleId }
        var pulled = 0
        var pushed = 0

        for (remote in remoteNeedles) {
            val local = localById[remote.needleId]
            if (remote.deletedAtMs != null) {
                if (local != null) {
                    CatchNeedleManifest.deleteRecord(context, remote.needleId)
                    pulled += 1
                }
                continue
            }
            if (local == null || remote.updatedAtMs > local.updatedAtMs) {
                CatchNeedleManifest.applySyncedRecord(
                    context,
                    remote.toLocalRecord(),
                    remote.imagePngBase64,
                )
                pulled += 1
            }
        }

        for (local in localRecords) {
            val remote = remoteById[local.id]
            if (remote == null || local.updatedAtMs > remote.updatedAtMs) {
                val imageBase64 = CatchNeedleManifest.encodeImageBase64(local) ?: continue
                api.upsertCatchNeedle(local.toRemoteRecord(imageBase64))
                pushed += 1
            }
        }
        CatchNeedleSyncCounts(remotePushed = pushed, remotePulled = pulled)
    }

    suspend fun pushCatchNeedle(recordId: String): SupabaseSyncResult = withContext(Dispatchers.IO) {
        if (!api.isConfigured) return@withContext SupabaseSyncResult(skipped = true)
        val record = CatchNeedleManifest.readRecords(context).find { it.id == recordId }
            ?: return@withContext SupabaseSyncResult(errorMessage = "catch needle missing")
        val imageBase64 = CatchNeedleManifest.encodeImageBase64(record)
            ?: return@withContext SupabaseSyncResult(errorMessage = "catch needle image missing")
        runCatching {
            api.upsertCatchNeedle(record.toRemoteRecord(imageBase64))
            SupabaseSyncResult(remotePushed = 1)
        }.getOrElse { SupabaseSyncResult(errorMessage = it.message) }
    }
}

private fun RemoteCatchNeedle.toLocalRecord(): CatchNeedleRecord =
    CatchNeedleRecord(
        id = needleId,
        mode = "CATCH",
        feature = feature,
        lane = lane,
        variantOrder = variantOrder,
        assetPath = "",
        sourceSize = CatchNeedleSize(sourceWidth, sourceHeight),
        cropRect = CatchNeedleRect(cropLeft, cropTop, cropRight, cropBottom),
        searchRect = CatchNeedleRect(searchLeft, searchTop, searchRight, searchBottom),
        threshold = threshold,
        createdAtMs = createdAtMs,
        updatedAtMs = updatedAtMs,
    )

private fun CatchNeedleRecord.toRemoteRecord(imagePngBase64: String): RemoteCatchNeedle =
    RemoteCatchNeedle(
        needleId = id,
        feature = feature,
        lane = lane,
        variantOrder = variantOrder,
        imagePngBase64 = imagePngBase64,
        sourceWidth = sourceSize.width,
        sourceHeight = sourceSize.height,
        cropLeft = cropRect.left,
        cropTop = cropRect.top,
        cropRight = cropRect.right,
        cropBottom = cropRect.bottom,
        searchLeft = searchRect.left,
        searchTop = searchRect.top,
        searchRight = searchRect.right,
        searchBottom = searchRect.bottom,
        threshold = threshold,
        createdAtMs = createdAtMs,
        updatedAtMs = updatedAtMs,
        deletedAtMs = null,
    )

data class ThrowletSyncCounts(
    val gestures: SupabaseSyncResult = SupabaseSyncResult(),
    val buddyCrops: SupabaseSyncResult = SupabaseSyncResult(),
    val catchNeedlesPushed: Int = 0,
    val catchNeedlesPulled: Int = 0,
)

data class CatchNeedleSyncCounts(
    val remotePushed: Int = 0,
    val remotePulled: Int = 0,
    val skipped: Boolean = false,
)
