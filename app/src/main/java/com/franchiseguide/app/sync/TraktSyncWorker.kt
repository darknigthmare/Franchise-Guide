package com.franchiseguide.app.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.franchiseguide.app.data.AppRepository
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class TraktSyncWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val repo = AppRepository.getInstance(applicationContext)
        if (repo.isRecoveryOnly() || !repo.settings.value.traktAutoSync || !repo.isTraktConnected()) return Result.success()
        return withContext(Dispatchers.IO) { repo.syncFromTrakt(uploadLocal = true) }.fold(
            onSuccess = { Result.success() },
            onFailure = { if (runAttemptCount < 3) Result.retry() else Result.failure() }
        )
    }
}

object TraktSyncScheduler {
    private const val UNIQUE = "franchiseguide-trakt-auto-sync"

    fun schedule(context: Context, repo: AppRepository) {
        val wm = WorkManager.getInstance(context)
        if (repo.isRecoveryOnly() || !repo.settings.value.traktAutoSync || !repo.isTraktConnected()) {
            wm.cancelUniqueWork(UNIQUE)
            return
        }
        val hours = repo.settings.value.traktSyncIntervalHours.coerceIn(1, 168).toLong()
        val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
        val request = PeriodicWorkRequestBuilder<TraktSyncWorker>(hours, TimeUnit.HOURS)
            .setConstraints(constraints)
            .build()
        wm.enqueueUniquePeriodicWork(UNIQUE, ExistingPeriodicWorkPolicy.UPDATE, request)
    }
}
