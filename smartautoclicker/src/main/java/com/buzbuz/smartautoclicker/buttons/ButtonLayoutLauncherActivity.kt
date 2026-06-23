package com.buzbuz.smartautoclicker.buttons

import android.os.Bundle
import android.widget.Toast

import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity

import com.buzbuz.smartautoclicker.R
import com.google.android.material.dialog.MaterialAlertDialogBuilder

import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class ButtonLayoutLauncherActivity : AppCompatActivity() {

    private val viewModel: ButtonLayoutLauncherViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        overridePendingTransition(0, 0)

        if (intent?.action != ButtonLayoutSacIntents.ACTION_START_BUTTON_LAYOUT) {
            finish()
            return
        }

        val layouts = viewModel.getRunnableLayouts()
        if (layouts.isEmpty()) {
            Toast.makeText(this, R.string.toast_no_visible_saved_buttons, Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        showLayoutDialog(layouts)
    }

    private fun showLayoutDialog(layouts: List<SavedOverlayButtonSet>) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.dialog_title_choose_button_layout)
            .setItems(layouts.map { it.name }.toTypedArray()) { _, which ->
                startLayout(layouts[which])
            }
            .setNegativeButton(android.R.string.cancel) { _, _ -> finish() }
            .setOnCancelListener { finish() }
            .show()
    }

    private fun startLayout(layout: SavedOverlayButtonSet) {
        viewModel.setActiveLayout(layout)
        viewModel.startPermissionFlowIfNeeded(
            activity = this,
            onMandatoryDenied = ::finish,
            onAllGranted = {
                viewModel.startTroubleshootingFlowIfNeeded(this) {
                    if (!viewModel.startButtonOverlay()) {
                        Toast.makeText(this, R.string.toast_denied_foreground_permission, Toast.LENGTH_SHORT).show()
                    }
                    finish()
                }
            },
        )
    }
}
