/*
 * Copyright (C) 2026 Nicolas Espinoza
 */
package com.buzbuz.smartautoclicker.feature.sync.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.buzbuz.smartautoclicker.feature.sync.di.SacSyncWorkerEntryPoint
import dagger.hilt.android.EntryPointAccessors
import java.util.concurrent.TimeUnit

class SacSyncWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val syncEngine = EntryPointAccessors.fromApplication(
            applicationContext,
            SacSyncWorkerEntryPoint::class.java,
        ).syncEngine()
        return if (syncEngine.syncAll().errorMessage == null) Result.success() else Result.retry()
    }

    companion object {
        private const val WORK_NAME = "sac_periodic_sync"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<SacSyncWorker>(30, TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }
    }
}
