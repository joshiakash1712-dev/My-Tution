package com.example.ui.admin

import android.content.Context
import android.content.Intent
import android.net.Uri
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.*
import com.example.utils.CurrencyFormatter

/**
 * Utility helpers for direct communication actions
 */
private fun openDialer(context: Context, phone: String) {
  try {
    val clean = phone.filter { it.isDigit() || it == '+' }
    if (clean.isNotBlank()) {
      val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$clean"))
      context.startActivity(intent)
    } else {
      Toast.makeText(context, "No phone number available", Toast.LENGTH_SHORT).show()
    }
  } catch (e: Exception) {
    Toast.makeText(context, "Contact: $phone", Toast.LENGTH_SHORT).show()
  }
}

private fun openWhatsApp(context: Context, phone: String, message: String) {
  try {
    val cleanDigits = phone.filter { it.isDigit() }
    val formatted = if (cleanDigits.length == 10) "91$cleanDigits" else cleanDigits
    val uri = Uri.parse("https://api.whatsapp.com/send?phone=$formatted&text=${Uri.encode(message)}")
    val intent = Intent(Intent.ACTION_VIEW, uri)
    context.startActivity(intent)
  } catch (e: Exception) {
    Toast.makeText(context, "WhatsApp alert queued for $phone", Toast.LENGTH_SHORT).show()
  }
}

/**
 * 1. Present Students List Dialog
 * Opened when user taps the "Present Today" card on the Dashboard.
 */
@Composable
fun PresentStudentsListDialog(
  students: List<Student>,
  currentDateFormatted: String,
  onDismiss: () -> Unit,
  onTakeAttendanceClick: () -> Unit,
  onStudentClick: (Student) -> Unit = {}
) {
  val context = LocalContext.current
  var searchQuery by remember { mutableStateOf("") }
  var selectedBatch by remember { mutableStateOf("All Batches") }

  val batchList = remember(students) {
    listOf("All Batches") + students.map { it.batch }.distinct().filter { it.isNotBlank() }
  }

  val filteredStudents = remember(students, searchQuery, selectedBatch) {
    students.filter { student ->
      val matchesSearch = student.name.contains(searchQuery, ignoreCase = true) ||
          student.id.contains(searchQuery, ignoreCase = true) ||
          student.mobile.contains(searchQuery)
      val matchesBatch = selectedBatch == "All Batches" || student.batch == selectedBatch
      matchesSearch && matchesBatch
    }
  }

  Dialog(
    onDismissRequest = onDismiss,
    properties = DialogProperties(usePlatformDefaultWidth = false)
  ) {
    Surface(
      modifier = Modifier
        .fillMaxWidth(0.95f)
        .fillMaxHeight(0.88f),
      shape = RoundedCornerShape(20.dp),
      color = Color(0xFF0D1526),
      border = BorderStroke(1.dp, Color(0xFF1E2D42))
    ) {
      Column(modifier = Modifier.fillMaxSize()) {
        // Header
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF111C2E))
            .padding(horizontal = 16.dp, vertical = 14.dp),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
          ) {
            Box(
              modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF10B981).copy(alpha = 0.18f)),
              contentAlignment = Alignment.Center
            ) {
              Icon(
                imageVector = Icons.Outlined.CheckCircle,
                contentDescription = null,
                tint = Color(0xFF4ADE80),
                modifier = Modifier.size(22.dp)
              )
            }
            Column {
              Text(
                text = "Present Students (${filteredStudents.size}/${students.size})",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = Color.White
              )
              Text(
                text = "$currentDateFormatted • In-Campus Learners",
                fontSize = 11.sp,
                color = Color(0xFF94A3B8)
              )
            }
          }
          IconButton(
            onClick = onDismiss,
            modifier = Modifier
              .size(32.dp)
              .background(Color(0xFF1E293B), CircleShape)
          ) {
            Icon(Icons.Default.Close, contentDescription = "Close", tint = Color(0xFF94A3B8), modifier = Modifier.size(16.dp))
          }
        }

        HorizontalDivider(color = Color(0xFF1E2D42))

        // Search & Batch Filters
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 10.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Search by name, roll no or contact...", fontSize = 12.sp, color = Color(0xFF64748B)) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Color(0xFF64748B), modifier = Modifier.size(18.dp)) },
            trailingIcon = {
              if (searchQuery.isNotBlank()) {
                IconButton(onClick = { searchQuery = "" }) {
                  Icon(Icons.Default.Close, contentDescription = "Clear", tint = Color(0xFF94A3B8), modifier = Modifier.size(16.dp))
                }
              }
            },
            singleLine = true,
            shape = RoundedCornerShape(10.dp),
            colors = OutlinedTextFieldDefaults.colors(
              focusedContainerColor = Color(0xFF0A1220),
              unfocusedContainerColor = Color(0xFF0A1220),
              focusedBorderColor = Color(0xFF10B981),
              unfocusedBorderColor = Color(0xFF1E2D42),
              focusedTextColor = Color.White,
              unfocusedTextColor = Color.White
            ),
            modifier = Modifier
              .fillMaxWidth()
              .height(50.dp)
          )

          // Batch Filter Chips
          if (batchList.size > 1) {
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
              horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
              batchList.forEach { batchName ->
                val isSelected = selectedBatch == batchName
                Surface(
                  shape = RoundedCornerShape(8.dp),
                  color = if (isSelected) Color(0xFF10B981) else Color(0xFF111C2E),
                  border = BorderStroke(1.dp, if (isSelected) Color(0xFF10B981) else Color(0xFF1E2D42)),
                  modifier = Modifier.clickable { selectedBatch = batchName }
                ) {
                  Text(
                    text = batchName,
                    fontSize = 11.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    color = if (isSelected) Color.Black else Color(0xFFCBD5E1),
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                  )
                }
              }
            }
          }
        }

        HorizontalDivider(color = Color(0xFF1E2D42))

        // Student List Content
        if (filteredStudents.isEmpty()) {
          Box(
            modifier = Modifier
              .weight(1f)
              .fillMaxWidth()
              .padding(24.dp),
            contentAlignment = Alignment.Center
          ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
              Icon(Icons.Outlined.EventBusy, contentDescription = null, tint = Color(0xFF64748B), modifier = Modifier.size(44.dp))
              Text(
                text = if (students.isEmpty()) "No attendance recorded for today yet" else "No matching students found",
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = Color.White
              )
              Text(
                text = "Tap below to launch the daily register and record student attendance.",
                fontSize = 12.sp,
                color = Color(0xFF94A3B8),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
              )
            }
          }
        } else {
          LazyColumn(
            modifier = Modifier
              .weight(1f)
              .fillMaxWidth(),
            contentPadding = PaddingValues(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            items(filteredStudents, key = { it.id }) { student ->
              Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFF111C2E),
                border = BorderStroke(1.dp, Color(0xFF24334A)),
                modifier = Modifier
                  .fillMaxWidth()
                  .clickable { onStudentClick(student) }
              ) {
                Row(
                  modifier = Modifier.padding(12.dp),
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                  // Avatar
                  Box(
                    modifier = Modifier
                      .size(40.dp)
                      .clip(CircleShape)
                      .background(Color(0xFF10B981).copy(alpha = 0.2f))
                      .border(1.dp, Color(0xFF10B981), CircleShape),
                    contentAlignment = Alignment.Center
                  ) {
                    Text(
                      text = student.name.take(1).uppercase(),
                      fontWeight = FontWeight.Bold,
                      fontSize = 16.sp,
                      color = Color(0xFF4ADE80)
                    )
                  }

                  // Details
                  Column(modifier = Modifier.weight(1f)) {
                    Row(
                      verticalAlignment = Alignment.CenterVertically,
                      horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                      Text(
                        text = student.name,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                      )
                      Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = Color(0xFF10B981).copy(alpha = 0.15f)
                      ) {
                        Text(
                          text = "Present",
                          fontSize = 9.sp,
                          fontWeight = FontWeight.Bold,
                          color = Color(0xFF4ADE80),
                          modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                        )
                      }
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                      text = "Roll: ${student.id} • ${student.batch.ifBlank { "General Batch" }}",
                      fontSize = 11.sp,
                      color = Color(0xFF94A3B8),
                      maxLines = 1,
                      overflow = TextOverflow.Ellipsis
                    )
                    Text(
                      text = "Overall: ${student.attendancePercent}% • Parent: ${student.parentName.ifBlank { "Guardian" }}",
                      fontSize = 10.sp,
                      color = Color(0xFF64748B),
                      maxLines = 1,
                      overflow = TextOverflow.Ellipsis
                    )
                  }

                  // Communication Actions
                  Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(
                      onClick = {
                        val phone = student.parentContact.ifBlank { student.mobile }
                        openWhatsApp(
                          context,
                          phone,
                          "Dear Parent, ${student.name} is PRESENT at Fadat Science Institute today ($currentDateFormatted)."
                        )
                      },
                      modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF22C55E).copy(alpha = 0.15f))
                    ) {
                      Icon(Icons.Default.Chat, contentDescription = "WhatsApp", tint = Color(0xFF4ADE80), modifier = Modifier.size(16.dp))
                    }
                    IconButton(
                      onClick = {
                        val phone = student.parentContact.ifBlank { student.mobile }
                        openDialer(context, phone)
                      },
                      modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF0284C7).copy(alpha = 0.15f))
                    ) {
                      Icon(Icons.Default.Call, contentDescription = "Call", tint = Color(0xFF38BDF8), modifier = Modifier.size(16.dp))
                    }
                  }
                }
              }
            }
          }
        }

        HorizontalDivider(color = Color(0xFF1E2D42))

        // Footer
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF111C2E))
            .padding(horizontal = 14.dp, vertical = 10.dp),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          TextButton(onClick = onDismiss) {
            Text("Close", color = Color(0xFF94A3B8), fontSize = 12.sp)
          }

          Button(
            onClick = {
              onDismiss()
              onTakeAttendanceClick()
            },
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
            shape = RoundedCornerShape(10.dp),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
          ) {
            Icon(Icons.Default.HowToReg, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Open Attendance Register", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.Black)
          }
        }
      }
    }
  }
}

