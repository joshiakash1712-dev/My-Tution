package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import com.example.data.*
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun DigitalIdCardDialog(student: Student, onDismiss: () -> Unit) {
  AlertDialog(
    onDismissRequest = onDismiss,
    confirmButton = {
      Button(
        onClick = onDismiss,
        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0061A4))
      ) {
        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text("Save & Download ID")
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) { Text("Close", color = Color(0xFF74777F)) }
    },
    text = {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .clip(RoundedCornerShape(24.dp))
          .background(Color(0xFF001D36))
          .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
      ) {
        // Institute Header
        Text("MY TUITION INSTITUTE", color = Color(0xFFAAC7FF), fontWeight = FontWeight.Bold, fontSize = 14.sp, letterSpacing = 2.sp)
        Text("Student Identity Card", color = Color.White.copy(0.7f), fontSize = 11.sp)
        Spacer(Modifier.height(16.dp))

        // Student Avatar
        Box(
          modifier = Modifier
            .size(80.dp)
            .clip(CircleShape)
            .background(Color(0xFFD1E4FF))
            .border(3.dp, Color(0xFFAAC7FF), CircleShape),
          contentAlignment = Alignment.Center
        ) {
          if (student.photo.isNotBlank()) {
            AsyncImage(
              model = student.photo,
              contentDescription = student.name,
              modifier = Modifier.fillMaxSize(),
              contentScale = ContentScale.Crop
            )
          } else {
            Text(student.name.take(1), fontSize = 36.sp, fontWeight = FontWeight.Bold, color = Color(0xFF001D36))
          }
        }
        Spacer(Modifier.height(12.dp))

        Text(student.name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
        Surface(color = Color(0xFF0061A4), shape = RoundedCornerShape(8.dp), modifier = Modifier.padding(top = 4.dp)) {
          Text(student.id, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp))
        }

        HorizontalDivider(color = Color.White.copy(0.15f), modifier = Modifier.padding(vertical = 12.dp))

        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
          IdCardRow("Batch", student.batch)
          IdCardRow("Class & Stream", "${student.className} (${student.stream})")
          IdCardRow("Parent", "${student.parentName} (${student.parentContact})")
          IdCardRow("Emergency Ph", student.mobile)
        }

        Spacer(Modifier.height(16.dp))
        // Simulated QR
        Box(
          modifier = Modifier
            .size(100.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White)
            .padding(8.dp),
          contentAlignment = Alignment.Center
        ) {
          Icon(Icons.Default.QrCode2, contentDescription = "QR Code", modifier = Modifier.fillMaxSize(), tint = Color(0xFF001D36))
        }
        Text("Verified Digital Pass", color = Color(0xFFAAC7FF), fontSize = 9.sp, modifier = Modifier.padding(top = 4.dp))
      }
    },
    containerColor = MaterialTheme.colorScheme.surface,
    shape = RoundedCornerShape(28.dp)
  )
}

@Composable
private fun IdCardRow(label: String, valText: String) {
  Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
    Text(label, color = Color.White.copy(0.6f), fontSize = 12.sp)
    Text(valText, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium)
  }
}

@Composable
fun ReportCardDialog(student: Student, onDismiss: () -> Unit) {
  AlertDialog(
    onDismissRequest = onDismiss,
    confirmButton = {
      Button(
        onClick = onDismiss,
        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7))
      ) {
        Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text("Export PDF Report")
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) { Text("Close", color = Color(0xFF94A3B8)) }
    },
    title = {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Default.School, contentDescription = null, tint = Color(0xFF38BDF8))
        Spacer(Modifier.width(8.dp))
        Text("Official Report Card", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color.White)
      }
    },
    text = {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp)
      ) {
        Surface(color = Color(0xFF111C2E), border = BorderStroke(1.dp, Color(0xFF1E2D42)), shape = RoundedCornerShape(16.dp)) {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
          ) {
            Box(
              modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(Color(0xFF1E293B))
                .border(1.5.dp, Color(0xFF38BDF8), CircleShape),
              contentAlignment = Alignment.Center
            ) {
              if (student.photo.isNotBlank()) {
                AsyncImage(
                  model = student.photo,
                  contentDescription = student.name,
                  modifier = Modifier.fillMaxSize(),
                  contentScale = ContentScale.Crop
                )
              } else {
                Text(student.name.take(1), fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color(0xFF38BDF8))
              }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
              Text(student.name, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.White)
              Text("ID: ${student.id} • Batch: ${student.batch}", fontSize = 12.sp, color = Color(0xFF94A3B8))
              Text("Overall Attendance: ${student.attendancePercent}%", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF38BDF8))
            }
          }
        }

        Text("Academic Performance Summary", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = Color.White)
        
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          StatMiniCard("Overall Avg", "${student.overallAvg}%", Color(0xFF0284C7).copy(alpha = 0.2f), Color(0xFF38BDF8), Modifier.weight(1f))
          StatMiniCard("Batch Rank", "#${student.rank}", Color(0xFFA855F7).copy(alpha = 0.2f), Color(0xFFC084FC), Modifier.weight(1f))
        }

        Text("Recent Test Breakdown", fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Color(0xFF94A3B8))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          student.recentScores.forEach { (tName, score) ->
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFF111C2E))
                .border(1.dp, Color(0xFF1E2D42), RoundedCornerShape(10.dp))
                .padding(10.dp),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Text(tName, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Color.White)
              Text("$score / 100", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF38BDF8))
            }
          }
        }

        Surface(color = Color(0xFF064E3B), border = BorderStroke(1.dp, Color(0xFF059669)), shape = RoundedCornerShape(12.dp)) {
          Text(
            "Faculty Remarks: Excellent grasping power in Calculus. Needs consistent practice in Organic mechanisms.",
            fontSize = 12.sp,
            color = Color(0xFF4ADE80),
            modifier = Modifier.padding(12.dp)
          )
        }
      }
    },
    containerColor = Color(0xFF0D1526),
    shape = RoundedCornerShape(24.dp)
  )
}

