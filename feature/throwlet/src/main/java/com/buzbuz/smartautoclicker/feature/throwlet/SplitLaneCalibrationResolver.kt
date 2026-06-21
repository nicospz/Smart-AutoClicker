package com.buzbuz.smartautoclicker.feature.throwlet

/**
 * Device-specific split-screen calibration. Screenshot divider and touch lane offset live in
 * different coordinate spaces on Samsung Ultra devices, so they are kept explicit instead of
 * deriving one from the other at runtime.
 */
data class DeviceSplitLaneCalibration(
    val screenObjectOffsetPx: Int,
    val touchOffset: Int,
)

object SplitLaneCalibrationResolver {
    /** Full-screen split layout: 1548px top + 24px divider + 1548px bottom = 3120px total. */
    const val SAMSUNG_ULTRA_SPLIT_LANE_OFFSET_PX = 1548
    const val SAMSUNG_ULTRA_SPLIT_LANE_OFFSET_TOUCH = 2064

    private val SAMSUNG_ULTRA_CALIBRATION = DeviceSplitLaneCalibration(
        screenObjectOffsetPx = SAMSUNG_ULTRA_SPLIT_LANE_OFFSET_PX,
        touchOffset = SAMSUNG_ULTRA_SPLIT_LANE_OFFSET_TOUCH,
    )

    fun resolve(model: String?, device: String?): DeviceSplitLaneCalibration? =
        if (isSamsungUltra(model, device)) SAMSUNG_ULTRA_CALIBRATION else null

    fun screenObjectOffsetPx(model: String?, device: String?): Int? =
        resolve(model, device)?.screenObjectOffsetPx

    fun touchOffset(model: String?, device: String?): Int? =
        resolve(model, device)?.touchOffset

    fun laneDividerPx(fullHeight: Int, model: String?, device: String?): Int =
        laneDividerPx(fullHeight, screenObjectOffsetPx(model, device))

    fun laneDividerPx(fullHeight: Int, screenObjectOffsetPx: Int?): Int =
        screenObjectOffsetPx?.coerceIn(1, (fullHeight - 1).coerceAtLeast(1))
            ?: (fullHeight / 2).coerceAtLeast(1)

    private fun isSamsungUltra(model: String?, device: String?): Boolean {
        val normalizedModel = model.orEmpty().uppercase()
        val normalizedDevice = device.orEmpty().lowercase()
        return normalizedModel.startsWith("SM-S918")
            || normalizedModel.startsWith("SM-S948")
            || normalizedDevice == "dm3q"
            || normalizedDevice == "m3q"
    }
}
