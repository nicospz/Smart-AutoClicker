package com.buzbuz.smartautoclicker.core.domain.model.event

/** Detection strategy for image events. */
enum class ImageEventDetectionMode {
    /** Existing behavior: each image condition is searched independently. */
    STANDARD,
    /** Re-check the same template at fixed translations 0..N·(X,Y). */
    OFFSET_REPEAT,
    /** Re-check in the top split-screen pane and again shifted down by the device Y offset. */
    SPLIT_SCREEN,
}
