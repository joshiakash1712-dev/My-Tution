package com.example.ui.parent

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.example.data.*
import com.example.ui.components.*
import com.example.ui.timetable.DynamicTimetableScreen
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun ParentMainContent(currentTab: Int, onTabChange: (Int) -> Unit = {}) {
  val students by AppRepository.students.collectAsState()
  val child = remember(students) {
    students.firstOrNull() ?: Student(
      id = "STU-000",
      name = "No Linked Student",
      photo = "",
      mobile = "-",
      parentName = "-",
      parentContact = "-",
      email = "-",
      dob = "-",
      gender = "-",
      address = "-",
      school = "-",
      className = "-",
      batch = "No Active Batch",
      stream = "-",
      admissionDate = "-",
      status = "Inactive",
      attendancePercent = 0,
      overallAvg = 0,
      rank = 0,
      strongestSubject = "-",
      weakestSubject = "-",
      recentScores = emptyList()
    )
  }
  val tests by AppRepository.tests.collectAsState()
  val fees by AppRepository.fees.collectAsState()
  val childFee = remember(fees, child) {
    fees.find { it.studentName.contains(child.name, true) }
      ?: fees.firstOrNull()
      ?: FeeRecord(
        id = "F000",
        studentName = child.name,
        feeAmount = 0,
        dueDate = "N/A",
        paidAmount = 0,
        pendingAmount = 0,
        paymentStatus = "No Dues",
        month = "N/A"
      )
  }
  val meetings by AppRepository.meetings.collectAsState()
  val notifications by AppRepository.notifications.collectAsState()

  when (currentTab) {
    0 -> ParentDashboardTab(child, childFee, notifications, tests, onTabChange)
    1 -> ParentAttendanceTab(child)
    2 -> ParentTestPerformanceTab(child, tests)
    3 -> ParentFeeStatusTab(childFee)
    4 -> DynamicTimetableScreen()
    else -> ParentDashboardTab(child, childFee, notifications, tests, onTabChange)
  }
}

