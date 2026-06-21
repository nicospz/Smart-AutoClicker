/*
 * Copyright (C) 2026 Nicolas Espinoza
 */
package com.buzbuz.smartautoclicker.feature.sync.data

import android.content.Context
import android.graphics.BitmapFactory
import android.util.Base64
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.ByteArrayOutputStream
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Singleton
class ConditionAssetSync @Inject constructor(
    @ApplicationContext private val context: Context,
    private val api: SacSupabaseClient,
) {
    suspend fun pushAssets(paths: Set<String>) = withContext(Dispatchers.IO) {
        if (!api.isConfigured || paths.isEmpty()) return@withContext
        val remoteHashes = api.listConditionAssets().associateBy { it.contentHash }
        val now = System.currentTimeMillis()
        for (path in paths) {
            val contentHash = path.substringAfterLast('/')
            val localFile = File(context.filesDir, path)
            if (!localFile.exists()) continue
            val remote = remoteHashes[contentHash]
            if (remote != null && remote.updatedAtMs >= localFile.lastModified()) continue
            val bytes = localFile.readBytes()
            val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
            api.upsertConditionAsset(contentHash, base64, now)
        }
    }

    suspend fun ensureLocalAssets(paths: Set<String>) = withContext(Dispatchers.IO) {
        if (!api.isConfigured || paths.isEmpty()) return@withContext
        val remoteAssets = api.listConditionAssets().associateBy { it.contentHash }
        for (path in paths) {
            val contentHash = path.substringAfterLast('/')
            val localFile = File(context.filesDir, path)
            if (localFile.exists()) continue
            val remote = remoteAssets[contentHash] ?: continue
            val bytes = Base64.decode(remote.imagePngBase64, Base64.DEFAULT)
            localFile.parentFile?.mkdirs()
            localFile.writeBytes(bytes)
            BitmapFactory.decodeFile(localFile.absolutePath)?.recycle()
        }
    }

    fun encodeFileBase64(path: String): String? {
        val file = File(context.filesDir, path)
        if (!file.exists()) return null
        return Base64.encodeToString(file.readBytes(), Base64.NO_WRAP)
    }

    fun writeBase64Png(relativePath: String, base64: String) {
        val bytes = Base64.decode(base64, Base64.DEFAULT)
        val file = File(context.filesDir, relativePath)
        file.parentFile?.mkdirs()
        file.writeBytes(bytes)
    }
}
