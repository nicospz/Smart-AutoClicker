package com.buzbuz.smartautoclicker.feature.throwlet

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.buzbuz.smartautoclicker.feature.throwlet.data.GestureStore
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.runBlocking
import kotlin.math.roundToInt
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

class ScreenshotDetectionController(
    private val context: Context,
    private val gestureStore: GestureStore,
    private val calibrationStore: SplitCalibrationStore,
    private val screenshotSource: ThrowletScreenshotSource,
    private val buddyCropMatcher: BuddyCropMatcher,
) : DetectionController {
    override var runCount: Int = 0

    override fun detectOnce(mode: HelperMode, lane: HelperLane, pokemonNameOverride: String?): CatchDetectionState {
        val startedAtNs = System.nanoTime()
        runCount += 1
        ThrowletLog.i("detectOnce start mode=$mode lane=$lane override=${pokemonNameOverride ?: "<none>"} runCount=$runCount")
        if (!pokemonNameOverride.isNullOrBlank()) {
            val state = supplied(pokemonNameOverride)
            ThrowletLog.i(
                "detectOnce supplied lane=$lane pokemon=${state.pokemonName} key=${state.pokemonKey} totalMs=${elapsedMs(startedAtNs)}",
            )
            return state
        }

        val screenshotStartedAtNs = System.nanoTime()
        val (screenshot, screenshotSource) = captureScreenshotForDetection()
        if (screenshot == null) {
            ThrowletLog.w("detectOnce screenshot unavailable lane=$lane sacAndAccessibilityFailed=true")
            return CatchDetectionState(
                null,
                null,
                null,
                false,
                "Start a smart scenario with screen capture in Smart Auto Clicker, or enable Throwlet Accessibility for one-shot detection.",
            )
        }
        val screenshotMs = elapsedMs(screenshotStartedAtNs)
        ThrowletLog.i(
            "detectOnce screenshot ok lane=$lane source=$screenshotSource size=${screenshot.width}x${screenshot.height} screenshotMs=$screenshotMs",
        )

        if (mode == HelperMode.BUDDY) {
            val dividerPx = screenshotDividerPx()
            val state = buddyCropMatcher.match(screenshot, lane, dividerPx)
            screenshot.recycle()
            ThrowletLog.i(
                "detectOnce buddy lane=$lane pokemon=${state.pokemonName ?: "<none>"} confidence=${state.confidencePercent ?: -1} totalMs=${elapsedMs(startedAtNs)}",
            )
            return state
        }

        val screenshotSize = SizeI(screenshot.width, screenshot.height)
        val displayDivider = screenshotDividerPx()
        val bitmapDivider = LaneGeometry.dividerForBitmap(
            size = screenshotSize,
            profile = TouchCoordinateSpace.profile(context),
            displayDividerPx = displayDivider,
        )
        val cropRect = LaneGeometry.cropFor(lane, screenshotSize, bitmapDivider)
        val cropStartedAtNs = System.nanoTime()
        ThrowletLog.i("detectOnce crop lane=$lane displayDivider=$displayDivider bitmapDivider=$bitmapDivider rect=${cropRect.left},${cropRect.top},${cropRect.right},${cropRect.bottom} size=${cropRect.width}x${cropRect.height}")
        val crop = Bitmap.createBitmap(screenshot, cropRect.left, cropRect.top, cropRect.width, cropRect.height)
        val sourceLaneHeight = when (lane) {
            HelperLane.FULL -> TouchCoordinateSpace.profile(context).displayHeight
            HelperLane.SPLIT_TOP -> displayDivider
            HelperLane.SPLIT_BOTTOM -> TouchCoordinateSpace.profile(context).displayHeight - displayDivider
        }
        val ocrInput = crop.createCpAnchoredOcrBitmap(lane, sourceLaneHeight)
        val ocrBitmap = ocrInput?.bitmap ?: crop.scaledForOcr(OCR_SCALE)
        val cropMs = elapsedMs(cropStartedAtNs)
        if (ocrInput != null) {
            ThrowletLog.i(
                "detectOnce ocrBitmap lane=$lane source=cp-anchor anchorScore=${ocrInput.anchorScore} " +
                    "anchor=${ocrInput.anchorRect.toLog()} rect=${ocrInput.sourceRect.toLog()} " +
                    "frameScale=${ocrInput.frameScale.toLog()} ocr=${ocrBitmap.width}x${ocrBitmap.height} cropMs=$cropMs",
            )
        } else {
            ThrowletLog.i(
                "detectOnce ocrBitmap lane=$lane source=whole-lane original=${crop.width}x${crop.height} scale=$OCR_SCALE ocr=${ocrBitmap.width}x${ocrBitmap.height} cropMs=$cropMs",
            )
        }
        val ocrStartedAtNs = System.nanoTime()
        val text = recognizeTextBlocking(ocrBitmap)
        val ocrMs = elapsedMs(ocrStartedAtNs)
        if (ocrBitmap !== crop) ocrBitmap.recycle()
        crop.recycle()
        screenshot.recycle()
        val normalizedText = text.replace(Regex("\\s+"), " ").take(240)
        ThrowletLog.i("detectOnce ocr lane=$lane ocrMs=$ocrMs text=$normalizedText")
        val matchStartedAtNs = System.nanoTime()
        val match = PokemonCatalog.get(context).bestMatch(text)
        val matchMs = elapsedMs(matchStartedAtNs)
        ThrowletLog.i("detectOnce ocr lane=$lane text=${text.replace(Regex("\\s+"), " ").take(240)}")
        ThrowletLog.i(
            "detectOnce match lane=$lane match=${match?.name ?: "<none>"} confidence=${match?.confidence ?: -1} matchMs=$matchMs totalMs=${elapsedMs(startedAtNs)}",
        )

        return if (match != null) {
            CatchDetectionState(match.key, match.name, match.confidence, false, "Detected ${match.name} from ${lane.name}")
        } else {
            CatchDetectionState(null, null, null, false, "No Pokémon name matched in ${lane.name}; OCR saw: ${text.take(80)}")
        }
    }

    private fun Bitmap.scaledForOcr(scale: Float): Bitmap {
        if (scale >= 0.99f) return this
        val w = (width * scale).toInt().coerceAtLeast(1)
        val h = (height * scale).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(this, w, h, true)
    }

    private fun Bitmap.createCpAnchoredOcrBitmap(lane: HelperLane, sourceLaneHeight: Int): OcrInput? {
        val anchor = loadCpAnchor(lane) ?: return null
        try {
            val frameScale = FrameScale.forCurrentFrame(context, width, height, sourceLaneHeight)
            val templateWidth = (anchor.width * frameScale.scaleX).roundToInt().coerceAtLeast(1)
            val templateHeight = (anchor.height * frameScale.scaleY).roundToInt().coerceAtLeast(1)
            val searchBottom = (height * CP_ANCHOR_SEARCH_LANE_HEIGHT_RATIO).roundToInt()
                .coerceIn(templateHeight, height)
            val match = BitmapTemplateMatcher.bestMatch(
                screen = this,
                templateBitmap = anchor,
                expectedRect = RectI(0, 0, templateWidth, templateHeight),
                searchRect = RectI(0, 0, width, searchBottom),
                thresholdPercent = CP_ANCHOR_THRESHOLD_PERCENT,
            ) ?: return null

            val leftPadding = (width * CP_OCR_NAME_LEFT_PADDING_RATIO).roundToInt()
            val rightPadding = (templateWidth * CP_OCR_CP_RIGHT_PADDING_FACTOR).roundToInt()
                .coerceAtLeast(CP_OCR_MIN_HORIZONTAL_PADDING_PX)
            val left = (match.left - leftPadding).coerceAtLeast(0)
            val right = (match.right + rightPadding).coerceAtMost(width)
            val verticalPadding = (templateHeight * CP_OCR_VERTICAL_PADDING_FACTOR).roundToInt()
                .coerceAtLeast(CP_OCR_MIN_VERTICAL_PADDING_PX)
            val top = (match.top - verticalPadding).coerceAtLeast(0)
            val bottom = (match.bottom + verticalPadding).coerceAtMost(height)
            if (right - left < 2 || bottom - top < 2) return null

            val sourceRect = RectI(left, top, right, bottom)
            val lineCrop = Bitmap.createBitmap(this, sourceRect.left, sourceRect.top, sourceRect.width, sourceRect.height)
            val ocrBitmap = lineCrop.scaledForOcr(CP_OCR_SCALE)
            if (ocrBitmap !== lineCrop) lineCrop.recycle()
            return OcrInput(
                bitmap = ocrBitmap,
                anchorScore = match.scorePercent,
                anchorRect = RectI(match.left, match.top, match.right, match.bottom),
                sourceRect = sourceRect,
                frameScale = frameScale,
            )
        } finally {
            anchor.recycle()
        }
    }

    private fun loadCpAnchor(lane: HelperLane): Bitmap? {
        val path = when (lane) {
            HelperLane.FULL -> CP_ANCHOR_FULL_ASSET
            HelperLane.SPLIT_TOP, HelperLane.SPLIT_BOTTOM -> CP_ANCHOR_SPLIT_ASSET
        }
        return runCatching {
            context.assets.open(path).use(BitmapFactory::decodeStream)
        }.getOrNull()
    }

    private fun recognizeTextBlocking(bitmap: Bitmap): String {
        val executor = Executors.newSingleThreadExecutor()
        val latch = CountDownLatch(1)
        val textRef = AtomicReference("")
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        ThrowletLog.d("ocr start bitmap=${bitmap.width}x${bitmap.height}")
        recognizer.process(InputImage.fromBitmap(bitmap, 0))
            .addOnSuccessListener(executor) { result ->
                textRef.set(result.text)
                ThrowletLog.d("ocr success chars=${result.text.length}")
                latch.countDown()
            }
            .addOnFailureListener(executor) { error ->
                ThrowletLog.e("ocr failure", error)
                latch.countDown()
            }
        val completed = latch.await(5, TimeUnit.SECONDS)
        if (!completed) ThrowletLog.w("ocr timeout")
        executor.shutdown()
        return textRef.get()
    }

    private fun screenshotDividerPx(): Int {
        val calibration = runBlocking { calibrationStore.load() }
        return calibration.topToBottomScreenshotDy
            ?: TouchCoordinateSpace.profile(context).defaultScreenshotDividerPx()
    }

    private fun captureScreenshotForDetection(): Pair<Bitmap?, String> {
        val screenshot = screenshotSource.captureBlocking()
        if (screenshot != null) return screenshot to "sac"
        return null to "none"
    }

    companion object {
        private const val OCR_SCALE = 0.30f
        private const val CP_OCR_SCALE = 1.6f
        private const val CP_ANCHOR_SEARCH_LANE_HEIGHT_RATIO = 0.70f
        private const val CP_ANCHOR_THRESHOLD_PERCENT = 72
        private const val CP_OCR_NAME_LEFT_PADDING_RATIO = 0.62f
        private const val CP_OCR_CP_RIGHT_PADDING_FACTOR = 1.25f
        private const val CP_OCR_MIN_HORIZONTAL_PADDING_PX = 10
        private const val CP_OCR_VERTICAL_PADDING_FACTOR = 2.0f
        private const val CP_OCR_MIN_VERTICAL_PADDING_PX = 14
        private const val CP_ANCHOR_FULL_ASSET = "needles/full/catch_cp_anchor.jpg"
        private const val CP_ANCHOR_SPLIT_ASSET = "needles/split/catch_cp_anchor.jpg"
    }

    private fun elapsedMs(startedAtNs: Long): Long =
        (System.nanoTime() - startedAtNs) / 1_000_000

    private fun supplied(name: String): CatchDetectionState {
        val clean = name.trim()
        val key = clean.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-')
        return CatchDetectionState(key, clean, 100, false, "Using Pokémon supplied by intent")
    }
}

private data class OcrInput(
    val bitmap: Bitmap,
    val anchorScore: Int,
    val anchorRect: RectI,
    val sourceRect: RectI,
    val frameScale: FrameScale,
)

private data class FrameScale(
    val scaleX: Float,
    val scaleY: Float,
    val sourceWidth: Int,
    val sourceHeight: Int,
) {
    fun toLog(): String = "${sourceWidth}x$sourceHeight->${scaleX.formatScale()}x${scaleY.formatScale()}"

    companion object {
        fun forCurrentFrame(context: Context, frameWidth: Int, frameHeight: Int, sourceLaneHeight: Int): FrameScale {
            val profile = TouchCoordinateSpace.profile(context)
            val sourceHeight = sourceLaneHeight.coerceAtLeast(1)

            return FrameScale(
                scaleX = frameWidth.toFloat() / profile.displayWidth.toFloat().coerceAtLeast(1f),
                scaleY = frameHeight.toFloat() / sourceHeight.toFloat(),
                sourceWidth = profile.displayWidth,
                sourceHeight = sourceHeight,
            )
        }
    }
}

private fun RectI.toLog(): String = "$left,$top,$right,$bottom"

private fun Float.formatScale(): String = "%.4f".format(this)