@Composable
fun ParentDashboardTab(
  child: Student,
  feeRecord: FeeRecord,
  notifications: List<NotificationItem>,
  tests: List<TestRecord>,
  onTabChange: (Int) -> Unit = {}
) {
  var showReportDialog by remember { mutableStateOf(false) }
  if (showReportDialog) {
    ReportCardDialog(child) { showReportDialog = false }
  }

  LazyColumn(
    modifier = Modifier.fillMaxSize(),
    contentPadding = PaddingValues(16.dp),
    verticalArrangement = Arrangement.spacedBy(16.dp)
  ) {
    // 1. Hero Feature Card
    item {
      HeroFeatureCard(
        title = "Child Academic Passport",
        subtitle = "${child.name} • Class 12 JEE • Rank #${child.rank}",
        tagText = "GUARDIAN ACCESS",
        icon = Icons.Default.FamilyRestroom
      )
    }

    // 2. Quick Action Grid
    item {
      QuickActionGrid(
        card1Title = "Report Card",
        card1Icon = Icons.Default.FactCheck,
        card1Click = { showReportDialog = true },
        card2Title = "Schedule Meeting",
        card2Icon = Icons.Default.Groups,
        card2Click = { onTabChange(4) }
      )
    }

    // 3. Child Core Performance Row
    item {
      Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        MetricBox("Attendance", "${child.attendancePercent}%", "Punctuality Rating: A+", Color(0xFFD1E4FF), Color(0xFF001D36), Modifier.weight(1f))
        MetricBox("Fee Status", feeRecord.paymentStatus, "Pending: ₹${feeRecord.pendingAmount}", if (feeRecord.pendingAmount == 0) Color(0xFFD1F2D1) else Color(0xFFF7D8FF), if (feeRecord.pendingAmount == 0) Color(0xFF072711) else Color(0xFF2B1236), Modifier.weight(1f))
      }
    }

    // 4. Automated Institute Alerts Queue
    item {
      GeoSectionCard(title = "Automated Institute Communications") {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
          notifications.take(3).forEach { notif ->
            Row(
              modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant).padding(12.dp),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.Top
            ) {
              Column(Modifier.weight(1f)) {
                Text(notif.title, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = if (notif.type == "Absence") Color(0xFFEF4444) else MaterialTheme.colorScheme.onSurface)
                Text(notif.message, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 2.dp))
              }
              Text(notif.time, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
          }
        }
      }
    }

    // 5. Recent Academic Standing
    item {
      GeoSectionCard(title = "Recent Academic Performance") {
        val recTest = tests.firstOrNull()
        if (recTest != null) {
          Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("${recTest.testName} • ${recTest.date}", fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Text("Score: ${child.recentScores.firstOrNull()?.second ?: 88} / 100", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Text("AI Faculty Diagnosis: ${recTest.aiSuggestion}", fontSize = 12.sp, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic, color = MaterialTheme.colorScheme.onSurfaceVariant)
          }
        }
      }
    }

    // 6. Adaptive Academic Milestone Planner
    item {
      GeoSectionCard(title = "Adaptive Academic Milestone Planner") {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Column(modifier = Modifier.weight(1f)) {
              Text("Target: AIR < 100 JEE Advanced", fontWeight = FontWeight.Bold, fontSize = 13.sp)
              Text("Day-by-Day sync with ${child.batch} schedule", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Surface(color = Color(0xFF0284C7).copy(alpha = 0.15f), shape = RoundedCornerShape(8.dp)) {
              Text("Active Goal", color = Color(0xFF0284C7), fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
            }
          }

          val milestones = listOf(
            Triple("Calculus & Vectors Mastery", "Target: 90% in upcoming test", true),
            Triple("Electrodynamics Numericals", "Practice 25 timed problems/week", true),
            Triple("Organic Chemistry Reagents", "Complete reaction pathway flashcards", false)
          )

          milestones.forEach { (title, subtitle, completed) ->
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                .padding(10.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
              Icon(
                if (completed) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                contentDescription = null,
                tint = if (completed) Color(0xFF16A34A) else Color(0xFF94A3B8),
                modifier = Modifier.size(18.dp)
              )
              Column(modifier = Modifier.weight(1f)) {
                Text(title, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Text(subtitle, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
              }
            }
          }
        }
      }
    }
  }
}

@Composable
fun ParentAttendanceTab(child: Student) {
  val attendanceList by AppRepository.attendance.collectAsState()
  val childAttendance = remember(attendanceList, child) { attendanceList.filter { it.studentId == child.id || it.studentName == child.name } }
  var isParentAttendanceLoading by remember { mutableStateOf(false) }

  LazyColumn(
    modifier = Modifier.fillMaxSize(),
    contentPadding = PaddingValues(16.dp),
    verticalArrangement = Arrangement.spacedBy(16.dp)
  ) {
    item {
      Column {
        Text("${child.name}'s Attendance Audit", fontWeight = FontWeight.Bold, fontSize = 18.sp)
        Text("Punctuality index is ${child.attendancePercent}%. Absence triggers SMS.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
      }
    }

    item {
      GeoSectionCard(title = "Monthly Punctuality Summary") {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
          Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Present", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("24 Days", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF22C55E))
          }
          Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Absent", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("1 Day", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFFEF4444))
          }
          Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Late", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("0 Days", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
          }
        }
      }
    }

    item { Text("Recent Daily Attendance Logs", fontWeight = FontWeight.Bold, fontSize = 16.sp) }
    
    if (isParentAttendanceLoading) {
      items(3) {
        AttendanceRecordSkeleton()
      }
    } else if (childAttendance.isEmpty()) {
      item {
        Card(
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
          shape = RoundedCornerShape(12.dp),
          modifier = Modifier.fillMaxWidth()
        ) {
          Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
          ) {
            Text("No Attendance Logs Found", fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Text(
              "No attendance records have been logged for ${child.name} yet.",
              fontSize = 12.sp,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              textAlign = TextAlign.Center
            )
          }
        }
      }
    } else {
      items(childAttendance) { att ->
        GeoSectionCard(title = att.batchName, actionText = att.status) {
          Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Date: ${att.date}${if (att.time.isNotBlank()) " • ${att.time}" else ""}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (!att.reason.isNullOrBlank()) {
              Text("Reason / Remark: ${att.reason}", fontSize = 11.sp, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Medium)
            }
          }
        }
      }
    }
  }
}

