package com.example.ui.admin

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.AppRepository
import com.example.data.Batch
import com.example.data.Student
import java.io.BufferedReader
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.*

/**
 * Parsed student record with validation metadata
 */
data class ParsedStudentRow(
  val rawIndex: Int,
  val student: Student,
  val isDuplicateId: Boolean,
  val validationIssues: List<String> = emptyList()
)

object StudentCsvParser {

  private val SAMPLE_CSV_TEMPLATE = """
    Name,ID,Class,Batch,Mobile,Parent Name,Parent Contact,Email,Stream,Fees Paid,Total Fees
    Aarav Sharma,STU_101,10th Standard,Morning Alpha,9876543210,Rajesh Sharma,9876543211,aarav.s@example.com,Science,15000,25000
    Diya Patel,STU_102,12th Standard,Evening Beta,9876543212,Sanjay Patel,9876543213,diya.p@example.com,Commerce,20000,30000
    Rohan Verma,STU_103,9th Standard,Weekend Delta,9876543214,Vikram Verma,9876543215,rohan.v@example.com,General,12000,20000
    Ananya Iyer,STU_104,11th Standard,Morning Alpha,9876543216,Raman Iyer,9876543217,ananya.i@example.com,Science,18000,28000
  """.trimIndent()

  fun getSampleCsv(): String = SAMPLE_CSV_TEMPLATE

