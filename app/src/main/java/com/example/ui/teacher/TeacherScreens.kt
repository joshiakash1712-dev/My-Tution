package com.example.ui.teacher

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.*
import com.example.ui.attendance.TakeAttendanceWorkflow
import com.example.ui.components.*
import com.example.ui.timetable.DynamicTimetableScreen
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun TeacherMainContent(currentTab: Int, onTabChange: (Int) -> Unit = {}) {
  val students by AppRepository.students.collectAsState()
  val batches by AppRepository.batches.collectAsState()
  val tests by AppRepository.tests.collectAsState()
  val doubts by AppRepository.doubts.collectAsState()
  val timetable by AppRepository.timetable.collectAsState()
  val subjects by AppRepository.subjects.collectAsState()

  when (currentTab) {
    0 -> TeacherDashboardTab(batches, doubts, timetable, subjects, onTabChange)
    1 -> TeacherAttendanceTab(students, batches)
    2 -> TeacherTestsTab(tests, students)
    3 -> TeacherTimetableTab(timetable)
    4 -> TeacherDoubtsAndMaterialsTab(doubts)
  }
}

@Composable
fun TeacherDashboardTab(
  batches: List<Batch>,
  doubts: List<DoubtItem>,
  timetable: List<TimetableItem>,
  subjects: List<Subject>,
  onTabChange: (Int) -> Unit = {}
) {
  val pendingDoubtsCount = doubts.count { it.status == "Pending" }
  var showTeacherAnnouncementDialog by remember { mutableStateOf(false) }

  if (showTeacherAnnouncementDialog) {
    var selectedBatchForAnnc by remember { mutableStateOf(batches.firstOrNull()?.name ?: "All Batches") }
    var selectedAudience by remember { mutableStateOf("Everyone") }
    var anncTitle by remember { mutableStateOf("") }
    var anncMessage by remember { mutableStateOf("") }
    var announcementSentNotice by remember { mutableStateOf<String?>(null) }

    AlertDialog(
      onDismissRequest = { showTeacherAnnouncementDialog = false },
      containerColor = Color(0xFF111C2E),
      titleContentColor = Color.White,
      textContentColor = Color(0xFF94A3B8),
      title = {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          Icon(Icons.Default.Campaign, contentDescription = null, tint = Color(0xFF38BDF8))
          Text("Send Class Announcement", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.White)
        }
      },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
          if (announcementSentNotice != null) {
            Surface(
              color = Color(0xFF064E3B),
              border = BorderStroke(1.dp, Color(0xFF059669)),
              shape = RoundedCornerShape(8.dp),
              modifier = Modifier.fillMaxWidth()
            ) {
              Text(
                announcementSentNotice!!,
                color = Color(0xFF4ADE80),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(10.dp)
              )
            }
          }

          Text("Target Audience:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFFCBD5E1))
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
          ) {
            listOf("Everyone", "Parents Only", "Teachers Only").forEach { aud ->
              val isSelected = selectedAudience == aud
              Surface(
                color = if (isSelected) Color(0xFF0284C7) else Color(0xFF0D1526),
                contentColor = if (isSelected) Color.White else Color(0xFF94A3B8),
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, if (isSelected) Color(0xFF38BDF8) else Color(0xFF1E2D42)),
                modifier = Modifier
                  .weight(1f)
                  .clickable { selectedAudience = aud }
              ) {
                Box(
                  modifier = Modifier.padding(vertical = 6.dp),
                  contentAlignment = Alignment.Center
                ) {
                  Text(
                    text = when (aud) {
                      "Everyone" -> "🌐 All"
                      "Parents Only" -> "👨‍👩‍👧 Parents"
                      "Teachers Only" -> "👨‍🏫 Teachers"
                      else -> aud
                    },
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                  )
                }
              }
            }
          }

          Text("Select Batch / Class:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFFCBD5E1))
          ScrollableTabRow(
            selectedTabIndex = (listOf("All Batches") + batches.map { it.name }).indexOf(selectedBatchForAnnc).coerceAtLeast(0),
            edgePadding = 0.dp,
            containerColor = Color(0xFF0D1526),
            contentColor = Color(0xFF38BDF8),
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
          ) {
            (listOf("All Batches") + batches.map { it.name }).forEach { bName ->
              Tab(
                selected = selectedBatchForAnnc == bName,
                onClick = { selectedBatchForAnnc = bName },
                text = { Text(bName, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if (selectedBatchForAnnc == bName) Color(0xFF38BDF8) else Color(0xFF94A3B8)) }
              )
            }
          }

          OutlinedTextField(
            value = anncTitle,
            onValueChange = { anncTitle = it },
            label = { Text("Announcement Title", color = Color(0xFF94A3B8)) },
            placeholder = { Text("e.g. Physics Extra Class Tomorrow", color = Color(0xFF64748B)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
          )

          OutlinedTextField(
            value = anncMessage,
            onValueChange = { anncMessage = it },
            label = { Text("Announcement Details", color = Color(0xFF94A3B8)) },
            placeholder = { Text("Enter detailed notes or instructions for $selectedAudience...", color = Color(0xFF64748B)) },
            maxLines = 4,
            modifier = Modifier.fillMaxWidth()
          )
        }
      },
      confirmButton = {
        Button(
          onClick = {
            if (anncTitle.isNotBlank() && anncMessage.isNotBlank()) {
              val annc = FirestoreAnnouncement(
                id = "ANNC_${System.currentTimeMillis()}",
                title = "📢 $anncTitle",
                message = anncMessage,
                sender = "Faculty ($selectedBatchForAnnc)",
                targetAudience = selectedAudience,
                batchName = selectedBatchForAnnc,
                timestamp = System.currentTimeMillis(),
                dateStr = AppRepository.getCurrentTimeStr(),
                type = "Announcement"
              )
              FirestoreService.publishAnnouncement(
                announcement = annc,
                onSuccess = {
                  announcementSentNotice = "Synced to Cloud Firestore collection & push delivered!"
                }
              )
              anncTitle = ""
              anncMessage = ""
            }
          },
          shape = RoundedCornerShape(10.dp),
          colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7))
        ) {
          Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(16.dp))
          Spacer(Modifier.width(6.dp))
          Text("Send Announcement")
        }
      },
      dismissButton = {
        TextButton(onClick = { showTeacherAnnouncementDialog = false }) {
          Text("Close", color = Color(0xFF94A3B8))
        }
      }
    )
  }

  LazyColumn(
    modifier = Modifier.fillMaxSize(),
    contentPadding = PaddingValues(16.dp),
    verticalArrangement = Arrangement.spacedBy(16.dp)
  ) {
    item {
      HeroFeatureCard(
        title = "Faculty Command Center",
        subtitle = "Faculty & Class Operations",
        tagText = "$pendingDoubtsCount PENDING DOUBTS",
        icon = Icons.Default.School
      )
    }

    item {
      QuickActionGrid(
        card1Title = "Mark Attendance",
        card1Icon = Icons.Default.FactCheck,
        card1Click = { onTabChange(1) },
        card2Title = "Solve Doubts",
        card2Icon = Icons.Default.HelpOutline,
        card2Click = { onTabChange(4) }
      )
    }

    // Teacher Send Class Announcement Action Card
    item {
      Card(
        modifier = Modifier
          .fillMaxWidth()
          .clickable { showTeacherAnnouncementDialog = true },
        colors = CardDefaults.cardColors(containerColor = Color(0xFF111C2E)),
        border = BorderStroke(1.dp, Color(0xFF1E2D42)),
        shape = RoundedCornerShape(16.dp)
      ) {
        Row(
          modifier = Modifier.padding(16.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
          Box(
            modifier = Modifier
              .size(44.dp)
              .clip(CircleShape)
              .background(Color(0xFF0284C7)),
            contentAlignment = Alignment.Center
          ) {
            Icon(Icons.Default.Campaign, contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
          }
          Column(modifier = Modifier.weight(1f)) {
            Text("Send Class Announcement", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color(0xFFF8FAFC))
            Text("Broadcast instant push notifications & alerts to your batches", fontSize = 12.sp, color = Color(0xFF94A3B8))
          }
          Icon(Icons.Default.ChevronRight, contentDescription = null, tint = Color(0xFF38BDF8))
        }
      }
    }

    // Today's Classes Summary Card
    item {
      GeoSectionCard(title = "Today's Schedule (Monday)") {
        val todaySchedule = timetable.filter { it.day.equals("Monday", true) }
        if (todaySchedule.isEmpty()) {
          DashboardEmptyStates.TodaysSchedule(
            onScheduleClass = { onTabChange(3) }, // Switch to Timetable tab
            onAcademicSetup = null,
            modifier = Modifier.padding(vertical = 4.dp)
          )
        } else {
          Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            todaySchedule.forEach { cl ->
              Row(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color(0xFFEEF0F6)).padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
              ) {
                Column {
                  Text(cl.subject, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFF001D36))
                  Text("${cl.batch} • ${cl.room}", fontSize = 11.sp, color = Color(0xFF44474E))
                }
                Surface(color = Color(0xFF0061A4), shape = RoundedCornerShape(8.dp)) {
                  Text("${cl.startTime} - ${cl.endTime}", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                }
              }
            }
          }
        }
      }
    }

    // Syllabus Progress Overview
    item {
      GeoSectionCard(title = "Assigned Syllabus Coverage") {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
          subjects.forEach { sub ->
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
              Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(sub.name, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                Text("${sub.completionPercent}%", fontWeight = FontWeight.Bold, color = Color(0xFF0061A4), fontSize = 13.sp)
              }
              LinearProgressIndicator(
                progress = { sub.completionPercent / 100f },
                modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                color = Color(0xFF0061A4),
                trackColor = Color(0xFFEEF0F6)
              )
            }
          }
        }
      }
    }

    // Weekly Attendance Trends Telemetry
    item {
      GeoSectionCard(title = "Weekly Attendance Trends (All Batches)") {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Column {
              Text("Weekly Class Average", fontSize = 11.sp, color = Color(0xFF64748B))
              Text("94.8% Present", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF15803D))
            }
            Surface(
              color = Color(0xFFDCFCE7),
              shape = RoundedCornerShape(8.dp)
            ) {
              Text(
                "+2.4% vs Last Week",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF15803D),
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
              )
            }
          }
          
          Row(
            modifier = Modifier.fillMaxWidth().height(48.dp).padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom
          ) {
            val attendanceDays = listOf(
              Pair("Mon", 95f),
              Pair("Tue", 92f),
              Pair("Wed", 96f),
              Pair("Thu", 91f),
              Pair("Fri", 98f),
              Pair("Sat", 95f)
            )
            attendanceDays.forEach { (day, pct) ->
              Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.weight(1f)
              ) {
                Box(
                  modifier = Modifier
                    .width(18.dp)
                    .height((pct * 0.35f).dp)
                    .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                    .background(if (pct >= 95f) Color(0xFF15803D) else Color(0xFF0061A4))
                )
                Text(day, fontSize = 9.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF64748B))
              }
            }
          }
        }
      }
    }
  }
}

