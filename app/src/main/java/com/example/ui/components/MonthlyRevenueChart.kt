package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import com.example.ui.theme.pressSpring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.StudentFeePayment
import com.example.utils.CurrencyFormatter
import com.example.ui.theme.NumberTypography
import com.example.ui.theme.HeroStatText
import com.example.ui.theme.CardStatText
import com.example.ui.theme.InlineStatText
import com.example.ui.theme.MicroLabelText
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.max

data class MonthlyRevenueDataPoint(
  val monthKey: String,          // e.g. "August 2026"
  val shortMonth: String,        // e.g. "Aug"
  val year: Int,                 // e.g. 2026
  val monthIndex: Int,           // 0..11
  val collectedAmount: Double,   // Sum of Paid + Partial
  val pendingAmount: Double,     // Sum of Pending
  val totalAmount: Double,       // Total Billed
  val transactionCount: Int,
  val paidCount: Int,
  val collectionRate: Float,     // 0..100%
  val modeBreakdown: Map<String, Double>,
  val typeBreakdown: Map<String, Double>,
  val latestTimestamp: Long
)

/**
 * Calculates monthly revenue metrics dynamically from the Room SQLite fee database.
 */
fun calculateMonthlyRevenueFromPayments(payments: List<StudentFeePayment>): List<MonthlyRevenueDataPoint> {
  if (payments.isEmpty()) return emptyList()

  val monthNames = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
  val fullMonthNames = listOf(
    "January", "February", "March", "April", "May", "June",
    "July", "August", "September", "October", "November", "December"
  )

  // Group by month string (or parse from paymentDate / timestamp if month string is missing)
  val grouped = payments.groupBy { payment ->
    if (payment.month.isNotBlank()) {
      payment.month.trim()
    } else {
      val cal = Calendar.getInstance().apply { timeInMillis = payment.timestamp }
      val m = cal.get(Calendar.MONTH)
      val y = cal.get(Calendar.YEAR)
      "${fullMonthNames[m]} $y"
    }
  }

  val points = grouped.map { (mKey, mPayments) ->
    val collected = mPayments.filter { it.paymentStatus.equals("Paid", ignoreCase = true) || it.paymentStatus.equals("Partial", ignoreCase = true) }
      .sumOf { it.amount }
    val pending = mPayments.filter { it.paymentStatus.equals("Pending", ignoreCase = true) || it.paymentStatus.equals("Overdue", ignoreCase = true) }
      .sumOf { it.amount }
    val total = collected + pending
    val paidCount = mPayments.count { it.paymentStatus.equals("Paid", ignoreCase = true) }

    val modeMap = mutableMapOf<String, Double>()
    val typeMap = mutableMapOf<String, Double>()

    mPayments.forEach { p ->
      val m = p.paymentMode.ifBlank { "UPI" }
      modeMap[m] = (modeMap[m] ?: 0.0) + p.amount
      val t = p.feeType.ifBlank { "Tuition" }
      typeMap[t] = (typeMap[t] ?: 0.0) + p.amount
    }

    // Determine short month and sort order
    var shortM = "Mth"
    var mIndex = 0
    var yr = 2026

    for (i in fullMonthNames.indices) {
      if (mKey.contains(fullMonthNames[i], ignoreCase = true)) {
        shortM = monthNames[i]
        mIndex = i
        break
      }
    }

    val yearRegex = "\\b(20\\d{2})\\b".toRegex()
    val match = yearRegex.find(mKey)
    if (match != null) {
      yr = match.value.toIntOrNull() ?: 2026
    }

    val latestTs = mPayments.maxOfOrNull { it.timestamp } ?: 0L

    MonthlyRevenueDataPoint(
      monthKey = mKey,
      shortMonth = shortM,
      year = yr,
      monthIndex = mIndex,
      collectedAmount = collected,
      pendingAmount = pending,
      totalAmount = total,
      transactionCount = mPayments.size,
      paidCount = paidCount,
      collectionRate = if (total > 0) ((collected / total) * 100f).toFloat() else 100f,
      modeBreakdown = modeMap,
      typeBreakdown = typeMap,
      latestTimestamp = latestTs
    )
  }

  // Sort chronologically by year then monthIndex
  return points.sortedWith(compareBy({ it.year }, { it.monthIndex }))
}