/**
 * 2. Absent Students List Dialog
 * Opened when user taps the "Absent Today" card on the Dashboard.
 */
@Composable
fun AbsentStudentsListDialog(
  students: List<Student>,
  currentDateFormatted: String,
  onDismiss: () -> Unit,
  onTakeAttendanceClick: () -> Unit,
  onStudentClick: (Student) -> Unit = {},
  onMarkPresent: (Student) -> Unit = {}
) {
  val context = LocalContext.current
  var searchQuery by remember { mutableStateOf("") }
  var selectedBatch by remember { mutableStateOf("All Batches") }
  var broadcastAlertSent by remember { mutableStateOf(false) }

  val batchList = remember(students) {
    listOf("All Batches") + students.map { it.batch }.distinct().filter { it.isNotBlank() }
  }

  val filteredStudents = remember(students, searchQuery, selectedBatch) {
    students.filter { student ->
      val matchesSearch = student.name.contains(searchQuery, ignoreCase = true) ||
          student.id.contains(searchQuery, ignoreCase = true) ||
          student.mobile.contains(searchQuery)
      val matchesBatch = selectedBatch == "All Batches" || student.batch == selectedBatch
      matchesSearch && matchesBatch
    }
  }

  Dialog(
    onDismissRequest = onDismiss,
    properties = DialogProperties(usePlatformDefaultWidth = false)
  ) {
    Surface(
      modifier = Modifier
        .fillMaxWidth(0.95f)
        .fillMaxHeight(0.88f),
      shape = RoundedCornerShape(20.dp),
      color = Color(0xFF0D1526),
      border = BorderStroke(1.dp, Color(0xFF1E2D42))
    ) {
      Column(modifier = Modifier.fillMaxSize()) {
        // Header
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF111C2E))
            .padding(horizontal = 16.dp, vertical = 14.dp),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
          ) {
            Box(
              modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFFEF4444).copy(alpha = 0.18f)),
              contentAlignment = Alignment.Center
            ) {
              Icon(
                imageVector = Icons.Outlined.Cancel,
                contentDescription = null,
                tint = Color(0xFFF87171),
                modifier = Modifier.size(22.dp)
              )
            }
            Column {
              Text(
                text = "Absent Students (${filteredStudents.size}/${students.size})",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = Color.White
              )
              Text(
                text = "$currentDateFormatted • Action Required",
                fontSize = 11.sp,
                color = Color(0xFF94A3B8)
              )
            }
          }
          IconButton(
            onClick = onDismiss,
            modifier = Modifier
              .size(32.dp)
              .background(Color(0xFF1E293B), CircleShape)
          ) {
            Icon(Icons.Default.Close, contentDescription = "Close", tint = Color(0xFF94A3B8), modifier = Modifier.size(16.dp))
          }
        }

        HorizontalDivider(color = Color(0xFF1E2D42))

        // Bulk Alert Action Banner
        if (students.isNotEmpty()) {
          Surface(
            color = if (broadcastAlertSent) Color(0xFF064E3B) else Color(0xFF1A1A2E),
            border = BorderStroke(1.dp, if (broadcastAlertSent) Color(0xFF059669) else Color(0xFFEF4444).copy(alpha = 0.3f)),
            modifier = Modifier
              .fillMaxWidth()
              .padding(horizontal = 14.dp, vertical = 8.dp),
            shape = RoundedCornerShape(10.dp)
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.SpaceBetween
            ) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
              ) {
                Icon(
                  imageVector = if (broadcastAlertSent) Icons.Default.CheckCircle else Icons.Default.NotificationsActive,
                  contentDescription = null,
                  tint = if (broadcastAlertSent) Color(0xFF4ADE80) else Color(0xFFF87171),
                  modifier = Modifier.size(18.dp)
                )
                Text(
                  text = if (broadcastAlertSent) "✓ Absence notices queued for all parents!" else "Notify all ${students.size} parents via WhatsApp & SMS",
                  fontSize = 11.sp,
                  fontWeight = FontWeight.SemiBold,
                  color = if (broadcastAlertSent) Color(0xFF4ADE80) else Color.White
                )
              }
              if (!broadcastAlertSent) {
                Button(
                  onClick = {
                    broadcastAlertSent = true
                    students.forEach { s ->
                      AppRepository.addNotification(
                        NotificationItem(
                          id = "ABS_${System.currentTimeMillis()}_${s.id}",
                          title = "Absence Alert: ${s.name}",
                          message = "Student ${s.name} is marked ABSENT today ($currentDateFormatted). Please contact tuition office.",
                          time = AppRepository.getCurrentTimeStr(),
                          type = "Absence",
                          recipientRole = "Parent"
                        )
                      )
                    }
                    Toast.makeText(context, "Absence alerts sent to all ${students.size} parents!", Toast.LENGTH_SHORT).show()
                  },
                  colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                  shape = RoundedCornerShape(8.dp),
                  contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                  modifier = Modifier.height(30.dp)
                ) {
                  Text("Send All", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
              }
            }
          }
        }

        // Search & Filters
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 6.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Search absent student...", fontSize = 12.sp, color = Color(0xFF64748B)) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Color(0xFF64748B), modifier = Modifier.size(18.dp)) },
            trailingIcon = {
              if (searchQuery.isNotBlank()) {
                IconButton(onClick = { searchQuery = "" }) {
                  Icon(Icons.Default.Close, contentDescription = "Clear", tint = Color(0xFF94A3B8), modifier = Modifier.size(16.dp))
                }
              }
            },
            singleLine = true,
            shape = RoundedCornerShape(10.dp),
            colors = OutlinedTextFieldDefaults.colors(
              focusedContainerColor = Color(0xFF0A1220),
              unfocusedContainerColor = Color(0xFF0A1220),
              focusedBorderColor = Color(0xFFEF4444),
              unfocusedBorderColor = Color(0xFF1E2D42),
              focusedTextColor = Color.White,
              unfocusedTextColor = Color.White
            ),
            modifier = Modifier
              .fillMaxWidth()
              .height(50.dp)
          )

          // Batch Filter Chips
          if (batchList.size > 1) {
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
              horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
              batchList.forEach { batchName ->
                val isSelected = selectedBatch == batchName
                Surface(
                  shape = RoundedCornerShape(8.dp),
                  color = if (isSelected) Color(0xFFEF4444) else Color(0xFF111C2E),
                  border = BorderStroke(1.dp, if (isSelected) Color(0xFFEF4444) else Color(0xFF1E2D42)),
                  modifier = Modifier.clickable { selectedBatch = batchName }
                ) {
                  Text(
                    text = batchName,
                    fontSize = 11.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    color = if (isSelected) Color.White else Color(0xFFCBD5E1),
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                  )
                }
              }
            }
          }
        }

        HorizontalDivider(color = Color(0xFF1E2D42))

        // Student List Content
        if (filteredStudents.isEmpty()) {
          Box(
            modifier = Modifier
              .weight(1f)
              .fillMaxWidth()
              .padding(24.dp),
            contentAlignment = Alignment.Center
          ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
              Icon(Icons.Outlined.SentimentSatisfied, contentDescription = null, tint = Color(0xFF4ADE80), modifier = Modifier.size(48.dp))
              Text(
                text = if (students.isEmpty()) "All Enrolled Students Are Present!" else "No matching absent students found",
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = Color.White
              )
              Text(
                text = "100% attendance recorded today across batches.",
                fontSize = 12.sp,
                color = Color(0xFF94A3B8),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
              )
            }
          }
        } else {
          LazyColumn(
            modifier = Modifier
              .weight(1f)
              .fillMaxWidth(),
            contentPadding = PaddingValues(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            items(filteredStudents, key = { it.id }) { student ->
              Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFF111C2E),
                border = BorderStroke(1.dp, Color(0xFF24334A)),
                modifier = Modifier
                  .fillMaxWidth()
                  .clickable { onStudentClick(student) }
              ) {
                Row(
                  modifier = Modifier.padding(12.dp),
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                  // Avatar in Red Tint
                  Box(
                    modifier = Modifier
                      .size(40.dp)
                      .clip(CircleShape)
                      .background(Color(0xFFEF4444).copy(alpha = 0.2f))
                      .border(1.dp, Color(0xFFEF4444), CircleShape),
                    contentAlignment = Alignment.Center
                  ) {
                    Text(
                      text = student.name.take(1).uppercase(),
                      fontWeight = FontWeight.Bold,
                      fontSize = 16.sp,
                      color = Color(0xFFF87171)
                    )
                  }

                  // Details
                  Column(modifier = Modifier.weight(1f)) {
                    Row(
                      verticalAlignment = Alignment.CenterVertically,
                      horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                      Text(
                        text = student.name,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                      )
                      Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = Color(0xFFEF4444).copy(alpha = 0.15f)
                      ) {
                        Text(
                          text = "Absent",
                          fontSize = 9.sp,
                          fontWeight = FontWeight.Bold,
                          color = Color(0xFFF87171),
                          modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                        )
                      }
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                      text = "Roll: ${student.id} • ${student.batch.ifBlank { "General Batch" }}",
                      fontSize = 11.sp,
                      color = Color(0xFF94A3B8),
                      maxLines = 1,
                      overflow = TextOverflow.Ellipsis
                    )
                    Text(
                      text = "Parent: ${student.parentName.ifBlank { "Guardian" }} (${student.parentContact.ifBlank { student.mobile }})",
                      fontSize = 10.sp,
                      color = Color(0xFF64748B),
                      maxLines = 1,
                      overflow = TextOverflow.Ellipsis
                    )
                  }

                  // Action Buttons: Mark Present + WhatsApp + Call
                  Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    // Mark Present toggle
                    IconButton(
                      onClick = {
                        onMarkPresent(student)
                        Toast.makeText(context, "${student.name} marked Present!", Toast.LENGTH_SHORT).show()
                      },
                      modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF10B981).copy(alpha = 0.15f))
                    ) {
                      Icon(Icons.Default.Check, contentDescription = "Mark Present", tint = Color(0xFF4ADE80), modifier = Modifier.size(16.dp))
                    }

                    // WhatsApp Alert
                    IconButton(
                      onClick = {
                        val phone = student.parentContact.ifBlank { student.mobile }
                        openWhatsApp(
                          context,
                          phone,
                          "Dear Parent, your child ${student.name} is ABSENT today ($currentDateFormatted) at Fadat Science Institute. Please contact tuition office if this is an error."
                        )
                      },
                      modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF22C55E).copy(alpha = 0.15f))
                    ) {
                      Icon(Icons.Default.Chat, contentDescription = "WhatsApp", tint = Color(0xFF4ADE80), modifier = Modifier.size(16.dp))
                    }

                    // Call Parent
                    IconButton(
                      onClick = {
                        val phone = student.parentContact.ifBlank { student.mobile }
                        openDialer(context, phone)
                      },
                      modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF0284C7).copy(alpha = 0.15f))
                    ) {
                      Icon(Icons.Default.Call, contentDescription = "Call", tint = Color(0xFF38BDF8), modifier = Modifier.size(16.dp))
                    }
                  }
                }
              }
            }
          }
        }

        HorizontalDivider(color = Color(0xFF1E2D42))

        // Footer
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF111C2E))
            .padding(horizontal = 14.dp, vertical = 10.dp),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          TextButton(onClick = onDismiss) {
            Text("Close", color = Color(0xFF94A3B8), fontSize = 12.sp)
          }

          Button(
            onClick = {
              onDismiss()
              onTakeAttendanceClick()
            },
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
            shape = RoundedCornerShape(10.dp),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
          ) {
            Icon(Icons.Default.HowToReg, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Daily Attendance Register", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
          }
        }
      }
    }
  }
}

