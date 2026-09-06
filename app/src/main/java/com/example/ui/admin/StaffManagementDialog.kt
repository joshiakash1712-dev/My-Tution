package com.example.ui.admin

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.example.data.AppRepository
import com.example.data.Teacher
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.random.Random

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddStaffDialog(
  onDismiss: () -> Unit,
  onSaveTeacher: (Teacher) -> Unit
) {
  val context = LocalContext.current
  val clipboardManager = LocalClipboardManager.current
  val instituteConfig by AppRepository.instituteConfig.collectAsState()
  val batches by AppRepository.batches.collectAsState()

  var staffPhotoUri by remember { mutableStateOf<Uri?>(null) }
  val staffPhotoPicker = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.PickVisualMedia(),
    onResult = { uri ->
      if (uri != null) {
        staffPhotoUri = uri
        Toast.makeText(context, "Staff photo selected from storage", Toast.LENGTH_SHORT).show()
      }
    }
  )

  var name by remember { mutableStateOf("") }
  var gender by remember { mutableStateOf("Male") }
  var dob by remember { mutableStateOf("12/04/1988") }
  var showDatePicker by remember { mutableStateOf(false) }

  var phone by remember { mutableStateOf("") }
  var email by remember { mutableStateOf("") }
  var designation by remember { mutableStateOf("Senior Faculty") }
  var qualification by remember { mutableStateOf("M.Sc. / B.Ed.") }
  var experience by remember { mutableStateOf("5 Years") }

  // Available subjects from institute config
  val availableSubjects = remember(instituteConfig) {
    instituteConfig.gradeSubjects.values.flatten().distinct().ifEmpty {
      listOf("Physics", "Chemistry", "Mathematics", "Biology", "English", "Science")
    }
  }
  var selectedSubject by remember { mutableStateOf(availableSubjects.firstOrNull() ?: "Physics") }
  var subjectExpanded by remember { mutableStateOf(false) }

  var selectedBatches by remember { mutableStateOf(batches.take(2).map { it.name }.toSet()) }
  var salary by remember { mutableStateOf("75000") }

  val generatedStaffCode = remember {
    (100000 + Random.nextInt(900000)).toString()
  }

  if (showDatePicker) {
    val datePickerState = rememberDatePickerState()
    DatePickerDialog(
      onDismissRequest = { showDatePicker = false },
      confirmButton = {
        TextButton(
          onClick = {
            datePickerState.selectedDateMillis?.let { millis ->
              val formatter = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
              dob = formatter.format(Date(millis))
            }
            showDatePicker = false
          }
        ) { Text("Set DOB", fontWeight = FontWeight.Bold) }
      },
      dismissButton = {
        TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
      }
    ) {
      DatePicker(state = datePickerState)
    }
  }

  Dialog(
    onDismissRequest = onDismiss,
    properties = DialogProperties(usePlatformDefaultWidth = false)
  ) {
    Surface(
      shape = RoundedCornerShape(24.dp),
      color = MaterialTheme.colorScheme.surface,
      border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
      modifier = Modifier
        .fillMaxWidth(0.96f)
        .fillMaxHeight(0.92f)
    ) {
      Column(
        modifier = Modifier
          .fillMaxSize()
          .padding(18.dp)
      ) {
        // Header
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
              modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(Color(0xFF0061A4).copy(alpha = 0.12f)),
              contentAlignment = Alignment.Center
            ) {
              Icon(Icons.Default.PersonAdd, contentDescription = null, tint = Color(0xFF0061A4))
            }
            Spacer(Modifier.width(10.dp))
            Column {
              Text("Onboard New Faculty / Staff", fontWeight = FontWeight.Bold, fontSize = 16.sp)
              Text("Staff registration & secure role activation code", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
          }
          IconButton(onClick = onDismiss) {
            Icon(Icons.Default.Close, contentDescription = "Close")
          }
        }

        Spacer(Modifier.height(14.dp))

        Column(
          modifier = Modifier
            .weight(1f)
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
          verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
          // (1) Photo & Personal Info Row
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            // Photo Picker Box
            Card(
              shape = RoundedCornerShape(12.dp),
              colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
              border = BorderStroke(1.dp, if (staffPhotoUri != null) Color(0xFF0061A4) else Color(0xFFCBD5E1)),
              modifier = Modifier
                .width(100.dp)
                .height(118.dp)
                .clickable {
                  staffPhotoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }
            ) {
              if (staffPhotoUri != null) {
                Box(modifier = Modifier.fillMaxSize()) {
                  AsyncImage(
                    model = staffPhotoUri,
                    contentDescription = "Faculty Photo",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                  )
                  Surface(
                    color = Color.Black.copy(alpha = 0.6f),
                    modifier = Modifier
                      .fillMaxWidth()
                      .align(Alignment.BottomCenter)
                  ) {
                    Row(
                      modifier = Modifier.padding(vertical = 3.dp),
                      horizontalArrangement = Arrangement.Center,
                      verticalAlignment = Alignment.CenterVertically
                    ) {
                      Icon(Icons.Default.Edit, contentDescription = null, tint = Color.White, modifier = Modifier.size(10.dp))
                      Spacer(Modifier.width(2.dp))
                      Text("Change", fontSize = 9.sp, color = Color.White, fontWeight = FontWeight.Bold)
                    }
                  }
                }
              } else {
                Column(
                  modifier = Modifier
                    .fillMaxSize()
                    .padding(6.dp),
                  horizontalAlignment = Alignment.CenterHorizontally,
                  verticalArrangement = Arrangement.Center
                ) {
                  Box(
                    modifier = Modifier
                      .size(40.dp)
                      .clip(CircleShape)
                      .background(Color(0xFF0061A4).copy(alpha = 0.1f)),
                    contentAlignment = Alignment.Center
                  ) {
                    Icon(Icons.Default.AddAPhoto, contentDescription = "Add Staff Photo", tint = Color(0xFF0061A4), modifier = Modifier.size(22.dp))
                  }
                  Spacer(Modifier.height(4.dp))
                  Text("Staff Photo", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F172A))
                  Text("From Storage", fontSize = 8.sp, color = Color(0xFF64748B), textAlign = TextAlign.Center)
                }
              }
            }

            Column(
              modifier = Modifier.weight(1f),
              verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
              OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Teacher / Staff Full Name") },
                placeholder = { Text("e.g. Prof. Sandeep Shinde") },
                leadingIcon = { Icon(Icons.Default.Person, contentDescription = null, tint = Color(0xFF0061A4)) },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
              )

              OutlinedTextField(
                value = designation,
                onValueChange = { designation = it },
                label = { Text("Designation / Role") },
                placeholder = { Text("e.g. Senior Faculty / HOD") },
                leadingIcon = { Icon(Icons.Default.Badge, contentDescription = null, tint = Color(0xFF0061A4)) },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
              )
            }
          }

          Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(modifier = Modifier.weight(1f)) {
              Text("Gender", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
              Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf("Male", "Female").forEach { g ->
                  FilterChip(
                    selected = gender == g,
                    onClick = { gender = g },
                    label = { Text(g, fontSize = 11.sp) }
                  )
                }
              }
            }

            OutlinedTextField(
              value = dob,
              onValueChange = {},
              readOnly = true,
              label = { Text("Date of Birth") },
              trailingIcon = {
                IconButton(onClick = { showDatePicker = true }) {
                  Icon(Icons.Default.CalendarMonth, contentDescription = null, tint = Color(0xFF0061A4))
                }
              },
              shape = RoundedCornerShape(12.dp),
              modifier = Modifier.weight(1f).clickable { showDatePicker = true }
            )
          }

          Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(
              value = phone,
              onValueChange = { phone = it },
              label = { Text("Phone / WhatsApp") },
              placeholder = { Text("9822100001") },
              leadingIcon = { Text("+91", fontWeight = FontWeight.Bold, color = Color(0xFF64748B), modifier = Modifier.padding(start = 12.dp)) },
              singleLine = true,
              shape = RoundedCornerShape(12.dp),
              modifier = Modifier.weight(1f)
            )

            OutlinedTextField(
              value = email,
              onValueChange = { email = it },
              label = { Text("Email (For App Login)") },
              placeholder = { Text("shinde.faculty@radal.com") },
              leadingIcon = { Icon(Icons.Default.Email, contentDescription = null, tint = Color(0xFF0061A4)) },
              singleLine = true,
              shape = RoundedCornerShape(12.dp),
              modifier = Modifier.weight(1.2f)
            )
          }

          // (2) Academic Specialization & Batches
          Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ExposedDropdownMenuBox(
              expanded = subjectExpanded,
              onExpandedChange = { subjectExpanded = it },
              modifier = Modifier.weight(1.2f)
            ) {
              OutlinedTextField(
                value = selectedSubject,
                onValueChange = {},
                readOnly = true,
                label = { Text("Specialized Subject") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = subjectExpanded) },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().menuAnchor()
              )
              ExposedDropdownMenu(
                expanded = subjectExpanded,
                onDismissRequest = { subjectExpanded = false }
              ) {
                availableSubjects.forEach { sub ->
                  DropdownMenuItem(
                    text = { Text(sub) },
                    onClick = {
                      selectedSubject = sub
                      subjectExpanded = false
                    }
                  )
                }
              }
            }

            OutlinedTextField(
              value = experience,
              onValueChange = { experience = it },
              label = { Text("Experience") },
              placeholder = { Text("5 Years") },
              leadingIcon = { Icon(Icons.Default.WorkHistory, contentDescription = null, tint = Color(0xFF0061A4)) },
              singleLine = true,
              shape = RoundedCornerShape(12.dp),
              modifier = Modifier.weight(0.8f)
            )
          }

          Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(
              value = qualification,
              onValueChange = { qualification = it },
              label = { Text("Qualification") },
              placeholder = { Text("M.Sc. Physics (IIT Bombay)") },
              singleLine = true,
              shape = RoundedCornerShape(12.dp),
              modifier = Modifier.weight(1f)
            )

            OutlinedTextField(
              value = salary,
              onValueChange = { salary = it },
              label = { Text("Monthly Salary (₹)") },
              placeholder = { Text("75000") },
              leadingIcon = { Text("₹", fontWeight = FontWeight.Bold, color = Color(0xFF059669), modifier = Modifier.padding(start = 12.dp)) },
              singleLine = true,
              shape = RoundedCornerShape(12.dp),
              modifier = Modifier.weight(1f)
            )
          }

          // Batch assignment chips
          Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Assign Batches:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
              items(batches) { b ->
                val isSel = selectedBatches.contains(b.name)
                FilterChip(
                  selected = isSel,
                  onClick = {
                    selectedBatches = if (isSel) selectedBatches - b.name else selectedBatches + b.name
                  },
                  label = { Text(b.name, fontSize = 11.sp) }
                )
              }
            }
          }

          // (3) Generated Activation Code
          Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFFEF3C7)),
            border = BorderStroke(1.dp, Color(0xFFFDE68A))
          ) {
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Column(modifier = Modifier.weight(1f)) {
                Text("Teacher App Activation Code", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF92400E))
                Text("Faculty will enter their email & this code to set their personal password", fontSize = 10.sp, color = Color(0xFFB45309))
                Spacer(Modifier.height(4.dp))
                Text(
                  text = "${generatedStaffCode.take(3)} ${generatedStaffCode.takeLast(3)}",
                  fontSize = 20.sp,
                  fontWeight = FontWeight.ExtraBold,
                  letterSpacing = 2.sp,
                  color = Color(0xFF78350F)
                )
              }

              IconButton(
                onClick = {
                  clipboardManager.setText(AnnotatedString(generatedStaffCode))
                  Toast.makeText(context, "Code Copied: $generatedStaffCode", Toast.LENGTH_SHORT).show()
                }
              ) {
                Icon(Icons.Default.ContentCopy, contentDescription = "Copy Code", tint = Color(0xFF92400E))
              }
            }
          }
        }

        Spacer(Modifier.height(12.dp))

        // Action Buttons
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          OutlinedButton(onClick = onDismiss, shape = RoundedCornerShape(12.dp)) {
            Text("Cancel")
          }

          Button(
            onClick = {
              if (name.isBlank()) {
                Toast.makeText(context, "Please enter teacher name", Toast.LENGTH_SHORT).show()
                return@Button
              }

              val teacherId = "FAC_${System.currentTimeMillis().toString().takeLast(3)}"
              val teacher = Teacher(
                id = teacherId,
                name = name.trim(),
                photo = staffPhotoUri?.toString() ?: "",
                subject = selectedSubject,
                contact = if (phone.isNotBlank()) "+91 ${phone.trim()}" else "+91 9822100001",
                qualification = qualification.ifBlank { "Post Graduate" },
                experience = experience.ifBlank { "5 Years" },
                assignedBatches = selectedBatches.toList().ifEmpty { listOf("11th - Science (B)") },
                classesTaken = 0,
                attendancePercent = 100,
                feedbackRating = 5.0f,
                salary = salary.toIntOrNull() ?: 75000,
                incentives = 5000,
                deductions = 0,
                status = "Active",
                email = if (email.isNotBlank()) email.trim() else "${name.lowercase().replace(" ", ".")}@radal.com",
                gender = gender,
                dob = dob,
                joiningDate = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date()),
                activationCode = generatedStaffCode,
                isActivated = false
              )

              onSaveTeacher(teacher)
              Toast.makeText(context, "Faculty Member Added Successfully!", Toast.LENGTH_SHORT).show()
              onDismiss()
            },
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0061A4)),
            shape = RoundedCornerShape(12.dp)
          ) {
            Icon(Icons.Default.PersonAdd, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("Save & Onboard Staff", fontWeight = FontWeight.Bold)
          }
        }
      }
    }
  }
}
