package com.example.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.util.Log
import android.widget.RemoteViews
import com.example.MainActivity
import com.example.R
import com.example.data.AppDatabase
import com.example.data.AttendanceRecord
import com.example.utils.DateTimeUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Calendar

class AttendanceWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        // Handle custom triggers or broadcast updates if needed
        if (intent.action == "com.example.action.UPDATE_WIDGET") {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val ids = appWidgetManager.getAppWidgetIds(ComponentName(context, AttendanceWidgetProvider::class.java))
            onUpdate(context, appWidgetManager, ids)
        }
    }

    companion object {
        fun triggerWidgetUpdate(context: Context) {
            val intent = Intent(context, AttendanceWidgetProvider::class.java).apply {
                action = "com.example.action.UPDATE_WIDGET"
            }
            context.sendBroadcast(intent)
        }

        private fun updateAppWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
            val views = RemoteViews(context.packageName, R.layout.attendance_widget_layout)

            // Setup click intent to open App
            val pendingIntent = PendingIntent.getActivity(
                context,
                0,
                Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_title, pendingIntent)

            // Fetch data asynchronously in IO thread
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val db = AppDatabase.getDatabase(context)
                    val records = db.attendanceDao().getAllRecords()

                    val todayDate = DateTimeUtils.getTodayDate()
                    val todayRecord = records.find { it.date == todayDate }
                    val todayStatus = todayRecord?.status ?: "No Entry"

                    // Compute Stats
                    val currentStreak = calculateStreak(records)
                    val monthHours = calculateMonthlyHours(records)
                    val attendancePercentage = calculateAttendancePercentage(records)

                    // Update UI in main thread
                    launch(Dispatchers.Main) {
                        views.setTextViewText(R.id.widget_status, todayStatus)
                        
                        // Style the status badge color
                        val badgeColor = when (todayStatus) {
                            "Present" -> Color.parseColor("#2E7D32")
                            "Still in Coaching" -> Color.parseColor("#FBC02D")
                            "Forgot OUTWARD" -> Color.parseColor("#E65100")
                            "Holiday", "Sunday" -> Color.parseColor("#1976D2")
                            "Absent" -> Color.parseColor("#C62828")
                            else -> Color.parseColor("#616161")
                        }
                        
                        // Set text and color
                        views.setTextColor(R.id.widget_status, Color.WHITE)
                        views.setTextViewText(R.id.widget_streak, "$currentStreak Days")
                        views.setTextViewText(R.id.widget_hours, "${String.format("%.1f", monthHours)}h")
                        views.setTextViewText(R.id.widget_ratio, "${attendancePercentage.toInt()}%")

                        appWidgetManager.updateAppWidget(appWidgetId, views)
                    }
                } catch (e: Exception) {
                    Log.e("AttendanceWidget", "Error updating widget: ${e.message}", e)
                }
            }
        }

        private fun calculateStreak(records: List<AttendanceRecord>): Int {
            if (records.isEmpty()) return 0
            val sorted = records.sortedByDescending { DateTimeUtils.parseDate(it.date)?.time ?: 0L }
            var streak = 0
            for (record in sorted) {
                if (record.status == "Present" || record.status == "Holiday" || record.status == "Sunday") {
                    streak++
                } else if (record.status == "Absent" || record.status == "Forgot OUTWARD") {
                    break
                }
            }
            return streak
        }

        private fun calculateMonthlyHours(records: List<AttendanceRecord>): Double {
            val cal = Calendar.getInstance()
            val monthStr = String.format("%02d", cal.get(Calendar.MONTH) + 1)
            val yearStr = cal.get(Calendar.YEAR).toString()
            val monthYearSuffix = "-$monthStr-$yearStr"

            return records.filter { it.date.endsWith(monthYearSuffix) }
                .sumOf { it.studyHours }
        }

        private fun calculateAttendancePercentage(records: List<AttendanceRecord>): Double {
            val coachingDays = records.count { it.status in listOf("Present", "Absent", "Forgot OUTWARD") }
            if (coachingDays == 0) return 100.0
            val presentDays = records.count { it.status == "Present" }
            return (presentDays.toDouble() / coachingDays) * 100.0
        }
    }
}
