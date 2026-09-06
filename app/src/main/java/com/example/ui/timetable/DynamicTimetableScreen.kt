package com.example.ui.timetable

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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.AppRepository
import com.example.data.DynamicTimetableEntry
import com.example.data.SeedData
import com.example.data.UserRole
import com.example.ui.components.DashboardEmptyStates
import com.example.ui.components.HeroFeatureCard
import com.example.ui.components.SectionEmptyState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay

@Composable
fun DynamicTimetableScreen() {
  val currentRole by AppRepository.currentRole.collectAsState()
  val allTimetableEntries by AppRepository.dynamicTimetable.collectAsState()
  val allBatches by AppRepository.batches.collectAsState()

  var selectedBatchFilter by remember { mutableStateOf("All Batches") }
  var selectedTab by remember { mutableStateOf(0) } // 0 = Active, 1 = History Archive
  var showAddDialog by remember { mutableStateOf(false) }

  // Periodic recomposition to keep countdown timers fresh every 30 seconds
  var ticks by remember { mutableStateOf(0L) }
  LaunchedEffect(Unit) {
    while (true) {
      delay(30000L)
      ticks++
    }
  }

  val twentyThreeHoursInMillis = 23 * 60 * 60 * 1000L

  // Build the comprehensive list of batches for filtering
  val availableBatchNames = remember(allBatches, allTimetableEntries) {
    val list = mutableListOf("All Batches")
    val defaultBatchNames = SeedData.defaultBatches.map { it.name }
    val repoBatchNames = allBatches.map { it.name }
    val combinedBatches = (repoBatchNames + defaultBatchNames).distinct()
    combinedBatches.forEach { name ->
      if (!list.contains(name)) list.add(name)
    }
    allTimetableEntries.map { it.batch }.distinct().forEach { name ->
      if (!list.contains(name)) list.add(name)
    }
    list
  }

  // Filter entries based on batch selection
  val entriesFilteredByBatch = remember(allTimetableEntries, selectedBatchFilter) {
    if (selectedBatchFilter == "All Batches") {
      allTimetableEntries
    } else {
      allTimetableEntries.filter { it.batch.equals(selectedBatchFilter, ignoreCase = true) }
    }
  }

  // Strict 23-hour auto-archive rule: Active entries are < 23 hours old
  val activeEntries = remember(entriesFilteredByBatch, ticks) {
    entriesFilteredByBatch.filter { item ->
      val elapsed = System.currentTimeMillis() - item.createdAt
      elapsed in 0L until twentyThreeHoursInMillis
    }.sortedBy { it.createdAt }
  }

  // Strict 23-hour auto-archive rule: Entries >= 23 hours old automatically move to History Archive
  val historyEntries = remember(entriesFilteredByBatch, ticks) {
    entriesFilteredByBatch.filter { item ->
      val elapsed = System.currentTimeMillis() - item.createdAt
      elapsed >= twentyThreeHoursInMillis || elapsed < 0L
    }.sortedByDescending { it.createdAt }
  }

  if (showAddDialog) {
    val selectableBatches = remember(availableBatchNames) {
      availableBatchNames.filter { it != "All Batches" }
    }
    val defaultBatchForModal = if (selectedBatchFilter != "All Batches") {
      selectedBatchFilter
    } else {
      selectableBatches.firstOrNull() ?: "12th Science"
    }

    AddTimetableDialog(
      allEntries = allTimetableEntries,
      availableBatches = selectableBatches,
      initialBatch = defaultBatchForModal,
      onDismiss = { showAddDialog = false },
      onSave = { batch, entries ->
        AppRepository.addDynamicTimetableEntries(
          batch = batch,
          entries = entries.map { Triple(it.subject, it.startTime, it.endTime) },
          createdAt = System.currentTimeMillis()
        )
        showAddDialog = false
      }
    )
  }

  LazyColumn(
    modifier = Modifier
      .fillMaxSize()
      .testTag("dynamic_timetable_screen"),
    contentPadding = PaddingValues(16.dp),
    verticalArrangement = Arrangement.spacedBy(14.dp)
  ) {
    // 1. Header Hero Card
    item {
      HeroFeatureCard(
        title = "Dynamic Timetable Board",
        subtitle = "Admin-scheduled classes remain live for 23 hours, then archive automatically.",
        tagText = "REALTIME SYNC",
        icon = Icons.Default.Schedule
      )
    }

    // 2. Batch Filter / Tab Bar above Active/History Archive Toggle
    item {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .clip(RoundedCornerShape(16.dp))
          .background(Color(0xFF0D1526))
          .border(1.dp, Color(0xFF1E2D42), RoundedCornerShape(16.dp))
          .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
          ) {
            Icon(
              imageVector = Icons.Default.FilterList,
              contentDescription = null,
              tint = Color(0xFF38BDF8),
              modifier = Modifier.size(14.dp)
            )
            Text(
              text = "BATCH SCHEDULE FILTER",
              fontSize = 11.sp,
              fontWeight = FontWeight.Bold,
              color = Color(0xFF94A3B8),
              letterSpacing = 0.5.sp
            )
          }

          if (selectedBatchFilter != "All Batches") {
            Text(
              text = "Show All Batches",
              fontSize = 11.sp,
              fontWeight = FontWeight.SemiBold,
              color = Color(0xFF38BDF8),
              modifier = Modifier
                .clickable { selectedBatchFilter = "All Batches" }
                .padding(vertical = 2.dp, horizontal = 4.dp)
            )
          }
        }

        // Horizontal scrollable batch chips
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          availableBatchNames.forEach { batchName ->
            val isSelected = batchName == selectedBatchFilter
            val count = if (batchName == "All Batches") {
              allTimetableEntries.size
            } else {
              allTimetableEntries.count { it.batch.equals(batchName, ignoreCase = true) }
            }

            Surface(
              onClick = { selectedBatchFilter = batchName },
              shape = RoundedCornerShape(10.dp),
              color = if (isSelected) MaterialTheme.colorScheme.primary else Color(0xFF10192C),
              border = BorderStroke(
                width = 1.dp,
                color = if (isSelected) MaterialTheme.colorScheme.primary else Color(0xFF1E2D42)
              )
            ) {
              Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
              ) {
                Text(
                  text = batchName,
                  fontSize = 11.sp,
                  fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                  color = if (isSelected) MaterialTheme.colorScheme.onPrimary else Color(0xFFCBD5E1)
                )
                Surface(
                  shape = CircleShape,
                  color = if (isSelected) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.2f) else Color(0xFF1E2D42)
                ) {
                  Text(
                    text = "$count",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary else Color(0xFF94A3B8),
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                  )
                }
              }
            }
          }
        }
      }
    }

    // 3. Control Row: Active / History Archive Toggle & Set Timetable Action Button
    item {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        // Rounded custom tab buttons
        Row(
          modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF0D1526))
            .border(1.dp, Color(0xFF1E2D42), RoundedCornerShape(16.dp))
            .padding(4.dp),
          horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
          TabButton(
            text = "Active (${activeEntries.size})",
            isSelected = selectedTab == 0,
            onClick = { selectedTab = 0 }
          )
          TabButton(
            text = "History Archive (${historyEntries.size})",
            isSelected = selectedTab == 1,
            onClick = { selectedTab = 1 }
          )
        }

        // Add class button visible only to ADMIN
        if (currentRole == UserRole.ADMIN) {
          Button(
            onClick = { showAddDialog = true },
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
            modifier = Modifier.testTag("admin_add_timetable_button")
          ) {
            Icon(
              imageVector = Icons.Default.Add,
              contentDescription = "Schedule class",
              modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text("Set Timetable", fontSize = 12.sp, fontWeight = FontWeight.Bold)
          }
        }
      }
    }

    // 4. Timetable Content List
    val displayList = if (selectedTab == 0) activeEntries else historyEntries

    if (displayList.isEmpty()) {
      item {
        if (selectedTab == 0) {
          DashboardEmptyStates.TodaysSchedule(
            onScheduleClass = if (currentRole == UserRole.ADMIN) { { showAddDialog = true } } else { {} },
            onAcademicSetup = null,
            modifier = Modifier.padding(vertical = 12.dp)
          )
        } else {
          SectionEmptyState(
            icon = Icons.Default.HistoryToggleOff,
            title = "No Archived Schedule History",
            description = "Lectures stay active on the timetable for 23 hours after starting before moving to the historical archive automatically.",
            actionLabel = if (currentRole == UserRole.ADMIN) "Schedule Dynamic Class" else null,
            onActionClick = if (currentRole == UserRole.ADMIN) { { showAddDialog = true } } else null,
            accentColor = Color(0xFF64748B),
            modifier = Modifier.padding(vertical = 12.dp)
          )
        }
      }
    } else {
      items(displayList, key = { it.id }) { item ->
        val elapsed = System.currentTimeMillis() - item.createdAt
        val remainingMillis = twentyThreeHoursInMillis - elapsed

        DynamicTimetableCard(
          entry = item,
          isHistory = selectedTab == 1,
          remainingMillis = remainingMillis,
          onDelete = if (currentRole == UserRole.ADMIN) { { AppRepository.deleteDynamicTimetableEntry(item.id) } } else null
        )
      }
    }
  }
}

