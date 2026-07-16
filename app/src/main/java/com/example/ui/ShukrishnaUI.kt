package com.example.ui

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.R
import com.example.data.AttendanceRecord
import com.example.utils.DateTimeUtils
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShukrishnaAppUI(viewModel: AttendanceViewModel) {
    val context = LocalContext.current
    val records by viewModel.allRecords.collectAsState()
    val filteredRecords by viewModel.filteredRecords.collectAsState()
    val stats by viewModel.dashboardStats.collectAsState()
    val insights by viewModel.smartInsights.collectAsState()
    val syncState by viewModel.syncStatus.collectAsState()

    var currentTab by remember { mutableStateOf("Dashboard") }
    var selectedRecordForDetail by remember { mutableStateOf<AttendanceRecord?>(null) }
    var showManualAddDialog by remember { mutableStateOf(false) }

    // Theme configuration based on settings
    val appThemeColor = when (viewModel.themeColor.collectAsState().value) {
        "Slate" -> Color(0xFF455A64)
        "Blue" -> Color(0xFF1565C0)
        "Purple" -> Color(0xFF6A1B9A)
        else -> Color(0xFF1B5E20) // Default green
    }

    // PDF Exporter Launcher
    val pdfExportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri ->
        uri?.let {
            viewModel.exportReportToPdf(it) { success, message ->
                Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Shukrishna Tracker",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                        Text(
                            text = "Pranay Sah • Sukrishna Commerce",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.syncNow() }) {
                        Icon(
                            imageVector = Icons.Default.Sync,
                            contentDescription = "Sync Now",
                            tint = if (syncState is SyncState.Syncing) appThemeColor else MaterialTheme.colorScheme.onSurface
                        )
                    }
                    if (currentTab == "Calendar") {
                        IconButton(onClick = { showManualAddDialog = true }) {
                            Icon(Icons.Default.Add, contentDescription = "Add Manual Entry")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp)
                )
            )
        },
        bottomBar = {
            NavigationBar {
                val tabs = listOf(
                    NavigationTab("Dashboard", Icons.Default.Dashboard),
                    NavigationTab("Calendar", Icons.Default.CalendarMonth),
                    NavigationTab("Analytics", Icons.Default.Analytics),
                    NavigationTab("Subjects", Icons.Default.Schedule),
                    NavigationTab("Search", Icons.Default.Search),
                    NavigationTab("Settings", Icons.Default.Settings)
                )
                tabs.forEach { tab ->
                    NavigationBarItem(
                        selected = currentTab == tab.name,
                        onClick = { currentTab = tab.name },
                        icon = { Icon(tab.icon, contentDescription = tab.name) },
                        label = { Text(tab.name, fontSize = 10.sp) }
                    )
                }
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            AnimatedContent(
                targetState = currentTab,
                transitionSpec = {
                    fadeIn() togetherWith fadeOut()
                },
                label = "ScreenTransition"
            ) { targetTab ->
                when (targetTab) {
                    "Dashboard" -> DashboardScreen(
                        stats = stats,
                        insights = insights,
                        appThemeColor = appThemeColor,
                        onSyncClick = { viewModel.syncNow() }
                    )
                    "Calendar" -> CalendarScreen(
                        records = records,
                        appThemeColor = appThemeColor,
                        onDateClick = { selectedRecordForDetail = it }
                    )
                    "Analytics" -> AnalyticsScreen(
                        records = records,
                        stats = stats,
                        appThemeColor = appThemeColor
                    )
                    "Subjects" -> TimetableScreen(appThemeColor = appThemeColor)
                    "Search" -> SearchScreen(
                        filteredRecords = filteredRecords,
                        searchQuery = viewModel.searchQuery.collectAsState().value,
                        statusFilter = viewModel.statusFilter.collectAsState().value,
                        onSearchChange = { viewModel.updateSearchQuery(it) },
                        onFilterChange = { viewModel.updateStatusFilter(it) },
                        onRecordClick = { selectedRecordForDetail = it }
                    )
                    "Settings" -> SettingsScreen(
                        viewModel = viewModel,
                        appThemeColor = appThemeColor,
                        onExportPdf = {
                            val today = DateTimeUtils.getTodayDate()
                            pdfExportLauncher.launch("Sukrishna_Attendance_Report_$today.pdf")
                        }
                    )
                }
            }

            // Sync Status Indicator Overlay
            if (syncState is SyncState.Syncing) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.3f)),
                    contentAlignment = Alignment.Center
                ) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(24.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            CircularProgressIndicator(color = appThemeColor)
                            Text("Synchronizing with Firebase...", fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }
        }
    }

    // Manual Entry Dialog
    if (showManualAddDialog) {
        ManualEntryDialog(
            onDismiss = { showManualAddDialog = false },
            onConfirm = { date, inward, outward, status, note ->
                viewModel.insertManualEntry(date, inward, outward, status, note)
                showManualAddDialog = false
            }
        )
    }

    // Date Details Sheet / Dialog
    selectedRecordForDetail?.let { record ->
        AttendanceDetailDialog(
            record = record,
            onDismiss = { selectedRecordForDetail = null },
            onDelete = {
                viewModel.deleteRecord(record)
                selectedRecordForDetail = null
            },
            onSaveEdit = { inward, outward, status, note ->
                viewModel.insertManualEntry(record.date, inward, outward, status, note)
                selectedRecordForDetail = null
            }
        )
    }
}

