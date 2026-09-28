package com.example.data

enum class UserRole(val displayName: String, val subtitle: String) {
  ADMIN("Administrator", "Institute Director & System Admin"),
  TEACHER("Teacher / Faculty", "Teaching Staff & Class Manager"),
  STUDENT("Student", "Learner Portal"),
  PARENT("Parent / Guardian", "Parent Monitoring & Fee Portal")
}

data class UserAccount(
  val userId: String,
  val email: String,
  val mobile: String,
  val name: String,
  val role: UserRole,
  val linkedEntityId: String = "",
  val linkedStudentIds: List<String> = emptyList(),
  val status: String = "Active",
  val instituteId: String = "FSI_MAIN",
  val createdAt: Long = System.currentTimeMillis(),
  val lastLoginAt: Long = System.currentTimeMillis()
)

enum class DarkThemeMode(val displayName: String) {
  LIGHT("Light Mode"),
  DARK("Dark Mode"),
  SYSTEM("System Default")
}

data class SavedAccount(
  val identifier: String,
  val role: UserRole,
  val displayName: String
)

data class AccountStatusInfo(
  val exists: Boolean,
  val role: UserRole?,
  val displayName: String,
  val isActivated: Boolean,
  val expectedActivationCode: String = "",
  val studentId: String? = null,
  val teacherId: String? = null
)

data class InstituteConfig(
  val instituteName: String = "My Tuition Academy",
  val tagline: String = "Empowering Academic Excellence",
  val founderName: String = "Director",
  val address: String = "Main Campus, Education Hub",
  val contactPhone: String = "+91 9822100000",
  val email: String = "contact@mytuition.com",
  val academicYear: String = "2026–2027",
  val existingStaffCount: Int = 8,
  val selectedGrades: List<String> = listOf("8th", "9th", "10th", "11th", "12th"),
  val gradeSubjects: Map<String, List<String>> = mapOf(
    "LKG" to listOf("English Rhymes", "Numbers", "Drawing"),
    "UKG" to listOf("English", "Maths Basics", "General Knowledge"),
    "1st" to listOf("English", "Mathematics", "EVS", "Regional Language"),
    "2nd" to listOf("English", "Mathematics", "EVS", "Regional Language"),
    "3rd" to listOf("English", "Mathematics", "Science", "Social Studies"),
    "4th" to listOf("English", "Mathematics", "Science", "Social Studies"),
    "5th" to listOf("English", "Mathematics", "Science", "Social Science", "Hindi"),
    "6th" to listOf("English", "Mathematics", "General Science", "Social Science"),
    "7th" to listOf("English", "Mathematics", "General Science", "Social Science"),
    "8th" to listOf("Mathematics", "Science", "English", "Social Science"),
    "9th" to listOf("Mathematics", "Science", "English", "Social Science"),
    "10th" to listOf("Mathematics", "Science", "English", "Social Science"),
    "11th" to listOf("Physics", "Chemistry", "Mathematics", "Biology", "Computer Science"),
    "12th" to listOf("Physics", "Chemistry", "Mathematics", "Biology", "Electronics")
  ),
  val competitiveExams: List<String> = listOf("NEET", "CET", "JEE Main", "JEE Advanced", "12th Board"),
  val streams: List<String> = listOf("PCM", "PCB", "PCMB", "PCMC"),
  val gradeFees: Map<String, Int> = mapOf(
    "8th" to 22000,
    "9th" to 25000,
    "10th" to 30000,
    "11th" to 55000,
    "12th" to 65000
  ),
  val adminFullName: String = "Institute Administrator",
  val adminRole: String = "Director",
  val adminGender: String = "Male",
  val adminDob: String = "15/08/1980",
  val adminPhone: String = "",
  val adminEmail: String = "",
  val isSetupCompleted: Boolean = true
)

