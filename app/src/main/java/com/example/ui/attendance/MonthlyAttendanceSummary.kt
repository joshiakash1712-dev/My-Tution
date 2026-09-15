package com.example.ui.attendance

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
import com.example.ui.theme.NumberTypography
import com.example.ui.theme.HeroStatText
import com.example.ui.theme.CardStatText
import com.example.ui.theme.InlineStatText
import com.example.ui.theme.MicroLabelText
import java.text.DateFormatSymbols
import java.util.*

data class StudentMonthlyAttendance(
  val student: Student,
  val totalDaysRecorded: Int,
  val presentCount: Int,
  val absentCount: Int,
  val lateCount: Int,
  val monthlyPercentage: Int,
  val isLowAttendance: Boolean,
  val records: List<AttendanceRecord>
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MonthlyAttendanceSummary(
  modifier: Modifier = Modifier,
  currentUserRole: String = "Admin",
  currentUserId: String = "system_fsi",
  onNavigateToDailyRegister: () -> Unit = {}
) {
  val context = LocalContext.current
  val allStudents by AppRepository.students.collectAsState()
  val allBatches by AppRepository.batches.collectAsState()
  val allRecords by AppRepository.attendance.collectAsState()

  val activeBatches = remember(allBatches) {
    if (allBatches.isEmpty()) SeedData.defaultBatches else allBatches
  }

  // Month & Year state
  val calendar = remember { Calendar.getInstance() }
  var selectedMonth by remember { mutableIntStateOf(calendar.get(Calendar.MONTH) + 1) } // 1..12
  var selectedYear by remember { mutableIntStateOf(calendar.get(Calendar.YEAR)) }

  val monthNames = remember { DateFormatSymbols().months }
  val currentMonthName = remember(selectedMonth) {
    if (selectedMonth in 1..12) monthNames[selectedMonth - 1] else "Month $selectedMonth"
  }

  // Filter thresholds & filters
  var thresholdPercent by remember { mutableIntStateOf(75) }
  var selectedBatchFilter by remember { mutableStateOf("All") }
  var selectedCategoryFilter by remember { mutableStateOf("All") } // "All", "Low (<75%)", "Good (≥75%)", "Perfect (100%)", "No Records"

  // Dialog inspection state
  var inspectingStudentSummary by remember { mutableStateOf<StudentMonthlyAttendance?>(null) }
  var showBroadcastConfirmation by remember { mutableStateOf(false) }

  // Filter raw attendance records strictly for the selected Month and Year
  val monthlyRecords = remember(allRecords, selectedMonth, selectedYear) {
    allRecords.filter { record ->
      if (record.month > 0 && record.year > 0) {
        record.month == selectedMonth && record.year == selectedYear
      } else {
        val parsed = com.example.data.db.AttendanceRecordEntity.parseDateAndTimeString(record.date, record.timestamp)
        parsed.month == selectedMonth && parsed.year == selectedYear
      }
    }
  }

  // Group records by student ID
  val recordsByStudentId = remember(monthlyRecords) {
    monthlyRecords.groupBy { it.studentId }
  }

  // Compute monthly calculations for all enrolled students
  val allStudentMonthlyData = remember(allStudents, recordsByStudentId, thresholdPercent) {
    allStudents.map { student ->
      val studentRecords = recordsByStudentId[student.id] ?: emptyList()
      val total = studentRecords.size
      val presents = studentRecords.count { it.status == "Present" }
      val absents = studentRecords.count { it.status == "Absent" }
      
      val percentage = if (total > 0) {
        ((presents.toFloat() / total) * 100).toInt().coerceIn(0, 100)
      } else {
        // If no records logged in this specific month, default to student's overall profile avg or 0
        student.attendancePercent
      }

      val hasLow = total > 0 && percentage < thresholdPercent || (total == 0 && student.attendancePercent < thresholdPercent)

      StudentMonthlyAttendance(
        student = student,
        totalDaysRecorded = total,
        presentCount = presents,
        absentCount = absents,
        lateCount = 0,
        monthlyPercentage = percentage,
        isLowAttendance = hasLow,
        records = studentRecords.sortedByDescending { it.timestamp }
      )
    }
  }

  // Filter based on Batch and Category Tab (search query removed)
  val filteredData = remember(allStudentMonthlyData, selectedBatchFilter, selectedCategoryFilter, thresholdPercent) {
    allStudentMonthlyData.filter { item ->
      val matchesBatch = selectedBatchFilter == "All" || item.student.batch == selectedBatchFilter

      val matchesCategory = when (selectedCategoryFilter) {
        "Low (<75%)" -> item.isLowAttendance
        "Good (≥75%)" -> !item.isLowAttendance && item.monthlyPercentage >= thresholdPercent
        "Perfect (100%)" -> item.monthlyPercentage == 100 && item.totalDaysRecorded > 0
        "No Records" -> item.totalDaysRecorded == 0
        else -> true
      }

      matchesBatch && matchesCategory
    }
  }

  // Summary Metrics
  val totalEnrolled = allStudentMonthlyData.size
  val lowAttendanceList = remember(allStudentMonthlyData, selectedBatchFilter) {
    allStudentMonthlyData.filter {
      (selectedBatchFilter == "All" || it.student.batch == selectedBatchFilter) && it.isLowAttendance
    }
  }
  val lowAttendanceCount = lowAttendanceList.size
  val safeAttendanceCount = (if (selectedBatchFilter == "All") totalEnrolled else allStudentMonthlyData.count { it.student.batch == selectedBatchFilter }) - lowAttendanceCount
  val batchAverage = remember(allStudentMonthlyData, selectedBatchFilter) {
    val batchList = if (selectedBatchFilter == "All") allStudentMonthlyData else allStudentMonthlyData.filter { it.student.batch == selectedBatchFilter }
    if (batchList.isNotEmpty()) {
      batchList.map { it.monthlyPercentage }.average().toInt()
    } else 0
  }

  // Detail Modal Dialog for Student Breakdown
  if (inspectingStudentSummary != null) {
    val item = inspectingStudentSummary!!
    AlertDialog(
      onDismissRequest = { inspectingStudentSummary = null },
      containerColor = Color(0xFF111C2E),
      titleContentColor = Color.White,
      textContentColor = Color(0xFF94A3B8),
      title = {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Column {
            Text(item.student.name, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.White)
            Text("ID: ${item.student.id} • ${item.student.batch}", fontSize = 11.sp, color = Color(0xFF94A3B8))
          }
          Surface(
            color = if (item.isLowAttendance) Color(0xFF991B1B).copy(alpha = 0.25f) else Color(0xFF166534).copy(alpha = 0.25f),
            shape = RoundedCornerShape(8.dp),
            border = BorderStroke(1.dp, if (item.isLowAttendance) Color(0xFFEF4444).copy(alpha = 0.4f) else Color(0xFF22C55E).copy(alpha = 0.4f))
          ) {
            Text(
              "${item.monthlyPercentage}%",
              fontWeight = FontWeight.ExtraBold,
              fontSize = 14.sp,
              color = if (item.isLowAttendance) Color(0xFFF87171) else Color(0xFF4ADE80),
              modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
            )
          }
        }
      },
      text = {
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
          verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
          Text(
            "$currentMonthName $selectedYear Attendance Log Breakdown",
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color(0xFFCBD5E1)
          )

          // 3-Metric Pills
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
          ) {
            Surface(
              color = Color(0xFF0A1220),
              shape = RoundedCornerShape(8.dp),
              border = BorderStroke(1.dp, Color(0xFF1E2D42)),
              modifier = Modifier.weight(1f)
            ) {
              Column(modifier = Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("${item.presentCount}", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFF4ADE80))
                Text("Present", fontSize = 10.sp, color = Color(0xFF94A3B8))
              }
            }
            Surface(
              color = Color(0xFF0A1220),
              shape = RoundedCornerShape(8.dp),
              border = BorderStroke(1.dp, Color(0xFF1E2D42)),
              modifier = Modifier.weight(1f)
            ) {
              Column(modifier = Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("${item.absentCount}", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFFF87171))
                Text("Absent", fontSize = 10.sp, color = Color(0xFF94A3B8))
              }
            }
            Surface(
              color = Color(0xFF0A1220),
              shape = RoundedCornerShape(8.dp),
              border = BorderStroke(1.dp, Color(0xFF1E2D42)),
              modifier = Modifier.weight(1f)
            ) {
              Column(modifier = Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("${item.lateCount}", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFFFBBF24))
                Text("Late", fontSize = 10.sp, color = Color(0xFF94A3B8))
              }
            }
          }

          HorizontalDivider(color = Color(0xFF1E2D42))

          if (item.records.isEmpty()) {
            Text(
              "No specific attendance sessions logged yet for $currentMonthName $selectedYear. The percentage shown is based on the registered academic baseline.",
              fontSize = 11.sp,
              color = Color(0xFF94A3B8)
            )
          } else {
            Text("Logged Dates & Reasons (${item.records.size} sessions):", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFFCBD5E1))
            LazyColumn(
              modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 200.dp),
              verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
              items(item.records) { rec ->
                Surface(
                  color = Color(0xFF0A1220),
                  shape = RoundedCornerShape(8.dp),
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
                    Column {
                      Text(rec.date, fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Color.White)
                      if (!rec.reason.isNullOrBlank()) {
                        Text("Reason: ${rec.reason}", fontSize = 10.sp, color = Color(0xFFF87171))
                      }
                    }
                    Surface(
                      color = when (rec.status) {
                        "Present" -> Color(0xFF166534).copy(alpha = 0.3f)
                        "Absent" -> Color(0xFF991B1B).copy(alpha = 0.3f)
                        else -> Color(0xFF854D0E).copy(alpha = 0.3f)
                      },
                      border = BorderStroke(
                        0.5.dp,
                        when (rec.status) {
                          "Present" -> Color(0xFF22C55E).copy(alpha = 0.4f)
                          "Absent" -> Color(0xFFEF4444).copy(alpha = 0.4f)
                          else -> Color(0xFFFBBF24).copy(alpha = 0.4f)
                        }
                      ),
                      shape = RoundedCornerShape(4.dp)
                    ) {
                      Text(
                        rec.status,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = when (rec.status) {
                          "Present" -> Color(0xFF4ADE80)
                          "Absent" -> Color(0xFFF87171)
                          else -> Color(0xFFFBBF24)
                        },
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                      )
                    }
                  }
                }
              }
            }
          }
        }
      },
      confirmButton = {
        Button(
          onClick = {
            // Trigger automated parent alert
            AppRepository.addNotification(
              NotificationItem(
                id = "NOTIF_${System.currentTimeMillis()}",
                title = "Monthly Attendance Notice: ${item.student.name}",
                message = "Monthly attendance summary for ${item.student.name} is ${item.monthlyPercentage}% (${item.absentCount} absents in $currentMonthName $selectedYear). Please review.",
                time = "Just now",
                type = "Absence",
                recipientRole = "Parent"
              )
            )
            Toast.makeText(context, "Attendance alert dispatched to ${item.student.parentName} (${item.student.parentContact})", Toast.LENGTH_SHORT).show()
            inspectingStudentSummary = null
          },
          colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0061A4)),
          shape = RoundedCornerShape(8.dp)
        ) {
          Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(14.dp))
          Spacer(Modifier.width(4.dp))
          Text("Alert Parent", fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
      },
      dismissButton = {
        TextButton(onClick = { inspectingStudentSummary = null }) {
          Text("Close")
        }
      }
    )
  }

  // Bulk Notification Dialog
  if (showBroadcastConfirmation) {
    AlertDialog(
      onDismissRequest = { showBroadcastConfirmation = false },
      containerColor = Color(0xFF111C2E),
      titleContentColor = Color.White,
      textContentColor = Color(0xFF94A3B8),
      icon = { Icon(Icons.Default.WarningAmber, contentDescription = null, tint = Color(0xFFDC2626), modifier = Modifier.size(32.dp)) },
      title = { Text("Broadcast Low Attendance Alert?", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.White) },
      text = {
        Text(
          "This will dispatch automated warning notifications to parents of all $lowAttendanceCount students who have attendance below $thresholdPercent% in $currentMonthName $selectedYear.",
          fontSize = 12.sp,
          color = Color(0xFF94A3B8)
        )
      },
      confirmButton = {
        Button(
          onClick = {
            lowAttendanceList.forEach { item ->
              AppRepository.addNotification(
                NotificationItem(
                  id = "NOTIF_${System.currentTimeMillis()}_${item.student.id}",
                  title = "Low Attendance Warning (<$thresholdPercent%)",
                  message = "Dear Parent, ${item.student.name}'s attendance for $currentMonthName $selectedYear is ${item.monthlyPercentage}%, which is below the minimum mandatory threshold of $thresholdPercent%. Kindly ensure regular attendance.",
                  time = "Just now",
                  type = "Absence",
                  recipientRole = "Parent"
                )
              )
            }
            Toast.makeText(context, "Dispatched alerts to parents of all $lowAttendanceCount low-attendance students.", Toast.LENGTH_LONG).show()
            showBroadcastConfirmation = false
          },
          colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
          shape = RoundedCornerShape(8.dp)
        ) {
          Text("Send $lowAttendanceCount Alerts", fontWeight = FontWeight.Bold)
        }
      },
      dismissButton = {
        TextButton(onClick = { showBroadcastConfirmation = false }) {
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
    // 1. Month-Year Selector & Header Card
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
                "Monthly Attendance Summary",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
              )
              Text(
                "Automated Percentages • Low Attendance Diagnostics",
                color = Color(0xFF94A3B8),
                fontSize = 11.sp
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
                Icon(Icons.Default.Analytics, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(14.dp))
                Text("Threshold: $thresholdPercent%", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
              }
            }
          }

          // Month Stepper Navigator
          Surface(
            color = Color(0xFF111C2E),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, Color(0xFF1E2D42)),
            modifier = Modifier.fillMaxWidth()
          ) {
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .padding(6.dp),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              IconButton(
                onClick = {
                  if (selectedMonth == 1) {
                    selectedMonth = 12
                    selectedYear -= 1
                  } else {
                    selectedMonth -= 1
                  }
                }
              ) {
                Icon(Icons.Default.ChevronLeft, contentDescription = "Previous Month", tint = Color.White)
              }

              Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Default.CalendarMonth, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(18.dp))
                Text(
                  "$currentMonthName $selectedYear",
                  color = Color.White,
                  fontSize = 15.sp,
                  fontWeight = FontWeight.Bold
                )
              }

              IconButton(
                onClick = {
                  if (selectedMonth == 12) {
                    selectedMonth = 1
                    selectedYear += 1
                  } else {
                    selectedMonth += 1
                  }
                }
              ) {
                Icon(Icons.Default.ChevronRight, contentDescription = "Next Month", tint = Color.White)
              }
            }
          }
        }
      }
    }

    // 2. Low Attendance Diagnostic Highlight Banner
    item {
      Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0D1526)),
        border = BorderStroke(1.dp, if (lowAttendanceCount > 0) Color(0xFFEF4444).copy(alpha = 0.4f) else Color(0xFF1E2D42)),
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
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              Box(
                modifier = Modifier
                  .size(32.dp)
                  .clip(CircleShape)
                  .background(if (lowAttendanceCount > 0) Color(0xFFEF4444) else Color(0xFF16A34A)),
                contentAlignment = Alignment.Center
              ) {
                Icon(
                  imageVector = if (lowAttendanceCount > 0) Icons.Default.Warning else Icons.Default.CheckCircle,
                  contentDescription = null,
                  tint = Color.White,
                  modifier = Modifier.size(18.dp)
                )
              }
              Column {
                Text(
                  if (lowAttendanceCount > 0) "$lowAttendanceCount Students with Low Attendance" else "All Students Above Threshold ($thresholdPercent%)",
                  fontWeight = FontWeight.Bold,
                  fontSize = 13.sp,
                  color = Color.White
                )
                Text(
                  if (lowAttendanceCount > 0) "Immediate intervention recommended via parent alerts." else "Healthy attendance distribution maintained for $currentMonthName.",
                  fontSize = 11.sp,
                  color = Color(0xFF94A3B8)
                )
              }
            }

            if (lowAttendanceCount > 0) {
              FilledTonalButton(
                onClick = { showBroadcastConfirmation = true },
                colors = ButtonDefaults.filledTonalButtonColors(containerColor = Color(0xFFDC2626), contentColor = Color.White),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
              ) {
                Icon(Icons.Default.NotificationsActive, contentDescription = null, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
                Text("Alert All", fontSize = 11.sp, fontWeight = FontWeight.Bold)
              }
            }
          }

          // 4 Metric Statistics Grid
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            SummaryMetricBox(
              title = "Batch Avg",
              value = "$batchAverage%",
              color = Color(0xFF38BDF8),
              bgColor = Color(0xFF111C2E),
              modifier = Modifier.weight(1f)
            )
            SummaryMetricBox(
              title = "Safe (≥$thresholdPercent%)",
              value = "$safeAttendanceCount",
              color = Color(0xFF4ADE80),
              bgColor = Color(0xFF111C2E),
              modifier = Modifier.weight(1f)
            )
            SummaryMetricBox(
              title = "Low (<$thresholdPercent%)",
              value = "$lowAttendanceCount",
              color = Color(0xFFF87171),
              bgColor = Color(0xFF111C2E),
              modifier = Modifier.weight(1f)
            )
            SummaryMetricBox(
              title = "Sessions",
              value = "${monthlyRecords.map { it.date }.distinct().size} Days",
              color = Color(0xFFA78BFA),
              bgColor = Color(0xFF111C2E),
              modifier = Modifier.weight(1f)
            )
          }
        }
      }
    }

    // 3. Batch Selector Chips & Search Bar
    item {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // Batch Chips
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
          horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
          val batchOptions = listOf("All") + activeBatches.map { it.name }
          batchOptions.forEach { bName ->
            val isSel = selectedBatchFilter == bName
            FilterChip(
              selected = isSel,
              onClick = { selectedBatchFilter = bName },
              label = {
                Text(
                  text = if (bName == "All") "All Batches (${allStudents.size})" else bName,
                  fontSize = 11.sp,
                  fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                  maxLines = 1,
                  softWrap = false,
                  overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
              },
              colors = FilterChipDefaults.filterChipColors(
                containerColor = Color(0xFF111C2E),
                labelColor = Color(0xFF94A3B8),
                selectedContainerColor = Color(0xFF0061A4),
                selectedLabelColor = Color.White
              ),
              border = FilterChipDefaults.filterChipBorder(
                enabled = true,
                selected = isSel,
                borderColor = if (isSel) Color(0xFF60A5FA) else Color(0xFF1E2D42)
              )
            )
          }
        }

        // Register navigation shortcut
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.End,
          verticalAlignment = Alignment.CenterVertically
        ) {
          FilledTonalButton(
            onClick = onNavigateToDailyRegister,
            shape = RoundedCornerShape(10.dp),
            colors = ButtonDefaults.filledTonalButtonColors(
              containerColor = Color(0xFF1E293B),
              contentColor = Color(0xFF38BDF8)
            ),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
          ) {
            Icon(Icons.Default.FactCheck, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text("Open Daily Register", fontSize = 11.sp, fontWeight = FontWeight.Bold)
          }
        }
      }
    }

    // 4. Status Filter Category Tabs
    item {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
      ) {
        listOf("All", "Low (<75%)", "Good (≥75%)", "Perfect (100%)", "No Records").forEach { cat ->
          val isSel = selectedCategoryFilter == cat
          val catCount = when (cat) {
            "Low (<75%)" -> lowAttendanceCount
            "Good (≥75%)" -> safeAttendanceCount
            "Perfect (100%)" -> allStudentMonthlyData.count { it.monthlyPercentage == 100 && it.totalDaysRecorded > 0 }
            "No Records" -> allStudentMonthlyData.count { it.totalDaysRecorded == 0 }
            else -> allStudentMonthlyData.size
          }
          Surface(
            color = if (isSel) {
              if (cat == "Low (<75%)") Color(0xFFDC2626) else Color(0xFF0061A4)
            } else Color(0xFF111C2E),
            border = BorderStroke(1.dp, if (isSel) Color.Transparent else Color(0xFF1E2D42)),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier
              .weight(1f)
              .clickable { selectedCategoryFilter = cat }
          ) {
            Column(
              modifier = Modifier.padding(vertical = 6.dp),
              horizontalAlignment = Alignment.CenterHorizontally
            ) {
              Text(
                cat.takeWhile { it != ' ' },
                fontSize = 10.sp,
                fontWeight = if (isSel) FontWeight.Bold else FontWeight.SemiBold,
                color = if (isSel) Color.White else Color(0xFF94A3B8)
              )
              Text(
                "($catCount)",
                fontSize = 9.sp,
                color = if (isSel) Color(0xFFE0F2FE) else Color(0xFF64748B)
              )
            }
          }
        }
      }
    }

    // 5. Student Monthly List Items
    if (filteredData.isEmpty()) {
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
              .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            Icon(Icons.Default.PersonSearch, contentDescription = null, tint = Color(0xFF64748B), modifier = Modifier.size(38.dp))
            Text("No students found matching current filters", fontSize = 13.sp, color = Color(0xFF94A3B8))
          }
        }
      }
    } else {
      items(filteredData, key = { it.student.id }) { item ->
        MonthlyStudentAttendanceCard(
          data = item,
          monthName = currentMonthName,
          year = selectedYear,
          threshold = thresholdPercent,
          onViewBreakdown = { inspectingStudentSummary = item },
          onAlertParent = {
            AppRepository.addNotification(
              NotificationItem(
                id = "NOTIF_${System.currentTimeMillis()}_${item.student.id}",
                title = "Monthly Attendance: ${item.student.name}",
                message = "${item.student.name}'s attendance for $currentMonthName $selectedYear is ${item.monthlyPercentage}%. Recorded absents: ${item.absentCount}.",
                time = "Just now",
                type = "Absence",
                recipientRole = "Parent"
              )
            )
            Toast.makeText(context, "Attendance alert sent to parent of ${item.student.name}.", Toast.LENGTH_SHORT).show()
          }
        )
      }
    }
  }
}

