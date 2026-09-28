package com.example.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types


import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.example.data.db.BatchEnrollmentEntity

object AppRepository {
  private var preferences: android.content.SharedPreferences? = null
  private var studentBatchRepo: StudentBatchRepository? = null
  private var appContext: android.content.Context? = null
  private val repoScope = CoroutineScope(kotlinx.coroutines.SupervisorJob() + Dispatchers.IO)
  private val collectorJobs = java.util.concurrent.CopyOnWriteArrayList<kotlinx.coroutines.Job>()
  @Volatile
  private var collectorsInitializedForContextHash: Int = 0

  fun getActiveCollectorJobCount(): Int = collectorJobs.count { it.isActive }

  fun cancelAllCollectors() {
    collectorJobs.forEach { it.cancel() }
    collectorJobs.clear()
    collectorsInitializedForContextHash = 0
  }

  private val _isLoggedIn = MutableStateFlow(false)
  val isLoggedIn = _isLoggedIn.asStateFlow()

  private val _currentUserIdentifier = MutableStateFlow("")
  val currentUserIdentifier = _currentUserIdentifier.asStateFlow()

  private val _currentUserAccount = MutableStateFlow<UserAccount?>(null)
  val currentUserAccount = _currentUserAccount.asStateFlow()

  private val _userAccounts = MutableStateFlow<List<UserAccount>>(emptyList())
  val userAccounts = _userAccounts.asStateFlow()

  private val _passwordResetNotice = MutableStateFlow<String?>(null)
  val passwordResetNotice = _passwordResetNotice.asStateFlow()

  private val _currentRole = MutableStateFlow(UserRole.STUDENT)
  val currentRole = _currentRole.asStateFlow()

  private val _savedAccounts = MutableStateFlow<List<SavedAccount>>(emptyList())
  val savedAccounts = _savedAccounts.asStateFlow()

  private val _darkThemeMode = MutableStateFlow(DarkThemeMode.SYSTEM)
  val darkThemeMode = _darkThemeMode.asStateFlow()

  private val _batchEnrollments = MutableStateFlow<List<BatchEnrollmentEntity>>(emptyList())
  val batchEnrollments = _batchEnrollments.asStateFlow()

  private val _selectedChildIdForParent = MutableStateFlow<String?>(null)
  val selectedChildIdForParent = _selectedChildIdForParent.asStateFlow()

  fun setSelectedChildForParent(studentId: String): Boolean {
    val cleanId = studentId.trim()
    if (cleanId.isBlank()) return false
    val isAdmin = _currentRole.value == UserRole.ADMIN || _currentUserAccount.value?.role == UserRole.ADMIN
    if (isAdmin) {
      val exists = _students.value.any { it.id.equals(cleanId, ignoreCase = true) }
      if (exists) {
        _selectedChildIdForParent.value = cleanId
        return true
      }
      return false
    }
    val linkedIds = getLinkedStudentsForCurrentParent().map { it.id }
    val matchedId = linkedIds.find { it.equals(cleanId, ignoreCase = true) }
    return if (matchedId != null) {
      _selectedChildIdForParent.value = matchedId
      true
    } else {
      false
    }
  }

  // Offline action notification & guard
  private val _offlineActionBlockedNotice = MutableStateFlow<String?>(null)
  val offlineActionBlockedNotice = _offlineActionBlockedNotice.asStateFlow()

  private val _offlineBlockedEvent = MutableStateFlow<OfflineBlockedEvent?>(null)
  val offlineBlockedEvent = _offlineBlockedEvent.asStateFlow()

  fun clearOfflineActionNotice() {
    _offlineActionBlockedNotice.value = null
    _offlineBlockedEvent.value = null
  }

  fun clearOfflineBlockedEvent() {
    _offlineBlockedEvent.value = null
    _offlineActionBlockedNotice.value = null
  }

  fun triggerOfflineActionBlocked(actionName: String, customReason: String? = null) {
    val titleFormatted = actionName.trim().replaceFirstChar {
      if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString()
    }
    val defaultReason = "Cannot perform '$titleFormatted' while disconnected from the internet."
    val effectiveReason = customReason ?: defaultReason
    _offlineActionBlockedNotice.value = effectiveReason
    _offlineBlockedEvent.value = OfflineBlockedEvent(
      actionName = titleFormatted,
      reason = effectiveReason
    )
  }

  /**
   * Genuine offline-first local operation: local Room database writes always succeed offline
   * and synchronize with Firestore when connectivity is available.
   */
  fun checkOnlineOrBlock(actionDescription: String): Boolean {
    return true
  }

  fun initialize(context: android.content.Context) {
    val appCtx = context.applicationContext
    appContext = appCtx
    preferences = context.getSharedPreferences("my_tuition_fsi_prefs", android.content.Context.MODE_PRIVATE)

    com.example.security.SecurityEngine.initialize(appCtx)
    NetworkConnectivityManager.initialize(appCtx)
    com.example.utils.NotificationHelper.createNotificationChannel(appCtx)

    if (NetworkConnectivityManager.isOnline.value) {
      FirestoreService.fetchAnnouncements()
      FirestoreService.listenToAnnouncements()
    }

    val isCleared = preferences?.getBoolean("is_data_cleared", false) ?: false
    if (!isCleared && _notifications.value.isEmpty()) {
      _notifications.value = listOf(
        NotificationItem(
          id = "NOT_INIT_1",
          title = "📢 Welcome to FSI My Tuition!",
          message = "Stay updated with class schedules, tests, homework, and institute announcements.",
          time = getCurrentTimeStr(),
          type = "Announcement",
          recipientRole = "All",
          isRead = false
        ),
        NotificationItem(
          id = "NOT_INIT_2",
          title = "📢 Mid-Term Examination Schedule Announced",
          message = "Mid-term tests for 10th & 12th standards commence next Monday. Check test tab for syllabus.",
          time = "Yesterday",
          type = "Announcement",
          recipientRole = "All",
          isRead = false
        )
      )
    }

    val ctxHash = System.identityHashCode(appCtx)
    val repo = studentBatchRepo?.takeIf { collectorsInitializedForContextHash == ctxHash }
      ?: StudentBatchRepository(appCtx).also { studentBatchRepo = it }

    if (!isCleared) {
      _badges.value = SeedData.getDefaultBadges()
    }

    val hasActiveCollectors = collectorJobs.isNotEmpty() &&
      collectorJobs.all { it.isActive } &&
      collectorsInitializedForContextHash == ctxHash

    if (!hasActiveCollectors) {
      cancelAllCollectors()
      collectorsInitializedForContextHash = ctxHash

      // Perform one-shot deterministic demo seeding if database has not been seeded or cleared
      collectorJobs.add(
        repoScope.launch {
          seedInitialDemoDataIfRequired(repo)
        }
      )

      // Pure read-only Room Flow collectors (no write-back loops, no empty-list re-seeding)
      collectorJobs.add(
        repoScope.launch {
          repo.allStudents.collect { list ->
            _students.value = list
            syncBatchStudentCounts(list)
          }
        }
      )

      collectorJobs.add(
        repoScope.launch {
          repo.allBatches.collect { list ->
            val updated = list.map { b ->
              val count = _students.value.count { it.batch.equals(b.name, ignoreCase = true) }
              b.copy(studentCount = count)
            }
            _batches.value = updated
          }
        }
      )

      collectorJobs.add(
        repoScope.launch {
          repo.allEnrollments.collect { list ->
            _batchEnrollments.value = list
          }
        }
      )

      collectorJobs.add(
        repoScope.launch {
          repo.allTeachers.collect { list ->
            _teachers.value = list
          }
        }
      )

      collectorJobs.add(
        repoScope.launch {
          repo.allUserAccounts.collect { entities ->
            _userAccounts.value = entities.map { it.toDomain() }
            val activeId = _currentUserIdentifier.value
            if (_isLoggedIn.value && activeId.isNotBlank()) {
              val matched = _userAccounts.value.find {
                it.email.equals(activeId, ignoreCase = true) ||
                  it.userId.equals(activeId, ignoreCase = true) ||
                  it.linkedEntityId.equals(activeId, ignoreCase = true) ||
                  (it.mobile.isNotBlank() && it.mobile == activeId)
              }
              if (matched != null) {
                val authoritativeRole = com.example.security.SecurityEngine.getVerifiedRole(activeId)
                  ?: getAccountStatus(activeId).role
                  ?: matched.role
                _currentUserAccount.value = matched.copy(role = authoritativeRole)
                _currentRole.value = authoritativeRole
              }
            }
          }
        }
      )

      collectorJobs.add(
        repoScope.launch {
          repo.allAttendance.collect { list ->
            _attendance.value = list
            if (list.isNotEmpty()) {
              recalculateAllStudentsAttendanceFromRecords(list)
            }
          }
        }
      )

      collectorJobs.add(
        repoScope.launch {
          repo.allFeeInvoices.collect { list ->
            _fees.value = list
          }
        }
      )

      collectorJobs.add(
        repoScope.launch {
          repo.allFeePayments.collect { list ->
            _feePayments.value = list
          }
        }
      )

      collectorJobs.add(
        repoScope.launch {
          repo.allHomeworks.collect { list ->
            _homework.value = list
          }
        }
      )

      collectorJobs.add(
        repoScope.launch {
          repo.allDoubts.collect { list ->
            _doubts.value = list
          }
        }
      )

      collectorJobs.add(
        repoScope.launch {
          repo.allTests.collect { list ->
            _tests.value = list
          }
        }
      )

      collectorJobs.add(
        repoScope.launch {
          repo.allSubjects.collect { list ->
            _subjects.value = list
          }
        }
      )

      collectorJobs.add(
        repoScope.launch {
          repo.allLeads.collect { list ->
            _leads.value = list
          }
        }
      )

      collectorJobs.add(
        repoScope.launch {
          repo.allOnlineForms.collect { list ->
            _onlineForms.value = list
          }
        }
      )

      collectorJobs.add(
        repoScope.launch {
          repo.allNotifications.collect { list ->
            if (list.isNotEmpty() || (preferences?.getBoolean("is_data_cleared", false) == true)) {
              _notifications.value = list
            }
          }
        }
      )

      collectorJobs.add(
        repoScope.launch {
          repo.allTimetableEntries.collect { list ->
            _dynamicTimetable.value = list
          }
        }
      )

      collectorJobs.add(
        repoScope.launch {
          repo.allWeeklyTimetable.collect { list ->
            _timetable.value = list
          }
        }
      )

      collectorJobs.add(
        repoScope.launch {
          repo.allQuestions.collect { list ->
            _questions.value = list
          }
        }
      )

      collectorJobs.add(
        repoScope.launch {
          repo.allStudentAttempts.collect { list ->
            _studentTestAttempts.value = list
          }
        }
      )

      collectorJobs.add(
        repoScope.launch {
          repo.allStudyLogs.collect { list ->
            _studyLogs.value = list
          }
        }
      )

      collectorJobs.add(
        repoScope.launch {
          repo.allStudentGoals.collect { list ->
            if (list.isNotEmpty()) {
              val activeStuId = getActiveStudentForCurrentSession()?.id
              val matched = if (!activeStuId.isNullOrBlank()) {
                list.find { it.studentId.equals(activeStuId, ignoreCase = true) }
              } else null
              _studentGoal.value = matched ?: list.first()
            }
          }
        }
      )

      collectorJobs.add(
        repoScope.launch {
          repo.allParentMeetings.collect { list ->
            _meetings.value = list
          }
        }
      )

      // Rule 1: Automatic non-permanent rolling lifecycle cleanup (purge records older than 90 days)
      repoScope.launch {
        try {
          val cutoffTimestamp = System.currentTimeMillis() - (90L * 24 * 60 * 60 * 1000L)
          repo.purgeExpiredAttendance(cutoffTimestamp)
        } catch (e: Exception) {
          e.printStackTrace()
        }
      }
    }

    val persistedThemeStr = preferences?.getString("dark_theme_mode", DarkThemeMode.SYSTEM.name) ?: DarkThemeMode.SYSTEM.name
    val persistedTheme = try {
      DarkThemeMode.valueOf(persistedThemeStr)
    } catch (e: Exception) {
      DarkThemeMode.SYSTEM
    }
    _darkThemeMode.value = persistedTheme

    // Restore authenticated session ONLY if a valid cryptographic session token or active Firebase Auth session exists
    val hasExplicitAuthSession = preferences?.getBoolean("has_explicit_auth_session", false) ?: false
    val persistedLoggedIn = preferences?.getBoolean("is_logged_in", false) ?: false
    val persistedIdentifier = preferences?.getString("user_identifier", "") ?: ""
    val persistedSessionToken = preferences?.getString("session_token", "") ?: ""

    val loadedAccounts = loadSavedAccountsFromPrefs().toMutableList()
    _savedAccounts.value = loadedAccounts

    val tokenVerifiedRole = if (hasExplicitAuthSession && persistedLoggedIn && persistedIdentifier.isNotBlank()) {
      com.example.security.SecurityEngine.verifySessionToken(persistedIdentifier, persistedSessionToken)
    } else null

    if (tokenVerifiedRole != null &&
      (com.example.security.SecurityEngine.hasRegisteredAccount(persistedIdentifier) || getAccountStatus(persistedIdentifier).exists)
    ) {
      val authoritativeRole = getAccountStatus(persistedIdentifier).role
        ?: com.example.security.SecurityEngine.getVerifiedRole(persistedIdentifier)
        ?: tokenVerifiedRole
      bindAuthenticatedUserSession(
        identifier = persistedIdentifier,
        role = authoritativeRole,
        preserveExistingToken = persistedSessionToken
      )
    } else {
      // Fresh installation, logged-out state, or invalid/expired session token must require authentication
      _isLoggedIn.value = false
      _currentUserIdentifier.value = ""
      _currentUserAccount.value = null
      _currentRole.value = UserRole.STUDENT
    }

    // Check Firebase Auth session if configured
    FirebaseAuthService.checkExistingSession(
      onSessionFound = { userEmail, role, _ ->
        val authoritativeRole = getAccountStatus(userEmail).role ?: role
        val newToken = com.example.security.SecurityEngine.issueSessionToken(userEmail, authoritativeRole)
        bindAuthenticatedUserSession(
          identifier = userEmail,
          role = authoritativeRole,
          preserveExistingToken = newToken
        )
      },
      onNoSession = {
        // When Firebase Auth is configured as the production authority, a missing Firebase session invalidates stale local state
        if (FirebaseAuthService.isAvailable()) {
          logout()
        }
      }
    )
  }

  private suspend fun seedInitialDemoDataIfRequired(repo: StudentBatchRepository) {
    val isCleared = preferences?.getBoolean("is_data_cleared", false) ?: false
    if (isCleared) return
    val alreadySeeded = preferences?.getBoolean("initial_demo_seed_completed", false) ?: false
    if (alreadySeeded && repo.getUserAccountCount() > 0) return

    val demoStudents = SeedData.get15DemoStudents().map { stu ->
      stu.copy(
        batchId = stu.batchId.ifBlank { SeedData.batchIdForName(stu.batch) },
        parentId = stu.parentId.ifBlank { "PAR_${stu.id}" },
        parentEmail = stu.parentEmail.ifBlank { "parent_${stu.id.lowercase()}@mytuition.com" }
      )
    }
    val demoTeachers = SeedData.defaultTeachers

    if (_students.value.isEmpty() || !alreadySeeded) {
      repo.bulkInsertStudents(demoStudents)
    }
    if (!alreadySeeded) {
      SeedData.defaultBatches.forEach { b ->
        val count = demoStudents.count { it.batch.equals(b.name, ignoreCase = true) }
        repo.insertBatch(b.copy(studentCount = count))
      }
      repo.insertTeachers(demoTeachers)
      val defaultsAtt = getDefaultAttendance()
      if (defaultsAtt.isNotEmpty()) {
        repo.insertAttendanceList(defaultsAtt)
      }
      repo.insertFeeInvoices(SeedData.getDefaultFeeRecords())
      repo.insertFeePayments(SeedData.getDefaultFeePayments())
      repo.insertHomeworks(SeedData.getDefaultHomeworks())
      repo.insertDoubts(SeedData.getDefaultDoubts())
      repo.insertTests(SeedData.getDefaultTestRecords())
      repo.insertSubjects(SeedData.defaultSubjects)
      repo.insertLeads(SeedData.getDefaultLeads())
      repo.insertOnlineForms(SeedData.getDefaultOnlineForms())
      if (_notifications.value.isNotEmpty()) {
        repo.insertNotifications(_notifications.value)
      }
      repo.insertTimetableEntries(SeedData.getDefaultTimetableEntries())
      repo.insertWeeklyTimetableEntries(SeedData.getDefaultWeeklyTimetable())
      repo.insertQuestions(SeedData.getDefaultQuestions())
    }
    ensureDefaultUserAccountsSeeded(repo, demoStudents, demoTeachers)
    preferences?.edit()?.putBoolean("initial_demo_seed_completed", true)?.apply()
  }

  private suspend fun ensureDefaultUserAccountsSeeded(
    repo: StudentBatchRepository,
    studentsList: List<Student>,
    teachersList: List<Teacher>
  ) {
    if (repo.getUserAccountCount() > 0) return
    val accounts = mutableListOf<com.example.data.db.UserAccountEntity>()

    // Store non-sensitive local profile/directory metadata only (never store default passwords or hashes in Room)
    teachersList.forEach { t ->
      if (t.email.isNotBlank()) {
        accounts.add(
          com.example.data.db.UserAccountEntity(
            userId = "USR_TCH_${t.id}",
            email = t.email.lowercase(),
            mobile = t.contact,
            name = t.name,
            passwordHash = "",
            passwordSalt = "",
            role = UserRole.TEACHER.name,
            linkedEntityId = t.id,
            status = t.status
          )
        )
      }
    }

    studentsList.forEach { s ->
      val stuEmail = s.email.ifBlank { "${s.id.lowercase()}@mytuition.com" }.lowercase()
      accounts.add(
        com.example.data.db.UserAccountEntity(
          userId = "USR_STU_${s.id}",
          email = stuEmail,
          mobile = s.mobile,
          name = s.name,
          passwordHash = "",
          passwordSalt = "",
          role = UserRole.STUDENT.name,
          linkedEntityId = s.id,
          linkedStudentIds = listOf(s.id),
          status = s.status
        )
      )
      val parentEmail = s.parentEmail.ifBlank { "parent_${s.id.lowercase()}@mytuition.com" }.lowercase()
      accounts.add(
        com.example.data.db.UserAccountEntity(
          userId = "USR_PAR_${s.id}",
          email = parentEmail,
          mobile = s.parentContact,
          name = s.parentName.ifBlank { "Parent of ${s.name}" },
          passwordHash = "",
          passwordSalt = "",
          role = UserRole.PARENT.name,
          linkedEntityId = s.id,
          linkedStudentIds = listOf(s.id),
          status = "Active"
        )
      )
    }

    repo.insertUserAccounts(accounts)
  }

  private fun saveSavedAccountsToPrefs(accounts: List<SavedAccount>) {
    val serialized = accounts.joinToString(";;;") { "${it.identifier}:::${it.role.name}:::${it.displayName}" }
    preferences?.edit()?.putString("saved_accounts_list", serialized)?.apply()
  }

  private fun loadSavedAccountsFromPrefs(): List<SavedAccount> {
    val serialized = preferences?.getString("saved_accounts_list", "") ?: ""
    if (serialized.isBlank()) return emptyList()
    return serialized.split(";;;").mapNotNull { entry ->
      val parts = entry.split(":::")
      if (parts.size >= 3) {
        val id = parts[0]
        val role = try { UserRole.valueOf(parts[1]) } catch (e: Exception) { UserRole.STUDENT }
        val name = parts[2]
        SavedAccount(id, role, name)
      } else null
    }
  }