data class Student(
  val id: String,
  val name: String,
  val photo: String = "",
  val mobile: String,
  val parentName: String,
  val parentContact: String,
  val email: String,
  val dob: String,
  val gender: String,
  val address: String,
  val school: String,
  val className: String,
  val batch: String,
  val stream: String,
  val admissionDate: String,
  val status: String = "Active",
  val attendancePercent: Int = 85,
  val overallAvg: Int = 80,
  val rank: Int = 1,
  val strongestSubject: String = "Physics",
  val weakestSubject: String = "Mathematics",
  val recentScores: List<Pair<String, Int>> = emptyList(),
  // Extended fields matching Suraj Prabhodhini & Radal Institute admission reference
  val caste: String = "General",
  val motherTongue: String = "Marathi",
  val parentOccupation: String = "Business / Service",
  val yearlyIncome: String = "₹4,50,000",
  val aadharPhoto: String = "",
  val markMemoPhoto10th: String = "",
  val markMemoPhoto11th: String = "",
  val targetExam: String = "JEE / NEET / CET",
  val activationCode: String = "",
  val isActivated: Boolean = false,
  val batchId: String = "",
  val parentId: String = "",
  val parentEmail: String = "",
  val feeStatus: String = "Pending",
  val instituteId: String = "FSI_MAIN",
  val admissionTimestamp: Long = System.currentTimeMillis()
)

data class Teacher(
  val id: String,
  val name: String,
  val photo: String = "",
  val subject: String,
  val contact: String,
  val qualification: String,
  val experience: String,
  val assignedBatches: List<String>,
  val classesTaken: Int,
  val attendancePercent: Int,
  val feedbackRating: Float,
  val salary: Int,
  val incentives: Int,
  val deductions: Int,
  val status: String,
  val email: String = "",
  val gender: String = "Male",
  val dob: String = "12/04/1988",
  val joiningDate: String = "01/06/2024",
  val activationCode: String = "",
  val isActivated: Boolean = false,
  val assignedBatchIds: List<String> = emptyList(),
  val instituteId: String = "FSI_MAIN"
) {
  val subjects: String get() = subject
}

data class Batch(
  val id: String,
  val name: String,
  val schedule: String,
  val studentCount: Int,
  val teacherName: String,
  val teacherId: String = "",
  val instituteId: String = "FSI_MAIN"
)

data class Subject(
  val id: String,
  val name: String,
  val completionPercent: Int,
  val completedChapters: List<String>,
  val pendingChapters: List<String>,
  val batchId: String = "",
  val batchName: String = "",
  val teacherId: String = "",
  val instituteId: String = "FSI_MAIN"
)

data class AttendanceRecord(
  val id: String,
  val date: String,
  val studentId: String,
  val studentName: String,
  val batchName: String,
  val status: String, // "Present" | "Absent" | "Late"
  val timestamp: Long = System.currentTimeMillis(),
  val lastModifiedTimestamp: Long = System.currentTimeMillis(),
  val day: Int = 0,
  val month: Int = 0,
  val monthName: String = "",
  val year: Int = 0,
  val time: String = "",
  val reason: String? = null,
  val recordedBy: String? = null,
  val batchId: String = "",
  val instituteId: String = "FSI_MAIN"
)

data class TestRecord(
  val id: String,
  val testName: String,
  val subject: String,
  val date: String,
  val batch: String,
  val totalMarks: Int,
  val studentMarks: Map<String, Int>, // studentId -> score
  val remarks: String,
  val aiAnalysisStrong: List<String>,
  val aiAnalysisWeak: List<String>,
  val aiSuggestion: String,
  val batchId: String = "",
  val subjectId: String = "",
  val questionIds: List<String> = emptyList(),
  val durationMinutes: Int = 15,
  val instituteId: String = "FSI_MAIN"
)