@Composable
fun TabButton(text: String, isSelected: Boolean, onClick: () -> Unit) {
  Box(
    modifier = Modifier
      .clip(RoundedCornerShape(12.dp))
      .background(if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent)
      .clickable { onClick() }
      .padding(horizontal = 14.dp, vertical = 8.dp),
    contentAlignment = Alignment.Center
  ) {
    Text(
      text = text,
      color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
      fontSize = 12.sp,
      fontWeight = FontWeight.Bold
    )
  }
}

@Composable
fun DynamicTimetableCard(
  entry: DynamicTimetableEntry,
  isHistory: Boolean,
  remainingMillis: Long,
  onDelete: (() -> Unit)? = null
) {
  val formattedDate = remember(entry.createdAt) {
    SimpleDateFormat("hh:mm a, dd MMM", Locale.getDefault()).format(Date(entry.createdAt))
  }

  Card(
    modifier = Modifier
      .fillMaxWidth()
      .border(
        width = 1.dp,
        color = if (isHistory) Color(0xFF1E2D42).copy(alpha = 0.5f) else Color(0xFF1E2D42),
        shape = RoundedCornerShape(16.dp)
      ),
    colors = CardDefaults.cardColors(
      containerColor = if (isHistory) Color(0xFF0A0F1D) else Color(0xFF0D1526)
    ),
    shape = RoundedCornerShape(16.dp)
  ) {
    Column(
      modifier = Modifier.padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
      // Top header: Subject Title & Delete if admin
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = entry.subject,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
          )
          Spacer(modifier = Modifier.height(2.dp))
          Text(
            text = "Scheduled by: ${entry.teacherName}",
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
          )
        }

        if (onDelete != null) {
          IconButton(
            onClick = onDelete,
            modifier = Modifier.size(24.dp)
          ) {
            Icon(
              imageVector = Icons.Default.Delete,
              contentDescription = "Delete timetable entry",
              tint = MaterialTheme.colorScheme.error,
              modifier = Modifier.size(16.dp)
            )
          }
        }
      }

      // Details block: Batch & Class Hours (NO room info displayed)
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
        // Batch Tag
        Surface(
          color = MaterialTheme.colorScheme.secondaryContainer,
          shape = RoundedCornerShape(8.dp)
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            Icon(
              imageVector = Icons.Default.Groups,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.onSecondaryContainer,
              modifier = Modifier.size(12.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
              text = entry.batch,
              fontSize = 10.sp,
              fontWeight = FontWeight.Bold,
              color = MaterialTheme.colorScheme.onSecondaryContainer,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis
            )
          }
        }

        // Class Timing Info
        Surface(
          color = MaterialTheme.colorScheme.primaryContainer,
          shape = RoundedCornerShape(8.dp)
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            Icon(
              imageVector = Icons.Default.Schedule,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.onPrimaryContainer,
              modifier = Modifier.size(12.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
              text = "${entry.startTime} - ${entry.endTime}",
              fontSize = 10.sp,
              fontWeight = FontWeight.Bold,
              color = MaterialTheme.colorScheme.onPrimaryContainer,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis
            )
          }
        }
      }

      HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

      // Bottom footer: Timing status & 23h limit countdown
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Column {
          Text(
            text = "Posted At",
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
          )
          Text(
            text = formattedDate,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }

        // Remaining time display / Status Tag
        if (isHistory) {
          Surface(
            color = MaterialTheme.colorScheme.outlineVariant,
            shape = CircleShape
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
              Icon(
                imageVector = Icons.Default.Archive,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(12.dp)
              )
              Text(
                text = "Archived History",
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
            }
          }
        } else {
          // Calculate remaining hours and minutes for 23h lifetime
          val totalMinutes = (remainingMillis / (1000 * 60)).coerceAtLeast(0)
          val hours = totalMinutes / 60
          val minutes = totalMinutes % 60

          Surface(
            color = if (hours < 2) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer,
            shape = CircleShape
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
              Icon(
                imageVector = Icons.Default.Timer,
                contentDescription = null,
                tint = if (hours < 2) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(12.dp)
              )
              Text(
                text = "Live: ${hours}h ${minutes}m left",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = if (hours < 2) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimaryContainer
              )
            }
          }
        }
      }
    }
  }
}

