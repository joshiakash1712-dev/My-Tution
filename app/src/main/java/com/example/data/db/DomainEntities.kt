package com.example.data.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Update
import com.example.data.DoubtItem
import com.example.data.FeeRecord
import com.example.data.GoalItem
import com.example.data.HomeworkItem
import com.example.data.LeadRecord
import com.example.data.NotificationItem
import com.example.data.OnlineFormSubmission
import com.example.data.ParentMeetingItem
import com.example.data.QuestionItem
import com.example.data.StudentTestAttempt
import com.example.data.StudyLogItem
import com.example.data.Subject
import com.example.data.TestRecord
import com.example.data.TimetableItem
import com.example.data.UserAccount
import com.example.data.UserRole
import kotlinx.coroutines.flow.Flow
import org.json.JSONObject

// =========================================================================
// 1. USER ACCOUNT ENTITY & DAO (Authentic Role & Identity Binding)
// =========================================================================
@Entity(
  tableName = "user_accounts",
  indices = [
    Index(value = ["email"], unique = true),
    Index(value = ["mobile"])
  ]
)
data class UserAccountEntity(
  @PrimaryKey val userId: String,
  val email: String,
  val mobile: String,
  val name: String,
  val passwordHash: String,
  val passwordSalt: String,
  val role: String, // "ADMIN", "TEACHER", "STUDENT", "PARENT"
  val linkedEntityId: String = "",
  val linkedStudentIds: List<String> = emptyList(),
  val status: String = "Active", // "Active", "Pending Approval", "Suspended"
  val instituteId: String = "FSI_MAIN",
  val createdAt: Long = System.currentTimeMillis(),
  val lastLoginAt: Long = System.currentTimeMillis()
) {
  fun toDomain(): UserAccount {
    val parsedRole = try {
      UserRole.valueOf(role.uppercase())
    } catch (_: Exception) {
      UserRole.STUDENT
    }
    return UserAccount(
      userId = userId,
      email = email,
      mobile = mobile,
      name = name,
      role = parsedRole,
      linkedEntityId = linkedEntityId,
      linkedStudentIds = linkedStudentIds,
      status = status,
      instituteId = instituteId,
      createdAt = createdAt,
      lastLoginAt = lastLoginAt
    )
  }
}

@Dao
interface UserAccountDao {
  @Query("SELECT * FROM user_accounts ORDER BY createdAt ASC")
  fun getAllAccounts(): Flow<List<UserAccountEntity>>

  @Query("SELECT * FROM user_accounts WHERE userId = :userId LIMIT 1")
  suspend fun getAccountById(userId: String): UserAccountEntity?

  @Query("SELECT * FROM user_accounts WHERE LOWER(email) = LOWER(:identifier) OR mobile = :identifier OR LOWER(userId) = LOWER(:identifier) OR LOWER(linkedEntityId) = LOWER(:identifier) LIMIT 1")
  suspend fun findAccountByIdentifier(identifier: String): UserAccountEntity?

  @Query("SELECT COUNT(*) FROM user_accounts")
  suspend fun getCount(): Int

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertAccount(account: UserAccountEntity)

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertAccounts(accounts: List<UserAccountEntity>)

  @Update
  suspend fun updateAccount(account: UserAccountEntity)

  @Query("DELETE FROM user_accounts WHERE userId = :userId")
  suspend fun deleteAccountById(userId: String)

  @Query("UPDATE user_accounts SET status = 'Inactive' WHERE linkedEntityId IN (:entityIds)")
  suspend fun deactivateAccountsByLinkedEntities(entityIds: List<String>)

  @Query("DELETE FROM user_accounts WHERE linkedEntityId IN (:entityIds)")
  suspend fun deleteAccountsByLinkedEntities(entityIds: List<String>)
}

