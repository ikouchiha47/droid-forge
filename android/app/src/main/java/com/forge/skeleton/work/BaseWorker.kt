package com.forge.skeleton.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

abstract class BaseWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    private val _progress = MutableStateFlow(0)
    val progress: StateFlow<Int> = _progress.asStateFlow()

    protected suspend fun reportProgress(percent: Int) {
        val clamped = percent.coerceIn(0, 100)
        _progress.value = clamped
        setProgress(workDataOf(KEY_PROGRESS to clamped))
    }

    companion object {
        const val KEY_PROGRESS = "progress"
    }
}
