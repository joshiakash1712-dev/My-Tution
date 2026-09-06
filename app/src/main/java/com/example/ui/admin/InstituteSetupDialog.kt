package com.example.ui.admin

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.AppRepository
import com.example.data.InstituteConfig
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun InstituteSetupDialog(
  onDismiss: () -> Unit
) {
  val context = LocalContext.current
  val currentConfig by AppRepository.instituteConfig.collectAsState()

  var selectedTab by remember { mutableStateOf(0) }

  // Admin Profile States
  var adminFullName by remember { mutableStateOf(currentConfig.adminFullName) }
  var adminRole by remember { mutableStateOf(currentConfig.adminRole) }
  var adminGender by remember { mutableStateOf(currentConfig.adminGender) }
  var adminDob by remember { mutableStateOf(currentConfig.adminDob) }
  var adminPhone by remember { mutableStateOf(currentConfig.adminPhone) }
  var adminEmail by remember { mutableStateOf(currentConfig.adminEmail) }
  var adminNewPassword by remember { mutableStateOf("") }

  // Institute Identity States
  var instituteName by remember { mutableStateOf(currentConfig.instituteName) }
  var tagline by remember { mutableStateOf(currentConfig.tagline) }
  var founderName by remember { mutableStateOf(currentConfig.founderName) }
  var address by remember { mutableStateOf(currentConfig.address) }
  var contactPhone by remember { mutableStateOf(currentConfig.contactPhone) }
  var email by remember { mutableStateOf(currentConfig.email) }
  var academicYear by remember { mutableStateOf(currentConfig.academicYear) }
  var existingStaffCount by remember { mutableStateOf(currentConfig.existingStaffCount.toString()) }

  // All 14 Grade options
  val allAvailableGrades = listOf(
    "LKG", "UKG", "1st", "2nd", "3rd", "4th", "5th",
    "6th", "7th", "8th", "9th", "10th", "11th", "12th"
  )

  // Selected Grades
  var selectedGrades by remember { mutableStateOf(currentConfig.selectedGrades.toSet()) }

  // Subject maps per grade
  val gradeSubjectsMap = remember {
    mutableStateMapOf<String, MutableList<String>>().apply {
      currentConfig.gradeSubjects.forEach { (k, v) ->
        put(k, v.toMutableList())
      }
      allAvailableGrades.forEach { g ->
        if (!containsKey(g)) {
          put(g, mutableListOf("Mathematics", "Science", "English"))
        }
      }
    }
  }

  // Fees per grade
  val gradeFeesMap = remember {
    mutableStateMapOf<String, String>().apply {
      currentConfig.gradeFees.forEach { (k, v) ->
        put(k, v.toString())
      }
      allAvailableGrades.forEach { g ->
        if (!containsKey(g)) {
          put(g, "25000")
        }
      }
    }
  }

  // Competitive exams & streams
  var selectedExams by remember { mutableStateOf(currentConfig.competitiveExams.toSet()) }
  var selectedStreams by remember { mutableStateOf(currentConfig.streams.toSet()) }

  // New subject input per active grade
  var activeGradeSubjectTab by remember { mutableStateOf(selectedGrades.firstOrNull() ?: "10th") }
  var newSubjectInput by remember { mutableStateOf("") }

  // Date picker dialog
  var showDatePicker by remember { mutableStateOf(false) }

  if (showDatePicker) {
    val datePickerState = rememberDatePickerState()
    DatePickerDialog(
      onDismissRequest = { showDatePicker = false },
      confirmButton = {
        TextButton(
          onClick = {
            datePickerState.selectedDateMillis?.let { millis ->
              val formatter = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
              adminDob = formatter.format(Date(millis))
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
                .size(42.dp)
                .clip(CircleShape)
                .background(Color(0xFF0061A4).copy(alpha = 0.12f)),
              contentAlignment = Alignment.Center
            ) {
              Icon(Icons.Default.School, contentDescription = null, tint = Color(0xFF0061A4))
            }
            Spacer(Modifier.width(12.dp))
            Column {
              Text(
                "Institute & Academic Setup",
                fontWeight = FontWeight.Bold,
                fontSize = 17.sp,
                color = MaterialTheme.colorScheme.onSurface
              )
              Text(
                "Universal institute configuration & academic parameters",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
            }
          }
          IconButton(onClick = onDismiss) {
            Icon(Icons.Default.Close, contentDescription = "Close", tint = MaterialTheme.colorScheme.onSurfaceVariant)
          }
        }

        Spacer(Modifier.height(14.dp))

        // Navigation Tabs (Admin Profile, Institute Identity, Grades & Subjects)
        TabRow(
          selectedTabIndex = selectedTab,
          containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
          modifier = Modifier.clip(RoundedCornerShape(14.dp))
        ) {
          Tab(
            selected = selectedTab == 0,
            onClick = { selectedTab = 0 },
            text = { Text("1. Admin Profile", fontSize = 12.sp, fontWeight = FontWeight.Bold) },
            icon = { Icon(Icons.Default.AdminPanelSettings, contentDescription = null, modifier = Modifier.size(18.dp)) }
          )
          Tab(
            selected = selectedTab == 1,
            onClick = { selectedTab = 1 },
            text = { Text("2. Institute Info", fontSize = 12.sp, fontWeight = FontWeight.Bold) },
            icon = { Icon(Icons.Default.Apartment, contentDescription = null, modifier = Modifier.size(18.dp)) }
          )
          Tab(
            selected = selectedTab == 2,
            onClick = { selectedTab = 2 },
            text = { Text("3. Grades & Subjects", fontSize = 12.sp, fontWeight = FontWeight.Bold) },
            icon = { Icon(Icons.Default.MenuBook, contentDescription = null, modifier = Modifier.size(18.dp)) }
          )
        }

        Spacer(Modifier.height(14.dp))

        // Scrollable Body Content
        Box(
          modifier = Modifier
            .weight(1f)
            .fillMaxWidth()
        ) {
          when (selectedTab) {
            0 -> AdminProfileStep(
              fullName = adminFullName,
              onFullNameChange = { adminFullName = it },
              role = adminRole,
              onRoleChange = { adminRole = it },
              gender = adminGender,
              onGenderChange = { adminGender = it },
              dob = adminDob,
              onOpenDatePicker = { showDatePicker = true },
              phone = adminPhone,
              onPhoneChange = { adminPhone = it },
              email = adminEmail,
              onEmailChange = { adminEmail = it },
              newPassword = adminNewPassword,
              onNewPasswordChange = { adminNewPassword = it }
            )
            1 -> InstituteDetailsStep(
              name = instituteName,
              onNameChange = { instituteName = it },
              tagline = tagline,
              onTaglineChange = { tagline = it },
              founder = founderName,
              onFounderChange = { founderName = it },
              address = address,
              onAddressChange = { address = it },
              phone = contactPhone,
              onPhoneChange = { contactPhone = it },
              email = email,
              onEmailChange = { email = it },
              academicYear = academicYear,
              onAcademicYearChange = { academicYear = it },
              staffCount = existingStaffCount,
              onStaffCountChange = { existingStaffCount = it }
            )
            2 -> GradesAndSubjectsStep(
              allAvailableGrades = allAvailableGrades,
              selectedGrades = selectedGrades,
              onToggleGrade = { g ->
                selectedGrades = if (selectedGrades.contains(g)) selectedGrades - g else selectedGrades + g
                if (activeGradeSubjectTab !in selectedGrades && selectedGrades.isNotEmpty()) {
                  activeGradeSubjectTab = selectedGrades.first()
                }
              },
              onSelectAllGrades = {
                selectedGrades = allAvailableGrades.toSet()
              },
              onDeselectAllGrades = {
                selectedGrades = setOf("10th", "11th", "12th")
              },
              activeGradeSubjectTab = activeGradeSubjectTab,
              onSelectGradeSubjectTab = { activeGradeSubjectTab = it },
              gradeSubjectsMap = gradeSubjectsMap,
              newSubjectInput = newSubjectInput,
              onNewSubjectInputChange = { newSubjectInput = it },
              onAddSubject = { grade ->
                if (newSubjectInput.isNotBlank()) {
                  val currentList = gradeSubjectsMap[grade] ?: mutableListOf()
                  if (!currentList.contains(newSubjectInput.trim())) {
                    currentList.add(newSubjectInput.trim())
                    gradeSubjectsMap[grade] = currentList
                  }
                  newSubjectInput = ""
                }
              },
              onRemoveSubject = { grade, sub ->
                val currentList = gradeSubjectsMap[grade] ?: mutableListOf()
                currentList.remove(sub)
                gradeSubjectsMap[grade] = currentList
              },
              gradeFeesMap = gradeFeesMap,
              onFeeChange = { grade, feeStr ->
                gradeFeesMap[grade] = feeStr
              },
              selectedExams = selectedExams,
              onToggleExam = { ex ->
                selectedExams = if (selectedExams.contains(ex)) selectedExams - ex else selectedExams + ex
              },
              selectedStreams = selectedStreams,
              onToggleStream = { st ->
                selectedStreams = if (selectedStreams.contains(st)) selectedStreams - st else selectedStreams + st
              }
            )
          }
        }

        Spacer(Modifier.height(14.dp))

        // Action Buttons Row
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          if (selectedTab > 0) {
            OutlinedButton(
              onClick = { selectedTab -= 1 },
              shape = RoundedCornerShape(12.dp)
            ) {
              Icon(Icons.Default.ArrowBack, contentDescription = null, modifier = Modifier.size(16.dp))
              Spacer(Modifier.width(6.dp))
              Text("Previous")
            }
          } else {
            Spacer(Modifier.width(8.dp))
          }

          Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (selectedTab < 2) {
              Button(
                onClick = { selectedTab += 1 },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0061A4)),
                shape = RoundedCornerShape(12.dp)
              ) {
                Text("Next Step")
                Spacer(Modifier.width(6.dp))
                Icon(Icons.Default.ArrowForward, contentDescription = null, modifier = Modifier.size(16.dp))
              }
            } else {
              Button(
                onClick = {
                  val finalFees = gradeFeesMap.mapValues { it.value.toIntOrNull() ?: 25000 }
                  val finalSubjects = gradeSubjectsMap.toMap()

                  val newConfig = InstituteConfig(
                    instituteName = instituteName.ifBlank { "Radal Institute" },
                    tagline = tagline.ifBlank { "Suraj Prabhodhini" },
                    founderName = founderName.ifBlank { "Prof. Prashant N. Sondlikar" },
                    address = address,
                    contactPhone = contactPhone,
                    email = email,
                    academicYear = academicYear,
                    existingStaffCount = existingStaffCount.toIntOrNull() ?: 8,
                    selectedGrades = selectedGrades.toList(),
                    gradeSubjects = finalSubjects,
                    competitiveExams = selectedExams.toList(),
                    streams = selectedStreams.toList(),
                    gradeFees = finalFees,
                    adminFullName = adminFullName,
                    adminRole = adminRole,
                    adminGender = adminGender,
                    adminDob = adminDob,
                    adminPhone = adminPhone,
                    adminEmail = adminEmail,
                    isSetupCompleted = true
                  )

                  AppRepository.updateInstituteConfig(newConfig)

                  if (adminNewPassword.isNotBlank()) {
                    com.example.security.SecurityEngine.registerUserCredential(
                      identifier = adminEmail,
                      rawPassword = adminNewPassword,
                      role = com.example.data.UserRole.ADMIN
                    )
                  }

                  Toast.makeText(context, "✅ Institute Configuration Saved Permanently!", Toast.LENGTH_SHORT).show()
                  onDismiss()
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF059669)),
                shape = RoundedCornerShape(12.dp)
              ) {
                Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Save Configuration", fontWeight = FontWeight.Bold)
              }
            }
          }
        }
      }
    }
  }
}

