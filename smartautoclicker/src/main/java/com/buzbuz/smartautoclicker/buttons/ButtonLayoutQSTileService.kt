package com.buzbuz.smartautoclicker.buttons

import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.util.Log

import com.buzbuz.smartautoclicker.R
import com.buzbuz.smartautoclicker.core.base.extensions.startActivityAndCollapseCompat
import com.buzbuz.smartautoclicker.localservice.LocalServiceProvider

import dagger.hilt.android.AndroidEntryPoint

import javax.inject.Inject

@AndroidEntryPoint
class ButtonLayoutQSTileService : TileService() {

    @Inject lateinit var repository: SavedOverlayButtonRepository

    override fun onStartListening() {
        super.onStartListening()
        updateTile()
    }

    override fun onClick() {
        super.onClick()
        Log.d(TAG, "onClick")

        if (LocalServiceProvider.isServiceStarted()) {
            LocalServiceProvider.localServiceInstance?.stop()
            updateTile()
            return
        }

        if (!hasRunnableLayouts()) {
            updateTile()
            return
        }

        startActivityAndCollapseCompat(ButtonLayoutSacIntents.showButtonLayoutsActivity(this))
    }

    private fun updateTile() {
        val isRunning = LocalServiceProvider.isServiceStarted()
        val runnableLayouts = getRunnableLayouts()
        val isAvailable = runnableLayouts.isNotEmpty()

        qsTile?.apply {
            state = when {
                isRunning -> Tile.STATE_ACTIVE
                isAvailable -> Tile.STATE_INACTIVE
                else -> Tile.STATE_UNAVAILABLE
            }
            label = getString(
                if (isRunning) R.string.tile_label_stop_button_layout
                else R.string.tile_label_start_button_layout
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                subtitle = if (isAvailable) {
                    resources.getQuantityString(
                        R.plurals.tile_subtext_button_layout_count,
                        runnableLayouts.size,
                        runnableLayouts.size,
                    )
                } else {
                    getString(R.string.tile_subtext_no_button_layout)
                }
            }
            updateTile()
        }
    }

    private fun hasRunnableLayouts(): Boolean =
        getRunnableLayouts().isNotEmpty()

    private fun getRunnableLayouts(): List<SavedOverlayButtonSet> =
        repository.sets.value.filter { set ->
            set.deletedAtMs == null && hasRunnableButtons(set)
        }

    private fun hasRunnableButtons(layout: SavedOverlayButtonSet): Boolean =
        repository.buttons.value.any { button ->
            button.deletedAtMs == null &&
                    button.setSyncId == layout.syncId &&
                    button.isVisible &&
                    button.enabled
        }
}

private const val TAG = "ButtonLayoutQSTile"