@Composable
fun TeacherAttendanceTab(students: List<Student>, batches: List<Batch>) {
  var selectedTabState by remember { mutableStateOf(0) } // 0 = Daily Register, 1 = Card Flow, 2 = Attendance History & Monthly Analytics
  val currentUserId by AppRepository.currentUserIdentifier.collectAsState()

  Column(modifier = Modifier.fillMaxSize()) {
    // Top Tabs: Daily Register vs Card Flow vs Attendance History
    ScrollableTabRow(
      selectedTabIndex = selectedTabState,
      containerColor = MaterialTheme.colorScheme.surface,
      contentColor = Color(0xFF0061A4),
      edgePadding = 16.dp,
      modifier = Modifier.fillMaxWidth()
    ) {
      Tab(
        selected = selectedTabState == 0,
        onClick = { selectedTabState = 0 },
        text = { Text("Daily Register", fontWeight = FontWeight.Bold, fontSize = 13.sp) },
        icon = { Icon(imageVector = Icons.Default.FactCheck, contentDescription = "Daily Register Icon", modifier = Modifier.size(18.dp)) }
      )
      Tab(
        selected = selectedTabState == 1,
        onClick = { selectedTabState = 1 },
        text = { Text("Card Flow", fontWeight = FontWeight.Bold, fontSize = 13.sp) },
        icon = { Icon(imageVector = Icons.Default.HowToReg, contentDescription = "Card Flow Icon", modifier = Modifier.size(18.dp)) }
      )
      Tab(
        selected = selectedTabState == 2,
        onClick = { selectedTabState = 2 },
        text = { Text("Attendance History", fontWeight = FontWeight.Bold, fontSize = 13.sp) },
        icon = { Icon(imageVector = Icons.Default.History, contentDescription = "History Icon", modifier = Modifier.size(18.dp)) }
      )
    }

    when (selectedTabState) {
      0 -> {
        // 1. FAST DAILY ROLL CALL REGISTER (Room Database Daily Attendance)
        com.example.ui.attendance.DailyAttendanceRegister(
          currentUserRole = "Teacher",
          currentUserId = currentUserId.ifBlank { "teacher_fsi" },
          onNavigateToHistory = { selectedTabState = 2 }
        )
      }
      1 -> {
        // 2. GUIDED CARD-SWIPE ATTENDANCE WORKFLOW
        TakeAttendanceWorkflow(
          onFinishWorkflow = { selectedTabState = 2 },
          onOpenHistoryLogs = { selectedTabState = 2 }
        )
      }
      else -> {
        // 3. ATTENDANCE HISTORY & MONTHLY SUMMARY (Day/Month/Year filtering, 22-Hour edit window, Room DB)
        com.example.ui.attendance.AttendanceHistoryView(
          currentUserRole = "Teacher",
          currentUserId = currentUserId.ifBlank { "teacher_fsi" }
        )
      }
    }
  }
}

