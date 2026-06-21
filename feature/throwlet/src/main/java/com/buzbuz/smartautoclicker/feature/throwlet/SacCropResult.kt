package com.buzbuz.smartautoclicker.feature.throwlet

data class SacCropResult(
    val frameWidth: Int,
    val frameHeight: Int,
    val cropLeft: Int,
    val cropTop: Int,
    val cropRight: Int,
    val cropBottom: Int,
    val cropBitmapPath: String,
)