// =========================================================================
// 2. FEE INVOICE ENTITY & DAO (Normalized with studentId)
// =========================================================================
@Entity(
  tableName = "fee_invoices",
  foreignKeys = [
    ForeignKey(
      entity = StudentEntity::class,
      parentColumns = ["id"],
      childColumns = ["studentId"],
      onDelete = ForeignKey.RESTRICT,
      onUpdate = ForeignKey.CASCADE,
      deferred = true
    )
  ],
  indices = [
    Index(value = ["studentId"]),
    Index(value = ["instituteId"])
  ]
)
data class FeeInvoiceEntity(
  @PrimaryKey val id: String,
  val studentId: String,
  val studentName: String,
  val batchName: String = "",
  val batchId: String = "",
  val instituteId: String = "FSI_MAIN",
  val feeAmount: Int,
  val dueDate: String,
  val paidAmount: Int,
  val pendingAmount: Int,
  val paymentStatus: String,
  val month: String,
  val remindedCount: Int = 0,
  val lastReminded: String = "",
  val lastUpdated: Long = System.currentTimeMillis()
) {
  fun toDomain(): FeeRecord = FeeRecord(
    id = id,
    studentName = studentName,
    feeAmount = feeAmount,
    dueDate = dueDate,
    paidAmount = paidAmount,
    pendingAmount = pendingAmount,
    paymentStatus = paymentStatus,
    month = month,
    remindedCount = remindedCount,
    lastReminded = lastReminded,
    studentId = studentId,
    batchName = batchName,
    batchId = batchId,
    instituteId = instituteId
  )

  companion object {
    fun fromDomain(record: FeeRecord): FeeInvoiceEntity = FeeInvoiceEntity(
      id = record.id,
      studentId = record.studentId,
      studentName = record.studentName,
      batchName = record.batchName,
      batchId = record.batchId,
      instituteId = record.instituteId,
      feeAmount = record.feeAmount,
      dueDate = record.dueDate,
      paidAmount = record.paidAmount,
      pendingAmount = record.pendingAmount,
      paymentStatus = record.paymentStatus,
      month = record.month,
      remindedCount = record.remindedCount,
      lastReminded = record.lastReminded
    )
  }
}

@Dao
interface FeeInvoiceDao {
  @Query("SELECT * FROM fee_invoices ORDER BY lastUpdated DESC")
  fun getAllInvoices(): Flow<List<FeeInvoiceEntity>>

  @Query("SELECT * FROM fee_invoices WHERE studentId = :studentId")
  suspend fun getInvoicesByStudentId(studentId: String): List<FeeInvoiceEntity>

  @Query("SELECT * FROM fee_invoices WHERE id = :id LIMIT 1")
  suspend fun getInvoiceById(id: String): FeeInvoiceEntity?

  @Query("SELECT COUNT(*) FROM fee_invoices")
  suspend fun getCount(): Int

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertInvoice(invoice: FeeInvoiceEntity)

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertInvoices(invoices: List<FeeInvoiceEntity>)

  @Query("DELETE FROM fee_invoices WHERE id = :id")
  suspend fun deleteInvoiceById(id: String)

  @Query("DELETE FROM fee_invoices WHERE studentId IN (:studentIds)")
  suspend fun deleteInvoicesByStudentIds(studentIds: List<String>)

  @Query("DELETE FROM fee_invoices")
  suspend fun deleteAllInvoices()
}

// =========================================================================
// 3. HOMEWORK ENTITY & DAO (With Per-Student Completion Tracking)
// =========================================================================
@Entity(tableName = "homeworks")
data class HomeworkEntity(
  @PrimaryKey val id: String,
  val title: String,
  val subject: String,
  val dueDate: String,
  val description: String,
  val status: String,
  val submissionDate: String? = null,
  val teacherFeedback: String? = null,
  val batchName: String = "",
  val assignedBy: String = "",
  val completedStudentIds: List<String> = emptyList(),
  val batchId: String = "",
  val subjectId: String = "",
  val teacherId: String = "",
  val instituteId: String = "FSI_MAIN",
  val createdAt: Long = System.currentTimeMillis()
) {
  fun toDomain(): HomeworkItem = HomeworkItem(
    id = id,
    title = title,
    subject = subject,
    dueDate = dueDate,
    description = description,
    status = status,
    submissionDate = submissionDate,
    teacherFeedback = teacherFeedback,
    batchName = batchName,
    assignedBy = assignedBy,
    completedByStudentIds = completedStudentIds,
    batchId = batchId,
    subjectId = subjectId,
    teacherId = teacherId,
    instituteId = instituteId
  )

  companion object {
    fun fromDomain(item: HomeworkItem): HomeworkEntity = HomeworkEntity(
      id = item.id,
      title = item.title,
      subject = item.subject,
      dueDate = item.dueDate,
      description = item.description,
      status = item.status,
      submissionDate = item.submissionDate,
      teacherFeedback = item.teacherFeedback,
      batchName = item.batchName,
      assignedBy = item.assignedBy,
      completedStudentIds = item.completedByStudentIds,
      batchId = item.batchId,
      subjectId = item.subjectId,
      teacherId = item.teacherId,
      instituteId = item.instituteId
    )
  }
}