@Composable
fun TeacherTestsTab(tests: List<TestRecord>, students: List<Student>) {
  var selectedTest by remember { mutableStateOf(tests.firstOrNull()) }
  var marksEditingMap by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }

  LaunchedEffect(selectedTest) {
    if (selectedTest != null) {
      marksEditingMap = selectedTest!!.studentMarks
    }
  }

  LazyColumn(
    modifier = Modifier.fillMaxSize(),
    contentPadding = PaddingValues(16.dp),
    verticalArrangement = Arrangement.spacedBy(14.dp)
  ) {
    item { Text("Test Marks Evaluation & Rank Engine", fontWeight = FontWeight.Bold, fontSize = 18.sp) }

    item {
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        tests.forEach { t ->
          val sel = selectedTest?.id == t.id
          Surface(
            color = if (sel) Color(0xFF0061A4) else Color(0xFFEEF0F6),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.clickable { selectedTest = t }
          ) { Text(t.testName.take(16) + "...", color = if (sel) Color.White else Color(0xFF44474E), fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) }
        }
      }
    }

    if (selectedTest != null) {
      item {
        GeoSectionCard(title = "${selectedTest!!.testName} (${selectedTest!!.batch})", actionText = "TOTAL ${selectedTest!!.totalMarks} MKS") {
          Text(selectedTest!!.remarks, fontSize = 12.sp, color = Color(0xFF44474E))
          Spacer(Modifier.height(10.dp))
          
          Text("Student Marks Entry (Auto Calculates Rank)", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
          Spacer(Modifier.height(6.dp))

          val batchStudents = students.filter { it.batch == selectedTest!!.batch || it.id in marksEditingMap.keys }
          
          Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            batchStudents.forEach { bStu ->
              val currScore = marksEditingMap[bStu.id] ?: 0
              Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(bStu.name, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                  IconButton(onClick = { marksEditingMap = marksEditingMap + (bStu.id to (currScore - 10).coerceAtLeast(0)) }, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.RemoveCircleOutline, contentDescription = null, tint = Color(0xFF0061A4))
                  }
                  Text("$currScore", fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.width(36.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                  IconButton(onClick = { marksEditingMap = marksEditingMap + (bStu.id to (currScore + 10).coerceAtMost(selectedTest!!.totalMarks)) }, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.AddCircleOutline, contentDescription = null, tint = Color(0xFF0061A4))
                  }
                }
              }
            }
          }

          Spacer(Modifier.height(14.dp))
          Button(
            onClick = { AppRepository.saveTestMarks(selectedTest!!.id, marksEditingMap) },
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF072711)),
            modifier = Modifier.fillMaxWidth()
          ) { Text("Save Evaluation & Generate AI Analysis") }
        }
      }

      item {
        val marksList = marksEditingMap.values.toList()
        val classAverage = if (marksList.isNotEmpty()) marksList.average().toInt() else 0
        val maxScore = if (marksList.isNotEmpty()) marksList.maxOrNull() ?: 0 else 0
        val minScore = if (marksList.isNotEmpty()) marksList.minOrNull() ?: 0 else 0

        GeoSectionCard(title = "📊 Class Gradebook Analytics") {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
          ) {
            Column(
              modifier = Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(Color(0xFF182438)).border(1.dp, Color(0xFF1E2D42), RoundedCornerShape(12.dp)).padding(10.dp),
              horizontalAlignment = Alignment.CenterHorizontally
            ) {
              Text("Class Avg", fontSize = 11.sp, color = Color(0xFF94A3B8))
              Text("$classAverage / ${selectedTest!!.totalMarks}", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFF38BDF8))
            }
            Column(
              modifier = Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(Color(0xFF064E3B)).border(1.dp, Color(0xFF047857), RoundedCornerShape(12.dp)).padding(10.dp),
              horizontalAlignment = Alignment.CenterHorizontally
            ) {
              Text("Highest Score", fontSize = 11.sp, color = Color(0xFF6EE7B7))
              Text("$maxScore / ${selectedTest!!.totalMarks}", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFF4ADE80))
            }
            Column(
              modifier = Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(Color(0xFF450A0A)).border(1.dp, Color(0xFF991B1B), RoundedCornerShape(12.dp)).padding(10.dp),
              horizontalAlignment = Alignment.CenterHorizontally
            ) {
              Text("Lowest Score", fontSize = 11.sp, color = Color(0xFFFCA5A5))
              Text("$minScore / ${selectedTest!!.totalMarks}", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFFF87171))
            }
          }
        }
      }

      item {
        var inspectedStudent by remember(students) { mutableStateOf(students.firstOrNull()) }

        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
          Text("Individual Learner Progression", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color(0xFFF8FAFC))
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
          ) {
            students.take(5).forEach { st ->
              val isSel = inspectedStudent?.id == st.id
              Surface(
                color = if (isSel) Color(0xFF0284C7) else Color(0xFF182438),
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, if (isSel) Color(0xFF38BDF8) else Color(0xFF1E2D42)),
                modifier = Modifier.clickable { inspectedStudent = st }
              ) {
                Text(
                  st.name.take(12),
                  fontSize = 11.sp,
                  fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                  color = if (isSel) Color.White else Color(0xFF94A3B8),
                  modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                )
              }
            }
          }

          if (inspectedStudent != null) {
            StudentGradeProgressDashboard(
              student = inspectedStudent!!,
              tests = tests
            )
          }
        }
      }
    }
  }
}