data class NavigationTab(val name: String, val icon: androidx.compose.ui.graphics.vector.ImageVector)

// --- DASHBOARD SCREEN ---
@Composable
fun DashboardScreen(
    stats: DashboardStats,
    insights: List<String>,
    appThemeColor: Color,
    onSyncClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Hero Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                Image(
                    painter = painterResource(id = R.drawable.img_hero_banner_1784212031236),
                    contentDescription = "Workspace Banner",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                // Black semi-transparent overlay for text legibility
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f))
                            )
                        )
                )
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(16.dp)
                ) {
                    Text(
                        text = "Sukrishna Commerce",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp
                    )
                    Text(
                        text = "Kankarbagh, Patna • Student Workspace",
                        color = Color.LightGray,
                        fontSize = 12.sp
                    )
                }
            }
        }

        // Today's Status Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(2.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("TODAY'S STATUS", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = appThemeColor)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stats.todayAttendance,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = when (stats.todayAttendance) {
                            "Present" -> Color(0xFF2E7D32)
                            "Still in Coaching" -> Color(0xFFFBC02D)
                            "Forgot OUTWARD" -> Color(0xFFE65100)
                            "Holiday", "Sunday" -> Color(0xFF1976D2)
                            "Absent" -> Color(0xFFC62828)
                            else -> MaterialTheme.colorScheme.onSurface
                        }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Subjects: ${if (stats.todaySubjects.isEmpty()) "None" else stats.todaySubjects.joinToString(", ")}",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                // Hour Circle status
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .drawBehind {
                            drawCircle(
                                color = appThemeColor.copy(alpha = 0.2f),
                                style = Stroke(width = 6.dp.toPx())
                            )
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = String.format("%.1f", stats.todayStudyHours),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text("HRS", fontSize = 8.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        // Metrics Grid (2 Columns)
        Row(modifier = Modifier.fillMaxWidth()) {
            // Left Column
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                MetricCard(
                    title = "Attendance Ratio",
                    value = "${String.format("%.1f", stats.attendancePercentage)}%",
                    icon = Icons.Default.Percent,
                    color = Color(0xFF2E7D32)
                )
                MetricCard(
                    title = "Current Streak",
                    value = "${stats.currentStreak} Days",
                    icon = Icons.Default.Whatshot,
                    color = Color(0xFFFF8F00)
                )
                MetricCard(
                    title = "Avg Arrival",
                    value = stats.averageArrivalTime.ifEmpty { "--:--" },
                    icon = Icons.Default.Login,
                    color = Color(0xFF1565C0)
                )
                MetricCard(
                    title = "Late Arrivals",
                    value = "${stats.lateArrivals} Times",
                    icon = Icons.Default.ReportProblem,
                    color = Color(0xFFC62828)
                )
            }

            // Right Column
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                MetricCard(
                    title = "Month Study Hours",
                    value = "${String.format("%.1f", stats.monthlyHours)}h",
                    icon = Icons.Default.AccessTime,
                    color = Color(0xFF00838F)
                )
                MetricCard(
                    title = "Longest Streak",
                    value = "${stats.longestStreak} Days",
                    icon = Icons.Default.WorkspacePremium,
                    color = Color(0xFFAD1457)
                )
                MetricCard(
                    title = "Avg Leaving",
                    value = stats.averageLeavingTime.ifEmpty { "--:--" },
                    icon = Icons.Default.Logout,
                    color = Color(0xFF558B2F)
                )
                MetricCard(
                    title = "Early Exits",
                    value = "${stats.earlyExits} Times",
                    icon = Icons.Default.TimerOff,
                    color = Color(0xFFD84315)
                )
            }
        }

        // Smart Insights Section
        Text(
            text = "Smart Academic Insights",
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp,
            color = appThemeColor,
            modifier = Modifier.padding(top = 8.dp)
        )

        insights.forEach { insight ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp)
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Lightbulb,
                        contentDescription = "Insight",
                        tint = Color(0xFFFFB300),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = insight,
                        fontSize = 12.sp,
                        lineHeight = 16.sp
                    )
                }
            }
        }
    }
}

