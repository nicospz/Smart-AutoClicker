package com.buzbuz.smartautoclicker.feature.throwlet

import java.nio.ByteBuffer
import java.nio.ByteOrder

private const val GESTURE_MAGIC = "PGCG"
private const val GESTURE_VERSION = 1
private const val HEADER_SIZE = 20
private const val EVENT_RECORD_SIZE = 32
private const val EV_ABS = 0x03
private const val ABS_X = 0x00
private const val ABS_Y = 0x01
private const val ABS_MT_POSITION_X = 0x35
private const val ABS_MT_POSITION_Y = 0x36

data class RawGestureEvent(val deltaUs: Long, val timeSec: Long, val timeUsec: Long, val type: Int, val code: Int, val value: Int) {
    val isX: Boolean get() = type == EV_ABS && (code == ABS_X || code == ABS_MT_POSITION_X)
    val isY: Boolean get() = type == EV_ABS && (code == ABS_Y || code == ABS_MT_POSITION_Y)
}

data class GestureBounds(
    val minX: Int = 0,
    val maxX: Int = Int.MAX_VALUE,
    val minY: Int = 0,
    val maxY: Int = Int.MAX_VALUE,
)

data class RawGesturePayload(val durationMs: Long, val events: List<RawGestureEvent>) {
    fun extractPath(): List<PointI> {
        var x: Int? = null
        var y: Int? = null
        val points = mutableListOf<PointI>()
        events.forEach { event ->
            var changed = false
            if (event.isX) { x = event.value; changed = true }
            if (event.isY) { y = event.value; changed = true }
            val px = x
            val py = y
            if (changed && px != null && py != null) {
                val p = PointI(px, py)
                if (points.lastOrNull() != p) points += p
            }
        }
        return points
    }

    fun dominantLane(splitOffset: Int): HelperLane? {
        val path = extractPath()
        if (path.isEmpty()) return null
        val divider = splitOffset.coerceAtLeast(1)
        val medianY = path.map { it.y }.sorted()[path.size / 2]
        return if (medianY < divider) HelperLane.SPLIT_TOP else HelperLane.SPLIT_BOTTOM
    }

    fun medianY(): Int? {
        val ys = extractPath().map { it.y }
        if (ys.isEmpty()) return null
        return ys.sorted()[ys.size / 2]
    }

    fun inferBounds(): GestureBounds {
        var maxX = 0
        var maxY = 0
        events.forEach { event ->
            if (event.isX) maxX = maxOf(maxX, event.value)
            if (event.isY) maxY = maxOf(maxY, event.value)
        }
        return GestureBounds(maxX = maxX.coerceAtLeast(1), maxY = maxY.coerceAtLeast(1))
    }

    fun translatedY(dy: Int): RawGesturePayload {
        ThrowletLog.i("gesture translateY dy=$dy events=${events.size}")
        return copy(events = events.map { event -> if (event.isY) event.copy(value = event.value + dy) else event })
    }

    fun translatedY(dy: Int, bounds: GestureBounds): RawGesturePayload {
        ThrowletLog.i("gesture translateY dy=$dy bounded events=${events.size}")
        return copy(
            events = events.map { event ->
                if (!event.isY) return@map event
                val shifted = event.value + dy
                event.copy(value = shifted.coerceIn(bounds.minY, bounds.maxY))
            },
        )
    }

    fun encodeHex(): String {
        ThrowletLog.i("gesture encode start events=${events.size} durationMs=$durationMs")
        val buffer = ByteBuffer.allocate(HEADER_SIZE + events.size * EVENT_RECORD_SIZE).order(ByteOrder.LITTLE_ENDIAN)
        buffer.put(GESTURE_MAGIC.encodeToByteArray())
        buffer.putInt(GESTURE_VERSION)
        buffer.putInt(events.size)
        buffer.putLong(durationMs)
        events.forEach { event ->
            buffer.putLong(event.deltaUs)
            buffer.putLong(event.timeSec)
            buffer.putLong(event.timeUsec)
            buffer.putShort(event.type.toShort())
            buffer.putShort(event.code.toShort())
            buffer.putInt(event.value)
        }
        return buffer.array().toHex().also { ThrowletLog.i("gesture encode ok payloadChars=${it.length}") }
    }
}

object RawGestureCodec {
    fun decode(hex: String): Result<RawGesturePayload> = runCatching {
        ThrowletLog.i("gesture decode start payloadChars=${hex.length}")
        val bytes = hex.hexBytes()
        require(bytes.size >= HEADER_SIZE) { "gesture payload header truncated" }
        require(bytes.copyOfRange(0, 4).decodeToString() == GESTURE_MAGIC) { "gesture payload magic mismatch" }
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        buffer.position(4)
        val version = buffer.int
        val count = buffer.int
        val duration = buffer.long
        require(version == GESTURE_VERSION) { "unsupported gesture payload" }
        require(count >= 0 && count <= 50_000) { "unsupported gesture payload size" }
        require(bytes.size == HEADER_SIZE + count * EVENT_RECORD_SIZE) { "gesture payload events truncated" }
        val events = buildList(count) {
            repeat(count) {
                add(RawGestureEvent(buffer.long, buffer.long, buffer.long, buffer.short.toInt() and 0xffff, buffer.short.toInt() and 0xffff, buffer.int))
            }
        }
        RawGesturePayload(duration, events).also { decoded ->
            val path = decoded.extractPath()
            ThrowletLog.i("gesture decode ok events=${events.size} durationMs=$duration pathPoints=${path.size} first=${path.firstOrNull()} last=${path.lastOrNull()}")
        }
    }
}

private fun String.hexBytes(): ByteArray {
    require(length % 2 == 0) { "invalid hex" }
    return ByteArray(length / 2) { i -> ((this[i * 2].hexNibble() shl 4) or this[i * 2 + 1].hexNibble()).toByte() }
}

private fun Char.hexNibble(): Int = when (this) {
    in '0'..'9' -> this - '0'
    in 'a'..'f' -> this - 'a' + 10
    in 'A'..'F' -> this - 'A' + 10
    else -> error("invalid hex")
}

private fun ByteArray.toHex(): String = buildString(size * 2) {
    val digits = "0123456789ABCDEF"
    for (byte in this@toHex) {
        val value = byte.toInt() and 0xff
        append(digits[value ushr 4])
        append(digits[value and 0xf])
    }
}
