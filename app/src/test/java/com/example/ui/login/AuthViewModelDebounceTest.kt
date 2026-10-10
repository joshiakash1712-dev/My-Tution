package com.example.ui.login

import com.example.data.AppRepository
import com.example.data.UserRole
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AuthViewModelDebounceTest {

  private val testDispatcher = StandardTestDispatcher()

  @Before
  fun setUp() {
    Dispatchers.setMain(testDispatcher)
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  @Test
  fun identifierUpdatesImmediatelyWhileAccountStatusDebounces() = runTest(testDispatcher) {
    val viewModel = AuthViewModel()

    // 1. Initial state
    assertEquals("", viewModel.identifier.value)

    // 2. Change identifier
    viewModel.onIdentifierChanged("admin@test.com")

    // The text field state is immediately updated
    assertEquals("admin@test.com", viewModel.identifier.value)

    // Before debounce duration (150ms < 300ms), account status is not yet resolved
    advanceTimeBy(150)
    assertEquals(false, viewModel.accountStatus.value.exists)

    // After remaining debounce duration (160ms -> total 310ms > 300ms), account status is resolved
    advanceTimeBy(160)
    // The lookup should now be completed
    val status = AppRepository.getAccountStatus("admin@test.com")
    assertEquals(status.exists, viewModel.accountStatus.value.exists)
    assertEquals(status.role, viewModel.accountStatus.value.role)
  }

  @Test
  fun rapidTypingCancelsIntermediateLookupsAndEvaluatesOnlyFinalIdentifier() = runTest(testDispatcher) {
    val viewModel = AuthViewModel()

    // Rapid keystrokes within debounce interval
    viewModel.onIdentifierChanged("a")
    advanceTimeBy(100)
    viewModel.onIdentifierChanged("ad")
    advanceTimeBy(100)
    viewModel.onIdentifierChanged("adm")
    advanceTimeBy(100)
    viewModel.onIdentifierChanged("admi")
    advanceTimeBy(100)
    viewModel.onIdentifierChanged("admin")

    // Identifier reflects latest input immediately
    assertEquals("admin", viewModel.identifier.value)

    // Intermediate inputs ("a", "ad", "adm", "admi") should NOT have triggered completion
    advanceTimeBy(200) // 200ms < 300ms from last keystroke
    assertEquals(false, viewModel.accountStatus.value.exists)

    // Allow debounce to complete for "admin"
    advanceTimeBy(150) // Total 350ms > 300ms from last keystroke
    val expected = AppRepository.getAccountStatus("admin")
    assertEquals(expected.exists, viewModel.accountStatus.value.exists)
    assertEquals(expected.role, viewModel.accountStatus.value.role)
  }

  @Test
  fun clearingIdentifierImmediatelyResetsAccountStatusWithoutStaleLeak() = runTest(testDispatcher) {
    val viewModel = AuthViewModel()

    // Settle a valid identifier first
    viewModel.onIdentifierChanged("teacher1@tuition.com")
    advanceTimeBy(350)

    // Clear input
    viewModel.onIdentifierChanged("")

    // Both identifier and account status reset immediately to unauthenticated default
    assertEquals("", viewModel.identifier.value)
    assertFalse(viewModel.accountStatus.value.exists)
    assertEquals(null, viewModel.accountStatus.value.role)
    assertEquals("", viewModel.accountStatus.value.displayName)
    assertTrue(viewModel.accountStatus.value.isActivated)

    // Advancing time further cannot overwrite with a stale result
    advanceTimeBy(500)
    assertEquals("", viewModel.identifier.value)
    assertFalse(viewModel.accountStatus.value.exists)
  }

  @Test
  fun clearingIdentifierWithWhitespaceImmediatelyResetsStatus() = runTest(testDispatcher) {
    val viewModel = AuthViewModel()

    viewModel.onIdentifierChanged("   ")
    assertEquals("   ", viewModel.identifier.value)
    assertFalse(viewModel.accountStatus.value.exists)

    advanceTimeBy(400)
    assertFalse(viewModel.accountStatus.value.exists)
  }
}
