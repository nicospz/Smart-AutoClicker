package com.buzbuz.smartautoclicker.feature.throwlet

import android.graphics.Bitmap
import android.graphics.Rect
import com.buzbuz.smartautoclicker.core.detection.NativeDetector
import kotlin.math.abs
import kotlin.math.roundToInt

data class BitmapTemplateMatch(
    val scorePercent: Int,
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
)

object BitmapTemplateMatcher {
    fun bestMatchPercent(
        screen: Bitmap,
        templateBitmap: Bitmap,
        expectedRect: RectI,
        searchRect: RectI,
        thresholdPercent: Int,
    ): Int? =
        bestMatch(screen, templateBitmap, expectedRect, searchRect, thresholdPercent)?.scorePercent

    fun bestMatch(
        screen: Bitmap,
        templateBitmap: Bitmap,
        expectedRect: RectI,
        searchRect: RectI,
        thresholdPercent: Int,
    ): BitmapTemplateMatch? =
        bestMatchNative(screen, templateBitmap, expectedRect, searchRect, thresholdPercent)

    private fun bestMatchNative(
        screen: Bitmap,
        templateBitmap: Bitmap,
        expectedRect: RectI,
        searchRect: RectI,
        thresholdPercent: Int,
    ): BitmapTemplateMatch? {
        if (screen.width <= 0 || screen.height <= 0) return null
        val expected = expectedRect.toAndroidRect()
        if (expected.width() <= 0 || expected.height() <= 0) return null

        val search = searchRect.toAndroidRect().apply { intersect(0, 0, screen.width, screen.height) }
        if (search.width() < expected.width() || search.height() < expected.height()) return null

        val detector = NativeDetector.newInstance()
            ?: return bestMatchKotlin(screen, templateBitmap, expectedRect, searchRect, thresholdPercent)
        return try {
            detector.init()
            detector.setScreenBitmap(screen, "throwlet-template")
            val result = detector.detectCondition(
                conditionBitmap = templateBitmap,
                conditionWidth = expected.width(),
                conditionHeight = expected.height(),
                detectionArea = search,
                threshold = (100 - thresholdPercent).coerceIn(0, 100),
            )
            if (!result.isDetected) return null

            val scorePercent = (result.confidenceRate * 100.0).roundToInt().coerceIn(0, 100)
            val left = result.position.x - expected.width() / 2
            val top = result.position.y - expected.height() / 2
            BitmapTemplateMatch(
                scorePercent = scorePercent,
                left = left,
                top = top,
                right = left + expected.width(),
                bottom = top + expected.height(),
            )
        } finally {
            detector.releaseScreenBitmap(screen)
            detector.close()
        }
    }

    private fun bestMatchKotlin(
        screen: Bitmap,
        templateBitmap: Bitmap,
        expectedRect: RectI,
        searchRect: RectI,
        thresholdPercent: Int,
    ): BitmapTemplateMatch? {
        if (screen.width <= 0 || screen.height <= 0) return null
        val expected = expectedRect.toAndroidRect()
        if (expected.width() <= 0 || expected.height() <= 0) return null

        val template = if (templateBitmap.width == expected.width() && templateBitmap.height == expected.height()) {
            templateBitmap
        } else {
            Bitmap.createScaledBitmap(templateBitmap, expected.width(), expected.height(), true)
        }

        val search = searchRect.toAndroidRect().apply { intersect(0, 0, screen.width, screen.height) }
        if (search.width() < template.width || search.height() < template.height) {
            if (template !== templateBitmap) template.recycle()
            return null
        }

        var bestPercent = 0
        var bestX = search.left
        var bestY = search.top
        val templatePixels = IntArray(template.width * template.height)
        template.getPixels(templatePixels, 0, template.width, 0, 0, template.width, template.height)

        val maxY = search.bottom - template.height
        val maxX = search.right - template.width
        for (y in search.top..maxY) {
            for (x in search.left..maxX) {
                val score = similarityAt(screen, templatePixels, template.width, template.height, x, y)
                if (score > bestPercent) {
                    bestPercent = score
                    bestX = x
                    bestY = y
                }
            }
        }

        if (template !== templateBitmap) template.recycle()
        return bestPercent
            .takeIf { it >= thresholdPercent }
            ?.let { score ->
                BitmapTemplateMatch(
                    scorePercent = score,
                    left = bestX,
                    top = bestY,
                    right = bestX + template.width,
                    bottom = bestY + template.height,
                )
            }
    }

    private fun similarityAt(
        screen: Bitmap,
        templatePixels: IntArray,
        templateWidth: Int,
        templateHeight: Int,
        originX: Int,
        originY: Int,
    ): Int {
        var totalDiff = 0L
        var samples = 0
        val step = 2
        var ty = 0
        while (ty < templateHeight) {
            var tx = 0
            while (tx < templateWidth) {
                val templateColor = templatePixels[ty * templateWidth + tx]
                val screenColor = screen.getPixel(originX + tx, originY + ty)
                totalDiff += colorDistance(templateColor, screenColor)
                samples += 1
                tx += step
            }
            ty += step
        }
        if (samples == 0) return 0
        val avgDiff = totalDiff / samples
        return (100 - (avgDiff * 100 / 255)).toInt().coerceIn(0, 100)
    }

    private fun colorDistance(a: Int, b: Int): Int {
        val ar = (a shr 16) and 0xFF
        val ag = (a shr 8) and 0xFF
        val ab = a and 0xFF
        val br = (b shr 16) and 0xFF
        val bg = (b shr 8) and 0xFF
        val bb = b and 0xFF
        return (abs(ar - br) + abs(ag - bg) + abs(ab - bb)) / 3
    }

    private fun RectI.toAndroidRect(): Rect = Rect(left, top, right, bottom)
}
