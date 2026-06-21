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
package com.buzbuz.smartautoclicker.feature.smart.config.ui.scenario.config

import android.content.Context
import android.text.InputFilter
import android.text.InputFilter.LengthFilter
import android.text.InputType
import android.view.LayoutInflater
import android.view.ViewGroup

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle

import com.buzbuz.smartautoclicker.core.common.overlays.dialog.implementation.navbar.NavBarDialogContent
import com.buzbuz.smartautoclicker.core.common.overlays.dialog.implementation.navbar.viewModels
import com.buzbuz.smartautoclicker.core.ui.bindings.fields.setChecked
import com.buzbuz.smartautoclicker.core.ui.bindings.fields.setDescription
import com.buzbuz.smartautoclicker.core.ui.bindings.fields.setOnClickListener
import com.buzbuz.smartautoclicker.core.ui.bindings.fields.setTitle
import com.buzbuz.smartautoclicker.core.ui.bindings.fields.setupDescriptions
import com.buzbuz.smartautoclicker.core.ui.bindings.fields.setError
import com.buzbuz.smartautoclicker.core.ui.bindings.fields.enableEasyOverwriteOnFocus
import com.buzbuz.smartautoclicker.core.ui.bindings.fields.setLabel
import com.buzbuz.smartautoclicker.core.ui.bindings.fields.setOnTextChangedListener
import com.buzbuz.smartautoclicker.core.ui.bindings.fields.setText
import com.buzbuz.smartautoclicker.core.ui.utils.MinMaxInputFilter
import com.buzbuz.smartautoclicker.feature.smart.config.R
import com.buzbuz.smartautoclicker.feature.smart.config.databinding.ContentScenarioConfigBinding
import com.buzbuz.smartautoclicker.feature.smart.config.di.ScenarioConfigViewModelsEntryPoint

import kotlinx.coroutines.launch
import kotlin.math.roundToInt

class ScenarioConfigContent(appContext: Context) : NavBarDialogContent(appContext) {

    /** View model for this content. */
    private val viewModel: ScenarioConfigViewModel by viewModels(
        entryPoint = ScenarioConfigViewModelsEntryPoint::class.java,
        creator = { scenarioConfigViewModel() },
    )

    private lateinit var viewBinding: ContentScenarioConfigBinding

    override fun onCreateView(container: ViewGroup): ViewGroup {
        viewBinding = ContentScenarioConfigBinding.inflate(LayoutInflater.from(context), container, false).apply {
            fieldScenarioName.apply {
                setLabel(R.string.input_field_label_scenario_name)
                setOnTextChangedListener { viewModel.setScenarioName(it.toString()) }
                textField.filters = arrayOf<InputFilter>(
                    LengthFilter(context.resources.getInteger(R.integer.name_max_length))
                )
            }
            dialogController.hideSoftInputOnFocusLoss(fieldScenarioName.textField)
            fieldScenarioName.enableEasyOverwriteOnFocus()

            fieldScenarioCategory.apply {
                setLabel(com.buzbuz.smartautoclicker.core.ui.R.string.input_field_label_scenario_category)
                setOnTextChangedListener { viewModel.setScenarioCategory(it.toString()) }
                textField.filters = arrayOf<InputFilter>(
                    LengthFilter(context.resources.getInteger(R.integer.name_max_length))
                )
            }
            dialogController.hideSoftInputOnFocusLoss(fieldScenarioCategory.textField)
            fieldScenarioCategory.enableEasyOverwriteOnFocus()

            fieldAntiDetection.apply {
                setTitle(context.resources.getString(R.string.input_field_label_anti_detection))
                setupDescriptions(
                    listOf(
                        context.getString(R.string.dropdown_helper_text_anti_detection_disabled),
                        context.getString(R.string.dropdown_helper_text_anti_detection_enabled),
                    )
                )
                setOnClickListener(viewModel::toggleRandomization)
            }

            fieldKeepScreenOn.apply {
                setTitle(context.resources.getString(R.string.field_scenario_keep_screen_on_title))
                setupDescriptions(
                    listOf(
                        context.getString(R.string.field_scenario_keep_screen_on_disabled),
                        context.getString(R.string.field_scenario_keep_screen_on_enabled),
                    )
                )
                setOnClickListener(viewModel::toggleKeepScreenOn)
            }

            fieldAccessibilityScreenshot.apply {
                setTitle(context.resources.getString(R.string.field_scenario_accessibility_screenshot_title))
                setupDescriptions(
                    listOf(
                        context.getString(R.string.field_scenario_accessibility_screenshot_disabled),
                        context.getString(R.string.field_scenario_accessibility_screenshot_enabled),
                    )
                )
                setOnClickListener(viewModel::toggleScreenCaptureMode)
            }

            fieldAutoStart.apply {
                setTitle(context.resources.getString(R.string.field_scenario_auto_start_title))
                setupDescriptions(
                    listOf(
                        context.getString(R.string.field_scenario_auto_start_disabled),
                        context.getString(R.string.field_scenario_auto_start_enabled),
                    )
                )
                setOnClickListener(viewModel::toggleAutoStart)
            }

            fieldAutoStartDelay.apply {
                setLabel(R.string.input_field_label_auto_start_delay)
                textField.filters = arrayOf(MinMaxInputFilter(0))
                setOnTextChangedListener {
                    viewModel.setAutoStartDelay(if (it.isNotEmpty()) it.toString().toLong() else 0L)
                }
            }
            dialogController.hideSoftInputOnFocusLoss(fieldAutoStartDelay.textField)

            textSpeed.setOnClickListener { viewModel.decreaseDetectionQuality() }
            textPrecision.setOnClickListener { viewModel.increaseDetectionQuality() }
            seekbarResolution.addOnChangeListener { _, value, fromUser ->
                if (fromUser) viewModel.setDetectionQuality(value.roundToInt())
            }
        }

        return viewBinding.root
    }

