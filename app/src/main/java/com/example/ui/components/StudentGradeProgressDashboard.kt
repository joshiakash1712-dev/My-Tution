package com.example.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import com.example.ui.theme.pressSpring
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.Student
import com.example.data.TestRecord
import com.example.ui.theme.NumberTypography
import com.example.ui.theme.HeroStatText
import com.example.ui.theme.CardStatText
import com.example.ui.theme.InlineStatText
import com.example.ui.theme.MicroLabelText
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Data structure representing a semester grade point on the chart
 */
data class GradeDataPoint(
  val testId: String,
  val testName: String,
  val shortName: String,
  val date: String,
  val subject: String,
  val score: Int,
  val totalMarks: Int,
  val percentage: Float,
  val classAverage: Float,
  val targetBenchmark: Float = 75f,
  val aiInsight: String = "",
  val rankInTest: Int = 1
)

enum class GradeChartType(val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
  AREA_SPLINE("Spline Area", Icons.Default.ShowChart),
  GROUPED_BAR("Bar Compare", Icons.Default.BarChart),
  SUBJECT_MATRIX("Subject Breakdown", Icons.Default.PieChart),
  AI_DIAGNOSTICS("AI Diagnostics", Icons.Default.AutoAwesome)
}

enum class SemesterTerm(val label: String) {
  FULL_YEAR("Full Semester"),
  TERM_1("Term 1 (Aug - Oct)"),
  TERM_2("Term 2 (Nov - Jan)")
}

/**
 * Generates comprehensive grade trend data points for a student over the semester
 */
fun buildSemesterGradeData(student: Student, tests: List<TestRecord>): List<GradeDataPoint> {
  val defaultAssessments = listOf(
    GradeDataPoint(
      testId = "T1",
      testName = "Diagnostic Assessment",
      shortName = "Unit 1",
      date = "12 Aug",
      subject = "Physics",
      score = 74,
      totalMarks = 100,
      percentage = 74f,
      classAverage = 68f,
      targetBenchmark = 75f,
      aiInsight = "Solid baseline in Kinematics; rotational mechanics needs practice.",
      rankInTest = 4
    ),
    GradeDataPoint(
      testId = "T2",
      testName = "Periodic Review Test 1",
      shortName = "Review 1",
      date = "28 Aug",
      subject = "Mathematics",
      score = 82,
      totalMarks = 100,
      percentage = 82f,
      classAverage = 71f,
      targetBenchmark = 75f,
      aiInsight = "Strong problem solving in Calculus & Trigonometry.",
      rankInTest = 2
    ),
    GradeDataPoint(
      testId = "T3",
      testName = "Mid-Semester Examination",
      shortName = "Mid-Term",
      date = "15 Sep",
      subject = "Chemistry",
      score = 88,
      totalMarks = 100,
      percentage = 88f,
      classAverage = 73f,
      targetBenchmark = 75f,
      aiInsight = "Excellence in Organic reaction mechanisms and equilibrium.",
      rankInTest = 1
    ),
    GradeDataPoint(
      testId = "T4",
      testName = "Unit Test 2: Core Physics",
      shortName = "Unit 2",
      date = "04 Oct",
      subject = "Physics",
      score = 79,
      totalMarks = 100,
      percentage = 79f,
      classAverage = 70f,
      targetBenchmark = 75f,
      aiInsight = "Good grasp of Electrodynamics; work on numerical speed.",
      rankInTest = 3
    ),
    GradeDataPoint(
      testId = "T5",
      testName = "Advance Mock Test",
      shortName = "Mock 1",
      date = "22 Oct",
      subject = "Mathematics",
      score = 92,
      totalMarks = 100,
      percentage = 92f,
      classAverage = 75f,
      targetBenchmark = 75f,
      aiInsight = "Top-tier performance in Vectors and 3D Geometry!",
      rankInTest = 1
    ),
    GradeDataPoint(
      testId = "T6",
      testName = "Pre-Board Benchmark",
      shortName = "Pre-Board",
      date = "10 Nov",
      subject = "Physics",
      score = 95,
      totalMarks = 100,
      percentage = 95f,
      classAverage = 77f,
      targetBenchmark = 75f,
      aiInsight = "Mastery in Modern Physics & Thermodynamics.",
      rankInTest = 1
    ),
    GradeDataPoint(
      testId = "T7",
      testName = "Semester Final Evaluation",
      shortName = "Final Exam",
      date = "28 Nov",
      subject = "Chemistry",
      score = 94,
      totalMarks = 100,
      percentage = 94f,
      classAverage = 78f,
      targetBenchmark = 75f,
      aiInsight = "Exceptional conceptual clarity and error-free analytical steps.",
      rankInTest = 1
    )
  )

  if (tests.isEmpty()) return defaultAssessments

  // Map real tests from database if available
  val mappedFromDb = tests.mapIndexed { idx, t ->
    val rawScore = t.studentMarks[student.id] ?: ((student.overallAvg + (idx * 3) - 5).coerceIn(60, 98))
    val pct = if (t.totalMarks > 0) (rawScore.toFloat() / t.totalMarks) * 100f else rawScore.toFloat()
    val avgScore = if (t.studentMarks.isNotEmpty()) t.studentMarks.values.average().toFloat() else 72f
    val avgPct = if (t.totalMarks > 0) (avgScore / t.totalMarks) * 100f else avgScore

    GradeDataPoint(
      testId = t.id,
      testName = t.testName,
      shortName = if (t.testName.length > 10) t.testName.take(8) + ".." else t.testName,
      date = t.date,
      subject = t.subject,
      score = rawScore,
      totalMarks = t.totalMarks,
      percentage = pct,
      classAverage = avgPct,
      targetBenchmark = 75f,
      aiInsight = if (t.aiAnalysisStrong.isNotEmpty()) "Strong in ${t.aiAnalysisStrong.first()}" else "Consistent progress in ${t.subject}",
      rankInTest = if (pct >= 90) 1 else if (pct >= 80) 2 else 4
    )
  }

  return if (mappedFromDb.size >= 4) mappedFromDb else defaultAssessments
}

