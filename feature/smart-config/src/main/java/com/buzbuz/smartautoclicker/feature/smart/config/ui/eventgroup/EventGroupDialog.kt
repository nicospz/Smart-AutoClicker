/*
 * Copyright (C) 2026 Nicolas Espinoza
 */
package com.buzbuz.smartautoclicker.feature.smart.config.ui.eventgroup

import android.text.InputFilter
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.buzbuz.smartautoclicker.core.common.overlays.base.viewModels
import com.buzbuz.smartautoclicker.core.common.overlays.dialog.OverlayDialog
import com.buzbuz.smartautoclicker.core.domain.model.AND
import com.buzbuz.smartautoclicker.core.domain.model.ConditionOperator
import com.buzbuz.smartautoclicker.core.domain.model.OR
import com.buzbuz.smartautoclicker.core.ui.bindings.buttons.DualStateButtonTextConfig
import com.buzbuz.smartautoclicker.core.ui.bindings.dialogs.DialogNavigationButton
import com.buzbuz.smartautoclicker.core.ui.bindings.dialogs.setButtonEnabledState
import com.buzbuz.smartautoclicker.core.ui.bindings.dialogs.setButtonVisibility
import com.buzbuz.smartautoclicker.core.ui.bindings.fields.enableEasyOverwriteOnFocus
import com.buzbuz.smartautoclicker.core.ui.bindings.fields.setButtonConfig
import com.buzbuz.smartautoclicker.core.ui.bindings.fields.setChecked
import com.buzbuz.smartautoclicker.core.ui.bindings.fields.setDescription
import com.buzbuz.smartautoclicker.core.ui.bindings.fields.setError
import com.buzbuz.smartautoclicker.core.ui.bindings.fields.setLabel
import com.buzbuz.smartautoclicker.core.ui.bindings.fields.setOnCheckedListener
import com.buzbuz.smartautoclicker.core.ui.bindings.fields.setOnTextChangedListener
import com.buzbuz.smartautoclicker.core.ui.bindings.fields.setText
import com.buzbuz.smartautoclicker.core.ui.bindings.fields.setTitle
import com.buzbuz.smartautoclicker.core.ui.bindings.fields.setupDescriptions
import com.buzbuz.smartautoclicker.feature.smart.config.R
import com.buzbuz.smartautoclicker.feature.smart.config.databinding.DialogEventGroupBinding
import com.buzbuz.smartautoclicker.feature.smart.config.di.ScenarioConfigViewModelsEntryPoint
import com.buzbuz.smartautoclicker.feature.smart.config.ui.common.dialogs.showCloseWithoutSavingDialog
import com.buzbuz.smartautoclicker.feature.smart.config.ui.common.dialogs.showDeleteEventGroupDialog
import com.buzbuz.smartautoclicker.feature.smart.config.ui.common.model.condition.UiImageCondition
import com.buzbuz.smartautoclicker.feature.smart.config.ui.condition.image.brief.ImageConditionsBriefMenu
import com.buzbuz.smartautoclicker.feature.smart.config.ui.condition.trigger.TriggerConditionListDialog
import com.buzbuz.smartautoclicker.core.ui.bindings.dropdown.setItems
import com.buzbuz.smartautoclicker.core.ui.bindings.dropdown.setSelectedItem
import com.buzbuz.smartautoclicker.feature.smart.config.ui.event.EventChildrenCardsAdapter
import com.buzbuz.smartautoclicker.feature.smart.config.ui.event.EventGroupDropdownItem
import com.buzbuz.smartautoclicker.feature.smart.config.ui.event.setAdapter
import com.buzbuz.smartautoclicker.feature.smart.config.ui.event.setEmptyDescription
import com.buzbuz.smartautoclicker.feature.smart.config.ui.event.setItems
import com.buzbuz.smartautoclicker.feature.smart.config.ui.event.setOnClickListener
import com.buzbuz.smartautoclicker.feature.smart.config.ui.event.setTitle
import com.buzbuz.smartautoclicker.feature.smart.config.ui.event.EventImageConditionsAdapter
import com.google.android.material.bottomsheet.BottomSheetDialog
import kotlinx.coroutines.launch