    override fun onViewCreated() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.scenarioName.collect(::updateScenarioName) }
                launch { viewModel.scenarioNameError.collect(viewBinding.fieldScenarioName::setError) }
                launch { viewModel.scenarioCategory.collect(::updateScenarioCategory) }
                launch { viewModel.randomization.collect(::updateRandomization) }
                launch { viewModel.keepScreenOn.collect(::updateKeepScreenOn) }
                launch { viewModel.useAccessibilityScreenshot.collect(::updateAccessibilityScreenshot) }
                launch { viewModel.autoStart.collect(::updateAutoStart) }
                launch { viewModel.autoStartDelay.collect(::updateAutoStartDelay) }
                launch { viewModel.autoStartDelayError.collect(viewBinding.fieldAutoStartDelay::setError) }
                launch { viewModel.detectionQuality.collect(::updateQuality) }
            }
        }
    }

    private fun updateScenarioName(name: String?) {
        viewBinding.fieldScenarioName.setText(name)
    }

    private fun updateScenarioCategory(category: String?) {
        viewBinding.fieldScenarioCategory.setText(category)
    }

    private fun updateRandomization(isEnabled: Boolean) {
        viewBinding.fieldAntiDetection.apply {
            setChecked(isEnabled)
            setDescription(if (isEnabled) 1 else 0)
        }
    }

    private fun updateKeepScreenOn(isEnabled: Boolean) {
        viewBinding.fieldKeepScreenOn.apply {
            setChecked(isEnabled)
            setDescription(if (isEnabled) 1 else 0)
        }
    }

    private fun updateAccessibilityScreenshot(isEnabled: Boolean) {
        viewBinding.fieldAccessibilityScreenshot.apply {
            setChecked(isEnabled)
            setDescription(if (isEnabled) 1 else 0)
        }
    }

    private fun updateAutoStart(isEnabled: Boolean) {
        viewBinding.fieldAutoStart.apply {
            setChecked(isEnabled)
            setDescription(if (isEnabled) 1 else 0)
        }

        viewBinding.fieldAutoStartDelay.root.apply {
            this.isEnabled = isEnabled
            alpha = if (isEnabled) 1f else 0.5f
        }
    }

    private fun updateAutoStartDelay(delayMs: String) {
        viewBinding.fieldAutoStartDelay.setText(delayMs, InputType.TYPE_CLASS_NUMBER)
    }

    private fun updateQuality(quality: UiDetectionQuality) {
        viewBinding.apply {
            textQualityValue.text = quality.displayText

            val isNotInitialized = seekbarResolution.value == 0f
            seekbarResolution.value = quality.qualityValue

            if (isNotInitialized) {
                seekbarResolution.valueFrom = quality.min
                seekbarResolution.valueTo = quality.max
            }
        }
    }
}
