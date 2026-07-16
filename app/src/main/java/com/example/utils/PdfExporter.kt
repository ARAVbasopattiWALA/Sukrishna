package com.example.utils

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.util.Log
import com.example.data.AttendanceRecord
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.*

object PdfExporter {

    fun exportToPdf(context: Context, records: List<AttendanceRecord>, uri: Uri): Result<Unit> {
        return try {
            val outputStream: OutputStream = context.contentResolver.openOutputStream(uri) 
                ?: return Result.failure(Exception("Cannot open output stream for URI"))

            val pdfDocument = PdfDocument()
            
            // Standard A4 Size: 595 x 842 points
            val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
            val page = pdfDocument.startPage(pageInfo)
            val canvas: Canvas = page.canvas

            val paint = Paint()
            val textPaint = Paint().apply {
                isAntiAlias = true
                color = Color.BLACK
            }

            var yPosition = 40f

            // 1. Header Section
            paint.color = Color.parseColor("#1B5E20") // Sukrishna Dark Green
            canvas.drawRect(20f, 20f, 575f, 90f, paint)

            textPaint.apply {
                color = Color.WHITE
                textSize = 20f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            canvas.drawText("SHUKRISHNA ATTENDANCE REPORT", 40f, 55f, textPaint)

            textPaint.apply {
                textSize = 10f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            }
            canvas.drawText("Sukrishna Commerce, Kankarbagh", 40f, 75f, textPaint)

            val currentDate = SimpleDateFormat("dd-MM-yyyy HH:mm", Locale.getDefault()).format(Date())
            canvas.drawText("Generated: $currentDate", 430f, 75f, textPaint)

            // Reset paint for text
            textPaint.color = Color.BLACK
            yPosition = 120f

            // 2. Student Info
            textPaint.apply {
                textSize = 12f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            canvas.drawText("Student Profile:", 30f, yPosition, textPaint)
            yPosition += 18f

            textPaint.apply {
                textSize = 10f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            }
            canvas.drawText("Name: Pranay Sah", 30f, yPosition, textPaint)
            canvas.drawText("Institution: Sukrishna Commerce, Kankarbagh", 250f, yPosition, textPaint)
            yPosition += 30f

            // 3. Stats Summary Cards
            val totalDays = records.count { it.status in listOf("Present", "Absent", "Forgot OUTWARD") }
            val presentDays = records.count { it.status == "Present" }
            val absentDays = records.count { it.status == "Absent" }
            val holidayDays = records.count { it.status == "Holiday" || it.status == "Sunday" }
            val totalHours = records.sumOf { it.studyHours }
            val ratio = if (totalDays > 0) (presentDays.toDouble() / totalDays) * 100 else 100.0

            // Draw Box backgrounds
            paint.color = Color.parseColor("#F1F8E9") // Very Light Green
            canvas.drawRect(30f, yPosition, 150f, yPosition + 60f, paint)
            canvas.drawRect(165f, yPosition, 285f, yPosition + 60f, paint)
            canvas.drawRect(300f, yPosition, 420f, yPosition + 60f, paint)
            canvas.drawRect(435f, yPosition, 555f, yPosition + 60f, paint)

            // Write Card Text
            textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textPaint.textSize = 9f
            textPaint.color = Color.parseColor("#33691E")

            // Card 1: Attendance %
            canvas.drawText("ATTENDANCE %", 40f, yPosition + 18f, textPaint)
            textPaint.textSize = 16f
            canvas.drawText("${String.format("%.1f", ratio)}%", 40f, yPosition + 45f, textPaint)

            // Card 2: Study Hours
            textPaint.textSize = 9f
            canvas.drawText("TOTAL STUDY HRS", 175f, yPosition + 18f, textPaint)
            textPaint.textSize = 16f
            canvas.drawText("${String.format("%.1f", totalHours)}h", 175f, yPosition + 45f, textPaint)

            // Card 3: Days Present/Absent
            textPaint.textSize = 9f
            canvas.drawText("PRESENT / ABSENT", 310f, yPosition + 18f, textPaint)
            textPaint.textSize = 16f
            canvas.drawText("$presentDays / $absentDays", 310f, yPosition + 45f, textPaint)

            // Card 4: Holidays
            textPaint.textSize = 9f
            canvas.drawText("HOLIDAYS / SUN", 445f, yPosition + 18f, textPaint)
            textPaint.textSize = 16f
            canvas.drawText("$holidayDays Days", 445f, yPosition + 45f, textPaint)

            yPosition += 90f

            // 4. Detailed History Table Header
            paint.color = Color.parseColor("#E0E0E0")
            canvas.drawRect(30f, yPosition, 555f, yPosition + 25f, paint)

            textPaint.apply {
                color = Color.BLACK
                textSize = 9f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            canvas.drawText("DATE", 40f, yPosition + 16f, textPaint)
            canvas.drawText("DAY", 110f, yPosition + 16f, textPaint)
            canvas.drawText("STATUS", 180f, yPosition + 16f, textPaint)
            canvas.drawText("INWARD", 280f, yPosition + 16f, textPaint)
            canvas.drawText("OUTWARD", 350f, yPosition + 16f, textPaint)
            canvas.drawText("DURATION", 420f, yPosition + 16f, textPaint)
            canvas.drawText("SUBJECTS", 485f, yPosition + 16f, textPaint)

            yPosition += 25f

            // Detailed History Table Rows
            textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            val sortedRecords = records.sortedByDescending { DateTimeUtils.parseDate(it.date)?.time ?: 0L }

            // Take the most recent 20 records for standard single page PDF limit
            val recordsToPrint = sortedRecords.take(18)

            for (record in recordsToPrint) {
                // Draw row divider
                paint.color = Color.parseColor("#EEEEEE")
                canvas.drawLine(30f, yPosition, 555f, yPosition, paint)

                yPosition += 20f

                if (yPosition > 800f) {
                    break // Avoid drawing off-page in simple 1-page report
                }

                val dayOfWeek = DateTimeUtils.getDayOfWeek(record.date).take(3) // Mon, Tue, etc.
                val subjects = DateTimeUtils.getExpectedSubjectsForDay(DateTimeUtils.getDayOfWeek(record.date)).joinToString(", ")

                canvas.drawText(record.date, 40f, yPosition - 5f, textPaint)
                canvas.drawText(dayOfWeek, 110f, yPosition - 5f, textPaint)
                
                // Color status text beautifully
                val statusPaint = Paint(textPaint).apply {
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    color = when (record.status) {
                        "Present" -> Color.parseColor("#2E7D32")
                        "Absent" -> Color.parseColor("#C62828")
                        "Holiday", "Sunday" -> Color.parseColor("#1565C0")
                        "Still in Coaching" -> Color.parseColor("#F57F17")
                        "Forgot OUTWARD" -> Color.parseColor("#D84315")
                        else -> Color.BLACK
                    }
                }
                canvas.drawText(record.status, 180f, yPosition - 5f, statusPaint)
                
                canvas.drawText(record.inwardTime ?: "-", 280f, yPosition - 5f, textPaint)
                canvas.drawText(record.outwardTime ?: "-", 350f, yPosition - 5f, textPaint)
                canvas.drawText(if (record.studyHours > 0) "${record.studyHours} hrs" else "-", 420f, yPosition - 5f, textPaint)
                canvas.drawText(subjects.take(12) + (if (subjects.length > 12) ".." else ""), 485f, yPosition - 5f, textPaint)
            }

            // Footer note
            paint.color = Color.parseColor("#9E9E9E")
            canvas.drawLine(30f, 810f, 555f, 810f, paint)
            textPaint.apply {
                textSize = 8f
                color = Color.GRAY
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.ITALIC)
            }
            canvas.drawText("Pranay Sah | Shukrishna Attendance Tracker | Sukrishna Commerce Kankarbagh", 40f, 825f, textPaint)
            canvas.drawText("Page 1 of 1", 500f, 825f, textPaint)

            pdfDocument.finishPage(page)
            pdfDocument.writeTo(outputStream)
            pdfDocument.close()
            outputStream.close()

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("PdfExporter", "Error generating PDF: ${e.message}", e)
            Result.failure(e)
        }
    }
}
