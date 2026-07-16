package com.example.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.SmsMessage
import android.util.Log
import com.example.data.AppDatabase
import com.example.data.AttendanceRecord
import com.example.data.AttendanceRepository
import com.example.utils.DateTimeUtils
import com.example.utils.NotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Locale

class SmsReceiver : BroadcastReceiver() {

    // Regex to match: "Attendance of your ward pranay sah is registered INWARD at 03:08 PM on 16-07-2026"
    private val smsRegex = "Attendance of your ward (.+?) is registered (INWARD|OUTWARD) at (.+?) on ([0-9]{2}-[0-9]{2}-[0-9]{4})".toRegex(RegexOption.IGNORE_CASE)

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != "android.provider.Telephony.SMS_RECEIVED") return

        val bundle = intent.extras ?: return
        try {
            val pdus = bundle.get("pdus") as? Array<*> ?: return
            val format = bundle.getString("format")
            
            for (pdu in pdus) {
                val bytePdu = pdu as? ByteArray ?: continue
                val message = SmsMessage.createFromPdu(bytePdu, format)
                val body = message.messageBody ?: continue
                
                Log.d("SmsReceiver", "Received SMS: $body")
                processSmsBody(context, body)
            }
        } catch (e: Exception) {
            Log.e("SmsReceiver", "Error parsing SMS: ${e.message}", e)
        }
    }

    private fun processSmsBody(context: Context, body: String) {
        val matchResult = smsRegex.find(body) ?: return
        
        try {
            val rawStudentName = matchResult.groups[1]?.value?.trim() ?: ""
            val type = matchResult.groups[2]?.value?.trim()?.uppercase(Locale.ROOT) ?: ""
            val timeStr = matchResult.groups[3]?.value?.trim() ?: ""
            val dateStr = matchResult.groups[4]?.value?.trim() ?: ""

            // Capitalize Student Name beautifully
            val studentName = rawStudentName.split(" ").joinToString(" ") { 
                it.replaceFirstChar { char -> if (char.isLowerCase()) char.titlecase(Locale.ROOT) else it.toString() } 
            }

            Log.d("SmsReceiver", "Extracted: Name=$studentName, Type=$type, Time=$timeStr, Date=$dateStr")

            val db = AppDatabase.getDatabase(context)
            val repository = AttendanceRepository(context, db.attendanceDao())

            CoroutineScope(Dispatchers.IO).launch {
                val existingRecord = repository.getRecordByDate(dateStr)
                
                val updatedRecord = if (type == "INWARD") {
                    if (existingRecord != null) {
                        // Already exists (maybe user manually created or received outward first)
                        val studyHrs = if (existingRecord.outwardTime != null) {
                            DateTimeUtils.calculateStudyHours(timeStr, existingRecord.outwardTime)
                        } else 0.0

                        val status = if (existingRecord.outwardTime != null) "Present" else "Still in Coaching"

                        existingRecord.copy(
                            studentName = studentName,
                            inwardTime = timeStr,
                            status = status,
                            studyHours = studyHrs,
                            synced = false
                        )
                    } else {
                        // Brand new record
                        AttendanceRecord(
                            date = dateStr,
                            studentName = studentName,
                            coachingName = "Sukrishna Commerce",
                            inwardTime = timeStr,
                            status = "Still in Coaching",
                            studyHours = 0.0,
                            synced = false
                        )
                    }
                } else { // OUTWARD
                    if (existingRecord != null) {
                        val studyHrs = if (existingRecord.inwardTime != null) {
                            DateTimeUtils.calculateStudyHours(existingRecord.inwardTime, timeStr)
                        } else 0.0

                        existingRecord.copy(
                            studentName = studentName,
                            outwardTime = timeStr,
                            status = "Present",
                            studyHours = studyHrs,
                            synced = false
                        )
                    } else {
                        // Received OUTWARD but no INWARD received yet
                        AttendanceRecord(
                            date = dateStr,
                            studentName = studentName,
                            coachingName = "Sukrishna Commerce",
                            outwardTime = timeStr,
                            status = "Present", // Mark present but study hours 0 or manual adjustment
                            studyHours = 0.0,
                            synced = false
                        )
                    }
                }

                repository.insertOrUpdate(updatedRecord)
                
                // Show notification to user
                launch(Dispatchers.Main) {
                    val notifTitle = "Sukrishna Attendance Detected"
                    val notifMessage = if (type == "INWARD") {
                        "$studentName entered coaching at $timeStr."
                    } else {
                        val durationText = if (updatedRecord.studyHours > 0.0) {
                            " Studied: ${DateTimeUtils.formatHours(updatedRecord.studyHours)}."
                        } else ""
                        "$studentName exited coaching at $timeStr.$durationText"
                    }
                    NotificationHelper.showAttendanceNotification(context, notifTitle, notifMessage)
                }
            }
        } catch (e: Exception) {
            Log.e("SmsReceiver", "Error processing extracted data: ${e.message}", e)
        }
    }
}
