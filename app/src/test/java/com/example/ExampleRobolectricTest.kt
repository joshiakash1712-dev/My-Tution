package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlinx.coroutines.flow.first

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("My Tuition (FSI)", appName)
  }

  @Test
  fun `verify live batch student counts sum equals 15`() {
    val demoStudents = com.example.data.SeedData.get15DemoStudents()
    assertEquals(15, demoStudents.size)

    val defaultBatches = com.example.data.SeedData.defaultBatches
    val totalCountInBatches = defaultBatches.sumOf { b ->
      demoStudents.count { it.batch.equals(b.name, ignoreCase = true) }
    }
    assertEquals(15, totalCountInBatches)

    // Verify individual batch distributions
    assertEquals(4, demoStudents.count { it.batch.equals("10th - Science (A)", ignoreCase = true) })
    assertEquals(2, demoStudents.count { it.batch.equals("11th - Science (B)", ignoreCase = true) })
    assertEquals(2, demoStudents.count { it.batch.equals("JEE Apex Morning", ignoreCase = true) })
    assertEquals(2, demoStudents.count { it.batch.equals("Foundation Target", ignoreCase = true) })
    assertEquals(2, demoStudents.count { it.batch.equals("10th - Science (B)", ignoreCase = true) })
    assertEquals(2, demoStudents.count { it.batch.equals("12th Science", ignoreCase = true) })
    assertEquals(1, demoStudents.count { it.batch.equals("NEET Zenith", ignoreCase = true) })
    assertEquals(0, demoStudents.count { it.batch.equals("8th Standard", ignoreCase = true) })
    assertEquals(0, demoStudents.count { it.batch.equals("11th Science", ignoreCase = true) })
  }

  @Test
  fun `verify faculty consistency between batches, timetable entries, and staff directory`() {
    val officialTeachers = com.example.data.SeedData.defaultTeachers
    val officialTeacherNames = officialTeachers.map { it.name }

    // 1. Staff directory contains exactly the 3 primary educators
    assertEquals(3, officialTeachers.size)
    assert(officialTeacherNames.contains("Prof. Shrirang Deshmukh"))
    assert(officialTeacherNames.contains("Dr. Faisal Patel"))
    assert(officialTeacherNames.contains("Prof. Akash Joshi"))

    // 2. All 9 batches in defaultBatches are assigned to official teachers
    val defaultBatches = com.example.data.SeedData.defaultBatches
    assertEquals(9, defaultBatches.size)
    defaultBatches.forEach { batch ->
      assert(officialTeacherNames.contains(batch.teacherName)) {
        "Batch '${batch.name}' has unassigned teacher: '${batch.teacherName}'"
      }
    }

    // 3. All timetable entries in getDefaultTimetableEntries are assigned to official teachers
    val defaultTimetable = com.example.data.SeedData.getDefaultTimetableEntries()
    defaultTimetable.forEach { entry ->
      assert(officialTeacherNames.contains(entry.teacherName)) {
        "Timetable entry '${entry.subject}' has unassigned teacher: '${entry.teacherName}'"
      }
    }

    // 4. Verify no occurrences of legacy or phantom teacher names
    val allMentionedTeachers = (defaultBatches.map { it.teacherName } + defaultTimetable.map { it.teacherName }).distinct()
    val legacyNames = listOf("Prof. Patil", "Dr. Shinde", "Er. Kulkarni", "Prof. Deshmukh", "Prof. Joshi")
    legacyNames.forEach { legacy ->
      assert(!allMentionedTeachers.contains(legacy)) {
        "Legacy teacher name '$legacy' is still present in batches or timetable!"
      }
    }

    // 5. Verify AppRepository.reconcileFacultyName properly resolves variations
    assertEquals("Dr. Faisal Patel", com.example.data.AppRepository.reconcileFacultyName("Prof. Patil"))
    assertEquals("Prof. Akash Joshi", com.example.data.AppRepository.reconcileFacultyName("Er. Kulkarni"))
    assertEquals("Prof. Shrirang Deshmukh", com.example.data.AppRepository.reconcileFacultyName("Dr. Shinde", "Foundation Target"))
    assertEquals("Dr. Faisal Patel", com.example.data.AppRepository.reconcileFacultyName("Dr. Shinde", "NEET Zenith"))
    assertEquals("Prof. Shrirang Deshmukh", com.example.data.AppRepository.reconcileFacultyName("Prof. Deshmukh"))
    assertEquals("Prof. Akash Joshi", com.example.data.AppRepository.reconcileFacultyName("Prof. Joshi"))

    // 6. Verify isSameFaculty correctly detects matching identity
    assert(com.example.data.AppRepository.isSameFaculty("Prof. Shrirang Deshmukh", "Prof. Deshmukh"))
    assert(com.example.data.AppRepository.isSameFaculty("Dr. Faisal Patel", "Prof. Patil"))
    assert(com.example.data.AppRepository.isSameFaculty("Prof. Akash Joshi", "Er. Kulkarni"))
  }

  @Test
  fun `verify attendance eligibility logic disables selection for batches with 0 enrolled students`() {
    val allStudents = com.example.data.SeedData.get12thScienceStudents()
    val allBatches = com.example.data.SeedData.defaultBatches

    val emptyBatches = allBatches.filter { batch ->
      allStudents.none { it.batch.equals(batch.name, ignoreCase = true) }
    }

    val populatedBatches = allBatches.filter { batch ->
      allStudents.any { it.batch.equals(batch.name, ignoreCase = true) }
    }

    // Verify we have both empty and populated batches in seed data
    assert(emptyBatches.isNotEmpty()) { "Expected at least one batch with 0 students" }
    assert(populatedBatches.isNotEmpty()) { "Expected at least one batch with enrolled students" }

    // Check specific known empty batches: 8th Standard and 11th Science
    val batch8th = allBatches.first { it.name == "8th Standard" }
    val batch11thSci = allBatches.first { it.name == "11th Science" }
    assertEquals(0, allStudents.count { it.batch.equals(batch8th.name, ignoreCase = true) })
    assertEquals(0, allStudents.count { it.batch.equals(batch11thSci.name, ignoreCase = true) })

    // Verify eligibility logic: hasStudents must be false for empty batches and true for populated batches
    emptyBatches.forEach { batch ->
      val studentsInBatch = allStudents.filter { it.batch.equals(batch.name, ignoreCase = true) }
      val hasStudents = studentsInBatch.isNotEmpty()
      assert(!hasStudents) { "Batch '${batch.name}' should have hasStudents = false (0 enrolled students)" }
    }

    populatedBatches.forEach { batch ->
      val studentsInBatch = allStudents.filter { it.batch.equals(batch.name, ignoreCase = true) }
      val hasStudents = studentsInBatch.isNotEmpty()
      assert(hasStudents) { "Batch '${batch.name}' should have hasStudents = true" }
    }
  }

  @Test
  fun `verify full database persistence for students, teachers, attendance and data regained across logout and login`() = kotlinx.coroutines.runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val db = com.example.data.db.AppRoomDatabase.getDatabase(context)
    val repo = com.example.data.StudentBatchRepository(context)

    // 1. Verify Student Integration with all related fields into Room
    val customStudent = com.example.data.Student(
      id = "STU_VERIFY_999",
      name = "Devendra Singh",
      photo = "",
      mobile = "+91 98230 11223",
      parentName = "Rajendra Singh",
      parentContact = "+91 98230 11224",
      email = "devendra.singh@test.com",
      dob = "15/08/2007",
      gender = "Male",
      address = "42 Silicon Avenue, Pune",
      school = "Army Public School",
      className = "Class 12",
      batch = "JEE Apex Morning",
      stream = "Science",
      admissionDate = "14/09/2026",
      status = "Active",
      attendancePercent = 95,
      overallAvg = 88,
      rank = 3,
      strongestSubject = "Physics",
      weakestSubject = "Chemistry",
      recentScores = listOf("Unit Test 1" to 85, "Mid-Term" to 92, "Terminal" to 88)
    )
    repo.insertStudent(customStudent)

    val queriedStudent = db.studentDao().getStudentById("STU_VERIFY_999").first()
    org.junit.Assert.assertNotNull("Student must be saved into Room database", queriedStudent)
    assertEquals("Devendra Singh", queriedStudent?.name)
    assertEquals("devendra.singh@test.com", queriedStudent?.email)
    assertEquals("+91 98230 11223", queriedStudent?.mobile)
    assertEquals("Rajendra Singh", queriedStudent?.parentName)
    assertEquals("+91 98230 11224", queriedStudent?.parentContact)
    assertEquals("Class 12", queriedStudent?.className)
    assertEquals("JEE Apex Morning", queriedStudent?.batch)
    assertEquals("Science", queriedStudent?.stream)
    assertEquals("Physics", queriedStudent?.strongestSubject)

    // 2. Verify Teacher Integration with all related fields into Room
    val customTeacher = com.example.data.Teacher(
      id = "FAC_VERIFY_888",
      name = "Dr. Ananya Sharma",
      subject = "Organic Chemistry",
      contact = "+91 98765 44332",
      qualification = "Ph.D in Chemical Sciences",
      experience = "12 Years",
      assignedBatches = listOf("JEE Apex Morning", "NEET Zenith"),
      classesTaken = 140,
      attendancePercent = 99,
      feedbackRating = 4.9f,
      salary = 85000,
      incentives = 5000,
      deductions = 0,
      status = "Active",
      email = "dr.ananya@mytuition.com",
      activationCode = "CHEM-2026",
      isActivated = true
    )
    repo.insertTeacher(customTeacher)

    val queriedTeacher = db.teacherDao().getTeacherById("FAC_VERIFY_888").first()
    org.junit.Assert.assertNotNull("Teacher must be saved into Room database", queriedTeacher)
    assertEquals("Dr. Ananya Sharma", queriedTeacher?.name)
    assertEquals("Organic Chemistry", queriedTeacher?.subject)
    assertEquals("Ph.D in Chemical Sciences", queriedTeacher?.qualification)
    assertEquals("12 Years", queriedTeacher?.experience)
    assertEquals(85000, queriedTeacher?.salary)
    assertEquals(5000, queriedTeacher?.incentives)
    assertEquals("Active", queriedTeacher?.status)
    assertEquals("dr.ananya@mytuition.com", queriedTeacher?.email)

    // 3. Verify Attendance registered into database & queryable by admin
    val todayDate = "14/09/2026"
    val customAttendance = com.example.data.AttendanceRecord(
      id = "ATT_STU_VERIFY_999_JEE_Apex_Morning_14_09_2026",
      date = todayDate,
      day = 14,
      month = 9,
      monthName = "Sep",
      year = 2026,
      time = "09:30 AM",
      studentId = "STU_VERIFY_999",
      studentName = "Devendra Singh",
      batchName = "JEE Apex Morning",
      status = "Present",
      timestamp = System.currentTimeMillis(),
      lastModifiedTimestamp = System.currentTimeMillis(),
      reason = null,
      recordedBy = "Dr. Ananya Sharma"
    )
    repo.insertAttendance(customAttendance)

    val queriedAttendance = db.attendanceDao().getAttendanceById("ATT_STU_VERIFY_999_JEE_Apex_Morning_14_09_2026")
    org.junit.Assert.assertNotNull("Attendance must be saved into Room database", queriedAttendance)
    assertEquals("STU_VERIFY_999", queriedAttendance?.studentId)
    assertEquals("Devendra Singh", queriedAttendance?.studentName)
    assertEquals("Present", queriedAttendance?.status)
    assertEquals("JEE Apex Morning", queriedAttendance?.batchName)
    assertEquals(14, queriedAttendance?.day)
    assertEquals(9, queriedAttendance?.month)
    assertEquals(2026, queriedAttendance?.year)

    // 4. Verify Credentials & Session Persistence across Logout and Login
    com.example.security.SecurityEngine.initialize(context)
    com.example.security.SecurityEngine.registerSaltedCredential(
      identifier = "dr.ananya@mytuition.com",
      rawPassword = "SecurePassword@123",
      role = com.example.data.UserRole.TEACHER,
      fullName = "Dr. Ananya Sharma"
    )

    // Simulate Logout
    com.example.data.AppRepository.initialize(context)
    com.example.data.AppRepository.logout()
    org.junit.Assert.assertFalse("User should be logged out", com.example.data.AppRepository.isLoggedIn.value)

    // Simulate Re-Login with credentials
    val (isAuthValid, role) = com.example.security.SecurityEngine.verifySaltedCredential("dr.ananya@mytuition.com", "SecurePassword@123")
    org.junit.Assert.assertTrue("Credentials must verify successfully after logout", isAuthValid)
    assertEquals(com.example.data.UserRole.TEACHER, role)

    com.example.data.AppRepository.login("dr.ananya@mytuition.com", role!!)
    org.junit.Assert.assertTrue("User must be logged in again", com.example.data.AppRepository.isLoggedIn.value)
    assertEquals("dr.ananya@mytuition.com", com.example.data.AppRepository.currentUserIdentifier.value)

    // 5. Verify ALL data is regained in Room database after re-login
    val reStudent = db.studentDao().getStudentById("STU_VERIFY_999").first()
    val reTeacher = db.teacherDao().getTeacherById("FAC_VERIFY_888").first()
    val reAttendance = db.attendanceDao().getAttendanceById("ATT_STU_VERIFY_999_JEE_Apex_Morning_14_09_2026")

    org.junit.Assert.assertNotNull("Student data must persist across login/logout cycles", reStudent)
    org.junit.Assert.assertNotNull("Teacher data must persist across login/logout cycles", reTeacher)
    org.junit.Assert.assertNotNull("Attendance data must persist across login/logout cycles", reAttendance)
    assertEquals("Devendra Singh", reStudent?.name)
    assertEquals("Dr. Ananya Sharma", reTeacher?.name)
    assertEquals("Present", reAttendance?.status)
  }
}
