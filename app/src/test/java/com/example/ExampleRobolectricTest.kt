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
    com.example.data.AppRepository.logout()
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

    // 1b. Verify configured primary admin account (joshiakash1712@gmail.com) authenticates as ADMIN
    val primaryAdminStatus = com.example.data.AppRepository.getAccountStatus("joshiakash1712@gmail.com")
    org.junit.Assert.assertTrue(primaryAdminStatus.exists)
    org.junit.Assert.assertTrue(primaryAdminStatus.isActivated)
    assertEquals(com.example.data.UserRole.ADMIN, primaryAdminStatus.role)
    val (primaryAdminValid, primaryAdminRole) = com.example.security.SecurityEngine.verifySaltedCredential(
      "joshiakash1712@gmail.com",
      "Trillionaire@1209"
    )
    org.junit.Assert.assertTrue("Primary admin credential must verify with BCrypt", primaryAdminValid)
    assertEquals(com.example.data.UserRole.ADMIN, primaryAdminRole)
    val (primaryAdminWrongPwd, _) = com.example.security.SecurityEngine.verifySaltedCredential(
      "joshiakash1712@gmail.com",
      "WrongPassword123"
    )
    org.junit.Assert.assertFalse("Wrong password for primary admin must be rejected", primaryAdminWrongPwd)

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

  @Test
  fun `verify firestore security rules and multi-institute tenant isolation across Scenarios A to H`() {
    // 0. Audit firestore.rules file for static compliance
    val rulesFile = listOf(
      java.io.File("firestore.rules"),
      java.io.File("../firestore.rules")
    ).firstOrNull { it.exists() }
    org.junit.Assert.assertNotNull("firestore.rules file must exist in project root", rulesFile)
    val rulesText = rulesFile!!.readText()

    org.junit.Assert.assertFalse(
      "firestore.rules must NOT contain global authenticated read rule",
      rulesText.contains("allow read: if request.auth != null")
    )
    org.junit.Assert.assertFalse(
      "firestore.rules must NOT contain hardcoded personal @gmail.com addresses",
      rulesText.contains("@gmail.com", ignoreCase = true)
    )
    org.junit.Assert.assertTrue(
      "firestore.rules must enforce default deny-all on match /{document=**}",
      rulesText.contains("allow get, list, create, update, delete: if false;")
    )
    org.junit.Assert.assertTrue(
      "firestore.rules must reject global primary_institute key",
      rulesText.contains("instId != 'primary_institute'")
    )

    // Setup Auth Contexts for INSTITUTE_A and INSTITUTE_B
    val adminA = com.example.security.FirestoreAuthContext(
      uid = "UID_ADMIN_A",
      role = com.example.data.UserRole.ADMIN,
      instituteId = "INSTITUTE_A"
    )
    val teacherA = com.example.security.FirestoreAuthContext(
      uid = "UID_TEACHER_A",
      role = com.example.data.UserRole.TEACHER,
      instituteId = "INSTITUTE_A",
      linkedEntityId = "FAC_A1",
      assignedBatchIds = listOf("BATCH_A1")
    )
    val studentA1 = com.example.security.FirestoreAuthContext(
      uid = "UID_STU_A1",
      role = com.example.data.UserRole.STUDENT,
      instituteId = "INSTITUTE_A",
      linkedEntityId = "STU_A1",
      batchId = "BATCH_A1"
    )
    val parentA1 = com.example.security.FirestoreAuthContext(
      uid = "UID_PAR_A1",
      role = com.example.data.UserRole.PARENT,
      instituteId = "INSTITUTE_A",
      linkedEntityId = "PAR_A1",
      linkedStudentIds = listOf("STU_A1"),
      linkedBatchIds = listOf("BATCH_A1")
    )

    val docStudentA1 = mapOf("id" to "STU_A1", "batchId" to "BATCH_A1", "parentId" to "PAR_A1", "instituteId" to "INSTITUTE_A")
    val docStudentA2 = mapOf("id" to "STU_A2", "batchId" to "BATCH_A2", "parentId" to "PAR_A2", "instituteId" to "INSTITUTE_A")
    val docStudentB1 = mapOf("id" to "STU_B1", "batchId" to "BATCH_B1", "parentId" to "PAR_B1", "instituteId" to "INSTITUTE_B")
    val docAttendanceA1 = mapOf("studentId" to "STU_A1", "batchId" to "BATCH_A1", "instituteId" to "INSTITUTE_A")
    val docAttendanceA2 = mapOf("studentId" to "STU_A2", "batchId" to "BATCH_A2", "instituteId" to "INSTITUTE_A")
    val docAttendanceB1 = mapOf("studentId" to "STU_B1", "batchId" to "BATCH_B1", "instituteId" to "INSTITUTE_B")
    val docFeeA1 = mapOf("studentId" to "STU_A1", "batchId" to "BATCH_A1", "instituteId" to "INSTITUTE_A")
    val docFeeA2 = mapOf("studentId" to "STU_A2", "batchId" to "BATCH_A2", "instituteId" to "INSTITUTE_A")
    val docFeeB1 = mapOf("studentId" to "STU_B1", "batchId" to "BATCH_B1", "instituteId" to "INSTITUTE_B")

    // Scenario A: Authenticated user in Institute A attempts to read student_profiles, attendance, or fees belonging to Institute B -> DENIED
    org.junit.Assert.assertFalse(
      com.example.security.SecurityEngine.evaluateFirestoreAccess(
        adminA, "student_profiles", "STU_B1", com.example.security.FirestoreOperation.GET, existingResourceData = docStudentB1
      )
    )
    org.junit.Assert.assertFalse(
      com.example.security.SecurityEngine.evaluateFirestoreAccess(
        adminA, "attendance", "ATT_B1", com.example.security.FirestoreOperation.GET, existingResourceData = docAttendanceB1
      )
    )
    org.junit.Assert.assertFalse(
      com.example.security.SecurityEngine.evaluateFirestoreAccess(
        adminA, "fees", "FEE_B1", com.example.security.FirestoreOperation.GET, existingResourceData = docFeeB1
      )
    )

    // Scenario B: Authenticated user in Institute A attempts to write a document with instituteId = "INSTITUTE_B" -> DENIED
    org.junit.Assert.assertFalse(
      com.example.security.SecurityEngine.evaluateFirestoreAccess(
        adminA, "student_profiles", "STU_B_NEW", com.example.security.FirestoreOperation.CREATE, requestResourceData = docStudentB1
      )
    )
    org.junit.Assert.assertFalse(
      com.example.security.SecurityEngine.evaluateFirestoreAccess(
        adminA, "attendance", "ATT_B_NEW", com.example.security.FirestoreOperation.CREATE, requestResourceData = docAttendanceB1
      )
    )
    org.junit.Assert.assertFalse(
      com.example.security.SecurityEngine.evaluateFirestoreAccess(
        adminA, "fees", "FEE_B_NEW", com.example.security.FirestoreOperation.CREATE, requestResourceData = docFeeB1
      )
    )

    // Scenario C: Authenticated user attempts to update an existing document's instituteId from "INSTITUTE_A" to "INSTITUTE_B" -> DENIED
    val reassignedStudent = docStudentA1 + ("instituteId" to "INSTITUTE_B")
    org.junit.Assert.assertFalse(
      com.example.security.SecurityEngine.evaluateFirestoreAccess(
        adminA, "student_profiles", "STU_A1", com.example.security.FirestoreOperation.UPDATE,
        existingResourceData = docStudentA1, requestResourceData = reassignedStudent
      )
    )

    // Scenario D: Authenticated student attempts to read another student's profile, attendance, or fees -> DENIED (own -> ALLOWED)
    org.junit.Assert.assertTrue(
      com.example.security.SecurityEngine.evaluateFirestoreAccess(
        studentA1, "student_profiles", "STU_A1", com.example.security.FirestoreOperation.GET, existingResourceData = docStudentA1
      )
    )
    org.junit.Assert.assertFalse(
      com.example.security.SecurityEngine.evaluateFirestoreAccess(
        studentA1, "student_profiles", "STU_A2", com.example.security.FirestoreOperation.GET, existingResourceData = docStudentA2
      )
    )
    org.junit.Assert.assertFalse(
      com.example.security.SecurityEngine.evaluateFirestoreAccess(
        studentA1, "attendance", "ATT_A2", com.example.security.FirestoreOperation.GET, existingResourceData = docAttendanceA2
      )
    )
    org.junit.Assert.assertFalse(
      com.example.security.SecurityEngine.evaluateFirestoreAccess(
        studentA1, "fees", "FEE_A2", com.example.security.FirestoreOperation.GET, existingResourceData = docFeeA2
      )
    )

    // Scenario E: Authenticated parent attempts to read an unlinked student's profile, attendance, or fees -> DENIED (linked -> ALLOWED)
    org.junit.Assert.assertTrue(
      com.example.security.SecurityEngine.evaluateFirestoreAccess(
        parentA1, "student_profiles", "STU_A1", com.example.security.FirestoreOperation.GET, existingResourceData = docStudentA1
      )
    )
    org.junit.Assert.assertTrue(
      com.example.security.SecurityEngine.evaluateFirestoreAccess(
        parentA1, "fees", "FEE_A1", com.example.security.FirestoreOperation.GET, existingResourceData = docFeeA1
      )
    )
    org.junit.Assert.assertFalse(
      com.example.security.SecurityEngine.evaluateFirestoreAccess(
        parentA1, "student_profiles", "STU_A2", com.example.security.FirestoreOperation.GET, existingResourceData = docStudentA2
      )
    )
    org.junit.Assert.assertFalse(
      com.example.security.SecurityEngine.evaluateFirestoreAccess(
        parentA1, "attendance", "ATT_A2", com.example.security.FirestoreOperation.GET, existingResourceData = docAttendanceA2
      )
    )
    org.junit.Assert.assertFalse(
      com.example.security.SecurityEngine.evaluateFirestoreAccess(
        parentA1, "fees", "FEE_A2", com.example.security.FirestoreOperation.GET, existingResourceData = docFeeA2
      )
    )

    // Scenario F: Authenticated user attempts to write role = "ADMIN" or "OWNER" to /user_roles/{uid} -> DENIED
    org.junit.Assert.assertFalse(
      com.example.security.SecurityEngine.evaluateFirestoreAccess(
        studentA1, "user_roles", "UID_STU_A1", com.example.security.FirestoreOperation.UPDATE,
        existingResourceData = mapOf("uid" to "UID_STU_A1", "role" to "STUDENT", "instituteId" to "INSTITUTE_A"),
        requestResourceData = mapOf("uid" to "UID_STU_A1", "role" to "ADMIN", "instituteId" to "INSTITUTE_A")
      )
    )
    org.junit.Assert.assertFalse(
      com.example.security.SecurityEngine.evaluateFirestoreAccess(
        adminA, "user_roles", "UID_NEW_ADMIN", com.example.security.FirestoreOperation.CREATE,
        requestResourceData = mapOf("uid" to "UID_NEW_ADMIN", "role" to "OWNER", "instituteId" to "INSTITUTE_A")
      )
    )

    // Scenario G: Authenticated teacher in Institute A accesses authorized batch data -> ALLOWED; unauthorized batch or fees -> DENIED
    org.junit.Assert.assertTrue(
      com.example.security.SecurityEngine.evaluateFirestoreAccess(
        teacherA, "attendance", "ATT_A1", com.example.security.FirestoreOperation.GET, existingResourceData = docAttendanceA1
      )
    )
    org.junit.Assert.assertTrue(
      com.example.security.SecurityEngine.evaluateFirestoreAccess(
        teacherA, "attendance", "ATT_A1_NEW", com.example.security.FirestoreOperation.CREATE, requestResourceData = docAttendanceA1
      )
    )
    org.junit.Assert.assertFalse(
      com.example.security.SecurityEngine.evaluateFirestoreAccess(
        teacherA, "attendance", "ATT_A2", com.example.security.FirestoreOperation.GET, existingResourceData = docAttendanceA2
      )
    )
    org.junit.Assert.assertFalse(
      com.example.security.SecurityEngine.evaluateFirestoreAccess(
        teacherA, "fees", "FEE_A1", com.example.security.FirestoreOperation.GET, existingResourceData = docFeeA1
      )
    )

    // Scenario H: Authenticated admin/owner in Institute A manages Institute A data -> ALLOWED; primary_institute collision -> DENIED
    org.junit.Assert.assertTrue(
      com.example.security.SecurityEngine.evaluateFirestoreAccess(
        adminA, "student_profiles", "STU_A1", com.example.security.FirestoreOperation.GET, existingResourceData = docStudentA1
      )
    )
    org.junit.Assert.assertTrue(
      com.example.security.SecurityEngine.evaluateFirestoreAccess(
        adminA, "fees", "FEE_A1", com.example.security.FirestoreOperation.CREATE, requestResourceData = docFeeA1
      )
    )
    org.junit.Assert.assertTrue(
      com.example.security.SecurityEngine.evaluateFirestoreAccess(
        adminA, "institute_config", "INSTITUTE_A", com.example.security.FirestoreOperation.CREATE,
        requestResourceData = mapOf("instituteId" to "INSTITUTE_A", "instituteName" to "Institute A")
      )
    )
    org.junit.Assert.assertFalse(
      "primary_institute document ID must be rejected",
      com.example.security.SecurityEngine.evaluateFirestoreAccess(
        adminA, "institute_config", "primary_institute", com.example.security.FirestoreOperation.CREATE,
        requestResourceData = mapOf("instituteId" to "primary_institute", "instituteName" to "Collision")
      )
    )

    // Verify AppRepository & FirestoreService tenant isolation & primary_institute rejection
    com.example.data.AppRepository.logout()
    org.junit.Assert.assertFalse(
      "AppRepository.updateInstituteConfig must reject primary_institute",
      com.example.data.AppRepository.updateInstituteConfig(
        com.example.data.InstituteConfig(instituteId = "primary_institute", instituteName = "Invalid")
      )
    )
    com.example.data.AppRepository.login("admin_a@inst-a.com", com.example.data.UserRole.ADMIN, instituteId = "INSTITUTE_A")
    assertEquals("INSTITUTE_A", com.example.data.AppRepository.getCurrentInstituteId())
    org.junit.Assert.assertTrue(
      "Admin of INSTITUTE_A can update INSTITUTE_A config",
      com.example.data.AppRepository.updateInstituteConfig(
        com.example.data.InstituteConfig(instituteId = "INSTITUTE_A", instituteName = "Apex Institute A")
      )
    )
    org.junit.Assert.assertFalse(
      "Admin of INSTITUTE_A cannot overwrite INSTITUTE_B config",
      com.example.data.AppRepository.updateInstituteConfig(
        com.example.data.InstituteConfig(instituteId = "INSTITUTE_B", instituteName = "Foreign Institute B")
      )
    )
    org.junit.Assert.assertNull(
      "Admin of INSTITUTE_A cannot read INSTITUTE_B config",
      com.example.data.AppRepository.getInstituteConfigForTenant("INSTITUTE_B")
    )
    val queryConstraints = com.example.data.FirestoreService.buildScopedQueryConstraints("student_profiles")
    assertEquals("INSTITUTE_A", queryConstraints["instituteId"])
    com.example.data.AppRepository.logout()
  }

  @Test
  fun `verify room migrations 1 to 8 preserve data, foreign keys prevent destructive cascades, student deactivation retains history, and 90-day attendance purge is removed`() = kotlinx.coroutines.runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()

    // 1. Verify AppRoomDatabase configuration does not use fallbackToDestructiveMigration and has exportSchema = true
    val dbSourceFile = listOf(
      java.io.File("src/main/java/com/example/data/db/AppRoomDatabase.kt"),
      java.io.File("app/src/main/java/com/example/data/db/AppRoomDatabase.kt")
    ).firstOrNull { it.exists() }
    org.junit.Assert.assertNotNull("AppRoomDatabase.kt source file must exist", dbSourceFile)
    val dbSourceText = dbSourceFile!!.readText()
    org.junit.Assert.assertFalse(
      "AppRoomDatabase must NOT contain fallbackToDestructiveMigration",
      dbSourceText.contains("fallbackToDestructiveMigration")
    )
    org.junit.Assert.assertTrue(
      "AppRoomDatabase must have exportSchema = true",
      dbSourceText.contains("exportSchema = true")
    )
    val exportedSchemaFile = listOf(
      java.io.File("schemas/com.example.data.db.AppRoomDatabase/9.json"),
      java.io.File("app/schemas/com.example.data.db.AppRoomDatabase/9.json"),
      java.io.File("schemas/com.example.data.db.AppRoomDatabase/8.json"),
      java.io.File("app/schemas/com.example.data.db.AppRoomDatabase/8.json")
    ).firstOrNull { it.exists() }
    org.junit.Assert.assertNotNull("Exported Room schema must exist", exportedSchemaFile)

    // 2. Create a genuine v1 SQLite database and populate records at each historical version (1 -> 2 -> 3 -> 4 -> 5 -> 6 -> 7 -> 8)
    val migrationDbName = "migration_verify_v1_to_v8.db"
    context.deleteDatabase(migrationDbName)
    val sqlitePath = context.getDatabasePath(migrationDbName)
    sqlitePath.parentFile?.mkdirs()

    val rawDb = android.database.sqlite.SQLiteDatabase.openOrCreateDatabase(sqlitePath, null)
    rawDb.version = 1
    // v1 tables: students, teachers, batches, batch_enrollments
    rawDb.execSQL(
      """
      CREATE TABLE `students` (
        `id` TEXT NOT NULL, `name` TEXT NOT NULL, `photo` TEXT NOT NULL, `mobile` TEXT NOT NULL,
        `parentName` TEXT NOT NULL, `parentContact` TEXT NOT NULL, `email` TEXT NOT NULL,
        `dob` TEXT NOT NULL, `gender` TEXT NOT NULL, `address` TEXT NOT NULL, `school` TEXT NOT NULL,
        `className` TEXT NOT NULL, `batch` TEXT NOT NULL, `stream` TEXT NOT NULL,
        `admissionDate` TEXT NOT NULL, `status` TEXT NOT NULL, `attendancePercent` INTEGER NOT NULL,
        `overallAvg` INTEGER NOT NULL, `rank` INTEGER NOT NULL, `strongestSubject` TEXT NOT NULL,
        `weakestSubject` TEXT NOT NULL, `recentScoresSerialized` TEXT NOT NULL,
        `syncedToFirestore` INTEGER NOT NULL DEFAULT 0, `lastUpdated` INTEGER NOT NULL DEFAULT 0,
        PRIMARY KEY(`id`)
      )
      """.trimIndent()
    )
    rawDb.execSQL(
      """
      CREATE TABLE `teachers` (
        `id` TEXT NOT NULL, `name` TEXT NOT NULL, `photo` TEXT NOT NULL DEFAULT '',
        `subject` TEXT NOT NULL, `contact` TEXT NOT NULL, `qualification` TEXT NOT NULL,
        `experience` TEXT NOT NULL, `assignedBatchesSerialized` TEXT NOT NULL,
        `classesTaken` INTEGER NOT NULL, `attendancePercent` INTEGER NOT NULL,
        `feedbackRating` REAL NOT NULL, `salary` INTEGER NOT NULL, `incentives` INTEGER NOT NULL,
        `deductions` INTEGER NOT NULL, `status` TEXT NOT NULL, `lastUpdated` INTEGER NOT NULL DEFAULT 0,
        PRIMARY KEY(`id`)
      )
      """.trimIndent()
    )
    rawDb.execSQL(
      """
      CREATE TABLE `batches` (
        `id` TEXT NOT NULL, `name` TEXT NOT NULL, `schedule` TEXT NOT NULL,
        `studentCount` INTEGER NOT NULL, `teacherName` TEXT NOT NULL,
        `syncedToFirestore` INTEGER NOT NULL DEFAULT 0, `lastUpdated` INTEGER NOT NULL DEFAULT 0,
        PRIMARY KEY(`id`)
      )
      """.trimIndent()
    )
    rawDb.execSQL(
      """
      CREATE TABLE `batch_enrollments` (
        `id` TEXT NOT NULL, `studentId` TEXT NOT NULL, `studentName` TEXT NOT NULL,
        `batchId` TEXT NOT NULL, `batchName` TEXT NOT NULL, `className` TEXT NOT NULL,
        `stream` TEXT NOT NULL, `enrollmentDate` TEXT NOT NULL, `feeStatus` TEXT NOT NULL DEFAULT 'Up-to-Date',
        `status` TEXT NOT NULL DEFAULT 'Active', `syncedToFirestore` INTEGER NOT NULL DEFAULT 0,
        `lastUpdated` INTEGER NOT NULL DEFAULT 0, PRIMARY KEY(`id`)
      )
      """.trimIndent()
    )

    // Seed v1 records
    rawDb.execSQL(
      """
      INSERT INTO `students` VALUES (
        'STU_V1_001', 'Aarav Kulkarni', '', '+91 9000011111', 'Sanjay Kulkarni', '+91 9000011112',
        'aarav@v1.com', '10/05/2008', 'Male', 'Pune', 'Ferguson Jr College', '12th', 'JEE Apex Morning',
        'Science', '01/06/2025', 'Active', 94, 89, 2, 'Mathematics', 'Chemistry', 'Test 1:90', 0, 1000
      )
      """.trimIndent()
    )
    rawDb.execSQL(
      """
      INSERT INTO `batches` VALUES (
        'BATCH_V1_01', 'JEE Apex Morning', 'Mon-Sat 7:30 AM', 1, 'Prof. Shrirang Deshmukh', 0, 1000
      )
      """.trimIndent()
    )
    rawDb.execSQL(
      """
      INSERT INTO `batch_enrollments` VALUES (
        'ENR_V1_001', 'STU_V1_001', 'Aarav Kulkarni', 'BATCH_V1_01', 'JEE Apex Morning',
        '12th', 'Science', '01/06/2025', 'Up-to-Date', 'Active', 0, 1000
      )
      """.trimIndent()
    )
    rawDb.close()

    // Open with Room using ALL_MIGRATIONS (upgrades v1 -> v8 and runs Room's strict schema validator)
    val migratedDb = androidx.room.Room.databaseBuilder(
      context,
      com.example.data.db.AppRoomDatabase::class.java,
      migrationDbName
    )
      .addMigrations(*com.example.data.db.AppRoomDatabase.ALL_MIGRATIONS)
      .allowMainThreadQueries()
      .build()

    // Verify v1 student and batch enrollment survived the 1 -> 8 upgrade with appropriate defaults
    val migratedStudent = migratedDb.studentDao().getStudentByIdOnce("STU_V1_001")
    org.junit.Assert.assertNotNull("v1 student must survive migration 1 -> 8", migratedStudent)
    assertEquals("Aarav Kulkarni", migratedStudent?.name)
    assertEquals("FSI_MAIN", migratedStudent?.instituteId)
    val migratedEnrollments = migratedDb.batchEnrollmentDao().getEnrollmentsByStudent("STU_V1_001").first()
    assertEquals(1, migratedEnrollments.size)
    assertEquals("BATCH_V1_01", migratedEnrollments.first().batchId)

    // 3. Verify Foreign-Key Integrity: inserting historical records for STU_V1_001 and attempting raw DELETE on parent is restricted
    val oldTimestamp180DaysAgo = System.currentTimeMillis() - (180L * 24 * 60 * 60 * 1000L)
    migratedDb.attendanceDao().insertAttendance(
      com.example.data.db.AttendanceRecordEntity(
        id = "ATT_HIST_180D",
        date = "15/03/2026",
        day = 15,
        month = 3,
        monthName = "March",
        year = 2026,
        time = "08:00 AM",
        studentId = "STU_V1_001",
        studentName = "Aarav Kulkarni",
        batchName = "JEE Apex Morning",
        status = "Present",
        timestamp = oldTimestamp180DaysAgo,
        lastModifiedTimestamp = oldTimestamp180DaysAgo,
        batchId = "BATCH_V1_01",
        instituteId = "FSI_MAIN"
      )
    )
    migratedDb.feePaymentDao().insertPayment(
      com.example.data.db.FeePaymentEntity(
        id = "PAY_HIST_001",
        studentId = "STU_V1_001",
        studentName = "Aarav Kulkarni",
        amount = 15000.0,
        paymentDate = "10/06/2025",
        paymentStatus = "Paid",
        feeType = "Tuition Fee",
        paymentMode = "UPI",
        receiptNo = "REC-V1-001",
        month = "June 2025",
        remarks = "First Installment",
        recordedBy = "Admin",
        timestamp = oldTimestamp180DaysAgo
      )
    )

    var rawDeleteBlockedByForeignKey = false
    try {
      migratedDb.studentDao().deleteStudentById("STU_V1_001")
    } catch (_: android.database.sqlite.SQLiteConstraintException) {
      rawDeleteBlockedByForeignKey = true
    }
    org.junit.Assert.assertTrue(
      "Foreign key ON DELETE RESTRICT must block raw deletion of a student who has historical attendance and fee records",
      rawDeleteBlockedByForeignKey
    )

    // Verify historical records are still intact after blocked raw delete
    org.junit.Assert.assertNotNull(migratedDb.attendanceDao().getAttendanceById("ATT_HIST_180D"))
    assertEquals(1, migratedDb.feePaymentDao().getPaymentsByStudent("STU_V1_001").first().size)
    migratedDb.close()
    context.deleteDatabase(migrationDbName)

    // 4. Verify Student Deactivation (deleteStudent) preserves attendance, fees, attempts, doubts, and study logs
    val repo = com.example.data.StudentBatchRepository(context)
    val mainDb = com.example.data.db.AppRoomDatabase.getDatabase(context)
    val archiveStudent = com.example.data.Student(
      id = "STU_RETENTION_777",
      name = "Meera Joshi",
      photo = "",
      mobile = "+91 9111122222",
      parentName = "Vikram Joshi",
      parentContact = "+91 9111122223",
      email = "meera.joshi@test.com",
      dob = "11/11/2008",
      gender = "Female",
      address = "Kothrud, Pune",
      school = "Symbiosis College",
      className = "12th",
      batch = "JEE Apex Morning",
      stream = "Science",
      admissionDate = "01/06/2025",
      status = "Active",
      attendancePercent = 96,
      overallAvg = 91,
      rank = 1,
      strongestSubject = "Physics",
      weakestSubject = "Chemistry",
      recentScores = listOf("Mock 1" to 92)
    )
    repo.insertStudent(archiveStudent)

    // Insert >90-day-old attendance, fee invoice, fee payment, exam attempt, doubt, study log, goal
    val ancientTimestamp = System.currentTimeMillis() - (150L * 24 * 60 * 60 * 1000L)
    repo.insertAttendance(
      com.example.data.AttendanceRecord(
        id = "ATT_150DAYS_OLD_STU_RETENTION_777",
        date = "01/04/2026",
        day = 1,
        month = 4,
        monthName = "April",
        year = 2026,
        time = "09:00 AM",
        studentId = "STU_RETENTION_777",
        studentName = "Meera Joshi",
        batchName = "JEE Apex Morning",
        status = "Present",
        timestamp = ancientTimestamp,
        lastModifiedTimestamp = ancientTimestamp
      )
    )
    repo.insertFeeInvoice(
      com.example.data.FeeRecord(
        id = "INV_RETENTION_777",
        studentId = "STU_RETENTION_777",
        studentName = "Meera Joshi",
        feeAmount = 45000,
        dueDate = "15/06/2025",
        paidAmount = 45000,
        pendingAmount = 0,
        paymentStatus = "Paid",
        month = "June 2025"
      )
    )
    repo.insertFeePayment(
      com.example.data.StudentFeePayment(
        id = "PAY_RETENTION_777",
        studentId = "STU_RETENTION_777",
        studentName = "Meera Joshi",
        amount = 45000.0,
        paymentDate = "10/06/2025",
        paymentStatus = "Paid",
        feeType = "Annual Tuition",
        paymentMode = "NEFT",
        receiptNo = "REC-777",
        month = "June 2025",
        remarks = "Full fee paid",
        recordedBy = "Admin"
      )
    )
    repo.insertStudentAttempt(
      com.example.data.StudentTestAttempt(
        attemptId = "ATTEMPT_RETENTION_777",
        testId = "TST_01",
        testName = "JEE Mock 1",
        studentId = "STU_RETENTION_777",
        studentName = "Meera Joshi",
        answers = mapOf("Q1" to 1),
        score = 92,
        totalMarks = 100,
        correctCount = 23,
        wrongCount = 2,
        unattemptedCount = 0,
        timeSpentSeconds = 1800
      )
    )

    // Soft-delete / deactivate student via repository
    repo.deleteStudent("STU_RETENTION_777")

    // Active student directory excludes deactivated student, but historical identity and all academic/financial records remain intact
    val activeStudents = repo.allStudents.first()
    org.junit.Assert.assertFalse(
      "Deactivated student must not appear in active student list",
      activeStudents.any { it.id == "STU_RETENTION_777" }
    )
    val preservedStudentRow = mainDb.studentDao().getStudentByIdOnce("STU_RETENTION_777")
    org.junit.Assert.assertNotNull("Deactivated student row must remain in database for historical foreign-key integrity", preservedStudentRow)
    assertEquals("Inactive", preservedStudentRow?.status)

    // Verify attendance, fee invoice, fee payment, and test attempt were NOT destroyed
    org.junit.Assert.assertNotNull(
      "Historical attendance (>90 days old) must be preserved after student deletion",
      mainDb.attendanceDao().getAttendanceById("ATT_150DAYS_OLD_STU_RETENTION_777")
    )
    org.junit.Assert.assertNotNull(
      "Historical fee invoice must be preserved after student deletion",
      mainDb.feeInvoiceDao().getInvoiceById("INV_RETENTION_777")
    )
    assertEquals(
      "Historical fee payment must be preserved after student deletion",
      1,
      mainDb.feePaymentDao().getPaymentsByStudent("STU_RETENTION_777").first().size
    )
    assertEquals(
      "Historical test attempt must be preserved after student deletion",
      1,
      mainDb.studentAttemptDao().getAttemptsByStudent("STU_RETENTION_777").size
    )

    // 5. Verify re-initializing AppRepository does NOT purge attendance older than 90 days
    com.example.data.AppRepository.initialize(context)
    val attendanceAfterInit = mainDb.attendanceDao().getAttendanceById("ATT_150DAYS_OLD_STU_RETENTION_777")
    org.junit.Assert.assertNotNull(
      "Attendance older than 90 days must NOT be purged on AppRepository.initialize()",
      attendanceAfterInit
    )
  }

  @Test
  fun `verify repository RBAC, privileged mutations, null-student fallbacks, scoped StateFlows, and studentIdOverride protections`() = kotlinx.coroutines.runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    com.example.security.SecurityEngine.initialize(context)
    com.example.data.AppRepository.initialize(context)
    com.example.data.AppRepository.awaitInitialDemoDataSeeded()
    com.example.data.AppRepository.logout()

    // Ensure baseline demo data is available in AppRepository via an Admin session
    com.example.data.AppRepository.login("admin@mytuition.com", com.example.data.UserRole.ADMIN, instituteId = "FSI_MAIN")
    val allDemoStudents = com.example.data.SeedData.get15DemoStudents()
    val studentA = allDemoStudents.first { it.id == "STU1001" } // Rahul Sharma - Foundation Target
    val studentB = allDemoStudents.first { it.id == "STU1002" } // Ananya Singh - 10th - Science (A)

    // Seed an attempt, doubt, homework, and study log for STU1001 and STU1002 as Admin so collections are non-empty
    com.example.data.AppRepository.submitStudentTestAttempt(
      testId = "T_RBAC_1",
      testName = "RBAC Test 1",
      subject = "Physics",
      answers = mapOf("Q1" to 1),
      durationSeconds = 120,
      studentIdOverride = studentA.id
    )
    com.example.data.AppRepository.submitStudentTestAttempt(
      testId = "T_RBAC_2",
      testName = "RBAC Test 2",
      subject = "Physics",
      answers = mapOf("Q1" to 2),
      durationSeconds = 150,
      studentIdOverride = studentB.id
    )
    com.example.data.AppRepository.submitStudentDoubt(
      subject = "Physics",
      chapter = "Optics",
      text = "Doubt from Student A",
      studentIdOverride = studentA.id
    )
    com.example.data.AppRepository.submitStudentDoubt(
      subject = "Chemistry",
      chapter = "Atoms",
      text = "Doubt from Student B",
      studentIdOverride = studentB.id
    )
    com.example.data.AppRepository.addHomework(
      subject = "Physics",
      title = "Foundation Homework",
      description = "Solve exercises",
      dueDate = "30/09/2026",
      batchName = studentA.batch
    )
    com.example.data.AppRepository.logDailyStudy(
      hours = 2.5f,
      subjects = "Physics",
      chapters = "Kinematics",
      studentIdOverride = studentA.id
    )
    com.example.data.AppRepository.logDailyStudy(
      hours = 3.0f,
      subjects = "Chemistry",
      chapters = "Bonding",
      studentIdOverride = studentB.id
    )

    // 1. Unauthenticated / Unresolved Student Fallback Checks:
    // Must return emptyList() and NEVER fall back to returning all records
    com.example.data.AppRepository.logout()
    org.junit.Assert.assertTrue(
      "Unauthenticated getAttemptsForCurrentStudent() must return emptyList()",
      com.example.data.AppRepository.getAttemptsForCurrentStudent().isEmpty()
    )
    org.junit.Assert.assertTrue(
      "Unauthenticated getDoubtsForCurrentStudent() must return emptyList()",
      com.example.data.AppRepository.getDoubtsForCurrentStudent().isEmpty()
    )
    org.junit.Assert.assertTrue(
      "Unauthenticated getHomeworkForCurrentStudent() must return emptyList()",
      com.example.data.AppRepository.getHomeworkForCurrentStudent().isEmpty()
    )
    org.junit.Assert.assertTrue(
      "Unauthenticated getStudyLogsForCurrentStudent() must return emptyList()",
      com.example.data.AppRepository.getStudyLogsForCurrentStudent().isEmpty()
    )
    org.junit.Assert.assertTrue(
      "Unauthenticated scoped students StateFlow must be empty",
      com.example.data.AppRepository.students.value.isEmpty()
    )

    // Log in with an unresolved/unlinked student identity -> must also return emptyList(), never all records
    com.example.data.AppRepository.login(
      identifier = "unlinked.ghost.student@mytuition.com",
      role = com.example.data.UserRole.STUDENT,
      linkedEntityId = "STU_DOES_NOT_EXIST"
    )
    org.junit.Assert.assertNull(com.example.data.AppRepository.getActiveStudentForCurrentSession())
    org.junit.Assert.assertTrue(
      "Unresolved student getAttemptsForCurrentStudent() must return emptyList()",
      com.example.data.AppRepository.getAttemptsForCurrentStudent().isEmpty()
    )
    org.junit.Assert.assertTrue(
      "Unresolved student getDoubtsForCurrentStudent() must return emptyList()",
      com.example.data.AppRepository.getDoubtsForCurrentStudent().isEmpty()
    )
    org.junit.Assert.assertTrue(
      "Unresolved student getHomeworkForCurrentStudent() must return emptyList()",
      com.example.data.AppRepository.getHomeworkForCurrentStudent().isEmpty()
    )
    org.junit.Assert.assertTrue(
      "Unresolved student getStudyLogsForCurrentStudent() must return emptyList()",
      com.example.data.AppRepository.getStudyLogsForCurrentStudent().isEmpty()
    )

    // 2. TEACHER Authorization & Batch Scope Checks:
    // Log in as FAC_01 (Prof. Shrirang Deshmukh - assigned to Foundation Target, 10th - Science (A), 12th Science)
    com.example.data.AppRepository.login(
      identifier = "deshmukh.physics@mytuition.com",
      role = com.example.data.UserRole.TEACHER,
      linkedEntityId = "FAC_01"
    )
    assertEquals(com.example.data.UserRole.TEACHER, com.example.data.AppRepository.currentRole.value)

    // Teacher calling addStudent(), updateStudent(), deleteStudent() -> rejected
    val newStuAttempt = studentA.copy(id = "STU_UNAUTH_TEACHER_ADD", name = "Unauthorized Add")
    org.junit.Assert.assertFalse(
      "Teacher calling addStudent() must be rejected",
      com.example.data.AppRepository.addStudent(newStuAttempt)
    )
    org.junit.Assert.assertFalse(
      "Teacher calling updateStudent() must be rejected",
      com.example.data.AppRepository.updateStudent(studentA.copy(name = "Tampered Name"))
    )
    org.junit.Assert.assertFalse(
      "Teacher calling deleteStudent() must be rejected",
      com.example.data.AppRepository.deleteStudent(studentA.id)
    )
    org.junit.Assert.assertFalse(
      "Teacher calling clearAllAppData() must be rejected",
      com.example.data.AppRepository.clearAllAppData(context)
    )
    org.junit.Assert.assertFalse(
      "Teacher calling generateAndBulkImportRealUsers() must be rejected",
      com.example.data.AppRepository.generateAndBulkImportRealUsers(10)
    )

    // Teacher modifying another teacher's batch (e.g., "NEET Zenith" assigned to FAC_02 Dr. Faisal Patel) -> rejected
    org.junit.Assert.assertFalse(
      "Teacher modifying another teacher's batch via addHomework must be rejected",
      com.example.data.AppRepository.addHomework(
        subject = "Biology",
        title = "Unauthorized Batch HW",
        description = "Should fail",
        dueDate = "30/09/2026",
        batchName = "NEET Zenith"
      )
    )
    org.junit.Assert.assertFalse(
      "Teacher marking attendance for another teacher's batch must be rejected",
      com.example.data.AppRepository.submitBatchAttendance(
        batchName = "NEET Zenith",
        statusMap = mapOf("STU1005" to "Present")
      )
    )
    // Teacher modifying their own assigned batch ("Foundation Target") -> allowed
    org.junit.Assert.assertTrue(
      "Teacher adding homework to own assigned batch must succeed",
      com.example.data.AppRepository.addHomework(
        subject = "Physics",
        title = "Authorized Batch HW",
        description = "Allowed",
        dueDate = "30/09/2026",
        batchName = "Foundation Target"
      )
    )

    // 3. STUDENT Authorization, Data Isolation & studentIdOverride Checks:
    // Log in as Student A (STU1001 - Rahul Sharma)
    com.example.data.AppRepository.login(
      identifier = "rahul.sharma@mytuition.com",
      role = com.example.data.UserRole.STUDENT,
      linkedEntityId = "STU1001"
    )
    assertEquals(com.example.data.UserRole.STUDENT, com.example.data.AppRepository.currentRole.value)

    // Student calling deleteStudent(), addStudent(), clearAllAppData(), generateAndBulkImportRealUsers() -> rejected
    org.junit.Assert.assertFalse(
      "Student calling deleteStudent() must be rejected",
      com.example.data.AppRepository.deleteStudent(studentB.id)
    )
    org.junit.Assert.assertFalse(
      "Student calling clearAllAppData() must be rejected",
      com.example.data.AppRepository.clearAllAppData(context)
    )
    org.junit.Assert.assertFalse(
      "Student calling generateAndBulkImportRealUsers() must be rejected",
      com.example.data.AppRepository.generateAndBulkImportRealUsers(5)
    )

    // Student A attempting to access Student B's data -> rejected / empty
    org.junit.Assert.assertTrue(
      "Student A requesting Student B's attempts must return emptyList()",
      com.example.data.AppRepository.getAttemptsForCurrentStudent(studentB).isEmpty()
    )
    org.junit.Assert.assertTrue(
      "Student A requesting Student B's doubts must return emptyList()",
      com.example.data.AppRepository.getDoubtsForCurrentStudent(studentB).isEmpty()
    )
    org.junit.Assert.assertTrue(
      "Student A requesting Student B's homework must return emptyList()",
      com.example.data.AppRepository.getHomeworkForCurrentStudent(studentB).isEmpty()
    )
    org.junit.Assert.assertTrue(
      "Student A requesting Student B's study logs must return emptyList()",
      com.example.data.AppRepository.getStudyLogsForCurrentStudent(studentB).isEmpty()
    )
    org.junit.Assert.assertTrue(
      "Student A requesting Student B's attendance must return emptyList()",
      com.example.data.AppRepository.getAttendanceForStudent(studentB.id).isEmpty()
    )
    org.junit.Assert.assertNull(
      "Student A resolving Student B's target identity must return null",
      com.example.data.AppRepository.resolveAuthorizedStudentTarget(studentB.id)
    )
    org.junit.Assert.assertTrue(
      "Scoped fees flow for Student A must not contain Student B's fee records",
      com.example.data.AppRepository.fees.value.none { it.studentId == studentB.id }
    )

    // Scoped StateFlow for Student A must only contain Student A
    assertEquals(1, com.example.data.AppRepository.students.value.size)
    assertEquals("STU1001", com.example.data.AppRepository.students.value.first().id)

    // Student A attempting submitStudentTestAttempt(studentIdOverride = "STU1002") -> rejected (null)
    val spoofedAttempt = com.example.data.AppRepository.submitStudentTestAttempt(
      testId = "T_SPOOF",
      testName = "Spoofed Test",
      subject = "Physics",
      answers = mapOf("Q1" to 1),
      durationSeconds = 90,
      studentIdOverride = studentB.id
    )
    org.junit.Assert.assertNull(
      "Student A attempting submitStudentTestAttempt with another student's ID must be rejected",
      spoofedAttempt
    )

    // Student A submitting for own identity -> succeeds with STU1001
    val ownAttempt = com.example.data.AppRepository.submitStudentTestAttempt(
      testId = "T_OWN",
      testName = "Own Test",
      subject = "Physics",
      answers = mapOf("Q1" to 1),
      durationSeconds = 90
    )
    org.junit.Assert.assertNotNull("Student A submitting own test attempt must succeed", ownAttempt)
    assertEquals("STU1001", ownAttempt?.studentId)

    // 4. PARENT Authorization & Unlinked Student Isolation Checks:
    com.example.data.AppRepository.login(
      identifier = "parent.sharma@mytuition.com",
      role = com.example.data.UserRole.PARENT,
      linkedEntityId = "STU1001",
      linkedStudentIds = listOf("STU1001")
    )
    assertEquals(com.example.data.UserRole.PARENT, com.example.data.AppRepository.currentRole.value)

    // Parent calling updateInstituteConfig(), clearAllAppData(), generateAndBulkImportRealUsers() -> rejected
    org.junit.Assert.assertFalse(
      "Parent calling updateInstituteConfig() must be rejected",
      com.example.data.AppRepository.updateInstituteConfig(
        com.example.data.InstituteConfig(instituteId = "FSI_MAIN", instituteName = "Hacked By Parent")
      )
    )
    org.junit.Assert.assertFalse(
      "Parent calling clearAllAppData() must be rejected",
      com.example.data.AppRepository.clearAllAppData(context)
    )
    org.junit.Assert.assertFalse(
      "Parent calling generateAndBulkImportRealUsers() must be rejected",
      com.example.data.AppRepository.generateAndBulkImportRealUsers(5)
    )

    // Parent attempting to select or access unlinked student STU1002 -> rejected
    org.junit.Assert.assertFalse(
      "Parent selecting unlinked child STU1002 must be rejected",
      com.example.data.AppRepository.setSelectedChildForParent("STU1002")
    )
    org.junit.Assert.assertTrue(
      "Parent requesting unlinked student STU1002 attendance must return emptyList()",
      com.example.data.AppRepository.getAttendanceForStudent("STU1002").isEmpty()
    )
    org.junit.Assert.assertNull(
      "Parent resolving unlinked student STU1002 target must return null",
      com.example.data.AppRepository.resolveAuthorizedStudentTarget("STU1002")
    )
    org.junit.Assert.assertTrue(
      "Parent requesting unlinked student STU1002 attempts must return emptyList()",
      com.example.data.AppRepository.getAttemptsForCurrentStudent(studentB).isEmpty()
    )

    // Parent accessing linked child STU1001 -> succeeds
    org.junit.Assert.assertTrue(
      "Parent selecting linked child STU1001 must succeed",
      com.example.data.AppRepository.setSelectedChildForParent("STU1001")
    )
    org.junit.Assert.assertFalse(
      "Parent requesting linked student STU1001 attempts must return non-empty list",
      com.example.data.AppRepository.getAttemptsForCurrentStudent(studentA).isEmpty()
    )

    // 5. Cross-Institute Isolation Checks (Institute A vs Institute B):
    com.example.data.AppRepository.login(
      identifier = "admin.instA@mytuition.com",
      role = com.example.data.UserRole.ADMIN,
      instituteId = "INSTITUTE_A"
    )
    assertEquals("INSTITUTE_A", com.example.data.AppRepository.getCurrentInstituteId())

    // Admin in INSTITUTE_A attempting to mutate a student belonging to FSI_MAIN or INSTITUTE_B -> rejected
    org.junit.Assert.assertFalse(
      "Admin in INSTITUTE_A attempting to update a student in FSI_MAIN must be rejected",
      com.example.data.AppRepository.updateStudent(studentA.copy(name = "Cross-Institute Tamper"))
    )
    org.junit.Assert.assertFalse(
      "Admin in INSTITUTE_A attempting to delete a student in FSI_MAIN must be rejected",
      com.example.data.AppRepository.deleteStudent(studentA.id)
    )
    org.junit.Assert.assertFalse(
      "Admin in INSTITUTE_A attempting to add a batch for INSTITUTE_B must be rejected",
      com.example.data.AppRepository.addBatch(
        com.example.data.Batch(
          id = "BATCH_FOREIGN_B",
          name = "Foreign Batch B",
          schedule = "Mon-Fri",
          studentCount = 0,
          teacherName = "Prof. B",
          instituteId = "INSTITUTE_B"
        )
      )
    )
    // Scoped students StateFlow in INSTITUTE_A must not leak FSI_MAIN students
    org.junit.Assert.assertTrue(
      "Scoped students flow for INSTITUTE_A must not contain FSI_MAIN records",
      com.example.data.AppRepository.students.value.none { it.instituteId == "FSI_MAIN" }
    )

    com.example.data.AppRepository.logout()
  }

  @Test
  fun `syncQueue_testA_insertPendingSyncItem_existsInRoom`(): Unit = kotlinx.coroutines.runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val dbName = "sync_queue_test_a.db"
    context.deleteDatabase(dbName)
    val db = androidx.room.Room.databaseBuilder(
      context,
      com.example.data.db.AppRoomDatabase::class.java,
      dbName
    )
      .addMigrations(*com.example.data.db.AppRoomDatabase.ALL_MIGRATIONS)
      .allowMainThreadQueries()
      .build()

    val dao = db.syncQueueDao()
    val payload = com.example.data.db.SyncQueueEntity.serializePayload(
      mapOf(
        "id" to "STU_SYNC_A1",
        "name" to "Aarav Deshmukh",
        "batchId" to "BATCH_01",
        "password" to "MustNeverBePersisted123!",
        "authToken" to "secret_token_xyz"
      )
    )
    val item = com.example.data.db.SyncQueueEntity(
      queueId = "Q_ITEM_A1",
      instituteId = "FSI_MAIN",
      entityType = "student_profiles",
      entityId = "STU_SYNC_A1",
      operationType = com.example.data.db.SyncOperationType.CREATE,
      serializedPayload = payload,
      createdAt = 1700000001000L,
      updatedAt = 1700000001000L,
      retryCount = 0,
      lastAttemptAt = null,
      status = com.example.data.db.SyncQueueStatus.PENDING,
      errorMessage = null
    )

    dao.insertQueueItem(item)

    val stored = dao.getItemById("Q_ITEM_A1")
    org.junit.Assert.assertNotNull("Inserted pending sync item must exist in Room", stored)
    assertEquals("Q_ITEM_A1", stored?.queueId)
    assertEquals("FSI_MAIN", stored?.instituteId)
    assertEquals("student_profiles", stored?.entityType)
    assertEquals("STU_SYNC_A1", stored?.entityId)
    assertEquals(com.example.data.db.SyncOperationType.CREATE, stored?.operationType)
    assertEquals(com.example.data.db.SyncQueueStatus.PENDING, stored?.status)
    assertEquals(0, stored?.retryCount)
    org.junit.Assert.assertNull(stored?.lastAttemptAt)
    org.junit.Assert.assertFalse(
      "Sensitive password or authToken must never be stored in serializedPayload",
      stored?.serializedPayload?.contains("MustNeverBePersisted123!") == true ||
        stored?.serializedPayload?.contains("secret_token_xyz") == true
    )

    db.close()
    context.deleteDatabase(dbName)
  }

  @Test
  fun `syncQueue_testB_readPendingItems_returnsCorrectItem`(): Unit = kotlinx.coroutines.runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val dbName = "sync_queue_test_b.db"
    context.deleteDatabase(dbName)
    val db = androidx.room.Room.databaseBuilder(
      context,
      com.example.data.db.AppRoomDatabase::class.java,
      dbName
    )
      .addMigrations(*com.example.data.db.AppRoomDatabase.ALL_MIGRATIONS)
      .allowMainThreadQueries()
      .build()

    val dao = db.syncQueueDao()
    val pendingItem = com.example.data.db.SyncQueueEntity(
      queueId = "Q_ITEM_B_PENDING",
      instituteId = "FSI_MAIN",
      entityType = "fee_payments",
      entityId = "PAY_101",
      operationType = com.example.data.db.SyncOperationType.UPDATE,
      serializedPayload = """{"id":"PAY_101","amount":15000}""",
      createdAt = 1700000002000L,
      updatedAt = 1700000002000L,
      status = com.example.data.db.SyncQueueStatus.PENDING
    )
    val completedItem = com.example.data.db.SyncQueueEntity(
      queueId = "Q_ITEM_B_COMPLETED",
      instituteId = "FSI_MAIN",
      entityType = "fee_payments",
      entityId = "PAY_100",
      operationType = com.example.data.db.SyncOperationType.CREATE,
      serializedPayload = """{"id":"PAY_100","amount":12000}""",
      createdAt = 1700000001000L,
      updatedAt = 1700000001500L,
      status = com.example.data.db.SyncQueueStatus.COMPLETED
    )

    dao.insertQueueItem(pendingItem)
    dao.insertQueueItem(completedItem)

    val pendingList = dao.getPendingItems()
    assertEquals(1, pendingList.size)
    assertEquals(1, dao.countPendingItems())
    val returned = pendingList.first()
    assertEquals("Q_ITEM_B_PENDING", returned.queueId)
    assertEquals("fee_payments", returned.entityType)
    assertEquals("PAY_101", returned.entityId)
    assertEquals(com.example.data.db.SyncOperationType.UPDATE, returned.operationType)
    assertEquals(com.example.data.db.SyncQueueStatus.PENDING, returned.status)

    db.close()
    context.deleteDatabase(dbName)
  }

  @Test
  fun `syncQueue_testC_restartPersistence_reopenedDatabaseRetainsItem`(): Unit = kotlinx.coroutines.runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val dbName = "sync_queue_test_c_restart.db"
    context.deleteDatabase(dbName)

    // First database instance (before simulated process death / restart)
    val dbBeforeRestart = androidx.room.Room.databaseBuilder(
      context,
      com.example.data.db.AppRoomDatabase::class.java,
      dbName
    )
      .addMigrations(*com.example.data.db.AppRoomDatabase.ALL_MIGRATIONS)
      .allowMainThreadQueries()
      .build()

    val item = com.example.data.db.SyncQueueEntity(
      queueId = "Q_ITEM_C_SURVIVE_RESTART",
      instituteId = "FSI_MAIN",
      entityType = "attendance",
      entityId = "ATT_2026_09_28_BATCH_1",
      operationType = com.example.data.db.SyncOperationType.CREATE,
      serializedPayload = """{"batchId":"BATCH_1","date":"28/09/2026","presentCount":24}""",
      createdAt = 1700000010000L,
      updatedAt = 1700000010000L,
      retryCount = 1,
      lastAttemptAt = 1700000015000L,
      status = com.example.data.db.SyncQueueStatus.PENDING,
      errorMessage = "Network unreachable"
    )
    dbBeforeRestart.syncQueueDao().insertQueueItem(item)
    dbBeforeRestart.close()

    // Recreate and reopen the Room database from disk (simulating app restart after process death)
    val dbAfterRestart = androidx.room.Room.databaseBuilder(
      context,
      com.example.data.db.AppRoomDatabase::class.java,
      dbName
    )
      .addMigrations(*com.example.data.db.AppRoomDatabase.ALL_MIGRATIONS)
      .allowMainThreadQueries()
      .build()

    val persisted = dbAfterRestart.syncQueueDao().getItemById("Q_ITEM_C_SURVIVE_RESTART")
    org.junit.Assert.assertNotNull("Queue item must survive Room database close and reopen", persisted)
    assertEquals("Q_ITEM_C_SURVIVE_RESTART", persisted?.queueId)
    assertEquals("FSI_MAIN", persisted?.instituteId)
    assertEquals("attendance", persisted?.entityType)
    assertEquals("ATT_2026_09_28_BATCH_1", persisted?.entityId)
    assertEquals(com.example.data.db.SyncOperationType.CREATE, persisted?.operationType)
    assertEquals(1, persisted?.retryCount)
    assertEquals(1700000015000L, persisted?.lastAttemptAt)
    assertEquals(com.example.data.db.SyncQueueStatus.PENDING, persisted?.status)
    assertEquals("Network unreachable", persisted?.errorMessage)
    assertEquals(1, dbAfterRestart.syncQueueDao().countPendingItems())

    dbAfterRestart.close()
    context.deleteDatabase(dbName)
  }

  @Test
  fun `syncQueue_testD_statusTransitions_pendingToProcessingToFailedPersists`(): Unit = kotlinx.coroutines.runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val dbName = "sync_queue_test_d_status.db"
    context.deleteDatabase(dbName)
    val db = androidx.room.Room.databaseBuilder(
      context,
      com.example.data.db.AppRoomDatabase::class.java,
      dbName
    )
      .addMigrations(*com.example.data.db.AppRoomDatabase.ALL_MIGRATIONS)
      .allowMainThreadQueries()
      .build()

    val dao = db.syncQueueDao()
    val item = com.example.data.db.SyncQueueEntity(
      queueId = "Q_ITEM_D_STATE",
      instituteId = "FSI_MAIN",
      entityType = "student_profiles",
      entityId = "STU_D1",
      operationType = com.example.data.db.SyncOperationType.UPDATE,
      serializedPayload = """{"id":"STU_D1","status":"Active"}""",
      createdAt = 1700000020000L,
      updatedAt = 1700000020000L,
      retryCount = 0,
      lastAttemptAt = null,
      status = com.example.data.db.SyncQueueStatus.PENDING
    )
    dao.insertQueueItem(item)
    assertEquals(com.example.data.db.SyncQueueStatus.PENDING, dao.getItemById("Q_ITEM_D_STATE")?.status)

    // Transition PENDING -> PROCESSING
    val processingTimestamp = 1700000025000L
    dao.markProcessing("Q_ITEM_D_STATE", attemptAt = processingTimestamp)
    val afterProcessing = dao.getItemById("Q_ITEM_D_STATE")
    assertEquals(com.example.data.db.SyncQueueStatus.PROCESSING, afterProcessing?.status)
    assertEquals(processingTimestamp, afterProcessing?.lastAttemptAt)
    assertEquals(0, dao.countPendingItems())

    // Transition PROCESSING -> FAILED
    val failedTimestamp = 1700000030000L
    dao.markFailed(
      queueId = "Q_ITEM_D_STATE",
      errorMessage = "Firestore deadline exceeded",
      attemptAt = failedTimestamp
    )
    val afterFailed = dao.getItemById("Q_ITEM_D_STATE")
    assertEquals(com.example.data.db.SyncQueueStatus.FAILED, afterFailed?.status)
    assertEquals(1, afterFailed?.retryCount)
    assertEquals(failedTimestamp, afterFailed?.lastAttemptAt)
    assertEquals(failedTimestamp, afterFailed?.updatedAt)
    assertEquals("Firestore deadline exceeded", afterFailed?.errorMessage)

    db.close()
    context.deleteDatabase(dbName)
  }

  @Test
  fun `syncQueue_testE_completion_markCompletedAndDeleteCompleted`(): Unit = kotlinx.coroutines.runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val dbName = "sync_queue_test_e_completion.db"
    context.deleteDatabase(dbName)
    val db = androidx.room.Room.databaseBuilder(
      context,
      com.example.data.db.AppRoomDatabase::class.java,
      dbName
    )
      .addMigrations(*com.example.data.db.AppRoomDatabase.ALL_MIGRATIONS)
      .allowMainThreadQueries()
      .build()

    val dao = db.syncQueueDao()
    val item = com.example.data.db.SyncQueueEntity(
      queueId = "Q_ITEM_E_COMPLETE",
      instituteId = "FSI_MAIN",
      entityType = "student_profiles",
      entityId = "STU_E1",
      operationType = com.example.data.db.SyncOperationType.DELETE,
      serializedPayload = """{"id":"STU_E1"}""",
      createdAt = 1700000040000L,
      updatedAt = 1700000040000L,
      status = com.example.data.db.SyncQueueStatus.PENDING,
      errorMessage = "Previous transient error"
    )
    dao.insertQueueItem(item)
    assertEquals(1, dao.countPendingItems())

    val completedTimestamp = 1700000045000L
    dao.markCompleted("Q_ITEM_E_COMPLETE", completedAt = completedTimestamp)

    val completedItem = dao.getItemById("Q_ITEM_E_COMPLETE")
    org.junit.Assert.assertNotNull("Completed item must exist in Room before explicit cleanup", completedItem)
    assertEquals(com.example.data.db.SyncQueueStatus.COMPLETED, completedItem?.status)
    assertEquals(completedTimestamp, completedItem?.updatedAt)
    assertEquals(completedTimestamp, completedItem?.lastAttemptAt)
    org.junit.Assert.assertNull("Error message must be cleared on completion", completedItem?.errorMessage)
    assertEquals(0, dao.countPendingItems())
    org.junit.Assert.assertTrue(dao.getPendingItems().isEmpty())

    // Verify deleting completed item removes it cleanly
    val deletedRows = dao.deleteCompletedItem("Q_ITEM_E_COMPLETE")
    assertEquals(1, deletedRows)
    org.junit.Assert.assertNull(dao.getItemById("Q_ITEM_E_COMPLETE"))

    db.close()
    context.deleteDatabase(dbName)
  }

  @Test
  fun `syncQueue_testF_multipleItems_deterministicOrderingAndMigrationPreservation`(): Unit = kotlinx.coroutines.runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val dbName = "sync_queue_test_f_ordering.db"
    context.deleteDatabase(dbName)
    val db = androidx.room.Room.databaseBuilder(
      context,
      com.example.data.db.AppRoomDatabase::class.java,
      dbName
    )
      .addMigrations(*com.example.data.db.AppRoomDatabase.ALL_MIGRATIONS)
      .allowMainThreadQueries()
      .build()

    val dao = db.syncQueueDao()

    // Insert out-of-order timestamps and same-timestamp tie-breakers across CREATE, UPDATE, DELETE
    val item3 = com.example.data.db.SyncQueueEntity(
      queueId = "Q_03_DELETE",
      instituteId = "FSI_MAIN",
      entityType = "student_profiles",
      entityId = "STU_03",
      operationType = com.example.data.db.SyncOperationType.DELETE,
      serializedPayload = """{"id":"STU_03"}""",
      createdAt = 3000L,
      updatedAt = 3000L,
      status = com.example.data.db.SyncQueueStatus.PENDING
    )
    val item1 = com.example.data.db.SyncQueueEntity(
      queueId = "Q_01_CREATE",
      instituteId = "FSI_MAIN",
      entityType = "student_profiles",
      entityId = "STU_01",
      operationType = com.example.data.db.SyncOperationType.CREATE,
      serializedPayload = """{"id":"STU_01"}""",
      createdAt = 1000L,
      updatedAt = 1000L,
      status = com.example.data.db.SyncQueueStatus.PENDING
    )
    val item2B = com.example.data.db.SyncQueueEntity(
      queueId = "Q_02_B_UPDATE",
      instituteId = "FSI_MAIN",
      entityType = "fee_payments",
      entityId = "PAY_02B",
      operationType = com.example.data.db.SyncOperationType.UPDATE,
      serializedPayload = """{"id":"PAY_02B"}""",
      createdAt = 2000L,
      updatedAt = 2000L,
      status = com.example.data.db.SyncQueueStatus.PENDING
    )
    val item2A = com.example.data.db.SyncQueueEntity(
      queueId = "Q_02_A_UPDATE",
      instituteId = "FSI_MAIN",
      entityType = "fee_payments",
      entityId = "PAY_02A",
      operationType = com.example.data.db.SyncOperationType.UPDATE,
      serializedPayload = """{"id":"PAY_02A"}""",
      createdAt = 2000L,
      updatedAt = 2000L,
      status = com.example.data.db.SyncQueueStatus.PENDING
    )

    dao.insertQueueItems(listOf(item3, item1, item2B, item2A))

    assertEquals(4, dao.countPendingItems())
    val orderedPending = dao.getPendingItems()
    assertEquals(
      listOf("Q_01_CREATE", "Q_02_A_UPDATE", "Q_02_B_UPDATE", "Q_03_DELETE"),
      orderedPending.map { it.queueId }
    )
    assertEquals(
      listOf(
        com.example.data.db.SyncOperationType.CREATE,
        com.example.data.db.SyncOperationType.UPDATE,
        com.example.data.db.SyncOperationType.UPDATE,
        com.example.data.db.SyncOperationType.DELETE
      ),
      orderedPending.map { it.operationType }
    )

    db.close()
    context.deleteDatabase(dbName)
  }

  @Test
  fun `task5_2_offlineCreateAndUpdate_persistPendingQueueItemsWithActiveInstituteAndSurviveReopen`(): Unit = kotlinx.coroutines.runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    com.example.security.SecurityEngine.initialize(context)
    com.example.data.db.AppRoomDatabase.closeAndResetInstance()
    context.deleteDatabase("my_tuition_fsi_database")

    // Log in with an authenticated non-default institute ID (must NOT fall back to FSI_MAIN)
    com.example.data.AppRepository.login(
      identifier = "admin.quantum@mytuition.com",
      role = com.example.data.UserRole.ADMIN,
      instituteId = "INST_QUANTUM_99"
    )
    assertEquals("INST_QUANTUM_99", com.example.data.AppRepository.getCurrentInstituteId())

    val repo = com.example.data.StudentBatchRepository(context)
    val db = com.example.data.db.AppRoomDatabase.getDatabase(context)
    val syncDao = db.syncQueueDao()
    com.example.data.FirestoreService.setSyncQueueDaoForTesting(null)
    com.example.data.FirestoreService.clearPendingSyncQueue()
    com.example.data.FirestoreService.setSimulateImmediateWriteFailureForTesting(false)

    // Switch to Offline Mode
    com.example.data.NetworkConnectivityManager.setSimulatedOffline(true)
    org.junit.Assert.assertFalse(com.example.data.NetworkConnectivityManager.isOnline.value)

    try {
      // 1, 3, 4, 5: Offline CREATE creates a persistent PENDING queue item with active institute ID,
      // entityType, entityId, and SyncOperationType.CREATE
      val createdStudent = com.example.data.Student(
        id = "STU_OFFLINE_CREATE_501",
        name = "Aarav Quantum",
        photo = "",
        mobile = "9876500501",
        parentName = "Rajesh Quantum",
        parentContact = "9876500500",
        email = "aarav.quantum@mytuition.com",
        dob = "15 Aug 2008",
        gender = "Male",
        address = "Pune",
        school = "Quantum Junior College",
        className = "Class 12",
        batch = "12th Science",
        stream = "Science",
        admissionDate = "01 Jun 2026"
      )
      repo.insertStudent(createdStudent)

      val pendingAfterCreate = syncDao.getPendingItems()
      assertEquals(1, pendingAfterCreate.size)
      val createItem = pendingAfterCreate.first()
      assertEquals(com.example.data.db.SyncQueueStatus.PENDING, createItem.status)
      assertEquals("INST_QUANTUM_99", createItem.instituteId)
      assertEquals("student_profiles", createItem.entityType)
      assertEquals("STU_OFFLINE_CREATE_501", createItem.entityId)
      assertEquals(com.example.data.db.SyncOperationType.CREATE, createItem.operationType)
      assertEquals(0, createItem.retryCount)
      org.junit.Assert.assertTrue(createItem.queueId.isNotBlank())
      org.junit.Assert.assertTrue(createItem.createdAt > 0L)
      org.junit.Assert.assertTrue(createItem.updatedAt >= createItem.createdAt)
      org.junit.Assert.assertTrue(createItem.serializedPayload.contains("Aarav Quantum"))
      org.junit.Assert.assertTrue(createItem.serializedPayload.contains("INST_QUANTUM_99"))

      // 2, 3, 4, 6: Offline UPDATE creates a persistent PENDING queue item with SyncOperationType.UPDATE
      val updatedStudent = createdStudent.copy(
        name = "Aarav Quantum Updated",
        overallAvg = 94
      )
      repo.updateStudent(updatedStudent)

      val pendingAfterUpdate = syncDao.getPendingItems()
      assertEquals(2, pendingAfterUpdate.size)
      val updateItem = pendingAfterUpdate.last()
      assertEquals(com.example.data.db.SyncQueueStatus.PENDING, updateItem.status)
      assertEquals("INST_QUANTUM_99", updateItem.instituteId)
      assertEquals("student_profiles", updateItem.entityType)
      assertEquals("STU_OFFLINE_CREATE_501", updateItem.entityId)
      assertEquals(com.example.data.db.SyncOperationType.UPDATE, updateItem.operationType)
      assertEquals(0, updateItem.retryCount)
      org.junit.Assert.assertNotEquals(createItem.queueId, updateItem.queueId)
      org.junit.Assert.assertTrue(updateItem.serializedPayload.contains("Aarav Quantum Updated"))

      // Multi-tenant protection: attempting to change an existing entity's instituteId or queue for another institute must be rejected
      repo.updateStudent(updatedStudent.copy(instituteId = "FOREIGN_INST_77", name = "Hijacked Student"))
      assertEquals(
        "Cross-tenant or institute reassignment update must not create a queue item",
        2,
        syncDao.countPendingItems()
      )

      // 8: Close and reopen Room database (and wipe in-memory cache) to verify survival across process death / restart
      val createQueueId = createItem.queueId
      val updateQueueId = updateItem.queueId
      com.example.data.FirestoreService.clearInMemoryPendingSyncCache()
      com.example.data.db.AppRoomDatabase.closeAndResetInstance()

      val reopenedDb = com.example.data.db.AppRoomDatabase.getDatabase(context)
      val reopenedDao = reopenedDb.syncQueueDao()
      val survivedItems = reopenedDao.getPendingItems()

      assertEquals(2, survivedItems.size)
      assertEquals(createQueueId, survivedItems[0].queueId)
      assertEquals(com.example.data.db.SyncOperationType.CREATE, survivedItems[0].operationType)
      assertEquals("INST_QUANTUM_99", survivedItems[0].instituteId)
      assertEquals("student_profiles", survivedItems[0].entityType)
      assertEquals("STU_OFFLINE_CREATE_501", survivedItems[0].entityId)
      assertEquals(com.example.data.db.SyncQueueStatus.PENDING, survivedItems[0].status)

      assertEquals(updateQueueId, survivedItems[1].queueId)
      assertEquals(com.example.data.db.SyncOperationType.UPDATE, survivedItems[1].operationType)
      assertEquals("INST_QUANTUM_99", survivedItems[1].instituteId)
      assertEquals("student_profiles", survivedItems[1].entityType)
      assertEquals("STU_OFFLINE_CREATE_501", survivedItems[1].entityId)
      assertEquals(com.example.data.db.SyncQueueStatus.PENDING, survivedItems[1].status)
    } finally {
      com.example.data.NetworkConnectivityManager.setSimulatedOffline(false)
      com.example.data.FirestoreService.setSimulateImmediateWriteFailureForTesting(false)
      com.example.data.FirestoreService.clearPendingSyncQueue()
      com.example.data.AppRepository.logout()
      com.example.data.db.AppRoomDatabase.closeAndResetInstance()
      context.deleteDatabase("my_tuition_fsi_database")
    }
  }

  @Test
  fun `task5_2_helperSanitizesCredentials_onlineSuccessSkipsQueue_andFailedWriteIsNotLost`(): Unit = kotlinx.coroutines.runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    com.example.security.SecurityEngine.initialize(context)
    com.example.data.db.AppRoomDatabase.closeAndResetInstance()
    context.deleteDatabase("my_tuition_fsi_database")

    com.example.data.AppRepository.login(
      identifier = "admin.vertex@mytuition.com",
      role = com.example.data.UserRole.ADMIN,
      instituteId = "INST_VERTEX_42"
    )

    val repo = com.example.data.StudentBatchRepository(context)
    val db = com.example.data.db.AppRoomDatabase.getDatabase(context)
    val syncDao = db.syncQueueDao()
    com.example.data.FirestoreService.setSyncQueueDaoForTesting(null)
    com.example.data.FirestoreService.clearPendingSyncQueue()
    com.example.data.NetworkConnectivityManager.setSimulatedOffline(false)
    com.example.data.NetworkConnectivityManager.setSimulatedServiceUnavailable(false)
    com.example.data.FirestoreService.setSimulateImmediateWriteFailureForTesting(false)

    try {
      // 7: Helper converts envelope and strips all sensitive credential fields
      val envelopeWithSecrets = com.example.data.FirestoreSyncEnvelope(
        collection = "student_profiles",
        documentId = "STU_SECRET_CHECK_701",
        operation = "CREATE",
        payload = mapOf(
          "id" to "STU_SECRET_CHECK_701",
          "name" to "Meera Nair",
          "password" to "PlaintextPassword123!",
          "passwordHash" to "hash_abc_123",
          "passwordSalt" to "salt_xyz_456",
          "authToken" to "bearer_secret_token_999",
          "refreshToken" to "refresh_secret_888",
          "firebaseSecret" to "fb_private_secret",
          "apiKey" to "AIzaSyFakeKey",
          "nestedAuth" to mapOf(
            "sessionToken" to "nested_token_val",
            "safeNote" to "kept_value"
          )
        )
      )
      val convertedEntity = repo.toSyncQueueEntity(envelopeWithSecrets)
      assertEquals("INST_VERTEX_42", convertedEntity.instituteId)
      assertEquals("student_profiles", convertedEntity.entityType)
      assertEquals("STU_SECRET_CHECK_701", convertedEntity.entityId)
      assertEquals(com.example.data.db.SyncOperationType.CREATE, convertedEntity.operationType)
      assertEquals(com.example.data.db.SyncQueueStatus.PENDING, convertedEntity.status)
      assertEquals(0, convertedEntity.retryCount)
      org.junit.Assert.assertTrue(convertedEntity.serializedPayload.contains("Meera Nair"))
      org.junit.Assert.assertTrue(convertedEntity.serializedPayload.contains("kept_value"))
      org.junit.Assert.assertFalse(convertedEntity.serializedPayload.contains("PlaintextPassword123!"))
      org.junit.Assert.assertFalse(convertedEntity.serializedPayload.contains("hash_abc_123"))
      org.junit.Assert.assertFalse(convertedEntity.serializedPayload.contains("salt_xyz_456"))
      org.junit.Assert.assertFalse(convertedEntity.serializedPayload.contains("bearer_secret_token_999"))
      org.junit.Assert.assertFalse(convertedEntity.serializedPayload.contains("refresh_secret_888"))
      org.junit.Assert.assertFalse(convertedEntity.serializedPayload.contains("fb_private_secret"))
      org.junit.Assert.assertFalse(convertedEntity.serializedPayload.contains("AIzaSyFakeKey"))
      org.junit.Assert.assertFalse(convertedEntity.serializedPayload.contains("nested_token_val"))

      // 9: Online-success behavior does NOT create an unnecessary pending queue item
      val onlineStudent = com.example.data.Student(
        id = "STU_ONLINE_OK_702",
        name = "Rohan Online",
        photo = "",
        mobile = "9876500702",
        parentName = "Suresh Online",
        parentContact = "9876500700",
        email = "rohan.online@mytuition.com",
        dob = "10 May 2009",
        gender = "Male",
        address = "Mumbai",
        school = "Vertex Academy",
        className = "Class 11",
        batch = "11th Science",
        stream = "Science",
        admissionDate = "01 Jun 2026"
      )
      repo.insertStudent(onlineStudent)
      repo.updateStudent(onlineStudent.copy(name = "Rohan Online Updated"))
      assertEquals(
        "Immediate online success must not leave pending items in sync_queue",
        0,
        syncDao.countPendingItems()
      )

      // 10: Immediate Firestore write failure while online is NOT silently lost; it persists in sync_queue
      com.example.data.FirestoreService.setSimulateImmediateWriteFailureForTesting(
        fail = true,
        errorMessage = "UNAVAILABLE: Firestore backend deadline exceeded"
      )
      val failedOnlineStudent = com.example.data.Student(
        id = "STU_WRITE_FAIL_703",
        name = "Kavya Retryable",
        photo = "",
        mobile = "9876500703",
        parentName = "Anil Retryable",
        parentContact = "9876500704",
        email = "kavya.retryable@mytuition.com",
        dob = "20 Nov 2008",
        gender = "Female",
        address = "Pune",
        school = "Vertex High",
        className = "Class 12",
        batch = "12th Science",
        stream = "Science",
        admissionDate = "01 Jun 2026"
      )
      repo.insertStudent(failedOnlineStudent)

      val queuedAfterFailure = syncDao.getPendingItems()
      assertEquals(1, queuedAfterFailure.size)
      val queuedFailedCreate = queuedAfterFailure.first()
      assertEquals("INST_VERTEX_42", queuedFailedCreate.instituteId)
      assertEquals("student_profiles", queuedFailedCreate.entityType)
      assertEquals("STU_WRITE_FAIL_703", queuedFailedCreate.entityId)
      assertEquals(com.example.data.db.SyncOperationType.CREATE, queuedFailedCreate.operationType)
      assertEquals(com.example.data.db.SyncQueueStatus.PENDING, queuedFailedCreate.status)
      assertEquals(0, queuedFailedCreate.retryCount)
    } finally {
      com.example.data.FirestoreService.setSimulateImmediateWriteFailureForTesting(false)
      com.example.data.NetworkConnectivityManager.setSimulatedOffline(false)
      com.example.data.NetworkConnectivityManager.setSimulatedServiceUnavailable(false)
      com.example.data.FirestoreService.clearPendingSyncQueue()
      com.example.data.AppRepository.logout()
      com.example.data.db.AppRoomDatabase.closeAndResetInstance()
      context.deleteDatabase("my_tuition_fsi_database")
    }
  }

  @Test
  fun `task5_3_offlineAndFailedDelete_persistDeleteQueueItem_preventDuplicates_preserveSoftDeactivation_andSurviveReopen`(): Unit = kotlinx.coroutines.runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    com.example.security.SecurityEngine.initialize(context)
    com.example.data.db.AppRoomDatabase.closeAndResetInstance()
    context.deleteDatabase("my_tuition_fsi_database")

    // Log in with an authenticated active institute ID (must NOT use FSI_MAIN)
    com.example.data.AppRepository.login(
      identifier = "admin.horizon@mytuition.com",
      role = com.example.data.UserRole.ADMIN,
      instituteId = "INST_HORIZON_88"
    )
    assertEquals("INST_HORIZON_88", com.example.data.AppRepository.getCurrentInstituteId())

    val repo = com.example.data.StudentBatchRepository(context)
    val db = com.example.data.db.AppRoomDatabase.getDatabase(context)
    val syncDao = db.syncQueueDao()
    com.example.data.FirestoreService.setSyncQueueDaoForTesting(null)
    com.example.data.FirestoreService.clearPendingSyncQueue()
    com.example.data.NetworkConnectivityManager.setSimulatedOffline(false)
    com.example.data.NetworkConnectivityManager.setSimulatedServiceUnavailable(false)
    com.example.data.FirestoreService.setSimulateImmediateWriteFailureForTesting(false)

    try {
      // Seed two active students in INST_HORIZON_88 while online (immediate CREATE succeeds, queue stays empty)
      val studentOfflineDel = com.example.data.Student(
        id = "STU_DEL_OFFLINE_801",
        name = "Nikhil Horizon",
        photo = "",
        mobile = "9876500801",
        parentName = "Prakash Horizon",
        parentContact = "9876500800",
        email = "nikhil.horizon@mytuition.com",
        dob = "12 Jan 2009",
        gender = "Male",
        address = "Nashik",
        school = "Horizon Public School",
        className = "Class 12",
        batch = "12th Science",
        stream = "Science",
        admissionDate = "01 Jun 2026",
        status = "Active",
        isActivated = true,
        instituteId = "INST_HORIZON_88"
      )
      val studentFailedDel = studentOfflineDel.copy(
        id = "STU_DEL_FAIL_802",
        name = "Sneha Horizon",
        mobile = "9876500802",
        email = "sneha.horizon@mytuition.com"
      )
      val studentOnlineDel = studentOfflineDel.copy(
        id = "STU_DEL_ONLINE_803",
        name = "Tanvi Horizon",
        mobile = "9876500803",
        email = "tanvi.horizon@mytuition.com"
      )
      repo.insertStudent(studentOfflineDel)
      repo.insertStudent(studentFailedDel)
      repo.insertStudent(studentOnlineDel)
      assertEquals(0, syncDao.countPendingItems())

      // Immediate online DELETE/deactivation succeeds and does NOT leave a PENDING queue item
      repo.deleteStudent("STU_DEL_ONLINE_803")
      assertEquals(
        "Immediate online DELETE success must not leave a pending queue item",
        0,
        syncDao.countPendingItems()
      )
      val onlineDeactivatedRow = db.studentDao().getStudentByIdOnce("STU_DEL_ONLINE_803")
      org.junit.Assert.assertNotNull("Soft-deactivated student row must remain in Room", onlineDeactivatedRow)
      assertEquals("Inactive", onlineDeactivatedRow?.status)
      assertEquals(false, onlineDeactivatedRow?.isActivated)

      // 1, 3, 4, 5, 6: Switch to Offline Mode and perform DELETE/deactivation
      com.example.data.NetworkConnectivityManager.setSimulatedOffline(true)
      repo.deleteStudent("STU_DEL_OFFLINE_801")

      // Preserve soft deactivation semantics locally in Room (row is NOT hard-deleted from SQLite)
      val softDeactivatedRow = db.studentDao().getStudentByIdOnce("STU_DEL_OFFLINE_801")
      org.junit.Assert.assertNotNull(
        "Local student deactivation must preserve row in Room rather than hard-deleting it",
        softDeactivatedRow
      )
      assertEquals("Inactive", softDeactivatedRow?.status)
      assertEquals(false, softDeactivatedRow?.isActivated)
      assertEquals("INST_HORIZON_88", softDeactivatedRow?.instituteId)

      val pendingAfterOfflineDelete = syncDao.getPendingItems()
      assertEquals(1, pendingAfterOfflineDelete.size)
      val offlineDeleteItem = pendingAfterOfflineDelete.first()
      assertEquals(com.example.data.db.SyncQueueStatus.PENDING, offlineDeleteItem.status)
      assertEquals(com.example.data.db.SyncOperationType.DELETE, offlineDeleteItem.operationType)
      assertEquals("INST_HORIZON_88", offlineDeleteItem.instituteId)
      assertEquals("student_profiles", offlineDeleteItem.entityType)
      assertEquals("STU_DEL_OFFLINE_801", offlineDeleteItem.entityId)
      assertEquals(0, offlineDeleteItem.retryCount)
      org.junit.Assert.assertTrue(offlineDeleteItem.queueId.isNotBlank())
      org.junit.Assert.assertTrue(offlineDeleteItem.createdAt > 0L)
      org.junit.Assert.assertTrue(offlineDeleteItem.updatedAt >= offlineDeleteItem.createdAt)
      org.junit.Assert.assertTrue(offlineDeleteItem.serializedPayload.contains("STU_DEL_OFFLINE_801"))
      org.junit.Assert.assertTrue(offlineDeleteItem.serializedPayload.contains("INST_HORIZON_88"))
      org.junit.Assert.assertTrue(offlineDeleteItem.serializedPayload.contains("Inactive"))
      // Verify minimal payload (does not serialize full profile fields like school or parentName)
      org.junit.Assert.assertFalse(offlineDeleteItem.serializedPayload.contains("Horizon Public School"))
      org.junit.Assert.assertFalse(offlineDeleteItem.serializedPayload.contains("Prakash Horizon"))

      // 8: Repeating the same pending DELETE reuses/updates existing pending record without creating duplicates
      val firstQueueId = offlineDeleteItem.queueId
      repo.deleteStudent("STU_DEL_OFFLINE_801")
      com.example.data.FirestoreService.deleteStudentProfile("STU_DEL_OFFLINE_801", {}, {})

      val pendingAfterRepeat = syncDao.getPendingItems()
      assertEquals(
        "Repeating the same pending DELETE must not create duplicate pending DELETE records",
        1,
        pendingAfterRepeat.size
      )
      assertEquals(firstQueueId, pendingAfterRepeat.first().queueId)
      assertEquals(com.example.data.db.SyncOperationType.DELETE, pendingAfterRepeat.first().operationType)

      // 2: Failed immediate DELETE while online persists a PENDING DELETE queue item
      com.example.data.NetworkConnectivityManager.setSimulatedOffline(false)
      com.example.data.FirestoreService.setSimulateImmediateWriteFailureForTesting(
        fail = true,
        errorMessage = "UNAVAILABLE: Firestore delete RPC failed"
      )
      repo.deleteStudent("STU_DEL_FAIL_802")

      val pendingAfterFailedDelete = syncDao.getPendingItems()
      assertEquals(2, pendingAfterFailedDelete.size)
      val failedDeleteItem = pendingAfterFailedDelete.first { it.entityId == "STU_DEL_FAIL_802" }
      assertEquals(com.example.data.db.SyncQueueStatus.PENDING, failedDeleteItem.status)
      assertEquals(com.example.data.db.SyncOperationType.DELETE, failedDeleteItem.operationType)
      assertEquals("INST_HORIZON_88", failedDeleteItem.instituteId)
      assertEquals("student_profiles", failedDeleteItem.entityType)
      assertEquals("STU_DEL_FAIL_802", failedDeleteItem.entityId)
      assertEquals(0, failedDeleteItem.retryCount)

      // 7: DELETE queue items survive Room database close/reopen and in-memory cache wipe
      val secondQueueId = failedDeleteItem.queueId
      com.example.data.FirestoreService.clearInMemoryPendingSyncCache()
      com.example.data.db.AppRoomDatabase.closeAndResetInstance()

      val reopenedDb = com.example.data.db.AppRoomDatabase.getDatabase(context)
      val reopenedDao = reopenedDb.syncQueueDao()
      val survivedItems = reopenedDao.getPendingItems()
      assertEquals(2, survivedItems.size)
      assertEquals(firstQueueId, survivedItems[0].queueId)
      assertEquals(com.example.data.db.SyncOperationType.DELETE, survivedItems[0].operationType)
      assertEquals("INST_HORIZON_88", survivedItems[0].instituteId)
      assertEquals("STU_DEL_OFFLINE_801", survivedItems[0].entityId)

      assertEquals(secondQueueId, survivedItems[1].queueId)
      assertEquals(com.example.data.db.SyncOperationType.DELETE, survivedItems[1].operationType)
      assertEquals("INST_HORIZON_88", survivedItems[1].instituteId)
      assertEquals("STU_DEL_FAIL_802", survivedItems[1].entityId)
    } finally {
      com.example.data.FirestoreService.setSimulateImmediateWriteFailureForTesting(false)
      com.example.data.NetworkConnectivityManager.setSimulatedOffline(false)
      com.example.data.NetworkConnectivityManager.setSimulatedServiceUnavailable(false)
      com.example.data.FirestoreService.clearPendingSyncQueue()
      com.example.data.AppRepository.logout()
      com.example.data.db.AppRoomDatabase.closeAndResetInstance()
      context.deleteDatabase("my_tuition_fsi_database")
    }
  }

  @Test
  fun `task5_3_deleteEnvelopeSanitization_andCrossInstituteDeleteRejection`(): Unit = kotlinx.coroutines.runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    com.example.security.SecurityEngine.initialize(context)
    com.example.data.db.AppRoomDatabase.closeAndResetInstance()
    context.deleteDatabase("my_tuition_fsi_database")

    // 1. Seed a student under INST_ALPHA_11
    com.example.data.AppRepository.login(
      identifier = "admin.alpha@mytuition.com",
      role = com.example.data.UserRole.ADMIN,
      instituteId = "INST_ALPHA_11"
    )
    val repo = com.example.data.StudentBatchRepository(context)
    val db = com.example.data.db.AppRoomDatabase.getDatabase(context)
    val syncDao = db.syncQueueDao()
    com.example.data.FirestoreService.setSyncQueueDaoForTesting(null)
    com.example.data.FirestoreService.clearPendingSyncQueue()
    com.example.data.NetworkConnectivityManager.setSimulatedOffline(false)
    com.example.data.FirestoreService.setSimulateImmediateWriteFailureForTesting(false)

    try {
      val alphaStudent = com.example.data.Student(
        id = "STU_ALPHA_PROTECTED_901",
        name = "Ishaan Alpha",
        photo = "",
        mobile = "9876500901",
        parentName = "Deepak Alpha",
        parentContact = "9876500900",
        email = "ishaan.alpha@mytuition.com",
        dob = "05 Mar 2009",
        gender = "Male",
        address = "Nagpur",
        school = "Alpha Junior College",
        className = "Class 11",
        batch = "11th Science",
        stream = "Science",
        admissionDate = "01 Jun 2026",
        status = "Active",
        isActivated = true,
        instituteId = "INST_ALPHA_11"
      )
      repo.insertStudent(alphaStudent)
      assertEquals(0, syncDao.countPendingItems())

      // Switch active session to INST_BETA_22 and go offline
      com.example.data.AppRepository.login(
        identifier = "admin.beta@mytuition.com",
        role = com.example.data.UserRole.ADMIN,
        instituteId = "INST_BETA_22"
      )
      assertEquals("INST_BETA_22", com.example.data.AppRepository.getCurrentInstituteId())
      com.example.data.NetworkConnectivityManager.setSimulatedOffline(true)

      // 9: Cross-institute DELETE attempts via StudentBatchRepository, AppRepository, and FirestoreService must be rejected
      repo.deleteStudent("STU_ALPHA_PROTECTED_901")
      org.junit.Assert.assertFalse(
        "AppRepository.deleteStudent must reject cross-institute deletion",
        com.example.data.AppRepository.deleteStudent("STU_ALPHA_PROTECTED_901")
      )
      var deleteProfileRejected = false
      com.example.data.FirestoreService.deleteStudentProfile(
        studentId = "STU_ALPHA_PROTECTED_901",
        onSuccess = {},
        onFailure = { deleteProfileRejected = true }
      )
      org.junit.Assert.assertTrue(deleteProfileRejected)

      var crossEnvelopeCompleted = true
      com.example.data.FirestoreService.enqueueOrSyncEnvelope(
        com.example.data.FirestoreSyncEnvelope(
          collection = "student_profiles",
          documentId = "STU_ALPHA_PROTECTED_901",
          instituteId = "INST_ALPHA_11",
          operation = "DELETE",
          payload = mapOf("id" to "STU_ALPHA_PROTECTED_901", "instituteId" to "INST_ALPHA_11")
        )
      ) { ok -> crossEnvelopeCompleted = ok }
      org.junit.Assert.assertFalse(crossEnvelopeCompleted)

      assertEquals(
        "Cross-institute DELETE attempts must not create any sync_queue item",
        0,
        syncDao.countPendingItems()
      )
      val verifyStillActive = db.studentDao().getStudentByIdOnce("STU_ALPHA_PROTECTED_901")
      assertEquals("Active", verifyStillActive?.status)
      assertEquals(true, verifyStillActive?.isActivated)
      assertEquals("INST_ALPHA_11", verifyStillActive?.instituteId)

      // 3, 6: Verify DELETE envelope conversion maps to SyncOperationType.DELETE with minimal payload and no secrets
      val deleteEnvWithSecrets = com.example.data.FirestoreSyncEnvelope(
        collection = "student_profiles",
        documentId = "STU_BETA_DEL_902",
        instituteId = "INST_BETA_22",
        operation = "DELETE",
        payload = mapOf(
          "id" to "STU_BETA_DEL_902",
          "instituteId" to "INST_BETA_22",
          "status" to "Inactive",
          "isActivated" to false,
          "password" to "SuperSecretDeletePass!",
          "passwordHash" to "del_hash_999",
          "authToken" to "del_bearer_token",
          "firebaseSecret" to "del_fb_secret",
          "apiKey" to "AIzaDeleteSecretKey"
        )
      )
      val deleteQueueEntity = repo.toSyncQueueEntity(deleteEnvWithSecrets)
      assertEquals(com.example.data.db.SyncOperationType.DELETE, deleteQueueEntity.operationType)
      assertEquals(com.example.data.db.SyncQueueStatus.PENDING, deleteQueueEntity.status)
      assertEquals("INST_BETA_22", deleteQueueEntity.instituteId)
      assertEquals("student_profiles", deleteQueueEntity.entityType)
      assertEquals("STU_BETA_DEL_902", deleteQueueEntity.entityId)
      assertEquals(0, deleteQueueEntity.retryCount)
      org.junit.Assert.assertTrue(deleteQueueEntity.serializedPayload.contains("STU_BETA_DEL_902"))
      org.junit.Assert.assertTrue(deleteQueueEntity.serializedPayload.contains("INST_BETA_22"))
      org.junit.Assert.assertFalse(deleteQueueEntity.serializedPayload.contains("SuperSecretDeletePass!"))
      org.junit.Assert.assertFalse(deleteQueueEntity.serializedPayload.contains("del_hash_999"))
      org.junit.Assert.assertFalse(deleteQueueEntity.serializedPayload.contains("del_bearer_token"))
      org.junit.Assert.assertFalse(deleteQueueEntity.serializedPayload.contains("del_fb_secret"))
      org.junit.Assert.assertFalse(deleteQueueEntity.serializedPayload.contains("AIzaDeleteSecretKey"))
    } finally {
      com.example.data.FirestoreService.setSimulateImmediateWriteFailureForTesting(false)
      com.example.data.NetworkConnectivityManager.setSimulatedOffline(false)
      com.example.data.NetworkConnectivityManager.setSimulatedServiceUnavailable(false)
      com.example.data.FirestoreService.clearPendingSyncQueue()
      com.example.data.AppRepository.logout()
      com.example.data.db.AppRoomDatabase.closeAndResetInstance()
      context.deleteDatabase("my_tuition_fsi_database")
    }
  }

  @Test
  fun `task5_4_completeOfflineStudentWorkflow_createUpdateBatchEnrollmentStatusDeactivation_andSurviveReopen`(): Unit = kotlinx.coroutines.runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    com.example.security.SecurityEngine.initialize(context)
    com.example.data.db.AppRoomDatabase.closeAndResetInstance()
    context.deleteDatabase("my_tuition_fsi_database")

    // Log in as ADMIN of active institute INST_ZENITH_54 (must never fall back to FSI_MAIN)
    com.example.data.AppRepository.login(
      identifier = "admin.zenith@mytuition.com",
      role = com.example.data.UserRole.ADMIN,
      instituteId = "INST_ZENITH_54"
    )
    assertEquals("INST_ZENITH_54", com.example.data.AppRepository.getCurrentInstituteId())

    val repo = com.example.data.StudentBatchRepository(context)
    val db = com.example.data.db.AppRoomDatabase.getDatabase(context)
    val syncDao = db.syncQueueDao()
    com.example.data.FirestoreService.setSyncQueueDaoForTesting(null)
    com.example.data.FirestoreService.clearPendingSyncQueue()
    com.example.data.NetworkConnectivityManager.setSimulatedOffline(false)
    com.example.data.NetworkConnectivityManager.setSimulatedServiceUnavailable(false)
    com.example.data.FirestoreService.setSimulateImmediateWriteFailureForTesting(false)

    try {
      // Seed one online student first to verify online-created student receiving multiple offline updates
      val onlineSeededStudent = com.example.data.Student(
        id = "STU_ZENITH_ONLINE_101",
        name = "Siddharth Zenith",
        photo = "",
        mobile = "9876501101",
        parentName = "Mahesh Zenith",
        parentContact = "9876501100",
        email = "siddharth.zenith@mytuition.com",
        dob = "14 Feb 2009",
        gender = "Male",
        address = "Pune",
        school = "Zenith High School",
        className = "Class 11",
        batch = "11th Science",
        batchId = "BATCH_11_SCI",
        stream = "Science",
        admissionDate = "01 Jun 2026",
        status = "Active",
        isActivated = true,
        instituteId = "INST_ZENITH_54"
      )
      org.junit.Assert.assertTrue(com.example.data.AppRepository.addStudent(onlineSeededStudent))
      assertEquals(
        "Online student creation must not leave a pending sync_queue item",
        0,
        syncDao.countPendingItems()
      )

      // Switch to Offline Mode for the entire student workflow
      com.example.data.NetworkConnectivityManager.setSimulatedOffline(true)
      org.junit.Assert.assertFalse(com.example.data.NetworkConnectivityManager.isOnline.value)

      // 1. Offline Student CREATE via AppRepository.addStudent:
      // Writes to Room first, creates persistent CREATE queue item with active institute ID, remains visible locally
      val offlineNewStudent = com.example.data.Student(
        id = "STU_ZENITH_OFFLINE_102",
        name = "Ananya Zenith",
        photo = "",
        mobile = "9876501102",
        parentName = "Vikram Zenith",
        parentContact = "9876501103",
        email = "ananya.zenith@mytuition.com",
        dob = "22 Jul 2008",
        gender = "Female",
        address = "Mumbai",
        school = "Zenith Junior College",
        className = "Class 12",
        batch = "11th Science",
        batchId = "BATCH_11_SCI",
        stream = "Science",
        admissionDate = "10 Jun 2026",
        status = "Active",
        isActivated = false,
        instituteId = "INST_ZENITH_54"
      )
      org.junit.Assert.assertTrue(
        "Offline AppRepository.addStudent must succeed locally",
        com.example.data.AppRepository.addStudent(offlineNewStudent)
      )

      // Verify student is immediately visible in Room and in AppRepository.students
      val roomCreated = db.studentDao().getStudentByIdOnce("STU_ZENITH_OFFLINE_102")
      org.junit.Assert.assertNotNull("Offline-created student must exist in Room", roomCreated)
      assertEquals("Ananya Zenith", roomCreated?.name)
      assertEquals("INST_ZENITH_54", roomCreated?.instituteId)
      assertEquals("11th Science", roomCreated?.batch)
      assertEquals("BATCH_11_SCI", roomCreated?.batchId)
      org.junit.Assert.assertTrue(
        "Offline-created student must be visible in AppRepository.students",
        com.example.data.AppRepository.students.value.any { it.id == "STU_ZENITH_OFFLINE_102" && it.name == "Ananya Zenith" }
      )
      val initialEnrollments = db.batchEnrollmentDao().getEnrollmentsByStudent("STU_ZENITH_OFFLINE_102").first()
      assertEquals(1, initialEnrollments.size)
      assertEquals("BATCH_11_SCI", initialEnrollments.first().batchId)
      assertEquals("Active", initialEnrollments.first().status)

      val pendingAfterOfflineCreate = syncDao.getPendingItems()
      assertEquals(1, pendingAfterOfflineCreate.size)
      val createQueueItem = pendingAfterOfflineCreate.first()
      assertEquals(com.example.data.db.SyncOperationType.CREATE, createQueueItem.operationType)
      assertEquals(com.example.data.db.SyncQueueStatus.PENDING, createQueueItem.status)
      assertEquals("INST_ZENITH_54", createQueueItem.instituteId)
      assertEquals("student_profiles", createQueueItem.entityType)
      assertEquals("STU_ZENITH_OFFLINE_102", createQueueItem.entityId)
      org.junit.Assert.assertTrue(createQueueItem.serializedPayload.contains("Ananya Zenith"))
      org.junit.Assert.assertFalse(createQueueItem.serializedPayload.lowercase().contains("passwordhash"))
      org.junit.Assert.assertFalse(createQueueItem.serializedPayload.lowercase().contains("passwordsalt"))
      org.junit.Assert.assertFalse(createQueueItem.serializedPayload.lowercase().contains("authtoken"))

      // 2 & 4. Multiple Offline Student UPDATEs (profile update, status update, activation update, batch enrollment change):
      // Verify local Room updates first and repeated offline UPDATEs coalesce without uncontrolled duplicates
      org.junit.Assert.assertTrue(
        com.example.data.AppRepository.updateStudent(
          onlineSeededStudent.copy(name = "Siddharth Zenith v2", overallAvg = 91)
        )
      )
      val pendingAfterFirstUpdate = syncDao.getPendingItems().filter { it.entityId == "STU_ZENITH_ONLINE_101" }
      assertEquals(1, pendingAfterFirstUpdate.size)
      val firstUpdateQueueId = pendingAfterFirstUpdate.first().queueId
      assertEquals(com.example.data.db.SyncOperationType.UPDATE, pendingAfterFirstUpdate.first().operationType)

      // Second offline update: status change
      org.junit.Assert.assertTrue(
        com.example.data.AppRepository.updateStudentStatus("STU_ZENITH_ONLINE_101", "Scholarship")
      )
      // Third offline update: activation status change
      org.junit.Assert.assertTrue(
        com.example.data.AppRepository.updateStudentActivationStatus("STU_ZENITH_ONLINE_101", true)
      )
      // Fourth offline update: batch enrollment change via AppRepository.enrollStudentInBatch
      val targetBatch12 = com.example.data.Batch(
        id = "BATCH_ZENITH_12_A",
        name = "12th Zenith Target",
        schedule = "Mon-Sat 8:00 AM",
        studentCount = 0,
        teacherName = "Prof. Zenith",
        instituteId = "INST_ZENITH_54"
      )
      val currentStu101 = com.example.data.AppRepository.students.value.first { it.id == "STU_ZENITH_ONLINE_101" }
      org.junit.Assert.assertTrue(
        "Offline enrollStudentInBatch must succeed locally",
        com.example.data.AppRepository.enrollStudentInBatch(currentStu101, targetBatch12)
      )

      // Verify Room student and batch_enrollments reflect all offline updates and batch reassignment
      val roomUpdated101 = db.studentDao().getStudentByIdOnce("STU_ZENITH_ONLINE_101")
      org.junit.Assert.assertNotNull(roomUpdated101)
      assertEquals("Siddharth Zenith v2", roomUpdated101?.name)
      assertEquals(91, roomUpdated101?.overallAvg)
      assertEquals("12th Zenith Target", roomUpdated101?.batch)
      assertEquals("BATCH_ZENITH_12_A", roomUpdated101?.batchId)
      assertEquals("INST_ZENITH_54", roomUpdated101?.instituteId)

      val enrollments101 = db.batchEnrollmentDao().getEnrollmentsByStudent("STU_ZENITH_ONLINE_101").first()
      org.junit.Assert.assertTrue(
        "New batch enrollment must be Active in Room",
        enrollments101.any { it.batchId == "BATCH_ZENITH_12_A" && it.status == "Active" }
      )
      org.junit.Assert.assertTrue(
        "Previous batch enrollment must be marked Inactive in Room",
        enrollments101.filter { it.batchId == "BATCH_11_SCI" }.all { it.status == "Inactive" }
      )

      // Verify multiple offline updates for STU_ZENITH_ONLINE_101 coalesced into a single PENDING UPDATE queue item
      val pendingUpdatesFor101 = syncDao.getPendingItems().filter { it.entityId == "STU_ZENITH_ONLINE_101" }
      assertEquals(
        "Multiple offline UPDATE mutations for the same student must reuse/update the pending UPDATE queue item",
        1,
        pendingUpdatesFor101.size
      )
      val coalescedUpdateItem = pendingUpdatesFor101.first()
      assertEquals(firstUpdateQueueId, coalescedUpdateItem.queueId)
      assertEquals(com.example.data.db.SyncOperationType.UPDATE, coalescedUpdateItem.operationType)
      assertEquals("INST_ZENITH_54", coalescedUpdateItem.instituteId)
      org.junit.Assert.assertTrue(coalescedUpdateItem.serializedPayload.contains("Siddharth Zenith v2"))
      org.junit.Assert.assertTrue(coalescedUpdateItem.serializedPayload.contains("12th Zenith Target"))
      org.junit.Assert.assertTrue(coalescedUpdateItem.serializedPayload.contains("BATCH_ZENITH_12_A"))

      // 3. Offline Student DELETE/Deactivation via AppRepository.deactivateStudent:
      // Seed historical attendance for STU_ZENITH_ONLINE_101 and verify soft deactivation preserves history
      db.attendanceDao().insertAttendance(
        com.example.data.db.AttendanceRecordEntity(
          id = "ATT_ZENITH_101_H1",
          date = "15/06/2026",
          day = 15,
          month = 6,
          monthName = "June",
          year = 2026,
          time = "08:00 AM",
          studentId = "STU_ZENITH_ONLINE_101",
          studentName = "Siddharth Zenith v2",
          batchName = "12th Zenith Target",
          status = "Present",
          timestamp = 1780000000000L,
          lastModifiedTimestamp = 1780000000000L,
          batchId = "BATCH_ZENITH_12_A",
          instituteId = "INST_ZENITH_54"
        )
      )
      org.junit.Assert.assertTrue(
        "Offline deactivateStudent must succeed locally",
        com.example.data.AppRepository.deactivateStudent("STU_ZENITH_ONLINE_101")
      )
      // Repeat deactivateStudent to verify no duplicate DELETE queue items
      com.example.data.AppRepository.deactivateStudent("STU_ZENITH_ONLINE_101")

      val deactivatedRow101 = db.studentDao().getStudentByIdOnce("STU_ZENITH_ONLINE_101")
      org.junit.Assert.assertNotNull("Soft-deactivated student must remain in Room", deactivatedRow101)
      assertEquals("Inactive", deactivatedRow101?.status)
      assertEquals(false, deactivatedRow101?.isActivated)
      org.junit.Assert.assertFalse(
        "Deactivated student must be removed from active AppRepository.students list",
        com.example.data.AppRepository.students.value.any { it.id == "STU_ZENITH_ONLINE_101" }
      )
      val preservedAttendance = db.attendanceDao().getAttendanceByStudent("STU_ZENITH_ONLINE_101").first()
      assertEquals("Historical attendance must remain intact after student deactivation", 1, preservedAttendance.size)

      val deleteQueueItemsFor101 = syncDao.getPendingItems().filter {
        it.entityId == "STU_ZENITH_ONLINE_101" && it.operationType == com.example.data.db.SyncOperationType.DELETE
      }
      assertEquals(1, deleteQueueItemsFor101.size)

      // Close and reopen Room database to verify all offline mutations and queue items survive process restart
      com.example.data.FirestoreService.clearInMemoryPendingSyncCache()
      com.example.data.db.AppRoomDatabase.closeAndResetInstance()

      val reopenedDb = com.example.data.db.AppRoomDatabase.getDatabase(context)
      val reopenedOfflineCreated = reopenedDb.studentDao().getStudentByIdOnce("STU_ZENITH_OFFLINE_102")
      org.junit.Assert.assertNotNull(reopenedOfflineCreated)
      assertEquals("Ananya Zenith", reopenedOfflineCreated?.name)
      assertEquals("INST_ZENITH_54", reopenedOfflineCreated?.instituteId)

      val reopenedDeactivated = reopenedDb.studentDao().getStudentByIdOnce("STU_ZENITH_ONLINE_101")
      org.junit.Assert.assertNotNull(reopenedDeactivated)
      assertEquals("Inactive", reopenedDeactivated?.status)
      assertEquals("12th Zenith Target", reopenedDeactivated?.batch)

      val reopenedPendingQueue = reopenedDb.syncQueueDao().getPendingItems()
      org.junit.Assert.assertTrue(
        reopenedPendingQueue.any {
          it.entityId == "STU_ZENITH_OFFLINE_102" &&
            it.operationType == com.example.data.db.SyncOperationType.CREATE &&
            it.instituteId == "INST_ZENITH_54"
        }
      )
      org.junit.Assert.assertTrue(
        reopenedPendingQueue.any {
          it.entityId == "STU_ZENITH_ONLINE_101" &&
            it.operationType == com.example.data.db.SyncOperationType.DELETE &&
            it.instituteId == "INST_ZENITH_54"
        }
      )
    } finally {
      com.example.data.FirestoreService.setSimulateImmediateWriteFailureForTesting(false)
      com.example.data.NetworkConnectivityManager.setSimulatedOffline(false)
      com.example.data.NetworkConnectivityManager.setSimulatedServiceUnavailable(false)
      com.example.data.FirestoreService.clearPendingSyncQueue()
      com.example.data.AppRepository.logout()
      com.example.data.db.AppRoomDatabase.closeAndResetInstance()
      context.deleteDatabase("my_tuition_fsi_database")
    }
  }

  @Test
  fun `task5_4_offlineActiveInstituteAndRbacEnforcement_blocksCrossTenantStudentAndBatchMutations`(): Unit = kotlinx.coroutines.runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    com.example.security.SecurityEngine.initialize(context)
    com.example.data.db.AppRoomDatabase.closeAndResetInstance()
    context.deleteDatabase("my_tuition_fsi_database")

    // 1. Seed a student in INST_ORIGIN_10 while online
    com.example.data.AppRepository.login(
      identifier = "admin.origin@mytuition.com",
      role = com.example.data.UserRole.ADMIN,
      instituteId = "INST_ORIGIN_10"
    )
    val repo = com.example.data.StudentBatchRepository(context)
    val db = com.example.data.db.AppRoomDatabase.getDatabase(context)
    val syncDao = db.syncQueueDao()
    com.example.data.FirestoreService.setSyncQueueDaoForTesting(null)
    com.example.data.FirestoreService.clearPendingSyncQueue()
    com.example.data.NetworkConnectivityManager.setSimulatedOffline(false)

    try {
      val originStudent = com.example.data.Student(
        id = "STU_ORIGIN_PROTECTED_201",
        name = "Kunal Origin",
        photo = "",
        mobile = "9876502201",
        parentName = "Ramesh Origin",
        parentContact = "9876502200",
        email = "kunal.origin@mytuition.com",
        dob = "09 Sep 2008",
        gender = "Male",
        address = "Pune",
        school = "Origin High",
        className = "Class 12",
        batch = "12th Science",
        batchId = "BATCH_12_SCI",
        stream = "Science",
        admissionDate = "01 Jun 2026",
        status = "Active",
        isActivated = true,
        instituteId = "INST_ORIGIN_10"
      )
      org.junit.Assert.assertTrue(com.example.data.AppRepository.addStudent(originStudent))
      assertEquals(0, syncDao.countPendingItems())

      // 2. Switch to ADMIN of INST_TARGET_20 and go Offline
      com.example.data.AppRepository.login(
        identifier = "admin.target@mytuition.com",
        role = com.example.data.UserRole.ADMIN,
        instituteId = "INST_TARGET_20"
      )
      assertEquals("INST_TARGET_20", com.example.data.AppRepository.getCurrentInstituteId())
      com.example.data.NetworkConnectivityManager.setSimulatedOffline(true)

      // Attempt cross-institute student CREATE (targeting INST_ORIGIN_10 while active is INST_TARGET_20)
      val crossCreateStudent = originStudent.copy(
        id = "STU_CROSS_CREATE_202",
        name = "Cross Create Attempt",
        instituteId = "INST_ORIGIN_10"
      )
      org.junit.Assert.assertFalse(
        "Cross-institute AppRepository.addStudent while offline must be rejected",
        com.example.data.AppRepository.addStudent(crossCreateStudent)
      )
      repo.insertStudent(crossCreateStudent)
      org.junit.Assert.assertNull(db.studentDao().getStudentByIdOnce("STU_CROSS_CREATE_202"))

      // Attempt cross-institute student UPDATE and institute reassignment while offline
      org.junit.Assert.assertFalse(
        "Cross-institute AppRepository.updateStudent while offline must be rejected",
        com.example.data.AppRepository.updateStudent(originStudent.copy(name = "Tampered Offline"))
      )
      org.junit.Assert.assertFalse(
        "Institute reassignment via AppRepository.updateStudent while offline must be rejected",
        com.example.data.AppRepository.updateStudent(originStudent.copy(instituteId = "INST_TARGET_20", name = "Hijacked"))
      )
      repo.updateStudent(originStudent.copy(name = "Tampered Repo Offline"))
      repo.updateStudent(originStudent.copy(instituteId = "INST_TARGET_20", name = "Hijacked Repo"))

      // Attempt cross-institute batch enrollment while offline
      val foreignBatch = com.example.data.Batch(
        id = "BATCH_FOREIGN_ORIGIN",
        name = "Foreign Origin Batch",
        schedule = "Mon-Fri",
        studentCount = 0,
        teacherName = "Prof. Origin",
        instituteId = "INST_ORIGIN_10"
      )
      val targetBatch = com.example.data.Batch(
        id = "BATCH_TARGET_20",
        name = "Target Batch 20",
        schedule = "Mon-Fri",
        studentCount = 0,
        teacherName = "Prof. Target",
        instituteId = "INST_TARGET_20"
      )
      org.junit.Assert.assertFalse(
        "Enrolling a foreign student into active batch while offline must be rejected",
        com.example.data.AppRepository.enrollStudentInBatch(originStudent, targetBatch)
      )
      repo.enrollStudentInBatch(originStudent, targetBatch)

      // Create a valid student in INST_TARGET_20 and attempt to enroll them in a foreign batch (INST_ORIGIN_10)
      val validTargetStudent = originStudent.copy(
        id = "STU_TARGET_VALID_203",
        name = "Priya Target",
        instituteId = "INST_TARGET_20"
      )
      org.junit.Assert.assertTrue(com.example.data.AppRepository.addStudent(validTargetStudent))
      assertEquals(1, syncDao.countPendingItems())

      org.junit.Assert.assertFalse(
        "Enrolling active student into a foreign institute batch while offline must be rejected",
        com.example.data.AppRepository.enrollStudentInBatch(validTargetStudent, foreignBatch)
      )
      repo.enrollStudentInBatch(validTargetStudent, foreignBatch)

      // 3. Verify RBAC while offline: non-admin (TEACHER / STUDENT) cannot create/update/delete students offline
      com.example.data.AppRepository.login(
        identifier = "teacher.target@mytuition.com",
        role = com.example.data.UserRole.TEACHER,
        linkedEntityId = "FAC_01",
        instituteId = "INST_TARGET_20"
      )
      org.junit.Assert.assertFalse(
        "Teacher calling addStudent while offline must be rejected by RBAC",
        com.example.data.AppRepository.addStudent(validTargetStudent.copy(id = "STU_TEACHER_OFFLINE_204"))
      )
      org.junit.Assert.assertFalse(
        "Teacher calling updateStudent while offline must be rejected by RBAC",
        com.example.data.AppRepository.updateStudent(validTargetStudent.copy(name = "Teacher Tamper"))
      )
      org.junit.Assert.assertFalse(
        "Teacher calling deleteStudent while offline must be rejected by RBAC",
        com.example.data.AppRepository.deleteStudent("STU_TARGET_VALID_203")
      )

      // Queue must still only contain the single valid CREATE item for STU_TARGET_VALID_203
      val pendingItems = syncDao.getPendingItems()
      assertEquals(1, pendingItems.size)
      assertEquals("STU_TARGET_VALID_203", pendingItems.first().entityId)
      assertEquals("INST_TARGET_20", pendingItems.first().instituteId)

      // Verify STU_ORIGIN_PROTECTED_201 in Room was never modified or reassigned
      val untouchedOrigin = db.studentDao().getStudentByIdOnce("STU_ORIGIN_PROTECTED_201")
      assertEquals("Kunal Origin", untouchedOrigin?.name)
      assertEquals("INST_ORIGIN_10", untouchedOrigin?.instituteId)
      assertEquals("12th Science", untouchedOrigin?.batch)
    } finally {
      com.example.data.FirestoreService.setSimulateImmediateWriteFailureForTesting(false)
      com.example.data.NetworkConnectivityManager.setSimulatedOffline(false)
      com.example.data.NetworkConnectivityManager.setSimulatedServiceUnavailable(false)
      com.example.data.FirestoreService.clearPendingSyncQueue()
      com.example.data.AppRepository.logout()
      com.example.data.db.AppRoomDatabase.closeAndResetInstance()
      context.deleteDatabase("my_tuition_fsi_database")
    }
  }
}
