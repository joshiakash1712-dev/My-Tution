package com.example.ui.student

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.*
import com.example.ui.components.*
import com.example.ui.teacher.TimetableCard
import com.example.ui.timetable.DynamicTimetableScreen
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun StudentMainContent(currentTab: Int, onTabChange: (Int) -> Unit = {}) {
  val students by AppRepository.students.collectAsState()
  val currentUserId by AppRepository.currentUserIdentifier.collectAsState()
  val currentAccount by AppRepository.currentUserAccount.collectAsState()
  val activeStudent = remember(students, currentUserId, currentAccount) {
    AppRepository.getActiveStudentForCurrentSession() ?: Student(
      id = "STU-000",
      name = "No Enrolled Student",
      photo = "",
      mobile = "-",
      parentName = "-",
      parentContact = "-",
      email = "-",
      dob = "-",
      gender = "-",
      address = "-",
      school = "-",
      className = "-",
      batch = "No Active Batch",
      stream = "-",
      admissionDate = "-",
      status = "Inactive",
      attendancePercent = 0,
      overallAvg = 0,
      rank = 0,
      strongestSubject = "-",
      weakestSubject = "-",
      recentScores = emptyList()
    )
  }
  val tests by AppRepository.tests.collectAsState()
  val homework by AppRepository.homework.collectAsState()
  val timetable by AppRepository.timetable.collectAsState()
  val doubts by AppRepository.doubts.collectAsState()
  val studyLogs by AppRepository.studyLogs.collectAsState()
  val goal by AppRepository.studentGoal.collectAsState()
  val badges by AppRepository.badges.collectAsState()

  val studentTests = remember(tests, activeStudent.id, activeStudent.batch, activeStudent.batchId) {
    tests.filter {
      (activeStudent.batchId.isNotBlank() && it.batchId.equals(activeStudent.batchId, ignoreCase = true)) ||
        it.batch.equals(activeStudent.batch, ignoreCase = true) ||
        it.studentMarks.containsKey(activeStudent.id)
    }
  }

  val studentHomework = remember(homework, activeStudent.id, activeStudent.batch, activeStudent.batchId) {
    AppRepository.getHomeworkForCurrentStudent(activeStudent)
  }

  val studentDoubts = remember(doubts, activeStudent.id) {
    AppRepository.getDoubtsForCurrentStudent(activeStudent)
  }

  val studentStudyLogs = remember(studyLogs, activeStudent.id) {
    AppRepository.getStudyLogsForCurrentStudent(activeStudent)
  }

  val activeGoal = remember(goal, activeStudent.id) {
    AppRepository.getGoalForCurrentStudent(activeStudent)
  }

  when (currentTab) {
    0 -> StudentDashboardTab(activeStudent, studentTests, studentHomework, activeGoal, badges, onTabChange)
    1 -> StudentTimetableTab(timetable, activeStudent.batch, activeStudent.batchId)
    2 -> StudentOnlineTestTab(activeStudent)
    3 -> StudentAiAssistantTab(activeStudent)
    4 -> StudentStudyTrackerTab(studentStudyLogs, studentDoubts, activeStudent)
  }
}

