package com.buzbuz.smartautoclicker.feature.throwlet.needle

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.buzbuz.smartautoclicker.feature.throwlet.HelperLane
import com.buzbuz.smartautoclicker.feature.throwlet.RectI
import com.buzbuz.smartautoclicker.feature.throwlet.SizeI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object UserCatchNeedles {
    private const val DIR = "needles/catch"
    private const val TOP_SEARCH_PADDING_PX = 72

    suspend fun load(
        context: Context,
        splitMode: Boolean,
        laneDividerPx: Int,
    ): List<LoadedCatchNeedle> = withContext(Dispatchers.IO) {
        val records = CatchNeedleManifest.readRecords(context)
        val entries = records.map { it.toManifestEntry() }
        val runtime = planRuntimeNeedles(entries, splitMode, laneDividerPx)
        runtime.mapNotNull { needle ->
            val bitmap = runCatching { BitmapFactory.decodeFile(needle.assetPath) }.getOrNull()
                ?: return@mapNotNull null
            LoadedCatchNeedle(needle, bitmap)
        }
    }

    fun changedAtMs(context: Context): Long {
        val records = readRecords(context)
        return records.maxOfOrNull { it.updatedAtMs } ?: 0L
    }

    suspend fun save(
        context: Context,
        feature: CatchNeedleFeature,
        lane: HelperLane,
        screenshotSize: SizeI,
        cropRect: RectI,
        searchRect: RectI,
        crop: Bitmap,
        topToBottomScreenshotDy: Int = screenshotSize.height / 2,
        threshold: Int = defaultThreshold(feature),
    ): File = withContext(Dispatchers.IO) {
        val dir = directory(context).also { it.mkdirs() }
        val normalizedLane = storageLane(lane)
        val normalizedCrop = normalizedRectForStorage(lane, cropRect, screenshotSize, topToBottomScreenshotDy)
        val normalizedSearch = normalizedRectForStorage(lane, searchRect, screenshotSize, topToBottomScreenshotDy)
        val id = "${System.currentTimeMillis()}_${feature.manifestValue.lowercase()}_${normalizedLane.lowercase()}"
        val image = File(dir, "$id.png")
        image.outputStream().use { output ->
            crop.compress(Bitmap.CompressFormat.PNG, 100, output)
        }

        val existing = readRecords(context)
        val variantOrder = existing.count { it.feature == feature.manifestValue && it.lane == normalizedLane }
        val record = CatchNeedleRecord(
            id = id,
            mode = "CATCH",
            feature = feature.manifestValue,
            lane = normalizedLane,
            variantOrder = variantOrder,
            assetPath = image.absolutePath,
            sourceSize = CatchNeedleSize(screenshotSize.width, screenshotSize.height),
            cropRect = normalizedCrop.toCatchRect(),
            searchRect = normalizedSearch.toCatchRect(),
            threshold = threshold,
            createdAtMs = System.currentTimeMillis(),
            updatedAtMs = System.currentTimeMillis(),
        )
        CatchNeedleManifest.writeRecord(context, record)
        image
    }

    internal fun planRuntimeNeedles(
        entries: List<ManifestEntry>,
        splitMode: Boolean,
        laneDividerPx: Int,
    ): List<CatchNeedle> {
        val catchEntries = entries.filter { entry ->
            entry.mode == "CATCH" && CatchNeedleFeature.fromManifestValue(entry.feature) != null
        }
        return if (splitMode) {
            catchEntries.flatMap { entry ->
                when (entry.lane) {
                    "BOTTOM" -> listOf(
                        entry.toCatchNeedle(NeedleLane.TOP, -laneDividerPx, TOP_SEARCH_PADDING_PX),
                        entry.toCatchNeedle(NeedleLane.BOTTOM, 0, 0),
                    )
                    "TOP" -> listOf(entry.toCatchNeedle(NeedleLane.TOP, 0, TOP_SEARCH_PADDING_PX))
                    else -> emptyList()
                }
            }
        } else {
            catchEntries.filter { it.lane == "FULL" }.map { it.toCatchNeedle(NeedleLane.FULL, 0, 0) }
        }
    }

    private fun readRecords(context: Context): List<CatchNeedleRecord> =
        CatchNeedleManifest.readRecords(context)

    private fun CatchNeedleRecord.toManifestEntry(): ManifestEntry = ManifestEntry(
        mode = mode,
        feature = feature,
        lane = lane,
        variantOrder = variantOrder,
        assetPath = assetPath,
        sourceSize = ManifestSize(sourceSize.width, sourceSize.height),
        cropRect = ManifestRect(cropRect.left, cropRect.top, cropRect.right, cropRect.bottom),
        searchRect = ManifestRect(searchRect.left, searchRect.top, searchRect.right, searchRect.bottom),
        threshold = threshold,
    )

    private fun storageLane(lane: HelperLane): String = when (lane) {
        HelperLane.FULL -> "FULL"
        HelperLane.SPLIT_TOP, HelperLane.SPLIT_BOTTOM -> "BOTTOM"
    }

    private fun normalizedRectForStorage(lane: HelperLane, rect: RectI, size: SizeI, topToBottomScreenshotDy: Int): RectI = when (lane) {
        HelperLane.SPLIT_TOP -> rect.translated(topToBottomScreenshotDy).clamped(size)
        else -> rect.clamped(size)
    }

    private fun RectI.translated(dy: Int): RectI = copy(top = top + dy, bottom = bottom + dy)

    private fun RectI.clamped(size: SizeI): RectI {
        val left = left.coerceIn(0, size.width - 1)
        val top = top.coerceIn(0, size.height - 1)
        val right = right.coerceIn(left + 1, size.width)
        val bottom = bottom.coerceIn(top + 1, size.height)
        return RectI(left, top, right, bottom)
    }

    private fun RectI.toCatchRect(): CatchNeedleRect = CatchNeedleRect(left, top, right, bottom)

    private fun directory(context: Context): File = File(context.filesDir, DIR)
}

fun defaultThreshold(feature: CatchNeedleFeature): Int = when (feature) {
    CatchNeedleFeature.BERRY_MENU -> 85
    CatchNeedleFeature.POKEBALL_READY -> 88
}
