package com.example.ui.attendance

import android.app.DatePickerDialog
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.*
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DailyAttendanceRegister(
  modifier: Modifier = Modifier,
  currentUserRole: String = "Admin",
  currentUserId: String = "system_fsi",
  onNavigateToHistory: () -> Unit = {}
) {
  val context = LocalContext.current
  val allStudents by AppRepository.students.collectAsState()
  val allBatches by AppRepository.batches.collectAsState()
  val attendanceRecords by AppRepository.attendance.collectAsState()

  // Ensure default batches if list is empty
  val activeBatches = remember(allBatches) {
    if (allBatches.isEmpty()) SeedData.defaultBatches else allBatches
  }

  // Date Selection State (dd/MM/yyyy)
  val calendar = remember { Calendar.getInstance() }
  var selectedDay by remember { mutableIntStateOf(calendar.get(Calendar.DAY_OF_MONTH)) }
  var selectedMonth by remember { mutableIntStateOf(calendar.get(Calendar.MONTH) + 1) } // 1..12
  var selectedYear by remember { mutableIntStateOf(calendar.get(Calendar.YEAR)) }

  val formattedDate = remember(selectedDay, selectedMonth, selectedYear) {
    String.format(Locale.getDefault(), "%02d/%02d/%04d", selectedDay, selectedMonth, selectedYear)
  }

  val displayDateLabel = remember(selectedDay, selectedMonth, selectedYear) {
    val cal = Calendar.getInstance()
    cal.set(selectedYear, selectedMonth - 1, selectedDay)
    SimpleDateFormat("EEEE, dd MMM yyyy", Locale.getDefault()).format(cal.time)
  }

  val isToday = remember(formattedDate) {
    formattedDate == AppRepository.getCurrentDateStr()
  }

  // Batch-First Workflow: selectedBatchName is null initially until user selects a batch
  var selectedBatchName by remember { mutableStateOf<String?>(null) }
  var selectedStatusTabFilter by remember { mutableStateOf("All") } // "All", "Present", "Absent", "Unmarked"

  // Reason attribution dialog
  var reasonDialogStudent by remember { mutableStateOf<Student?>(null) }

  // Filter students strictly for the selected Batch
  val batchStudents = remember(allStudents, selectedBatchName) {
    if (selectedBatchName == null) {
      emptyList()
    } else {
      allStudents.filter { stu -> stu.batch.equals(selectedBatchName, ignoreCase = true) }
    }
  }

  // Compute daily status mapping for the selected date from Room StateFlow
  val dailyRecordsForDate = remember(attendanceRecords, formattedDate) {
    attendanceRecords.filter { it.date == formattedDate }
  }

  val studentStatusMap = remember(dailyRecordsForDate, batchStudents) {
    val map = mutableMapOf<String, AttendanceRecord>()
    dailyRecordsForDate.forEach { rec ->
      map[rec.studentId] = rec
    }
    map
  }

  // Daily Metrics for Selected Batch
  val totalInBatch = batchStudents.size
  val presentCount = batchStudents.count { studentStatusMap[it.id]?.status == "Present" }
  val absentCount = batchStudents.count { studentStatusMap[it.id]?.status == "Absent" }
  val unmarkedCount = totalInBatch - (presentCount + absentCount)
  val attendancePercent = if (totalInBatch > 0) ((presentCount.toFloat() / totalInBatch) * 100).toInt() else 0

  // Filter students further by Status Tab if chosen
  val finalDisplayStudents = remember(batchStudents, studentStatusMap, selectedStatusTabFilter) {
    when (selectedStatusTabFilter) {
      "Present" -> batchStudents.filter { studentStatusMap[it.id]?.status == "Present" }
      "Absent" -> batchStudents.filter { studentStatusMap[it.id]?.status == "Absent" }
      "Unmarked" -> batchStudents.filter { studentStatusMap[it.id] == null }
      else -> batchStudents
    }
  }

  // Absence Reason Dialog
  if (reasonDialogStudent != null) {
    val stu = reasonDialogStudent!!
    var selectedReason by remember { mutableStateOf(studentStatusMap[stu.id]?.reason ?: "Medical Leave") }
    var customReason by remember { mutableStateOf("") }
    val predefinedReasons = listOf("Medical Leave", "Sick / Fever", "Family Function", "Out of Station", "Exam Prep", "Weather / Rain", "Other")

    AlertDialog(
      onDismissRequest = { reasonDialogStudent = null },
      containerColor = Color(0xFF111C2E),
      titleContentColor = Color.White,
      textContentColor = Color(0xFF94A3B8),
      icon = {
        Icon(Icons.Default.EventBusy, contentDescription = null, tint = Color(0xFFEF4444), modifier = Modifier.size(28.dp))
      },
      title = {
        Text("Absence Reason: ${stu.name}", fontWeight = FontWeight.Bold, fontSize = 16.sp, textAlign = TextAlign.Center, color = Color.White)
      },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
          Text(
            "Select reason for absence on $formattedDate. This will be recorded in the Room Database and notified to parent.",
            fontSize = 12.sp,
            color = Color(0xFF94A3B8)
          )

          Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            predefinedReasons.chunked(2).forEach { rowReasons ->
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
              ) {
                rowReasons.forEach { r ->
                  val isSel = selectedReason == r
                  Surface(
                    color = if (isSel) Color(0xFF991B1B).copy(alpha = 0.3f) else Color(0xFF0A1220),
                    border = BorderStroke(1.dp, if (isSel) Color(0xFFEF4444) else Color(0xFF1E2D42)),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                      .weight(1f)
                      .clickable { selectedReason = r }
                  ) {
                    Text(
                      text = r,
                      fontSize = 11.sp,
                      fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                      color = if (isSel) Color(0xFFFCA5A5) else Color(0xFFCBD5E1),
                      modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                      textAlign = TextAlign.Center
                    )
                  }
                }
              }
            }
          }

          if (selectedReason == "Other") {
            OutlinedTextField(
              value = customReason,
              onValueChange = { customReason = it },
              label = { Text("Specify Custom Reason") },
              placeholder = { Text("e.g. Travel, Personal") },
              modifier = Modifier.fillMaxWidth(),
              singleLine = true
            )
          }
        }
      },
      confirmButton = {
        Button(
          onClick = {
            val finalReason = if (selectedReason == "Other" && customReason.isNotBlank()) customReason else selectedReason
            val success = AppRepository.saveSingleAttendance(
              studentId = stu.id,
              studentName = stu.name,
              batchName = stu.batch,
              date = formattedDate,
              status = "Absent",
              reason = finalReason,
              recordedBy = currentUserId
            )
            if (success) {
              Toast.makeText(context, "${stu.name} marked Absent ($finalReason).", Toast.LENGTH_SHORT).show()
            } else {
              Toast.makeText(context, "Attendance locked (22-hour edit window expired)", Toast.LENGTH_SHORT).show()
            }
            reasonDialogStudent = null
          },
          colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
          shape = RoundedCornerShape(10.dp)
        ) {
          Text("Confirm Absent", fontWeight = FontWeight.Bold)
        }
      },
      dismissButton = {
        TextButton(onClick = { reasonDialogStudent = null }) {
          Text("Cancel", color = Color(0xFF94A3B8))
        }
      }
    )
  }

  LazyColumn(
    modifier = modifier.fillMaxSize(),
    contentPadding = PaddingValues(16.dp),
    verticalArrangement = Arrangement.spacedBy(14.dp)
  ) {
    // 1. Header Banner with Date Navigator
    item {
      Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0D1526)),
        border = BorderStroke(1.dp, Color(0xFF1E2D42)),
        elevation = CardDefaults.cardElevation(2.dp),
        modifier = Modifier.fillMaxWidth()
      ) {
        Column(
          modifier = Modifier.padding(16.dp),
          verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Column {
              Text(
                "Daily Attendance Register",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
              )
              Text(
                if (selectedBatchName == null) "Step 1: Select a Batch to Take Attendance" else "Active Batch: $selectedBatchName",
                color = Color(0xFF94A3B8),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
              )
            }

            Surface(
              color = Color(0xFF1E293B),
              shape = RoundedCornerShape(10.dp),
              border = BorderStroke(1.dp, Color(0xFF334155))
            ) {
              Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
              ) {
                Icon(Icons.Default.Storage, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(14.dp))
                Text("Room DB Active", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
              }
            }
          }

          // Date Navigator Toolbar
          Surface(
            color = Color(0xFF111C2E),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, Color(0xFF1E2D42)),
            modifier = Modifier.fillMaxWidth()
          ) {
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                // Today Button
                Surface(
                  color = if (isToday) Color(0xFF0061A4) else Color(0xFF1E293B),
                  shape = RoundedCornerShape(8.dp),
                  modifier = Modifier.clickable {
                    val now = Calendar.getInstance()
                    selectedDay = now.get(Calendar.DAY_OF_MONTH)
                    selectedMonth = now.get(Calendar.MONTH) + 1
                    selectedYear = now.get(Calendar.YEAR)
                  }
                ) {
                  Text(
                    "Today",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                  )
                }

                // Date label
                Text(
                  displayDateLabel,
                  color = Color.White,
                  fontSize = 12.sp,
                  fontWeight = FontWeight.SemiBold
                )
              }

              // Change Date Picker Dialog Trigger
              OutlinedButton(
                onClick = {
                  DatePickerDialog(
                    context,
                    { _, y, m, d ->
                      selectedYear = y
                      selectedMonth = m + 1
                      selectedDay = d
                    },
                    selectedYear,
                    selectedMonth - 1,
                    selectedDay
                  ).show()
                },
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF38BDF8)),
                border = BorderStroke(1.dp, Color(0xFF38BDF8).copy(alpha = 0.6f)),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
              ) {
                Icon(Icons.Default.CalendarMonth, contentDescription = "Pick Date", modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
                Text("Pick Date", fontSize = 11.sp)
              }
            }
          }
        }
      }
    }

    // ==========================================
    // CASE A: NO BATCH SELECTED (STEP 1: ASK FOR BATCH FIRST)
    // ==========================================
    if (selectedBatchName == null) {
      item {
        Card(
          shape = RoundedCornerShape(14.dp),
          colors = CardDefaults.cardColors(containerColor = Color(0xFF0D1526)),
          border = BorderStroke(1.dp, Color(0xFF1E2D42)),
          modifier = Modifier.fillMaxWidth()
        ) {
          Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
              Box(
                modifier = Modifier
                  .size(32.dp)
                  .clip(CircleShape)
                  .background(Color(0xFF0284C7).copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
              ) {
                Icon(
                  imageVector = Icons.Default.Class,
                  contentDescription = null,
                  tint = Color(0xFF38BDF8),
                  modifier = Modifier.size(18.dp)
                )
              }
              Column {
                Text(
                  "Select a Batch to Record Attendance",
                  fontWeight = FontWeight.Bold,
                  fontSize = 14.sp,
                  color = Color.White
                )
                Text(
                  "Please select a batch below to load student options for Present and Absent.",
                  fontSize = 11.sp,
                  color = Color(0xFF94A3B8)
                )
              }
            }
          }
        }
      }

      items(activeBatches, key = { it.id }) { batch ->
        val studentsInBatch = allStudents.filter { it.batch.equals(batch.name, ignoreCase = true) }
        val markedInBatch = dailyRecordsForDate.count { rec ->
          studentsInBatch.any { it.id == rec.studentId }
        }
        val isAllMarked = studentsInBatch.isNotEmpty() && markedInBatch == studentsInBatch.size

        Card(
          shape = RoundedCornerShape(14.dp),
          colors = CardDefaults.cardColors(containerColor = Color(0xFF0D1526)),
          border = BorderStroke(1.dp, if (isAllMarked) Color(0xFF065F46) else Color(0xFF1E2D42)),
          modifier = Modifier
            .fillMaxWidth()
            .clickable {
              selectedBatchName = batch.name
              selectedStatusTabFilter = "All"
            }
        ) {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
              Box(
                modifier = Modifier
                  .size(42.dp)
                  .clip(CircleShape)
                  .background(if (isAllMarked) Color(0xFF064E3B) else Color(0xFF1E2D42)),
                contentAlignment = Alignment.Center
              ) {
                Icon(
                  imageVector = if (isAllMarked) Icons.Default.CheckCircle else Icons.Default.Groups,
                  contentDescription = null,
                  tint = if (isAllMarked) Color(0xFF4ADE80) else Color(0xFF38BDF8),
                  modifier = Modifier.size(22.dp)
                )
              }

              Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                  batch.name,
                  fontWeight = FontWeight.Bold,
                  fontSize = 14.sp,
                  color = Color.White
                )
                Text(
                  "${batch.schedule} • ${batch.teacherName}",
                  fontSize = 11.sp,
                  color = Color(0xFF94A3B8)
                )
                Row(
                  horizontalArrangement = Arrangement.spacedBy(8.dp),
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  Text(
                    "${studentsInBatch.size} Students Enrolled",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF38BDF8)
                  )
                  Text("•", fontSize = 10.sp, color = Color(0xFF475569))
                  Text(
                    if (isAllMarked) "All Marked" else if (markedInBatch > 0) "$markedInBatch/${studentsInBatch.size} Marked" else "Not Marked Yet",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isAllMarked) Color(0xFF4ADE80) else if (markedInBatch > 0) Color(0xFFFBBF24) else Color(0xFF94A3B8)
                  )
                }
              }
            }

            Button(
              onClick = {
                selectedBatchName = batch.name
                selectedStatusTabFilter = "All"
              },
              shape = RoundedCornerShape(8.dp),
              colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
              contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
            ) {
              Text("Select", fontSize = 11.sp, fontWeight = FontWeight.Bold)
              Spacer(Modifier.width(4.dp))
              Icon(Icons.Default.ArrowForward, contentDescription = null, modifier = Modifier.size(14.dp))
            }
          }
        }
      }
    } else {
      // ==========================================
      // CASE B: BATCH SELECTED -> SHOW STUDENTS WITH PRESENT / ABSENT OPTIONS
      // ==========================================

      // 2. Active Batch Selector Bar & Switch Batch Button
      item {
        Card(
          shape = RoundedCornerShape(14.dp),
          colors = CardDefaults.cardColors(containerColor = Color(0xFF111C2E)),
          border = BorderStroke(1.dp, Color(0xFF1E2D42)),
          modifier = Modifier.fillMaxWidth()
        ) {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
              Icon(Icons.Default.School, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(20.dp))
              Column {
                Text(
                  "Batch: $selectedBatchName",
                  fontWeight = FontWeight.Bold,
                  fontSize = 13.sp,
                  color = Color.White
                )
                Text(
                  "${batchStudents.size} Students Enrolled",
                  fontSize = 11.sp,
                  color = Color(0xFF94A3B8)
                )
              }
            }

            OutlinedButton(
              onClick = { selectedBatchName = null },
              shape = RoundedCornerShape(8.dp),
              colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF38BDF8)),
              border = BorderStroke(1.dp, Color(0xFF38BDF8).copy(alpha = 0.6f)),
              contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
            ) {
              Icon(Icons.Default.SwapHoriz, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(14.dp))
              Spacer(Modifier.width(4.dp))
              Text("Switch Batch", fontSize = 11.sp, color = Color(0xFF38BDF8), fontWeight = FontWeight.Bold)
            }
          }
        }
      }

      // 3. Batch Metrics Summary Card (Total, Present, Absent, Unmarked, Rate)
      item {
        Card(
          shape = RoundedCornerShape(14.dp),
          colors = CardDefaults.cardColors(containerColor = Color(0xFF0D1526)),
          border = BorderStroke(1.dp, Color(0xFF1E2D42)),
          modifier = Modifier.fillMaxWidth()
        ) {
          Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
          ) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Text("Attendance Progress", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color.White)
              Text(
                "$attendancePercent% Attendance Rate",
                fontWeight = FontWeight.ExtraBold,
                fontSize = 12.sp,
                color = if (attendancePercent >= 75) Color(0xFF4ADE80) else Color(0xFFF87171)
              )
            }

            // Progress bar
            LinearProgressIndicator(
              progress = { if (totalInBatch > 0) presentCount.toFloat() / totalInBatch else 0f },
              modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp)),
              color = Color(0xFF22C55E),
              trackColor = Color(0xFF1E2D42)
            )

            // 4 Grid Metric Boxes (NO LATE)
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
              AttendanceStatPill(
                title = "Total",
                count = totalInBatch.toString(),
                bgColor = Color(0xFF111C2E),
                textColor = Color.White,
                modifier = Modifier.weight(1f)
              )
              AttendanceStatPill(
                title = "Present",
                count = presentCount.toString(),
                bgColor = Color(0xFF111C2E),
                textColor = Color(0xFF4ADE80),
                modifier = Modifier.weight(1f)
              )
              AttendanceStatPill(
                title = "Absent",
                count = absentCount.toString(),
                bgColor = Color(0xFF111C2E),
                textColor = Color(0xFFF87171),
                modifier = Modifier.weight(1f)
              )
              AttendanceStatPill(
                title = "Unmarked",
                count = unmarkedCount.toString(),
                bgColor = Color(0xFF111C2E),
                textColor = Color(0xFFFBBF24),
                modifier = Modifier.weight(1f)
              )
            }
          }
        }
      }

      // 4. Fast Bulk Actions Bar
      item {
        Surface(
          color = Color(0xFF111C2E),
          shape = RoundedCornerShape(12.dp),
          border = BorderStroke(1.dp, Color(0xFF1E2D42)),
          modifier = Modifier.fillMaxWidth()
        ) {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Text("Batch Actions:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF94A3B8))

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
              // Bulk Mark All Present
              Button(
                onClick = {
                  batchStudents.forEach { stu ->
                    AppRepository.saveSingleAttendance(
                      studentId = stu.id,
                      studentName = stu.name,
                      batchName = stu.batch,
                      date = formattedDate,
                      status = "Present",
                      recordedBy = currentUserId
                    )
                  }
                  Toast.makeText(context, "Marked all ${batchStudents.size} students Present for $formattedDate", Toast.LENGTH_SHORT).show()
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16A34A)),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
              ) {
                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(13.dp))
                Spacer(Modifier.width(3.dp))
                Text("All Present", fontSize = 10.sp, fontWeight = FontWeight.Bold)
              }

              // Bulk Mark All Absent
              OutlinedButton(
                onClick = {
                  batchStudents.forEach { stu ->
                    AppRepository.saveSingleAttendance(
                      studentId = stu.id,
                      studentName = stu.name,
                      batchName = stu.batch,
                      date = formattedDate,
                      status = "Absent",
                      reason = "Unexcused Absence",
                      recordedBy = currentUserId
                    )
                  }
                  Toast.makeText(context, "Marked all ${batchStudents.size} students Absent for $formattedDate", Toast.LENGTH_SHORT).show()
                },
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFF87171)),
                border = BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.5f)),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
              ) {
                Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(13.dp))
                Spacer(Modifier.width(3.dp))
                Text("All Absent", fontSize = 10.sp, fontWeight = FontWeight.Bold)
              }
            }
          }
        }
      }

      // 5. Status Filter FilterChips Row (All, Present, Absent, Unmarked - NO LATE)
      item {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
          listOf("All", "Present", "Absent", "Unmarked").forEach { st ->
            val isSel = selectedStatusTabFilter == st
            val count = when (st) {
              "Present" -> presentCount
              "Absent" -> absentCount
              "Unmarked" -> unmarkedCount
              else -> totalInBatch
            }
            Surface(
              color = if (isSel) Color(0xFF0061A4) else Color(0xFF111C2E),
              border = BorderStroke(1.dp, if (isSel) Color.Transparent else Color(0xFF1E2D42)),
              shape = RoundedCornerShape(8.dp),
              modifier = Modifier
                .weight(1f)
                .clickable { selectedStatusTabFilter = st }
            ) {
              Column(
                modifier = Modifier.padding(vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally
              ) {
                Text(
                  st,
                  fontSize = 10.sp,
                  fontWeight = if (isSel) FontWeight.Bold else FontWeight.SemiBold,
                  color = if (isSel) Color.White else Color(0xFF94A3B8)
                )
                Text(
                  "($count)",
                  fontSize = 9.sp,
                  color = if (isSel) Color(0xFFE0F2FE) else Color(0xFF64748B)
                )
              }
            }
          }
        }
      }

      // 6. Student Attendance Roster Cards (Options for Present or Absent)
      if (finalDisplayStudents.isEmpty()) {
        item {
          Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0D1526)),
            border = BorderStroke(1.dp, Color(0xFF1E2D42)),
            modifier = Modifier.fillMaxWidth()
          ) {
            Column(
              modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
              horizontalAlignment = Alignment.CenterHorizontally,
              verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
              Icon(Icons.Default.PersonSearch, contentDescription = null, tint = Color(0xFF64748B), modifier = Modifier.size(36.dp))
              Text("No students match your filter criteria", fontSize = 13.sp, color = Color(0xFF94A3B8))
            }
          }
        }
      } else {
        items(finalDisplayStudents, key = { it.id }) { student ->
          val record = studentStatusMap[student.id]

          StudentAttendanceCard(
            student = student,
            date = formattedDate,
            currentRecord = record,
            currentUserId = currentUserId,
            onMarkPresent = {
              val success = AppRepository.saveSingleAttendance(
                studentId = student.id,
                studentName = student.name,
                batchName = student.batch,
                date = formattedDate,
                status = "Present",
                recordedBy = currentUserId
              )
              if (!success) {
                Toast.makeText(context, "Cannot edit: 22h lock expired", Toast.LENGTH_SHORT).show()
              }
            },
            onMarkAbsent = {
              reasonDialogStudent = student
            },
            onEditReason = {
              reasonDialogStudent = student
            }
          )
        }
      }
    }
  }
}