data class TimetableTimeSlotInput(
  val id: String = java.util.UUID.randomUUID().toString(),
  val subject: String = "",
  val startTime: String = "10:00 AM",
  val endTime: String = "11:30 AM"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTimetableDialog(
  allEntries: List<DynamicTimetableEntry>,
  availableBatches: List<String>,
  initialBatch: String,
  onDismiss: () -> Unit,
  onSave: (batch: String, entries: List<TimetableTimeSlotInput>) -> Unit
) {
  var selectedBatch by remember { mutableStateOf(initialBatch) }
  var showBatchDropdown by remember { mutableStateOf(false) }

  // Multiple time entries list
  var timeSlots by remember {
    mutableStateOf(
      listOf(
        TimetableTimeSlotInput(
          subject = "",
          startTime = "09:00 AM",
          endTime = "10:30 AM"
        )
      )
    )
  }

  // Conflict warning checking across all entries and between slots in this submission
  val conflictWarning = remember(allEntries, selectedBatch, timeSlots) {
    checkScheduleConflictMulti(allEntries, selectedBatch, timeSlots)
  }

  val hasValidSubject = timeSlots.any { it.subject.isNotBlank() }
  val canSave = selectedBatch.isNotBlank() && hasValidSubject && conflictWarning == null

  Dialog(onDismissRequest = onDismiss) {
    Card(
      modifier = Modifier
        .fillMaxWidth()
        .padding(12.dp)
        .border(1.dp, Color(0xFF1E2D42), RoundedCornerShape(24.dp)),
      shape = RoundedCornerShape(24.dp),
      colors = CardDefaults.cardColors(containerColor = Color(0xFF0D1526))
    ) {
      Column(
        modifier = Modifier
          .padding(20.dp)
          .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp)
      ) {
        // Title block
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Column {
            Text(
              text = "Schedule Dynamic Class",
              fontSize = 18.sp,
              fontWeight = FontWeight.Bold,
              color = MaterialTheme.colorScheme.onSurface
            )
            Text(
              text = "Set Timetable",
              fontSize = 11.sp,
              fontWeight = FontWeight.Medium,
              color = Color(0xFF38BDF8)
            )
          }
          IconButton(onClick = onDismiss) {
            Icon(Icons.Default.Close, contentDescription = "Close")
          }
        }

        Text(
          text = "Admins can schedule flash lectures and meetings. Each active entry automatically moves to History Archive after exactly 23 hours.",
          fontSize = 11.sp,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // Required Batch Dropdown Selection
        Box(modifier = Modifier.fillMaxWidth()) {
          OutlinedTextField(
            value = selectedBatch,
            onValueChange = {},
            readOnly = true,
            label = { Text("Batch * (Required)") },
            placeholder = { Text("Select target batch") },
            trailingIcon = {
              IconButton(onClick = { showBatchDropdown = !showBatchDropdown }) {
                Icon(Icons.Default.ArrowDropDown, contentDescription = "Select Batch")
              }
            },
            modifier = Modifier.fillMaxWidth()
          )
          DropdownMenu(
            expanded = showBatchDropdown,
            onDismissRequest = { showBatchDropdown = false },
            modifier = Modifier.fillMaxWidth(0.85f)
          ) {
            availableBatches.forEach { batchName ->
              DropdownMenuItem(
                text = {
                  Text(
                    text = batchName,
                    fontWeight = if (batchName == selectedBatch) FontWeight.Bold else FontWeight.Normal
                  )
                },
                onClick = {
                  selectedBatch = batchName
                  showBatchDropdown = false
                }
              )
            }
          }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

        // Multiple Time Entries Section Header
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Text(
            text = "Class Schedule Entries (${timeSlots.size})",
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
          )
          Text(
            text = "Batch: $selectedBatch",
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = Color(0xFF38BDF8)
          )
        }

        // List of entries
        timeSlots.forEachIndexed { index, slot ->
          Card(
            modifier = Modifier
              .fillMaxWidth()
              .border(1.dp, Color(0xFF1E2D42), RoundedCornerShape(16.dp)),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF10192C))
          ) {
            Column(
              modifier = Modifier.padding(12.dp),
              verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
              // Entry header with remove button
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
              ) {
                Surface(
                  color = MaterialTheme.colorScheme.primaryContainer,
                  shape = RoundedCornerShape(6.dp)
                ) {
                  Text(
                    text = "Lecture Entry #${index + 1}",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                  )
                }

                // Individual remove button
                if (timeSlots.size > 1) {
                  IconButton(
                    onClick = {
                      timeSlots = timeSlots.filterIndexed { i, _ -> i != index }
                    },
                    modifier = Modifier.size(24.dp)
                  ) {
                    Icon(
                      imageVector = Icons.Default.DeleteOutline,
                      contentDescription = "Remove entry",
                      tint = MaterialTheme.colorScheme.error,
                      modifier = Modifier.size(18.dp)
                    )
                  }
                }
              }

              // Subject TextField
              OutlinedTextField(
                value = slot.subject,
                onValueChange = { newSubject ->
                  timeSlots = timeSlots.toMutableList().also { list ->
                    list[index] = slot.copy(subject = newSubject)
                  }
                },
                label = { Text("Subject / Lecture Topic *") },
                placeholder = { Text("e.g. Physics • Modern Physics Revision") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
              )

              // Start and End Times row
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
              ) {
                OutlinedTextField(
                  value = slot.startTime,
                  onValueChange = { newStart ->
                    timeSlots = timeSlots.toMutableList().also { list ->
                      list[index] = slot.copy(startTime = newStart)
                    }
                  },
                  label = { Text("Start Time") },
                  modifier = Modifier.weight(1f),
                  singleLine = true
                )
                OutlinedTextField(
                  value = slot.endTime,
                  onValueChange = { newEnd ->
                    timeSlots = timeSlots.toMutableList().also { list ->
                      list[index] = slot.copy(endTime = newEnd)
                    }
                  },
                  label = { Text("End Time") },
                  modifier = Modifier.weight(1f),
                  singleLine = true
                )
              }
            }
          }
        }

        // "+ Add another entry" Button
        OutlinedButton(
          onClick = {
            val lastSlot = timeSlots.lastOrNull()
            val nextStart = lastSlot?.endTime?.trim()?.ifBlank { "10:45 AM" } ?: "10:45 AM"
            val nextEnd = calculateNextEndTime(nextStart)
            timeSlots = timeSlots + TimetableTimeSlotInput(
              subject = "",
              startTime = nextStart,
              endTime = nextEnd
            )
          },
          modifier = Modifier.fillMaxWidth(),
          shape = RoundedCornerShape(12.dp),
          colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF38BDF8)),
          border = BorderStroke(1.dp, Color(0xFF1E2D42))
        ) {
          Icon(
            imageVector = Icons.Default.Add,
            contentDescription = null,
            modifier = Modifier.size(16.dp)
          )
          Spacer(modifier = Modifier.width(6.dp))
          Text("+ Add another entry", fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }

        // Conflict Warning Banner
        if (conflictWarning != null) {
          Surface(
            color = MaterialTheme.colorScheme.errorContainer,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
          ) {
            Row(
              modifier = Modifier.padding(12.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
              Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = "Conflict warning",
                tint = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.size(18.dp)
              )
              Text(
                text = conflictWarning,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onErrorContainer
              )
            }
          }
        }

        // Action Buttons
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.End,
          verticalAlignment = Alignment.CenterVertically
        ) {
          TextButton(onClick = onDismiss) {
            Text("Cancel")
          }
          Spacer(modifier = Modifier.width(10.dp))
          Button(
            onClick = {
              val validSlots = timeSlots.filter { it.subject.isNotBlank() }
              if (validSlots.isNotEmpty() && selectedBatch.isNotBlank()) {
                onSave(selectedBatch, validSlots)
              }
            },
            enabled = canSave,
            shape = RoundedCornerShape(12.dp)
          ) {
            Text("Set Active")
          }
        }
      }
    }
  }
}

