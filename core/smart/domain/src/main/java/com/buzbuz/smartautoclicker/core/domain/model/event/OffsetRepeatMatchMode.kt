package com.buzbuz.smartautoclicker.core.domain.model.event

/** Match behavior for [ImageEventDetectionMode.OFFSET_REPEAT]. */
enum class OffsetRepeatMatchMode {
    /** Stop at the first fulfilling offset instance and execute once. */
    FIRST_MATCH,
    /** Execute actions for every fulfilling offset instance in the same frame. */
    ALL_MATCHES,
}