@Composable
fun MetricCard(title: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector, color: Color) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = title, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Icon(imageVector = icon, contentDescription = title, tint = color, modifier = Modifier.size(16.dp))
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = value, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = color)
        }
    }
}


// --- CALENDAR SCREEN ---
@Composable
fun CalendarScreen(
    records: List<AttendanceRecord>,
    appThemeColor: Color,
    onDateClick: (AttendanceRecord) -> Unit
) {
    val calendar = Calendar.getInstance()
    var currentYear by remember { mutableStateOf(calendar.get(Calendar.YEAR)) }
    var currentMonth by remember { mutableStateOf(calendar.get(Calendar.MONTH)) }

    val monthNames = listOf(
        "January", "February", "March", "April", "May", "June", 
        "July", "August", "September", "October", "November", "December"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Month Controller
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = {
                if (currentMonth == 0) {
                    currentMonth = 11
                    currentYear--
                } else {
                    currentMonth--
                }
            }) {
                Icon(Icons.Default.ArrowBackIos, contentDescription = "Previous Month")
            }

            Text(
                text = "${monthNames[currentMonth]} $currentYear",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = appThemeColor
            )

            IconButton(onClick = {
                if (currentMonth == 11) {
                    currentMonth = 0
                    currentYear++
                } else {
                    currentMonth++
                }
            }) {
                Icon(Icons.Default.ArrowForwardIos, contentDescription = "Next Month")
            }
        }

        // Days of week header row
        Row(modifier = Modifier.fillMaxWidth()) {
            val days = listOf("Su", "Mo", "Tu", "We", "Th", "Fr", "Sa")
            days.forEach { d ->
                Text(
                    text = d,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Calendar Grid
        val daysInMonth = getDaysInMonth(currentMonth, currentYear)
        val firstDayOffset = getFirstDayOffset(currentMonth, currentYear)

        val totalCells = daysInMonth + firstDayOffset
        val rows = (totalCells + 6) / 7

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            for (r in 0 until rows) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    for (c in 0..6) {
                        val cellIndex = r * 7 + c
                        val day = cellIndex - firstDayOffset + 1

                        if (cellIndex in firstDayOffset until totalCells) {
                            val formattedDate = String.format("%02d-%02d-%d", day, currentMonth + 1, currentYear)
                            val record = records.find { it.date == formattedDate } ?: AttendanceRecord(
                                date = formattedDate,
                                studentName = "Pranay Sah",
                                coachingName = "Sukrishna Commerce",
                                status = "No Entry"
                            )

                            // Status color selection
                            val colorScheme = when (record.status) {
                                "Present" -> CalendarColors(
                                    bg = Color(0xFFE8F5E9),
                                    border = Color(0xFF2E7D32),
                                    text = Color(0xFF1B5E20)
                                )
                                "Absent" -> CalendarColors(
                                    bg = Color(0xFFFFEBEE),
                                    border = Color(0xFFC62828),
                                    text = Color(0xFFB71C1C)
                                )
                                "Holiday" -> CalendarColors(
                                    bg = Color(0xFFE3F2FD),
                                    border = Color(0xFF1565C0),
                                    text = Color(0xFF0D47A1)
                                )
                                "Sunday" -> CalendarColors(
                                    bg = Color(0xFFECEFF1),
                                    border = Color(0xFF78909C),
                                    text = Color(0xFF37474F)
                                )
                                "Still in Coaching" -> CalendarColors(
                                    bg = Color(0xFFFFFDE7),
                                    border = Color(0xFFFBC02D),
                                    text = Color(0xFFF57F17)
                                )
                                "Forgot OUTWARD" -> CalendarColors(
                                    bg = Color(0xFFFFF3E0),
                                    border = Color(0xFFE65100),
                                    text = Color(0xFFE65100)
                                )
                                else -> CalendarColors(
                                    bg = Color.Transparent,
                                    border = MaterialTheme.colorScheme.outlineVariant,
                                    text = MaterialTheme.colorScheme.onSurface
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .aspectRatio(1f)
                                    .padding(2.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(colorScheme.bg)
                                    .border(1.dp, colorScheme.border, RoundedCornerShape(8.dp))
                                    .clickable { onDateClick(record) },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = day.toString(),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    color = colorScheme.text
                                )
                            }
                        } else {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }

        // Color coding guidelines description
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp))
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Color Legends", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = appThemeColor)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    LegendItem("Present", Color(0xFF2E7D32), Modifier.weight(1f))
                    LegendItem("Absent", Color(0xFFC62828), Modifier.weight(1f))
                    LegendItem("Holiday", Color(0xFF1565C0), Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    LegendItem("Sunday", Color(0xFF78909C), Modifier.weight(1f))
                    LegendItem("In Coaching", Color(0xFFFBC02D), Modifier.weight(1f))
                    LegendItem("No Outward", Color(0xFFE65100), Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
fun LegendItem(label: String, color: Color, modifier: Modifier = Modifier) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier) {
        Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(color))
        Spacer(modifier = Modifier.width(6.dp))
        Text(label, fontSize = 10.sp)
    }
}

data class CalendarColors(val bg: Color, val border: Color, val text: Color)

private fun getDaysInMonth(month: Int, year: Int): Int {
    val cal = Calendar.getInstance()
    cal.set(Calendar.YEAR, year)
    cal.set(Calendar.MONTH, month)
    return cal.getActualMaximum(Calendar.DAY_OF_MONTH)
}

private fun getFirstDayOffset(month: Int, year: Int): Int {
    val cal = Calendar.getInstance()
    cal.set(Calendar.YEAR, year)
    cal.set(Calendar.MONTH, month)
    cal.set(Calendar.DAY_OF_MONTH, 1)
    return cal.get(Calendar.DAY_OF_WEEK) - 1 // Sunday is 1, return 0 offset
}


// --- ANALYTICS SCREEN WITH CUSTOM CANVAS GRAPHICS ---
@Composable
fun AnalyticsScreen(
    records: List<AttendanceRecord>,
    stats: DashboardStats,
    appThemeColor: Color
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Visual Insights", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = appThemeColor)

        // Graph 1: Weekly/Daily Study Hours bar chart
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Study Hours (Recent Days)", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                Spacer(modifier = Modifier.height(16.dp))
                
                // Draw custom bar chart
                CustomBarChart(records = records, barColor = appThemeColor)
            }
        }

        // Graph 2: Attendance Status Ratio Pie Chart
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Attendance State Distribution", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                Spacer(modifier = Modifier.height(16.dp))

                CustomPieChart(records = records)
            }
        }

        // Graph 3: Subject Hours Analysis
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Subject Hours Share", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                Spacer(modifier = Modifier.height(16.dp))

                CustomSubjectBarChart(records = records, tint = appThemeColor)
            }
        }
    }
}

