package com.example.utils

import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

object DateTimeUtils {

    const val DATE_FORMAT = "dd-MM-yyyy"
    const val TIME_FORMAT = "hh:mm a"

    fun getTodayDate(): String {
        val sdf = SimpleDateFormat(DATE_FORMAT, Locale.getDefault())
        return sdf.format(Date())
    }

    fun getCurrentTime(): String {
        val sdf = SimpleDateFormat(TIME_FORMAT, Locale.getDefault())
        return sdf.format(Date())
    }

    fun getDayOfWeek(dateStr: String): String {
        try {
            val sdf = SimpleDateFormat(DATE_FORMAT, Locale.getDefault())
            val date = sdf.parse(dateStr) ?: return ""
            val cal = Calendar.getInstance()
            cal.time = date
            return cal.getDisplayName(Calendar.DAY_OF_WEEK, Calendar.LONG, Locale.getDefault()) ?: ""
        } catch (e: Exception) {
            return ""
        }
    }

    fun getExpectedSubjectsForDay(dayOfWeek: String): List<String> {
        return when (dayOfWeek.lowercase()) {
            "monday", "wednesday", "friday" -> listOf("BST", "Accounts", "Statistics")
            "tuesday", "thursday", "saturday" -> listOf("English", "Maths")
            else -> emptyList()
        }
    }

    fun getExpectedHoursForDay(dayOfWeek: String): Double {
        return when (dayOfWeek.lowercase()) {
            "monday", "wednesday", "friday" -> 3.0
            "tuesday", "thursday", "saturday" -> 2.0
            else -> 0.0
        }
    }

    fun getExpectedClassStartTime(dayOfWeek: String): String? {
        return when (dayOfWeek.lowercase()) {
            "monday", "wednesday", "friday" -> "03:00 PM"
            "tuesday", "thursday", "saturday" -> "04:00 PM"
            else -> null
        }
    }

    fun calculateStudyHours(inwardTimeStr: String, outwardTimeStr: String): Double {
        try {
            val sdf = SimpleDateFormat(TIME_FORMAT, Locale.getDefault())
            val inwardDate = sdf.parse(inwardTimeStr) ?: return 0.0
            val outwardDate = sdf.parse(outwardTimeStr) ?: return 0.0

            var diffMillis = outwardDate.time - inwardDate.time
            if (diffMillis < 0) {
                // Handle class going overnight, though unlikely for coaching,
                // but just in case, we can add 24 hours in millis.
                diffMillis += TimeUnit.DAYS.toMillis(1)
            }

            val diffHours = diffMillis.toDouble() / (1000 * 60 * 60)
            // Round to 2 decimal places
            return Math.round(diffHours * 100.0) / 100.0
        } catch (e: Exception) {
            return 0.0
        }
    }

    fun formatHours(hours: Double): String {
        val totalMinutes = (hours * 60).toInt()
        val h = totalMinutes / 60
        val m = totalMinutes % 60
        return when {
            h > 0 && m > 0 -> "$h hr $m min"
            h > 0 -> "$h hr"
            m > 0 -> "$m min"
            else -> "0 min"
        }
    }

    fun parseTimeToCalendar(timeStr: String, dateStr: String): Calendar? {
        try {
            val sdf = SimpleDateFormat("$DATE_FORMAT $TIME_FORMAT", Locale.getDefault())
            val date = sdf.parse("$dateStr $timeStr") ?: return null
            val cal = Calendar.getInstance()
            cal.time = date
            return cal
        } catch (e: Exception) {
            return null
        }
    }

    fun parseDate(dateStr: String): Date? {
        return try {
            SimpleDateFormat(DATE_FORMAT, Locale.getDefault()).parse(dateStr)
        } catch (e: Exception) {
            null
        }
    }
}