  private fun saveSession(loggedIn: Boolean, identifier: String, role: UserRole, sessionToken: String = "") {
    preferences?.edit()?.apply {
      putBoolean("is_logged_in", loggedIn)
      putBoolean("has_explicit_auth_session", loggedIn)
      putString("user_identifier", identifier)
      putString("user_role", role.name)
      if (loggedIn && sessionToken.isNotBlank()) {
        putString("session_token", sessionToken)
      } else if (!loggedIn) {
        remove("session_token")
      }
      apply()
    }
  }

  fun setRole(role: UserRole) {
    // Only an actively authenticated ADMIN can switch preview roles; non-admin or unauthenticated users cannot self-escalate
    if (!_isLoggedIn.value) return
    val verifiedAccount = _currentUserAccount.value ?: return
    if (verifiedAccount.role != UserRole.ADMIN) {
      return
    }
    _currentRole.value = role
  }

  // =========================================================================
  // ROLE-SCOPED IDENTITY RESOLUTION HELPERS
  // =========================================================================
  fun getActiveStudentForCurrentSession(): Student? {
    val allStu = _students.value
    if (allStu.isEmpty()) return null
    val account = _currentUserAccount.value
    val activeId = _currentUserIdentifier.value.trim().lowercase()
    val digitsOnly = activeId.filter { it.isDigit() }

    if (account != null && account.linkedEntityId.isNotBlank()) {
      allStu.find { it.id.equals(account.linkedEntityId, ignoreCase = true) }?.let { return it }
    }
    if (activeId.isNotBlank()) {
      allStu.find { stu ->
        stu.id.equals(activeId, ignoreCase = true) ||
          stu.email.equals(activeId, ignoreCase = true) ||
          (digitsOnly.length >= 8 && stu.mobile.filter { it.isDigit() }.contains(digitsOnly))
      }?.let { return it }
    }
    // Only allow fallback preview for an authenticated ADMIN inspecting the Student Portal
    if (account?.role == UserRole.ADMIN) {
      return allStu.firstOrNull()
    }
    return null
  }

  fun getLinkedStudentsForCurrentParent(): List<Student> {
    val allStu = _students.value
    if (allStu.isEmpty()) return emptyList()
    val account = _currentUserAccount.value
    val activeId = _currentUserIdentifier.value.trim().lowercase()
    val digitsOnly = activeId.filter { it.isDigit() }

    val matchedByAccount = if (account != null && account.linkedStudentIds.isNotEmpty()) {
      allStu.filter { stu -> account.linkedStudentIds.any { id -> id.equals(stu.id, ignoreCase = true) } }
    } else if (account != null && account.linkedEntityId.isNotBlank()) {
      allStu.filter { it.id.equals(account.linkedEntityId, ignoreCase = true) }
    } else emptyList()

    if (matchedByAccount.isNotEmpty()) return matchedByAccount

    if (activeId.isNotBlank()) {
      val matchedByContactOrEmail = allStu.filter { stu ->
        stu.parentEmail.equals(activeId, ignoreCase = true) ||
          "parent_${stu.id.lowercase()}@mytuition.com" == activeId ||
          stu.parentId.equals(activeId, ignoreCase = true) ||
          (digitsOnly.length >= 8 && stu.parentContact.filter { it.isDigit() }.contains(digitsOnly))
      }
      if (matchedByContactOrEmail.isNotEmpty()) return matchedByContactOrEmail
    }

    // Only allow fallback preview for an authenticated ADMIN inspecting the Parent Portal
    if (account?.role == UserRole.ADMIN) {
      return listOfNotNull(allStu.firstOrNull())
    }
    return emptyList()
  }

  fun getActiveTeacherForCurrentSession(): Teacher? {
    val allTch = _teachers.value
    if (allTch.isEmpty()) return null
    val account = _currentUserAccount.value
    val activeId = _currentUserIdentifier.value.trim().lowercase()
    val digitsOnly = activeId.filter { it.isDigit() }

    if (account != null && account.linkedEntityId.isNotBlank()) {
      allTch.find { it.id.equals(account.linkedEntityId, ignoreCase = true) }?.let { return it }
    }
    if (activeId.isNotBlank()) {
      allTch.find { t ->
        t.id.equals(activeId, ignoreCase = true) ||
          t.email.equals(activeId, ignoreCase = true) ||
          (digitsOnly.length >= 8 && t.contact.filter { it.isDigit() }.contains(digitsOnly)) ||
          t.name.equals(activeId, ignoreCase = true)
      }?.let { return it }
    }
    // Only allow fallback preview for an authenticated ADMIN inspecting the Teacher Portal
    if (account?.role == UserRole.ADMIN) {
      return allTch.firstOrNull()
    }
    return null
  }

  fun setDarkThemeMode(mode: DarkThemeMode) {
    _darkThemeMode.value = mode
    preferences?.edit()?.apply {
      putString("dark_theme_mode", mode.name)
      apply()
    }
  }

  /**
   * Proactively verifies if an active Firebase Auth user session exists.
   * If found, automatically logs the user in without requiring re-verification of their identifier.
   */
  fun checkAndRestoreFirebaseAuthSession(onResult: ((isRestored: Boolean, role: UserRole?) -> Unit)? = null) {
    FirebaseAuthService.checkExistingSession(
      onSessionFound = { userEmail, role, _ ->
        val authoritativeRole = getAccountStatus(userEmail).role ?: role
        val token = com.example.security.SecurityEngine.issueSessionToken(userEmail, authoritativeRole)
        bindAuthenticatedUserSession(userEmail, authoritativeRole, preserveExistingToken = token)
        onResult?.invoke(true, _currentRole.value)
      },
      onNoSession = {
        if (FirebaseAuthService.isAvailable()) {
          logout()
        }
        onResult?.invoke(_isLoggedIn.value, if (_isLoggedIn.value) _currentRole.value else null)
      }
    )
  }

  // Students
  private val _students = MutableStateFlow<List<Student>>(SeedData.get15DemoStudents())
  val students = _students.asStateFlow()

  /**
   * Reconciles remote Firestore student documents into Room using stable student IDs (never names)
   * and LAST_WRITE_WINS conflict strategy (Section 27).
   */
  fun reconcileRemoteStudentsToRoom(remoteList: List<FirestoreStudent>) {
    if (remoteList.isEmpty()) return
    val validRemote = remoteList.filter { it.id.isNotBlank() }
    if (validRemote.isEmpty()) return

    val updatedStudents = mutableListOf<Student>()
    _students.update { localList ->
      val localById = localList.associateBy { it.id }.toMutableMap()
      validRemote.forEach { remote ->
        val existing = localById[remote.id]
        val merged = remote.toDomain(existing)
        localById[remote.id] = merged
        updatedStudents.add(merged)
      }
      localById.values.toList()
    }
    syncBatchStudentCounts()
    if (updatedStudents.isNotEmpty()) {
      repoScope.launch {
        studentBatchRepo?.bulkInsertStudents(updatedStudents)
      }
    }
  }

  fun addStudent(student: Student) {
    if (!checkOnlineOrBlock("add student record")) return
    val normalizedStudent = student.copy(
      batchId = student.batchId.ifBlank { "BATCH_${student.batch.replace(" ", "_")}" },
      parentId = student.parentId.ifBlank { "PAR_${student.id}" },
      parentEmail = student.parentEmail.ifBlank { "parent_${student.id.lowercase()}@mytuition.com" }
    )
    _students.update { it + normalizedStudent }
    syncBatchStudentCounts()
    val stuEmail = normalizedStudent.email.ifBlank { "${normalizedStudent.id.lowercase()}@mytuition.com" }.lowercase()
    val parEmail = normalizedStudent.parentEmail.lowercase()
    repoScope.launch {
      studentBatchRepo?.insertStudent(normalizedStudent)
      studentBatchRepo?.insertUserAccounts(
        listOf(
          com.example.data.db.UserAccountEntity(
            userId = "USR_STU_${normalizedStudent.id}",
            email = stuEmail,
            mobile = normalizedStudent.mobile,
            name = normalizedStudent.name,
            passwordHash = "",
            passwordSalt = "",
            role = UserRole.STUDENT.name,
            linkedEntityId = normalizedStudent.id,
            linkedStudentIds = listOf(normalizedStudent.id),
            status = normalizedStudent.status
          ),
          com.example.data.db.UserAccountEntity(
            userId = "USR_PAR_${normalizedStudent.id}",
            email = parEmail,
            mobile = normalizedStudent.parentContact,
            name = normalizedStudent.parentName.ifBlank { "Parent of ${normalizedStudent.name}" },
            passwordHash = "",
            passwordSalt = "",
            role = UserRole.PARENT.name,
            linkedEntityId = normalizedStudent.id,
            linkedStudentIds = listOf(normalizedStudent.id),
            status = "Active"
          )
        )
      )
    }
    FirestoreService.syncStudentToCloud(normalizedStudent, _currentUserIdentifier.value.ifBlank { "ADMIN" })
    NetworkConnectivityManager.markDataUpdatedOnline()
    addNotification(
      NotificationItem(
        id = "NOT_${System.currentTimeMillis()}",
        title = "New Student Admitted",
        message = "${normalizedStudent.name} (${normalizedStudent.id}) admitted to batch ${normalizedStudent.batch}.",
        time = getCurrentTimeStr(),
        type = "Announcement",
        recipientRole = "All",
        targetStudentId = normalizedStudent.id
      )
    )
  }

  fun updateStudent(student: Student) {
    if (!checkOnlineOrBlock("update student record")) return
    _students.update { list ->
      list.map { if (it.id == student.id) student else it }
    }
    syncBatchStudentCounts()
    repoScope.launch {
      studentBatchRepo?.updateStudent(student)
    }
    FirestoreService.syncStudentToCloud(student, _currentUserIdentifier.value.ifBlank { "ADMIN" })
    NetworkConnectivityManager.markDataUpdatedOnline()
  }

  fun addStudents(newStudents: List<Student>) {
    if (!checkOnlineOrBlock("import students")) return
    if (newStudents.isEmpty()) return
    _students.update { current ->
      val currentMap = current.associateBy { it.id }.toMutableMap()
      newStudents.forEach { currentMap[it.id] = it }
      currentMap.values.toList()
    }
    syncBatchStudentCounts()
    repoScope.launch {
      studentBatchRepo?.bulkInsertStudents(newStudents)
    }
    NetworkConnectivityManager.markDataUpdatedOnline()
    addNotification(
      NotificationItem(
        id = "NOT_${System.currentTimeMillis()}",
        title = "Bulk Student Import Completed",
        message = "Successfully imported ${newStudents.size} student records via CSV.",
        time = getCurrentTimeStr(),
        type = "Announcement",
        recipientRole = "All"
      )
    )
  }

  fun updateStudentStatus(studentId: String, newStatus: String) {
    if (!checkOnlineOrBlock("update student status")) return
    _students.update { list ->
      list.map { if (it.id == studentId) it.copy(status = newStatus) else it }
    }
    val updatedStudent = _students.value.find { it.id == studentId }
    if (updatedStudent != null) {
      repoScope.launch {
        studentBatchRepo?.updateStudent(updatedStudent)
      }
      FirestoreService.syncStudentToCloud(updatedStudent, _currentUserIdentifier.value.ifBlank { "ADMIN" })
    }
    NetworkConnectivityManager.markDataUpdatedOnline()
  }

  fun updateStudentActivationStatus(studentId: String, isActivated: Boolean) {
    if (!checkOnlineOrBlock("change student activation status")) return
    _students.update { list ->
      list.map { if (it.id == studentId) it.copy(isActivated = isActivated, status = if (isActivated) "Active" else it.status) else it }
    }
    val updatedStudent = _students.value.find { it.id == studentId }
    if (updatedStudent != null) {
      repoScope.launch {
        studentBatchRepo?.updateStudent(updatedStudent)
      }
    }
    NetworkConnectivityManager.markDataUpdatedOnline()
  }

  fun deleteStudent(studentId: String) {
    if (!checkOnlineOrBlock("delete student record")) return
    val student = _students.value.find { it.id == studentId }
    _students.update { list -> list.filterNot { it.id == studentId } }
    syncBatchStudentCounts()
    repoScope.launch {
      studentBatchRepo?.deleteStudent(studentId)
    }
    NetworkConnectivityManager.markDataUpdatedOnline()
    addNotification(
      NotificationItem(
        id = "NOT_${System.currentTimeMillis()}",
        title = "Student Record Removed",
        message = "Student ${student?.name ?: studentId} was removed from the active directory.",
        time = getCurrentTimeStr(),
        type = "Announcement",
        recipientRole = "Admin"
      )
    )
  }

  fun deleteStudents(studentIds: List<String>) {
    if (!checkOnlineOrBlock("batch delete student records")) return
    if (studentIds.isEmpty()) return
    val idSet = studentIds.toSet()
    _students.update { list -> list.filterNot { idSet.contains(it.id) } }
    syncBatchStudentCounts()
    repoScope.launch {
      studentBatchRepo?.deleteStudents(studentIds)
    }
    NetworkConnectivityManager.markDataUpdatedOnline()
    addNotification(
      NotificationItem(
        id = "NOT_${System.currentTimeMillis()}",
        title = "Batch Students Deletion",
        message = "Successfully removed ${studentIds.size} student records in batch.",
        time = getCurrentTimeStr(),
        type = "Announcement",
        recipientRole = "Admin"
      )
    )
  }

  // Institute Configuration & Academic Profile
  private val _instituteConfig = MutableStateFlow(InstituteConfig())
  val instituteConfig = _instituteConfig.asStateFlow()

  fun updateInstituteConfig(newConfig: InstituteConfig) {
    if (!checkOnlineOrBlock("update institute configuration")) return
    _instituteConfig.value = newConfig
    preferences?.edit()?.apply {
      putString("institute_name", newConfig.instituteName)
      putString("institute_tagline", newConfig.tagline)
      putString("founder_name", newConfig.founderName)
      putString("institute_address", newConfig.address)
      putString("institute_phone", newConfig.contactPhone)
      putString("institute_email", newConfig.email)
      putString("academic_year", newConfig.academicYear)
      putInt("existing_staff_count", newConfig.existingStaffCount)
      putString("selected_grades", newConfig.selectedGrades.joinToString(","))
      putBoolean("is_setup_completed", newConfig.isSetupCompleted)
      apply()
    }
    // Sync to Cloud Firestore in real-time
    FirestoreService.syncInstituteConfig(newConfig)
    NetworkConnectivityManager.markDataUpdatedOnline()
    addNotification(
      NotificationItem(
        id = "NOT_${System.currentTimeMillis()}",
        title = "🏫 Institute Profile Updated",
        message = "${newConfig.instituteName} academic configuration saved successfully.",
        time = getCurrentTimeStr(),
        type = "Announcement",
        recipientRole = "Admin"
      )
    )
  }

  fun reconcileFacultyName(rawTeacherName: String?, batchName: String? = null, subject: String? = null): String {
    val clean = (rawTeacherName ?: "").trim()

    // Preserve any explicitly provided teacher name or ID that isn't a generic placeholder
    if (clean.isNotBlank() &&
      !clean.equals("Faculty", ignoreCase = true) &&
      !clean.equals("Subject Faculty", ignoreCase = true) &&
      !clean.equals("TBA", ignoreCase = true)
    ) {
      val exactTeacher = _teachers.value.find {
        it.id.equals(clean, ignoreCase = true) ||
          it.name.equals(clean, ignoreCase = true) ||
          it.email.equals(clean, ignoreCase = true)
      }
      return exactTeacher?.name ?: clean
    }

    // If a batch has an assigned teacher in the repository, use that teacher's real name
    if (!batchName.isNullOrBlank()) {
      val batchObj = _batches.value.find {
        it.id.equals(batchName, ignoreCase = true) || it.name.equals(batchName, ignoreCase = true)
      }
      if (batchObj != null) {
        if (batchObj.teacherId.isNotBlank()) {
          _teachers.value.find { it.id.equals(batchObj.teacherId, ignoreCase = true) }?.let { return it.name }
        }
        if (batchObj.teacherName.isNotBlank() && !batchObj.teacherName.equals("Faculty", ignoreCase = true)) {
          return batchObj.teacherName
        }
      }
      val assignedTeacher = _teachers.value.find { t ->
        t.assignedBatchIds.any { bId -> bId.equals(batchName, ignoreCase = true) } ||
          t.assignedBatches.any { b -> b.equals(batchName, ignoreCase = true) }
      }
      if (assignedTeacher != null) return assignedTeacher.name
    }

    // If a subject is provided, match against registered teachers for that subject
    if (!subject.isNullOrBlank()) {
      val subjectLower = subject.lowercase()
      val matchedBySubject = _teachers.value.find { t ->
        t.subject.lowercase().contains(subjectLower) || subjectLower.contains(t.subject.lowercase())
      }
      if (matchedBySubject != null) return matchedBySubject.name
    }

    return "Institute Faculty"
  }

  fun isSameFaculty(nameOrId1: String?, nameOrId2: String?): Boolean {
    if (nameOrId1.isNullOrBlank() || nameOrId2.isNullOrBlank()) return false
    val c1 = nameOrId1.trim()
    val c2 = nameOrId2.trim()
    if (c1.equals(c2, ignoreCase = true)) return true
    val t1 = _teachers.value.find { it.id.equals(c1, ignoreCase = true) || it.name.equals(c1, ignoreCase = true) || it.email.equals(c1, ignoreCase = true) }
    val t2 = _teachers.value.find { it.id.equals(c2, ignoreCase = true) || it.name.equals(c2, ignoreCase = true) || it.email.equals(c2, ignoreCase = true) }
    if (t1 != null && t2 != null) return t1.id.equals(t2.id, ignoreCase = true)
    return false
  }

  /**
   * Authoritative check whether a teacher is assigned to a given batch (by batchId or batchName).
   * Admins have institute-wide access; Teachers are restricted strictly to their assigned batches.
   */
  fun isTeacherAssignedToBatch(batchNameOrId: String, teacherOverride: Teacher? = null): Boolean {
    val cleanBatch = batchNameOrId.trim()
    if (cleanBatch.isBlank()) return false
    val isAdmin = _currentRole.value == UserRole.ADMIN || _currentUserAccount.value?.role == UserRole.ADMIN
    if (isAdmin && teacherOverride == null) return true

    val teacher = teacherOverride ?: getActiveTeacherForCurrentSession() ?: return false
    val targetBatch = _batches.value.find {
      it.id.equals(cleanBatch, ignoreCase = true) || it.name.equals(cleanBatch, ignoreCase = true)
    }

    // Direct match on teacher's assignedBatchIds or assignedBatches
    val matchesTeacherLists = teacher.assignedBatchIds.any {
      it.equals(cleanBatch, ignoreCase = true) || (targetBatch != null && it.equals(targetBatch.id, ignoreCase = true))
    } || teacher.assignedBatches.any {
      it.equals(cleanBatch, ignoreCase = true) || (targetBatch != null && it.equals(targetBatch.name, ignoreCase = true))
    }
    if (matchesTeacherLists) return true

    // Match on batch's assigned teacherId or teacherName
    if (targetBatch != null) {
      if (targetBatch.teacherId.isNotBlank() && targetBatch.teacherId.equals(teacher.id, ignoreCase = true)) {
        return true
      }
      if (targetBatch.teacherName.isNotBlank() && targetBatch.teacherName.equals(teacher.name, ignoreCase = true)) {
        return true
      }
    }
    return false
  }

  fun getAssignedBatchesForCurrentTeacher(): List<Batch> {
    val isAdmin = _currentRole.value == UserRole.ADMIN || _currentUserAccount.value?.role == UserRole.ADMIN
    if (isAdmin) return _batches.value
    val teacher = getActiveTeacherForCurrentSession() ?: return emptyList()
    return _batches.value.filter { b ->
      isTeacherAssignedToBatch(b.id, teacher) || isTeacherAssignedToBatch(b.name, teacher)
    }
  }

  fun getAuthorizedBatchesForCurrentTeacher(): List<Batch> = getAssignedBatchesForCurrentTeacher()