@Dao
interface HomeworkDao {
  @Query("SELECT * FROM homeworks ORDER BY createdAt DESC")
  fun getAllHomeworks(): Flow<List<HomeworkEntity>>

  @Query("SELECT * FROM homeworks WHERE id = :id LIMIT 1")
  suspend fun getHomeworkById(id: String): HomeworkEntity?

  @Query("SELECT COUNT(*) FROM homeworks")
  suspend fun getCount(): Int

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertHomework(homework: HomeworkEntity)

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertHomeworks(homeworks: List<HomeworkEntity>)

  @Query("DELETE FROM homeworks WHERE id = :id")
  suspend fun deleteHomeworkById(id: String)

  @Query("DELETE FROM homeworks")
  suspend fun deleteAllHomeworks()
}

// =========================================================================
// 4. DOUBT ENTITY & DAO (With Student ID & Teacher Reply Persistence)
// =========================================================================
@Entity(
  tableName = "doubts",
  foreignKeys = [
    ForeignKey(
      entity = StudentEntity::class,
      parentColumns = ["id"],
      childColumns = ["studentId"],
      onDelete = ForeignKey.RESTRICT,
      onUpdate = ForeignKey.CASCADE,
      deferred = true
    )
  ],
  indices = [
    Index(value = ["studentId"]),
    Index(value = ["instituteId"])
  ]
)
data class DoubtEntity(
  @PrimaryKey val id: String,
  val studentId: String,
  val studentName: String,
  val batchName: String = "",
  val batchId: String = "",
  val subject: String,
  val subjectId: String = "",
  val chapter: String,
  val questionText: String,
  val date: String,
  val reply: String?,
  val repliedBy: String = "",
  val status: String,
  val resolutionTime: String?,
  val photoUrl: String? = null,
  val instituteId: String = "FSI_MAIN",
  val createdAt: Long = System.currentTimeMillis(),
  val updatedAt: Long = System.currentTimeMillis()
) {
  fun toDomain(): DoubtItem = DoubtItem(
    id = id,
    studentName = studentName,
    subject = subject,
    chapter = chapter,
    questionText = questionText,
    date = date,
    reply = reply,
    status = status,
    resolutionTime = resolutionTime,
    photoUrl = photoUrl,
    studentId = studentId,
    batchName = batchName,
    repliedBy = repliedBy,
    instituteId = instituteId,
    subjectId = subjectId,
    batchId = batchId,
    createdAt = createdAt,
    updatedAt = updatedAt
  )

  companion object {
    fun fromDomain(item: DoubtItem): DoubtEntity = DoubtEntity(
      id = item.id,
      studentId = item.studentId,
      studentName = item.studentName,
      batchName = item.batchName,
      batchId = item.batchId,
      subject = item.subject,
      subjectId = item.subjectId,
      chapter = item.chapter,
      questionText = item.questionText,
      date = item.date,
      reply = item.reply,
      repliedBy = item.repliedBy,
      status = item.status,
      resolutionTime = item.resolutionTime,
      photoUrl = item.photoUrl,
      instituteId = item.instituteId,
      createdAt = item.createdAt,
      updatedAt = item.updatedAt
    )
  }
}

@Dao
interface DoubtDao {
  @Query("SELECT * FROM doubts ORDER BY createdAt DESC")
  fun getAllDoubts(): Flow<List<DoubtEntity>>

  @Query("SELECT * FROM doubts WHERE id = :id LIMIT 1")
  suspend fun getDoubtById(id: String): DoubtEntity?

  @Query("SELECT COUNT(*) FROM doubts")
  suspend fun getCount(): Int

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertDoubt(doubt: DoubtEntity)

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertDoubts(doubts: List<DoubtEntity>)

  @Query("DELETE FROM doubts")
  suspend fun deleteAllDoubts()
}