@Composable
fun StudentAttendanceCard(
  student: Student,
  date: String,
  currentRecord: AttendanceRecord?,
  currentUserId: String,
  onMarkPresent: () -> Unit,
  onMarkAbsent: () -> Unit,
  onEditReason: () -> Unit
) {
  val status = currentRecord?.status
  val isPresent = status == "Present"
  val isAbsent = status == "Absent"

  Card(
    shape = RoundedCornerShape(14.dp),
    colors = CardDefaults.cardColors(containerColor = Color(0xFF111C2E)),
    border = BorderStroke(1.dp, if (isAbsent) Color(0xFFEF4444).copy(alpha = 0.35f) else Color(0xFF1E2D42)),
    modifier = Modifier.fillMaxWidth()
  ) {
    Column(
      modifier = Modifier.padding(12.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
      // Student Metadata Row
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
          // Initials Avatar
          Box(
            modifier = Modifier
              .size(38.dp)
              .clip(CircleShape)
              .background(
                when {
                  isPresent -> Color(0xFF16A34A).copy(alpha = 0.25f)
                  isAbsent -> Color(0xFFEF4444).copy(alpha = 0.25f)
                  else -> Color(0xFF2563EB).copy(alpha = 0.25f)
                }
              ),
            contentAlignment = Alignment.Center
          ) {
            Text(
              student.name.take(2).uppercase(),
              color = when {
                isPresent -> Color(0xFF86EFAC)
                isAbsent -> Color(0xFFFCA5A5)
                else -> Color(0xFF93C5FD)
              },
              fontWeight = FontWeight.Bold,
              fontSize = 12.sp
            )
          }

          Column {
            Text(
              student.name,
              fontWeight = FontWeight.Bold,
              fontSize = 13.sp,
              color = Color.White,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
              Text("ID: ${student.id}", fontSize = 10.sp, color = Color(0xFF94A3B8))
              Text("•", fontSize = 10.sp, color = Color(0xFF475569))
              Text(student.batch, fontSize = 10.sp, color = Color(0xFF38BDF8), fontWeight = FontWeight.SemiBold)
            }
          }
        }

        // Attendance Avg Indicator
        Surface(
          color = if (student.attendancePercent >= 75) Color(0xFF166534).copy(alpha = 0.25f) else Color(0xFF991B1B).copy(alpha = 0.25f),
          shape = RoundedCornerShape(6.dp),
          border = BorderStroke(1.dp, if (student.attendancePercent >= 75) Color(0xFF22C55E).copy(alpha = 0.4f) else Color(0xFFEF4444).copy(alpha = 0.4f))
        ) {
          Text(
            "${student.attendancePercent}% Att.",
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = if (student.attendancePercent >= 75) Color(0xFF4ADE80) else Color(0xFFF87171),
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
          )
        }
      }

      // 2-Way Recording Buttons: [ Present | Absent ] (NO LATE)
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        // 1. PRESENT BUTTON
        Button(
          onClick = onMarkPresent,
          colors = ButtonDefaults.buttonColors(
            containerColor = if (isPresent) Color(0xFF16A34A) else Color(0xFF1E293B),
            contentColor = if (isPresent) Color.White else Color(0xFF94A3B8)
          ),
          border = if (!isPresent) BorderStroke(1.dp, Color(0xFF334155)) else null,
          shape = RoundedCornerShape(8.dp),
          modifier = Modifier.weight(1f),
          contentPadding = PaddingValues(vertical = 8.dp)
        ) {
          Icon(
            imageVector = if (isPresent) Icons.Default.CheckCircle else Icons.Default.Check,
            contentDescription = null,
            modifier = Modifier.size(16.dp)
          )
          Spacer(Modifier.width(6.dp))
          Text("Present", fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }

        // 2. ABSENT BUTTON
        Button(
          onClick = onMarkAbsent,
          colors = ButtonDefaults.buttonColors(
            containerColor = if (isAbsent) Color(0xFFDC2626) else Color(0xFF1E293B),
            contentColor = if (isAbsent) Color.White else Color(0xFF94A3B8)
          ),
          border = if (!isAbsent) BorderStroke(1.dp, Color(0xFF334155)) else null,
          shape = RoundedCornerShape(8.dp),
          modifier = Modifier.weight(1f),
          contentPadding = PaddingValues(vertical = 8.dp)
        ) {
          Icon(
            imageVector = if (isAbsent) Icons.Default.Cancel else Icons.Default.Close,
            contentDescription = null,
            modifier = Modifier.size(16.dp)
          )
          Spacer(Modifier.width(6.dp))
          Text("Absent", fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
      }

      // Reason Badge & Recorder Info if already marked
      if (currentRecord != null) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          if (isAbsent && !currentRecord.reason.isNullOrBlank()) {
            Surface(
              color = Color(0xFF991B1B).copy(alpha = 0.3f),
              shape = RoundedCornerShape(6.dp),
              border = BorderStroke(0.5.dp, Color(0xFFEF4444)),
              modifier = Modifier.clickable { onEditReason() }
            ) {
              Row(
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
              ) {
                Icon(Icons.Default.Edit, contentDescription = null, tint = Color(0xFFFCA5A5), modifier = Modifier.size(10.dp))
                Text("Reason: ${currentRecord.reason}", fontSize = 10.sp, color = Color(0xFFFCA5A5), fontWeight = FontWeight.SemiBold)
              }
            }
          } else {
            Spacer(Modifier.width(1.dp))
          }

          Text(
            "Logged: ${currentRecord.time.ifBlank { "Recorded" }}",
            fontSize = 9.sp,
            color = Color(0xFF94A3B8)
          )
        }
      }
    }
  }
}

@Composable
fun AttendanceStatPill(
  title: String,
  count: String,
  bgColor: Color,
  textColor: Color,
  modifier: Modifier = Modifier
) {
  Surface(
    color = bgColor,
    shape = RoundedCornerShape(8.dp),
    border = BorderStroke(1.dp, Color(0xFF1E2D42)),
    modifier = modifier
  ) {
    Column(
      modifier = Modifier.padding(vertical = 8.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.Center
    ) {
      Text(count, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp, color = textColor)
      Text(title, fontSize = 10.sp, color = Color(0xFF94A3B8))
    }
  }
}
