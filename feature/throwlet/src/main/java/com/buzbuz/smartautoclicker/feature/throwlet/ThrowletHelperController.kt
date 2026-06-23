/*
 * Copyright (C) 2026 Nicolas Espinoza
 */
package com.buzbuz.smartautoclicker.feature.throwlet

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import android.util.Log
import android.view.View
import android.widget.PopupWindow
import com.buzbuz.smartautoclicker.core.common.actions.AndroidActionExecutor
import com.buzbuz.smartautoclicker.core.common.actions.ThrowletCatchLane
import com.buzbuz.smartautoclicker.core.common.actions.ThrowletCatchMode
import com.buzbuz.smartautoclicker.core.common.actions.ThrowletCatchOperation
import com.buzbuz.smartautoclicker.core.common.actions.ThrowletCatchSession
import com.buzbuz.smartautoclicker.core.display.recorder.ThrowletCropPicker
import com.buzbuz.smartautoclicker.feature.throwlet.data.GestureStore
import com.buzbuz.smartautoclicker.feature.throwlet.data.ThrowletDatabase
import com.buzbuz.smartautoclicker.feature.throwlet.sync.SupabaseSyncRepository
import com.buzbuz.smartautoclicker.feature.throwlet.sync.SupabaseSyncResult
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

/**
 * In-process Throwlet helper orchestrator (replaces the external Throwlet app service).
 */