fun calculateNextEndTime(startTimeStr: String): String {
  val startMinutes = parseTimeTextToMinutes(startTimeStr)
  if (startMinutes == -1) return "12:00 PM"
  val endMinutes = (startMinutes + 90) % (24 * 60)
  val hour24 = endMinutes / 60
  val minute = endMinutes % 60
  val isPm = hour24 >= 12
  val hour12 = when {
    hour24 == 0 -> 12
    hour24 > 12 -> hour24 - 12
    else -> hour24
  }
  val amPm = if (isPm) "PM" else "AM"
  return String.format(Locale.getDefault(), "%02d:%02d %s", hour12, minute, amPm)
}

fun parseTimeTextToMinutes(timeStr: String): Int {
  try {
    val clean = timeStr.trim().uppercase()
    val isPm = clean.endsWith("PM")
    val isAm = clean.endsWith("AM")
    val content = clean.replace("AM", "").replace("PM", "").trim()
    val parts = content.split(":")
    if (parts.isNotEmpty()) {
      var hour = parts[0].toIntOrNull() ?: 0
      val minute = if (parts.size > 1) parts[1].toIntOrNull() ?: 0 else 0
      if (isPm && hour < 12) hour += 12
      if (isAm && hour == 12) hour = 0
      return hour * 60 + minute
    }
  } catch (e: Exception) {
    // Return fallback
  }
  return -1
}

