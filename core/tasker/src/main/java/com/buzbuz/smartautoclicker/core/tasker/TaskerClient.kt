/*
 * Copyright (C) 2026 Nicolas Espinoza
 */
package com.buzbuz.smartautoclicker.core.tasker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.util.Log
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import net.dinglisch.android.tasker.TaskerIntent
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

@Singleton
class TaskerClient @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    fun getStatus(): TaskerIntent.Status = TaskerIntent.testStatus(context)

    fun getExternalAccessIntent(): Intent = TaskerIntent.getExternalAccessPrefsIntent()

    suspend fun queryTasks(): List<TaskerTaskItem> = withContext(Dispatchers.IO) {
        if (getStatus() != TaskerIntent.Status.OK) return@withContext emptyList()

        val tasks = mutableListOf<TaskerTaskItem>()
        val cursor: Cursor? = runCatching {
            context.contentResolver.query(
                Uri.parse(TaskerIntent.PROVIDER_URI_TASKS),
                null,
                null,
                null,
                null,
            )
        }.getOrNull()

        cursor?.use {
            val nameCol = it.getColumnIndex(TaskerIntent.PROVIDER_COL_NAME)
            val projectCol = it.getColumnIndex(TaskerIntent.PROVIDER_COL_PROJECT_NAME)
            if (nameCol < 0) return@withContext emptyList()

            while (it.moveToNext()) {
                val name = it.getString(nameCol) ?: continue
                val project = if (projectCol >= 0) it.getString(projectCol) else null
                tasks += TaskerTaskItem(name = name, projectName = project)
            }
        }
        tasks.sortedWith(compareBy({ it.projectName.orEmpty() }, { it.name }))
    }

    suspend fun runTask(request: TaskerRunRequest) {
        if (request.taskName.isBlank()) {
            Log.w(TAG, "Tasker task name is blank, skipping")
            return
        }

        when (getStatus()) {
            TaskerIntent.Status.NOT_INSTALLED -> {
                Log.w(TAG, "Tasker is not installed, skipping task ${request.taskName}")
                return
            }
            TaskerIntent.Status.NO_ACCESS -> {
                Log.w(TAG, "Tasker external access not granted, skipping task ${request.taskName}")
                return
            }
            TaskerIntent.Status.OK -> Unit
        }

        val taskerIntent = TaskerIntent(request.taskName)
        val androidIntent = taskerIntent.intent.apply {
            addTaskerVariables(request.variables)
        }

        if (request.waitForCompletion) {
            withContext(Dispatchers.Main) {
                awaitTaskCompletion(taskerIntent, request.timeoutMs) {
                    context.sendBroadcast(androidIntent)
                }
            }
        } else {
            withContext(Dispatchers.Main) {
                context.sendBroadcast(androidIntent)
            }
        }
    }

    private suspend fun awaitTaskCompletion(
        taskerIntent: TaskerIntent,
        timeoutMs: Long,
        onStart: () -> Unit,
    ) {
        val completed = withTimeoutOrNull(timeoutMs) {
            suspendCancellableCoroutine { continuation ->
                val receiver = object : BroadcastReceiver() {
                    override fun onReceive(ctx: Context?, intent: Intent?) {
                        val success = intent?.getBooleanExtra(TaskerIntent.EXTRA_SUCCESS_FLAG, false) == true
                        if (!success) {
                            Log.w(TAG, "Tasker reported task failure for ${taskerIntent.intent.getStringExtra(TaskerIntent.EXTRA_TASK_NAME)}")
                        }
                        runCatching { context.unregisterReceiver(this) }
                        if (continuation.isActive) continuation.resume(Unit)
                    }
                }

                continuation.invokeOnCancellation {
                    runCatching { context.unregisterReceiver(receiver) }
                }

                ContextCompat.registerReceiver(
                    context,
                    receiver,
                    taskerIntent.getCompletionFilter(),
                    ContextCompat.RECEIVER_EXPORTED,
                )
                onStart()
            }
        }

        if (completed == null) {
            Log.w(TAG, "Timed out waiting for Tasker task ${taskerIntent.intent.getStringExtra(TaskerIntent.EXTRA_TASK_NAME)}")
        }
    }

    private fun Intent.addTaskerVariables(variables: List<TaskerVariable>) {
        val valid = variables.mapNotNull { variable ->
            val name = variable.normalizedName()
            if (name.isEmpty()) null else name to variable.value
        }
        if (valid.isEmpty()) return

        putStringArrayListExtra(
            TaskerIntent.EXTRA_VAR_NAMES_LIST,
            ArrayList(valid.map { it.first }),
        )
        putStringArrayListExtra(
            TaskerIntent.EXTRA_VAR_VALUES_LIST,
            ArrayList(valid.map { it.second }),
        )
    }

    companion object {
        private const val TAG = "TaskerClient"
    }
}
