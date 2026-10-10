package com.example.data

import android.content.Context
import android.util.Log
import com.example.data.db.AppRoomDatabase
import com.example.data.db.BatchEnrollmentEntity
import com.example.data.db.BatchEntity
import com.example.data.db.StudentEntity
import com.example.data.db.SyncOperationType
import com.example.data.db.SyncQueueEntity
import com.example.data.db.SyncQueueStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class StudentBatchRepository(private val context: Context) {

  private val database = AppRoomDatabase.getDatabase(context)
  private val studentDao = database.studentDao()
  private val teacherDao = database.teacherDao()
  private val batchDao = database.batchDao()
  private val enrollmentDao = database.batchEnrollmentDao()
  private val attendanceDao = database.attendanceDao()
  private val feePaymentDao = database.feePaymentDao()
  private val timetableDao = database.timetableDao()
  private val userAccountDao = database.userAccountDao()
  private val feeInvoiceDao = database.feeInvoiceDao()
  private val homeworkDao = database.homeworkDao()
  private val doubtDao = database.doubtDao()
  private val testRecordDao = database.testRecordDao()
  private val subjectDao = database.subjectDao()
  private val leadDao = database.leadDao()
  private val onlineFormDao = database.onlineFormDao()
  private val notificationDao = database.notificationDao()
  private val questionDao = database.questionDao()
  private val studentAttemptDao = database.studentAttemptDao()
  private val studyLogDao = database.studyLogDao()
  private val studentGoalDao = database.studentGoalDao()
  private val parentMeetingDao = database.parentMeetingDao()
  private val weeklyTimetableDao = database.weeklyTimetableDao()
  private val syncQueueDao = database.syncQueueDao()

  init {
    AppRepository.bindStudentBatchRepository(context, this)
  }

  internal fun isDatabaseOpenAndCurrent(): Boolean {
    return database.isOpen && AppRoomDatabase.getInstanceOrNull() === database
  }

  val allStudents: Flow<List<Student>> = studentDao.getAllStudents().map { entities ->
    entities.map { it.toDomain() }
  }

  val allStudentsIncludingInactive: Flow<List<Student>> = studentDao.getAllStudentsIncludingInactive().map { entities ->
    entities.map { it.toDomain() }
  }

  val allTeachers: Flow<List<Teacher>> = teacherDao.getAllTeachers().map { entities ->
    entities.map { it.toDomain() }
  }

  val allBatches: Flow<List<Batch>> = batchDao.getAllBatches().map { entities ->
    entities.map { it.toDomain() }
  }

  val allEnrollments: Flow<List<BatchEnrollmentEntity>> = enrollmentDao.getAllEnrollments()

  val allAttendance: Flow<List<AttendanceRecord>> = attendanceDao.getAllAttendance().map { entities ->
    entities.map { it.toDomain() }
  }

  val allFeePayments: Flow<List<StudentFeePayment>> = feePaymentDao.getAllPayments().map { entities ->
    entities.map { it.toDomain() }
  }

  val allTimetableEntries: Flow<List<DynamicTimetableEntry>> = timetableDao.getAllTimetableEntries().map { entities ->
    entities.map { it.toDomain() }
  }

  val allUserAccounts: Flow<List<com.example.data.db.UserAccountEntity>> = userAccountDao.getAllAccounts()

  val allFeeInvoices: Flow<List<FeeRecord>> = feeInvoiceDao.getAllInvoices().map { entities ->
    entities.map { it.toDomain() }
  }

  val allHomeworks: Flow<List<HomeworkItem>> = homeworkDao.getAllHomeworks().map { entities ->
    entities.map { it.toDomain() }
  }

  val allDoubts: Flow<List<DoubtItem>> = doubtDao.getAllDoubts().map { entities ->
    entities.map { it.toDomain() }
  }

  val allTests: Flow<List<TestRecord>> = testRecordDao.getAllTests().map { entities ->
    entities.map { it.toDomain() }
  }

  val allSubjects: Flow<List<Subject>> = subjectDao.getAllSubjects().map { entities ->
    entities.map { it.toDomain() }
  }

  val allLeads: Flow<List<LeadRecord>> = leadDao.getAllLeads().map { entities ->
    entities.map { it.toDomain() }
  }

  val allOnlineForms: Flow<List<OnlineFormSubmission>> = onlineFormDao.getAllForms().map { entities ->
    entities.map { it.toDomain() }
  }

  val allNotifications: Flow<List<NotificationItem>> = notificationDao.getAllNotifications().map { entities ->
    entities.map { it.toDomain() }
  }

  val allQuestions: Flow<List<QuestionItem>> = questionDao.getAllQuestions().map { entities ->
    entities.map { it.toDomain() }
  }

  val allStudentAttempts: Flow<List<StudentTestAttempt>> = studentAttemptDao.getAllAttempts().map { entities ->
    entities.map { it.toDomain() }
  }

  val allStudyLogs: Flow<List<StudyLogItem>> = studyLogDao.getAllStudyLogs().map { entities ->
    entities.map { it.toDomain() }
  }

  val allStudentGoals: Flow<List<GoalItem>> = studentGoalDao.getAllGoals().map { entities ->
    entities.map { it.toDomain() }
  }

  val allParentMeetings: Flow<List<ParentMeetingItem>> = parentMeetingDao.getAllMeetings().map { entities ->
    entities.map { it.toDomain() }
  }

  val allWeeklyTimetable: Flow<List<TimetableItem>> = weeklyTimetableDao.getAllWeeklyTimetable().map { entities ->
    entities.map { it.toDomain() }
  }

  val pendingSyncQueueItems: Flow<List<SyncQueueEntity>> = syncQueueDao.observePendingItems()
  val allSyncQueueItems: Flow<List<SyncQueueEntity>> = syncQueueDao.observeAllItems()
  val pendingSyncQueueCount: Flow<Int> = syncQueueDao.observePendingCount()

  private suspend fun ensureStudentExistsForForeignKey(
    studentId: String,
    studentName: String = "",
    batchName: String = "",
    batchId: String = "",
    instituteId: String = AppRepository.getCurrentInstituteId()
  ) {
    val cleanId = studentId.trim()
    if (cleanId.isBlank()) return
    val existing = studentDao.getStudentByIdOnce(cleanId)
    if (existing == null) {
      studentDao.insertStudentIgnore(
        StudentEntity(
          id = cleanId,
          name = studentName.ifBlank { "Student $cleanId" },
          photo = "",
          mobile = "",
          parentName = "",
          parentContact = "",
          email = "",
          dob = "",
          gender = "",
          address = "",
          school = "",
          className = "",
          batch = batchName,
          stream = "",
          admissionDate = "",
          status = "Active",
          attendancePercent = 0,
          overallAvg = 0,
          rank = 0,
          strongestSubject = "",
          weakestSubject = "",
          recentScoresSerialized = "",
          batchId = batchId,
          instituteId = instituteId.ifBlank { AppRepository.getCurrentInstituteId() }
        )
      )
    }
  }

  private suspend fun ensureBatchExistsForForeignKey(
    batchId: String,
    batchName: String = "",
    instituteId: String = AppRepository.getCurrentInstituteId()
  ) {
    val cleanId = batchId.trim()
    if (cleanId.isBlank()) return
    val existing = batchDao.getBatchByIdOnce(cleanId)
    if (existing == null) {
      batchDao.insertBatchIgnore(
        BatchEntity(
          id = cleanId,
          name = batchName.ifBlank { cleanId },
          schedule = "",
          studentCount = 0,
          teacherName = "",
          instituteId = instituteId.ifBlank { AppRepository.getCurrentInstituteId() }
        )
      )
    }
  }

  suspend fun getStudentCount(): Int = studentDao.getTotalCountIncludingInactive()

  suspend fun getAttendanceCount(): Int = attendanceDao.getCount()

  suspend fun insertQuestion(item: QuestionItem) {
    questionDao.insertQuestion(com.example.data.db.QuestionEntity.fromDomain(item))
  }

  suspend fun insertQuestions(items: List<QuestionItem>) {
    questionDao.insertQuestions(items.map { com.example.data.db.QuestionEntity.fromDomain(it) })
  }

  suspend fun deleteQuestion(questionId: String) {
    questionDao.deleteQuestionById(questionId)
  }

  suspend fun insertStudentAttempt(attempt: StudentTestAttempt) {
    val entity = com.example.data.db.StudentAttemptEntity.fromDomain(attempt)
    ensureStudentExistsForForeignKey(
      studentId = entity.studentId,
      studentName = entity.studentName,
      instituteId = entity.instituteId
    )
    studentAttemptDao.insertAttempt(entity)
  }

  suspend fun insertStudyLog(log: StudyLogItem) {
    val entity = com.example.data.db.StudyLogEntity.fromDomain(log)
    ensureStudentExistsForForeignKey(
      studentId = entity.studentId,
      instituteId = entity.instituteId
    )
    studyLogDao.insertStudyLog(entity)
  }

  suspend fun insertStudentGoal(goal: GoalItem) {
    val entity = com.example.data.db.StudentGoalEntity.fromDomain(goal)
    ensureStudentExistsForForeignKey(
      studentId = entity.studentId,
      instituteId = entity.instituteId
    )
    studentGoalDao.insertGoal(entity)
  }

  suspend fun insertParentMeeting(meeting: ParentMeetingItem) {
    val entity = com.example.data.db.ParentMeetingEntity.fromDomain(meeting)
    ensureStudentExistsForForeignKey(
      studentId = entity.studentId,
      studentName = entity.studentName,
      instituteId = entity.instituteId
    )
    parentMeetingDao.insertMeeting(entity)
  }

  suspend fun insertWeeklyTimetableEntry(entry: TimetableItem) {
    weeklyTimetableDao.insertWeeklyEntry(com.example.data.db.WeeklyTimetableEntity.fromDomain(entry))
  }

  suspend fun insertWeeklyTimetableEntries(entries: List<TimetableItem>) {
    weeklyTimetableDao.insertWeeklyEntries(entries.map { com.example.data.db.WeeklyTimetableEntity.fromDomain(it) })
  }

  suspend fun findUserAccount(identifier: String): com.example.data.db.UserAccountEntity? {
    return userAccountDao.findAccountByIdentifier(identifier.trim())
  }

  suspend fun getUserAccountCount(): Int = userAccountDao.getCount()

  suspend fun insertUserAccount(account: com.example.data.db.UserAccountEntity) {
    userAccountDao.insertAccount(account)
  }

  suspend fun insertUserAccounts(accounts: List<com.example.data.db.UserAccountEntity>) {
    userAccountDao.insertAccounts(accounts)
  }

  suspend fun updateUserAccount(account: com.example.data.db.UserAccountEntity) {
    userAccountDao.updateAccount(account)
  }

  suspend fun insertFeeInvoice(invoice: FeeRecord) {
    val entity = com.example.data.db.FeeInvoiceEntity.fromDomain(invoice)
    ensureStudentExistsForForeignKey(
      studentId = entity.studentId,
      studentName = entity.studentName,
      batchName = entity.batchName,
      batchId = entity.batchId,
      instituteId = entity.instituteId
    )
    feeInvoiceDao.insertInvoice(entity)
  }

  suspend fun updateFeeInvoice(invoice: FeeRecord) {
    val entity = com.example.data.db.FeeInvoiceEntity.fromDomain(invoice)
    ensureStudentExistsForForeignKey(
      studentId = entity.studentId,
      studentName = entity.studentName,
      batchName = entity.batchName,
      batchId = entity.batchId,
      instituteId = entity.instituteId
    )
    feeInvoiceDao.insertInvoice(entity)
  }

  suspend fun insertFeeInvoices(invoices: List<FeeRecord>) {
    val entities = invoices.map { com.example.data.db.FeeInvoiceEntity.fromDomain(it) }
    entities.forEach { entity ->
      ensureStudentExistsForForeignKey(
        studentId = entity.studentId,
        studentName = entity.studentName,
        batchName = entity.batchName,
        batchId = entity.batchId,
        instituteId = entity.instituteId
      )
    }
    feeInvoiceDao.insertInvoices(entities)
  }

  suspend fun deleteFeeInvoice(invoiceId: String) {
    feeInvoiceDao.deleteInvoiceById(invoiceId)
  }

  suspend fun insertHomework(item: HomeworkItem) {
    homeworkDao.insertHomework(com.example.data.db.HomeworkEntity.fromDomain(item))
  }

  suspend fun updateHomework(item: HomeworkItem) {
    homeworkDao.insertHomework(com.example.data.db.HomeworkEntity.fromDomain(item))
  }

  suspend fun deleteHomework(homeworkId: String) {
    homeworkDao.deleteHomeworkById(homeworkId)
  }

  suspend fun insertHomeworks(items: List<HomeworkItem>) {
    homeworkDao.insertHomeworks(items.map { com.example.data.db.HomeworkEntity.fromDomain(it) })
  }

  suspend fun insertDoubt(item: DoubtItem) {
    val entity = com.example.data.db.DoubtEntity.fromDomain(item)
    ensureStudentExistsForForeignKey(
      studentId = entity.studentId,
      studentName = entity.studentName,
      batchName = entity.batchName,
      batchId = entity.batchId,
      instituteId = entity.instituteId
    )
    doubtDao.insertDoubt(entity)
  }

  suspend fun updateDoubt(item: DoubtItem) {
    val entity = com.example.data.db.DoubtEntity.fromDomain(item)
    ensureStudentExistsForForeignKey(
      studentId = entity.studentId,
      studentName = entity.studentName,
      batchName = entity.batchName,
      batchId = entity.batchId,
      instituteId = entity.instituteId
    )
    doubtDao.insertDoubt(entity)
  }

  suspend fun insertDoubts(items: List<DoubtItem>) {
    val entities = items.map { com.example.data.db.DoubtEntity.fromDomain(it) }
    entities.forEach { entity ->
      ensureStudentExistsForForeignKey(
        studentId = entity.studentId,
        studentName = entity.studentName,
        batchName = entity.batchName,
        batchId = entity.batchId,
        instituteId = entity.instituteId
      )
    }
    doubtDao.insertDoubts(entities)
  }

  suspend fun insertTest(item: TestRecord) {
    testRecordDao.insertTest(com.example.data.db.TestRecordEntity.fromDomain(item))
  }

  suspend fun updateTest(item: TestRecord) {
    testRecordDao.insertTest(com.example.data.db.TestRecordEntity.fromDomain(item))
  }

  suspend fun insertTests(items: List<TestRecord>) {
    testRecordDao.insertTests(items.map { com.example.data.db.TestRecordEntity.fromDomain(it) })
  }

  suspend fun insertSubject(item: Subject) {
    subjectDao.insertSubject(com.example.data.db.SubjectEntity.fromDomain(item))
  }

  suspend fun updateSubject(item: Subject) {
    subjectDao.insertSubject(com.example.data.db.SubjectEntity.fromDomain(item))
  }

  suspend fun insertSubjects(items: List<Subject>) {
    subjectDao.insertSubjects(items.map { com.example.data.db.SubjectEntity.fromDomain(it) })
  }

  suspend fun insertLead(item: LeadRecord) {
    leadDao.insertLead(com.example.data.db.LeadEntity.fromDomain(item))
  }

  suspend fun updateLead(item: LeadRecord) {
    leadDao.insertLead(com.example.data.db.LeadEntity.fromDomain(item))
  }

  suspend fun insertLeads(items: List<LeadRecord>) {
    leadDao.insertLeads(items.map { com.example.data.db.LeadEntity.fromDomain(it) })
  }

  suspend fun insertOnlineForm(item: OnlineFormSubmission) {
    onlineFormDao.insertForm(com.example.data.db.OnlineFormEntity.fromDomain(item))
  }

  suspend fun updateOnlineForm(item: OnlineFormSubmission) {
    onlineFormDao.insertForm(com.example.data.db.OnlineFormEntity.fromDomain(item))
  }

  suspend fun insertOnlineForms(items: List<OnlineFormSubmission>) {
    onlineFormDao.insertForms(items.map { com.example.data.db.OnlineFormEntity.fromDomain(it) })
  }

  suspend fun insertNotification(item: NotificationItem) {
    notificationDao.insertNotification(com.example.data.db.NotificationEntity.fromDomain(item))
  }

  suspend fun updateNotification(item: NotificationItem) {
    notificationDao.insertNotification(com.example.data.db.NotificationEntity.fromDomain(item))
  }

  suspend fun markAllNotificationsAsRead() {
    notificationDao.markAllAsRead()
  }

  suspend fun deleteNotification(id: String) {
    notificationDao.deleteNotificationById(id)
  }

  suspend fun deleteAllNotifications() {
    notificationDao.deleteAllNotifications()
  }

  suspend fun insertNotifications(items: List<NotificationItem>) {
    notificationDao.insertNotifications(items.map { com.example.data.db.NotificationEntity.fromDomain(it) })
  }

  fun getFeePaymentsByStudent(studentId: String): Flow<List<StudentFeePayment>> {
    return feePaymentDao.getPaymentsByStudent(studentId).map { entities -> entities.map { it.toDomain() } }
  }

  fun getFeePaymentsByStatus(status: String): Flow<List<StudentFeePayment>> {
    return feePaymentDao.getPaymentsByStatus(status).map { entities -> entities.map { it.toDomain() } }
  }

  fun getFeePaymentsByMonth(month: String): Flow<List<StudentFeePayment>> {
    return feePaymentDao.getPaymentsByMonth(month).map { entities -> entities.map { it.toDomain() } }
  }

  suspend fun insertFeePayment(payment: StudentFeePayment) {
    val entity = com.example.data.db.FeePaymentEntity.fromDomain(payment)
    ensureStudentExistsForForeignKey(
      studentId = entity.studentId,
      studentName = entity.studentName,
      instituteId = entity.instituteId
    )
    feePaymentDao.insertPayment(entity)
  }

  suspend fun insertFeePayments(payments: List<StudentFeePayment>) {
    val entities = payments.map { com.example.data.db.FeePaymentEntity.fromDomain(it) }
    entities.forEach { entity ->
      ensureStudentExistsForForeignKey(
        studentId = entity.studentId,
        studentName = entity.studentName,
        instituteId = entity.instituteId
      )
    }
    feePaymentDao.insertPayments(entities)
  }

  suspend fun updateFeePayment(payment: StudentFeePayment) {
    val entity = com.example.data.db.FeePaymentEntity.fromDomain(payment)
    ensureStudentExistsForForeignKey(
      studentId = entity.studentId,
      studentName = entity.studentName,
      instituteId = entity.instituteId
    )
    feePaymentDao.updatePayment(entity)
  }

  suspend fun deleteFeePayment(paymentId: String) {
    feePaymentDao.deletePaymentById(paymentId)
  }

  suspend fun deleteAllFeePayments() {
    feePaymentDao.deleteAllPayments()
  }

  fun getAttendanceByDate(date: String): Flow<List<AttendanceRecord>> {
    return attendanceDao.getAttendanceByDate(date).map { entities -> entities.map { it.toDomain() } }
  }

  fun getAttendanceByDateComponents(day: Int, month: Int, year: Int): Flow<List<AttendanceRecord>> {
    return attendanceDao.getAttendanceByDateComponents(day, month, year).map { entities -> entities.map { it.toDomain() } }
  }

  suspend fun insertAttendance(record: AttendanceRecord) {
    val entity = com.example.data.db.AttendanceRecordEntity.fromDomain(record)
    ensureStudentExistsForForeignKey(
      studentId = entity.studentId,
      studentName = entity.studentName,
      batchName = entity.batchName,
      batchId = entity.batchId,
      instituteId = entity.instituteId
    )
    attendanceDao.insertAttendance(entity)
  }

  suspend fun insertAttendanceList(records: List<AttendanceRecord>) {
    val entities = records.map { com.example.data.db.AttendanceRecordEntity.fromDomain(it) }
    entities.forEach { entity ->
      ensureStudentExistsForForeignKey(
        studentId = entity.studentId,
        studentName = entity.studentName,
        batchName = entity.batchName,
        batchId = entity.batchId,
        instituteId = entity.instituteId
      )
    }
    attendanceDao.insertAttendanceList(entities)
  }

  suspend fun updateAttendance(record: AttendanceRecord) {
    val entity = com.example.data.db.AttendanceRecordEntity.fromDomain(record)
    ensureStudentExistsForForeignKey(
      studentId = entity.studentId,
      studentName = entity.studentName,
      batchName = entity.batchName,
      batchId = entity.batchId,
      instituteId = entity.instituteId
    )
    attendanceDao.updateAttendance(entity)
  }

  suspend fun deleteAttendance(recordId: String) {
    attendanceDao.deleteAttendanceById(recordId)
  }

  suspend fun deleteAllAttendance() {
    attendanceDao.deleteAllAttendance()
  }

  suspend fun insertTimetableEntry(entry: DynamicTimetableEntry) {
    val entity = com.example.data.db.TimetableEntryEntity.fromDomain(entry)
    timetableDao.insertTimetableEntry(entity)
  }

  suspend fun insertTimetableEntries(entries: List<DynamicTimetableEntry>) {
    val entities = entries.map { com.example.data.db.TimetableEntryEntity.fromDomain(it) }
    timetableDao.insertTimetableEntries(entities)
  }

  suspend fun deleteTimetableEntry(entryId: String) {
    timetableDao.deleteTimetableEntry(entryId)
  }

  suspend fun deleteAllTimetableEntries() {
    timetableDao.deleteAllTimetableEntries()
  }

  suspend fun insertTeacher(teacher: Teacher) {
    val entity = com.example.data.db.TeacherEntity.fromDomain(teacher)
    teacherDao.insertTeacher(entity)
  }

  suspend fun insertTeachers(teachers: List<Teacher>) {
    val entities = teachers.map { com.example.data.db.TeacherEntity.fromDomain(it) }
    teacherDao.insertTeachers(entities)
  }

  suspend fun updateTeacher(teacher: Teacher) {
    val entity = com.example.data.db.TeacherEntity.fromDomain(teacher)
    teacherDao.updateTeacher(entity)
  }

  suspend fun deleteTeacher(teacherId: String) {
    batchDao.unassignTeacherFromBatches(teacherId)
    teacherDao.deleteTeacherById(teacherId)
    userAccountDao.deactivateAccountsByLinkedEntities(listOf(teacherId))
  }

  suspend fun deleteAllTeachers() {
    teacherDao.deleteAllTeachers()
  }

  suspend fun insertStudent(student: Student, syncToCloud: Boolean = true) {
    val activeInstituteId = AppRepository.getCurrentInstituteId()
    val existingEntity = studentDao.getStudentByIdOnce(student.id)
    val existingInstituteId = existingEntity?.instituteId ?: AppRepository.findExistingStudentInstituteId(student.id)
    val requestedInstituteId = student.instituteId.trim().ifBlank { existingInstituteId ?: activeInstituteId }
    if (!com.example.security.SecurityEngine.isValidInstituteId(requestedInstituteId)) {
      com.example.security.SecurityEngine.logEvent(
        severity = com.example.security.SecuritySeverity.ALERT,
        eventType = "INVALID_INSTITUTE_SYNC_BLOCKED",
        actor = AppRepository.currentUserIdentifier.value.ifBlank { "System" },
        details = "Blocked insertStudent for '${student.id}' with invalid instituteId '${student.instituteId}'."
      )
      return
    }
    if (existingInstituteId != null && !existingInstituteId.equals(requestedInstituteId, ignoreCase = true)) {
      com.example.security.SecurityEngine.logEvent(
        severity = com.example.security.SecuritySeverity.ALERT,
        eventType = "INSTITUTE_REASSIGNMENT_BLOCKED",
        actor = AppRepository.currentUserIdentifier.value.ifBlank { "System" },
        details = "Blocked insertStudent institute reassignment for '${student.id}' from '$existingInstituteId' to '$requestedInstituteId'."
      )
      return
    }
    if (existingInstituteId != null && !existingInstituteId.equals(activeInstituteId, ignoreCase = true)) {
      com.example.security.SecurityEngine.logEvent(
        severity = com.example.security.SecuritySeverity.ALERT,
        eventType = "CROSS_TENANT_WRITE_BLOCKED",
        actor = AppRepository.currentUserIdentifier.value.ifBlank { "System" },
        details = "Blocked cross-institute insertStudent for '${student.id}' belonging to '$existingInstituteId' from active institute '$activeInstituteId'."
      )
      return
    }
    if (AppRepository.isLoggedIn.value && !requestedInstituteId.equals(activeInstituteId, ignoreCase = true)) {
      com.example.security.SecurityEngine.logEvent(
        severity = com.example.security.SecuritySeverity.ALERT,
        eventType = "CROSS_TENANT_WRITE_BLOCKED",
        actor = AppRepository.currentUserIdentifier.value.ifBlank { "System" },
        details = "Blocked cross-institute insertStudent for '${student.id}' targeting '$requestedInstituteId' while active institute is '$activeInstituteId'."
      )
      return
    }

    val resolvedBatchId = student.batchId.ifBlank { "BATCH_${student.batch.replace(" ", "_")}" }
    val normalizedStudent = student.copy(
      batchId = resolvedBatchId,
      instituteId = existingInstituteId ?: requestedInstituteId
    )
    val entity = StudentEntity.fromDomain(normalizedStudent)
    studentDao.insertStudent(entity)

    ensureBatchExistsForForeignKey(
      batchId = resolvedBatchId,
      batchName = normalizedStudent.batch,
      instituteId = normalizedStudent.instituteId
    )

    val enrollment = BatchEnrollmentEntity(
      id = "ENR_${normalizedStudent.id}_${normalizedStudent.batch.replace(" ", "_")}",
      studentId = normalizedStudent.id,
      studentName = normalizedStudent.name,
      batchId = resolvedBatchId,
      batchName = normalizedStudent.batch,
      className = normalizedStudent.className,
      stream = normalizedStudent.stream,
      enrollmentDate = normalizedStudent.admissionDate,
      feeStatus = normalizedStudent.feeStatus,
      status = normalizedStudent.status
    )
    enrollmentDao.insertEnrollment(enrollment)
    AppRepository.onLocalStudentUpserted(normalizedStudent, enrollment)

    if (syncToCloud) {
      syncStudentToFirestore(normalizedStudent, SyncOperationType.CREATE)
      syncEnrollmentToFirestore(enrollment)
    }
  }

  suspend fun updateStudent(student: Student, syncToCloud: Boolean = true) {
    val activeInstituteId = AppRepository.getCurrentInstituteId()
    val existingEntity = studentDao.getStudentByIdOnce(student.id)
    val existingInstituteId = existingEntity?.instituteId ?: AppRepository.findExistingStudentInstituteId(student.id)
    val requestedInstituteId = student.instituteId.trim().ifBlank { existingInstituteId ?: activeInstituteId }
    if (!com.example.security.SecurityEngine.isValidInstituteId(requestedInstituteId)) {
      com.example.security.SecurityEngine.logEvent(
        severity = com.example.security.SecuritySeverity.ALERT,
        eventType = "INVALID_INSTITUTE_SYNC_BLOCKED",
        actor = AppRepository.currentUserIdentifier.value.ifBlank { "System" },
        details = "Blocked updateStudent for '${student.id}' with invalid instituteId '${student.instituteId}'."
      )
      return
    }
    if (existingInstituteId != null && !existingInstituteId.equals(requestedInstituteId, ignoreCase = true)) {
      com.example.security.SecurityEngine.logEvent(
        severity = com.example.security.SecuritySeverity.ALERT,
        eventType = "INSTITUTE_REASSIGNMENT_BLOCKED",
        actor = AppRepository.currentUserIdentifier.value.ifBlank { "System" },
        details = "Blocked updateStudent institute reassignment for '${student.id}' from '$existingInstituteId' to '$requestedInstituteId'."
      )
      return
    }
    if (existingInstituteId != null && !existingInstituteId.equals(activeInstituteId, ignoreCase = true)) {
      com.example.security.SecurityEngine.logEvent(
        severity = com.example.security.SecuritySeverity.ALERT,
        eventType = "CROSS_TENANT_WRITE_BLOCKED",
        actor = AppRepository.currentUserIdentifier.value.ifBlank { "System" },
        details = "Blocked cross-institute updateStudent for '${student.id}' belonging to '$existingInstituteId' from active institute '$activeInstituteId'."
      )
      return
    }
    if (AppRepository.isLoggedIn.value && !requestedInstituteId.equals(activeInstituteId, ignoreCase = true)) {
      com.example.security.SecurityEngine.logEvent(
        severity = com.example.security.SecuritySeverity.ALERT,
        eventType = "CROSS_TENANT_WRITE_BLOCKED",
        actor = AppRepository.currentUserIdentifier.value.ifBlank { "System" },
        details = "Blocked cross-institute updateStudent for '${student.id}' targeting '$requestedInstituteId' while active institute is '$activeInstituteId'."
      )
      return
    }

    val resolvedBatchId = student.batchId.ifBlank { "BATCH_${student.batch.replace(" ", "_")}" }
    val normalizedStudent = student.copy(
      batchId = resolvedBatchId,
      instituteId = existingInstituteId ?: requestedInstituteId
    )
    val entity = StudentEntity.fromDomain(normalizedStudent)
    if (existingEntity != null) {
      studentDao.updateStudent(entity)
    } else {
      studentDao.insertStudent(entity)
    }
    ensureBatchExistsForForeignKey(
      batchId = resolvedBatchId,
      batchName = normalizedStudent.batch,
      instituteId = normalizedStudent.instituteId
    )
    if (existingEntity != null &&
      (!existingEntity.batch.equals(normalizedStudent.batch, ignoreCase = true) ||
        (existingEntity.batchId.isNotBlank() && !existingEntity.batchId.equals(resolvedBatchId, ignoreCase = true)))
    ) {
      enrollmentDao.deactivateEnrollmentsByStudent(normalizedStudent.id)
    }
    val enrollment = BatchEnrollmentEntity(
      id = "ENR_${normalizedStudent.id}_${normalizedStudent.batch.replace(" ", "_")}",
      studentId = normalizedStudent.id,
      studentName = normalizedStudent.name,
      batchId = resolvedBatchId,
      batchName = normalizedStudent.batch,
      className = normalizedStudent.className,
      stream = normalizedStudent.stream,
      enrollmentDate = normalizedStudent.admissionDate,
      feeStatus = normalizedStudent.feeStatus,
      status = normalizedStudent.status
    )
    enrollmentDao.insertEnrollment(enrollment)
    AppRepository.onLocalStudentUpserted(normalizedStudent, enrollment)
    if (syncToCloud) {
      syncStudentToFirestore(normalizedStudent, SyncOperationType.UPDATE)
    }
  }

  /**
   * Soft-deactivates the student (`status = 'Inactive'`, `isActivated = false`) and their active batch
   * enrollment and user account while preserving historical student identity and all academic/financial
   * history (attendance, fee invoices, fee payments, test attempts, doubts, study logs, goals, meetings).
   */
  suspend fun deactivateStudent(studentId: String, syncToCloud: Boolean = true) {
    val cleanId = studentId.trim()
    if (cleanId.isBlank()) return
    val activeInstituteId = AppRepository.getCurrentInstituteId()
    if (!com.example.security.SecurityEngine.isValidInstituteId(activeInstituteId)) {
      com.example.security.SecurityEngine.logEvent(
        severity = com.example.security.SecuritySeverity.ALERT,
        eventType = "INVALID_INSTITUTE_SYNC_BLOCKED",
        actor = AppRepository.currentUserIdentifier.value.ifBlank { "System" },
        details = "Blocked deactivateStudent for '$cleanId' with invalid active instituteId '$activeInstituteId'."
      )
      return
    }
    val existingEntity = studentDao.getStudentByIdOnce(cleanId)
    val existingInstituteId = existingEntity?.instituteId ?: AppRepository.findExistingStudentInstituteId(cleanId)
    if (existingInstituteId != null && !existingInstituteId.equals(activeInstituteId, ignoreCase = true)) {
      com.example.security.SecurityEngine.logEvent(
        severity = com.example.security.SecuritySeverity.ALERT,
        eventType = "CROSS_TENANT_DELETE_BLOCKED",
        actor = AppRepository.currentUserIdentifier.value.ifBlank { "System" },
        details = "Blocked cross-institute deactivateStudent for '$cleanId' belonging to '$existingInstituteId' from active institute '$activeInstituteId'."
      )
      return
    }
    studentDao.deactivateStudentById(cleanId)
    enrollmentDao.deactivateEnrollmentsByStudent(cleanId)
    userAccountDao.deactivateAccountsByLinkedEntities(listOf(cleanId))
    AppRepository.onLocalStudentsDeactivated(listOf(cleanId))
    if (syncToCloud) {
      FirestoreService.deleteStudentProfile(cleanId, {}, {})
    }
  }

  suspend fun deactivateStudents(studentIds: List<String>, syncToCloud: Boolean = true) {
    if (studentIds.isEmpty()) return
    val activeInstituteId = AppRepository.getCurrentInstituteId()
    if (!com.example.security.SecurityEngine.isValidInstituteId(activeInstituteId)) {
      com.example.security.SecurityEngine.logEvent(
        severity = com.example.security.SecuritySeverity.ALERT,
        eventType = "INVALID_INSTITUTE_SYNC_BLOCKED",
        actor = AppRepository.currentUserIdentifier.value.ifBlank { "System" },
        details = "Blocked deactivateStudents with invalid active instituteId '$activeInstituteId'."
      )
      return
    }
    val allowedIds = studentIds.map { it.trim() }.filter { cleanId ->
      if (cleanId.isBlank()) return@filter false
      val existingEntity = studentDao.getStudentByIdOnce(cleanId)
      val existingInstituteId = existingEntity?.instituteId ?: AppRepository.findExistingStudentInstituteId(cleanId)
      if (existingInstituteId != null && !existingInstituteId.equals(activeInstituteId, ignoreCase = true)) {
        com.example.security.SecurityEngine.logEvent(
          severity = com.example.security.SecuritySeverity.ALERT,
          eventType = "CROSS_TENANT_DELETE_BLOCKED",
          actor = AppRepository.currentUserIdentifier.value.ifBlank { "System" },
          details = "Blocked cross-institute deactivateStudents for '$cleanId' belonging to '$existingInstituteId' from active institute '$activeInstituteId'."
        )
        false
      } else {
        true
      }
    }
    if (allowedIds.isEmpty()) return
    studentDao.deactivateStudentsByIds(allowedIds)
    allowedIds.forEach { id ->
      enrollmentDao.deactivateEnrollmentsByStudent(id)
      if (syncToCloud) {
        FirestoreService.deleteStudentProfile(id, {}, {})
      }
    }
    userAccountDao.deactivateAccountsByLinkedEntities(allowedIds)
    AppRepository.onLocalStudentsDeactivated(allowedIds)
  }

  suspend fun deleteStudent(studentId: String, syncToCloud: Boolean = true) {
    deactivateStudent(studentId, syncToCloud)
  }

  suspend fun deleteStudents(studentIds: List<String>, syncToCloud: Boolean = true) {
    deactivateStudents(studentIds, syncToCloud)
  }

  suspend fun clearAllData() {
    // Delete child tables with foreign keys first, then parent tables
    enrollmentDao.deleteAllEnrollments()
    attendanceDao.deleteAllAttendance()
    feePaymentDao.deleteAllPayments()
    feeInvoiceDao.deleteAllInvoices()
    doubtDao.deleteAllDoubts()
    studentAttemptDao.deleteAllAttempts()
    studyLogDao.deleteAllStudyLogs()
    studentGoalDao.deleteAllGoals()
    parentMeetingDao.deleteAllMeetings()
    timetableDao.deleteAllTimetableEntries()
    homeworkDao.deleteAllHomeworks()
    testRecordDao.deleteAllTests()
    subjectDao.deleteAllSubjects()
    leadDao.deleteAllLeads()
    onlineFormDao.deleteAllForms()
    notificationDao.deleteAllNotifications()
    questionDao.deleteAllQuestions()
    weeklyTimetableDao.deleteAllWeeklyTimetable()
    batchDao.deleteAllBatches()
    teacherDao.deleteAllTeachers()
    studentDao.deleteAllStudents()
  }

  suspend fun bulkInsertStudents(studentsList: List<Student>, syncToCloud: Boolean = false) {
    val activeInstituteId = AppRepository.getCurrentInstituteId()
    val allowedStudents = studentsList.mapNotNull { stu ->
      val reqInst = stu.instituteId.trim().ifBlank { activeInstituteId }
      if (!com.example.security.SecurityEngine.isValidInstituteId(reqInst)) {
        null
      } else if (AppRepository.isLoggedIn.value && !reqInst.equals(activeInstituteId, ignoreCase = true)) {
        com.example.security.SecurityEngine.logEvent(
          severity = com.example.security.SecuritySeverity.ALERT,
          eventType = "CROSS_TENANT_WRITE_BLOCKED",
          actor = AppRepository.currentUserIdentifier.value.ifBlank { "System" },
          details = "Blocked cross-institute bulkInsertStudents for '${stu.id}' targeting '$reqInst' while active institute is '$activeInstituteId'."
        )
        null
      } else {
        val resolvedBatchId = stu.batchId.ifBlank { "BATCH_${stu.batch.replace(" ", "_")}" }
        stu.copy(batchId = resolvedBatchId, instituteId = reqInst)
      }
    }
    if (allowedStudents.isEmpty()) return

    val entities = allowedStudents.map { StudentEntity.fromDomain(it) }
    studentDao.insertStudents(entities)

    val enrollments = allowedStudents.map { stu ->
      val resolvedBatchId = stu.batchId.ifBlank { "BATCH_${stu.batch.replace(" ", "_")}" }
      ensureBatchExistsForForeignKey(
        batchId = resolvedBatchId,
        batchName = stu.batch,
        instituteId = stu.instituteId
      )
      BatchEnrollmentEntity(
        id = "ENR_${stu.id}_${stu.batch.replace(" ", "_")}",
        studentId = stu.id,
        studentName = stu.name,
        batchId = resolvedBatchId,
        batchName = stu.batch,
        className = stu.className,
        stream = stu.stream,
        enrollmentDate = stu.admissionDate,
        feeStatus = stu.feeStatus.ifBlank { "Up-to-Date" },
        status = stu.status
      )
    }
    enrollmentDao.insertEnrollments(enrollments)
    allowedStudents.zip(enrollments).forEach { (stu, enr) ->
      AppRepository.onLocalStudentUpserted(stu, enr)
      if (syncToCloud) {
        syncStudentToFirestore(stu, SyncOperationType.CREATE)
        syncEnrollmentToFirestore(enr)
      }
    }
  }

  suspend fun insertBatch(batch: Batch) {
    val entity = BatchEntity.fromDomain(batch)
    batchDao.insertBatch(entity)
  }

  suspend fun enrollStudentInBatch(student: Student, batch: Batch, syncToCloud: Boolean = true) {
    val activeInstituteId = AppRepository.getCurrentInstituteId()
    val existingEntity = studentDao.getStudentByIdOnce(student.id)
    val existingInstituteId = existingEntity?.instituteId ?: AppRepository.findExistingStudentInstituteId(student.id)
    val requestedStudentInstituteId = student.instituteId.trim().ifBlank { existingInstituteId ?: activeInstituteId }
    val requestedBatchInstituteId = batch.instituteId.trim().ifBlank { activeInstituteId }

    if (!com.example.security.SecurityEngine.isValidInstituteId(requestedStudentInstituteId) ||
      !com.example.security.SecurityEngine.isValidInstituteId(requestedBatchInstituteId)
    ) {
      com.example.security.SecurityEngine.logEvent(
        severity = com.example.security.SecuritySeverity.ALERT,
        eventType = "INVALID_INSTITUTE_SYNC_BLOCKED",
        actor = AppRepository.currentUserIdentifier.value.ifBlank { "System" },
        details = "Blocked enrollStudentInBatch for '${student.id}' with invalid instituteId."
      )
      return
    }
    if (existingInstituteId != null && !existingInstituteId.equals(requestedStudentInstituteId, ignoreCase = true)) {
      com.example.security.SecurityEngine.logEvent(
        severity = com.example.security.SecuritySeverity.ALERT,
        eventType = "INSTITUTE_REASSIGNMENT_BLOCKED",
        actor = AppRepository.currentUserIdentifier.value.ifBlank { "System" },
        details = "Blocked enrollStudentInBatch institute reassignment for '${student.id}' from '$existingInstituteId' to '$requestedStudentInstituteId'."
      )
      return
    }
    if ((existingInstituteId != null && !existingInstituteId.equals(activeInstituteId, ignoreCase = true)) ||
      (AppRepository.isLoggedIn.value && !requestedStudentInstituteId.equals(activeInstituteId, ignoreCase = true)) ||
      !requestedBatchInstituteId.equals(existingInstituteId ?: requestedStudentInstituteId, ignoreCase = true)
    ) {
      com.example.security.SecurityEngine.logEvent(
        severity = com.example.security.SecuritySeverity.ALERT,
        eventType = "CROSS_TENANT_WRITE_BLOCKED",
        actor = AppRepository.currentUserIdentifier.value.ifBlank { "System" },
        details = "Blocked cross-institute enrollStudentInBatch for student '${student.id}' ($requestedStudentInstituteId) and batch '${batch.id}' ($requestedBatchInstituteId) while active institute is '$activeInstituteId'."
      )
      return
    }

    val resolvedInstituteId = existingInstituteId ?: requestedStudentInstituteId
    val resolvedBatchId = batch.id.ifBlank { "BATCH_${batch.name.replace(" ", "_")}" }
    val updatedStudent = student.copy(
      batch = batch.name,
      batchId = resolvedBatchId,
      instituteId = resolvedInstituteId
    )
    val entity = StudentEntity.fromDomain(updatedStudent)
    if (existingEntity != null) {
      studentDao.updateStudent(entity)
      if (!existingEntity.batch.equals(batch.name, ignoreCase = true) ||
        (existingEntity.batchId.isNotBlank() && !existingEntity.batchId.equals(resolvedBatchId, ignoreCase = true))
      ) {
        enrollmentDao.deactivateEnrollmentsByStudent(updatedStudent.id)
      }
    } else {
      studentDao.insertStudent(entity)
    }

    ensureBatchExistsForForeignKey(
      batchId = resolvedBatchId,
      batchName = batch.name,
      instituteId = resolvedInstituteId
    )

    val enrollment = BatchEnrollmentEntity(
      id = "ENR_${updatedStudent.id}_${resolvedBatchId}",
      studentId = updatedStudent.id,
      studentName = updatedStudent.name,
      batchId = resolvedBatchId,
      batchName = batch.name,
      className = updatedStudent.className,
      stream = updatedStudent.stream,
      enrollmentDate = SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date()),
      feeStatus = updatedStudent.feeStatus.ifBlank { "Up-to-Date" },
      status = updatedStudent.status.ifBlank { "Active" }
    )
    enrollmentDao.insertEnrollment(enrollment)
    AppRepository.onLocalStudentUpserted(updatedStudent, enrollment)

    if (syncToCloud) {
      syncStudentToFirestore(updatedStudent, SyncOperationType.UPDATE)
      syncEnrollmentToFirestore(enrollment)
    }
  }

  private fun syncStudentToFirestore(
    student: Student,
    operationType: SyncOperationType = SyncOperationType.UPDATE
  ) {
    FirestoreService.syncStudentToCloud(
      student = student,
      createdBy = AppRepository.currentUserIdentifier.value.ifBlank { "ADMIN" },
      operationType = operationType
    ) { synced ->
      if (synced) {
        Log.d("StudentBatchRepository", "Student ${student.name} (${operationType.name}) synced to Firestore.")
      } else {
        Log.w("StudentBatchRepository", "Student ${student.name} (${operationType.name}) queued in persistent sync_queue.")
      }
    }
  }

  fun toSyncQueueEntity(
    envelope: FirestoreSyncEnvelope,
    queueId: String = SyncQueueEntity.generateQueueId(),
    activeInstituteId: String = AppRepository.getCurrentInstituteId()
  ): SyncQueueEntity {
    return FirestoreService.toSyncQueueEntity(
      envelope = envelope,
      queueId = queueId,
      activeInstituteId = activeInstituteId
    )
  }

  private fun syncEnrollmentToFirestore(enrollment: BatchEnrollmentEntity) {
    val db = FirestoreService.db ?: return
    val map = mapOf(
      "id" to enrollment.id,
      "studentId" to enrollment.studentId,
      "studentName" to enrollment.studentName,
      "batchId" to enrollment.batchId,
      "batchName" to enrollment.batchName,
      "className" to enrollment.className,
      "stream" to enrollment.stream,
      "enrollmentDate" to enrollment.enrollmentDate,
      "feeStatus" to enrollment.feeStatus,
      "status" to enrollment.status,
      "lastUpdated" to enrollment.lastUpdated
    )
    db.collection("batch_enrollments")
      .document(enrollment.id)
      .set(map)
      .addOnSuccessListener {
        Log.d("StudentBatchRepository", "Batch enrollment ${enrollment.id} synced to Firestore.")
      }
  }

  // --- PERSISTENT SYNC QUEUE FOUNDATION ---
  suspend fun insertSyncQueueItem(item: SyncQueueEntity) {
    syncQueueDao.insertQueueItem(item)
  }

  suspend fun updateSyncQueueItem(item: SyncQueueEntity) {
    syncQueueDao.updateQueueItem(item)
  }

  suspend fun getPendingSyncQueueItems(): List<SyncQueueEntity> {
    return syncQueueDao.getPendingItems()
  }

  suspend fun getSyncQueueItemById(queueId: String): SyncQueueEntity? {
    return syncQueueDao.getItemById(queueId)
  }

  suspend fun markSyncQueueItemProcessing(
    queueId: String,
    attemptAt: Long = System.currentTimeMillis()
  ): Int {
    return syncQueueDao.markProcessing(queueId = queueId, attemptAt = attemptAt)
  }

  suspend fun markSyncQueueItemFailed(
    queueId: String,
    errorMessage: String? = null,
    attemptAt: Long = System.currentTimeMillis()
  ): Int {
    return syncQueueDao.markFailed(
      queueId = queueId,
      errorMessage = errorMessage,
      attemptAt = attemptAt
    )
  }

  suspend fun markSyncQueueItemCompleted(
    queueId: String,
    completedAt: Long = System.currentTimeMillis()
  ): Int {
    return syncQueueDao.markCompleted(queueId = queueId, completedAt = completedAt)
  }

  suspend fun deleteCompletedSyncQueueItem(queueId: String): Int {
    return syncQueueDao.deleteCompletedItem(queueId = queueId)
  }

  suspend fun countPendingSyncQueueItems(): Int {
    return syncQueueDao.countPendingItems()
  }
}