data class StudentTestAttempt(
  val attemptId: String,
  val testId: String,
  val testName: String,
  val studentId: String,
  val studentName: String,
  val instituteId: String = "FSI_MAIN",
  val answers: Map<String, Int>, // questionId -> selectedOptionIndex
  val score: Int,
  val totalMarks: Int,
  val correctCount: Int,
  val wrongCount: Int,
  val unattemptedCount: Int,
  val timeSpentSeconds: Int,
  val status: String = "Submitted", // "InProgress" | "Submitted"
  val startedAt: Long = System.currentTimeMillis(),
  val submittedAt: Long = System.currentTimeMillis()
) {
  val id: String get() = attemptId
  val maxMarks: Int get() = totalMarks
  val durationSeconds: Int get() = timeSpentSeconds
}

data class FeeRecord(
  val id: String,
  val studentName: String,
  val feeAmount: Int,
  val dueDate: String,
  val paidAmount: Int,
  val pendingAmount: Int,
  val paymentStatus: String, // "Paid" | "Pending" | "Overdue"
  val month: String = "November 2025",
  val remindedCount: Int = 0,
  val lastReminded: String = "",
  val studentId: String = "",
  val batchName: String = "",
  val batchId: String = "",
  val instituteId: String = "FSI_MAIN"
)

data class StudentFeePayment(
  val id: String,
  val studentId: String,
  val studentName: String,
  val amount: Double,
  val paymentDate: String, // "dd/MM/yyyy" or "16 Aug 2026"
  val paymentStatus: String, // "Paid" | "Pending" | "Partial" | "Overdue" | "Refunded"
  val feeType: String = "Monthly Tuition", // "Monthly Tuition" | "Exam Fee" | "Registration" | "Material"
  val paymentMode: String = "UPI", // "UPI" | "Cash" | "Card" | "Net Banking" | "Cheque"
  val receiptNo: String = "",
  val month: String = "August 2026",
  val remarks: String = "",
  val recordedBy: String = "Admin",
  val timestamp: Long = System.currentTimeMillis(),
  val invoiceId: String = "",
  val instituteId: String = "FSI_MAIN",
  val transactionId: String = ""
) {
  val paymentId: String get() = id
  val status: String get() = paymentStatus
  val amountPaid: Double get() = amount
  val paymentMethod: String get() = paymentMode
  val createdBy: String get() = recordedBy
  val createdAt: Long get() = timestamp
}

data class LeadRecord(
  val id: String,
  val name: String,
  val mobile: String,
  val className: String,
  val stream: String,
  val source: String,
  val inquiryDate: String,
  val assignedCounselor: String,
  val status: String, // "New" | "Contacted" | "Follow-Up" | "Demo Scheduled" | "Admitted" | "Rejected"
  val instituteId: String = "FSI_MAIN"
)

data class OnlineFormSubmission(
  val id: String,
  val name: String,
  val mobile: String,
  val email: String,
  val school: String,
  val className: String,
  val stream: String,
  val preferredBatch: String,
  val submissionDate: String,
  val status: String, // "Pending" | "Approved" | "Rejected"
  val instituteId: String = "FSI_MAIN"
)

data class QuestionItem(
  val id: String,
  val subject: String,
  val chapter: String,
  val topic: String,
  val difficulty: String, // "Easy" | "Medium" | "Hard"
  val type: String, // "MCQ" | "Subjective"
  val questionText: String,
  val options: List<String>,
  val correctOption: Int,
  val solution: String,
  val tags: List<String>,
  val subjectId: String = "",
  val testId: String = "",
  val instituteId: String = "FSI_MAIN",
  val marks: Int = 4
) {
  val optionA: String get() = options.getOrElse(0) { "" }
  val optionB: String get() = options.getOrElse(1) { "" }
  val optionC: String get() = options.getOrElse(2) { "" }
  val optionD: String get() = options.getOrElse(3) { "" }
  val correctOptionLetter: String
    get() = when (correctOption) {
      0 -> "A"
      1 -> "B"
      2 -> "C"
      3 -> "D"
      else -> "A"
    }
}