/**
 * Recharts-Inspired Interactive Student Grade Progress Dashboard
 */
@Composable
fun StudentGradeProgressDashboard(
  student: Student,
  tests: List<TestRecord> = emptyList(),
  modifier: Modifier = Modifier
) {
  val allDataPoints = remember(student, tests) { buildSemesterGradeData(student, tests) }

  var selectedChartType by remember { mutableStateOf(GradeChartType.AREA_SPLINE) }
  var selectedSubjectFilter by remember { mutableStateOf("All Subjects") }
  var selectedTerm by remember { mutableStateOf(SemesterTerm.FULL_YEAR) }
  var selectedPointIndex by remember { mutableStateOf<Int?>(null) }

  val availableSubjects = remember(allDataPoints) {
    listOf("All Subjects") + allDataPoints.map { it.subject }.distinct()
  }

  // Filtered dataset
  val filteredDataPoints = remember(allDataPoints, selectedSubjectFilter, selectedTerm) {
    var list = if (selectedSubjectFilter == "All Subjects") {
      allDataPoints
    } else {
      allDataPoints.filter { it.subject.equals(selectedSubjectFilter, ignoreCase = true) }
    }

    if (selectedTerm == SemesterTerm.TERM_1) {
      list = list.take((list.size + 1) / 2)
    } else if (selectedTerm == SemesterTerm.TERM_2) {
      list = list.drop(list.size / 2)
    }

    if (list.isEmpty()) allDataPoints.take(3) else list
  }

  // Calculate high-level KPIs
  val currentAvg = remember(filteredDataPoints) {
    if (filteredDataPoints.isNotEmpty()) filteredDataPoints.map { it.percentage }.average().toFloat() else 0f
  }
  val peakScore = remember(filteredDataPoints) {
    filteredDataPoints.maxOfOrNull { it.percentage } ?: 0f
  }
  val startScore = remember(filteredDataPoints) {
    filteredDataPoints.firstOrNull()?.percentage ?: 0f
  }
  val growthDelta = remember(filteredDataPoints, startScore) {
    val last = filteredDataPoints.lastOrNull()?.percentage ?: 0f
    last - startScore
  }

  Card(
    shape = RoundedCornerShape(24.dp),
    colors = CardDefaults.cardColors(containerColor = Color(0xFF0D1526)),
    border = BorderStroke(1.dp, Color(0xFF1E2D42)),
    modifier = modifier
      .fillMaxWidth()
      .shadow(4.dp, RoundedCornerShape(24.dp), ambientColor = Color(0x10000000), spotColor = Color(0x15000000))
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(20.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
      // 1. Dashboard Header
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
          Box(
            modifier = Modifier
              .size(44.dp)
              .clip(CircleShape)
              .background(
                Brush.linearGradient(
                  colors = listOf(Color(0xFF0061A4), Color(0xFF0088D1))
                )
              ),
            contentAlignment = Alignment.Center
          ) {
            Icon(Icons.Default.TrendingUp, contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
          }

          Column {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
              Text(
                "Grade Progress Analytics",
                fontWeight = FontWeight.ExtraBold,
                fontSize = 17.sp,
                color = Color.White
              )
              Surface(
                color = Color(0xFF1E293B),
                shape = RoundedCornerShape(6.dp),
                border = BorderStroke(1.dp, Color(0xFF334155))
              ) {
                Text(
                  "RECHARTS ENGINE",
                  fontSize = 8.sp,
                  fontWeight = FontWeight.ExtraBold,
                  color = Color(0xFF38BDF8),
                  modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                )
              }
            }
            Text(
              "${student.name} • ${student.className} • ${student.batch}",
              fontSize = 12.sp,
              color = Color(0xFF94A3B8)
            )
          }
        }

        // Quick Export / Refresh icon
        IconButton(
          onClick = { selectedPointIndex = null },
          modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(Color(0xFF1E293B))
        ) {
          Icon(Icons.Default.Refresh, contentDescription = "Reset Selection", tint = Color(0xFF94A3B8), modifier = Modifier.size(18.dp))
        }
      }

      // 2. High-Level Performance Metrics Ribbon
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        Surface(
          color = Color(0xFF111C2E),
          shape = RoundedCornerShape(12.dp),
          border = BorderStroke(1.dp, Color(0xFF1E2D42)),
          modifier = Modifier.weight(1f)
        ) {
          Column(modifier = Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            CardStatText(text = "${currentAvg.roundToInt()}%", color = Color(0xFF4ADE80))
            MicroLabelText(text = "Semester Avg", color = Color(0xFF94A3B8))
          }
        }

        Surface(
          color = Color(0xFF111C2E),
          shape = RoundedCornerShape(12.dp),
          border = BorderStroke(1.dp, Color(0xFF1E2D42)),
          modifier = Modifier.weight(1f)
        ) {
          Column(modifier = Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            CardStatText(text = "${peakScore.roundToInt()}%", color = Color(0xFF38BDF8))
            MicroLabelText(text = "Peak Score", color = Color(0xFF94A3B8))
          }
        }

        Surface(
          color = Color(0xFF111C2E),
          shape = RoundedCornerShape(12.dp),
          border = BorderStroke(1.dp, Color(0xFF1E2D42)),
          modifier = Modifier.weight(1f)
        ) {
          Column(modifier = Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Icon(
                if (growthDelta >= 0) Icons.Default.ArrowDropUp else Icons.Default.ArrowDropDown,
                contentDescription = null,
                tint = if (growthDelta >= 0) Color(0xFFA78BFA) else Color(0xFFF87171),
                modifier = Modifier.size(18.dp)
              )
              CardStatText(
                text = "${if (growthDelta >= 0) "+" else ""}${growthDelta.roundToInt()}%",
                color = if (growthDelta >= 0) Color(0xFFA78BFA) else Color(0xFFF87171)
              )
            }
            MicroLabelText(
              text = "Growth Trend",
              color = Color(0xFF94A3B8)
            )
          }
        }
      }

      // 3. Chart Mode Selection & Time Horizon
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        // Chart Type Selector Chips
        Row(
          horizontalArrangement = Arrangement.spacedBy(6.dp),
          modifier = Modifier.horizontalScroll(rememberScrollState())
        ) {
          GradeChartType.values().forEach { chartType ->
            val isSelected = selectedChartType == chartType
            Surface(
              color = if (isSelected) Color(0xFF0061A4) else Color(0xFF111C2E),
              shape = RoundedCornerShape(8.dp),
              border = BorderStroke(1.dp, if (isSelected) Color.Transparent else Color(0xFF1E2D42)),
              modifier = Modifier
                .pressSpring(0.95f)
                .clickable {
                  selectedChartType = chartType
                  selectedPointIndex = null
                }
            ) {
              Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
              ) {
                Icon(
                  chartType.icon,
                  contentDescription = null,
                  tint = if (isSelected) Color.White else Color(0xFF94A3B8),
                  modifier = Modifier.size(14.dp)
                )
                Text(
                  chartType.label,
                  fontSize = 11.sp,
                  fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                  color = if (isSelected) Color.White else Color(0xFF94A3B8)
                )
              }
            }
          }
        }
      }

      // 4. Subject Filter Strip
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
      ) {
        availableSubjects.forEach { sub ->
          val isSel = selectedSubjectFilter == sub
          FilterChip(
            selected = isSel,
            onClick = {
              selectedSubjectFilter = sub
              selectedPointIndex = null
            },
            label = {
              Text(
                text = sub,
                fontSize = 10.sp,
                fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                maxLines = 1,
                softWrap = false,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
              )
            },
            colors = FilterChipDefaults.filterChipColors(
              containerColor = Color(0xFF111C2E),
              labelColor = Color(0xFF94A3B8),
              selectedContainerColor = Color(0xFF0061A4),
              selectedLabelColor = Color.White
            ),
            border = FilterChipDefaults.filterChipBorder(
              enabled = true,
              selected = isSel,
              borderColor = if (isSel) Color(0xFF60A5FA) else Color(0xFF1E2D42)
            )
          )
        }
      }

      // 5. Interactive Chart Canvas Area
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .height(if (selectedChartType == GradeChartType.AI_DIAGNOSTICS) 340.dp else 240.dp)
          .clip(RoundedCornerShape(16.dp))
          .background(Color(0xFF0A101D))
          .border(1.dp, Color(0xFF1E2D42), RoundedCornerShape(16.dp))
          .padding(top = 16.dp, bottom = 8.dp, start = 8.dp, end = 8.dp)
      ) {
        val textMeasurer = rememberTextMeasurer()

        AnimatedContent(
          targetState = selectedChartType,
          transitionSpec = {
            (fadeIn(animationSpec = tween(220)) + scaleIn(initialScale = 0.96f, animationSpec = tween(220)))
              .togetherWith(fadeOut(animationSpec = tween(180)))
          },
          label = "grade_chart_transition"
        ) { chartType ->
          when (chartType) {
            GradeChartType.AREA_SPLINE -> {
              RechartsSplineAreaCanvas(
                dataPoints = filteredDataPoints,
                selectedIndex = selectedPointIndex,
                onPointSelected = { selectedPointIndex = it },
                textMeasurer = textMeasurer
              )
            }
            GradeChartType.GROUPED_BAR -> {
              RechartsGroupedBarCanvas(
                dataPoints = filteredDataPoints,
                selectedIndex = selectedPointIndex,
                onPointSelected = { selectedPointIndex = it },
                textMeasurer = textMeasurer
              )
            }
            GradeChartType.SUBJECT_MATRIX -> {
              RechartsSubjectMatrixView(
                dataPoints = filteredDataPoints
              )
            }
            GradeChartType.AI_DIAGNOSTICS -> {
              RechartsAiDiagnosticsView(
                student = student,
                dataPoints = allDataPoints
              )
            }
          }
        }
      }

      // 6. Interactive Recharts Tooltip / Active Point Inspection Card
      val activePoint = selectedPointIndex?.let { filteredDataPoints.getOrNull(it) } ?: filteredDataPoints.lastOrNull()

      if (activePoint != null && selectedChartType != GradeChartType.SUBJECT_MATRIX && selectedChartType != GradeChartType.AI_DIAGNOSTICS) {
        Surface(
          color = Color(0xFF0F172A),
          shape = RoundedCornerShape(14.dp),
          border = BorderStroke(1.dp, Color(0xFF1E2D42)),
          modifier = Modifier.fillMaxWidth()
        ) {
          Column(
            modifier = Modifier
              .fillMaxWidth()
              .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                  modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF2563EB)),
                  contentAlignment = Alignment.Center
                ) {
                  Text("${activePoint.rankInTest}", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
                Column {
                  Text(activePoint.testName, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color.White)
                  Text("${activePoint.subject} • ${activePoint.date}", fontSize = 10.sp, color = Color(0xFF94A3B8))
                }
              }

              Surface(
                color = Color(0xFF1E293B),
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, Color(0xFF334155))
              ) {
                Row(
                  modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                  Text("Score:", fontSize = 11.sp, color = Color(0xFF94A3B8))
                  Text(
                    "${activePoint.score}/${activePoint.totalMarks} (${activePoint.percentage.roundToInt()}%)",
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 12.sp,
                    color = Color(0xFF38BDF8)
                  )
                }
              }
            }

            // Benchmark Comparison Pill
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(10.dp),
              verticalAlignment = Alignment.CenterVertically
            ) {
              val diffFromAvg = activePoint.percentage - activePoint.classAverage
              Surface(
                color = if (diffFromAvg >= 0) Color(0xFF064E3B) else Color(0xFF7F1D1D),
                shape = RoundedCornerShape(6.dp)
              ) {
                Text(
                  if (diffFromAvg >= 0) "+${diffFromAvg.roundToInt()}% vs Class Avg (${activePoint.classAverage.roundToInt()}%)" else "${diffFromAvg.roundToInt()}% vs Class Avg (${activePoint.classAverage.roundToInt()}%)",
                  fontSize = 10.sp,
                  fontWeight = FontWeight.Bold,
                  color = if (diffFromAvg >= 0) Color(0xFF6EE7B7) else Color(0xFFFCA5A5),
                  modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                )
              }

              Text(
                activePoint.aiInsight,
                fontSize = 11.sp,
                color = Color(0xFFCBD5E1),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
              )
            }
          }
        }
      }

      // 7. Legend Strip
      if (selectedChartType != GradeChartType.SUBJECT_MATRIX && selectedChartType != GradeChartType.AI_DIAGNOSTICS) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.Center,
          verticalAlignment = Alignment.CenterVertically
        ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
          Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(Color(0xFF0061A4)))
          Text("Student Score", fontSize = 11.sp, color = Color(0xFF94A3B8), fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.width(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
          Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(Color(0xFF64748B)))
          Text("Class Average", fontSize = 11.sp, color = Color(0xFF94A3B8), fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.width(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
          Box(modifier = Modifier.size(10.dp, 2.dp).background(Color(0xFFE11D48)))
          Text("Target (75%)", fontSize = 11.sp, color = Color(0xFF94A3B8), fontWeight = FontWeight.SemiBold)
        }
      }
    }
  }
}
}

