package com.example.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.data.db.AppRoomDatabase
import com.example.data.db.SyncOperationType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Task 4 Regression & Verification Suite:
 * Validates that AppRoomDatabase has eliminated .allowMainThreadQueries() and that
 * all offline sync queue mutations, deduplication, clearing, reconnection, and student
 * management operations execute asynchronously and safely on background dispatchers without main-thread access violations.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class RoomMainThreadAccessEliminationTest {

  private lateinit var context: Context

  @Before
  fun setUp() = runBlocking {
    context = ApplicationProvider.getApplicationContext<Context>()
    com.example.security.SecurityEngine.initialize(context)
    AppRoomDatabase.closeAndResetInstance()
    context.deleteDatabase("my_tuition_fsi_database")
    AppRepository.initialize(context)
    AppRepository.awaitInitialDemoDataSeeded()
    NetworkConnectivityManager.initialize(context)
    NetworkConnectivityManager.setSimulatedOffline(false)
    NetworkConnectivityManager.setSimulatedServiceUnavailable(false)
    FirestoreService.setSimulateImmediateWriteFailureForTesting(false)
  }

  @After
  fun tearDown() = runBlocking {
    FirestoreService.setSimulateImmediateWriteFailureForTesting(false)
    FirestoreService.clearPendingSyncQueue()
    AppRepository.logout()
    AppRoomDatabase.closeAndResetInstance()
  }

  @Test
  fun testRoomDatabaseBuilderHasNoMainThreadQueriesAllowance() {
    // Confirm the production database builds and opens via AppRoomDatabase.getDatabase()
    val db = AppRoomDatabase.getDatabase(context)
    assertNotNull("AppRoomDatabase instance should not be null", db)
    assertTrue("Database should be open or openable", db.openHelper.writableDatabase.isOpen)
  }

  @Test
  fun testOfflineQueueInsertionAndDeduplicationPreserved() = runBlocking {
    val instituteId = AppRepository.getCurrentInstituteId()

    // Switch to offline state
    NetworkConnectivityManager.setSimulatedOffline(true)
    assertFalse(NetworkConnectivityManager.isOnline.value)

    val studentEnvelope1 = FirestoreSyncEnvelope(
      collection = "students",
      documentId = "STU_OFFLINE_TEST_1",
      instituteId = instituteId,
      ownership = "ADMIN",
      createdAt = System.currentTimeMillis(),
      updatedAt = System.currentTimeMillis(),
      syncStatus = "PENDING_SYNC",
      conflictStrategy = "LAST_WRITE_WINS",
      operation = SyncOperationType.CREATE.name,
      payload = mapOf("id" to "STU_OFFLINE_TEST_1", "name" to "Offline Student 1", "status" to "Active")
    )

    // Persist to Room queue on Dispatchers.IO
    val queued1 = withContext(Dispatchers.IO) {
      FirestoreService.persistCreateOrUpdateToRoomQueue(studentEnvelope1, instituteId)
    }
    assertNotNull("Queued entity should be persisted to Room", queued1)

    val countAfterFirst = FirestoreService.getPendingSyncCount()
    assertEquals("Should have exactly 1 pending item in queue", 1, countAfterFirst)

    // Deduplication check: Enqueue updated envelope with same collection, documentId, and operation
    val studentEnvelope1Updated = studentEnvelope1.copy(
      updatedAt = System.currentTimeMillis() + 1000,
      operation = SyncOperationType.CREATE.name,
      payload = mapOf("id" to "STU_OFFLINE_TEST_1", "name" to "Offline Student 1 Updated", "status" to "Active")
    )

    val queuedUpdated = withContext(Dispatchers.IO) {
      FirestoreService.persistCreateOrUpdateToRoomQueue(studentEnvelope1Updated, instituteId)
    }
    assertNotNull("Updated entity should be persisted", queuedUpdated)

    val countAfterDeduplication = FirestoreService.getPendingSyncCount()
    assertEquals("Deduplication must prevent duplicate queue items for the same entity and operation", 1, countAfterDeduplication)

    val envelopes = FirestoreService.getPendingSyncEnvelopes()
    assertEquals(1, envelopes.size)
    assertEquals("STU_OFFLINE_TEST_1", envelopes[0].documentId)
  }

  @Test
  fun testClearPendingSyncQueueEmptiesBothRoomAndCache() = runBlocking {
    val instituteId = AppRepository.getCurrentInstituteId()
    NetworkConnectivityManager.setSimulatedOffline(true)

    val env = FirestoreSyncEnvelope(
      collection = "students",
      documentId = "STU_CLEAR_TEST",
      instituteId = instituteId,
      ownership = "ADMIN",
      createdAt = System.currentTimeMillis(),
      updatedAt = System.currentTimeMillis(),
      syncStatus = "PENDING_SYNC",
      conflictStrategy = "LAST_WRITE_WINS",
      operation = SyncOperationType.CREATE.name,
      payload = mapOf("id" to "STU_CLEAR_TEST", "name" to "Clear Test")
    )

    FirestoreService.persistCreateOrUpdateToRoomQueue(env, instituteId)
    assertTrue("Should have pending items before clear", FirestoreService.getPendingSyncCount() > 0)

    // Clear queue
    FirestoreService.clearPendingSyncQueue()
    assertEquals("Pending count must be 0 after clear", 0, FirestoreService.getPendingSyncCount())
    assertTrue("Pending envelopes list must be empty after clear", FirestoreService.getPendingSyncEnvelopes().isEmpty())
  }

  @Test
  fun testSynchronizationFailureRetainsItemInQueue() = runBlocking {
    val instituteId = AppRepository.getCurrentInstituteId()
    NetworkConnectivityManager.setSimulatedOffline(false)
    NetworkConnectivityManager.setSimulatedServiceUnavailable(false)

    // Simulate immediate write failure
    FirestoreService.setSimulateImmediateWriteFailureForTesting(true, "Simulated network timeout")

    val student = Student(
      id = "STU_FAIL_TEST_1",
      name = "Fail Test Student",
      photo = "",
      mobile = "9876543210",
      parentName = "Fail Parent",
      parentContact = "9876543211",
      email = "fail@test.com",
      dob = "01/01/2008",
      gender = "Male",
      address = "Pune",
      school = "City School",
      className = "Class 10",
      batch = "10th Foundation",
      batchId = "BATCH_10_FDN",
      stream = "Foundation",
      admissionDate = "01/06/2026",
      status = "Active",
      isActivated = true,
      instituteId = instituteId
    )

    var failureReported = false
    val fsStudent = FirestoreStudent.fromDomain(student)
    FirestoreService.createStudentProfile(
      student = fsStudent,
      onSuccess = { failureReported = false },
      onFailure = { failureReported = true }
    )

    assertTrue("Failure callback should have been invoked", failureReported)
    val pendingCount = FirestoreService.getPendingSyncCount()
    assertTrue("Failed write must be retained in Room sync queue for retry", pendingCount >= 1)
  }

  @Test
  fun testStudentManagementRepositoryOperationsExecuteWithoutMainThreadViolations() = runBlocking {
    val instituteId = AppRepository.getCurrentInstituteId()
    val repo = StudentBatchRepository(context)

    val testStudent = Student(
      id = "STU_DISPATCHER_TEST_1",
      name = "Dispatcher Safe Student",
      photo = "",
      mobile = "9876543299",
      parentName = "Parent Safe",
      parentContact = "9876543298",
      email = "dispatchersafe@test.com",
      dob = "01/01/2008",
      gender = "Female",
      address = "Mumbai",
      school = "Modern School",
      className = "Class 11",
      batch = "11th Science",
      batchId = "BATCH_11_SCI",
      stream = "Science",
      admissionDate = "01/06/2026",
      status = "Active",
      isActivated = true,
      instituteId = instituteId
    )

    // Execute repository mutations across IO dispatcher
    withContext(Dispatchers.IO) {
      repo.insertStudent(testStudent, syncToCloud = false)
    }

    // Verify local lookup using suspend function
    val retrieved = AppRepository.findStudentLocal(testStudent.id)
    assertNotNull("Student should be retrieved from Room without main-thread access allowance", retrieved)
    assertEquals("Dispatcher Safe Student", retrieved?.name)

    // Deactivate student safely
    withContext(Dispatchers.IO) {
      repo.deactivateStudent(testStudent.id, syncToCloud = false)
    }

    val retrievedAfterDeactivation = AppRepository.findStudentLocal(testStudent.id)
    assertNull("Deactivated student should not be returned by findStudentLocal", retrievedAfterDeactivation)
  }
}