@Composable
fun StatMiniCard(title: String, valStr: String, bg: Color, tx: Color, modifier: Modifier = Modifier) {
  Box(modifier = modifier.clip(RoundedCornerShape(16.dp)).background(bg).padding(12.dp)) {
    Column {
      Text(title, fontSize = 11.sp, color = tx.copy(0.7f))
      Text(valStr, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = tx)
    }
  }
}

@Composable
fun AddStudentDialog(onDismiss: () -> Unit, onSave: (Student) -> Unit) {
  var name by remember { mutableStateOf("") }
  var mobile by remember { mutableStateOf("") }
  var parentName by remember { mutableStateOf("") }
  var batch by remember { mutableStateOf("JEE Apex Morning") }
  var stream by remember { mutableStateOf("PCM") }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Admit New Student", fontWeight = FontWeight.Bold, color = Color.White) },
    text = {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp)
      ) {
        OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Student Full Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = mobile, onValueChange = { mobile = it }, label = { Text("Student Mobile") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = parentName, onValueChange = { parentName = it }, label = { Text("Parent / Guardian Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        
        Text("Assign Batch", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFFCBD5E1))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
          listOf("JEE Apex Morning", "NEET Zenith", "Foundation Target").forEach { b ->
            FilterChip(
              selected = batch == b,
              onClick = { batch = b },
              colors = FilterChipDefaults.filterChipColors(
                containerColor = Color(0xFF111C2E),
                labelColor = Color(0xFF94A3B8),
                selectedContainerColor = Color(0xFF0284C7),
                selectedLabelColor = Color.White
              ),
              border = FilterChipDefaults.filterChipBorder(
                enabled = true,
                selected = batch == b,
                borderColor = if (batch == b) Color(0xFF38BDF8) else Color(0xFF1E2D42)
              ),
              label = {
                Text(
                  text = b.substringBefore(" "),
                  fontSize = 10.sp,
                  maxLines = 1,
                  softWrap = false,
                  overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
              }
            )
          }
        }
      }
    },
    confirmButton = {
      Button(
        onClick = {
          if (name.isNotBlank()) {
            val s = Student(
              id = "STU${System.currentTimeMillis().toString().takeLast(4)}",
              name = name,
              photo = "",
              mobile = if (mobile.isBlank()) "+91 9800000000" else mobile,
              parentName = if (parentName.isBlank()) "Guardian" else parentName,
              parentContact = "+91 9800000001",
              email = "${name.lowercase().replace(" ", ".")}@institute.com",
              dob = "01/01/2008",
              gender = "Male",
              address = "Institute Hostel",
              school = "City Public School",
              className = "Class 12",
              batch = batch,
              stream = stream,
              admissionDate = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date()),
              status = "Active",
              attendancePercent = 100,
              overallAvg = 85,
              rank = 10,
              strongestSubject = "General Aptitude",
              weakestSubject = "Revision",
              recentScores = listOf("Entry Test" to 85)
            )
            onSave(s)
          }
        },
        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7))
      ) { Text("Admit Student") }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) { Text("Cancel", color = Color(0xFF94A3B8)) }
    },
    containerColor = Color(0xFF0D1526),
    shape = RoundedCornerShape(24.dp)
  )
}

