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
import com.example.data.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.random.Random

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InstituteAdmissionFormDialog(
  onDismiss: () -> Unit,
  onSaveStudent: (Student) -> Unit
) {
  val context = LocalContext.current
  val clipboardManager = LocalClipboardManager.current
  val instituteConfig by AppRepository.instituteConfig.collectAsState()
  val batches by AppRepository.batches.collectAsState()

  // 1. Student Personal Fields
  var fullName by remember { mutableStateOf("") }
  var gender by remember { mutableStateOf("Male") }
  var genderExpanded by remember { mutableStateOf(false) }

  var privatePhone by remember { mutableStateOf("") }
  var parentPhone by remember { mutableStateOf("") }
  var email by remember { mutableStateOf("") }

  var dob by remember { mutableStateOf("15/06/2009") }
  var showDatePicker by remember { mutableStateOf(false) }

  var caste by remember { mutableStateOf("Open / General") }
  var motherTongue by remember { mutableStateOf("Marathi") }
  var address by remember { mutableStateOf("") }
  var schoolCollege by remember { mutableStateOf("") }

  // 2. Standard & Stream
  val availableGrades = remember(instituteConfig) {
    if (instituteConfig.selectedGrades.isNotEmpty()) instituteConfig.selectedGrades else listOf("8th", "9th", "10th", "11th", "12th")
  }
  var selectedStandard by remember { mutableStateOf(availableGrades.find { it == "11th" } ?: availableGrades.firstOrNull() ?: "11th") }
  var standardExpanded by remember { mutableStateOf(false) }

  var selectedStreamFaculty by remember { mutableStateOf("PCMB") }
  var selectedCompetitiveExam by remember { mutableStateOf("JEE Main & MHT-CET") }
  var selectedBatch by remember { mutableStateOf(batches.firstOrNull()?.name ?: "11th - Science (B)") }

  // 3. Parent Info
  var parentType by remember { mutableStateOf("Father") }
  var parentFullName by remember { mutableStateOf("") }
  var parentEmail by remember { mutableStateOf("") }
  var parentOccupation by remember { mutableStateOf("Business / Service") }

  // 4. Documents & Photo state (Selected from device storage)
  var studentPhotoUri by remember { mutableStateOf<Uri?>(null) }
  var aadharCardUri by remember { mutableStateOf<Uri?>(null) }
  var markMemo10thUri by remember { mutableStateOf<Uri?>(null) }
  var markMemo11thUri by remember { mutableStateOf<Uri?>(null) }

  val studentPhotoPicker = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.PickVisualMedia(),
    onResult = { uri ->
      if (uri != null) {
        studentPhotoUri = uri
        Toast.makeText(context, "Student photo selected from device", Toast.LENGTH_SHORT).show()
      }
    }
  )

  val aadharPicker = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.PickVisualMedia(),
    onResult = { uri ->
      if (uri != null) {
        aadharCardUri = uri
        Toast.makeText(context, "Aadhar card selected", Toast.LENGTH_SHORT).show()
      }
    }
  )

  val markMemo10thPicker = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.PickVisualMedia(),
    onResult = { uri ->
      if (uri != null) {
        markMemo10thUri = uri
        Toast.makeText(context, "10th Mark memo selected", Toast.LENGTH_SHORT).show()
      }
    }
  )

  val markMemo11thPicker = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.PickVisualMedia(),
    onResult = { uri ->
      if (uri != null) {
        markMemo11thUri = uri
        Toast.makeText(context, "11th Mark memo selected", Toast.LENGTH_SHORT).show()
      }
    }
  )

  // 5. Activation Code Generation
  val generatedActivationCode = remember {
    (100000 + Random.nextInt(900000)).toString()
  }

  // 6. Admission confirmation step
  var showSuccessSlip by remember { mutableStateOf(false) }
  var admittedStudentObj by remember { mutableStateOf<Student?>(null) }

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
        ) { Text("Confirm DOB", fontWeight = FontWeight.Bold) }
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
        .fillMaxHeight(0.95f)
    ) {
      if (showSuccessSlip && admittedStudentObj != null) {
        AdmissionSlipView(
          student = admittedStudentObj!!,
          instituteConfig = instituteConfig,
          onClose = onDismiss
        )
      } else {
        Column(
          modifier = Modifier
            .fillMaxSize()
            .padding(18.dp)
        ) {
          // Clean Modern Dialog Header
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(bottom = 12.dp),
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
                  .clip(CircleShape)
                  .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
              ) {
                Icon(
                  imageVector = Icons.Default.PersonAdd,
                  contentDescription = null,
                  tint = MaterialTheme.colorScheme.primary,
                  modifier = Modifier.size(22.dp)
                )
              }
              Column {
                Text(
                  "Student Admission",
                  fontWeight = FontWeight.Bold,
                  fontSize = 18.sp,
                  color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                  "Enter candidate details and allocation",
                  fontSize = 12.sp,
                  color = MaterialTheme.colorScheme.onSurfaceVariant
                )
              }
            }

            IconButton(onClick = onDismiss) {
              Icon(Icons.Default.Close, contentDescription = "Close", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
          }

          // Form Body
          Column(
            modifier = Modifier
              .weight(1f)
              .fillMaxWidth()
              .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp)
          ) {
            // Top Section: Full Name & Passport Photo Placeholder
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(12.dp),
              verticalAlignment = Alignment.Top
            ) {
              Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                // (1) Full Name
                OutlinedTextField(
                  value = fullName,
                  onValueChange = { fullName = it },
                  label = { Text("(1) Full Name") },
                  placeholder = { Text("e.g. Akash Prashant Joshi") },
                  leadingIcon = { Icon(Icons.Default.Person, contentDescription = null, tint = Color(0xFF0061A4)) },
                  singleLine = true,
                  shape = RoundedCornerShape(12.dp),
                  modifier = Modifier.fillMaxWidth()
                )

                // (2) Gender Dropdown
                ExposedDropdownMenuBox(
                  expanded = genderExpanded,
                  onExpandedChange = { genderExpanded = it }
                ) {
                  OutlinedTextField(
                    value = gender,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("(2) Gender") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = genderExpanded) },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                      .fillMaxWidth()
                      .menuAnchor()
                  )
                  ExposedDropdownMenu(
                    expanded = genderExpanded,
                    onDismissRequest = { genderExpanded = false }
                  ) {
                    listOf("Male", "Female", "Transgender").forEach { g ->
                      DropdownMenuItem(
                        text = { Text(g) },
                        onClick = {
                          gender = g
                          genderExpanded = false
                        }
                      )
                    }
                  }
                }
              }

              // Photo Box with real file picker
              Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
                border = BorderStroke(1.dp, if (studentPhotoUri != null) Color(0xFF0284C7) else Color(0xFFCBD5E1)),
                modifier = Modifier
                  .width(108.dp)
                  .height(136.dp)
                  .clickable {
                    studentPhotoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                  }
              ) {
                if (studentPhotoUri != null) {
                  Box(modifier = Modifier.fillMaxSize()) {
                    AsyncImage(
                      model = studentPhotoUri,
                      contentDescription = "Selected Student Photo",
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
                        modifier = Modifier.padding(vertical = 3.dp, horizontal = 4.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                      ) {
                        Icon(Icons.Default.Edit, contentDescription = null, tint = Color.White, modifier = Modifier.size(11.dp))
                        Spacer(Modifier.width(3.dp))
                        Text("Change", fontSize = 9.sp, color = Color.White, fontWeight = FontWeight.Bold)
                      }
                    }
                  }
                } else {
                  Column(
                    modifier = Modifier
                      .fillMaxSize()
                      .padding(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                  ) {
                    Box(
                      modifier = Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFE2E8F0)),
                      contentAlignment = Alignment.Center
                    ) {
                      Icon(Icons.Default.AddAPhoto, contentDescription = "Add Student Photo", tint = Color(0xFF0284C7), modifier = Modifier.size(24.dp))
                    }
                    Spacer(Modifier.height(6.dp))
                    Text("Add Photo", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F172A))
                    Text("From Storage", fontSize = 9.sp, color = Color(0xFF64748B), textAlign = TextAlign.Center)
                  }
                }
              }
            }

            // (3) Contact (Personal) Card
            Card(
              shape = RoundedCornerShape(14.dp),
              colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)),
              border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
              Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("(3) Contact Information (Personal & Parents)", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color(0xFF0061A4))

                OutlinedTextField(
                  value = privatePhone,
                  onValueChange = { privatePhone = it },
                  label = { Text("Student Phone number (Private)") },
                  placeholder = { Text("9822100000") },
                  leadingIcon = { Text("+91", fontWeight = FontWeight.Bold, color = Color(0xFF64748B), modifier = Modifier.padding(start = 12.dp)) },
                  singleLine = true,
                  shape = RoundedCornerShape(12.dp),
                  modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                  value = parentPhone,
                  onValueChange = { parentPhone = it },
                  label = { Text("Parent / Guardian Phone (WhatsApp / SMS Alerts)") },
                  placeholder = { Text("9068300000") },
                  leadingIcon = { Text("+91", fontWeight = FontWeight.Bold, color = Color(0xFF059669), modifier = Modifier.padding(start = 12.dp)) },
                  singleLine = true,
                  shape = RoundedCornerShape(12.dp),
                  modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                  value = email,
                  onValueChange = { email = it },
                  label = { Text("E-mail Address (Private)") },
                  placeholder = { Text("student@radal.com") },
                  leadingIcon = { Icon(Icons.Default.Email, contentDescription = null, tint = Color(0xFF0061A4)) },
                  singleLine = true,
                  shape = RoundedCornerShape(12.dp),
                  modifier = Modifier.fillMaxWidth()
                )
              }
            }

            // (4), (5), (6), (7): DOB, Caste, Address, School
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
              OutlinedTextField(
                value = dob,
                onValueChange = {},
                readOnly = true,
                label = { Text("(4) Date of Birth (DOB)") },
                trailingIcon = {
                  IconButton(onClick = { showDatePicker = true }) {
                    Icon(Icons.Default.CalendarMonth, contentDescription = "Pick Date", tint = Color(0xFF0061A4))
                  }
                },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                  .weight(1f)
                  .clickable { showDatePicker = true }
              )

              OutlinedTextField(
                value = caste,
                onValueChange = { caste = it },
                label = { Text("(5) Caste / Category") },
                placeholder = { Text("Open / OBC / SC / ST") },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.weight(1f)
              )
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
              OutlinedTextField(
                value = motherTongue,
                onValueChange = { motherTongue = it },
                label = { Text("Mother Tongue") },
                placeholder = { Text("Marathi / Hindi / English") },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.weight(1f)
              )

              OutlinedTextField(
                value = schoolCollege,
                onValueChange = { schoolCollege = it },
                label = { Text("(7) School / College") },
                placeholder = { Text("City Junior College, Beed") },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.weight(1.3f)
              )
            }

            OutlinedTextField(
              value = address,
              onValueChange = { address = it },
              label = { Text("(6) Residential Address") },
              placeholder = { Text("Plot No 14, Sant Namdev Nagar, Dhanora Road, Beed") },
              leadingIcon = { Icon(Icons.Default.Home, contentDescription = null, tint = Color(0xFF0061A4)) },
              shape = RoundedCornerShape(12.dp),
              modifier = Modifier.fillMaxWidth()
            )

            // Standard & Stream Faculty (PCM / PCB / PCMB / PCMC)
            Card(
              shape = RoundedCornerShape(14.dp),
              colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
              border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
              Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                  "Standard & Stream / Faculty",
                  fontWeight = FontWeight.Bold,
                  fontSize = 13.sp,
                  color = MaterialTheme.colorScheme.primary
                )

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                  // Standard Dropdown
                  ExposedDropdownMenuBox(
                    expanded = standardExpanded,
                    onExpandedChange = { standardExpanded = it },
                    modifier = Modifier.weight(1f)
                  ) {
                    OutlinedTextField(
                      value = "$selectedStandard Standard",
                      onValueChange = {},
                      readOnly = true,
                      label = { Text("Standard / Grade") },
                      trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = standardExpanded) },
                      shape = RoundedCornerShape(12.dp),
                      modifier = Modifier.fillMaxWidth().menuAnchor()
                    )
                    ExposedDropdownMenu(
                      expanded = standardExpanded,
                      onDismissRequest = { standardExpanded = false }
                    ) {
                      availableGrades.forEach { grd ->
                        DropdownMenuItem(
                          text = { Text("$grd Standard", color = MaterialTheme.colorScheme.onSurface) },
                          onClick = {
                            selectedStandard = grd
                            standardExpanded = false
                          }
                        )
                      }
                    }
                  }

                  // Batch Assignment
                  Column(modifier = Modifier.weight(1.2f)) {
                    Text(
                      "Batch Allocation",
                      fontSize = 11.sp,
                      fontWeight = FontWeight.SemiBold,
                      color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(4.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                      items(batches) { b ->
                        FilterChip(
                          selected = selectedBatch == b.name,
                          onClick = { selectedBatch = b.name },
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
                }

                // If 11th or 12th, show Stream faculty PCM / PCB / PCMB / PCMC
                if (selectedStandard == "11th" || selectedStandard == "12th") {
                  Text(
                    "Stream / Subject Group:",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                  )
                  Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                  ) {
                    listOf("PCM", "PCB", "PCMB", "PCMC").forEach { st ->
                      val isSel = selectedStreamFaculty == st
                      FilterChip(
                        selected = isSel,
                        onClick = { selectedStreamFaculty = st },
                        label = {
                          Text(
                            text = st,
                            fontSize = 12.sp,
                            fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                            maxLines = 1,
                            softWrap = false,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                          )
                        }
                      )
                    }
                  }

                  Text(
                    "Competitive Exam Track:",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                  )
                  LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                  ) {
                    items(listOf("JEE Main & Adv", "NEET (Medical)", "MHT-CET", "12th Board Only")) { ex ->
                      val isSel = selectedCompetitiveExam == ex
                      FilterChip(
                        selected = isSel,
                        onClick = { selectedCompetitiveExam = ex },
                        label = {
                          Text(
                            text = ex,
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
              }
            }

            // Parents / Guardian Details Card
            Card(
              shape = RoundedCornerShape(14.dp),
              colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)),
              border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
              Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                  "Parents / Guardian Details",
                  fontWeight = FontWeight.Bold,
                  fontSize = 13.sp,
                  color = MaterialTheme.colorScheme.primary
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                  listOf("Father", "Mother", "Guardian").forEach { pt ->
                    FilterChip(
                      selected = parentType == pt,
                      onClick = { parentType = pt },
                      label = {
                        Text(
                          text = pt,
                          fontSize = 12.sp,
                          maxLines = 1,
                          softWrap = false,
                          overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                      }
                    )
                  }
                }

                OutlinedTextField(
                  value = parentFullName,
                  onValueChange = { parentFullName = it },
                  label = { Text("$parentType Full Name") },
                  placeholder = { Text("e.g. Ramesh S. Sharma") },
                  leadingIcon = { Icon(Icons.Default.PersonOutline, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                  singleLine = true,
                  shape = RoundedCornerShape(12.dp),
                  modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                  value = parentOccupation,
                  onValueChange = { parentOccupation = it },
                  label = { Text("Occupation") },
                  placeholder = { Text("Business / Service / Professional") },
                  leadingIcon = { Icon(Icons.Default.WorkOutline, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                  singleLine = true,
                  shape = RoundedCornerShape(12.dp),
                  modifier = Modifier.fillMaxWidth()
                )
              }
            }

            // (11) Document Verification Checkboxes & Storage File Pickers
            Card(
              shape = RoundedCornerShape(14.dp),
              colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)),
              border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
              Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("(11) Document Verification & Memos", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color(0xFF0061A4))

                // Aadhar card file picker row
                Row(
                  modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (aadharCardUri != null) Color(0xFF0284C7).copy(alpha = 0.08f) else Color.Transparent)
                    .padding(vertical = 4.dp, horizontal = 6.dp),
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.SpaceBetween
                ) {
                  Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Checkbox(
                      checked = aadharCardUri != null,
                      onCheckedChange = { checked ->
                        if (checked) {
                          aadharPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        } else {
                          aadharCardUri = null
                        }
                      }
                    )
                    Spacer(Modifier.width(6.dp))
                    Column {
                      Text("Aadhar Card Photo / Scan", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                      Text(
                        if (aadharCardUri != null) "File selected from device" else "Tap to choose image file",
                        fontSize = 10.sp,
                        color = if (aadharCardUri != null) Color(0xFF059669) else Color(0xFF64748B)
                      )
                    }
                  }
                  OutlinedButton(
                    onClick = { aadharPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(8.dp)
                  ) {
                    Icon(Icons.Default.FileOpen, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(if (aadharCardUri != null) "Change" else "Browse", fontSize = 11.sp)
                  }
                }

                if (selectedStandard == "11th" || selectedStandard == "12th") {
                  Row(
                    modifier = Modifier
                      .fillMaxWidth()
                      .clip(RoundedCornerShape(8.dp))
                      .background(if (markMemo10thUri != null) Color(0xFF0284C7).copy(alpha = 0.08f) else Color.Transparent)
                      .padding(vertical = 4.dp, horizontal = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                  ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                      Checkbox(
                        checked = markMemo10thUri != null,
                        onCheckedChange = { checked ->
                          if (checked) {
                            markMemo10thPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                          } else {
                            markMemo10thUri = null
                          }
                        }
                      )
                      Spacer(Modifier.width(6.dp))
                      Column {
                        Text("10th Board Mark Memo", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                        Text(
                          if (markMemo10thUri != null) "File selected from device" else "Tap to choose image file",
                          fontSize = 10.sp,
                          color = if (markMemo10thUri != null) Color(0xFF059669) else Color(0xFF64748B)
                        )
                      }
                    }
                    OutlinedButton(
                      onClick = { markMemo10thPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                      contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                      shape = RoundedCornerShape(8.dp)
                    ) {
                      Icon(Icons.Default.FileOpen, contentDescription = null, modifier = Modifier.size(14.dp))
                      Spacer(Modifier.width(4.dp))
                      Text(if (markMemo10thUri != null) "Change" else "Browse", fontSize = 11.sp)
                    }
                  }
                }

                if (selectedStandard == "12th") {
                  Row(
                    modifier = Modifier
                      .fillMaxWidth()
                      .clip(RoundedCornerShape(8.dp))
                      .background(if (markMemo11thUri != null) Color(0xFF0284C7).copy(alpha = 0.08f) else Color.Transparent)
                      .padding(vertical = 4.dp, horizontal = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                  ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                      Checkbox(
                        checked = markMemo11thUri != null,
                        onCheckedChange = { checked ->
                          if (checked) {
                            markMemo11thPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                          } else {
                            markMemo11thUri = null
                          }
                        }
                      )
                      Spacer(Modifier.width(6.dp))
                      Column {
                        Text("11th Standard Mark Memo", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                        Text(
                          if (markMemo11thUri != null) "File selected from device" else "Tap to choose image file",
                          fontSize = 10.sp,
                          color = if (markMemo11thUri != null) Color(0xFF059669) else Color(0xFF64748B)
                        )
                      }
                    }
                    OutlinedButton(
                      onClick = { markMemo11thPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                      contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                      shape = RoundedCornerShape(8.dp)
                    ) {
                      Icon(Icons.Default.FileOpen, contentDescription = null, modifier = Modifier.size(14.dp))
                      Spacer(Modifier.width(4.dp))
                      Text(if (markMemo11thUri != null) "Change" else "Browse", fontSize = 11.sp)
                    }
                  }
                }
              }
            }

            // (12) Auto-Generated Student/Parent Activation Code Card
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
                  Text("First-Time Student & Parent Activation Code", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF92400E))
                  Text("Share this 6-digit code for first-time password setup in the app", fontSize = 10.sp, color = Color(0xFFB45309))
                  Spacer(Modifier.height(4.dp))
                  Text(
                    text = "${generatedActivationCode.take(3)} ${generatedActivationCode.takeLast(3)}",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 2.sp,
                    color = Color(0xFF78350F)
                  )
                }

                IconButton(
                  onClick = {
                    clipboardManager.setText(AnnotatedString(generatedActivationCode))
                    Toast.makeText(context, "Activation Code Copied: $generatedActivationCode", Toast.LENGTH_SHORT).show()
                  }
                ) {
                  Icon(Icons.Default.ContentCopy, contentDescription = "Copy Code", tint = Color(0xFF92400E))
                }
              }
            }
          }

          Spacer(Modifier.height(10.dp))

          // Bottom Action Row
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            OutlinedButton(
              onClick = onDismiss,
              shape = RoundedCornerShape(12.dp)
            ) {
              Text("Cancel")
            }

            Button(
              onClick = {
                if (fullName.isBlank()) {
                  Toast.makeText(context, "Please enter student full name", Toast.LENGTH_SHORT).show()
                  return@Button
                }

                val studentId = "STU_${System.currentTimeMillis().toString().takeLast(4)}"
                val newStudent = Student(
                  id = studentId,
                  name = fullName.trim(),
                  photo = studentPhotoUri?.toString() ?: "",
                  mobile = if (privatePhone.isNotBlank()) "+91 ${privatePhone.trim()}" else "+91 9822100000",
                  parentName = if (parentFullName.isNotBlank()) parentFullName.trim() else "$fullName Guardian",
                  parentContact = if (parentPhone.isNotBlank()) "+91 ${parentPhone.trim()}" else "+91 9068300000",
                  email = if (email.isNotBlank()) email.trim() else "${fullName.lowercase().replace(" ", ".")}@mytuition.com",
                  dob = dob,
                  gender = gender,
                  address = address.ifBlank { "Local Address" },
                  school = schoolCollege.ifBlank { "Junior College" },
                  className = "$selectedStandard Standard",
                  batch = selectedBatch,
                  stream = if (selectedStandard == "11th" || selectedStandard == "12th") selectedStreamFaculty else "Foundation",
                  admissionDate = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date()),
                  status = "Active",
                  attendancePercent = 100,
                  overallAvg = 85,
                  rank = 1,
                  strongestSubject = "Physics",
                  weakestSubject = "Revision",
                  caste = caste,
                  motherTongue = motherTongue,
                  parentOccupation = parentOccupation,
                  yearlyIncome = "",
                  aadharPhoto = aadharCardUri?.toString() ?: "",
                  markMemoPhoto10th = markMemo10thUri?.toString() ?: "",
                  markMemoPhoto11th = markMemo11thUri?.toString() ?: "",
                  targetExam = selectedCompetitiveExam,
                  activationCode = generatedActivationCode,
                  isActivated = false
                )

                onSaveStudent(newStudent)
                admittedStudentObj = newStudent
                showSuccessSlip = true
              },
              colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
              shape = RoundedCornerShape(12.dp)
            ) {
              Icon(Icons.Default.PersonAdd, contentDescription = null, modifier = Modifier.size(18.dp))
              Spacer(Modifier.width(6.dp))
              Text("Submit & Register Admission", fontWeight = FontWeight.Bold)
            }
          }
        }
      }
    }
  }
}