/**
 * 3. Enrolled Students Directory Dialog
 * Opened when user taps "Total Students" on the Dashboard.
 */
@Composable
fun EnrolledStudentsListDialog(
  students: List<Student>,
  fees: List<FeeRecord>,
  onDismiss: () -> Unit,
  onAdmitClick: () -> Unit,
  onOpenDirectoryClick: () -> Unit,
  onStudentClick: (Student) -> Unit = {}
) {
  val context = LocalContext.current
  var searchQuery by remember { mutableStateOf("") }
  var selectedBatch by remember { mutableStateOf("All Batches") }

  val feeMap = remember(fees) {
    fees.associateBy { it.studentName }
  }

  val batchList = remember(students) {
    listOf("All Batches") + students.map { it.batch }.distinct().filter { it.isNotBlank() }
  }

  val filteredStudents = remember(students, searchQuery, selectedBatch) {
    students.filter { student ->
      val matchesSearch = student.name.contains(searchQuery, ignoreCase = true) ||
          student.id.contains(searchQuery, ignoreCase = true) ||
          student.mobile.contains(searchQuery)
      val matchesBatch = selectedBatch == "All Batches" || student.batch == selectedBatch
      matchesSearch && matchesBatch
    }
  }

  Dialog(
    onDismissRequest = onDismiss,
    properties = DialogProperties(usePlatformDefaultWidth = false)
  ) {
    Surface(
      modifier = Modifier
        .fillMaxWidth(0.95f)
        .fillMaxHeight(0.88f),
      shape = RoundedCornerShape(20.dp),
      color = Color(0xFF0D1526),
      border = BorderStroke(1.dp, Color(0xFF1E2D42))
    ) {
      Column(modifier = Modifier.fillMaxSize()) {
        // Header
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF111C2E))
            .padding(horizontal = 16.dp, vertical = 14.dp),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
          ) {
            Box(
              modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF6366F1).copy(alpha = 0.18f)),
              contentAlignment = Alignment.Center
            ) {
              Icon(
                imageVector = Icons.Outlined.People,
                contentDescription = null,
                tint = Color(0xFF818CF8),
                modifier = Modifier.size(22.dp)
              )
            }
            Column {
              Text(
                text = "Enrolled Students (${filteredStudents.size}/${students.size})",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = Color.White
              )
              Text(
                text = "Session 2026-27 • Active Admissions",
                fontSize = 11.sp,
                color = Color(0xFF94A3B8)
              )
            }
          }
          IconButton(
            onClick = onDismiss,
            modifier = Modifier
              .size(32.dp)
              .background(Color(0xFF1E293B), CircleShape)
          ) {
            Icon(Icons.Default.Close, contentDescription = "Close", tint = Color(0xFF94A3B8), modifier = Modifier.size(16.dp))
          }
        }

        HorizontalDivider(color = Color(0xFF1E2D42))

        // Search & Filters
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 10.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Search by name, roll no or contact...", fontSize = 12.sp, color = Color(0xFF64748B)) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Color(0xFF64748B), modifier = Modifier.size(18.dp)) },
            trailingIcon = {
              if (searchQuery.isNotBlank()) {
                IconButton(onClick = { searchQuery = "" }) {
                  Icon(Icons.Default.Close, contentDescription = "Clear", tint = Color(0xFF94A3B8), modifier = Modifier.size(16.dp))
                }
              }
            },
            singleLine = true,
            shape = RoundedCornerShape(10.dp),
            colors = OutlinedTextFieldDefaults.colors(
              focusedContainerColor = Color(0xFF0A1220),
              unfocusedContainerColor = Color(0xFF0A1220),
              focusedBorderColor = Color(0xFF818CF8),
              unfocusedBorderColor = Color(0xFF1E2D42),
              focusedTextColor = Color.White,
              unfocusedTextColor = Color.White
            ),
            modifier = Modifier
              .fillMaxWidth()
              .height(50.dp)
          )

          // Batch Filter Chips
          if (batchList.size > 1) {
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
              horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
              batchList.forEach { batchName ->
                val isSelected = selectedBatch == batchName
                Surface(
                  shape = RoundedCornerShape(8.dp),
                  color = if (isSelected) Color(0xFF6366F1) else Color(0xFF111C2E),
                  border = BorderStroke(1.dp, if (isSelected) Color(0xFF6366F1) else Color(0xFF1E2D42)),
                  modifier = Modifier.clickable { selectedBatch = batchName }
                ) {
                  Text(
                    text = batchName,
                    fontSize = 11.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    color = if (isSelected) Color.White else Color(0xFFCBD5E1),
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                  )
                }
              }
            }
          }
        }

        HorizontalDivider(color = Color(0xFF1E2D42))

        // Student List Content
        LazyColumn(
          modifier = Modifier
            .weight(1f)
            .fillMaxWidth(),
          contentPadding = PaddingValues(14.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          items(filteredStudents, key = { it.id }) { student ->
            val fee = feeMap[student.name]
            val feeStatus = fee?.paymentStatus ?: "Paid"

            Surface(
              shape = RoundedCornerShape(12.dp),
              color = Color(0xFF111C2E),
              border = BorderStroke(1.dp, Color(0xFF24334A)),
              modifier = Modifier
                .fillMaxWidth()
                .clickable { onStudentClick(student) }
            ) {
              Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
              ) {
                // Avatar
                Box(
                  modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF6366F1).copy(alpha = 0.2f))
                    .border(1.dp, Color(0xFF818CF8), CircleShape),
                  contentAlignment = Alignment.Center
                ) {
                  Text(
                    text = student.name.take(1).uppercase(),
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = Color(0xFF818CF8)
                  )
                }

                // Details
                Column(modifier = Modifier.weight(1f)) {
                  Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                  ) {
                    Text(
                      text = student.name,
                      fontWeight = FontWeight.Bold,
                      fontSize = 13.sp,
                      color = Color.White,
                      maxLines = 1,
                      overflow = TextOverflow.Ellipsis
                    )
                    Surface(
                      shape = RoundedCornerShape(4.dp),
                      color = when (feeStatus) {
                        "Overdue" -> Color(0xFFEF4444).copy(alpha = 0.15f)
                        "Pending" -> Color(0xFFF59E0B).copy(alpha = 0.15f)
                        else -> Color(0xFF10B981).copy(alpha = 0.15f)
                      }
                    ) {
                      Text(
                        text = feeStatus,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = when (feeStatus) {
                          "Overdue" -> Color(0xFFF87171)
                          "Pending" -> Color(0xFFFBBF24)
                          else -> Color(0xFF4ADE80)
                        },
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                      )
                    }
                  }
                  Spacer(modifier = Modifier.height(2.dp))
                  Text(
                    text = "Roll: ${student.id} • ${student.batch.ifBlank { "General" }}",
                    fontSize = 11.sp,
                    color = Color(0xFF94A3B8),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                  )
                  Text(
                    text = "Attendance: ${student.attendancePercent}% • Contact: ${student.mobile.ifBlank { student.parentContact }}",
                    fontSize = 10.sp,
                    color = Color(0xFF64748B),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                  )
                }

                // Call / WhatsApp
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                  IconButton(
                    onClick = {
                      openWhatsApp(context, student.mobile.ifBlank { student.parentContact }, "Hello ${student.name}, message from Fadat Science Institute Admin.")
                    },
                    modifier = Modifier
                      .size(32.dp)
                      .clip(RoundedCornerShape(8.dp))
                      .background(Color(0xFF22C55E).copy(alpha = 0.15f))
                  ) {
                    Icon(Icons.Default.Chat, contentDescription = "WhatsApp", tint = Color(0xFF4ADE80), modifier = Modifier.size(16.dp))
                  }
                  IconButton(
                    onClick = { openDialer(context, student.mobile.ifBlank { student.parentContact }) },
                    modifier = Modifier
                      .size(32.dp)
                      .clip(RoundedCornerShape(8.dp))
                      .background(Color(0xFF0284C7).copy(alpha = 0.15f))
                  ) {
                    Icon(Icons.Default.Call, contentDescription = "Call", tint = Color(0xFF38BDF8), modifier = Modifier.size(16.dp))
                  }
                }
              }
            }
          }
        }

        HorizontalDivider(color = Color(0xFF1E2D42))

        // Footer
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF111C2E))
            .padding(horizontal = 14.dp, vertical = 10.dp),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          TextButton(onClick = onDismiss) {
            Text("Close", color = Color(0xFF94A3B8), fontSize = 12.sp)
          }

          Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
              onClick = {
                onDismiss()
                onOpenDirectoryClick()
              },
              shape = RoundedCornerShape(10.dp),
              border = BorderStroke(1.dp, Color(0xFF38BDF8)),
              contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
            ) {
              Text("Student Tab", fontSize = 11.sp, color = Color(0xFF38BDF8))
            }

            Button(
              onClick = {
                onDismiss()
                onAdmitClick()
              },
              colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6366F1)),
              shape = RoundedCornerShape(10.dp),
              contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
            ) {
              Icon(Icons.Default.PersonAdd, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
              Spacer(modifier = Modifier.width(6.dp))
              Text("New Admission", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }
          }
        }
      }
    }
  }
}

