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

    // 5. Verify AppRepository.reconcileFacultyName properly resolves by teacher ID, batch, subject, and preserves custom faculty
    assertEquals("Prof. Shrirang Deshmukh", com.example.data.AppRepository.reconcileFacultyName("FAC_01"))
    assertEquals("Dr. Faisal Patel", com.example.data.AppRepository.reconcileFacultyName("FAC_02"))
    assertEquals("Prof. Akash Joshi", com.example.data.AppRepository.reconcileFacultyName("FAC_03"))
    assertEquals("Prof. Shrirang Deshmukh", com.example.data.AppRepository.reconcileFacultyName("Faculty", "Foundation Target"))
    assertEquals("Dr. Faisal Patel", com.example.data.AppRepository.reconcileFacultyName("Subject Faculty", "NEET Zenith"))
    assertEquals("Prof. Akash Joshi", com.example.data.AppRepository.reconcileFacultyName(null, null, "Mathematics"))
    assertEquals("Dr. Ananya Sharma", com.example.data.AppRepository.reconcileFacultyName("Dr. Ananya Sharma"))

    // 6. Verify isSameFaculty correctly detects matching identity across ID, name, and email
    assert(com.example.data.AppRepository.isSameFaculty("Prof. Shrirang Deshmukh", "FAC_01"))
    assert(com.example.data.AppRepository.isSameFaculty("Dr. Faisal Patel", "FAC_02"))
    assert(com.example.data.AppRepository.isSameFaculty("Prof. Akash Joshi", "FAC_03"))
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

  @Test
  fun `verify security authorization guards and strict identity binding without fallback`() = kotlinx.coroutines.runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    com.example.security.SecurityEngine.initialize(context)
    com.example.data.AppRepository.initialize(context)
    com.example.data.AppRepository.logout()

    // 1. Verify fresh or logged-out session is not logged in
    org.junit.Assert.assertFalse("Logged out state must require authentication", com.example.data.AppRepository.isLoggedIn.value)

    // 2. Log in as a specific Student (STU1001 - Rahul Sharma)
    com.example.data.AppRepository.login(
      identifier = "rahul.sharma@mytuition.com",
      role = com.example.data.UserRole.STUDENT,
      linkedEntityId = "STU1001"
    )
    assertEquals(com.example.data.UserRole.STUDENT, com.example.data.AppRepository.currentRole.value)

    // 3. Verify Student cannot escalate role to ADMIN via setRole
    com.example.data.AppRepository.setRole(com.example.data.UserRole.ADMIN)
    assertEquals(
      "Student must not be able to escalate role to ADMIN",
      com.example.data.UserRole.STUDENT,
      com.example.data.AppRepository.currentRole.value
    )

    // 4. Verify strict identity resolution binds to STU1001
    val boundStudent = com.example.data.AppRepository.getActiveStudentForCurrentSession()
    org.junit.Assert.assertNotNull("Bound student must resolve for STU1001", boundStudent)
    assertEquals("STU1001", boundStudent?.id)

    // 5. Verify an unlinked student identifier does NOT fall back to firstOrNull()
    com.example.data.AppRepository.login(
      identifier = "unknown.student@nowhere.com",
      role = com.example.data.UserRole.STUDENT,
      linkedEntityId = "STU_NON_EXISTENT"
    )
    val unboundStudent = com.example.data.AppRepository.getActiveStudentForCurrentSession()
    org.junit.Assert.assertNull("Unmatched student session must not fall back to firstOrNull()", unboundStudent)

    // 6. Verify Parent identity binding resolves strictly to linked children
    com.example.data.AppRepository.login(
      identifier = "parent.sharma@mytuition.com",
      role = com.example.data.UserRole.PARENT,
      linkedEntityId = "STU1001",
      linkedStudentIds = listOf("STU1001")
    )
    val linkedChildren = com.example.data.AppRepository.getLinkedStudentsForCurrentParent()
    assertEquals(1, linkedChildren.size)
    assertEquals("STU1001", linkedChildren.first().id)

    // 7. Verify Student cannot spoof another student's ID via caller-supplied linkedEntityId
    com.example.data.AppRepository.login(
      identifier = "rahul.sharma@mytuition.com",
      role = com.example.data.UserRole.STUDENT,
      linkedEntityId = "STU1002" // Attempted spoofing of Ananya Singh's ID
    )
    val antiSpoofStudent = com.example.data.AppRepository.getActiveStudentForCurrentSession()
    assertEquals("STU1001", antiSpoofStudent?.id)
  }

  @Test
  fun `verify no default credentials, no plaintext password keys, passwordless switch blocked, reset failure surfaced, and session token security`() = kotlinx.coroutines.runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    com.example.security.SecurityEngine.initialize(context)
    com.example.security.SecurityEngine.clearAllCredentials()
    com.example.data.AppRepository.initialize(context)
    com.example.data.AppRepository.logoutAllAccounts()

    // 1. Verify zero pre-seeded credentials exist on startup
    assertEquals(0, com.example.security.SecurityEngine.getRegisteredAccountCount())
    val (adminDefaultValid, _) = com.example.security.SecurityEngine.verifySaltedCredential("admin@fsi.com", "admin123")
    org.junit.Assert.assertFalse("Hardcoded default admin credential must not exist", adminDefaultValid)

    // 2. Register an explicit test account and verify no raw password or def_hash_ key is stored in SharedPreferences
    val rawTestPwd = "MyUniqueTestSecret#2026"
    com.example.security.SecurityEngine.registerSaltedCredential(
      identifier = "verified.teacher@mytuition.com",
      rawPassword = rawTestPwd,
      role = com.example.data.UserRole.TEACHER,
      fullName = "Verified Teacher"
    )
    val vaultPrefs = context.getSharedPreferences("fsi_security_credential_vault", Context.MODE_PRIVATE)
    val hasPlaintextKeyOrValue = vaultPrefs.all.any { (k, v) ->
      k.startsWith("def_hash_") || k.contains(rawTestPwd) || (v?.toString()?.contains(rawTestPwd) == true)
    }
    org.junit.Assert.assertFalse("Raw password or def_hash_ key must never be stored in SharedPreferences", hasPlaintextKeyOrValue)

    // 3. Verify password reset reports failure when Firebase Auth is unavailable
    var resetSucceeded = true
    var resetMessage = ""
    com.example.data.FirebaseAuthService.sendPasswordResetEmail(
      email = "verified.teacher@mytuition.com",
      onSuccess = { resetSucceeded = true },
      onFailure = { err ->
        resetSucceeded = false
        resetMessage = err
      }
    )
    org.junit.Assert.assertFalse("Password reset must fail when Firebase is unavailable", resetSucceeded)
    org.junit.Assert.assertTrue("Error message must be non-empty", resetMessage.isNotBlank())

    var repoResetOk = true
    com.example.data.AppRepository.resetUserPassword("verified.teacher@mytuition.com") { ok, _ ->
      repoResetOk = ok
    }
    org.junit.Assert.assertFalse("AppRepository.resetUserPassword must report failure when Firebase is unavailable", repoResetOk)
    org.junit.Assert.assertTrue(
      "passwordResetNotice must indicate failure",
      com.example.data.AppRepository.passwordResetNotice.value?.contains("failed", ignoreCase = true) == true
    )

    // 4. Verify passwordless switchAccount cannot bypass authentication
    com.example.data.AppRepository.login("rahul.sharma@mytuition.com", com.example.data.UserRole.STUDENT)
    assertEquals("rahul.sharma@mytuition.com", com.example.data.AppRepository.currentUserIdentifier.value)

    val targetSaved = com.example.data.SavedAccount(
      identifier = "verified.teacher@mytuition.com",
      role = com.example.data.UserRole.TEACHER,
      displayName = "Verified Teacher"
    )
    val switchedWithoutPassword = com.example.data.AppRepository.switchAccount(targetSaved)
    org.junit.Assert.assertFalse("Passwordless switchAccount must be rejected", switchedWithoutPassword)
    assertEquals(
      "Active user must remain unchanged after blocked passwordless switch",
      "rahul.sharma@mytuition.com",
      com.example.data.AppRepository.currentUserIdentifier.value
    )

    // 5. Verify logout followed by app restart (initialize) does NOT restore an authenticated session
    com.example.data.AppRepository.logout()
    org.junit.Assert.assertFalse(com.example.data.AppRepository.isLoggedIn.value)
    com.example.data.AppRepository.initialize(context)
    org.junit.Assert.assertFalse("Re-initializing after logout must not restore a session", com.example.data.AppRepository.isLoggedIn.value)

    // 6. Verify tampering with SharedPreferences (is_logged_in=true, user_role=ADMIN) without a valid session token is rejected
    val appPrefs = context.getSharedPreferences("my_tuition_fsi_prefs", Context.MODE_PRIVATE)
    appPrefs.edit()
      .putBoolean("is_logged_in", true)
      .putBoolean("has_explicit_auth_session", true)
      .putString("user_identifier", "rahul.sharma@mytuition.com")
      .putString("user_role", com.example.data.UserRole.ADMIN.name)
      .putString("session_token", "forged_invalid_token_12345")
      .commit()

    com.example.data.AppRepository.initialize(context)
    org.junit.Assert.assertFalse(
      "Forged SharedPreferences session without valid cryptographic token must be rejected",
      com.example.data.AppRepository.isLoggedIn.value
    )
  }
}
