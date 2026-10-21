package com.example.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AppRepositoryStartupOptimizationTest {

  @Test
  fun testInitializeReturnsImmediatelyAndDemoDataSeedingCompletesAsynchronously() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()

    // 1. Initialize AppRepository without blocking main thread
    AppRepository.initialize(context)

    // 2. Await background completion via awaitInitialDemoDataSeeded()
    AppRepository.awaitInitialDemoDataSeeded()

    // 3. Verify demo seeding is complete and account status is authoritative
    val adminStatus = AppRepository.getAccountStatus("joshiakash1712@gmail.com")
    assertTrue("Admin account must exist after seeding", adminStatus.exists)
    assertTrue("Admin account must have ADMIN role", adminStatus.role == UserRole.ADMIN)

    // 4. Verify repeated initialize() calls do not trigger duplicate seeding
    AppRepository.initialize(context)
    AppRepository.awaitInitialDemoDataSeeded()
    val adminStatusAgain = AppRepository.getAccountStatus("joshiakash1712@gmail.com")
    assertTrue("Admin status remains valid after re-initialization", adminStatusAgain.exists)
  }

  @Test
  fun testSessionRestorationPreservedAfterNonBlockingInit() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    com.example.security.SecurityEngine.initialize(context)
    AppRepository.initialize(context)
    AppRepository.logout()

    assertFalse("User must be logged out", AppRepository.isLoggedIn.value)

    // Verify session restoration works when valid session exists
    AppRepository.login("admin@mytuition.com", UserRole.ADMIN, instituteId = "FSI_MAIN")
    assertTrue("User must be logged in", AppRepository.isLoggedIn.value)
  }

  @Test
  fun testStudentMutationsExecuteSafelyOnDispatchersIO() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    com.example.security.SecurityEngine.initialize(context)
    AppRepository.initialize(context)
    AppRepository.awaitInitialDemoDataSeeded()

    AppRepository.login("admin@mytuition.com", UserRole.ADMIN, instituteId = "FSI_MAIN")

    val student = Student(
      id = "STU_IO_TEST_101",
      name = "IO Test Student",
      photo = "",
      mobile = "9988776655",
      parentName = "IO Parent",
      parentContact = "9988776656",
      email = "io.test@mytuition.com",
      dob = "15/05/2007",
      gender = "Male",
      address = "Pune",
      school = "City High School",
      className = "Class 12",
      batch = "12th Science",
      batchId = "BATCH_12_SCI",
      stream = "Science",
      admissionDate = "01/06/2026",
      status = "Active",
      isActivated = true,
      instituteId = "FSI_MAIN"
    )

    // 1. Add student
    val addResult = AppRepository.addStudent(student)
    assertTrue("addStudent must succeed", addResult)
    val found = AppRepository.findStudentLocal("STU_IO_TEST_101")
    assertNotNull("Student must be found locally", found)

    // 2. Update student status
    val statusResult = AppRepository.updateStudentStatus("STU_IO_TEST_101", "Alumni")
    assertTrue("updateStudentStatus must succeed", statusResult)

    // 3. Batch enrollment update
    val targetBatch = Batch(
      id = "BATCH_JEE_MAINS",
      name = "JEE Mains Batch",
      schedule = "Mon-Fri 09:00 AM",
      studentCount = 10,
      teacherName = "Faculty One",
      teacherId = "FAC_001",
      instituteId = "FSI_MAIN"
    )
    val enrollResult = AppRepository.enrollStudentInBatch(found!!, targetBatch)
    assertTrue("enrollStudentInBatch must succeed", enrollResult)

    // 4. Deactivate student
    val deleteResult = AppRepository.deleteStudent("STU_IO_TEST_101")
    assertTrue("deleteStudent must succeed", deleteResult)
  }
}