data class DoubtItem(
  val id: String,
  val studentName: String,
  val subject: String,
  val chapter: String,
  val questionText: String,
  val date: String,
  val reply: String?,
  val status: String, // "Pending" | "Resolved"
  val resolutionTime: String?,
  val photoUrl: String? = null,
  val studentId: String = "",
  val batchName: String = "",
  val repliedBy: String = "",
  val instituteId: String = "FSI_MAIN",
  val subjectId: String = "",
  val batchId: String = "",
  val createdAt: Long = System.currentTimeMillis(),
  val updatedAt: Long = System.currentTimeMillis()
) {
  val doubtId: String get() = id
  val isResolved: Boolean get() = status.equals("Resolved", ignoreCase = true) || !reply.isNullOrBlank()
  val doubtText: String get() = questionText
  val photoAttachmentUri: String? get() = photoUrl
}

data class HomeworkItem(
  val id: String,
  val title: String,
  val subject: String,
  val dueDate: String,
  val description: String,
  val status: String, // "Pending" | "Submitted" | "Evaluated"
  val submissionDate: String? = null,
  val teacherFeedback: String? = null,
  val batchName: String = "",
  val assignedBy: String = "",
  val completedByStudentIds: List<String> = emptyList(),
  val batchId: String = "",
  val subjectId: String = "",
  val teacherId: String = "",
  val instituteId: String = "FSI_MAIN"
) {
  val completedStudentIds: List<String> get() = completedByStudentIds
}

data class TimetableItem(
  val id: String,
  val day: String, // "Monday" .. "Saturday"
  val startTime: String,
  val endTime: String,
  val subject: String,
  val batch: String,
  val room: String,
  val teacherName: String = "",
  val batchId: String = "",
  val teacherId: String = "",
  val subjectId: String = "",
  val instituteId: String = "FSI_MAIN"
)

data class DynamicTimetableEntry(
  val id: String,
  val subject: String,
  val batch: String,
  val startTime: String,
  val endTime: String,
  val createdAt: Long, // milliseconds timestamp
  val teacherName: String = "Subject Faculty",
  val batchId: String = "",
  val teacherId: String = "",
  val subjectId: String = "",
  val instituteId: String = "FSI_MAIN",
  val dateStr: String = "",
  val scheduledStartMillis: Long = 0L,
  val scheduledEndMillis: Long = 0L,
  val isArchived: Boolean = false
)

data class StudyLogItem(
  val id: String,
  val date: String,
  val hours: Float,
  val subjects: String,
  val chapters: String,
  val studentId: String = "",
  val instituteId: String = "FSI_MAIN",
  val timestamp: Long = System.currentTimeMillis()
)

data class GoalItem(
  val targetMarks: Int,
  val targetPercent: Int,
  val targetRank: Int,
  val targetAttendance: Int,
  val weeklyStudyHours: Int,
  val studentId: String = "",
  val instituteId: String = "FSI_MAIN"
)

data class BadgeItem(
  val id: String,
  val name: String,
  val description: String,
  val iconName: String,
  val earned: Boolean,
  val studentId: String = "",
  val instituteId: String = "FSI_MAIN"
)

data class NotificationItem(
  val id: String,
  val title: String,
  val message: String,
  val time: String,
  val type: String, // "Absence" | "Fee" | "Test" | "Announcement"
  val recipientRole: String,
  val isRead: Boolean = false,
  val targetStudentId: String = "",
  val targetBatchName: String = "",
  val targetBatchId: String = "",
  val instituteId: String = "FSI_MAIN"
)

data class ParentMeetingItem(
  val id: String,
  val studentName: String,
  val parentName: String,
  val teacherName: String,
  val date: String,
  val time: String,
  val notes: String,
  val actionItems: String,
  val status: String, // "Scheduled" | "Completed"
  val studentId: String = "",
  val parentId: String = "",
  val teacherId: String = "",
  val instituteId: String = "FSI_MAIN"
)

data class OfflineBlockedEvent(
  val id: String = java.util.UUID.randomUUID().toString(),
  val actionName: String,
  val reason: String = "Internet connection is required to record changes or modify data. The app is in read-only mode to prevent data desynchronization.",
  val timestamp: Long = System.currentTimeMillis()
)