@Composable
fun StudentDashboardTab(
  student: Student,
  tests: List<TestRecord>,
  homework: List<HomeworkItem>,
  goal: GoalItem,
  badges: List<BadgeItem>,
  onTabChange: (Int) -> Unit = {}
) {
  var showIdDialog by remember { mutableStateOf(false) }
  if (showIdDialog) {
    DigitalIdCardDialog(student) { showIdDialog = false }
  }

  LazyColumn(
    modifier = Modifier.fillMaxSize(),
    contentPadding = PaddingValues(16.dp),
    verticalArrangement = Arrangement.spacedBy(16.dp)
  ) {
    // 1. Hero Feature Card
    item {
      HeroFeatureCard(
        title = "Target JEE Advanced AIR 100",
        subtitle = "${student.name} • Batch Rank #${student.rank} • Overall Avg ${student.overallAvg}%",
        tagText = "7 DAY STUDY STREAK 🔥",
        icon = Icons.Default.EmojiEvents
      )
    }

    // 2. Quick Action Grid
    item {
      QuickActionGrid(
        card1Title = "Digital ID Card",
        card1Icon = Icons.Default.Badge,
        card1Click = { showIdDialog = true },
        card2Title = "AI Study Guide",
        card2Icon = Icons.Default.AutoAwesome,
        card2Click = { onTabChange(3) }
      )
    }

    // 3. Goal Progress & Attendance Metrics
    item {
      Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        MetricBox("Attendance", "${student.attendancePercent}%", "Target: ${goal.targetAttendance}%", Color(0xFFD1E4FF), Color(0xFF001D36), Modifier.weight(1f))
        MetricBox("Mock Rank", "#${student.rank}", "Goal: AIR #${goal.targetRank}", Color(0xFFF7D8FF), Color(0xFF2B1236), Modifier.weight(1f))
      }
    }

    // 4. Recharts-based Visual Grade Progress Dashboard
    item {
      StudentGradeProgressDashboard(
        student = student,
        tests = tests
      )
    }

    // 5. Badges & Gamification Showcase
    item {
      GeoSectionCard(title = "Earned Achievements (${badges.count { it.earned }})") {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
          badges.filter { it.earned }.take(3).forEach { bg ->
            Column(
              horizontalAlignment = Alignment.CenterHorizontally,
              modifier = Modifier.weight(1f).clip(RoundedCornerShape(16.dp)).background(Color(0xFFEEF0F6)).padding(10.dp)
            ) {
              Icon(
                imageVector = when(bg.iconName) {
                  "emoji_events" -> Icons.Default.EmojiEvents
                  "local_fire_department" -> Icons.Default.LocalFireDepartment
                  else -> Icons.Default.School
                },
                contentDescription = null,
                tint = Color(0xFF0061A4),
                modifier = Modifier.size(24.dp)
              )
              Spacer(Modifier.height(4.dp))
              Text(bg.name, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            }
          }
        }
      }
    }

    // 5. Recent Test Performance & AI Feedback
    item {
      GeoSectionCard(title = "Academic Scores & AI Diagnosis") {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
          student.recentScores.forEach { (tName, score) ->
            Row(
              modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant).padding(12.dp),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Text(tName, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).padding(end = 8.dp))
              Surface(color = Color(0xFFD1E4FF), shape = RoundedCornerShape(8.dp)) {
                Text("$score / 100", fontWeight = FontWeight.Bold, color = Color(0xFF001D36), fontSize = 12.sp, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
              }
            }
          }

          val recTest = tests.firstOrNull()
          if (recTest != null) {
            Surface(color = Color(0xFFC2E7FF), shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
              Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                  Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = Color(0xFF001D35), modifier = Modifier.size(16.dp))
                  Spacer(Modifier.width(6.dp))
                  Text("AI Diagnostic Insights (${recTest.testName})", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color(0xFF001D35))
                }
                Text("Strong: ${recTest.aiAnalysisStrong.joinToString(", ")}", fontSize = 11.sp, color = Color(0xFF072711), fontWeight = FontWeight.SemiBold)
                Text("Weak Area: ${recTest.aiAnalysisWeak.joinToString(", ")}", fontSize = 11.sp, color = Color(0xFF8C1D18))
                Text(recTest.aiSuggestion, fontSize = 11.sp, color = Color(0xFF001D35), fontStyle = androidx.compose.ui.text.font.FontStyle.Italic, modifier = Modifier.padding(top = 2.dp))
              }
            }
          }
        }
      }
    }

    // 6. Pending Homework Assignments
    item {
      GeoSectionCard(title = "Homework Assignments (${homework.size})") {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          homework.forEach { hw ->
            val isCompleted = hw.completedByStudentIds.contains(student.id) ||
              hw.status.equals("Submitted", ignoreCase = true) ||
              hw.status.equals("Reviewed", ignoreCase = true)
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(if (!isCompleted) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.surfaceVariant)
                .clickable { AppRepository.toggleHomeworkStatus(hw.id, student.id) }
                .padding(12.dp),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Column(Modifier.weight(1f).padding(end = 8.dp)) {
                Text("${hw.subject}: ${hw.title}", fontWeight = FontWeight.Medium, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("Due: ${hw.dueDate}${if (hw.assignedBy.isNotBlank()) " • ${hw.assignedBy}" else ""}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
              }
              Surface(
                shape = RoundedCornerShape(8.dp),
                color = if (isCompleted) Color(0xFFDCFCE7) else Color(0xFF0061A4)
              ) {
                Text(
                  text = if (isCompleted) "SUBMITTED ✓" else "MARK DONE",
                  fontWeight = FontWeight.Bold,
                  fontSize = 10.sp,
                  maxLines = 1,
                  softWrap = false,
                  overflow = TextOverflow.Ellipsis,
                  color = if (isCompleted) Color(0xFF15803D) else Color.White,
                  modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                )
              }
            }
          }
        }
      }
    }
  }
}

@Composable
fun StudentTimetableTab(timetable: List<TimetableItem>, batchName: String, batchId: String = "") {
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
        text = { Text("Daily Dynamic Timetable", fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis) },
        icon = { Icon(Icons.Default.Schedule, contentDescription = null, modifier = Modifier.size(20.dp)) }
      )
      Tab(
        selected = selectedSubTab == 1,
        onClick = { selectedSubTab = 1 },
        text = { Text("Weekly Fixed Schedule", fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis) },
        icon = { Icon(Icons.Default.DateRange, contentDescription = null, modifier = Modifier.size(20.dp)) }
      )
    }

    if (selectedSubTab == 0) {
      DynamicTimetableScreen()
    } else {
      StudentWeeklyTimetableList(timetable, batchName, batchId)
    }
  }
}

@Composable
fun StudentWeeklyTimetableList(timetable: List<TimetableItem>, batchName: String, batchId: String = "") {
  val daysOfWeek = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday")
  val studentBatchSchedule = remember(timetable, batchName, batchId) {
    timetable.filter {
      (batchId.isNotBlank() && it.batchId.equals(batchId, ignoreCase = true)) ||
        it.batch.equals(batchName, ignoreCase = true)
    }
  }

  LazyColumn(
    modifier = Modifier.fillMaxSize(),
    contentPadding = PaddingValues(16.dp),
    verticalArrangement = Arrangement.spacedBy(14.dp)
  ) {
    item {
      Column {
        Text("My Batch Schedule ($batchName)", fontWeight = FontWeight.Bold, fontSize = 18.sp)
        Text("Weekly lectures and practice labs Mon-Sat", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
      }
    }

    daysOfWeek.forEach { d ->
      val dayClasses = studentBatchSchedule.filter { it.day.equals(d, true) }
      item {
        GeoSectionCard(title = d) {
          if (dayClasses.isEmpty()) {
            Text("No classes scheduled", fontStyle = androidx.compose.ui.text.font.FontStyle.Italic, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
          } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
              dayClasses.forEach { cl ->
                TimetableCard(cl, isToday = false)
              }
            }
          }
        }
      }
    }
  }
}