@Composable
fun CustomBarChart(records: List<AttendanceRecord>, barColor: Color) {
    val recentRecords = records.sortedBy { DateTimeUtils.parseDate(it.date)?.time ?: 0L }
        .takeLast(7)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp)
            .padding(8.dp)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = size.width
            val height = size.height

            // Y Axis Guidelines (0, 2, 4 hours)
            val yLines = listOf(0.0, 2.0, 4.0)
            val maxVal = 4.0

            yLines.forEach { valY ->
                val ratio = (valY / maxVal).toFloat()
                val drawY = height - (ratio * height)
                drawLine(
                    color = Color.LightGray.copy(alpha = 0.5f),
                    start = Offset(0f, drawY),
                    end = Offset(width, drawY),
                    strokeWidth = 1f
                )
            }

            if (recentRecords.isEmpty()) return@Canvas

            val colWidth = width / recentRecords.size
            val barW = colWidth * 0.4f

            recentRecords.forEachIndexed { index, rec ->
                val hHours = rec.studyHours
                val ratio = (hHours / maxVal).toFloat().coerceAtMost(1.0f)
                val barH = ratio * height

                val startX = index * colWidth + (colWidth - barW) / 2
                val startY = height - barH

                // Draw solid bar
                drawRect(
                    color = if (hHours > 0) barColor else Color.LightGray.copy(alpha = 0.3f),
                    topLeft = Offset(startX, startY),
                    size = androidx.compose.ui.geometry.Size(barW, barH)
                )
            }
        }

        // Render Labels below Canvas
        Row(
            modifier = Modifier.fillMaxWidth().align(Alignment.BottomCenter).offset(y = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            recentRecords.forEach { rec ->
                Text(
                    text = rec.date.take(5), // "dd-MM"
                    fontSize = 8.sp,
                    color = Color.Gray,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
fun CustomPieChart(records: List<AttendanceRecord>) {
    val present = records.count { it.status == "Present" }
    val absent = records.count { it.status == "Absent" }
    val holiday = records.count { it.status == "Holiday" || it.status == "Sunday" }
    val exceptions = records.count { it.status == "Forgot OUTWARD" }

    val total = (present + absent + holiday + exceptions).toFloat()

    if (total == 0f) {
        Box(modifier = Modifier.fillMaxWidth().height(140.dp), contentAlignment = Alignment.Center) {
            Text("No distribution data yet", color = Color.Gray, fontSize = 12.sp)
        }
        return
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        Box(modifier = Modifier.size(120.dp), contentAlignment = Alignment.Center) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                var currentAngle = 0f

                val slices = listOf(
                    PieSlice(present.toFloat() / total, Color(0xFF2E7D32)),
                    PieSlice(absent.toFloat() / total, Color(0xFFC62828)),
                    PieSlice(holiday.toFloat() / total, Color(0xFF1565C0)),
                    PieSlice(exceptions.toFloat() / total, Color(0xFFE65100))
                )

                slices.forEach { slice ->
                    val sweep = slice.ratio * 360f
                    if (sweep > 0f) {
                        drawArc(
                            color = slice.color,
                            startAngle = currentAngle,
                            sweepAngle = sweep,
                            useCenter = true
                        )
                        currentAngle += sweep
                    }
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            PieLegendItem("Present ($present)", Color(0xFF2E7D32))
            PieLegendItem("Absent ($absent)", Color(0xFFC62828))
            PieLegendItem("Holidays/Sundays ($holiday)", Color(0xFF1565C0))
            PieLegendItem("Omission Alert ($exceptions)", Color(0xFFE65100))
        }
    }
}

@Composable
fun PieLegendItem(label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(12.dp).clip(RoundedCornerShape(2.dp)).background(color))
        Spacer(modifier = Modifier.width(8.dp))
        Text(label, fontSize = 11.sp, fontWeight = FontWeight.Medium)
    }
}

data class PieSlice(val ratio: Float, val color: Color)

@Composable
fun CustomSubjectBarChart(records: List<AttendanceRecord>, tint: Color) {
    // Subject Hours calculation
    // BST, Accounts, Statistics, English, Maths
    var bst = 0.0
    var accounts = 0.0
    var stats = 0.0
    var english = 0.0
    var maths = 0.0

    records.filter { it.status == "Present" }.forEach { r ->
        val day = DateTimeUtils.getDayOfWeek(r.date).lowercase()
        val hours = r.studyHours
        if (day in listOf("monday", "wednesday", "friday")) {
            // Monday/Wed/Fri has 3 classes: BST (1h), Accounts (1h), Stats (1h)
            // If studied hours is full, add equally
            val share = hours / 3.0
            bst += share
            accounts += share
            stats += share
        } else if (day in listOf("tuesday", "thursday", "saturday")) {
            // English (1h), Maths (1h)
            val share = hours / 2.0
            english += share
            maths += share
        }
    }

    val subjects = listOf(
        SubjectStats("BST", bst, Color(0xFF1B5E20)),
        SubjectStats("Accounts", accounts, Color(0xFF1565C0)),
        SubjectStats("Statistics", stats, Color(0xFF8E24AA)),
        SubjectStats("English", english, Color(0xFFE65100)),
        SubjectStats("Maths", maths, Color(0xFF00838F))
    )

    val maxHours = (subjects.maxOf { it.hours }).coerceAtLeast(1.0)

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        subjects.forEach { s ->
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(s.name, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Text("${String.format("%.1f", s.hours)} hours", fontSize = 11.sp, color = Color.Gray)
                }
                Spacer(modifier = Modifier.height(4.dp))
                // Progress Bar representation
                val ratio = (s.hours / maxHours).toFloat()
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color.LightGray.copy(alpha = 0.2f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(ratio)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(4.dp))
                            .background(s.color)
                    )
                }
            }
        }
    }
}