  fun getStudentsForCurrentTeacher(): List<Student> {
    val isAdmin = _currentRole.value == UserRole.ADMIN || _currentUserAccount.value?.role == UserRole.ADMIN
    if (isAdmin) return _students.value
    val assignedBatches = getAssignedBatchesForCurrentTeacher()
    if (assignedBatches.isEmpty()) return emptyList()
    val batchIds = assignedBatches.map { it.id.lowercase() }.toSet()
    val batchNames = assignedBatches.map { it.name.lowercase() }.toSet()
    return _students.value.filter { stu ->
      (stu.batchId.isNotBlank() && batchIds.contains(stu.batchId.lowercase())) ||
        batchNames.contains(stu.batch.lowercase())
    }
  }

  // Teachers
  private val _teachers = MutableStateFlow<List<Teacher>>(SeedData.defaultTeachers)
  val teachers = _teachers.asStateFlow()

  fun addTeacher(t: Teacher) {
    if (!checkOnlineOrBlock("add new faculty")) return
    _teachers.update { it + t }
    repoScope.launch {
      studentBatchRepo?.insertTeacher(t)
    }
    NetworkConnectivityManager.markDataUpdatedOnline()
    addNotification(
      NotificationItem(
        id = "NOT_${System.currentTimeMillis()}",
        title = "Staff Member Onboarded",
        message = "${t.name} (${t.subject}) registered in faculty directory.",
        time = getCurrentTimeStr(),
        type = "Announcement",
        recipientRole = "Admin"
      )
    )
  }

  fun updateTeacher(t: Teacher) {
    if (!checkOnlineOrBlock("update faculty record")) return
    _teachers.update { list ->
      list.map { if (it.id == t.id) t else it }
    }
    repoScope.launch {
      studentBatchRepo?.updateTeacher(t)
    }
    NetworkConnectivityManager.markDataUpdatedOnline()
  }

  fun deleteTeacher(teacherId: String) {
    if (!checkOnlineOrBlock("delete faculty record")) return
    _teachers.update { list -> list.filterNot { it.id == teacherId } }
    repoScope.launch {
      studentBatchRepo?.deleteTeacher(teacherId)
    }
    NetworkConnectivityManager.markDataUpdatedOnline()
  }

  fun updateTeacherStatus(teacherId: String, isActivated: Boolean) {
    if (!checkOnlineOrBlock("update teacher status")) return
    _teachers.update { list ->
      list.map { if (it.id == teacherId) it.copy(isActivated = isActivated, status = if (isActivated) "Active" else "Pending") else it }
    }
    val updated = _teachers.value.find { it.id == teacherId }
    if (updated != null) {
      repoScope.launch {
        studentBatchRepo?.updateTeacher(updated)
      }
    }
    NetworkConnectivityManager.markDataUpdatedOnline()
  }

  fun resolveCanonicalEmailForIdentifier(identifier: String): String? {
    val cleanId = identifier.trim().lowercase()
    if (cleanId.isBlank()) return null
    if (cleanId.contains("@")) return cleanId
    val digitsOnly = cleanId.filter { it.isDigit() }
    val userAcc = _userAccounts.value.find { acc ->
      acc.userId.equals(cleanId, ignoreCase = true) ||
        acc.linkedEntityId.equals(cleanId, ignoreCase = true) ||
        (digitsOnly.length >= 10 && acc.mobile.filter { it.isDigit() }.endsWith(digitsOnly.takeLast(10)))
    }
    if (userAcc != null && userAcc.email.contains("@")) return userAcc.email.lowercase()

    val teacher = _teachers.value.find { t ->
      t.id.equals(cleanId, ignoreCase = true) ||
        (digitsOnly.length >= 10 && t.contact.filter { it.isDigit() }.endsWith(digitsOnly.takeLast(10)))
    }
    if (teacher != null && teacher.email.contains("@")) return teacher.email.lowercase()

    val student = _students.value.find { s ->
      s.id.equals(cleanId, ignoreCase = true) ||
        s.parentId.equals(cleanId, ignoreCase = true) ||
        (digitsOnly.length >= 10 && s.mobile.filter { it.isDigit() }.endsWith(digitsOnly.takeLast(10))) ||
        (digitsOnly.length >= 10 && s.parentContact.filter { it.isDigit() }.endsWith(digitsOnly.takeLast(10)))
    }
    if (student != null) {
      return if (isParentIdentifier(student, cleanId, digitsOnly)) {
        student.parentEmail.ifBlank { "parent_${student.id.lowercase()}@mytuition.com" }.lowercase()
      } else {
        student.email.ifBlank { "${student.id.lowercase()}@mytuition.com" }.lowercase()
      }
    }
    return null
  }

  fun getAccountStatus(identifier: String): AccountStatusInfo {
    val cleanId = identifier.trim().lowercase()
    if (cleanId.isBlank()) {
      return AccountStatusInfo(exists = false, role = null, displayName = "", isActivated = true)
    }

    val digitsOnly = cleanId.filter { it.isDigit() }

    // 0. Check normalized UserAccount table first
    val userAcc = _userAccounts.value.find { acc ->
      acc.email.equals(cleanId, ignoreCase = true) ||
        acc.userId.equals(cleanId, ignoreCase = true) ||
        acc.linkedEntityId.equals(cleanId, ignoreCase = true) ||
        (digitsOnly.length >= 8 && acc.mobile.filter { it.isDigit() }.contains(digitsOnly))
    }

    // 1. Check Registered Institute Admins (configured admin or verified admin credential — never hardcoded emails)
    val config = _instituteConfig.value
    val isConfiguredAdmin = (config.adminEmail.isNotBlank() && cleanId == config.adminEmail.lowercase()) ||
      (digitsOnly.length >= 10 && config.adminPhone.isNotBlank() && config.adminPhone.filter { it.isDigit() }.endsWith(digitsOnly.takeLast(10)))
    val isVaultAdmin = com.example.security.SecurityEngine.getVerifiedRole(cleanId) == UserRole.ADMIN
    val isUserAccAdmin = userAcc?.role == UserRole.ADMIN
    if (isConfiguredAdmin || isVaultAdmin || isUserAccAdmin) {
      return AccountStatusInfo(
        exists = true,
        role = UserRole.ADMIN,
        displayName = userAcc?.name?.ifBlank { null } ?: config.adminFullName.ifBlank { "Institute Administrator" },
        isActivated = true
      )
    }

    // 2. Check Faculty / Teachers (exact email, ID, or phone match)
    val matchedTeacher = _teachers.value.find { t ->
      t.email.lowercase() == cleanId ||
        t.id.lowercase() == cleanId ||
        (digitsOnly.length >= 10 && t.contact.filter { it.isDigit() }.endsWith(digitsOnly.takeLast(10)))
    }
    if (matchedTeacher != null) {
      val hasCustomPassword = com.example.security.SecurityEngine.hasRegisteredAccount(matchedTeacher.email) ||
        com.example.security.SecurityEngine.hasRegisteredAccount(matchedTeacher.id)
      val isActivated = matchedTeacher.isActivated && hasCustomPassword
      return AccountStatusInfo(
        exists = true,
        role = UserRole.TEACHER,
        displayName = matchedTeacher.name,
        isActivated = isActivated,
        expectedActivationCode = matchedTeacher.activationCode,
        teacherId = matchedTeacher.id
      )
    }

    // 3. Check Students & Parents (exact email, ID, parentEmail, or phone match)
    val matchedStudent = _students.value.find { s ->
      s.email.lowercase() == cleanId ||
        s.id.lowercase() == cleanId ||
        s.parentEmail.lowercase() == cleanId ||
        "parent_${s.id.lowercase()}@mytuition.com" == cleanId ||
        s.parentId.lowercase() == cleanId ||
        (digitsOnly.length >= 10 && s.mobile.filter { it.isDigit() }.endsWith(digitsOnly.takeLast(10))) ||
        (digitsOnly.length >= 10 && s.parentContact.filter { it.isDigit() }.endsWith(digitsOnly.takeLast(10)))
    }
    if (matchedStudent != null) {
      val isParent = isParentIdentifier(matchedStudent, cleanId, digitsOnly)
      val role = if (isParent) UserRole.PARENT else UserRole.STUDENT
      val lookupKey = if (isParent) {
        matchedStudent.parentEmail.ifBlank { "parent_${matchedStudent.id.lowercase()}@mytuition.com" }
      } else {
        matchedStudent.email
      }
      val hasCustomPassword = com.example.security.SecurityEngine.hasRegisteredAccount(lookupKey) ||
        com.example.security.SecurityEngine.hasRegisteredAccount(matchedStudent.id)
      val isActivated = matchedStudent.isActivated && hasCustomPassword
      return AccountStatusInfo(
        exists = true,
        role = role,
        displayName = if (isParent) "${matchedStudent.parentName} (Parent of ${matchedStudent.name})" else matchedStudent.name,
        isActivated = isActivated,
        expectedActivationCode = matchedStudent.activationCode,
        studentId = matchedStudent.id
      )
    }

    if (userAcc != null) {
      return AccountStatusInfo(
        exists = true,
        role = userAcc.role,
        displayName = userAcc.name,
        isActivated = userAcc.status.equals("Active", ignoreCase = true),
        teacherId = if (userAcc.role == UserRole.TEACHER) userAcc.linkedEntityId else null,
        studentId = if (userAcc.role == UserRole.STUDENT || userAcc.role == UserRole.PARENT) userAcc.linkedEntityId else null
      )
    }

    return AccountStatusInfo(exists = false, role = null, displayName = "", isActivated = false)
  }

  private fun isParentIdentifier(student: Student, cleanId: String, digitsOnly: String): Boolean {
    if (cleanId == student.parentEmail.lowercase() ||
      cleanId == "parent_${student.id.lowercase()}@mytuition.com" ||
      cleanId == student.parentId.lowercase()
    ) {
      return true
    }
    if (digitsOnly.length >= 10 && student.parentContact.filter { it.isDigit() }.endsWith(digitsOnly.takeLast(10)) &&
      !student.mobile.filter { it.isDigit() }.endsWith(digitsOnly.takeLast(10))
    ) {
      return true
    }
    return false
  }

  suspend fun activateAccountWithCodeAndSetPassword(
    identifier: String,
    activationCode: String,
    newPassword: String
  ): Triple<Boolean, UserRole?, String?> = kotlinx.coroutines.withContext(Dispatchers.Default) {
    val cleanId = identifier.trim().lowercase()
    val cleanCode = activationCode.trim().replace(" ", "").uppercase()

    val status = getAccountStatus(cleanId)
    if (!status.exists || status.role == null) {
      return@withContext Triple(false, null, "Account identifier not found in the institute directory. Contact administration.")
    }

    // Strict verification against the entity's assigned activation code (no hardcoded master bypass codes)
    val expected = status.expectedActivationCode.replace(" ", "").uppercase()
    val isCodeValid = expected.isNotEmpty() && expected == cleanCode

    if (!isCodeValid) {
      return@withContext Triple(false, null, "Invalid activation code. Please enter the 6-digit code issued on your admission/appointment letter.")
    }

    val targetRole = status.role

    if (status.teacherId != null) {
      updateTeacherStatus(status.teacherId, isActivated = true)
    }
    if (status.studentId != null) {
      updateStudentActivationStatus(status.studentId, isActivated = true)
    }

    com.example.security.SecurityEngine.registerUserCredential(
      identifier = cleanId,
      rawPassword = newPassword,
      role = targetRole,
      fullName = status.displayName
    )

    repoScope.launch {
      val existingAcc = studentBatchRepo?.findUserAccount(cleanId)
      if (existingAcc != null) {
        studentBatchRepo?.updateUserAccount(
          existingAcc.copy(
            passwordHash = "",
            passwordSalt = "",
            status = "Active",
            lastLoginAt = System.currentTimeMillis()
          )
        )
      } else {
        studentBatchRepo?.insertUserAccount(
          com.example.data.db.UserAccountEntity(
            userId = "USR_${System.currentTimeMillis()}",
            email = cleanId,
            mobile = if (cleanId.any { it.isDigit() } && !cleanId.contains("@")) cleanId else "",
            name = status.displayName,
            passwordHash = "",
            passwordSalt = "",
            role = targetRole.name,
            linkedEntityId = status.teacherId ?: status.studentId ?: "",
            linkedStudentIds = if (status.studentId != null) listOf(status.studentId) else emptyList(),
            status = "Active"
          )
        )
      }
    }

    Triple(true, targetRole, null)
  }

  fun verifyActivationCode(identifier: String, code: String): Pair<Boolean, UserRole?> {
    val cleanId = identifier.trim().lowercase()
    val cleanCode = code.trim().replace(" ", "").uppercase()
    val status = getAccountStatus(cleanId)
    if (!status.exists || status.role == null) return false to null
    val expected = status.expectedActivationCode.replace(" ", "").uppercase()
    if (expected.isNotEmpty() && expected == cleanCode) {
      return true to status.role
    }
    return false to null
  }

  // Batches
  private val _batches = MutableStateFlow<List<Batch>>(
    SeedData.defaultBatches.map { b ->
      val count = SeedData.get15DemoStudents().count { it.batch.equals(b.name, ignoreCase = true) }
      b.copy(studentCount = count)
    }
  )
  val batches = _batches.asStateFlow()

  fun syncBatchStudentCounts(studentsList: List<Student> = _students.value) {
    _batches.update { currentBatches ->
      currentBatches.map { b ->
        val count = studentsList.count { it.batch.equals(b.name, ignoreCase = true) }
        b.copy(studentCount = count)
      }
    }
  }

  fun getLiveBatchStudentCount(batchName: String): Int {
    return _students.value.count { it.batch.equals(batchName, ignoreCase = true) }
  }

  fun addBatch(b: Batch) {
    if (!checkOnlineOrBlock("create new batch")) return
    val count = _students.value.count { it.batch.equals(b.name, ignoreCase = true) }
    val newBatch = b.copy(studentCount = count)
    _batches.update { it + newBatch }
    repoScope.launch {
      studentBatchRepo?.insertBatch(newBatch)
    }
    NetworkConnectivityManager.markDataUpdatedOnline()
  }

  fun enrollStudentInBatch(student: Student, batch: Batch) {
    if (!checkOnlineOrBlock("enroll student in batch")) return
    _students.update { list ->
      list.map { if (it.id == student.id) it.copy(batch = batch.name, batchId = batch.id) else it }
    }
    syncBatchStudentCounts()
    repoScope.launch {
      studentBatchRepo?.enrollStudentInBatch(student, batch)
    }
    NetworkConnectivityManager.markDataUpdatedOnline()
  }

  // Subjects
  private val _subjects = MutableStateFlow<List<Subject>>(SeedData.defaultSubjects)
  val subjects = _subjects.asStateFlow()

  fun addSubject(s: Subject) {
    if (!checkOnlineOrBlock("add subject")) return
    _subjects.update { it + s }
    repoScope.launch {
      studentBatchRepo?.insertSubject(s)
    }
    NetworkConnectivityManager.markDataUpdatedOnline()
  }

  fun markChapterCompleted(subjectId: String, chapter: String): Boolean {
    if (!checkOnlineOrBlock("update chapter completion status")) return false
    // Only Admin or Teacher can modify syllabus completion
    val role = _currentRole.value
    val isAdmin = role == UserRole.ADMIN || _currentUserAccount.value?.role == UserRole.ADMIN
    if (!isAdmin && role != UserRole.TEACHER) return false

    val targetSub = _subjects.value.find { it.id == subjectId } ?: return false
    if (!isAdmin && targetSub.batchName.isNotBlank() && !isTeacherAssignedToBatch(targetSub.batchId.ifBlank { targetSub.batchName })) {
      return false
    }

    var updatedSubject: Subject? = null
    _subjects.update { list ->
      list.map { sub ->
        if (sub.id == subjectId && sub.pendingChapters.contains(chapter)) {
          val newPending = sub.pendingChapters - chapter
          val newCompleted = (sub.completedChapters + chapter).distinct()
          val total = newPending.size + newCompleted.size
          val newPct = if (total > 0) (newCompleted.size * 100) / total else 100
          val updated = sub.copy(completionPercent = newPct, completedChapters = newCompleted, pendingChapters = newPending)
          updatedSubject = updated
          updated
        } else sub
      }
    }
    updatedSubject?.let { sub ->
      repoScope.launch {
        studentBatchRepo?.updateSubject(sub)
      }
    }
    NetworkConnectivityManager.markDataUpdatedOnline()
    return updatedSubject != null
  }

  fun updateSyllabusProgress(subjectId: String, newCompletionPercent: Int): Boolean {
    if (!checkOnlineOrBlock("update syllabus progress")) return false
    val role = _currentRole.value
    val isAdmin = role == UserRole.ADMIN || _currentUserAccount.value?.role == UserRole.ADMIN
    if (!isAdmin && role != UserRole.TEACHER) return false

    val targetSub = _subjects.value.find { it.id == subjectId } ?: return false
    if (!isAdmin && targetSub.batchName.isNotBlank() && !isTeacherAssignedToBatch(targetSub.batchId.ifBlank { targetSub.batchName })) {
      return false
    }

    val clamped = newCompletionPercent.coerceIn(0, 100)
    var updatedSubject: Subject? = null
    _subjects.update { list ->
      list.map { sub ->
        if (sub.id == subjectId) {
          val copy = sub.copy(completionPercent = clamped)
          updatedSubject = copy
          copy
        } else sub
      }
    }
    updatedSubject?.let { sub ->
      repoScope.launch {
        studentBatchRepo?.updateSubject(sub)
      }
    }
    NetworkConnectivityManager.markDataUpdatedOnline()
    return updatedSubject != null
  }

  private fun getDefaultAttendance(): List<AttendanceRecord> {
    val list = mutableListOf<AttendanceRecord>()
    val now = System.currentTimeMillis()
    val today = getCurrentDateStr()
    val todayParsed = com.example.data.db.AttendanceRecordEntity.parseDateAndTimeString(today, now)

    val studentsList = _students.value.ifEmpty { SeedData.get15DemoStudents() }
    studentsList.forEachIndexed { index, stu ->
      val (status, reason) = when (index % 6) {
        0 -> "Present" to null
        1 -> "Present" to null
        2 -> "Present" to null
        3 -> "Absent" to "Medical - Viral Fever"
        4 -> "Late" to "Bus transit delay"
        else -> "Present" to null
      }
      val resolvedBatchId = stu.batchId.ifBlank { SeedData.batchIdForName(stu.batch) }
      list.add(
        AttendanceRecord(
          id = "ATT_${stu.id}_${stu.batch.replace(" ", "_")}_${today.replace("/", "_")}",
          date = today,
          day = todayParsed.day,
          month = todayParsed.month,
          monthName = todayParsed.monthName,
          year = todayParsed.year,
          time = "09:00 AM",
          studentId = stu.id,
          studentName = stu.name,
          batchName = stu.batch,
          status = status,
          timestamp = now - (index * 60_000L),
          lastModifiedTimestamp = now,
          reason = reason,
          recordedBy = "Institute Administrator",
          batchId = resolvedBatchId,
          instituteId = stu.instituteId.ifBlank { "FSI_MAIN" }
        )
      )
    }
    return list
  }

  // Attendance Records & Policy (Rules 1-5) — Room is the single authoritative local attendance source
  const val ATTENDANCE_EDIT_WINDOW_HOURS = 22
  const val ATTENDANCE_EDIT_WINDOW_MS = 22 * 60 * 60 * 1000L

  private val _attendance = MutableStateFlow<List<AttendanceRecord>>(emptyList())
  val attendance = _attendance.asStateFlow()
  val attendanceRecords = _attendance.asStateFlow()

  fun isAttendanceEditable(record: AttendanceRecord): Boolean {
    val elapsed = System.currentTimeMillis() - record.timestamp
    return elapsed >= 0 && elapsed <= ATTENDANCE_EDIT_WINDOW_MS
  }

  fun getAttendanceRemainingEditTime(record: AttendanceRecord): Pair<Long, Long> {
    val elapsed = System.currentTimeMillis() - record.timestamp
    val remainingMs = ATTENDANCE_EDIT_WINDOW_MS - elapsed
    if (remainingMs <= 0) return 0L to 0L
    val hours = remainingMs / (60 * 60 * 1000L)
    val minutes = (remainingMs % (60 * 60 * 1000L)) / (60 * 1000L)
    return hours to minutes
  }