fun checkScheduleConflictMulti(
  allEntries: List<DynamicTimetableEntry>,
  batch: String,
  slots: List<TimetableTimeSlotInput>
): String? {
  val twentyThreeHoursInMillis = 23 * 60 * 60 * 1000L
  val validSlots = slots.filter { it.subject.isNotBlank() }

  // 1. Check internal conflicts between entries within the current submission
  for (i in validSlots.indices) {
    val s1 = validSlots[i]
    val start1 = parseTimeTextToMinutes(s1.startTime)
    val end1 = parseTimeTextToMinutes(s1.endTime)
    if (start1 == -1 || end1 == -1 || start1 >= end1) continue

    for (j in i + 1 until validSlots.size) {
      val s2 = validSlots[j]
      val start2 = parseTimeTextToMinutes(s2.startTime)
      val end2 = parseTimeTextToMinutes(s2.endTime)
      if (start2 == -1 || end2 == -1 || start2 >= end2) continue

      if (start1 < end2 && end1 > start2) {
        return "⚠️ Slot Conflict: '${s1.subject}' (${s1.startTime} - ${s1.endTime}) overlaps with '${s2.subject}' (${s2.startTime} - ${s2.endTime})!"
      }
    }
  }

  // 2. Check external conflicts against currently active entries for this batch
  for (slot in validSlots) {
    val slotStart = parseTimeTextToMinutes(slot.startTime)
    val slotEnd = parseTimeTextToMinutes(slot.endTime)
    if (slotStart == -1 || slotEnd == -1 || slotStart >= slotEnd) continue

    for (entry in allEntries) {
      val elapsed = System.currentTimeMillis() - entry.createdAt
      if (elapsed in 0L until twentyThreeHoursInMillis) {
        if (entry.batch.trim().equals(batch.trim(), ignoreCase = true)) {
          val entryStart = parseTimeTextToMinutes(entry.startTime)
          val entryEnd = parseTimeTextToMinutes(entry.endTime)
          if (entryStart == -1 || entryEnd == -1 || entryStart >= entryEnd) continue

          if (slotStart < entryEnd && slotEnd > entryStart) {
            return "⚠️ Batch Conflict: $batch already has an active class '${entry.subject}' (${entry.startTime} - ${entry.endTime})!"
          }
        }
      }
    }
  }

  return null
}