data class SubjectStats(val name: String, val hours: Double, val color: Color)


// --- TIMETABLE SCREEN ---
@Composable
fun TimetableScreen(appThemeColor: Color) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Timetable Schedule", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = appThemeColor)

        // MWF Schedule
        Text("Monday, Wednesday, Friday", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = appThemeColor)
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                TimetableItem(time = "03:00 PM - 04:00 PM", subject = "Business Studies (BST)", expected = "1 Hour", color = Color(0xFF1B5E20))
                Divider()
                TimetableItem(time = "04:00 PM - 05:00 PM", subject = "Accounts", expected = "1 Hour", color = Color(0xFF1565C0))
                Divider()
                TimetableItem(time = "05:00 PM - 06:00 PM", subject = "Statistics", expected = "1 Hour", color = Color(0xFF8E24AA))
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // TTS Schedule
        Text("Tuesday, Thursday, Saturday", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = appThemeColor)
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                TimetableItem(time = "04:00 PM - 05:00 PM", subject = "English", expected = "1 Hour", color = Color(0xFFE65100))
                Divider()
                TimetableItem(time = "05:00 PM - 06:00 PM", subject = "Maths", expected = "1 Hour", color = Color(0xFF00838F))
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Sunday
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp))
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Weekend, contentDescription = "Sunday", tint = Color.Gray)
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text("Sunday", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text("Automatically marked as a Holiday.", fontSize = 11.sp, color = Color.Gray)
                }
            }
        }
    }
}

