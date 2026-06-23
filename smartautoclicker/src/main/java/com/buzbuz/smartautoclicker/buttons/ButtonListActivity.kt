package com.buzbuz.smartautoclicker.buttons

import android.os.Bundle
import android.text.InputType
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.EditText
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

import com.buzbuz.smartautoclicker.R
import com.buzbuz.smartautoclicker.core.dumb.domain.model.DumbScenario
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.dialog.MaterialAlertDialogBuilder

import dagger.hilt.android.AndroidEntryPoint

import kotlinx.coroutines.launch

@AndroidEntryPoint
class ButtonListActivity : AppCompatActivity() {

    private val viewModel: ButtonListViewModel by viewModels()
    private lateinit var adapter: ButtonListAdapter
    private lateinit var list: RecyclerView
    private lateinit var emptyLayout: View
    private lateinit var emptyText: TextView
    private lateinit var activeLayoutTitle: TextView
    private lateinit var activeLayoutSubtitle: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_buttons)

        val toolbar = findViewById<MaterialToolbar>(R.id.top_app_bar)
        setSupportActionBar(toolbar)
        toolbar.setNavigationOnClickListener { finish() }

        adapter = ButtonListAdapter(
            onVisibleChanged = { button, visible -> viewModel.setVisible(button.id, visible) },
            onEditClicked = ::showEditDialog,
            onDeleteClicked = ::showDeleteDialog,
        )
        list = findViewById<RecyclerView>(R.id.list).apply {
            layoutManager = LinearLayoutManager(this@ButtonListActivity)
            adapter = this@ButtonListActivity.adapter
        }
        emptyLayout = findViewById(R.id.layout_empty)
        emptyText = findViewById(R.id.empty_text_title)
        activeLayoutTitle = findViewById(R.id.text_active_layout)
        activeLayoutSubtitle = findViewById(R.id.text_layout_summary)
        findViewById<View>(R.id.layout_set_selector).setOnClickListener { showSetSelectorDialog() }
        findViewById<View>(R.id.add).setOnClickListener { showAddDialog() }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.uiState.collect(::renderState)
                }
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_buttons_activity, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean =
        when (item.itemId) {
            R.id.action_show_buttons -> {
                startOverlayWhenReady()
                true
            }
            R.id.action_new_layout -> {
                showCreateSetDialog()
                true
            }
            R.id.action_rename_layout -> {
                activeSetOrToast()?.let(::showRenameSetDialog)
                true
            }
            R.id.action_duplicate_layout -> {
                activeSetOrToast()?.let(::showDuplicateSetDialog)
                true
            }
            R.id.action_delete_layout -> {
                activeSetOrToast()?.let(::showDeleteSetDialog)
                true
            }
            else -> super.onOptionsItemSelected(item)
        }

    private fun renderState(state: ButtonLayoutUiState) {
        adapter.submitList(state.activeButtons)

        activeLayoutTitle.text = state.activeSet
            ?.let { getString(R.string.label_active_button_layout, it.name) }
            ?: getString(R.string.label_no_active_button_layout)
        activeLayoutSubtitle.text = when {
            state.sets.isEmpty() -> getString(R.string.label_button_layout_selector_empty)
            state.activeSet == null -> getString(R.string.label_button_layout_selector_choose)
            else -> resources.getQuantityString(
                R.plurals.label_button_layout_button_count,
                state.activeButtons.size,
                state.activeButtons.size,
            )
        }

        val emptyMessage = when {
            state.sets.isEmpty() -> R.string.message_empty_button_layouts
            state.activeSet == null -> R.string.message_no_active_button_layout
            else -> R.string.message_empty_active_button_layout
        }
        emptyText.setText(emptyMessage)
        emptyLayout.visibility = if (state.activeButtons.isEmpty()) View.VISIBLE else View.GONE
        list.visibility = if (state.activeButtons.isEmpty()) View.GONE else View.VISIBLE
    }

    private fun showSetSelectorDialog() {
        val state = viewModel.uiState.value
        if (state.sets.isEmpty()) {
            showCreateSetDialog()
            return
        }

        val labels = listOf(getString(R.string.item_no_active_button_layout)) + state.sets.map { it.name }
        val checkedIndex = state.activeSet?.let { active ->
            state.sets.indexOfFirst { it.syncId == active.syncId }.takeIf { it >= 0 }?.plus(1)
        } ?: 0

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.dialog_title_choose_button_layout)
            .setSingleChoiceItems(labels.toTypedArray(), checkedIndex) { dialog, which ->
                viewModel.setActiveSet(if (which == 0) null else state.sets[which - 1].syncId)
                dialog.dismiss()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showAddDialog() {
        val scenarios = viewModel.dumbScenarios.value.filter { it.isValid() }
        if (scenarios.isEmpty()) {
            Toast.makeText(this, R.string.toast_no_dumb_scenario_for_button, Toast.LENGTH_SHORT).show()
            return
        }

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.dialog_title_add_saved_button)
            .setItems(scenarios.map(DumbScenario::name).toTypedArray()) { _, which ->
                showAddToLayoutDialog(scenarios[which])
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showAddToLayoutDialog(scenario: DumbScenario) {
        val sets = viewModel.uiState.value.sets
        if (sets.isEmpty()) {
            showCreateSetDialog { set -> viewModel.addButton(set.syncId, scenario) }
            return
        }

        val labels = sets.map { it.name } + getString(R.string.item_create_new_button_layout)
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.dialog_title_choose_button_layout)
            .setItems(labels.toTypedArray()) { _, which ->
                if (which == sets.size) {
                    showCreateSetDialog { set -> viewModel.addButton(set.syncId, scenario) }
                } else {
                    viewModel.addButton(sets[which].syncId, scenario)
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showCreateSetDialog(onCreated: (SavedOverlayButtonSet) -> Unit = {}) {
        showSetNameDialog(
            titleRes = R.string.dialog_title_create_button_layout,
            initialName = viewModel.nextLayoutName(),
        ) { name ->
            onCreated(viewModel.createSet(name))
        }
    }

    private fun showRenameSetDialog(set: SavedOverlayButtonSet) {
        showSetNameDialog(
            titleRes = R.string.dialog_title_rename_button_layout,
            initialName = set.name,
        ) { name ->
            viewModel.renameSet(set.syncId, name)
        }
    }

    private fun showDuplicateSetDialog(set: SavedOverlayButtonSet) {
        showSetNameDialog(
            titleRes = R.string.dialog_title_duplicate_button_layout,
            initialName = viewModel.duplicateLayoutName(set),
        ) { name ->
            viewModel.duplicateSet(set.syncId, name)
        }
    }

    private fun showDeleteSetDialog(set: SavedOverlayButtonSet) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.dialog_title_delete_button_layout)
            .setMessage(getString(R.string.message_delete_button_layout, set.name))
            .setPositiveButton(android.R.string.ok) { _, _ -> viewModel.deleteSet(set.syncId) }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showSetNameDialog(
        titleRes: Int,
        initialName: String,
        onConfirmed: (String) -> Unit,
    ) {
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_TEXT
            setText(initialName)
            selectAll()
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(titleRes)
            .setView(input)
            .setPositiveButton(android.R.string.ok) { _, _ -> onConfirmed(input.text?.toString().orEmpty()) }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showEditDialog(button: SavedOverlayButton) {
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_TEXT
            setText(button.labelOverride ?: button.scenarioNameSnapshot)
            selectAll()
        }
        val iconButtons = mutableListOf<RadioButton>()
        var selectedIconGlyph: String? = button.iconGlyph
        val iconPicker = createIconPicker(
            currentIconGlyph = button.iconGlyph,
            iconButtons = iconButtons,
            onSelected = { selectedIconGlyph = it },
        )
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(8), dp(24), dp(0))
            addView(input)
            addView(TextView(this@ButtonListActivity).apply {
                text = getString(R.string.button_icon_choice_title)
                setPadding(0, dp(16), 0, dp(4))
            })
            addView(iconPicker)
        }
        val scrollContent = ScrollView(this).apply {
            addView(content)
        }

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.dialog_title_edit_saved_button)
            .setView(scrollContent)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                viewModel.updateAppearance(button.id, input.text?.toString(), selectedIconGlyph)
            }
            .setNeutralButton(
                if (button.enabled) R.string.button_text_disable else R.string.button_text_enable
            ) { _, _ ->
                viewModel.setEnabled(button.id, !button.enabled)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun createIconPicker(
        currentIconGlyph: String?,
        iconButtons: MutableList<RadioButton>,
        onSelected: (String?) -> Unit,
    ): View =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addIconChoice(
                parent = this,
                label = getString(R.string.button_icon_choice_text),
                iconGlyph = null,
                checked = currentIconGlyph == null,
                iconButtons = iconButtons,
                onSelected = onSelected,
            )
            ICON_PACKS.forEach { pack ->
                addView(TextView(this@ButtonListActivity).apply {
                    text = pack.name
                    textSize = 13f
                    alpha = 0.72f
                    setPadding(0, dp(12), 0, dp(4))
                })
                addView(GridLayout(this@ButtonListActivity).apply {
                    columnCount = 2
                    pack.icons.forEach { choice ->
                        addIconChoice(
                            parent = this,
                            label = "${choice.glyph}  ${choice.name}",
                            iconGlyph = choice.glyph,
                            checked = currentIconGlyph == choice.glyph,
                            iconButtons = iconButtons,
                            onSelected = onSelected,
                        )
                    }
                })
            }
            val isKnownIcon = currentIconGlyph == null || ICON_PACKS.any { pack ->
                pack.icons.any { it.glyph == currentIconGlyph }
            }
            val unknownIcon = currentIconGlyph?.takeUnless { isKnownIcon }
            if (unknownIcon != null) {
                addView(TextView(this@ButtonListActivity).apply {
                    text = getString(R.string.button_icon_pack_current)
                    textSize = 13f
                    alpha = 0.72f
                    setPadding(0, dp(12), 0, dp(4))
                })
                addIconChoice(
                    parent = this,
                    label = unknownIcon,
                    iconGlyph = unknownIcon,
                    checked = true,
                    iconButtons = iconButtons,
                    onSelected = onSelected,
                )
            }
        }

    private fun addIconChoice(
        parent: android.view.ViewGroup,
        label: String,
        iconGlyph: String?,
        checked: Boolean,
        iconButtons: MutableList<RadioButton>,
        onSelected: (String?) -> Unit,
    ) {
        val radioButton = RadioButton(this).apply {
            id = View.generateViewId()
            tag = iconGlyph
            text = label
            textSize = if (iconGlyph == null) 14f else 18f
            isChecked = checked
            setPadding(0, dp(2), dp(8), dp(2))
            setOnClickListener {
                iconButtons.forEach { button -> button.isChecked = button === this }
                onSelected(iconGlyph)
            }
        }
        iconButtons += radioButton
        parent.addView(radioButton)
    }

    private fun showDeleteDialog(button: SavedOverlayButton) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.dialog_title_delete_saved_button)
            .setMessage(getString(R.string.message_delete_saved_button, button.displayName))
            .setPositiveButton(android.R.string.ok) { _, _ -> viewModel.deleteButton(button.id) }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun startOverlayWhenReady() {
        val state = viewModel.uiState.value
        if (state.activeSet == null) {
            Toast.makeText(this, R.string.toast_no_active_button_layout, Toast.LENGTH_SHORT).show()
            return
        }
        val visibleButtons = state.activeButtons.count { it.isVisible && it.enabled }
        if (visibleButtons == 0) {
            Toast.makeText(this, R.string.toast_no_visible_saved_buttons, Toast.LENGTH_SHORT).show()
            return
        }

        viewModel.startPermissionFlowIfNeeded(this) {
            viewModel.startTroubleshootingFlowIfNeeded(this) {
                if (viewModel.startButtonOverlay()) finish()
                else Toast.makeText(this, R.string.toast_denied_foreground_permission, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun activeSetOrToast(): SavedOverlayButtonSet? =
        viewModel.uiState.value.activeSet ?: run {
            Toast.makeText(this, R.string.toast_no_active_button_layout, Toast.LENGTH_SHORT).show()
            null
        }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()
}

private data class IconPack(
    val name: String,
    val icons: List<IconChoice>,
)

private data class IconChoice(
    val glyph: String,
    val name: String,
)

private val ICON_PACKS = listOf(
    IconPack(
        name = "Material",
        icons = listOf(
            IconChoice("▶", "Play"),
            IconChoice("⏸", "Pause"),
            IconChoice("■", "Stop"),
            IconChoice("●", "Record"),
            IconChoice("✓", "Check"),
            IconChoice("✕", "Close"),
            IconChoice("+", "Add"),
            IconChoice("⌂", "Home"),
            IconChoice("⚙", "Settings"),
            IconChoice("🔍", "Search"),
        ),
    ),
    IconPack(
        name = "Holo",
        icons = listOf(
            IconChoice("✦", "Spark"),
            IconChoice("✧", "Shine"),
            IconChoice("◇", "Diamond"),
            IconChoice("◆", "Core"),
            IconChoice("◎", "Target"),
            IconChoice("◉", "Focus"),
            IconChoice("◌", "Ring"),
            IconChoice("⬡", "Hex"),
            IconChoice("⬢", "Hex fill"),
            IconChoice("⟐", "Prism"),
        ),
    ),
    IconPack(
        name = "Action",
        icons = listOf(
            IconChoice("⚡", "Bolt"),
            IconChoice("🎯", "Aim"),
            IconChoice("🔁", "Loop"),
            IconChoice("🚀", "Boost"),
            IconChoice("🛡", "Shield"),
            IconChoice("★", "Star"),
            IconChoice("♥", "Heart"),
            IconChoice("☠", "Skull"),
            IconChoice("♞", "Knight"),
            IconChoice("💥", "Burst"),
        ),
    ),
    IconPack(
        name = "Elements",
        icons = listOf(
            IconChoice("✨", "Magic"),
            IconChoice("🔥", "Fire"),
            IconChoice("💧", "Water"),
            IconChoice("🌿", "Leaf"),
            IconChoice("❄", "Ice"),
            IconChoice("⚔", "Battle"),
            IconChoice("🧿", "Eye"),
            IconChoice("🐾", "Buddy"),
            IconChoice("🍬", "Candy"),
            IconChoice("📍", "Pin"),
        ),
    ),
)