// =========================================================================
// 5. TEST RECORD ENTITY & DAO (Persistent Exams & Student Marks)
// =========================================================================
@Entity(tableName = "test_records")
data class TestRecordEntity(
  @PrimaryKey val id: String,
  val testName: String,
  val subject: String,
  val date: String,
  val batch: String,
  val totalMarks: Int,
  val studentMarksJson: String,
  val remarks: String,
  val aiAnalysisStrong: List<String>,
  val aiAnalysisWeak: List<String>,
  val aiSuggestion: String,
  val batchId: String = "",
  val subjectId: String = "",
  val questionIds: List<String> = emptyList(),
  val durationMinutes: Int = 15,
  val instituteId: String = "FSI_MAIN",
  val createdAt: Long = System.currentTimeMillis()
) {
  fun toDomain(): TestRecord {
    val marksMap = mutableMapOf<String, Int>()
    if (studentMarksJson.isNotBlank()) {
      try {
        val obj = JSONObject(studentMarksJson)
        val keys = obj.keys()
        while (keys.hasNext()) {
          val k = keys.next()
          marksMap[k] = obj.optInt(k, 0)
        }
      } catch (_: Exception) {}
    }
    return TestRecord(
      id = id,
      testName = testName,
      subject = subject,
      date = date,
      batch = batch,
      totalMarks = totalMarks,
      studentMarks = marksMap,
      remarks = remarks,
      aiAnalysisStrong = aiAnalysisStrong,
      aiAnalysisWeak = aiAnalysisWeak,
      aiSuggestion = aiSuggestion,
      batchId = batchId,
      subjectId = subjectId,
      questionIds = questionIds,
      durationMinutes = durationMinutes,
      instituteId = instituteId
    )
  }

  companion object {
    fun fromDomain(record: TestRecord): TestRecordEntity {
      val jsonObj = JSONObject()
      record.studentMarks.forEach { (k, v) -> jsonObj.put(k, v) }
      return TestRecordEntity(
        id = record.id,
        testName = record.testName,
        subject = record.subject,
        date = record.date,
        batch = record.batch,
        totalMarks = record.totalMarks,
        studentMarksJson = jsonObj.toString(),
        remarks = record.remarks,
        aiAnalysisStrong = record.aiAnalysisStrong,
        aiAnalysisWeak = record.aiAnalysisWeak,
        aiSuggestion = record.aiSuggestion,
        batchId = record.batchId,
        subjectId = record.subjectId,
        questionIds = record.questionIds,
        durationMinutes = record.durationMinutes,
        instituteId = record.instituteId
      )
    }
  }
}

@Dao
interface TestRecordDao {
  @Query("SELECT * FROM test_records ORDER BY createdAt DESC")
  fun getAllTests(): Flow<List<TestRecordEntity>>

  @Query("SELECT COUNT(*) FROM test_records")
  suspend fun getCount(): Int

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertTest(test: TestRecordEntity)

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertTests(tests: List<TestRecordEntity>)

  @Query("DELETE FROM test_records")
  suspend fun deleteAllTests()
}

// =========================================================================
// 6. SUBJECT ENTITY & DAO (Persistent Syllabus Progress)
// =========================================================================
@Entity(tableName = "subjects")
data class SubjectEntity(
  @PrimaryKey val id: String,
  val name: String,
  val completionPercent: Int,
  val completedChapters: List<String>,
  val pendingChapters: List<String>,
  val batchId: String = "",
  val batchName: String = "",
  val teacherId: String = "",
  val instituteId: String = "FSI_MAIN"
) {
  fun toDomain(): Subject = Subject(
    id = id,
    name = name,
    completionPercent = completionPercent,
    completedChapters = completedChapters,
    pendingChapters = pendingChapters,
    batchId = batchId,
    batchName = batchName,
    teacherId = teacherId,
    instituteId = instituteId
  )

  companion object {
    fun fromDomain(subject: Subject): SubjectEntity = SubjectEntity(
      id = subject.id,
      name = subject.name,
      completionPercent = subject.completionPercent,
      completedChapters = subject.completedChapters,
      pendingChapters = subject.pendingChapters,
      batchId = subject.batchId,
      batchName = subject.batchName,
      teacherId = subject.teacherId,
      instituteId = subject.instituteId
    )
  }
}

@Dao
interface SubjectDao {
  @Query("SELECT * FROM subjects ORDER BY name ASC")
  fun getAllSubjects(): Flow<List<SubjectEntity>>

  @Query("SELECT COUNT(*) FROM subjects")
  suspend fun getCount(): Int

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertSubject(subject: SubjectEntity)

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertSubjects(subjects: List<SubjectEntity>)

  @Query("DELETE FROM subjects")
  suspend fun deleteAllSubjects()
}