  /**
   * Reads text from a content URI
   */
  fun readTextFromUri(context: Context, uri: Uri): String? {
    return try {
      context.contentResolver.openInputStream(uri)?.use { inputStream ->
        BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8)).use { reader ->
          reader.readText()
        }
      }
    } catch (e: Exception) {
      e.printStackTrace()
      null
    }
  }

  /**
   * Robust CSV Line Tokenizer handling quotes and commas/semicolons/tabs
   */
  fun tokenizeLine(line: String, delimiter: Char = ','): List<String> {
    val tokens = mutableListOf<String>()
    val sb = java.lang.StringBuilder()
    var inQuotes = false
    var i = 0

    while (i < line.length) {
      val c = line[i]
      if (c == '"') {
        if (inQuotes && i + 1 < line.length && line[i + 1] == '"') {
          // Escaped quote
          sb.append('"')
          i++
        } else {
          inQuotes = !inQuotes
        }
      } else if (c == delimiter && !inQuotes) {
        tokens.add(sb.toString().trim())
        sb.setLength(0)
      } else {
        sb.append(c)
      }
      i++
    }
    tokens.add(sb.toString().trim())
    return tokens
  }

  /**
   * Detects delimiter from first non-empty lines
   */
  fun detectDelimiter(csvText: String): Char {
    val firstLine = csvText.lines().firstOrNull { it.isNotBlank() } ?: return ','
    val commaCount = firstLine.count { it == ',' }
    val semiCount = firstLine.count { it == ';' }
    val tabCount = firstLine.count { it == '\t' }

    return when {
      tabCount > commaCount && tabCount > semiCount -> '\t'
      semiCount > commaCount && semiCount > tabCount -> ';'
      else -> ','
    }
  }

  /**
   * Main parsing function
   */
  fun parseCsv(
    csvContent: String,
    existingStudents: List<Student>,
    availableBatches: List<Batch>,
    defaultBatch: String = "Morning Alpha"
  ): List<ParsedStudentRow> {
    if (csvContent.isBlank()) return emptyList()

    val delimiter = detectDelimiter(csvContent)
    val lines = csvContent.lines().filter { it.isNotBlank() }
    if (lines.isEmpty()) return emptyList()

    val firstTokens = tokenizeLine(lines.first(), delimiter).map { it.lowercase().trim().replace(" ", "").replace("_", "") }

    // Column Index Mapping
    var nameIdx = -1
    var idIdx = -1
    var classIdx = -1
    var batchIdx = -1
    var mobileIdx = -1
    var parentNameIdx = -1
    var parentContactIdx = -1
    var emailIdx = -1
    var streamIdx = -1
    var feesPaidIdx = -1
    var totalFeesIdx = -1
    var attendanceIdx = -1

    var hasHeader = false

    firstTokens.forEachIndexed { index, token ->
      when {
        token in listOf("name", "studentname", "fullname", "student") -> { nameIdx = index; hasHeader = true }
        token in listOf("id", "studentid", "rollno", "uniqueid", "rollnumber") -> { idIdx = index; hasHeader = true }
        token in listOf("class", "grade", "standard", "classname") -> { classIdx = index; hasHeader = true }
        token in listOf("batch", "batchname", "section") -> { batchIdx = index; hasHeader = true }
        token in listOf("mobile", "phone", "contact", "studentmobile", "phone_number", "phonenumber") -> { mobileIdx = index; hasHeader = true }
        token in listOf("parentname", "fathername", "guardianname", "mothername", "parent") -> { parentNameIdx = index; hasHeader = true }
        token in listOf("parentcontact", "parentmobile", "fathercontact", "guardiancontact", "parentphone") -> { parentContactIdx = index; hasHeader = true }
        token in listOf("email", "studentemail", "mail") -> { emailIdx = index; hasHeader = true }
        token in listOf("stream", "branch", "subject") -> { streamIdx = index; hasHeader = true }
        token in listOf("feespaid", "paidfees", "paid", "amountpaid") -> { feesPaidIdx = index; hasHeader = true }
        token in listOf("totalfees", "totalfee", "fees", "fee", "coursefee") -> { totalFeesIdx = index; hasHeader = true }
        token in listOf("attendance", "attendancepercent", "attendance%") -> { attendanceIdx = index; hasHeader = true }
      }
    }

    val rowsToProcess = if (hasHeader) lines.drop(1) else lines
    val existingIds = existingStudents.map { it.id.lowercase() }.toSet()
    val generatedIdBase = System.currentTimeMillis() % 100000

    val fallbackBatch = if (availableBatches.isNotEmpty()) availableBatches.first().name else defaultBatch
    val todayDateStr = SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date())

    val parsedList = mutableListOf<ParsedStudentRow>()
    val sessionIds = mutableSetOf<String>()

    rowsToProcess.forEachIndexed { lineIdx, line ->
      val tokens = tokenizeLine(line, delimiter)
      if (tokens.isEmpty() || tokens.all { it.isBlank() }) return@forEachIndexed

      fun getToken(idx: Int): String {
        return if (idx >= 0 && idx < tokens.size) tokens[idx].trim() else ""
      }

      // If no header found, map positional by default
      val name = if (hasHeader && nameIdx >= 0) getToken(nameIdx) else tokens.getOrNull(0) ?: ""
      if (name.isBlank()) return@forEachIndexed // Skip rows with no name

      var id = if (hasHeader && idIdx >= 0) getToken(idIdx) else tokens.getOrNull(1) ?: ""
      val isAutoId = id.isBlank()
      if (isAutoId) {
        id = "STU_${generatedIdBase + lineIdx + 1}"
      }

      val className = if (hasHeader && classIdx >= 0) getToken(classIdx).ifBlank { "10th Standard" } else tokens.getOrNull(2) ?: "10th Standard"
      val rawBatch = if (hasHeader && batchIdx >= 0) getToken(batchIdx) else tokens.getOrNull(3) ?: ""
      val matchedBatch = availableBatches.find { it.name.equals(rawBatch, ignoreCase = true) }?.name
        ?: rawBatch.ifBlank { fallbackBatch }

      val mobile = if (hasHeader && mobileIdx >= 0) getToken(mobileIdx) else tokens.getOrNull(4) ?: "9876543210"
      val parentName = if (hasHeader && parentNameIdx >= 0) getToken(parentNameIdx) else tokens.getOrNull(5) ?: "Guardian of $name"
      val parentContact = if (hasHeader && parentContactIdx >= 0) getToken(parentContactIdx) else tokens.getOrNull(6) ?: mobile
      val email = if (hasHeader && emailIdx >= 0) getToken(emailIdx) else tokens.getOrNull(7) ?: "${name.lowercase().replace(" ", "")}@tuition.edu"
      val stream = if (hasHeader && streamIdx >= 0) getToken(streamIdx).ifBlank { "General" } else tokens.getOrNull(8) ?: "General"

      val feesPaid = (if (hasHeader && feesPaidIdx >= 0) getToken(feesPaidIdx).filter { it.isDigit() }.toIntOrNull() else tokens.getOrNull(9)?.filter { it.isDigit() }?.toIntOrNull()) ?: 15000
      val totalFees = (if (hasHeader && totalFeesIdx >= 0) getToken(totalFeesIdx).filter { it.isDigit() }.toIntOrNull() else tokens.getOrNull(10)?.filter { it.isDigit() }?.toIntOrNull()) ?: 25000
      val attendance = (if (hasHeader && attendanceIdx >= 0) getToken(attendanceIdx).filter { it.isDigit() }.toIntOrNull() else tokens.getOrNull(11)?.filter { it.isDigit() }?.toIntOrNull()) ?: 85

      val isDuplicate = existingIds.contains(id.lowercase()) || sessionIds.contains(id.lowercase())
      sessionIds.add(id.lowercase())

      val validationIssues = mutableListOf<String>()
      if (mobile.length < 10) validationIssues.add("Short phone number")
      if (isDuplicate) validationIssues.add("Duplicate ID detected")

      val dob = "15 Jan 2008"
      val gender = "Male"
      val address = "Main City Center"
      val school = "City High School"

      val student = Student(
        id = id,
        name = name,
        mobile = mobile.ifBlank { "9876543210" },
        parentName = parentName.ifBlank { "Guardian of $name" },
        parentContact = parentContact.ifBlank { "9876543210" },
        email = email,
        dob = dob,
        gender = gender,
        address = address,
        school = school,
        className = className,
        batch = matchedBatch,
        stream = stream,
        admissionDate = todayDateStr,
        status = "Active",
        attendancePercent = attendance.coerceIn(0, 100)
      )

      parsedList.add(
        ParsedStudentRow(
          rawIndex = lineIdx + 1,
          student = student,
          isDuplicateId = isDuplicate,
          validationIssues = validationIssues
        )
      )
    }

    return parsedList
  }
}