  fun getAttendanceForDate(dateStr: String): List<AttendanceRecord> {
    return _attendance.value.filter { it.date == dateStr }
  }

  fun getAttendanceByBatch(batchNameOrId: String, dateStr: String? = null): List<AttendanceRecord> {
    val cleanBatch = batchNameOrId.trim()
    if (cleanBatch.isBlank()) return emptyList()
    val role = _currentRole.value
    val isAdmin = role == UserRole.ADMIN || _currentUserAccount.value?.role == UserRole.ADMIN
    if (!isAdmin && role == UserRole.TEACHER && !isTeacherAssignedToBatch(cleanBatch)) {
      return emptyList()
    }
    val batchObj = _batches.value.find {
      it.id.equals(cleanBatch, ignoreCase = true) || it.name.equals(cleanBatch, ignoreCase = true)
    }
    return _attendance.value.filter { rec ->
      val batchMatches = rec.batchId.equals(cleanBatch, ignoreCase = true) ||
        rec.batchName.equals(cleanBatch, ignoreCase = true) ||
        (batchObj != null && (rec.batchId.equals(batchObj.id, ignoreCase = true) || rec.batchName.equals(batchObj.name, ignoreCase = true)))
      val dateMatches = dateStr.isNullOrBlank() || rec.date == dateStr
      batchMatches && dateMatches
    }
  }

  fun getAttendanceForStudent(studentId: String): List<AttendanceRecord> {
    val cleanStudentId = studentId.trim()
    if (cleanStudentId.isBlank()) return emptyList()
    val role = _currentRole.value
    val isAdmin = role == UserRole.ADMIN || _currentUserAccount.value?.role == UserRole.ADMIN
    if (!isAdmin) {
      when (role) {
        UserRole.STUDENT -> {
          val activeStu = getActiveStudentForCurrentSession()
          if (activeStu == null || !activeStu.id.equals(cleanStudentId, ignoreCase = true)) {
            return emptyList()
          }
        }
        UserRole.PARENT -> {
          val linkedIds = getLinkedStudentsForCurrentParent().map { it.id }
          if (linkedIds.none { it.equals(cleanStudentId, ignoreCase = true) }) {
            return emptyList()
          }
        }
        UserRole.TEACHER -> {
          val allowedStudents = getStudentsForCurrentTeacher().map { it.id }
          if (allowedStudents.none { it.equals(cleanStudentId, ignoreCase = true) }) {
            return emptyList()
          }
        }
        else -> {}
      }
    }
    return _attendance.value.filter { it.studentId.equals(cleanStudentId, ignoreCase = true) }
  }

  fun calculateStudentAttendancePercent(studentId: String, records: List<AttendanceRecord> = _attendance.value): Int {
    val stuRecords = records.filter { it.studentId.equals(studentId, ignoreCase = true) }
    if (stuRecords.isEmpty()) {
      return _students.value.find { it.id.equals(studentId, ignoreCase = true) }?.attendancePercent ?: 0
    }
    val presentOrLate = stuRecords.count {
      it.status.equals("Present", ignoreCase = true) || it.status.equals("Late", ignoreCase = true)
    }
    return ((presentOrLate * 100f) / stuRecords.size).toInt().coerceIn(0, 100)
  }

  fun calculateStudentAttendancePercentage(studentId: String, records: List<AttendanceRecord> = _attendance.value): Int {
    return calculateStudentAttendancePercent(studentId, records)
  }

  fun calculateConsecutiveAbsences(studentId: String, records: List<AttendanceRecord> = _attendance.value): Int {
    val stuRecords = records
      .filter { it.studentId.equals(studentId, ignoreCase = true) }
      .sortedByDescending { it.timestamp }
    var streak = 0
    for (rec in stuRecords) {
      if (rec.status.equals("Absent", ignoreCase = true)) {
        streak++
      } else {
        break
      }
    }
    return streak
  }

  fun getAttendanceForDateComponents(day: Int, month: Int, year: Int): List<AttendanceRecord> {
    return _attendance.value.filter { record ->
      if (record.day > 0 && record.month > 0 && record.year > 0) {
        record.day == day && record.month == month && record.year == year
      } else {
        val parsed = com.example.data.db.AttendanceRecordEntity.parseDateAndTimeString(record.date, record.timestamp)
        parsed.day == day && parsed.month == month && parsed.year == year
      }
    }
  }

  private fun recalculateAllStudentsAttendanceFromRecords(records: List<AttendanceRecord>) {
    if (records.isEmpty()) return
    val byStudent = records.groupBy { it.studentId }
    _students.update { list ->
      list.map { stu ->
        val stuRecords = byStudent[stu.id]
        if (!stuRecords.isNullOrEmpty()) {
          val presentOrLate = stuRecords.count {
            it.status.equals("Present", ignoreCase = true) || it.status.equals("Late", ignoreCase = true)
          }
          val pct = ((presentOrLate * 100f) / stuRecords.size).toInt().coerceIn(0, 100)
          if (stu.attendancePercent != pct) stu.copy(attendancePercent = pct) else stu
        } else stu
      }
    }
  }

  private fun recalculateStudentAttendancePercentage(studentId: String) {
    val stuRecords = _attendance.value.filter { it.studentId.equals(studentId, ignoreCase = true) }
    if (stuRecords.isEmpty()) return
    val presentOrLate = stuRecords.count {
      it.status.equals("Present", ignoreCase = true) || it.status.equals("Late", ignoreCase = true)
    }
    val pct = ((presentOrLate * 100f) / stuRecords.size).toInt().coerceIn(0, 100)
    var updatedStu: Student? = null
    _students.update { list ->
      list.map { s ->
        if (s.id.equals(studentId, ignoreCase = true)) {
          val copy = s.copy(attendancePercent = pct)
          updatedStu = copy
          copy
        } else s
      }
    }
    updatedStu?.let { stu ->
      repoScope.launch {
        studentBatchRepo?.updateStudent(stu)
      }
    }
  }

  fun updateAttendanceRecord(recordId: String, newStatus: String, reason: String? = null, recordedBy: String? = null): Boolean {
    if (!checkOnlineOrBlock("modify attendance record")) return false
    val role = _currentRole.value
    val isAdmin = role == UserRole.ADMIN || _currentUserAccount.value?.role == UserRole.ADMIN
    if (!isAdmin && role != UserRole.TEACHER) return false

    val targetRecord = _attendance.value.find { it.id == recordId } ?: return false
    if (!isAdmin && !isTeacherAssignedToBatch(targetRecord.batchId.ifBlank { targetRecord.batchName })) {
      return false
    }

    var success = false
    var updatedItem: AttendanceRecord? = null
    val finalRecorder = recordedBy ?: _currentUserIdentifier.value.ifBlank { "system_fsi" }
    val now = System.currentTimeMillis()

    _attendance.update { current ->
      current.map { record ->
        if (record.id == recordId) {
          if (isAttendanceEditable(record)) {
            success = true
            val updated = record.copy(
              status = newStatus,
              lastModifiedTimestamp = now,
              reason = reason,
              recordedBy = finalRecorder
            )
            updatedItem = updated
            updated
          } else {
            record
          }
        } else {
          record
        }
      }
    }

    if (success && updatedItem != null) {
      recalculateStudentAttendancePercentage(updatedItem!!.studentId)
      repoScope.launch {
        studentBatchRepo?.updateAttendance(updatedItem!!)
      }
      NetworkConnectivityManager.markDataUpdatedOnline()
    }
    return success
  }

  fun saveSingleAttendance(
    studentId: String,
    studentName: String,
    batchName: String,
    date: String,
    status: String,
    reason: String? = null,
    recordedBy: String? = null
  ): Boolean {
    if (!checkOnlineOrBlock("record attendance")) return false
    val role = _currentRole.value
    val isAdmin = role == UserRole.ADMIN || _currentUserAccount.value?.role == UserRole.ADMIN
    if (!isAdmin && role != UserRole.TEACHER) return false
    if (!isAdmin && !isTeacherAssignedToBatch(batchName)) return false

    val finalRecorder = recordedBy ?: _currentUserIdentifier.value.ifBlank { "system_fsi" }
    var allowed = true
    val now = System.currentTimeMillis()
    val parsed = com.example.data.db.AttendanceRecordEntity.parseDateAndTimeString(date, now)
    var savedRecord: AttendanceRecord? = null
    val resolvedBatchId = _batches.value.find {
      it.name.equals(batchName, ignoreCase = true) || it.id.equals(batchName, ignoreCase = true)
    }?.id ?: SeedData.batchIdForName(batchName)

    _attendance.update { current ->
      val existing = current.find { it.studentId == studentId && it.date == date && (it.batchName.equals(batchName, ignoreCase = true) || it.batchId.equals(resolvedBatchId, ignoreCase = true)) }
      if (existing != null) {
        if (isAttendanceEditable(existing)) {
          current.map { record ->
            if (record.id == existing.id) {
              val updated = record.copy(
                status = status,
                lastModifiedTimestamp = now,
                reason = reason,
                recordedBy = finalRecorder,
                batchId = resolvedBatchId
              )
              savedRecord = updated
              updated
            } else record
          }
        } else {
          allowed = false
          current
        }
      } else {
        val recordId = "ATT_${studentId}_${batchName.replace(" ", "_")}_${date.replace("/", "_")}"
        val newRecord = AttendanceRecord(
          id = recordId,
          date = date,
          day = parsed.day,
          month = parsed.month,
          monthName = parsed.monthName,
          year = parsed.year,
          time = parsed.time,
          studentId = studentId,
          studentName = studentName,
          batchName = batchName,
          status = status,
          timestamp = now,
          lastModifiedTimestamp = now,
          reason = reason,
          recordedBy = finalRecorder,
          batchId = resolvedBatchId,
          instituteId = "FSI_MAIN"
        )
        savedRecord = newRecord
        current + newRecord
      }
    }

    if (allowed && savedRecord != null) {
      recalculateStudentAttendancePercentage(studentId)
      repoScope.launch {
        studentBatchRepo?.insertAttendance(savedRecord!!)
      }
      NetworkConnectivityManager.markDataUpdatedOnline()
    }

    if (allowed && status == "Absent") {
      val stu = _students.value.find { it.id == studentId }
      if (stu != null) {
        val reasonSfx = if (!reason.isNullOrBlank()) " due to $reason" else ""
        addNotification(
          NotificationItem(
            id = "NOT_ABS_${System.currentTimeMillis()}",
            title = "⚠️ Automated Absence Alert",
            message = "Dear Parent (${stu.parentName}), ${stu.name} was marked ABSENT today ($date) in ${batchName}${reasonSfx}. Please contact office.",
            time = getCurrentTimeStr(),
            type = "Absence",
            recipientRole = "Parent",
            targetStudentId = stu.id,
            targetBatchName = batchName,
            targetBatchId = resolvedBatchId
          )
        )
      }
    }

    return allowed
  }

  fun deleteAttendanceRecord(recordId: String): Boolean {
    if (!checkOnlineOrBlock("delete attendance record")) return false
    val role = _currentRole.value
    val isAdmin = role == UserRole.ADMIN || _currentUserAccount.value?.role == UserRole.ADMIN
    val targetRecord = _attendance.value.find { it.id == recordId } ?: return false
    if (!isAdmin) {
      if (role != UserRole.TEACHER || !isTeacherAssignedToBatch(targetRecord.batchId.ifBlank { targetRecord.batchName })) {
        return false
      }
    }
    val targetStudentId = targetRecord.studentId
    _attendance.update { list -> list.filter { it.id != recordId } }
    recalculateStudentAttendancePercentage(targetStudentId)
    repoScope.launch {
      studentBatchRepo?.deleteAttendance(recordId)
    }
    NetworkConnectivityManager.markDataUpdatedOnline()
    return true
  }

  fun purgeExpiredAttendance(cutoffTimestamp: Long) {
    _attendance.update { list -> list.filter { it.timestamp >= cutoffTimestamp } }
    repoScope.launch {
      studentBatchRepo?.purgeExpiredAttendance(cutoffTimestamp)
    }
  }

  fun submitBatchAttendance(batchName: String, statusMap: Map<String, String>, recordedBy: String? = null): Boolean {
    if (!checkOnlineOrBlock("record batch attendance")) return false
    val role = _currentRole.value
    val isAdmin = role == UserRole.ADMIN || _currentUserAccount.value?.role == UserRole.ADMIN
    if (!isAdmin && role != UserRole.TEACHER) return false
    if (!isAdmin && !isTeacherAssignedToBatch(batchName)) return false

    val date = getCurrentDateStr()
    statusMap.forEach { (stuId, st) ->
      val stuName = _students.value.find { it.id == stuId }?.name ?: "Student"
      saveSingleAttendance(stuId, stuName, batchName, date, st, recordedBy = recordedBy)
    }
    return true
  }

  // Tests & CBT Assessments
  private val _tests = MutableStateFlow<List<TestRecord>>(SeedData.getDefaultTestRecords())
  val tests = _tests.asStateFlow()

  private val _studentTestAttempts = MutableStateFlow<List<StudentTestAttempt>>(emptyList())
  val studentTestAttempts = _studentTestAttempts.asStateFlow()

  fun getAttemptsForCurrentStudent(student: Student? = null): List<StudentTestAttempt> {
    val activeStu = student ?: getActiveStudentForCurrentSession() ?: return _studentTestAttempts.value
    return _studentTestAttempts.value.filter {
      it.studentId.equals(activeStu.id, ignoreCase = true) ||
        (it.studentId.isBlank() && it.studentName.equals(activeStu.name, ignoreCase = true))
    }
  }

  fun submitStudentTestAttempt(
    testId: String,
    testName: String = "CBT Online Assessment",
    subject: String = "General",
    answers: Map<String, Int>,
    durationSeconds: Int,
    studentIdOverride: String? = null
  ): StudentTestAttempt {
    val existingAttempt = submitCbtTestAttempt(
      testId = testId,
      answers = answers,
      timeSpentSeconds = durationSeconds,
      studentIdOverride = studentIdOverride
    )
    if (existingAttempt != null) return existingAttempt

    val stu = if (!studentIdOverride.isNullOrBlank()) {
      _students.value.find { it.id.equals(studentIdOverride, ignoreCase = true) }
    } else {
      getActiveStudentForCurrentSession()
    }
    val studentId = stu?.id ?: studentIdOverride ?: "STU001"
    val studentName = stu?.name ?: "Student"
    val testQuestions = getQuestionsForTest(testId)
    var correctCount = 0
    var wrongCount = 0
    var unattemptedCount = 0
    testQuestions.forEach { q ->
      val selected = answers[q.id]
      if (selected == null) {
        unattemptedCount++
      } else if (selected == q.correctOption) {
        correctCount++
      } else {
        wrongCount++
      }
    }
    val maxMarks = (testQuestions.size * 4).coerceAtLeast(1)
    val score = ((correctCount * 4) - wrongCount).coerceIn(0, maxMarks)
    val now = System.currentTimeMillis()
    val fallbackAttempt = StudentTestAttempt(
      attemptId = "ATTEMPT_${studentId}_${testId}_$now",
      testId = testId,
      testName = testName,
      studentId = studentId,
      studentName = studentName,
      instituteId = stu?.instituteId?.ifBlank { "FSI_MAIN" } ?: "FSI_MAIN",
      answers = answers,
      score = score,
      totalMarks = maxMarks,
      correctCount = correctCount,
      wrongCount = wrongCount,
      unattemptedCount = unattemptedCount,
      timeSpentSeconds = durationSeconds,
      status = "Submitted",
      startedAt = now - (durationSeconds * 1000L),
      submittedAt = now
    )
    _studentTestAttempts.update { list ->
      listOf(fallbackAttempt) + list.filterNot { it.studentId == studentId && it.testId == testId }
    }
    repoScope.launch {
      studentBatchRepo?.insertStudentAttempt(fallbackAttempt)
    }
    return fallbackAttempt
  }

  fun createTest(t: TestRecord): Boolean {
    if (!checkOnlineOrBlock("schedule new test")) return false
    val role = _currentRole.value
    val isAdmin = role == UserRole.ADMIN || _currentUserAccount.value?.role == UserRole.ADMIN
    if (!isAdmin && role != UserRole.TEACHER) return false
    if (!isAdmin && t.batch.isNotBlank() && !isTeacherAssignedToBatch(t.batchId.ifBlank { t.batch })) return false

    val resolvedBatchId = t.batchId.ifBlank { SeedData.batchIdForName(t.batch) }
    val resolvedQuestionIds = t.questionIds.ifEmpty {
      _questions.value.filter { q ->
        q.subject.equals(t.subject, ignoreCase = true) || t.subject.contains(q.subject, ignoreCase = true)
      }.map { it.id }.ifEmpty { _questions.value.map { it.id } }
    }
    val normalizedTest = t.copy(
      batchId = resolvedBatchId,
      questionIds = resolvedQuestionIds,
      instituteId = t.instituteId.ifBlank { "FSI_MAIN" }
    )
    _tests.update { listOf(normalizedTest) + it }
    repoScope.launch {
      studentBatchRepo?.insertTest(normalizedTest)
    }
    NetworkConnectivityManager.markDataUpdatedOnline()
    addNotification(
      NotificationItem(
        id = "NOT_TST_${System.currentTimeMillis()}",
        title = "📅 Upcoming Test Announced",
        message = "${normalizedTest.testName} (${normalizedTest.subject}) scheduled for ${normalizedTest.date} for batch ${normalizedTest.batch}.",
        time = getCurrentTimeStr(),
        type = "Test",
        recipientRole = "Student",
        targetBatchName = normalizedTest.batch,
        targetBatchId = resolvedBatchId
      )
    )
    return true
  }

  fun getQuestionsForTest(testId: String): List<QuestionItem> {
    val allQ = _questions.value.ifEmpty { SeedData.getDefaultQuestions() }
    val test = _tests.value.find { it.id == testId }
    if (test != null) {
      if (test.questionIds.isNotEmpty()) {
        val byIds = allQ.filter { q -> test.questionIds.contains(q.id) }
        if (byIds.isNotEmpty()) return byIds
      }
      val byTestId = allQ.filter { it.testId == testId }
      if (byTestId.isNotEmpty()) return byTestId
      val bySubject = allQ.filter { q ->
        q.subject.equals(test.subject, ignoreCase = true) ||
          test.subject.contains(q.subject, ignoreCase = true) ||
          (test.subjectId.isNotBlank() && q.subjectId == test.subjectId)
      }
      if (bySubject.isNotEmpty()) return bySubject
    }
    return allQ
  }