@Composable
fun AdmissionSlipView(
  student: Student,
  instituteConfig: InstituteConfig,
  onClose: () -> Unit
) {
  val context = LocalContext.current
  val clipboardManager = LocalClipboardManager.current

  Column(
    modifier = Modifier
      .fillMaxSize()
      .padding(20.dp)
      .verticalScroll(rememberScrollState()),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(14.dp)
  ) {
    Box(
      modifier = Modifier
        .size(56.dp)
        .clip(CircleShape)
        .background(Color(0xFF059669).copy(alpha = 0.15f)),
      contentAlignment = Alignment.Center
    ) {
      Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF059669), modifier = Modifier.size(36.dp))
    }

    Text(
      "Admission Confirmed Successfully!",
      fontWeight = FontWeight.ExtraBold,
      fontSize = 18.sp,
      color = Color(0xFF059669)
    )

    Text(
      "Student record permanently saved in institute database",
      fontSize = 12.sp,
      color = MaterialTheme.colorScheme.onSurfaceVariant
    )

    // Slip Card
    Card(
      shape = RoundedCornerShape(16.dp),
      colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
      border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
      modifier = Modifier.fillMaxWidth()
    ) {
      Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (student.photo.isNotBlank()) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center
          ) {
            Box(
              modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .border(2.dp, Color(0xFF0061A4), CircleShape)
            ) {
              AsyncImage(
                model = student.photo,
                contentDescription = "Admitted Student Photo",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
              )
            }
          }
          Spacer(Modifier.height(4.dp))
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
          Text("Institute:", fontSize = 11.sp, color = Color(0xFF64748B))
          Text(instituteConfig.instituteName, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color(0xFF0061A4))
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
          Text("Student ID:", fontSize = 11.sp, color = Color(0xFF64748B))
          Text(student.id, fontWeight = FontWeight.Bold, fontSize = 12.sp)
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
          Text("Student Name:", fontSize = 11.sp, color = Color(0xFF64748B))
          Text(student.name, fontWeight = FontWeight.Bold, fontSize = 12.sp)
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
          Text("Class & Stream:", fontSize = 11.sp, color = Color(0xFF64748B))
          Text("${student.className} • ${student.stream}", fontWeight = FontWeight.Bold, fontSize = 12.sp)
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
          Text("Batch Allocated:", fontSize = 11.sp, color = Color(0xFF64748B))
          Text(student.batch, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color(0xFF0061A4))
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
          Text("Parent Contact:", fontSize = 11.sp, color = Color(0xFF64748B))
          Text("${student.parentName} (${student.parentContact})", fontSize = 11.sp)
        }

        Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), modifier = Modifier.padding(vertical = 4.dp))

        // Activation Code Highlight
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFFFEF3C7), RoundedCornerShape(10.dp))
            .padding(10.dp),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Column {
            Text("Student/Parent Activation Code:", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF92400E))
            Text(
              "${student.activationCode.take(3)} ${student.activationCode.takeLast(3)}",
              fontSize = 18.sp,
              fontWeight = FontWeight.ExtraBold,
              letterSpacing = 2.sp,
              color = Color(0xFF78350F)
            )
          }

          IconButton(
            onClick = {
              clipboardManager.setText(AnnotatedString(student.activationCode))
              Toast.makeText(context, "Code Copied: ${student.activationCode}", Toast.LENGTH_SHORT).show()
            }
          ) {
            Icon(Icons.Default.ContentCopy, contentDescription = "Copy", tint = Color(0xFF92400E))
          }
        }
      }
    }

    Spacer(Modifier.height(8.dp))

    Button(
      onClick = onClose,
      colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0061A4)),
      shape = RoundedCornerShape(12.dp),
      modifier = Modifier.fillMaxWidth()
    ) {
      Text("Done & Return to Directory", fontWeight = FontWeight.Bold)
    }
  }
}
