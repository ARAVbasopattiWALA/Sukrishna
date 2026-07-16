package com.example.worker

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.data.AppDatabase
import com.example.data.AttendanceRecord
import com.example.data.AttendanceRepository
import com.example.utils.DateTimeUtils
import com.example.utils.NotificationHelper
import java.util.Calendar

class AttendanceCheckWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    private val sharedPrefs: SharedPreferences = context.getSharedPreferences("attendance_worker_prefs", Context.MODE_PRIVATE)

    override suspend fun doWork(): Result {
        Log.d("AttendanceCheckWorker", "Running background checks...")
        val context = applicationContext
        val db = AppDatabase.getDatabase(context)
        val repository = AttendanceRepository(context, db.attendanceDao())

        val todayDate = DateTimeUtils.getTodayDate()
        val dayOfWeek = DateTimeUtils.getDayOfWeek(todayDate)

        val calendar = Calendar.getInstance()
        val currentHour = calendar.get(Calendar.HOUR_OF_DAY)

        try {
            // 1. Sunday: Automatically mark Holiday
            if (dayOfWeek.lowercase() == "sunday") {
                val existing = repository.getRecordByDate(todayDate)
                if (existing == null) {
                    val sundayHoliday = AttendanceRecord(
                        date = todayDate,
                        studentName = "Pranay Sah",
                        coachingName = "Sukrishna Commerce",
                        status = "Holiday",
                        studyHours = 0.0,
                        note = "Sunday Auto-Holiday"
                    )
                    repository.insertOrUpdate(sundayHoliday)
                    Log.d("AttendanceCheckWorker", "Automatically marked Sunday as Holiday")
                    NotificationHelper.showGenericReminder(context, "Happy Sunday!", "Today is automatically marked as a Holiday.")
                }
                return Result.success()
            }

            // Fetch record for today
            val todayRecord = repository.getRecordByDate(todayDate)

            // 2. Outward Check: If no OUTWARD arrives even after 8 PM (20:00)
            if (currentHour >= 20) {
                if (todayRecord != null && todayRecord.status == "Still in Coaching") {
                    val updated = todayRecord.copy(
                        status = "Forgot OUTWARD",
                        studyHours = 0.0,
                        synced = false
                    )
                    repository.insertOrUpdate(updated)
                    NotificationHelper.showForgotOutwardAlert(context, todayDate)
                    Log.d("AttendanceCheckWorker", "Flagged Forgot OUTWARD for $todayDate")
                }
            }

            // 3. Expected Start Time + 1 hour missing check
            // Mon/Wed/Fri class starts at 3:00 PM (15:00). Check at/after 4:00 PM (16:00).
            // Tue/Thu/Sat class starts at 4:00 PM (16:00). Check at/after 5:00 PM (17:00).
            var checkHour = -1
            if (dayOfWeek.lowercase() in listOf("monday", "wednesday", "friday")) {
                checkHour = 16
            } else if (dayOfWeek.lowercase() in listOf("tuesday", "thursday", "saturday")) {
                checkHour = 17
            }

            if (checkHour != -1 && currentHour >= checkHour) {
                // Check if we already alerted today
                val lastAlertedDate = sharedPrefs.getString("last_missing_alert_date", "")
                if (lastAlertedDate != todayDate && todayRecord == null) {
                    // Show Notification
                    NotificationHelper.showMissingAttendanceReminder(context, todayDate)
                    // Mark as alerted today
                    sharedPrefs.edit().putString("last_missing_alert_date", todayDate).apply()
                    Log.d("AttendanceCheckWorker", "Triggered missing attendance alert for $todayDate")
                }
            }

            return Result.success()
        } catch (e: Exception) {
            Log.e("AttendanceCheckWorker", "Error during background checks: ${e.message}", e)
            return Result.retry()
        }
    }
}
