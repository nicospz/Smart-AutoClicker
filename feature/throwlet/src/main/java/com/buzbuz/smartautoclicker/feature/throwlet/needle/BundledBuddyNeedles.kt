package com.buzbuz.smartautoclicker.feature.throwlet.needle

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.buzbuz.smartautoclicker.feature.throwlet.BitmapTemplateMatcher
import com.buzbuz.smartautoclicker.feature.throwlet.RectI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object BundledBuddyNeedles {
    private const val MANIFEST = "needles/manifest.json"
    private const val SYNTHETIC_TOP_SEARCH_PADDING_PX = 72

    suspend fun load(
        context: Context,
        splitMode: Boolean,
        laneDividerPx: Int,
    ): List<LoadedBuddyNeedle> = withContext(Dispatchers.IO) {
        val entries = context.assets.open(MANIFEST).bufferedReader().use { reader ->
            BundledCatchNeedles.manifestJson.decodeFromString<List<ManifestEntry>>(reader.readText())
        }
        val runtime = planRuntimeNeedles(entries, splitMode, laneDividerPx)
        runtime.mapNotNull { needle ->
            val bitmap = runCatching {
                context.assets.open(needle.assetPath).use(BitmapFactory::decodeStream)
            }.getOrNull() ?: return@mapNotNull null
            LoadedBuddyNeedle(needle, bitmap)
        }
    }

    internal fun planRuntimeNeedles(
        entries: List<ManifestEntry>,
        splitMode: Boolean,
        laneDividerPx: Int,
    ): List<BuddyNeedle> {
        val buddyEntries = entries.filter { entry ->
            entry.mode == "BUDDY" && BuddyNeedleFeature.fromManifestValue(entry.feature) != null
        }
        return if (splitMode) {
            buddyEntries.filter { it.lane == "FULL" || it.lane == "BOTTOM" }.flatMap { entry ->
                listOf(
                    entry.toBuddyNeedle(NeedleLane.TOP, -laneDividerPx, SYNTHETIC_TOP_SEARCH_PADDING_PX),
                    entry.toBuddyNeedle(NeedleLane.BOTTOM, 0, 0),
                )
            }
        } else {
            buddyEntries.filter { it.lane == "FULL" }.map { it.toBuddyNeedle(NeedleLane.FULL, 0, 0) }
        }
    }
}

internal fun ManifestEntry.toBuddyNeedle(
    lane: NeedleLane,
    yOffset: Int,
    searchPaddingPx: Int,
): BuddyNeedle {
    val top = (cropRect.top + yOffset).coerceIn(0, sourceSize.height)
    val bottom = (cropRect.bottom + yOffset).coerceIn(0, sourceSize.height)
    val tapYShifted = (resolvedTapPoint.y + yOffset).coerceIn(0, sourceSize.height - 1)
    return BuddyNeedle(
        feature = BuddyNeedleFeature.fromManifestValue(feature)
            ?: error("unsupported buddy needle feature: $feature"),
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
        tapY = tapYShifted,
        thresholdPercent = threshold,
    )
}

object BuddyNeedleMatcher {
    fun bestMatch(
        screen: Bitmap,
        needles: List<LoadedBuddyNeedle>,
        feature: BuddyNeedleFeature,
        lane: NeedleLane,
    ): BuddyNeedleMatch? {
        val scaled = NormalizedNeedleFrame.scaled(screen)
        try {
            var best: BuddyNeedleMatch? = null
            for (loaded in needles) {
                val needle = loaded.needle
                if (needle.feature != feature || needle.lane != lane) continue
                val expected = needle.expectedRect(scaled.width, scaled.height)
                val search = needle.searchRect(scaled.width, scaled.height)
                val score = BitmapTemplateMatcher.bestMatchPercent(
                    screen = scaled,
                    templateBitmap = loaded.template,
                    expectedRect = RectI(expected.left, expected.top, expected.right, expected.bottom),
                    searchRect = RectI(search.left, search.top, search.right, search.bottom),
                    thresholdPercent = needle.thresholdPercent,
                ) ?: continue
                val (tapX, tapY) = needle.tapPoint(screen.width, screen.height)
                val previous = best
                if (previous == null || score > previous.scorePercent) {
                    best = BuddyNeedleMatch(feature, lane, score, tapX, tapY)
                }
            }
            return best
        } finally {
            if (scaled !== screen) scaled.recycle()
        }
    }
}
