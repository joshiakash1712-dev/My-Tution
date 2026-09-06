package com.example.data

enum class UserRole(val displayName: String, val subtitle: String) {
  ADMIN("Administrator", "Institute Director & System Admin"),
  TEACHER("Teacher / Faculty", "Teaching Staff & Class Manager"),
  STUDENT("Student", "Learner Portal"),
  PARENT("Parent / Guardian", "Parent Monitoring & Fee Portal")
}

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
  val adminPhone: String = "+91 9822100000",
  val adminEmail: String = "admin@mytuition.com",
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
  val activationCode: String = "742189",
  val isActivated: Boolean = true
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
  val email: String = "faculty@mytuition.com",
  val gender: String = "Male",
  val dob: String = "12/04/1988",
  val joiningDate: String = "01/06/2024",
  val activationCode: String = "891420",
  val isActivated: Boolean = true
)

data class Batch(
  val id: String,
  val name: String,
  val schedule: String,
  val studentCount: Int,
  val teacherName: String
)

data class Subject(
  val id: String,
  val name: String,
  val completionPercent: Int,
  val completedChapters: List<String>,
  val pendingChapters: List<String>
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
  val recordedBy: String? = null
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
  val aiSuggestion: String
)

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
  val lastReminded: String = ""
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
  val timestamp: Long = System.currentTimeMillis()
)

data class LeadRecord(
  val id: String,
  val name: String,
  val mobile: String,
  val className: String,
  val stream: String,
  val source: String,
  val inquiryDate: String,
  val assignedCounselor: String,
  val status: String // "New" | "Contacted" | "Follow-Up" | "Demo Scheduled" | "Admitted" | "Rejected"
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
  val status: String // "Pending" | "Approved" | "Rejected"
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
  val tags: List<String>
)

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
  val photoUrl: String? = null
)

data class HomeworkItem(
  val id: String,
  val title: String,
  val subject: String,
  val dueDate: String,
  val description: String,
  val status: String // "Pending" | "Submitted" | "Evaluated"
)

data class TimetableItem(
  val id: String,
  val day: String, // "Monday" .. "Saturday"
  val startTime: String,
  val endTime: String,
  val subject: String,
  val batch: String,
  val room: String
)

data class DynamicTimetableEntry(
  val id: String,
  val subject: String,
  val batch: String,
  val startTime: String,
  val endTime: String,
  val createdAt: Long, // milliseconds timestamp
  val teacherName: String = "Subject Faculty"
)

data class StudyLogItem(
  val id: String,
  val date: String,
  val hours: Float,
  val subjects: String,
  val chapters: String
)

data class GoalItem(
  val targetMarks: Int,
  val targetPercent: Int,
  val targetRank: Int,
  val targetAttendance: Int,
  val weeklyStudyHours: Int
)

data class BadgeItem(
  val id: String,
  val name: String,
  val description: String,
  val iconName: String,
  val earned: Boolean
)

data class NotificationItem(
  val id: String,
  val title: String,
  val message: String,
  val time: String,
  val type: String, // "Absence" | "Fee" | "Test" | "Announcement"
  val recipientRole: String,
  val isRead: Boolean = false
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
  val status: String // "Scheduled" | "Completed"
)

data class OfflineBlockedEvent(
  val id: String = java.util.UUID.randomUUID().toString(),
  val actionName: String,
  val reason: String = "Internet connection is required to record changes or modify data. The app is in read-only mode to prevent data desynchronization.",
  val timestamp: Long = System.currentTimeMillis()
)
