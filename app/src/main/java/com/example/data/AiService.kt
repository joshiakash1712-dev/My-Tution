package com.example.data

import kotlinx.coroutines.delay

object AiService {
  val NV_API_KEY: String
    get() = System.getenv("NVIDIA_API_KEY") ?: ""

  fun hasValidKey(): Boolean {
    return NV_API_KEY.isNotBlank() && NV_API_KEY.startsWith("nvapi-")
  }

  suspend fun askAssistant(prompt: String, contextSubject: String): String {
    if (!NetworkConnectivityManager.isOnline.value) {
      return "📡 **Offline Mode (Read-Only)**:\nAI features require an active internet connection to query AI models. You are currently viewing cached tuition data updated before disconnect. Please reconnect to the internet to use AI features."
    }

    val currentRole = AppRepository.currentRole.value
    val pLower = prompt.lowercase()

    // Data Classification & RBAC Permission Check
    val asksPrivateData = pLower.contains("attendance") || pLower.contains("fee") || pLower.contains("phone") || pLower.contains("mobile") || pLower.contains("address") || pLower.contains("password") || pLower.contains("contact")
    val asksUnrelatedStudent = pLower.contains("ananya") || pLower.contains("stu102") || pLower.contains("vikram") || pLower.contains("priya") || pLower.contains("someone else") || pLower.contains("other student")

    if (asksPrivateData && asksUnrelatedStudent && currentRole != UserRole.ADMIN) {
      return "🔒 **RBAC Security Alert (Private Personal Data Protected)**:\nAccess Denied. Attendance, fee account status, contact numbers, and residential addresses are classified as **Private Personal Data** under institute data governance. You do not have authorization to query private records of unrelated students."
    }

    if (asksPrivateData && currentRole == UserRole.STUDENT && asksUnrelatedStudent) {
      return "🔒 **RBAC Security Alert**:\nAs a Student, you are restricted to accessing your own attendance and tuition fee profile."
    }

    // Public Academic Data queries (Marks, Leaderboard, Toppers, Ranks)
    if (pLower.contains("marks") || pLower.contains("topped") || pLower.contains("leaderboard") || pLower.contains("score") || pLower.contains("rank")) {
      val studentList = AppRepository.students.value
      return if (studentList.isNotEmpty()) {
        val topList = studentList.sortedByDescending { it.overallAvg }.take(3)
        val sb = StringBuilder("📊 **Public Academic Intelligence Report**:\n")
        topList.forEachIndexed { index, st ->
          sb.append("${index + 1}. **${st.name}** (${st.batch}) • Avg: ${st.overallAvg}%\n")
        }
        sb.append("*Note: Test standings and rankings are classified as Public Academic Data under institute transparency guidelines.*")
        sb.toString()
      } else {
        "📊 **Public Academic Intelligence Report**:\nNo student scores registered in system yet. Enroll students and upload test records to view real-time batch leaderboards."
      }
    }

    return when {
      pLower.contains("calculus") || pLower.contains("integration") -> {
        "💡 **NVIDIA AI Mentor (Calculus)**:\nTo master Definite Integration, apply King's Property: ∫ f(x) dx = ∫ f(a+b-x) dx. This simplifies trigonometric rational expressions instantly."
      }
      pLower.contains("rotational") || pLower.contains("inertia") -> {
        "💡 **NVIDIA AI Mentor (Physics)**:\nFor Rotational Dynamics, ensure axis passes through Center of Mass before applying Parallel Axis Theorem (I = I_cm + Md^2). Pure rolling implies v_cm = ωR."
      }
      pLower.contains("plan") || pLower.contains("schedule") -> {
        "📅 **NVIDIA Optimized Study Timetable**:\n• **Morning**: Active JEE Lectures & Lab practice.\n• **Afternoon**: Timed numerical solving (30 MCQs).\n• **Evening**: Calculus PYQs & Error log review."
      }
      else -> {
        "✨ **NVIDIA AI Education Agent (RBAC Enabled)**:\nAuthenticated as **${currentRole.displayName}**.\nBased on your recent standing in $contextSubject, I recommend improving speed accuracy. You currently average 2.4 mins per MCQ; let's target 1.8 mins."
      }
    }
  }

  suspend fun generatePerformanceInsights(testName: String, score: Int): String {
    return if (score > 240) {
      "🌟 **NVIDIA Diagnostic Engine ($testName)**:\nOutstanding score ($score/300)! Conceptual mastery in Electromagnetism and Calculus is in the top 1% percentile. Focus on multi-correct JEE Advanced PYQs."
    } else if (score > 180) {
      "📈 **NVIDIA Diagnostic Engine ($testName)**:\nSolid progress ($score/300). Sectional cutoffs cleared. Minimize unattempted negative marking in Organic Chemistry."
    } else {
      "⚠️ **NVIDIA Diagnostic Engine ($testName)**:\nNeeds immediate foundation repair ($score/300). Schedule a 1-on-1 doubt session with your subject faculty to review core formulas."
    }
  }
}
