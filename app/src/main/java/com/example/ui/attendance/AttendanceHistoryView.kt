package com.example.ui.attendance

import android.content.Context
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import com.example.data.AppRepository
import com.example.data.AttendanceRecord
import com.example.data.db.AttendanceRecordEntity
import com.example.utils.ExportUtils
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AttendanceHistoryView(
  modifier: Modifier = Modifier,
  currentUserRole: String = "Admin",
  currentUserId: String = "system_fsi"
) {
  val context = LocalContext.current
  val rawAttendanceList by AppRepository.attendance.collectAsState()
  val allBatches by AppRepository.batches.collectAsState()
  val allStudents by AppRepository.students.collectAsState()

  val batches = remember(allBatches, currentUserRole) {
    if (currentUserRole.equals("Teacher", ignoreCase = true)) {
      AppRepository.getAuthorizedBatchesForCurrentTeacher()
    } else {
      allBatches
    }
  }
  val authorizedBatchNames = remember(batches) { batches.map { it.name.lowercase() }.toSet() }
  val liveAttendanceList = remember(rawAttendanceList, currentUserRole, authorizedBatchNames) {
    if (currentUserRole.equals("Teacher", ignoreCase = true)) {
      rawAttendanceList.filter { it.batchName.lowercase() in authorizedBatchNames }
    } else {
      rawAttendanceList
    }
  }

  // Calendar state for Day, Month, Year selection (Rule 3)
  val todayCal = remember { Calendar.getInstance() }
  var selectedDay by remember { mutableStateOf(todayCal.get(Calendar.DAY_OF_MONTH)) }
  var selectedMonth by remember { mutableStateOf(todayCal.get(Calendar.MONTH) + 1) } // 1..12
  var selectedYear by remember { mutableStateOf(todayCal.get(Calendar.YEAR)) }
  var filterBySpecificDate by remember { mutableStateOf(false) }

  var selectedBatchFilter by remember { mutableStateOf("All") }
  var selectedStatusFilter by remember { mutableStateOf("All") }
  var selectedStudentIdFilter by remember { mutableStateOf("All") }
  var systemFeedbackMsg by remember { mutableStateOf<String?>(null) }
  var showDatePickerDialog by remember { mutableStateOf(false) }
  var editingRecordForReason by remember { mutableStateOf<AttendanceRecord?>(null) }
  var customReasonText by remember { mutableStateOf("") }
  var historySubSection by remember { mutableIntStateOf(0) } // 0 = Date / Daily Audit Logs, 1 = Monthly Summary & Analytics

  val monthNames = listOf(
    "Jan", "Feb", "Mar", "Apr", "May", "Jun",
    "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"
  )

  val fullMonthNames = listOf(
    "January", "February", "March", "April", "May", "June",
    "July", "August", "September", "October", "November", "December"
  )

  val yearsList = listOf(2023, 2024, 2025, 2026, 2027, 2028, 2029, 2030)

  // Max days in currently selected month/year
  val maxDaysInMonth = remember(selectedMonth, selectedYear) {
    val cal = Calendar.getInstance()
    cal.set(Calendar.YEAR, selectedYear)
    cal.set(Calendar.MONTH, selectedMonth - 1)
    cal.getActualMaximum(Calendar.DAY_OF_MONTH)
  }

  // Adjust selectedDay if it exceeds maxDaysInMonth
  LaunchedEffect(maxDaysInMonth) {
    if (selectedDay > maxDaysInMonth) {
      selectedDay = maxDaysInMonth
    }
  }

  val formattedSelectedDate = remember(selectedDay, selectedMonth, selectedYear) {
    String.format(Locale.getDefault(), "%02d/%02d/%04d", selectedDay, selectedMonth, selectedYear)
  }

  // Filtered List calculation
  val filteredRecords = remember(
    liveAttendanceList,
    filterBySpecificDate,
    selectedDay,
    selectedMonth,
    selectedYear,
    selectedBatchFilter,
    selectedStatusFilter,
    selectedStudentIdFilter
  ) {
    liveAttendanceList.filter { record ->
      // 1. Date filter (Day, Month, Year)
      val matchesDate = if (!filterBySpecificDate) {
        true
      } else {
        if (record.day > 0 && record.month > 0 && record.year > 0) {
          record.day == selectedDay && record.month == selectedMonth && record.year == selectedYear
        } else {
          val parsed = AttendanceRecordEntity.parseDateAndTimeString(record.date, record.timestamp)
          parsed.day == selectedDay && parsed.month == selectedMonth && parsed.year == selectedYear
        }
      }

      // 2. Batch filter
      val matchesBatch = if (selectedBatchFilter == "All") {
        true
      } else {
        record.batchName.equals(selectedBatchFilter, ignoreCase = true)
      }

      // 3. Status filter
      val matchesStatus = if (selectedStatusFilter == "All") {
        true
      } else {
        record.status.equals(selectedStatusFilter, ignoreCase = true)
      }

      // 4. Student filter (by stable studentId)
      val matchesStudent = if (selectedStudentIdFilter == "All") {
        true
      } else {
        record.studentId == selectedStudentIdFilter
      }

      matchesDate && matchesBatch && matchesStatus && matchesStudent
    }.sortedByDescending { it.timestamp }
  }

  // Summary Metrics
  val totalCount = filteredRecords.size
  val presentCount = filteredRecords.count { it.status.equals("Present", ignoreCase = true) }
  val absentCount = filteredRecords.count { it.status.equals("Absent", ignoreCase = true) }
  val attendanceRate = if (totalCount > 0) ((presentCount.toFloat() / totalCount) * 100).toInt() else 0

  Column(modifier = modifier.fillMaxSize()) {
    // Top Sub-Navigation Strip inside Attendance History: Daily / Audit Logs vs Monthly Summary
    Surface(
      color = Color(0xFF0F172A),
      modifier = Modifier.fillMaxWidth()
    ) {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 14.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        listOf(
          0 to ("Daily & Audit Logs" to Icons.Default.CalendarMonth),
          1 to ("Monthly Summary & Analytics" to Icons.Default.Assessment)
        ).forEach { (idx, item) ->
          val (label, icon) = item
          val isSelected = historySubSection == idx
          Surface(
            color = if (isSelected) Color(0xFF2563EB) else Color(0xFF1E293B),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier
              .weight(1f)
              .clickable { historySubSection = idx }
          ) {
            Row(
              modifier = Modifier.padding(vertical = 8.dp, horizontal = 8.dp),
              horizontalArrangement = Arrangement.Center,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (isSelected) Color.White else Color(0xFF94A3B8),
                modifier = Modifier.size(15.dp)
              )
              Spacer(Modifier.width(6.dp))
              Text(
                text = label,
                fontSize = 11.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = if (isSelected) Color.White else Color(0xFF94A3B8),
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis
              )
            }
          }
        }
      }
    }

    if (historySubSection == 0) {
      LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
      ) {
    // 1. UNIVERSAL DATE SELECTOR (Any Date, Any Month, Any Year)
    item {
      Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0D1526)),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(
          1.dp,
          if (filterBySpecificDate) Color(0xFF38BDF8) else Color(0xFF1E2D42)
        ),
        modifier = Modifier.fillMaxWidth()
      ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
          // Header Row: Toggle Specific Date Filter vs All Dates
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(8.dp),
              modifier = Modifier.weight(1f)
            ) {
              Icon(
                imageVector = Icons.Default.CalendarMonth,
                contentDescription = null,
                tint = Color(0xFF38BDF8),
                modifier = Modifier.size(20.dp)
              )
              Text(
                text = "Date Filter (Day / Month / Year)",
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
              )
            }

            FilterChip(
              selected = filterBySpecificDate,
              onClick = { filterBySpecificDate = !filterBySpecificDate },
              colors = FilterChipDefaults.filterChipColors(
                containerColor = Color(0xFF111C2E),
                labelColor = Color(0xFF94A3B8),
                selectedContainerColor = Color(0xFF0284C7),
                selectedLabelColor = Color.White
              ),
              border = FilterChipDefaults.filterChipBorder(
                enabled = true,
                selected = filterBySpecificDate,
                borderColor = if (filterBySpecificDate) Color(0xFF38BDF8) else Color(0xFF1E2D42)
              ),
              label = {
                Text(
                  text = if (filterBySpecificDate) "Date Filter: ON" else "Show All Dates",
                  fontSize = 11.sp,
                  fontWeight = FontWeight.Bold,
                  maxLines = 1,
                  softWrap = false,
                  overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
              },
              leadingIcon = {
                Icon(
                  imageVector = if (filterBySpecificDate) Icons.Default.Check else Icons.Default.FilterList,
                  contentDescription = null,
                  modifier = Modifier.size(14.dp)
                )
              }
            )
          }

          // Quick Date Shortcuts & Calendar Picker Button
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            SuggestionChip(
              onClick = {
                val cal = Calendar.getInstance()
                selectedDay = cal.get(Calendar.DAY_OF_MONTH)
                selectedMonth = cal.get(Calendar.MONTH) + 1
                selectedYear = cal.get(Calendar.YEAR)
                filterBySpecificDate = true
              },
              colors = SuggestionChipDefaults.suggestionChipColors(
                containerColor = Color(0xFF111C2E),
                labelColor = Color(0xFFCBD5E1)
              ),
              border = SuggestionChipDefaults.suggestionChipBorder(enabled = true, borderColor = Color(0xFF1E2D42)),
              label = { Text("Today (${todayCal.get(Calendar.DAY_OF_MONTH)} ${monthNames[todayCal.get(Calendar.MONTH)]})", fontSize = 11.sp, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis) }
            )

            SuggestionChip(
              onClick = {
                val cal = Calendar.getInstance()
                cal.add(Calendar.DAY_OF_MONTH, -1)
                selectedDay = cal.get(Calendar.DAY_OF_MONTH)
                selectedMonth = cal.get(Calendar.MONTH) + 1
                selectedYear = cal.get(Calendar.YEAR)
                filterBySpecificDate = true
              },
              colors = SuggestionChipDefaults.suggestionChipColors(
                containerColor = Color(0xFF111C2E),
                labelColor = Color(0xFFCBD5E1)
              ),
              border = SuggestionChipDefaults.suggestionChipBorder(enabled = true, borderColor = Color(0xFF1E2D42)),
              label = { Text("Yesterday", fontSize = 11.sp, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis) }
            )

            SuggestionChip(
              onClick = { showDatePickerDialog = true },
              colors = SuggestionChipDefaults.suggestionChipColors(
                containerColor = Color(0xFF111C2E),
                labelColor = Color(0xFF38BDF8)
              ),
              border = SuggestionChipDefaults.suggestionChipBorder(enabled = true, borderColor = Color(0xFF1E2D42)),
              label = { Text("Calendar Picker", fontSize = 11.sp, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis) },
              icon = { Icon(Icons.Default.Event, contentDescription = null, modifier = Modifier.size(14.dp), tint = Color(0xFF38BDF8)) }
            )

            if (filterBySpecificDate) {
              SuggestionChip(
                onClick = { filterBySpecificDate = false },
                colors = SuggestionChipDefaults.suggestionChipColors(
                  containerColor = Color(0xFF0284C7).copy(alpha = 0.2f),
                  labelColor = Color(0xFF38BDF8)
                ),
                border = SuggestionChipDefaults.suggestionChipBorder(enabled = true, borderColor = Color(0xFF0284C7)),
                label = { Text("View All Records", fontSize = 11.sp, color = Color(0xFF38BDF8), maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis) }
              )
            }
          }

          // Active Date Selectors (Day, Month, Year Pickers)
          AnimatedVisibility(visible = filterBySpecificDate) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
              HorizontalDivider(color = Color(0xFF1E2D42))

              // Selected Date Indicator
              Surface(
                color = Color(0xFF0284C7).copy(alpha = 0.15f),
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, Color(0xFF0284C7).copy(alpha = 0.3f)),
                modifier = Modifier.fillMaxWidth()
              ) {
                Row(
                  modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                  horizontalArrangement = Arrangement.SpaceBetween,
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  Text(
                    text = "Filtering Records for: $formattedSelectedDate",
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = Color(0xFF38BDF8)
                  )
                  Text(
                    text = "${fullMonthNames[selectedMonth - 1]} $selectedYear",
                    fontSize = 11.sp,
                    color = Color(0xFF94A3B8)
                  )
                }
              }

              // 1. Year Picker Row
              Text("Year:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF94A3B8))
              Row(
                modifier = Modifier
                  .fillMaxWidth()
                  .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
              ) {
                yearsList.forEach { y ->
                  val isSelected = selectedYear == y
                  Surface(
                    onClick = { selectedYear = y },
                    shape = RoundedCornerShape(8.dp),
                    color = if (isSelected) Color(0xFF0284C7) else Color(0xFF111C2E),
                    border = BorderStroke(1.dp, if (isSelected) Color(0xFF38BDF8) else Color(0xFF1E2D42)),
                    modifier = Modifier.padding(vertical = 2.dp)
                  ) {
                    Text(
                      text = "$y",
                      fontSize = 11.sp,
                      textAlign = TextAlign.Center,
                      fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                      color = if (isSelected) Color.White else Color(0xFF94A3B8),
                      modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                  }
                }
              }

              // 2. Month Picker Row
              Text("Month:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF94A3B8))
              Row(
                modifier = Modifier
                  .fillMaxWidth()
                  .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
              ) {
                monthNames.forEachIndexed { index, mName ->
                  val monthNum = index + 1
                  val isSelected = selectedMonth == monthNum
                  Surface(
                    onClick = { selectedMonth = monthNum },
                    shape = RoundedCornerShape(8.dp),
                    color = if (isSelected) Color(0xFF0284C7) else Color(0xFF111C2E),
                    border = BorderStroke(1.dp, if (isSelected) Color(0xFF38BDF8) else Color(0xFF1E2D42)),
                    modifier = Modifier.padding(vertical = 2.dp)
                  ) {
                    Text(
                      text = mName,
                      fontSize = 11.sp,
                      fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                      color = if (isSelected) Color.White else Color(0xFF94A3B8),
                      modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                  }
                }
              }

              // 3. Day Picker Row
              Text("Day:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF94A3B8))
              Row(
                modifier = Modifier
                  .fillMaxWidth()
                  .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
              ) {
                (1..maxDaysInMonth).forEach { dayNum ->
                  val isSelected = selectedDay == dayNum
                  Surface(
                    onClick = { selectedDay = dayNum },
                    shape = RoundedCornerShape(8.dp),
                    color = if (isSelected) Color(0xFF0284C7) else Color(0xFF111C2E),
                    border = BorderStroke(1.dp, if (isSelected) Color(0xFF38BDF8) else Color(0xFF1E2D42)),
                    modifier = Modifier.padding(vertical = 2.dp)
                  ) {
                    Text(
                      text = "$dayNum",
                      fontSize = 11.sp,
                      fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                      color = if (isSelected) Color.White else Color(0xFF94A3B8),
                      modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                    )
                  }
                }
              }
            }
          }
        }
      }
    }

    // 2. METRICS SUMMARY BAR
    item {
      Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0D1526)),
        shape = RoundedCornerShape(16.dp),
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
          Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
            Text("TOTAL", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF94A3B8), maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
            Text("$totalCount", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1, softWrap = false)
          }
          VerticalDivider(modifier = Modifier.height(28.dp), color = Color(0xFF1E2D42))
          Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
            Text("PRESENT", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF4ADE80), maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
            Text("$presentCount", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFF4ADE80), maxLines = 1, softWrap = false)
          }
          VerticalDivider(modifier = Modifier.height(28.dp), color = Color(0xFF1E2D42))
          Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
            Text("ABSENT", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFFF87171), maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
            Text("$absentCount", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFFF87171), maxLines = 1, softWrap = false)
          }
          VerticalDivider(modifier = Modifier.height(28.dp), color = Color(0xFF1E2D42))
          Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
            Text("RATE", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF38BDF8), maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
            Text("$attendanceRate%", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFF38BDF8), maxLines = 1, softWrap = false)
          }
        }
      }
    }

    // 3. STATUS & BATCH FILTERS & EXPORT (Search input removed)
    item {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // Filter Pills Row
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
          horizontalArrangement = Arrangement.spacedBy(6.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          // Status Filters (Present, Absent only - Late removed)
          listOf("All", "Present", "Absent").forEach { stat ->
            val isSelected = selectedStatusFilter == stat
            FilterChip(
              selected = isSelected,
              onClick = { selectedStatusFilter = stat },
              colors = FilterChipDefaults.filterChipColors(
                containerColor = Color(0xFF111C2E),
                labelColor = Color(0xFF94A3B8),
                selectedContainerColor = Color(0xFF0284C7),
                selectedLabelColor = Color.White
              ),
              border = FilterChipDefaults.filterChipBorder(
                enabled = true,
                selected = isSelected,
                borderColor = if (isSelected) Color(0xFF38BDF8) else Color(0xFF1E2D42)
              ),
              label = {
                Text(
                  text = stat,
                  fontSize = 11.sp,
                  maxLines = 1,
                  softWrap = false,
                  overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
              }
            )
          }

          // Batch Filter Chips
          if (batches.isNotEmpty()) {
            batches.forEach { b ->
              val isSelected = selectedBatchFilter == b.name
              FilterChip(
                selected = isSelected,
                onClick = { selectedBatchFilter = if (isSelected) "All" else b.name },
                colors = FilterChipDefaults.filterChipColors(
                  containerColor = Color(0xFF111C2E),
                  labelColor = Color(0xFF94A3B8),
                  selectedContainerColor = Color(0xFF0284C7),
                  selectedLabelColor = Color.White
                ),
                border = FilterChipDefaults.filterChipBorder(
                  enabled = true,
                  selected = isSelected,
                  borderColor = if (isSelected) Color(0xFF38BDF8) else Color(0xFF1E2D42)
                ),
                label = {
                  Text(
                    text = b.name,
                    fontSize = 11.sp,
                    maxLines = 1,
                    softWrap = false,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                  )
                }
              )
            }
          }
        }

        // Student Filter Row
        val scopedStudents = remember(allStudents, currentUserRole, authorizedBatchNames, selectedBatchFilter) {
          allStudents.filter { stu ->
            val roleAllowed = !currentUserRole.equals("Teacher", ignoreCase = true) || stu.batch.lowercase() in authorizedBatchNames
            val batchMatched = selectedBatchFilter == "All" || stu.batch.equals(selectedBatchFilter, ignoreCase = true)
            roleAllowed && batchMatched
          }
        }
        if (scopedStudents.isNotEmpty()) {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            FilterChip(
              selected = selectedStudentIdFilter == "All",
              onClick = { selectedStudentIdFilter = "All" },
              colors = FilterChipDefaults.filterChipColors(
                containerColor = Color(0xFF111C2E),
                labelColor = Color(0xFF94A3B8),
                selectedContainerColor = Color(0xFF0284C7),
                selectedLabelColor = Color.White
              ),
              border = FilterChipDefaults.filterChipBorder(
                enabled = true,
                selected = selectedStudentIdFilter == "All",
                borderColor = if (selectedStudentIdFilter == "All") Color(0xFF38BDF8) else Color(0xFF1E2D42)
              ),
              label = { Text("All Students", fontSize = 11.sp) }
            )
            scopedStudents.forEach { st ->
              val isSel = selectedStudentIdFilter == st.id
              FilterChip(
                selected = isSel,
                onClick = { selectedStudentIdFilter = if (isSel) "All" else st.id },
                colors = FilterChipDefaults.filterChipColors(
                  containerColor = Color(0xFF111C2E),
                  labelColor = Color(0xFF94A3B8),
                  selectedContainerColor = Color(0xFF0284C7),
                  selectedLabelColor = Color.White
                ),
                border = FilterChipDefaults.filterChipBorder(
                  enabled = true,
                  selected = isSel,
                  borderColor = if (isSel) Color(0xFF38BDF8) else Color(0xFF1E2D42)
                ),
                label = { Text("${st.name} (${st.id})", fontSize = 11.sp, maxLines = 1) }
              )
            }
          }
        }

        // Action Toolbar (Count + Export CSV)
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Text(
            text = "Showing ${filteredRecords.size} records",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF94A3B8)
          )

          Button(
            onClick = { ExportUtils.exportAttendanceToCsv(context, filteredRecords) },
            shape = RoundedCornerShape(10.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF065F46)),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
          ) {
            Icon(Icons.Default.Share, contentDescription = "Export", modifier = Modifier.size(14.dp), tint = Color.White)
            Spacer(Modifier.width(4.dp))
            Text("Export CSV", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
          }
        }
      }
    }

    // 5. SYSTEM FEEDBACK ALERT
    if (systemFeedbackMsg != null) {
      item {
        Surface(
          color = Color(0xFF450A0A),
          border = BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.5f)),
          shape = RoundedCornerShape(12.dp),
          modifier = Modifier.fillMaxWidth()
        ) {
          Row(
            Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = Color(0xFFF87171), modifier = Modifier.size(18.dp))
            Text(systemFeedbackMsg!!, fontSize = 12.sp, color = Color(0xFFFCA5A5), fontWeight = FontWeight.Medium)
          }
        }
      }
    }

    // 6. ATTENDANCE RECORDS LIST
    if (filteredRecords.isEmpty()) {
      item {
        Card(
          modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp),
          colors = CardDefaults.cardColors(containerColor = Color(0xFF0D1526)),
          border = BorderStroke(1.dp, Color(0xFF1E2D42)),
          shape = RoundedCornerShape(16.dp)
        ) {
          Column(
            modifier = Modifier
              .fillMaxWidth()
              .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
          ) {
            Box(
              modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(Color(0xFF0284C7).copy(alpha = 0.2f)),
              contentAlignment = Alignment.Center
            ) {
              Icon(
                imageVector = Icons.Outlined.EventBusy,
                contentDescription = null,
                tint = Color(0xFF38BDF8),
                modifier = Modifier.size(28.dp)
              )
            }
            Text(
              text = if (filterBySpecificDate) "No Attendance for $formattedSelectedDate" else "No Attendance Records Found",
              fontWeight = FontWeight.Bold,
              fontSize = 15.sp,
              color = Color.White
            )
            Text(
              text = if (filterBySpecificDate)
                "There are no attendance sessions logged for this day. You can select another day or turn off the date filter."
              else
                "Attendance logs registered during class sessions will be stored in the database and displayed here.",
              fontSize = 12.sp,
              color = Color(0xFF94A3B8),
              textAlign = TextAlign.Center,
              lineHeight = 17.sp
            )
          }
        }
      }
    } else {
      items(filteredRecords, key = { it.id }) { log ->
        val isEditable = AppRepository.isAttendanceEditable(log)
        val (remHours, remMinutes) = AppRepository.getAttendanceRemainingEditTime(log)
        val editStatusText = if (isEditable) "${remHours}h ${remMinutes}m edit window" else "Locked (22h expired)"

        Card(
          modifier = Modifier.fillMaxWidth(),
          shape = RoundedCornerShape(16.dp),
          colors = CardDefaults.cardColors(
            containerColor = Color(0xFF111C2E)
          ),
          border = BorderStroke(
            1.dp,
            if (isEditable) Color(0xFF0284C7).copy(alpha = 0.4f) else Color(0xFF1E2D42)
          )
        ) {
          Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            // Header Row: Student Name + Edit Badge
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                Text(
                  text = log.studentName,
                  fontWeight = FontWeight.Bold,
                  fontSize = 15.sp,
                  color = Color.White,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis
                )
                Text(
                  text = "ID: ${log.studentId} • Batch: ${log.batchName}",
                  fontSize = 11.sp,
                  color = Color(0xFF94A3B8),
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis
                )
              }

              // 22-Hour Edit Window Badge (Rule 2)
              Surface(
                color = if (isEditable) Color(0xFF0284C7).copy(alpha = 0.2f) else Color(0xFF1E293B),
                border = BorderStroke(0.5.dp, if (isEditable) Color(0xFF38BDF8).copy(alpha = 0.5f) else Color(0xFF334155)),
                shape = RoundedCornerShape(8.dp)
              ) {
                Row(
                  modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                  Icon(
                    imageVector = if (isEditable) Icons.Default.Timelapse else Icons.Default.Lock,
                    contentDescription = null,
                    tint = if (isEditable) Color(0xFF38BDF8) else Color(0xFF94A3B8),
                    modifier = Modifier.size(12.dp)
                  )
                  Text(
                    text = editStatusText,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isEditable) Color(0xFF38BDF8) else Color(0xFF94A3B8),
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis
                  )
                }
              }
            }

            // Date, Time & Recorder Details (Rule 4)
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Icon(
                  imageVector = Icons.Default.CalendarToday,
                  contentDescription = null,
                  tint = Color(0xFF94A3B8),
                  modifier = Modifier.size(12.dp)
                )
                Text(
                  text = "${log.date} • ${if (log.time.isNotBlank()) log.time else "Recorded"}",
                  fontSize = 11.sp,
                  fontWeight = FontWeight.SemiBold,
                  color = Color(0xFFCBD5E1)
                )
              }

              Text(
                text = "By: ${log.recordedBy ?: "system_fsi"}",
                fontSize = 11.sp,
                color = Color(0xFF94A3B8)
              )
            }

            if (!log.reason.isNullOrBlank()) {
              Surface(
                color = Color(0xFF0D1526),
                border = BorderStroke(1.dp, Color(0xFF1E2D42)),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
              ) {
                Text(
                  text = "Remark: ${log.reason}",
                  fontSize = 11.sp,
                  color = Color(0xFFFCA5A5),
                  modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
              }
            }

            HorizontalDivider(color = Color(0xFF1E2D42))

            // Status Control Row (Editable only within 22 hours - Rule 2)
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              // Current Status Display
              Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Status: ", fontSize = 11.sp, color = Color(0xFF94A3B8))
                Surface(
                  color = when (log.status.lowercase()) {
                    "present" -> Color(0xFF064E3B)
                    "absent" -> Color(0xFF450A0A)
                    else -> Color(0xFF1E293B)
                  },
                  border = BorderStroke(
                    0.5.dp,
                    when (log.status.lowercase()) {
                      "present" -> Color(0xFF059669)
                      "absent" -> Color(0xFFDC2626)
                      else -> Color(0xFF334155)
                    }
                  ),
                  shape = RoundedCornerShape(6.dp)
                ) {
                  Text(
                    text = log.status.uppercase(),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = when (log.status.lowercase()) {
                      "present" -> Color(0xFF4ADE80)
                      "absent" -> Color(0xFFF87171)
                      else -> Color(0xFFCBD5E1)
                    },
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                  )
                }
              }

              // Status Change Buttons (Enabled only if <= 22 hours)
              Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("Present", "Absent").forEach { stat ->
                  val isActive = log.status.equals(stat, ignoreCase = true)
                  Surface(
                    color = if (isActive) {
                      when (stat) {
                        "Present" -> Color(0xFF16A34A)
                        "Absent" -> Color(0xFFDC2626)
                        else -> Color(0xFF0284C7)
                      }
                    } else if (isEditable) {
                      Color(0xFF1E293B)
                    } else {
                      Color(0xFF1E293B).copy(alpha = 0.4f)
                    },
                    border = BorderStroke(1.dp, if (isActive) Color.Transparent else Color(0xFF334155)),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.clickable(enabled = isEditable) {
                      if (isEditable) {
                        val success = AppRepository.updateAttendanceRecord(log.id, stat, recordedBy = currentUserId)
                        if (success) {
                          systemFeedbackMsg = null
                        }
                      } else {
                        systemFeedbackMsg = "Security Block: This attendance record is permanently locked as the 22-hour edit window has expired."
                      }
                    }
                  ) {
                    Text(
                      text = stat,
                      color = if (isActive) Color.White else if (isEditable) Color(0xFFCBD5E1) else Color(0xFF64748B),
                      fontSize = 10.sp,
                      fontWeight = FontWeight.Bold,
                      maxLines = 1,
                      softWrap = false,
                      overflow = TextOverflow.Ellipsis,
                      modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                  }
                }

                // Edit remark & Delete buttons if editable/authorized
                if (isEditable) {
                  IconButton(
                    onClick = {
                      editingRecordForReason = log
                      customReasonText = log.reason ?: ""
                    },
                    modifier = Modifier.size(28.dp)
                  ) {
                    Icon(
                      imageVector = Icons.Outlined.EditNote,
                      contentDescription = "Edit Remark",
                      tint = Color(0xFF38BDF8),
                      modifier = Modifier.size(18.dp)
                    )
                  }
                }
                if (isEditable || currentUserRole.equals("Admin", ignoreCase = true)) {
                  IconButton(
                    onClick = {
                      val deleted = AppRepository.deleteAttendanceRecord(log.id)
                      if (deleted) {
                        systemFeedbackMsg = null
                      } else {
                        systemFeedbackMsg = "Not authorized to delete this attendance record."
                      }
                    },
                    modifier = Modifier.size(28.dp)
                  ) {
                    Icon(
                      imageVector = Icons.Default.DeleteOutline,
                      contentDescription = "Delete Record",
                      tint = Color(0xFFF87171),
                      modifier = Modifier.size(18.dp)
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
} else {
  MonthlyAttendanceSummary(
    modifier = Modifier.fillMaxSize(),
    currentUserRole = currentUserRole,
    currentUserId = currentUserId
  )
}
}

  // --- REASON / REMARK EDIT DIALOG ---
  if (editingRecordForReason != null) {
    val rec = editingRecordForReason!!
    AlertDialog(
      onDismissRequest = { editingRecordForReason = null },
      title = {
        Text("Update Attendance Remark", fontWeight = FontWeight.Bold, fontSize = 16.sp)
      },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
          Text(
            text = "${rec.studentName} (${rec.batchName}) - ${rec.date}",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
          OutlinedTextField(
            value = customReasonText,
            onValueChange = { customReasonText = it },
            placeholder = { Text("Enter remark/reason (e.g. Medical leave, Traffic)", fontSize = 12.sp) },
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.fillMaxWidth()
          )
        }
      },
      confirmButton = {
        Button(
          onClick = {
            AppRepository.updateAttendanceRecord(
              rec.id,
              rec.status,
              reason = customReasonText.ifBlank { null },
              recordedBy = currentUserId
            )
            editingRecordForReason = null
          },
          shape = RoundedCornerShape(8.dp)
        ) {
          Text("Save")
        }
      },
      dismissButton = {
        TextButton(onClick = { editingRecordForReason = null }) {
          Text("Cancel")
        }
      }
    )
  }

  // --- CALENDAR DATE PICKER MODAL ---
  if (showDatePickerDialog) {
    val datePickerState = rememberDatePickerState(
      initialSelectedDateMillis = System.currentTimeMillis()
    )

    DatePickerDialog(
      onDismissRequest = { showDatePickerDialog = false },
      confirmButton = {
        TextButton(
          onClick = {
            datePickerState.selectedDateMillis?.let { millis ->
              val cal = Calendar.getInstance()
              cal.timeInMillis = millis
              selectedDay = cal.get(Calendar.DAY_OF_MONTH)
              selectedMonth = cal.get(Calendar.MONTH) + 1
              selectedYear = cal.get(Calendar.YEAR)
              filterBySpecificDate = true
            }
            showDatePickerDialog = false
          }
        ) {
          Text("Select Date", fontWeight = FontWeight.Bold)
        }
      },
      dismissButton = {
        TextButton(onClick = { showDatePickerDialog = false }) {
          Text("Cancel")
        }
      }
    ) {
      DatePicker(state = datePickerState)
    }
  }
}