@Composable
fun StudentOnlineTestTab(student: Student? = null) {
  val activeStudent = student ?: AppRepository.getActiveStudentForCurrentSession()
  val allQuestions by AppRepository.questionBank.collectAsState()
  val allTests by AppRepository.tests.collectAsState()
  val allAttempts by AppRepository.studentTestAttempts.collectAsState()

  val availableTests = remember(allTests, activeStudent?.batchId, activeStudent?.batch) {
    val batchFiltered = allTests.filter {
      activeStudent == null ||
        it.batchId.isBlank() ||
        it.batchId.equals(activeStudent.batchId, ignoreCase = true) ||
        it.batch.equals(activeStudent.batch, ignoreCase = true)
    }
    batchFiltered.ifEmpty { allTests }
  }

  var selectedTestId by rememberSaveable { mutableStateOf("") }
  val activeTest = remember(availableTests, selectedTestId) {
    availableTests.find { it.id == selectedTestId } ?: availableTests.firstOrNull()
  }

  val activeQuestions = remember(allQuestions, activeTest?.id, activeTest?.subject) {
    val byTestId = if (activeTest != null) {
      allQuestions.filter { it.testId == activeTest.id }
    } else emptyList()
    val bySubject = if (byTestId.isEmpty() && activeTest != null && !activeTest.subject.equals("All", ignoreCase = true)) {
      allQuestions.filter { it.subject.contains(activeTest.subject, ignoreCase = true) }
    } else emptyList()
    when {
      byTestId.isNotEmpty() -> byTestId
      bySubject.isNotEmpty() -> bySubject
      allQuestions.isNotEmpty() -> allQuestions
      else -> SeedData.getDefaultQuestions()
    }
  }

  val studentAttempts = remember(allAttempts, activeStudent?.id) {
    AppRepository.getAttemptsForCurrentStudent(activeStudent)
  }

  var testActive by rememberSaveable { mutableStateOf(false) }
  var currentQuestionIdx by rememberSaveable { mutableStateOf(0) }
  // Store serialized answer state ("questionId:optionIndex,...") in rememberSaveable so rotation/recreation survives
  var serializedAnswers by rememberSaveable { mutableStateOf("") }
  var lastSubmittedAttemptId by rememberSaveable { mutableStateOf<String?>(null) }
  var remainingSeconds by rememberSaveable { mutableStateOf(900) }

  val selectedOptions: Map<String, Int> = remember(serializedAnswers) {
    if (serializedAnswers.isBlank()) {
      emptyMap()
    } else {
      serializedAnswers.split(",").mapNotNull { entry ->
        val parts = entry.split(":")
        if (parts.size == 2) {
          val qId = parts[0]
          val opt = parts[1].toIntOrNull()
          if (opt != null) qId to opt else null
        } else null
      }.toMap()
    }
  }

  fun updateSelectedOption(questionId: String, optionIdx: Int?) {
    val mutable = selectedOptions.toMutableMap()
    if (optionIdx == null) {
      mutable.remove(questionId)
    } else {
      mutable[questionId] = optionIdx
    }
    serializedAnswers = mutable.entries.joinToString(",") { "${it.key}:${it.value}" }
  }

  fun submitCurrentAttempt() {
    val testId = activeTest?.id ?: "CBT_DEFAULT"
    val testName = activeTest?.testName ?: "All India JEE Speed Test #12"
    val subject = activeTest?.subject ?: "Physics / Math / Chemistry"
    val durationSpent = (900 - remainingSeconds).coerceAtLeast(1)
    val persistedAttempt = AppRepository.submitStudentTestAttempt(
      testId = testId,
      testName = testName,
      subject = subject,
      answers = selectedOptions,
      durationSeconds = durationSpent,
      studentIdOverride = activeStudent?.id
    )
    testActive = false
    lastSubmittedAttemptId = persistedAttempt.id
  }

  // Active Countdown Timer with Auto-Submit & Persistence
  LaunchedEffect(testActive) {
    if (testActive) {
      while (remainingSeconds > 0 && testActive) {
        kotlinx.coroutines.delay(1000L)
        remainingSeconds--
      }
      if (remainingSeconds <= 0 && testActive) {
        submitCurrentAttempt()
      }
    }
  }

  val viewedAttempt = remember(studentAttempts, lastSubmittedAttemptId) {
    if (lastSubmittedAttemptId != null) {
      studentAttempts.find { it.id == lastSubmittedAttemptId } ?: studentAttempts.firstOrNull()
    } else null
  }

  if (!testActive && viewedAttempt == null) {
    LazyColumn(
      modifier = Modifier.fillMaxSize(),
      contentPadding = PaddingValues(16.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
      item {
        HeroFeatureCard(
          title = "Online CBT Examination Portal",
          subtitle = "Live Auto-Timed Evaluation & Persisted Question Bank (${allQuestions.size} Qs)",
          tagText = "JEE / NEET CBT",
          icon = Icons.Default.Quiz
        )
      }

      item {
        GeoSectionCard(title = "Select CBT Examination (${availableTests.size} Available)") {
          Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            availableTests.forEach { testItem ->
              val isSelected = activeTest?.id == testItem.id
              val testQuestionsCount = allQuestions.count { it.testId == testItem.id }.let {
                if (it > 0) it else activeQuestions.size
              }
              Surface(
                shape = RoundedCornerShape(12.dp),
                color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                border = BorderStroke(1.dp, if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier
                  .fillMaxWidth()
                  .clickable { selectedTestId = testItem.id }
              ) {
                Row(
                  modifier = Modifier.padding(12.dp),
                  horizontalArrangement = Arrangement.SpaceBetween,
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                    Text(testItem.testName, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
                    Text("${testItem.subject} • $testQuestionsCount Questions • +4 / -1 Marking • 15 Mins", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                  }
                  RadioButton(selected = isSelected, onClick = { selectedTestId = testItem.id })
                }
              }
            }

            Button(
              onClick = {
                remainingSeconds = 900
                currentQuestionIdx = 0
                serializedAnswers = ""
                lastSubmittedAttemptId = null
                testActive = true
              },
              colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
              modifier = Modifier.fillMaxWidth()
            ) { Text("Start Live Test Now") }
          }
        }
      }

      if (studentAttempts.isNotEmpty()) {
        item {
          GeoSectionCard(title = "Persisted CBT Attempts (${studentAttempts.size})") {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
              studentAttempts.forEach { attempt ->
                Surface(
                  shape = RoundedCornerShape(12.dp),
                  color = MaterialTheme.colorScheme.surfaceVariant,
                  modifier = Modifier
                    .fillMaxWidth()
                    .clickable { lastSubmittedAttemptId = attempt.id }
                ) {
                  Row(
                    modifier = Modifier.padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                  ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                      Text(attempt.testName, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                      Text("Correct: ${attempt.correctCount} • Wrong: ${attempt.wrongCount} • Unattempted: ${attempt.unattemptedCount}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Surface(color = Color(0xFFD1E4FF), shape = RoundedCornerShape(8.dp)) {
                      Text(
                        "${attempt.score} / ${attempt.maxMarks}",
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF001D36),
                        fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                      )
                    }
                  }
                }
              }
            }
          }
        }
      }
    }
  } else if (testActive) {
    val safeIdx = currentQuestionIdx.coerceIn(0, (activeQuestions.size - 1).coerceAtLeast(0))
    val currentQuestion = activeQuestions[safeIdx]
    val opts = listOf(
      currentQuestion.optionA,
      currentQuestion.optionB,
      currentQuestion.optionC,
      currentQuestion.optionD
    )

    val mins = remainingSeconds / 60
    val secs = remainingSeconds % 60
    val timeFormatted = String.format(Locale.getDefault(), "%02d:%02d", mins, secs)

    Column(
      modifier = Modifier.fillMaxSize().padding(16.dp),
      verticalArrangement = Arrangement.SpaceBetween
    ) {
      Column(
        modifier = Modifier
          .weight(1f)
          .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp)
      ) {
        // Status Row
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
          Text("Question ${safeIdx + 1} of ${activeQuestions.size}", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.primary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
          Surface(
            color = if (remainingSeconds < 120) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.tertiaryContainer,
            shape = RoundedCornerShape(12.dp)
          ) {
            Text(
              text = "⏱️ $timeFormatted",
              fontSize = 13.sp,
              fontWeight = FontWeight.Bold,
              maxLines = 1,
              softWrap = false,
              overflow = TextOverflow.Ellipsis,
              color = if (remainingSeconds < 120) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onTertiaryContainer,
              modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
            )
          }
        }

        // Question Navigator Bar
        LazyRow(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          items(activeQuestions.size) { qIndex ->
            val qItem = activeQuestions[qIndex]
            val isCurrent = safeIdx == qIndex
            val isAttempted = selectedOptions.containsKey(qItem.id)
            Surface(
              shape = RoundedCornerShape(8.dp),
              color = when {
                isCurrent -> MaterialTheme.colorScheme.primary
                isAttempted -> Color(0xFF059669)
                else -> Color(0xFF1E293B)
              },
              border = BorderStroke(1.dp, if (isCurrent) Color.White else Color(0xFF334155)),
              modifier = Modifier.clickable { currentQuestionIdx = qIndex }
            ) {
              Text(
                text = "Q${qIndex + 1}${if (isAttempted) " ✓" else ""}",
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
              )
            }
          }
        }

        GeoSectionCard(title = "${currentQuestion.subject} • ${currentQuestion.chapter} (+${currentQuestion.marks}, -1)") {
          Text(currentQuestion.questionText, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
          Spacer(Modifier.height(14.dp))
          Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            opts.forEachIndexed { idx, oTxt ->
              val sel = selectedOptions[currentQuestion.id] == idx
              Row(
                modifier = Modifier
                  .fillMaxWidth()
                  .clip(RoundedCornerShape(14.dp))
                  .background(if (sel) Color(0xFFD1E4FF) else Color(0xFFEEF0F6))
                  .border(2.dp, if (sel) Color(0xFF0061A4) else Color.Transparent, RoundedCornerShape(14.dp))
                  .clickable { updateSelectedOption(currentQuestion.id, idx) }
                  .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
              ) {
                RadioButton(selected = sel, onClick = { updateSelectedOption(currentQuestion.id, idx) })
                Spacer(Modifier.width(10.dp))
                Text(oTxt, fontSize = 14.sp, fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal)
              }
            }
          }

          if (selectedOptions.containsKey(currentQuestion.id)) {
            Spacer(Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
              TextButton(onClick = { updateSelectedOption(currentQuestion.id, null) }) {
                Text("Clear Selection", fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
              }
            }
          }
        }
      }

      Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        if (safeIdx > 0) {
          OutlinedButton(onClick = { currentQuestionIdx = safeIdx - 1 }, modifier = Modifier.weight(1f).height(50.dp)) { Text("Previous") }
        }
        if (safeIdx < activeQuestions.size - 1) {
          Button(onClick = { currentQuestionIdx = safeIdx + 1 }, modifier = Modifier.weight(1f).height(50.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0061A4))) { Text("Next Question") }
        } else {
          Button(
            onClick = { submitCurrentAttempt() },
            modifier = Modifier.weight(1f).height(50.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF059669))
          ) { Text("Submit CBT Test") }
        }
      }
    }
  } else if (viewedAttempt != null) {
    val totalScore = viewedAttempt.score
    val maxMarks = viewedAttempt.maxMarks
    val correctCount = viewedAttempt.correctCount
    val wrongCount = viewedAttempt.wrongCount
    val unattemptedCount = viewedAttempt.unattemptedCount
    val timeSpentSeconds = viewedAttempt.durationSeconds
    val timeSpentStr = "${timeSpentSeconds / 60}m ${timeSpentSeconds % 60}s"

    val attemptQuestions = remember(allQuestions, viewedAttempt.answers) {
      val matched = allQuestions.filter { viewedAttempt.answers.containsKey(it.id) }
      if (matched.isNotEmpty()) activeQuestions else activeQuestions
    }

    LazyColumn(
      modifier = Modifier.fillMaxSize(),
      contentPadding = PaddingValues(16.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
      item {
        HeroFeatureCard(
          title = "CBT Test Evaluation Complete",
          subtitle = "Score: $totalScore / $maxMarks Marks • Time Taken: $timeSpentStr",
          tagText = if (totalScore >= maxMarks / 2) "EXCELLENT" else "COMPLETED",
          icon = Icons.Default.FactCheck
        )
      }

      // Summary Metric Strip
      item {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          Surface(
            color = Color(0xFF065F46).copy(alpha = 0.2f),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.weight(1f)
          ) {
            Column(modifier = Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
              Text("Correct", fontSize = 11.sp, color = Color(0xFF34D399), fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
              Text("$correctCount", fontSize = 18.sp, color = Color(0xFF34D399), fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
            }
          }
          Surface(
            color = Color(0xFF991B1B).copy(alpha = 0.2f),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.weight(1f)
          ) {
            Column(modifier = Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
              Text("Incorrect", fontSize = 11.sp, color = Color(0xFFF87171), fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
              Text("$wrongCount", fontSize = 18.sp, color = Color(0xFFF87171), fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
            }
          }
          Surface(
            color = Color(0xFF1E293B),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.weight(1f)
          ) {
            Column(modifier = Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
              Text("Unattempted", fontSize = 11.sp, color = Color(0xFF94A3B8), fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
              Text("$unattemptedCount", fontSize = 18.sp, color = Color.White, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
            }
          }
        }
      }

      item { Text("Detailed Answer Key & Solutions", fontWeight = FontWeight.Bold, fontSize = 18.sp) }

      items(attemptQuestions.size) { qIdx ->
        val qItem = attemptQuestions[qIdx]
        val optionsList = listOf(qItem.optionA, qItem.optionB, qItem.optionC, qItem.optionD)
        val corrIdx = qItem.correctOption.coerceIn(0, optionsList.lastIndex.coerceAtLeast(0))
        val userSel = viewedAttempt.answers[qItem.id]
        val isCorrect = userSel == corrIdx

        GeoSectionCard(title = "Q${qIdx + 1}: ${if (isCorrect) "✅ +${qItem.marks} Marks" else if (userSel != null) "❌ -1 Negative" else "⚪ Unattempted (0)"}") {
          Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(qItem.questionText, fontSize = 13.sp)
            Text("Your Choice: ${if (userSel != null && userSel in optionsList.indices) optionsList[userSel] else "None"}", fontWeight = FontWeight.SemiBold, color = if (isCorrect) Color(0xFF072711) else Color.Red, fontSize = 12.sp)
            Text("Correct Answer: ${optionsList[corrIdx]}", fontWeight = FontWeight.Bold, color = Color(0xFF0061A4), fontSize = 12.sp)

            Spacer(Modifier.height(8.dp))
            Surface(
              color = Color(0xFF182438),
              shape = RoundedCornerShape(10.dp),
              border = BorderStroke(1.dp, Color(0xFF1E2D42)),
              modifier = Modifier.fillMaxWidth()
            ) {
              Text(
                text = "💡 Solution (${qItem.subject} • ${qItem.chapter}): Option ${qItem.correctOptionLetter} (${optionsList[corrIdx]}) is verified by the academic question bank.",
                fontSize = 11.sp,
                color = Color(0xFF38BDF8),
                modifier = Modifier.padding(10.dp),
                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
              )
            }
          }
        }
      }

      item {
        Button(
          onClick = {
            lastSubmittedAttemptId = null
            serializedAnswers = ""
          },
          modifier = Modifier.fillMaxWidth(),
          colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7))
        ) { Text("Back to Test Portal") }
      }
    }
  }
}

@Composable
fun StudentAiAssistantTab(student: Student) {
  var userPrompt by remember { mutableStateOf("") }
  val studentDisplayName = if (student.name == "No Enrolled Student") "there" else student.name
  val messages = remember {
    mutableStateListOf(
      "✨ **Gemini AI Study Mentor**:\nHello $studentDisplayName! I'm your dedicated AI Academic Mentor at My Tuition (FSI).\n\nAsk me to break down complex formulas, explain step-by-step concepts in Maths, Physics, Chemistry, or Biology, or design a custom 7-day revision routine!"
    )
  }
  var isThinking by remember { mutableStateOf(false) }
  val scope = rememberCoroutineScope()
  val listState = rememberLazyListState()
  val clipboardManager = LocalClipboardManager.current
  var copiedIndex by remember { mutableStateOf(-1) }

  val suggestionChips = remember {
    listOf(
      "⚡ King's Rule in Integration",
      "📅 7-Day Revision Routine",
      "📐 Rotational Dynamics & Inertia",
      "🧪 Le Chatelier's Principle",
      "🔬 SN1 vs SN2 Mechanisms",
      "🎯 MCQ Speed & Negative Marking",
      "🧬 Mendel's Laws of Genetics"
    )
  }

  // Smooth auto-scroll to the bottom whenever messages update or thinking state changes
  LaunchedEffect(messages.size, isThinking) {
    if (messages.isNotEmpty()) {
      listState.animateScrollToItem((messages.size).coerceAtLeast(0))
    }
  }

  Column(
    modifier = Modifier.fillMaxSize().padding(14.dp),
    verticalArrangement = Arrangement.SpaceBetween
  ) {
    Column(modifier = Modifier.weight(1f)) {
      // Header with Live Status & Clear Action
      Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
      ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f).padding(end = 8.dp)) {
          Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(22.dp))
          Spacer(Modifier.width(8.dp))
          Column(modifier = Modifier.weight(1f)) {
            Text("Gemini Academic Mentor", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(if (AiService.hasValidKey()) "Powered by Gemini 3.5 Flash" else "Instant Academic Knowledge Engine", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
          }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
          Surface(
            color = if (AiService.hasValidKey()) Color(0xFF064E3B) else Color(0xFF1E293B),
            shape = RoundedCornerShape(10.dp),
            border = BorderStroke(1.dp, if (AiService.hasValidKey()) Color(0xFF047857) else Color(0xFF334155))
          ) {
            Text(
              if (AiService.hasValidKey()) "CLOUD GEMINI" else "FAST ENGINE",
              fontSize = 9.sp,
              fontWeight = FontWeight.Bold,
              maxLines = 1,
              softWrap = false,
              overflow = TextOverflow.Ellipsis,
              color = if (AiService.hasValidKey()) Color(0xFF6EE7B7) else Color(0xFF38BDF8),
              modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
            )
          }
          if (messages.size > 1) {
            IconButton(
              onClick = {
                messages.clear()
                messages.add("✨ **Gemini AI Study Mentor**:\nHello $studentDisplayName! Chat reset. What would you like to explore next?")
              },
              modifier = Modifier.size(28.dp)
            ) {
              Icon(Icons.Default.Refresh, contentDescription = "Clear Chat", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
            }
          }
        }
      }

      // Chat Messages List
      LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().weight(1f),
        verticalArrangement = Arrangement.spacedBy(10.dp)
      ) {
        items(messages.size) { idx ->
          val msg = messages[idx]
          val isUser = msg.startsWith("You:")

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
          ) {
            Box(
              modifier = Modifier
                .fillMaxWidth(if (isUser) 0.82f else 0.94f)
                .clip(RoundedCornerShape(16.dp))
                .background(if (isUser) Color(0xFF0284C7) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                .border(
                  width = 1.dp,
                  color = if (isUser) Color(0xFF38BDF8) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                  shape = RoundedCornerShape(16.dp)
                )
                .padding(12.dp)
            ) {
              Column {
                if (!isUser) {
                  Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                  ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                      Icon(Icons.Default.Psychology, contentDescription = null, tint = Color(0xFF0284C7), modifier = Modifier.size(16.dp))
                      Spacer(Modifier.width(4.dp))
                      Text("Academic Mentor", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0284C7))
                    }
                    IconButton(
                      onClick = {
                        clipboardManager.setText(AnnotatedString(msg))
                        copiedIndex = idx
                      },
                      modifier = Modifier.size(22.dp)
                    ) {
                      Icon(
                        if (copiedIndex == idx) Icons.Default.Check else Icons.Outlined.ContentCopy,
                        contentDescription = "Copy text",
                        tint = if (copiedIndex == idx) Color(0xFF16A34A) else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(14.dp)
                      )
                    }
                  }
                }
                Text(
                  text = if (isUser) msg.removePrefix("You: ") else msg,
                  color = if (isUser) Color.White else MaterialTheme.colorScheme.onSurface,
                  fontSize = 13.sp,
                  lineHeight = 19.sp
                )
              }
            }
          }
        }

        if (isThinking) {
          item {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(8.dp),
              modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                .border(1.dp, Color(0xFF38BDF8).copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
              CircularProgressIndicator(modifier = Modifier.size(14.dp), color = Color(0xFF0284C7), strokeWidth = 2.dp)
              Text("Gemini is formulating step-by-step explanation...", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)
            }
          }
        }
      }
    }

    // Interactive Quick Topic Chips
    Column(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
      LazyRow(
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
      ) {
        items(suggestionChips) { chip ->
          Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
            modifier = Modifier.clickable {
              if (!isThinking) {
                userPrompt = chip
                val p = chip
                userPrompt = ""
                messages.add("You: $p")
                isThinking = true
                scope.launch {
                  val ans = AiService.askAssistant(p, student.strongestSubject)
                  isThinking = false
                  messages.add(ans)
                }
              }
            }
          ) {
            Text(
              text = chip,
              fontSize = 11.sp,
              fontWeight = FontWeight.Medium,
              color = MaterialTheme.colorScheme.primary,
              maxLines = 1,
              softWrap = false,
              overflow = TextOverflow.Ellipsis,
              modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
            )
          }
        }
      }

      // Input Row
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        OutlinedTextField(
          value = userPrompt,
          onValueChange = { userPrompt = it },
          placeholder = { Text("Ask any formula, theory, or study doubt...", fontSize = 12.sp) },
          modifier = Modifier.weight(1f),
          shape = RoundedCornerShape(24.dp),
          singleLine = true
        )
        FloatingActionButton(
          onClick = {
            if (userPrompt.isNotBlank() && !isThinking) {
              val p = userPrompt
              userPrompt = ""
              messages.add("You: $p")
              isThinking = true
              scope.launch {
                val ans = AiService.askAssistant(p, student.strongestSubject)
                isThinking = false
                messages.add(ans)
              }
            }
          },
          containerColor = Color(0xFF0061A4),
          contentColor = Color.White,
          shape = CircleShape,
          modifier = Modifier.size(48.dp)
        ) {
          Icon(Icons.Default.Send, contentDescription = "Send question", modifier = Modifier.size(20.dp))
        }
      }
    }
  }
}