/**
 * 4. Active Batches List Dialog
 * Opened when user taps "Total Batches" on the Dashboard.
 */
@Composable
fun ActiveBatchesListDialog(
  batches: List<Batch>,
  onDismiss: () -> Unit,
  onSetupClick: () -> Unit,
  onTimetableClick: () -> Unit,
  onTakeAttendanceClick: () -> Unit = {}
) {
  val students by AppRepository.students.collectAsState()

  Dialog(
    onDismissRequest = onDismiss,
    properties = DialogProperties(usePlatformDefaultWidth = false)
  ) {
    Surface(
      modifier = Modifier
        .fillMaxWidth(0.95f)
        .fillMaxHeight(0.85f),
      shape = RoundedCornerShape(20.dp),
      color = Color(0xFF0D1526),
      border = BorderStroke(1.dp, Color(0xFF1E2D42))
    ) {
      Column(modifier = Modifier.fillMaxSize()) {
        // Header
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF111C2E))
            .padding(horizontal = 16.dp, vertical = 14.dp),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
          ) {
            Box(
              modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF0284C7).copy(alpha = 0.18f)),
              contentAlignment = Alignment.Center
            ) {
              Icon(
                imageVector = Icons.Outlined.School,
                contentDescription = null,
                tint = Color(0xFF38BDF8),
                modifier = Modifier.size(22.dp)
              )
            }
            Column {
              Text(
                text = "Active Batches (${batches.size})",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = Color.White
              )
              Text(
                text = "Total ${students.size} Enrolled • Live Roster",
                fontSize = 11.sp,
                color = Color(0xFF38BDF8)
              )
            }
          }
          IconButton(
            onClick = onDismiss,
            modifier = Modifier
              .size(32.dp)
              .background(Color(0xFF1E293B), CircleShape)
          ) {
            Icon(Icons.Default.Close, contentDescription = "Close", tint = Color(0xFF94A3B8), modifier = Modifier.size(16.dp))
          }
        }

        HorizontalDivider(color = Color(0xFF1E2D42))

        // Batches List
        LazyColumn(
          modifier = Modifier
            .weight(1f)
            .fillMaxWidth(),
          contentPadding = PaddingValues(14.dp),
          verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
          items(batches, key = { it.id }) { batch ->
            val liveStudentCount = remember(students, batch.name) {
              students.count { it.batch.equals(batch.name, ignoreCase = true) }
            }
            Surface(
              shape = RoundedCornerShape(14.dp),
              color = Color(0xFF111C2E),
              border = BorderStroke(1.dp, Color(0xFF24334A)),
              modifier = Modifier.fillMaxWidth()
            ) {
              Column(modifier = Modifier.padding(14.dp)) {
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.SpaceBetween,
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(
                      modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF0284C7).copy(alpha = 0.2f)),
                      contentAlignment = Alignment.Center
                    ) {
                      Icon(Icons.Default.Class, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(18.dp))
                    }
                    Column {
                      Text(batch.name, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color.White)
                      Text("Timing: ${batch.schedule}", fontSize = 11.sp, color = Color(0xFF94A3B8))
                    }
                  }

                  Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = Color(0xFF0284C7).copy(alpha = 0.15f)
                  ) {
                    Text(
                      text = "$liveStudentCount Students",
                      fontSize = 11.sp,
                      fontWeight = FontWeight.Bold,
                      color = Color(0xFF38BDF8),
                      modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                  }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.SpaceBetween,
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  Text(
                    text = "Faculty: ${batch.teacherName}",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFFCBD5E1)
                  )

                  Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Button(
                      onClick = {
                        onDismiss()
                        onTakeAttendanceClick()
                      },
                      shape = RoundedCornerShape(8.dp),
                      colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                      contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                      modifier = Modifier.height(30.dp)
                    ) {
                      Text("Attendance", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                    }

                    OutlinedButton(
                      onClick = {
                        onDismiss()
                        onTimetableClick()
                      },
                      shape = RoundedCornerShape(8.dp),
                      border = BorderStroke(1.dp, Color(0xFF38BDF8)),
                      contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                      modifier = Modifier.height(30.dp)
                    ) {
                      Text("Schedule", fontSize = 11.sp, color = Color(0xFF38BDF8))
                    }
                  }
                }
              }
            }
          }
        }

        HorizontalDivider(color = Color(0xFF1E2D42))

        // Footer
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF111C2E))
            .padding(horizontal = 14.dp, vertical = 10.dp),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          TextButton(onClick = onDismiss) {
            Text("Close", color = Color(0xFF94A3B8), fontSize = 12.sp)
          }

          Button(
            onClick = {
              onDismiss()
              onSetupClick()
            },
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
            shape = RoundedCornerShape(10.dp),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
          ) {
            Icon(Icons.Default.Settings, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Academic Setup & Batches", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
          }
        }
      }
    }
  }
}

