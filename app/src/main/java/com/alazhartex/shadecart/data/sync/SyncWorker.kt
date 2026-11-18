package com.alazhartex.shadecart.data.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.alazhartex.shadecart.data.repo.SyncWorkScheduler
import com.alazhartex.shadecart.domain.usecase.TriggerSyncUseCase
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Background Hilt WorkManager worker draining queued shade-cart updates via [TriggerSyncUseCase].
 *
 * Uses connected-network constraints and exponential backoff retry on transient HTTP/network failures
 * (Pyre, *Android Development with Jetpack Compose & Kotlin*, Chapter 8.2 & Chapter 10.1).
 */
@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val triggerSyncUseCase: TriggerSyncUseCase,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val summary = triggerSyncUseCase()
        return if (summary.failed > 0 && runAttemptCount < MAX_RETRIES) {
            Result.retry()
        } else {
            Result.success()
        }
    }

    companion object {
        const val UNIQUE_SYNC_WORK = "alazhartex_shade_cart_sync"
        private const val MAX_RETRIES = 3
    }
}

/**
 * Enqueues unique WorkManager sync tasks whenever local shade cart mutations are committed.
 */
@Singleton
class WorkManagerSyncScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) : SyncWorkScheduler {

    override fun enqueueSync() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val request = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.SECONDS)
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            SyncWorker.UNIQUE_SYNC_WORK,
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }
}
