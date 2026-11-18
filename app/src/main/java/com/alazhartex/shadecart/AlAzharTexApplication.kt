package com.alazhartex.shadecart

import android.app.Application
import android.os.StrictMode
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.alazhartex.shadecart.data.local.DatabaseSeeder
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class AlAzharTexApplication : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var databaseSeeder: DatabaseSeeder

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) {
            // Flag accidental main-thread disk I/O or slow pixel work during development
            StrictMode.setThreadPolicy(
                StrictMode.ThreadPolicy.Builder()
                    .detectDiskReads()
                    .detectDiskWrites()
                    .detectNetwork()
                    .penaltyLog()
                    .build(),
            )
        }
        appScope.launch {
            databaseSeeder.ensureSeeded()
        }
    }
}