@Composable
fun TimetableItem(time: String, subject: String, expected: String, color: Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(4.dp, 24.dp).clip(RoundedCornerShape(2.dp)).background(color))
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(subject, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Text(time, fontSize = 11.sp, color = Color.Gray)
            }
        }
        Text(expected, fontSize = 11.sp, fontWeight = FontWeight.Medium, color = color)
    }
}


// --- SEARCH SCREEN ---
@Composable
fun SearchScreen(
    filteredRecords: List<AttendanceRecord>,
    searchQuery: String,
    statusFilter: String,
    onSearchChange: (String) -> Unit,
    onFilterChange: (String) -> Unit,
    onRecordClick: (AttendanceRecord) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Search Input
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Search by Date, Subject, Day...") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search") },
            singleLine = true,
            shape = RoundedCornerShape(12.dp)
        )

        // Status filter Row
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val filters = listOf("All", "Present", "Absent", "Holiday", "Sunday", "Still in Coaching", "Forgot OUTWARD")
            filters.forEach { f ->
                FilterChip(
                    selected = statusFilter == f,
                    onClick = { onFilterChange(f) },
                    label = { Text(f, fontSize = 11.sp) }
                )
            }
        }

        // List of Results
        if (filteredRecords.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.HourglassEmpty, contentDescription = "Empty", tint = Color.Gray, modifier = Modifier.size(48.dp))
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("No records matched your search.", color = Color.Gray, fontSize = 12.sp)
                }
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(filteredRecords) { rec ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onRecordClick(rec) }
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(rec.date, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(DateTimeUtils.getDayOfWeek(rec.date).take(3), fontSize = 11.sp, color = Color.Gray)
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = if (rec.studyHours > 0.0) "Studied: ${DateTimeUtils.formatHours(rec.studyHours)}" else "No Study Duration",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            // Status badge
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(
                                        when (rec.status) {
                                            "Present" -> Color(0xFFE8F5E9)
                                            "Absent" -> Color(0xFFFFEBEE)
                                            "Holiday" -> Color(0xFFE3F2FD)
                                            "Sunday" -> Color(0xFFECEFF1)
                                            "Still in Coaching" -> Color(0xFFFFFDE7)
                                            else -> Color(0xFFFFF3E0)
                                        }
                                    )
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = rec.status,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = when (rec.status) {
                                        "Present" -> Color(0xFF2E7D32)
                                        "Absent" -> Color(0xFFC62828)
                                        "Holiday" -> Color(0xFF1565C0)
                                        "Sunday" -> Color(0xFF37474F)
                                        "Still in Coaching" -> Color(0xFFF57F17)
                                        else -> Color(0xFFE65100)
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}


// --- SETTINGS SCREEN ---
@Composable
fun SettingsScreen(
    viewModel: AttendanceViewModel,
    appThemeColor: Color,
    onExportPdf: () -> Unit
) {
    val isDark by viewModel.isDarkMode.collectAsState()
    val themeChoice by viewModel.themeColor.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Preferences & Backups", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = appThemeColor)

        // Custom Themes Row
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Theme Accents", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    val colors = listOf("Green", "Slate", "Blue", "Purple")
                    colors.forEach { c ->
                        Button(
                            onClick = { viewModel.setThemeColor(c) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (themeChoice == c) appThemeColor else Color.LightGray.copy(alpha = 0.2f),
                                contentColor = if (themeChoice == c) Color.White else MaterialTheme.colorScheme.onSurface
                            ),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(c, fontSize = 11.sp)
                        }
                    }
                }
            }
        }

        // Toggles
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Dark Mode", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Text("Comfortable visual styling", fontSize = 11.sp, color = Color.Gray)
                    }
                    Switch(checked = isDark, onCheckedChange = { viewModel.setDarkMode(it) })
                }
            }
        }

        // Actions
        Text("Exports & Administration", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = appThemeColor)

        Button(
            onClick = onExportPdf,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = appThemeColor)
        ) {
            Icon(Icons.Default.PictureAsPdf, contentDescription = "Export")
            Spacer(modifier = Modifier.width(8.dp))
            Text("Export Attendance as PDF")
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OutlinedButton(
                onClick = { viewModel.syncNow() },
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.CloudSync, contentDescription = "Sync Now")
                Spacer(modifier = Modifier.width(8.dp))
                Text("Sync Now", fontSize = 11.sp)
            }

            OutlinedButton(
                onClick = { viewModel.restoreBackup() },
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.Backup, contentDescription = "Restore")
                Spacer(modifier = Modifier.width(8.dp))
                Text("Restore Backup", fontSize = 11.sp)
            }
        }

        // Admin Profile Details
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp))
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Shukrishna Core v1.0", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                Text("Designed for student Pranay Sah, attending Sukrishna Commerce, Kankarbagh. Integrates WorkManager periodic daemons, automatic SQLite caching, and live Firebase Realtime Database cloud backups.", fontSize = 11.sp, color = Color.Gray, lineHeight = 16.sp)
            }
        }
    }
}


