package com.buzbuz.smartautoclicker.buttons

import android.content.Context
import android.content.res.Configuration
import android.graphics.Point
import android.text.TextUtils
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView

import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.core.view.setPadding

import com.buzbuz.smartautoclicker.R
import com.buzbuz.smartautoclicker.core.base.extensions.disableMoveAnimations
import com.buzbuz.smartautoclicker.core.base.extensions.safeAddView
import com.buzbuz.smartautoclicker.core.base.extensions.safeUpdateViewLayout
import com.buzbuz.smartautoclicker.core.common.overlays.manager.OverlayManager
import com.buzbuz.smartautoclicker.core.dumb.domain.IDumbRepository
import com.buzbuz.smartautoclicker.core.dumb.engine.DumbEngine
import com.buzbuz.smartautoclicker.feature.sync.domain.SacSyncCoordinator

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ButtonOverlayController(
    private val context: Context,
    private val scope: CoroutineScope,
    private val repository: SavedOverlayButtonRepository,
    private val dumbRepository: IDumbRepository,
    private val dumbEngine: DumbEngine,
    private val sacSyncCoordinator: SacSyncCoordinator,
) {

    private val windowManager: WindowManager = context.getSystemService(WindowManager::class.java)
    private val menuBtnSize: Int =
        context.resources.getDimensionPixelSize(R.dimen.overlay_menu_btn_size)
    private val menuBtnColor: Int =
        ContextCompat.getColor(context, R.color.overlayMenuButtons)
    private val shownButtons = mutableMapOf<Long, ButtonWindow>()
    private var railWindow: RailWindow? = null
    private var collectJob: Job? = null
    private var activeButtonId: Long? = null
    private var shownSetSyncId: String? = null

    fun show() {
        if (collectJob != null) return
        collectJob = scope.launch(Dispatchers.Main) {
            combine(
                repository.sets,
                repository.activeSetSyncId,
                repository.buttons,
                dumbEngine.isRunning,
                dumbEngine.isPaused,
            ) { sets, activeSetSyncId, buttons, _, _ ->
                val activeSet = sets.firstOrNull { it.syncId == activeSetSyncId && it.deletedAtMs == null }
                ActiveSetRender(
                    set = activeSet,
                    buttons = buttons.filter { button ->
                        activeSet != null &&
                                button.deletedAtMs == null &&
                                button.isVisible &&
                                button.setSyncId == activeSet.syncId
                    }.sortedBy { it.priority },
                )
            }.collect { renderState ->
                render(renderState.set, renderState.buttons)
                updateButtonStates()
            }
        }
    }

    fun hideAll() {
        collectJob?.cancel()
        collectJob = null
        clearRail(stopRunningScenario = dumbEngine.isRunning.value || dumbEngine.isPaused.value)
    }

    private fun render(activeSet: SavedOverlayButtonSet?, visibleButtons: List<SavedOverlayButton>) {
        if (activeSet == null || visibleButtons.isEmpty()) {
            val activeStillVisible = activeButtonId != null && visibleButtons.any { it.id == activeButtonId }
            clearRail(stopRunningScenario = activeSet?.syncId != shownSetSyncId || !activeStillVisible)
            return
        }

        if (shownSetSyncId != null && shownSetSyncId != activeSet.syncId) {
            clearRail(stopRunningScenario = true)
        }
        shownSetSyncId = activeSet.syncId

        val rail = ensureRailWindow(activeSet)
        val visibleIds = visibleButtons.map { it.id }.toSet()
        shownButtons.keys.toList().filterNot { it in visibleIds }.forEach(::removeButton)
        visibleButtons.forEach { button ->
            val existing = shownButtons[button.id]
            if (existing == null) addButton(rail, button) else existing.bind(button)
        }
    }

    private fun clearRail(stopRunningScenario: Boolean) {
        railWindow?.let { window -> runCatching { windowManager.removeView(window.view) } }
        railWindow = null
        shownButtons.clear()
        shownSetSyncId = null
        if (stopRunningScenario && (dumbEngine.isRunning.value || dumbEngine.isPaused.value)) {
            dumbEngine.stopDumbScenario()
        }
        activeButtonId = null
    }

    private fun ensureRailWindow(activeSet: SavedOverlayButtonSet): RailWindow {
        railWindow?.let { return it }

        val items = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(
                context.resources.getDimensionPixelSize(R.dimen.overlay_menu_padding_horizontal),
                context.resources.getDimensionPixelSize(R.dimen.overlay_menu_padding_vertical),
                context.resources.getDimensionPixelSize(R.dimen.overlay_menu_padding_horizontal),
                context.resources.getDimensionPixelSize(R.dimen.overlay_menu_padding_vertical),
            )
        }
        val card = CardView(context).apply {
            radius = context.resources.getDimension(R.dimen.overlay_menu_corner_radius)
            cardElevation = 0f
            setCardBackgroundColor(ContextCompat.getColor(context, R.color.overlayMenuBackground))
            addView(items)
        }
        val params = createLayoutParams(activeSet)
        val rail = RailWindow(card, items, params)
        if (windowManager.safeAddView(card, params)) railWindow = rail
        return railWindow ?: rail
    }

    private fun removeButton(buttonId: Long) {
        shownButtons.remove(buttonId)?.let { window -> railWindow?.items?.removeView(window.view) }
    }

    private fun createLayoutParams(set: SavedOverlayButtonSet): WindowManager.LayoutParams {
        val displaySize = displaySize()
        val (xPercent, yPercent) = set.currentPosition(isLandscape())
        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            OverlayManager.OVERLAY_WINDOW_TYPE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            android.graphics.PixelFormat.TRANSLUCENT,
        ).apply {
            disableMoveAnimations()
            gravity = Gravity.TOP or Gravity.START
            x = (displaySize.x * xPercent).toInt()
            y = (displaySize.y * yPercent).toInt()
        }
    }

    private fun addButton(rail: RailWindow, button: SavedOverlayButton) {
        val textView = TextView(context).apply {
            text = button.overlayText
            setTextColor(menuBtnColor)
            textSize = button.textSizeSp()
            gravity = Gravity.CENTER
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            background = null
            layoutParams = LinearLayout.LayoutParams(menuBtnSize, menuBtnSize)
            alpha = buttonOpacity(isPaused = false, enabled = button.enabled)
        }
        val window = ButtonWindow(button, textView)
        textView.setOnTouchListener(ButtonTouchHandler(window))
        rail.items.addView(textView)
        shownButtons[button.id] = window
    }

    private fun onButtonClicked(button: SavedOverlayButton) {
        if (!button.enabled) return
        scope.launch {
            when {
                activeButtonId == button.id && dumbEngine.isRunning.value -> dumbEngine.pauseDumbScenario()
                activeButtonId == button.id && dumbEngine.isPaused.value -> dumbEngine.resumeDumbScenario()
                else -> {
                    if (dumbEngine.isRunning.value || dumbEngine.isPaused.value) dumbEngine.stopDumbScenario()
                    val scenario = withContext(Dispatchers.IO) {
                        dumbRepository.getDumbScenario(button.scenarioDbId)
                    } ?: run {
                        repository.setEnabled(button.id, false)
                        sacSyncCoordinator.scheduleSettingsPush()
                        return@launch
                    }
                    activeButtonId = button.id
                    dumbEngine.init(scenario)
                    dumbEngine.startDumbScenario()
                }
            }
        }
    }

    private fun onButtonLongPressed(button: SavedOverlayButton) {
        if (activeButtonId == button.id && (dumbEngine.isRunning.value || dumbEngine.isPaused.value)) {
            dumbEngine.stopDumbScenario()
            activeButtonId = null
        }
        repository.setVisible(button.id, false)
        sacSyncCoordinator.scheduleSettingsPush()
    }

    private fun onButtonMoved() {
        val rail = railWindow ?: return
        val setSyncId = shownSetSyncId ?: return
        val displaySize = displaySize()
        repository.updateSetPosition(
            setSyncId = setSyncId,
            xPercent = rail.params.x.toFloat() / displaySize.x.toFloat(),
            yPercent = rail.params.y.toFloat() / displaySize.y.toFloat(),
            isLandscape = isLandscape(),
        )
        sacSyncCoordinator.scheduleSettingsPush()
    }

    private fun updateButtonStates() {
        shownButtons.values.forEach { window ->
            val isPaused = window.button.id == activeButtonId && dumbEngine.isPaused.value
            window.view.alpha = buttonOpacity(isPaused, window.button.enabled)
            window.view.textSize = window.button.textSizeSp()
            window.view.text = window.button.overlayText
        }
    }

    private inner class ButtonTouchHandler(private val window: ButtonWindow) : View.OnTouchListener {
        private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
        private val longPressTimeout = ViewConfiguration.getLongPressTimeout().toLong()
        private var downRaw = Point()
        private var downWindow = Point()
        private var moved = false
        private var longPressed = false
        private val longPressRunnable = Runnable {
            longPressed = true
            onButtonLongPressed(window.button)
        }

        override fun onTouch(view: View, event: MotionEvent): Boolean {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    moved = false
                    longPressed = false
                    downRaw = Point(event.rawX.toInt(), event.rawY.toInt())
                    val params = railWindow?.params ?: return false
                    downWindow = Point(params.x, params.y)
                    view.postDelayed(longPressRunnable, longPressTimeout)
                    return true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX.toInt() - downRaw.x
                    val dy = event.rawY.toInt() - downRaw.y
                    if (!moved && dx * dx + dy * dy > touchSlop * touchSlop) {
                        moved = true
                        view.removeCallbacks(longPressRunnable)
                    }
                    if (moved && !longPressed) {
                        val rail = railWindow ?: return true
                        rail.params.x = downWindow.x + dx
                        rail.params.y = downWindow.y + dy
                        windowManager.safeUpdateViewLayout(rail.view, rail.params)
                    }
                    return true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    view.removeCallbacks(longPressRunnable)
                    when {
                        moved && !longPressed -> onButtonMoved()
                        !moved && !longPressed -> onButtonClicked(window.button)
                    }
                    return true
                }
            }
            return true
        }
    }

    private inner class ButtonWindow(
        var button: SavedOverlayButton,
        val view: TextView,
    ) {
        fun bind(newButton: SavedOverlayButton) {
            button = newButton
            view.text = newButton.overlayText
            view.textSize = newButton.textSizeSp()
        }
    }

    private class RailWindow(
        val view: View,
        val items: LinearLayout,
        val params: WindowManager.LayoutParams,
    )

    private data class ActiveSetRender(
        val set: SavedOverlayButtonSet?,
        val buttons: List<SavedOverlayButton>,
    )

    private fun SavedOverlayButtonSet.currentPosition(landscape: Boolean): Pair<Float, Float> =
        if (landscape) {
            (landscapeXPercent ?: portraitXPercent) to (landscapeYPercent ?: portraitYPercent)
        } else {
            portraitXPercent to portraitYPercent
        }

    private fun buttonOpacity(isPaused: Boolean, enabled: Boolean): Float =
        when {
            !enabled || isPaused -> 0.35f
            else -> 1f
        }

    private fun SavedOverlayButton.textSizeSp(): Float =
        if (iconGlyph.isNullOrBlank()) 9f else 18f

    private fun displaySize(): Point {
        val metrics = context.resources.displayMetrics
        return Point(metrics.widthPixels.coerceAtLeast(1), metrics.heightPixels.coerceAtLeast(1))
    }

    private fun isLandscape(): Boolean =
        context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
}