@Composable
fun TeacherTimetableTab(timetable: List<TimetableItem>) {
  var selectedSubTab by remember { mutableStateOf(0) } // 0 = Daily Dynamic, 1 = Weekly Schedule

  Column(modifier = Modifier.fillMaxSize()) {
    ScrollableTabRow(
      selectedTabIndex = selectedSubTab,
      containerColor = MaterialTheme.colorScheme.surface,
      contentColor = MaterialTheme.colorScheme.primary,
      edgePadding = 16.dp
    ) {
      Tab(
        selected = selectedSubTab == 0,
        onClick = { selectedSubTab = 0 },
        text = { Text("Daily Dynamic Timetable", fontWeight = FontWeight.Bold, fontSize = 12.sp) },
        icon = { Icon(Icons.Default.Schedule, contentDescription = null, modifier = Modifier.size(20.dp)) }
      )
      Tab(
        selected = selectedSubTab == 1,
        onClick = { selectedSubTab = 1 },
        text = { Text("Weekly Fixed Schedule", fontWeight = FontWeight.Bold, fontSize = 12.sp) },
        icon = { Icon(Icons.Default.DateRange, contentDescription = null, modifier = Modifier.size(20.dp)) }
      )
    }

    if (selectedSubTab == 0) {
      DynamicTimetableScreen()
    } else {
      TeacherWeeklyTimetableList(timetable)
    }
  }
}