// =========================================================================
// 7. LEAD & ONLINE FORM ENTITIES & DAOS (Persistent Admissions CRM)
// =========================================================================
@Entity(tableName = "leads")
data class LeadEntity(
  @PrimaryKey val id: String,
  val name: String,
  val mobile: String,
  val className: String,
  val stream: String,
  val source: String,
  val inquiryDate: String,
  val assignedCounselor: String,
  val status: String,
  val instituteId: String = "FSI_MAIN",
  val createdAt: Long = System.currentTimeMillis()
) {
  fun toDomain(): LeadRecord = LeadRecord(
    id = id,
    name = name,
    mobile = mobile,
    className = className,
    stream = stream,
    source = source,
    inquiryDate = inquiryDate,
    assignedCounselor = assignedCounselor,
    status = status,
    instituteId = instituteId
  )

  companion object {
    fun fromDomain(lead: LeadRecord): LeadEntity = LeadEntity(
      id = lead.id,
      name = lead.name,
      mobile = lead.mobile,
      className = lead.className,
      stream = lead.stream,
      source = lead.source,
      inquiryDate = lead.inquiryDate,
      assignedCounselor = lead.assignedCounselor,
      status = lead.status,
      instituteId = lead.instituteId
    )
  }
}

@Dao
interface LeadDao {
  @Query("SELECT * FROM leads ORDER BY createdAt DESC")
  fun getAllLeads(): Flow<List<LeadEntity>>

  @Query("SELECT * FROM leads WHERE id = :id LIMIT 1")
  suspend fun getLeadById(id: String): LeadEntity?

  @Query("SELECT COUNT(*) FROM leads")
  suspend fun getCount(): Int

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertLead(lead: LeadEntity)

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertLeads(leads: List<LeadEntity>)

  @Query("DELETE FROM leads")
  suspend fun deleteAllLeads()
}

@Entity(tableName = "online_forms")
data class OnlineFormEntity(
  @PrimaryKey val id: String,
  val name: String,
  val mobile: String,
  val email: String,
  val school: String,
  val className: String,
  val stream: String,
  val preferredBatch: String,
  val submissionDate: String,
  val status: String,
  val instituteId: String = "FSI_MAIN",
  val createdAt: Long = System.currentTimeMillis()
) {
  fun toDomain(): OnlineFormSubmission = OnlineFormSubmission(
    id = id,
    name = name,
    mobile = mobile,
    email = email,
    school = school,
    className = className,
    stream = stream,
    preferredBatch = preferredBatch,
    submissionDate = submissionDate,
    status = status,
    instituteId = instituteId
  )

  companion object {
    fun fromDomain(form: OnlineFormSubmission): OnlineFormEntity = OnlineFormEntity(
      id = form.id,
      name = form.name,
      mobile = form.mobile,
      email = form.email,
      school = form.school,
      className = form.className,
      stream = form.stream,
      preferredBatch = form.preferredBatch,
      submissionDate = form.submissionDate,
      status = form.status,
      instituteId = form.instituteId
    )
  }
}

@Dao
interface OnlineFormDao {
  @Query("SELECT * FROM online_forms ORDER BY createdAt DESC")
  fun getAllForms(): Flow<List<OnlineFormEntity>>

  @Query("SELECT * FROM online_forms WHERE id = :id LIMIT 1")
  suspend fun getFormById(id: String): OnlineFormEntity?

  @Query("SELECT COUNT(*) FROM online_forms")
  suspend fun getCount(): Int

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertForm(form: OnlineFormEntity)

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertForms(forms: List<OnlineFormEntity>)

  @Query("DELETE FROM online_forms")
  suspend fun deleteAllForms()
}

// =========================================================================
// 8. NOTIFICATION ENTITY & DAO (Role & Student Scoped Persistent Alerts)
// =========================================================================
@Entity(tableName = "notifications")
data class NotificationEntity(
  @PrimaryKey val id: String,
  val title: String,
  val message: String,
  val time: String,
  val type: String,
  val recipientRole: String,
  val isRead: Boolean = false,
  val targetStudentId: String = "",
  val targetBatchName: String = "",
  val targetBatchId: String = "",
  val instituteId: String = "FSI_MAIN",
  val createdAt: Long = System.currentTimeMillis()
) {
  fun toDomain(): NotificationItem = NotificationItem(
    id = id,
    title = title,
    message = message,
    time = time,
    type = type,
    recipientRole = recipientRole,
    isRead = isRead,
    targetStudentId = targetStudentId,
    targetBatchName = targetBatchName,
    targetBatchId = targetBatchId,
    instituteId = instituteId
  )

  companion object {
    fun fromDomain(item: NotificationItem): NotificationEntity = NotificationEntity(
      id = item.id,
      title = item.title,
      message = item.message,
      time = item.time,
      type = item.type,
      recipientRole = item.recipientRole,
      isRead = item.isRead,
      targetStudentId = item.targetStudentId,
      targetBatchName = item.targetBatchName,
      targetBatchId = item.targetBatchId,
      instituteId = item.instituteId
    )
  }
}