/**
 * Custom Compose Canvas rendering smooth Recharts-style Bézier Spline Curves with Gradient Fill
 */
@Composable
fun RechartsSplineAreaCanvas(
  dataPoints: List<GradeDataPoint>,
  selectedIndex: Int?,
  onPointSelected: (Int) -> Unit,
  textMeasurer: TextMeasurer
) {
  if (dataPoints.isEmpty()) return

  val animProgress by animateFloatAsState(
    targetValue = 1f,
    animationSpec = spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessLow),
    label = "spline_anim"
  )

  Canvas(
    modifier = Modifier
      .fillMaxSize()
      .pointerInput(dataPoints) {
        detectTapGestures { offset ->
          val width = size.width
          val paddingLeft = 40f
          val paddingRight = 30f
          val usableWidth = width - paddingLeft - paddingRight
          val step = if (dataPoints.size > 1) usableWidth / (dataPoints.size - 1) else usableWidth

          var closestIdx = 0
          var minDistance = Float.MAX_VALUE

          dataPoints.indices.forEach { idx ->
            val pointX = paddingLeft + (idx * step)
            val dist = kotlin.math.abs(offset.x - pointX)
            if (dist < minDistance) {
              minDistance = dist
              closestIdx = idx
            }
          }

          if (minDistance < 60f) {
            onPointSelected(closestIdx)
          }
        }
      }
  ) {
    val canvasWidth = size.width
    val canvasHeight = size.height
    val paddingLeft = 50f
    val paddingRight = 40f
    val paddingTop = 25f
    val paddingBottom = 40f

    val chartWidth = canvasWidth - paddingLeft - paddingRight
    val chartHeight = canvasHeight - paddingTop - paddingBottom

    val maxScore = 100f
    val minScore = 40f

    // 1. Draw Grid Lines (Y-Axis)
    val gridLines = listOf(40, 60, 80, 100)
    gridLines.forEach { value ->
      val normalized = (value - minScore) / (maxScore - minScore)
      val y = paddingTop + chartHeight - (normalized * chartHeight)

      // Grid horizontal line
      drawLine(
        color = Color(0xFFE2E8F0),
        start = Offset(paddingLeft, y),
        end = Offset(canvasWidth - paddingRight, y),
        strokeWidth = 1f,
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f))
      )

      // Y-axis label
      drawText(
        textMeasurer = textMeasurer,
        text = "$value%",
        topLeft = Offset(8f, y - 8f),
        style = TextStyle(fontSize = 9.sp, color = Color(0xFF94A3B8), fontWeight = FontWeight.SemiBold)
      )
    }

    // 2. Draw 75% Target Line (Recharts ReferenceLine)
    val targetY = paddingTop + chartHeight - (((75f - minScore) / (maxScore - minScore)) * chartHeight)
    drawLine(
      color = Color(0xFFF43F5E),
      start = Offset(paddingLeft, targetY),
      end = Offset(canvasWidth - paddingRight, targetY),
      strokeWidth = 1.5f,
      pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 4f))
    )

    val stepX = if (dataPoints.size > 1) chartWidth / (dataPoints.size - 1) else chartWidth

    // Compute coordinate points for Student Score and Class Avg
    val studentCoords = dataPoints.mapIndexed { idx, point ->
      val x = paddingLeft + (idx * stepX)
      val normalized = ((point.percentage * animProgress) - minScore) / (maxScore - minScore)
      val y = paddingTop + chartHeight - (normalized.coerceIn(0f, 1f) * chartHeight)
      Offset(x, y)
    }

    val avgCoords = dataPoints.mapIndexed { idx, point ->
      val x = paddingLeft + (idx * stepX)
      val normalized = ((point.classAverage * animProgress) - minScore) / (maxScore - minScore)
      val y = paddingTop + chartHeight - (normalized.coerceIn(0f, 1f) * chartHeight)
      Offset(x, y)
    }

    // 3. Construct Spline Paths (Cubic Bézier Curve)
    fun buildSplinePath(coords: List<Offset>): Path {
      val path = Path()
      if (coords.isEmpty()) return path

      path.moveTo(coords[0].x, coords[0].y)
      for (i in 0 until coords.size - 1) {
        val p0 = coords[i]
        val p1 = coords[i + 1]
        val controlX = (p0.x + p1.x) / 2f
        path.cubicTo(controlX, p0.y, controlX, p1.y, p1.x, p1.y)
      }
      return path
    }

    val studentLinePath = buildSplinePath(studentCoords)
    val avgLinePath = buildSplinePath(avgCoords)

    // 4. Draw Area Gradient under Student Spline Curve
    if (studentCoords.isNotEmpty()) {
      val areaPath = Path().apply {
        addPath(studentLinePath)
        lineTo(studentCoords.last().x, paddingTop + chartHeight)
        lineTo(studentCoords.first().x, paddingTop + chartHeight)
        close()
      }

      drawPath(
        path = areaPath,
        brush = Brush.verticalGradient(
          colors = listOf(
            Color(0xFF0061A4).copy(alpha = 0.35f * animProgress),
            Color(0xFF38BDF8).copy(alpha = 0.15f * animProgress),
            Color(0xFF0061A4).copy(alpha = 0.00f)
          ),
          startY = paddingTop,
          endY = paddingTop + chartHeight
        )
      )
    }

    // 5. Stroke Class Average Line (Muted Slate)
    drawPath(
      path = avgLinePath,
      color = Color(0xFF94A3B8),
      style = Stroke(width = 2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 4f)))
    )

    // 6. Stroke Student Score Spline Curve (Vibrant Brand Blue)
    drawPath(
      path = studentLinePath,
      brush = Brush.horizontalGradient(
        colors = listOf(Color(0xFF0061A4), Color(0xFF0284C7), Color(0xFF0061A4))
      ),
      style = Stroke(width = 3.5f, cap = StrokeCap.Round, join = StrokeJoin.Round)
    )

    // 7. Draw Data Point Dots & Selected Highlight
    studentCoords.forEachIndexed { idx, offset ->
      val isSelected = selectedIndex == idx
      val point = dataPoints[idx]

      // Outer Glow/Ring if selected
      if (isSelected) {
        drawCircle(
          color = Color(0xFF0061A4).copy(alpha = 0.25f),
          radius = 16f,
          center = offset
        )
        // Vertical Cursor Reference Line
        drawLine(
          color = Color(0xFF0061A4).copy(alpha = 0.5f),
          start = Offset(offset.x, paddingTop),
          end = Offset(offset.x, paddingTop + chartHeight),
          strokeWidth = 1.5f,
          pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 4f))
        )
      }

      // Outer White Border Circle
      drawCircle(
        color = Color.White,
        radius = if (isSelected) 8f else 5.5f,
        center = offset
      )

      // Inner Colored Center Dot
      drawCircle(
        color = if (isSelected) Color(0xFF0F172A) else Color(0xFF0061A4),
        radius = if (isSelected) 5f else 3.5f,
        center = offset
      )

      // X-Axis Labels (Short Test Name)
      drawText(
        textMeasurer = textMeasurer,
        text = point.shortName,
        topLeft = Offset(offset.x - 22f, paddingTop + chartHeight + 10f),
        style = TextStyle(
          fontSize = 9.sp,
          color = if (isSelected) Color(0xFF0061A4) else Color(0xFF64748B),
          fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
        )
      )
    }
  }
}

