package com.buzbuz.smartautoclicker.feature.throwlet

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object FastCatchPreset {
    private const val ASSET_PATH = "fast_catch/default_hold.hex"
    const val THROW_OFFSET_MS = 150
    const val HOLD_AFTER_THROW_MS = 50

    /** Berry-bag hold was captured in the bottom split pane's touch space. */
    val RECORDED_LANE: HelperLane = HelperLane.SPLIT_BOTTOM

    suspend fun holdPayloadHex(context: Context): String? = withContext(Dispatchers.IO) {
        runCatching {
            context.assets.open(ASSET_PATH).bufferedReader().use { it.readText().trim() }
        }.getOrNull()?.takeIf { it.isNotEmpty() }
    }

    suspend fun holdPayloadHexForReplay(
        context: Context,
        targetLane: HelperLane,
        laneOffsetTouch: Int,
    ): String? = withContext(Dispatchers.IO) {
        val raw = holdPayloadHex(context) ?: return@withContext null
        val decoded = RawGestureCodec.decode(raw).getOrNull() ?: return@withContext null
        SplitLaneTransforms.forReplayStored(
            payload = decoded,
            sourceLane = RECORDED_LANE,
            targetLane = targetLane,
            laneOffsetTouch = laneOffsetTouch,
        ).encodeHex()
    }
}