/**
 * 5. Low Attendance Alerts Dialog (<75%)
 * Opened when user taps the Warning tile in "Attention Required".
 */
@Composable
fun LowAttendanceAlertsDialog(
  students: List<Student>,
  onDismiss: () -> Unit,
  onTakeAttendanceClick: () -> Unit
) {
  val context = LocalContext.current
  var warningSentAll by remember { mutableStateOf(false) }

  Dialog(
    onDismissRequest = onDismiss,
    properties = DialogProperties(usePlatformDefaultWidth = false)
  ) {
    Surface(
      modifier = Modifier
        .fillMaxWidth(0.95f)
        .fillMaxHeight(0.85f),
      shape = RoundedCornerShape(20.dp),
      color = Color(0xFF0D1526),
      border = BorderStroke(1.dp, Color(0xFF1E2D42))
    ) {
      Column(modifier = Modifier.fillMaxSize()) {
        // Header
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF111C2E))
            .padding(horizontal = 16.dp, vertical = 14.dp),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
          ) {
            Box(
              modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFFF59E0B).copy(alpha = 0.18f)),
              contentAlignment = Alignment.Center
            ) {
              Icon(
                imageVector = Icons.Outlined.PersonSearch,
                contentDescription = null,
                tint = Color(0xFFFBBF24),
                modifier = Modifier.size(22.dp)
              )
            }
            Column {
              Text(
                text = "Low Attendance Alerts (${students.size})",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = Color.White
              )
              Text(
                text = "Below 75% Institutional Threshold",
                fontSize = 11.sp,
                color = Color(0xFF94A3B8)
              )
            }
          }
          IconButton(
            onClick = onDismiss,
            modifier = Modifier
              .size(32.dp)
              .background(Color(0xFF1E293B), CircleShape)
          ) {
            Icon(Icons.Default.Close, contentDescription = "Close", tint = Color(0xFF94A3B8), modifier = Modifier.size(16.dp))
          }
        }

        HorizontalDivider(color = Color(0xFF1E2D42))

        // Warning Alert Banner
        Surface(
          color = if (warningSentAll) Color(0xFF064E3B) else Color(0xFF2A1C0A),
          border = BorderStroke(1.dp, if (warningSentAll) Color(0xFF059669) else Color(0xFFF59E0B).copy(alpha = 0.3f)),
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 8.dp),
          shape = RoundedCornerShape(10.dp)
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(8.dp),
              modifier = Modifier.weight(1f)
            ) {
              Icon(
                imageVector = if (warningSentAll) Icons.Default.CheckCircle else Icons.Default.Warning,
                contentDescription = null,
                tint = if (warningSentAll) Color(0xFF4ADE80) else Color(0xFFFBBF24),
                modifier = Modifier.size(18.dp)
              )
              Text(
                text = if (warningSentAll) "✓ Warnings dispatched to all parents!" else "Mandatory 75% required for examination eligibility.",
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (warningSentAll) Color(0xFF4ADE80) else Color.White
              )
            }
            if (!warningSentAll && students.isNotEmpty()) {
              Button(
                onClick = {
                  warningSentAll = true
                  students.forEach { s ->
                    AppRepository.addNotification(
                      NotificationItem(
                        id = "WARN_${System.currentTimeMillis()}_${s.id}",
                        title = "Attendance Warning: ${s.name}",
                        message = "Urgent: ${s.name}'s attendance is currently ${s.attendancePercent}%, below the 75% requirement. Please visit tuition office.",
                        time = AppRepository.getCurrentTimeStr(),
                        type = "Absence",
                        recipientRole = "Parent"
                      )
                    )
                  }
                  Toast.makeText(context, "Attendance warning notifications sent to all parents!", Toast.LENGTH_SHORT).show()
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF59E0B)),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                modifier = Modifier.height(30.dp)
              ) {
                Text("Warn All", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.Black)
              }
            }
          }
        }

        // Student List
        LazyColumn(
          modifier = Modifier
            .weight(1f)
            .fillMaxWidth(),
          contentPadding = PaddingValues(14.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          items(students, key = { it.id }) { student ->
            Surface(
              shape = RoundedCornerShape(12.dp),
              color = Color(0xFF111C2E),
              border = BorderStroke(1.dp, Color(0xFF24334A)),
              modifier = Modifier.fillMaxWidth()
            ) {
              Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
              ) {
                // Circular percentage badge
                Box(
                  modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFEF4444).copy(alpha = 0.15f))
                    .border(1.5.dp, Color(0xFFEF4444), CircleShape),
                  contentAlignment = Alignment.Center
                ) {
                  Text(
                    text = "${student.attendancePercent}%",
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 11.sp,
                    color = Color(0xFFF87171)
                  )
                }

                // Details
                Column(modifier = Modifier.weight(1f)) {
                  Text(
                    text = student.name,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = Color.White
                  )
                  Text(
                    text = "Batch: ${student.batch.ifBlank { "General" }} • Roll: ${student.id}",
                    fontSize = 11.sp,
                    color = Color(0xFF94A3B8)
                  )
                  Text(
                    text = "Parent: ${student.parentName.ifBlank { "Guardian" }} (${student.parentContact.ifBlank { student.mobile }})",
                    fontSize = 10.sp,
                    color = Color(0xFF64748B)
                  )
                }

                // WhatsApp Warning & Call
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                  IconButton(
                    onClick = {
                      val phone = student.parentContact.ifBlank { student.mobile }
                      openWhatsApp(
                        context,
                        phone,
                        "Attendance Notice: ${student.name}'s attendance has fallen to ${student.attendancePercent}% at Fadat Science Institute. Please ensure regular attendance to maintain board exam eligibility."
                      )
                    },
                    modifier = Modifier
                      .size(32.dp)
                      .clip(RoundedCornerShape(8.dp))
                      .background(Color(0xFF22C55E).copy(alpha = 0.15f))
                  ) {
                    Icon(Icons.Default.Chat, contentDescription = "WhatsApp", tint = Color(0xFF4ADE80), modifier = Modifier.size(16.dp))
                  }
                  IconButton(
                    onClick = {
                      val phone = student.parentContact.ifBlank { student.mobile }
                      openDialer(context, phone)
                    },
                    modifier = Modifier
                      .size(32.dp)
                      .clip(RoundedCornerShape(8.dp))
                      .background(Color(0xFF0284C7).copy(alpha = 0.15f))
                  ) {
                    Icon(Icons.Default.Call, contentDescription = "Call", tint = Color(0xFF38BDF8), modifier = Modifier.size(16.dp))
                  }
                }
              }
            }
          }
        }

        HorizontalDivider(color = Color(0xFF1E2D42))

        // Footer
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF111C2E))
            .padding(horizontal = 14.dp, vertical = 10.dp),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          TextButton(onClick = onDismiss) {
            Text("Close", color = Color(0xFF94A3B8), fontSize = 12.sp)
          }

          Button(
            onClick = {
              onDismiss()
              onTakeAttendanceClick()
            },
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
            shape = RoundedCornerShape(10.dp),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
          ) {
            Icon(Icons.Default.HowToReg, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Check Attendance Register", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
          }
        }
      }
    }
  }
}

