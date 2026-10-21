package com.example.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class StudentManagementSuspendRefactorTest {

  private lateinit var context: Context

  @Before
  fun setUp() = runBlocking {
    context = ApplicationProvider.getApplicationContext<Context>()
    com.example.security.SecurityEngine.initialize(context)
    com.example.data.db.AppRoomDatabase.closeAndResetInstance()
    context.deleteDatabase("my_tuition_fsi_database")
    AppRepository.initialize(context)
    AppRepository.awaitInitialDemoDataSeeded()
  }

  @org.junit.After
  fun tearDown() {
    AppRepository.logout()
  }

  @Test
  fun testSuspendStudentManagementLifecycleAndAuthorization() = runBlocking {
    // 1. Unauthenticated or non-admin calls must be rejected synchronously before IO
    AppRepository.logout()
    val testStudent = Student(
      id = "STU_REFACTOR_201",
      name = "Refactor Student",
      photo = "",
      mobile = "9876543210",
      parentName = "Refactor Parent",
      parentContact = "9876543211",
      email = "refactor@mytuition.com",
      dob = "10/10/2007",
      gender = "Male",
      address = "Pune",
      school = "Model High School",
      className = "Class 12",
      batch = "12th Science",
      batchId = "BATCH_12_SCI",
      stream = "Science",
      admissionDate = "01/06/2026",
      status = "Active",
      isActivated = true,
      instituteId = "FSI_MAIN"
    )

    assertFalse("Unauthenticated addStudent must fail", AppRepository.addStudent(testStudent))
    assertFalse("Unauthenticated updateStudent must fail", AppRepository.updateStudent(testStudent))
    assertFalse("Unauthenticated deleteStudent must fail", AppRepository.deleteStudent(testStudent.id))

    // 2. Admin login for valid mutations
    AppRepository.login("admin@mytuition.com", UserRole.ADMIN, instituteId = "FSI_MAIN")

    // Add Student
    val addSuccess = AppRepository.addStudent(testStudent)
    assertTrue("Admin addStudent must succeed", addSuccess)

    // Lookup via suspend findStudentLocal and findExistingStudentInstituteId
    val localFound = AppRepository.findStudentLocal("STU_REFACTOR_201")
    assertNotNull("Student must be found via findStudentLocal", localFound)
    assertEquals("Refactor Student", localFound?.name)

    val existingInst = AppRepository.findExistingStudentInstituteId("STU_REFACTOR_201")
    assertEquals("FSI_MAIN", existingInst)

    // 3. Update Student details
    val updatedStudent = localFound!!.copy(name = "Refactor Student Updated", overallAvg = 92)
    val updateSuccess = AppRepository.updateStudent(updatedStudent)
    assertTrue("updateStudent must succeed", updateSuccess)
    val afterUpdate = AppRepository.findStudentLocal("STU_REFACTOR_201")
    assertEquals("Refactor Student Updated", afterUpdate?.name)
    assertEquals(92, afterUpdate?.overallAvg)

    // 4. Update Student Status
    val statusSuccess = AppRepository.updateStudentStatus("STU_REFACTOR_201", "Alumni")
    assertTrue("updateStudentStatus must succeed", statusSuccess)
    val afterStatus = AppRepository.findStudentLocal("STU_REFACTOR_201")
    assertEquals("Alumni", afterStatus?.status)

    // 5. Update Student Activation
    val deactivationSuccess = AppRepository.updateStudentActivationStatus("STU_REFACTOR_201", false)
    assertTrue("updateStudentActivationStatus must succeed", deactivationSuccess)
    val afterDeactivation = AppRepository.findStudentLocal("STU_REFACTOR_201")
    assertEquals(false, afterDeactivation?.isActivated)

    // 6. Batch Enrollment
    val newBatch = Batch(
      id = "BATCH_REF_ADVANCED",
      name = "Advanced Batch",
      schedule = "Mon-Fri 08:00 AM",
      studentCount = 5,
      teacherName = "Prof Refactor",
      teacherId = "FAC_REF_01",
      instituteId = "FSI_MAIN"
    )
    val enrollSuccess = AppRepository.enrollStudentInBatch(afterDeactivation!!, newBatch)
    assertTrue("enrollStudentInBatch must succeed", enrollSuccess)
    val afterEnroll = AppRepository.findStudentLocal("STU_REFACTOR_201")
    assertEquals("Advanced Batch", afterEnroll?.batch)

    // 7. Cross-tenant tampering must be rejected
    val foreignStudent = testStudent.copy(id = "STU_FOREIGN_999", instituteId = "FOREIGN_INST_99")
    val crossAdd = AppRepository.addStudent(foreignStudent)
    assertFalse("Cross-tenant student creation must be rejected", crossAdd)

    val crossTamper = afterEnroll!!.copy(instituteId = "FOREIGN_INST_99")
    val crossUpdate = AppRepository.updateStudent(crossTamper)
    assertFalse("Reassigning instituteId via updateStudent must be rejected", crossUpdate)

    // 8. Delete Student
    val deleteSuccess = AppRepository.deleteStudent("STU_REFACTOR_201")
    assertTrue("deleteStudent must succeed", deleteSuccess)
    val afterDelete = AppRepository.findStudentLocal("STU_REFACTOR_201")
    assertNull("Deleted student must not be returned by findStudentLocal", afterDelete)
  }

  @Test
  fun testBulkInsertAndBatchDeletionSuspendOperations() = runBlocking {
    AppRepository.login("admin@mytuition.com", UserRole.ADMIN, instituteId = "FSI_MAIN")

    val bulkList = listOf(
      Student(
        id = "STU_BULK_01",
        name = "Bulk Student 1",
        photo = "",
        mobile = "9000000001",
        parentName = "Parent 1",
        parentContact = "9000000002",
        email = "bulk1@mytuition.com",
        dob = "01/01/2008",
        gender = "Male",
        address = "Pune",
        school = "High School 1",
        className = "Class 11",
        batch = "11th Science",
        batchId = "BATCH_11_SCI",
        stream = "Science",
        admissionDate = "01/06/2026",
        status = "Active",
        isActivated = true,
        instituteId = "FSI_MAIN"
      ),
      Student(
        id = "STU_BULK_02",
        name = "Bulk Student 2",
        photo = "",
        mobile = "9000000003",
        parentName = "Parent 2",
        parentContact = "9000000004",
        email = "bulk2@mytuition.com",
        dob = "01/01/2008",
        gender = "Female",
        address = "Pune",
        school = "High School 2",
        className = "Class 11",
        batch = "11th Science",
        batchId = "BATCH_11_SCI",
        stream = "Science",
        admissionDate = "01/06/2026",
        status = "Active",
        isActivated = true,
        instituteId = "FSI_MAIN"
      )
    )

    val bulkSuccess = AppRepository.addStudents(bulkList)
    assertTrue("addStudents must succeed", bulkSuccess)

    assertNotNull("Bulk student 1 must be present", AppRepository.findStudentLocal("STU_BULK_01"))
    assertNotNull("Bulk student 2 must be present", AppRepository.findStudentLocal("STU_BULK_02"))

    val batchDeleteSuccess = AppRepository.deleteStudents(listOf("STU_BULK_01", "STU_BULK_02"))
    assertTrue("deleteStudents must succeed", batchDeleteSuccess)

    assertNull("Bulk student 1 must be deleted", AppRepository.findStudentLocal("STU_BULK_01"))
    assertNull("Bulk student 2 must be deleted", AppRepository.findStudentLocal("STU_BULK_02"))
  }
}
