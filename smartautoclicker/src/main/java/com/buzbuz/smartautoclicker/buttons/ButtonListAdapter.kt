package com.buzbuz.smartautoclicker.buttons

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup

import androidx.recyclerview.widget.RecyclerView

import com.buzbuz.smartautoclicker.R
import com.google.android.material.button.MaterialButton
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.textview.MaterialTextView

class ButtonListAdapter(
    private val onVisibleChanged: (SavedOverlayButton, Boolean) -> Unit,
    private val onEditClicked: (SavedOverlayButton) -> Unit,
    private val onDeleteClicked: (SavedOverlayButton) -> Unit,
) : RecyclerView.Adapter<ButtonListAdapter.ViewHolder>() {

    private var items: List<SavedOverlayButton> = emptyList()

    fun submitList(newItems: List<SavedOverlayButton>) {
        items = newItems.filter { it.deletedAtMs == null }
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder =
        ViewHolder(
            LayoutInflater.from(parent.context)
                .inflate(R.layout.item_saved_overlay_button, parent, false)
        )

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position])
    }

    inner class ViewHolder(
        view: View,
    ) : RecyclerView.ViewHolder(view) {

        private val title = view.findViewById<MaterialTextView>(R.id.button_name)
        private val subtitle = view.findViewById<MaterialTextView>(R.id.button_subtitle)
        private val visibleSwitch = view.findViewById<SwitchMaterial>(R.id.switch_visible)
        private val deleteButton = view.findViewById<MaterialButton>(R.id.button_delete)

        fun bind(button: SavedOverlayButton) {
            title.text = button.iconGlyph
                ?.takeIf { it.isNotBlank() }
                ?.let { icon -> icon + "  " + button.displayName }
                ?: button.displayName
            subtitle.text = itemView.context.getString(R.string.item_saved_button_runs, button.scenarioNameSnapshot)
            visibleSwitch.setOnCheckedChangeListener(null)
            visibleSwitch.isChecked = button.isVisible
            visibleSwitch.isEnabled = button.enabled
            visibleSwitch.setOnCheckedChangeListener { _, isChecked -> onVisibleChanged(button, isChecked) }
            itemView.alpha = if (button.enabled) 1f else DISABLED_ALPHA
            itemView.setOnClickListener { onEditClicked(button) }
            deleteButton.setOnClickListener { onDeleteClicked(button) }
        }
    }
}

private const val DISABLED_ALPHA = 0.55f
