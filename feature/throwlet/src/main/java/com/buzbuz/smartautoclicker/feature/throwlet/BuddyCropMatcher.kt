package com.buzbuz.smartautoclicker.feature.throwlet

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.buzbuz.smartautoclicker.feature.throwlet.data.ThrowletDatabase
import com.buzbuz.smartautoclicker.feature.throwlet.needle.NormalizedNeedleFrame
import kotlin.math.roundToInt

class BuddyCropMatcher(
    private val db: ThrowletDatabase,
) {
    fun match(
        screenshot: Bitmap,
        detectionLane: HelperLane,
        dividerPx: Int,
    ): CatchDetectionState {
        val crops = kotlinx.coroutines.runBlocking { db.buddyCropDao().enabled() }
        if (crops.isEmpty()) {
            return CatchDetectionState(null, null, null, false, "No buddy crops saved")
        }

        val scaled = NormalizedNeedleFrame.scaled(screenshot)
        val targetSize = SizeI(scaled.width, scaled.height)
        val scaledDivider = if (screenshot.height == scaled.height) {
            dividerPx
        } else {
            (dividerPx.toFloat() * scaled.height / screenshot.height).roundToInt()
        }

        var bestCrop: BuddyCropEntity? = null
        var bestScore = -1

        for (crop in crops) {
            val template = BitmapFactory.decodeFile(crop.imagePath) ?: continue
            try {
                val expected = BuddyCropStorage.runtimeCropRect(crop, targetSize, detectionLane, scaledDivider)
                val search = BuddyCropStorage.runtimeSearchRect(crop, targetSize, detectionLane, scaledDivider)
                val score = BitmapTemplateMatcher.bestMatchPercent(
                    screen = scaled,
                    templateBitmap = template,
                    expectedRect = expected,
                    searchRect = search,
                    thresholdPercent = crop.thresholdPercent,
                )
                if (score != null && score > bestScore) {
                    bestScore = score
                    bestCrop = crop
                }
            } finally {
                template.recycle()
            }
        }

        if (scaled !== screenshot) scaled.recycle()

        val match = bestCrop
        return if (match != null) {
            CatchDetectionState(
                pokemonKey = match.pokemonKey,
                pokemonName = match.pokemonName,
                confidencePercent = bestScore,
                hasGesture = false,
                message = "Detected buddy ${match.pokemonName}",
            )
        } else {
            CatchDetectionState(null, null, null, false, "No buddy matched in ${detectionLane.name}")
        }
    }
}
