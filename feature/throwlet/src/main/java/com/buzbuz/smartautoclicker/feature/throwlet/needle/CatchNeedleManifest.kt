/*
 * Copyright (C) 2026 Nicolas Espinoza
 */
package com.buzbuz.smartautoclicker.feature.throwlet.needle

import android.content.Context
import android.graphics.BitmapFactory
import android.util.Base64
import java.io.File
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

object CatchNeedleManifest {
    private const val DIR = "needles/catch"
    private const val MANIFEST = "manifest.json"
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    fun readRecords(context: Context): List<CatchNeedleRecord> {
        val file = manifestFile(context)
        if (!file.exists()) return emptyList()
        return runCatching {
            json.decodeFromString<List<CatchNeedleRecord>>(file.readText())
        }.getOrElse { emptyList() }
    }

    fun applySyncedRecord(context: Context, record: CatchNeedleRecord, imagePngBase64: String) {
        val dir = directory(context).also { it.mkdirs() }
        val image = File(dir, "${record.id}.png")
        image.writeBytes(Base64.decode(imagePngBase64, Base64.DEFAULT))
        BitmapFactory.decodeFile(image.absolutePath)?.recycle()
        val existing = readRecords(context).filterNot { it.id == record.id }
        writeRecords(context, existing + record.copy(assetPath = image.absolutePath))
    }

    fun deleteRecord(context: Context, recordId: String) {
        val records = readRecords(context)
        val removed = records.find { it.id == recordId } ?: return
        runCatching { File(removed.assetPath).delete() }
        writeRecords(context, records.filterNot { it.id == recordId })
    }

    fun encodeImageBase64(record: CatchNeedleRecord): String? {
        val file = File(record.assetPath)
        if (!file.exists()) return null
        return Base64.encodeToString(file.readBytes(), Base64.NO_WRAP)
    }

    private fun writeRecords(context: Context, records: List<CatchNeedleRecord>) {
        val file = manifestFile(context)
        file.parentFile?.mkdirs()
        file.writeText(json.encodeToString(records))
    }

    fun writeRecord(context: Context, record: CatchNeedleRecord) {
        val existing = readRecords(context).filterNot { it.id == record.id }
        writeRecords(context, existing + record)
    }

    private fun directory(context: Context): File = File(context.filesDir, DIR)
    private fun manifestFile(context: Context): File = File(directory(context), MANIFEST)
}

@Serializable
data class CatchNeedleRecord(
    val id: String,
    val mode: String,
    val feature: String,
    val lane: String,
    val variantOrder: Int,
    val assetPath: String,
    val sourceSize: CatchNeedleSize,
    val cropRect: CatchNeedleRect,
    val searchRect: CatchNeedleRect,
    val threshold: Int,
    val createdAtMs: Long,
    val updatedAtMs: Long = createdAtMs,
)

@Serializable
data class CatchNeedleSize(val width: Int, val height: Int)

@Serializable
data class CatchNeedleRect(val left: Int, val top: Int, val right: Int, val bottom: Int)
