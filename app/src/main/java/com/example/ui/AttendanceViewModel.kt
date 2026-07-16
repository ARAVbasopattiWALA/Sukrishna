package com.example.ui

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.AttendanceRecord
import com.example.data.AttendanceRepository
import com.example.utils.DateTimeUtils
import com.example.utils.PdfExporter
import com.example.widget.AttendanceWidgetProvider
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class AttendanceViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getDatabase(application)
    private val repository = AttendanceRepository(application, db.attendanceDao())
    private val sharedPrefs: SharedPreferences = application.getSharedPreferences("shukrishna_prefs", Context.MODE_PRIVATE)

    // UI state flows
    val allRecords: StateFlow<List<AttendanceRecord>> = repository.allRecords
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // Settings States
    private val _isDarkMode = MutableStateFlow(sharedPrefs.getBoolean("dark_mode", true))
    val isDarkMode: StateFlow<Boolean> = _isDarkMode.asStateFlow()

    private val _themeColor = MutableStateFlow(sharedPrefs.getString("theme_color", "Green") ?: "Green")
    val themeColor: StateFlow<String> = _themeColor.asStateFlow()

    private val _syncStatus = MutableStateFlow<SyncState>(SyncState.Idle)
    val syncStatus: StateFlow<SyncState> = _syncStatus.asStateFlow()

    // Search Query & Filters
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _statusFilter = MutableStateFlow("All")
    val statusFilter: StateFlow<String> = _statusFilter.asStateFlow()

    // Filtered Records based on search
    val filteredRecords: StateFlow<List<AttendanceRecord>> = combine(
        allRecords, _searchQuery, _statusFilter
    ) { records, query, status ->
        records.filter { record ->
            val matchesQuery = query.isEmpty() ||
                record.date.contains(query, ignoreCase = true) ||
                record.studentName.contains(query, ignoreCase = true) ||
                DateTimeUtils.getDayOfWeek(record.date).contains(query, ignoreCase = true) ||
                DateTimeUtils.getExpectedSubjectsForDay(DateTimeUtils.getDayOfWeek(record.date)).any {
                    it.contains(query, ignoreCase = true)
                }
            val matchesStatus = status == "All" || record.status.equals(status, ignoreCase = true)
            matchesQuery && matchesStatus
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Live Dashboard Statistics
    val dashboardStats: StateFlow<DashboardStats> = allRecords.map { records ->
        calculateStats(records)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DashboardStats())

    // Live Insights based on stats
    val smartInsights: StateFlow<List<String>> = dashboardStats.map { stats ->
        generateInsightsList(stats)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Database Actions
    fun insertManualEntry(date: String, inward: String?, outward: String?, status: String, note: String?) {
        viewModelScope.launch {
            val inwardVal = inward?.trim()?.ifEmpty { null }
            val outwardVal = outward?.trim()?.ifEmpty { null }
            
            val studyHrs = if (inwardVal != null && outwardVal != null) {
                DateTimeUtils.calculateStudyHours(inwardVal, outwardVal)
            } else 0.0

            val record = AttendanceRecord(
                date = date,
                studentName = "Pranay Sah",
                coachingName = "Sukrishna Commerce",
                inwardTime = inwardVal,
                outwardTime = outwardVal,
                status = status,
                studyHours = studyHrs,
                isManual = true,
                note = note
            )
            repository.insertOrUpdate(record)
            AttendanceWidgetProvider.triggerWidgetUpdate(getApplication())
        }
    }

    fun deleteRecord(record: AttendanceRecord) {
        viewModelScope.launch {
            repository.deleteRecord(record)
            AttendanceWidgetProvider.triggerWidgetUpdate(getApplication())
        }
    }

    fun deleteRecordById(id: Int) {
        viewModelScope.launch {
            repository.deleteRecordById(id)
            AttendanceWidgetProvider.triggerWidgetUpdate(getApplication())
        }
    }

    fun setDarkMode(enabled: Boolean) {
        _isDarkMode.value = enabled
        sharedPrefs.edit().putBoolean("dark_mode", enabled).apply()
    }

    fun setThemeColor(colorName: String) {
        _themeColor.value = colorName
        sharedPrefs.edit().putString("theme_color", colorName).apply()
    }

    // Sync Commands
    fun syncNow() {
        viewModelScope.launch {
            _syncStatus.value = SyncState.Syncing
            val result = repository.syncNow()
            if (result.isSuccess) {
                _syncStatus.value = SyncState.Success
                AttendanceWidgetProvider.triggerWidgetUpdate(getApplication())
            } else {
                _syncStatus.value = SyncState.Error(result.exceptionOrNull()?.message ?: "Sync Failed")
            }
        }
    }

    fun restoreBackup() {
        // Since Firebase automatically caches locally and synchronizes, 
        // a simple database sync acts as a perfect data restoration!
        syncNow()
    }

    // PDF Export
    fun exportReportToPdf(uri: Uri, callback: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            val result = PdfExporter.exportToPdf(getApplication(), allRecords.value, uri)
            if (result.isSuccess) {
                callback(true, "PDF saved successfully")
            } else {
                callback(false, result.exceptionOrNull()?.message ?: "Failed to save PDF")
            }
        }
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun updateStatusFilter(status: String) {
        _statusFilter.value = status
    }

    // Analytical Math Algorithms
    private fun calculateStats(records: List<AttendanceRecord>): DashboardStats {
        val stats = DashboardStats()
        if (records.isEmpty()) return stats

        val todayDate = DateTimeUtils.getTodayDate()
        val todayRecord = records.find { it.date == todayDate }
        val dayOfWeekToday = DateTimeUtils.getDayOfWeek(todayDate)

        stats.todayAttendance = todayRecord?.status ?: "No Entry"
        stats.todaySubjects = DateTimeUtils.getExpectedSubjectsForDay(dayOfWeekToday)
        stats.todayStudyHours = todayRecord?.studyHours ?: 0.0

        val coachingRecords = records.filter { it.status in listOf("Present", "Absent", "Forgot OUTWARD") }
        stats.totalCoachingDays = coachingRecords.size
        stats.daysPresent = records.count { it.status == "Present" }
        stats.daysAbsent = records.count { it.status == "Absent" }
        stats.holidayCount = records.count { it.status == "Holiday" }
        stats.sundayCount = records.count { it.status == "Sunday" }

        if (stats.totalCoachingDays > 0) {
            stats.attendancePercentage = (stats.daysPresent.toDouble() / stats.totalCoachingDays) * 100
        }

        // Streak Calculations
        val sortedAsc = records.sortedBy { DateTimeUtils.parseDate(it.date)?.time ?: 0L }
        var tempStreak = 0
        var maxStreak = 0
        for (r in sortedAsc) {
            if (r.status in listOf("Present", "Holiday", "Sunday")) {
                tempStreak++
                if (tempStreak > maxStreak) {
                    maxStreak = tempStreak
                }
            } else if (r.status in listOf("Absent", "Forgot OUTWARD")) {
                tempStreak = 0
            }
        }
        stats.longestStreak = maxStreak

        // Current Streak
        var curStreak = 0
        val sortedDesc = records.sortedByDescending { DateTimeUtils.parseDate(it.date)?.time ?: 0L }
        for (r in sortedDesc) {
            if (r.status in listOf("Present", "Holiday", "Sunday")) {
                curStreak++
            } else if (r.status in listOf("Absent", "Forgot OUTWARD")) {
                break
            }
        }
        stats.currentStreak = curStreak

        // Monthly / Yearly Study Hours
        val cal = Calendar.getInstance()
        val curMonthStr = String.format("-%02d-", cal.get(Calendar.MONTH) + 1)
        val curYearStr = "-${cal.get(Calendar.YEAR)}"
        
        stats.monthlyHours = records.filter { it.date.contains(curMonthStr) }.sumOf { it.studyHours }
        stats.yearlyHours = records.filter { it.date.endsWith(curYearStr) }.sumOf { it.studyHours }

        // Average Arrival & Leaving Times, Late Arrivals, Early Exits
        val presentRecords = records.filter { it.status == "Present" }
        var totalArrivalMinutes = 0
        var arrivalCount = 0
        var totalLeavingMinutes = 0
        var leavingCount = 0
        var lateArrivals = 0
        var earlyExits = 0

        val timeSdf = SimpleDateFormat("hh:mm a", Locale.getDefault())

        for (r in presentRecords) {
            val dayOfWk = DateTimeUtils.getDayOfWeek(r.date)
            
            // Late Arrivals Check
            if (r.inwardTime != null) {
                try {
                    val inwardCal = Calendar.getInstance().apply { time = timeSdf.parse(r.inwardTime)!! }
                    val minutesInward = inwardCal.get(Calendar.HOUR_OF_DAY) * 60 + inwardCal.get(Calendar.MINUTE)
                    
                    totalArrivalMinutes += minutesInward
                    arrivalCount++

                    // Expected Start: Mon/Wed/Fri is 3:00 PM (15:00 = 900m), Tue/Thu/Sat is 4:00 PM (16:00 = 960m)
                    val expectedLimit = if (dayOfWk.lowercase() in listOf("monday", "wednesday", "friday")) {
                        910 // 3:10 PM
                    } else {
                        970 // 4:10 PM
                    }
                    if (minutesInward > expectedLimit) {
                        lateArrivals++
                    }
                } catch (e: Exception) {
                    Log.e("Stats", "Error parsing inward time: ${r.inwardTime}")
                }
            }

            // Early Exits Check
            if (r.outwardTime != null) {
                try {
                    val outwardCal = Calendar.getInstance().apply { time = timeSdf.parse(r.outwardTime)!! }
                    val minutesOutward = outwardCal.get(Calendar.HOUR_OF_DAY) * 60 + outwardCal.get(Calendar.MINUTE)

                    totalLeavingMinutes += minutesOutward
                    leavingCount++

                    // Expected Class Duration
                    val expectedHours = DateTimeUtils.getExpectedHoursForDay(dayOfWk)
                    if (r.studyHours < expectedHours - 0.25) { // Left > 15 mins early
                        earlyExits++
                    }
                } catch (e: Exception) {
                    Log.e("Stats", "Error parsing outward time: ${r.outwardTime}")
                }
            }
        }

        stats.lateArrivals = lateArrivals
        stats.earlyExits = earlyExits

        if (arrivalCount > 0) {
            val avgArrMinutes = totalArrivalMinutes / arrivalCount
            stats.averageArrivalTime = formatMinutesToTime(avgArrMinutes)
        }
        if (leavingCount > 0) {
            val avgLeaveMinutes = totalLeavingMinutes / leavingCount
            stats.averageLeavingTime = formatMinutesToTime(avgLeaveMinutes)
        }

        return stats
    }

    private fun formatMinutesToTime(totalMinutes: Int): String {
        val h = totalMinutes / 60
        val m = totalMinutes % 60
        val ampm = if (h >= 12) "PM" else "AM"
        val displayHour = when {
            h == 0 -> 12
            h > 12 -> h - 12
            else -> h
        }
        return String.format("%02d:%02d %s", displayHour, m, ampm)
    }

    private fun generateInsightsList(stats: DashboardStats): List<String> {
        val insights = mutableListOf<String>()
        if (stats.totalCoachingDays == 0) {
            return listOf("Welcome to Shukrishna! Attendance stats will appear once you receive class check-in SMS alerts.")
        }

        insights.add("You attended ${String.format("%.1f", stats.attendancePercentage)}% of classes.")
        insights.add("You studied total ${String.format("%.1f", stats.monthlyHours)} hours this month.")
        
        if (stats.averageArrivalTime.isNotEmpty()) {
            insights.add("Your average arrival time is ${stats.averageArrivalTime}.")
        }

        if (stats.longestStreak > 0) {
            insights.add("Your longest attendance streak is ${stats.longestStreak} days.")
        }

        if (stats.lateArrivals > 0) {
            insights.add("You were late to class ${stats.lateArrivals} times. Try to check-in on time!")
        }

        if (stats.earlyExits > 0) {
            insights.add("You exited class early ${stats.earlyExits} times.")
        }

        return insights
    }
}

sealed class SyncState {
    object Idle : SyncState()
    object Syncing : SyncState()
    object Success : SyncState()
    data class Error(val message: String) : SyncState()
}

data class DashboardStats(
    var todayAttendance: String = "No Entry",
    var todaySubjects: List<String> = emptyList(),
    var todayStudyHours: Double = 0.0,
    var attendancePercentage: Double = 100.0,
    var currentStreak: Int = 0,
    var longestStreak: Int = 0,
    var monthlyHours: Double = 0.0,
    var yearlyHours: Double = 0.0,
    var averageArrivalTime: String = "",
    var averageLeavingTime: String = "",
    var daysPresent: Int = 0,
    var daysAbsent: Int = 0,
    var totalCoachingDays: Int = 0,
    var holidayCount: Int = 0,
    var sundayCount: Int = 0,
    var lateArrivals: Int = 0,
    var earlyExits: Int = 0
)