/**
 * Recharts Grouped Bar Chart comparing Student vs Class Average per assessment
 */
@Composable
fun RechartsGroupedBarCanvas(
  dataPoints: List<GradeDataPoint>,
  selectedIndex: Int?,
  onPointSelected: (Int) -> Unit,
  textMeasurer: TextMeasurer
) {
  if (dataPoints.isEmpty()) return

  val animProgress by animateFloatAsState(
    targetValue = 1f,
    animationSpec = tween(durationMillis = 700),
    label = "bar_anim"
  )

  Canvas(
    modifier = Modifier
      .fillMaxSize()
      .pointerInput(dataPoints) {
        detectTapGestures { offset ->
          val width = size.width
          val paddingLeft = 50f
          val paddingRight = 30f
          val usableWidth = width - paddingLeft - paddingRight
          val groupWidth = usableWidth / dataPoints.size

          val tappedIdx = ((offset.x - paddingLeft) / groupWidth).toInt()
          if (tappedIdx in dataPoints.indices) {
            onPointSelected(tappedIdx)
          }
        }
      }
  ) {
    val canvasWidth = size.width
    val canvasHeight = size.height
    val paddingLeft = 50f
    val paddingRight = 30f
    val paddingTop = 25f
    val paddingBottom = 40f

    val chartWidth = canvasWidth - paddingLeft - paddingRight
    val chartHeight = canvasHeight - paddingTop - paddingBottom

    val maxScore = 100f
    val minScore = 0f

    // 1. Grid Lines
    listOf(0, 25, 50, 75, 100).forEach { value ->
      val normalized = (value - minScore) / (maxScore - minScore)
      val y = paddingTop + chartHeight - (normalized * chartHeight)

      drawLine(
        color = Color(0xFFE2E8F0),
        start = Offset(paddingLeft, y),
        end = Offset(canvasWidth - paddingRight, y),
        strokeWidth = 1f
      )

      drawText(
        textMeasurer = textMeasurer,
        text = "$value%",
        topLeft = Offset(8f, y - 7f),
        style = TextStyle(fontSize = 9.sp, color = Color(0xFF94A3B8), fontWeight = FontWeight.SemiBold)
      )
    }

    // 2. Target 75% Reference Line
    val targetY = paddingTop + chartHeight - (0.75f * chartHeight)
    drawLine(
      color = Color(0xFFF43F5E),
      start = Offset(paddingLeft, targetY),
      end = Offset(canvasWidth - paddingRight, targetY),
      strokeWidth = 1.5f,
      pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 4f))
    )

    val groupWidth = chartWidth / dataPoints.size
    val barWidth = min(groupWidth * 0.32f, 16f)
    val barSpacing = 4f

    dataPoints.forEachIndexed { idx, point ->
      val groupStartX = paddingLeft + (idx * groupWidth)
      val groupCenterX = groupStartX + (groupWidth / 2f)

      val isSelected = selectedIndex == idx

      // Student Bar Height
      val studentBarHeight = (point.percentage / 100f) * chartHeight * animProgress
      val studentBarY = paddingTop + chartHeight - studentBarHeight
      val studentBarX = groupCenterX - barWidth - (barSpacing / 2f)

      // Class Avg Bar Height
      val avgBarHeight = (point.classAverage / 100f) * chartHeight * animProgress
      val avgBarY = paddingTop + chartHeight - avgBarHeight
      val avgBarX = groupCenterX + (barSpacing / 2f)

      // Background selection highlight pillar
      if (isSelected) {
        drawRoundRect(
          color = Color(0xFF0061A4).copy(alpha = 0.08f),
          topLeft = Offset(groupStartX + 2f, paddingTop),
          size = Size(groupWidth - 4f, chartHeight),
          cornerRadius = CornerRadius(8f, 8f)
        )
      }

      // Draw Class Average Bar (Slate)
      drawRoundRect(
        color = Color(0xFFCBD5E1),
        topLeft = Offset(avgBarX, avgBarY),
        size = Size(barWidth, avgBarHeight),
        cornerRadius = CornerRadius(4f, 4f)
      )

      // Draw Student Bar (Brand Blue Capsule Gradient)
      drawRoundRect(
        brush = Brush.verticalGradient(
          colors = if (isSelected) listOf(Color(0xFF004170), Color(0xFF0061A4)) else listOf(Color(0xFF0284C7), Color(0xFF0061A4))
        ),
        topLeft = Offset(studentBarX, studentBarY),
        size = Size(barWidth, studentBarHeight),
        cornerRadius = CornerRadius(4f, 4f)
      )

      // Score on top of student bar if high or selected
      if (isSelected || dataPoints.size <= 5) {
        drawText(
          textMeasurer = textMeasurer,
          text = "${point.percentage.roundToInt()}%",
          topLeft = Offset(studentBarX - 4f, studentBarY - 14f),
          style = TextStyle(fontSize = 8.sp, color = Color(0xFF0061A4), fontWeight = FontWeight.Bold)
        )
      }

      // X-Axis Label
      drawText(
        textMeasurer = textMeasurer,
        text = point.shortName,
        topLeft = Offset(groupCenterX - 18f, paddingTop + chartHeight + 10f),
        style = TextStyle(
          fontSize = 9.sp,
          color = if (isSelected) Color(0xFF0061A4) else Color(0xFF64748B),
          fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
        )
      )
    }
  }
}