  fun submitCbtTestAttempt(
    testId: String,
    answers: Map<String, Int>,
    timeSpentSeconds: Int,
    studentIdOverride: String? = null
  ): StudentTestAttempt? {
    if (!checkOnlineOrBlock("submit CBT test attempt")) return null
    val student = if (!studentIdOverride.isNullOrBlank()) {
      _students.value.find { it.id.equals(studentIdOverride, ignoreCase = true) }
    } else {
      getActiveStudentForCurrentSession()
    } ?: return null

    val test = _tests.value.find { it.id == testId } ?: _tests.value.firstOrNull()
    val resolvedTestId = test?.id ?: testId.ifBlank { "TST_12S_01" }
    val resolvedTestName = test?.testName ?: "CBT Online Assessment"
    val testQuestions = getQuestionsForTest(resolvedTestId)
    if (testQuestions.isEmpty()) return null

    var correctCount = 0
    var wrongCount = 0
    var unattemptedCount = 0
    testQuestions.forEach { q ->
      val selected = answers[q.id]
      if (selected == null) {
        unattemptedCount++
      } else if (selected == q.correctOption) {
        correctCount++
      } else {
        wrongCount++
      }
    }

    val rawScore = ((correctCount * 4) - (wrongCount * 1)).coerceAtLeast(0)
    val rawMax = (testQuestions.size * 4).coerceAtLeast(1)
    val totalMarks = test?.totalMarks ?: rawMax
    val scaledScore = if (totalMarks != rawMax) {
      ((rawScore.toFloat() / rawMax.toFloat()) * totalMarks).toInt().coerceIn(0, totalMarks)
    } else {
      rawScore
    }

    val now = System.currentTimeMillis()
    val attempt = StudentTestAttempt(
      attemptId = "ATTEMPT_${student.id}_${resolvedTestId}_$now",
      testId = resolvedTestId,
      testName = resolvedTestName,
      studentId = student.id,
      studentName = student.name,
      instituteId = student.instituteId.ifBlank { "FSI_MAIN" },
      answers = answers,
      score = scaledScore,
      totalMarks = totalMarks,
      correctCount = correctCount,
      wrongCount = wrongCount,
      unattemptedCount = unattemptedCount,
      timeSpentSeconds = timeSpentSeconds,
      status = "Submitted",
      startedAt = now - (timeSpentSeconds * 1000L),
      submittedAt = now
    )

    _studentTestAttempts.update { list ->
      listOf(attempt) + list.filterNot { it.studentId == student.id && it.testId == resolvedTestId }
    }
    repoScope.launch {
      studentBatchRepo?.insertStudentAttempt(attempt)
    }

    // Update the authoritative TestRecord studentMarks so Teacher, Admin, Student, and Parent see the CBT result
    if (test != null) {
      val updatedMarks = test.studentMarks.toMutableMap().apply {
        put(student.id, scaledScore)
      }
      saveTestMarksInternal(test.id, updatedMarks)
    }

    return attempt
  }

  fun saveTestMarks(testId: String, marksMap: Map<String, Int>): Boolean {
    if (!checkOnlineOrBlock("submit test marks")) return false
    val role = _currentRole.value
    val isAdmin = role == UserRole.ADMIN || _currentUserAccount.value?.role == UserRole.ADMIN
    val targetTest = _tests.value.find { it.id == testId }
    if (!isAdmin) {
      if (role != UserRole.TEACHER) return false
      if (targetTest != null && !isTeacherAssignedToBatch(targetTest.batchId.ifBlank { targetTest.batch })) {
        return false
      }
    }
    saveTestMarksInternal(testId, marksMap)
    return true
  }

  private fun saveTestMarksInternal(testId: String, marksMap: Map<String, Int>) {
    val validStudentIds = _students.value.map { it.id }.toSet()
    val sanitizedMarks = if (validStudentIds.isNotEmpty()) {
      marksMap.filterKeys { validStudentIds.contains(it) }
    } else {
      marksMap
    }
    val classScores = sanitizedMarks.values.toList()
    val avgScore = if (classScores.isNotEmpty()) classScores.average().toInt() else 0
    val targetTest = _tests.value.find { it.id == testId }
    val testName = targetTest?.testName ?: "Batch Assessment"
    val maxMarks = (targetTest?.totalMarks ?: 300).coerceAtLeast(1)
    val dynamicAiSuggestion = AiService.generatePerformanceInsights(testName, avgScore, maxMarks)

    var updatedTestRecord: TestRecord? = null
    _tests.update { list ->
      list.map { test ->
        if (test.id == testId) {
          val copy = test.copy(studentMarks = sanitizedMarks, aiSuggestion = dynamicAiSuggestion)
          updatedTestRecord = copy
          copy
        } else test
      }
    }
    updatedTestRecord?.let { tst ->
      repoScope.launch {
        studentBatchRepo?.updateTest(tst)
      }
    }

    // Synchronize student performance analytics (overallAvg, recentScores, rank)
    recalculateStudentAcademicStats(sanitizedMarks, targetTest?.subject ?: "Assessment", maxMarks)

    NetworkConnectivityManager.markDataUpdatedOnline()
    sanitizedMarks.forEach { (stuId, score) ->
      val pct = (score * 100) / maxMarks
      if (pct < 45) {
        val stu = _students.value.find { it.id == stuId }
        if (stu != null) {
          addNotification(
            NotificationItem(
              id = "NOT_LOW_${System.currentTimeMillis()}_$stuId",
              title = "📉 Academic Performance Alert",
              message = "${stu.name} scored $score/$maxMarks ($pct%) in $testName. Teacher review recommended.",
              time = getCurrentTimeStr(),
              type = "Test",
              recipientRole = "Parent",
              targetStudentId = stu.id,
              targetBatchName = stu.batch
            )
          )
        }
      }
    }
  }

  private fun recalculateStudentAcademicStats(marksMap: Map<String, Int>, subjectName: String, maxMarks: Int) {
    val sortedEntries = marksMap.entries.sortedByDescending { it.value }
    val rankMap = sortedEntries.mapIndexed { idx, entry -> entry.key to (idx + 1) }.toMap()

    val updatedStudents = mutableListOf<Student>()
    _students.update { list ->
      list.map { stu ->
        val score = marksMap[stu.id]
        if (score != null) {
          val pctScore = ((score * 100f) / maxMarks.coerceAtLeast(1)).toInt().coerceIn(0, 100)
          val updatedRecent = (listOf(subjectName to pctScore) + stu.recentScores.filterNot { it.first.equals(subjectName, ignoreCase = true) }).take(5)
          val newAvg = if (updatedRecent.isNotEmpty()) updatedRecent.map { it.second }.average().toInt() else stu.overallAvg
          val strongest = updatedRecent.maxByOrNull { it.second }?.first ?: stu.strongestSubject
          val weakest = updatedRecent.minByOrNull { it.second }?.first ?: stu.weakestSubject
          val newRank = rankMap[stu.id] ?: stu.rank
          val copy = stu.copy(
            overallAvg = newAvg,
            rank = newRank,
            recentScores = updatedRecent,
            strongestSubject = strongest,
            weakestSubject = weakest
          )
          updatedStudents.add(copy)
          copy
        } else stu
      }
    }
    if (updatedStudents.isNotEmpty()) {
      repoScope.launch {
        updatedStudents.forEach { studentBatchRepo?.updateStudent(it) }
      }
    }
  }

  // Fees & Revenue — Authoritative Unified Ledger
  private val _fees = MutableStateFlow<List<FeeRecord>>(SeedData.getDefaultFeeRecords())
  val fees = _fees.asStateFlow()

  private val _feePayments = MutableStateFlow<List<StudentFeePayment>>(SeedData.getDefaultFeePayments())
  val feePayments = _feePayments.asStateFlow()

  private fun syncStudentFeeStatus(studentId: String) {
    if (studentId.isBlank()) return
    val studentInvoices = _fees.value.filter { it.studentId.equals(studentId, ignoreCase = true) }
    if (studentInvoices.isEmpty()) return
    val totalPending = studentInvoices.sumOf { it.pendingAmount }
    val newFeeStatus = if (totalPending <= 0) "Paid" else "Pending"
    var updatedStudent: Student? = null
    _students.update { list ->
      list.map { stu ->
        if (stu.id.equals(studentId, ignoreCase = true) && stu.feeStatus != newFeeStatus) {
          val copy = stu.copy(feeStatus = newFeeStatus)
          updatedStudent = copy
          copy
        } else stu
      }
    }
    updatedStudent?.let { stu ->
      repoScope.launch {
        studentBatchRepo?.updateStudent(stu)
      }
    }
  }