@Composable
fun ParentTestPerformanceTab(child: Student, tests: List<TestRecord>) {
  LazyColumn(
    modifier = Modifier.fillMaxSize(),
    contentPadding = PaddingValues(16.dp),
    verticalArrangement = Arrangement.spacedBy(14.dp)
  ) {
    item { Text("${child.name}'s Test Scores & AI Insights", fontWeight = FontWeight.Bold, fontSize = 18.sp) }

    item {
      HeroFeatureCard(
        title = "Overall Standing: Rank #${child.rank}",
        subtitle = "Institute Average 74% • ${child.name}'s Average ${child.overallAvg}%",
        tagText = "TOP 2% PERCENTILE",
        icon = Icons.Default.Analytics
      )
    }

    item {
      StudentGradeProgressDashboard(
        student = child,
        tests = tests
      )
    }

    items(tests) { test ->
      val score = test.studentMarks[child.id] ?: 86
      GeoSectionCard(title = test.testName, actionText = "$score / ${test.totalMarks}") {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Text("Conducted on: ${test.date} • Batch: ${test.batch}", fontSize = 11.sp, color = Color(0xFF74777F))
          Text(test.remarks, fontSize = 13.sp, color = Color(0xFF1A1C1E))

          Surface(color = Color(0xFFEEF0F6), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
              Text("🤖 AI Performance Diagnosis:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0061A4))
              Text("Strong Concepts: ${test.aiAnalysisStrong.joinToString(", ")}", fontSize = 12.sp, color = Color(0xFF072711))
              Text("Needs Revision: ${test.aiAnalysisWeak.joinToString(", ")}", fontSize = 12.sp, color = Color(0xFF8C1D18))
              Text("Recommendation: ${test.aiSuggestion}", fontSize = 12.sp, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)
            }
          }
        }
      }
    }
  }
}