/**
 * Subject Breakdown Matrix View
 */
@Composable
fun RechartsSubjectMatrixView(
  dataPoints: List<GradeDataPoint>
) {
  val subjectGroups = remember(dataPoints) {
    dataPoints.groupBy { it.subject }
  }

  LazyColumn(
    modifier = Modifier
      .fillMaxSize()
      .padding(horizontal = 8.dp),
    verticalArrangement = Arrangement.spacedBy(10.dp)
  ) {
    items(subjectGroups.entries.toList()) { (subject, points) ->
      val avg = points.map { it.percentage }.average().toFloat()
      val latest = points.lastOrNull()?.percentage ?: 0f

      Surface(
        color = Color.White,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
        modifier = Modifier.fillMaxWidth()
      ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
              Box(
                modifier = Modifier
                  .size(8.dp)
                  .clip(CircleShape)
                  .background(
                    when (subject.lowercase()) {
                      "physics" -> Color(0xFF0061A4)
                      "mathematics" -> Color(0xFF7E22CE)
                      "chemistry" -> Color(0xFFD97706)
                      else -> Color(0xFF059669)
                    }
                  )
              )
              Text(subject, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFF0F172A))
            }

            Text(
              "Average: ${avg.roundToInt()}% • Latest: ${latest.roundToInt()}%",
              fontWeight = FontWeight.SemiBold,
              fontSize = 11.sp,
              color = Color(0xFF475569)
            )
          }

          // Progress Bar Line
          Box(
            modifier = Modifier
              .fillMaxWidth()
              .height(8.dp)
              .clip(RoundedCornerShape(4.dp))
              .background(Color(0xFFF1F5F9))
          ) {
            Box(
              modifier = Modifier
                .fillMaxWidth(fraction = (avg / 100f).coerceIn(0f, 1f))
                .fillMaxHeight()
                .clip(RoundedCornerShape(4.dp))
                .background(
                  Brush.horizontalGradient(
                    colors = listOf(Color(0xFF0061A4), Color(0xFF38BDF8))
                  )
                )
            )
          }
        }
      }
    }
  }
}