@Composable
fun AdminProfileStep(
  fullName: String,
  onFullNameChange: (String) -> Unit,
  role: String,
  onRoleChange: (String) -> Unit,
  gender: String,
  onGenderChange: (String) -> Unit,
  dob: String,
  onOpenDatePicker: () -> Unit,
  phone: String,
  onPhoneChange: (String) -> Unit,
  email: String,
  onEmailChange: (String) -> Unit,
  newPassword: String,
  onNewPasswordChange: (String) -> Unit
) {
  Column(
    modifier = Modifier
      .fillMaxSize()
      .verticalScroll(rememberScrollState()),
    verticalArrangement = Arrangement.spacedBy(12.dp)
  ) {
    Text(
      "Administrator Personal & Access Details",
      fontWeight = FontWeight.Bold,
      fontSize = 14.sp,
      color = Color(0xFF0061A4)
    )

    OutlinedTextField(
      value = fullName,
      onValueChange = onFullNameChange,
      label = { Text("Admin / Director Full Name") },
      placeholder = { Text("e.g. Prof. Prashant N. Sondlikar") },
      leadingIcon = { Icon(Icons.Default.Person, contentDescription = null, tint = Color(0xFF0061A4)) },
      singleLine = true,
      shape = RoundedCornerShape(12.dp),
      modifier = Modifier.fillMaxWidth()
    )

    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
      OutlinedTextField(
        value = role,
        onValueChange = onRoleChange,
        label = { Text("Designation / Title") },
        placeholder = { Text("Founder Director / Principal") },
        leadingIcon = { Icon(Icons.Default.Badge, contentDescription = null, tint = Color(0xFF0061A4)) },
        singleLine = true,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.weight(1f)
      )

      Column(modifier = Modifier.weight(1f)) {
        Text("Gender / Sex", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
          listOf("Male", "Female", "Other").forEach { g ->
            FilterChip(
              selected = gender == g,
              onClick = { onGenderChange(g) },
              label = { Text(g, fontSize = 11.sp) }
            )
          }
        }
      }
    }

    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
      OutlinedTextField(
        value = dob,
        onValueChange = {},
        readOnly = true,
        label = { Text("Date of Birth (DOB)") },
        trailingIcon = {
          IconButton(onClick = onOpenDatePicker) {
            Icon(Icons.Default.CalendarMonth, contentDescription = "Pick Date", tint = Color(0xFF0061A4))
          }
        },
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.weight(1f).clickable { onOpenDatePicker() }
      )

      OutlinedTextField(
        value = phone,
        onValueChange = onPhoneChange,
        label = { Text("Contact Phone / WhatsApp") },
        placeholder = { Text("+91 9068306693") },
        leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null, tint = Color(0xFF0061A4)) },
        singleLine = true,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.weight(1f)
      )
    }

    OutlinedTextField(
      value = email,
      onValueChange = onEmailChange,
      label = { Text("Official Admin Email (For Login)") },
      placeholder = { Text("appa1050@yahoo.com") },
      leadingIcon = { Icon(Icons.Default.Email, contentDescription = null, tint = Color(0xFF0061A4)) },
      singleLine = true,
      shape = RoundedCornerShape(12.dp),
      modifier = Modifier.fillMaxWidth()
    )

    Card(
      shape = RoundedCornerShape(14.dp),
      colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
      border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
      Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Change Admin Password (Optional)", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
        OutlinedTextField(
          value = newPassword,
          onValueChange = onNewPasswordChange,
          label = { Text("Set New Password") },
          placeholder = { Text("Leave blank to keep current password") },
          visualTransformation = PasswordVisualTransformation(),
          leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null, tint = Color(0xFF0061A4)) },
          singleLine = true,
          shape = RoundedCornerShape(12.dp),
          modifier = Modifier.fillMaxWidth()
        )
      }
    }
  }
}

