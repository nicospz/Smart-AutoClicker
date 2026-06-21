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
package com.buzbuz.smartautoclicker.feature.smart.config.ui.scenario.imageevents

import android.content.Context
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.ItemTouchHelper
import com.buzbuz.smartautoclicker.core.common.overlays.dialog.implementation.navbar.NavBarDialogContent
import com.buzbuz.smartautoclicker.core.common.overlays.dialog.implementation.navbar.viewModels
import com.buzbuz.smartautoclicker.core.domain.model.event.EventGroup
import com.buzbuz.smartautoclicker.core.domain.model.event.ImageEvent
import com.buzbuz.smartautoclicker.core.ui.bindings.lists.setEmptyText
import com.buzbuz.smartautoclicker.core.ui.bindings.lists.updateState
import com.buzbuz.smartautoclicker.core.ui.databinding.IncludeLoadableListBinding
import com.buzbuz.smartautoclicker.feature.smart.config.R
import com.buzbuz.smartautoclicker.feature.smart.config.di.ScenarioConfigViewModelsEntryPoint
import com.buzbuz.smartautoclicker.feature.smart.config.ui.common.model.event.UiImageEvent
import com.buzbuz.smartautoclicker.feature.smart.config.ui.event.EventDialog
import com.buzbuz.smartautoclicker.feature.smart.config.ui.event.copy.EventCopyDialog
import com.buzbuz.smartautoclicker.feature.smart.config.ui.eventgroup.EventGroupDialog
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.launch

class ImageEventListContent(appContext: Context) : NavBarDialogContent(appContext) {

    private val viewModel: ImageEventListViewModel by viewModels(
        entryPoint = ScenarioConfigViewModelsEntryPoint::class.java,
        creator = { imageEventListViewModel() },
    )

    private lateinit var viewBinding: IncludeLoadableListBinding
    private lateinit var listAdapter: ImageEventGroupedListAdapter
    private val itemTouchHelper = ItemTouchHelper(
        ImageEventGroupedReorderTouchHelper { listAdapter },
    )

    override fun floatingActionButtonsAreAvailable(): Boolean = true

    override fun onCreateView(container: ViewGroup): ViewGroup {
        listAdapter = ImageEventGroupedListAdapter(
            onEventClicked = ::onEventItemClicked,
            onEventIgnoreChanged = viewModel::setEventIgnored,
            onGroupClicked = ::onGroupItemClicked,
            onGroupExpandClicked = viewModel::toggleGroupExpanded,
            onAddGroupClicked = ::onAddGroupClicked,
            onReorderFinished = viewModel::updateListOrder,
            onEventViewBound = ::onEventItemBound,
        )

        val rootLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
        }
        rootLayout.addView(createSearchField())

        viewBinding = IncludeLoadableListBinding.inflate(LayoutInflater.from(context), rootLayout, false).apply {
            root.layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f,
            )
            setEmptyText(
                id = R.string.message_empty_screen_event_title,
                secondaryId = R.string.message_empty_screen_event_desc,
            )
            list.apply {
                addItemDecoration(DividerItemDecoration(context, DividerItemDecoration.VERTICAL))
                itemTouchHelper.attachToRecyclerView(this)
                adapter = listAdapter
            }
        }
        rootLayout.addView(viewBinding.root)

        return rootLayout
    }

    override fun onViewCreated() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.copyButtonIsVisible.collect(::updateCopyButtonVisibility) }
                launch { viewModel.listItems.collect(::updateEventList) }
            }
        }
    }

    override fun onStop() {
        super.onStop()
        viewModel.stopViewMonitoring()
    }

    override fun onPrimaryFloatingActionButtonClicked() {
        debounceUserInteraction {
            showEventConfigDialog(viewModel.createNewEvent(context))
        }
    }

    override fun onSecondaryFloatingActionButtonClicked() {
        debounceUserInteraction {
            showEventCopyDialog()
        }
    }

    private fun onEventItemClicked(event: ImageEvent) {
        debounceUserInteraction {
            showEventConfigDialog(event)
        }
    }

    private fun onGroupItemClicked(group: EventGroup) {
        debounceUserInteraction {
            showGroupConfigDialog(group)
        }
    }

    private fun onAddGroupClicked() {
        debounceUserInteraction {
            showGroupConfigDialog(viewModel.createNewGroup(context))
        }
    }

    private fun createSearchField(): TextInputLayout {
        val horizontalMargin = context.resources.getDimensionPixelSize(
            com.buzbuz.smartautoclicker.core.ui.R.dimen.margin_horizontal_default,
        )
        val verticalMargin = context.resources.getDimensionPixelSize(
            com.buzbuz.smartautoclicker.core.ui.R.dimen.margin_vertical_small,
        )

        return TextInputLayout(context).apply {
            hint = context.getString(R.string.search_view_hint_event_copy)
            endIconMode = TextInputLayout.END_ICON_CLEAR_TEXT
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply {
                setMargins(horizontalMargin, verticalMargin, horizontalMargin, verticalMargin)
            }

            addView(
                TextInputEditText(context).apply {
                    inputType = InputType.TYPE_CLASS_TEXT
                    setSingleLine(true)
                    doAfterTextChanged { viewModel.updateSearchQuery(it?.toString().orEmpty()) }
                },
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ),
            )
        }
    }

    private fun onEventItemBound(index: Int, eventItemView: View?) {
        val firstEventIndex = listAdapter.currentList.indexOfFirst { it is ImageEventListItem.EventItem }
        if (index != firstEventIndex) return

        if (eventItemView != null) viewModel.monitorFirstEventView(eventItemView)
        else viewModel.stopViewMonitoring()
    }

    private fun updateEventList(newItems: List<ImageEventListItem>?) {
        viewBinding.updateState(newItems)
        listAdapter.submitList(newItems)
    }

    private fun updateCopyButtonVisibility(isVisible: Boolean) {
        dialogController.floatingActionButtons.secondary.apply {
            if (isVisible) show() else hide()
        }
    }

    private fun showEventCopyDialog() {
        dialogController.overlayManager.navigateTo(
            context = context,
            newOverlay = EventCopyDialog(
                requestTriggerEvents = false,
                onEventSelected = { event ->
                    showEventConfigDialog(viewModel.createNewEvent(context, event as ImageEvent))
                },
            ),
        )
    }

    private fun showEventConfigDialog(item: ImageEvent) {
        viewModel.startEventEdition(item)

        dialogController.overlayManager.navigateTo(
            context = context,
            newOverlay = EventDialog(
                onConfigComplete = viewModel::saveEventEdition,
                onDelete = viewModel::deleteEditedEvent,
                onDismiss = viewModel::dismissEditedEvent,
            ),
            hideCurrent = true,
        )
    }

    private fun showGroupConfigDialog(group: EventGroup) {
        viewModel.startGroupEdition(group)

        dialogController.overlayManager.navigateTo(
            context = context,
            newOverlay = EventGroupDialog(
                onConfigComplete = viewModel::saveGroupEdition,
                onDelete = viewModel::deleteEditedGroup,
                onDismiss = viewModel::dismissEditedGroup,
            ),
            hideCurrent = true,
        )
    }
}
