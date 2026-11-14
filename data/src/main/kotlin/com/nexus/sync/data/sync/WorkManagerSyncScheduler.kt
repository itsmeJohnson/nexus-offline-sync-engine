package com.nexus.sync.data.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WorkManagerSyncScheduler @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val workManager: WorkManager by lazy {
        WorkManager.getInstance(context)
    }

    private val networkConstraints: Constraints
        get() = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

    fun schedulePeriodicSync(intervalMinutes: Long = 15L) {
        val periodicRequest = PeriodicWorkRequestBuilder<SyncCoroutineWorker>(
            repeatInterval = maxOf(intervalMinutes, 15L),
            repeatIntervalTimeUnit = TimeUnit.MINUTES
        )
            .setConstraints(networkConstraints)
            .setBackoffCriteria(
                backoffPolicy = BackoffPolicy.EXPONENTIAL,
                backoffDelay = 30L,
                timeUnit = TimeUnit.SECONDS
            )
            .build()

        workManager.enqueueUniquePeriodicWork(
            SyncCoroutineWorker.WORK_NAME_PERIODIC,
            ExistingPeriodicWorkPolicy.UPDATE,
            periodicRequest
        )
    }

    fun triggerImmediateSync() {
        val oneTimeRequest = OneTimeWorkRequestBuilder<SyncCoroutineWorker>()
            .setConstraints(networkConstraints)
            .setBackoffCriteria(
                backoffPolicy = BackoffPolicy.EXPONENTIAL,
                backoffDelay = 15L,
                timeUnit = TimeUnit.SECONDS
            )
            .build()

        workManager.enqueueUniqueWork(
            SyncCoroutineWorker.WORK_NAME_ONE_TIME,
            ExistingWorkPolicy.REPLACE,
            oneTimeRequest
        )
    }

    fun cancelAllSyncWork() {
        workManager.cancelUniqueWork(SyncCoroutineWorker.WORK_NAME_PERIODIC)
        workManager.cancelUniqueWork(SyncCoroutineWorker.WORK_NAME_ONE_TIME)
    }
}