/**
 * 6. Class Session Details Dialog
 * Opened when user taps any scheduled class item in "Today's Schedule".
 */
@Composable
fun ClassSessionDetailDialog(
  timeSlot: String,
  subject: String,
  batchName: String,
  room: String,
  teacher: String,
  batchStudents: List<Student>,
  onDismiss: () -> Unit,
  onTakeAttendance: () -> Unit,
  onOpenTimetable: () -> Unit
) {
  val context = LocalContext.current

  Dialog(
    onDismissRequest = onDismiss,
    properties = DialogProperties(usePlatformDefaultWidth = false)
  ) {
    Surface(
      modifier = Modifier
        .fillMaxWidth(0.95f)
        .fillMaxHeight(0.85f),
      shape = RoundedCornerShape(20.dp),
      color = Color(0xFF0D1526),
      border = BorderStroke(1.dp, Color(0xFF1E2D42))
    ) {
      Column(modifier = Modifier.fillMaxSize()) {
        // Header
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF111C2E))
            .padding(horizontal = 16.dp, vertical = 14.dp),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
          ) {
            Box(
              modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF38BDF8).copy(alpha = 0.18f)),
              contentAlignment = Alignment.Center
            ) {
              Icon(
                imageVector = Icons.Outlined.Schedule,
                contentDescription = null,
                tint = Color(0xFF38BDF8),
                modifier = Modifier.size(22.dp)
              )
            }
            Column {
              Text(
                text = subject,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = Color.White
              )
              Text(
                text = "$batchName • $room",
                fontSize = 11.sp,
                color = Color(0xFF94A3B8)
              )
            }
          }
          IconButton(
            onClick = onDismiss,
            modifier = Modifier
              .size(32.dp)
              .background(Color(0xFF1E293B), CircleShape)
          ) {
            Icon(Icons.Default.Close, contentDescription = "Close", tint = Color(0xFF94A3B8), modifier = Modifier.size(16.dp))
          }
        }

        HorizontalDivider(color = Color(0xFF1E2D42))

        // Key Information Grid
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .padding(14.dp),
          verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            // Timing
            Surface(
              shape = RoundedCornerShape(10.dp),
              color = Color(0xFF111C2E),
              border = BorderStroke(1.dp, Color(0xFF24334A)),
              modifier = Modifier.weight(1f)
            ) {
              Column(modifier = Modifier.padding(10.dp)) {
                Text("TIME SLOT", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color(0xFF94A3B8))
                Spacer(modifier = Modifier.height(2.dp))
                Text(timeSlot, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF38BDF8))
              }
            }

            // Room
            Surface(
              shape = RoundedCornerShape(10.dp),
              color = Color(0xFF111C2E),
              border = BorderStroke(1.dp, Color(0xFF24334A)),
              modifier = Modifier.weight(1f)
            ) {
              Column(modifier = Modifier.padding(10.dp)) {
                Text("CLASSROOM", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color(0xFF94A3B8))
                Spacer(modifier = Modifier.height(2.dp))
                Text(room, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF4ADE80))
              }
            }
          }

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            // Faculty
            Surface(
              shape = RoundedCornerShape(10.dp),
              color = Color(0xFF111C2E),
              border = BorderStroke(1.dp, Color(0xFF24334A)),
              modifier = Modifier.weight(1f)
            ) {
              Column(modifier = Modifier.padding(10.dp)) {
                Text("MENTOR FACULTY", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color(0xFF94A3B8))
                Spacer(modifier = Modifier.height(2.dp))
                Text(teacher, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
              }
            }

            // Enrolled
            Surface(
              shape = RoundedCornerShape(10.dp),
              color = Color(0xFF111C2E),
              border = BorderStroke(1.dp, Color(0xFF24334A)),
              modifier = Modifier.weight(1f)
            ) {
              Column(modifier = Modifier.padding(10.dp)) {
                Text("BATCH ROSTER", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color(0xFF94A3B8))
                Spacer(modifier = Modifier.height(2.dp))
                Text("${batchStudents.size} Learners", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFFC084FC))
              }
            }
          }
        }

        HorizontalDivider(color = Color(0xFF1E2D42))

        // Student Roster for this batch
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 8.dp),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Text("Enrolled Students in $batchName", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color(0xFFCBD5E1))
          Text("${batchStudents.size} enrolled", fontSize = 11.sp, color = Color(0xFF64748B))
        }

        LazyColumn(
          modifier = Modifier
            .weight(1f)
            .fillMaxWidth(),
          contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
          verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
          items(batchStudents, key = { it.id }) { student ->
            Surface(
              shape = RoundedCornerShape(8.dp),
              color = Color(0xFF111C2E),
              border = BorderStroke(1.dp, Color(0xFF1E2D42)),
              modifier = Modifier.fillMaxWidth()
            ) {
              Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
              ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                  Box(
                    modifier = Modifier
                      .size(28.dp)
                      .clip(CircleShape)
                      .background(Color(0xFF38BDF8).copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                  ) {
                    Text(
                      text = student.name.take(1).uppercase(),
                      fontWeight = FontWeight.Bold,
                      fontSize = 12.sp,
                      color = Color(0xFF38BDF8)
                    )
                  }
                  Column {
                    Text(student.name, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = Color.White)
                    Text("Roll: ${student.id}", fontSize = 10.sp, color = Color(0xFF94A3B8))
                  }
                }

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                  Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = if (student.attendancePercent >= 75) Color(0xFF10B981).copy(alpha = 0.15f) else Color(0xFFEF4444).copy(alpha = 0.15f)
                  ) {
                    Text(
                      text = "${student.attendancePercent}%",
                      fontSize = 10.sp,
                      fontWeight = FontWeight.Bold,
                      color = if (student.attendancePercent >= 75) Color(0xFF4ADE80) else Color(0xFFF87171),
                      modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                    )
                  }
                }
              }
            }
          }
        }

        HorizontalDivider(color = Color(0xFF1E2D42))

        // Footer Actions
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF111C2E))
            .padding(horizontal = 14.dp, vertical = 10.dp),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          TextButton(onClick = onDismiss) {
            Text("Close", color = Color(0xFF94A3B8), fontSize = 12.sp)
          }

          Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
              onClick = {
                onDismiss()
                onOpenTimetable()
              },
              shape = RoundedCornerShape(10.dp),
              border = BorderStroke(1.dp, Color(0xFF38BDF8)),
              contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
            ) {
              Text("Timetable", fontSize = 11.sp, color = Color(0xFF38BDF8))
            }

            Button(
              onClick = {
                onDismiss()
                onTakeAttendance()
              },
              colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
              shape = RoundedCornerShape(10.dp),
              contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
            ) {
              Icon(Icons.Default.HowToReg, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
              Spacer(modifier = Modifier.width(6.dp))
              Text("Take Attendance", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.Black)
            }
          }
        }
      }
    }
  }
}
