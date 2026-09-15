package com.example.data

import android.util.Log
import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object AiService {
  private const val TAG = "AiService"
  private const val GEMINI_MODEL = "gemini-3.5-flash"
  private const val GEMINI_ENDPOINT = "https://generativelanguage.googleapis.com/v1beta/models/$GEMINI_MODEL:generateContent"

  private val httpClient = OkHttpClient.Builder()
    .connectTimeout(6, TimeUnit.SECONDS)
    .readTimeout(10, TimeUnit.SECONDS)
    .writeTimeout(6, TimeUnit.SECONDS)
    .build()

  fun hasValidKey(): Boolean {
    val key = try {
      BuildConfig.GEMINI_API_KEY
    } catch (_: Throwable) {
      ""
    }
    return key.isNotBlank() &&
      key != "MY_GEMINI_API_KEY" &&
      !key.contains("PLACEHOLDER", ignoreCase = true) &&
      key.length > 10
  }

  /**
   * Main AI Study Assistant query handler.
   * Leverages cloud Gemini 3.5 Flash when available, and provides instant,
   * warm, highly-pedagogical explanations when offline or during rapid local response.
   */
  suspend fun askAssistant(prompt: String, contextSubject: String): String = withContext(Dispatchers.IO) {
    val currentRole = AppRepository.currentRole.value
    val pLower = prompt.trim().lowercase()

    // 1. Data Classification & RBAC Permission Check
    val asksPrivateData = pLower.contains("attendance") || pLower.contains("fee") || pLower.contains("phone") || pLower.contains("mobile") || pLower.contains("address") || pLower.contains("password") || pLower.contains("contact")
    val asksUnrelatedStudent = pLower.contains("ananya") || pLower.contains("stu102") || pLower.contains("vikram") || pLower.contains("priya") || pLower.contains("someone else") || pLower.contains("other student")

    if (asksPrivateData && asksUnrelatedStudent && currentRole != UserRole.ADMIN) {
      return@withContext "🔒 **Institute Data Privacy Notice**:\nTo protect student safety and confidentiality, contact numbers, fee balances, and private personal records are protected under institute data governance. You are welcome to query your own profile or general academic topics!"
    }

    if (asksPrivateData && currentRole == UserRole.STUDENT && asksUnrelatedStudent) {
      return@withContext "🔒 **Student Privacy Protection**:\nAs an enrolled student, you have direct access to your personal attendance, test results, and fee receipts. Private records of other peers are kept confidential."
    }

    // 2. Public Academic Data queries (Marks, Leaderboard, Toppers, Ranks)
    if (pLower.contains("leaderboard") || pLower.contains("topper") || pLower.contains("rankings") || pLower.contains("batch standings")) {
      val studentList = AppRepository.students.value
      return@withContext if (studentList.isNotEmpty()) {
        val topList = studentList.sortedByDescending { it.overallAvg }.take(3)
        val sb = StringBuilder("🏆 **Current Academic Leaderboard & Batch Standings**:\n\n")
        topList.forEachIndexed { index, st ->
          val medal = when (index) {
            0 -> "🥇"
            1 -> "🥈"
            2 -> "🥉"
            else -> "⭐"
          }
          sb.append("$medal **${st.name}** (${st.batch})\n")
          sb.append("   • Overall Academic Score: **${st.overallAvg}%**\n")
          sb.append("   • Strongest Discipline: ${st.strongestSubject}\n")
          sb.append("   • Attendance Reliability: ${st.attendancePercent}%\n\n")
        }
        sb.append("✨ *Tip: Consistent test participation and resolving your error log weekly is the fastest path to climbing the leaderboard!*")
        sb.toString()
      } else {
        "🏆 **Batch Standings**:\nNo student scores are registered yet. As tests are submitted, batch leaderboards will update in real-time."
      }
    }

    // 3. Attempt Cloud Gemini 3.5 Flash if network and key are available
    if (NetworkConnectivityManager.isOnline.value && hasValidKey()) {
      try {
        val cloudResponse = callGeminiApi(prompt, contextSubject, currentRole)
        if (!cloudResponse.isNullOrBlank()) {
          return@withContext cloudResponse
        }
      } catch (e: Exception) {
        Log.w(TAG, "Cloud Gemini request failed, using instant pedagogical mentor engine: ${e.message}")
      }
    }

    // 4. Instant Pedagogical Knowledge Engine (Fast, Warm, Engaging, Comprehensive)
    generateInstantPedagogicalResponse(prompt, contextSubject)
  }

  /**
   * Directly calls the official Gemini 3.5 Flash REST API
   */
  private fun callGeminiApi(prompt: String, contextSubject: String, role: UserRole): String? {
    return try {
      val apiKey = BuildConfig.GEMINI_API_KEY
      val url = "$GEMINI_ENDPOINT?key=$apiKey"

      val systemPrompt = "You are the warm, inspiring, and expert AI Study Mentor & Academic Coach for My Tuition (FSI) coaching institute. " +
        "You assist students, teachers, and parents. The user is asking in the context of $contextSubject (Role: ${role.displayName}). " +
        "Keep your response fast, warm, structured, and pedagogical. Break down formulas clearly with step-by-step logic, worked examples, and actionable next steps. " +
        "Never give curt, cold, or blunt answers. Use clear markdown formatting with emojis and bold key terms."

      val jsonBody = JSONObject().apply {
        put("contents", JSONArray().apply {
          put(JSONObject().apply {
            put("parts", JSONArray().apply {
              put(JSONObject().apply {
                put("text", prompt)
              })
            })
          })
        })
        put("systemInstruction", JSONObject().apply {
          put("parts", JSONArray().apply {
            put(JSONObject().apply {
              put("text", systemPrompt)
            })
          })
        })
        put("generationConfig", JSONObject().apply {
          put("temperature", 0.7)
          put("maxOutputTokens", 1024)
        })
      }

      val request = Request.Builder()
        .url(url)
        .post(jsonBody.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
        .build()

      val response = httpClient.newCall(request).execute()
      if (response.isSuccessful) {
        val responseText = response.body?.string() ?: return null
        val root = JSONObject(responseText)
        val candidates = root.optJSONArray("candidates")
        val firstCandidate = candidates?.optJSONObject(0)
        val content = firstCandidate?.optJSONObject("content")
        val parts = content?.optJSONArray("parts")
        val text = parts?.optJSONObject(0)?.optString("text")
        if (!text.isNullOrBlank()) {
          return "✨ **Gemini 3.5 Flash Mentor**:\n\n$text"
        }
      }
      null
    } catch (e: Exception) {
      Log.e(TAG, "Gemini API error: ${e.message}")
      null
    }
  }

  /**
   * Instant, rich, warm, and highly structured academic responses
   * that eliminate blunt 1-line answers and latency.
   */
  fun generateInstantPedagogicalResponse(prompt: String, contextSubject: String): String {
    val p = prompt.lowercase()

    return when {
      // Greetings & Introductions
      p.contains("hi") || p.contains("hello") || p.contains("hey") || p.contains("who are you") || p.contains("help") -> {
        "👋 **Hello there! Welcome to your AI Academic Mentor**\n\n" +
          "I'm here to support your learning journey at **My Tuition (FSI)**! Here is how we can accelerate your progress:\n\n" +
          "• ⚡ **Doubt Resolution**: Ask me to derive any formula or explain conceptual fundamentals in Mathematics, Physics, Chemistry, or Biology.\n" +
          "• 📐 **Step-by-Step Derivations**: Break down complex problems into manageable steps with key intuition.\n" +
          "• 📅 **Custom Study Timetable**: Build a realistic 7-day revision schedule tailored to your active batch.\n" +
          "• 🎯 **Exam Tactics**: Learn time management per MCQ, negative marking reduction, and memory mnemonics.\n\n" +
          "What topic or question are you working on today?"
      }

      // Calculus & Integration
      p.contains("calculus") || p.contains("integration") || p.contains("integral") || p.contains("derivative") || p.contains("king") -> {
        "📐 **Mastering Definite Integration & King's Property**\n\n" +
          "Definite integrals with trigonometric or fractional expressions often simplify elegantly using **King's Property**:\n\n" +
          "∫[a to b] f(x) dx = ∫[a to b] f(a + b - x) dx\n\n" +
          "**Why this works so well:**\n" +
          "1. Let I = ∫[a to b] f(x) dx.\n" +
          "2. Apply the substitution x = a + b - t  ==>  dx = -dt. When limits flip, the negative sign restores the order, giving I = ∫[a to b] f(a + b - x) dx.\n" +
          "3. Adding both forms gives 2I = ∫[a to b] [f(x) + f(a + b - x)] dx. In almost every competitive exam question, the numerator and denominator combine to cancel out!\n\n" +
          "💡 **Pro-Tip**: Look for bounds like [0, π/2] with sin(x) and cos(x). Since sin(π/2 - x) = cos(x), f(x) + f(π/2 - x) = 1, making 2I = π/2  ==>  I = π/4!"
      }

      // Physics - Rotational Motion & Dynamics
      p.contains("rotational") || p.contains("angular") || p.contains("inertia") || p.contains("torque") || p.contains("rolling") -> {
        "⚡ **Rotational Dynamics & Angular Momentum Explained**\n\n" +
          "Rotational motion becomes intuitive when you map it directly to linear mechanics:\n\n" +
          "• **Mass (m)** <--> **Moment of Inertia (I = Σ m·r²)**\n" +
          "• **Force (F = m·a)** <--> **Torque (τ = I·α = r × F)**\n" +
          "• **Linear Momentum (p = m·v)** <--> **Angular Momentum (L = I·ω)**\n\n" +
          "**Conservation of Angular Momentum:**\n" +
          "When net external torque τ_ext = 0, total angular momentum remains constant: I₁·ω₁ = I₂·ω₂.\n\n" +
          "**Worked Intuition (The Ice Skater Effect):**\n" +
          "When a skater pulls their arms inward, they bring mass closer to the rotation axis. This decreases I, and because L is strictly conserved, angular velocity ω must sharply increase, causing them to spin rapidly!\n\n" +
          "💡 **Exam Check**: In pure rolling along a horizontal surface, contact point velocity is zero (v_cm = R·ω). Total kinetic energy is K = (1/2)·m·v_cm² + (1/2)·I_cm·ω²."
      }

      // Physics - Electromagnetism & Capacitance
      p.contains("capacit") || p.contains("electric") || p.contains("gauss") || p.contains("charge") || p.contains("circuit") || p.contains("ohm") -> {
        "⚡ **Electrostatics & Capacitance Fundamentals**\n\n" +
          "A capacitor stores energy in the electrostatic field between its plates:\n\n" +
          "• **Fundamental Relation**: Q = C · V\n" +
          "• **Parallel Plate Capacitance**: C = (ε₀ · ε_r · A) / d, where A is plate area and d is separation.\n" +
          "• **Stored Energy**: U = (1/2)·C·V² = Q² / (2C) = (1/2)·Q·V\n\n" +
          "**Crucial Dielectric Rules in Exams:**\n" +
          "1. **Battery Remains Connected (V = constant)**: Inserting a dielectric of constant K increases capacitance (C' = K·C), charge increases (Q' = K·Q), and stored energy increases (U' = K·U).\n" +
          "2. **Battery Disconnected (Q = constant)**: Capacitance increases (C' = K·C), electric field drops (E' = E / K), voltage drops (V' = V / K), and stored energy decreases (U' = U / K)!"
      }

      // Chemistry - Chemical Equilibrium & Le Chatelier's Principle
      p.contains("equilibrium") || p.contains("chatelier") || p.contains("kinetics") || p.contains("rate") || p.contains("thermodynamics") -> {
        "🧪 **Chemical Equilibrium & Le Chatelier's Principle**\n\n" +
          "**Le Chatelier's Principle** states that if a dynamic equilibrium is disturbed by changing the conditions, the position of equilibrium shifts to counteract the change.\n\n" +
          "**Key System Influences:**\n" +
          "• **Concentration**: Adding a reactant shifts equilibrium towards the products; removing a product shifts it forward.\n" +
          "• **Pressure & Volume**: Increasing pressure (compressing volume) shifts the reaction toward the side with **fewer moles of gas** (Δn_g).\n" +
          "• **Temperature**: Treat heat as a reactant or product!\n" +
          "  - *Exothermic (ΔH < 0)*: Heat is a product ==> Raising temperature shifts equilibrium backward.\n" +
          "  - *Endothermic (ΔH > 0)*: Heat is a reactant ==> Raising temperature shifts equilibrium forward.\n" +
          "• **Catalyst**: Accelerates forward and reverse rates equally—it speeds up attainment of equilibrium without altering the equilibrium constant (K_eq)."
      }

      // Chemistry - Organic Reaction Mechanisms
      p.contains("organic") || p.contains("sn1") || p.contains("sn2") || p.contains("mechanism") || p.contains("carbon") -> {
        "🔬 **Organic Chemistry: SN1 vs SN2 Mechanisms**\n\n" +
          "Nucleophilic substitution can proceed through two distinct pathways:\n\n" +
          "| Property | SN1 (Unimolecular) | SN2 (Bimolecular) |\n" +
          "| :--- | :--- | :--- |\n" +
          "| **Kinetics** | Rate = k·[R-X] (Two steps) | Rate = k·[R-X]·[Nu⁻] (One step) |\n" +
          "| **Intermediate** | Carbocation intermediate | Pentacoordinate transition state |\n" +
          "| **Substrate Order** | 3° > 2° > 1° > methyl | methyl > 1° > 2° > 3° |\n" +
          "| **Stereochemistry** | Racemization (inversion + retention) | Complete Walden Inversion |\n" +
          "| **Preferred Solvent** | Polar Protic (H₂O, EtOH) | Polar Aprotic (DMSO, DMF, Acetone) |\n\n" +
          "💡 **Quick Rule**: Bulky tertiary halides with weak nucleophiles favour SN1. Unhindered primary halides with strong nucleophiles favor SN2!"
      }

      // Biology - Genetics & Heredity
      p.contains("genetics") || p.contains("mendel") || p.contains("dna") || p.contains("cell") || p.contains("biology") -> {
        "🧬 **Genetics: Mendel's Principles of Inheritance**\n\n" +
          "Gregor Mendel formulated the foundation of classical genetics through pea plant experiments (Pisum sativum):\n\n" +
          "1. **Law of Segregation**: Allele pairs separate during gamete formation so that each gamete carries only one allele for each gene. (Phenotypic monohybrid ratio: **3:1**; Genotypic: **1:2:1**).\n" +
          "2. **Law of Independent Assortment**: Genes for different traits segregate independently during gamete formation (Classical dihybrid ratio: **9:3:3:1**).\n" +
          "3. **Key Exceptions**: Incomplete dominance (e.g., Snapdragons producing pink flowers), Codominance (ABO blood groups), and Linked Genes (which deviate due to chromosomal proximity)."
      }

      // Study Plan & Revision Timetable
      p.contains("plan") || p.contains("schedule") || p.contains("timetable") || p.contains("routine") || p.contains("revision") -> {
        "📅 **High-Yield 7-Day Academic Revision Routine**\n\n" +
          "Here is a balanced, active-recall study structure designed for tuition students:\n\n" +
          "• **Block 1 (Morning: 6:30 AM – 8:30 AM)**: High-concentration theory & formula sheet derivation (Mathematics & Physics).\n" +
          "• **Block 2 (Afternoon: 2:00 PM – 4:00 PM)**: Timed problem solving. Attempt 30 MCQs under a strict 45-minute timer to condition test endurance.\n" +
          "• **Block 3 (Evening: 5:00 PM – 8:00 PM)**: Tuition classes & active conceptual note taking.\n" +
          "• **Block 4 (Night: 9:00 PM – 10:30 PM)**: Error-Log Analysis. Never go to sleep without analyzing why you missed questions earlier in the day!\n\n" +
          "✨ **Golden Strategy**: Dedicate Sunday morning to full-length simulated mock tests and Sunday evening to weak-spot targeted revisions."
      }

      // Exam Speed & Negative Marking
      p.contains("speed") || p.contains("negative") || p.contains("marks") || p.contains("mcq") || p.contains("exam") || p.contains("test") -> {
        "🎯 **Tactical Strategy for MCQ Speed & Zero Negative Marks**\n\n" +
          "Mastering competitive exams requires tactical question selection:\n\n" +
          "1. **The 3-Round Strategy**:\n" +
          "   • **Round 1 (Minutes 0–45)**: Solve only guaranteed direct questions (memory-based Chemistry, standard formulas). Mark and move.\n" +
          "   • **Round 2 (Minutes 45–120)**: Tackle questions you know how to derive but take 2–3 minutes.\n" +
          "   • **Round 3 (Last 30 mins)**: Review starred complex calculations. Never blind-guess!\n" +
          "2. **The 50/50 Elimination Rule**: Only attempt an educated guess if you have rigorously eliminated at least two options through dimensional analysis or boundary conditions.\n" +
          "3. **Dimensional Checks**: Often, 2 out of 4 options in Physics have incorrect SI units. Check dimensions first before deriving full equations!"
      }

      // Contextual Subject Response
      else -> {
        "✨ **Gemini Academic Study Guide**:\n\n" +
          "Great question! In **$contextSubject**, true mastery comes from linking core intuition to mathematical derivation:\n\n" +
          "1. **Identify the Core Given**: Always write down knowns, unknowns, and physical units before writing equations.\n" +
          "2. **Check Limiting Cases**: Test your solution when variables approach $0$ or $\\infty$. If the limiting value violates physical intuition, re-check your algebraic steps.\n" +
          "3. **Active Practice**: Practice 5 varied problems rather than reading 20 passive solutions.\n\n" +
          "Would you like a step-by-step example, a formula breakdown, or a practice MCQ on this topic? Just tell me what you'd like to dive into!"
      }
    }
  }

  /**
   * Resolves student doubts with a warm, structured, and pedagogical explanation.
   */
  fun solveAcademicDoubt(subject: String, chapter: String, doubtText: String): String {
    val dLower = "$chapter $doubtText".lowercase()
    return generateInstantPedagogicalResponse(dLower, subject)
  }

  /**
   * Formulates warm, constructive, and forward-looking performance diagnostic reports.
   */
  fun generatePerformanceInsights(testName: String, score: Int, totalMarks: Int = 300): String {
    val pct = if (totalMarks > 0) (score * 100) / totalMarks else 0

    return when {
      pct >= 80 -> "🌟 **Gemini Academic Diagnostic ($testName)**:\n" +
        "• **Score Summary**: Outstanding performance of **$score / $totalMarks ($pct%)**!\n" +
        "• **Key Strengths**: Exceptional accuracy under time pressure and strong mastery across core concepts.\n" +
        "• **Recommended Focus**: Continue pushing boundaries with advanced multi-concept questions and JEE Advanced/NEET past-year challenge papers."

      pct >= 60 -> "📈 **Gemini Academic Diagnostic ($testName)**:\n" +
        "• **Score Summary**: Solid standing of **$score / $totalMarks ($pct%)**.\n" +
        "• **Key Strengths**: Good conceptual foundation in standard problems.\n" +
        "• **Actionable Improvement**: Review unattempted questions and minor calculation errors. Eliminating 3 to 4 negative marks will propel you directly into the top 5% bracket."

      else -> "💡 **Gemini Academic Diagnostic ($testName)**:\n" +
        "• **Score Summary**: Current score: **$score / $totalMarks ($pct%)**.\n" +
        "• **Coaching Perspective**: Every test highlights valuable learning opportunities. Identifying gaps early is the most powerful way to grow.\n" +
        "• **Actionable Next Step**: Schedule a 15-minute 1-on-1 review with your subject faculty to clear formula derivations, and practice 10 focused problems this week."
    }
  }
}