@Composable
fun ParentFeeStatusTab(feeRecord: FeeRecord) {
  var showPaymentGateway by remember { mutableStateOf(false) }
  var showReceiptDialog by remember { mutableStateOf(false) }
  var completedPaymentAmount by remember { mutableStateOf(0) }

  if (showPaymentGateway) {
    SimulatedPaymentGatewayDialog(
      amount = feeRecord.pendingAmount,
      feeId = feeRecord.id,
      onDismiss = { showPaymentGateway = false },
      onPaymentSuccess = { payAmt ->
        AppRepository.recordFeePayment(feeRecord.id, payAmt)
        completedPaymentAmount = payAmt
        showPaymentGateway = false
        showReceiptDialog = true
      }
    )
  }

  if (showReceiptDialog) {
    ParentReceiptDialog(
      feeRecord = feeRecord,
      payAmt = completedPaymentAmount,
      onDismiss = { showReceiptDialog = false }
    )
  }

  LazyColumn(
    modifier = Modifier.fillMaxSize(),
    contentPadding = PaddingValues(16.dp),
    verticalArrangement = Arrangement.spacedBy(16.dp)
  ) {
    item { Text("Fee Installments & Receipts", fontWeight = FontWeight.Bold, fontSize = 18.sp) }

    item {
      GeoSectionCard(title = "Current Fee Account Status", actionText = feeRecord.paymentStatus) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Total Annual Tuition Fee:", fontSize = 14.sp)
            Text("₹${feeRecord.feeAmount}", fontSize = 14.sp, fontWeight = FontWeight.Bold)
          }
          Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Paid till date:", fontSize = 14.sp, color = Color(0xFF072711))
            Text("₹${feeRecord.paidAmount}", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFF072711))
          }
          HorizontalDivider(color = Color(0xFFEEF0F6))
          Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Pending Installment Amount:", fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Text("₹${feeRecord.pendingAmount}", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0061A4))
          }
          Text("Due Date: ${feeRecord.dueDate}", fontSize = 12.sp, color = Color(0xFF74777F))

          if (feeRecord.pendingAmount > 0) {
            Button(
              onClick = { showPaymentGateway = true },
              colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF15803D)),
              modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            ) { Text("Pay Online ₹${feeRecord.pendingAmount} Now") }
          }
        }
      }
    }

    item { Text("Payment Receipt History", fontWeight = FontWeight.Bold, fontSize = 16.sp) }
    
    if (feeRecord.paidAmount > 0) {
      item {
        GeoSectionCard(title = "Term 1 Installment Receipt #RCP1092", actionText = "PAID") {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Column {
              Text("Amount: ₹${feeRecord.paidAmount} • Mode: UPI Secure Online", fontSize = 12.sp, color = Color(0xFF94A3B8))
              Text("Transaction: TXN-${feeRecord.id.takeLast(6).uppercase()}", fontSize = 10.sp, color = Color(0xFF64748B))
            }
            IconButton(onClick = { 
              completedPaymentAmount = feeRecord.paidAmount
              showReceiptDialog = true 
            }) {
              Icon(Icons.Default.ReceiptLong, contentDescription = "View Receipt", tint = Color(0xFF38BDF8))
            }
          }
        }
      }
    } else {
      item {
        SectionEmptyState(
          icon = Icons.Default.ReceiptLong,
          title = "No Payment Receipts Generated",
          description = "Payment receipts and downloadable transaction invoices will be stored here automatically once you clear tuition installments.",
          actionLabel = if (feeRecord.pendingAmount > 0) "Pay Installment ₹${feeRecord.pendingAmount}" else null,
          onActionClick = if (feeRecord.pendingAmount > 0) { { showPaymentGateway = true } } else null,
          accentColor = Color(0xFF4ADE80)
        )
      }
    }
  }
}

