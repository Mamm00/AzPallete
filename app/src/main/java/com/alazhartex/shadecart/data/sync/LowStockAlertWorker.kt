package com.alazhartex.shadecart.data.sync

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.alazhartex.shadecart.R
import com.alazhartex.shadecart.domain.usecase.GetLowStockRollsUseCase
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * C3: Periodic & cut-triggered WorkManager worker scanning warehouse rolls whose available meterage
 * has dropped below threshold and raising local Android notifications.
 */
@HiltWorker
class LowStockAlertWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val getLowStockRollsUseCase: GetLowStockRollsUseCase,
) : CoroutineWorker(appContext, workerParams) {

    @SuppressLint("MissingPermission")
    override suspend fun doWork(): Result {
        val lowRolls = getLowStockRollsUseCase()
        if (lowRolls.isEmpty()) return Result.success()

        ensureNotificationChannel()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                applicationContext,
                Manifest.permission.POST_NOTIFICATIONS,
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return Result.success()
        }

        val topSummary = lowRolls.take(3).joinToString(" · ") { roll ->
            "${roll.rollCode} (${String.format(Locale.US, "%.0f", roll.availableMeters)}m)"
        }
        val title = applicationContext.getString(R.string.low_stock_alert_title, lowRolls.size)
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(topSummary)
            .setStyle(NotificationCompat.BigTextStyle().bigText(topSummary))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()

        runCatching {
            NotificationManagerCompat.from(applicationContext).notify(NOTIFICATION_ID, notification)
        }
        return Result.success()
    }

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = applicationContext.getSystemService(NotificationManager::class.java) ?: return
            if (manager.getNotificationChannel(CHANNEL_ID) == null) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    "Warehouse Low Stock Alerts",
                    NotificationManager.IMPORTANCE_DEFAULT,
                ).apply {
                    description = "Alerts when textile roll available meters drop below threshold."
                }
                manager.createNotificationChannel(channel)
            }
        }
    }

    companion object {
        const val CHANNEL_ID = "alazhartex_low_stock"
        const val NOTIFICATION_ID = 2041
        const val PERIODIC_WORK_NAME = "alazhartex_low_stock_periodic"
        const val IMMEDIATE_WORK_NAME = "alazhartex_low_stock_immediate"
    }
}

@Singleton
class LowStockAlertScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun schedulePeriodic() {
        val req = PeriodicWorkRequestBuilder<LowStockAlertWorker>(12, TimeUnit.HOURS).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            LowStockAlertWorker.PERIODIC_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            req,
        )
    }

    fun checkNow() {
        val req = OneTimeWorkRequestBuilder<LowStockAlertWorker>().build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            LowStockAlertWorker.IMMEDIATE_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            req,
        )
    }
}