@Dao
interface NotificationDao {
  @Query("SELECT * FROM notifications ORDER BY createdAt DESC")
  fun getAllNotifications(): Flow<List<NotificationEntity>>

  @Query("SELECT COUNT(*) FROM notifications")
  suspend fun getCount(): Int

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertNotification(notification: NotificationEntity)

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertNotifications(notifications: List<NotificationEntity>)

  @Query("UPDATE notifications SET isRead = 1")
  suspend fun markAllAsRead()

  @Query("DELETE FROM notifications WHERE id = :id")
  suspend fun deleteNotificationById(id: String)

  @Query("DELETE FROM notifications")
  suspend fun deleteAllNotifications()
}

// =========================================================================
// 9. QUESTION BANK ENTITY & DAO (Persistent CBT Question Bank)
// =========================================================================
@Entity(tableName = "question_bank")
data class QuestionEntity(
  @PrimaryKey val id: String,
  val subject: String,
  val chapter: String,
  val topic: String,
  val difficulty: String,
  val type: String,
  val questionText: String,
  val options: List<String>,
  val correctOption: Int,
  val solution: String,
  val tags: List<String>,
  val subjectId: String = "",
  val testId: String = "",
  val instituteId: String = "FSI_MAIN",
  val createdAt: Long = System.currentTimeMillis()
) {
  fun toDomain(): QuestionItem = QuestionItem(
    id = id,
    subject = subject,
    chapter = chapter,
    topic = topic,
    difficulty = difficulty,
    type = type,
    questionText = questionText,
    options = options,
    correctOption = correctOption,
    solution = solution,
    tags = tags,
    subjectId = subjectId,
    testId = testId,
    instituteId = instituteId
  )

  companion object {
    fun fromDomain(item: QuestionItem): QuestionEntity = QuestionEntity(
      id = item.id,
      subject = item.subject,
      chapter = item.chapter,
      topic = item.topic,
      difficulty = item.difficulty,
      type = item.type,
      questionText = item.questionText,
      options = item.options,
      correctOption = item.correctOption,
      solution = item.solution,
      tags = item.tags,
      subjectId = item.subjectId,
      testId = item.testId,
      instituteId = item.instituteId
    )
  }
}

@Dao
interface QuestionDao {
  @Query("SELECT * FROM question_bank ORDER BY createdAt ASC")
  fun getAllQuestions(): Flow<List<QuestionEntity>>

  @Query("SELECT COUNT(*) FROM question_bank")
  suspend fun getCount(): Int

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertQuestion(question: QuestionEntity)

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertQuestions(questions: List<QuestionEntity>)

  @Query("DELETE FROM question_bank WHERE id = :id")
  suspend fun deleteQuestionById(id: String)

  @Query("DELETE FROM question_bank")
  suspend fun deleteAllQuestions()
}

// =========================================================================
// 10. STUDENT TEST ATTEMPT ENTITY & DAO (Persistent CBT Attempts & Results)
// =========================================================================
@Entity(
  tableName = "student_test_attempts",
  foreignKeys = [
    ForeignKey(
      entity = StudentEntity::class,
      parentColumns = ["id"],
      childColumns = ["studentId"],
      onDelete = ForeignKey.RESTRICT,
      onUpdate = ForeignKey.CASCADE,
      deferred = true
    )
  ],
  indices = [Index(value = ["studentId"]), Index(value = ["testId"])]
)
data class StudentAttemptEntity(
  @PrimaryKey val attemptId: String,
  val testId: String,
  val testName: String,
  val studentId: String,
  val studentName: String,
  val instituteId: String = "FSI_MAIN",
  val answersJson: String,
  val score: Int,
  val totalMarks: Int,
  val correctCount: Int,
  val wrongCount: Int,
  val unattemptedCount: Int,
  val timeSpentSeconds: Int,
  val status: String = "Submitted",
  val startedAt: Long = System.currentTimeMillis(),
  val submittedAt: Long = System.currentTimeMillis()
) {
  fun toDomain(): StudentTestAttempt {
    val map = mutableMapOf<String, Int>()
    if (answersJson.isNotBlank()) {
      try {
        val obj = JSONObject(answersJson)
        val keys = obj.keys()
        while (keys.hasNext()) {
          val k = keys.next()
          map[k] = obj.optInt(k, 0)
        }
      } catch (_: Exception) {}
    }
    return StudentTestAttempt(
      attemptId = attemptId,
      testId = testId,
      testName = testName,
      studentId = studentId,
      studentName = studentName,
      instituteId = instituteId,
      answers = map,
      score = score,
      totalMarks = totalMarks,
      correctCount = correctCount,
      wrongCount = wrongCount,
      unattemptedCount = unattemptedCount,
      timeSpentSeconds = timeSpentSeconds,
      status = status,
      startedAt = startedAt,
      submittedAt = submittedAt
    )
  }

  companion object {
    fun fromDomain(attempt: StudentTestAttempt): StudentAttemptEntity {
      val obj = JSONObject()
      attempt.answers.forEach { (k, v) -> obj.put(k, v) }
      return StudentAttemptEntity(
        attemptId = attempt.attemptId,
        testId = attempt.testId,
        testName = attempt.testName,
        studentId = attempt.studentId,
        studentName = attempt.studentName,
        instituteId = attempt.instituteId,
        answersJson = obj.toString(),
        score = attempt.score,
        totalMarks = attempt.totalMarks,
        correctCount = attempt.correctCount,
        wrongCount = attempt.wrongCount,
        unattemptedCount = attempt.unattemptedCount,
        timeSpentSeconds = attempt.timeSpentSeconds,
        status = attempt.status,
        startedAt = attempt.startedAt,
        submittedAt = attempt.submittedAt
      )
    }
  }
}