@Composable
fun MonthlyStudentAttendanceCard(
  data: StudentMonthlyAttendance,
  monthName: String,
  year: Int,
  threshold: Int,
  onViewBreakdown: () -> Unit,
  onAlertParent: () -> Unit
) {
  val isLow = data.isLowAttendance
  val student = data.student

  Card(
    shape = RoundedCornerShape(14.dp),
    colors = CardDefaults.cardColors(
      containerColor = Color(0xFF111C2E)
    ),
    border = BorderStroke(1.dp, if (isLow) Color(0xFFEF4444).copy(alpha = 0.35f) else Color(0xFF1E2D42)),
    modifier = Modifier
      .fillMaxWidth()
      .clickable { onViewBreakdown() }
  ) {
    Column(
      modifier = Modifier.padding(12.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
      // Header: Avatar, Name, Batch, and Badge
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
          Box(
            modifier = Modifier
              .size(38.dp)
              .clip(CircleShape)
              .background(if (isLow) Color(0xFFEF4444).copy(alpha = 0.25f) else Color(0xFF2563EB).copy(alpha = 0.25f)),
            contentAlignment = Alignment.Center
          ) {
            Text(
              student.name.take(2).uppercase(),
              color = if (isLow) Color(0xFFFCA5A5) else Color(0xFF93C5FD),
              fontWeight = FontWeight.Bold,
              fontSize = 12.sp
            )
          }

          Column {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
              Text(
                student.name,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
              )
              if (isLow) {
                Surface(
                  color = Color(0xFF991B1B).copy(alpha = 0.3f),
                  shape = RoundedCornerShape(4.dp),
                  border = BorderStroke(0.5.dp, Color(0xFFEF4444))
                ) {
                  Text(
                    "LOW ATTENDANCE",
                    fontSize = 8.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color(0xFFFCA5A5),
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                  )
                }
              }
            }
            Text(
              "ID: ${student.id} • ${student.batch}",
              fontSize = 10.sp,
              color = Color(0xFF94A3B8)
            )
          }
        }

        // Percentage Indicator Pill
        Surface(
          color = if (isLow) Color(0xFF991B1B).copy(alpha = 0.25f) else Color(0xFF166534).copy(alpha = 0.25f),
          shape = RoundedCornerShape(8.dp),
          border = BorderStroke(1.dp, if (isLow) Color(0xFFEF4444).copy(alpha = 0.4f) else Color(0xFF22C55E).copy(alpha = 0.4f))
        ) {
          Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalAlignment = Alignment.End
          ) {
            Text(
              "${data.monthlyPercentage}%",
              fontSize = 13.sp,
              fontWeight = FontWeight.ExtraBold,
              color = if (isLow) Color(0xFFF87171) else Color(0xFF4ADE80)
            )
            Text(
              if (isLow) "Below $threshold%" else "Safe",
              fontSize = 8.sp,
              fontWeight = FontWeight.SemiBold,
              color = if (isLow) Color(0xFFFCA5A5) else Color(0xFF86EFAC)
            )
          }
        }
      }

      // Progress bar
      LinearProgressIndicator(
        progress = { (data.monthlyPercentage.toFloat() / 100f).coerceIn(0f, 1f) },
        modifier = Modifier
          .fillMaxWidth()
          .height(6.dp)
          .clip(RoundedCornerShape(3.dp)),
        color = if (isLow) Color(0xFFEF4444) else Color(0xFF22C55E),
        trackColor = Color(0xFF1E2D42)
      )

      // Breakdown Metrics Row
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
          Text("Present: ${data.presentCount}d", fontSize = 10.sp, color = Color(0xFF4ADE80), fontWeight = FontWeight.SemiBold)
          Text("Absent: ${data.absentCount}d", fontSize = 10.sp, color = if (data.absentCount > 0) Color(0xFFF87171) else Color(0xFF94A3B8), fontWeight = FontWeight.SemiBold)
          Text("Total: ${data.totalDaysRecorded}d", fontSize = 10.sp, color = Color(0xFF94A3B8))
        }

        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
          // Alert Parent Button
          if (isLow) {
            OutlinedButton(
              onClick = onAlertParent,
              shape = RoundedCornerShape(6.dp),
              colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFF87171)),
              border = BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.5f)),
              contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
              modifier = Modifier.height(28.dp)
            ) {
              Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(11.dp))
              Spacer(Modifier.width(2.dp))
              Text("Alert Parent", fontSize = 9.sp, fontWeight = FontWeight.Bold)
            }
          }

          // View Breakdown Button
          TextButton(
            onClick = onViewBreakdown,
            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
            modifier = Modifier.height(28.dp)
          ) {
            Text("Details", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF38BDF8))
            Icon(Icons.Default.ChevronRight, contentDescription = null, modifier = Modifier.size(12.dp), tint = Color(0xFF38BDF8))
          }
        }
      }
    }
  }
}

@Composable
fun SummaryMetricBox(
  title: String,
  value: String,
  color: Color,
  bgColor: Color,
  modifier: Modifier = Modifier
) {
  Surface(
    color = Color(0xFF111C2E),
    shape = RoundedCornerShape(8.dp),
    border = BorderStroke(1.dp, Color(0xFF1E2D42)),
    modifier = modifier
  ) {
    Column(
      modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.Center
    ) {
      InlineStatText(text = value, color = color)
      MicroLabelText(text = title, color = Color(0xFF94A3B8), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
  }
}
