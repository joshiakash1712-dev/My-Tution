package com.example.ui.student

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import com.example.ui.components.*
import com.example.ui.teacher.TimetableCard
import com.example.ui.timetable.DynamicTimetableScreen
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun StudentMainContent(currentTab: Int, onTabChange: (Int) -> Unit = {}) {
  val students by AppRepository.students.collectAsState()
  val activeStudent = remember(students) { 
    students.firstOrNull() ?: Student(
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

  when (currentTab) {
    0 -> StudentDashboardTab(activeStudent, tests, homework, goal, badges, onTabChange)
    1 -> StudentTimetableTab(timetable, activeStudent.batch)
    2 -> StudentOnlineTestTab()
    3 -> StudentAiAssistantTab(activeStudent)
    4 -> StudentStudyTrackerTab(studyLogs, doubts)
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
              Text(bg.name, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
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
              Text(tName, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
              Surface(color = Color(0xFFD1E4FF), shape = RoundedCornerShape(8.dp)) {
                Text("$score / 100", fontWeight = FontWeight.Bold, color = Color(0xFF001D36), fontSize = 12.sp, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
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
      GeoSectionCard(title = "Pending Homework Assignments") {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          homework.forEach { hw ->
            Row(
              modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(if (hw.status == "Pending") MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.surfaceVariant).padding(12.dp),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Column(Modifier.weight(1f)) {
                Text(hw.title, fontWeight = FontWeight.Medium, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface)
                Text("Due: ${hw.dueDate}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
              }
              Text(hw.status.uppercase(), fontWeight = FontWeight.Bold, fontSize = 10.sp, color = if (hw.status == "Pending") MaterialTheme.colorScheme.onTertiaryContainer else Color(0xFF22C55E))
            }
          }
        }
      }
    }
  }
}

@Composable
fun StudentTimetableTab(timetable: List<TimetableItem>, batchName: String) {
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
      StudentWeeklyTimetableList(timetable, batchName)
    }
  }
}

@Composable
fun StudentWeeklyTimetableList(timetable: List<TimetableItem>, batchName: String) {
  val daysOfWeek = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday")
  val studentBatchSchedule = remember(timetable, batchName) { timetable.filter { it.batch == batchName } }

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
fun StudentOnlineTestTab() {
  var testActive by remember { mutableStateOf(false) }
  var currentQuestionIdx by remember { mutableStateOf(0) }
  var selectedOptions by remember { mutableStateOf<Map<Int, Int>>(emptyMap()) }
  var testSubmitted by remember { mutableStateOf(false) }

  val sampleQuestions = listOf(
    "A particle moves in a circle of radius R with constant angular acceleration α. If it starts from rest, at what time will the radial acceleration equal tangential acceleration?" to listOf("√(1/α)", "1/α", "√(2/α)", "2/α") to 0,
    "The value of limit (x->0) [ (1 - cos 2x) / x^2 ] is:" to listOf("1", "2", "0", "4") to 1,
    "Which of the following has the highest lattice energy?" to listOf("NaCl", "MgO", "LiF", "KCl") to 1
  )

  if (!testActive && !testSubmitted) {
    LazyColumn(
      modifier = Modifier.fillMaxSize(),
      contentPadding = PaddingValues(16.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
      item {
        HeroFeatureCard(
          title = "Online CBT Examination Portal",
          subtitle = "Instant Evaluation & Answer Key Generator",
          tagText = "JEE PATTERN MCQ",
          icon = Icons.Default.Quiz
        )
      }

      item {
        GeoSectionCard(title = "Available CBT Examinations") {
          Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("All India JEE Speed Test #12\n3 Questions • +4 Marks / -1 Negative • Time Limit: 15 Mins", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Button(
              onClick = {
                testActive = true
                currentQuestionIdx = 0
                selectedOptions = emptyMap()
              },
              colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
              modifier = Modifier.fillMaxWidth()
            ) { Text("Start Live Test Now") }
          }
        }
      }
    }
  } else if (testActive) {
    val qData = sampleQuestions[currentQuestionIdx]
    val (qTuple, correctIdx) = qData
    val (qText, opts) = qTuple

    Column(
      modifier = Modifier.fillMaxSize().padding(16.dp),
      verticalArrangement = Arrangement.SpaceBetween
    ) {
      Column(
        modifier = Modifier
          .weight(1f)
          .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
      ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
          Text("Question ${currentQuestionIdx + 1} of ${sampleQuestions.size}", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.primary)
          Surface(color = MaterialTheme.colorScheme.tertiaryContainer, shape = RoundedCornerShape(12.dp)) {
            Text("⏱️ 14:12 Remaining", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onTertiaryContainer, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
          }
        }

        GeoSectionCard(title = "Physics / Math MCQ") {
          Text(qText, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
          Spacer(Modifier.height(14.dp))
          Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            opts.forEachIndexed { idx, oTxt ->
              val sel = selectedOptions[currentQuestionIdx] == idx
              Row(
                modifier = Modifier
                  .fillMaxWidth()
                  .clip(RoundedCornerShape(14.dp))
                  .background(if (sel) Color(0xFFD1E4FF) else Color(0xFFEEF0F6))
                  .border(2.dp, if (sel) Color(0xFF0061A4) else Color.Transparent, RoundedCornerShape(14.dp))
                  .clickable { selectedOptions = selectedOptions + (currentQuestionIdx to idx) }
                  .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
              ) {
                RadioButton(selected = sel, onClick = { selectedOptions = selectedOptions + (currentQuestionIdx to idx) })
                Spacer(Modifier.width(10.dp))
                Text(oTxt, fontSize = 14.sp, fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal)
              }
            }
          }
        }
      }

      Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        if (currentQuestionIdx > 0) {
          OutlinedButton(onClick = { currentQuestionIdx-- }, modifier = Modifier.weight(1f).height(50.dp)) { Text("Previous") }
        }
        if (currentQuestionIdx < sampleQuestions.size - 1) {
          Button(onClick = { currentQuestionIdx++ }, modifier = Modifier.weight(1f).height(50.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0061A4))) { Text("Next Question") }
        } else {
          Button(
            onClick = {
              testActive = false
              testSubmitted = true
            },
            modifier = Modifier.weight(1f).height(50.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF072711))
          ) { Text("Submit CBT Test") }
        }
      }
    }
  } else if (testSubmitted) {
    var totalScore = 0
    sampleQuestions.forEachIndexed { idx, item ->
      if (selectedOptions[idx] == item.second) totalScore += 4 else if (selectedOptions.containsKey(idx)) totalScore -= 1
    }

    LazyColumn(
      modifier = Modifier.fillMaxSize(),
      contentPadding = PaddingValues(16.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
      item {
        HeroFeatureCard(
          title = "CBT Test Evaluation Complete",
          subtitle = "You scored $totalScore out of 12 Marks!",
          tagText = "AUTO EVALUATED",
          icon = Icons.Default.FactCheck
        )
      }

      item { Text("Detailed Answer Key & Solutions", fontWeight = FontWeight.Bold, fontSize = 18.sp) }

      items(sampleQuestions.size) { qIdx ->
        val itemTuple = sampleQuestions[qIdx]
        val (qPair, corrIdx) = itemTuple
        val (qTxt, optionsList) = qPair
        val userSel = selectedOptions[qIdx]
        val isCorrect = userSel == corrIdx

        GeoSectionCard(title = "Q${qIdx + 1}: ${if (isCorrect) "✅ +4 Marks" else if (userSel != null) "❌ -1 Negative" else "⚪ Unattempted (0)"}") {
          Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(qTxt, fontSize = 13.sp)
            Text("Your Choice: ${if (userSel != null) optionsList[userSel] else "None"}", fontWeight = FontWeight.SemiBold, color = if (isCorrect) Color(0xFF072711) else Color.Red, fontSize = 12.sp)
            Text("Correct Answer: ${optionsList[corrIdx]}", fontWeight = FontWeight.Bold, color = Color(0xFF0061A4), fontSize = 12.sp)

            val explanation = when(qIdx) {
              0 -> "💡 AI Explanation: Radial acceleration is a_r = ω^2 * R = (α * t)^2 * R. Tangential acceleration is a_t = α * R. Setting them equal gives: α^2 * t^2 * R = α * R => t^2 = 1/α => t = √(1/α). Hence, option A is correct."
              1 -> "💡 AI Explanation: Use the trigonometric identity 1 - cos 2x = 2 * sin^2(x). Therefore, limit (x->0) [ 2 * sin^2(x) / x^2 ] = 2 * (limit (x->0) [ sin(x) / x ])^2 = 2 * (1)^2 = 2. Hence, option B is correct."
              2 -> "💡 AI Explanation: Lattice energy is directly proportional to ionic charge magnitude (q1*q2) and inversely proportional to internuclear separation. MgO consists of Mg2+ and O2- ions (doubly charged), whereas NaCl, LiF, KCl are singly charged (+1/-1). MgO's electrostatic potential attraction is significantly higher. Hence, option B is correct."
              else -> "💡 AI Explanation: Step-by-step conceptual logic isolation yields option B."
            }
            Spacer(Modifier.height(8.dp))
            Surface(
              color = Color(0xFF182438),
              shape = RoundedCornerShape(10.dp),
              border = BorderStroke(1.dp, Color(0xFF1E2D42)),
              modifier = Modifier.fillMaxWidth()
            ) {
              Text(
                text = explanation,
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
        Button(onClick = { testSubmitted = false }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7))) { Text("Back to Test Portal") }
      }
    }
  }
}

@Composable
fun StudentAiAssistantTab(student: Student) {
  var userPrompt by remember { mutableStateOf("") }
  val studentDisplayName = if (student.name == "No Enrolled Student") "there" else student.name
  val messages = remember { mutableStateListOf("✨ **Gemini AI Study Mentor**:\nHello $studentDisplayName! I am your AI Study Mentor. Ask me to solve any academic doubt, explain complex concepts, or generate a customized study revision plan.") }
  var isThinking by remember { mutableStateOf(false) }
  val scope = rememberCoroutineScope()

  Column(
    modifier = Modifier.fillMaxSize().padding(16.dp),
    verticalArrangement = Arrangement.SpaceBetween
  ) {
    Column(modifier = Modifier.weight(1f)) {
      Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = Color(0xFF38BDF8))
          Spacer(Modifier.width(8.dp))
          Text("Gemini Academic Assistant", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color(0xFFF8FAFC))
        }
        Surface(color = if (AiService.hasValidKey()) Color(0xFF064E3B) else Color(0xFF182438), shape = RoundedCornerShape(10.dp), border = BorderStroke(1.dp, Color(0xFF1E2D42))) {
          Text(if (AiService.hasValidKey()) "CLOUD GEMINI" else "HYBRID ENGINE", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = if (AiService.hasValidKey()) Color(0xFF6EE7B7) else Color(0xFF38BDF8), modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
        }
      }

      LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        reverseLayout = false
      ) {
        items(messages) { msg ->
          val isUser = msg.startsWith("You:")
          Row(Modifier.fillMaxWidth(), horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start) {
            Box(
              modifier = Modifier
                .fillMaxWidth(0.85f)
                .clip(RoundedCornerShape(20.dp))
                .background(if (isUser) Color(0xFF0284C7) else Color(0xFF111C2E))
                .border(1.dp, if (isUser) Color(0xFF38BDF8) else Color(0xFF1E2D42), RoundedCornerShape(20.dp))
                .padding(14.dp)
            ) {
              Text(
                text = if (isUser) msg.removePrefix("You: ") else msg,
                color = Color(0xFFF8FAFC),
                fontSize = 13.sp
              )
            }
          }
        }
        if (isThinking) {
          item {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              CircularProgressIndicator(modifier = Modifier.size(16.dp), color = MaterialTheme.colorScheme.primary, strokeWidth = 2.dp)
              Text("Gemini is formulating academic explanation...", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)
            }
          }
        }
      }
    }

    // Input Row
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(top = 10.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
      OutlinedTextField(
        value = userPrompt,
        onValueChange = { userPrompt = it },
        placeholder = { Text("Ask doubt, integration trick, schedule...", fontSize = 13.sp) },
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
        shape = CircleShape
      ) { Icon(Icons.Default.Send, contentDescription = null) }
    }
  }
}

