package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "attendance_records")
data class AttendanceRecord(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val date: String,             // Format: "dd-MM-yyyy"
    val studentName: String,      // e.g. "Pranay Sah"
    val coachingName: String,     // e.g. "Sukrishna Commerce"
    val inwardTime: String? = null, // Format: "hh:mm a" (e.g. "03:08 PM")
    val outwardTime: String? = null, // Format: "hh:mm a" (e.g. "07:02 PM")
    val status: String,           // "Present", "Absent", "Holiday", "Still in Coaching", "Forgot OUTWARD", "Sunday"
    val studyHours: Double = 0.0, // Calculated study duration in decimal hours
    val synced: Boolean = false,
    val isManual: Boolean = false,
    val note: String? = null
)