@Composable
fun SimulatedPaymentGatewayDialog(
  amount: Int,
  feeId: String,
  onDismiss: () -> Unit,
  onPaymentSuccess: (payAmt: Int) -> Unit
) {
  var paymentStep by remember { mutableStateOf(0) } // 0: Select Method, 1: Process Payment, 2: Success
  var selectedMethod by remember { mutableStateOf("UPI") } // "UPI", "Card", "Netbanking"

  // Method details
  var upiId by remember { mutableStateOf("parent@upi") }
  var isUpiVerified by remember { mutableStateOf(true) }

  var cardNumber by remember { mutableStateOf("4532 7182 9381 0029") }
  var cardHolder by remember { mutableStateOf("Rajesh Kumar") }
  var expiryDate by remember { mutableStateOf("12/28") }
  var cvv by remember { mutableStateOf("123") }

  var selectedBank by remember { mutableStateOf("HDFC Bank") }
  val banks = listOf("HDFC Bank", "SBI", "ICICI Bank", "Axis Bank")

  if (paymentStep == 1) {
    LaunchedEffect(Unit) {
      kotlinx.coroutines.delay(350L)
      onPaymentSuccess(amount)
      paymentStep = 2
    }
  }

  AlertDialog(
    onDismissRequest = { if (paymentStep != 1) onDismiss() },
    title = {
      Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(Icons.Default.Security, contentDescription = null, tint = Color(0xFF4ADE80))
        Text(
          text = when (paymentStep) {
            0 -> "FSI Secure Payment Gateway"
            1 -> "Processing Transaction..."
            else -> "Payment Successful 🎉"
          },
          fontWeight = FontWeight.Bold,
          fontSize = 16.sp,
          color = Color(0xFFF8FAFC)
        )
      }
    },
    text = {
      Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
      ) {
        if (paymentStep == 0) {
          // Amount Indicator
          Surface(
            color = Color(0xFF182438),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, Color(0xFF1E2D42)),
            modifier = Modifier.fillMaxWidth()
          ) {
            Row(
              modifier = Modifier.padding(12.dp),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Text("Payable Amount:", fontSize = 12.sp, color = Color(0xFF94A3B8))
              Text("₹$amount", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF38BDF8))
            }
          }

          // Payment Method Selector
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            listOf("UPI", "Card", "Netbanking").forEach { method ->
              val isSel = selectedMethod == method
              Surface(
                color = if (isSel) Color(0xFF0284C7) else Color(0xFF182438),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                  .weight(1f)
                  .border(
                    width = 1.dp,
                    color = if (isSel) Color(0xFF38BDF8) else Color(0xFF1E2D42),
                    shape = RoundedCornerShape(12.dp)
                  )
                  .clickable { selectedMethod = method }
              ) {
                Box(
                  modifier = Modifier.padding(vertical = 10.dp),
                  contentAlignment = Alignment.Center
                ) {
                  Text(
                    text = method,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isSel) Color.White else Color(0xFF94A3B8)
                  )
                }
              }
            }
          }

          Spacer(modifier = Modifier.height(4.dp))

          // Method inputs
          when (selectedMethod) {
            "UPI" -> {
              OutlinedTextField(
                value = upiId,
                onValueChange = { upiId = it },
                label = { Text("UPI ID") },
                shape = RoundedCornerShape(12.dp),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                trailingIcon = {
                  Text(
                    "VERIFIED ✅",
                    color = Color(0xFF15803D),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(end = 8.dp)
                  )
                }
              )
            }
            "Card" -> {
              OutlinedTextField(
                value = cardNumber,
                onValueChange = { cardNumber = it },
                label = { Text("Card Number") },
                shape = RoundedCornerShape(12.dp),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
              )
              OutlinedTextField(
                value = cardHolder,
                onValueChange = { cardHolder = it },
                label = { Text("Cardholder Name") },
                shape = RoundedCornerShape(12.dp),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
              )
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
              ) {
                OutlinedTextField(
                  value = expiryDate,
                  onValueChange = { expiryDate = it },
                  label = { Text("Expiry (MM/YY)") },
                  shape = RoundedCornerShape(12.dp),
                  singleLine = true,
                  modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                  value = cvv,
                  onValueChange = { cvv = it },
                  label = { Text("CVV") },
                  shape = RoundedCornerShape(12.dp),
                  singleLine = true,
                  modifier = Modifier.weight(1f)
                )
              }
            }
            "Netbanking" -> {
              Text("Select Popular Indian Bank", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = Color(0xFF94A3B8))
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
              ) {
                banks.take(2).forEach { bank ->
                  val isSel = selectedBank == bank
                  Surface(
                    color = if (isSel) Color(0xFF0284C7) else Color(0xFF182438),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                      .weight(1f)
                      .border(
                        1.dp,
                        if (isSel) Color(0xFF38BDF8) else Color(0xFF1E2D42),
                        RoundedCornerShape(10.dp)
                      )
                      .clickable { selectedBank = bank }
                  ) {
                    Box(modifier = Modifier.padding(10.dp), contentAlignment = Alignment.Center) {
                      Text(bank, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if (isSel) Color.White else Color(0xFF94A3B8))
                    }
                  }
                }
              }
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
              ) {
                banks.drop(2).forEach { bank ->
                  val isSel = selectedBank == bank
                  Surface(
                    color = if (isSel) Color(0xFF0284C7) else Color(0xFF182438),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                      .weight(1f)
                      .border(
                        1.dp,
                        if (isSel) Color(0xFF38BDF8) else Color(0xFF1E2D42),
                        RoundedCornerShape(10.dp)
                      )
                      .clickable { selectedBank = bank }
                  ) {
                    Box(modifier = Modifier.padding(10.dp), contentAlignment = Alignment.Center) {
                      Text(bank, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if (isSel) Color.White else Color(0xFF94A3B8))
                    }
                  }
                }
              }
            }
          }
        } else if (paymentStep == 1) {
          Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
          ) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary, modifier = Modifier.size(48.dp))
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
              Text("Communicating with payment server...", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Color(0xFFF8FAFC))
              Text("Please do not refresh or press back.", fontSize = 11.sp, color = Color(0xFF94A3B8))
            }
          }
        } else if (paymentStep == 2) {
          Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
          ) {
            Box(
              modifier = Modifier.size(56.dp).clip(CircleShape).background(Color(0xFF064E3B)),
              contentAlignment = Alignment.Center
            ) {
              Icon(Icons.Default.Check, contentDescription = null, tint = Color(0xFF4ADE80), modifier = Modifier.size(32.dp))
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
              Text("₹$amount Received Successfully", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFF4ADE80))
              Text("Tuition fee ledger updated. Receipt generated.", fontSize = 11.sp, color = Color(0xFF94A3B8))
            }
          }
        }
      }
    },
    confirmButton = {
      if (paymentStep == 0) {
        Button(
          onClick = { paymentStep = 1 },
          colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
          shape = RoundedCornerShape(12.dp),
          modifier = Modifier.fillMaxWidth()
        ) {
          Text("Pay Now Securely", color = Color.White)
        }
      } else if (paymentStep == 2) {
        Button(
          onClick = { onDismiss() },
          colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
          shape = RoundedCornerShape(12.dp),
          modifier = Modifier.fillMaxWidth()
        ) {
          Text("Show Dynamic Receipt", color = Color.White)
        }
      }
    },
    dismissButton = {
      if (paymentStep == 0) {
        TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
          Text("Cancel Process", color = Color(0xFF94A3B8))
        }
      }
    },
    containerColor = MaterialTheme.colorScheme.surface,
    shape = RoundedCornerShape(24.dp)
  )
}