/**
 * AI Performance Diagnostics: Strength Maps, Weak-Spot Isolation & Bespoke Revision Questions
 */
@Composable
fun RechartsAiDiagnosticsView(
  student: Student,
  dataPoints: List<GradeDataPoint>
) {
  var expandedQuestionIndex by remember { mutableStateOf<Int?>(null) }

  val subjectBreakdown = remember(dataPoints, student) {
    val grouped = dataPoints.groupBy { it.subject }
    if (grouped.isNotEmpty()) {
      grouped.map { (subject, pts) ->
        val avg = pts.map { it.percentage }.average().toFloat()
        val status = when {
          avg >= 85f -> "Mastered"
          avg >= 70f -> "Proficient"
          else -> "Needs Attention"
        }
        val weakTopic = when (subject.lowercase()) {
          "physics" -> "Rotational Dynamics & Moment of Inertia"
          "chemistry" -> "Chemical Kinetics & Rate Laws"
          "mathematics" -> "Definite Integrals & Area Under Curves"
          "biology" -> "Cellular Respiration & Krebs Cycle"
          else -> "Core Application Problems"
        }
        Triple(subject, avg, Pair(status, weakTopic))
      }.sortedBy { it.second }
    } else {
      listOf(
        Triple("Physics", 72f, Pair("Proficient", "Rotational Dynamics & Torque")),
        Triple("Chemistry", 84f, Pair("Mastered", "Coordination Compounds")),
        Triple("Mathematics", 68f, Pair("Needs Attention", "Integral Calculus Application"))
      )
    }
  }

  val bespokeQuestions = remember(subjectBreakdown) {
    listOf(
      Triple(
        "Bespoke Challenge #1 (Physics)",
        "A uniform disc of mass M and radius R rolls without slipping. Find the ratio of rotational to total kinetic energy.",
        "💡 Hint: K_rot = (1/2) I ω^2, K_trans = (1/2) M v^2. For a solid disc, I = (1/2) M R^2 and v = ωR. Total K = (3/4) M v^2. Ratio = 1/3."
      ),
      Triple(
        "Bespoke Challenge #2 (Calculus)",
        "Evaluate ∫ (0 to π/2) [ sin(x) / (sin(x) + cos(x)) ] dx using King's property.",
        "💡 Hint: Let I = ∫ sin(x)/(sin(x)+cos(x)). By King's rule ∫ f(x) = ∫ f(a+b-x), I = ∫ cos(x)/(sin(x)+cos(x)). 2I = ∫ 1 dx = π/2 => I = π/4."
      ),
      Triple(
        "Bespoke Challenge #3 (Chemistry)",
        "For a first order reaction, how many half-lives are required for 87.5% completion?",
        "💡 Hint: Remaining fraction = 100% - 87.5% = 12.5% = (1/2)^3. Exactly 3 half-lives are needed."
      )
    )
  }

  LazyColumn(
    modifier = Modifier
      .fillMaxSize()
      .padding(horizontal = 4.dp),
    verticalArrangement = Arrangement.spacedBy(10.dp)
  ) {
    // 1. Conceptual Strength Map
    item {
      Surface(
        color = Color(0xFF0F172A),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, Color(0xFF1E2D42)),
        modifier = Modifier.fillMaxWidth()
      ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
              Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(16.dp))
              Text("Conceptual Strength Map", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color.White)
            }
            Text("Overall: ${student.overallAvg}%", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF38BDF8))
          }

          subjectBreakdown.forEach { (subject, avg, meta) ->
            val (status, _) = meta
            val statusColor = when (status) {
              "Mastered" -> Color(0xFF34D399)
              "Proficient" -> Color(0xFF60A5FA)
              else -> Color(0xFFFBBF24)
            }

            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
              ) {
                Text(subject, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFFE2E8F0))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                  Surface(
                    color = statusColor.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(4.dp)
                  ) {
                    Text(
                      status,
                      fontSize = 9.sp,
                      fontWeight = FontWeight.Bold,
                      color = statusColor,
                      modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                    )
                  }
                  Text("${avg.roundToInt()}%", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
              }

              Box(
                modifier = Modifier
                  .fillMaxWidth()
                  .height(6.dp)
                  .clip(RoundedCornerShape(3.dp))
                  .background(Color(0xFF1E293B))
              ) {
                Box(
                  modifier = Modifier
                    .fillMaxWidth(fraction = (avg / 100f).coerceIn(0f, 1f))
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(3.dp))
                    .background(statusColor)
                )
              }
            }
          }
        }
      }
    }

    // 2. Localized Weak-Spot Identification
    item {
      Surface(
        color = Color(0xFF0F172A),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, Color(0xFF1E2D42)),
        modifier = Modifier.fillMaxWidth()
      ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(Icons.Default.TrendingDown, contentDescription = null, tint = Color(0xFFF87171), modifier = Modifier.size(16.dp))
            Text("Localized Weak-Spot Detection", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color.White)
          }

          subjectBreakdown.take(2).forEach { (subject, _, meta) ->
            val (_, weakTopic) = meta
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF1E1E2E))
                .padding(8.dp),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Column(modifier = Modifier.weight(1f)) {
                Text(subject, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF94A3B8))
                Text(weakTopic, fontSize = 11.sp, fontWeight = FontWeight.Medium, color = Color(0xFFFCA5A5))
              }
              Surface(
                color = Color(0xFFEF4444).copy(alpha = 0.15f),
                shape = RoundedCornerShape(4.dp)
              ) {
                Text(
                  "+8% Target Boost",
                  fontSize = 9.sp,
                  fontWeight = FontWeight.Bold,
                  color = Color(0xFFF87171),
                  modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                )
              }
            }
          }
        }
      }
    }

    // 3. Bespoke Revision Questions
    item {
      Text(
        "Bespoke AI Revision Challenge",
        fontWeight = FontWeight.Bold,
        fontSize = 12.sp,
        color = Color(0xFF38BDF8),
        modifier = Modifier.padding(top = 2.dp)
      )
    }

    items(bespokeQuestions.size) { idx ->
      val (title, question, hint) = bespokeQuestions[idx]
      val isExpanded = expandedQuestionIndex == idx

      Surface(
        color = Color(0xFF131D31),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, Color(0xFF1E2D42)),
        modifier = Modifier.fillMaxWidth()
      ) {
        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Text(title, fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Color(0xFF38BDF8))
            TextButton(
              onClick = {
                expandedQuestionIndex = if (isExpanded) null else idx
              },
              contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
            ) {
              Text(
                if (isExpanded) "Hide Hint" else "💡 Show Hint",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFFBBF24)
              )
            }
          }

          Text(question, fontSize = 11.sp, color = Color(0xFFE2E8F0), lineHeight = 16.sp)

          AnimatedVisibility(visible = isExpanded) {
            Surface(
              color = Color(0xFF0D1526),
              shape = RoundedCornerShape(6.dp),
              border = BorderStroke(1.dp, Color(0xFF2E3D52)),
              modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
            ) {
              Text(
                hint,
                fontSize = 10.sp,
                color = Color(0xFF93C5FD),
                modifier = Modifier.padding(8.dp),
                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
              )
            }
          }
        }
      }
    }
  }
}