@Composable
fun TeacherWeeklyTimetableList(timetable: List<TimetableItem>) {
  val currentDay = remember {
    SimpleDateFormat("EEEE", Locale.getDefault()).format(Date())
  }

  val daysOfWeek = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday")

  LazyColumn(
    modifier = Modifier.fillMaxSize(),
    contentPadding = PaddingValues(16.dp),
    verticalArrangement = Arrangement.spacedBy(16.dp)
  ) {
    item {
      Column {
        Text("Faculty Timetable Mon-Sat", fontWeight = FontWeight.Bold, fontSize = 18.sp)
        Text("Today is highlighted dynamically.", fontSize = 12.sp, color = Color(0xFF74777F))
      }
    }

    // Prominent TODAY section at top
    item {
      val todayList = timetable.filter { it.day.equals(currentDay, true) || (currentDay == "Sunday" && it.day == "Monday") }
      val headerDay = if (currentDay == "Sunday") "Monday (Tomorrow)" else currentDay

      GeoSectionCard(title = "Today's Highlight ($headerDay)", actionText = "ACTIVE") {
        if (todayList.isEmpty()) {
          Text("No classes scheduled today.", fontStyle = androidx.compose.ui.text.font.FontStyle.Italic, color = Color(0xFF74777F))
        } else {
          Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            todayList.forEach { item ->
              TimetableCard(item, isToday = true)
            }
          }
        }
      }
    }

    // Full Weekly Mon-Sat Schedule below
    item { Text("Weekly Schedule (Monday to Saturday)", fontWeight = FontWeight.Bold, fontSize = 16.sp) }

    daysOfWeek.forEach { dayName ->
      val dayItems = timetable.filter { it.day.equals(dayName, true) }
      val isThisDay = currentDay.equals(dayName, true)

      item {
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(if (isThisDay) Color(0xFF182438) else Color(0xFF111C2E))
            .border(1.dp, if (isThisDay) Color(0xFF38BDF8) else Color(0xFF1E2D42), RoundedCornerShape(20.dp))
            .padding(16.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(dayName.uppercase(), fontWeight = FontWeight.Bold, fontSize = 13.sp, color = if (isThisDay) Color(0xFF38BDF8) else Color(0xFFF8FAFC))
            if (isThisDay) {
              Surface(color = Color(0xFF0284C7), shape = CircleShape) {
                Text("TODAY", fontSize = 9.sp, color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp))
              }
            }
          }

          if (dayItems.isEmpty()) {
            Text("No classes", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)
          } else {
            dayItems.forEach { tItem ->
              TimetableCard(tItem, isToday = false)
            }
          }
        }
      }
    }
  }
}

