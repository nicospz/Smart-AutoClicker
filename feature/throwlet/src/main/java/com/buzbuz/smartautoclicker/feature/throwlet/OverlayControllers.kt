package com.buzbuz.smartautoclicker.feature.throwlet

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.edit
import kotlin.math.max

class AndroidRailController(
    private val context: Context,
    private val mode: HelperMode,
    private val lane: HelperLane,
    private val callbacks: RailCallbacks,
    private val splitLayout: SplitScreenLayout? = null,
) : RailController {
    private val wm = context.getSystemService(WindowManager::class.java)
    private var rail: View? = null
    private var railParams: WindowManager.LayoutParams? = null
    private var actionPanel: View? = null
    private var actionParams: WindowManager.LayoutParams? = null
    private var visible = false
    private lateinit var spriteContainer: FrameLayout
    private lateinit var pokemonSprite: ImageView
    private lateinit var throwScoreBadge: TextView
    private lateinit var saveButton: ImageButton
    private lateinit var berryThrowButton: ImageButton
    private lateinit var fastCatchButton: ImageButton
    private lateinit var holdToThrowButton: ImageButton
    private lateinit var throwSpeedButton: ImageButton
    private lateinit var stopButton: ImageButton
    private lateinit var actionButton: ImageButton
    private var holdToThrowEnabled = false
    private var loadedSpriteName: String? = null
    private val splitPositions: SplitOverlayPositions? =
        splitLayout?.let { SplitOverlayPositions(context, mode, it) }

    override fun show() {
        if (visible) return
        visible = true
        ensureRail()
        ensureActionButton()
        rail?.visibility = View.VISIBLE
        if (mode != HelperMode.CATCH) {
            actionPanel?.visibility = View.VISIBLE
        }
    }

    override fun hide() {
        visible = false
        rail?.visibility = View.INVISIBLE
        actionPanel?.visibility = View.INVISIBLE
    }

    override fun stop() {
        visible = false
        rail?.let { runCatching { wm.removeView(it) } }
        actionPanel?.let { runCatching { wm.removeView(it) } }
        rail = null
        actionPanel = null
        railParams = null
        actionParams = null
    }

    fun updateBerry(berry: BerryAction) {
        if (!::berryThrowButton.isInitialized || mode != HelperMode.CATCH) return
        berryThrowButton.setImageResource(
            if (berry == BerryAction.NONE) R.drawable.ic_berry_none else berry.iconRes,
        )
        berryThrowButton.alpha = if (berry == BerryAction.NONE) 0.45f else 0.95f
        berryThrowButton.contentDescription = if (berry == BerryAction.NONE) {
            "Long press to choose berry"
        } else {
            "Throw ${berry.label} berry. Long press to change berry"
        }
    }

    fun updateFastCatch(enabled: Boolean) {
        if (!::fastCatchButton.isInitialized || mode != HelperMode.CATCH) return
        fastCatchButton.alpha = if (enabled) 0.95f else 0.45f
        fastCatchButton.contentDescription = if (enabled) {
            "Fast catch on (berry hold + throw)"
        } else {
            "Fast catch off"
        }
    }

    fun updateHoldToThrow(enabled: Boolean) {
        holdToThrowEnabled = enabled
        if (!::holdToThrowButton.isInitialized || mode != HelperMode.CATCH) return
        holdToThrowButton.alpha = if (enabled) 0.95f else 0.45f
        holdToThrowButton.contentDescription = if (enabled) {
            "Hold-to-throw on"
        } else {
            "Hold-to-throw off"
        }
    }

    fun updateThrowTuning(enabled: Boolean, tuning: ThrowGestureTuning) {
        if (!::throwSpeedButton.isInitialized || mode != HelperMode.CATCH) return
        val formattedSpeed = ThrowSpeedDialog.formatSpeed(tuning.speed)
        val offsetSummary = ThrowSpeedDialog.formatOffsetSummary(tuning)
        throwSpeedButton.alpha = if (enabled) 0.95f else 0.45f
        throwSpeedButton.contentDescription = if (enabled) {
            "Custom throw tuning $formattedSpeed$offsetSummary on. Long press to edit"
        } else {
            "Custom throw tuning $formattedSpeed$offsetSummary off. Long press to edit"
        }
    }

    fun updateRail(state: CatchDetectionState) {
        if (!::pokemonSprite.isInitialized) return
        val pokemonName = state.pokemonName
        if (pokemonName != null) {
            setPokemonSprite(pokemonName)
        } else {
            showDetectionPlaceholder(refreshIcon = true)
        }
        val showScore = mode == HelperMode.CATCH && pokemonName != null && state.hasGesture
        throwScoreBadge.text = state.throwScore.badge
        throwScoreBadge.visibility = if (showScore) View.VISIBLE else View.GONE
        if (mode == HelperMode.CATCH) {
            actionPanel?.visibility = if (state.hasGesture) View.VISIBLE else View.GONE
        }
        spriteContainer.contentDescription = when {
            mode == HelperMode.CATCH && showScore ->
                "Throw score: ${state.throwScore.label}. Tap to change, long press to rescan"
            mode == HelperMode.CATCH && pokemonName != null ->
                "Long press to rescan catch detection"
            mode == HelperMode.CATCH ->
                "Long press to rescan catch identity"
            pokemonName != null ->
                "Tap or long press to rescan ${mode.name.lowercase()} detection"
            else ->
                "Tap or long press to rescan ${mode.name.lowercase()} identity"
        }
    }

    private fun ensureRail() {
        if (rail != null) return
        val panel = DraggableOverlayLayout(
            context = context,
            windowManager = wm,
            paramsProvider = { railParams },
            touchSlop = dp(8),
            onDragFinished = { saveRailPosition() },
        ).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(0, dp(4), 0, dp(4))
            background = floatingMenuBackground()
            alpha = 0.96f
        }
        val identityFrame = FrameLayout(context).apply {
            isClickable = true
            isFocusable = true
            isLongClickable = true
            contentDescription = "Refresh ${mode.name.lowercase()} identity"
            setOnClickListener {
                if (mode == HelperMode.CATCH) {
                    callbacks.cycleThrowScore(lane)
                } else {
                    callbacks.refresh(lane)
                }
            }
            setOnLongClickListener {
                callbacks.refresh(lane)
                true
            }
        }
        spriteContainer = identityFrame
        pokemonSprite = ImageView(context).apply {
            adjustViewBounds = true
            scaleType = ImageView.ScaleType.FIT_CENTER
        }
        identityFrame.addView(pokemonSprite, FrameLayout.LayoutParams(dp(44), dp(44), Gravity.CENTER))
        throwScoreBadge = TextView(context).apply {
            visibility = View.GONE
            text = "?"
            textSize = 9f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            includeFontPadding = false
            background = badgeBackground(Color.argb(230, 0, 102, 136))
        }
        identityFrame.addView(
            throwScoreBadge,
            FrameLayout.LayoutParams(dp(14), dp(14)).apply { gravity = Gravity.TOP or Gravity.END },
        )
        panel.addView(identityFrame, LinearLayout.LayoutParams(dp(RAIL_WIDTH_DP), dp(RAIL_WIDTH_DP)))

        saveButton = menuIconButton(R.drawable.ic_overlay_save, "Save latest ${mode.name.lowercase()} gesture") { callbacks.saveLatest(lane) }
        if (mode == HelperMode.CATCH) {
            berryThrowButton = ImageButton(context).apply {
                setImageResource(R.drawable.ic_berry_none)
                contentDescription = "Long press to choose berry"
                background = null
                scaleType = ImageView.ScaleType.FIT_CENTER
                alpha = 0.45f
                isLongClickable = true
                val padding = dp(7)
                setPadding(padding, padding, padding, padding)
                layoutParams = LinearLayout.LayoutParams(dp(RAIL_WIDTH_DP), dp(BUTTON_SIZE_DP))
                setOnClickListener { callbacks.throwBerry(lane) }
                setOnLongClickListener {
                    callbacks.openBerryMenu(lane, this)
                    true
                }
            }
            panel.addView(berryThrowButton)
            fastCatchButton = menuIconButton(R.drawable.ic_overlay_fast_catch, "Fast catch off") {
                callbacks.toggleFastCatch(lane)
            }
            panel.addView(fastCatchButton)
            throwSpeedButton = menuIconButton(R.drawable.ic_overlay_speed, "Custom throw speed off") {
                callbacks.toggleThrowSpeed(lane)
            }.apply {
                alpha = 0.45f
                isLongClickable = true
                setOnLongClickListener {
                    callbacks.openThrowSpeedDialog(lane)
                    true
                }
            }
            panel.addView(throwSpeedButton)
            holdToThrowButton = menuIconButton(R.drawable.ic_overlay_play, "Hold-to-throw off") {
                callbacks.toggleHoldToThrow(lane)
            }
            holdToThrowButton.alpha = 0.45f
            panel.addView(holdToThrowButton)
        }
        stopButton = menuIconButton(R.drawable.ic_overlay_stop, "Stop ${lane.name.lowercase()} helper") { callbacks.stop(lane) }
        panel.addView(saveButton)
        if (mode == HelperMode.BUDDY) {
            panel.addView(
                menuIconButton(R.drawable.ic_overlay_capture, "Pick buddy region in Smart Auto Clicker") { callbacks.crop(lane) },
            )
        }
        panel.addView(stopButton)

        railParams = overlayParams().apply {
            val pos = restoredRailPosition()
            x = pos.first
            y = pos.second
        }
        rail = panel
        wm.addView(panel, railParams)
        panel.post { clamp(rail, railParams, laneClamp = true) }
    }

    private fun ensureActionButton() {
        if (actionPanel != null) return
        val panel = DraggableOverlayLayout(
            context = context,
            windowManager = wm,
            paramsProvider = { actionParams },
            touchSlop = dp(8),
            onDragFinished = { saveActionPosition() },
        ).apply {
            gravity = Gravity.CENTER
            background = null
        }
        val isCatch = mode == HelperMode.CATCH
        val actionButtonDp = if (isCatch) CATCH_ACTION_BUTTON_DP else ACTION_BUTTON_DP
        actionButton = ImageButton(context).apply {
            setImageResource(if (isCatch) R.drawable.ic_overlay_record else R.drawable.ic_overlay_play)
            contentDescription = if (isCatch) "Throw ${lane.name.lowercase()}" else "Play ${lane.name.lowercase()}"
            background = actionButtonBackground(isCatch)
            scaleType = android.widget.ImageView.ScaleType.CENTER
            val p = dp(if (isCatch) 14 else 10)
            setPadding(p, p, p, p)
            alpha = if (isCatch) 0.48f else 0.92f
            setOnTouchListener { view, event ->
                if (!isCatch || !holdToThrowEnabled) return@setOnTouchListener false
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        view.isPressed = true
                        callbacks.startHeldThrow(lane)
                        true
                    }
                    MotionEvent.ACTION_UP -> {
                        view.isPressed = false
                        callbacks.releaseHeldThrow(lane)
                        true
                    }
                    MotionEvent.ACTION_CANCEL -> {
                        view.isPressed = false
                        callbacks.cancelHeldThrow(lane)
                        true
                    }
                    else -> true
                }
            }
            setOnClickListener { if (!holdToThrowEnabled) callbacks.play(lane) }
        }
        panel.addView(actionButton, LinearLayout.LayoutParams(dp(actionButtonDp), dp(actionButtonDp)))
        actionParams = overlayParams().apply {
            val pos = restoredActionPosition()
            x = pos.first
            y = pos.second
        }
        actionPanel = panel
        wm.addView(panel, actionParams)
        panel.post { clamp(actionPanel, actionParams, laneClamp = true) }
    }

    private fun menuIconButton(iconRes: Int, description: String, onClick: () -> Unit): ImageButton =
        ImageButton(context).apply {
            setImageResource(iconRes)
            contentDescription = description
            background = null
            scaleType = android.widget.ImageView.ScaleType.CENTER
            alpha = 0.95f
            val padding = dp(5)
            setPadding(padding, padding, padding, padding)
            setOnClickListener { onClick() }
            layoutParams = LinearLayout.LayoutParams(dp(RAIL_WIDTH_DP), dp(BUTTON_SIZE_DP))
        }

    private fun overlayParams() = WindowManager.LayoutParams(
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.WRAP_CONTENT,
        if (Build.VERSION.SDK_INT >= 26) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
        PixelFormat.TRANSLUCENT,
    ).apply { gravity = Gravity.TOP or Gravity.START }

    private fun restoredRailPosition(): Pair<Int, Int> {
        val prefs = prefs()
        val metrics = context.resources.displayMetrics
        val defaultX = when (lane) {
            HelperLane.FULL, HelperLane.SPLIT_TOP -> metrics.widthPixels - dp(RAIL_WIDTH_DP + 12)
            HelperLane.SPLIT_BOTTOM -> metrics.widthPixels - dp(RAIL_WIDTH_DP + 12)
        }.coerceAtLeast(0)
        val divider = splitLayout?.dividerPx ?: (metrics.heightPixels / 2)
        val defaultY = when (lane) {
            HelperLane.FULL -> dp(110)
            HelperLane.SPLIT_TOP -> dp(72)
            HelperLane.SPLIT_BOTTOM -> divider + dp(72)
        }
        return prefs.getInt("rail_x", defaultX) to prefs.getInt("rail_y", defaultY)
    }

    private fun restoredActionPosition(): Pair<Int, Int> {
        val metrics = context.resources.displayMetrics
        val actionButtonDp = if (mode == HelperMode.CATCH) CATCH_ACTION_BUTTON_DP else ACTION_BUTTON_DP
        val defaultX = metrics.widthPixels - dp(actionButtonDp + 28)
        splitPositions?.let { return it.restoredActionPosition(lane, defaultX) }
        val prefs = prefs()
        val divider = splitLayout?.dividerPx ?: (metrics.heightPixels / 2)
        val defaultY = when (lane) {
            HelperLane.FULL -> metrics.heightPixels - dp(170)
            HelperLane.SPLIT_TOP -> divider - dp(105)
            HelperLane.SPLIT_BOTTOM -> divider + dp(23)
        }
        return prefs.getInt("action_x", defaultX) to prefs.getInt("action_y", defaultY)
    }

    private fun saveRailPosition() {
        clamp(rail, railParams, laneClamp = true)
        railParams?.let { prefs().edit { putInt("rail_x", it.x); putInt("rail_y", it.y) } }
    }

    private fun saveActionPosition() {
        clamp(actionPanel, actionParams, laneClamp = true)
        val params = actionParams ?: return
        splitPositions?.saveActionPosition(lane, params.x, params.y)
            ?: prefs().edit {
                putInt("action_x", params.x)
                putInt("action_y", params.y)
            }
    }

    private fun clamp(view: View?, params: WindowManager.LayoutParams?, laneClamp: Boolean = false) {
        if (view == null || params == null) return
        val metrics = context.resources.displayMetrics
        val maxX = max(0, metrics.widthPixels - view.width)
        var minY = 0
        var maxY = max(0, metrics.heightPixels - view.height)
        if (laneClamp && splitLayout != null && lane != HelperLane.FULL) {
            val range = splitLayout.laneYRange(lane, view.height, metrics.heightPixels)
            minY = range.first
            maxY = range.last
        }
        params.x = params.x.coerceIn(0, maxX)
        params.y = params.y.coerceIn(minY, maxY)
        runCatching { wm.updateViewLayout(view, params) }
    }

    private fun prefs() = context.getSharedPreferences("throwlet_overlay_${mode.name}_${lane.name}", Context.MODE_PRIVATE)
    private fun dp(value: Int): Int = (value * context.resources.displayMetrics.density).toInt()

    private fun floatingMenuBackground(): GradientDrawable = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = dp(7).toFloat()
        setColor(Color.argb(142, 0, 0, 0))
    }

    private fun badgeBackground(color: Int): GradientDrawable = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(Color.argb(235, Color.red(color), Color.green(color), Color.blue(color)))
        setStroke(dp(1), Color.argb(230, 255, 255, 255))
    }

    private fun showDetectionPlaceholder(refreshIcon: Boolean) {
        loadedSpriteName = null
        pokemonSprite.setImageResource(if (refreshIcon) R.drawable.ic_overlay_refresh else R.drawable.ic_overlay_help)
        pokemonSprite.setColorFilter(Color.WHITE)
        val padding = dp(10)
        pokemonSprite.setPadding(padding, padding, padding, padding)
        pokemonSprite.alpha = 0.95f
    }

    private fun setPokemonSprite(pokemonName: String) {
        if (loadedSpriteName == pokemonName && pokemonSprite.drawable != null) {
            pokemonSprite.setPadding(0, 0, 0, 0)
            return
        }
        loadedSpriteName = pokemonName
        PokemonSprites.bind(pokemonSprite, pokemonName, R.drawable.ic_overlay_refresh)
    }

    private fun actionButtonBackground(catchMode: Boolean): GradientDrawable = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        if (catchMode) {
            setColor(Color.argb(88, 0, 0, 0))
            setStroke(dp(1), Color.argb(120, 255, 255, 255))
        } else {
            setColor(Color.argb(198, 0, 0, 0))
            setStroke(dp(2), Color.argb(230, 255, 255, 255))
        }
    }

    companion object {
        private const val RAIL_WIDTH_DP = 44
        private const val BUTTON_SIZE_DP = 42
        private const val ACTION_BUTTON_DP = 58
        private const val CATCH_ACTION_BUTTON_DP = 84
    }
}

