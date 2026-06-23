package com.buzbuz.smartautoclicker.buttons

import android.Manifest
import android.content.Context
import android.os.Build

import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.PermissionChecker
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModel

import com.buzbuz.smartautoclicker.core.base.data.AppComponentsProvider
import com.buzbuz.smartautoclicker.core.common.permissions.PermissionsController
import com.buzbuz.smartautoclicker.core.common.permissions.model.PermissionAccessibilityService
import com.buzbuz.smartautoclicker.core.common.permissions.model.PermissionOverlay
import com.buzbuz.smartautoclicker.core.common.permissions.model.PermissionPostNotification
import com.buzbuz.smartautoclicker.core.common.quality.domain.QualityRepository
import com.buzbuz.smartautoclicker.localservice.ILocalService
import com.buzbuz.smartautoclicker.localservice.LocalServiceProvider

import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext

import javax.inject.Inject

@HiltViewModel
class ButtonLayoutLauncherViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: SavedOverlayButtonRepository,
    private val permissionController: PermissionsController,
    private val qualityRepository: QualityRepository,
    private val appComponentsProvider: AppComponentsProvider,
) : ViewModel() {

    private val serviceConnection: (ILocalService?) -> Unit = { localService ->
        clickerService = localService
    }
    private var clickerService: ILocalService? = null

    init {
        LocalServiceProvider.getLocalService(serviceConnection)
    }

    override fun onCleared() {
        LocalServiceProvider.getLocalService(null)
        super.onCleared()
    }

    fun getRunnableLayouts(): List<SavedOverlayButtonSet> =
        repository.sets.value.filter { set ->
            set.deletedAtMs == null && hasRunnableButtons(set)
        }

    fun setActiveLayout(layout: SavedOverlayButtonSet) {
        repository.setActiveSet(layout.syncId)
    }

    fun hasRunnableButtons(layout: SavedOverlayButtonSet): Boolean =
        repository.buttons.value.any { button ->
            button.deletedAtMs == null &&
                    button.setSyncId == layout.syncId &&
                    button.isVisible &&
                    button.enabled
        }

    fun startPermissionFlowIfNeeded(
        activity: AppCompatActivity,
        onAllGranted: () -> Unit,
        onMandatoryDenied: () -> Unit,
    ) {
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
            onMandatoryDenied = onMandatoryDenied,
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

        val service = clickerService ?: return false
        service.startButtonOverlay()
        return true
    }
}
