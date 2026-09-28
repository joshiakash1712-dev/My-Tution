package com.example.data

import android.content.Context
import android.util.Log
import com.example.data.db.AppRoomDatabase
import com.example.data.db.BatchEnrollmentEntity
import com.example.data.db.BatchEntity
import com.example.data.db.StudentEntity
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

  val allStudents: Flow<List<Student>> = studentDao.getAllStudents().map { entities ->
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
    studentAttemptDao.insertAttempt(com.example.data.db.StudentAttemptEntity.fromDomain(attempt))
  }

  suspend fun insertStudyLog(log: StudyLogItem) {
    studyLogDao.insertStudyLog(com.example.data.db.StudyLogEntity.fromDomain(log))
  }

  suspend fun insertStudentGoal(goal: GoalItem) {
    studentGoalDao.insertGoal(com.example.data.db.StudentGoalEntity.fromDomain(goal))
  }

  suspend fun insertParentMeeting(meeting: ParentMeetingItem) {
    parentMeetingDao.insertMeeting(com.example.data.db.ParentMeetingEntity.fromDomain(meeting))
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
    feeInvoiceDao.insertInvoice(com.example.data.db.FeeInvoiceEntity.fromDomain(invoice))
  }

  suspend fun updateFeeInvoice(invoice: FeeRecord) {
    feeInvoiceDao.insertInvoice(com.example.data.db.FeeInvoiceEntity.fromDomain(invoice))
  }

  suspend fun insertFeeInvoices(invoices: List<FeeRecord>) {
    feeInvoiceDao.insertInvoices(invoices.map { com.example.data.db.FeeInvoiceEntity.fromDomain(it) })
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
    doubtDao.insertDoubt(com.example.data.db.DoubtEntity.fromDomain(item))
  }

  suspend fun updateDoubt(item: DoubtItem) {
    doubtDao.insertDoubt(com.example.data.db.DoubtEntity.fromDomain(item))
  }

  suspend fun insertDoubts(items: List<DoubtItem>) {
    doubtDao.insertDoubts(items.map { com.example.data.db.DoubtEntity.fromDomain(it) })
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
    feePaymentDao.insertPayment(entity)
  }

  suspend fun insertFeePayments(payments: List<StudentFeePayment>) {
    val entities = payments.map { com.example.data.db.FeePaymentEntity.fromDomain(it) }
    feePaymentDao.insertPayments(entities)
  }

  suspend fun updateFeePayment(payment: StudentFeePayment) {
    val entity = com.example.data.db.FeePaymentEntity.fromDomain(payment)
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
    attendanceDao.insertAttendance(entity)
  }

  suspend fun insertAttendanceList(records: List<AttendanceRecord>) {
    val entities = records.map { com.example.data.db.AttendanceRecordEntity.fromDomain(it) }
    attendanceDao.insertAttendanceList(entities)
  }

  suspend fun updateAttendance(record: AttendanceRecord) {
    val entity = com.example.data.db.AttendanceRecordEntity.fromDomain(record)
    attendanceDao.updateAttendance(entity)
  }

  suspend fun deleteAttendance(recordId: String) {
    attendanceDao.deleteAttendanceById(recordId)
  }

  suspend fun purgeExpiredAttendance(cutoffTimestamp: Long): Int {
    return attendanceDao.deleteExpiredAttendance(cutoffTimestamp)
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
    teacherDao.deleteTeacherById(teacherId)
    userAccountDao.deleteAccountsByLinkedEntities(listOf(teacherId))
  }

  suspend fun deleteAllTeachers() {
    teacherDao.deleteAllTeachers()
  }

  suspend fun insertStudent(student: Student) {
    val entity = StudentEntity.fromDomain(student)
    studentDao.insertStudent(entity)

    val enrollment = BatchEnrollmentEntity(
      id = "ENR_${student.id}_${student.batch.replace(" ", "_")}",
      studentId = student.id,
      studentName = student.name,
      batchId = student.batchId.ifBlank { "BATCH_${student.batch.replace(" ", "_")}" },
      batchName = student.batch,
      className = student.className,
      stream = student.stream,
      enrollmentDate = student.admissionDate,
      feeStatus = student.feeStatus,
      status = student.status
    )
    enrollmentDao.insertEnrollment(enrollment)

    syncStudentToFirestore(student)
    syncEnrollmentToFirestore(enrollment)
  }

  suspend fun updateStudent(student: Student) {
    val entity = StudentEntity.fromDomain(student)
    studentDao.updateStudent(entity)
    val enrollment = BatchEnrollmentEntity(
      id = "ENR_${student.id}_${student.batch.replace(" ", "_")}",
      studentId = student.id,
      studentName = student.name,
      batchId = student.batchId.ifBlank { "BATCH_${student.batch.replace(" ", "_")}" },
      batchName = student.batch,
      className = student.className,
      stream = student.stream,
      enrollmentDate = student.admissionDate,
      feeStatus = student.feeStatus,
      status = student.status
    )
    enrollmentDao.insertEnrollment(enrollment)
    syncStudentToFirestore(student)
  }

  suspend fun deleteStudent(studentId: String) {
    studentDao.deleteStudentById(studentId)
    enrollmentDao.deleteEnrollmentsByStudent(studentId)
    feeInvoiceDao.deleteInvoicesByStudentIds(listOf(studentId))
    userAccountDao.deleteAccountsByLinkedEntities(listOf(studentId))
    FirestoreService.deleteStudentProfile(studentId, {}, {})
  }

  suspend fun deleteStudents(studentIds: List<String>) {
    if (studentIds.isEmpty()) return
    studentDao.deleteStudentsByIds(studentIds)
    studentIds.forEach { id ->
      enrollmentDao.deleteEnrollmentsByStudent(id)
      FirestoreService.deleteStudentProfile(id, {}, {})
    }
    feeInvoiceDao.deleteInvoicesByStudentIds(studentIds)
    userAccountDao.deleteAccountsByLinkedEntities(studentIds)
  }

  suspend fun clearAllData() {
    studentDao.deleteAllStudents()
    teacherDao.deleteAllTeachers()
    batchDao.deleteAllBatches()
    enrollmentDao.deleteAllEnrollments()
    attendanceDao.deleteAllAttendance()
    feePaymentDao.deleteAllPayments()
    timetableDao.deleteAllTimetableEntries()
    feeInvoiceDao.deleteAllInvoices()
    homeworkDao.deleteAllHomeworks()
    doubtDao.deleteAllDoubts()
    testRecordDao.deleteAllTests()
    subjectDao.deleteAllSubjects()
    leadDao.deleteAllLeads()
    onlineFormDao.deleteAllForms()
    notificationDao.deleteAllNotifications()
    questionDao.deleteAllQuestions()
    studentAttemptDao.deleteAllAttempts()
    studyLogDao.deleteAllStudyLogs()
    studentGoalDao.deleteAllGoals()
    parentMeetingDao.deleteAllMeetings()
    weeklyTimetableDao.deleteAllWeeklyTimetable()
  }

  suspend fun bulkInsertStudents(studentsList: List<Student>) {
    val entities = studentsList.map { StudentEntity.fromDomain(it) }
    studentDao.insertStudents(entities)

    val enrollments = studentsList.map { stu ->
      BatchEnrollmentEntity(
        id = "ENR_${stu.id}_${stu.batch.replace(" ", "_")}",
        studentId = stu.id,
        studentName = stu.name,
        batchId = "BATCH_${stu.batch.replace(" ", "_")}",
        batchName = stu.batch,
        className = stu.className,
        stream = stu.stream,
        enrollmentDate = stu.admissionDate,
        feeStatus = "Up-to-Date",
        status = stu.status
      )
    }
    enrollmentDao.insertEnrollments(enrollments)
  }

  suspend fun insertBatch(batch: Batch) {
    val entity = BatchEntity.fromDomain(batch)
    batchDao.insertBatch(entity)
  }

  suspend fun enrollStudentInBatch(student: Student, batch: Batch) {
    val updatedStudent = student.copy(batch = batch.name)
    studentDao.updateStudent(StudentEntity.fromDomain(updatedStudent))

    val enrollment = BatchEnrollmentEntity(
      id = "ENR_${student.id}_${batch.id}",
      studentId = student.id,
      studentName = student.name,
      batchId = batch.id,
      batchName = batch.name,
      className = student.className,
      stream = student.stream,
      enrollmentDate = SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date()),
      feeStatus = "Up-to-Date",
      status = "Active"
    )
    enrollmentDao.insertEnrollment(enrollment)
    syncEnrollmentToFirestore(enrollment)
  }

  private fun syncStudentToFirestore(student: Student) {
    val fsStudent = FirestoreStudent(
      id = student.id,
      name = student.name,
      grade = "${student.className} (${student.stream})",
      subjectsEnrolled = listOf("Physics", "Chemistry", "Mathematics"),
      contactInfo = student.mobile,
      parentName = student.parentName,
      parentContact = student.parentContact
    )
    FirestoreService.createStudentProfile(
      fsStudent,
      onSuccess = {
        Log.d("StudentBatchRepository", "Student ${student.name} synced to Firestore.")
      },
      onFailure = { e ->
        Log.w("StudentBatchRepository", "Firestore sync postponed (offline): ${e.message}")
      }
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
}
