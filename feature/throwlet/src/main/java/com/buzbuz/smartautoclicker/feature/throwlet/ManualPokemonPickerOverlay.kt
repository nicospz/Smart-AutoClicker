/*
 * Copyright (C) 2026 Nicolas Espinoza
 */
package com.buzbuz.smartautoclicker.feature.throwlet

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.view.Gravity
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.roundToInt

internal class ManualPokemonPickerOverlay(
    private val context: Context,
) {
    private val windowManager = context.getSystemService(WindowManager::class.java)
    private var overlayView: LinearLayout? = null

    fun show(
        initialPokemonName: String?,
        onPokemonSelected: (String) -> Unit,
        onDismiss: () -> Unit,
    ) {
        hide()
        val catalog = PokemonCatalog.get(context)
        val input = AutoCompleteTextView(context).apply {
            setAdapter(ArrayAdapter(context, android.R.layout.simple_dropdown_item_1line, catalog.allNames()))
            threshold = 1
            hint = "Pokémon name"
            setSingleLine(true)
            setTextColor(Color.WHITE)
            setHintTextColor(Color.LTGRAY)
            initialPokemonName?.let { setText(it, false) }
        }
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(16), dp(12), dp(12))
            setBackgroundColor(Color.rgb(22, 24, 28))
            addView(TextView(context).apply {
                text = "Choose Pokémon for manual Throwlet catch"
                textSize = 14f
                setTextColor(Color.WHITE)
                setPadding(0, 0, 0, dp(12))
            })
            addView(input)
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                addView(Button(context).apply {
                    text = "Select"
                    setOnClickListener {
                        val match = catalog.resolveExactName(input.text?.toString().orEmpty())
                        if (match == null) {
                            context.toast("Select a valid Pokémon name")
                            return@setOnClickListener
                        }
                        hide()
                        onPokemonSelected(match.name)
                    }
                }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
                addView(Button(context).apply {
                    text = "Cancel"
                    setOnClickListener {
                        hide()
                        onDismiss()
                    }
                }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            })
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= 26) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.BOTTOM
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE
        }

        runCatching { windowManager?.addView(root, params) }
            .onSuccess {
                overlayView = root
                input.requestFocus()
                input.post {
                    context.getSystemService(InputMethodManager::class.java)
                        ?.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT)
                    input.showDropDown()
                }
            }
            .onFailure { error ->
                context.toast("Could not show Pokémon picker: " + error.message)
                onDismiss()
            }
    }

    fun hide() {
        overlayView?.let { runCatching { windowManager?.removeView(it) } }
        overlayView = null
    }

    private fun dp(value: Int): Int = (value * context.resources.displayMetrics.density).roundToInt()
}
