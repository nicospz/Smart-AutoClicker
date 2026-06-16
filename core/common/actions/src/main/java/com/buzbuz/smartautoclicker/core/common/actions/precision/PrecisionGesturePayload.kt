/*
 * Copyright (C) 2026
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.buzbuz.smartautoclicker.core.common.actions.precision

import java.nio.ByteBuffer
import java.nio.ByteOrder

const val PRECISION_GESTURE_HELPER_MODE = "raw-evdev"

data class PrecisionGesturePayload(
    val payloadHex: String,
    val eventCount: Int,
    val durationMs: Long,
    val helperMode: String = PRECISION_GESTURE_HELPER_MODE,
) {
    fun isValid(): Boolean =
        payloadHex.isNotBlank() && eventCount > 0 && durationMs >= 0 && decode(payloadHex).isSuccess

    companion object {
        fun validate(payloadHex: String?, eventCount: Int?, durationMs: Long?): Boolean =
            !payloadHex.isNullOrBlank() && eventCount != null && eventCount > 0 &&
                durationMs != null && durationMs >= 0 && decodeMetadata(payloadHex).getOrNull()?.let { metadata ->
                    metadata.eventCount == eventCount && metadata.durationMs == durationMs
                } == true

        fun decode(payloadHex: String): Result<DecodedPrecisionGesturePayload> = runCatching {
            val bytes = payloadHex.hexToBytes()
            require(bytes.size >= HEADER_SIZE) { "gesture payload header truncated" }
            require(bytes.copyOfRange(0, 4).decodeToString() == GESTURE_MAGIC) { "gesture payload magic mismatch" }

            val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            buffer.position(4)
            val version = buffer.int
            val eventCount = buffer.int
            val durationMs = buffer.long
            require(version == GESTURE_VERSION) { "unsupported gesture payload version" }
            require(eventCount in 0..MAX_GESTURE_EVENTS) { "unsupported gesture payload event count" }
            require(bytes.size == HEADER_SIZE + eventCount * EVENT_RECORD_SIZE) { "gesture payload events truncated" }

            DecodedPrecisionGesturePayload(version, eventCount, durationMs)
        }

        fun decodeMetadata(payloadHex: String): Result<DecodedPrecisionGesturePayload> = runCatching {
            require(payloadHex.length >= HEADER_SIZE * 2) { "gesture payload header truncated" }

            val header = payloadHex.substring(0, HEADER_SIZE * 2).hexToBytes()
            require(header.copyOfRange(0, 4).decodeToString() == GESTURE_MAGIC) { "gesture payload magic mismatch" }

            val buffer = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
            buffer.position(4)
            val version = buffer.int
            val eventCount = buffer.int
            val durationMs = buffer.long
            require(version == GESTURE_VERSION) { "unsupported gesture payload version" }
            require(eventCount in 0..MAX_GESTURE_EVENTS) { "unsupported gesture payload event count" }
            require(payloadHex.length == (HEADER_SIZE + eventCount * EVENT_RECORD_SIZE) * 2) {
                "gesture payload events truncated"
            }

            DecodedPrecisionGesturePayload(version, eventCount, durationMs)
        }
    }
}

data class DecodedPrecisionGesturePayload(
    val version: Int,
    val eventCount: Int,
    val durationMs: Long,
)

private fun String.hexToBytes(): ByteArray {
    require(length % 2 == 0) { "hex payload has odd length" }
    return ByteArray(length / 2) { index ->
        substring(index * 2, index * 2 + 2).toInt(16).toByte()
    }
}

private const val GESTURE_MAGIC = "PGCG"
private const val GESTURE_VERSION = 1
private const val HEADER_SIZE = 20
private const val EVENT_RECORD_SIZE = 32
private const val MAX_GESTURE_EVENTS = 50_000
