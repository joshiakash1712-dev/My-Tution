package com.example.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
class ScopedStateFlowOptimizationTest {

  @Test
  fun testNotificationAdditionDoesNotTriggerUnrelatedScopedFlowEmissions() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    com.example.security.SecurityEngine.initialize(context)
    AppRepository.initialize(context)
    AppRepository.awaitInitialDemoDataSeeded()

    AppRepository.login("admin@mytuition.com", UserRole.ADMIN, instituteId = "FSI_MAIN")

    val beforeStudents = AppRepository.students.value
    val beforeTeachers = AppRepository.teachers.value
    val beforeBatches = AppRepository.batches.value
    val beforeFees = AppRepository.fees.value

    val notificationId = "TEST_NOTIF_${System.currentTimeMillis()}"
    val testNotif = NotificationItem(
      id = notificationId,
      title = "Optimized Scoped Flow Test",
      message = "Testing granular stateflow emission isolation",
      time = "Just now",
      type = "Announcement",
      recipientRole = "All",
      instituteId = "FSI_MAIN"
    )

    val addSuccess = AppRepository.addNotification(testNotif)
    assertTrue("Notification should be added successfully", addSuccess)

    // Verify scoped notifications updated
    val scopedNotifications = AppRepository.notifications.value
    assertTrue("Notifications must contain the new notification", scopedNotifications.any { it.id == notificationId })

    // Verify unrelated scoped collections remain strictly identical object references (not re-allocated or re-filtered)
    assertSame("Students list reference must be identical", beforeStudents, AppRepository.students.value)
    assertSame("Teachers list reference must be identical", beforeTeachers, AppRepository.teachers.value)
    assertSame("Batches list reference must be identical", beforeBatches, AppRepository.batches.value)
    assertSame("Fees list reference must be identical", beforeFees, AppRepository.fees.value)
  }

  @Test
  fun testHomeworkMutationOnlyRefreshesHomeworkScope() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    com.example.security.SecurityEngine.initialize(context)
    AppRepository.initialize(context)
    AppRepository.awaitInitialDemoDataSeeded()

    AppRepository.login("admin@mytuition.com", UserRole.ADMIN, instituteId = "FSI_MAIN")

    val initialAttendance = AppRepository.attendance.value
    val initialFees = AppRepository.fees.value
    val initialStudents = AppRepository.students.value

    val success = AppRepository.addHomework(
      subject = "Physics",
      title = "Gravitation Numericals",
      description = "Solve problems 1 to 10",
      dueDate = "20/10/2026",
      batchName = "12th Science"
    )
    assertTrue("addHomework must succeed", success)

    // Verify homework scoped list is updated
    val currentHw = AppRepository.homework.value
    assertTrue("Scoped homework must contain new assignment", currentHw.any { it.title == "Gravitation Numericals" })

    // Verify unrelated collections remain strictly identical object references
    assertSame(initialAttendance, AppRepository.attendance.value)
    assertSame(initialFees, AppRepository.fees.value)
    assertSame(initialStudents, AppRepository.students.value)
  }

  @Test
  fun testSessionRoleSwitchPreservesFullConsistency() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    com.example.security.SecurityEngine.initialize(context)
    AppRepository.initialize(context)
    AppRepository.awaitInitialDemoDataSeeded()

    // 1. Admin session sees all students and all teachers
    AppRepository.login("admin@mytuition.com", UserRole.ADMIN, instituteId = "FSI_MAIN")
    val adminStudents = AppRepository.students.value
    assertTrue("Admin should see students", adminStudents.isNotEmpty())

    // 2. Student session only sees own student profile
    AppRepository.login("rahul.sharma@mytuition.com", UserRole.STUDENT, linkedEntityId = "STU1001", instituteId = "FSI_MAIN")
    val studentScoped = AppRepository.students.value
    assertEquals("Student must only see 1 profile in scoped state", 1, studentScoped.size)
    assertEquals("STU1001", studentScoped.first().id)

    // 3. Parent session only sees linked child
    AppRepository.login("parent@mytuition.com", UserRole.PARENT, linkedStudentIds = listOf("STU1001"), instituteId = "FSI_MAIN")
    val parentScoped = AppRepository.students.value
    assertEquals("Parent must only see linked child", 1, parentScoped.size)
    assertEquals("STU1001", parentScoped.first().id)
  }
}
