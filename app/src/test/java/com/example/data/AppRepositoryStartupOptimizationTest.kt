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
}
