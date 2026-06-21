package com.buzbuz.smartautoclicker.feature.throwlet.needle

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.buzbuz.smartautoclicker.feature.throwlet.BitmapTemplateMatcher
import com.buzbuz.smartautoclicker.feature.throwlet.HelperLane
import com.buzbuz.smartautoclicker.feature.throwlet.RectI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.math.roundToInt

@Serializable
internal data class ManifestSize(val width: Int, val height: Int)

@Serializable
internal data class ManifestRect(val left: Int, val top: Int, val right: Int, val bottom: Int)

@Serializable
internal data class ManifestPoint(val x: Int, val y: Int)

@Serializable
internal data class ManifestEntry(
    val mode: String,
    val feature: String,
    val lane: String,
    val variantOrder: Int = 0,
    val assetPath: String,
    val sourceSize: ManifestSize,
    val cropRect: ManifestRect,
    val searchRect: ManifestRect = cropRect,
    val tapPoint: ManifestPoint? = null,
    val threshold: Int = 85,
) {
    val resolvedTapPoint: ManifestPoint
        get() = tapPoint ?: ManifestPoint(
            x = (cropRect.left + cropRect.right) / 2,
            y = (cropRect.top + cropRect.bottom) / 2,
        )
}

object BundledCatchNeedles {
    private const val MANIFEST = "needles/manifest.json"
    private const val SYNTHETIC_TOP_SEARCH_PADDING_PX = 72
    internal val manifestJson = Json { ignoreUnknownKeys = true }
    private val json get() = manifestJson

    suspend fun load(
        context: Context,
        splitMode: Boolean,
        laneDividerPx: Int,
    ): List<LoadedCatchNeedle> = withContext(Dispatchers.IO) {
        val entries = context.assets.open(MANIFEST).bufferedReader().use { reader ->
            json.decodeFromString<List<ManifestEntry>>(reader.readText())
        }
        val runtime = planRuntimeNeedles(entries, splitMode, laneDividerPx)

        runtime.mapNotNull { needle ->
            val bitmap = runCatching {
                context.assets.open(needle.assetPath).use(BitmapFactory::decodeStream)
            }.getOrNull() ?: return@mapNotNull null
            LoadedCatchNeedle(needle, bitmap)
        }
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
            catchEntries.filter { it.lane == "BOTTOM" }.flatMap { entry ->
                listOf(
                    entry.toCatchNeedle(NeedleLane.TOP, -laneDividerPx, SYNTHETIC_TOP_SEARCH_PADDING_PX),
                    entry.toCatchNeedle(NeedleLane.BOTTOM, 0, 0),
                )
            }
        } else {
            catchEntries.filter { it.lane == "FULL" }.map { it.toCatchNeedle(NeedleLane.FULL, 0, 0) }
        }
    }
}

internal fun ManifestEntry.toCatchNeedle(
    lane: NeedleLane,
    yOffset: Int,
    searchPaddingPx: Int,
): CatchNeedle {
    val top = (cropRect.top + yOffset).coerceIn(0, sourceSize.height)
    val bottom = (cropRect.bottom + yOffset).coerceIn(0, sourceSize.height)
    return CatchNeedle(
        feature = CatchNeedleFeature.fromManifestValue(feature)
            ?: error("unsupported catch needle feature: $feature"),
        lane = lane,
        variantOrder = variantOrder,
        assetPath = assetPath,
        sourceWidth = sourceSize.width,
        sourceHeight = sourceSize.height,
        cropLeft = cropRect.left,
        cropTop = top,
        cropRight = cropRect.right,
        cropBottom = bottom,
        searchLeft = (searchRect.left - searchPaddingPx).coerceAtLeast(0),
        searchTop = (searchRect.top + yOffset - searchPaddingPx).coerceAtLeast(0),
        searchRight = (searchRect.right + searchPaddingPx).coerceAtMost(sourceSize.width),
        searchBottom = (searchRect.bottom + yOffset + searchPaddingPx).coerceAtMost(sourceSize.height),
        tapX = resolvedTapPoint.x,
        tapY = (resolvedTapPoint.y + yOffset).coerceIn(0, sourceSize.height),
        thresholdPercent = threshold,
    )
}

fun HelperLane.toNeedleLane(): NeedleLane = when (this) {
    HelperLane.FULL -> NeedleLane.FULL
    HelperLane.SPLIT_TOP -> NeedleLane.TOP
    HelperLane.SPLIT_BOTTOM -> NeedleLane.BOTTOM
}

object NormalizedNeedleFrame {
    const val SCALE = 0.3f

    fun scaled(bitmap: Bitmap): Bitmap {
        val width = (bitmap.width * SCALE).roundToInt().coerceAtLeast(1)
        val height = (bitmap.height * SCALE).roundToInt().coerceAtLeast(1)
        if (width == bitmap.width && height == bitmap.height) return bitmap
        return Bitmap.createScaledBitmap(bitmap, width, height, true)
    }
}

object SimpleTemplateMatcher {
    fun bestMatch(
        screen: Bitmap,
        needle: CatchNeedle,
        templateBitmap: Bitmap,
    ): NeedleMatch? {
        val expected = needle.expectedRect(screen.width, screen.height)
        val search = needle.searchRect(screen.width, screen.height)
        val score = BitmapTemplateMatcher.bestMatchPercent(
            screen = screen,
            templateBitmap = templateBitmap,
            expectedRect = RectI(expected.left, expected.top, expected.right, expected.bottom),
            searchRect = RectI(search.left, search.top, search.right, search.bottom),
            thresholdPercent = needle.thresholdPercent,
        ) ?: return null
        val (tapX, tapY) = needle.tapPoint(screen.width, screen.height)
        return NeedleMatch(
            feature = needle.feature,
            lane = needle.lane,
            scorePercent = score,
            tapX = tapX,
            tapY = tapY,
        )
    }
}
