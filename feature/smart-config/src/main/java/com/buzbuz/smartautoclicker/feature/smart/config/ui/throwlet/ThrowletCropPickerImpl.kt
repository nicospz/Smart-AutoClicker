/*
 * Copyright (C) 2026 Nicolas Espinoza
 */
package com.buzbuz.smartautoclicker.feature.smart.config.ui.throwlet

import android.graphics.Bitmap
import android.graphics.Rect
import android.util.Log
import com.buzbuz.smartautoclicker.core.base.di.Dispatcher
import com.buzbuz.smartautoclicker.core.base.di.HiltCoroutineDispatchers.Main
import com.buzbuz.smartautoclicker.core.common.overlays.manager.OverlayManager
import com.buzbuz.smartautoclicker.core.display.recorder.ThrowletCropPickResult
import com.buzbuz.smartautoclicker.core.display.recorder.ThrowletCropPicker
import java.io.ByteArrayOutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

@Singleton
class ThrowletCropPickerImpl @Inject constructor(
    private val overlayContextProvider: ThrowletOverlayContextProvider,
    private val overlayManager: OverlayManager,
    @Dispatcher(Main) private val mainDispatcher: CoroutineDispatcher,
) : ThrowletCropPicker {

    override suspend fun pickCrop(frame: Bitmap, defaultArea: Rect?): ThrowletCropPickResult? =
        withContext(mainDispatcher) {
            Log.i(
                TAG,
                "pickCrop start frame=${frame.width}x${frame.height} defaultArea=$defaultArea",
            )
            val overlayContext = overlayContextProvider.overlayContext()
            if (overlayContext == null) {
                Log.e(TAG, "pickCrop abort: no overlay context")
                return@withContext null
            }
            val wasHidden = overlayManager.isStackHidden()
            if (wasHidden) {
                Log.i(TAG, "pickCrop restoring hidden overlay stack")
                overlayManager.restoreVisibility()
                Log.i(TAG, "pickCrop stackHidden after restore=${overlayManager.isStackHidden()}")
            }
            val screenshot = frame.copy(Bitmap.Config.ARGB_8888, false)
            suspendCancellableCoroutine { continuation ->
                val menu = ThrowletCropPickerMenu(
                    screenshot = screenshot,
                    defaultArea = defaultArea,
                    onResult = { area, cropBitmap ->
                        if (!continuation.isActive) {
                            Log.w(TAG, "pickCrop onResult ignored; continuation inactive area=$area")
                            return@ThrowletCropPickerMenu
                        }
                        val result = if (area == null || cropBitmap == null) {
                            Log.i(TAG, "pickCrop onResult cancelled area=$area cropBitmap=${cropBitmap != null}")
                            null
                        } else {
                            Log.i(
                                TAG,
                                "pickCrop onResult ok rect=$area crop=${cropBitmap.width}x${cropBitmap.height}",
                            )
                            val png = ByteArrayOutputStream().also { stream ->
                                cropBitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
                            }.toByteArray()
                            ThrowletCropPickResult(
                                frameWidth = frame.width,
                                frameHeight = frame.height,
                                cropLeft = area.left,
                                cropTop = area.top,
                                cropRight = area.right,
                                cropBottom = area.bottom,
                                cropPng = png,
                            )
                        }
                        continuation.resume(result)
                    },
                    onOverlayFailed = {
                        Log.e(TAG, "pickCrop overlay failed before display")
                        if (continuation.isActive) continuation.resume(null)
                    },
                )
                continuation.invokeOnCancellation {
                    Log.i(TAG, "pickCrop continuation cancelled; navigating up")
                    runCatching { overlayManager.navigateUp(overlayContext) }
                }
                Log.i(
                    TAG,
                    "pickCrop navigateTo context=${overlayContext.javaClass.name} " +
                        "hideCurrent=true menu=${menu.hashCode()}",
                )
                try {
                    overlayManager.navigateTo(overlayContext, menu, hideCurrent = true)
                    Log.i(TAG, "pickCrop navigateTo queued menu=${menu.hashCode()}")
                } catch (error: Throwable) {
                    Log.e(TAG, "pickCrop navigateTo failed", error)
                    if (continuation.isActive) continuation.resume(null)
                }
            }
        }

    companion object {
        private const val TAG = "ThrowletCropPicker"
    }
}
