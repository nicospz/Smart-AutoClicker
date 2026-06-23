/*
 * Copyright (C) 2024 Kevin Buzeau
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.buzbuz.smartautoclicker.localservice

import android.app.Notification
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.util.Log
import android.view.KeyEvent

import com.buzbuz.smartautoclicker.R
import com.buzbuz.smartautoclicker.core.base.data.AppComponentsProvider
import com.buzbuz.smartautoclicker.core.common.actions.AndroidActionExecutor
import com.buzbuz.smartautoclicker.core.common.actions.ThrowletCatchController
import com.buzbuz.smartautoclicker.core.common.actions.ThrowletCatchControllers
import com.buzbuz.smartautoclicker.core.common.actions.ThrowletCatchLane
import com.buzbuz.smartautoclicker.core.common.actions.ThrowletCatchMode
import com.buzbuz.smartautoclicker.core.common.actions.ThrowletCatchOperation
import com.buzbuz.smartautoclicker.core.common.actions.ThrowletCatchSession
import com.buzbuz.smartautoclicker.core.common.overlays.manager.OverlayManager
import com.buzbuz.smartautoclicker.core.display.recorder.DisplayRecorder
import com.buzbuz.smartautoclicker.core.domain.model.scenario.ScreenCaptureMode
import com.buzbuz.smartautoclicker.core.display.recorder.ThrowletCropPicker
import com.buzbuz.smartautoclicker.feature.throwlet.ThrowletHelperController
import com.buzbuz.smartautoclicker.feature.throwlet.data.GestureStore
import com.buzbuz.smartautoclicker.feature.throwlet.data.ThrowletDatabase
import com.buzbuz.smartautoclicker.feature.throwlet.sync.SupabaseSyncRepository
import com.buzbuz.smartautoclicker.core.domain.model.scenario.Scenario
import com.buzbuz.smartautoclicker.core.dumb.domain.IDumbRepository
import com.buzbuz.smartautoclicker.core.dumb.domain.model.DumbScenario
import com.buzbuz.smartautoclicker.core.dumb.engine.DumbEngine
import com.buzbuz.smartautoclicker.core.processing.domain.SmartProcessingRepository
import com.buzbuz.smartautoclicker.core.processing.domain.model.DetectionState
import com.buzbuz.smartautoclicker.core.settings.SettingsRepository
import com.buzbuz.smartautoclicker.core.smart.debugging.domain.DebuggingRepository
import com.buzbuz.smartautoclicker.feature.smart.config.ui.mainmenu.MainMenu
import com.buzbuz.smartautoclicker.feature.dumb.config.ui.DumbMainMenu
import com.buzbuz.smartautoclicker.feature.notifications.ServiceNotificationController
import com.buzbuz.smartautoclicker.feature.notifications.ServiceNotificationListener
import com.buzbuz.smartautoclicker.feature.revenue.IRevenueRepository
import com.buzbuz.smartautoclicker.feature.revenue.UserBillingState
import com.buzbuz.smartautoclicker.feature.throwlet.ThrowletRepository
import com.buzbuz.smartautoclicker.feature.sync.domain.SacSyncCoordinator
import com.buzbuz.smartautoclicker.buttons.ButtonOverlayController
import com.buzbuz.smartautoclicker.buttons.SavedOverlayButtonRepository

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

class LocalService(
    private val context: Context,
    private val overlayManager: OverlayManager,
    private val appComponentsProvider: AppComponentsProvider,
    private val settingsRepository: SettingsRepository,
    private val smartProcessingRepository: SmartProcessingRepository,
    private val dumbRepository: IDumbRepository,
    private val dumbEngine: DumbEngine,
    private val savedOverlayButtonRepository: SavedOverlayButtonRepository,
    private val actionExecutor: AndroidActionExecutor,
    private val throwletRepository: ThrowletRepository,
    private val throwletDatabase: ThrowletDatabase,
    private val gestureStore: GestureStore,
    private val throwletSyncRepository: SupabaseSyncRepository,
    private val sacSyncCoordinator: SacSyncCoordinator,
    private val displayRecorder: DisplayRecorder,
    private val throwletCropPicker: ThrowletCropPicker,
    private val revenueRepository: IRevenueRepository,
    private val debuggingRepository: DebuggingRepository,
    private val onStart: (scenarioId: Long?, isSmart: Boolean?, foregroundNotification: Notification?) -> Unit,
    private val onStop: () -> Unit,
) : ILocalService {

    /** Scope for this LocalService. */
    private val serviceScope: CoroutineScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    /** Coroutine job for the delayed start of engine & ui. */
    private var startJob: Job? = null
    /** Coroutine job for delayed auto-start. */
    private var autoStartJob: Job? = null
    /** Coroutine job for the paywall result upon start from notification. */
    private var paywallResultJob: Job? = null

    /** Controls the notifications for the foreground service. */
    private val notificationController: ServiceNotificationController by lazy {
        ServiceNotificationController(
            context = context,
            appComponentsProvider = appComponentsProvider,
            settingsRepository = settingsRepository,
            listener = object : ServiceNotificationListener {
                override fun onPlay() = play()
                override fun onPause()= pause()
                override fun onShow() = showMenu()
                override fun onHide() = hideMenu()
                override fun onStop() = stop()
            }
        )
    }

    /** State of this LocalService. */
    private var state: LocalServiceState = LocalServiceState(isStarted = false, isSmartLoaded = false)
    /** True if the overlay is started, false if not. */
    internal val isStarted: Boolean
        get() = state.isStarted

    private val throwletHelperController: ThrowletHelperController by lazy {
        ThrowletHelperController(
            context = context,
            scope = serviceScope,
            database = throwletDatabase,
            gestureStore = gestureStore,
            syncRepository = throwletSyncRepository,
            actionExecutor = actionExecutor,
            screenshotSource = ThrowletDisplayScreenshotSource(displayRecorder),
            cropPicker = throwletCropPicker,
            buddyCropSaver = BuddyCropSaveOverlay(
                context = context,
                scope = serviceScope,
                throwletRepository = throwletRepository,
            ),
            isScreenRecording = { state.isSmartLoaded },
            onThrowletSyncRequested = { sacSyncCoordinator.requestThrowletSync() },
        )
    }

    private val buttonOverlayController: ButtonOverlayController by lazy {
        ButtonOverlayController(
            context = context,
            scope = serviceScope,
            repository = savedOverlayButtonRepository,
            dumbRepository = dumbRepository,
            dumbEngine = dumbEngine,
            sacSyncCoordinator = sacSyncCoordinator,
        )
    }

    init {
        ThrowletCatchControllers.instance = ThrowletCatchController { operation, session ->
            Log.i(THROWLET_CATCH_TAG, "controller operation=$operation session=$session")
            throwletHelperController.execute(operation, session)
        }

        serviceScope.launch {
            sacSyncCoordinator.requestFullSync()
        }
        combine(dumbEngine.isRunning, smartProcessingRepository.detectionState) { dumbIsRunning, smartState ->
            dumbIsRunning || smartState == DetectionState.DETECTING
        }.onEach { isRunning ->
            notificationController.updateNotification(context, isRunning, !overlayManager.isStackHidden())
        }.launchIn(serviceScope)

        overlayManager.onVisibilityChangedListener = {
            notificationController.updateNotification(
                context,
                dumbEngine.isRunning.value || smartProcessingRepository.isRunning(),
                !overlayManager.isStackHidden()
            )
        }
    }

    override fun startDumbScenario(dumbScenario: DumbScenario) {
        if (state.isStarted) return
        autoStartJob?.cancel()
        state = LocalServiceState(isStarted = true, isSmartLoaded = false)
        onStart(dumbScenario.id.databaseId, false, null)

        startJob = serviceScope.launch {
            delay(500)

            dumbEngine.init(dumbScenario)

            overlayManager.navigateTo(
                context = context,
                newOverlay = DumbMainMenu(dumbScenario.id) { stop() },
            )

            scheduleDumbAutoStart(dumbScenario)
        }
    }

    override fun startButtonOverlay() {
        if (state.isStarted) return
        autoStartJob?.cancel()
        state = LocalServiceState(isStarted = true, isSmartLoaded = false, isButtonOverlayLoaded = true)
        val activeSetName = savedOverlayButtonRepository.sets.value
            .firstOrNull { it.syncId == savedOverlayButtonRepository.activeSetSyncId.value && it.deletedAtMs == null }
            ?.name
        val notification = notificationController.createNotification(
            context = context,
            scenarioName = activeSetName
                ?.let { "${context.getString(R.string.activity_buttons_title)}: $it" }
                ?: context.getString(R.string.activity_buttons_title),
            isRunning = false,
            isMenuVisible = true,
            isButtonOverlay = true,
        )
        notificationController.showNotification(context, notification)
        onStart(null, null, null)

        startJob = serviceScope.launch {
            delay(250)
            buttonOverlayController.show()
        }
    }

    override fun startThrowletOverlay() {
        Log.i(THROWLET_CATCH_TAG, "startThrowletOverlay isStarted=${state.isStarted}")
        if (state.isStarted) {
            showThrowletOverlay()
            return
        }

        autoStartJob?.cancel()
        state = LocalServiceState(isStarted = true, isSmartLoaded = false, isThrowletOverlayLoaded = true)
        val notification = notificationController.createNotification(
            context = context,
            scenarioName = context.getString(R.string.activity_throwlet_overlay_title),
            isRunning = false,
            isMenuVisible = true,
        )
        notificationController.showNotification(context, notification)
        onStart(null, null, null)

        startJob = serviceScope.launch {
            delay(250)
            showThrowletOverlay()
        }
    }

    /**
     * Start the overlay UI and instantiates the detection objects.
     *
     * This requires the media projection permission code and its data intent, they both can be retrieved using the
     * results of the activity intent provided by [MediaProjectionManager.createScreenCaptureIntent] (this Intent
     * shows the dialog warning about screen recording privacy). Any attempt to call this method without the
     * correct screen capture intent result will lead to a crash.
     *
     * @param resultCode the result code provided by the screen capture intent activity result callback
     * [android.app.Activity.onActivityResult]
     * @param data the data intent provided by the screen capture intent activity result callback
     * [android.app.Activity.onActivityResult]
     * @param scenario the identifier of the scenario of clicks to be used for detection.
     */
    override fun startSmartScenario(resultCode: Int, data: Intent?, scenario: Scenario) {
        if (isStarted) return
        autoStartJob?.cancel()
        state = LocalServiceState(isStarted = true, isSmartLoaded = true)

        onStart(
            scenario.id.databaseId,
            true,
            notificationController.createNotification(
                context = context,
                scenarioName = scenario.name,
                isRunning = false,
                isMenuVisible = true
            )
        )

        startJob = serviceScope.launch {
            Log.i(TAG, "startSmartScenario: creating MainMenu for scenario=${scenario.name} id=${scenario.id.databaseId}")
            val mainMenu = MainMenu { stop() }

            smartProcessingRepository.apply {
                setScenarioId(scenario.id, markAsUsed = true)
                setProjectionErrorHandler { mainMenu.onMediaProjectionLost() }
            }

            Log.i(TAG, "startSmartScenario: navigating to MainMenu")
            overlayManager.navigateTo(
                context = context,
                newOverlay = mainMenu,
            )
            val topOverlay = overlayManager.getBackStackTop()
            Log.i(
                TAG,
                "startSmartScenario: navigateTo done top=${topOverlay?.javaClass?.simpleName} " +
                    "lifecycle=${topOverlay?.lifecycle?.currentState}",
            )

            Log.i(TAG, "startSmartScenario: starting screen record mode=${scenario.screenCaptureMode}")
            when (scenario.screenCaptureMode) {
                ScreenCaptureMode.MEDIA_PROJECTION -> {
                    if (data == null) {
                        Log.e(TAG, "startSmartScenario: missing MediaProjection data")
                        stop()
                        return@launch
                    }

                    smartProcessingRepository.startScreenRecord(
                        resultCode = resultCode,
                        data = data,
                    )
                }

                ScreenCaptureMode.ACCESSIBILITY_SCREENSHOT ->
                    smartProcessingRepository.startAccessibilityScreenshotRecord()
            }

            scheduleSmartAutoStart(scenario)
        }
    }

    override fun stop() {
        if (!isStarted) return
        state = LocalServiceState(isStarted = false, isSmartLoaded = false)

        serviceScope.launch {
            startJob?.join()
            startJob = null
            autoStartJob?.cancel()
            autoStartJob = null

            dumbEngine.release()
            overlayManager.closeAll(context)
            buttonOverlayController.hideAll()
            throwletHelperController.hideAll()
            smartProcessingRepository.stopScreenRecord()

            onStop()
            notificationController.destroyNotification()
        }
    }

    override fun release() {
        ThrowletCatchControllers.instance = null
        buttonOverlayController.hideAll()
        throwletHelperController.hideAll()
        autoStartJob?.cancel()
        serviceScope.cancel()
    }

    override fun toggleThrowletOverlay() {
        Log.i(THROWLET_CATCH_TAG, "toggleThrowletOverlay")
        serviceScope.launch {
            throwletHelperController.execute(
                ThrowletCatchOperation.TOGGLE,
                ThrowletCatchSession(ThrowletCatchMode.CATCH, ThrowletCatchLane.FULL),
            )
        }
    }

    override fun showThrowletOverlay() {
        Log.i(THROWLET_CATCH_TAG, "showThrowletOverlay")
        serviceScope.launch {
            throwletHelperController.execute(
                ThrowletCatchOperation.SHOW,
                ThrowletCatchSession(ThrowletCatchMode.CATCH, ThrowletCatchLane.FULL),
            )
        }
    }

    override fun hideThrowletOverlay() {
        Log.i(THROWLET_CATCH_TAG, "hideThrowletOverlay")
        serviceScope.launch {
            throwletHelperController.execute(
                ThrowletCatchOperation.HIDE,
                ThrowletCatchSession(ThrowletCatchMode.CATCH, ThrowletCatchLane.FULL),
            )
        }
    }

    internal fun onKeyEvent(event: KeyEvent?): Boolean {
        event ?: return false
        return overlayManager.propagateKeyEvent(event)
    }

    private fun play() {
        serviceScope.launch {
            autoStartJob?.cancel()
            autoStartJob = null
            if (state.isSmartLoaded && !smartProcessingRepository.isRunning()) {
                if (revenueRepository.userBillingState.value == UserBillingState.AD_REQUESTED) startPaywall()
                else startSmartScenario()
            } else if (!state.isSmartLoaded && !dumbEngine.isRunning.value) {
                dumbEngine.startDumbScenario()
            }
        }
    }

    private fun pause() {
        serviceScope.launch {
            autoStartJob?.cancel()
            autoStartJob = null
            when {
                dumbEngine.isRunning.value -> dumbEngine.stopDumbScenario()
                smartProcessingRepository.isRunning() -> smartProcessingRepository.stopDetection()
            }
        }
    }

    private fun startPaywall() {
        revenueRepository.startPaywallUiFlow(context)

        paywallResultJob = combine(revenueRepository.isBillingFlowInProgress, revenueRepository.userBillingState) { inProgress, state ->
            if (inProgress) return@combine

            if (state != UserBillingState.AD_REQUESTED) startSmartScenario()
            paywallResultJob?.cancel()
            paywallResultJob = null
        }.launchIn(serviceScope)
    }

    private fun startSmartScenario() {
        serviceScope.launch {
            smartProcessingRepository.startDetection(
                context = context,
                autoStopDuration = revenueRepository.consumeTrial(),
                liveDebugging = debuggingRepository.isDebugViewEnabled(),
                generateReport = debuggingRepository.isDebugReportEnabled(),
            )
        }
    }

    private fun scheduleDumbAutoStart(dumbScenario: DumbScenario) {
        if (!dumbScenario.autoStart) return

        autoStartJob = serviceScope.launch {
            delay(dumbScenario.autoStartDelayMs)
            if (state.isStarted && !state.isSmartLoaded && !dumbEngine.isRunning.value) {
                dumbEngine.startDumbScenario()
            }
        }
    }

    private fun scheduleSmartAutoStart(scenario: Scenario) {
        if (!scenario.autoStart) return

        autoStartJob = serviceScope.launch {
            smartProcessingRepository.detectionState
                .filter { it == DetectionState.RECORDING }
                .first()

            delay(scenario.autoStartDelayMs)
            if (state.isStarted && state.isSmartLoaded && !smartProcessingRepository.isRunning()) {
                if (revenueRepository.userBillingState.value == UserBillingState.AD_REQUESTED) startPaywall()
                else startSmartScenario()
            }
        }
    }

    private fun hideMenu() {
        overlayManager.hideAll()
    }

    private fun showMenu() {
        overlayManager.restoreVisibility()
    }
}

private data class LocalServiceState(
    val isStarted: Boolean,
    val isSmartLoaded: Boolean,
    val isButtonOverlayLoaded: Boolean = false,
    val isThrowletOverlayLoaded: Boolean = false,
)

private const val TAG = "LocalService"
private const val THROWLET_CATCH_TAG = "SacThrowletCatch"
