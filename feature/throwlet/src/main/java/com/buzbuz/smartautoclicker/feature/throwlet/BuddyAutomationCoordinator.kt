package com.buzbuz.smartautoclicker.feature.throwlet

import android.content.Context
import com.buzbuz.smartautoclicker.feature.throwlet.needle.BuddyNeedleFeature
import com.buzbuz.smartautoclicker.feature.throwlet.needle.BuddyNeedleMatch
import com.buzbuz.smartautoclicker.feature.throwlet.needle.BuddyNeedleMatcher
import com.buzbuz.smartautoclicker.feature.throwlet.needle.BundledBuddyNeedles
import com.buzbuz.smartautoclicker.feature.throwlet.needle.LoadedBuddyNeedle
import com.buzbuz.smartautoclicker.feature.throwlet.needle.NeedleLane
import com.buzbuz.smartautoclicker.feature.throwlet.needle.toNeedleLane
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class BuddyAutomationCoordinator(
    private val context: Context,
    private val scope: CoroutineScope,
    private val helperClient: HelperClient,
    private val frameSource: ThrowletFrameSource,
    private val laneDividerProvider: suspend () -> Int,
    private val splitModeProvider: () -> Boolean,
    private val activeBuddyLanes: () -> Set<HelperLane>,
    private val pokemonNameForLane: (HelperLane) -> String?,
    private val hasGestureForLane: suspend (HelperLane) -> Boolean,
    private val playGestureForLane: suspend (HelperLane) -> Boolean,
    private val onFlowStatus: (HelperLane, String) -> Unit = { _, _ -> },
) {
    private val flowJobs = mutableMapOf<HelperLane, Job>()
    private var loadedNeedles: List<LoadedBuddyNeedle> = emptyList()
    private var loadedSplitMode: Boolean? = null
    private var loadedDivider: Int = -1

    fun onBuddySessionStarted(lane: HelperLane) {
        flowJobs[lane]?.cancel()
        flowJobs[lane] = scope.launch(Dispatchers.Default) { runAutoFlow(lane) }
    }

    fun onBuddySessionStopped(lane: HelperLane) {
        flowJobs.remove(lane)?.cancel()
        if (flowJobs.isEmpty()) unloadNeedles()
    }

    private suspend fun runAutoFlow(lane: HelperLane) {
        publish(lane, "recognizing", "waiting for buddy")
        delay(RECOGNITION_SETTLE_MS)
        val pokemonName = waitForRecognizedBuddy(lane) ?: return
        publish(lane, "checking gesture", pokemonName)
        if (!hasGestureForLane(lane)) {
            publish(lane, "skipped", "no gesture for $pokemonName", active = false)
            return
        }

        val needleLane = lane.toNeedleLane()
        val camera = waitForNeedle(lane, needleLane, BuddyNeedleFeature.CAMERA_BUTTON) ?: return
        if (!tap(lane, camera.tapX, camera.tapY)) return
        delay(AFTER_CAMERA_TAP_MS)

        publish(lane, "playing", pokemonName)
        if (!GestureHelperSession.runExclusive { playGestureForLane(lane) }) {
            publish(lane, "failed", "gesture play failed", active = false)
            return
        }

        publish(lane, "waiting for berries")
        val berryMenu = waitForNeedle(lane, needleLane, BuddyNeedleFeature.BERRY_MENU) ?: return
        if (!tap(lane, berryMenu.tapX, berryMenu.tapY)) return
        delay(AFTER_BERRY_MENU_TAP_MS)

        var feedCount = 1
        while (scope.isActive && activeBuddyLanes().contains(lane)) {
            publish(lane, "waiting for nanab", attempt = feedCount)
            val nanab = waitForNeedle(lane, needleLane, BuddyNeedleFeature.NANAB_BERRY, timeoutMs = NANAB_WAIT_TIMEOUT_MS)
                ?: break
            if (!tap(lane, nanab.tapX, nanab.tapY)) return
            publish(lane, "feeding nanab", attempt = feedCount)
            delay(AFTER_BERRY_SELECT_MS)
            if (!swipeUpFrom(lane, nanab.tapX, nanab.tapY)) return
            feedCount += 1
        }

        if (activeBuddyLanes().contains(lane)) {
            publish(lane, "done", active = false)
        } else {
            publish(lane, "stopped", "buddy session ended", active = false)
        }
    }

    private suspend fun waitForRecognizedBuddy(lane: HelperLane): String? {
        while (scope.isActive && activeBuddyLanes().contains(lane)) {
            val name = pokemonNameForLane(lane)
            if (!name.isNullOrBlank()) return name
            delay(POLL_MS)
        }
        publish(lane, "stopped", "buddy session ended", active = false)
        return null
    }

    private suspend fun waitForNeedle(
        lane: HelperLane,
        needleLane: NeedleLane,
        feature: BuddyNeedleFeature,
        timeoutMs: Long = NEEDLE_WAIT_TIMEOUT_MS,
    ): BuddyNeedleMatch? {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (scope.isActive && activeBuddyLanes().contains(lane)) {
            if (!frameSource.isRecording()) {
                delay(POLL_MS)
                continue
            }
            val frame = withContext(Dispatchers.IO) {
                frameSource.screenshotBlocking(maxCacheAgeMs = 0)
            }
            if (frame != null) {
                try {
                    ensureNeedles(frame.width, frame.height)
                    val match = BuddyNeedleMatcher.bestMatch(frame, loadedNeedles, feature, needleLane)
                    if (match != null) return match
                } finally {
                    frame.recycle()
                }
            }
            if (System.currentTimeMillis() >= deadline) {
                publish(lane, "stopped", "${feature.manifestValue} not found", active = false)
                return null
            }
            delay(POLL_MS)
        }
        publish(lane, "stopped", "buddy session ended", active = false)
        return null
    }

    private suspend fun ensureNeedles(frameWidth: Int, frameHeight: Int) {
        val splitMode = splitModeProvider()
        val divider = laneDividerProvider()
        if (loadedNeedles.isNotEmpty() && loadedSplitMode == splitMode && loadedDivider == divider) return
        unloadNeedles()
        loadedNeedles = BundledBuddyNeedles.load(context, splitMode, divider)
        loadedSplitMode = splitMode
        loadedDivider = divider
        ThrowletLog.i(
            "buddy flow needles loaded split=$splitMode divider=$divider count=${loadedNeedles.size} frame=${frameWidth}x$frameHeight",
        )
    }

    private fun unloadNeedles() {
        loadedNeedles.forEach { it.template.recycle() }
        loadedNeedles = emptyList()
        loadedSplitMode = null
        loadedDivider = -1
    }

    private suspend fun tap(lane: HelperLane, x: Int, y: Int): Boolean {
        val reply = GestureHelperSession.runExclusive {
            helperClient.send("TAP $x $y", timeoutMs = 5_000)
        }
        ThrowletLog.i("buddy flow tap lane=$lane x=$x y=$y ok=${reply.ok} reply=${reply.displayText.take(120)}")
        if (!reply.ok) publish(lane, "failed", reply.displayText.lines().firstOrNull().orEmpty(), active = false)
        return reply.ok
    }

    private suspend fun swipeUpFrom(lane: HelperLane, x: Int, y: Int): Boolean {
        val frameHeight = withContext(Dispatchers.IO) {
            frameSource.screenshotBlocking()?.height
        } ?: context.resources.displayMetrics.heightPixels
        val endY = (y - (frameHeight * SWIPE_UP_DISTANCE_RATIO).toInt()).coerceAtLeast(1)
        val reply = GestureHelperSession.runExclusive {
            helperClient.send("DODGE_SWIPE $x $y $x $endY $SWIPE_DURATION_MS", timeoutMs = 5_000)
        }
        ThrowletLog.i("buddy flow swipe lane=$lane from=$x,$y to=$x,$endY ok=${reply.ok}")
        if (!reply.ok) publish(lane, "failed", reply.displayText.lines().firstOrNull().orEmpty(), active = false)
        return reply.ok
    }

    private fun publish(
        lane: HelperLane,
        step: String,
        detail: String = "",
        active: Boolean = true,
        attempt: Int = 0,
    ) {
        val suffix = detail.takeIf { it.isNotBlank() }?.let { " · $it" }.orEmpty()
        val attemptSuffix = if (attempt > 0) " (#$attempt)" else ""
        val message = "buddy flow: $step$suffix$attemptSuffix"
        ThrowletLog.i("buddy flow lane=$lane active=$active $message")
        onFlowStatus(lane, message)
    }

    companion object {
        private const val RECOGNITION_SETTLE_MS = 500L
        private const val AFTER_CAMERA_TAP_MS = 500L
        private const val AFTER_BERRY_MENU_TAP_MS = 300L
        private const val AFTER_BERRY_SELECT_MS = 400L
        private const val POLL_MS = 250L
        private const val NEEDLE_WAIT_TIMEOUT_MS = 30_000L
        private const val NANAB_WAIT_TIMEOUT_MS = 15_000L
        private const val SWIPE_DURATION_MS = 120
        private const val SWIPE_UP_DISTANCE_RATIO = 0.22f
    }
}