@Composable
fun CreateUserAccountDialog(onDismiss: () -> Unit) {
  var fullName by remember { mutableStateOf("") }
  var email by remember { mutableStateOf("") }
  var password by remember { mutableStateOf("") }
  var selectedRole by remember { mutableStateOf(UserRole.STUDENT) }
  var errorMsg by remember { mutableStateOf<String?>(null) }
  var successMsg by remember { mutableStateOf<String?>(null) }
  var isLoading by remember { mutableStateOf(false) }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Create User Account (Admin Only)", fontWeight = FontWeight.Bold) },
    text = {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp)
      ) {
        Text(
          "Register secure authentication credentials directly into the database. Users cannot register themselves.",
          fontSize = 11.sp,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        OutlinedTextField(
          value = fullName,
          onValueChange = { fullName = it; errorMsg = null; successMsg = null },
          label = { Text("Full Name") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
          value = email,
          onValueChange = { email = it; errorMsg = null; successMsg = null },
          label = { Text("Email (Smart Identifier)") },
          placeholder = { Text("e.g. name@mytuition.com") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
          value = password,
          onValueChange = { password = it; errorMsg = null; successMsg = null },
          label = { Text("Password (Min 6 chars)") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth()
        )

        Text("Designate System Role", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
          listOf(UserRole.STUDENT, UserRole.TEACHER, UserRole.PARENT).forEach { r ->
            FilterChip(
              selected = selectedRole == r,
              onClick = { selectedRole = r },
              label = {
                Text(
                  text = r.name,
                  fontSize = 10.sp,
                  maxLines = 1,
                  softWrap = false,
                  overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
              }
            )
          }
        }

        if (successMsg != null) {
          Surface(color = Color(0xFF064E3B), border = BorderStroke(1.dp, Color(0xFF059669)), shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
            Text(successMsg ?: "", color = Color(0xFF4ADE80), fontSize = 11.sp, modifier = Modifier.padding(10.dp), fontWeight = FontWeight.SemiBold)
          }
        }

        if (errorMsg != null) {
          Surface(color = Color(0xFF450A0A), border = BorderStroke(1.dp, Color(0xFFEF4444)), shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
            Text(errorMsg ?: "", color = Color(0xFFF87171), fontSize = 11.sp, modifier = Modifier.padding(10.dp), fontWeight = FontWeight.SemiBold)
          }
        }
      }
    },
    confirmButton = {
      Button(
        onClick = {
          if (fullName.isBlank() || email.isBlank() || password.isBlank()) {
            errorMsg = "Please fill in all details"
            return@Button
          }
          if (password.length < 6) {
            errorMsg = "Password must be at least 6 characters"
            return@Button
          }
          isLoading = true
          FirebaseAuthService.signUp(
            email = email.trim(),
            password = password.trim(),
            fullName = fullName.trim(),
            role = selectedRole,
            onSuccess = {
              isLoading = false
              successMsg = "✅ Account created successfully for $email"
              // Add student/teacher into AppRepository lists automatically!
              if (selectedRole == UserRole.STUDENT) {
                val s = Student(
                  id = "STU${System.currentTimeMillis().toString().takeLast(4)}",
                  name = fullName,
                  photo = "",
                  mobile = "+91 9800000000",
                  parentName = "Guardian",
                  parentContact = "+91 9800000001",
                  email = email.trim().lowercase(),
                  dob = "01/01/2008",
                  gender = "Male",
                  address = "Institute Hostel",
                  school = "City Public School",
                  className = "Class 12",
                  batch = "JEE Apex Morning",
                  stream = "PCM",
                  admissionDate = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date()),
                  status = "Active",
                  attendancePercent = 100,
                  overallAvg = 85,
                  rank = 10,
                  strongestSubject = "General Aptitude",
                  weakestSubject = "Revision",
                  recentScores = listOf("Entry Test" to 85)
                )
                AppRepository.addStudent(s)
              } else if (selectedRole == UserRole.TEACHER) {
                val t = Teacher(
                  id = "FAC${System.currentTimeMillis().toString().takeLast(3)}",
                  name = fullName,
                  subject = "General Academic",
                  contact = "+91 9800000000",
                  qualification = "B.Sc / M.Sc",
                  experience = "5 Years",
                  assignedBatches = listOf("JEE Apex Morning"),
                  classesTaken = 0,
                  attendancePercent = 100,
                  feedbackRating = 5.0f,
                  salary = 45000,
                  incentives = 5000,
                  deductions = 0,
                  status = "Active",
                  email = email.trim().lowercase()
                )
                AppRepository.addTeacher(t)
              }
            },
            onFailure = { err ->
              isLoading = false
              errorMsg = err
            }
          )
        },
        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
        enabled = !isLoading
      ) {
        if (isLoading) {
          CircularProgressIndicator(color = Color.White, modifier = Modifier.size(16.dp))
        } else {
          Text("Create Account")
        }
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss, enabled = !isLoading) { Text("Close", color = Color(0xFF94A3B8)) }
    },
    containerColor = Color(0xFF0D1526),
    shape = RoundedCornerShape(24.dp)
  )
}

