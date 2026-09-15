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
  private val scope = CoroutineScope(Dispatchers.IO)

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

  init {
    scope.launch {
      try {
        if (batchDao.getCount() == 0) {
          batchDao.insertBatches(SeedData.defaultBatches.map { BatchEntity.fromDomain(it) })
        }
        if (studentDao.getCount() == 0) {
          val initialStudents = SeedData.get15DemoStudents()
          studentDao.insertStudents(initialStudents.map { StudentEntity.fromDomain(it) })
          val enrollments = initialStudents.map { stu ->
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
        if (teacherDao.getCount() == 0) {
          teacherDao.insertTeachers(SeedData.defaultTeachers.map { com.example.data.db.TeacherEntity.fromDomain(it) })
        }
      } catch (e: Exception) {
        android.util.Log.e("StudentBatchRepository", "Error during DB initialization: ${e.message}")
      }
    }
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
      batchId = "BATCH_${student.batch.replace(" ", "_")}",
      batchName = student.batch,
      className = student.className,
      stream = student.stream,
      enrollmentDate = student.admissionDate,
      feeStatus = "Up-to-Date",
      status = student.status
    )
    enrollmentDao.insertEnrollment(enrollment)

    syncStudentToFirestore(student)
    syncEnrollmentToFirestore(enrollment)
  }

  suspend fun updateStudent(student: Student) {
    val entity = StudentEntity.fromDomain(student)
    studentDao.updateStudent(entity)
    syncStudentToFirestore(student)
  }

  suspend fun deleteStudent(studentId: String) {
    studentDao.deleteStudentById(studentId)
    FirestoreService.deleteStudentProfile(studentId, {}, {})
  }

  suspend fun deleteStudents(studentIds: List<String>) {
    if (studentIds.isEmpty()) return
    studentDao.deleteStudentsByIds(studentIds)
    studentIds.forEach { id ->
      FirestoreService.deleteStudentProfile(id, {}, {})
    }
  }

  suspend fun clearAllData() {
    studentDao.deleteAllStudents()
    teacherDao.deleteAllTeachers()
    batchDao.deleteAllBatches()
    enrollmentDao.deleteAllEnrollments()
    attendanceDao.deleteAllAttendance()
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