@Dao
interface StudentAttemptDao {
  @Query("SELECT * FROM student_test_attempts ORDER BY submittedAt DESC")
  fun getAllAttempts(): Flow<List<StudentAttemptEntity>>

  @Query("SELECT * FROM student_test_attempts WHERE studentId = :studentId ORDER BY submittedAt DESC")
  suspend fun getAttemptsByStudent(studentId: String): List<StudentAttemptEntity>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertAttempt(attempt: StudentAttemptEntity)

  @Query("DELETE FROM student_test_attempts")
  suspend fun deleteAllAttempts()
}

// =========================================================================
// 11. STUDY LOG, GOAL, PARENT MEETING & WEEKLY TIMETABLE ENTITIES & DAOS
// =========================================================================
@Entity(
  tableName = "study_logs",
  foreignKeys = [
    ForeignKey(
      entity = StudentEntity::class,
      parentColumns = ["id"],
      childColumns = ["studentId"],
      onDelete = ForeignKey.RESTRICT,
      onUpdate = ForeignKey.CASCADE,
      deferred = true
    )
  ],
  indices = [
    Index(value = ["studentId"]),
    Index(value = ["instituteId"])
  ]
)
data class StudyLogEntity(
  @PrimaryKey val id: String,
  val studentId: String,
  val date: String,
  val hours: Float,
  val subjects: String,
  val chapters: String,
  val instituteId: String = "FSI_MAIN",
  val timestamp: Long = System.currentTimeMillis()
) {
  fun toDomain(): StudyLogItem = StudyLogItem(
    id = id,
    date = date,
    hours = hours,
    subjects = subjects,
    chapters = chapters,
    studentId = studentId,
    instituteId = instituteId,
    timestamp = timestamp
  )

  companion object {
    fun fromDomain(item: StudyLogItem): StudyLogEntity = StudyLogEntity(
      id = item.id,
      studentId = item.studentId,
      date = item.date,
      hours = item.hours,
      subjects = item.subjects,
      chapters = item.chapters,
      instituteId = item.instituteId,
      timestamp = item.timestamp
    )
  }
}

@Dao
interface StudyLogDao {
  @Query("SELECT * FROM study_logs ORDER BY timestamp DESC")
  fun getAllStudyLogs(): Flow<List<StudyLogEntity>>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertStudyLog(log: StudyLogEntity)

  @Query("DELETE FROM study_logs")
  suspend fun deleteAllStudyLogs()
}

@Entity(
  tableName = "student_goals",
  foreignKeys = [
    ForeignKey(
      entity = StudentEntity::class,
      parentColumns = ["id"],
      childColumns = ["studentId"],
      onDelete = ForeignKey.RESTRICT,
      onUpdate = ForeignKey.CASCADE,
      deferred = true
    )
  ],
  indices = [
    Index(value = ["studentId"])
  ]
)
data class StudentGoalEntity(
  @PrimaryKey val studentId: String,
  val targetMarks: Int,
  val targetPercent: Int,
  val targetRank: Int,
  val targetAttendance: Int,
  val weeklyStudyHours: Int,
  val instituteId: String = "FSI_MAIN",
  val updatedAt: Long = System.currentTimeMillis()
) {
  fun toDomain(): GoalItem = GoalItem(
    targetMarks = targetMarks,
    targetPercent = targetPercent,
    targetRank = targetRank,
    targetAttendance = targetAttendance,
    weeklyStudyHours = weeklyStudyHours,
    studentId = studentId,
    instituteId = instituteId
  )

  companion object {
    fun fromDomain(goal: GoalItem): StudentGoalEntity = StudentGoalEntity(
      studentId = goal.studentId.ifBlank { "DEFAULT" },
      targetMarks = goal.targetMarks,
      targetPercent = goal.targetPercent,
      targetRank = goal.targetRank,
      targetAttendance = goal.targetAttendance,
      weeklyStudyHours = goal.weeklyStudyHours,
      instituteId = goal.instituteId
    )
  }
}

