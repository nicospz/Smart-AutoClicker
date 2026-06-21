package com.buzbuz.smartautoclicker.localservice

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class ThrowletOverlayBroadcastReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action
        Log.i(TAG, "onReceive action=$action localService=${LocalServiceProvider.localServiceInstance != null}")
        val service = LocalServiceProvider.localServiceInstance ?: run {
            Log.w(TAG, "onReceive ignored: LocalService not available for action=$action")
            return
        }
        when (action) {
            ACTION_TOGGLE_THROWLET_OVERLAY -> service.toggleThrowletOverlay()
            ACTION_SHOW_THROWLET_OVERLAY -> service.showThrowletOverlay()
            ACTION_HIDE_THROWLET_OVERLAY -> service.hideThrowletOverlay()
            else -> Log.w(TAG, "onReceive ignored: unknown action=$action")
        }
    }

    companion object {
        private const val TAG = "SacThrowletCatch"
        const val ACTION_TOGGLE_THROWLET_OVERLAY = "com.buzbuz.smartautoclicker.action.TOGGLE_THROWLET_OVERLAY"
        const val ACTION_SHOW_THROWLET_OVERLAY = "com.buzbuz.smartautoclicker.action.SHOW_THROWLET_OVERLAY"
        const val ACTION_HIDE_THROWLET_OVERLAY = "com.buzbuz.smartautoclicker.action.HIDE_THROWLET_OVERLAY"
    }
}