// --- ATTENDANCE DETAIL SHEET / DIALOG ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AttendanceDetailDialog(
    record: AttendanceRecord,
    onDismiss: () -> Unit,
    onDelete: () -> Unit,
    onSaveEdit: (String?, String?, String, String?) -> Unit
) {
    var inward by remember { mutableStateOf(record.inwardTime ?: "") }
    var outward by remember { mutableStateOf(record.outwardTime ?: "") }
    var status by remember { mutableStateOf(record.status) }
    var note by remember { mutableStateOf(record.note ?: "") }

    val statusOptions = listOf("Present", "Absent", "Holiday", "Sunday", "Still in Coaching", "Forgot OUTWARD")

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Attendance details - ${record.date}", fontWeight = FontWeight.Bold, fontSize = 16.sp)

                // Status dropdown/pills
                Text("Status", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    statusOptions.forEach { opt ->
                        FilterChip(
                            selected = status == opt,
                            onClick = { status = opt },
                            label = { Text(opt, fontSize = 10.sp) }
                        )
                    }
                }

                OutlinedTextField(
                    value = inward,
                    onValueChange = { inward = it },
                    label = { Text("Inward Time (e.g. 03:08 PM)") },
                    singleLine = true,
                    shape = RoundedCornerShape(8.dp)
                )

                OutlinedTextField(
                    value = outward,
                    onValueChange = { outward = it },
                    label = { Text("Outward Time (e.g. 07:02 PM)") },
                    singleLine = true,
                    shape = RoundedCornerShape(8.dp)
                )

                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Internal Note") },
                    shape = RoundedCornerShape(8.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.Red)
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        TextButton(onClick = onDismiss) {
                            Text("Cancel")
                        }
                        Button(onClick = {
                            onSaveEdit(inward.ifEmpty { null }, outward.ifEmpty { null }, status, note.ifEmpty { null })
                        }) {
                            Text("Save")
                        }
                    }
                }
            }
        }
    }
}


