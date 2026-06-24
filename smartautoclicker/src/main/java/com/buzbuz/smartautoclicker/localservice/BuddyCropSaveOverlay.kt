/*
 * Copyright (C) 2026 Nicolas Espinoza
 */
package com.buzbuz.smartautoclicker.localservice

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Button
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import com.buzbuz.smartautoclicker.feature.throwlet.BuddyCropEntity
import com.buzbuz.smartautoclicker.feature.throwlet.BuddyCropStorage
import com.buzbuz.smartautoclicker.feature.throwlet.HelperLane
import com.buzbuz.smartautoclicker.feature.throwlet.PokemonCatalog
import com.buzbuz.smartautoclicker.feature.throwlet.RectI
import com.buzbuz.smartautoclicker.feature.throwlet.SacCropResult
import com.buzbuz.smartautoclicker.feature.throwlet.SizeI
import com.buzbuz.smartautoclicker.feature.throwlet.ThrowletBuddyCropSaver
import com.buzbuz.smartautoclicker.feature.throwlet.ThrowletRepository
import java.io.File
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal class BuddyCropSaveOverlay(
    private val context: Context,
    private val scope: CoroutineScope,
    private val throwletRepository: ThrowletRepository,
) : ThrowletBuddyCropSaver {

    private val windowManager = context.getSystemService(WindowManager::class.java)
    private var overlayView: View? = null

    override fun showSaveUi(
        lane: HelperLane,
        sacCrop: SacCropResult,
        dividerPx: Int,
        pokemonName: String?,
        onDismiss: () -> Unit,
    ) {
        hide()
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(16), dp(12), dp(12))
            setBackgroundColor(Color.rgb(22, 24, 28))
        }
        root.addView(TextView(context).apply {
            text = "Name this Pokémon buddy crop and save."
            textSize = 14f
            setTextColor(Color.WHITE)
            setPadding(0, 0, 0, dp(12))
        })

        val catalog = PokemonCatalog.get(context)
        val pokemonInput = AutoCompleteTextView(context).apply {
            setAdapter(ArrayAdapter(context, android.R.layout.simple_dropdown_item_1line, catalog.allNames()))
            threshold = 1
            hint = "Pokémon name"
            setSingleLine(true)
            setTextColor(Color.WHITE)
            setHintTextColor(Color.LTGRAY)
            pokemonName?.let { setText(it, false) }
        }
        root.addView(pokemonInput)

        val thresholdSpinner = Spinner(context).apply {
            adapter = ArrayAdapter(context, android.R.layout.simple_spinner_dropdown_item, listOf(75, 80, 85, 90, 93))
            setSelection(2)
        }
        root.addView(thresholdSpinner)

        val buttons = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        buttons.addView(Button(context).apply {
            text = "Save"
            setOnClickListener {
                saveCrop(
                    lane = lane,
                    sacCrop = sacCrop,
                    dividerPx = dividerPx,
                    pokemonInput = pokemonInput,
                    thresholdSpinner = thresholdSpinner,
                    onDismiss = onDismiss,
                )
            }
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        buttons.addView(Button(context).apply {
            text = "Close"
            setOnClickListener {
                cleanupTempCrop(sacCrop)
                hide()
                onDismiss()
            }
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        root.addView(buttons)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= 26) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else WindowManager.LayoutParams.TYPE_PHONE,
            0,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.BOTTOM
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE or
                WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        }

        runCatching { windowManager?.addView(root, params) }
            .onSuccess {
                overlayView = root
                pokemonInput.requestFocus()
                pokemonInput.post {
                    context.getSystemService(android.view.inputmethod.InputMethodManager::class.java)
                        ?.showSoftInput(pokemonInput, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
                    pokemonInput.showDropDown()
                }
            }
            .onFailure {
                cleanupTempCrop(sacCrop)
                onDismiss()
            }
    }

    fun hide() {
        overlayView?.let { runCatching { windowManager?.removeView(it) } }
        overlayView = null
    }

    private fun saveCrop(
        lane: HelperLane,
        sacCrop: SacCropResult,
        dividerPx: Int,
        pokemonInput: AutoCompleteTextView,
        thresholdSpinner: Spinner,
        onDismiss: () -> Unit,
    ) {
        val match = PokemonCatalog.get(context).resolveExactName(pokemonInput.text?.toString().orEmpty())
        if (match == null) return
        val threshold = thresholdSpinner.selectedItem as Int
        val now = System.currentTimeMillis()
        scope.launch {
            val frameSize = SizeI(sacCrop.frameWidth, sacCrop.frameHeight)
            val cropRect = RectI(sacCrop.cropLeft, sacCrop.cropTop, sacCrop.cropRight, sacCrop.cropBottom)
            val divider = dividerPx.takeIf { it > 0 } ?: frameSize.height / 2
            val (storageLane, storedRect) = BuddyCropStorage.normalizeForStorage(lane, cropRect, divider)
            val imagePath = withContext(Dispatchers.IO) {
                val dir = File(context.filesDir, "needles/buddy").also { it.mkdirs() }
                val safeKey = match.key.lowercase().replace(Regex("[^a-z0-9_-]"), "_")
                val file = File(dir, "$safeKey.png")
                File(sacCrop.cropBitmapPath).copyTo(file, overwrite = true)
                file.absolutePath
            }
            val existing = throwletRepository.findBuddyCrop(match.key)
            val entity = BuddyCropEntity(
                id = existing?.id ?: 0,
                pokemonKey = match.key,
                pokemonName = match.name,
                imagePath = imagePath,
                sourceLane = storageLane,
                sourceWidth = frameSize.width,
                sourceHeight = frameSize.height,
                cropLeft = storedRect.left,
                cropTop = storedRect.top,
                cropRight = storedRect.right,
                cropBottom = storedRect.bottom,
                thresholdPercent = threshold,
                enabled = true,
                createdAtMs = existing?.createdAtMs ?: now,
                updatedAtMs = now,
            )
            val saved = throwletRepository.saveBuddyCrop(entity)
            if (saved != null) {
                throwletRepository.pushBuddyCrop(saved)
            }
            cleanupTempCrop(sacCrop)
            hide()
            onDismiss()
        }
    }

    private fun cleanupTempCrop(sacCrop: SacCropResult) {
        runCatching { File(sacCrop.cropBitmapPath).delete() }
    }

    private fun dp(value: Int): Int = (value * context.resources.displayMetrics.density).roundToInt()
}
