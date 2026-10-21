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
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import com.example.data.db.BatchEnrollmentEntity

object AppRepository {
  private var preferences: android.content.SharedPreferences? = null
  private var studentBatchRepo: StudentBatchRepository? = null
  private var appContext: android.content.Context? = null
  private val repoScope = CoroutineScope(kotlinx.coroutines.SupervisorJob() + Dispatchers.IO)
  private val collectorJobs = java.util.concurrent.CopyOnWriteArrayList<kotlinx.coroutines.Job>()
  @Volatile
  private var collectorsInitializedForContextHash: Int = 0

  private val demoSeedMutex = Mutex()
  @Volatile
  private var demoSeedDeferred: Deferred<Unit>? = null

  suspend fun awaitInitialDemoDataSeeded() {
    demoSeedDeferred?.await()
  }

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

  private val _allUserAccounts = MutableStateFlow<List<UserAccount>>(emptyList())
  private val _scopedUserAccounts = MutableStateFlow<List<UserAccount>>(emptyList())
  val userAccounts = _scopedUserAccounts.asStateFlow()

  private val _passwordResetNotice = MutableStateFlow<String?>(null)
  val passwordResetNotice = _passwordResetNotice.asStateFlow()

  private val _currentRole = MutableStateFlow(UserRole.STUDENT)
  val currentRole = _currentRole.asStateFlow()

  private val _currentInstituteId = MutableStateFlow("FSI_MAIN")
  val currentInstituteId = _currentInstituteId.asStateFlow()

  fun isAuthenticatedSession(): Boolean {
    return _isLoggedIn.value && _currentUserIdentifier.value.isNotBlank()
  }

  fun isAdminSession(): Boolean {
    return isAuthenticatedSession() &&
      (_currentRole.value == UserRole.ADMIN || _currentUserAccount.value?.role == UserRole.ADMIN)
  }

  fun isTeacherSession(): Boolean {
    return isAuthenticatedSession() && _currentRole.value == UserRole.TEACHER
  }

  fun isStudentSession(): Boolean {
    return isAuthenticatedSession() && _currentRole.value == UserRole.STUDENT
  }

  fun isParentSession(): Boolean {
    return isAuthenticatedSession() && _currentRole.value == UserRole.PARENT
  }

  private fun requireAdminSession(actionName: String, targetInstituteId: String? = null): Boolean {
    if (!isAdminSession()) {
      com.example.security.SecurityEngine.logEvent(
        severity = com.example.security.SecuritySeverity.ALERT,
        eventType = "UNAUTHORIZED_ADMIN_MUTATION_BLOCKED",
        actor = _currentUserIdentifier.value.ifBlank { "Unauthenticated" },
        details = "Blocked '$actionName': caller role '${_currentRole.value}' (loggedIn=${_isLoggedIn.value}) is not an authenticated ADMIN/OWNER."
      )
      return false
    }
    if (!targetInstituteId.isNullOrBlank()) {
      if (!com.example.security.SecurityEngine.isValidInstituteId(targetInstituteId) ||
        !belongsToCurrentInstitute(targetInstituteId)
      ) {
        com.example.security.SecurityEngine.logEvent(
          severity = com.example.security.SecuritySeverity.ALERT,
          eventType = "CROSS_TENANT_MUTATION_BLOCKED",
          actor = _currentUserIdentifier.value,
          details = "Blocked '$actionName' targeting institute '$targetInstituteId' from active institute '${getCurrentInstituteId()}'."
        )
        return false
      }
    }
    return true
  }

  private fun requireTeacherOrAdminForBatch(
    actionName: String,
    batchNameOrId: String = "",
    targetInstituteId: String? = null
  ): Boolean {
    if (!isAuthenticatedSession()) {
      com.example.security.SecurityEngine.logEvent(
        severity = com.example.security.SecuritySeverity.ALERT,
        eventType = "UNAUTHENTICATED_MUTATION_BLOCKED",
        actor = "Unauthenticated",
        details = "Blocked '$actionName': no active authenticated session."
      )
      return false
    }
    if (!targetInstituteId.isNullOrBlank() &&
      (!com.example.security.SecurityEngine.isValidInstituteId(targetInstituteId) || !belongsToCurrentInstitute(targetInstituteId))
    ) {
      com.example.security.SecurityEngine.logEvent(
        severity = com.example.security.SecuritySeverity.ALERT,
        eventType = "CROSS_TENANT_MUTATION_BLOCKED",
        actor = _currentUserIdentifier.value,
        details = "Blocked '$actionName' across institutes ('$targetInstituteId' vs '${getCurrentInstituteId()}')."
      )
      return false
    }
    if (isAdminSession()) return true
    if (isTeacherSession()) {
      val activeTeacher = getActiveTeacherForCurrentSession() ?: return false
      if (!belongsToCurrentInstitute(activeTeacher.instituteId)) return false
      if (batchNameOrId.isNotBlank()) {
        if (!isTeacherAssignedToBatch(batchNameOrId, activeTeacher)) {
          com.example.security.SecurityEngine.logEvent(
            severity = com.example.security.SecuritySeverity.ALERT,
            eventType = "UNAUTHORIZED_TEACHER_BATCH_MUTATION_BLOCKED",
            actor = _currentUserIdentifier.value,
            details = "Blocked '$actionName': teacher '${activeTeacher.id}' is not assigned to batch '$batchNameOrId'."
          )
          return false
        }
      }
      return true
    }
    com.example.security.SecurityEngine.logEvent(
      severity = com.example.security.SecuritySeverity.ALERT,
      eventType = "UNAUTHORIZED_ROLE_MUTATION_BLOCKED",
      actor = _currentUserIdentifier.value,
      details = "Blocked '$actionName' for unauthorized role '${_currentRole.value}'."
    )
    return false
  }

  /**
   * Resolves the authorized target Student for student/parent/teacher/admin operations.
   * Rejects caller-supplied student/studentId mismatches against the authenticated identity.
   */
  fun resolveAuthorizedStudentTarget(
    requestedStudent: Student? = null,
    requestedStudentId: String? = null
  ): Student? {
    if (!isAuthenticatedSession()) return null
    val reqId = requestedStudentId?.trim()?.takeIf { it.isNotBlank() }
      ?: requestedStudent?.id?.trim()?.takeIf { it.isNotBlank() }

    if (requestedStudent != null &&
      requestedStudent.instituteId.isNotBlank() &&
      !belongsToCurrentInstitute(requestedStudent.instituteId)
    ) {
      return null
    }

    if (isAdminSession()) {
      val instStudents = _allStudents.value.filter { belongsToCurrentInstitute(it.instituteId) }
      if (reqId != null) {
        return instStudents.find { it.id.equals(reqId, ignoreCase = true) }
      }
      return getActiveStudentForCurrentSession()
    }

    return when (_currentRole.value) {
      UserRole.STUDENT -> {
        val ownStudent = getActiveStudentForCurrentSession() ?: return null
        if (reqId != null && !ownStudent.id.equals(reqId, ignoreCase = true)) {
          com.example.security.SecurityEngine.logEvent(
            severity = com.example.security.SecuritySeverity.ALERT,
            eventType = "STUDENT_IDENTITY_MISMATCH_BLOCKED",
            actor = _currentUserIdentifier.value,
            details = "Student '${ownStudent.id}' attempted to access/modify data for another student '$reqId'."
          )
          return null
        }
        ownStudent
      }
      UserRole.PARENT -> {
        val linked = getLinkedStudentsForCurrentParent()
        if (linked.isEmpty()) return null
        if (reqId != null) {
          val matched = linked.find { it.id.equals(reqId, ignoreCase = true) }
          if (matched == null) {
            com.example.security.SecurityEngine.logEvent(
              severity = com.example.security.SecuritySeverity.ALERT,
              eventType = "UNLINKED_CHILD_ACCESS_BLOCKED",
              actor = _currentUserIdentifier.value,
              details = "Parent attempted to access/modify data for unlinked student '$reqId'."
            )
          }
          return matched
        }
        val selectedId = _selectedChildIdForParent.value
        if (!selectedId.isNullOrBlank()) {
          linked.find { it.id.equals(selectedId, ignoreCase = true) }?.let { return it }
        }
        linked.firstOrNull()
      }
      UserRole.TEACHER -> {
        if (reqId == null) return null
        val allowedStudents = getStudentsForCurrentTeacher()
        allowedStudents.find { it.id.equals(reqId, ignoreCase = true) }
      }
      UserRole.ADMIN -> null
    }
  }

  fun getCurrentInstituteId(): String {
    val accountInst = _currentUserAccount.value?.instituteId?.trim().orEmpty()
    if (com.example.security.SecurityEngine.isValidInstituteId(accountInst)) {
      return accountInst
    }
    val flowInst = _currentInstituteId.value.trim()
    if (com.example.security.SecurityEngine.isValidInstituteId(flowInst)) {
      return flowInst
    }
    val configInst = _instituteConfig.value.instituteId.trim()
    if (com.example.security.SecurityEngine.isValidInstituteId(configInst)) {
      return configInst
    }
    return "FSI_MAIN"
  }

  fun currentActiveInstituteId(): String = getCurrentInstituteId()

  fun currentVerifiedRole(): UserRole = _currentRole.value

  fun isCurrentSessionAdmin(): Boolean = isAdminSession()

  fun isSameInstitute(instA: String?, instB: String?): Boolean {
    val a = instA?.trim().orEmpty()
    val b = instB?.trim().orEmpty()
    if (a.isBlank() || b.isBlank()) return false
    return a.equals(b, ignoreCase = true)
  }

  private fun logRbacDenial(actionName: String, reason: String) {
    com.example.security.SecurityEngine.logEvent(
      severity = com.example.security.SecuritySeverity.ALERT,
      eventType = "RBAC_DENIAL",
      actor = _currentUserIdentifier.value.ifBlank { "Unauthenticated" },
      details = "Blocked '$actionName': $reason"
    )
  }

  fun resolveAuthorizedStudentTarget(requestedStudentId: String?): Student? {
    return resolveAuthorizedStudentTarget(requestedStudent = null, requestedStudentId = requestedStudentId)
  }

  fun belongsToCurrentInstitute(recordInstituteId: String?): Boolean {
    if (!com.example.security.SecurityEngine.isValidInstituteId(recordInstituteId)) return false
    return recordInstituteId!!.trim().equals(getCurrentInstituteId(), ignoreCase = true)
  }

  fun buildCurrentFirestoreAuthContext(): com.example.security.FirestoreAuthContext? {
    if (!_isLoggedIn.value) return null
    val acc = _currentUserAccount.value
    val role = _currentRole.value
    val instId = getCurrentInstituteId()
    val uid = acc?.userId?.ifBlank { _currentUserIdentifier.value } ?: _currentUserIdentifier.value
    val linkedEntityId = when (role) {
      UserRole.STUDENT -> getActiveStudentForCurrentSession()?.id ?: acc?.linkedEntityId.orEmpty()
      UserRole.TEACHER -> getActiveTeacherForCurrentSession()?.id ?: acc?.linkedEntityId.orEmpty()
      UserRole.PARENT -> acc?.linkedEntityId.orEmpty()
      UserRole.ADMIN -> acc?.linkedEntityId.orEmpty()
    }
    val linkedStuIds = if (role == UserRole.PARENT) {
      getLinkedStudentsForCurrentParent().map { it.id }.ifEmpty { acc?.linkedStudentIds.orEmpty() }
    } else {
      acc?.linkedStudentIds.orEmpty()
    }
    val assignedBatches = if (role == UserRole.TEACHER) {
      val t = getActiveTeacherForCurrentSession()
      val fromBatches = _allBatches.value.filter {
        belongsToCurrentInstitute(it.instituteId) &&
          (it.teacherId.equals(t?.id, ignoreCase = true) || it.teacherName.equals(t?.name, ignoreCase = true))
      }.map { it.id }
      (t?.assignedBatchIds.orEmpty() + fromBatches).distinct()
    } else emptyList()
    val stuBatchId = if (role == UserRole.STUDENT) {
      getActiveStudentForCurrentSession()?.batchId.orEmpty()
    } else ""
    val parBatchIds = if (role == UserRole.PARENT) {
      getLinkedStudentsForCurrentParent().mapNotNull { it.batchId.takeIf { b -> b.isNotBlank() } }.distinct()
    } else emptyList()

    return com.example.security.FirestoreAuthContext(
      uid = uid,
      role = role,
      instituteId = instId,
      linkedEntityId = linkedEntityId,
      linkedStudentIds = linkedStuIds,
      assignedBatchIds = assignedBatches,
      batchId = stuBatchId,
      linkedBatchIds = parBatchIds,
      isAuthenticated = true
    )
  }

  private val _savedAccounts = MutableStateFlow<List<SavedAccount>>(emptyList())
  val savedAccounts = _savedAccounts.asStateFlow()

  private val _darkThemeMode = MutableStateFlow(DarkThemeMode.SYSTEM)
  val darkThemeMode = _darkThemeMode.asStateFlow()

  private val _allBatchEnrollments = MutableStateFlow<List<BatchEnrollmentEntity>>(emptyList())
  private val _scopedBatchEnrollments = MutableStateFlow<List<BatchEnrollmentEntity>>(emptyList())
  val batchEnrollments = _scopedBatchEnrollments.asStateFlow()

  private val _selectedChildIdForParent = MutableStateFlow<String?>(null)
  val selectedChildIdForParent = _selectedChildIdForParent.asStateFlow()