@Composable
fun TimetableCard(item: TimetableItem, isToday: Boolean) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(12.dp))
      .background(if (isToday) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant)
      .padding(12.dp),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically
  ) {
    Column(
      modifier = Modifier
        .weight(1f)
        .padding(end = 8.dp)
    ) {
      Text(item.subject, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
      Text("Batch: ${item.batch}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
      Text("Room: ${item.room}", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
    Surface(
      color = Color(0xFF0061A4),
      shape = RoundedCornerShape(8.dp)
    ) {
      Text(
        text = "${item.startTime} - ${item.endTime}",
        color = Color.White,
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
      )
    }
  }
}

@Composable
fun TeacherDoubtsAndMaterialsTab(doubts: List<DoubtItem>) {
  var replyText by remember { mutableStateOf("") }
  var activeDoubt by remember { mutableStateOf<DoubtItem?>(null) }

  if (activeDoubt != null) {
    AlertDialog(
      onDismissRequest = { activeDoubt = null },
      title = { Text("Resolve Doubt: ${activeDoubt!!.studentName}", fontWeight = FontWeight.Bold, fontSize = 16.sp) },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
          Surface(color = Color(0xFFEEF0F6), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
            Text(activeDoubt!!.questionText, fontSize = 13.sp, modifier = Modifier.padding(12.dp))
          }
          OutlinedTextField(
            value = replyText,
            onValueChange = { replyText = it },
            label = { Text("Type Teacher Solution / Explanation") },
            modifier = Modifier.fillMaxWidth().height(120.dp)
          )
        }
      },
      confirmButton = {
        Button(
          onClick = {
            if (replyText.isNotBlank()) {
              AppRepository.replyToDoubt(activeDoubt!!.id, replyText)
              replyText = ""
              activeDoubt = null
            }
          },
          colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0061A4))
        ) { Text("Send Solution") }
      },
      dismissButton = { TextButton(onClick = { activeDoubt = null }) { Text("Cancel") } },
      containerColor = MaterialTheme.colorScheme.surface,
      shape = RoundedCornerShape(24.dp)
    )
  }

  LazyColumn(
    modifier = Modifier.fillMaxSize(),
    contentPadding = PaddingValues(16.dp),
    verticalArrangement = Arrangement.spacedBy(14.dp)
  ) {
    item { Text("Student Doubts Queue & Uploads", fontWeight = FontWeight.Bold, fontSize = 18.sp) }

    items(doubts) { d ->
      GeoSectionCard(title = "${d.studentName} (${d.subject})", actionText = d.status) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Text("Topic: ${d.chapter}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
          Text(d.questionText, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)

          if (d.status == "Pending") {
            Button(
              onClick = { activeDoubt = d },
              colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0061A4)),
              modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
            ) {
              Icon(Icons.Default.Reply, contentDescription = null, modifier = Modifier.size(16.dp))
              Spacer(Modifier.width(6.dp))
              Text("Write Solution / Upload Answer")
            }
          } else {
            Surface(color = Color(0xFFD1F2D1), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
              Column(Modifier.padding(12.dp)) {
                Text("✅ Faculty Reply (${d.resolutionTime}):", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF072711))
                Text(d.reply ?: "Resolved", fontSize = 13.sp, color = Color(0xFF072711))
              }
            }
          }
        }
      }
    }
  }
}
