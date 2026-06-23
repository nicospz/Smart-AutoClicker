package com.buzbuz.smartautoclicker.feature.throwlet

interface RailController {
    fun show()
    fun hide()
    fun stop()
}

interface DetectionController {
    var runCount: Int
    fun detectOnce(mode: HelperMode, lane: HelperLane, pokemonNameOverride: String? = null): CatchDetectionState
}

interface GestureController {
    fun saveLatest(session: HelperSession, latest: GesturePayload, display: SizeI, calibration: SplitCalibration?): SaveGestureResult
    fun gestureForReplay(session: HelperSession, stored: GesturePayload, calibration: SplitCalibration?): GesturePayload
}

data class HelperSession(
    val key: HelperSessionKey,
    val mode: HelperMode,
    val lane: HelperLane,
    val railController: RailController,
    val detectionController: DetectionController,
    val gestureController: GestureController,
    var manualSelectionOnly: Boolean = false,
    var detectionState: CatchDetectionState? = null,
    var selectedBerry: BerryAction = BerryAction.NONE,
    var fastCatchEnabled: Boolean = false,
)

sealed class SaveGestureResult {
    data class Saved(val mode: GestureMode, val payload: GesturePayload) : SaveGestureResult()
    data class Rejected(val message: String) : SaveGestureResult()
}

class DefaultGestureController : GestureController {
    override fun saveLatest(session: HelperSession, latest: GesturePayload, display: SizeI, calibration: SplitCalibration?): SaveGestureResult {
        if (session.lane == HelperLane.FULL) {
            return SaveGestureResult.Saved(GestureModes.storageMode(session.mode, session.lane), latest)
        }
        if (session.mode == HelperMode.CATCH) {
            val touchSplit = calibration?.topToBottomTouchDy ?: (display.height / 2)
            val dominant = latest.dominantLane(touchSplit)
            if (dominant != session.lane) {
                return SaveGestureResult.Rejected("latest swipe was not in this lane")
            }
        }
        return SaveGestureResult.Saved(GestureModes.storageMode(session.mode, session.lane), latest)
    }

    override fun gestureForReplay(
        session: HelperSession,
        stored: GesturePayload,
        calibration: SplitCalibration?,
    ): GesturePayload = when (session.lane) {
        HelperLane.SPLIT_TOP -> stored
        HelperLane.SPLIT_BOTTOM -> stored.translated(calibration?.topToBottomTouchDy ?: 0)
        HelperLane.FULL -> stored
    }
}

class NoopRailController : RailController {
    var visible = false
    var stopped = false
    override fun show() { visible = true }
    override fun hide() { visible = false }
    override fun stop() { stopped = true; visible = false }
}

class NoopDetectionController : DetectionController {
    override var runCount: Int = 0
    var lastMode: HelperMode? = null
    override fun detectOnce(mode: HelperMode, lane: HelperLane, pokemonNameOverride: String?): CatchDetectionState {
        runCount += 1
        lastMode = mode
        val name = pokemonNameOverride
        return CatchDetectionState(
            pokemonKey = name?.lowercase()?.replace(' ', '-'),
            pokemonName = name,
            confidencePercent = if (name == null) null else 100,
            hasGesture = false,
            message = if (name == null) "No Pokémon detected for ${lane.name}" else "Using $name from intent",
        )
    }
}