@Composable
fun ParentReceiptDialog(
  feeRecord: FeeRecord,
  payAmt: Int,
  onDismiss: () -> Unit
) {
  AlertDialog(
    onDismissRequest = onDismiss,
    title = {
      Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(Icons.Default.ReceiptLong, contentDescription = null, tint = Color(0xFF4ADE80))
        Text("Digital Payment Receipt", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color(0xFFF8FAFC))
      }
    },
    text = {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .clip(RoundedCornerShape(12.dp))
          .background(Color(0xFF0D1526))
          .border(1.dp, Color(0xFF1E2D42), RoundedCornerShape(12.dp))
          .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
      ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
          Text("Receipt No:", fontSize = 11.sp, color = Color(0xFF94A3B8))
          Text("REC-${System.currentTimeMillis().toString().takeLast(8).uppercase()}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF38BDF8))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
          Text("Date & Time:", fontSize = 11.sp, color = Color(0xFF94A3B8))
          Text(AppRepository.getCurrentDateStr() + " • Online", fontSize = 11.sp, color = Color(0xFFF8FAFC))
        }
        HorizontalDivider(color = Color(0xFF1E2D42))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
          Text("Student Name:", fontSize = 12.sp, color = Color(0xFF94A3B8))
          Text(feeRecord.studentName, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFFF8FAFC))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
          Text("Session Term:", fontSize = 12.sp, color = Color(0xFF94A3B8))
          Text(feeRecord.month, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFFCBD5E1))
        }
        HorizontalDivider(color = Color(0xFF1E2D42))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
          Text("Total Installment:", fontSize = 12.sp, color = Color(0xFF94A3B8))
          Text("₹${feeRecord.feeAmount}", fontSize = 12.sp, color = Color(0xFFF8FAFC))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
          Text("Amount Paid:", fontSize = 13.sp, color = Color(0xFF4ADE80), fontWeight = FontWeight.Bold)
          Text("₹$payAmt", fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF4ADE80))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
          Text("Current Balance:", fontSize = 12.sp, color = Color(0xFFF87171))
          Text("₹${(feeRecord.pendingAmount).coerceAtLeast(0)}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFFF87171))
        }
        Spacer(modifier = Modifier.height(4.dp))
        Surface(
          color = Color(0xFF064E3B),
          shape = RoundedCornerShape(8.dp),
          modifier = Modifier.align(Alignment.CenterHorizontally)
        ) {
          Text(
            "ONLINE PAYMENT VERIFIED",
            fontSize = 10.sp,
            fontWeight = FontWeight.ExtraBold,
            color = Color(0xFF6EE7B7),
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
          )
        }
      }
    },
    confirmButton = {
      Button(
        onClick = onDismiss,
        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
      ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
          Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
          Text("Save Receipt PDF", color = Color.White)
        }
      }
    },
    containerColor = MaterialTheme.colorScheme.surface,
    shape = RoundedCornerShape(24.dp)
  )
}

