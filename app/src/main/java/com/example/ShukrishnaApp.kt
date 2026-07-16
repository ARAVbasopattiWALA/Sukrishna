package com.example

import android.app.Application
import android.util.Log
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.worker.AttendanceCheckWorker
import com.example.utils.NotificationHelper
import java.util.concurrent.TimeUnit

class ShukrishnaApp : Application() {

    override fun onCreate() {
        super.onCreate()
        Log.d("ShukrishnaApp", "Application started, initializing systems...")

        // Create notification channels
        NotificationHelper.createNotificationChannels(this)

        // Schedule background worker
        scheduleBackgroundChecks()
    }

    private fun scheduleBackgroundChecks() {
        try {
            val workRequest = PeriodicWorkRequestBuilder<AttendanceCheckWorker>(
                15, TimeUnit.MINUTES // Minimum WorkManager periodic interval is 15 mins
            ).build()

            WorkManager.getInstance(this).enqueueUniquePeriodicWork(
                "AttendanceCheckWork",
                ExistingPeriodicWorkPolicy.KEEP,
                workRequest
            )
            Log.d("ShukrishnaApp", "Enqueued background work daemon successfully.")
        } catch (e: Exception) {
            Log.e("ShukrishnaApp", "Failed to schedule background checks: ${e.message}", e)
        }
    }
}