@Composable
fun StudentStudyTrackerTab(
  studyLogs: List<StudyLogItem>,
  doubts: List<DoubtItem>,
  student: Student? = null
) {
  val context = LocalContext.current
  var showDoubtForm by remember { mutableStateOf(false) }
  var doubtSub by remember { mutableStateOf("Physics") }
  var doubtChap by remember { mutableStateOf("Rotational") }
  var doubtTxt by remember { mutableStateOf("") }
  var attachedPhotoUrl by remember { mutableStateOf<String?>(null) }

  val photoPickerLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.PickVisualMedia()
  ) { uri: Uri? ->
    if (uri != null) {
      try {
        val dir = File(context.filesDir, "doubt_attachments").apply { mkdirs() }
        val destFile = File(dir, "doubt_${System.currentTimeMillis()}.jpg")
        context.contentResolver.openInputStream(uri)?.use { input ->
          destFile.outputStream().use { output ->
            input.copyTo(output)
          }
        }
        attachedPhotoUrl = if (destFile.exists() && destFile.length() > 0L) {
          Uri.fromFile(destFile).toString()
        } else {
          uri.toString()
        }
      } catch (_: Exception) {
        attachedPhotoUrl = uri.toString()
      }
    }
  }

  var studyHoursInput by remember { mutableStateOf("4.5") }
  var studySubsInput by remember { mutableStateOf("Mathematics, Physics") }
  val coroutineScope = rememberCoroutineScope()

  if (showDoubtForm) {
    AlertDialog(
      onDismissRequest = { showDoubtForm = false },
      title = { Text("Submit Doubt to Faculty", fontWeight = FontWeight.Bold) },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
          OutlinedTextField(value = doubtSub, onValueChange = { doubtSub = it }, label = { Text("Subject") }, modifier = Modifier.fillMaxWidth())
          OutlinedTextField(value = doubtChap, onValueChange = { doubtChap = it }, label = { Text("Chapter / Topic") }, modifier = Modifier.fillMaxWidth())
          OutlinedTextField(value = doubtTxt, onValueChange = { doubtTxt = it }, label = { Text("Question Description") }, modifier = Modifier.fillMaxWidth().height(80.dp))

          if (attachedPhotoUrl != null) {
            Surface(
              color = Color(0xFFDCFCE7),
              shape = RoundedCornerShape(10.dp),
              modifier = Modifier.fillMaxWidth().border(1.dp, Color(0xFF15803D), RoundedCornerShape(10.dp))
            ) {
              Row(
                modifier = Modifier.padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
              ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.weight(1f)) {
                  Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF15803D), modifier = Modifier.size(16.dp))
                  Text(
                    text = attachedPhotoUrl?.substringAfterLast("/") ?: "Diagram Attached.jpg",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF15803D),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                  )
                }
                IconButton(onClick = { attachedPhotoUrl = null }, modifier = Modifier.size(24.dp)) {
                  Icon(Icons.Default.Close, contentDescription = "Clear Photo", tint = Color(0xFF15803D), modifier = Modifier.size(14.dp))
                }
              }
            }
          } else {
            OutlinedButton(
              onClick = {
                try {
                  photoPickerLauncher.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                  )
                } catch (_: Exception) {
                  // Fallback in headless/test environments without a system media picker activity
                  val dir = File(context.filesDir, "doubt_attachments").apply { mkdirs() }
                  val fallbackFile = File(dir, "query_diagram_${System.currentTimeMillis()}.jpg")
                  fallbackFile.writeText("Diagram attachment for $doubtSub - $doubtChap")
                  attachedPhotoUrl = Uri.fromFile(fallbackFile).toString()
                }
              },
              shape = RoundedCornerShape(12.dp),
              modifier = Modifier.fillMaxWidth()
            ) {
              Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Default.PhotoCamera, contentDescription = null, modifier = Modifier.size(16.dp))
                Text("Attach Query Diagram Photo", fontSize = 12.sp)
              }
            }
          }
        }
      },
      confirmButton = {
        Button(
          onClick = {
            if (doubtTxt.isNotBlank()) {
              AppRepository.submitStudentDoubt(
                subject = doubtSub,
                chapter = doubtChap,
                text = doubtTxt,
                photoUrl = attachedPhotoUrl,
                studentIdOverride = student?.id,
                studentNameOverride = student?.name
              )
              doubtTxt = ""
              showDoubtForm = false
              attachedPhotoUrl = null
            }
          },
          colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0061A4))
        ) { Text("Submit Doubt") }
      },
      dismissButton = {
        TextButton(onClick = {
          showDoubtForm = false
          attachedPhotoUrl = null
        }) { Text("Cancel") }
      },
      containerColor = MaterialTheme.colorScheme.surface,
      shape = RoundedCornerShape(24.dp)
    )
  }

  LazyColumn(
    modifier = Modifier.fillMaxSize(),
    contentPadding = PaddingValues(16.dp),
    verticalArrangement = Arrangement.spacedBy(16.dp)
  ) {
    item {
      Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
          Text("Daily Study Consistency Log", fontWeight = FontWeight.Bold, fontSize = 18.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
          Text("7 Day study streak active 🔥", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Button(onClick = { showDoubtForm = true }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary), shape = RoundedCornerShape(16.dp)) {
          Icon(Icons.Default.HelpOutline, contentDescription = null, modifier = Modifier.size(16.dp))
          Spacer(Modifier.width(6.dp))
          Text("Ask Doubt", fontSize = 12.sp, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
        }
      }
    }

    // Log Today Study Card
    item {
      GeoSectionCard(title = "Log Today's Self-Study") {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
          OutlinedTextField(value = studyHoursInput, onValueChange = { studyHoursInput = it }, label = { Text("Hours") }, modifier = Modifier.width(80.dp), singleLine = true)
          OutlinedTextField(value = studySubsInput, onValueChange = { studySubsInput = it }, label = { Text("Subjects Studied") }, modifier = Modifier.weight(1f), singleLine = true)
          Button(
            onClick = {
              val h = studyHoursInput.toFloatOrNull() ?: 2f
              AppRepository.logDailyStudy(h, studySubsInput, "Self Revision")
            },
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF072711)),
            modifier = Modifier.height(56.dp)
          ) { Text("+ Log", maxLines = 1, softWrap = false) }
        }
      }
    }

    item { Text("Recent Study Activity History", fontWeight = FontWeight.Bold, fontSize = 16.sp) }
    items(studyLogs) { sl ->
      GeoSectionCard(title = "${sl.hours} Hours • ${sl.subjects}", actionText = sl.date) {
        Text("Chapters: ${sl.chapters}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
      }
    }

    item { Text("My Submitted Doubts Tracker", fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.padding(top = 8.dp)) }
    items(doubts) { db ->
      var showExpandedPhoto by remember { mutableStateOf(false) }

      if (showExpandedPhoto && db.photoUrl != null) {
        AlertDialog(
          onDismissRequest = { showExpandedPhoto = false },
          title = { Text("Question Attachment Preview", fontSize = 14.sp, fontWeight = FontWeight.Bold) },
          text = {
            Box(
              modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF0F172A)),
              contentAlignment = Alignment.Center
            ) {
              Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.Image, contentDescription = null, tint = Color.White, modifier = Modifier.size(48.dp))
                Text("📸 [Simulated Diagram]", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Text(db.photoUrl!!, color = Color(0xFF94A3B8), fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("Concept: ${db.chapter}", color = Color(0xFF38BDF8), fontSize = 12.sp, fontWeight = FontWeight.Medium)
              }
            }
          },
          confirmButton = {
            TextButton(onClick = { showExpandedPhoto = false }) {
              Text("Close")
            }
          },
          containerColor = MaterialTheme.colorScheme.surface,
          shape = RoundedCornerShape(20.dp)
        )
      }

      GeoSectionCard(title = db.subject, actionText = db.status) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
          Text(db.questionText, fontSize = 13.sp, fontWeight = FontWeight.Medium)
          
          if (db.photoUrl != null) {
            Surface(
              color = MaterialTheme.colorScheme.surfaceVariant,
              shape = RoundedCornerShape(12.dp),
              modifier = Modifier
                .fillMaxWidth()
                .clickable { showExpandedPhoto = true }
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
            ) {
              Row(
                modifier = Modifier.padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
              ) {
                Icon(Icons.Default.PhotoCamera, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                Text("Attached: Math/Physics Query Photo.jpg", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.primary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Text("TAP TO VIEW", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
              }
            }
          }

          if (db.reply != null) {
            Spacer(Modifier.height(6.dp))
            Surface(
              color = Color(0xFF064E3B),
              shape = RoundedCornerShape(10.dp),
              border = BorderStroke(1.dp, Color(0xFF047857)),
              modifier = Modifier.fillMaxWidth()
            ) {
              Text(
                text = if (db.reply!!.startsWith("🤖")) db.reply!! else "✅ Faculty Reply: ${db.reply}",
                fontSize = 12.sp,
                color = Color(0xFF6EE7B7),
                modifier = Modifier.padding(10.dp)
              )
            }
          } else if (db.status == "Pending") {
            Spacer(Modifier.height(8.dp))
            var aiLoading by remember { mutableStateOf(false) }
            
            if (aiLoading) {
              Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.primary)
                Text("AI Tutor is analyzing conceptual steps...", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
              }
            } else {
              Button(
                onClick = {
                  aiLoading = true
                  coroutineScope.launch {
                    val aiResponse = "🤖 " + AiService.solveAcademicDoubt(db.subject, db.chapter, db.questionText)
                    AppRepository.replyToDoubt(db.id, aiResponse)
                    aiLoading = false
                  }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEFF6FF), contentColor = Color(0xFF0061A4)),
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                modifier = Modifier.height(30.dp)
              ) {
                Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(12.dp))
                Spacer(Modifier.width(4.dp))
                Text("Instant AI Tutor Resolve", fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
              }
            }
          }
        }
      }
    }
  }
}