// --- MANUAL ADD ENTRY DIALOG ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManualEntryDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, String?, String?, String, String?) -> Unit
) {
    var date by remember { mutableStateOf("") }
    var inward by remember { mutableStateOf("") }
    var outward by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("Present") }
    var note by remember { mutableStateOf("") }

    val statusOptions = listOf("Present", "Absent", "Holiday", "Sunday", "Still in Coaching", "Forgot OUTWARD")

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Add Manual Attendance", fontWeight = FontWeight.Bold, fontSize = 16.sp)

                OutlinedTextField(
                    value = date,
                    onValueChange = { date = it },
                    label = { Text("Date (dd-MM-yyyy)") },
                    singleLine = true,
                    shape = RoundedCornerShape(8.dp)
                )

                // Status choices
                Text("Status", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    statusOptions.forEach { opt ->
                        FilterChip(
                            selected = status == opt,
                            onClick = { status = opt },
                            label = { Text(opt, fontSize = 10.sp) }
                        )
                    }
                }

                OutlinedTextField(
                    value = inward,
                    onValueChange = { inward = it },
                    label = { Text("Inward Time (e.g. 03:08 PM)") },
                    singleLine = true,
                    shape = RoundedCornerShape(8.dp)
                )

                OutlinedTextField(
                    value = outward,
                    onValueChange = { outward = it },
                    label = { Text("Outward Time (e.g. 07:02 PM)") },
                    singleLine = true,
                    shape = RoundedCornerShape(8.dp)
                )

                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Internal Note") },
                    shape = RoundedCornerShape(8.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Button(
                        onClick = {
                            if (date.isNotEmpty()) {
                                onConfirm(
                                    date, 
                                    inward.ifEmpty { null }, 
                                    outward.ifEmpty { null }, 
                                    status, 
                                    note.ifEmpty { null }
                                )
                            }
                        }
                    ) {
                        Text("Add")
                    }
                }
            }
        }
    }
}