class EventGroupDialog(
    private val onConfigComplete: () -> Unit,
    private val onDelete: () -> Unit,
    private val onDismiss: () -> Unit,
) : OverlayDialog(R.style.ScenarioConfigTheme) {

    private val viewModel: EventGroupDialogViewModel by viewModels(
        entryPoint = ScenarioConfigViewModelsEntryPoint::class.java,
        creator = { eventGroupDialogViewModel() },
    )

    private lateinit var viewBinding: DialogEventGroupBinding

    override fun onCreateView(): ViewGroup {
        viewBinding = DialogEventGroupBinding.inflate(LayoutInflater.from(context)).apply {
            setupNavBar()
            setupGroupProperties()
            setupConditionsCard()
        }
        return viewBinding.root
    }

    private fun DialogEventGroupBinding.setupNavBar() = layoutTopBar.apply {
        setButtonVisibility(DialogNavigationButton.SAVE, View.VISIBLE)
        setButtonVisibility(DialogNavigationButton.DELETE, View.VISIBLE)
        dialogTitle.setText(R.string.dialog_title_event_group)

        buttonDismiss.setDebouncedOnClickListener { back() }
        buttonSave.setDebouncedOnClickListener {
            onConfigComplete()
            super.back()
        }
        buttonDelete.setDebouncedOnClickListener { onDeleteButtonClicked() }
    }

    private fun DialogEventGroupBinding.setupGroupProperties() {
        fieldGroupName.apply {
            setLabel(R.string.generic_name)
            setOnTextChangedListener { viewModel.setGroupName(it.toString()) }
            textField.filters = arrayOf<InputFilter>(
                InputFilter.LengthFilter(context.resources.getInteger(R.integer.name_max_length)),
            )
        }
        hideSoftInputOnFocusLoss(fieldGroupName.textField)
        fieldGroupName.enableEasyOverwriteOnFocus()
    }

    private fun updateParentGroupDropdownVisibility(isVisible: Boolean) {
        viewBinding.fieldParentGroup.root.isVisible = isVisible
    }

    private fun updateParentGroupDropdownItems(items: List<EventGroupDropdownItem>) {
        if (items.size <= 1) return

        viewBinding.fieldParentGroup.setItems(
            label = context.getString(R.string.dropdown_parent_group_label),
            items = items,
            onItemSelected = viewModel::setParentGroup,
        )
    }

    private fun updateSelectedParentGroup(item: EventGroupDropdownItem) {
        viewBinding.fieldParentGroup.setSelectedItem(item)
    }

    private fun DialogEventGroupBinding.setupConditionsCard() {
        if (viewModel.isConfiguringScreenGroup()) {
            fieldTriggerConditionsSelector.root.visibility = View.GONE
            fieldImageConditionsSelector.apply {
                root.visibility = View.VISIBLE
                setTitle(
                    titleRes = R.string.label_event_group_gate,
                    emptyTitleRes = R.string.message_empty_screen_condition_list_title,
                )
                setEmptyDescription(R.string.message_empty_screen_condition_list_desc)
                setAdapter(
                    EventImageConditionsAdapter(
                        itemClickedListener = ::showImageConditionsBriefMenu,
                        bitmapProvider = viewModel::getConditionBitmap,
                    ),
                )
                setOnClickListener { debounceUserInteraction { showImageConditionsBriefMenu() } }
            }
        } else {
            fieldImageConditionsSelector.root.visibility = View.GONE
            fieldTriggerConditionsSelector.apply {
                root.visibility = View.VISIBLE
                setTitle(
                    titleRes = R.string.label_event_group_gate,
                    emptyTitleRes = R.string.message_empty_trigger_condition_list_title,
                )
                setEmptyDescription(R.string.message_empty_trigger_condition_list_desc)
                setAdapter(EventChildrenCardsAdapter { showTriggerConditionsDialog() })
                setOnClickListener { debounceUserInteraction { showTriggerConditionsDialog() } }
            }
        }

        fieldConditionsOperator.apply {
            setTitle(context.getString(R.string.field_operator_title))
            setupDescriptions(
                listOf(
                    context.getString(R.string.field_operator_desc_and),
                    context.getString(R.string.field_operator_desc_or),
                ),
            )
            setButtonConfig(
                DualStateButtonTextConfig(
                    textLeft = context.getString(R.string.condition_operator_and),
                    textRight = context.getString(R.string.condition_operator_or),
                    selectionRequired = true,
                    singleSelection = true,
                ),
            )
            setOnCheckedListener { checkedId ->
                viewModel.setConditionOperator(if (checkedId == 0) AND else OR)
            }
        }
    }

    override fun onDialogCreated(dialog: BottomSheetDialog) {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.CREATED) {
                launch { viewModel.isEditingEventGroup.collect(::onGroupEditingStateChanged) }
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.groupCanBeSaved.collect(::updateSaveButton) }
                launch { viewModel.groupName.collect(viewBinding.fieldGroupName::setText) }
                launch { viewModel.groupNameError.collect(viewBinding.fieldGroupName::setError) }
                launch { viewModel.isParentGroupDropdownVisible.collect(::updateParentGroupDropdownVisibility) }
                launch { viewModel.parentGroupDropdownItems.collect(::updateParentGroupDropdownItems) }
                launch { viewModel.selectedParentGroup.collect(::updateSelectedParentGroup) }
                launch { viewModel.conditionOperator.collect(::updateConditionOperator) }

                if (viewModel.isConfiguringScreenGroup()) {
                    launch { viewModel.imageConditions.collect(::updateImageConditionsField) }
                } else {
                    launch { viewModel.triggerConditionsDescription.collect(viewBinding.fieldTriggerConditionsSelector::setItems) }
                }
            }
        }
    }

    override fun onStop() {
        super.onStop()
    }

    override fun back() {
        if (viewModel.hasUnsavedModifications()) {
            context.showCloseWithoutSavingDialog {
                onDismiss()
                super.back()
            }
            return
        }

        onDismiss()
        super.back()
    }

    private fun onGroupEditingStateChanged(isEditing: Boolean) {
        if (!isEditing) back()
    }

    private fun updateSaveButton(enabled: Boolean) {
        viewBinding.layoutTopBar.setButtonEnabledState(DialogNavigationButton.SAVE, enabled)
    }

    private fun updateImageConditionsField(conditions: List<UiImageCondition>) {
        viewBinding.fieldImageConditionsSelector.setItems(conditions)
    }

    private fun updateConditionOperator(@ConditionOperator operator: Int) {
        viewBinding.fieldConditionsOperator.apply {
            val index = if (operator == AND) 0 else 1
            setChecked(index)
            setDescription(index)
        }
    }

    private fun onDeleteButtonClicked() {
        context.showDeleteEventGroupDialog {
            onDelete()
            super.back()
        }
    }

    private fun showImageConditionsBriefMenu(initialFocusedIndex: Int = 0) {
        overlayManager.navigateTo(
            context = context,
            newOverlay = ImageConditionsBriefMenu(initialFocusedIndex),
            hideCurrent = true,
        )
    }

    private fun showTriggerConditionsDialog() {
        overlayManager.navigateTo(
            context = context,
            newOverlay = TriggerConditionListDialog(),
        )
    }
}
