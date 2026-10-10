package com.example

import com.example.data.AttendanceRecord
import com.example.data.Course
import com.example.data.CourseEnrollment
import com.example.data.FeeRecord
import com.example.data.FeeStructure
import com.example.data.Student
import com.example.data.StudentFeePayment
import com.example.data.db.AppRoomDatabase
import com.example.data.db.AttendanceRecordEntity
import com.example.data.db.CourseEnrollmentEntity
import com.example.data.db.CourseEntity
import com.example.data.db.FeePaymentEntity
import com.example.data.db.FeeStructureEntity
import com.example.data.db.StudentEntity
import com.example.data.firestore.FirestoreAttendanceRecord
import com.example.data.firestore.FirestoreCourse
import com.example.data.firestore.FirestoreCourseEnrollment
import com.example.data.firestore.FirestoreFeeInvoice
import com.example.data.firestore.FirestoreFeePayment
import com.example.data.firestore.FirestoreFeeStructure
import com.example.data.firestore.FirestoreStudent
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import androidx.test.core.app.ApplicationProvider
import android.content.Context
import androidx.room.Room

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AcademicDataModelsTest {

  @Test
  fun `verify Room and Firestore Student models mapping and persistence`() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val inMemoryDb = Room.inMemoryDatabaseBuilder(context, AppRoomDatabase::class.java)
      .allowMainThreadQueries()
      .build()

    val domainStudent = Student(
      id = "STU_TEST_101",
      name = "Aditya Kulkarni",
      photo = "https://example.com/photo.jpg",
      mobile = "+91 98220 12345",
      parentName = "Suresh Kulkarni",
      parentContact = "+91 98220 12346",
      parentEmail = "suresh.kulkarni@example.com",
      email = "aditya.kulkarni@mytuition.com",
      dob = "14/05/2008",
      gender = "Male",
      address = "Shivaji Nagar, Pune",
      school = "Fergusson Junior College",
      className = "12th",
      batch = "12th Science",
      batchId = "BATCH_12_SCI",
      courseId = "CRS_JEE_12",
      stream = "Science",
      admissionDate = "01/06/2025",
      status = "Active",
      attendancePercent = 92,
      overallAvg = 88,
      rank = 1,
      feeStatus = "Paid",
      instituteId = "FSI_MAIN"
    )

    // 1. Room Entity Conversion and DAO insertion
    val entity = StudentEntity.fromDomain(domainStudent)
    inMemoryDb.studentDao().insertStudent(entity)
    val fetchedFromDb = inMemoryDb.studentDao().getStudentByIdOnce("STU_TEST_101")
    assertNotNull(fetchedFromDb)
    assertEquals("Aditya Kulkarni", fetchedFromDb?.name)
    assertEquals("12th", fetchedFromDb?.className)

    val convertedBackDomain = fetchedFromDb!!.toDomain()
    assertEquals("STU_TEST_101", convertedBackDomain.id)
    assertEquals(92, convertedBackDomain.attendancePercent)

    // 2. Firestore Model Conversion and Mapping
    val firestoreModel = FirestoreStudent.fromDomain(domainStudent)
    val map = firestoreModel.toMap()
    assertEquals("STU_TEST_101", map["id"])
    assertEquals("Aditya Kulkarni", map["name"])
    assertEquals("FSI_MAIN", map["instituteId"])

    val reconstructedFromMap = FirestoreStudent.fromMap("STU_TEST_101", map)
    val reconstructedDomain = reconstructedFromMap.toDomain()
    assertEquals("Aditya Kulkarni", reconstructedDomain.name)
    assertEquals("FSI_MAIN", reconstructedDomain.instituteId)

    inMemoryDb.close()
  }

  @Test
  fun `verify Room and Firestore Course models mapping and persistence`() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val inMemoryDb = Room.inMemoryDatabaseBuilder(context, AppRoomDatabase::class.java)
      .allowMainThreadQueries()
      .build()

    val domainCourse = Course(
      id = "CRS_JEE_12",
      name = "Complete Class 12th Physics Mastery & JEE Advanced",
      code = "PHY-12-JEE",
      description = "Comprehensive mechanics, electrodynamics, optics, and modern physics.",
      grade = "12th",
      stream = "Science",
      subjects = listOf("Physics", "Mechanics", "Electrodynamics"),
      totalFee = 48000.0,
      durationMonths = 12,
      hoursPerWeek = 8,
      leadInstructorId = "FAC_01",
      leadInstructorName = "Prof. Shrirang Deshmukh",
      status = "Active",
      maxCapacity = 35,
      enrolledStudentCount = 20,
      syllabusChapters = listOf("Rotational Motion", "Electrostatics", "Wave Optics"),
      instituteId = "FSI_MAIN"
    )

    // 1. Room Entity Insertion and Flow query
    val courseEntity = CourseEntity.fromDomain(domainCourse)
    inMemoryDb.courseDao().insertCourse(courseEntity)

    val queriedCourse = inMemoryDb.courseDao().getCourseById("CRS_JEE_12")
    assertNotNull(queriedCourse)
    assertEquals("Complete Class 12th Physics Mastery & JEE Advanced", queriedCourse?.name)
    assertEquals("12th", queriedCourse?.grade)
    assertEquals(48000.0, queriedCourse?.totalFee ?: 0.0, 0.001)

    val allCourses = inMemoryDb.courseDao().getAllCourses().first()
    assertEquals(1, allCourses.size)

    // 2. Firestore Course Mapping
    val firestoreCourse = FirestoreCourse.fromDomain(domainCourse)
    val map = firestoreCourse.toMap()
    assertEquals("PHY-12-JEE", map["code"])
    assertEquals(48000.0, map["totalFee"])

    val fromMapCourse = FirestoreCourse.fromMap("CRS_JEE_12", map)
    assertEquals("Prof. Shrirang Deshmukh", fromMapCourse.leadInstructorName)
    assertEquals(3, fromMapCourse.subjects.size)

    // 3. Course Enrollment
    val student = Student(
      id = "STU_TEST_101",
      name = "Aditya Kulkarni",
      mobile = "+91 98220 12345",
      parentName = "Suresh Kulkarni",
      parentContact = "+91 98220 12346",
      email = "aditya@test.com",
      dob = "14/05/2008",
      gender = "Male",
      address = "Pune",
      school = "Fergusson Jr College",
      className = "12th",
      batch = "12th Science",
      stream = "Science",
      admissionDate = "01/06/2025"
    )
    inMemoryDb.studentDao().insertStudent(StudentEntity.fromDomain(student))

    val enrollment = CourseEnrollment(
      enrollmentId = "ENR_101",
      studentId = "STU_TEST_101",
      courseId = "CRS_JEE_12",
      batchId = "BATCH_12_SCI",
      enrolledDate = "01/06/2025",
      status = "Active",
      discountPercent = 10.0,
      netFee = 43200.0,
      instituteId = "FSI_MAIN"
    )
    val enrollmentEntity = CourseEnrollmentEntity.fromDomain(enrollment)
    inMemoryDb.courseEnrollmentDao().insertEnrollment(enrollmentEntity)

    val fetchedEnrollment = inMemoryDb.courseEnrollmentDao().getEnrollmentById("ENR_101")
    assertNotNull(fetchedEnrollment)
    assertEquals(43200.0, fetchedEnrollment?.netFee ?: 0.0, 0.001)

    inMemoryDb.close()
  }

  @Test
  fun `verify Room and Firestore AttendanceRecord models mapping and persistence`() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val inMemoryDb = Room.inMemoryDatabaseBuilder(context, AppRoomDatabase::class.java)
      .allowMainThreadQueries()
      .build()

    // Insert prerequisite student to satisfy foreign key
    val student = Student(
      id = "STU_ATT_01",
      name = "Neha Sharma",
      mobile = "+91 98888 11111",
      parentName = "Rajesh Sharma",
      parentContact = "+91 98888 11112",
      email = "neha@test.com",
      dob = "01/01/2008",
      gender = "Female",
      address = "Pune",
      school = "Modern High School",
      className = "12th",
      batch = "12th Science",
      stream = "Science",
      admissionDate = "01/06/2025"
    )
    inMemoryDb.studentDao().insertStudent(StudentEntity.fromDomain(student))

    val domainAttendance = AttendanceRecord(
      id = "ATT_20260816_12THSCIENCE_STU_ATT_01",
      date = "16/08/2026",
      studentId = "STU_ATT_01",
      studentName = "Neha Sharma",
      batchName = "12th Science",
      status = "Present",
      day = 16,
      month = 8,
      monthName = "August",
      year = 2026,
      time = "10:00 AM",
      batchId = "BATCH_12_SCI",
      courseId = "CRS_JEE_12",
      sessionType = "Regular Class",
      instituteId = "FSI_MAIN"
    )

    // 1. Room Entity Insertion and Query
    val attendanceEntity = AttendanceRecordEntity.fromDomain(domainAttendance)
    inMemoryDb.attendanceDao().insertAttendance(attendanceEntity)

    val fetchedAttendance = inMemoryDb.attendanceDao().getAttendanceById("ATT_20260816_12THSCIENCE_STU_ATT_01")
    assertNotNull(fetchedAttendance)
    assertEquals("Present", fetchedAttendance?.status)
    assertEquals("Neha Sharma", fetchedAttendance?.studentName)

    // 2. Firestore Attendance Mapping
    val firestoreAttendance = FirestoreAttendanceRecord.fromDomain(domainAttendance)
    val map = firestoreAttendance.toMap()
    assertEquals("16/08/2026", map["date"])
    assertEquals("Present", map["status"])
    assertEquals("CRS_JEE_12", map["courseId"])

    val reconstructed = FirestoreAttendanceRecord.fromMap(domainAttendance.id, map)
    assertEquals("Neha Sharma", reconstructed.studentName)
    assertEquals("Regular Class", reconstructed.sessionType)

    inMemoryDb.close()
  }

  @Test
  fun `verify Room and Firestore Fee models mapping and persistence`() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val inMemoryDb = Room.inMemoryDatabaseBuilder(context, AppRoomDatabase::class.java)
      .allowMainThreadQueries()
      .build()

    // 1. Fee Structure
    val feeStructure = FeeStructure(
      id = "FEE_STR_12_SCI",
      courseId = "CRS_JEE_12",
      courseName = "Class 12th Science JEE Preparation",
      grade = "12th",
      stream = "Science",
      baseTuitionFee = 42000.0,
      examFee = 4000.0,
      materialFee = 2000.0,
      totalFee = 48000.0,
      installmentCount = 3,
      installmentIntervalMonths = 3,
      allowDiscount = true,
      academicYear = "2026–2027",
      instituteId = "FSI_MAIN"
    )

    val feeStructureEntity = FeeStructureEntity.fromDomain(feeStructure)
    inMemoryDb.feeStructureDao().insertFeeStructure(feeStructureEntity)

    val fetchedStructure = inMemoryDb.feeStructureDao().getFeeStructureById("FEE_STR_12_SCI")
    assertNotNull(fetchedStructure)
    assertEquals(48000.0, fetchedStructure?.totalFee ?: 0.0, 0.001)

    // 2. Fee Invoice & Payment
    val student = Student(
      id = "STU_FEE_01",
      name = "Tanvi Patil",
      mobile = "+91 97777 22222",
      parentName = "Anil Patil",
      parentContact = "+91 97777 22223",
      email = "tanvi@test.com",
      dob = "10/10/2008",
      gender = "Female",
      address = "Pune",
      school = "SP College",
      className = "12th",
      batch = "12th Science",
      stream = "Science",
      admissionDate = "01/06/2025"
    )
    inMemoryDb.studentDao().insertStudent(StudentEntity.fromDomain(student))

    val payment = StudentFeePayment(
      id = "PAY_2026_001",
      studentId = "STU_FEE_01",
      studentName = "Tanvi Patil",
      amount = 16000.0,
      paymentDate = "16/08/2026",
      paymentStatus = "Paid",
      feeType = "Installment 1",
      paymentMode = "UPI",
      receiptNo = "REC-8801",
      transactionId = "UPI-UTR-991283",
      month = "August 2026",
      remarks = "Paid via PhonePe",
      recordedBy = "Admin",
      courseId = "CRS_JEE_12",
      instituteId = "FSI_MAIN"
    )

    val paymentEntity = FeePaymentEntity.fromDomain(payment)
    inMemoryDb.feePaymentDao().insertPayment(paymentEntity)

    val studentPayments = inMemoryDb.feePaymentDao().getPaymentsByStudent("STU_FEE_01").first()
    assertEquals(1, studentPayments.size)
    assertEquals(16000.0, studentPayments.first().amount, 0.001)

    // 3. Firestore Fee Payment Mapping
    val firestorePayment = FirestoreFeePayment.fromDomain(payment)
    val paymentMap = firestorePayment.toMap()
    assertEquals("REC-8801", paymentMap["receiptNo"])
    assertEquals(16000.0, paymentMap["amount"])

    val fromMapPayment = FirestoreFeePayment.fromMap(payment.id, paymentMap)
    assertEquals("UPI-UTR-991283", fromMapPayment.transactionId)
    assertEquals("Paid", fromMapPayment.paymentStatus)

    // 4. Firestore Fee Invoice Mapping
    val invoice = FeeRecord(
      id = "INV_2026_01",
      studentName = "Tanvi Patil",
      feeAmount = 48000,
      dueDate = "15/09/2026",
      paidAmount = 16000,
      pendingAmount = 32000,
      paymentStatus = "Partial",
      month = "August 2026",
      studentId = "STU_FEE_01",
      batchName = "12th Science",
      batchId = "BATCH_12_SCI",
      courseId = "CRS_JEE_12",
      feeType = "Annual Course Fee",
      instituteId = "FSI_MAIN"
    )

    val firestoreInvoice = FirestoreFeeInvoice.fromDomain(invoice)
    val invoiceMap = firestoreInvoice.toMap()
    assertEquals(48000, invoiceMap["feeAmount"])
    assertEquals(32000, invoiceMap["pendingAmount"])

    val fromMapInvoice = FirestoreFeeInvoice.fromMap(invoice.id, invoiceMap)
    assertEquals("Partial", fromMapInvoice.paymentStatus)
    assertEquals("INV_2026_01", fromMapInvoice.id)

    inMemoryDb.close()
  }
}