@Composable
fun InstituteDetailsStep(
  name: String,
  onNameChange: (String) -> Unit,
  tagline: String,
  onTaglineChange: (String) -> Unit,
  founder: String,
  onFounderChange: (String) -> Unit,
  address: String,
  onAddressChange: (String) -> Unit,
  phone: String,
  onPhoneChange: (String) -> Unit,
  email: String,
  onEmailChange: (String) -> Unit,
  academicYear: String,
  onAcademicYearChange: (String) -> Unit,
  staffCount: String,
  onStaffCountChange: (String) -> Unit
) {
  Column(
    modifier = Modifier
      .fillMaxSize()
      .verticalScroll(rememberScrollState()),
    verticalArrangement = Arrangement.spacedBy(12.dp)
  ) {
    Text(
      "Institute Identity & Official Contact",
      fontWeight = FontWeight.Bold,
      fontSize = 14.sp,
      color = Color(0xFF0061A4)
    )

    OutlinedTextField(
      value = name,
      onValueChange = onNameChange,
      label = { Text("Institute Name") },
      placeholder = { Text("e.g. Radal Institute") },
      leadingIcon = { Icon(Icons.Default.Apartment, contentDescription = null, tint = Color(0xFF0061A4)) },
      singleLine = true,
      shape = RoundedCornerShape(12.dp),
      modifier = Modifier.fillMaxWidth()
    )

    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
      OutlinedTextField(
        value = tagline,
        onValueChange = onTaglineChange,
        label = { Text("Organization / Tagline") },
        placeholder = { Text("e.g. Suraj Prabhodhini") },
        leadingIcon = { Icon(Icons.Default.Star, contentDescription = null, tint = Color(0xFFF59E0B)) },
        singleLine = true,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.weight(1f)
      )

      OutlinedTextField(
        value = founder,
        onValueChange = onFounderChange,
        label = { Text("Founder / President") },
        placeholder = { Text("Prof. Prashant N. Sondlikar") },
        leadingIcon = { Icon(Icons.Default.AccountCircle, contentDescription = null, tint = Color(0xFF0061A4)) },
        singleLine = true,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.weight(1f)
      )
    }

    OutlinedTextField(
      value = address,
      onValueChange = onAddressChange,
      label = { Text("Complete Institute Address / Landmark") },
      placeholder = { Text("Yallamai Sant Namdev Nagar (E), Dhanora Road, Beed - 431122") },
      leadingIcon = { Icon(Icons.Default.LocationOn, contentDescription = null, tint = Color(0xFFEF4444)) },
      shape = RoundedCornerShape(12.dp),
      modifier = Modifier.fillMaxWidth()
    )

    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
      OutlinedTextField(
        value = phone,
        onValueChange = onPhoneChange,
        label = { Text("Institute Phone / Helpline") },
        placeholder = { Text("+91 9068306693") },
        leadingIcon = { Icon(Icons.Default.PhoneInTalk, contentDescription = null, tint = Color(0xFF0061A4)) },
        singleLine = true,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.weight(1f)
      )

      OutlinedTextField(
        value = email,
        onValueChange = onEmailChange,
        label = { Text("Institute Contact Email") },
        placeholder = { Text("appa1050@yahoo.com") },
        leadingIcon = { Icon(Icons.Default.AlternateEmail, contentDescription = null, tint = Color(0xFF0061A4)) },
        singleLine = true,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.weight(1f)
      )
    }

    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
      OutlinedTextField(
        value = academicYear,
        onValueChange = onAcademicYearChange,
        label = { Text("Current Academic Year") },
        placeholder = { Text("2026–2027") },
        leadingIcon = { Icon(Icons.Default.DateRange, contentDescription = null, tint = Color(0xFF0061A4)) },
        singleLine = true,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.weight(1f)
      )

      OutlinedTextField(
        value = staffCount,
        onValueChange = onStaffCountChange,
        label = { Text("Staff Members Already Working") },
        placeholder = { Text("8") },
        leadingIcon = { Icon(Icons.Default.Groups, contentDescription = null, tint = Color(0xFF0061A4)) },
        singleLine = true,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.weight(1f)
      )
    }
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GradesAndSubjectsStep(
  allAvailableGrades: List<String>,
  selectedGrades: Set<String>,
  onToggleGrade: (String) -> Unit,
  onSelectAllGrades: () -> Unit,
  onDeselectAllGrades: () -> Unit,
  activeGradeSubjectTab: String,
  onSelectGradeSubjectTab: (String) -> Unit,
  gradeSubjectsMap: Map<String, MutableList<String>>,
  newSubjectInput: String,
  onNewSubjectInputChange: (String) -> Unit,
  onAddSubject: (String) -> Unit,
  onRemoveSubject: (String, String) -> Unit,
  gradeFeesMap: Map<String, String>,
  onFeeChange: (String, String) -> Unit,
  selectedExams: Set<String>,
  onToggleExam: (String) -> Unit,
  selectedStreams: Set<String>,
  onToggleStream: (String) -> Unit
) {
  Column(
    modifier = Modifier
      .fillMaxSize()
      .verticalScroll(rememberScrollState()),
    verticalArrangement = Arrangement.spacedBy(14.dp)
  ) {
    // 1. Grade Offerings with Select All
    Card(
      shape = RoundedCornerShape(16.dp),
      colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
      border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
      Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Column {
            Text("Grades Offered by Institute", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface)
            Text("Select which standards your coaching classes teach", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
          }

          Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            TextButton(onClick = onSelectAllGrades, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)) {
              Icon(Icons.Default.SelectAll, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color(0xFF0061A4))
              Spacer(Modifier.width(4.dp))
              Text("Select All", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0061A4))
            }
            TextButton(onClick = onDeselectAllGrades, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)) {
              Text("Reset", fontSize = 11.sp, color = Color(0xFF64748B))
            }
          }
        }

        // Wrap-around Grade chips
        FlowRow(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(6.dp),
          verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
          allAvailableGrades.forEach { g ->
            val isSelected = selectedGrades.contains(g)
            FilterChip(
              selected = isSelected,
              onClick = { onToggleGrade(g) },
              label = { Text(g, fontSize = 11.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
              leadingIcon = if (isSelected) {
                { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp)) }
              } else null,
              colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = Color(0xFF0061A4),
                selectedLabelColor = Color.White,
                selectedLeadingIconColor = Color.White
              )
            )
          }
        }
      }
    }

    // 2. Competitive Exams & Streams (If 11th or 12th is selected)
    val hasHigherSecondary = selectedGrades.contains("11th") || selectedGrades.contains("12th")
    if (hasHigherSecondary) {
      Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF0F9FF)),
        border = BorderStroke(1.dp, Color(0xFFBAE6FD))
      ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.EmojiEvents, contentDescription = null, tint = Color(0xFF0284C7), modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text("11th & 12th Science Stream & Competitive Tracks", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFF0369A1))
          }

          Text("Stream Faculties (PCM / PCB / PCMB / PCMC):", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF334155))
          Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("PCM", "PCB", "PCMB", "PCMC").forEach { st ->
              val isSel = selectedStreams.contains(st)
              FilterChip(
                selected = isSel,
                onClick = { onToggleStream(st) },
                label = { Text(st, fontSize = 11.sp) }
              )
            }
          }

          Text("Target Competitive Exams:", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF334155))
          Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
          ) {
            listOf("NEET (Medical)", "MHT-CET / CET", "JEE Main", "JEE Advanced", "12th Board Exam").forEach { ex ->
              val isSel = selectedExams.contains(ex)
              FilterChip(
                selected = isSel,
                onClick = { onToggleExam(ex) },
                label = { Text(ex, fontSize = 11.sp) }
              )
            }
          }
        }
      }
    }

    // 3. Dynamic Manual Subject Entry per Grade
    if (selectedGrades.isNotEmpty()) {
      Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
      ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
          Text(
            "Manual Subject Configuration per Grade",
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurface
          )
          Text(
            "Type subjects one by one to customize curriculum for each standard",
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )

          // Grade selector pills
          LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(selectedGrades.toList()) { g ->
              val isCurrent = activeGradeSubjectTab == g
              Surface(
                onClick = { onSelectGradeSubjectTab(g) },
                shape = RoundedCornerShape(10.dp),
                color = if (isCurrent) Color(0xFF0061A4) else MaterialTheme.colorScheme.surfaceVariant
              ) {
                Text(
                  text = "$g Class",
                  color = if (isCurrent) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                  fontSize = 11.sp,
                  fontWeight = FontWeight.Bold,
                  modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
              }
            }
          }

          Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

          // Subject Add input
          Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            OutlinedTextField(
              value = newSubjectInput,
              onValueChange = onNewSubjectInputChange,
              placeholder = { Text("Type subject (e.g. Physics, Marathi, Algebra)...", fontSize = 12.sp) },
              singleLine = true,
              shape = RoundedCornerShape(12.dp),
              modifier = Modifier.weight(1f)
            )

            Button(
              onClick = { onAddSubject(activeGradeSubjectTab) },
              colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0061A4)),
              shape = RoundedCornerShape(12.dp)
            ) {
              Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
              Spacer(Modifier.width(4.dp))
              Text("Add")
            }
          }

          // Active Grade Subject Chips
          val currentSubjects = gradeSubjectsMap[activeGradeSubjectTab] ?: mutableListOf()
          Text(
            "Subjects for $activeGradeSubjectTab (${currentSubjects.size}):",
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
          )

          FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
          ) {
            currentSubjects.forEach { sub ->
              InputChip(
                selected = false,
                onClick = {},
                label = { Text(sub, fontSize = 11.sp) },
                trailingIcon = {
                  Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Delete",
                    tint = Color(0xFFEF4444),
                    modifier = Modifier
                      .size(16.dp)
                      .clickable { onRemoveSubject(activeGradeSubjectTab, sub) }
                  )
                }
              )
            }
          }

          Spacer(Modifier.height(4.dp))

          // Standard Fee for this active grade
          OutlinedTextField(
            value = gradeFeesMap[activeGradeSubjectTab] ?: "25000",
            onValueChange = { onFeeChange(activeGradeSubjectTab, it) },
            label = { Text("Standard Annual Tuition Fee for $activeGradeSubjectTab (₹)") },
            leadingIcon = { Text("₹", fontWeight = FontWeight.Bold, color = Color(0xFF059669), modifier = Modifier.padding(start = 12.dp)) },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
          )
        }
      }
    }
  }
}