  fun setSelectedChildForParent(studentId: String): Boolean {
    if (!isAuthenticatedSession()) return false
    val cleanId = studentId.trim()
    if (cleanId.isBlank()) return false
    if (isAdminSession()) {
      val exists = _allStudents.value.any {
        belongsToCurrentInstitute(it.instituteId) && it.id.equals(cleanId, ignoreCase = true)
      }
      if (exists) {
        _selectedChildIdForParent.value = cleanId
        refreshScopedStateFlows()
        return true
      }
      return false
    }
    if (!isParentSession()) return false
    val linkedIds = getLinkedStudentsForCurrentParent().map { it.id }
    val matchedId = linkedIds.find { it.equals(cleanId, ignoreCase = true) }
    return if (matchedId != null) {
      _selectedChildIdForParent.value = matchedId
      refreshScopedStateFlows()
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

  internal fun getAppContextOrNull(): android.content.Context? = appContext

  internal fun bindStudentBatchRepository(
    context: android.content.Context,
    repo: StudentBatchRepository
  ) {
    if (appContext == null) {
      appContext = context.applicationContext ?: context
    }
    studentBatchRepo = repo
  }

  private fun resolveStudentBatchRepo(): StudentBatchRepository? {
    val existing = studentBatchRepo
    if (existing != null && existing.isDatabaseOpenAndCurrent()) {
      return existing
    }
    val ctx = appContext ?: return existing
    if (com.example.data.db.AppRoomDatabase.getInstanceOrNull() != null) {
      return StudentBatchRepository(ctx).also { studentBatchRepo = it }
    }
    return existing
  }

  suspend fun findStudentLocal(studentId: String): Student? = withContext(Dispatchers.IO) {
    val cleanId = studentId.trim()
    if (cleanId.isBlank()) return@withContext null
    _allStudents.value.find { it.id == cleanId }?.let { return@withContext it }
    val dbInst = com.example.data.db.AppRoomDatabase.getInstanceOrNull()
      ?: appContext?.let { com.example.data.db.AppRoomDatabase.getDatabase(it) }
    if (dbInst != null) {
      val entity = dbInst.studentDao().getStudentByIdOnce(cleanId)
      if (entity != null && !entity.status.equals("Inactive", ignoreCase = true)) {
        return@withContext entity.toDomain()
      }
    }
    return@withContext null
  }

  fun findStudentCached(studentId: String): Student? {
    val cleanId = studentId.trim()
    if (cleanId.isBlank()) return null
    return _allStudents.value.find { it.id == cleanId && !it.status.equals("Inactive", ignoreCase = true) }
  }

  internal fun onLocalStudentUpserted(
    student: Student,
    enrollment: com.example.data.db.BatchEnrollmentEntity? = null
  ) {
    _allStudents.update { current ->
      val idx = current.indexOfFirst { it.id == student.id }
      if (student.status.equals("Inactive", ignoreCase = true)) {
        current.filterNot { it.id == student.id }
      } else if (idx >= 0) {
        current.toMutableList().also { it[idx] = student }
      } else {
        current + student
      }
    }
    if (enrollment != null) {
      _allBatchEnrollments.update { current ->
        val filtered = current.map { existing ->
          if (existing.studentId == student.id && existing.id != enrollment.id) {
            existing.copy(status = "Inactive")
          } else {
            existing
          }
        }.filterNot { it.id == enrollment.id }
        filtered + enrollment
      }
    }
    syncBatchStudentCounts()
    refreshScopedStateFlows()
  }

  internal fun onLocalStudentsDeactivated(studentIds: List<String>) {
    val idSet = studentIds.map { it.trim() }.filter { it.isNotBlank() }.toSet()
    if (idSet.isEmpty()) return
    _allStudents.update { list -> list.filterNot { idSet.contains(it.id) } }
    _allBatchEnrollments.update { list ->
      list.map { if (idSet.contains(it.studentId)) it.copy(status = "Inactive") else it }
    }
    _allUserAccounts.update { list ->
      list.map { if (idSet.contains(it.linkedEntityId)) it.copy(status = "Inactive") else it }
    }
    syncBatchStudentCounts()
    refreshScopedStateFlows()
  }

  suspend fun findExistingStudentInstituteId(studentId: String): String? = withContext(Dispatchers.IO) {
    val cleanId = studentId.trim()
    if (cleanId.isBlank()) return@withContext null
    _allStudents.value.find { it.id == cleanId }?.let { return@withContext it.instituteId }
    val dbInst = com.example.data.db.AppRoomDatabase.getInstanceOrNull()
      ?: appContext?.let { com.example.data.db.AppRoomDatabase.getDatabase(it) }
    if (dbInst != null) {
      val entity = dbInst.studentDao().getStudentByIdOnce(cleanId)
      if (entity != null) return@withContext entity.instituteId
    }
    return@withContext null
  }

  fun findExistingStudentInstituteIdCached(studentId: String): String? {
    val cleanId = studentId.trim()
    if (cleanId.isBlank()) return null
    return _allStudents.value.find { it.id == cleanId }?.instituteId
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
    if (!isCleared && _allNotifications.value.isEmpty()) {
      _allNotifications.value = listOf(
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
    val existingRepoValid = studentBatchRepo?.isDatabaseOpenAndCurrent() == true
    val repo = studentBatchRepo?.takeIf {
      collectorsInitializedForContextHash == ctxHash && existingRepoValid
    } ?: StudentBatchRepository(appCtx).also { studentBatchRepo = it }

    if (!isCleared) {
      _badges.value = SeedData.getDefaultBadges()
    }

    val hasActiveCollectors = existingRepoValid &&
      collectorJobs.isNotEmpty() &&
      collectorJobs.all { it.isActive } &&
      collectorsInitializedForContextHash == ctxHash

    if (!hasActiveCollectors) {
      cancelAllCollectors()
      collectorsInitializedForContextHash = ctxHash

      // Perform one-shot deterministic demo seeding on Dispatchers.IO without blocking main thread
      val seedDeferred = repoScope.async<Unit> {
        demoSeedMutex.withLock {
          try {
            seedInitialDemoDataIfRequired(repo)
          } catch (e: Throwable) {
            android.util.Log.w("AppRepository", "Demo data seeding fallback warning: ${e.message}")
          }
        }
      }
      demoSeedDeferred = seedDeferred

      // Pure read-only Room Flow collectors updating internal caches and projecting role-scoped StateFlows
      collectorJobs.add(
        repoScope.launch {
          repo.allStudents.collect { list ->
            _allStudents.value = list
            syncBatchStudentCounts(list)
            refreshScopedStateFlows()
          }
        }
      )

      collectorJobs.add(
        repoScope.launch {
          repo.allBatches.collect { list ->
            val updated = list.map { b ->
              val count = _allStudents.value.count {
                it.instituteId.equals(b.instituteId, ignoreCase = true) &&
                  it.batch.equals(b.name, ignoreCase = true) &&
                  !it.status.equals("Inactive", ignoreCase = true) &&
                  !it.status.equals("Archived", ignoreCase = true)
              }
              b.copy(studentCount = count)
            }
            _allBatches.value = updated
            refreshScopedBatches()
          }
        }
      )

      collectorJobs.add(
        repoScope.launch {
          repo.allEnrollments.collect { list ->
            _allBatchEnrollments.value = list
            refreshScopedBatchEnrollments()
          }
        }
      )

      collectorJobs.add(
        repoScope.launch {
          repo.allTeachers.collect { list ->
            _allTeachers.value = list
            refreshScopedTeachers()
          }
        }
      )

      collectorJobs.add(
        repoScope.launch {
          repo.allUserAccounts.collect { entities ->
            _allUserAccounts.value = entities.map { it.toDomain() }
            val activeId = _currentUserIdentifier.value
            var roleChanged = false
            if (_isLoggedIn.value && activeId.isNotBlank()) {
              val matched = _allUserAccounts.value.find {
                it.email.equals(activeId, ignoreCase = true) ||
                  it.userId.equals(activeId, ignoreCase = true) ||
                  it.linkedEntityId.equals(activeId, ignoreCase = true) ||
                  (it.mobile.isNotBlank() && it.mobile == activeId)
              }
              if (matched != null) {
                val authoritativeRole = com.example.security.SecurityEngine.getVerifiedRole(activeId)
                  ?: getAccountStatus(activeId).role
                  ?: matched.role
                if (_currentRole.value != authoritativeRole) {
                  roleChanged = true
                }
                _currentUserAccount.value = matched.copy(role = authoritativeRole)
                _currentRole.value = authoritativeRole
              }
            }
            if (roleChanged) {
              refreshScopedStateFlows()
            } else {
              refreshScopedUserAccounts()
            }
          }
        }
      )

      collectorJobs.add(
        repoScope.launch {
          repo.allAttendance.collect { list ->
            _allAttendance.value = list
            if (list.isNotEmpty()) {
              recalculateAllStudentsAttendanceFromRecords(list)
              refreshScopedStudents()
            }
            refreshScopedAttendance()
          }
        }
      )

      collectorJobs.add(
        repoScope.launch {
          repo.allFeeInvoices.collect { list ->
            _allFees.value = list
            refreshScopedFees()
          }
        }
      )

      collectorJobs.add(
        repoScope.launch {
          repo.allFeePayments.collect { list ->
            _allFeePayments.value = list
            refreshScopedFeePayments()
          }
        }
      )

      collectorJobs.add(
        repoScope.launch {
          repo.allHomeworks.collect { list ->
            _allHomework.value = list
            refreshScopedHomework()
          }
        }
      )

      collectorJobs.add(
        repoScope.launch {
          repo.allDoubts.collect { list ->
            _allDoubts.value = list
            refreshScopedDoubts()
          }
        }
      )

      collectorJobs.add(
        repoScope.launch {
          repo.allTests.collect { list ->
            _allTests.value = list
            refreshScopedTests()
          }
        }
      )

      collectorJobs.add(
        repoScope.launch {
          repo.allSubjects.collect { list ->
            _allSubjects.value = list
            refreshScopedSubjects()
          }
        }
      )

      collectorJobs.add(
        repoScope.launch {
          repo.allLeads.collect { list ->
            _allLeads.value = list
            refreshScopedLeads()
          }
        }
      )

      collectorJobs.add(
        repoScope.launch {
          repo.allOnlineForms.collect { list ->
            _allOnlineForms.value = list
            refreshScopedOnlineForms()
          }
        }
      )

      collectorJobs.add(
        repoScope.launch {
          repo.allNotifications.collect { list ->
            if (list.isNotEmpty() || (preferences?.getBoolean("is_data_cleared", false) == true)) {
              _allNotifications.value = list
            }
            refreshScopedNotifications()
          }
        }
      )

      collectorJobs.add(
        repoScope.launch {
          repo.allTimetableEntries.collect { list ->
            _allDynamicTimetable.value = list
            refreshScopedDynamicTimetable()
          }
        }
      )

      collectorJobs.add(
        repoScope.launch {
          repo.allWeeklyTimetable.collect { list ->
            _allTimetable.value = list
            refreshScopedTimetable()
          }
        }
      )

      collectorJobs.add(
        repoScope.launch {
          repo.allQuestions.collect { list ->
            _allQuestions.value = list
            refreshScopedQuestions()
          }
        }
      )

      collectorJobs.add(
        repoScope.launch {
          repo.allStudentAttempts.collect { list ->
            _allStudentTestAttempts.value = list
            refreshScopedStudentTestAttempts()
          }
        }
      )

      collectorJobs.add(
        repoScope.launch {
          repo.allStudyLogs.collect { list ->
            _allStudyLogs.value = list
            refreshScopedStudyLogs()
          }
        }
      )

      collectorJobs.add(
        repoScope.launch {
          repo.allStudentGoals.collect { list ->
            _allStudentGoals.value = list
            refreshScopedStudentGoal()
          }
        }
      )

      collectorJobs.add(
        repoScope.launch {
          repo.allParentMeetings.collect { list ->
            _allMeetings.value = list
            refreshScopedMeetings()
          }
        }
      )
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
      refreshScopedStateFlows()
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
        if (FirebaseAuthService.isAvailable() && !com.example.security.SecurityEngine.hasRegisteredAccount(_currentUserIdentifier.value)) {
          logout()
        }
      }
    )
  }

  private suspend fun seedInitialDemoDataIfRequired(repo: StudentBatchRepository) {
    ensurePrimaryAdminUserAccountSeeded(repo)
    val isCleared = preferences?.getBoolean("is_data_cleared", false) ?: false
    if (isCleared) return
    val alreadySeeded = preferences?.getBoolean("initial_demo_seed_completed", false) ?: false
    val needsFullSeed = !alreadySeeded || repo.getUserAccountCount() <= 1
    if (!needsFullSeed) {
      return
    }

    val demoStudents = SeedData.get15DemoStudents().map { stu ->
      stu.copy(
        batchId = stu.batchId.ifBlank { SeedData.batchIdForName(stu.batch) },
        parentId = stu.parentId.ifBlank { "PAR_${stu.id}" },
        parentEmail = stu.parentEmail.ifBlank { "parent_${stu.id.lowercase()}@mytuition.com" },
        instituteId = "FSI_MAIN"
      )
    }
    val demoTeachers = SeedData.defaultTeachers

    if (needsFullSeed) {
      val seededBatches = SeedData.defaultBatches.map { b ->
        val count = demoStudents.count { it.batch.equals(b.name, ignoreCase = true) }
        b.copy(studentCount = count, instituteId = "FSI_MAIN")
      }
      seededBatches.forEach { b ->
        repo.insertBatch(b)
      }
      repo.insertTeachers(demoTeachers)
      _allBatches.value = seededBatches
      _allTeachers.value = demoTeachers
    }
    if (_allStudents.value.isEmpty() || needsFullSeed) {
      repo.bulkInsertStudents(demoStudents)
      _allStudents.value = demoStudents
    }
    if (needsFullSeed) {
      if (repo.getAttendanceCount() == 0) {
        val defaultsAtt = getDefaultAttendance()
        if (defaultsAtt.isNotEmpty()) {
          repo.insertAttendanceList(defaultsAtt)
        }
      }
      repo.insertFeeInvoices(SeedData.getDefaultFeeRecords())
      repo.insertFeePayments(SeedData.getDefaultFeePayments())
      repo.insertHomeworks(SeedData.getDefaultHomeworks())
      repo.insertDoubts(SeedData.getDefaultDoubts())
      repo.insertTests(SeedData.getDefaultTestRecords())
      repo.insertSubjects(SeedData.defaultSubjects)
      repo.insertLeads(SeedData.getDefaultLeads())
      repo.insertOnlineForms(SeedData.getDefaultOnlineForms())
      if (_allNotifications.value.isNotEmpty()) {
        repo.insertNotifications(_allNotifications.value)
      }
      repo.insertTimetableEntries(SeedData.getDefaultTimetableEntries())
      repo.insertWeeklyTimetableEntries(SeedData.getDefaultWeeklyTimetable())
      repo.insertQuestions(SeedData.getDefaultQuestions())
    }
    ensureDefaultUserAccountsSeeded(repo, demoStudents, demoTeachers)
    ensurePrimaryAdminUserAccountSeeded(repo)
    preferences?.edit()?.putBoolean("initial_demo_seed_completed", true)?.apply()
  }

  private suspend fun ensurePrimaryAdminUserAccountSeeded(repo: StudentBatchRepository) {
    val existing = repo.findUserAccount("joshiakash1712@gmail.com")
    val adminEntity = if (existing == null) {
      com.example.data.db.UserAccountEntity(
        userId = "USR_ADM_PRIMARY",
        email = "joshiakash1712@gmail.com",
        mobile = "",
        name = "Akash Joshi",
        passwordHash = "",
        passwordSalt = "",
        role = UserRole.ADMIN.name,
        linkedEntityId = "ADM_01",
        status = "Active",
        instituteId = "FSI_MAIN"
      ).also { repo.insertUserAccount(it) }
    } else if (!existing.role.equals(UserRole.ADMIN.name, ignoreCase = true) || !existing.status.equals("Active", ignoreCase = true)) {
      existing.copy(
        role = UserRole.ADMIN.name,
        status = "Active",
        name = existing.name.ifBlank { "Akash Joshi" },
        instituteId = existing.instituteId.ifBlank { "FSI_MAIN" }
      ).also { repo.updateUserAccount(it) }
    } else {
      existing
    }
    val adminDomain = adminEntity.toDomain()
    _allUserAccounts.update { list ->
      val filtered = list.filterNot { it.email.equals("joshiakash1712@gmail.com", ignoreCase = true) }
      listOf(adminDomain) + filtered
    }
  }

  private suspend fun ensureDefaultUserAccountsSeeded(
    repo: StudentBatchRepository,
    studentsList: List<Student>,
    teachersList: List<Teacher>
  ) {
    if (repo.getUserAccountCount() > 1) return
    val accounts = mutableListOf<com.example.data.db.UserAccountEntity>()

    accounts.add(
      com.example.data.db.UserAccountEntity(
        userId = "USR_ADM_PRIMARY",
        email = "joshiakash1712@gmail.com",
        mobile = "",
        name = "Akash Joshi",
        passwordHash = "",
        passwordSalt = "",
        role = UserRole.ADMIN.name,
        linkedEntityId = "ADM_01",
        status = "Active",
        instituteId = "FSI_MAIN"
      )
    )

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
    refreshScopedStateFlows()
  }

  // =========================================================================
  // ROLE-SCOPED & TENANT-ISOLATED IDENTITY RESOLUTION HELPERS
  // =========================================================================
  fun getActiveStudentForCurrentSession(): Student? {
    if (!isAuthenticatedSession()) return null
    val account = _currentUserAccount.value
    val isAdmin = _currentRole.value == UserRole.ADMIN || account?.role == UserRole.ADMIN
    if (!isAdmin && _currentRole.value != UserRole.STUDENT) {
      return null
    }
    val allStu = _allStudents.value.filter {
      belongsToCurrentInstitute(it.instituteId) &&
        !it.status.equals("Inactive", ignoreCase = true) &&
        !it.status.equals("Archived", ignoreCase = true)
    }
    if (allStu.isEmpty()) return null
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
    // Only allow fallback preview for an authenticated ADMIN inspecting the Student Portal within their own institute
    if (isAdmin) {
      return allStu.firstOrNull()
    }
    return null
  }

  fun getLinkedStudentsForCurrentParent(): List<Student> {
    if (!isAuthenticatedSession()) return emptyList()
    val account = _currentUserAccount.value
    val isAdmin = _currentRole.value == UserRole.ADMIN || account?.role == UserRole.ADMIN
    if (!isAdmin && _currentRole.value != UserRole.PARENT) {
      return emptyList()
    }
    val allStu = _allStudents.value.filter {
      belongsToCurrentInstitute(it.instituteId) &&
        !it.status.equals("Inactive", ignoreCase = true) &&
        !it.status.equals("Archived", ignoreCase = true)
    }
    if (allStu.isEmpty()) return emptyList()
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

    // Only allow fallback preview for an authenticated ADMIN inspecting the Parent Portal within their own institute
    if (isAdmin) {
      return listOfNotNull(allStu.firstOrNull())
    }
    return emptyList()
  }

  fun getActiveTeacherForCurrentSession(): Teacher? {
    if (!isAuthenticatedSession()) return null
    val account = _currentUserAccount.value
    val isAdmin = _currentRole.value == UserRole.ADMIN || account?.role == UserRole.ADMIN
    if (!isAdmin && _currentRole.value != UserRole.TEACHER) {
      return null
    }
    val allTch = _allTeachers.value.filter { belongsToCurrentInstitute(it.instituteId) }
    if (allTch.isEmpty()) return null
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
    // Only allow fallback preview for an authenticated ADMIN inspecting the Teacher Portal within their own institute
    if (isAdmin) {
      return allTch.firstOrNull()
    }
    return null
  }

  fun getStudentsForCurrentSession(): List<Student> {
    if (!isAuthenticatedSession()) return emptyList()
    val instStudents = _allStudents.value.filter {
      belongsToCurrentInstitute(it.instituteId) &&
        !it.status.equals("Inactive", ignoreCase = true) &&
        !it.status.equals("Archived", ignoreCase = true)
    }
    return when (_currentRole.value) {
      UserRole.ADMIN -> instStudents
      UserRole.TEACHER -> getStudentsForCurrentTeacher()
      UserRole.STUDENT -> listOfNotNull(getActiveStudentForCurrentSession())
      UserRole.PARENT -> getLinkedStudentsForCurrentParent()
    }
  }

  fun getTeachersForCurrentSession(): List<Teacher> {
    if (!isAuthenticatedSession()) return emptyList()
    val instTeachers = _allTeachers.value.filter { belongsToCurrentInstitute(it.instituteId) }
    return when (_currentRole.value) {
      UserRole.ADMIN -> instTeachers
      UserRole.TEACHER -> listOfNotNull(getActiveTeacherForCurrentSession())
      UserRole.STUDENT -> {
        val stu = getActiveStudentForCurrentSession() ?: return emptyList()
        instTeachers.filter { t ->
          isTeacherAssignedToBatch(stu.batchId.ifBlank { stu.batch }, t) ||
            isTeacherAssignedToBatch(stu.batch, t)
        }.map { it.copy(salary = 0, incentives = 0, deductions = 0, activationCode = "") }
      }
      UserRole.PARENT -> {
        val linked = getLinkedStudentsForCurrentParent()
        if (linked.isEmpty()) return emptyList()
        instTeachers.filter { t ->
          linked.any { stu ->
            isTeacherAssignedToBatch(stu.batchId.ifBlank { stu.batch }, t) ||
              isTeacherAssignedToBatch(stu.batch, t)
          }
        }.map { it.copy(salary = 0, incentives = 0, deductions = 0, activationCode = "") }
      }
    }
  }

  fun getAttendanceForCurrentSession(): List<AttendanceRecord> {
    if (!isAuthenticatedSession()) return emptyList()
    val activeStudentIds = _allStudents.value
      .filter {
        belongsToCurrentInstitute(it.instituteId) &&
          !it.status.equals("Inactive", ignoreCase = true) &&
          !it.status.equals("Archived", ignoreCase = true)
      }
      .map { it.id.lowercase() }
      .toSet()
    val instAttendance = _allAttendance.value.filter {
      belongsToCurrentInstitute(it.instituteId) && (it.studentId.isBlank() || it.studentId.lowercase() in activeStudentIds)
    }
    return when (_currentRole.value) {
      UserRole.ADMIN -> instAttendance
      UserRole.TEACHER -> {
        val teacherBatches = getAssignedBatchesForCurrentTeacher()
        val batchIds = teacherBatches.map { it.id.lowercase() }.toSet()
        val batchNames = teacherBatches.map { it.name.lowercase() }.toSet()
        instAttendance.filter {
          (it.batchId.isNotBlank() && it.batchId.lowercase() in batchIds) ||
            (it.batchName.isNotBlank() && it.batchName.lowercase() in batchNames)
        }
      }
      UserRole.STUDENT -> {
        val stuId = getActiveStudentForCurrentSession()?.id ?: return emptyList()
        instAttendance.filter { it.studentId.equals(stuId, ignoreCase = true) }
      }
      UserRole.PARENT -> {
        val linkedIds = getLinkedStudentsForCurrentParent().map { it.id.lowercase() }.toSet()
        instAttendance.filter { it.studentId.lowercase() in linkedIds }
      }
    }
  }

  fun getFeesForCurrentSession(): List<FeeRecord> {
    if (!isAuthenticatedSession()) return emptyList()
    val activeStudentIds = _allStudents.value
      .filter {
        belongsToCurrentInstitute(it.instituteId) &&
          !it.status.equals("Inactive", ignoreCase = true) &&
          !it.status.equals("Archived", ignoreCase = true)
      }
      .map { it.id.lowercase() }
      .toSet()
    val instFees = _allFees.value.filter {
      belongsToCurrentInstitute(it.instituteId) && (it.studentId.isBlank() || it.studentId.lowercase() in activeStudentIds)
    }
    return when (_currentRole.value) {
      UserRole.ADMIN -> instFees
      UserRole.TEACHER -> emptyList() // Teachers are not authorized to read student fee records
      UserRole.STUDENT -> {
        val stuId = getActiveStudentForCurrentSession()?.id ?: return emptyList()
        instFees.filter { it.studentId.equals(stuId, ignoreCase = true) }
      }
      UserRole.PARENT -> {
        val linkedIds = getLinkedStudentsForCurrentParent().map { it.id.lowercase() }.toSet()
        instFees.filter { it.studentId.lowercase() in linkedIds }
      }
    }
  }

  fun getFeePaymentsForCurrentSession(): List<StudentFeePayment> {
    if (!isAuthenticatedSession()) return emptyList()
    val activeStudentIds = _allStudents.value
      .filter {
        belongsToCurrentInstitute(it.instituteId) &&
          !it.status.equals("Inactive", ignoreCase = true) &&
          !it.status.equals("Archived", ignoreCase = true)
      }
      .map { it.id.lowercase() }
      .toSet()
    val instPayments = _allFeePayments.value.filter {
      belongsToCurrentInstitute(it.instituteId) && (it.studentId.isBlank() || it.studentId.lowercase() in activeStudentIds)
    }
    return when (_currentRole.value) {
      UserRole.ADMIN -> instPayments
      UserRole.TEACHER -> emptyList() // Teachers are not authorized to read student fee payments
      UserRole.STUDENT -> {
        val stuId = getActiveStudentForCurrentSession()?.id ?: return emptyList()
        instPayments.filter { it.studentId.equals(stuId, ignoreCase = true) }
      }
      UserRole.PARENT -> {
        val linkedIds = getLinkedStudentsForCurrentParent().map { it.id.lowercase() }.toSet()
        instPayments.filter { it.studentId.lowercase() in linkedIds }
      }
    }
  }

  fun getBatchesForCurrentSession(): List<Batch> {
    if (!isAuthenticatedSession()) return emptyList()
    val instBatches = _allBatches.value.filter { belongsToCurrentInstitute(it.instituteId) }
    return when (_currentRole.value) {
      UserRole.ADMIN -> instBatches
      UserRole.TEACHER -> getAssignedBatchesForCurrentTeacher()
      UserRole.STUDENT -> {
        val stu = getActiveStudentForCurrentSession() ?: return emptyList()
        instBatches.filter {
          it.id.equals(stu.batchId, ignoreCase = true) || it.name.equals(stu.batch, ignoreCase = true)
        }
      }
      UserRole.PARENT -> {
        val linked = getLinkedStudentsForCurrentParent()
        val batchIds = linked.mapNotNull { it.batchId.takeIf { b -> b.isNotBlank() }?.lowercase() }.toSet()
        val batchNames = linked.mapNotNull { it.batch.takeIf { b -> b.isNotBlank() }?.lowercase() }.toSet()
        instBatches.filter {
          it.id.lowercase() in batchIds || it.name.lowercase() in batchNames
        }
      }
    }
  }

  fun getSubjectsForCurrentSession(): List<Subject> {
    if (!isAuthenticatedSession()) return emptyList()
    val instSubjects = _allSubjects.value.filter { belongsToCurrentInstitute(it.instituteId) }
    return when (_currentRole.value) {
      UserRole.ADMIN -> instSubjects
      UserRole.TEACHER -> {
        val teacher = getActiveTeacherForCurrentSession() ?: return emptyList()
        val assignedBatches = getAssignedBatchesForCurrentTeacher()
        val batchIds = assignedBatches.map { it.id.lowercase() }.toSet()
        val batchNames = assignedBatches.map { it.name.lowercase() }.toSet()
        instSubjects.filter { sub ->
          (sub.batchId.isBlank() && sub.batchName.isBlank()) ||
            sub.teacherId.equals(teacher.id, ignoreCase = true) ||
            (sub.batchId.isNotBlank() && sub.batchId.lowercase() in batchIds) ||
            (sub.batchName.isNotBlank() && sub.batchName.lowercase() in batchNames)
        }
      }
      UserRole.STUDENT -> {
        getActiveStudentForCurrentSession() ?: return emptyList()
        instSubjects
      }
      UserRole.PARENT -> {
        if (getLinkedStudentsForCurrentParent().isEmpty()) return emptyList()
        instSubjects
      }
    }
  }

  fun getTestsForCurrentSession(): List<TestRecord> {
    if (!isAuthenticatedSession()) return emptyList()
    val instTests = _allTests.value.filter { belongsToCurrentInstitute(it.instituteId) }
    return when (_currentRole.value) {
      UserRole.ADMIN -> instTests
      UserRole.TEACHER -> {
        val assignedBatches = getAssignedBatchesForCurrentTeacher()
        if (assignedBatches.isEmpty()) return emptyList()
        val batchIds = assignedBatches.map { it.id.lowercase() }.toSet()
        val batchNames = assignedBatches.map { it.name.lowercase() }.toSet()
        val allowedStudentIds = getStudentsForCurrentTeacher().map { it.id.lowercase() }.toSet()
        instTests.filter { tst ->
          (tst.batchId.isNotBlank() && tst.batchId.lowercase() in batchIds) ||
            (tst.batch.isNotBlank() && tst.batch.lowercase() in batchNames)
        }.map { tst ->
          tst.copy(studentMarks = tst.studentMarks.filterKeys { it.lowercase() in allowedStudentIds })
        }
      }
      UserRole.STUDENT -> {
        val stu = getActiveStudentForCurrentSession() ?: return emptyList()
        instTests.filter { tst ->
          (stu.batchId.isNotBlank() && tst.batchId.equals(stu.batchId, ignoreCase = true)) ||
            (stu.batch.isNotBlank() && tst.batch.equals(stu.batch, ignoreCase = true)) ||
            tst.studentMarks.keys.any { it.equals(stu.id, ignoreCase = true) }
        }.map { tst ->
          // Filter studentMarks to only the authenticated student's own score so other students' marks never leak
          tst.copy(studentMarks = tst.studentMarks.filterKeys { it.equals(stu.id, ignoreCase = true) })
        }
      }
      UserRole.PARENT -> {
        val linked = getLinkedStudentsForCurrentParent()
        if (linked.isEmpty()) return emptyList()
        val linkedIds = linked.map { it.id.lowercase() }.toSet()
        val batchIds = linked.mapNotNull { it.batchId.takeIf { b -> b.isNotBlank() }?.lowercase() }.toSet()
        val batchNames = linked.mapNotNull { it.batch.takeIf { b -> b.isNotBlank() }?.lowercase() }.toSet()
        instTests.filter { tst ->
          (tst.batchId.isNotBlank() && tst.batchId.lowercase() in batchIds) ||
            (tst.batch.isNotBlank() && tst.batch.lowercase() in batchNames) ||
            tst.studentMarks.keys.any { it.lowercase() in linkedIds }
        }.map { tst ->
          tst.copy(studentMarks = tst.studentMarks.filterKeys { it.lowercase() in linkedIds })
        }
      }
    }
  }

  fun getAttemptsForCurrentSession(): List<StudentTestAttempt> {
    if (!isAuthenticatedSession()) return emptyList()
    val activeStudentIds = _allStudents.value
      .filter {
        belongsToCurrentInstitute(it.instituteId) &&
          !it.status.equals("Inactive", ignoreCase = true) &&
          !it.status.equals("Archived", ignoreCase = true)
      }
      .map { it.id.lowercase() }
      .toSet()
    val instAttempts = _allStudentTestAttempts.value.filter {
      belongsToCurrentInstitute(it.instituteId) && (it.studentId.isBlank() || it.studentId.lowercase() in activeStudentIds)
    }
    return when (_currentRole.value) {
      UserRole.ADMIN -> instAttempts
      UserRole.TEACHER -> {
        val allowedStudentIds = getStudentsForCurrentTeacher().map { it.id.lowercase() }.toSet()
        instAttempts.filter { it.studentId.lowercase() in allowedStudentIds }
      }
      UserRole.STUDENT -> getAttemptsForCurrentStudent()
      UserRole.PARENT -> {
        val linkedIds = getLinkedStudentsForCurrentParent().map { it.id.lowercase() }.toSet()
        instAttempts.filter { it.studentId.lowercase() in linkedIds }
      }
    }
  }

  fun getDoubtsForCurrentSession(): List<DoubtItem> {
    if (!isAuthenticatedSession()) return emptyList()
    val activeStudentIds = _allStudents.value
      .filter {
        belongsToCurrentInstitute(it.instituteId) &&
          !it.status.equals("Inactive", ignoreCase = true) &&
          !it.status.equals("Archived", ignoreCase = true)
      }
      .map { it.id.lowercase() }
      .toSet()
    val instDoubts = _allDoubts.value.filter {
      belongsToCurrentInstitute(it.instituteId) && (it.studentId.isBlank() || it.studentId.lowercase() in activeStudentIds)
    }
    return when (_currentRole.value) {
      UserRole.ADMIN -> instDoubts
      UserRole.TEACHER -> {
        val assignedBatches = getAssignedBatchesForCurrentTeacher()
        if (assignedBatches.isEmpty()) return emptyList()
        val batchIds = assignedBatches.map { it.id.lowercase() }.toSet()
        val batchNames = assignedBatches.map { it.name.lowercase() }.toSet()
        val allowedStudentIds = getStudentsForCurrentTeacher().map { it.id.lowercase() }.toSet()
        instDoubts.filter { d ->
          d.studentId.lowercase() in allowedStudentIds ||
            (d.batchId.isNotBlank() && d.batchId.lowercase() in batchIds) ||
            (d.batchName.isNotBlank() && d.batchName.lowercase() in batchNames)
        }
      }
      UserRole.STUDENT -> getDoubtsForCurrentStudent()
      UserRole.PARENT -> {
        val linkedIds = getLinkedStudentsForCurrentParent().map { it.id.lowercase() }.toSet()
        instDoubts.filter { it.studentId.lowercase() in linkedIds }
      }
    }
  }

  fun getHomeworkForCurrentSession(): List<HomeworkItem> {
    if (!isAuthenticatedSession()) return emptyList()
    val instHw = _allHomework.value.filter { belongsToCurrentInstitute(it.instituteId) }
    return when (_currentRole.value) {
      UserRole.ADMIN -> instHw
      UserRole.TEACHER -> {
        val assignedBatches = getAssignedBatchesForCurrentTeacher()
        if (assignedBatches.isEmpty()) return emptyList()
        val batchIds = assignedBatches.map { it.id.lowercase() }.toSet()
        val batchNames = assignedBatches.map { it.name.lowercase() }.toSet()
        instHw.filter { hw ->
          (hw.batchId.isNotBlank() && hw.batchId.lowercase() in batchIds) ||
            (hw.batchName.isNotBlank() && hw.batchName.lowercase() in batchNames)
        }
      }
      UserRole.STUDENT -> getHomeworkForCurrentStudent()
      UserRole.PARENT -> {
        val linked = getLinkedStudentsForCurrentParent()
        if (linked.isEmpty()) return emptyList()
        val linkedIds = linked.map { it.id.lowercase() }.toSet()
        val batchIds = linked.mapNotNull { it.batchId.takeIf { b -> b.isNotBlank() }?.lowercase() }.toSet()
        val batchNames = linked.mapNotNull { it.batch.takeIf { b -> b.isNotBlank() }?.lowercase() }.toSet()
        instHw.filter { hw ->
          (hw.batchId.isNotBlank() && hw.batchId.lowercase() in batchIds) ||
            (hw.batchName.isNotBlank() && hw.batchName.lowercase() in batchNames)
        }.map { hw ->
          hw.copy(completedByStudentIds = hw.completedByStudentIds.filter { it.lowercase() in linkedIds })
        }
      }
    }
  }

  fun getStudyLogsForCurrentSession(): List<StudyLogItem> {
    if (!isAuthenticatedSession()) return emptyList()
    val instLogs = _allStudyLogs.value.filter { belongsToCurrentInstitute(it.instituteId) }
    return when (_currentRole.value) {
      UserRole.ADMIN -> instLogs
      UserRole.TEACHER -> {
        val allowedStudentIds = getStudentsForCurrentTeacher().map { it.id.lowercase() }.toSet()
        instLogs.filter { it.studentId.lowercase() in allowedStudentIds }
      }
      UserRole.STUDENT -> getStudyLogsForCurrentStudent()
      UserRole.PARENT -> {
        val linkedIds = getLinkedStudentsForCurrentParent().map { it.id.lowercase() }.toSet()
        instLogs.filter { it.studentId.lowercase() in linkedIds }
      }
    }
  }

  fun getTimetableForCurrentSession(): List<TimetableItem> {
    if (!isAuthenticatedSession()) return emptyList()
    val instTimetable = _allTimetable.value.filter { belongsToCurrentInstitute(it.instituteId) }
    return when (_currentRole.value) {
      UserRole.ADMIN -> instTimetable
      UserRole.TEACHER -> {
        val teacher = getActiveTeacherForCurrentSession() ?: return emptyList()
        val assignedBatches = getAssignedBatchesForCurrentTeacher()
        val batchIds = assignedBatches.map { it.id.lowercase() }.toSet()
        val batchNames = assignedBatches.map { it.name.lowercase() }.toSet()
        instTimetable.filter { item ->
          item.teacherId.equals(teacher.id, ignoreCase = true) ||
            isSameFaculty(item.teacherName, teacher.name) ||
            (item.batchId.isNotBlank() && item.batchId.lowercase() in batchIds) ||
            (item.batch.isNotBlank() && item.batch.lowercase() in batchNames)
        }
      }
      UserRole.STUDENT -> {
        val stu = getActiveStudentForCurrentSession() ?: return emptyList()
        instTimetable.filter { item ->
          (stu.batchId.isNotBlank() && item.batchId.equals(stu.batchId, ignoreCase = true)) ||
            (stu.batch.isNotBlank() && item.batch.equals(stu.batch, ignoreCase = true))
        }
      }
      UserRole.PARENT -> {
        val linked = getLinkedStudentsForCurrentParent()
        if (linked.isEmpty()) return emptyList()
        val batchIds = linked.mapNotNull { it.batchId.takeIf { b -> b.isNotBlank() }?.lowercase() }.toSet()
        val batchNames = linked.mapNotNull { it.batch.takeIf { b -> b.isNotBlank() }?.lowercase() }.toSet()
        instTimetable.filter { item ->
          (item.batchId.isNotBlank() && item.batchId.lowercase() in batchIds) ||
            (item.batch.isNotBlank() && item.batch.lowercase() in batchNames)
        }
      }
    }
  }

  fun getDynamicTimetableForCurrentSession(): List<DynamicTimetableEntry> {
    if (!isAuthenticatedSession()) return emptyList()
    val instDynamic = _allDynamicTimetable.value.filter { belongsToCurrentInstitute(it.instituteId) }
    return when (_currentRole.value) {
      UserRole.ADMIN -> instDynamic
      UserRole.TEACHER -> {
        val teacher = getActiveTeacherForCurrentSession() ?: return emptyList()
        val assignedBatches = getAssignedBatchesForCurrentTeacher()
        val batchIds = assignedBatches.map { it.id.lowercase() }.toSet()
        val batchNames = assignedBatches.map { it.name.lowercase() }.toSet()
        instDynamic.filter { entry ->
          entry.teacherId.equals(teacher.id, ignoreCase = true) ||
            isSameFaculty(entry.teacherName, teacher.name) ||
            (entry.batchId.isNotBlank() && entry.batchId.lowercase() in batchIds) ||
            (entry.batch.isNotBlank() && entry.batch.lowercase() in batchNames)
        }
      }
      UserRole.STUDENT -> {
        val stu = getActiveStudentForCurrentSession() ?: return emptyList()
        instDynamic.filter { entry ->
          (stu.batchId.isNotBlank() && entry.batchId.equals(stu.batchId, ignoreCase = true)) ||
            (stu.batch.isNotBlank() && entry.batch.equals(stu.batch, ignoreCase = true))
        }
      }
      UserRole.PARENT -> {
        val linked = getLinkedStudentsForCurrentParent()
        if (linked.isEmpty()) return emptyList()
        val batchIds = linked.mapNotNull { it.batchId.takeIf { b -> b.isNotBlank() }?.lowercase() }.toSet()
        val batchNames = linked.mapNotNull { it.batch.takeIf { b -> b.isNotBlank() }?.lowercase() }.toSet()
        instDynamic.filter { entry ->
          (entry.batchId.isNotBlank() && entry.batchId.lowercase() in batchIds) ||
            (entry.batch.isNotBlank() && entry.batch.lowercase() in batchNames)
        }
      }
    }
  }

  fun getQuestionsForCurrentSession(): List<QuestionItem> {
    if (!isAuthenticatedSession()) return emptyList()
    val instQuestions = _allQuestions.value.filter { belongsToCurrentInstitute(it.instituteId) }
    return when (_currentRole.value) {
      UserRole.ADMIN, UserRole.TEACHER -> instQuestions
      UserRole.STUDENT -> {
        getActiveStudentForCurrentSession() ?: return emptyList()
        instQuestions
      }
      UserRole.PARENT -> {
        if (getLinkedStudentsForCurrentParent().isEmpty()) return emptyList()
        instQuestions
      }
    }
  }

  fun getNotificationsForCurrentSession(): List<NotificationItem> {
    if (!isAuthenticatedSession()) return emptyList()
    val instNotes = _allNotifications.value.filter { belongsToCurrentInstitute(it.instituteId) }
    return when (_currentRole.value) {
      UserRole.ADMIN -> instNotes
      UserRole.TEACHER -> {
        val assignedBatches = getAssignedBatchesForCurrentTeacher()
        val batchIds = assignedBatches.map { it.id.lowercase() }.toSet()
        val batchNames = assignedBatches.map { it.name.lowercase() }.toSet()
        val allowedStudentIds = getStudentsForCurrentTeacher().map { it.id.lowercase() }.toSet()
        instNotes.filter { n ->
          val roleAllowed = n.recipientRole.equals("All", ignoreCase = true) ||
            n.recipientRole.equals("Teacher", ignoreCase = true) ||
            n.recipientRole.equals("Teachers Only", ignoreCase = true)
          val studentMatch = n.targetStudentId.isNullOrBlank() || n.targetStudentId.lowercase() in allowedStudentIds
          val batchMatch = (n.targetBatchId.isBlank() && n.targetBatchName.isNullOrBlank()) ||
            (n.targetBatchId.isNotBlank() && n.targetBatchId.lowercase() in batchIds) ||
            (!n.targetBatchName.isNullOrBlank() && n.targetBatchName.lowercase() in batchNames)
          roleAllowed && studentMatch && batchMatch
        }
      }
      UserRole.STUDENT -> {
        val stu = getActiveStudentForCurrentSession() ?: return emptyList()
        instNotes.filter { n ->
          val roleAllowed = n.recipientRole.equals("All", ignoreCase = true) ||
            n.recipientRole.equals("Student", ignoreCase = true) ||
            n.recipientRole.equals("Students Only", ignoreCase = true)
          val studentMatch = n.targetStudentId.isNullOrBlank() || n.targetStudentId.equals(stu.id, ignoreCase = true)
          val batchMatch = (n.targetBatchId.isBlank() && n.targetBatchName.isNullOrBlank()) ||
            (stu.batchId.isNotBlank() && n.targetBatchId.equals(stu.batchId, ignoreCase = true)) ||
            (stu.batch.isNotBlank() && n.targetBatchName.equals(stu.batch, ignoreCase = true))
          roleAllowed && studentMatch && batchMatch
        }
      }
      UserRole.PARENT -> {
        val linked = getLinkedStudentsForCurrentParent()
        if (linked.isEmpty()) return emptyList()
        val linkedIds = linked.map { it.id.lowercase() }.toSet()
        val batchIds = linked.mapNotNull { it.batchId.takeIf { b -> b.isNotBlank() }?.lowercase() }.toSet()
        val batchNames = linked.mapNotNull { it.batch.takeIf { b -> b.isNotBlank() }?.lowercase() }.toSet()
        instNotes.filter { n ->
          val roleAllowed = n.recipientRole.equals("All", ignoreCase = true) ||
            n.recipientRole.equals("Parent", ignoreCase = true) ||
            n.recipientRole.equals("Parents Only", ignoreCase = true)
          val studentMatch = n.targetStudentId.isNullOrBlank() || n.targetStudentId.lowercase() in linkedIds
          val batchMatch = (n.targetBatchId.isBlank() && n.targetBatchName.isNullOrBlank()) ||
            (n.targetBatchId.isNotBlank() && n.targetBatchId.lowercase() in batchIds) ||
            (!n.targetBatchName.isNullOrBlank() && n.targetBatchName.lowercase() in batchNames)
          roleAllowed && studentMatch && batchMatch
        }
      }
    }
  }

  fun getMeetingsForCurrentSession(): List<ParentMeetingItem> {
    if (!isAuthenticatedSession()) return emptyList()
    val instMeetings = _allMeetings.value.filter { belongsToCurrentInstitute(it.instituteId) }
    return when (_currentRole.value) {
      UserRole.ADMIN -> instMeetings
      UserRole.TEACHER -> {
        val teacher = getActiveTeacherForCurrentSession() ?: return emptyList()
        val allowedStudentIds = getStudentsForCurrentTeacher().map { it.id.lowercase() }.toSet()
        instMeetings.filter { m ->
          m.teacherId.equals(teacher.id, ignoreCase = true) ||
            isSameFaculty(m.teacherName, teacher.name) ||
            m.studentId.lowercase() in allowedStudentIds
        }
      }
      UserRole.STUDENT -> {
        val stu = getActiveStudentForCurrentSession() ?: return emptyList()
        instMeetings.filter { it.studentId.equals(stu.id, ignoreCase = true) }
      }
      UserRole.PARENT -> {
        val linkedIds = getLinkedStudentsForCurrentParent().map { it.id.lowercase() }.toSet()
        if (linkedIds.isEmpty()) return emptyList()
        instMeetings.filter { it.studentId.lowercase() in linkedIds }
      }
    }
  }

  fun getLeadsForCurrentSession(): List<LeadRecord> {
    if (!isAdminSession()) return emptyList()
    return _allLeads.value.filter { belongsToCurrentInstitute(it.instituteId) }
  }

  fun getOnlineFormsForCurrentSession(): List<OnlineFormSubmission> {
    if (!isAdminSession()) return emptyList()
    return _allOnlineForms.value.filter { belongsToCurrentInstitute(it.instituteId) }
  }

  fun getUserAccountsForCurrentSession(): List<UserAccount> {
    if (!isAuthenticatedSession()) return emptyList()
    if (isAdminSession()) {
      return _allUserAccounts.value.filter { belongsToCurrentInstitute(it.instituteId) }
    }
    return listOfNotNull(_currentUserAccount.value)
  }

  fun getBatchEnrollmentsForCurrentSession(): List<BatchEnrollmentEntity> {
    if (!isAuthenticatedSession()) return emptyList()
    val instStudentIds = _allStudents.value
      .filter { belongsToCurrentInstitute(it.instituteId) }
      .map { it.id.lowercase() }
      .toSet()
    val instEnrollments = _allBatchEnrollments.value.filter { it.studentId.lowercase() in instStudentIds }
    return when (_currentRole.value) {
      UserRole.ADMIN -> instEnrollments
      UserRole.TEACHER -> {
        val batchIds = getAssignedBatchesForCurrentTeacher().map { it.id.lowercase() }.toSet()
        instEnrollments.filter { it.batchId.lowercase() in batchIds }
      }
      UserRole.STUDENT -> {
        val stuId = getActiveStudentForCurrentSession()?.id ?: return emptyList()
        instEnrollments.filter { it.studentId.equals(stuId, ignoreCase = true) }
      }
      UserRole.PARENT -> {
        val linkedIds = getLinkedStudentsForCurrentParent().map { it.id.lowercase() }.toSet()
        instEnrollments.filter { it.studentId.lowercase() in linkedIds }
      }
    }
  }

  fun refreshScopedStudents() {
    _scopedStudents.value = if (!isAuthenticatedSession()) emptyList() else getStudentsForCurrentSession()
  }

  fun refreshScopedTeachers() {
    _scopedTeachers.value = if (!isAuthenticatedSession()) emptyList() else getTeachersForCurrentSession()
  }

  fun refreshScopedBatches() {
    _scopedBatches.value = if (!isAuthenticatedSession()) emptyList() else getBatchesForCurrentSession()
  }

  fun refreshScopedSubjects() {
    _scopedSubjects.value = if (!isAuthenticatedSession()) emptyList() else getSubjectsForCurrentSession()
  }

  fun refreshScopedAttendance() {
    _scopedAttendance.value = if (!isAuthenticatedSession()) emptyList() else getAttendanceForCurrentSession()
  }

  fun refreshScopedFees() {
    _scopedFees.value = if (!isAuthenticatedSession()) emptyList() else getFeesForCurrentSession()
  }

  fun refreshScopedFeePayments() {
    _scopedFeePayments.value = if (!isAuthenticatedSession()) emptyList() else getFeePaymentsForCurrentSession()
  }

  fun refreshScopedTests() {
    _scopedTests.value = if (!isAuthenticatedSession()) emptyList() else getTestsForCurrentSession()
  }

  fun refreshScopedStudentTestAttempts() {
    _scopedStudentTestAttempts.value = if (!isAuthenticatedSession()) emptyList() else getAttemptsForCurrentSession()
  }

  fun refreshScopedQuestions() {
    _scopedQuestions.value = if (!isAuthenticatedSession()) emptyList() else getQuestionsForCurrentSession()
  }

  fun refreshScopedDoubts() {
    _scopedDoubts.value = if (!isAuthenticatedSession()) emptyList() else getDoubtsForCurrentSession()
  }

  fun refreshScopedHomework() {
    _scopedHomework.value = if (!isAuthenticatedSession()) emptyList() else getHomeworkForCurrentSession()
  }

  fun refreshScopedTimetable() {
    _scopedTimetable.value = if (!isAuthenticatedSession()) emptyList() else getTimetableForCurrentSession()
  }

  fun refreshScopedDynamicTimetable() {
    _scopedDynamicTimetable.value = if (!isAuthenticatedSession()) emptyList() else getDynamicTimetableForCurrentSession()
  }

  fun refreshScopedStudyLogs() {
    _scopedStudyLogs.value = if (!isAuthenticatedSession()) emptyList() else getStudyLogsForCurrentSession()
  }

  fun refreshScopedStudentGoal() {
    _scopedStudentGoal.value = if (!isAuthenticatedSession()) {
      GoalItem(
        studentId = "",
        targetMarks = 0,
        targetPercent = 0,
        targetRank = 0,
        targetAttendance = 0,
        weeklyStudyHours = 0,
        instituteId = ""
      )
    } else {
      getGoalForCurrentStudent()
    }
  }

  fun refreshScopedNotifications() {
    _scopedNotifications.value = if (!isAuthenticatedSession()) emptyList() else getNotificationsForCurrentSession()
  }

  fun refreshScopedMeetings() {
    _scopedMeetings.value = if (!isAuthenticatedSession()) emptyList() else getMeetingsForCurrentSession()
  }

  fun refreshScopedLeads() {
    _scopedLeads.value = if (!isAuthenticatedSession()) emptyList() else getLeadsForCurrentSession()
  }

  fun refreshScopedOnlineForms() {
    _scopedOnlineForms.value = if (!isAuthenticatedSession()) emptyList() else getOnlineFormsForCurrentSession()
  }

  fun refreshScopedUserAccounts() {
    _scopedUserAccounts.value = if (!isAuthenticatedSession()) emptyList() else getUserAccountsForCurrentSession()
  }

  fun refreshScopedBatchEnrollments() {
    _scopedBatchEnrollments.value = if (!isAuthenticatedSession()) emptyList() else getBatchEnrollmentsForCurrentSession()
  }

  fun refreshScopedStateFlows() {
    refreshScopedStudents()
    refreshScopedTeachers()
    refreshScopedBatches()
    refreshScopedSubjects()
    refreshScopedAttendance()
    refreshScopedFees()
    refreshScopedFeePayments()
    refreshScopedTests()
    refreshScopedStudentTestAttempts()
    refreshScopedQuestions()
    refreshScopedDoubts()
    refreshScopedHomework()
    refreshScopedTimetable()
    refreshScopedDynamicTimetable()
    refreshScopedStudyLogs()
    refreshScopedStudentGoal()
    refreshScopedNotifications()
    refreshScopedMeetings()
    refreshScopedLeads()
    refreshScopedOnlineForms()
    refreshScopedUserAccounts()
    refreshScopedBatchEnrollments()
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
  private val _allStudents = MutableStateFlow<List<Student>>(SeedData.get15DemoStudents())
  private val _scopedStudents = MutableStateFlow<List<Student>>(emptyList())
  val students = _scopedStudents.asStateFlow()

  /**
   * Reconciles remote Firestore student documents into Room using stable student IDs (never names)
   * and LAST_WRITE_WINS conflict strategy (Section 27).
   */
  fun reconcileRemoteStudentsToRoom(remoteList: List<FirestoreStudent>) {
    if (remoteList.isEmpty()) return
    val activeInst = getCurrentInstituteId()
    val validRemote = remoteList.filter {
      it.id.isNotBlank() &&
        com.example.security.SecurityEngine.isValidInstituteId(it.instituteId) &&
        (!_isLoggedIn.value || it.instituteId.equals(activeInst, ignoreCase = true))
    }
    if (validRemote.isEmpty()) return

    val updatedStudents = mutableListOf<Student>()
    _allStudents.update { localList ->
      val localById = localList.associateBy { it.id }.toMutableMap()
      validRemote.forEach { remote ->
        val existing = localById[remote.id]
        // Prevent cross-institute overwrite of an existing student belonging to another institute
        if (existing != null && !existing.instituteId.equals(remote.instituteId, ignoreCase = true)) {
          return@forEach
        }
        val merged = remote.toDomain(existing)
        localById[remote.id] = merged
        updatedStudents.add(merged)
      }
      localById.values.toList()
    }
    syncBatchStudentCounts()
    refreshScopedStateFlows()
    if (updatedStudents.isNotEmpty()) {
      repoScope.launch {
        studentBatchRepo?.bulkInsertStudents(updatedStudents)
      }
    }
  }

  suspend fun addStudent(student: Student): Boolean = withContext(Dispatchers.IO) {
    if (!checkOnlineOrBlock("add student record")) return@withContext false
    val existingInstId = findExistingStudentInstituteId(student.id)
    if (existingInstId != null &&
      (!com.example.security.SecurityEngine.isValidInstituteId(student.instituteId) ||
        !existingInstId.equals(student.instituteId, ignoreCase = true))
    ) {
      com.example.security.SecurityEngine.logEvent(
        severity = com.example.security.SecuritySeverity.ALERT,
        eventType = "INSTITUTE_REASSIGNMENT_BLOCKED",
        actor = _currentUserIdentifier.value.ifBlank { "System" },
        details = "Blocked attempt in addStudent to change instituteId of student '${student.id}' from '$existingInstId' to '${student.instituteId}'."
      )
      return@withContext false
    }
    if (!requireAdminSession("addStudent", existingInstId ?: student.instituteId)) return@withContext false
    val resolvedInstituteId = getCurrentInstituteId()
    val normalizedStudent = student.copy(
      batchId = student.batchId.ifBlank { "BATCH_${student.batch.replace(" ", "_")}" },
      parentId = student.parentId.ifBlank { "PAR_${student.id}" },
      parentEmail = student.parentEmail.ifBlank { "parent_${student.id.lowercase()}@mytuition.com" },
      instituteId = resolvedInstituteId
    )
    val stuEmail = normalizedStudent.email.ifBlank { "${normalizedStudent.id.lowercase()}@mytuition.com" }.lowercase()
    val parEmail = normalizedStudent.parentEmail.lowercase()
    val repo = resolveStudentBatchRepo()
    if (repo != null) {
      try {
        repo.insertStudent(normalizedStudent, syncToCloud = false)
        repo.insertUserAccounts(
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
              status = normalizedStudent.status,
              instituteId = resolvedInstituteId
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
              status = "Active",
              instituteId = resolvedInstituteId
            )
          )
        )
      } catch (e: Exception) {
        android.util.Log.e("AppRepository", "Failed to insert student into Room database", e)
        return@withContext false
      }
    } else {
      onLocalStudentUpserted(normalizedStudent)
    }
    FirestoreService.syncStudentToCloud(
      student = normalizedStudent,
      createdBy = _currentUserIdentifier.value.ifBlank { "ADMIN" },
      operationType = com.example.data.db.SyncOperationType.CREATE
    )
    if (NetworkConnectivityManager.isOnline.value) {
      NetworkConnectivityManager.markDataUpdatedOnline()
    }
    addNotificationInternal(
      NotificationItem(
        id = "NOT_${System.currentTimeMillis()}",
        title = "New Student Admitted",
        message = "${normalizedStudent.name} (${normalizedStudent.id}) admitted to batch ${normalizedStudent.batch}.",
        time = getCurrentTimeStr(),
        type = "Announcement",
        recipientRole = "All",
        targetStudentId = normalizedStudent.id,
        instituteId = resolvedInstituteId
      )
    )
    return@withContext true
  }

  suspend fun updateStudent(student: Student): Boolean = withContext(Dispatchers.IO) {
    if (!checkOnlineOrBlock("update student record")) return@withContext false
    val existing = findStudentLocal(student.id)
    val existingInstituteId = existing?.instituteId ?: findExistingStudentInstituteId(student.id)
    if (existingInstituteId != null) {
      if (!com.example.security.SecurityEngine.isValidInstituteId(student.instituteId) ||
        !existingInstituteId.equals(student.instituteId, ignoreCase = true)
      ) {
        com.example.security.SecurityEngine.logEvent(
          severity = com.example.security.SecuritySeverity.ALERT,
          eventType = "INSTITUTE_REASSIGNMENT_BLOCKED",
          actor = _currentUserIdentifier.value.ifBlank { "System" },
          details = "Blocked attempt to change instituteId of student '${student.id}' from '$existingInstituteId' to '${student.instituteId}'."
        )
        return@withContext false
      }
      if (!requireAdminSession("updateStudent", existingInstituteId)) return@withContext false
    } else {
      if (!requireAdminSession("updateStudent", student.instituteId)) return@withContext false
    }
    val resolvedBatchId = student.batchId.ifBlank { "BATCH_${student.batch.replace(" ", "_")}" }
    val normalized = student.copy(
      batchId = resolvedBatchId,
      instituteId = existingInstituteId ?: getCurrentInstituteId()
    )
    val repo = resolveStudentBatchRepo()
    if (repo != null) {
      try {
        repo.updateStudent(normalized, syncToCloud = false)
      } catch (e: Exception) {
        android.util.Log.e("AppRepository", "Failed to update student in Room database", e)
        return@withContext false
      }
    } else {
      onLocalStudentUpserted(normalized)
    }
    FirestoreService.syncStudentToCloud(
      student = normalized,
      createdBy = _currentUserIdentifier.value.ifBlank { "ADMIN" },
      operationType = com.example.data.db.SyncOperationType.UPDATE
    )
    if (NetworkConnectivityManager.isOnline.value) {
      NetworkConnectivityManager.markDataUpdatedOnline()
    }
    return@withContext true
  }

  suspend fun addStudents(newStudents: List<Student>): Boolean = withContext(Dispatchers.IO) {
    if (!checkOnlineOrBlock("import students")) return@withContext false
    if (newStudents.isEmpty()) return@withContext false
    if (!requireAdminSession("addStudents")) return@withContext false
    val activeInst = getCurrentInstituteId()
    val sanitized = newStudents.mapNotNull { stu ->
      val existingInst = findExistingStudentInstituteId(stu.id)
      if (existingInst != null && !existingInst.equals(activeInst, ignoreCase = true)) {
        com.example.security.SecurityEngine.logEvent(
          severity = com.example.security.SecuritySeverity.ALERT,
          eventType = "CROSS_TENANT_WRITE_BLOCKED",
          actor = _currentUserIdentifier.value,
          details = "Blocked addStudents item '${stu.id}' belonging to foreign institute '$existingInst'."
        )
        null
      } else if (stu.instituteId.isNotBlank() && !stu.instituteId.equals(activeInst, ignoreCase = true)) {
        com.example.security.SecurityEngine.logEvent(
          severity = com.example.security.SecuritySeverity.ALERT,
          eventType = "CROSS_TENANT_WRITE_BLOCKED",
          actor = _currentUserIdentifier.value,
          details = "Blocked addStudents item '${stu.id}' with foreign instituteId '${stu.instituteId}'."
        )
        null
      } else {
        val resolvedBatchId = stu.batchId.ifBlank { "BATCH_${stu.batch.replace(" ", "_")}" }
        stu.copy(batchId = resolvedBatchId, instituteId = activeInst)
      }
    }
    if (sanitized.isEmpty()) return@withContext false
    val repo = resolveStudentBatchRepo()
    if (repo != null) {
      try {
        repo.bulkInsertStudents(sanitized, syncToCloud = false)
      } catch (e: Exception) {
        android.util.Log.e("AppRepository", "Failed to bulk insert students into Room database", e)
        return@withContext false
      }
    } else {
      sanitized.forEach { onLocalStudentUpserted(it) }
    }
    sanitized.forEach { stu ->
      FirestoreService.syncStudentToCloud(
        student = stu,
        createdBy = _currentUserIdentifier.value.ifBlank { "ADMIN" },
        operationType = com.example.data.db.SyncOperationType.CREATE
      )
    }
    if (NetworkConnectivityManager.isOnline.value) {
      NetworkConnectivityManager.markDataUpdatedOnline()
    }
    addNotificationInternal(
      NotificationItem(
        id = "NOT_${System.currentTimeMillis()}",
        title = "Bulk Student Import Completed",
        message = "Successfully imported ${sanitized.size} student records via CSV.",
        time = getCurrentTimeStr(),
        type = "Announcement",
        recipientRole = "All",
        instituteId = activeInst
      )
    )
    return@withContext true
  }

  suspend fun bulkInsertStudents(newStudents: List<Student>): Boolean = addStudents(newStudents)

  suspend fun updateStudentStatus(studentId: String, newStatus: String): Boolean = withContext(Dispatchers.IO) {
    if (!checkOnlineOrBlock("update student status")) return@withContext false
    val existing = findStudentLocal(studentId) ?: return@withContext false
    if (!requireAdminSession("updateStudentStatus", existing.instituteId)) return@withContext false
    val updatedStudent = existing.copy(
      status = newStatus,
      instituteId = existing.instituteId.ifBlank { getCurrentInstituteId() }
    )
    val repo = resolveStudentBatchRepo()
    if (repo != null) {
      try {
        repo.updateStudent(updatedStudent, syncToCloud = false)
      } catch (e: Exception) {
        android.util.Log.e("AppRepository", "Failed to update student status in Room database", e)
        return@withContext false
      }
    } else {
      onLocalStudentUpserted(updatedStudent)
    }
    FirestoreService.syncStudentToCloud(
      student = updatedStudent,
      createdBy = _currentUserIdentifier.value.ifBlank { "ADMIN" },
      operationType = com.example.data.db.SyncOperationType.UPDATE
    )
    if (NetworkConnectivityManager.isOnline.value) {
      NetworkConnectivityManager.markDataUpdatedOnline()
    }
    return@withContext true
  }

  private suspend fun markStudentActivatedInternal(studentId: String, isActivated: Boolean): Boolean = withContext(Dispatchers.IO) {
    val existing = findStudentLocal(studentId) ?: return@withContext false
    val updatedStudent = existing.copy(
      isActivated = isActivated,
      status = if (isActivated) "Active" else existing.status,
      instituteId = existing.instituteId.ifBlank { getCurrentInstituteId() }
    )
    val repo = resolveStudentBatchRepo()
    if (repo != null) {
      try {
        repo.updateStudent(updatedStudent, syncToCloud = false)
      } catch (e: Exception) {
        android.util.Log.e("AppRepository", "Failed to update student activation in Room database", e)
        return@withContext false
      }
    } else {
      onLocalStudentUpserted(updatedStudent)
    }
    FirestoreService.syncStudentToCloud(
      student = updatedStudent,
      createdBy = _currentUserIdentifier.value.ifBlank { "ADMIN" },
      operationType = com.example.data.db.SyncOperationType.UPDATE
    )
    return@withContext true
  }

  suspend fun updateStudentActivationStatus(studentId: String, isActivated: Boolean): Boolean = withContext(Dispatchers.IO) {
    if (!checkOnlineOrBlock("change student activation status")) return@withContext false
    val existing = findStudentLocal(studentId) ?: return@withContext false
    if (!requireAdminSession("updateStudentActivationStatus", existing.instituteId)) return@withContext false
    val success = markStudentActivatedInternal(studentId, isActivated)
    if (!success) return@withContext false
    if (NetworkConnectivityManager.isOnline.value) {
      NetworkConnectivityManager.markDataUpdatedOnline()
    }
    return@withContext true
  }

  suspend fun updateStudentActivation(studentId: String, isActivated: Boolean): Boolean =
    updateStudentActivationStatus(studentId, isActivated)

  suspend fun deactivateStudent(studentId: String): Boolean {
    return deleteStudent(studentId)
  }

  suspend fun deactivateStudents(studentIds: List<String>): Boolean {
    return deleteStudents(studentIds)
  }

  suspend fun deleteStudent(studentId: String): Boolean = withContext(Dispatchers.IO) {
    if (!checkOnlineOrBlock("delete student record")) return@withContext false
    val cleanId = studentId.trim()
    if (cleanId.isBlank()) return@withContext false
    val student = findStudentLocal(cleanId)
    val targetInstituteId = student?.instituteId ?: findExistingStudentInstituteId(cleanId)
    if (!requireAdminSession("deleteStudent", targetInstituteId)) return@withContext false
    val repo = resolveStudentBatchRepo()
    if (repo != null) {
      try {
        repo.deleteStudent(cleanId, syncToCloud = false)
      } catch (e: Exception) {
        android.util.Log.e("AppRepository", "Failed to delete student from Room database", e)
        return@withContext false
      }
    } else {
      onLocalStudentsDeactivated(listOf(cleanId))
    }
    FirestoreService.deleteStudentProfile(cleanId, {}, {})
    if (NetworkConnectivityManager.isOnline.value) {
      NetworkConnectivityManager.markDataUpdatedOnline()
    }
    addNotificationInternal(
      NotificationItem(
        id = "NOT_${System.currentTimeMillis()}",
        title = "Student Record Removed",
        message = "Student ${student?.name ?: cleanId} was removed from the active directory.",
        time = getCurrentTimeStr(),
        type = "Announcement",
        recipientRole = "Admin",
        instituteId = getCurrentInstituteId()
      )
    )
    return@withContext true
  }

  suspend fun deleteStudents(studentIds: List<String>): Boolean = withContext(Dispatchers.IO) {
    if (!checkOnlineOrBlock("batch delete student records")) return@withContext false
    if (studentIds.isEmpty()) return@withContext false
    if (!requireAdminSession("deleteStudents")) return@withContext false
    val allowedIds = studentIds.map { it.trim() }.filter { id ->
      if (id.isBlank()) return@filter false
      val stuInst = findStudentLocal(id)?.instituteId ?: findExistingStudentInstituteId(id)
      stuInst == null || belongsToCurrentInstitute(stuInst)
    }
    if (allowedIds.isEmpty()) return@withContext false
    val repo = resolveStudentBatchRepo()
    if (repo != null) {
      try {
        repo.deleteStudents(allowedIds, syncToCloud = false)
      } catch (e: Exception) {
        android.util.Log.e("AppRepository", "Failed to delete students from Room database", e)
        return@withContext false
      }
    } else {
      onLocalStudentsDeactivated(allowedIds)
    }
    allowedIds.forEach { id ->
      FirestoreService.deleteStudentProfile(id, {}, {})
    }
    if (NetworkConnectivityManager.isOnline.value) {
      NetworkConnectivityManager.markDataUpdatedOnline()
    }
    addNotificationInternal(
      NotificationItem(
        id = "NOT_${System.currentTimeMillis()}",
        title = "Batch Students Deletion",
        message = "Successfully removed ${allowedIds.size} student records in batch.",
        time = getCurrentTimeStr(),
        type = "Announcement",
        recipientRole = "Admin",
        instituteId = getCurrentInstituteId()
      )
    )
    return@withContext true
  }

  // Institute Configuration & Academic Profile (Tenant-Scoped per InstituteId)
  private val instituteConfigsByTenant = java.util.concurrent.ConcurrentHashMap<String, InstituteConfig>().apply {
    put("FSI_MAIN", InstituteConfig(instituteId = "FSI_MAIN"))
  }
  private val _instituteConfig = MutableStateFlow(InstituteConfig(instituteId = "FSI_MAIN"))
  val instituteConfig = _instituteConfig.asStateFlow()

  fun getInstituteConfigForTenant(instituteId: String = getCurrentInstituteId()): InstituteConfig? {
    val cleanInst = instituteId.trim()
    if (!com.example.security.SecurityEngine.isValidInstituteId(cleanInst)) {
      return null
    }
    if (_isLoggedIn.value && !belongsToCurrentInstitute(cleanInst)) {
      com.example.security.SecurityEngine.logEvent(
        severity = com.example.security.SecuritySeverity.ALERT,
        eventType = "CROSS_TENANT_CONFIG_READ_BLOCKED",
        actor = _currentUserIdentifier.value,
        details = "Blocked cross-institute config read for '$cleanInst' from active institute '${getCurrentInstituteId()}'."
      )
      return null
    }
    return instituteConfigsByTenant[cleanInst]
      ?: _instituteConfig.value.takeIf { it.instituteId.equals(cleanInst, ignoreCase = true) }
  }

  fun updateInstituteConfig(newConfig: InstituteConfig): Boolean {
    if (!checkOnlineOrBlock("update institute configuration")) return false
    val rawInstId = newConfig.instituteId.trim()
    if (rawInstId.equals("primary_institute", ignoreCase = true) || rawInstId.isBlank()) {
      com.example.security.SecurityEngine.logEvent(
        severity = com.example.security.SecuritySeverity.ALERT,
        eventType = "PRIMARY_INSTITUTE_COLLISION_BLOCKED",
        actor = _currentUserIdentifier.value.ifBlank { "System" },
        details = "Rejected institute configuration update using invalid or global key '$rawInstId'."
      )
      return false
    }
    if (!requireAdminSession("updateInstituteConfig", rawInstId)) {
      return false
    }

    val scopedConfig = newConfig.copy(instituteId = rawInstId)
    instituteConfigsByTenant[rawInstId] = scopedConfig
    if (belongsToCurrentInstitute(rawInstId)) {
      _instituteConfig.value = scopedConfig
    }
    preferences?.edit()?.apply {
      putString("institute_name_$rawInstId", scopedConfig.instituteName)
      putString("institute_tagline_$rawInstId", scopedConfig.tagline)
      putString("founder_name_$rawInstId", scopedConfig.founderName)
      putString("institute_address_$rawInstId", scopedConfig.address)
      putString("institute_phone_$rawInstId", scopedConfig.contactPhone)
      putString("institute_email_$rawInstId", scopedConfig.email)
      putString("academic_year_$rawInstId", scopedConfig.academicYear)
      putInt("existing_staff_count_$rawInstId", scopedConfig.existingStaffCount)
      putString("selected_grades_$rawInstId", scopedConfig.selectedGrades.joinToString(","))
      putBoolean("is_setup_completed_$rawInstId", scopedConfig.isSetupCompleted)
      apply()
    }
    // Sync to Cloud Firestore under the tenant-isolated document ID
    FirestoreService.syncInstituteConfig(scopedConfig)
    NetworkConnectivityManager.markDataUpdatedOnline()
    addNotificationInternal(
      NotificationItem(
        id = "NOT_${System.currentTimeMillis()}",
        title = "🏫 Institute Profile Updated",
        message = "${scopedConfig.instituteName} academic configuration saved successfully.",
        time = getCurrentTimeStr(),
        type = "Announcement",
        recipientRole = "Admin",
        instituteId = rawInstId
      )
    )
    return true
  }

  fun reconcileFacultyName(rawTeacherName: String?, batchName: String? = null, subject: String? = null): String {
    val clean = (rawTeacherName ?: "").trim()
    val instTeachers = _allTeachers.value.filter {
      !_isLoggedIn.value || belongsToCurrentInstitute(it.instituteId)
    }.ifEmpty { SeedData.defaultTeachers }
    val instBatches = _allBatches.value.filter {
      !_isLoggedIn.value || belongsToCurrentInstitute(it.instituteId)
    }.ifEmpty { SeedData.defaultBatches }

    // Preserve any explicitly provided teacher name or ID that isn't a generic placeholder
    if (clean.isNotBlank() &&
      !clean.equals("Faculty", ignoreCase = true) &&
      !clean.equals("Subject Faculty", ignoreCase = true) &&
      !clean.equals("TBA", ignoreCase = true)
    ) {
      val exactTeacher = instTeachers.find {
        it.id.equals(clean, ignoreCase = true) ||
          it.name.equals(clean, ignoreCase = true) ||
          it.email.equals(clean, ignoreCase = true)
      } ?: SeedData.defaultTeachers.find {
        it.id.equals(clean, ignoreCase = true) ||
          it.name.equals(clean, ignoreCase = true) ||
          it.email.equals(clean, ignoreCase = true)
      }
      return exactTeacher?.name ?: clean
    }

    // If a batch has an assigned teacher in the repository, use that teacher's real name
    if (!batchName.isNullOrBlank()) {
      val batchObj = instBatches.find {
        it.id.equals(batchName, ignoreCase = true) || it.name.equals(batchName, ignoreCase = true)
      } ?: SeedData.defaultBatches.find {
        it.id.equals(batchName, ignoreCase = true) || it.name.equals(batchName, ignoreCase = true)
      }
      if (batchObj != null) {
        if (batchObj.teacherId.isNotBlank()) {
          instTeachers.find { it.id.equals(batchObj.teacherId, ignoreCase = true) }?.let { return it.name }
            ?: SeedData.defaultTeachers.find { it.id.equals(batchObj.teacherId, ignoreCase = true) }?.let { return it.name }
        }
        if (batchObj.teacherName.isNotBlank() && !batchObj.teacherName.equals("Faculty", ignoreCase = true)) {
          return batchObj.teacherName
        }
      }
      val assignedTeacher = instTeachers.find { t ->
        t.assignedBatchIds.any { bId -> bId.equals(batchName, ignoreCase = true) } ||
          t.assignedBatches.any { b -> b.equals(batchName, ignoreCase = true) }
      } ?: SeedData.defaultTeachers.find { t ->
        t.assignedBatchIds.any { bId -> bId.equals(batchName, ignoreCase = true) } ||
          t.assignedBatches.any { b -> b.equals(batchName, ignoreCase = true) }
      }
      if (assignedTeacher != null) return assignedTeacher.name
    }

    // If a subject is provided, match against registered teachers for that subject
    if (!subject.isNullOrBlank()) {
      val subjectLower = subject.lowercase()
      val matchedBySubject = instTeachers.find { t ->
        t.subject.lowercase().contains(subjectLower) || subjectLower.contains(t.subject.lowercase())
      } ?: SeedData.defaultTeachers.find { t ->
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
    val allTeachersPool = (_allTeachers.value + SeedData.defaultTeachers).distinctBy { it.id }
    val instTeachers = allTeachersPool.filter {
      !_isLoggedIn.value || belongsToCurrentInstitute(it.instituteId)
    }.ifEmpty { SeedData.defaultTeachers }
    val t1 = instTeachers.find { it.id.equals(c1, ignoreCase = true) || it.name.equals(c1, ignoreCase = true) || it.email.equals(c1, ignoreCase = true) }
    val t2 = instTeachers.find { it.id.equals(c2, ignoreCase = true) || it.name.equals(c2, ignoreCase = true) || it.email.equals(c2, ignoreCase = true) }
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
    if (!isAuthenticatedSession() && teacherOverride == null) return false
    if (isAdminSession() && teacherOverride == null) return true

    val teacher = teacherOverride ?: getActiveTeacherForCurrentSession() ?: return false
    val instBatches = _allBatches.value.filter {
      it.instituteId.equals(teacher.instituteId, ignoreCase = true)
    }
    val targetBatch = instBatches.find {
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
    if (!isAuthenticatedSession()) return emptyList()
    val instBatches = _allBatches.value.filter { belongsToCurrentInstitute(it.instituteId) }
    if (isAdminSession()) return instBatches
    if (!isTeacherSession()) return emptyList()
    val teacher = getActiveTeacherForCurrentSession() ?: return emptyList()
    return instBatches.filter { b ->
      isTeacherAssignedToBatch(b.id, teacher) || isTeacherAssignedToBatch(b.name, teacher)
    }
  }

  fun getAuthorizedBatchesForCurrentTeacher(): List<Batch> = getAssignedBatchesForCurrentTeacher()

  fun getStudentsForCurrentTeacher(): List<Student> {
    if (!isAuthenticatedSession()) return emptyList()
    val instStudents = _allStudents.value.filter { belongsToCurrentInstitute(it.instituteId) }
    if (isAdminSession()) return instStudents
    if (!isTeacherSession()) return emptyList()
    val assignedBatches = getAssignedBatchesForCurrentTeacher()
    if (assignedBatches.isEmpty()) return emptyList()
    val batchIds = assignedBatches.map { it.id.lowercase() }.toSet()
    val batchNames = assignedBatches.map { it.name.lowercase() }.toSet()
    return instStudents.filter { stu ->
      (stu.batchId.isNotBlank() && batchIds.contains(stu.batchId.lowercase())) ||
        batchNames.contains(stu.batch.lowercase())
    }
  }

  // Teachers
  private val _allTeachers = MutableStateFlow<List<Teacher>>(SeedData.defaultTeachers)
  private val _scopedTeachers = MutableStateFlow<List<Teacher>>(emptyList())
  val teachers = _scopedTeachers.asStateFlow()

  fun addTeacher(t: Teacher): Boolean {
    if (!checkOnlineOrBlock("add new faculty")) return false
    if (!requireAdminSession("addTeacher", t.instituteId)) return false
    val normalized = t.copy(instituteId = getCurrentInstituteId())
    _allTeachers.update { it + normalized }
    refreshScopedStateFlows()
    repoScope.launch {
      studentBatchRepo?.insertTeacher(normalized)
    }
    NetworkConnectivityManager.markDataUpdatedOnline()
    addNotificationInternal(
      NotificationItem(
        id = "NOT_${System.currentTimeMillis()}",
        title = "Staff Member Onboarded",
        message = "${normalized.name} (${normalized.subject}) registered in faculty directory.",
        time = getCurrentTimeStr(),
        type = "Announcement",
        recipientRole = "Admin",
        instituteId = normalized.instituteId
      )
    )
    return true
  }

  fun updateTeacher(t: Teacher): Boolean {
    if (!checkOnlineOrBlock("update faculty record")) return false
    val existing = _allTeachers.value.find { it.id == t.id }
    if (existing != null && !existing.instituteId.equals(t.instituteId, ignoreCase = true)) {
      com.example.security.SecurityEngine.logEvent(
        severity = com.example.security.SecuritySeverity.ALERT,
        eventType = "INSTITUTE_REASSIGNMENT_BLOCKED",
        actor = _currentUserIdentifier.value.ifBlank { "System" },
        details = "Blocked attempt to change instituteId of teacher '${t.id}'."
      )
      return false
    }
    if (!requireAdminSession("updateTeacher", existing?.instituteId ?: t.instituteId)) return false
    val normalized = t.copy(instituteId = existing?.instituteId ?: getCurrentInstituteId())
    _allTeachers.update { list ->
      list.map { if (it.id == normalized.id) normalized else it }
    }
    refreshScopedStateFlows()
    repoScope.launch {
      studentBatchRepo?.updateTeacher(normalized)
    }
    NetworkConnectivityManager.markDataUpdatedOnline()
    return true
  }

  fun deleteTeacher(teacherId: String): Boolean {
    if (!checkOnlineOrBlock("delete faculty record")) return false
    val existing = _allTeachers.value.find { it.id == teacherId }
    if (!requireAdminSession("deleteTeacher", existing?.instituteId)) return false
    _allTeachers.update { list -> list.filterNot { it.id == teacherId } }
    refreshScopedStateFlows()
    repoScope.launch {
      studentBatchRepo?.deleteTeacher(teacherId)
    }
    NetworkConnectivityManager.markDataUpdatedOnline()
    return true
  }

  private fun markTeacherActivatedInternal(teacherId: String, isActivated: Boolean) {
    _allTeachers.update { list ->
      list.map { if (it.id == teacherId) it.copy(isActivated = isActivated, status = if (isActivated) "Active" else "Pending") else it }
    }
    refreshScopedStateFlows()
    val updated = _allTeachers.value.find { it.id == teacherId }
    if (updated != null) {
      repoScope.launch {
        studentBatchRepo?.updateTeacher(updated)
      }
    }
  }

  fun updateTeacherStatus(teacherId: String, isActivated: Boolean): Boolean {
    if (!checkOnlineOrBlock("update teacher status")) return false
    val existing = _allTeachers.value.find { it.id == teacherId } ?: return false
    if (!requireAdminSession("updateTeacherStatus", existing.instituteId)) return false
    markTeacherActivatedInternal(teacherId, isActivated)
    NetworkConnectivityManager.markDataUpdatedOnline()
    return true
  }

  fun resolveCanonicalEmailForIdentifier(identifier: String): String? {
    val cleanId = identifier.trim().lowercase()
    if (cleanId.isBlank()) return null
    if (cleanId == "joshiakash1712") return "joshiakash1712@gmail.com"
    if (cleanId.contains("@")) return cleanId
    val digitsOnly = cleanId.filter { it.isDigit() }
    val userAcc = _allUserAccounts.value.find { acc ->
      acc.userId.equals(cleanId, ignoreCase = true) ||
        acc.linkedEntityId.equals(cleanId, ignoreCase = true) ||
        (digitsOnly.length >= 10 && acc.mobile.filter { it.isDigit() }.endsWith(digitsOnly.takeLast(10)))
    }
    if (userAcc != null && userAcc.email.contains("@")) return userAcc.email.lowercase()

    val teacher = _allTeachers.value.find { t ->
      t.id.equals(cleanId, ignoreCase = true) ||
        (digitsOnly.length >= 10 && t.contact.filter { it.isDigit() }.endsWith(digitsOnly.takeLast(10)))
    }
    if (teacher != null && teacher.email.contains("@")) return teacher.email.lowercase()

    val student = _allStudents.value.find { s ->
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
    val rawClean = identifier.trim().lowercase()
    val cleanId = if (rawClean == "joshiakash1712") "joshiakash1712@gmail.com" else rawClean
    if (cleanId.isBlank()) {
      return AccountStatusInfo(exists = false, role = null, displayName = "", isActivated = true)
    }

    val digitsOnly = cleanId.filter { it.isDigit() }

    // 0. Check normalized UserAccount table first
    val userAcc = _allUserAccounts.value.find { acc ->
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
      val resolvedInstId = userAcc?.instituteId?.takeIf { com.example.security.SecurityEngine.isValidInstituteId(it) }
        ?: config.instituteId.takeIf { com.example.security.SecurityEngine.isValidInstituteId(it) }
        ?: "FSI_MAIN"
      return AccountStatusInfo(
        exists = true,
        role = UserRole.ADMIN,
        displayName = userAcc?.name?.ifBlank { null } ?: config.adminFullName.ifBlank { "Institute Administrator" },
        isActivated = true,
        instituteId = resolvedInstId
      )
    }

    // 2. Check Faculty / Teachers (exact email, ID, or phone match)
    val matchedTeacher = _allTeachers.value.find { t ->
      t.email.lowercase() == cleanId ||
        t.id.lowercase() == cleanId ||
        (digitsOnly.length >= 10 && t.contact.filter { it.isDigit() }.endsWith(digitsOnly.takeLast(10)))
    }
    if (matchedTeacher != null) {
      val hasCustomPassword = com.example.security.SecurityEngine.hasRegisteredAccount(matchedTeacher.email) ||
        com.example.security.SecurityEngine.hasRegisteredAccount(matchedTeacher.id)
      val isActivated = matchedTeacher.isActivated && hasCustomPassword
      val resolvedInstId = userAcc?.instituteId?.takeIf { com.example.security.SecurityEngine.isValidInstituteId(it) }
        ?: matchedTeacher.instituteId.takeIf { com.example.security.SecurityEngine.isValidInstituteId(it) }
        ?: "FSI_MAIN"
      return AccountStatusInfo(
        exists = true,
        role = UserRole.TEACHER,
        displayName = matchedTeacher.name,
        isActivated = isActivated,
        expectedActivationCode = matchedTeacher.activationCode,
        teacherId = matchedTeacher.id,
        instituteId = resolvedInstId
      )
    }

    // 3. Check Students & Parents (exact email, ID, parentEmail, or phone match)
    val matchedStudent = _allStudents.value.find { s ->
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
      val resolvedInstId = userAcc?.instituteId?.takeIf { com.example.security.SecurityEngine.isValidInstituteId(it) }
        ?: matchedStudent.instituteId.takeIf { com.example.security.SecurityEngine.isValidInstituteId(it) }
        ?: "FSI_MAIN"
      return AccountStatusInfo(
        exists = true,
        role = role,
        displayName = if (isParent) "${matchedStudent.parentName} (Parent of ${matchedStudent.name})" else matchedStudent.name,
        isActivated = isActivated,
        expectedActivationCode = matchedStudent.activationCode,
        studentId = matchedStudent.id,
        instituteId = resolvedInstId
      )
    }

    if (userAcc != null) {
      return AccountStatusInfo(
        exists = true,
        role = userAcc.role,
        displayName = userAcc.name,
        isActivated = userAcc.status.equals("Active", ignoreCase = true),
        teacherId = if (userAcc.role == UserRole.TEACHER) userAcc.linkedEntityId else null,
        studentId = if (userAcc.role == UserRole.STUDENT || userAcc.role == UserRole.PARENT) userAcc.linkedEntityId else null,
        instituteId = userAcc.instituteId.takeIf { com.example.security.SecurityEngine.isValidInstituteId(it) } ?: "FSI_MAIN"
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
      markTeacherActivatedInternal(status.teacherId, isActivated = true)
    }
    if (status.studentId != null) {
      markStudentActivatedInternal(status.studentId, isActivated = true)
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
            status = "Active",
            instituteId = status.instituteId.ifBlank { "FSI_MAIN" }
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
  private val _allBatches = MutableStateFlow<List<Batch>>(
    SeedData.defaultBatches.map { b ->
      val count = SeedData.get15DemoStudents().count { it.batch.equals(b.name, ignoreCase = true) }
      b.copy(studentCount = count)
    }
  )
  private val _scopedBatches = MutableStateFlow<List<Batch>>(emptyList())
  val batches = _scopedBatches.asStateFlow()

  fun syncBatchStudentCounts(studentsList: List<Student> = _allStudents.value) {
    _allBatches.update { currentBatches ->
      currentBatches.map { b ->
        val count = studentsList.count {
          it.instituteId.equals(b.instituteId, ignoreCase = true) &&
            it.batch.equals(b.name, ignoreCase = true)
        }
        b.copy(studentCount = count)
      }
    }
  }

  fun getLiveBatchStudentCount(batchName: String): Int {
    if (!isAuthenticatedSession()) return 0
    return getStudentsForCurrentSession().count { it.batch.equals(batchName, ignoreCase = true) }
  }

  fun addBatch(b: Batch): Boolean {
    if (!checkOnlineOrBlock("create new batch")) return false
    if (!requireAdminSession("addBatch", b.instituteId)) return false
    val activeInst = getCurrentInstituteId()
    val count = _allStudents.value.count {
      it.instituteId.equals(activeInst, ignoreCase = true) && it.batch.equals(b.name, ignoreCase = true)
    }
    val newBatch = b.copy(studentCount = count, instituteId = activeInst)
    _allBatches.update { it + newBatch }
    refreshScopedStateFlows()
    repoScope.launch {
      studentBatchRepo?.insertBatch(newBatch)
    }
    NetworkConnectivityManager.markDataUpdatedOnline()
    return true
  }

  suspend fun enrollStudentInBatch(student: Student, batch: Batch): Boolean = withContext(Dispatchers.IO) {
    if (!checkOnlineOrBlock("enroll student in batch")) return@withContext false
    val existing = findStudentLocal(student.id)
    val existingInstituteId = existing?.instituteId ?: findExistingStudentInstituteId(student.id)
    if (existingInstituteId != null &&
      (!com.example.security.SecurityEngine.isValidInstituteId(student.instituteId) ||
        !existingInstituteId.equals(student.instituteId, ignoreCase = true))
    ) {
      com.example.security.SecurityEngine.logEvent(
        severity = com.example.security.SecuritySeverity.ALERT,
        eventType = "INSTITUTE_REASSIGNMENT_BLOCKED",
        actor = _currentUserIdentifier.value.ifBlank { "System" },
        details = "Blocked attempt in enrollStudentInBatch to change instituteId of student '${student.id}' from '$existingInstituteId' to '${student.instituteId}'."
      )
      return@withContext false
    }
    val targetStudentInstituteId = existingInstituteId ?: student.instituteId
    if (!requireAdminSession("enrollStudentInBatch", targetStudentInstituteId)) return@withContext false
    if (!belongsToCurrentInstitute(batch.instituteId)) {
      com.example.security.SecurityEngine.logEvent(
        severity = com.example.security.SecuritySeverity.ALERT,
        eventType = "CROSS_TENANT_WRITE_BLOCKED",
        actor = _currentUserIdentifier.value.ifBlank { "System" },
        details = "Blocked enrollStudentInBatch for student '${student.id}' into foreign batch '${batch.id}' (${batch.instituteId})."
      )
      return@withContext false
    }
    val resolvedInstituteId = existingInstituteId ?: getCurrentInstituteId()
    val resolvedBatchId = batch.id.ifBlank { "BATCH_${batch.name.replace(" ", "_")}" }
    val normalizedBatch = batch.copy(id = resolvedBatchId, instituteId = resolvedInstituteId)
    val updatedStudent = (existing ?: student).copy(
      batch = normalizedBatch.name,
      batchId = normalizedBatch.id,
      instituteId = resolvedInstituteId
    )
    val repo = resolveStudentBatchRepo()
    if (repo != null) {
      try {
        repo.enrollStudentInBatch(updatedStudent, normalizedBatch, syncToCloud = false)
      } catch (e: Exception) {
        android.util.Log.e("AppRepository", "Failed to enroll student in batch in Room database", e)
        return@withContext false
      }
    } else {
      onLocalStudentUpserted(updatedStudent)
    }
    FirestoreService.syncStudentToCloud(
      student = updatedStudent,
      createdBy = _currentUserIdentifier.value.ifBlank { "ADMIN" },
      operationType = com.example.data.db.SyncOperationType.UPDATE
    )
    if (NetworkConnectivityManager.isOnline.value) {
      NetworkConnectivityManager.markDataUpdatedOnline()
    }
    return@withContext true
  }

  // Subjects
  private val _allSubjects = MutableStateFlow<List<Subject>>(SeedData.defaultSubjects)
  private val _scopedSubjects = MutableStateFlow<List<Subject>>(emptyList())
  val subjects = _scopedSubjects.asStateFlow()

  fun addSubject(s: Subject): Boolean {
    if (!checkOnlineOrBlock("add subject")) return false
    if (!requireAdminSession("addSubject", s.instituteId)) return false
    val normalized = s.copy(instituteId = getCurrentInstituteId())
    _allSubjects.update { it + normalized }
    refreshScopedStateFlows()
    repoScope.launch {
      studentBatchRepo?.insertSubject(normalized)
    }
    NetworkConnectivityManager.markDataUpdatedOnline()
    return true
  }

  fun markChapterCompleted(subjectId: String, chapter: String): Boolean {
    if (!checkOnlineOrBlock("update chapter completion status")) return false
    val targetSub = _allSubjects.value.find { it.id == subjectId } ?: return false
    if (!requireTeacherOrAdminForBatch(
        actionName = "markChapterCompleted",
        batchNameOrId = targetSub.batchId.ifBlank { targetSub.batchName },
        targetInstituteId = targetSub.instituteId
      )
    ) {
      return false
    }

    var updatedSubject: Subject? = null
    _allSubjects.update { list ->
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
    refreshScopedStateFlows()
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
    val targetSub = _allSubjects.value.find { it.id == subjectId } ?: return false
    if (!requireTeacherOrAdminForBatch(
        actionName = "updateSyllabusProgress",
        batchNameOrId = targetSub.batchId.ifBlank { targetSub.batchName },
        targetInstituteId = targetSub.instituteId
      )
    ) {
      return false
    }

    val clamped = newCompletionPercent.coerceIn(0, 100)
    var updatedSubject: Subject? = null
    _allSubjects.update { list ->
      list.map { sub ->
        if (sub.id == subjectId) {
          val copy = sub.copy(completionPercent = clamped)
          updatedSubject = copy
          copy
        } else sub
      }
    }
    refreshScopedStateFlows()
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

    val studentsList = _allStudents.value.ifEmpty { SeedData.get15DemoStudents() }
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

  private val _allAttendance = MutableStateFlow<List<AttendanceRecord>>(emptyList())
  private val _scopedAttendance = MutableStateFlow<List<AttendanceRecord>>(emptyList())
  val attendance = _scopedAttendance.asStateFlow()
  val attendanceRecords = _scopedAttendance.asStateFlow()

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
    if (!isAuthenticatedSession()) return emptyList()
    return getAttendanceForCurrentSession().filter { it.date == dateStr }
  }

  fun getAttendanceByBatch(batchNameOrId: String, dateStr: String? = null): List<AttendanceRecord> {
    if (!isAuthenticatedSession()) return emptyList()
    val cleanBatch = batchNameOrId.trim()
    if (cleanBatch.isBlank()) return emptyList()
    if (isTeacherSession() && !isTeacherAssignedToBatch(cleanBatch)) {
      return emptyList()
    }
    val batchObj = _allBatches.value.find {
      belongsToCurrentInstitute(it.instituteId) &&
        (it.id.equals(cleanBatch, ignoreCase = true) || it.name.equals(cleanBatch, ignoreCase = true))
    }
    return getAttendanceForCurrentSession().filter { rec ->
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
    val authorizedStu = resolveAuthorizedStudentTarget(requestedStudentId = cleanStudentId) ?: return emptyList()
    return _allAttendance.value.filter {
      belongsToCurrentInstitute(it.instituteId) &&
        it.studentId.equals(authorizedStu.id, ignoreCase = true)
    }
  }

  fun calculateStudentAttendancePercent(studentId: String, records: List<AttendanceRecord> = _scopedAttendance.value): Int {
    val stuRecords = records.filter { it.studentId.equals(studentId, ignoreCase = true) }
    if (stuRecords.isEmpty()) {
      return getStudentsForCurrentSession().find { it.id.equals(studentId, ignoreCase = true) }?.attendancePercent ?: 0
    }
    val presentOrLate = stuRecords.count {
      it.status.equals("Present", ignoreCase = true) || it.status.equals("Late", ignoreCase = true)
    }
    return ((presentOrLate * 100f) / stuRecords.size).toInt().coerceIn(0, 100)
  }

  fun calculateStudentAttendancePercentage(studentId: String, records: List<AttendanceRecord> = _scopedAttendance.value): Int {
    return calculateStudentAttendancePercent(studentId, records)
  }

  fun calculateConsecutiveAbsences(studentId: String, records: List<AttendanceRecord> = _scopedAttendance.value): Int {
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
    if (!isAuthenticatedSession()) return emptyList()
    return getAttendanceForCurrentSession().filter { record ->
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
    _allStudents.update { list ->
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
    val stuRecords = _allAttendance.value.filter { it.studentId.equals(studentId, ignoreCase = true) }
    if (stuRecords.isEmpty()) return
    val presentOrLate = stuRecords.count {
      it.status.equals("Present", ignoreCase = true) || it.status.equals("Late", ignoreCase = true)
    }
    val pct = ((presentOrLate * 100f) / stuRecords.size).toInt().coerceIn(0, 100)
    var updatedStu: Student? = null
    _allStudents.update { list ->
      list.map { s ->
        if (s.id.equals(studentId, ignoreCase = true)) {
          val copy = s.copy(attendancePercent = pct)
          updatedStu = copy
          copy
        } else s
      }
    }
    refreshScopedStateFlows()
    updatedStu?.let { stu ->
      repoScope.launch {
        studentBatchRepo?.updateStudent(stu)
      }
    }
  }

  fun updateAttendanceRecord(recordId: String, newStatus: String, reason: String? = null, recordedBy: String? = null): Boolean {
    if (!checkOnlineOrBlock("modify attendance record")) return false
    val targetRecord = _allAttendance.value.find { it.id == recordId } ?: return false
    if (!requireTeacherOrAdminForBatch(
        actionName = "updateAttendanceRecord",
        batchNameOrId = targetRecord.batchId.ifBlank { targetRecord.batchName },
        targetInstituteId = targetRecord.instituteId
      )
    ) {
      return false
    }

    var success = false
    var updatedItem: AttendanceRecord? = null
    val finalRecorder = _currentUserIdentifier.value.ifBlank { recordedBy ?: "system_fsi" }
    val now = System.currentTimeMillis()

    _allAttendance.update { current ->
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
      refreshScopedStateFlows()
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
    val targetStudent = _allStudents.value.find { it.id.equals(studentId, ignoreCase = true) }
    if (!requireTeacherOrAdminForBatch(
        actionName = "saveSingleAttendance",
        batchNameOrId = batchName,
        targetInstituteId = targetStudent?.instituteId
      )
    ) {
      return false
    }

    val finalRecorder = _currentUserIdentifier.value.ifBlank { recordedBy ?: "system_fsi" }
    var allowed = true
    val now = System.currentTimeMillis()
    val parsed = com.example.data.db.AttendanceRecordEntity.parseDateAndTimeString(date, now)
    var savedRecord: AttendanceRecord? = null
    val activeInst = getCurrentInstituteId()
    val resolvedBatchId = _allBatches.value.find {
      belongsToCurrentInstitute(it.instituteId) &&
        (it.name.equals(batchName, ignoreCase = true) || it.id.equals(batchName, ignoreCase = true))
    }?.id ?: SeedData.batchIdForName(batchName)

    _allAttendance.update { current ->
      val existing = current.find {
        it.studentId == studentId &&
          it.date == date &&
          (it.batchName.equals(batchName, ignoreCase = true) || it.batchId.equals(resolvedBatchId, ignoreCase = true))
      }
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
          studentName = targetStudent?.name ?: studentName,
          batchName = batchName,
          status = status,
          timestamp = now,
          lastModifiedTimestamp = now,
          reason = reason,
          recordedBy = finalRecorder,
          batchId = resolvedBatchId,
          instituteId = activeInst
        )
        savedRecord = newRecord
        current + newRecord
      }
    }

    if (allowed && savedRecord != null) {
      recalculateStudentAttendancePercentage(studentId)
      refreshScopedStateFlows()
      repoScope.launch {
        studentBatchRepo?.insertAttendance(savedRecord!!)
      }
      NetworkConnectivityManager.markDataUpdatedOnline()
    }

    if (allowed && status == "Absent") {
      val stu = targetStudent
      if (stu != null) {
        val reasonSfx = if (!reason.isNullOrBlank()) " due to $reason" else ""
        addNotificationInternal(
          NotificationItem(
            id = "NOT_ABS_${System.currentTimeMillis()}",
            title = "⚠️ Automated Absence Alert",
            message = "Dear Parent (${stu.parentName}), ${stu.name} was marked ABSENT today ($date) in ${batchName}${reasonSfx}. Please contact office.",
            time = getCurrentTimeStr(),
            type = "Absence",
            recipientRole = "Parent",
            targetStudentId = stu.id,
            targetBatchName = batchName,
            targetBatchId = resolvedBatchId,
            instituteId = activeInst
          )
        )
      }
    }

    return allowed
  }

  fun deleteAttendanceRecord(recordId: String): Boolean {
    if (!checkOnlineOrBlock("delete attendance record")) return false
    val targetRecord = _allAttendance.value.find { it.id == recordId } ?: return false
    if (!requireTeacherOrAdminForBatch(
        actionName = "deleteAttendanceRecord",
        batchNameOrId = targetRecord.batchId.ifBlank { targetRecord.batchName },
        targetInstituteId = targetRecord.instituteId
      )
    ) {
      return false
    }
    val targetStudentId = targetRecord.studentId
    _allAttendance.update { list -> list.filter { it.id != recordId } }
    recalculateStudentAttendancePercentage(targetStudentId)
    refreshScopedStateFlows()
    repoScope.launch {
      studentBatchRepo?.deleteAttendance(recordId)
    }
    NetworkConnectivityManager.markDataUpdatedOnline()
    return true
  }

  fun submitBatchAttendance(batchName: String, statusMap: Map<String, String>, recordedBy: String? = null): Boolean {
    if (!checkOnlineOrBlock("record batch attendance")) return false
    if (!requireTeacherOrAdminForBatch("submitBatchAttendance", batchName)) return false

    val date = getCurrentDateStr()
    val allowedStudents = getStudentsForCurrentSession().associateBy { it.id }
    statusMap.forEach { (stuId, st) ->
      val stu = allowedStudents[stuId] ?: return@forEach
      saveSingleAttendance(stuId, stu.name, batchName, date, st, recordedBy = recordedBy)
    }
    return true
  }

  // Tests & CBT Assessments
  private val _allTests = MutableStateFlow<List<TestRecord>>(SeedData.getDefaultTestRecords())
  private val _scopedTests = MutableStateFlow<List<TestRecord>>(emptyList())
  val tests = _scopedTests.asStateFlow()

  private val _allStudentTestAttempts = MutableStateFlow<List<StudentTestAttempt>>(emptyList())
  private val _scopedStudentTestAttempts = MutableStateFlow<List<StudentTestAttempt>>(emptyList())
  val studentTestAttempts = _scopedStudentTestAttempts.asStateFlow()

  fun getAttemptsForCurrentStudent(student: Student? = null): List<StudentTestAttempt> {
    val activeStu = resolveAuthorizedStudentTarget(requestedStudent = student) ?: return emptyList()
    return _allStudentTestAttempts.value.filter {
      belongsToCurrentInstitute(it.instituteId) &&
        it.studentId.equals(activeStu.id, ignoreCase = true)
    }
  }

  fun submitStudentTestAttempt(
    testId: String,
    testName: String = "CBT Online Assessment",
    subject: String = "General",
    answers: Map<String, Int>,
    durationSeconds: Int,
    studentIdOverride: String? = null
  ): StudentTestAttempt? {
    val existingAttempt = submitCbtTestAttempt(
      testId = testId,
      answers = answers,
      timeSpentSeconds = durationSeconds,
      studentIdOverride = studentIdOverride
    )
    if (existingAttempt != null) return existingAttempt

    if (!isAuthenticatedSession() || (!isStudentSession() && !isAdminSession())) return null
    val stu = resolveAuthorizedStudentTarget(requestedStudentId = studentIdOverride) ?: return null
    val studentId = stu.id
    val studentName = stu.name
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
      instituteId = stu.instituteId.ifBlank { getCurrentInstituteId() },
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
    _allStudentTestAttempts.update { list ->
      listOf(fallbackAttempt) + list.filterNot { it.studentId == studentId && it.testId == testId }
    }
    refreshScopedStateFlows()
    repoScope.launch {
      studentBatchRepo?.insertStudentAttempt(fallbackAttempt)
    }
    return fallbackAttempt
  }

  fun createTest(t: TestRecord): Boolean {
    if (!checkOnlineOrBlock("schedule new test")) return false
    if (!requireTeacherOrAdminForBatch("createTest", t.batchId.ifBlank { t.batch }, t.instituteId)) {
      return false
    }

    val activeInst = getCurrentInstituteId()
    val resolvedBatchId = t.batchId.ifBlank { SeedData.batchIdForName(t.batch) }
    val instQuestions = _allQuestions.value.filter { belongsToCurrentInstitute(it.instituteId) }
    val resolvedQuestionIds = t.questionIds.ifEmpty {
      instQuestions.filter { q ->
        q.subject.equals(t.subject, ignoreCase = true) || t.subject.contains(q.subject, ignoreCase = true)
      }.map { it.id }.ifEmpty { instQuestions.map { it.id } }
    }
    val normalizedTest = t.copy(
      batchId = resolvedBatchId,
      questionIds = resolvedQuestionIds,
      instituteId = activeInst
    )
    _allTests.update { listOf(normalizedTest) + it }
    refreshScopedStateFlows()
    repoScope.launch {
      studentBatchRepo?.insertTest(normalizedTest)
    }
    NetworkConnectivityManager.markDataUpdatedOnline()
    addNotificationInternal(
      NotificationItem(
        id = "NOT_TST_${System.currentTimeMillis()}",
        title = "📅 Upcoming Test Announced",
        message = "${normalizedTest.testName} (${normalizedTest.subject}) scheduled for ${normalizedTest.date} for batch ${normalizedTest.batch}.",
        time = getCurrentTimeStr(),
        type = "Test",
        recipientRole = "Student",
        targetBatchName = normalizedTest.batch,
        targetBatchId = resolvedBatchId,
        instituteId = activeInst
      )
    )
    return true
  }

  fun getQuestionsForTest(testId: String): List<QuestionItem> {
    if (!isAuthenticatedSession()) return emptyList()
    val allQ = _allQuestions.value.filter { belongsToCurrentInstitute(it.instituteId) }
      .ifEmpty { SeedData.getDefaultQuestions() }
    val test = _allTests.value.find { belongsToCurrentInstitute(it.instituteId) && it.id == testId }
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
    if (!isAuthenticatedSession() || (!isStudentSession() && !isAdminSession())) return null
    val student = resolveAuthorizedStudentTarget(requestedStudentId = studentIdOverride) ?: return null

    val instTests = _allTests.value.filter { belongsToCurrentInstitute(it.instituteId) }
    val test = instTests.find { it.id == testId } ?: instTests.firstOrNull()
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
      instituteId = student.instituteId.ifBlank { getCurrentInstituteId() },
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

    _allStudentTestAttempts.update { list ->
      listOf(attempt) + list.filterNot { it.studentId == student.id && it.testId == resolvedTestId }
    }
    refreshScopedStateFlows()
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
    val targetTest = _allTests.value.find { it.id == testId } ?: return false
    if (!requireTeacherOrAdminForBatch(
        actionName = "saveTestMarks",
        batchNameOrId = targetTest.batchId.ifBlank { targetTest.batch },
        targetInstituteId = targetTest.instituteId
      )
    ) {
      return false
    }
    val allowedStudentIds = getStudentsForCurrentSession().map { it.id }.toSet()
    val filteredMarks = marksMap.filterKeys { it in allowedStudentIds }
    saveTestMarksInternal(testId, filteredMarks)
    return true
  }

  private fun saveTestMarksInternal(testId: String, marksMap: Map<String, Int>) {
    val validStudentIds = _allStudents.value
      .filter { belongsToCurrentInstitute(it.instituteId) }
      .map { it.id }
      .toSet()
    val sanitizedMarks = if (validStudentIds.isNotEmpty()) {
      marksMap.filterKeys { validStudentIds.contains(it) }
    } else {
      marksMap
    }
    val classScores = sanitizedMarks.values.toList()
    val avgScore = if (classScores.isNotEmpty()) classScores.average().toInt() else 0
    val targetTest = _allTests.value.find { it.id == testId }
    val testName = targetTest?.testName ?: "Batch Assessment"
    val maxMarks = (targetTest?.totalMarks ?: 300).coerceAtLeast(1)
    val dynamicAiSuggestion = AiService.generatePerformanceInsights(testName, avgScore, maxMarks)

    var updatedTestRecord: TestRecord? = null
    _allTests.update { list ->
      list.map { test ->
        if (test.id == testId) {
          val copy = test.copy(studentMarks = sanitizedMarks, aiSuggestion = dynamicAiSuggestion)
          updatedTestRecord = copy
          copy
        } else test
      }
    }
    refreshScopedStateFlows()
    updatedTestRecord?.let { tst ->
      repoScope.launch {
        studentBatchRepo?.updateTest(tst)
      }
    }

    // Synchronize student performance analytics (overallAvg, recentScores, rank)
    recalculateStudentAcademicStats(sanitizedMarks, targetTest?.subject ?: "Assessment", maxMarks)

    NetworkConnectivityManager.markDataUpdatedOnline()
    val activeInst = getCurrentInstituteId()
    sanitizedMarks.forEach { (stuId, score) ->
      val pct = (score * 100) / maxMarks
      if (pct < 45) {
        val stu = _allStudents.value.find { it.id == stuId }
        if (stu != null) {
          addNotificationInternal(
            NotificationItem(
              id = "NOT_LOW_${System.currentTimeMillis()}_$stuId",
              title = "📉 Academic Performance Alert",
              message = "${stu.name} scored $score/$maxMarks ($pct%) in $testName. Teacher review recommended.",
              time = getCurrentTimeStr(),
              type = "Test",
              recipientRole = "Parent",
              targetStudentId = stu.id,
              targetBatchName = stu.batch,
              instituteId = activeInst
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
    _allStudents.update { list ->
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
    refreshScopedStateFlows()
    if (updatedStudents.isNotEmpty()) {
      repoScope.launch {
        updatedStudents.forEach { studentBatchRepo?.updateStudent(it) }
      }
    }
  }

  // Fees & Revenue — Authoritative Unified Ledger
  private val _allFees = MutableStateFlow<List<FeeRecord>>(SeedData.getDefaultFeeRecords())
  private val _scopedFees = MutableStateFlow<List<FeeRecord>>(emptyList())
  val fees = _scopedFees.asStateFlow()

  private val _allFeePayments = MutableStateFlow<List<StudentFeePayment>>(SeedData.getDefaultFeePayments())
  private val _scopedFeePayments = MutableStateFlow<List<StudentFeePayment>>(emptyList())
  val feePayments = _scopedFeePayments.asStateFlow()

  private fun syncStudentFeeStatus(studentId: String) {
    if (studentId.isBlank()) return
    val studentInvoices = _allFees.value.filter { it.studentId.equals(studentId, ignoreCase = true) }
    if (studentInvoices.isEmpty()) return
    val totalPending = studentInvoices.sumOf { it.pendingAmount }
    val newFeeStatus = if (totalPending <= 0) "Paid" else "Pending"
    var updatedStudent: Student? = null
    _allStudents.update { list ->
      list.map { stu ->
        if (stu.id.equals(studentId, ignoreCase = true) && stu.feeStatus != newFeeStatus) {
          val copy = stu.copy(feeStatus = newFeeStatus)
          updatedStudent = copy
          copy
        } else stu
      }
    }
    refreshScopedStudents()
    updatedStudent?.let { stu ->
      repoScope.launch {
        studentBatchRepo?.updateStudent(stu)
      }
    }
  }

  fun recordFeePayment(recordId: String, payAmt: Int, mode: String = "UPI", remarks: String = "Installment payment"): Boolean {
    if (!checkOnlineOrBlock("record fee transaction")) return false
    if (payAmt <= 0 || !isAuthenticatedSession()) return false
    val existingInvoice = _allFees.value.find { it.id == recordId } ?: return false
    if (!belongsToCurrentInstitute(existingInvoice.instituteId)) return false
    if (!isAdminSession()) {
      if (!isParentSession()) return false
      val linkedIds = getLinkedStudentsForCurrentParent().map { it.id.lowercase() }.toSet()
      if (existingInvoice.studentId.lowercase() !in linkedIds) {
        com.example.security.SecurityEngine.logEvent(
          severity = com.example.security.SecuritySeverity.ALERT,
          eventType = "UNAUTHORIZED_FEE_PAYMENT_BLOCKED",
          actor = _currentUserIdentifier.value,
          details = "Blocked parent attempt to record payment on unlinked invoice '$recordId' for student '${existingInvoice.studentId}'."
        )
        return false
      }
    }

    var targetStudentName = ""
    var targetStudentId = ""
    var targetMonth = "August 2026"
    var targetInvoiceId = recordId
    var updatedInvoice: FeeRecord? = null

    _allFees.update { list ->
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
    refreshScopedFees()

    updatedInvoice?.let { inv ->
      syncStudentFeeStatus(inv.studentId)
      repoScope.launch {
        studentBatchRepo?.updateFeeInvoice(inv)
      }
    }

    if (targetStudentId.isNotBlank() || targetStudentName.isNotBlank()) {
      val foundStudent = _allStudents.value.find {
        belongsToCurrentInstitute(it.instituteId) &&
          ((targetStudentId.isNotBlank() && it.id.equals(targetStudentId, ignoreCase = true)) ||
            (targetStudentId.isBlank() && it.name.equals(targetStudentName, ignoreCase = true)))
      }
      val resolvedStudentId = foundStudent?.id ?: targetStudentId
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
        instituteId = foundStudent?.instituteId?.ifBlank { getCurrentInstituteId() } ?: getCurrentInstituteId(),
        transactionId = "TXN-${now % 1000000}"
      )
      recordStudentFeePayment(paymentItem, syncInvoice = false)
    }
    NetworkConnectivityManager.markDataUpdatedOnline()
    return true
  }

  fun recordStudentFeePayment(payment: StudentFeePayment, syncInvoice: Boolean = true): Boolean {
    if (!checkOnlineOrBlock("record fee payment")) return false
    if (!isAuthenticatedSession() || payment.amount <= 0.0) return false
    // Resolve stable studentId first within current institute
    val instStudents = _allStudents.value.filter { belongsToCurrentInstitute(it.instituteId) }
    val matchedStu = instStudents.find {
      (payment.studentId.isNotBlank() && it.id.equals(payment.studentId, ignoreCase = true)) ||
        (payment.studentId.isBlank() && it.name.equals(payment.studentName, ignoreCase = true))
    }
    val resolvedStudentId = matchedStu?.id ?: payment.studentId
    if (!isAdminSession()) {
      if (!isParentSession()) return false
      val linkedIds = getLinkedStudentsForCurrentParent().map { it.id.lowercase() }.toSet()
      if (resolvedStudentId.lowercase() !in linkedIds) {
        return false
      }
    } else if (payment.instituteId.isNotBlank() && !belongsToCurrentInstitute(payment.instituteId)) {
      return false
    }

    val resolvedStudentName = matchedStu?.name ?: payment.studentName
    val activeInst = getCurrentInstituteId()
    val normalizedPayment = payment.copy(
      studentId = resolvedStudentId,
      studentName = resolvedStudentName,
      instituteId = activeInst,
      transactionId = payment.transactionId.ifBlank { "TXN-${payment.timestamp % 1000000}" },
      recordedBy = _currentUserIdentifier.value.ifBlank { payment.recordedBy.ifBlank { "Admin" } }
    )

    _allFeePayments.update { listOf(normalizedPayment) + it.filter { p -> p.id != normalizedPayment.id } }
    refreshScopedFeePayments()
    repoScope.launch {
      studentBatchRepo?.insertFeePayment(normalizedPayment)
    }

    // Keep FeeRecord invoice ledger synchronized when a direct receipt is logged
    if (syncInvoice && normalizedPayment.paymentStatus.equals("Paid", ignoreCase = true)) {
      var syncedInvoice: FeeRecord? = null
      _allFees.update { list ->
        val matchIdx = list.indexOfFirst { rec ->
          belongsToCurrentInstitute(rec.instituteId) &&
            ((normalizedPayment.invoiceId.isNotBlank() && rec.id == normalizedPayment.invoiceId) ||
              (resolvedStudentId.isNotBlank() && rec.studentId.equals(resolvedStudentId, ignoreCase = true)))
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
      refreshScopedFees()
      syncedInvoice?.let { inv ->
        syncStudentFeeStatus(inv.studentId)
        repoScope.launch {
          studentBatchRepo?.updateFeeInvoice(inv)
        }
      }
    }

    NetworkConnectivityManager.markDataUpdatedOnline()

    addNotificationInternal(
      NotificationItem(
        id = "N_PAY_${System.currentTimeMillis()}",
        title = "💳 Fee Payment Received",
        message = "Payment of ₹${normalizedPayment.amount.toInt()} received for ${normalizedPayment.studentName} (${normalizedPayment.feeType}, ${normalizedPayment.paymentMode}). Receipt: ${normalizedPayment.receiptNo}.",
        time = "Just now",
        type = "Fee",
        recipientRole = "All",
        targetStudentId = normalizedPayment.studentId,
        instituteId = activeInst
      )
    )
    return true
  }

  fun updateStudentFeePayment(payment: StudentFeePayment): Boolean {
    if (!checkOnlineOrBlock("update fee payment record")) return false
    val existing = _allFeePayments.value.find { it.id == payment.id }
    if (!requireAdminSession("updateStudentFeePayment", existing?.instituteId ?: payment.instituteId)) {
      return false
    }
    val normalized = payment.copy(instituteId = existing?.instituteId ?: getCurrentInstituteId())
    _allFeePayments.update { list ->
      list.map { if (it.id == normalized.id) normalized else it }
    }
    refreshScopedStateFlows()
    repoScope.launch {
      studentBatchRepo?.updateFeePayment(normalized)
    }
    NetworkConnectivityManager.markDataUpdatedOnline()
    return true
  }

  fun deleteStudentFeePayment(paymentId: String): Boolean {
    if (!checkOnlineOrBlock("delete fee payment record")) return false
    val existing = _allFeePayments.value.find { it.id == paymentId }
    if (!requireAdminSession("deleteStudentFeePayment", existing?.instituteId)) return false
    _allFeePayments.update { list -> list.filter { it.id != paymentId } }
    refreshScopedStateFlows()
    repoScope.launch {
      studentBatchRepo?.deleteFeePayment(paymentId)
    }
    NetworkConnectivityManager.markDataUpdatedOnline()
    return true
  }

  fun addFeeRecord(
    studentName: String,
    feeAmount: Int,
    dueDate: String,
    paidAmount: Int,
    month: String,
    studentId: String = ""
  ): Boolean {
    if (!checkOnlineOrBlock("create fee invoice")) return false
    if (!requireAdminSession("addFeeRecord")) return false
    val activeInst = getCurrentInstituteId()
    val matchedStu = _allStudents.value.find {
      belongsToCurrentInstitute(it.instituteId) &&
        ((studentId.isNotBlank() && it.id.equals(studentId, ignoreCase = true)) ||
          (studentId.isBlank() && it.name.equals(studentName, ignoreCase = true)))
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
      instituteId = activeInst
    )

    _allFees.update { list -> list + newRec }
    syncStudentFeeStatus(resolvedStudentId)
    refreshScopedStateFlows()
    repoScope.launch {
      studentBatchRepo?.insertFeeInvoice(newRec)
    }
    NetworkConnectivityManager.markDataUpdatedOnline()
    return true
  }

  fun sendFeeReminder(recordId: String): Boolean {
    if (!checkOnlineOrBlock("dispatch fee reminder")) return false
    val existing = _allFees.value.find { it.id == recordId } ?: return false
    if (!requireAdminSession("sendFeeReminder", existing.instituteId)) return false

    val currentTimeStr = SimpleDateFormat("MMM dd, hh:mm a", Locale.getDefault()).format(Date())
    var studentName = ""
    var studentId = ""
    var month = ""
    var pendingAmt = 0
    var dueDate = ""
    var updatedInvoice: FeeRecord? = null

    _allFees.update { list ->
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
    refreshScopedStateFlows()

    updatedInvoice?.let { inv ->
      repoScope.launch {
        studentBatchRepo?.updateFeeInvoice(inv)
      }
    }

    if (studentName.isNotEmpty()) {
      addNotificationInternal(
        NotificationItem(
          id = "N_FEE_${System.currentTimeMillis()}",
          title = "💳 Fee Payment Reminder",
          message = "Reminder for $studentName: The monthly fee of ₹$pendingAmt for $month is due on $dueDate. Please pay at the earliest.",
          time = "Just now",
          type = "Fee",
          recipientRole = "Parent",
          targetStudentId = studentId,
          instituteId = existing.instituteId
        )
      )
    }
    NetworkConnectivityManager.markDataUpdatedOnline()
    return true
  }

  fun deleteFeeRecord(recordId: String): Boolean {
    if (!checkOnlineOrBlock("delete fee record")) return false
    val existing = _allFees.value.find { it.id == recordId } ?: return false
    if (!requireAdminSession("deleteFeeRecord", existing.instituteId)) return false
    val targetStudentId = existing.studentId
    _allFees.update { current ->
      current.filter { it.id != recordId }
    }
    if (targetStudentId.isNotBlank()) syncStudentFeeStatus(targetStudentId)
    refreshScopedStateFlows()
    repoScope.launch {
      studentBatchRepo?.deleteFeeInvoice(recordId)
    }
    NetworkConnectivityManager.markDataUpdatedOnline()
    return true
  }

  // Admission CRM Leads
  private val _allLeads = MutableStateFlow<List<LeadRecord>>(emptyList())
  private val _scopedLeads = MutableStateFlow<List<LeadRecord>>(emptyList())
  val leads = _scopedLeads.asStateFlow()

  fun addLead(l: LeadRecord): Boolean {
    if (!checkOnlineOrBlock("record admission lead")) return false
    if (!requireAdminSession("addLead", l.instituteId)) return false
    val normalized = l.copy(instituteId = getCurrentInstituteId())
    _allLeads.update { listOf(normalized) + it }
    refreshScopedLeads()
    repoScope.launch {
      studentBatchRepo?.insertLead(normalized)
    }
    NetworkConnectivityManager.markDataUpdatedOnline()
    return true
  }

  fun updateLeadStatus(leadId: String, st: String): Boolean {
    if (!checkOnlineOrBlock("update lead status")) return false
    val existing = _allLeads.value.find { it.id == leadId } ?: return false
    if (!requireAdminSession("updateLeadStatus", existing.instituteId)) return false
    var updatedLead: LeadRecord? = null
    _allLeads.update { list ->
      list.map {
        if (it.id == leadId) {
          val copy = it.copy(status = st)
          updatedLead = copy
          copy
        } else it
      }
    }
    refreshScopedLeads()
    updatedLead?.let { lead ->
      repoScope.launch {
        studentBatchRepo?.updateLead(lead)
      }
    }
    NetworkConnectivityManager.markDataUpdatedOnline()
    return updatedLead != null
  }

  // Online Admission Form Submissions
  private val _allOnlineForms = MutableStateFlow<List<OnlineFormSubmission>>(emptyList())
  private val _scopedOnlineForms = MutableStateFlow<List<OnlineFormSubmission>>(emptyList())
  val onlineForms = _scopedOnlineForms.asStateFlow()

  fun submitOnlineForm(form: OnlineFormSubmission): Boolean {
    if (!checkOnlineOrBlock("submit online admission form")) return false
    if (!requireAdminSession("submitOnlineForm", form.instituteId)) return false
    val normalized = form.copy(instituteId = getCurrentInstituteId())
    _allOnlineForms.update { listOf(normalized) + it }
    refreshScopedOnlineForms()
    repoScope.launch {
      studentBatchRepo?.insertOnlineForm(normalized)
    }
    NetworkConnectivityManager.markDataUpdatedOnline()
    return true
  }

  fun processOnlineForm(formId: String, approve: Boolean): Boolean {
    if (!checkOnlineOrBlock("process online admission application")) return false
    val existing = _allOnlineForms.value.find { it.id == formId } ?: return false
    if (!requireAdminSession("processOnlineForm", existing.instituteId)) return false
    var updatedForm: OnlineFormSubmission? = null
    _allOnlineForms.update { list ->
      list.map {
        if (it.id == formId) {
          val copy = it.copy(status = if (approve) "Approved" else "Rejected")
          updatedForm = copy
          copy
        } else it
      }
    }
    refreshScopedOnlineForms()
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
            status = "New",
            instituteId = existing.instituteId
          )
        )
      }
    }
    NetworkConnectivityManager.markDataUpdatedOnline()
    return updatedForm != null
  }

  // Question Bank (Persisted in Room)
  private val _allQuestions = MutableStateFlow<List<QuestionItem>>(SeedData.getDefaultQuestions())
  private val _scopedQuestions = MutableStateFlow<List<QuestionItem>>(emptyList())
  val questions = _scopedQuestions.asStateFlow()
  val questionBank = _scopedQuestions.asStateFlow()

  fun addQuestion(q: QuestionItem): Boolean {
    if (!checkOnlineOrBlock("add question to repository")) return false
    if (!requireTeacherOrAdminForBatch("addQuestion", "", q.instituteId)) return false
    val activeInst = getCurrentInstituteId()
    val normalizedQ = q.copy(
      subjectId = q.subjectId.ifBlank {
        _allSubjects.value.find {
          belongsToCurrentInstitute(it.instituteId) && it.name.contains(q.subject, ignoreCase = true)
        }?.id ?: "SUB_PHY_12"
      },
      instituteId = activeInst
    )
    _allQuestions.update { listOf(normalizedQ) + it.filterNot { existing -> existing.id == normalizedQ.id } }
    refreshScopedQuestions()
    repoScope.launch {
      studentBatchRepo?.insertQuestion(normalizedQ)
    }
    NetworkConnectivityManager.markDataUpdatedOnline()
    return true
  }

  // Doubts (Persisted in Room, strictly attributed to authenticated student)
  private val _allDoubts = MutableStateFlow<List<DoubtItem>>(emptyList())
  private val _scopedDoubts = MutableStateFlow<List<DoubtItem>>(emptyList())
  val doubts = _scopedDoubts.asStateFlow()

  fun getDoubtsForCurrentStudent(student: Student? = null): List<DoubtItem> {
    val activeStu = resolveAuthorizedStudentTarget(requestedStudent = student) ?: return emptyList()
    return _allDoubts.value.filter { d ->
      belongsToCurrentInstitute(d.instituteId) &&
        d.studentId.equals(activeStu.id, ignoreCase = true)
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
    if (!isAuthenticatedSession() || (!isStudentSession() && !isAdminSession())) return null
    val activeStudent = resolveAuthorizedStudentTarget(requestedStudentId = studentIdOverride) ?: return null
    val studentId = activeStudent.id
    val studentName = activeStudent.name
    val batchName = activeStudent.batch
    val batchId = activeStudent.batchId.ifBlank { SeedData.batchIdForName(batchName) }
    val activeInst = activeStudent.instituteId.ifBlank { getCurrentInstituteId() }
    val subjectId = _allSubjects.value.find {
      belongsToCurrentInstitute(it.instituteId) &&
        (it.name.equals(subject, ignoreCase = true) || it.name.contains(subject, ignoreCase = true))
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
      instituteId = activeInst,
      subjectId = subjectId,
      batchId = batchId,
      createdAt = now,
      updatedAt = now
    )
    _allDoubts.update { listOf(newDoubt) + it }
    refreshScopedDoubts()
    repoScope.launch {
      studentBatchRepo?.insertDoubt(newDoubt)
    }
    NetworkConnectivityManager.markDataUpdatedOnline()
    return newDoubt
  }

  fun replyToDoubt(doubtId: String, replyText: String): Boolean {
    if (!checkOnlineOrBlock("reply to student doubt")) return false
    val targetDoubt = _allDoubts.value.find { it.id == doubtId } ?: return false
    if (!requireTeacherOrAdminForBatch(
        actionName = "replyToDoubt",
        batchNameOrId = targetDoubt.batchId.ifBlank { targetDoubt.batchName },
        targetInstituteId = targetDoubt.instituteId
      )
    ) {
      return false
    }

    val responderName = if (isTeacherSession()) {
      getActiveTeacherForCurrentSession()?.name ?: _currentUserIdentifier.value.ifBlank { "Faculty" }
    } else {
      _currentUserAccount.value?.name ?: "Institute Administrator"
    }
    val now = System.currentTimeMillis()
    var updatedDoubt: DoubtItem? = null
    _allDoubts.update { list ->
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
    refreshScopedDoubts()
    updatedDoubt?.let { d ->
      repoScope.launch {
        studentBatchRepo?.updateDoubt(d)
      }
      addNotificationInternal(
        NotificationItem(
          id = "NOT_DBT_$now",
          title = "💡 Doubt Resolved (${d.subject})",
          message = "Faculty ($responderName) replied to ${d.studentName}'s doubt on ${d.chapter}: \"$replyText\"",
          time = getCurrentTimeStr(),
          type = "Academic",
          recipientRole = "Student",
          targetStudentId = d.studentId,
          targetBatchName = d.batchName,
          targetBatchId = d.batchId,
          instituteId = d.instituteId
        )
      )
    }
    NetworkConnectivityManager.markDataUpdatedOnline()
    return updatedDoubt != null
  }

  // Homework (Persisted in Room with Teacher -> Homework -> Subject -> Batch -> Students normalization)
  private val _allHomework = MutableStateFlow<List<HomeworkItem>>(emptyList())
  private val _scopedHomework = MutableStateFlow<List<HomeworkItem>>(emptyList())
  val homework = _scopedHomework.asStateFlow()

  fun getHomeworkForCurrentStudent(student: Student? = null): List<HomeworkItem> {
    val activeStu = resolveAuthorizedStudentTarget(requestedStudent = student) ?: return emptyList()
    return _allHomework.value.filter { hw ->
      belongsToCurrentInstitute(hw.instituteId) &&
        ((activeStu.batchId.isNotBlank() && hw.batchId.equals(activeStu.batchId, ignoreCase = true)) ||
          (activeStu.batch.isNotBlank() && hw.batchName.equals(activeStu.batch, ignoreCase = true)))
    }.map { hw ->
      hw.copy(completedByStudentIds = hw.completedByStudentIds.filter { it.equals(activeStu.id, ignoreCase = true) })
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
    val targetBatchCheck = batchId.ifBlank { batchName }
    if (!requireTeacherOrAdminForBatch("addHomework", targetBatchCheck)) return false

    val activeInst = getCurrentInstituteId()
    val activeTeacher = getActiveTeacherForCurrentSession()
    val teacherName = assignedBy.ifBlank {
      activeTeacher?.name ?: reconcileFacultyName(null, batchName, subject)
    }
    val resolvedBatch = _allBatches.value.find {
      belongsToCurrentInstitute(it.instituteId) &&
        ((batchId.isNotBlank() && it.id.equals(batchId, ignoreCase = true)) ||
          it.name.equals(batchName, ignoreCase = true) ||
          it.id.equals(batchName, ignoreCase = true))
    }
    val resolvedBatchId = batchId.ifBlank { resolvedBatch?.id ?: SeedData.batchIdForName(batchName) }
    val resolvedBatchName = resolvedBatch?.name ?: batchName
    val resolvedTeacherId = activeTeacher?.id ?: resolvedBatch?.teacherId ?: ""
    val resolvedSubjectId = subjectId.ifBlank {
      _allSubjects.value.find {
        belongsToCurrentInstitute(it.instituteId) &&
          (it.name.equals(subject, ignoreCase = true) || it.name.contains(subject, ignoreCase = true))
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
      instituteId = activeInst
    )
    _allHomework.update { listOf(newHw) + it }
    refreshScopedHomework()
    repoScope.launch {
      studentBatchRepo?.insertHomework(newHw)
    }
    addNotificationInternal(
      NotificationItem(
        id = "NOT_HW_${System.currentTimeMillis()}",
        title = "📚 New Homework Assigned ($subject)",
        message = "$title for $resolvedBatchName due on $dueDate.",
        time = getCurrentTimeStr(),
        type = "Homework",
        recipientRole = "Student",
        targetBatchName = resolvedBatchName,
        targetBatchId = resolvedBatchId,
        instituteId = activeInst
      )
    )
    NetworkConnectivityManager.markDataUpdatedOnline()
    return true
  }

  fun toggleHomeworkStatus(homeworkId: String, studentId: String = "", feedback: String? = null): Boolean {
    if (!checkOnlineOrBlock("update homework status")) return false
    if (!isAuthenticatedSession() || isParentSession()) return false
    val targetHw = _allHomework.value.find { it.id == homeworkId } ?: return false
    if (!belongsToCurrentInstitute(targetHw.instituteId)) return false
    if (isTeacherSession() && !isTeacherAssignedToBatch(targetHw.batchId.ifBlank { targetHw.batchName })) {
      return false
    }
    val authorizedStudent = resolveAuthorizedStudentTarget(requestedStudentId = studentId) ?: return false
    val resolvedStudentId = authorizedStudent.id
    var updatedHw: HomeworkItem? = null
    val today = getCurrentDateStr()
    _allHomework.update { list ->
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
    refreshScopedHomework()
    updatedHw?.let { hw ->
      repoScope.launch {
        studentBatchRepo?.updateHomework(hw)
      }
    }
    NetworkConnectivityManager.markDataUpdatedOnline()
    return updatedHw != null
  }

  fun deleteHomework(homeworkId: String): Boolean {
    if (!checkOnlineOrBlock("delete homework")) return false
    val targetHw = _allHomework.value.find { it.id == homeworkId } ?: return false
    if (!requireTeacherOrAdminForBatch(
        actionName = "deleteHomework",
        batchNameOrId = targetHw.batchId.ifBlank { targetHw.batchName },
        targetInstituteId = targetHw.instituteId
      )
    ) {
      return false
    }

    _allHomework.update { list -> list.filterNot { it.id == homeworkId } }
    refreshScopedHomework()
    repoScope.launch {
      studentBatchRepo?.deleteHomework(homeworkId)
    }
    NetworkConnectivityManager.markDataUpdatedOnline()
    return true
  }

  // Timetable & Conflict Validation
  private val _allTimetable = MutableStateFlow<List<TimetableItem>>(SeedData.getDefaultWeeklyTimetable())
  private val _scopedTimetable = MutableStateFlow<List<TimetableItem>>(emptyList())
  val timetable = _scopedTimetable.asStateFlow()

  private val _allDynamicTimetable = MutableStateFlow<List<DynamicTimetableEntry>>(emptyList())
  private val _scopedDynamicTimetable = MutableStateFlow<List<DynamicTimetableEntry>>(emptyList())
  val dynamicTimetable = _scopedDynamicTimetable.asStateFlow()

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

    val activeInst = currentActiveInstituteId()
    val targetBatch = _allBatches.value.find {
      isSameInstitute(it.instituteId, activeInst) &&
        (it.id.equals(batchNameOrId, ignoreCase = true) || it.name.equals(batchNameOrId, ignoreCase = true))
    }
    val batchId = targetBatch?.id ?: batchNameOrId
    val batchName = targetBatch?.name ?: batchNameOrId

    val targetTeacher = _allTeachers.value.find {
      isSameInstitute(it.instituteId, activeInst) &&
        (it.id.equals(teacherNameOrId, ignoreCase = true) || it.name.equals(teacherNameOrId, ignoreCase = true))
    }
    val teacherId = targetTeacher?.id ?: ""
    val teacherName = targetTeacher?.name ?: teacherNameOrId

    if (day != null) {
      // Check weekly timetable on the same day within the current institute
      for (item in _allTimetable.value) {
        if (!isSameInstitute(item.instituteId, activeInst)) continue
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
      // Check active dynamic timetable entries (based on scheduled session end time, not createdAt) within the current institute
      for (entry in _allDynamicTimetable.value) {
        if (!isSameInstitute(entry.instituteId, activeInst)) continue
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
    val activeInst = currentActiveInstituteId()
    val batchKey = item.batchId.ifBlank { item.batch }
    if (!requireTeacherOrAdminForBatch(
        actionName = "addWeeklyTimetableItem",
        batchNameOrId = batchKey,
        targetInstituteId = item.instituteId.ifBlank { activeInst }
      )
    ) {
      return false to "Unauthorized role or batch assignment"
    }

    val batchObj = _allBatches.value.find {
      isSameInstitute(it.instituteId, activeInst) &&
        (it.id.equals(item.batchId, ignoreCase = true) || it.name.equals(item.batch, ignoreCase = true))
    }
    val resolvedBatchId = item.batchId.ifBlank { batchObj?.id ?: SeedData.batchIdForName(item.batch) }
    val resolvedBatchName = batchObj?.name ?: item.batch
    val resolvedTeacherName = item.teacherName.ifBlank { batchObj?.teacherName ?: reconcileFacultyName(null, resolvedBatchName, item.subject) }
    val teacherObj = _allTeachers.value.find {
      isSameInstitute(it.instituteId, activeInst) &&
        (it.id.equals(item.teacherId, ignoreCase = true) || it.name.equals(resolvedTeacherName, ignoreCase = true))
    }
    val resolvedTeacherId = item.teacherId.ifBlank { teacherObj?.id ?: batchObj?.teacherId ?: "" }
    val resolvedSubjectId = item.subjectId.ifBlank {
      _allSubjects.value.find {
        isSameInstitute(it.instituteId, activeInst) &&
          it.name.contains(item.subject.substringBefore("•").trim(), ignoreCase = true)
      }?.id ?: "SUB_PHY_12"
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
      instituteId = activeInst
    )
    _allTimetable.update { listOf(normalized) + it.filterNot { existing -> existing.id == normalized.id } }
    refreshScopedStateFlows()
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
    val activeInst = currentActiveInstituteId()
    if (!requireTeacherOrAdminForBatch(
        actionName = "addDynamicTimetableEntry",
        batchNameOrId = batchId.ifBlank { batch },
        targetInstituteId = activeInst
      )
    ) {
      return false
    }
    if (!isValidTimeRange(startTime, endTime)) return false

    val batchObj = _allBatches.value.find {
      isSameInstitute(it.instituteId, activeInst) &&
        ((batchId.isNotBlank() && it.id.equals(batchId, ignoreCase = true)) ||
          it.name.equals(batch, ignoreCase = true) ||
          it.id.equals(batch, ignoreCase = true))
    }
    val resolvedBatchId = batchId.ifBlank { batchObj?.id ?: SeedData.batchIdForName(batch) }
    val resolvedBatchName = batchObj?.name ?: batch

    val resolvedTeacher = if (teacherName.isBlank() || teacherName.equals("Faculty", ignoreCase = true) || teacherName.equals("Subject Faculty", ignoreCase = true)) {
      batchObj?.teacherName ?: reconcileFacultyName(teacherName, resolvedBatchName, subject)
    } else {
      reconcileFacultyName(teacherName, resolvedBatchName, subject)
    }
    val teacherObj = _allTeachers.value.find {
      isSameInstitute(it.instituteId, activeInst) &&
        ((teacherId.isNotBlank() && it.id.equals(teacherId, ignoreCase = true)) ||
          it.name.equals(resolvedTeacher, ignoreCase = true))
    }
    val resolvedTeacherId = teacherId.ifBlank { teacherObj?.id ?: batchObj?.teacherId ?: "" }
    val resolvedSubjectId = _allSubjects.value.find {
      isSameInstitute(it.instituteId, activeInst) &&
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
      instituteId = activeInst,
      dateStr = effectiveDateStr,
      scheduledStartMillis = schedStart,
      scheduledEndMillis = schedEnd,
      isArchived = archived
    )
    _allDynamicTimetable.update { current ->
      listOf(newEntry) + current
    }
    refreshScopedDynamicTimetable()
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
    val activeInst = currentActiveInstituteId()
    if (!requireTeacherOrAdminForBatch(
        actionName = "addDynamicTimetableEntries",
        batchNameOrId = batchId.ifBlank { batch },
        targetInstituteId = activeInst
      )
    ) {
      return false
    }

    val batchObj = _allBatches.value.find {
      isSameInstitute(it.instituteId, activeInst) &&
        ((batchId.isNotBlank() && it.id.equals(batchId, ignoreCase = true)) ||
          it.name.equals(batch, ignoreCase = true) ||
          it.id.equals(batch, ignoreCase = true))
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
      val teacherObj = _allTeachers.value.find {
        isSameInstitute(it.instituteId, activeInst) &&
          ((teacherId.isNotBlank() && it.id.equals(teacherId, ignoreCase = true)) ||
            it.name.equals(entryTeacher, ignoreCase = true))
      }
      val resolvedTeacherId = teacherId.ifBlank { teacherObj?.id ?: batchObj?.teacherId ?: "" }
      val resolvedSubjectId = _allSubjects.value.find {
        isSameInstitute(it.instituteId, activeInst) &&
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
        instituteId = activeInst,
        dateStr = effectiveDateStr,
        scheduledStartMillis = schedStart,
        scheduledEndMillis = schedEnd,
        isArchived = archived
      )
    }
    _allDynamicTimetable.update { current ->
      newEntries + current
    }
    refreshScopedDynamicTimetable()
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
    val targetEntry = _allDynamicTimetable.value.find { it.id == id } ?: return false
    if (!requireTeacherOrAdminForBatch(
        actionName = "deleteDynamicTimetableEntry",
        batchNameOrId = targetEntry.batchId.ifBlank { targetEntry.batch },
        targetInstituteId = targetEntry.instituteId
      )
    ) {
      return false
    }
    _allDynamicTimetable.update { current ->
      current.filter { it.id != id }
    }
    refreshScopedDynamicTimetable()
    studentBatchRepo?.let { repo ->
      repoScope.launch {
        repo.deleteTimetableEntry(id)
      }
    }
    NetworkConnectivityManager.markDataUpdatedOnline()
    return true
  }

  // Study Logs (Persisted in Room, scoped to authenticated student)
  private val _allStudyLogs = MutableStateFlow<List<StudyLogItem>>(emptyList())
  private val _scopedStudyLogs = MutableStateFlow<List<StudyLogItem>>(emptyList())
  val studyLogs = _scopedStudyLogs.asStateFlow()

  fun getStudyLogsForCurrentStudent(student: Student? = null): List<StudyLogItem> {
    if (!isAuthenticatedSession()) return emptyList()
    val activeStu = resolveAuthorizedStudentTarget(student?.id) ?: return emptyList()
    return _allStudyLogs.value.filter { log ->
      isSameInstitute(log.instituteId, activeStu.instituteId) &&
        log.studentId.equals(activeStu.id, ignoreCase = true)
    }
  }

  fun logDailyStudy(hours: Float, subjects: String, chapters: String, studentIdOverride: String? = null): Boolean {
    if (!checkOnlineOrBlock("log study session")) return false
    if (!isAuthenticatedSession()) {
      logRbacDenial("logDailyStudy", "Unauthenticated session")
      return false
    }
    val role = currentVerifiedRole()
    val isAdmin = isCurrentSessionAdmin()
    if (!isAdmin && role != UserRole.STUDENT) {
      logRbacDenial("logDailyStudy", "Role $role cannot log study sessions")
      return false
    }
    val stu = resolveAuthorizedStudentTarget(if (isAdmin) studentIdOverride else null)
    if (stu == null) {
      logRbacDenial("logDailyStudy", "No authorized student identity resolved")
      return false
    }
    if (!isAdmin && !studentIdOverride.isNullOrBlank() && !studentIdOverride.equals(stu.id, ignoreCase = true)) {
      logRbacDenial("logDailyStudy", "Student ${stu.id} attempted to log study session for $studentIdOverride")
      return false
    }
    val activeInst = currentActiveInstituteId()
    val logItem = StudyLogItem(
      id = "SL_${System.currentTimeMillis()}",
      date = getCurrentDateStr(),
      hours = hours,
      subjects = subjects,
      chapters = chapters,
      studentId = stu.id,
      instituteId = stu.instituteId.ifBlank { activeInst },
      timestamp = System.currentTimeMillis()
    )
    _allStudyLogs.update { listOf(logItem) + it }
    refreshScopedStudyLogs()
    repoScope.launch {
      studentBatchRepo?.insertStudyLog(logItem)
    }
    NetworkConnectivityManager.markDataUpdatedOnline()
    return true
  }

  // Student Goals (Persisted in Room, scoped to authenticated student)
  private val _allStudentGoals = MutableStateFlow<List<GoalItem>>(emptyList())
  private val _scopedStudentGoal = MutableStateFlow(
    GoalItem(targetMarks = 260, targetPercent = 90, targetRank = 3, targetAttendance = 95, weeklyStudyHours = 28)
  )
  val studentGoal = _scopedStudentGoal.asStateFlow()

  fun getGoalForCurrentStudent(student: Student? = null): GoalItem {
    val activeInst = currentActiveInstituteId()
    val defaultGoal = GoalItem(
      targetMarks = 260,
      targetPercent = 90,
      targetRank = 3,
      targetAttendance = 95,
      weeklyStudyHours = 28,
      studentId = "",
      instituteId = activeInst
    )
    if (!isAuthenticatedSession()) return defaultGoal
    val activeStu = resolveAuthorizedStudentTarget(requestedStudent = student, requestedStudentId = student?.id) ?: return defaultGoal
    val matchedFromList = _allStudentGoals.value.find {
      isSameInstitute(it.instituteId, activeStu.instituteId) &&
        it.studentId.equals(activeStu.id, ignoreCase = true)
    }
    if (matchedFromList != null) {
      return matchedFromList
    }
    val current = _scopedStudentGoal.value
    if (!isSameInstitute(current.instituteId, activeStu.instituteId)) {
      return defaultGoal.copy(studentId = activeStu.id, instituteId = activeStu.instituteId)
    }
    return if (current.studentId.equals(activeStu.id, ignoreCase = true)) {
      current.copy(studentId = activeStu.id, instituteId = activeStu.instituteId)
    } else {
      defaultGoal.copy(studentId = activeStu.id, instituteId = activeStu.instituteId)
    }
  }

  fun updateStudentGoal(g: GoalItem): Boolean {
    if (!checkOnlineOrBlock("modify academic goals")) return false
    if (!isAuthenticatedSession()) {
      logRbacDenial("updateStudentGoal", "Unauthenticated session")
      return false
    }
    val role = currentVerifiedRole()
    val isAdmin = isCurrentSessionAdmin()
    if (!isAdmin && role != UserRole.STUDENT) {
      logRbacDenial("updateStudentGoal", "Role $role cannot modify student academic goals")
      return false
    }
    val stu = resolveAuthorizedStudentTarget(if (isAdmin) g.studentId else null)
    if (stu == null) {
      logRbacDenial("updateStudentGoal", "No authorized student identity resolved")
      return false
    }
    if (!isAdmin && g.studentId.isNotBlank() && !g.studentId.equals(stu.id, ignoreCase = true)) {
      logRbacDenial("updateStudentGoal", "Student ${stu.id} attempted to update goal for ${g.studentId}")
      return false
    }
    val normalizedGoal = g.copy(
      studentId = stu.id,
      instituteId = stu.instituteId.ifBlank { currentActiveInstituteId() }
    )
    _allStudentGoals.update { list ->
      listOf(normalizedGoal) + list.filterNot {
        isSameInstitute(it.instituteId, normalizedGoal.instituteId) &&
          it.studentId.equals(normalizedGoal.studentId, ignoreCase = true)
      }
    }
    _scopedStudentGoal.value = normalizedGoal
    repoScope.launch {
      studentBatchRepo?.insertStudentGoal(normalizedGoal)
    }
    NetworkConnectivityManager.markDataUpdatedOnline()
    return true
  }

  // Badges
  private val _badges = MutableStateFlow<List<BadgeItem>>(emptyList())
  val badges = _badges.asStateFlow()

  // Notifications
  private val _allNotifications = MutableStateFlow<List<NotificationItem>>(emptyList())
  private val _scopedNotifications = MutableStateFlow<List<NotificationItem>>(emptyList())
  val notifications = _scopedNotifications.asStateFlow()

  private fun addNotificationInternal(n: NotificationItem) {
    val activeInst = currentActiveInstituteId()
    val normalized = n.copy(
      instituteId = n.instituteId.ifBlank { activeInst }
    )
    _allNotifications.update { listOf(normalized) + it }
    refreshScopedNotifications()
    repoScope.launch {
      studentBatchRepo?.insertNotification(normalized)
    }
    appContext?.let { ctx ->
      com.example.utils.NotificationHelper.sendPushNotification(ctx, normalized.title, normalized.message)
    }
  }

  fun addNotification(n: NotificationItem): Boolean {
    if (!isAuthenticatedSession()) {
      logRbacDenial("addNotification", "Unauthenticated session")
      return false
    }
    if (!isAdminSession() && !isTeacherSession()) {
      logRbacDenial("addNotification", "Role ${_currentRole.value} cannot broadcast notifications")
      return false
    }
    val activeInst = currentActiveInstituteId()
    if (n.instituteId.isNotBlank() && !isSameInstitute(n.instituteId, activeInst)) {
      logRbacDenial("addNotification", "Cross-institute notification blocked")
      return false
    }
    addNotificationInternal(n.copy(instituteId = activeInst))
    return true
  }

  fun markAllNotificationsAsRead() {
    if (!isAuthenticatedSession()) return
    val activeInst = currentActiveInstituteId()
    _allNotifications.update { list ->
      list.map {
        if (isSameInstitute(it.instituteId, activeInst)) it.copy(isRead = true) else it
      }
    }
    refreshScopedNotifications()
    repoScope.launch {
      studentBatchRepo?.markAllNotificationsAsRead()
    }
  }

  fun markNotificationAsRead(id: String) {
    if (!isAuthenticatedSession()) return
    val activeInst = currentActiveInstituteId()
    var updatedItem: NotificationItem? = null
    _allNotifications.update { list ->
      list.map {
        if (it.id == id && isSameInstitute(it.instituteId, activeInst)) {
          val copy = it.copy(isRead = true)
          updatedItem = copy
          copy
        } else it
      }
    }
    refreshScopedNotifications()
    updatedItem?.let { item ->
      repoScope.launch {
        studentBatchRepo?.updateNotification(item)
      }
    }
  }

  fun deleteNotification(id: String): Boolean {
    if (!isAuthenticatedSession()) {
      logRbacDenial("deleteNotification", "Unauthenticated session")
      return false
    }
    val activeInst = currentActiveInstituteId()
    val existing = _allNotifications.value.find { it.id == id } ?: return false
    if (!isSameInstitute(existing.instituteId, activeInst)) {
      logRbacDenial("deleteNotification", "Cross-institute notification deletion blocked")
      return false
    }
    if (!isCurrentSessionAdmin() && _scopedNotifications.value.none { it.id == id }) {
      logRbacDenial("deleteNotification", "Notification $id does not belong to current user scope")
      return false
    }
    _allNotifications.update { list -> list.filter { it.id != id } }
    refreshScopedNotifications()
    repoScope.launch {
      studentBatchRepo?.deleteNotification(id)
    }
    return true
  }

  fun deleteAllNotifications(): Boolean {
    if (!requireAdminSession("deleteAllNotifications")) return false
    val activeInst = currentActiveInstituteId()
    _allNotifications.update { list ->
      list.filterNot { isSameInstitute(it.instituteId, activeInst) }
    }
    refreshScopedNotifications()
    repoScope.launch {
      studentBatchRepo?.deleteAllNotifications()
    }
    return true
  }

  // Parent Meetings (Persisted in Room, scoped to linked parent/student/teacher)
  private val _allMeetings = MutableStateFlow<List<ParentMeetingItem>>(emptyList())
  private val _scopedMeetings = MutableStateFlow<List<ParentMeetingItem>>(emptyList())
  val meetings = _scopedMeetings.asStateFlow()

  fun getMeetingsForCurrentParent(): List<ParentMeetingItem> {
    if (!isAuthenticatedSession()) return emptyList()
    val activeInst = currentActiveInstituteId()
    val isAdmin = isCurrentSessionAdmin()
    if (isAdmin) {
      return _allMeetings.value.filter { isSameInstitute(it.instituteId, activeInst) }
    }
    val linkedIds = getLinkedStudentsForCurrentParent().map { it.id.lowercase() }.toSet()
    if (linkedIds.isEmpty()) return emptyList()
    return _allMeetings.value.filter {
      isSameInstitute(it.instituteId, activeInst) && it.studentId.lowercase() in linkedIds
    }
  }

  fun scheduleParentMeeting(
    teacherName: String,
    date: String,
    time: String,
    notes: String,
    studentIdOverride: String? = null
  ): Boolean {
    if (!checkOnlineOrBlock("schedule parent meeting")) return false
    if (!isAuthenticatedSession()) {
      logRbacDenial("scheduleParentMeeting", "Unauthenticated session")
      return false
    }
    val role = currentVerifiedRole()
    val isAdmin = isCurrentSessionAdmin()
    if (!isAdmin && role != UserRole.PARENT && role != UserRole.TEACHER) {
      logRbacDenial("scheduleParentMeeting", "Role $role cannot schedule parent meetings")
      return false
    }
    val activeInst = currentActiveInstituteId()
    val student = resolveAuthorizedStudentTarget(studentIdOverride)
    if (student == null) {
      logRbacDenial("scheduleParentMeeting", "Unauthorized or unresolved student target ($studentIdOverride)")
      return false
    }
    val studentId = student.id
    val studentName = student.name
    val parentId = student.parentId.ifBlank { _currentUserAccount.value?.userId ?: "" }
    val parentName = student.parentName.ifBlank { "Parent" }
    val teacherObj = _allTeachers.value.find {
      isSameInstitute(it.instituteId, activeInst) &&
        (it.name.equals(teacherName, ignoreCase = true) || it.id.equals(teacherName, ignoreCase = true))
    }
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
      instituteId = student.instituteId.ifBlank { activeInst }
    )
    _allMeetings.update { listOf(meetingItem) + it }
    refreshScopedMeetings()
    repoScope.launch {
      studentBatchRepo?.insertParentMeeting(meetingItem)
    }
    NetworkConnectivityManager.markDataUpdatedOnline()
    return true
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
    preserveExistingToken: String = "",
    instituteId: String = ""
  ): Boolean {
    val cleanId = identifier.trim()
    if (cleanId.isBlank()) return false
    val status = getAccountStatus(cleanId)
    val vaultRole = com.example.security.SecurityEngine.getVerifiedRole(cleanId)
    // Authoritative role resolution: directory or vault role takes precedence over caller-supplied role
    val resolvedRole = status.role ?: vaultRole ?: role

    if (resolvedRole == UserRole.TEACHER) {
      val t = _allTeachers.value.find {
        it.email.equals(cleanId, true) ||
          it.id.equals(cleanId, true) ||
          it.contact.equals(cleanId, true)
      }
      if (t?.status.equals("Pending Approval", ignoreCase = true)) {
        return false
      }
    }

    val matchedUserAcc = _allUserAccounts.value.find {
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

    val directoryInstituteId = matchedUserAcc?.instituteId?.takeIf {
      com.example.security.SecurityEngine.isValidInstituteId(it) && it != "FSI_MAIN"
    } ?: status.instituteId.takeIf {
      com.example.security.SecurityEngine.isValidInstituteId(it) && it != "FSI_MAIN"
    }
    val resolvedInstituteId = directoryInstituteId
      ?: instituteId.trim().takeIf { com.example.security.SecurityEngine.isValidInstituteId(it) }
      ?: matchedUserAcc?.instituteId?.takeIf { com.example.security.SecurityEngine.isValidInstituteId(it) }
      ?: status.instituteId.takeIf { com.example.security.SecurityEngine.isValidInstituteId(it) }
      ?: "FSI_MAIN"

    val resolvedAccount = matchedUserAcc?.copy(
      role = resolvedRole,
      linkedEntityId = effectiveLinkedId,
      linkedStudentIds = effectiveLinkedStudentIds,
      instituteId = resolvedInstituteId
    ) ?: UserAccount(
      userId = "USR_${cleanId.lowercase()}",
      email = cleanId,
      mobile = if (cleanId.any { it.isDigit() } && !cleanId.contains("@")) cleanId else "",
      name = status.displayName.ifBlank { cleanId },
      role = resolvedRole,
      linkedEntityId = effectiveLinkedId,
      linkedStudentIds = effectiveLinkedStudentIds,
      instituteId = resolvedInstituteId
    )

    val sessionToken = preserveExistingToken.ifBlank {
      com.example.security.SecurityEngine.issueSessionToken(cleanId, resolvedAccount.role)
    }

    _currentInstituteId.value = resolvedInstituteId
    _instituteConfig.value = instituteConfigsByTenant.getOrPut(resolvedInstituteId) {
      InstituteConfig(instituteId = resolvedInstituteId)
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
    refreshScopedStateFlows()

    val currentList = _savedAccounts.value.toMutableList()
    val existingIdx = currentList.indexOfFirst { it.identifier.equals(cleanId, true) }
    val displayName = status.displayName.ifBlank {
      cleanId.substringBefore("@").replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
    }
    val newAcc = SavedAccount(cleanId, _currentRole.value, displayName, resolvedInstituteId)
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
    linkedStudentIds: List<String> = emptyList(),
    instituteId: String = ""
  ): Boolean {
    return bindAuthenticatedUserSession(
      identifier = identifier,
      role = role,
      linkedEntityId = linkedEntityId,
      linkedStudentIds = linkedStudentIds,
      instituteId = instituteId
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
    _currentInstituteId.value = "FSI_MAIN"
    _selectedChildIdForParent.value = null
    saveSession(false, "", UserRole.STUDENT)
    refreshScopedStateFlows()
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
    _currentInstituteId.value = "FSI_MAIN"
    _selectedChildIdForParent.value = null
    saveSession(false, "", UserRole.STUDENT)
    refreshScopedStateFlows()
  }

  suspend fun registerStudent(name: String, email: String, rawPassword: String): Boolean = withContext(Dispatchers.IO) {
    if (!checkOnlineOrBlock("register student")) return@withContext false
    if (!requireAdminSession("registerStudent")) return@withContext false
    if (email.isBlank() || rawPassword.length < 6) return@withContext false
    val activeInst = currentActiveInstituteId()
    val existing = _allStudents.value.find {
      isSameInstitute(it.instituteId, activeInst) && it.email.equals(email, true)
    }
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
        isActivated = true,
        instituteId = activeInst
      )
      val added = addStudent(newStudent)
      if (!added) return@withContext false
      repoScope.launch(Dispatchers.Default) {
        com.example.security.SecurityEngine.registerUserCredential(
          identifier = email,
          rawPassword = rawPassword,
          role = UserRole.STUDENT,
          fullName = name
        )
      }
      NetworkConnectivityManager.markDataUpdatedOnline()
      return@withContext true
    }
    return@withContext false
  }

  fun registerTeacher(name: String, email: String, mobile: String, subject: String, rawPassword: String): Boolean {
    if (!checkOnlineOrBlock("register teacher")) return false
    if (!requireAdminSession("registerTeacher")) return false
    if (email.isBlank() || rawPassword.length < 6) return false
    val activeInst = currentActiveInstituteId()
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
      isActivated = true,
      instituteId = activeInst
    )
    _allTeachers.update { it + newT }
    refreshScopedStateFlows()
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
    return true
  }

  fun approveTeacher(teacherId: String): Boolean {
    if (!checkOnlineOrBlock("approve teacher account")) return false
    val existing = _allTeachers.value.find { it.id == teacherId } ?: return false
    if (!requireAdminSession("approveTeacher", existing.instituteId)) return false
    _allTeachers.update { list ->
      list.map { if (it.id == teacherId) it.copy(status = "Active", isActivated = true) else it }
    }
    refreshScopedStateFlows()
    val updated = _allTeachers.value.find { it.id == teacherId }
    if (updated != null) {
      repoScope.launch {
        studentBatchRepo?.updateTeacher(updated)
      }
    }
    NetworkConnectivityManager.markDataUpdatedOnline()
    return true
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

  fun clearAllAppData(context: android.content.Context): Boolean {
    if (!checkOnlineOrBlock("purge system data")) return false
    if (!requireAdminSession("clearAllAppData")) return false
    val activeInst = currentActiveInstituteId()

    preferences?.edit()?.apply {
      putBoolean("is_data_cleared", true)
      remove("saved_attendance_records")
      apply()
    }

    _allStudents.update { list -> list.filterNot { isSameInstitute(it.instituteId, activeInst) } }
    _allTeachers.update { list -> list.filterNot { isSameInstitute(it.instituteId, activeInst) } }
    _allBatches.update { list -> list.filterNot { isSameInstitute(it.instituteId, activeInst) } }
    _allSubjects.update { list -> list.filterNot { isSameInstitute(it.instituteId, activeInst) } }
    _allTests.update { list -> list.filterNot { isSameInstitute(it.instituteId, activeInst) } }
    _allStudentTestAttempts.update { list -> list.filterNot { isSameInstitute(it.instituteId, activeInst) } }
    _allStudentGoals.update { list -> list.filterNot { isSameInstitute(it.instituteId, activeInst) } }
    _allFees.update { list -> list.filterNot { isSameInstitute(it.instituteId, activeInst) } }
    _allFeePayments.update { list -> list.filterNot { isSameInstitute(it.instituteId, activeInst) } }
    _allAttendance.update { list -> list.filterNot { isSameInstitute(it.instituteId, activeInst) } }
    _allLeads.update { list -> list.filterNot { isSameInstitute(it.instituteId, activeInst) } }
    _allOnlineForms.update { list -> list.filterNot { isSameInstitute(it.instituteId, activeInst) } }
    _allQuestions.update { list -> list.filterNot { isSameInstitute(it.instituteId, activeInst) } }
    _allDoubts.update { list -> list.filterNot { isSameInstitute(it.instituteId, activeInst) } }
    _allHomework.update { list -> list.filterNot { isSameInstitute(it.instituteId, activeInst) } }
    _allTimetable.update { list -> list.filterNot { isSameInstitute(it.instituteId, activeInst) } }
    _allDynamicTimetable.update { list -> list.filterNot { isSameInstitute(it.instituteId, activeInst) } }
    _allStudyLogs.update { list -> list.filterNot { isSameInstitute(it.instituteId, activeInst) } }
    _allNotifications.update { list -> list.filterNot { isSameInstitute(it.instituteId, activeInst) } }
    _allMeetings.update { list -> list.filterNot { isSameInstitute(it.instituteId, activeInst) } }
    refreshScopedStateFlows()

    if (activeInst == "FSI_MAIN") {
      repoScope.launch {
        studentBatchRepo?.clearAllData()
      }
    }

    NetworkConnectivityManager.markDataUpdatedOnline()

    addNotification(
      NotificationItem(
        id = "NOT_CLR_${System.currentTimeMillis()}",
        title = "🧹 All System Data Purged",
        message = "All sample & legacy database records cleared for institute $activeInst.",
        time = getCurrentTimeStr(),
        type = "System",
        recipientRole = "All",
        instituteId = activeInst
      )
    )
    return true
  }

  fun initializeCleanInstituteStructure(): Boolean {
    if (!checkOnlineOrBlock("reset institute structure")) return false
    if (!requireAdminSession("initializeCleanInstituteStructure")) return false
    val activeInst = currentActiveInstituteId()
    val cleanBatches = listOf(
      Batch("BATCH_10_B", "Class 10 Board Target", "Mon - Sat (08:00 AM - 12:00 PM)", 0, "Faculty Team", instituteId = activeInst),
      Batch("BATCH_11_S", "11th Science Stream", "Mon - Sat (01:00 PM - 05:00 PM)", 0, "Science Faculty", instituteId = activeInst),
      Batch("BATCH_12_S", "12th Science Stream", "Mon - Sat (08:00 AM - 01:30 PM)", 0, "Senior Faculty", instituteId = activeInst),
      Batch("BATCH_JEE", "JEE Competitive Target", "Mon - Sat (07:00 AM - 02:00 PM)", 0, "IITian Mentors", instituteId = activeInst),
      Batch("BATCH_NEET", "NEET Medical Target", "Mon - Sat (08:00 AM - 02:00 PM)", 0, "Medical Faculty", instituteId = activeInst)
    )
    _allBatches.update { list -> list.filterNot { isSameInstitute(it.instituteId, activeInst) } + cleanBatches }
    _allStudents.update { list -> list.filterNot { isSameInstitute(it.instituteId, activeInst) } }
    _allTeachers.update { list -> list.filterNot { isSameInstitute(it.instituteId, activeInst) } }
    _allFees.update { list -> list.filterNot { isSameInstitute(it.instituteId, activeInst) } }
    refreshScopedStateFlows()

    if (activeInst == "FSI_MAIN") {
      repoScope.launch {
        studentBatchRepo?.clearAllData()
        cleanBatches.forEach { studentBatchRepo?.insertBatch(it) }
      }
    } else {
      repoScope.launch {
        cleanBatches.forEach { studentBatchRepo?.insertBatch(it) }
      }
    }
    NetworkConnectivityManager.markDataUpdatedOnline()
    return true
  }

  fun generateAndBulkImportRealUsers(count: Int = 1000): Boolean {
    if (!checkOnlineOrBlock("generate mock records")) return false
    if (!requireAdminSession("generateAndBulkImportRealUsers")) return false
    if (count <= 0) return false
    val activeInst = currentActiveInstituteId()
    val batchesList = listOf(
      Batch("BATCH_10_B", "Class 10 Board Target", "Mon - Sat (08:00 AM - 12:00 PM)", count / 4, "Faculty Team", instituteId = activeInst),
      Batch("BATCH_11_S", "11th Science Stream", "Mon - Sat (01:00 PM - 05:00 PM)", count / 4, "Science Faculty", instituteId = activeInst),
      Batch("BATCH_12_S", "12th Science Stream", "Mon - Sat (08:00 AM - 01:30 PM)", count / 4, "Senior Faculty", instituteId = activeInst),
      Batch("BATCH_JEE_APEX", "JEE Apex Competitive", "Mon - Sat (07:00 AM - 02:00 PM)", count / 4, "IITian Mentors", instituteId = activeInst)
    )

    _allBatches.update { list -> list.filterNot { isSameInstitute(it.instituteId, activeInst) } + batchesList }
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
        recentScores = listOf("Physics" to 82, "Chemistry" to 78, "Mathematics" to 88),
        instituteId = activeInst
      )
    }

    _allStudents.update { list -> list.filterNot { isSameInstitute(it.instituteId, activeInst) } + generatedStudents }

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
        batchName = stu.batch,
        batchId = stu.batchId,
        instituteId = activeInst
      )
    }
    _allFees.update { list -> list.filterNot { isSameInstitute(it.instituteId, activeInst) } + newFees }
    refreshScopedStateFlows()

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
        recipientRole = "All",
        instituteId = activeInst
      )
    )
    NetworkConnectivityManager.markDataUpdatedOnline()
    return true
  }

  fun bulkImportStudentsFromCsv(rawCsv: String): Boolean {
    if (!checkOnlineOrBlock("import CSV records")) return false
    if (!requireAdminSession("bulkImportStudentsFromCsv")) return false
    val activeInst = currentActiveInstituteId()
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
          recentScores = emptyList(),
          instituteId = activeInst
        )
        newStudents.add(s)
      }
    }

    if (newStudents.isNotEmpty()) {
      _allStudents.update { it + newStudents }
      syncBatchStudentCounts()
      refreshScopedStateFlows()
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
          recipientRole = "All",
          instituteId = activeInst
        )
      )
      return true
    }
    return false
  }
}
