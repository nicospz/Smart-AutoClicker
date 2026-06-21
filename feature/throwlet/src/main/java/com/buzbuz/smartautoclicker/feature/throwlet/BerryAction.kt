package com.buzbuz.smartautoclicker.feature.throwlet

enum class BerryAction(
    val helperKind: String?,
    val label: String,
    val iconRes: Int,
) {
    NONE(null, "None", R.drawable.ic_berry_none),
    PINAP("PINAP", "Pinap", R.drawable.ic_berry_pinap),
    SILVER_PINAP("SILVER_PINAP", "Silver Pinap", R.drawable.ic_berry_silver_pinap),
    GOLDEN_RAZZ("GOLDEN_RAZZ", "Golden Razz", R.drawable.ic_berry_golden_razz),
}