/**
 * Full-featured CSV Student Importer Dialog with file picker, sample template, and preview
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudentCsvImportDialog(
  onDismiss: () -> Unit,
  onImportComplete: (List<Student>) -> Unit
) {
  val context = LocalContext.current
  val clipboardManager = LocalClipboardManager.current

  val existingStudents by AppRepository.students.collectAsState()
  val activeBatches by AppRepository.batches.collectAsState()

  var inputMode by remember { mutableIntStateOf(0) } // 0 = Pick File / Paste, 1 = Preview & Import
  var selectedFileName by remember { mutableStateOf<String?>(null) }
  var rawCsvText by remember { mutableStateOf("") }
  var parsedRows by remember { mutableStateOf<List<ParsedStudentRow>>(emptyList()) }
  var duplicateResolution by remember { mutableStateOf("auto_id") } // "skip", "auto_id", "overwrite"
  var overrideBatchSelection by remember { mutableStateOf("Keep File Batch") }
  var isProcessing by remember { mutableStateOf(false) }

  // File Picker Launcher for CSV/Text files
  val filePickerLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.OpenDocument()
  ) { uri: Uri? ->
    if (uri != null) {
      val text = StudentCsvParser.readTextFromUri(context, uri)
      if (!text.isNullOrBlank()) {
        rawCsvText = text
        selectedFileName = uri.lastPathSegment ?: "student_records.csv"
        parsedRows = StudentCsvParser.parseCsv(text, existingStudents, activeBatches)
        if (parsedRows.isNotEmpty()) {
          inputMode = 1 // Auto-switch to preview
          Toast.makeText(context, "Loaded ${parsedRows.size} students from file!", Toast.LENGTH_SHORT).show()
        } else {
          Toast.makeText(context, "No valid student records found in file.", Toast.LENGTH_SHORT).show()
        }
      } else {
        Toast.makeText(context, "Could not read data from selected file.", Toast.LENGTH_SHORT).show()
      }
    }
  }

  Dialog(
    onDismissRequest = onDismiss,
    properties = DialogProperties(usePlatformDefaultWidth = false)
  ) {
    Surface(
      modifier = Modifier
        .fillMaxWidth(0.95f)
        .fillMaxHeight(0.90f),
      shape = RoundedCornerShape(20.dp),
      color = Color.White,
      tonalElevation = 6.dp
    ) {
      Column(
        modifier = Modifier
          .fillMaxSize()
          .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
      ) {
        // Header
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
                .size(42.dp)
                .clip(CircleShape)
                .background(Color(0xFF0061A4)),
              contentAlignment = Alignment.Center
            ) {
              Icon(Icons.Default.FileUpload, contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
            }
            Column {
              Text("Import Students from CSV", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color(0xFF0F172A))
              Text("Bulk populate students via device storage or CSV text", fontSize = 11.sp, color = Color(0xFF64748B))
            }
          }

          IconButton(onClick = onDismiss) {
            Icon(Icons.Default.Close, contentDescription = "Close", tint = Color(0xFF64748B))
          }
        }

        // Stepper Navigation / Toggle Tabs
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          Surface(
            color = if (inputMode == 0) Color(0xFF0061A4) else Color(0xFFF1F5F9),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier
              .weight(1f)
              .clickable { inputMode = 0 }
          ) {
            Row(
              modifier = Modifier.padding(vertical = 10.dp),
              horizontalArrangement = Arrangement.Center,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Icon(
                Icons.Default.UploadFile,
                contentDescription = null,
                tint = if (inputMode == 0) Color.White else Color(0xFF475569),
                modifier = Modifier.size(16.dp)
              )
              Spacer(Modifier.width(6.dp))
              Text(
                "1. Choose / Paste CSV",
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                color = if (inputMode == 0) Color.White else Color(0xFF475569),
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis
              )
            }
          }

          Surface(
            color = if (inputMode == 1) Color(0xFF0061A4) else Color(0xFFF1F5F9),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier
              .weight(1f)
              .clickable {
                if (rawCsvText.isNotBlank()) {
                  parsedRows = StudentCsvParser.parseCsv(rawCsvText, existingStudents, activeBatches)
                  inputMode = 1
                } else {
                  Toast.makeText(context, "Please select a file or enter CSV text first.", Toast.LENGTH_SHORT).show()
                }
              }
          ) {
            Row(
              modifier = Modifier.padding(vertical = 10.dp),
              horizontalArrangement = Arrangement.Center,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Icon(
                Icons.Default.FactCheck,
                contentDescription = null,
                tint = if (inputMode == 1) Color.White else Color(0xFF475569),
                modifier = Modifier.size(16.dp)
              )
              Spacer(Modifier.width(6.dp))
              Text(
                "2. Preview (${parsedRows.size})",
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                color = if (inputMode == 1) Color.White else Color(0xFF475569),
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis
              )
            }
          }
        }

        HorizontalDivider(color = Color(0xFFE2E8F0))

        if (inputMode == 0) {
          // ================= STEP 1: FILE PICKER & RAW PASTE =================
          LazyColumn(
            modifier = Modifier
              .weight(1f)
              .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(14.dp)
          ) {
            // Pick from Device Storage Action Card
            item {
              Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFF0F9FF)),
                border = BorderStroke(1.5.dp, Color(0xFFBAE6FD)),
                modifier = Modifier
                  .fillMaxWidth()
                  .clickable {
                    try {
                      filePickerLauncher.launch(arrayOf("text/comma-separated-values", "text/csv", "text/plain", "application/csv", "*/*"))
                    } catch (e: Exception) {
                      filePickerLauncher.launch(arrayOf("*/*"))
                    }
                  }
              ) {
                Column(
                  modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                  horizontalAlignment = Alignment.CenterHorizontally,
                  verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                  Box(
                    modifier = Modifier
                      .size(52.dp)
                      .clip(CircleShape)
                      .background(Color(0xFF0061A4)),
                    contentAlignment = Alignment.Center
                  ) {
                    Icon(Icons.Default.DriveFolderUpload, contentDescription = null, tint = Color.White, modifier = Modifier.size(28.dp))
                  }
                  Text("Select CSV File from Storage", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color(0xFF0C4A6E))
                  Text(
                    "Tap to browse device downloads, documents, or cloud drives (.csv or .txt)",
                    fontSize = 11.sp,
                    color = Color(0xFF0369A1),
                    textAlign = TextAlign.Center
                  )

                  if (selectedFileName != null) {
                    Surface(
                      color = Color(0xFFE0F2FE),
                      shape = RoundedCornerShape(8.dp),
                      modifier = Modifier.padding(top = 4.dp)
                    ) {
                      Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                      ) {
                        Icon(Icons.Default.InsertDriveFile, contentDescription = null, tint = Color(0xFF0284C7), modifier = Modifier.size(16.dp))
                        Text(selectedFileName!!, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color(0xFF0369A1))
                      }
                    }
                  }
                }
              }
            }

            // Quick Actions: Sample Template & Format Info
            item {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
              ) {
                OutlinedButton(
                  onClick = {
                    rawCsvText = StudentCsvParser.getSampleCsv()
                    selectedFileName = "sample_template.csv"
                    parsedRows = StudentCsvParser.parseCsv(rawCsvText, existingStudents, activeBatches)
                    Toast.makeText(context, "Loaded pre-filled sample CSV template!", Toast.LENGTH_SHORT).show()
                  },
                  shape = RoundedCornerShape(10.dp),
                  modifier = Modifier.weight(1f),
                  colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF0061A4)),
                  border = BorderStroke(1.dp, Color(0xFF93C5FD))
                ) {
                  Icon(Icons.Default.AutoFixHigh, contentDescription = null, modifier = Modifier.size(16.dp))
                  Spacer(Modifier.width(6.dp))
                  Text("Use Sample Data", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }

                OutlinedButton(
                  onClick = {
                    clipboardManager.setText(AnnotatedString(StudentCsvParser.getSampleCsv()))
                    Toast.makeText(context, "CSV template headers copied to clipboard!", Toast.LENGTH_SHORT).show()
                  },
                  shape = RoundedCornerShape(10.dp),
                  modifier = Modifier.weight(1f),
                  colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF475569)),
                  border = BorderStroke(1.dp, Color(0xFFCBD5E1))
                ) {
                  Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                  Spacer(Modifier.width(6.dp))
                  Text("Copy Template", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
              }
            }

            // Direct Text Paste Area
            item {
              Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.SpaceBetween,
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  Text("Or Paste Raw CSV Text:", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color(0xFF334155))
                  if (rawCsvText.isNotEmpty()) {
                    TextButton(onClick = { rawCsvText = ""; selectedFileName = null; parsedRows = emptyList() }) {
                      Text("Clear", fontSize = 11.sp, color = Color(0xFFDC2626))
                    }
                  }
                }

                OutlinedTextField(
                  value = rawCsvText,
                  onValueChange = {
                    rawCsvText = it
                    parsedRows = StudentCsvParser.parseCsv(it, existingStudents, activeBatches)
                  },
                  placeholder = {
                    Text(
                      "Name, ID, Class, Batch, Mobile, Parent Name, Parent Contact, Email, Stream, Fees Paid, Total Fees\nJohn Doe, STU_101, 10th Standard, Morning Alpha, 9876543210, Bob Doe, 9876543211, ...",
                      fontSize = 11.sp,
                      color = Color(0xFF94A3B8),
                      fontFamily = FontFamily.Monospace
                    )
                  },
                  modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp),
                  shape = RoundedCornerShape(12.dp),
                  textStyle = LocalTextStyle.current.copy(fontSize = 11.sp, fontFamily = FontFamily.Monospace),
                  colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFF0061A4),
                    unfocusedBorderColor = Color(0xFFCBD5E1)
                  )
                )
              }
            }
          }

          // Bottom Action to proceed to Preview
          Button(
            onClick = {
              if (rawCsvText.isNotBlank()) {
                parsedRows = StudentCsvParser.parseCsv(rawCsvText, existingStudents, activeBatches)
                if (parsedRows.isNotEmpty()) {
                  inputMode = 1
                } else {
                  Toast.makeText(context, "No valid rows detected. Ensure each line has student details.", Toast.LENGTH_LONG).show()
                }
              } else {
                Toast.makeText(context, "Please pick a file or paste CSV text first.", Toast.LENGTH_SHORT).show()
              }
            },
            enabled = rawCsvText.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0061A4))
          ) {
            Text("Review Parsed Records (${parsedRows.size})", fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Spacer(Modifier.width(6.dp))
            Icon(Icons.Default.ArrowForward, contentDescription = null, modifier = Modifier.size(18.dp))
          }

        } else {
          // ================= STEP 2: PREVIEW & INGESTION CONFIG =================
          val duplicateCount = remember(parsedRows) { parsedRows.count { it.isDuplicateId } }

          LazyColumn(
            modifier = Modifier
              .weight(1f)
              .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
          ) {
            // KPI Summary Grid
            item {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
              ) {
                Surface(
                  color = Color(0xFFEFF6FF),
                  shape = RoundedCornerShape(10.dp),
                  modifier = Modifier.weight(1f)
                ) {
                  Column(modifier = Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("${parsedRows.size}", fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, color = Color(0xFF0061A4), maxLines = 1, softWrap = false)
                    Text("Total Parsed", fontSize = 10.sp, color = Color(0xFF1E40AF), maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
                  }
                }

                Surface(
                  color = Color(0xFFDCFCE7),
                  shape = RoundedCornerShape(10.dp),
                  modifier = Modifier.weight(1f)
                ) {
                  Column(modifier = Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("${parsedRows.size - duplicateCount}", fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, color = Color(0xFF15803D), maxLines = 1, softWrap = false)
                    Text("Unique / Ready", fontSize = 10.sp, color = Color(0xFF166534), maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
                  }
                }

                Surface(
                  color = if (duplicateCount > 0) Color(0xFFFEF3C7) else Color(0xFFF1F5F9),
                  shape = RoundedCornerShape(10.dp),
                  modifier = Modifier.weight(1f)
                ) {
                  Column(modifier = Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("$duplicateCount", fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, color = if (duplicateCount > 0) Color(0xFFD97706) else Color(0xFF64748B), maxLines = 1, softWrap = false)
                    Text("Duplicate IDs", fontSize = 10.sp, color = if (duplicateCount > 0) Color(0xFFB45309) else Color(0xFF64748B), maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
                  }
                }
              }
            }

            // Duplicate Handling Options (if duplicates detected)
            if (duplicateCount > 0) {
              item {
                Card(
                  shape = RoundedCornerShape(12.dp),
                  colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFBEB)),
                  border = BorderStroke(1.dp, Color(0xFFFDE68A)),
                  modifier = Modifier.fillMaxWidth()
                ) {
                  Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                  ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                      Icon(Icons.Default.WarningAmber, contentDescription = null, tint = Color(0xFFD97706), modifier = Modifier.size(18.dp))
                      Text("Duplicate ID Strategy:", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color(0xFF92400E))
                    }
                    Row(
                      modifier = Modifier.fillMaxWidth(),
                      horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                      listOf(
                        "auto_id" to "Auto-Assign New ID",
                        "overwrite" to "Overwrite Existing",
                        "skip" to "Skip Duplicates"
                      ).forEach { (mode, label) ->
                        val isSel = duplicateResolution == mode
                        FilterChip(
                          selected = isSel,
                          onClick = { duplicateResolution = mode },
                          label = {
                            Text(
                              text = label,
                              fontSize = 10.sp,
                              fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                              maxLines = 1,
                              softWrap = false,
                              overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                          },
                          colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFFD97706),
                            selectedLabelColor = Color.White
                          )
                        )
                      }
                    }
                  }
                }
              }
            }

            // Batch Assignment Strategy
            item {
              Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
                border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                modifier = Modifier.fillMaxWidth()
              ) {
                Column(
                  modifier = Modifier.padding(12.dp),
                  verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                  Text("Target Batch Assignment:", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color(0xFF334155))
                  Row(
                    modifier = Modifier
                      .fillMaxWidth()
                      .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                  ) {
                    val batchOptions = listOf("Keep File Batch") + activeBatches.map { it.name }
                    batchOptions.forEach { bName ->
                      val isSel = overrideBatchSelection == bName
                      FilterChip(
                        selected = isSel,
                        onClick = { overrideBatchSelection = bName },
                        label = {
                          Text(
                            text = bName,
                            fontSize = 10.sp,
                            fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                            maxLines = 1,
                            softWrap = false,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                          )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                          selectedContainerColor = Color(0xFF0061A4),
                          selectedLabelColor = Color.White
                        )
                      )
                    }
                  }
                }
              }
            }

            // Parsed Records List
            item {
              Text(
                "Parsed Learners (${parsedRows.size} entries):",
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                color = Color(0xFF334155)
              )
            }

            items(parsedRows) { row ->
              val stu = row.student
              Surface(
                color = Color.White,
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, if (row.isDuplicateId) Color(0xFFFDE68A) else Color(0xFFE2E8F0)),
                modifier = Modifier.fillMaxWidth()
              ) {
                Row(
                  modifier = Modifier
                    .fillMaxWidth()
                    .padding(10.dp),
                  horizontalArrangement = Arrangement.SpaceBetween,
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f).padding(end = 8.dp)
                  ) {
                    Box(
                      modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF0061A4).copy(alpha = 0.12f)),
                      contentAlignment = Alignment.Center
                    ) {
                      Text(
                        stu.name.take(2).uppercase(),
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0061A4),
                        fontSize = 12.sp,
                        maxLines = 1,
                        softWrap = false
                      )
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                      Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(stu.name, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFF0F172A), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                        if (row.isDuplicateId) {
                          Surface(
                            color = Color(0xFFFEF3C7),
                            shape = RoundedCornerShape(4.dp)
                          ) {
                            Text(
                              "DUPLICATE",
                              fontSize = 8.sp,
                              fontWeight = FontWeight.Bold,
                              color = Color(0xFFB45309),
                              maxLines = 1,
                              softWrap = false,
                              overflow = TextOverflow.Ellipsis,
                              modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                            )
                          }
                        }
                      }
                      Text(
                        "ID: ${stu.id} • ${if (overrideBatchSelection == "Keep File Batch") stu.batch else overrideBatchSelection} • ${stu.className}",
                        fontSize = 10.sp,
                        color = Color(0xFF64748B),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                      )
                      Text(
                        "Mobile: ${stu.mobile} • Parent: ${stu.parentName}",
                        fontSize = 9.sp,
                        color = Color(0xFF94A3B8),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                      )
                    }
                  }

                  Surface(
                    color = Color(0xFFF1F5F9),
                    shape = RoundedCornerShape(6.dp)
                  ) {
                    Text(
                      "${stu.stream} • ${stu.attendancePercent}%",
                      fontSize = 10.sp,
                      fontWeight = FontWeight.Bold,
                      color = Color(0xFF475569),
                      maxLines = 1,
                      softWrap = false,
                      overflow = TextOverflow.Ellipsis,
                      modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
                    )
                  }
                }
              }
            }
          }

          // Import Confirmation CTA Row
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            OutlinedButton(
              onClick = { inputMode = 0 },
              shape = RoundedCornerShape(12.dp),
              modifier = Modifier.weight(1f)
            ) {
              Icon(Icons.Default.ArrowBack, contentDescription = null, modifier = Modifier.size(16.dp))
              Spacer(Modifier.width(4.dp))
              Text("Back", fontWeight = FontWeight.Bold)
            }

            Button(
              onClick = {
                isProcessing = true
                val finalStudents = mutableListOf<Student>()
                var generatedCounter = System.currentTimeMillis() % 100000

                parsedRows.forEach { row ->
                  val baseStu = row.student
                  val finalBatch = if (overrideBatchSelection == "Keep File Batch") baseStu.batch else overrideBatchSelection

                  if (row.isDuplicateId) {
                    when (duplicateResolution) {
                      "skip" -> {
                        // Skip this student
                      }
                      "overwrite" -> {
                        finalStudents.add(baseStu.copy(batch = finalBatch))
                      }
                      else -> { // auto_id
                        generatedCounter++
                        finalStudents.add(
                          baseStu.copy(
                            id = "STU_$generatedCounter",
                            batch = finalBatch
                          )
                        )
                      }
                    }
                  } else {
                    finalStudents.add(baseStu.copy(batch = finalBatch))
                  }
                }

                if (finalStudents.isNotEmpty()) {
                  AppRepository.addStudents(finalStudents)
                  Toast.makeText(context, "Successfully imported ${finalStudents.size} students!", Toast.LENGTH_LONG).show()
                  onImportComplete(finalStudents)
                  onDismiss()
                } else {
                  Toast.makeText(context, "No students to import after applying duplicate filter.", Toast.LENGTH_SHORT).show()
                  isProcessing = false
                }
              },
              enabled = !isProcessing && parsedRows.isNotEmpty(),
              modifier = Modifier.weight(2f),
              shape = RoundedCornerShape(12.dp),
              colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0061A4))
            ) {
              if (isProcessing) {
                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
                Text("Importing...", fontWeight = FontWeight.Bold)
              } else {
                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Confirm Import (${parsedRows.size})", fontWeight = FontWeight.Bold, fontSize = 14.sp)
              }
            }
          }
        }
      }
    }
  }
}
