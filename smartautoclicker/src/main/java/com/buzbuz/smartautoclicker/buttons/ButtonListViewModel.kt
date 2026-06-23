package com.buzbuz.smartautoclicker.buttons

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.os.Build

import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.PermissionChecker
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope

import com.buzbuz.smartautoclicker.core.base.data.AppComponentsProvider
import com.buzbuz.smartautoclicker.core.common.permissions.PermissionsController
import com.buzbuz.smartautoclicker.core.common.permissions.model.PermissionAccessibilityService
import com.buzbuz.smartautoclicker.core.common.permissions.model.PermissionOverlay
import com.buzbuz.smartautoclicker.core.common.permissions.model.PermissionPostNotification
import com.buzbuz.smartautoclicker.core.common.quality.domain.QualityRepository
import com.buzbuz.smartautoclicker.core.dumb.domain.IDumbRepository
import com.buzbuz.smartautoclicker.core.dumb.domain.model.DumbScenario
import com.buzbuz.smartautoclicker.feature.sync.domain.SacSyncCoordinator
import com.buzbuz.smartautoclicker.localservice.ILocalService
import com.buzbuz.smartautoclicker.localservice.LocalServiceProvider

import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext

import javax.inject.Inject

import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class ButtonListViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: SavedOverlayButtonRepository,
    private val dumbRepository: IDumbRepository,
    private val permissionController: PermissionsController,
    private val qualityRepository: QualityRepository,
    private val appComponentsProvider: AppComponentsProvider,
    private val sacSyncCoordinator: SacSyncCoordinator,
) : ViewModel() {

    private val serviceConnection: (ILocalService?) -> Unit = { localService ->
        clickerService = localService
    }
    private var clickerService: ILocalService? = null

    val uiState: StateFlow<ButtonLayoutUiState> =
        combine(repository.sets, repository.activeSetSyncId, repository.buttons) { sets, activeSetSyncId, buttons ->
            val activeSet = sets.firstOrNull { it.syncId == activeSetSyncId && it.deletedAtMs == null }
            ButtonLayoutUiState(
                sets = sets.filter { it.deletedAtMs == null },
                activeSet = activeSet,
                activeButtons = buttons.filter { button ->
                    button.deletedAtMs == null && activeSet != null && button.setSyncId == activeSet.syncId
                },
            )
        }.stateIn(viewModelScope, SharingStarted.Eagerly, ButtonLayoutUiState())

    val dumbScenarios: StateFlow<List<DumbScenario>> = dumbRepository.dumbScenarios
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    init {
        LocalServiceProvider.getLocalService(serviceConnection)
    }

    override fun onCleared() {
        LocalServiceProvider.getLocalService(null)
        super.onCleared()
    }

    fun createSet(name: String): SavedOverlayButtonSet {
        val set = repository.createSet(name)
        sacSyncCoordinator.scheduleSettingsPush()
        return set
    }

    fun renameSet(setSyncId: String, name: String) {
        repository.renameSet(setSyncId, name)
        sacSyncCoordinator.scheduleSettingsPush()
    }

    fun duplicateSet(setSyncId: String, name: String): SavedOverlayButtonSet {
        val set = repository.duplicateSet(setSyncId, name)
        sacSyncCoordinator.scheduleSettingsPush()
        return set
    }

    fun deleteSet(setSyncId: String) {
        repository.deleteSet(setSyncId)
        sacSyncCoordinator.scheduleSettingsPush()
    }

    fun setActiveSet(setSyncId: String?) {
        repository.setActiveSet(setSyncId)
        sacSyncCoordinator.scheduleSettingsPush()
    }

    fun addButton(setSyncId: String, scenario: DumbScenario) {
        repository.addButtonForScenario(setSyncId, scenario)
        sacSyncCoordinator.scheduleSettingsPush()
    }

    fun setVisible(buttonId: Long, visible: Boolean) {
        repository.setVisible(buttonId, visible)
        sacSyncCoordinator.scheduleSettingsPush()
    }

    fun setEnabled(buttonId: Long, enabled: Boolean) {
        repository.setEnabled(buttonId, enabled)
        sacSyncCoordinator.scheduleSettingsPush()
    }

    fun updateLabel(buttonId: Long, label: String?) {
        repository.updateLabel(buttonId, label)
        sacSyncCoordinator.scheduleSettingsPush()
    }

    fun updateAppearance(buttonId: Long, label: String?, iconGlyph: String?) {
        repository.updateAppearance(buttonId, label, iconGlyph)
        sacSyncCoordinator.scheduleSettingsPush()
    }

    fun deleteButton(buttonId: Long) {
        repository.deleteButton(buttonId)
        sacSyncCoordinator.scheduleSettingsPush()
    }

    fun startPermissionFlowIfNeeded(activity: AppCompatActivity, onAllGranted: () -> Unit) {
        permissionController.startPermissionsUiFlow(
            activity = activity,
            permissions = listOf(
                PermissionOverlay(),
                PermissionAccessibilityService(
                    componentName = appComponentsProvider.klickrServiceComponentName,
                    isServiceRunning = { LocalServiceProvider.isServiceStarted() },
                ),
                PermissionPostNotification(optional = false),
            ),
            onAllGranted = onAllGranted,
        )
    }

    fun startTroubleshootingFlowIfNeeded(activity: FragmentActivity, onCompleted: () -> Unit) {
        qualityRepository.startTroubleshootingUiFlowIfNeeded(activity, onCompleted)
    }

    fun startButtonOverlay(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val foregroundPermission = PermissionChecker.checkSelfPermission(context, Manifest.permission.FOREGROUND_SERVICE)
            if (foregroundPermission != PermissionChecker.PERMISSION_GRANTED) return false
        }

        clickerService?.startButtonOverlay()
        return true
    }

    fun nextLayoutName(): String {
        val existingNames = uiState.value.sets.map { it.name }.toSet()
        var index = 1
        while ("Layout $index" in existingNames) index++
        return "Layout $index"
    }

    fun duplicateLayoutName(source: SavedOverlayButtonSet): String {
        val existingNames = uiState.value.sets.map { it.name }.toSet()
        val base = "${source.name} copy"
        if (base !in existingNames) return base
        var index = 2
        while ("$base $index" in existingNames) index++
        return "$base $index"
    }
}

data class ButtonLayoutUiState(
    val sets: List<SavedOverlayButtonSet> = emptyList(),
    val activeSet: SavedOverlayButtonSet? = null,
    val activeButtons: List<SavedOverlayButton> = emptyList(),
)