class DraggableOverlayLayout(
    context: Context,
    private val windowManager: WindowManager,
    private val paramsProvider: () -> WindowManager.LayoutParams?,
    private val touchSlop: Int,
    private val onDragFinished: () -> Unit,
) : LinearLayout(context) {
    private var downRawX = 0f
    private var downRawY = 0f
    private var startX = 0
    private var startY = 0
    private var dragging = false
    private var updatePosted = false

    override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downRawX = event.rawX
                downRawY = event.rawY
                val params = paramsProvider()
                startX = params?.x ?: 0
                startY = params?.y ?: 0
                dragging = false
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = event.rawX - downRawX
                val dy = event.rawY - downRawY
                if (dx * dx + dy * dy > touchSlop * touchSlop) {
                    dragging = true
                    scheduleWindowPositionUpdate(dx, dy)
                    return true
                }
            }
        }
        return false
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val params = paramsProvider() ?: return super.onTouchEvent(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downRawX = event.rawX
                downRawY = event.rawY
                startX = params.x
                startY = params.y
                dragging = false
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = event.rawX - downRawX
                val dy = event.rawY - downRawY
                if (!dragging && dx * dx + dy * dy > touchSlop * touchSlop) dragging = true
                if (dragging) {
                    scheduleWindowPositionUpdate(dx, dy)
                    return true
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (dragging) {
                    params.x = startX + (event.rawX - downRawX).toInt()
                    params.y = startY + (event.rawY - downRawY).toInt()
                    onDragFinished()
                    dragging = false
                    return true
                }
            }
        }
        return super.onTouchEvent(event)
    }

    /**
     * Move the overlay window (not view translation). Translation clips because the
     * WindowManager surface is only WRAP_CONTENT around the rail.
     */
    private fun scheduleWindowPositionUpdate(dx: Float, dy: Float) {
        val params = paramsProvider() ?: return
        params.x = startX + dx.toInt()
        params.y = startY + dy.toInt()
        if (updatePosted) return
        updatePosted = true
        postOnAnimation {
            updatePosted = false
            val latest = paramsProvider() ?: return@postOnAnimation
            runCatching { windowManager.updateViewLayout(this, latest) }
        }
    }
}

interface RailCallbacks {
    fun refresh(lane: HelperLane)
    fun saveLatest(lane: HelperLane)
    fun play(lane: HelperLane)
    fun stop(lane: HelperLane)
    fun crop(lane: HelperLane)
    fun cycleThrowScore(lane: HelperLane)
    fun openBerryMenu(lane: HelperLane, anchor: View)
    fun throwBerry(lane: HelperLane)
    fun toggleFastCatch(lane: HelperLane)
    fun toggleHoldToThrow(lane: HelperLane)
    fun toggleThrowSpeed(lane: HelperLane)
    fun openThrowSpeedDialog(lane: HelperLane)
    fun startHeldThrow(lane: HelperLane)
    fun releaseHeldThrow(lane: HelperLane)
    fun cancelHeldThrow(lane: HelperLane)
}
