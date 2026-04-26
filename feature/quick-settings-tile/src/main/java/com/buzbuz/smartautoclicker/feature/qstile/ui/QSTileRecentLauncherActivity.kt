/*
 * Copyright (C) 2026 Kevin Buzeau
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
package com.buzbuz.smartautoclicker.feature.qstile.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log

import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope

import com.buzbuz.smartautoclicker.core.display.recorder.showMediaProjectionWarning
import com.buzbuz.smartautoclicker.feature.qstile.R
import com.buzbuz.smartautoclicker.feature.qstile.domain.QSTileRecentScenario

import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint

import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@AndroidEntryPoint
class QSTileRecentLauncherActivity : AppCompatActivity() {

    companion object {
        fun getStartIntent(context: Context): Intent =
            Intent(context, QSTileRecentLauncherActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
    }

    private val viewModel: QSTileLauncherViewModel by viewModels()
    private lateinit var projectionActivityResult: ActivityResultLauncher<Intent>

    private var selectedScenario: QSTileRecentScenario? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_qstile_launcher)

        projectionActivityResult = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode != RESULT_OK) {
                finish()
                return@registerForActivityResult
            }

            val scenario = selectedScenario ?: return@registerForActivityResult finish()
            Log.i(TAG, "Media projection is running, start recent scenario")

            viewModel.startSmartScenario(result.resultCode, result.data!!, scenario.scenarioId)
            finish()
        }

        lifecycleScope.launch {
            showRecentScenarioDialog(viewModel.recentScenarios.filterNotNull().first())
        }
    }

    private fun showRecentScenarioDialog(scenarios: List<QSTileRecentScenario>) {
        if (scenarios.isEmpty()) {
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.dialog_recent_scenarios_title)
                .setMessage(R.string.dialog_recent_scenarios_empty)
                .setPositiveButton(android.R.string.ok) { _, _ -> finish() }
                .setOnCancelListener { finish() }
                .create()
                .show()
            return
        }

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.dialog_recent_scenarios_title)
            .setItems(scenarios.map { it.name }.toTypedArray()) { _, which ->
                startScenario(scenarios[which])
            }
            .setOnCancelListener { finish() }
            .create()
            .show()
    }

    private fun startScenario(scenario: QSTileRecentScenario) {
        selectedScenario = scenario

        viewModel.startPermissionFlowIfNeeded(
            activity = this,
            onMandatoryDenied = ::finish,
            onAllGranted = {
                Log.i(TAG, "All permissions are granted, start selected recent scenario")
                if (scenario.isSmart) showMediaProjectionWarning()
                else {
                    viewModel.startDumbScenario(scenario.scenarioId)
                    finish()
                }
            }
        )
    }

    private fun showMediaProjectionWarning() {
        projectionActivityResult.showMediaProjectionWarning(this, viewModel.isEntireScreenCaptureForced()) {
            finish()
        }
    }
}

private const val TAG = "QSTileRecentLauncherActivity"
