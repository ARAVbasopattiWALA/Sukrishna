package com.example.utils

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.MainActivity
import com.example.receiver.NotificationActionReceiver

object NotificationHelper {

    private const val CHANNEL_ATTENDANCE_ID = "attendance_channel"
    private const val CHANNEL_REMINDER_ID = "reminder_channel"
    private const val CHANNEL_ALERT_ID = "alert_channel"

    private const val NOTIFICATION_ATTENDANCE_ID = 1001
    private const val NOTIFICATION_REMINDER_ID = 1002
    private const val NOTIFICATION_ALERT_ID = 1003

    fun createNotificationChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            val attendanceChannel = NotificationChannel(
                CHANNEL_ATTENDANCE_ID,
                "Attendance Updates",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notifications for automatic INWARD and OUTWARD registrations."
            }

            val reminderChannel = NotificationChannel(
                CHANNEL_REMINDER_ID,
                "Class & Daily Reminders",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Reminders for classes, streaks, and missing attendances."
            }

            val alertChannel = NotificationChannel(
                CHANNEL_ALERT_ID,
                "Urgent Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Warnings and urgent alerts like forgot outward notifications."
            }

            notificationManager.createNotificationChannel(attendanceChannel)
            notificationManager.createNotificationChannel(reminderChannel)
            notificationManager.createNotificationChannel(alertChannel)
        }
    }

    fun showAttendanceNotification(context: Context, title: String, message: String) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ATTENDANCE_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        try {
            with(NotificationManagerCompat.from(context)) {
                notify(NOTIFICATION_ATTENDANCE_ID, builder.build())
            }
        } catch (e: SecurityException) {
            // Android 13 notification permission not granted yet
        }
    }

    fun showForgotOutwardAlert(context: Context, date: String) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ALERT_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("⚠ Forgot OUTWARD Alert")
            .setContentText("You did not register your OUTWARD attendance for $date. Please contact Sukrishna Commerce coaching to verify.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setColor(0xFFFF0000.toInt()) // Red accent color for urgency

        try {
            with(NotificationManagerCompat.from(context)) {
                notify(NOTIFICATION_ALERT_ID, builder.build())
            }
        } catch (e: SecurityException) {
            // Android 13 notification permission not granted yet
        }
    }

    fun showMissingAttendanceReminder(context: Context, date: String) {
        // Intent for main activity click
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Action: I'm on Holiday
        val holidayIntent = Intent(context, NotificationActionReceiver::class.java).apply {
            action = NotificationActionReceiver.ACTION_MARK_HOLIDAY
            putExtra(NotificationActionReceiver.EXTRA_DATE, date)
        }
        val holidayPendingIntent = PendingIntent.getBroadcast(
            context, 1, holidayIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Action: I was Absent
        val absentIntent = Intent(context, NotificationActionReceiver::class.java).apply {
            action = NotificationActionReceiver.ACTION_MARK_ABSENT
            putExtra(NotificationActionReceiver.EXTRA_DATE, date)
        }
        val absentPendingIntent = PendingIntent.getBroadcast(
            context, 2, absentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Action: Ignore
        val ignoreIntent = Intent(context, NotificationActionReceiver::class.java).apply {
            action = NotificationActionReceiver.ACTION_IGNORE
        }
        val ignorePendingIntent = PendingIntent.getBroadcast(
            context, 3, ignoreIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_REMINDER_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Attendance Missing")
            .setContentText("You have not marked attendance today. What was your status?")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .addAction(android.R.drawable.ic_menu_today, "I'm on Holiday", holidayPendingIntent)
            .addAction(android.R.drawable.ic_delete, "I was Absent", absentPendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Ignore", ignorePendingIntent)

        try {
            with(NotificationManagerCompat.from(context)) {
                notify(NOTIFICATION_REMINDER_ID, builder.build())
            }
        } catch (e: SecurityException) {
            // Android 13 notification permission not granted yet
        }
    }

    fun showGenericReminder(context: Context, title: String, message: String) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_REMINDER_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        try {
            with(NotificationManagerCompat.from(context)) {
                notify(NOTIFICATION_REMINDER_ID, builder.build())
            }
        } catch (e: SecurityException) {
            // Android 13 notification permission not granted yet
        }
    }
}