@Dao
interface StudentGoalDao {
  @Query("SELECT * FROM student_goals")
  fun getAllGoals(): Flow<List<StudentGoalEntity>>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertGoal(goal: StudentGoalEntity)

  @Query("DELETE FROM student_goals")
  suspend fun deleteAllGoals()
}

@Entity(
  tableName = "parent_meetings",
  foreignKeys = [
    ForeignKey(
      entity = StudentEntity::class,
      parentColumns = ["id"],
      childColumns = ["studentId"],
      onDelete = ForeignKey.RESTRICT,
      onUpdate = ForeignKey.CASCADE,
      deferred = true
    )
  ],
  indices = [
    Index(value = ["studentId"]),
    Index(value = ["teacherId"])
  ]
)
data class ParentMeetingEntity(
  @PrimaryKey val id: String,
  val studentId: String,
  val studentName: String,
  val parentId: String,
  val parentName: String,
  val teacherId: String,
  val teacherName: String,
  val date: String,
  val time: String,
  val notes: String,
  val actionItems: String,
  val status: String,
  val instituteId: String = "FSI_MAIN",
  val createdAt: Long = System.currentTimeMillis()
) {
  fun toDomain(): ParentMeetingItem = ParentMeetingItem(
    id = id,
    studentName = studentName,
    parentName = parentName,
    teacherName = teacherName,
    date = date,
    time = time,
    notes = notes,
    actionItems = actionItems,
    status = status,
    studentId = studentId,
    parentId = parentId,
    teacherId = teacherId,
    instituteId = instituteId
  )

  companion object {
    fun fromDomain(item: ParentMeetingItem): ParentMeetingEntity = ParentMeetingEntity(
      id = item.id,
      studentId = item.studentId,
      studentName = item.studentName,
      parentId = item.parentId,
      parentName = item.parentName,
      teacherId = item.teacherId,
      teacherName = item.teacherName,
      date = item.date,
      time = item.time,
      notes = item.notes,
      actionItems = item.actionItems,
      status = item.status,
      instituteId = item.instituteId
    )
  }
}

@Dao
interface ParentMeetingDao {
  @Query("SELECT * FROM parent_meetings ORDER BY createdAt DESC")
  fun getAllMeetings(): Flow<List<ParentMeetingEntity>>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertMeeting(meeting: ParentMeetingEntity)

  @Query("DELETE FROM parent_meetings")
  suspend fun deleteAllMeetings()
}

@Entity(tableName = "weekly_timetable")
data class WeeklyTimetableEntity(
  @PrimaryKey val id: String,
  val day: String,
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
) {
  fun toDomain(): TimetableItem = TimetableItem(
    id = id,
    day = day,
    startTime = startTime,
    endTime = endTime,
    subject = subject,
    batch = batch,
    room = room,
    teacherName = teacherName,
    batchId = batchId,
    teacherId = teacherId,
    subjectId = subjectId,
    instituteId = instituteId
  )

  companion object {
    fun fromDomain(item: TimetableItem): WeeklyTimetableEntity = WeeklyTimetableEntity(
      id = item.id,
      day = item.day,
      startTime = item.startTime,
      endTime = item.endTime,
      subject = item.subject,
      batch = item.batch,
      room = item.room,
      teacherName = item.teacherName,
      batchId = item.batchId,
      teacherId = item.teacherId,
      subjectId = item.subjectId,
      instituteId = item.instituteId
    )
  }
}

@Dao
interface WeeklyTimetableDao {
  @Query("SELECT * FROM weekly_timetable")
  fun getAllWeeklyTimetable(): Flow<List<WeeklyTimetableEntity>>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertWeeklyEntry(entry: WeeklyTimetableEntity)

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertWeeklyEntries(entries: List<WeeklyTimetableEntity>)

  @Query("DELETE FROM weekly_timetable")
  suspend fun deleteAllWeeklyTimetable()
}
