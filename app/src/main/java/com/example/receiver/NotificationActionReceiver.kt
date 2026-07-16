package com.example.receiver

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.data.AppDatabase
import com.example.data.AttendanceRecord
import com.example.data.AttendanceRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class NotificationActionReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_MARK_HOLIDAY = "com.example.action.MARK_HOLIDAY"
        const val ACTION_MARK_ABSENT = "com.example.action.MARK_ABSENT"
        const val ACTION_IGNORE = "com.example.action.MARK_IGNORE"

        const val EXTRA_DATE = "extra_date"
        private const val NOTIFICATION_REMINDER_ID = 1002
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val date = intent.getStringExtra(EXTRA_DATE) ?: return

        // Dismiss notification
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(NOTIFICATION_REMINDER_ID)

        if (action == ACTION_IGNORE) {
            Log.d("NotificationAction", "Ignored missing attendance alert")
            return
        }

        val status = when (action) {
            ACTION_MARK_HOLIDAY -> "Holiday"
            ACTION_MARK_ABSENT -> "Absent"
            else -> return
        }

        val db = AppDatabase.getDatabase(context)
        val repository = AttendanceRepository(context, db.attendanceDao())

        // Use a coroutine to save in background
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val record = AttendanceRecord(
                    date = date,
                    studentName = "Pranay Sah",
                    coachingName = "Sukrishna Commerce",
                    status = status,
                    studyHours = 0.0,
                    isManual = false
                )
                repository.insertOrUpdate(record)
                Log.d("NotificationAction", "Successfully marked day $date as $status")
            } catch (e: Exception) {
                Log.e("NotificationAction", "Error updating attendance via notification action: ${e.message}")
            }
        }
    }
}