  fun recordFeePayment(recordId: String, payAmt: Int, mode: String = "UPI", remarks: String = "Installment payment") {
    if (!checkOnlineOrBlock("record fee transaction")) return
    if (payAmt <= 0) return
    var targetStudentName = ""
    var targetStudentId = ""
    var targetMonth = "August 2026"
    var targetInvoiceId = recordId
    var updatedInvoice: FeeRecord? = null

    _fees.update { list ->
      list.map { rec ->
        if (rec.id == recordId) {
          targetStudentName = rec.studentName
          targetStudentId = rec.studentId
          targetMonth = rec.month
          targetInvoiceId = rec.id
          val newPaid = (rec.paidAmount + payAmt).coerceAtMost(rec.feeAmount)
          val newPending = (rec.feeAmount - newPaid).coerceAtLeast(0)
          val newStatus = if (newPending == 0) "Paid" else {
            try {
              val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
              val dueDateObj = sdf.parse(rec.dueDate)
              if (dueDateObj != null && dueDateObj.before(Date())) {
                "Overdue"
              } else {
                "Pending"
              }
            } catch (e: Exception) {
              "Pending"
            }
          }
          val copy = rec.copy(paidAmount = newPaid, pendingAmount = newPending, paymentStatus = newStatus)
          updatedInvoice = copy
          copy
        } else rec
      }
    }

    updatedInvoice?.let { inv ->
      syncStudentFeeStatus(inv.studentId)
      repoScope.launch {
        studentBatchRepo?.updateFeeInvoice(inv)
      }
    }

    if (targetStudentId.isNotBlank() || targetStudentName.isNotBlank()) {
      val foundStudent = _students.value.find {
        (targetStudentId.isNotBlank() && it.id.equals(targetStudentId, ignoreCase = true)) ||
          (targetStudentId.isBlank() && it.name.equals(targetStudentName, ignoreCase = true))
      }
      val resolvedStudentId = foundStudent?.id ?: targetStudentId.ifBlank { "STU_${System.currentTimeMillis() % 1000}" }
      val resolvedStudentName = foundStudent?.name ?: targetStudentName
      val now = System.currentTimeMillis()
      val paymentItem = StudentFeePayment(
        id = "PAY_${now}_${(100..999).random()}",
        studentId = resolvedStudentId,
        studentName = resolvedStudentName,
        amount = payAmt.toDouble(),
        paymentDate = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date(now)),
        paymentStatus = "Paid",
        feeType = "Monthly Tuition",
        paymentMode = mode,
        receiptNo = "REC-2026-${now % 100000}",
        month = targetMonth,
        remarks = remarks,
        recordedBy = _currentUserIdentifier.value.ifBlank {
          if (_currentRole.value == UserRole.ADMIN) "Admin" else "Parent Portal"
        },
        timestamp = now,
        invoiceId = targetInvoiceId,
        instituteId = foundStudent?.instituteId?.ifBlank { "FSI_MAIN" } ?: "FSI_MAIN",
        transactionId = "TXN-${now % 1000000}"
      )
      recordStudentFeePayment(paymentItem, syncInvoice = false)
    }
    NetworkConnectivityManager.markDataUpdatedOnline()
  }

  fun recordStudentFeePayment(payment: StudentFeePayment, syncInvoice: Boolean = true) {
    if (!checkOnlineOrBlock("record fee payment")) return
    // Resolve stable studentId first; never rely solely on studentName when studentId is known
    val matchedStu = _students.value.find {
      (payment.studentId.isNotBlank() && it.id.equals(payment.studentId, ignoreCase = true)) ||
        (payment.studentId.isBlank() && it.name.equals(payment.studentName, ignoreCase = true))
    }
    val resolvedStudentId = matchedStu?.id ?: payment.studentId
    val resolvedStudentName = matchedStu?.name ?: payment.studentName
    val normalizedPayment = payment.copy(
      studentId = resolvedStudentId,
      studentName = resolvedStudentName,
      instituteId = payment.instituteId.ifBlank { matchedStu?.instituteId ?: "FSI_MAIN" },
      transactionId = payment.transactionId.ifBlank { "TXN-${payment.timestamp % 1000000}" },
      recordedBy = payment.recordedBy.ifBlank { _currentUserIdentifier.value.ifBlank { "Admin" } }
    )

    _feePayments.update { listOf(normalizedPayment) + it.filter { p -> p.id != normalizedPayment.id } }
    repoScope.launch {
      studentBatchRepo?.insertFeePayment(normalizedPayment)
    }

    // Keep FeeRecord invoice ledger synchronized when a direct receipt is logged
    if (syncInvoice && normalizedPayment.paymentStatus.equals("Paid", ignoreCase = true)) {
      var syncedInvoice: FeeRecord? = null
      _fees.update { list ->
        val matchIdx = list.indexOfFirst { rec ->
          (normalizedPayment.invoiceId.isNotBlank() && rec.id == normalizedPayment.invoiceId) ||
            (resolvedStudentId.isNotBlank() && rec.studentId.equals(resolvedStudentId, ignoreCase = true))
        }
        if (matchIdx >= 0) {
          list.mapIndexed { idx, rec ->
            if (idx == matchIdx) {
              val newPaid = (rec.paidAmount + normalizedPayment.amount.toInt()).coerceAtMost(rec.feeAmount)
              val newPending = (rec.feeAmount - newPaid).coerceAtLeast(0)
              val newStatus = if (newPending == 0) "Paid" else rec.paymentStatus
              val copy = rec.copy(paidAmount = newPaid, pendingAmount = newPending, paymentStatus = newStatus)
              syncedInvoice = copy
              copy
            } else rec
          }
        } else list
      }
      syncedInvoice?.let { inv ->
        syncStudentFeeStatus(inv.studentId)
        repoScope.launch {
          studentBatchRepo?.updateFeeInvoice(inv)
        }
      }
    }

    NetworkConnectivityManager.markDataUpdatedOnline()

    addNotification(
      NotificationItem(
        id = "N_PAY_${System.currentTimeMillis()}",
        title = "💳 Fee Payment Received",
        message = "Payment of ₹${normalizedPayment.amount.toInt()} received for ${normalizedPayment.studentName} (${normalizedPayment.feeType}, ${normalizedPayment.paymentMode}). Receipt: ${normalizedPayment.receiptNo}.",
        time = "Just now",
        type = "Fee",
        recipientRole = "All",
        targetStudentId = normalizedPayment.studentId
      )
    )
  }

  fun updateStudentFeePayment(payment: StudentFeePayment) {
    if (!checkOnlineOrBlock("update fee payment record")) return
    _feePayments.update { list ->
      list.map { if (it.id == payment.id) payment else it }
    }
    repoScope.launch {
      studentBatchRepo?.updateFeePayment(payment)
    }
    NetworkConnectivityManager.markDataUpdatedOnline()
  }

  fun deleteStudentFeePayment(paymentId: String) {
    if (!checkOnlineOrBlock("delete fee payment record")) return
    _feePayments.update { list -> list.filter { it.id != paymentId } }
    repoScope.launch {
      studentBatchRepo?.deleteFeePayment(paymentId)
    }
    NetworkConnectivityManager.markDataUpdatedOnline()
  }

  fun addFeeRecord(
    studentName: String,
    feeAmount: Int,
    dueDate: String,
    paidAmount: Int,
    month: String,
    studentId: String = ""
  ) {
    if (!checkOnlineOrBlock("create fee invoice")) return
    val matchedStu = _students.value.find {
      (studentId.isNotBlank() && it.id.equals(studentId, ignoreCase = true)) ||
        (studentId.isBlank() && it.name.equals(studentName, ignoreCase = true))
    }
    val resolvedStudentId = matchedStu?.id ?: studentId
    val resolvedBatchName = matchedStu?.batch ?: ""
    val resolvedBatchId = matchedStu?.batchId?.ifBlank { SeedData.batchIdForName(resolvedBatchName) } ?: ""
    val pendingAmount = (feeAmount - paidAmount).coerceAtLeast(0)
    val paymentStatus = if (pendingAmount == 0) "Paid" else {
      try {
        val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        val dueDateObj = sdf.parse(dueDate)
        if (dueDateObj != null && dueDateObj.before(Date())) {
          "Overdue"
        } else {
          "Pending"
        }
      } catch (e: Exception) {
        "Pending"
      }
    }

    val newRec = FeeRecord(
      id = "F_${System.currentTimeMillis()}",
      studentName = matchedStu?.name ?: studentName,
      feeAmount = feeAmount,
      dueDate = dueDate,
      paidAmount = paidAmount,
      pendingAmount = pendingAmount,
      paymentStatus = paymentStatus,
      month = month,
      remindedCount = 0,
      lastReminded = "",
      studentId = resolvedStudentId,
      batchName = resolvedBatchName,
      batchId = resolvedBatchId,
      instituteId = matchedStu?.instituteId?.ifBlank { "FSI_MAIN" } ?: "FSI_MAIN"
    )

    _fees.update { list -> list + newRec }
    syncStudentFeeStatus(resolvedStudentId)
    repoScope.launch {
      studentBatchRepo?.insertFeeInvoice(newRec)
    }
    NetworkConnectivityManager.markDataUpdatedOnline()
  }

  fun sendFeeReminder(recordId: String) {
    if (!checkOnlineOrBlock("dispatch fee reminder")) return
    val currentTimeStr = SimpleDateFormat("MMM dd, hh:mm a", Locale.getDefault()).format(Date())
    var studentName = ""
    var studentId = ""
    var month = ""
    var pendingAmt = 0
    var dueDate = ""
    var updatedInvoice: FeeRecord? = null

    _fees.update { list ->
      list.map { rec ->
        if (rec.id == recordId) {
          studentName = rec.studentName
          studentId = rec.studentId
          month = rec.month
          pendingAmt = rec.pendingAmount
          dueDate = rec.dueDate
          val copy = rec.copy(
            remindedCount = rec.remindedCount + 1,
            lastReminded = currentTimeStr
          )
          updatedInvoice = copy
          copy
        } else rec
      }
    }

    updatedInvoice?.let { inv ->
      repoScope.launch {
        studentBatchRepo?.updateFeeInvoice(inv)
      }
    }

    if (studentName.isNotEmpty()) {
      addNotification(
        NotificationItem(
          id = "N_FEE_${System.currentTimeMillis()}",
          title = "💳 Fee Payment Reminder",
          message = "Reminder for $studentName: The monthly fee of ₹$pendingAmt for $month is due on $dueDate. Please pay at the earliest.",
          time = "Just now",
          type = "Fee",
          recipientRole = "Parent",
          targetStudentId = studentId
        )
      )
    }
    NetworkConnectivityManager.markDataUpdatedOnline()
  }

  fun deleteFeeRecord(recordId: String) {
    if (!checkOnlineOrBlock("delete fee record")) return
    val targetStudentId = _fees.value.find { it.id == recordId }?.studentId ?: ""
    _fees.update { current ->
      current.filter { it.id != recordId }
    }
    if (targetStudentId.isNotBlank()) syncStudentFeeStatus(targetStudentId)
    repoScope.launch {
      studentBatchRepo?.deleteFeeInvoice(recordId)
    }
    NetworkConnectivityManager.markDataUpdatedOnline()
  }

  // Admission CRM Leads
  private val _leads = MutableStateFlow<List<LeadRecord>>(emptyList())
  val leads = _leads.asStateFlow()

  fun addLead(l: LeadRecord) {
    if (!checkOnlineOrBlock("record admission lead")) return
    _leads.update { listOf(l) + it }
    repoScope.launch {
      studentBatchRepo?.insertLead(l)
    }
    NetworkConnectivityManager.markDataUpdatedOnline()
  }

  fun updateLeadStatus(leadId: String, st: String) {
    if (!checkOnlineOrBlock("update lead status")) return
    var updatedLead: LeadRecord? = null
    _leads.update { list ->
      list.map {
        if (it.id == leadId) {
          val copy = it.copy(status = st)
          updatedLead = copy
          copy
        } else it
      }
    }
    updatedLead?.let { lead ->
      repoScope.launch {
        studentBatchRepo?.updateLead(lead)
      }
    }
    NetworkConnectivityManager.markDataUpdatedOnline()
  }

  // Online Admission Form Submissions
  private val _onlineForms = MutableStateFlow<List<OnlineFormSubmission>>(emptyList())
  val onlineForms = _onlineForms.asStateFlow()

  fun submitOnlineForm(form: OnlineFormSubmission) {
    if (!checkOnlineOrBlock("submit online admission form")) return
    _onlineForms.update { listOf(form) + it }
    repoScope.launch {
      studentBatchRepo?.insertOnlineForm(form)
    }
    NetworkConnectivityManager.markDataUpdatedOnline()
  }

  fun processOnlineForm(formId: String, approve: Boolean) {
    if (!checkOnlineOrBlock("process online admission application")) return
    var updatedForm: OnlineFormSubmission? = null
    _onlineForms.update { list ->
      list.map {
        if (it.id == formId) {
          val copy = it.copy(status = if (approve) "Approved" else "Rejected")
          updatedForm = copy
          copy
        } else it
      }
    }
    updatedForm?.let { f ->
      repoScope.launch {
        studentBatchRepo?.updateOnlineForm(f)
      }
      if (approve) {
        addLead(
          LeadRecord(
            id = "LD_${System.currentTimeMillis()}",
            name = f.name,
            mobile = f.mobile,
            className = f.className,
            stream = f.stream,
            source = "Online Form Portal",
            inquiryDate = getCurrentDateStr(),
            assignedCounselor = "Auto Assign",
            status = "New"
          )
        )
      }
    }
    NetworkConnectivityManager.markDataUpdatedOnline()
  }

  // Question Bank (Persisted in Room)
  private val _questions = MutableStateFlow<List<QuestionItem>>(SeedData.getDefaultQuestions())
  val questions = _questions.asStateFlow()
  val questionBank = _questions.asStateFlow()

  fun addQuestion(q: QuestionItem): Boolean {
    if (!checkOnlineOrBlock("add question to repository")) return false
    val role = _currentRole.value
    val isAdmin = role == UserRole.ADMIN || _currentUserAccount.value?.role == UserRole.ADMIN
    if (!isAdmin && role != UserRole.TEACHER) return false
    val normalizedQ = q.copy(
      subjectId = q.subjectId.ifBlank {
        _subjects.value.find { it.name.contains(q.subject, ignoreCase = true) }?.id ?: "SUB_PHY_12"
      },
      instituteId = q.instituteId.ifBlank { "FSI_MAIN" }
    )
    _questions.update { listOf(normalizedQ) + it.filterNot { existing -> existing.id == normalizedQ.id } }
    repoScope.launch {
      studentBatchRepo?.insertQuestion(normalizedQ)
    }
    NetworkConnectivityManager.markDataUpdatedOnline()
    return true
  }

  // Doubts (Persisted in Room, strictly attributed to authenticated student)
  private val _doubts = MutableStateFlow<List<DoubtItem>>(emptyList())
  val doubts = _doubts.asStateFlow()

  fun getDoubtsForCurrentStudent(student: Student? = null): List<DoubtItem> {
    val activeStu = student ?: getActiveStudentForCurrentSession() ?: return _doubts.value
    return _doubts.value.filter { d ->
      d.studentId.equals(activeStu.id, ignoreCase = true) ||
        (d.studentId.isBlank() && d.studentName.equals(activeStu.name, ignoreCase = true))
    }
  }

  fun submitStudentDoubt(
    subject: String,
    chapter: String,
    text: String,
    photoUrl: String? = null,
    studentIdOverride: String? = null,
    studentNameOverride: String? = null
  ): DoubtItem? {
    if (!checkOnlineOrBlock("submit academic doubt")) return null
    val isAdmin = _currentRole.value == UserRole.ADMIN || _currentUserAccount.value?.role == UserRole.ADMIN
    val activeStudent = if (!studentIdOverride.isNullOrBlank() && isAdmin) {
      _students.value.find { it.id.equals(studentIdOverride, ignoreCase = true) }
    } else {
      getActiveStudentForCurrentSession()
    }
    val studentId = activeStudent?.id ?: studentIdOverride ?: ""
    if (studentId.isBlank()) return null
    val studentName = activeStudent?.name ?: studentNameOverride ?: _currentUserIdentifier.value.ifBlank { "Student" }
    val batchName = activeStudent?.batch ?: ""
    val batchId = activeStudent?.batchId?.ifBlank { SeedData.batchIdForName(batchName) } ?: ""
    val subjectId = _subjects.value.find {
      it.name.equals(subject, ignoreCase = true) || it.name.contains(subject, ignoreCase = true)
    }?.id ?: "SUB_PHY_12"
    val now = System.currentTimeMillis()

    val newDoubt = DoubtItem(
      id = "DB_$now",
      studentName = studentName,
      subject = subject,
      chapter = chapter,
      questionText = text,
      date = getCurrentDateStr(),
      reply = null,
      status = "Pending",
      resolutionTime = null,
      photoUrl = photoUrl?.takeIf { it.isNotBlank() },
      studentId = studentId,
      batchName = batchName,
      repliedBy = "",
      instituteId = activeStudent?.instituteId?.ifBlank { "FSI_MAIN" } ?: "FSI_MAIN",
      subjectId = subjectId,
      batchId = batchId,
      createdAt = now,
      updatedAt = now
    )
    _doubts.update { listOf(newDoubt) + it }
    repoScope.launch {
      studentBatchRepo?.insertDoubt(newDoubt)
    }
    NetworkConnectivityManager.markDataUpdatedOnline()
    return newDoubt
  }

  fun replyToDoubt(doubtId: String, replyText: String): Boolean {
    if (!checkOnlineOrBlock("reply to student doubt")) return false
    val role = _currentRole.value
    val isAdmin = role == UserRole.ADMIN || _currentUserAccount.value?.role == UserRole.ADMIN
    if (!isAdmin && role != UserRole.TEACHER) return false

    val targetDoubt = _doubts.value.find { it.id == doubtId } ?: return false
    if (!isAdmin && targetDoubt.batchName.isNotBlank() && !isTeacherAssignedToBatch(targetDoubt.batchId.ifBlank { targetDoubt.batchName })) {
      return false
    }

    val responderName = if (role == UserRole.TEACHER) {
      getActiveTeacherForCurrentSession()?.name ?: _currentUserIdentifier.value.ifBlank { "Faculty" }
    } else {
      _currentUserAccount.value?.name ?: "Institute Administrator"
    }
    val now = System.currentTimeMillis()
    var updatedDoubt: DoubtItem? = null
    _doubts.update { list ->
      list.map {
        if (it.id == doubtId) {
          val copy = it.copy(
            reply = replyText,
            status = "Resolved",
            resolutionTime = "12 Mins",
            repliedBy = responderName,
            updatedAt = now
          )
          updatedDoubt = copy
          copy
        } else it
      }
    }
    updatedDoubt?.let { d ->
      repoScope.launch {
        studentBatchRepo?.updateDoubt(d)
      }
      addNotification(
        NotificationItem(
          id = "NOT_DBT_$now",
          title = "💡 Doubt Resolved (${d.subject})",
          message = "Faculty ($responderName) replied to ${d.studentName}'s doubt on ${d.chapter}: \"$replyText\"",
          time = getCurrentTimeStr(),
          type = "Academic",
          recipientRole = "Student",
          targetStudentId = d.studentId,
          targetBatchName = d.batchName,
          targetBatchId = d.batchId
        )
      )
    }
    NetworkConnectivityManager.markDataUpdatedOnline()
    return updatedDoubt != null
  }

  // Homework (Persisted in Room with Teacher -> Homework -> Subject -> Batch -> Students normalization)
  private val _homework = MutableStateFlow<List<HomeworkItem>>(emptyList())
  val homework = _homework.asStateFlow()

  fun getHomeworkForCurrentStudent(student: Student? = null): List<HomeworkItem> {
    val activeStu = student ?: getActiveStudentForCurrentSession() ?: return _homework.value
    return _homework.value.filter { hw ->
      (activeStu.batchId.isNotBlank() && hw.batchId.equals(activeStu.batchId, ignoreCase = true)) ||
        (activeStu.batch.isNotBlank() && hw.batchName.equals(activeStu.batch, ignoreCase = true)) ||
        hw.batchName.isBlank()
    }
  }

  fun addHomework(
    subject: String,
    title: String,
    description: String,
    dueDate: String,
    batchName: String = "12th Science",
    assignedBy: String = "",
    subjectId: String = "",
    batchId: String = ""
  ): Boolean {
    if (!checkOnlineOrBlock("assign homework")) return false
    val role = _currentRole.value
    val isAdmin = role == UserRole.ADMIN || _currentUserAccount.value?.role == UserRole.ADMIN
    if (!isAdmin && role != UserRole.TEACHER) return false
    val targetBatchCheck = batchId.ifBlank { batchName }
    if (!isAdmin && !isTeacherAssignedToBatch(targetBatchCheck)) return false

    val activeTeacher = getActiveTeacherForCurrentSession()
    val teacherName = assignedBy.ifBlank {
      activeTeacher?.name ?: reconcileFacultyName(null, batchName, subject)
    }
    val resolvedBatch = _batches.value.find {
      (batchId.isNotBlank() && it.id.equals(batchId, ignoreCase = true)) ||
        it.name.equals(batchName, ignoreCase = true) ||
        it.id.equals(batchName, ignoreCase = true)
    }
    val resolvedBatchId = batchId.ifBlank { resolvedBatch?.id ?: SeedData.batchIdForName(batchName) }
    val resolvedBatchName = resolvedBatch?.name ?: batchName
    val resolvedTeacherId = activeTeacher?.id ?: resolvedBatch?.teacherId ?: ""
    val resolvedSubjectId = subjectId.ifBlank {
      _subjects.value.find {
        it.name.equals(subject, ignoreCase = true) || it.name.contains(subject, ignoreCase = true)
      }?.id ?: "SUB_PHY_12"
    }

    val newHw = HomeworkItem(
      id = "HW_${System.currentTimeMillis()}",
      subject = subject,
      title = title,
      description = description,
      dueDate = dueDate,
      status = "Pending",
      submissionDate = null,
      teacherFeedback = null,
      batchName = resolvedBatchName,
      assignedBy = teacherName,
      batchId = resolvedBatchId,
      subjectId = resolvedSubjectId,
      teacherId = resolvedTeacherId,
      instituteId = "FSI_MAIN"
    )
    _homework.update { listOf(newHw) + it }
    repoScope.launch {
      studentBatchRepo?.insertHomework(newHw)
    }
    addNotification(
      NotificationItem(
        id = "NOT_HW_${System.currentTimeMillis()}",
        title = "📚 New Homework Assigned ($subject)",
        message = "$title for $resolvedBatchName due on $dueDate.",
        time = getCurrentTimeStr(),
        type = "Homework",
        recipientRole = "Student",
        targetBatchName = resolvedBatchName,
        targetBatchId = resolvedBatchId
      )
    )
    NetworkConnectivityManager.markDataUpdatedOnline()
    return true
  }

  fun toggleHomeworkStatus(homeworkId: String, studentId: String = "", feedback: String? = null) {
    if (!checkOnlineOrBlock("update homework status")) return
    val resolvedStudentId = studentId.ifBlank { getActiveStudentForCurrentSession()?.id ?: "" }
    if (resolvedStudentId.isBlank()) return
    var updatedHw: HomeworkItem? = null
    val today = getCurrentDateStr()
    _homework.update { list ->
      list.map { hw ->
        if (hw.id == homeworkId) {
          val isCurrentlyCompleted = hw.completedByStudentIds.contains(resolvedStudentId)
          val newCompletedIds = if (isCurrentlyCompleted) {
            hw.completedByStudentIds - resolvedStudentId
          } else {
            (hw.completedByStudentIds + resolvedStudentId).distinct()
          }
          val newStatus = if (newCompletedIds.isNotEmpty()) "Submitted" else "Pending"
          val copy = hw.copy(
            status = newStatus,
            submissionDate = if (newStatus == "Submitted") today else null,
            teacherFeedback = feedback ?: hw.teacherFeedback,
            completedByStudentIds = newCompletedIds
          )
          updatedHw = copy
          copy
        } else hw
      }
    }
    updatedHw?.let { hw ->
      repoScope.launch {
        studentBatchRepo?.updateHomework(hw)
      }
    }
    NetworkConnectivityManager.markDataUpdatedOnline()
  }

  fun deleteHomework(homeworkId: String): Boolean {
    if (!checkOnlineOrBlock("delete homework")) return false
    val role = _currentRole.value
    val isAdmin = role == UserRole.ADMIN || _currentUserAccount.value?.role == UserRole.ADMIN
    if (!isAdmin && role != UserRole.TEACHER) return false
    val targetHw = _homework.value.find { it.id == homeworkId } ?: return false
    if (!isAdmin && !isTeacherAssignedToBatch(targetHw.batchId.ifBlank { targetHw.batchName })) return false

    _homework.update { list -> list.filterNot { it.id == homeworkId } }
    repoScope.launch {
      studentBatchRepo?.deleteHomework(homeworkId)
    }
    NetworkConnectivityManager.markDataUpdatedOnline()
    return true
  }

  // Timetable & Conflict Validation
  private val _timetable = MutableStateFlow<List<TimetableItem>>(SeedData.getDefaultWeeklyTimetable())
  val timetable = _timetable.asStateFlow()

  private val _dynamicTimetable = MutableStateFlow<List<DynamicTimetableEntry>>(emptyList())
  val dynamicTimetable = _dynamicTimetable.asStateFlow()

  /**
   * Parses a time string such as "08:00 AM", "01:30 PM", or "14:30" into minutes since midnight (0..1439).
   */
  fun parseTimeToMinutes(timeStr: String): Int? {
    val clean = timeStr.trim().uppercase(Locale.getDefault())
    if (clean.isBlank()) return null
    try {
      val isPm = clean.contains("PM")
      val isAm = clean.contains("AM")
      val numericPart = clean.replace("AM", "").replace("PM", "").trim()
      val parts = numericPart.split(":")
      if (parts.size < 2) return null
      var hour = parts[0].trim().toIntOrNull() ?: return null
      val minute = parts[1].trim().take(2).toIntOrNull() ?: return null
      if (minute !in 0..59) return null
      if (isAm || isPm) {
        if (hour !in 1..12) return null
        if (isPm && hour != 12) hour += 12
        if (isAm && hour == 12) hour = 0
      } else {
        if (hour !in 0..23) return null
      }
      return hour * 60 + minute
    } catch (_: Exception) {
      return null
    }
  }

  fun isValidTimeRange(startTime: String, endTime: String): Boolean {
    val startMin = parseTimeToMinutes(startTime) ?: return false
    val endMin = parseTimeToMinutes(endTime) ?: return false
    return startMin < endMin
  }

  /**
   * Computes the scheduled session start timestamp (in epoch millis) from dateStr ("dd/MM/yyyy") and startTime.
   * Falls back to fallbackCreatedAt day if dateStr is blank.
   */
  fun computeScheduledStartMillis(
    dateStr: String,
    startTime: String,
    fallbackCreatedAt: Long = System.currentTimeMillis()
  ): Long {
    val minutes = parseTimeToMinutes(startTime) ?: return fallbackCreatedAt
    val cal = java.util.Calendar.getInstance()
    if (dateStr.isNotBlank()) {
      try {
        val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        val parsedDate = sdf.parse(dateStr.trim())
        if (parsedDate != null) {
          cal.time = parsedDate
        } else {
          cal.timeInMillis = fallbackCreatedAt
        }
      } catch (_: Exception) {
        cal.timeInMillis = fallbackCreatedAt
      }
    } else {
      cal.timeInMillis = fallbackCreatedAt
    }
    cal.set(java.util.Calendar.HOUR_OF_DAY, minutes / 60)
    cal.set(java.util.Calendar.MINUTE, minutes % 60)
    cal.set(java.util.Calendar.SECOND, 0)
    cal.set(java.util.Calendar.MILLISECOND, 0)
    return cal.timeInMillis
  }

  /**
   * Computes the scheduled session end timestamp (in epoch millis) from dateStr ("dd/MM/yyyy") and endTime.
   * Archive status is determined strictly from scheduled session end time, never from createdAt (Section 25).
   */
  fun computeScheduledEndMillis(
    dateStr: String,
    endTime: String,
    fallbackCreatedAt: Long = System.currentTimeMillis()
  ): Long {
    val minutes = parseTimeToMinutes(endTime) ?: return (fallbackCreatedAt + 60 * 60 * 1000L)
    val cal = java.util.Calendar.getInstance()
    if (dateStr.isNotBlank()) {
      try {
        val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        val parsedDate = sdf.parse(dateStr.trim())
        if (parsedDate != null) {
          cal.time = parsedDate
        } else {
          cal.timeInMillis = fallbackCreatedAt
        }
      } catch (_: Exception) {
        cal.timeInMillis = fallbackCreatedAt
      }
    } else {
      cal.timeInMillis = fallbackCreatedAt
    }
    cal.set(java.util.Calendar.HOUR_OF_DAY, minutes / 60)
    cal.set(java.util.Calendar.MINUTE, minutes % 60)
    cal.set(java.util.Calendar.SECOND, 0)
    cal.set(java.util.Calendar.MILLISECOND, 0)
    return cal.timeInMillis
  }

  fun isDynamicEntryArchived(
    entry: DynamicTimetableEntry,
    nowMillis: Long = System.currentTimeMillis()
  ): Boolean {
    if (entry.isArchived) return true
    val endMillis = entry.scheduledEndMillis.takeIf { it > 0L }
      ?: computeScheduledEndMillis(entry.dateStr, entry.endTime, entry.createdAt)
    return endMillis > 0L && nowMillis > endMillis
  }

  /**
   * Checks for overlapping timetable entries for the same batch OR the same teacher.
   * Returns a human-readable conflict description if an overlap exists, or null if conflict-free.
   */
  fun detectTimetableConflict(
    batchNameOrId: String,
    teacherNameOrId: String,
    startTime: String,
    endTime: String,
    day: String? = null,
    excludeEntryId: String? = null
  ): String? {
    val startMin = parseTimeToMinutes(startTime) ?: return "Invalid start time format ($startTime)"
    val endMin = parseTimeToMinutes(endTime) ?: return "Invalid end time format ($endTime)"
    if (startMin >= endMin) {
      return "Start time ($startTime) must be earlier than end time ($endTime)"
    }

    val targetBatch = _batches.value.find {
      it.id.equals(batchNameOrId, ignoreCase = true) || it.name.equals(batchNameOrId, ignoreCase = true)
    }
    val batchId = targetBatch?.id ?: batchNameOrId
    val batchName = targetBatch?.name ?: batchNameOrId

    val targetTeacher = _teachers.value.find {
      it.id.equals(teacherNameOrId, ignoreCase = true) || it.name.equals(teacherNameOrId, ignoreCase = true)
    }
    val teacherId = targetTeacher?.id ?: ""
    val teacherName = targetTeacher?.name ?: teacherNameOrId

    if (day != null) {
      // Check weekly timetable on the same day
      for (item in _timetable.value) {
        if (excludeEntryId != null && item.id == excludeEntryId) continue
        if (!item.day.equals(day, ignoreCase = true)) continue
        val itemStart = parseTimeToMinutes(item.startTime) ?: continue
        val itemEnd = parseTimeToMinutes(item.endTime) ?: continue
        if (startMin < itemEnd && endMin > itemStart) {
          val sameBatch = (batchId.isNotBlank() && item.batchId.equals(batchId, ignoreCase = true)) ||
            item.batch.equals(batchName, ignoreCase = true)
          if (sameBatch) {
            return "Batch conflict: $batchName already has ${item.subject} (${item.startTime} - ${item.endTime}) on $day"
          }
          val sameTeacher = (teacherId.isNotBlank() && item.teacherId.equals(teacherId, ignoreCase = true)) ||
            (teacherName.isNotBlank() && !teacherName.equals("Faculty", ignoreCase = true) && item.teacherName.equals(teacherName, ignoreCase = true))
          if (sameTeacher) {
            return "Teacher conflict: $teacherName is already scheduled in ${item.batch} (${item.startTime} - ${item.endTime}) on $day"
          }
        }
      }
    } else {
      // Check active dynamic timetable entries (based on scheduled session end time, not createdAt)
      for (entry in _dynamicTimetable.value) {
        if (excludeEntryId != null && entry.id == excludeEntryId) continue
        if (isDynamicEntryArchived(entry)) continue
        val itemStart = parseTimeToMinutes(entry.startTime) ?: continue
        val itemEnd = parseTimeToMinutes(entry.endTime) ?: continue
        if (startMin < itemEnd && endMin > itemStart) {
          val sameBatch = (batchId.isNotBlank() && entry.batchId.equals(batchId, ignoreCase = true)) ||
            entry.batch.equals(batchName, ignoreCase = true)
          if (sameBatch) {
            return "Batch conflict: $batchName already has ${entry.subject} (${entry.startTime} - ${entry.endTime})"
          }
          val sameTeacher = (teacherId.isNotBlank() && entry.teacherId.equals(teacherId, ignoreCase = true)) ||
            (teacherName.isNotBlank() && !teacherName.equals("Faculty", ignoreCase = true) && entry.teacherName.equals(teacherName, ignoreCase = true))
          if (sameTeacher) {
            return "Teacher conflict: $teacherName is already scheduled in ${entry.batch} (${entry.startTime} - ${entry.endTime})"
          }
        }
      }
    }
    return null
  }

  fun addWeeklyTimetableItem(item: TimetableItem): Pair<Boolean, String?> {
    if (!checkOnlineOrBlock("add weekly timetable slot")) return false to "Offline"
    val role = _currentRole.value
    val isAdmin = role == UserRole.ADMIN || _currentUserAccount.value?.role == UserRole.ADMIN
    if (!isAdmin && role != UserRole.TEACHER) return false to "Unauthorized role"
    if (!isAdmin && !isTeacherAssignedToBatch(item.batchId.ifBlank { item.batch })) {
      return false to "Teacher is not assigned to batch ${item.batch}"
    }

    val batchObj = _batches.value.find {
      it.id.equals(item.batchId, ignoreCase = true) || it.name.equals(item.batch, ignoreCase = true)
    }
    val resolvedBatchId = item.batchId.ifBlank { batchObj?.id ?: SeedData.batchIdForName(item.batch) }
    val resolvedBatchName = batchObj?.name ?: item.batch
    val resolvedTeacherName = item.teacherName.ifBlank { batchObj?.teacherName ?: reconcileFacultyName(null, resolvedBatchName, item.subject) }
    val teacherObj = _teachers.value.find {
      it.id.equals(item.teacherId, ignoreCase = true) || it.name.equals(resolvedTeacherName, ignoreCase = true)
    }
    val resolvedTeacherId = item.teacherId.ifBlank { teacherObj?.id ?: batchObj?.teacherId ?: "" }
    val resolvedSubjectId = item.subjectId.ifBlank {
      _subjects.value.find { it.name.contains(item.subject.substringBefore("•").trim(), ignoreCase = true) }?.id ?: "SUB_PHY_12"
    }

    val conflict = detectTimetableConflict(
      batchNameOrId = resolvedBatchId.ifBlank { resolvedBatchName },
      teacherNameOrId = resolvedTeacherId.ifBlank { resolvedTeacherName },
      startTime = item.startTime,
      endTime = item.endTime,
      day = item.day,
      excludeEntryId = item.id
    )
    if (conflict != null) {
      return false to conflict
    }

    val normalized = item.copy(
      batch = resolvedBatchName,
      batchId = resolvedBatchId,
      teacherName = resolvedTeacherName,
      teacherId = resolvedTeacherId,
      subjectId = resolvedSubjectId,
      instituteId = item.instituteId.ifBlank { "FSI_MAIN" }
    )
    _timetable.update { listOf(normalized) + it.filterNot { existing -> existing.id == normalized.id } }
    repoScope.launch {
      studentBatchRepo?.insertWeeklyTimetableEntry(normalized)
    }
    NetworkConnectivityManager.markDataUpdatedOnline()
    return true to null
  }

  fun addDynamicTimetableEntry(
    subject: String,
    batch: String,
    startTime: String,
    endTime: String,
    createdAt: Long = System.currentTimeMillis(),
    teacherName: String = "Faculty",
    batchId: String = "",
    teacherId: String = "",
    dateStr: String = ""
  ): Boolean {
    if (!checkOnlineOrBlock("schedule dynamic class")) return false
    val role = _currentRole.value
    val isAdmin = role == UserRole.ADMIN || _currentUserAccount.value?.role == UserRole.ADMIN
    if (!isAdmin && role != UserRole.TEACHER) return false
    if (!isAdmin && !isTeacherAssignedToBatch(batchId.ifBlank { batch })) return false
    if (!isValidTimeRange(startTime, endTime)) return false

    val batchObj = _batches.value.find {
      (batchId.isNotBlank() && it.id.equals(batchId, ignoreCase = true)) ||
        it.name.equals(batch, ignoreCase = true) ||
        it.id.equals(batch, ignoreCase = true)
    }
    val resolvedBatchId = batchId.ifBlank { batchObj?.id ?: SeedData.batchIdForName(batch) }
    val resolvedBatchName = batchObj?.name ?: batch

    val resolvedTeacher = if (teacherName.isBlank() || teacherName.equals("Faculty", ignoreCase = true) || teacherName.equals("Subject Faculty", ignoreCase = true)) {
      batchObj?.teacherName ?: reconcileFacultyName(teacherName, resolvedBatchName, subject)
    } else {
      reconcileFacultyName(teacherName, resolvedBatchName, subject)
    }
    val teacherObj = _teachers.value.find {
      (teacherId.isNotBlank() && it.id.equals(teacherId, ignoreCase = true)) ||
        it.name.equals(resolvedTeacher, ignoreCase = true)
    }
    val resolvedTeacherId = teacherId.ifBlank { teacherObj?.id ?: batchObj?.teacherId ?: "" }
    val resolvedSubjectId = _subjects.value.find {
      it.name.contains(subject.substringBefore("•").trim(), ignoreCase = true)
    }?.id ?: "SUB_PHY_12"

    val conflict = detectTimetableConflict(
      batchNameOrId = resolvedBatchId,
      teacherNameOrId = resolvedTeacherId.ifBlank { resolvedTeacher },
      startTime = startTime,
      endTime = endTime,
      day = null
    )
    if (conflict != null) return false

    val effectiveDateStr = dateStr.ifBlank {
      SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date(createdAt))
    }
    val schedStart = computeScheduledStartMillis(effectiveDateStr, startTime, createdAt)
    val schedEnd = computeScheduledEndMillis(effectiveDateStr, endTime, createdAt)
    val archived = schedEnd > 0L && System.currentTimeMillis() > schedEnd

    val newEntry = DynamicTimetableEntry(
      id = "DTT_${System.currentTimeMillis()}_${(1000..9999).random()}",
      subject = subject,
      batch = resolvedBatchName,
      startTime = startTime,
      endTime = endTime,
      createdAt = createdAt,
      teacherName = resolvedTeacher,
      batchId = resolvedBatchId,
      teacherId = resolvedTeacherId,
      subjectId = resolvedSubjectId,
      instituteId = "FSI_MAIN",
      dateStr = effectiveDateStr,
      scheduledStartMillis = schedStart,
      scheduledEndMillis = schedEnd,
      isArchived = archived
    )
    _dynamicTimetable.update { current ->
      listOf(newEntry) + current
    }
    studentBatchRepo?.let { repo ->
      repoScope.launch {
        repo.insertTimetableEntry(newEntry)
      }
    }
    NetworkConnectivityManager.markDataUpdatedOnline()
    return true
  }

  fun addDynamicTimetableEntries(
    batch: String,
    entries: List<Triple<String, String, String>>,
    createdAt: Long = System.currentTimeMillis(),
    teacherName: String = "Faculty",
    batchId: String = "",
    teacherId: String = "",
    dateStr: String = ""
  ): Boolean {
    if (!checkOnlineOrBlock("schedule dynamic classes")) return false
    val role = _currentRole.value
    val isAdmin = role == UserRole.ADMIN || _currentUserAccount.value?.role == UserRole.ADMIN
    if (!isAdmin && role != UserRole.TEACHER) return false
    if (!isAdmin && !isTeacherAssignedToBatch(batchId.ifBlank { batch })) return false

    val batchObj = _batches.value.find {
      (batchId.isNotBlank() && it.id.equals(batchId, ignoreCase = true)) ||
        it.name.equals(batch, ignoreCase = true) ||
        it.id.equals(batch, ignoreCase = true)
    }
    val resolvedBatchId = batchId.ifBlank { batchObj?.id ?: SeedData.batchIdForName(batch) }
    val resolvedBatchName = batchObj?.name ?: batch

    val resolvedTeacher = if (teacherName.isBlank() || teacherName.equals("Faculty", ignoreCase = true) || teacherName.equals("Subject Faculty", ignoreCase = true)) {
      batchObj?.teacherName ?: reconcileFacultyName(teacherName, resolvedBatchName)
    } else {
      reconcileFacultyName(teacherName, resolvedBatchName)
    }
    val validEntries = entries.filter { isValidTimeRange(it.second, it.third) }
    if (validEntries.isEmpty()) return false

    val effectiveDateStr = dateStr.ifBlank {
      SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date(createdAt))
    }

    val newEntries = validEntries.mapIndexed { idx, item ->
      val entryTeacher = if (resolvedTeacher.isNotBlank() && !resolvedTeacher.equals("Faculty", ignoreCase = true)) {
        resolvedTeacher
      } else {
        reconcileFacultyName(teacherName, resolvedBatchName, item.first)
      }
      val teacherObj = _teachers.value.find {
        (teacherId.isNotBlank() && it.id.equals(teacherId, ignoreCase = true)) ||
          it.name.equals(entryTeacher, ignoreCase = true)
      }
      val resolvedTeacherId = teacherId.ifBlank { teacherObj?.id ?: batchObj?.teacherId ?: "" }
      val resolvedSubjectId = _subjects.value.find {
        it.name.contains(item.first.substringBefore("•").trim(), ignoreCase = true)
      }?.id ?: "SUB_PHY_12"
      val schedStart = computeScheduledStartMillis(effectiveDateStr, item.second, createdAt)
      val schedEnd = computeScheduledEndMillis(effectiveDateStr, item.third, createdAt)
      val archived = schedEnd > 0L && System.currentTimeMillis() > schedEnd

      DynamicTimetableEntry(
        id = "DTT_${System.currentTimeMillis()}_${idx}_${(1000..9999).random()}",
        subject = item.first,
        batch = resolvedBatchName,
        startTime = item.second,
        endTime = item.third,
        createdAt = createdAt,
        teacherName = entryTeacher,
        batchId = resolvedBatchId,
        teacherId = resolvedTeacherId,
        subjectId = resolvedSubjectId,
        instituteId = "FSI_MAIN",
        dateStr = effectiveDateStr,
        scheduledStartMillis = schedStart,
        scheduledEndMillis = schedEnd,
        isArchived = archived
      )
    }
    _dynamicTimetable.update { current ->
      newEntries + current
    }
    studentBatchRepo?.let { repo ->
      repoScope.launch {
        repo.insertTimetableEntries(newEntries)
      }
    }
    NetworkConnectivityManager.markDataUpdatedOnline()
    return true
  }

  fun deleteDynamicTimetableEntry(id: String): Boolean {
    if (!checkOnlineOrBlock("delete timetable entry")) return false
    val role = _currentRole.value
    val isAdmin = role == UserRole.ADMIN || _currentUserAccount.value?.role == UserRole.ADMIN
    val targetEntry = _dynamicTimetable.value.find { it.id == id } ?: return false
    if (!isAdmin) {
      if (role != UserRole.TEACHER || !isTeacherAssignedToBatch(targetEntry.batchId.ifBlank { targetEntry.batch })) {
        return false
      }
    }
    _dynamicTimetable.update { current ->
      current.filter { it.id != id }
    }
    studentBatchRepo?.let { repo ->
      repoScope.launch {
        repo.deleteTimetableEntry(id)
      }
    }
    NetworkConnectivityManager.markDataUpdatedOnline()
    return true
  }

  // Study Logs (Persisted in Room, scoped to authenticated student)
  private val _studyLogs = MutableStateFlow<List<StudyLogItem>>(emptyList())
  val studyLogs = _studyLogs.asStateFlow()

  fun getStudyLogsForCurrentStudent(student: Student? = null): List<StudyLogItem> {
    val activeStu = student ?: getActiveStudentForCurrentSession() ?: return _studyLogs.value
    return _studyLogs.value.filter { log ->
      log.studentId.equals(activeStu.id, ignoreCase = true) || log.studentId.isBlank()
    }
  }

  fun logDailyStudy(hours: Float, subjects: String, chapters: String, studentIdOverride: String? = null) {
    if (!checkOnlineOrBlock("log study session")) return
    val stu = if (!studentIdOverride.isNullOrBlank()) {
      _students.value.find { it.id.equals(studentIdOverride, ignoreCase = true) }
    } else {
      getActiveStudentForCurrentSession()
    }
    val resolvedStudentId = stu?.id ?: studentIdOverride ?: ""
    val logItem = StudyLogItem(
      id = "SL_${System.currentTimeMillis()}",
      date = getCurrentDateStr(),
      hours = hours,
      subjects = subjects,
      chapters = chapters,
      studentId = resolvedStudentId,
      instituteId = stu?.instituteId?.ifBlank { "FSI_MAIN" } ?: "FSI_MAIN",
      timestamp = System.currentTimeMillis()
    )
    _studyLogs.update { listOf(logItem) + it }
    repoScope.launch {
      studentBatchRepo?.insertStudyLog(logItem)
    }
    NetworkConnectivityManager.markDataUpdatedOnline()
  }

  // Student Goals (Persisted in Room, scoped to authenticated student)
  private val _studentGoal = MutableStateFlow(
    GoalItem(targetMarks = 260, targetPercent = 90, targetRank = 3, targetAttendance = 95, weeklyStudyHours = 28)
  )
  val studentGoal = _studentGoal.asStateFlow()

  fun getGoalForCurrentStudent(student: Student? = null): GoalItem {
    val activeStu = student ?: getActiveStudentForCurrentSession()
    val current = _studentGoal.value
    return if (activeStu != null && current.studentId.isBlank()) {
      current.copy(studentId = activeStu.id)
    } else {
      current
    }
  }

  fun updateStudentGoal(g: GoalItem) {
    if (!checkOnlineOrBlock("modify academic goals")) return
    val stu = getActiveStudentForCurrentSession()
    val normalizedGoal = g.copy(
      studentId = g.studentId.ifBlank { stu?.id ?: "DEFAULT" },
      instituteId = g.instituteId.ifBlank { stu?.instituteId ?: "FSI_MAIN" }
    )
    _studentGoal.value = normalizedGoal
    repoScope.launch {
      studentBatchRepo?.insertStudentGoal(normalizedGoal)
    }
    NetworkConnectivityManager.markDataUpdatedOnline()
  }

  // Badges
  private val _badges = MutableStateFlow<List<BadgeItem>>(emptyList())
  val badges = _badges.asStateFlow()

  // Notifications
  private val _notifications = MutableStateFlow<List<NotificationItem>>(emptyList())
  val notifications = _notifications.asStateFlow()

  fun addNotification(n: NotificationItem) {
    _notifications.update { listOf(n) + it }
    repoScope.launch {
      studentBatchRepo?.insertNotification(n)
    }
    appContext?.let { ctx ->
      com.example.utils.NotificationHelper.sendPushNotification(ctx, n.title, n.message)
    }
  }

  fun markAllNotificationsAsRead() {
    _notifications.update { list -> list.map { it.copy(isRead = true) } }
    repoScope.launch {
      studentBatchRepo?.markAllNotificationsAsRead()
    }
  }

  fun markNotificationAsRead(id: String) {
    var updatedItem: NotificationItem? = null
    _notifications.update { list ->
      list.map {
        if (it.id == id) {
          val copy = it.copy(isRead = true)
          updatedItem = copy
          copy
        } else it
      }
    }
    updatedItem?.let { item ->
      repoScope.launch {
        studentBatchRepo?.updateNotification(item)
      }
    }
  }

  fun deleteNotification(id: String) {
    _notifications.update { list -> list.filter { it.id != id } }
    repoScope.launch {
      studentBatchRepo?.deleteNotification(id)
    }
  }

  fun deleteAllNotifications() {
    _notifications.value = emptyList()
    repoScope.launch {
      studentBatchRepo?.deleteAllNotifications()
    }
  }

  // Parent Meetings (Persisted in Room, scoped to linked parent/student/teacher)
  private val _meetings = MutableStateFlow<List<ParentMeetingItem>>(emptyList())
  val meetings = _meetings.asStateFlow()

  fun getMeetingsForCurrentParent(): List<ParentMeetingItem> {
    val isAdmin = _currentRole.value == UserRole.ADMIN || _currentUserAccount.value?.role == UserRole.ADMIN
    if (isAdmin) return _meetings.value
    val linkedIds = getLinkedStudentsForCurrentParent().map { it.id.lowercase() }.toSet()
    if (linkedIds.isEmpty()) return emptyList()
    return _meetings.value.filter { it.studentId.lowercase() in linkedIds }
  }

  fun scheduleParentMeeting(teacherName: String, date: String, time: String, notes: String, studentIdOverride: String? = null) {
    if (!checkOnlineOrBlock("schedule parent meeting")) return
    val isAdmin = _currentRole.value == UserRole.ADMIN || _currentUserAccount.value?.role == UserRole.ADMIN
    val linkedChildren = getLinkedStudentsForCurrentParent()
    val student = if (!studentIdOverride.isNullOrBlank()) {
      if (isAdmin || linkedChildren.any { it.id.equals(studentIdOverride, ignoreCase = true) }) {
        _students.value.find { it.id.equals(studentIdOverride, ignoreCase = true) }
      } else {
        linkedChildren.firstOrNull()
      }
    } else {
      linkedChildren.firstOrNull() ?: getActiveStudentForCurrentSession()
    }
    val studentId = student?.id ?: ""
    val studentName = student?.name ?: "Student"
    val parentId = student?.parentId ?: _currentUserAccount.value?.userId ?: ""
    val parentName = student?.parentName?.ifBlank { "Parent" } ?: "Parent"
    val teacherObj = _teachers.value.find { it.name.equals(teacherName, ignoreCase = true) || it.id.equals(teacherName, ignoreCase = true) }
    val meetingItem = ParentMeetingItem(
      id = "PM_${System.currentTimeMillis()}",
      studentName = studentName,
      parentName = parentName,
      teacherName = teacherObj?.name ?: teacherName,
      date = date,
      time = time,
      notes = notes,
      actionItems = "To be reviewed at meeting",
      status = "Scheduled",
      studentId = studentId,
      parentId = parentId,
      teacherId = teacherObj?.id ?: "",
      instituteId = student?.instituteId?.ifBlank { "FSI_MAIN" } ?: "FSI_MAIN"
    )
    _meetings.update { listOf(meetingItem) + it }
    repoScope.launch {
      studentBatchRepo?.insertParentMeeting(meetingItem)
    }
    NetworkConnectivityManager.markDataUpdatedOnline()
  }

  fun getCurrentDateStr(): String {
    return SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date())
  }

  fun getCurrentTimeStr(): String {
    return SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date())
  }

  private fun bindAuthenticatedUserSession(
    identifier: String,
    role: UserRole,
    linkedEntityId: String = "",
    linkedStudentIds: List<String> = emptyList(),
    preserveExistingToken: String = ""
  ): Boolean {
    val cleanId = identifier.trim()
    if (cleanId.isBlank()) return false
    val status = getAccountStatus(cleanId)
    val vaultRole = com.example.security.SecurityEngine.getVerifiedRole(cleanId)
    // Authoritative role resolution: directory or vault role takes precedence over caller-supplied role
    val resolvedRole = status.role ?: vaultRole ?: role

    if (resolvedRole == UserRole.TEACHER) {
      val t = _teachers.value.find {
        it.email.equals(cleanId, true) ||
          it.id.equals(cleanId, true) ||
          it.contact.equals(cleanId, true)
      }
      if (t?.status.equals("Pending Approval", ignoreCase = true)) {
        return false
      }
    }

    val matchedUserAcc = _userAccounts.value.find {
      it.email.equals(cleanId, ignoreCase = true) ||
        it.userId.equals(cleanId, ignoreCase = true) ||
        it.linkedEntityId.equals(cleanId, ignoreCase = true) ||
        (it.mobile.isNotBlank() && it.mobile == cleanId)
    }

    // Authoritative entity binding: directory/account links take precedence so UI callers cannot spoof another student/teacher ID
    val directoryLinkedId = status.studentId ?: status.teacherId ?: matchedUserAcc?.linkedEntityId.orEmpty()
    val effectiveLinkedId = directoryLinkedId.ifBlank { linkedEntityId }

    val effectiveLinkedStudentIds = if (!status.studentId.isNullOrBlank()) {
      listOf(status.studentId)
    } else if (matchedUserAcc != null && matchedUserAcc.linkedStudentIds.isNotEmpty()) {
      matchedUserAcc.linkedStudentIds
    } else if (linkedStudentIds.isNotEmpty()) {
      linkedStudentIds
    } else if (effectiveLinkedId.startsWith("STU", ignoreCase = true)) {
      listOf(effectiveLinkedId)
    } else {
      emptyList()
    }

    val resolvedAccount = matchedUserAcc?.copy(
      role = resolvedRole,
      linkedEntityId = effectiveLinkedId,
      linkedStudentIds = effectiveLinkedStudentIds
    ) ?: UserAccount(
      userId = "USR_${cleanId.lowercase()}",
      email = cleanId,
      mobile = if (cleanId.any { it.isDigit() } && !cleanId.contains("@")) cleanId else "",
      name = status.displayName.ifBlank { cleanId },
      role = resolvedRole,
      linkedEntityId = effectiveLinkedId,
      linkedStudentIds = effectiveLinkedStudentIds
    )

    val sessionToken = preserveExistingToken.ifBlank {
      com.example.security.SecurityEngine.issueSessionToken(cleanId, resolvedAccount.role)
    }

    _currentUserAccount.value = resolvedAccount
    _currentUserIdentifier.value = cleanId
    _currentRole.value = resolvedAccount.role
    _isLoggedIn.value = true
    saveSession(true, cleanId, _currentRole.value, sessionToken)

    if (_currentRole.value == UserRole.PARENT) {
      val linkedChildren = getLinkedStudentsForCurrentParent()
      if (linkedChildren.isNotEmpty() && _selectedChildIdForParent.value == null) {
        _selectedChildIdForParent.value = linkedChildren.first().id
      }
    }

    val currentList = _savedAccounts.value.toMutableList()
    val existingIdx = currentList.indexOfFirst { it.identifier.equals(cleanId, true) }
    val displayName = status.displayName.ifBlank {
      cleanId.substringBefore("@").replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
    }
    val newAcc = SavedAccount(cleanId, _currentRole.value, displayName)
    if (existingIdx >= 0) {
      currentList[existingIdx] = newAcc
    } else {
      currentList.add(newAcc)
    }
    _savedAccounts.value = currentList
    saveSavedAccountsToPrefs(currentList)
    return true
  }

  fun login(
    identifier: String,
    role: UserRole,
    linkedEntityId: String = "",
    linkedStudentIds: List<String> = emptyList()
  ): Boolean {
    return bindAuthenticatedUserSession(
      identifier = identifier,
      role = role,
      linkedEntityId = linkedEntityId,
      linkedStudentIds = linkedStudentIds
    )
  }

  /**
   * Passwordless account switching is strictly forbidden.
   * A stored account entry in SharedPreferences/local storage is NEVER proof of authentication.
   */
  fun switchAccount(account: SavedAccount): Boolean {
    com.example.security.SecurityEngine.logEvent(
      severity = com.example.security.SecuritySeverity.WARNING,
      eventType = "PASSWORDLESS_SWITCH_BLOCKED",
      actor = account.identifier,
      details = "Blocked attempt to switch to ${account.identifier} without credential verification."
    )
    return false
  }

  /**
   * Authenticates the target account through the authoritative authentication mechanism,
   * terminates the current session, and establishes a new authenticated session.
   */
  fun switchAccount(
    account: SavedAccount,
    password: String,
    onSuccess: () -> Unit = {},
    onFailure: (String) -> Unit = {}
  ) {
    val cleanId = account.identifier.trim()
    if (cleanId.isBlank() || password.isBlank()) {
      onFailure("Password is required to switch accounts.")
      return
    }
    FirebaseAuthService.signIn(
      email = cleanId,
      password = password,
      onSuccess = { verifiedRole, verifiedEmail ->
        // Terminate previous session cleanly before establishing the new authenticated session
        logout()
        val loggedIn = login(
          identifier = verifiedEmail,
          role = verifiedRole
        )
        if (loggedIn) {
          onSuccess()
        } else {
          onFailure("Account status is pending approval.")
        }
      },
      onFailure = { err ->
        onFailure(err)
      }
    )
  }

  fun removeAccount(identifier: String) {
    val updated = _savedAccounts.value.filter { !it.identifier.equals(identifier, true) }
    _savedAccounts.value = updated
    saveSavedAccountsToPrefs(updated)
    if (_currentUserIdentifier.value.equals(identifier, true)) {
      // Never auto-switch to another saved account without authentication
      logout()
    }
  }

  fun logout() {
    FirebaseAuthService.signOut()
    FirestoreService.stopAllListeners()
    com.example.security.SecurityEngine.revokeSessionToken()
    _isLoggedIn.value = false
    _currentUserIdentifier.value = ""
    _currentUserAccount.value = null
    _currentRole.value = UserRole.STUDENT
    _selectedChildIdForParent.value = null
    saveSession(false, "", UserRole.STUDENT)
  }

  fun logoutAllAccounts() {
    FirebaseAuthService.signOut()
    FirestoreService.stopAllListeners()
    com.example.security.SecurityEngine.revokeSessionToken()
    _savedAccounts.value = emptyList()
    saveSavedAccountsToPrefs(emptyList())
    _isLoggedIn.value = false
    _currentUserIdentifier.value = ""
    _currentUserAccount.value = null
    _currentRole.value = UserRole.STUDENT
    _selectedChildIdForParent.value = null
    saveSession(false, "", UserRole.STUDENT)
  }

  fun registerStudent(name: String, email: String, rawPassword: String) {
    if (!checkOnlineOrBlock("register student")) return
    if (email.isBlank() || rawPassword.length < 6) return
    val existing = _students.value.find { it.email.equals(email, true) }
    if (existing == null) {
      val stuId = "STU${System.currentTimeMillis().toString().takeLast(4)}"
      val newStudent = Student(
        id = stuId,
        name = if (name.isBlank()) "Student" else name,
        photo = "",
        mobile = "",
        parentName = "Parent of $name",
        parentContact = "",
        email = email,
        dob = "",
        gender = "",
        address = "",
        school = "",
        className = "Class 12",
        batch = "12th Science",
        batchId = "BATCH_12_SCI",
        parentId = "PAR_$stuId",
        parentEmail = "parent_${stuId.lowercase()}@mytuition.com",
        stream = "Science",
        admissionDate = getCurrentDateStr(),
        status = "Active",
        attendancePercent = 100,
        overallAvg = 0,
        rank = 0,
        strongestSubject = "General",
        weakestSubject = "General",
        recentScores = emptyList(),
        isActivated = true
      )
      addStudent(newStudent)
      repoScope.launch(Dispatchers.Default) {
        com.example.security.SecurityEngine.registerUserCredential(
          identifier = email,
          rawPassword = rawPassword,
          role = UserRole.STUDENT,
          fullName = name
        )
      }
      NetworkConnectivityManager.markDataUpdatedOnline()
    }
  }

  fun registerTeacher(name: String, email: String, mobile: String, subject: String, rawPassword: String) {
    if (!checkOnlineOrBlock("register teacher")) return
    if (email.isBlank() || rawPassword.length < 6) return
    val newT = Teacher(
      id = "FAC_${System.currentTimeMillis()}",
      name = name,
      subject = subject,
      contact = mobile,
      qualification = "Self Registered",
      experience = "New Faculty",
      assignedBatches = listOf("Foundation Target"),
      classesTaken = 0,
      attendancePercent = 100,
      feedbackRating = 5.0f,
      salary = 45000,
      incentives = 0,
      deductions = 0,
      status = "Pending Approval",
      email = email,
      isActivated = true
    )
    _teachers.update { it + newT }
    repoScope.launch {
      studentBatchRepo?.insertTeacher(newT)
    }
    repoScope.launch(Dispatchers.Default) {
      com.example.security.SecurityEngine.registerUserCredential(
        identifier = email,
        rawPassword = rawPassword,
        role = UserRole.TEACHER,
        fullName = name
      )
      com.example.security.SecurityEngine.registerUserCredential(
        identifier = newT.id,
        rawPassword = rawPassword,
        role = UserRole.TEACHER,
        fullName = name
      )
    }
    NetworkConnectivityManager.markDataUpdatedOnline()
  }

  fun approveTeacher(teacherId: String) {
    if (!checkOnlineOrBlock("approve teacher account")) return
    _teachers.update { list ->
      list.map { if (it.id == teacherId) it.copy(status = "Active", isActivated = true) else it }
    }
    val updated = _teachers.value.find { it.id == teacherId }
    if (updated != null) {
      repoScope.launch {
        studentBatchRepo?.updateTeacher(updated)
      }
    }
    NetworkConnectivityManager.markDataUpdatedOnline()
  }

  fun resetUserPassword(identifier: String, onResult: ((Boolean, String) -> Unit)? = null) {
    val cleanId = identifier.trim()
    val resolvedEmail = if (cleanId.contains("@")) {
      cleanId
    } else {
      resolveCanonicalEmailForIdentifier(cleanId) ?: _currentUserAccount.value?.email.orEmpty()
    }

    if (resolvedEmail.isBlank() || !resolvedEmail.contains("@")) {
      val msg = "Password reset failed: A valid registered email address is required."
      _passwordResetNotice.value = msg
      onResult?.invoke(false, msg)
      return
    }

    FirebaseAuthService.sendPasswordResetEmail(
      email = resolvedEmail,
      onSuccess = {
        val msg = "Password reset email sent to $resolvedEmail."
        _passwordResetNotice.value = msg
        onResult?.invoke(true, msg)
      },
      onFailure = { errorReason ->
        val msg = "Password reset failed: $errorReason"
        _passwordResetNotice.value = msg
        onResult?.invoke(false, msg)
      }
    )
  }

  fun clearPasswordNotice() {
    _passwordResetNotice.value = null
  }

  fun clearAllAppData(context: android.content.Context) {
    if (!checkOnlineOrBlock("purge system data")) return
    preferences?.edit()?.apply {
      putBoolean("is_data_cleared", true)
      remove("saved_attendance_records")
      apply()
    }

    _students.value = emptyList()
    _teachers.value = emptyList()
    _batches.value = emptyList()
    _subjects.value = emptyList()
    _tests.value = emptyList()
    _fees.value = emptyList()
    _feePayments.value = emptyList()
    _attendance.value = emptyList()
    _leads.value = emptyList()
    _onlineForms.value = emptyList()
    _questions.value = emptyList()
    _doubts.value = emptyList()
    _homework.value = emptyList()
    _timetable.value = emptyList()
    _dynamicTimetable.value = emptyList()
    _studyLogs.value = emptyList()
    _notifications.value = emptyList()
    _meetings.value = emptyList()

    repoScope.launch {
      studentBatchRepo?.clearAllData()
    }

    NetworkConnectivityManager.markDataUpdatedOnline()

    addNotification(
      NotificationItem(
        id = "NOT_CLR_${System.currentTimeMillis()}",
        title = "🧹 All System Data Purged",
        message = "All sample & legacy database records cleared. Platform is clean and ready for 1,000 real users.",
        time = getCurrentTimeStr(),
        type = "System",
        recipientRole = "All"
      )
    )
  }

  fun initializeCleanInstituteStructure() {
    if (!checkOnlineOrBlock("reset institute structure")) return
    val cleanBatches = listOf(
      Batch("BATCH_10_B", "Class 10 Board Target", "Mon - Sat (08:00 AM - 12:00 PM)", 0, "Faculty Team"),
      Batch("BATCH_11_S", "11th Science Stream", "Mon - Sat (01:00 PM - 05:00 PM)", 0, "Science Faculty"),
      Batch("BATCH_12_S", "12th Science Stream", "Mon - Sat (08:00 AM - 01:30 PM)", 0, "Senior Faculty"),
      Batch("BATCH_JEE", "JEE Competitive Target", "Mon - Sat (07:00 AM - 02:00 PM)", 0, "IITian Mentors"),
      Batch("BATCH_NEET", "NEET Medical Target", "Mon - Sat (08:00 AM - 02:00 PM)", 0, "Medical Faculty")
    )
    _batches.value = cleanBatches
    _students.value = emptyList()
    _teachers.value = emptyList()
    _fees.value = emptyList()

    repoScope.launch {
      studentBatchRepo?.clearAllData()
      cleanBatches.forEach { studentBatchRepo?.insertBatch(it) }
    }
    NetworkConnectivityManager.markDataUpdatedOnline()
  }

  fun generateAndBulkImportRealUsers(count: Int = 1000) {
    if (!checkOnlineOrBlock("generate mock records")) return
    val batchesList = listOf(
      Batch("BATCH_10_B", "Class 10 Board Target", "Mon - Sat (08:00 AM - 12:00 PM)", count / 4, "Faculty Team"),
      Batch("BATCH_11_S", "11th Science Stream", "Mon - Sat (01:00 PM - 05:00 PM)", count / 4, "Science Faculty"),
      Batch("BATCH_12_S", "12th Science Stream", "Mon - Sat (08:00 AM - 01:30 PM)", count / 4, "Senior Faculty"),
      Batch("BATCH_JEE_APEX", "JEE Apex Competitive", "Mon - Sat (07:00 AM - 02:00 PM)", count / 4, "IITian Mentors")
    )

    _batches.value = batchesList
    repoScope.launch {
      batchesList.forEach { studentBatchRepo?.insertBatch(it) }
    }

    val firstNames = listOf(
      "Aarav", "Ananya", "Rohan", "Sanya", "Aditya", "Ishita", "Kabir", "Meera", "Vivaan", "Diya",
      "Priya", "Arjun", "Kavya", "Rahul", "Tanvi", "Neha", "Dev", "Anushka", "Yash", "Sneha",
      "Shreyas", "Pooja", "Vikram", "Riya", "Karan", "Apurva", "Pranav", "Shruti", "Akash", "Swati",
      "Siddharth", "Bhavna", "Gaurav", "Nisha", "Manish", "Preeti", "Alok", "Shweta", "Mayank", "Monal"
    )

    val lastNames = listOf(
      "Sharma", "Patel", "Deshmukh", "Joshi", "Kulkarni", "Verma", "Gupta", "Singh", "Rao", "Nair",
      "Chavan", "Mehta", "Iyer", "Shinde", "Thakur", "Reddy", "Chopra", "Bhasin", "Garg", "Bhat"
    )

    val generatedStudents = (1..count).map { index ->
      val fName = firstNames[index % firstNames.size]
      val lName = lastNames[(index * 3) % lastNames.size]
      val name = "$fName $lName"
      val stuId = "STU" + String.format("%05d", index)
      val batchObj = batchesList[index % batchesList.size]

      val mobile = "+91 98" + String.format("%08d", 10000000 + index)
      val parentMobile = "+91 97" + String.format("%08d", 10000000 + index)
      val email = "${fName.lowercase()}.${lName.lowercase()}$index@mytuition.com"

      Student(
        id = stuId,
        name = name,
        photo = "",
        mobile = mobile,
        parentName = "Mr. $lName (Parent)",
        parentContact = parentMobile,
        email = email,
        dob = "12/08/2007",
        gender = if (index % 2 == 0) "Male" else "Female",
        address = "Campus Hub Sector ${index % 50 + 1}",
        school = "FSI Educational Academy",
        className = batchObj.name.substringBefore(" "),
        batch = batchObj.name,
        batchId = batchObj.id,
        parentId = "PAR_$stuId",
        parentEmail = "parent_${stuId.lowercase()}@mytuition.com",
        stream = if (batchObj.name.contains("Science") || batchObj.name.contains("JEE")) "Science" else "General",
        admissionDate = getCurrentDateStr(),
        status = "Active",
        attendancePercent = 85 + (index % 15),
        overallAvg = 70 + (index % 28),
        rank = index,
        strongestSubject = if (index % 2 == 0) "Physics" else "Mathematics",
        weakestSubject = if (index % 2 == 0) "Chemistry" else "Physics",
        recentScores = listOf("Physics" to 82, "Chemistry" to 78, "Mathematics" to 88)
      )
    }

    _students.value = generatedStudents

    val newFees = generatedStudents.mapIndexed { idx, stu ->
      val feeAmt = 45000
      val paidAmt = if (idx % 2 == 0) 45000 else 25000
      val pend = feeAmt - paidAmt
      FeeRecord(
        id = "F_REAL_${idx + 1}",
        studentName = stu.name,
        feeAmount = feeAmt,
        dueDate = "10/08/2026",
        paidAmount = paidAmt,
        pendingAmount = pend,
        paymentStatus = if (pend == 0) "Paid" else "Pending",
        month = "July 2026",
        remindedCount = 0,
        lastReminded = "",
        studentId = stu.id,
        batchName = stu.batch
      )
    }
    _fees.value = newFees

    repoScope.launch {
      studentBatchRepo?.bulkInsertStudents(generatedStudents)
      studentBatchRepo?.insertFeeInvoices(newFees)
    }

    addNotification(
      NotificationItem(
        id = "NOT_BULK_${System.currentTimeMillis()}",
        title = "⚡ 1,000 Real Student Profiles Ready",
        message = "Bulk onboarding completed. $count active student profiles and fee ledgers generated and indexed in database.",
        time = getCurrentTimeStr(),
        type = "System",
        recipientRole = "All"
      )
    )
    NetworkConnectivityManager.markDataUpdatedOnline()
  }

  fun bulkImportStudentsFromCsv(rawCsv: String) {
    if (!checkOnlineOrBlock("import CSV records")) return
    val lines = rawCsv.split("\n").map { it.trim() }.filter { it.isNotBlank() }
    val newStudents = mutableListOf<Student>()
    lines.forEachIndexed { idx, line ->
      val parts = line.split(",")
      if (parts.size >= 2) {
        val name = parts[0].trim()
        val email = parts[1].trim()
        val batch = if (parts.size > 2) parts[2].trim() else "General Batch"
        val mobile = if (parts.size > 3) parts[3].trim() else "+91 98000 00000"
        val parent = if (parts.size > 4) parts[4].trim() else "Parent"
        val stuId = "STU_CSV_${System.currentTimeMillis()}_$idx"

        val s = Student(
          id = stuId,
          name = name,
          photo = "",
          mobile = mobile,
          parentName = parent,
          parentContact = mobile,
          email = email,
          dob = "",
          gender = "Other",
          address = "City Campus",
          school = "City High",
          className = "Class 12",
          batch = batch,
          batchId = "BATCH_${batch.replace(" ", "_")}",
          parentId = "PAR_$stuId",
          parentEmail = "parent_${stuId.lowercase()}@mytuition.com",
          stream = "General",
          admissionDate = getCurrentDateStr(),
          status = "Active",
          attendancePercent = 100,
          overallAvg = 0,
          rank = idx + 1,
          strongestSubject = "General",
          weakestSubject = "General",
          recentScores = emptyList()
        )
        newStudents.add(s)
      }
    }

    if (newStudents.isNotEmpty()) {
      _students.update { it + newStudents }
      syncBatchStudentCounts()
      repoScope.launch {
        studentBatchRepo?.bulkInsertStudents(newStudents)
      }
      NetworkConnectivityManager.markDataUpdatedOnline()
      addNotification(
        NotificationItem(
          id = "NOT_CSV_${System.currentTimeMillis()}",
          title = "CSV Import Complete",
          message = "Successfully imported ${newStudents.size} students into roster.",
          time = getCurrentTimeStr(),
          type = "System",
          recipientRole = "All"
        )
      )
    }
  }
}