class ThrowletHelperController(
    private val context: Context,
    private val scope: CoroutineScope,
    private val database: ThrowletDatabase,
    private val gestureStore: GestureStore,
    private val syncRepository: SupabaseSyncRepository,
    private val actionExecutor: AndroidActionExecutor,
    private val screenshotSource: ThrowletScreenshotSource,
    private val cropPicker: ThrowletCropPicker,
    private val buddyCropSaver: ThrowletBuddyCropSaver,
    private val isScreenRecording: () -> Boolean,
    private val onThrowletSyncRequested: () -> Unit = {},
) : RailCallbacks {

    private val helperClient = HelperClient()
    private val frameSource = ThrowletFrameSource(screenshotSource, isScreenRecording)
    private val calibrationStore = SplitCalibrationStore(database, context)
    private val berryStore = BerryStore(context)
    private val fastCatchStore = FastCatchStore(context)
    private lateinit var berryAutomation: BerryAutomationCoordinator
    private lateinit var buddyAutomation: BuddyAutomationCoordinator
    private var berryMenuPopup: PopupWindow? = null
    private val pokemonPickerOverlay = ManualPokemonPickerOverlay(context)

    private val manager = HelperSessionManager { mode, lane ->
        val splitLayout = if (lane == HelperLane.FULL) {
            null
        } else {
            runBlocking { SplitScreenLayouts.fromCalibration(context, calibrationStore.load()) }
        }
        HelperSession(
            key = HelperSessionKey(lane),
            mode = mode,
            lane = lane,
            railController = AndroidRailController(context, mode, lane, this, splitLayout),
            detectionController = ScreenshotDetectionController(
                context = context,
                gestureStore = gestureStore,
                calibrationStore = calibrationStore,
                screenshotSource = screenshotSource,
                buddyCropMatcher = BuddyCropMatcher(database),
            ),
            gestureController = DefaultGestureController(),
        )
    }.also { sessionManager ->
        sessionManager.onSessionStopped = { lane, session ->
            if (session.mode == HelperMode.CATCH) {
                berryAutomation.onCatchSessionStopped(lane)
            } else if (session.mode == HelperMode.BUDDY) {
                buddyAutomation.onBuddySessionStopped(lane)
            }
        }
    }

    init {
        berryAutomation = BerryAutomationCoordinator(
            context = context,
            scope = scope,
            helperClient = helperClient,
            berryForLane = { lane -> manager.activeSessions[lane]?.selectedBerry ?: BerryAction.NONE },
            onFlowStatus = { _, message -> toast(message) },
        )
        buddyAutomation = BuddyAutomationCoordinator(
            context = context,
            scope = scope,
            helperClient = helperClient,
            frameSource = frameSource,
            laneDividerProvider = { laneDividerPx() },
            splitModeProvider = { manager.activeSessions.keys.any { it != HelperLane.FULL } },
            activeBuddyLanes = {
                manager.activeSessions.values
                    .filter { it.mode == HelperMode.BUDDY }
                    .map { it.lane }
                    .toSet()
            },
            pokemonNameForLane = { lane -> manager.activeSessions[lane]?.detectionState?.pokemonName },
            hasGestureForLane = { lane ->
                val session = manager.activeSessions[lane] ?: return@BuddyAutomationCoordinator false
                val pokemonKey = session.detectionState?.pokemonKey ?: return@BuddyAutomationCoordinator false
                val storageMode = GestureModes.storageMode(session.mode, lane)
                withContext(Dispatchers.IO) { gestureStore.find(pokemonKey, storageMode) != null }
            },
            playGestureForLane = { lane -> playBuddyGesture(lane) },
            onFlowStatus = { _, message -> toast(message) },
        )
    }

    suspend fun execute(operation: ThrowletCatchOperation, session: ThrowletCatchSession) {
        val mode = session.mode.toHelperMode()
        val lane = session.lane.toHelperLane()
        when (operation) {
            ThrowletCatchOperation.SHOW ->
                startSession(mode, lane, session.pokemonNameOverride, session.manualSelectionOnly)
            ThrowletCatchOperation.HIDE -> stopSession(lane)
            ThrowletCatchOperation.TOGGLE -> {
                if (manager.activeSessions.containsKey(lane)) stopSession(lane)
                else startSession(mode, lane, session.pokemonNameOverride, session.manualSelectionOnly)
            }
        }
    }

    fun hideAll() {
        berryMenuPopup?.dismiss()
        berryMenuPopup = null
        pokemonPickerOverlay.hide()
        manager.stopAll()
    }

    private fun startSession(
        mode: HelperMode,
        lane: HelperLane,
        pokemonNameOverride: String? = null,
        manualSelectionOnly: Boolean = false,
    ) {
        Log.i(TAG, "startSession mode=$mode lane=$lane override=${pokemonNameOverride ?: "<none>"}")
        scope.launch { onThrowletSyncRequested() }
        val session = manager.start(
            mode = mode,
            lane = lane,
            detectOnStart = !manualSelectionOnly,
            pokemonName = pokemonNameOverride,
        )
        session.manualSelectionOnly = manualSelectionOnly
        if (manualSelectionOnly) {
            session.detectionState = manualPokemonState(pokemonNameOverride)
        }
        scope.launch {
            session.detectionState?.let { session.detectionState = enrichDetectionState(session, it) }
            applyBerrySelection(session)
            applyFastCatchSelection(session)
            renderSessionRail(session, "session-start")
            if (mode == HelperMode.BUDDY) {
                buddyAutomation.onBuddySessionStarted(lane)
            }
            ensureLatestSwipeCapture("session-start-$mode-$lane")
        }
    }

    private fun stopSession(lane: HelperLane) {
        Log.i(TAG, "stopSession lane=$lane")
        manager.stop(lane)
    }

    override fun refresh(lane: HelperLane) {
        val session = manager.activeSessions[lane] ?: return
        if (session.manualSelectionOnly) {
            selectPokemon(lane)
            return
        }
        scope.launch {
            session.railController.hide()
            delay(300)
            val raw = withContext(Dispatchers.IO) {
                session.detectionController.detectOnce(session.mode, lane)
            }
            val state = enrichDetectionState(session, raw)
            session.detectionState = state
            applyBerrySelection(session)
            applyFastCatchSelection(session)
            session.railController.show()
            renderSessionRail(session, "refresh")
        }
    }

    override fun saveLatest(lane: HelperLane) {
        val session = manager.activeSessions[lane] ?: return
        scope.launch {
            if (session.manualSelectionOnly && session.detectionState?.pokemonKey == null) {
                toast("Choose a Pokémon first")
                selectPokemon(lane)
                return@launch
            }
            ensureLatestSwipeCapture("before-save-$lane")
            val export = GestureHelperSession.runExclusive {
                helperClient.send("EXPORT_LATEST_SWIPE").toExportedGesture()
            } ?: run {
                toast("No latest swipe exported")
                return@launch
            }
            val decoded = RawGestureCodec.decode(export.payloadHex).getOrElse { error ->
                Log.e(TAG, "record decode failed lane=$lane", error)
                toast("Gesture export unreadable")
                return@launch
            }
            val display = displaySize()
            val calibration = calibration()
            val laneOffsetTouch = calibration.topToBottomTouchDy
            if (session.mode == HelperMode.CATCH && lane != HelperLane.FULL) {
                val dominant = decoded.dominantLane(laneOffsetTouch)
                if (dominant != lane) {
                    toast("latest swipe was not in this lane")
                    return@launch
                }
            }
            val storageMode = GestureModes.storageMode(session.mode, lane)
            val normalized = when (lane) {
                HelperLane.SPLIT_TOP ->
                    SplitLaneTransforms.normalizeForStorage(decoded, HelperLane.SPLIT_TOP, laneOffsetTouch)
                else -> decoded
            }
            val state = session.detectionState
            val pokemonName = state?.pokemonName ?: "Unknown"
            val pokemonKey = state?.pokemonKey ?: "unknown"
            gestureStore.save(
                pokemonKey = pokemonKey,
                pokemonName = pokemonName,
                gestureMode = storageMode,
                payloadHex = normalized.encodeHex(),
                eventCount = normalized.events.size,
                durationMs = normalized.durationMs,
                helperMode = session.mode,
                sourceLane = lane,
                display = display,
                laneOffsetTouch = laneOffsetTouch,
            )
            val savedGesture = withContext(Dispatchers.IO) { gestureStore.find(pokemonKey, storageMode) }
            if (savedGesture != null) {
                logSupabaseSync("gesture push", withContext(Dispatchers.IO) { syncRepository.pushGesture(savedGesture) })
            }
            session.detectionState = session.detectionState?.let { enrichDetectionState(session, it) }
            session.detectionState?.let { renderSessionRail(session, "save") }
            toast(
                if (session.mode == HelperMode.CATCH) "Saved throw gesture"
                else "Saved buddy gesture for $pokemonName",
            )
        }
    }

    override fun play(lane: HelperLane) {
        val session = manager.activeSessions[lane] ?: return
        scope.launch {
            if (session.mode == HelperMode.BUDDY) {
                val ok = playBuddyGesture(lane)
                toast(if (ok) "Played once" else "Buddy play failed")
                return@launch
            }
            val state = session.detectionState
            val pokemonKey = state?.pokemonKey ?: "unknown"
            val storageMode = GestureModes.storageMode(session.mode, lane)
            val entity = gestureStore.find(pokemonKey, storageMode)
            if (entity == null) {
                toast("No saved gesture for ${state?.pokemonName ?: "this Pokémon"}")
                return@launch
            }
            val decoded = RawGestureCodec.decode(entity.payloadHex).getOrElse { error ->
                Log.e(TAG, "replay decode failed lane=$lane", error)
                toast("Saved gesture unreadable")
                return@launch
            }
            val calibration = calibration()
            val replay = SplitLaneTransforms.forReplayStored(
                payload = decoded,
                sourceLane = entity.sourceLane,
                targetLane = lane,
                laneOffsetTouch = calibration.topToBottomTouchDy,
            )
            val throwHex = replay.encodeHex()
            val playReply = if (session.fastCatchEnabled) {
                playCatchWithFastHold(throwHex, lane, calibration.topToBottomTouchDy)
            } else {
                GestureHelperSession.runExclusive {
                    val importReply = helperClient.send("IMPORT_GESTURE $throwHex")
                    if (!importReply.ok) return@runExclusive importReply
                    helperClient.send("PLAY_LAST")
                }
            }
            toast(
                when {
                    playReply.ok && session.fastCatchEnabled -> "Fast catch played"
                    playReply.ok -> "Played once"
                    else -> playReply.displayText
                },
            )
        }
    }

    override fun stop(lane: HelperLane) {
        stopSession(lane)
    }

    override fun crop(lane: HelperLane) {
        val session = manager.activeSessions[lane] ?: return
        if (session.mode != HelperMode.BUDDY) return
        scope.launch {
            session.railController.hide()
            delay(300)
            val calibration = withContext(Dispatchers.IO) { calibrationStore.load() }
            val dividerPx = calibration.topToBottomScreenshotDy
                ?: TouchCoordinateSpace.profile(context).defaultScreenshotDividerPx()
            val frame = screenshotSource.captureBlocking()
            if (frame == null) {
                session.railController.show()
                toast("Start a smart scenario with screen capture first")
                return@launch
            }
            val frameSize = SizeI(frame.width, frame.height)
            val defaultRect = BuddyCropStorage.defaultCropRect(lane, frameSize, dividerPx)
            val area = Rect(defaultRect.left, defaultRect.top, defaultRect.right, defaultRect.bottom)
            val pickResult = cropPicker.pickCrop(frame, area)
            frame.recycle()
            if (pickResult == null) {
                session.railController.show()
                return@launch
            }
            val cacheFile = File(context.cacheDir, "buddy-crop").also { it.mkdirs() }
                .resolve("sac-crop-${System.currentTimeMillis()}.png")
            cacheFile.writeBytes(pickResult.cropPng)
            val sacCrop = SacCropResult(
                frameWidth = pickResult.frameWidth,
                frameHeight = pickResult.frameHeight,
                cropLeft = pickResult.cropLeft,
                cropTop = pickResult.cropTop,
                cropRight = pickResult.cropRight,
                cropBottom = pickResult.cropBottom,
                cropBitmapPath = cacheFile.absolutePath,
            )
            buddyCropSaver.showSaveUi(
                lane = lane,
                sacCrop = sacCrop,
                dividerPx = dividerPx,
                pokemonName = session.detectionState?.pokemonName,
                onDismiss = { session.railController.show() },
            )
        }
    }

    override fun cycleThrowScore(lane: HelperLane) {
        val session = manager.activeSessions[lane] ?: return
        if (session.mode != HelperMode.CATCH) {
            refresh(lane)
            return
        }
        val state = session.detectionState
        if (state?.pokemonName == null) {
            if (session.manualSelectionOnly) selectPokemon(lane) else refresh(lane)
            return
        }
        if (!state.hasGesture) return
        val pokemonKey = state.pokemonKey ?: return
        val storageMode = GestureModes.storageMode(session.mode, lane)
        val previous = state
        val nextScore = state.throwScore.next()
        session.detectionState = state.copy(throwScore = nextScore)
        renderSessionRail(session, "cycle-throw-score")
        scope.launch {
            val saved = withContext(Dispatchers.IO) {
                gestureStore.setThrowScore(pokemonKey, storageMode, nextScore)
            }
            if (!saved) {
                session.detectionState = previous
                renderSessionRail(session, "cycle-throw-score-revert")
                toast("Could not save throw score")
            } else {
                withContext(Dispatchers.IO) { gestureStore.find(pokemonKey, storageMode) }?.let { gesture ->
                    logSupabaseSync("throw score push", withContext(Dispatchers.IO) { syncRepository.pushGesture(gesture) })
                }
            }
        }
    }

    override fun openBerryMenu(lane: HelperLane, anchor: View) {
        val session = manager.activeSessions[lane] ?: return
        if (session.mode != HelperMode.CATCH) return
        berryMenuPopup?.dismiss()
        berryMenuPopup = BerryMenuUi.show(
            context = context,
            anchor = anchor,
            selected = session.selectedBerry,
        ) { berry ->
            berryMenuPopup?.dismiss()
            berryMenuPopup = null
            session.selectedBerry = berry
            berryStore.save(session.detectionState?.pokemonName, berry)
            renderBerryIcon(lane)
            toast(if (berry == BerryAction.NONE) "Berry cleared" else "Selected ${berry.label}")
        }
    }

    override fun throwBerry(lane: HelperLane) {
        val session = manager.activeSessions[lane] ?: return
        if (session.mode != HelperMode.CATCH) return
        if (session.selectedBerry == BerryAction.NONE) {
            toast("Choose a berry first")
            return
        }
        berryAutomation.throwBerryNow(lane)
    }

    override fun toggleFastCatch(lane: HelperLane) {
        val session = manager.activeSessions[lane] ?: return
        if (session.mode != HelperMode.CATCH) return
        session.fastCatchEnabled = fastCatchStore.toggle(lane)
        renderFastCatchIcon(lane)
        toast(if (session.fastCatchEnabled) "Fast catch on" else "Fast catch off")
    }

    override fun selectPokemon(lane: HelperLane) {
        val session = manager.activeSessions[lane] ?: return
        if (session.mode != HelperMode.CATCH) {
            refresh(lane)
            return
        }
        scope.launch {
            session.railController.hide()
            pokemonPickerOverlay.show(
                initialPokemonName = session.detectionState?.pokemonName,
                onPokemonSelected = { pokemonName ->
                    scope.launch {
                        session.detectionState = enrichDetectionState(session, manualPokemonState(pokemonName))
                        applyBerrySelection(session)
                        applyFastCatchSelection(session)
                        session.railController.show()
                        renderSessionRail(session, "manual-pokemon-selected")
                    }
                },
                onDismiss = {
                    session.railController.show()
                    renderSessionRail(session, "manual-pokemon-dismiss")
                },
            )
        }
    }

    private suspend fun playCatchWithFastHold(
        throwHex: String,
        lane: HelperLane,
        laneOffsetTouch: Int,
    ): HelperReply {
        val holdHex = FastCatchPreset.holdPayloadHexForReplay(context, lane, laneOffsetTouch) ?: return HelperReply(
            command = "PLAY_CONCURRENT",
            lines = listOf("ERROR code=no_preset errno=0 message=\"fast catch preset missing\""),
        )
        return GestureHelperSession.runExclusive {
            val playReply = helperClient.playConcurrent(holdHex = holdHex, throwHex = throwHex)
            if (!playReply.ok) return@runExclusive playReply

            playFastCatchFinishTap(lane) ?: playReply
        }
    }

    private suspend fun playFastCatchFinishTap(lane: HelperLane): HelperReply? {
        val metrics = context.resources.displayMetrics
        val (x, y) = fastCatchFinishTap(metrics.widthPixels, metrics.heightPixels, lane)

        delay(FAST_CATCH_FINISH_STEP_DELAY_MS)
        val dismissReply = helperClient.send("TAP $x $y", timeoutMs = 5_000)
        ThrowletLog.i(
            "fast catch finish dismiss lane=$lane x=$x y=$y ok=${dismissReply.ok} " +
                "reply=${dismissReply.displayText.take(120)}",
        )
        return if (dismissReply.ok) null else dismissReply
    }

    private fun fastCatchFinishTap(screenWidth: Int, screenHeight: Int, lane: HelperLane): Pair<Int, Int> {
        val referencePoint = when (lane) {
            HelperLane.SPLIT_BOTTOM -> FAST_CATCH_FINISH_BOTTOM_TAP_X to FAST_CATCH_FINISH_BOTTOM_TAP_Y
            HelperLane.FULL,
            HelperLane.SPLIT_TOP -> FAST_CATCH_FINISH_TOP_TAP_X to FAST_CATCH_FINISH_TOP_TAP_Y
        }

        val scaleX = screenWidth.toFloat() / FAST_CATCH_FINISH_REFERENCE_WIDTH.toFloat()
        val scaleY = screenHeight.toFloat() / FAST_CATCH_FINISH_REFERENCE_HEIGHT.toFloat()
        val x = (referencePoint.first * scaleX).toInt().coerceIn(0, screenWidth.coerceAtLeast(1) - 1)
        val y = (referencePoint.second * scaleY).toInt().coerceIn(0, screenHeight.coerceAtLeast(1) - 1)
        return x to y
    }

    private suspend fun playBuddyGesture(lane: HelperLane): Boolean {
        val session = manager.activeSessions[lane] ?: return false
        val state = session.detectionState
        val pokemonKey = state?.pokemonKey ?: "unknown"
        val storageMode = GestureModes.storageMode(session.mode, lane)
        val entity = withContext(Dispatchers.IO) { gestureStore.find(pokemonKey, storageMode) } ?: return false
        val decoded = RawGestureCodec.decode(entity.payloadHex).getOrElse { return false }
        val calibration = calibration()
        val replay = SplitLaneTransforms.forReplayStored(
            payload = decoded,
            sourceLane = entity.sourceLane,
            targetLane = lane,
            laneOffsetTouch = calibration.topToBottomTouchDy,
        )
        val payload = replay.encodeHex()
        val playReply = GestureHelperSession.runExclusive {
            val importReply = helperClient.send("IMPORT_GESTURE $payload")
            if (!importReply.ok) return@runExclusive importReply
            helperClient.send("PLAY_LAST")
        }
        return playReply.ok
    }

    private fun renderSessionRail(session: HelperSession, reason: String) {
        val state = session.detectionState ?: return
        (session.railController as? AndroidRailController)?.apply {
            updateRail(state)
            updateBerry(session.selectedBerry)
            updateFastCatch(session.fastCatchEnabled)
        }
    }

    private fun renderBerryIcon(lane: HelperLane) {
        val session = manager.activeSessions[lane] ?: return
        (session.railController as? AndroidRailController)?.updateBerry(session.selectedBerry)
    }

    private fun renderFastCatchIcon(lane: HelperLane) {
        val session = manager.activeSessions[lane] ?: return
        (session.railController as? AndroidRailController)?.updateFastCatch(session.fastCatchEnabled)
    }

    private fun applyBerrySelection(session: HelperSession) {
        session.selectedBerry = berryStore.load(session.detectionState?.pokemonName)
    }

    private fun applyFastCatchSelection(session: HelperSession) {
        if (session.mode != HelperMode.CATCH) return
        session.fastCatchEnabled = fastCatchStore.load(session.lane)
    }

    private suspend fun enrichDetectionState(session: HelperSession, state: CatchDetectionState): CatchDetectionState {
        val pokemonKey = state.pokemonKey ?: return state
        val storageMode = GestureModes.storageMode(session.mode, session.lane)
        val entity = withContext(Dispatchers.IO) { gestureStore.find(pokemonKey, storageMode) }
        return state.copy(
            hasGesture = entity != null,
            throwScore = ThrowScore.fromStored(entity?.throwScore),
        )
    }

    private fun manualPokemonState(pokemonName: String?): CatchDetectionState {
        val clean = pokemonName?.trim().orEmpty()
        if (clean.isBlank()) {
            return CatchDetectionState(
                pokemonKey = null,
                pokemonName = null,
                confidencePercent = null,
                hasGesture = false,
                message = "Choose a Pokémon",
            )
        }

        val match = PokemonCatalog.get(context).resolveExactName(clean)
        val name = match?.name ?: clean
        val key = match?.key ?: clean.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-')
        return CatchDetectionState(
            pokemonKey = key,
            pokemonName = name,
            confidencePercent = 100,
            hasGesture = false,
            message = "Using manually selected Pokémon",
        )
    }

    private suspend fun ensureLatestSwipeCapture(reason: String) {
        helperClient.send("START_LATEST_SWIPE_CAPTURE", timeoutMs = 5_000)
    }

    private suspend fun calibration(): SplitCalibration = calibrationStore.load()

    private suspend fun laneDividerPx(): Int {
        val calibration = calibrationStore.load()
        return calibration.topToBottomScreenshotDy
            ?: TouchCoordinateSpace.profile(context).defaultScreenshotDividerPx()
    }

    private fun displaySize(): SizeI =
        SizeI(context.resources.displayMetrics.widthPixels, context.resources.displayMetrics.heightPixels)

    private fun logSupabaseSync(label: String, result: SupabaseSyncResult) {
        Log.i(TAG, "supabase $label ${result.statusText()}")
    }

    private fun toast(message: String) = context.toast(message)

    private fun elapsedMs(startedAtNs: Long): Long =
        (System.nanoTime() - startedAtNs) / 1_000_000

    companion object {
        private const val TAG = "SacThrowletCatch"
        private const val FAST_CATCH_FINISH_STEP_DELAY_MS = 300L
        private const val FAST_CATCH_FINISH_REFERENCE_WIDTH = 1440
        private const val FAST_CATCH_FINISH_REFERENCE_HEIGHT = 3120
        private const val FAST_CATCH_FINISH_TOP_TAP_X = 150
        private const val FAST_CATCH_FINISH_TOP_TAP_Y = 240
        private const val FAST_CATCH_FINISH_BOTTOM_TAP_X = 150
        private const val FAST_CATCH_FINISH_BOTTOM_TAP_Y = 1780
    }
}

fun interface ThrowletBuddyCropSaver {
    fun showSaveUi(
        lane: HelperLane,
        sacCrop: SacCropResult,
        dividerPx: Int,
        pokemonName: String?,
        onDismiss: () -> Unit,
    )
}

private fun ThrowletCatchMode.toHelperMode(): HelperMode = when (this) {
    ThrowletCatchMode.CATCH -> HelperMode.CATCH
    ThrowletCatchMode.BUDDY -> HelperMode.BUDDY
}

private fun ThrowletCatchLane.toHelperLane(): HelperLane = when (this) {
    ThrowletCatchLane.FULL -> HelperLane.FULL
    ThrowletCatchLane.TOP -> HelperLane.SPLIT_TOP
    ThrowletCatchLane.BOTTOM -> HelperLane.SPLIT_BOTTOM
}
