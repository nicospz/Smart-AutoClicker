/*
 * Copyright (C) 2026 Nicolas Espinoza
 */
package com.buzbuz.smartautoclicker.scenarios.list.adapter

import androidx.recyclerview.widget.RecyclerView
import com.buzbuz.smartautoclicker.R
import com.buzbuz.smartautoclicker.databinding.ItemScenarioCategoryHeaderBinding
import com.buzbuz.smartautoclicker.scenarios.list.model.ScenarioListUiState

internal class CategoryHeaderViewHolder(
    private val viewBinding: ItemScenarioCategoryHeaderBinding,
) : RecyclerView.ViewHolder(viewBinding.root) {

    fun onBind(item: ScenarioListUiState.Item.CategoryHeaderItem) {
        viewBinding.apply {
            textCategoryName.text = item.categoryName
            textScenarioCount.text = root.context.resources.getQuantityString(
                R.plurals.item_scenario_category_count,
                item.scenarioCount,
                item.scenarioCount,
            )
        }
    }
}