@Composable
fun ParentMeetingSchedulerTab(meetings: List<ParentMeetingItem>) {
  var showMeetingDialog by remember { mutableStateOf(false) }
  var teacherInput by remember { mutableStateOf("") }
  var dateInput by remember { mutableStateOf("") }
  var timeInput by remember { mutableStateOf("") }
  var notesInput by remember { mutableStateOf("") }

  if (showMeetingDialog) {
    AlertDialog(
      onDismissRequest = { showMeetingDialog = false },
      title = { Text("Schedule 1-on-1 Faculty Meeting", fontWeight = FontWeight.Bold) },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
          OutlinedTextField(value = teacherInput, onValueChange = { teacherInput = it }, label = { Text("Mentor Faculty") }, modifier = Modifier.fillMaxWidth())
          OutlinedTextField(value = dateInput, onValueChange = { dateInput = it }, label = { Text("Preferred Date (dd/mm/yyyy)") }, modifier = Modifier.fillMaxWidth())
          OutlinedTextField(value = timeInput, onValueChange = { timeInput = it }, label = { Text("Preferred Time Slot") }, modifier = Modifier.fillMaxWidth())
          OutlinedTextField(value = notesInput, onValueChange = { notesInput = it }, label = { Text("Agenda / Discussion Notes") }, modifier = Modifier.fillMaxWidth().height(80.dp))
        }
      },
      confirmButton = {
        Button(
          onClick = {
            if (teacherInput.isNotBlank()) {
              AppRepository.scheduleParentMeeting(teacherInput, dateInput, timeInput, notesInput)
              showMeetingDialog = false
            }
          },
          colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0061A4))
        ) { Text("Confirm Meeting") }
      },
      dismissButton = { TextButton(onClick = { showMeetingDialog = false }) { Text("Cancel") } },
      containerColor = MaterialTheme.colorScheme.surface,
      shape = RoundedCornerShape(24.dp)
    )
  }

  LazyColumn(
    modifier = Modifier.fillMaxSize(),
    contentPadding = PaddingValues(16.dp),
    verticalArrangement = Arrangement.spacedBy(16.dp)
  ) {
    item {
      Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Column {
          Text("Parent-Teacher Meeting Scheduler", fontWeight = FontWeight.Bold, fontSize = 18.sp)
          Text("Schedule direct discussions with course faculty.", fontSize = 12.sp, color = Color(0xFF74777F))
        }
        Button(onClick = { showMeetingDialog = true }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0061A4)), shape = RoundedCornerShape(16.dp)) {
          Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
          Spacer(Modifier.width(4.dp))
          Text("Schedule", fontSize = 12.sp)
        }
      }
    }

    items(meetings) { m ->
      GeoSectionCard(title = "${m.teacherName} • ${m.date} at ${m.time}", actionText = m.status) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
          Text("Agenda: ${m.notes}", fontSize = 13.sp, color = Color(0xFF1A1C1E))
          Surface(color = if (m.status == "Scheduled") Color(0xFFD1E4FF) else Color(0xFFD1F2D1), shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth()) {
            Text("📌 Action Items: ${m.actionItems}", fontSize = 12.sp, color = Color(0xFF001D36), modifier = Modifier.padding(10.dp))
          }
        }
      }
    }
  }
}
