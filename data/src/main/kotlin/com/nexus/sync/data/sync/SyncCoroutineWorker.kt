package com.nexus.sync.data.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.nexus.sync.domain.model.SyncResult
import com.nexus.sync.domain.usecase.PerformTwoWaySyncUseCase
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@HiltWorker
class SyncCoroutineWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val performTwoWaySyncUseCase: PerformTwoWaySyncUseCase
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val syncResult = performTwoWaySyncUseCase()
        when (syncResult) {
            is SyncResult.Success -> {
                Result.success()
            }
            is SyncResult.Failure -> {
                if (runAttemptCount < 5) {
                    // Triggers WorkManager exponential backoff
                    Result.retry()
                } else {
                    Result.failure()
                }
            }
        }
    }

    companion object {
        const val WORK_NAME_PERIODIC = "nexus_periodic_sync_worker"
        const val WORK_NAME_ONE_TIME = "nexus_immediate_sync_worker"
    }
}
