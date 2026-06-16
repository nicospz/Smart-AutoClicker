/*
 * Copyright (C) 2026
 */
package com.buzbuz.smartautoclicker.core.common.actions.precision

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class PrecisionGesturePayloadTest {

    @Test
    fun translatePayload_shiftsAbsCoordinates() {
        val payloadHex = buildPayloadHex(
            listOf(
                absEvent(code = ABS_X, value = 100),
                absEvent(code = ABS_Y, value = 200),
            ),
        )

        val translated = PrecisionGesturePayload.translatePayload(payloadHex, dx = 10, dy = 20)
        val values = decodeAbsValues(translated)

        assertEquals(110, values[ABS_X])
        assertEquals(220, values[ABS_Y])
        assertTrue(PrecisionGesturePayload.validate(translated, eventCount = 2, durationMs = 100L))
    }

    @Test
    fun translatePayload_zeroOffset_returnsOriginal() {
        val payloadHex = buildPayloadHex(listOf(absEvent(code = ABS_X, value = 50)))

        assertEquals(payloadHex, PrecisionGesturePayload.translatePayload(payloadHex, dx = 0, dy = 0))
    }

    private fun buildPayloadHex(events: List<EventRecord>): String {
        val buffer = ByteBuffer.allocate(HEADER_SIZE + events.size * EVENT_RECORD_SIZE).order(ByteOrder.LITTLE_ENDIAN)
        buffer.put(GESTURE_MAGIC.encodeToByteArray())
        buffer.putInt(GESTURE_VERSION)
        buffer.putInt(events.size)
        buffer.putLong(100L)
        events.forEach { event ->
            buffer.putLong(event.deltaUs)
            buffer.putLong(event.timeSec)
            buffer.putLong(event.timeUsec)
            buffer.putShort(event.type.toShort())
            buffer.putShort(event.code.toShort())
            buffer.putInt(event.value)
        }
        return buffer.array().joinToString(separator = "") { byte -> "%02x".format(byte) }
    }

    private fun decodeAbsValues(payloadHex: String): Map<Int, Int> {
        val bytes = payloadHex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        buffer.position(HEADER_SIZE)
        val eventCount = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).apply { position(8) }.int
        val values = mutableMapOf<Int, Int>()
        repeat(eventCount) {
            buffer.position(HEADER_SIZE + it * EVENT_RECORD_SIZE + 24)
            val type = buffer.short.toInt() and 0xFFFF
            val code = buffer.short.toInt() and 0xFFFF
            val value = buffer.int
            if (type == EV_ABS) values[code] = value
        }
        return values
    }

    private fun absEvent(code: Int, value: Int) =
        EventRecord(deltaUs = 0, timeSec = 0, timeUsec = 0, type = EV_ABS, code = code, value = value)

    private data class EventRecord(
        val deltaUs: Long,
        val timeSec: Long,
        val timeUsec: Long,
        val type: Int,
        val code: Int,
        val value: Int,
    )

    private companion object {
        private const val GESTURE_MAGIC = "PGCG"
        private const val GESTURE_VERSION = 1
        private const val HEADER_SIZE = 20
        private const val EVENT_RECORD_SIZE = 32
        private const val EV_ABS = 0x03
        private const val ABS_X = 0x00
        private const val ABS_Y = 0x01
    }
}