@Composable
fun MonthlyRevenueSummaryChart(
  feePayments: List<StudentFeePayment>,
  modifier: Modifier = Modifier,
  onRecordPaymentClick: () -> Unit = {},
  onViewLedgerClick: (statusFilter: String) -> Unit = {}
) {
  val dataPoints = remember(feePayments) {
    calculateMonthlyRevenueFromPayments(feePayments)
  }

  var selectedPointIndex by remember(dataPoints) {
    mutableStateOf(if (dataPoints.isNotEmpty()) dataPoints.size - 1 else -1)
  }

  var chartMode by remember { mutableStateOf(0) } // 0: Revenue Bar, 1: Collected vs Pending, 2: Payment Modes
  var timeFilter by remember { mutableStateOf("All Months") } // "Last 3 Months", "Last 6 Months", "All Months"

  val filteredPoints = remember(dataPoints, timeFilter) {
    when (timeFilter) {
      "Last 3 Months" -> dataPoints.takeLast(3)
      "Last 6 Months" -> dataPoints.takeLast(6)
      else -> dataPoints
    }
  }

  // Active selected point
  val selectedPoint = remember(filteredPoints, selectedPointIndex) {
    if (filteredPoints.isNotEmpty()) {
      if (selectedPointIndex in filteredPoints.indices) {
        filteredPoints[selectedPointIndex]
      } else {
        filteredPoints.last()
      }
    } else null
  }

  val totalRevenue = remember(dataPoints) { dataPoints.sumOf { it.collectedAmount } }
  val totalPending = remember(dataPoints) { dataPoints.sumOf { it.pendingAmount } }
  val avgMonthly = remember(dataPoints) { if (dataPoints.isNotEmpty()) totalRevenue / dataPoints.size else 0.0 }
  val peakMonth = remember(dataPoints) { dataPoints.maxByOrNull { it.collectedAmount } }
  val overallCollectionRate = remember(totalRevenue, totalPending) {
    val total = totalRevenue + totalPending
    if (total > 0) ((totalRevenue / total) * 100f).toInt() else 100
  }

  Card(
    shape = RoundedCornerShape(16.dp),
    colors = CardDefaults.cardColors(containerColor = Color(0xFF111C2E)),
    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF24334A)),
    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    modifier = modifier.fillMaxWidth()
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
      // Header with Title & Clean Status Indicator
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Row(
          modifier = Modifier.weight(1f),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
          Box(
            modifier = Modifier
              .size(40.dp)
              .clip(RoundedCornerShape(12.dp))
              .background(
                Brush.linearGradient(
                  listOf(Color(0xFF0284C7), Color(0xFF2563EB))
                )
              ),
            contentAlignment = Alignment.Center
          ) {
            Icon(Icons.Outlined.BarChart, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
          }
          Column(modifier = Modifier.weight(1f, fill = false)) {
            Text(
              text = "Monthly Revenue Summary",
              fontWeight = FontWeight.Bold,
              fontSize = 16.sp,
              color = Color.White,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis
            )
            Text(
              text = "${selectedPoint?.monthKey ?: "Active Month"} collections, dues & realization breakdown",
              fontSize = 12.sp,
              color = Color(0xFF94A3B8),
              maxLines = 1,
              overflow = TextOverflow.Ellipsis
            )
          }
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Prominent Live Trend Pill with guaranteed horizontal rendering (Clickable)
        Surface(
          color = Color(0xFF10B981).copy(alpha = 0.15f),
          shape = RoundedCornerShape(8.dp),
          border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.3f)),
          modifier = Modifier.clickable { onViewLedgerClick("All") }
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
          ) {
            Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(Color(0xFF10B981)))
            Text(
              text = "Live Trend",
              fontSize = 10.sp,
              fontWeight = FontWeight.Bold,
              color = Color(0xFF34D399),
              maxLines = 1,
              softWrap = false
            )
          }
        }
      }

      // KPI Metric Cards Row (Even & Symmetric Dark Tiles tracking specific month collections in Indian Rupees)
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        // 1. Specific Month Collected
        Surface(
          modifier = Modifier
            .weight(1f)
            .height(88.dp)
            .clickable { onViewLedgerClick("Paid") },
          color = Color(0xFF0A1220),
          shape = RoundedCornerShape(12.dp),
          border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1E2D42))
        ) {
          Column(
            modifier = Modifier
              .fillMaxSize()
              .padding(horizontal = 6.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.SpaceBetween
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(3.dp)
            ) {
              Icon(Icons.Outlined.TrendingUp, contentDescription = null, tint = Color(0xFF4ADE80), modifier = Modifier.size(12.dp))
              MicroLabelText(
                text = "${selectedPoint?.shortMonth ?: "Month"} Collected",
                color = Color(0xFFCBD5E1),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
              )
            }
            CardStatText(
              text = CurrencyFormatter.formatIndianRupees(selectedPoint?.collectedAmount ?: 0.0),
              color = Color(0xFF4ADE80)
            )
            MicroLabelText(
              text = "${selectedPoint?.paidCount ?: 0} of ${selectedPoint?.transactionCount ?: 0} Paid ›",
              color = Color(0xFF94A3B8),
              maxLines = 1,
              overflow = TextOverflow.Ellipsis
            )
          }
        }

        // 2. Specific Month Pending Dues
        Surface(
          modifier = Modifier
            .weight(1f)
            .height(88.dp)
            .clickable { onViewLedgerClick("Pending") },
          color = Color(0xFF0A1220),
          shape = RoundedCornerShape(12.dp),
          border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1E2D42))
        ) {
          Column(
            modifier = Modifier
              .fillMaxSize()
              .padding(horizontal = 6.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.SpaceBetween
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(3.dp)
            ) {
              Icon(Icons.Outlined.HourglassEmpty, contentDescription = null, tint = Color(0xFFFBBF24), modifier = Modifier.size(12.dp))
              MicroLabelText(
                text = "${selectedPoint?.shortMonth ?: "Month"} Dues",
                color = Color(0xFFCBD5E1),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
              )
            }
            CardStatText(
              text = CurrencyFormatter.formatIndianRupees(selectedPoint?.pendingAmount ?: 0.0),
              color = if ((selectedPoint?.pendingAmount ?: 0.0) > 0) Color(0xFFFBBF24) else Color(0xFF4ADE80)
            )
            MicroLabelText(
              text = if ((selectedPoint?.pendingAmount ?: 0.0) > 0) "Pending settlement ›" else "0 Dues Pending ›",
              color = Color(0xFF94A3B8),
              maxLines = 1,
              overflow = TextOverflow.Ellipsis
            )
          }
        }

        // 3. Specific Month Total Billed / Target
        Surface(
          modifier = Modifier
            .weight(1f)
            .height(88.dp)
            .clickable { onViewLedgerClick("All") },
          color = Color(0xFF0A1220),
          shape = RoundedCornerShape(12.dp),
          border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1E2D42))
        ) {
          Column(
            modifier = Modifier
              .fillMaxSize()
              .padding(horizontal = 6.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.SpaceBetween
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(3.dp)
            ) {
              Icon(Icons.Outlined.Assessment, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(12.dp))
              MicroLabelText(
                text = "${selectedPoint?.shortMonth ?: "Month"} Target",
                color = Color(0xFFCBD5E1),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
              )
            }
            CardStatText(
              text = CurrencyFormatter.formatIndianRupees(selectedPoint?.totalAmount ?: 0.0),
              color = Color(0xFF38BDF8)
            )
            MicroLabelText(
              text = "${selectedPoint?.collectionRate?.toInt() ?: 0}% Realized ›",
              color = Color(0xFF38BDF8),
              maxLines = 1,
              overflow = TextOverflow.Ellipsis
            )
          }
        }
      }

      // Chart Mode & Filter Chips Bar
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        // View Mode Toggle (Revenue, Due vs Paid, Modes)
        Row(
          modifier = Modifier
            .background(Color(0xFF0A1220), RoundedCornerShape(10.dp))
            .border(1.dp, Color(0xFF1E2D42), RoundedCornerShape(10.dp))
            .padding(2.dp),
          horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
          listOf(
            "Revenue" to 0,
            "Due vs Paid" to 1,
            "Modes" to 2
          ).forEach { (label, idx) ->
            val isSelected = chartMode == idx
            Box(
              modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(if (isSelected) Color(0xFF0284C7) else Color.Transparent)
                .clickable { chartMode = idx }
                .padding(horizontal = 8.dp, vertical = 4.dp),
              contentAlignment = Alignment.Center
            ) {
              Text(
                text = label,
                fontSize = 10.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = if (isSelected) Color.White else Color(0xFF94A3B8)
              )
            }
          }
        }

        // Time Range Filter Chips
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
          listOf("3M", "6M", "All").forEach { range ->
            val fullVal = when (range) {
              "3M" -> "Last 3 Months"
              "6M" -> "Last 6 Months"
              else -> "All Months"
            }
            val isSel = timeFilter == fullVal
            Surface(
              shape = RoundedCornerShape(8.dp),
              color = if (isSel) Color(0xFF38BDF8) else Color(0xFF0A1220),
              border = androidx.compose.foundation.BorderStroke(1.dp, if (isSel) Color(0xFF38BDF8) else Color(0xFF1E2D42)),
              modifier = Modifier.clickable { timeFilter = fullVal }
            ) {
              Text(
                text = range,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = if (isSel) Color(0xFF0A1220) else Color(0xFFCBD5E1),
                modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
              )
            }
          }
        }
      }

      if (filteredPoints.isEmpty()) {
        SectionEmptyState(
          icon = Icons.Outlined.BarChart,
          title = "No Revenue Data Points Yet",
          description = "Monthly collection comparisons, payment mode distributions (UPI, Cash, Bank Transfer), and dues realization trends will automatically appear once student fee payments are recorded.",
          actionLabel = "Record Payment",
          onActionClick = onRecordPaymentClick,
          accentColor = Color(0xFF38BDF8),
          hintSteps = listOf(
            "Record student fee installment or registration deposit",
            "Select payment channel (UPI, Cash, NetBanking, Cheque)",
            "System aggregates monthly revenue bars and realization rates"
          )
        )
      } else {
        // Compose Bar Chart Canvas Area (Dark Slate with high contrast bars)
        val maxVal = remember(filteredPoints, chartMode) {
          when (chartMode) {
            1 -> filteredPoints.maxOfOrNull { it.totalAmount } ?: 100000.0
            else -> filteredPoints.maxOfOrNull { it.collectedAmount } ?: 100000.0
          }.coerceAtLeast(10000.0) * 1.30 // 30% headroom for labels
        }

        Surface(
          color = Color(0xFF0A1220),
          shape = RoundedCornerShape(14.dp),
          border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1E2D42)),
          modifier = Modifier.fillMaxWidth()
        ) {
          Column(modifier = Modifier.padding(12.dp)) {
            // Chart Legend
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Text(
                text = "Tap on any bar to inspect month details",
                fontSize = 10.sp,
                color = Color(0xFF94A3B8)
              )

              Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                when (chartMode) {
                  0 -> {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                      Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(Color(0xFF38BDF8)))
                      Text("Collected", fontSize = 9.sp, color = Color(0xFFCBD5E1))
                    }
                  }
                  1 -> {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                      Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(Color(0xFF4ADE80)))
                      Text("Paid", fontSize = 9.sp, color = Color(0xFFCBD5E1))
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                      Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(Color(0xFFF59E0B)))
                      Text("Pending", fontSize = 9.sp, color = Color(0xFFCBD5E1))
                    }
                  }
                  2 -> {
                    listOf("UPI" to Color(0xFF38BDF8), "Cash" to Color(0xFF4ADE80), "Bank/Card" to Color(0xFFA78BFA)).forEach { (m, c) ->
                      Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(c))
                        Text(m, fontSize = 9.sp, color = Color(0xFFCBD5E1))
                      }
                    }
                  }
                }
              }
            }

            Spacer(Modifier.height(10.dp))

            // Bars Container with explicit top padding for non-colliding labels
            Box(
              modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
            ) {
              // Background Grid Lines
              Canvas(modifier = Modifier.fillMaxSize()) {
                val gridSteps = 4
                val stepHeight = size.height / gridSteps
                for (i in 0..gridSteps) {
                  val y = size.height - (i * stepHeight)
                  drawLine(
                    color = Color(0xFF1E2D42),
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = 1f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f)
                  )
                }
              }

              // Interactive Bar Columns
              Row(
                modifier = Modifier
                  .fillMaxSize()
                  .padding(top = 8.dp, bottom = 4.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.Bottom
              ) {
                filteredPoints.forEachIndexed { index, point ->
                  val isSelected = selectedPoint?.monthKey == point.monthKey

                  ComposeBarItem(
                    point = point,
                    maxVal = maxVal,
                    isSelected = isSelected,
                    chartMode = chartMode,
                    onClick = {
                      selectedPointIndex = index
                    },
                    modifier = Modifier.weight(1f)
                  )
                }
              }
            }

            // X-Axis Month Labels
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp),
              horizontalArrangement = Arrangement.SpaceEvenly
            ) {
              filteredPoints.forEachIndexed { index, point ->
                val isSelected = selectedPoint?.monthKey == point.monthKey
                val isCurrent = point.monthKey.contains("August", ignoreCase = true)

                Column(
                  horizontalAlignment = Alignment.CenterHorizontally,
                  modifier = Modifier
                    .weight(1f)
                    .clickable { selectedPointIndex = index }
                ) {
                  Text(
                    text = point.shortMonth,
                    fontSize = 10.sp,
                    fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium,
                    color = if (isSelected) Color(0xFF38BDF8) else if (isCurrent) Color.White else Color(0xFF94A3B8)
                  )
                  if (isCurrent) {
                    Box(
                      modifier = Modifier
                        .size(4.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF38BDF8))
                    )
                  }
                }
              }
            }
          }
        }

        // Detailed Selected Month Inspector Card (With Indian Currency & Complete Math)
        selectedPoint?.let { sp ->
          Surface(
            color = Color(0xFF0A1220),
            shape = RoundedCornerShape(14.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1E2D42)),
            modifier = Modifier.fillMaxWidth()
          ) {
            Column(
              modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
              verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
              ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                  Icon(Icons.Outlined.Insights, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(16.dp))
                  Text(
                    text = "${sp.monthKey} Breakdown",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = Color.White
                  )
                }

                Surface(
                  color = if (sp.collectionRate >= 70f) Color(0xFF16A34A).copy(alpha = 0.2f) else Color(0xFFF59E0B).copy(alpha = 0.2f),
                  shape = RoundedCornerShape(6.dp)
                ) {
                  Text(
                    text = "${sp.collectionRate.toInt()}% Realized",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (sp.collectionRate >= 70f) Color(0xFF4ADE80) else Color(0xFFFCD34D),
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                  )
                }
              }

              // Stat Grid (Using CurrencyFormatter for pure Indian grouping: ₹73,600 / ₹22,400)
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
              ) {
                Column(modifier = Modifier.weight(1f)) {
                  MicroLabelText(text = "Collected", color = Color(0xFF94A3B8))
                  InlineStatText(text = CurrencyFormatter.formatIndianRupees(sp.collectedAmount), color = Color(0xFF4ADE80))
                }
                Column(modifier = Modifier.weight(1f)) {
                  MicroLabelText(text = "Pending Dues", color = Color(0xFF94A3B8))
                  InlineStatText(text = CurrencyFormatter.formatIndianRupees(sp.pendingAmount), color = if (sp.pendingAmount > 0) Color(0xFFF87171) else Color(0xFF4ADE80))
                }
                Column(modifier = Modifier.weight(1f)) {
                  MicroLabelText(text = "Total Billed", color = Color(0xFF94A3B8))
                  InlineStatText(text = CurrencyFormatter.formatIndianRupees(sp.totalAmount), color = Color(0xFF38BDF8))
                }
              }

              // Mode Distribution: Clean horizontal label+value pairs without nested box borders
              if (sp.modeBreakdown.isNotEmpty()) {
                HorizontalDivider(color = Color(0xFF1E2D42), thickness = 1.dp)
                @OptIn(ExperimentalLayoutApi::class)
                FlowRow(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.spacedBy(12.dp),
                  verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                  Text(
                    text = "Modes:",
                    fontSize = 10.sp,
                    color = Color(0xFF94A3B8),
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.CenterVertically)
                  )
                  sp.modeBreakdown.entries.forEachIndexed { index, (mode, amt) ->
                    Row(
                      verticalAlignment = Alignment.CenterVertically,
                      horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                      Text(
                        text = mode,
                        fontSize = 10.sp,
                        color = Color(0xFF94A3B8),
                        fontWeight = FontWeight.Medium
                      )
                      Text(
                        text = CurrencyFormatter.formatIndianRupees(amt),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFFCBD5E1)
                      )
                      if (index < sp.modeBreakdown.size - 1) {
                        Text(
                          text = "•",
                          fontSize = 10.sp,
                          color = Color(0xFF475569),
                          modifier = Modifier.padding(start = 8.dp)
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
    }
  }
}

@Composable
private fun ComposeBarItem(
  point: MonthlyRevenueDataPoint,
  maxVal: Double,
  isSelected: Boolean,
  chartMode: Int,
  onClick: () -> Unit,
  modifier: Modifier = Modifier
) {
  val collectedFraction = (point.collectedAmount / maxVal).coerceIn(0.04, 0.95).toFloat()
  val pendingFraction = (point.pendingAmount / maxVal).coerceIn(0.0, 0.95).toFloat()
  val totalFraction = (point.totalAmount / maxVal).coerceIn(0.04, 0.95).toFloat()

  val animCollected by animateFloatAsState(
    targetValue = collectedFraction,
    animationSpec = spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessLow),
    label = "collected_bar_anim"
  )
  val animPending by animateFloatAsState(
    targetValue = pendingFraction,
    animationSpec = spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessLow),
    label = "pending_bar_anim"
  )
  val animTotal by animateFloatAsState(
    targetValue = totalFraction,
    animationSpec = spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessLow),
    label = "total_bar_anim"
  )

  Column(
    modifier = modifier
      .fillMaxHeight()
      .padding(horizontal = 3.dp)
      .pressSpring(0.92f)
      .clickable(onClick = onClick),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.Bottom
  ) {
    // Value Label on Top of Bar - Programmatic clearance so it NEVER touches the bar
    val labelText = when (chartMode) {
      1 -> CurrencyFormatter.formatCompact(point.totalAmount)
      else -> CurrencyFormatter.formatCompact(point.collectedAmount)
    }

    Text(
      text = labelText,
      fontSize = if (isSelected) 10.sp else 9.sp,
      fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Bold,
      color = if (isSelected) Color(0xFF38BDF8) else Color(0xFF94A3B8),
      maxLines = 1,
      modifier = Modifier.padding(bottom = 6.dp) // Programmatic 6dp padding above the bar
    )

    when (chartMode) {
      0 -> {
        // Single Revenue Bar
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(animCollected)
            .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
            .background(
              if (isSelected) {
                Brush.verticalGradient(listOf(Color(0xFF38BDF8), Color(0xFF0284C7)))
              } else {
                Brush.verticalGradient(listOf(Color(0xFF60A5FA), Color(0xFF2563EB)))
              }
            )
            .then(
              if (isSelected) {
                Modifier.border(1.5.dp, Color(0xFF38BDF8), RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
              } else Modifier
            )
        )
      }
      1 -> {
        // Stacked Due vs Paid Bar
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(animTotal)
            .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
            .then(
              if (isSelected) {
                Modifier.border(1.5.dp, Color(0xFF38BDF8), RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
              } else Modifier
            )
        ) {
          // Top Segment: Pending (Amber)
          if (point.pendingAmount > 0 && point.totalAmount > 0) {
            val pendingWeight = (point.pendingAmount / point.totalAmount).toFloat().coerceIn(0.01f, 0.99f)
            Box(
              modifier = Modifier
                .fillMaxWidth()
                .weight(pendingWeight)
                .background(Brush.verticalGradient(listOf(Color(0xFFFBBF24), Color(0xFFD97706))))
            )
          }
          // Bottom Segment: Paid (Emerald Green)
          val paidWeight = if (point.totalAmount > 0) (point.collectedAmount / point.totalAmount).toFloat().coerceIn(0.01f, 0.99f) else 1f
          Box(
            modifier = Modifier
              .fillMaxWidth()
              .weight(paidWeight)
              .background(Brush.verticalGradient(listOf(Color(0xFF4ADE80), Color(0xFF16A34A))))
          )
        }
      }
      2 -> {
        // Payment Modes Stacked Breakdown
        val modes = point.modeBreakdown
        val totalMode = modes.values.sum().coerceAtLeast(1.0)
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(animCollected)
            .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
            .then(
              if (isSelected) {
                Modifier.border(1.5.dp, Color(0xFF38BDF8), RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
              } else Modifier
            )
        ) {
          modes.entries.forEach { (mode, amt) ->
            val w = (amt / totalMode).toFloat().coerceIn(0.05f, 1f)
            val barColor = when (mode) {
              "UPI" -> Brush.verticalGradient(listOf(Color(0xFF38BDF8), Color(0xFF0284C7)))
              "Cash" -> Brush.verticalGradient(listOf(Color(0xFF4ADE80), Color(0xFF16A34A)))
              "Net Banking" -> Brush.verticalGradient(listOf(Color(0xFFA78BFA), Color(0xFF7C3AED)))
              else -> Brush.verticalGradient(listOf(Color(0xFFFBBF24), Color(0xFFD97706)))
            }
            Box(
              modifier = Modifier
                .fillMaxWidth()
                .weight(w)
                .background(barColor)
            )
          }
        }
      }
    }
  }
}

