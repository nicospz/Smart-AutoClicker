package com.buzbuz.smartautoclicker.core.domain.model.event

/** Detection strategy for image events. */
enum class ImageEventDetectionMode {
    /** Existing behavior: each image condition is searched independently. */
    STANDARD,
    /** Find an anchor condition repeatedly, then verify other conditions around each anchor occurrence. */
    ANCHORED_REPEAT,
}
