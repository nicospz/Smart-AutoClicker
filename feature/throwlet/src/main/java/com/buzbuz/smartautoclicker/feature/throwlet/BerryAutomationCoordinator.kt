package com.buzbuz.smartautoclicker.feature.throwlet

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.ArrayDeque

class BerryAutomationCoordinator(
    private val context: Context,
    private val scope: CoroutineScope,
    private val helperClient: HelperClient,
    private val berryForLane: (HelperLane) -> BerryAction,
    private val onFlowStatus: (HelperLane, String) -> Unit = { _, _ -> },
) {
    private val pendingLanes = ArrayDeque<HelperLane>()
    private var activeCommand: Job? = null

    fun onCatchSessionStopped(lane: HelperLane) {
        pendingLanes.removeAll { it == lane }
    }

    fun throwBerryNow(lane: HelperLane) {
        val berry = berryForLane(lane)
        if (berry.helperKind == null) {
            publish(lane, "no berry selected")
            return
        }
        if (lane !in pendingLanes) pendingLanes.addLast(lane)
        publish(lane, "queued ${berry.helperKind}")
        drainQueue()
    }

    private fun drainQueue() {
        if (activeCommand?.isActive == true) return
        while (pendingLanes.isNotEmpty()) {
            val lane = pendingLanes.removeFirst()
            val berry = berryForLane(lane)
            if (berry.helperKind == null) continue
            activeCommand = scope.launch {
                try {
                    val ok = runBerryTapSequence(lane, berry)
                    publish(lane, if (ok) "berry sent ${berry.helperKind}" else "berry failed")
                } finally {
                    activeCommand = null
                    drainQueue()
                }
            }
            return
        }
    }

    private suspend fun runBerryTapSequence(lane: HelperLane, berry: BerryAction): Boolean {
        val metrics = context.resources.displayMetrics
        val screenWidth = metrics.widthPixels
        val screenHeight = metrics.heightPixels

        val menuTap = BerryTapCoordinates.menuTap(screenWidth, screenHeight, lane)
        if (!tap(lane, menuTap.first, menuTap.second)) return false

        val berryTap = BerryTapCoordinates.berryTap(screenWidth, screenHeight, lane, berry)
            ?: run {
                publish(lane, "unknown berry ${berry.name}")
                return false
            }
        delay(BERRY_STEP_DELAY_MS)
        if (!tap(lane, berryTap.first, berryTap.second)) return false

        val confirmTap = BerryTapCoordinates.confirmTap(screenWidth, screenHeight, lane)
        delay(BERRY_STEP_DELAY_MS)
        if (!tap(lane, confirmTap.first, confirmTap.second)) return false
        return true
    }

    private suspend fun tap(lane: HelperLane, x: Int, y: Int): Boolean {
        publish(lane, "tap $x,$y")
        val reply = GestureHelperSession.runExclusive {
            helperClient.send("TAP $x $y", timeoutMs = 5_000)
        }
        ThrowletLog.i("berry tap lane=$lane x=$x y=$y ok=${reply.ok} reply=${reply.displayText.take(120)}")
        return reply.ok
    }

    private fun publish(lane: HelperLane, message: String) {
        ThrowletLog.i("berry flow lane=$lane $message")
        onFlowStatus(lane, "berry flow: $message")
    }
}

private const val BERRY_STEP_DELAY_MS = 500L