@Composable
fun StudentStudyTrackerTab(studyLogs: List<StudyLogItem>, doubts: List<DoubtItem>) {
  var showDoubtForm by remember { mutableStateOf(false) }
  var doubtSub by remember { mutableStateOf("Physics") }
  var doubtChap by remember { mutableStateOf("Rotational") }
  var doubtTxt by remember { mutableStateOf("") }
  var attachedPhotoUrl by remember { mutableStateOf<String?>(null) }

  var studyHoursInput by remember { mutableStateOf("4.5") }
  var studySubsInput by remember { mutableStateOf("Mathematics, Physics") }

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
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                  Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF15803D), modifier = Modifier.size(16.dp))
                  Text("Diagram Attached.jpg", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF15803D))
                }
                IconButton(onClick = { attachedPhotoUrl = null }, modifier = Modifier.size(24.dp)) {
                  Icon(Icons.Default.Close, contentDescription = "Clear Photo", tint = Color(0xFF15803D), modifier = Modifier.size(14.dp))
                }
              }
            }
          } else {
            OutlinedButton(
              onClick = { attachedPhotoUrl = "https://images.unsplash.com/photo-1635070041078-e363dbe005cb?w=400" },
              shape = RoundedCornerShape(12.dp),
              modifier = Modifier.fillMaxWidth()
            ) {
              Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Default.PhotoCamera, contentDescription = null, modifier = Modifier.size(16.dp))
                Text("Simulate Photo Attachment", fontSize = 12.sp)
              }
            }
          }
        }
      },
      confirmButton = {
        Button(
          onClick = {
            if (doubtTxt.isNotBlank()) {
              AppRepository.submitStudentDoubt(doubtSub, doubtChap, doubtTxt, attachedPhotoUrl)
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
        Column {
          Text("Daily Study Consistency Log", fontWeight = FontWeight.Bold, fontSize = 18.sp)
          Text("7 Day study streak active 🔥", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Button(onClick = { showDoubtForm = true }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary), shape = RoundedCornerShape(16.dp)) {
          Icon(Icons.Default.HelpOutline, contentDescription = null, modifier = Modifier.size(16.dp))
          Spacer(Modifier.width(6.dp))
          Text("Ask Doubt", fontSize = 12.sp)
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
          ) { Text("+ Log") }
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
                Text("Attached: Math/Physics Query Photo.jpg", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
                Text("TAP TO VIEW", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                  val aiResponse = when (db.subject.lowercase()) {
                    "physics" -> "🤖 AI Tutor: 1. Let's look at the conservation of Angular Momentum: L = I * ω.\n2. When an ice skater pulls their arms in, they bring mass closer to the axis of rotation, thereby decreasing the Moment of Inertia (I).\n3. Since there is no net external torque (τ_ext = 0), the total angular momentum (L) is conserved.\n4. Therefore, as I decreases, the angular velocity (ω) must increase to keep L constant. Consequently, they spin much faster!"
                    "chemistry" -> "🤖 AI Tutor: 1. Adding an inert gas at constant pressure increases the total volume of the container to keep pressure stable.\n2. This expansion lowers the partial pressures/concentrations of all reacting species.\n3. According to Le Chatelier's Principle, the equilibrium will shift in the direction that produces more moles of gas to counteract the decrease in concentration.\n4. If Δn > 0, shift is forward. If Δn < 0, shift is backward. If Δn = 0, no shift occurs."
                    else -> "🤖 AI Tutor: Let's break down your question step-by-step:\n1. First, we identify the core formula and physical constants relevant to ${db.chapter}.\n2. We isolate the unknown variable and substitute given constraints.\n3. The step-by-step derivation shows how equilibrium/forces balance out.\n4. Conceptual Check: Always double-check dimensional consistency to avoid calculation errors."
                  }
                  
                  android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    AppRepository.replyToDoubt(db.id, aiResponse)
                    aiLoading = false
                  }, 1200)
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEFF6FF), contentColor = Color(0xFF0061A4)),
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                modifier = Modifier.height(30.dp)
              ) {
                Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(12.dp))
                Spacer(Modifier.width(4.dp))
                Text("Instant AI Tutor Resolve", fontSize = 10.sp, fontWeight = FontWeight.Bold)
              }
            }
          }
        }
      }
    }
  }
}
