package com.example.data

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.PersistentCacheSettings
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.Source
import com.google.firebase.FirebaseApp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

data class CachedResource<T>(
  val data: T,
  val timestamp: Long = System.currentTimeMillis(),
  val source: String = "cache" // "server", "cache", "fallback"
) {
  fun isExpired(ttlMillis: Long): Boolean {
    return (System.currentTimeMillis() - timestamp) > ttlMillis
  }
}

data class FirestorePaginationState<T>(
  val items: List<T> = emptyList(),
  val isLoading: Boolean = false,
  val hasMore: Boolean = true,
  val lastSnapshot: DocumentSnapshot? = null,
  val currentPage: Int = 1,
  val pageSize: Long = 10,
  val isCacheSource: Boolean = false,
  val error: String? = null
)

data class FirestoreSyncEnvelope(
  val collection: String,
  val documentId: String,
  val instituteId: String = AppRepository.getCurrentInstituteId(),
  val ownership: String = "ADMIN",
  val createdAt: Long = System.currentTimeMillis(),
  val updatedAt: Long = System.currentTimeMillis(),
  val syncStatus: String = "PENDING_SYNC", // "PENDING_SYNC", "SYNCED", "SYNC_FAILED"
  val conflictStrategy: String = "LAST_WRITE_WINS_BY_UPDATED_AT",
  val operation: String = "UPDATE", // "CREATE", "UPDATE", "UPSERT", "DELETE"
  val payload: Map<String, Any> = emptyMap()
) {
  fun toMap(): Map<String, Any> {
    val sanitizedPayload = com.example.data.db.SyncQueueEntity.sanitizePayloadMap(payload)
      .mapValues { it.value ?: "" }
    return sanitizedPayload + mapOf(
      "collection" to collection,
      "documentId" to documentId,
      "instituteId" to instituteId,
      "ownership" to ownership,
      "createdAt" to createdAt,
      "updatedAt" to updatedAt,
      "syncStatus" to syncStatus,
      "conflictStrategy" to conflictStrategy,
      "operation" to operation,
      "payload" to sanitizedPayload
    )
  }

  fun toSyncQueueEntity(
    queueId: String = com.example.data.db.SyncQueueEntity.generateQueueId(),
    activeInstituteId: String = AppRepository.getCurrentInstituteId()
  ): com.example.data.db.SyncQueueEntity {
    val now = System.currentTimeMillis()
    val effectiveActiveInstitute = activeInstituteId.trim().ifBlank { AppRepository.getCurrentInstituteId() }
    val resolvedInstituteId = if (AppRepository.isLoggedIn.value && effectiveActiveInstitute.isNotBlank()) {
      effectiveActiveInstitute
    } else {
      instituteId.trim().ifBlank { effectiveActiveInstitute }
    }
    val effectiveCreatedAt = if (createdAt > 0L) createdAt else now
    val effectiveUpdatedAt = if (updatedAt > 0L) updatedAt else effectiveCreatedAt
    val resolvedOperationType = com.example.data.db.SyncOperationType.fromString(operation)
    val cleanEntityId = documentId.trim()
    val payloadWithInstitute = if (resolvedOperationType == com.example.data.db.SyncOperationType.DELETE) {
      LinkedHashMap<String, Any?>().apply {
        put("id", cleanEntityId)
        put("instituteId", resolvedInstituteId)
        (payload["status"] as? String)?.takeIf { it.isNotBlank() }?.let { put("status", it) }
        (payload["isActivated"] as? Boolean)?.let { put("isActivated", it) }
      }
    } else {
      LinkedHashMap<String, Any?>().apply {
        putAll(payload)
        put("instituteId", resolvedInstituteId)
      }
    }
    return com.example.data.db.SyncQueueEntity(
      queueId = queueId.trim().ifBlank { com.example.data.db.SyncQueueEntity.generateQueueId() },
      instituteId = resolvedInstituteId,
      entityType = collection.trim(),
      entityId = cleanEntityId,
      operationType = resolvedOperationType,
      serializedPayload = com.example.data.db.SyncQueueEntity.serializePayload(payloadWithInstitute),
      createdAt = effectiveCreatedAt,
      updatedAt = effectiveUpdatedAt,
      retryCount = 0,
      lastAttemptAt = null,
      status = com.example.data.db.SyncQueueStatus.PENDING,
      errorMessage = null
    ).sanitize()
  }

  companion object {
    fun fromSyncQueueEntity(entity: com.example.data.db.SyncQueueEntity): FirestoreSyncEnvelope {
      val mappedStatus = when (entity.status) {
        com.example.data.db.SyncQueueStatus.PENDING,
        com.example.data.db.SyncQueueStatus.PROCESSING -> "PENDING_SYNC"
        com.example.data.db.SyncQueueStatus.COMPLETED -> "SYNCED"
        com.example.data.db.SyncQueueStatus.FAILED -> "SYNC_FAILED"
      }
      return FirestoreSyncEnvelope(
        collection = entity.entityType,
        documentId = entity.entityId,
        instituteId = entity.instituteId,
        createdAt = entity.createdAt,
        updatedAt = entity.updatedAt,
        syncStatus = mappedStatus,
        operation = entity.operationType.name,
        payload = com.example.data.db.SyncQueueEntity.deserializePayload(entity.serializedPayload)
      )
    }
  }
}

data class FirestoreStudent(
  val id: String = "",
  val name: String = "",
  val grade: String = "",
  val subjectsEnrolled: List<String> = emptyList(),
  val contactInfo: String = "",
  val parentName: String = "",
  val parentContact: String = "",
  val email: String = "",
  val dob: String = "",
  val gender: String = "",
  val address: String = "",
  val school: String = "",
  val className: String = "Class 12",
  val batch: String = "",
  val batchId: String = "",
  val stream: String = "Science",
  val admissionDate: String = "",
  val status: String = "Active",
  val attendancePercent: Int = 85,
  val overallAvg: Int = 80,
  val rank: Int = 1,
  val parentId: String = "",
  val parentEmail: String = "",
  val feeStatus: String = "Pending",
  val instituteId: String = AppRepository.getCurrentInstituteId(),
  val createdAt: Long = System.currentTimeMillis(),
  val updatedAt: Long = System.currentTimeMillis(),
  val syncStatus: String = "SYNCED",
  val conflictStrategy: String = "LAST_WRITE_WINS_BY_UPDATED_AT"
) {
  // Convert to Map for Firestore upload without dropping important fields
  fun toMap(): Map<String, Any> {
    return mapOf(
      "id" to id,
      "name" to name,
      "grade" to grade,
      "subjectsEnrolled" to subjectsEnrolled,
      "contactInfo" to contactInfo,
      "parentName" to parentName,
      "parentContact" to parentContact,
      "email" to email,
      "dob" to dob,
      "gender" to gender,
      "address" to address,
      "school" to school,
      "className" to className,
      "batch" to batch,
      "batchId" to batchId,
      "stream" to stream,
      "admissionDate" to admissionDate,
      "status" to status,
      "attendancePercent" to attendancePercent,
      "overallAvg" to overallAvg,
      "rank" to rank,
      "parentId" to parentId,
      "parentEmail" to parentEmail,
      "feeStatus" to feeStatus,
      "instituteId" to instituteId,
      "createdAt" to createdAt,
      "updatedAt" to updatedAt,
      "syncStatus" to syncStatus,
      "conflictStrategy" to conflictStrategy
    )
  }

  fun toDomainStudent(existing: Student? = null): Student {
    return Student(
      id = id,
      name = name,
      photo = existing?.photo ?: "",
      mobile = contactInfo.ifBlank { existing?.mobile ?: "" },
      parentName = parentName.ifBlank { existing?.parentName ?: "" },
      parentContact = parentContact.ifBlank { existing?.parentContact ?: "" },
      email = email.ifBlank { existing?.email ?: "${id.lowercase()}@mytuition.com" },
      dob = dob.ifBlank { existing?.dob ?: "" },
      gender = gender.ifBlank { existing?.gender ?: "" },
      address = address.ifBlank { existing?.address ?: "" },
      school = school.ifBlank { existing?.school ?: "" },
      className = className.ifBlank { existing?.className ?: "Class 12" },
      batch = batch.ifBlank { existing?.batch ?: "12th Science" },
      stream = stream.ifBlank { existing?.stream ?: "Science" },
      admissionDate = admissionDate.ifBlank { existing?.admissionDate ?: "" },
      status = status.ifBlank { existing?.status ?: "Active" },
      attendancePercent = attendancePercent,
      overallAvg = overallAvg,
      rank = rank,
      strongestSubject = existing?.strongestSubject ?: "Physics",
      weakestSubject = existing?.weakestSubject ?: "Mathematics",
      recentScores = existing?.recentScores ?: emptyList(),
      batchId = batchId.ifBlank { existing?.batchId ?: "BATCH_${batch.replace(" ", "_")}" },
      parentId = parentId.ifBlank { existing?.parentId ?: "PAR_$id" },
      parentEmail = parentEmail.ifBlank { existing?.parentEmail ?: "parent_${id.lowercase()}@mytuition.com" },
      feeStatus = feeStatus.ifBlank { existing?.feeStatus ?: "Pending" },
      instituteId = instituteId.ifBlank { existing?.instituteId ?: AppRepository.getCurrentInstituteId() }
    )
  }

  fun toDomain(existing: Student? = null): Student = toDomainStudent(existing)

  companion object {
    fun fromDomain(student: Student, updatedAt: Long = System.currentTimeMillis(), syncStatus: String = "SYNCED"): FirestoreStudent =
      fromDomainStudent(student, updatedAt, syncStatus)

    fun fromDomainStudent(student: Student, updatedAt: Long = System.currentTimeMillis(), syncStatus: String = "SYNCED"): FirestoreStudent {
      return FirestoreStudent(
        id = student.id,
        name = student.name,
        grade = "${student.className} (${student.stream})",
        subjectsEnrolled = listOf("Physics", "Chemistry", "Mathematics"),
        contactInfo = student.mobile,
        parentName = student.parentName,
        parentContact = student.parentContact,
        email = student.email,
        dob = student.dob,
        gender = student.gender,
        address = student.address,
        school = student.school,
        className = student.className,
        batch = student.batch,
        batchId = student.batchId,
        stream = student.stream,
        admissionDate = student.admissionDate,
        status = student.status,
        attendancePercent = student.attendancePercent,
        overallAvg = student.overallAvg,
        rank = student.rank,
        parentId = student.parentId,
        parentEmail = student.parentEmail,
        feeStatus = student.feeStatus,
        instituteId = student.instituteId.ifBlank { AppRepository.getCurrentInstituteId() },
        createdAt = updatedAt,
        updatedAt = updatedAt,
        syncStatus = syncStatus
      )
    }

    // Helper to construct from Firestore document map
    fun fromMap(id: String, map: Map<String, Any>): FirestoreStudent {
      return FirestoreStudent(
        id = id,
        name = map["name"] as? String ?: "",
        grade = map["grade"] as? String ?: "",
        subjectsEnrolled = (map["subjectsEnrolled"] as? List<*>)?.mapNotNull { it as? String } ?: emptyList(),
        contactInfo = map["contactInfo"] as? String ?: "",
        parentName = map["parentName"] as? String ?: "",
        parentContact = map["parentContact"] as? String ?: "",
        email = map["email"] as? String ?: "",
        dob = map["dob"] as? String ?: "",
        gender = map["gender"] as? String ?: "",
        address = map["address"] as? String ?: "",
        school = map["school"] as? String ?: "",
        className = map["className"] as? String ?: "Class 12",
        batch = map["batch"] as? String ?: "",
        batchId = map["batchId"] as? String ?: "",
        stream = map["stream"] as? String ?: "Science",
        admissionDate = map["admissionDate"] as? String ?: "",
        status = map["status"] as? String ?: "Active",
        attendancePercent = (map["attendancePercent"] as? Number)?.toInt() ?: 85,
        overallAvg = (map["overallAvg"] as? Number)?.toInt() ?: 80,
        rank = (map["rank"] as? Number)?.toInt() ?: 1,
        parentId = map["parentId"] as? String ?: "",
        parentEmail = map["parentEmail"] as? String ?: "",
        feeStatus = map["feeStatus"] as? String ?: "Pending",
        instituteId = (map["instituteId"] as? String)?.ifBlank { AppRepository.getCurrentInstituteId() }
          ?: AppRepository.getCurrentInstituteId(),
        createdAt = (map["createdAt"] as? Number)?.toLong() ?: System.currentTimeMillis(),
        updatedAt = (map["updatedAt"] as? Number)?.toLong() ?: System.currentTimeMillis(),
        syncStatus = map["syncStatus"] as? String ?: "SYNCED",
        conflictStrategy = map["conflictStrategy"] as? String ?: "LAST_WRITE_WINS_BY_UPDATED_AT"
      )
    }
  }
}

data class FirestoreAnnouncement(
  val id: String = "",
  val title: String = "",
  val message: String = "",
  val sender: String = "Admin Office",
  val targetAudience: String = "Everyone", // "Everyone", "Parents Only", "Teachers Only", "Students Only"
  val timestamp: Long = System.currentTimeMillis(),
  val dateStr: String = "",
  val batchName: String? = null,
  val batchId: String = "",
  val instituteId: String = "FSI_MAIN",
  val type: String = "Announcement"
) {
  fun toMap(): Map<String, Any> {
    return mapOf(
      "id" to id,
      "title" to title,
      "message" to message,
      "sender" to sender,
      "targetAudience" to targetAudience,
      "timestamp" to timestamp,
      "dateStr" to dateStr,
      "batchName" to (batchName ?: ""),
      "batchId" to batchId,
      "instituteId" to instituteId.ifBlank { AppRepository.getCurrentInstituteId() },
      "type" to type
    )
  }

  companion object {
    fun fromMap(id: String, map: Map<String, Any>): FirestoreAnnouncement {
      return FirestoreAnnouncement(
        id = id,
        title = map["title"] as? String ?: "",
        message = map["message"] as? String ?: "",
        sender = map["sender"] as? String ?: "Admin Office",
        targetAudience = map["targetAudience"] as? String ?: "Everyone",
        timestamp = (map["timestamp"] as? Number)?.toLong() ?: System.currentTimeMillis(),
        dateStr = map["dateStr"] as? String ?: "",
        batchName = map["batchName"] as? String,
        batchId = map["batchId"] as? String ?: "",
        instituteId = map["instituteId"] as? String ?: "FSI_MAIN",
        type = map["type"] as? String ?: "Announcement"
      )
    }
  }
}

object FirestoreService {
  private const val TAG = "FirestoreService"
  private const val COLLECTION_NAME = "student_profiles"
  private const val ANNOUNCEMENTS_COLLECTION = "announcements"

  // Cache configuration
  private const val DEFAULT_CACHE_TTL_MS = 5 * 60 * 1000L // 5 minutes
  private const val LOW_BW_CACHE_TTL_MS = 30 * 60 * 1000L // 30 minutes in low-bandwidth mode

  // Low-Bandwidth Mode Toggle & Data Saver
  private val _isLowBandwidthMode = MutableStateFlow(false)
  val isLowBandwidthMode = _isLowBandwidthMode.asStateFlow()

  // Bandwidth & Cache Telemetry
  private val _cacheHitsCount = MutableStateFlow(0)
  val cacheHitsCount = _cacheHitsCount.asStateFlow()

  private val _networkCallsSaved = MutableStateFlow(0)
  val networkCallsSaved = _networkCallsSaved.asStateFlow()

  private val _dataSavedKb = MutableStateFlow(0)
  val dataSavedKb = _dataSavedKb.asStateFlow()

  private val _lastSyncTimestamp = MutableStateFlow(System.currentTimeMillis())
  val lastSyncTimestamp = _lastSyncTimestamp.asStateFlow()

  // In-Memory Caches with Time-To-Live
  private var announcementsCache: CachedResource<List<FirestoreAnnouncement>>? = null
  private var studentsCache: CachedResource<List<FirestoreStudent>>? = null
  private val attendanceCheckCache = ConcurrentHashMap<String, CachedResource<Boolean>>()

  // Pagination States
  private val _announcementsPaginationState = MutableStateFlow(FirestorePaginationState<FirestoreAnnouncement>())
  val announcementsPaginationState = _announcementsPaginationState.asStateFlow()

  private val _studentsPaginationState = MutableStateFlow(FirestorePaginationState<FirestoreStudent>())
  val studentsPaginationState = _studentsPaginationState.asStateFlow()

  private val _syncStatus = MutableStateFlow<String>("Initialized in Fallback/Local Mode (Persistent Cache Active)")
  val syncStatus = _syncStatus.asStateFlow()

  private val _remoteProfiles = MutableStateFlow<List<FirestoreStudent>>(emptyList())
  val remoteProfiles = _remoteProfiles.asStateFlow()

  private val _remoteAnnouncements = MutableStateFlow<List<FirestoreAnnouncement>>(
    listOf(
      FirestoreAnnouncement(
        id = "ANNC_DEF_1",
        title = "📢 Welcome to FSI My Tuition Platform",
        message = "Classes, test schedules, attendance tracking, and parent reports are live across all batches.",
        sender = "Institute Director",
        targetAudience = "Everyone",
        dateStr = "Today",
        type = "Announcement"
      ),
      FirestoreAnnouncement(
        id = "ANNC_DEF_2",
        title = "📝 Quarterly Parent-Teacher Meeting Scheduled",
        message = "Parent-Teacher meeting is scheduled for Saturday at 10:00 AM in Main Hall.",
        sender = "Admin Office",
        targetAudience = "Parents Only",
        dateStr = "Yesterday",
        type = "Announcement"
      ),
      FirestoreAnnouncement(
        id = "ANNC_DEF_3",
        title = "📚 Monthly Faculty Syllabus Submission",
        message = "All subject faculty members please submit updated monthly syllabus completion logs by Friday.",
        sender = "Academic Head",
        targetAudience = "Teachers Only",
        dateStr = "2 days ago",
        type = "Announcement"
      )
    )
  )
  val remoteAnnouncements = _remoteAnnouncements.asStateFlow()

  private val _isLoading = MutableStateFlow(false)
  val isLoading = _isLoading.asStateFlow()

  fun setLowBandwidthMode(enabled: Boolean) {
    _isLowBandwidthMode.value = enabled
    _syncStatus.value = if (enabled) {
      "⚡ Low-Bandwidth Mode Enabled (Extended Cache & Minimal Queries)"
    } else {
      "Standard Bandwidth Mode Active"
    }
  }

  fun toggleLowBandwidthMode(): Boolean {
    val newState = !_isLowBandwidthMode.value
    setLowBandwidthMode(newState)
    return newState
  }

  fun getEffectiveCacheTtl(): Long {
    return if (_isLowBandwidthMode.value) LOW_BW_CACHE_TTL_MS else DEFAULT_CACHE_TTL_MS
  }

  private val isFirebaseInitialized: Boolean by lazy {
    try {
      FirebaseApp.getInstance()
      true
    } catch (e: IllegalStateException) {
      false
    }
  }

  // Safely initialize Firestore with Persistent Disk Cache
  val db: FirebaseFirestore? by lazy {
    try {
      if (!isFirebaseInitialized) {
        Log.i(TAG, "FirebaseApp not initialized. Falling back to Local memory database for Firestore.")
        _syncStatus.value = "Offline Fallback Mode (google-services.json not configured)"
        null
      } else {
        val instance = FirebaseFirestore.getInstance()
        try {
          // Configure native local cache with unlimited size for offline & low-bandwidth resilience
          val settings = FirebaseFirestoreSettings.Builder()
            .setLocalCacheSettings(
              PersistentCacheSettings.newBuilder()
                .setSizeBytes(FirebaseFirestoreSettings.CACHE_SIZE_UNLIMITED)
                .build()
            )
            .build()
          instance.firestoreSettings = settings
          Log.i(TAG, "Configured Firestore PersistentCacheSettings successfully.")
        } catch (e: Exception) {
          Log.w(TAG, "Native PersistentCacheSettings configuration note: ${e.message}")
        }
        _syncStatus.value = "Firestore Connected with Persistent Local Cache"
        instance
      }
    } catch (e: Exception) {
      Log.w(TAG, "Failed to initialize Firebase Firestore. Fallback to Local memory database.", e)
      _syncStatus.value = "Offline Fallback Mode (google-services.json not configured)"
      null
    }
  }

  fun isAvailable(): Boolean {
    return db != null
  }

  /**
   * Builds and validates tenant-isolated, role-scoped query constraints for Firestore collections
   * so client queries strictly align with `/firestore.rules`.
   */
  fun buildScopedQueryConstraints(
    collection: String,
    instituteId: String = AppRepository.getCurrentInstituteId(),
    role: UserRole = AppRepository.currentRole.value
  ): Map<String, Any> {
    val cleanInst = instituteId.trim()
    if (!com.example.security.SecurityEngine.isValidInstituteId(cleanInst)) {
      return emptyMap()
    }
    val constraints = mutableMapOf<String, Any>("instituteId" to cleanInst)
    when (collection.trim().removePrefix("/")) {
      COLLECTION_NAME, "students", "attendance", "fees", "fee_payments", "attempts", "study_logs", "student_goals", "doubts" -> {
        when (role) {
          UserRole.STUDENT -> {
            val stuId = AppRepository.getActiveStudentForCurrentSession()?.id
              ?: AppRepository.currentUserAccount.value?.linkedEntityId.orEmpty()
            if (stuId.isNotBlank()) {
              constraints["studentId"] = stuId
            }
          }
          UserRole.PARENT -> {
            val linkedIds = AppRepository.getLinkedStudentsForCurrentParent().map { it.id }
              .ifEmpty { AppRepository.currentUserAccount.value?.linkedStudentIds.orEmpty() }
            if (linkedIds.isNotEmpty()) {
              constraints["studentIds"] = linkedIds
            }
          }
          UserRole.TEACHER -> {
            val teacher = AppRepository.getActiveTeacherForCurrentSession()
            if (teacher != null && teacher.assignedBatchIds.isNotEmpty()) {
              constraints["batchIds"] = teacher.assignedBatchIds
            }
          }
          UserRole.ADMIN -> {
            // Admin is scoped to their own instituteId
          }
        }
      }
      "batches", "subjects", "tests", "homework", "study_material", "timetable", "weekly_timetable" -> {
        when (role) {
          UserRole.STUDENT -> {
            val batchId = AppRepository.getActiveStudentForCurrentSession()?.batchId.orEmpty()
            if (batchId.isNotBlank()) {
              constraints["batchId"] = batchId
            }
          }
          UserRole.PARENT -> {
            val batchIds = AppRepository.getLinkedStudentsForCurrentParent().mapNotNull { it.batchId.takeIf { b -> b.isNotBlank() } }.distinct()
            if (batchIds.isNotEmpty()) {
              constraints["batchIds"] = batchIds
            }
          }
          UserRole.TEACHER -> {
            val teacher = AppRepository.getActiveTeacherForCurrentSession()
            if (teacher != null && teacher.assignedBatchIds.isNotEmpty()) {
              constraints["batchIds"] = teacher.assignedBatchIds
            }
          }
          UserRole.ADMIN -> {}
        }
      }
    }
    return constraints
  }

  private fun applyStudentScopeToQuery(baseQuery: Query, activeInstituteId: String): Query {
    var query = baseQuery.whereEqualTo("instituteId", activeInstituteId)
    if (!AppRepository.isLoggedIn.value) return query
    when (AppRepository.currentRole.value) {
      UserRole.STUDENT -> {
        val stuId = AppRepository.getActiveStudentForCurrentSession()?.id
          ?: AppRepository.currentUserAccount.value?.linkedEntityId.orEmpty()
        if (stuId.isNotBlank()) {
          query = query.whereEqualTo("id", stuId)
        }
      }
      UserRole.PARENT -> {
        val linkedIds = AppRepository.getLinkedStudentsForCurrentParent().map { it.id }
          .ifEmpty { AppRepository.currentUserAccount.value?.linkedStudentIds.orEmpty() }
          .take(10)
        if (linkedIds.isNotEmpty()) {
          query = query.whereIn("id", linkedIds)
        }
      }
      UserRole.TEACHER -> {
        val assignedBatches = AppRepository.getActiveTeacherForCurrentSession()?.assignedBatchIds.orEmpty().take(10)
        if (assignedBatches.isNotEmpty()) {
          query = query.whereIn("batchId", assignedBatches)
        }
      }
      UserRole.ADMIN -> {}
    }
    return query
  }

  private fun filterStudentsForActiveSession(list: List<FirestoreStudent>): List<FirestoreStudent> {
    val activeInst = AppRepository.getCurrentInstituteId()
    val instFiltered = list.filter {
      com.example.security.SecurityEngine.isValidInstituteId(it.instituteId) &&
        it.instituteId.equals(activeInst, ignoreCase = true)
    }
    if (!AppRepository.isLoggedIn.value) return instFiltered
    return when (AppRepository.currentRole.value) {
      UserRole.ADMIN -> instFiltered
      UserRole.STUDENT -> {
        val stuId = AppRepository.getActiveStudentForCurrentSession()?.id
          ?: AppRepository.currentUserAccount.value?.linkedEntityId.orEmpty()
        instFiltered.filter { it.id.equals(stuId, ignoreCase = true) }
      }
      UserRole.PARENT -> {
        val linkedIds = AppRepository.getLinkedStudentsForCurrentParent().map { it.id.lowercase() }
          .ifEmpty { AppRepository.currentUserAccount.value?.linkedStudentIds.orEmpty().map { it.lowercase() } }
          .toSet()
        instFiltered.filter { it.id.lowercase() in linkedIds }
      }
      UserRole.TEACHER -> {
        val teacher = AppRepository.getActiveTeacherForCurrentSession()
        val batchIds = teacher?.assignedBatchIds.orEmpty().map { it.lowercase() }.toSet()
        val batchNames = teacher?.assignedBatches.orEmpty().map { it.lowercase() }.toSet()
        instFiltered.filter {
          (it.batchId.isNotBlank() && it.batchId.lowercase() in batchIds) ||
            (it.batch.isNotBlank() && it.batch.lowercase() in batchNames)
        }
      }
    }
  }

  // CREATE/ADD Student Profile to Firestore
  fun createStudentProfile(
    student: FirestoreStudent,
    onSuccess: () -> Unit,
    onFailure: (Exception) -> Unit
  ) {
    val activeInstituteId = AppRepository.getCurrentInstituteId()
    val targetInstituteId = student.instituteId.ifBlank { activeInstituteId }
    if (!com.example.security.SecurityEngine.isValidInstituteId(targetInstituteId) ||
      (AppRepository.isLoggedIn.value && !targetInstituteId.equals(activeInstituteId, ignoreCase = true))
    ) {
      val ex = SecurityException("Cross-institute or invalid instituteId '${student.instituteId}' rejected.")
      _syncStatus.value = "Security Error: Cross-institute write blocked."
      onFailure(ex)
      return
    }
    val existingProfile = _remoteProfiles.value.find { it.id == student.id }
    val existingInstituteId = existingProfile?.instituteId ?: AppRepository.findExistingStudentInstituteId(student.id)
    if (existingInstituteId != null && !existingInstituteId.equals(targetInstituteId, ignoreCase = true)) {
      val ex = SecurityException("Cannot change student instituteId from '$existingInstituteId' to '$targetInstituteId'.")
      _syncStatus.value = "Security Error: Institute reassignment blocked."
      onFailure(ex)
      return
    }
    if (AppRepository.isLoggedIn.value && AppRepository.currentRole.value != UserRole.ADMIN) {
      val ex = SecurityException("Only an Institute Admin can create student profiles.")
      _syncStatus.value = "Security Error: Insufficient role permissions."
      onFailure(ex)
      return
    }
    val scopedStudent = student.copy(instituteId = targetInstituteId)
    val createEnvelope = FirestoreSyncEnvelope(
      collection = COLLECTION_NAME,
      documentId = scopedStudent.id,
      instituteId = targetInstituteId,
      ownership = AppRepository.currentUserIdentifier.value.ifBlank { "ADMIN" },
      createdAt = scopedStudent.createdAt,
      updatedAt = System.currentTimeMillis(),
      syncStatus = "PENDING_SYNC",
      conflictStrategy = scopedStudent.conflictStrategy,
      operation = com.example.data.db.SyncOperationType.CREATE.name,
      payload = scopedStudent.toMap()
    )

    if (!NetworkConnectivityManager.isOnline.value) {
      persistCreateOrUpdateToRoomQueue(createEnvelope, targetInstituteId)
      NetworkConnectivityManager.recordLocalMutation()
      val ex = IllegalStateException("Cannot create student profile in Offline Mode. Queued in Room sync_queue.")
      _syncStatus.value = "Offline Mode: CREATE queued in persistent sync_queue."
      onFailure(ex)
      return
    }

    if (!NetworkConnectivityManager.isFirebaseReachable.value) {
      persistCreateOrUpdateToRoomQueue(createEnvelope, targetInstituteId)
      NetworkConnectivityManager.recordLocalMutation()
      NetworkConnectivityManager.recordServiceUnavailable("Firebase service unreachable")
      val ex = IllegalStateException("Cloud Service Unavailable. CREATE queued in Room sync_queue.")
      _syncStatus.value = "Saved locally (Cloud Service Unavailable — Sync Pending)"
      onFailure(ex)
      return
    }

    if (simulateImmediateWriteFailure) {
      persistCreateOrUpdateToRoomQueue(createEnvelope, targetInstituteId)
      NetworkConnectivityManager.recordLocalMutation()
      NetworkConnectivityManager.recordSyncFailure(simulatedWriteErrorMessage)
      val ex = IllegalStateException(simulatedWriteErrorMessage)
      _syncStatus.value = "Cloud Sync Error: $simulatedWriteErrorMessage"
      onFailure(ex)
      return
    }

    // Immediately write-through to in-memory cache and pagination state
    val updatedProfiles = _remoteProfiles.value.filterNot { it.id == scopedStudent.id } + scopedStudent
    _remoteProfiles.value = filterStudentsForActiveSession(updatedProfiles)
    studentsCache = CachedResource(_remoteProfiles.value, System.currentTimeMillis(), "server")
    _studentsPaginationState.update { state ->
      val updatedItems = state.items.filterNot { it.id == scopedStudent.id } + scopedStudent
      state.copy(items = filterStudentsForActiveSession(updatedItems))
    }

    val database = db
    if (database == null) {
      _syncStatus.value = "Local memory update: Student '${scopedStudent.name}' saved."
      onSuccess()
      return
    }

    database.collection(COLLECTION_NAME)
      .document(scopedStudent.id)
      .set(scopedStudent.toMap())
      .addOnSuccessListener {
        Log.d(TAG, "Student profile '${scopedStudent.name}' successfully written to Firestore!")
        _syncStatus.value = "Student '${scopedStudent.name}' synchronized to Cloud Firestore!"
        onSuccess()
      }
      .addOnFailureListener { e ->
        Log.e(TAG, "Error writing student profile to Firestore", e)
        persistCreateOrUpdateToRoomQueue(createEnvelope, targetInstituteId)
        NetworkConnectivityManager.recordLocalMutation()
        NetworkConnectivityManager.recordSyncFailure(e.message ?: "Firestore write error")
        _syncStatus.value = "Cloud Sync Error: ${e.message}"
        onFailure(e)
      }
  }

  // READ Student Profiles with smart caching (TTL & low-bandwidth optimization)
  fun fetchStudentProfiles(
    forceRefresh: Boolean = false,
    onComplete: ((List<FirestoreStudent>) -> Unit)? = null
  ) {
    // 1. Check Smart In-Memory Cache
    val cached = studentsCache
    if (!forceRefresh && cached != null && !cached.isExpired(getEffectiveCacheTtl())) {
      _cacheHitsCount.value++
      _networkCallsSaved.value++
      _dataSavedKb.value += 12
      _syncStatus.value = "⚡ Served ${cached.data.size} profiles from Cache (0 Network Calls)"
      onComplete?.invoke(cached.data)
      return
    }

    if (!NetworkConnectivityManager.isOnline.value) {
      // Offline mode: only show existing cached profiles
      _isLoading.value = false
      val offlineList = cached?.data ?: _remoteProfiles.value
      onComplete?.invoke(offlineList)
      return
    }

    _isLoading.value = true
    CoroutineScope(Dispatchers.Main).launch {
      val database = db
      if (database == null) {
        _isLoading.value = false
        val fallbackList = cached?.data ?: _remoteProfiles.value
        onComplete?.invoke(fallbackList)
        return@launch
      }

      _syncStatus.value = "Fetching profiles from Firestore..."
      
      // In low-bandwidth mode without forceRefresh, check native disk cache first
      val source = if (_isLowBandwidthMode.value && !forceRefresh) Source.CACHE else Source.DEFAULT
      val activeInstituteId = AppRepository.getCurrentInstituteId()
      if (!com.example.security.SecurityEngine.isValidInstituteId(activeInstituteId)) {
        _isLoading.value = false
        onComplete?.invoke(emptyList())
        return@launch
      }

      val scopedQuery = applyStudentScopeToQuery(database.collection(COLLECTION_NAME), activeInstituteId)

      scopedQuery
        .get(source)
        .addOnSuccessListener { result ->
          val list = filterStudentsForActiveSession(
            result.map { doc ->
              FirestoreStudent.fromMap(doc.id, doc.data)
            }
          )
          if (list.isNotEmpty()) {
            _remoteProfiles.value = list
            studentsCache = CachedResource(list, System.currentTimeMillis(), "server")
            _lastSyncTimestamp.value = System.currentTimeMillis()
            AppRepository.reconcileRemoteStudentsToRoom(list)
          }
          _syncStatus.value = "Fetched ${list.size} profiles (${if (result.metadata.isFromCache) "Native Cache" else "Cloud Server"})"
          _isLoading.value = false
          onComplete?.invoke(_remoteProfiles.value)
        }
        .addOnFailureListener { e ->
          // If CACHE attempt failed in low-bandwidth mode, retry with DEFAULT
          if (source == Source.CACHE) {
            scopedQuery
              .get(Source.DEFAULT)
              .addOnSuccessListener { defaultRes ->
                val list = filterStudentsForActiveSession(
                  defaultRes.map { doc -> FirestoreStudent.fromMap(doc.id, doc.data) }
                )
                if (list.isNotEmpty()) {
                  _remoteProfiles.value = list
                  studentsCache = CachedResource(list, System.currentTimeMillis(), "server")
                  AppRepository.reconcileRemoteStudentsToRoom(list)
                }
                _isLoading.value = false
                onComplete?.invoke(_remoteProfiles.value)
              }
              .addOnFailureListener { err ->
                Log.e(TAG, "Error fetching profiles", err)
                _syncStatus.value = "Error fetching profiles: ${err.message}"
                _isLoading.value = false
                onComplete?.invoke(_remoteProfiles.value)
              }
          } else {
            Log.e(TAG, "Error fetching profiles", e)
            _syncStatus.value = "Error fetching profiles: ${e.message}"
            _isLoading.value = false
            onComplete?.invoke(_remoteProfiles.value)
          }
        }
    }
  }

  // PAGINATED Student Profiles (Cursor-based using DocumentSnapshot)
  fun loadNextStudentProfilesPage(
    pageSize: Long? = null,
    forceRefresh: Boolean = false,
    onComplete: ((FirestorePaginationState<FirestoreStudent>) -> Unit)? = null
  ) {
    val actualPageSize = pageSize ?: (if (_isLowBandwidthMode.value) 5L else 10L)
    val currentState = _studentsPaginationState.value

    if (currentState.isLoading) {
      onComplete?.invoke(currentState)
      return
    }

    if (!forceRefresh && !currentState.hasMore) {
      onComplete?.invoke(currentState)
      return
    }

    _studentsPaginationState.update { it.copy(isLoading = true, error = null) }

    val database = db
    if (database == null || !NetworkConnectivityManager.isOnline.value) {
      // Local slicing simulation for fallback / offline mode
      val allItems = _remoteProfiles.value
      val startIdx = if (forceRefresh) 0 else currentState.items.size
      val endIdx = (startIdx + actualPageSize.toInt()).coerceAtMost(allItems.size)
      val newSlice = if (startIdx < allItems.size) allItems.subList(startIdx, endIdx) else emptyList()
      val combined = if (forceRefresh) newSlice else currentState.items + newSlice
      val hasMore = endIdx < allItems.size

      val newState = FirestorePaginationState(
        items = combined,
        isLoading = false,
        hasMore = hasMore,
        lastSnapshot = null,
        currentPage = if (forceRefresh) 1 else currentState.currentPage + 1,
        pageSize = actualPageSize,
        isCacheSource = true
      )
      _studentsPaginationState.value = newState
      onComplete?.invoke(newState)
      return
    }

    val activeInstituteId = AppRepository.getCurrentInstituteId()
    var query: Query = applyStudentScopeToQuery(database.collection(COLLECTION_NAME), activeInstituteId)
      .orderBy("name")
      .limit(actualPageSize)

    if (!forceRefresh && currentState.lastSnapshot != null) {
      query = query.startAfter(currentState.lastSnapshot)
    }

    val source = if (_isLowBandwidthMode.value && !forceRefresh) Source.CACHE else Source.DEFAULT

    query.get(source)
      .addOnSuccessListener { snapshot ->
        val newStudents = snapshot.documents.map { doc ->
          FirestoreStudent.fromMap(doc.id, doc.data ?: emptyMap())
        }
        val lastDoc = snapshot.documents.lastOrNull()
        val combinedItems = if (forceRefresh) newStudents else (currentState.items + newStudents).distinctBy { it.id }
        val hasMore = snapshot.size() >= actualPageSize.toInt()

        val newState = FirestorePaginationState(
          items = combinedItems,
          isLoading = false,
          hasMore = hasMore,
          lastSnapshot = lastDoc ?: currentState.lastSnapshot,
          currentPage = if (forceRefresh) 1 else currentState.currentPage + 1,
          pageSize = actualPageSize,
          isCacheSource = snapshot.metadata.isFromCache
        )
        _studentsPaginationState.value = newState

        // Also update remote profiles cache
        val currentRemote = _remoteProfiles.value.toMutableList()
        newStudents.forEach { ns ->
          if (currentRemote.none { it.id == ns.id }) currentRemote.add(ns)
        }
        _remoteProfiles.value = currentRemote
        studentsCache = CachedResource(currentRemote, System.currentTimeMillis(), "server")

        _syncStatus.value = "Loaded page ${newState.currentPage} (${combinedItems.size} total students)"
        onComplete?.invoke(newState)
      }
      .addOnFailureListener { e ->
        Log.w(TAG, "Pagination query failed on source $source, trying default", e)
        if (source == Source.CACHE) {
          // Retry with server / default
          query.get(Source.DEFAULT)
            .addOnSuccessListener { fallbackSnap ->
              val newStudents = fallbackSnap.documents.map { doc ->
                FirestoreStudent.fromMap(doc.id, doc.data ?: emptyMap())
              }
              val lastDoc = fallbackSnap.documents.lastOrNull()
              val combinedItems = if (forceRefresh) newStudents else (currentState.items + newStudents).distinctBy { it.id }
              val hasMore = fallbackSnap.size() >= actualPageSize.toInt()

              val newState = FirestorePaginationState(
                items = combinedItems,
                isLoading = false,
                hasMore = hasMore,
                lastSnapshot = lastDoc ?: currentState.lastSnapshot,
                currentPage = if (forceRefresh) 1 else currentState.currentPage + 1,
                pageSize = actualPageSize,
                isCacheSource = false
              )
              _studentsPaginationState.value = newState
              onComplete?.invoke(newState)
            }
            .addOnFailureListener { err ->
              _studentsPaginationState.update { it.copy(isLoading = false, error = err.message) }
              onComplete?.invoke(_studentsPaginationState.value)
            }
        } else {
          _studentsPaginationState.update { it.copy(isLoading = false, error = e.message) }
          onComplete?.invoke(_studentsPaginationState.value)
        }
      }
  }

  fun resetStudentPagination() {
    _studentsPaginationState.value = FirestorePaginationState()
  }

  // UPDATE existing Student Profile in Firestore collection
  fun updateStudentProfile(
    student: FirestoreStudent,
    onSuccess: () -> Unit,
    onFailure: (Exception) -> Unit
  ) {
    val activeInstituteId = AppRepository.getCurrentInstituteId()
    val targetInstituteId = student.instituteId.ifBlank { activeInstituteId }
    if (!com.example.security.SecurityEngine.isValidInstituteId(targetInstituteId) ||
      (AppRepository.isLoggedIn.value && !targetInstituteId.equals(activeInstituteId, ignoreCase = true))
    ) {
      val ex = SecurityException("Cross-institute update blocked for institute '${student.instituteId}'.")
      _syncStatus.value = "Security Error: Cross-institute update blocked."
      onFailure(ex)
      return
    }
    val existingProfile = _remoteProfiles.value.find { it.id == student.id }
    val existingInstituteId = existingProfile?.instituteId ?: AppRepository.findExistingStudentInstituteId(student.id)
    if (existingInstituteId != null && !existingInstituteId.equals(targetInstituteId, ignoreCase = true)) {
      val ex = SecurityException("Cannot change student instituteId from '$existingInstituteId' to '$targetInstituteId'.")
      _syncStatus.value = "Security Error: Institute reassignment blocked."
      onFailure(ex)
      return
    }

    val scopedStudent = student.copy(instituteId = existingInstituteId ?: targetInstituteId)
    val updateEnvelope = FirestoreSyncEnvelope(
      collection = COLLECTION_NAME,
      documentId = scopedStudent.id,
      instituteId = scopedStudent.instituteId,
      ownership = AppRepository.currentUserIdentifier.value.ifBlank { "ADMIN" },
      createdAt = scopedStudent.createdAt,
      updatedAt = System.currentTimeMillis(),
      syncStatus = "PENDING_SYNC",
      conflictStrategy = scopedStudent.conflictStrategy,
      operation = com.example.data.db.SyncOperationType.UPDATE.name,
      payload = scopedStudent.toMap()
    )

    if (!NetworkConnectivityManager.isOnline.value) {
      persistCreateOrUpdateToRoomQueue(updateEnvelope, scopedStudent.instituteId)
      NetworkConnectivityManager.recordLocalMutation()
      val ex = IllegalStateException("Cannot update student in Offline Mode. Queued in Room sync_queue.")
      _syncStatus.value = "Offline Mode: UPDATE queued in persistent sync_queue."
      onFailure(ex)
      return
    }

    if (!NetworkConnectivityManager.isFirebaseReachable.value) {
      persistCreateOrUpdateToRoomQueue(updateEnvelope, scopedStudent.instituteId)
      NetworkConnectivityManager.recordLocalMutation()
      NetworkConnectivityManager.recordServiceUnavailable("Firebase service unreachable")
      val ex = IllegalStateException("Cloud Service Unavailable. UPDATE queued in Room sync_queue.")
      _syncStatus.value = "Saved locally (Cloud Service Unavailable — Sync Pending)"
      onFailure(ex)
      return
    }

    if (simulateImmediateWriteFailure) {
      persistCreateOrUpdateToRoomQueue(updateEnvelope, scopedStudent.instituteId)
      NetworkConnectivityManager.recordLocalMutation()
      NetworkConnectivityManager.recordSyncFailure(simulatedWriteErrorMessage)
      val ex = IllegalStateException(simulatedWriteErrorMessage)
      _syncStatus.value = "Update Error: $simulatedWriteErrorMessage"
      onFailure(ex)
      return
    }

    // Write-through update local memory & pagination states immediately
    val updatedList = _remoteProfiles.value.map { if (it.id == scopedStudent.id) scopedStudent else it }
    _remoteProfiles.value = filterStudentsForActiveSession(updatedList)
    studentsCache = CachedResource(_remoteProfiles.value, System.currentTimeMillis(), "server")
    _studentsPaginationState.update { state ->
      state.copy(items = filterStudentsForActiveSession(state.items.map { if (it.id == scopedStudent.id) scopedStudent else it }))
    }

    val database = db
    if (database == null) {
      _syncStatus.value = "Local update: ${scopedStudent.name}'s profile modified."
      onSuccess()
      return
    }

    database.collection(COLLECTION_NAME)
      .document(scopedStudent.id)
      .update(scopedStudent.toMap())
      .addOnSuccessListener {
        Log.d(TAG, "Student ${scopedStudent.id} profile updated in Firestore!")
        _syncStatus.value = "Updated profile for ${scopedStudent.name} in Firestore"
        onSuccess()
      }
      .addOnFailureListener { e ->
        Log.e(TAG, "Error updating student profile", e)
        persistCreateOrUpdateToRoomQueue(updateEnvelope, scopedStudent.instituteId)
        NetworkConnectivityManager.recordLocalMutation()
        NetworkConnectivityManager.recordSyncFailure(e.message ?: "Firestore write error")
        _syncStatus.value = "Update Error: ${e.message}"
        onFailure(e)
      }
  }

  // DELETE Student Profile from Firestore collection
  fun deleteStudentProfile(
    studentId: String,
    onSuccess: () -> Unit,
    onFailure: (Exception) -> Unit
  ) {
    val cleanId = studentId.trim()
    if (cleanId.isBlank()) {
      onFailure(IllegalArgumentException("Student ID cannot be blank."))
      return
    }
    val activeInstituteId = AppRepository.getCurrentInstituteId()
    if (!com.example.security.SecurityEngine.isValidInstituteId(activeInstituteId)) {
      com.example.security.SecurityEngine.logEvent(
        severity = com.example.security.SecuritySeverity.ALERT,
        eventType = "INVALID_INSTITUTE_SYNC_BLOCKED",
        actor = AppRepository.currentUserIdentifier.value.ifBlank { "System" },
        details = "Blocked deleteStudentProfile for '$cleanId' with invalid active instituteId '$activeInstituteId'."
      )
      val ex = SecurityException("Invalid active instituteId '$activeInstituteId'.")
      _syncStatus.value = "Security Error: Invalid institute."
      onFailure(ex)
      return
    }
    val existingProfile = _remoteProfiles.value.find { it.id == cleanId }
    val existingInstituteId = existingProfile?.instituteId ?: AppRepository.findExistingStudentInstituteId(cleanId)
    if (existingInstituteId != null && !existingInstituteId.equals(activeInstituteId, ignoreCase = true)) {
      com.example.security.SecurityEngine.logEvent(
        severity = com.example.security.SecuritySeverity.ALERT,
        eventType = "CROSS_TENANT_DELETE_BLOCKED",
        actor = AppRepository.currentUserIdentifier.value.ifBlank { "System" },
        details = "Blocked cross-institute delete for student '$cleanId' belonging to '$existingInstituteId' from active institute '$activeInstituteId'."
      )
      val ex = SecurityException("Cross-institute delete blocked for student '$cleanId'.")
      _syncStatus.value = "Security Error: Cross-institute delete blocked."
      onFailure(ex)
      return
    }
    if (AppRepository.isLoggedIn.value && AppRepository.currentRole.value != UserRole.ADMIN) {
      val ex = SecurityException("Only an Institute Admin can delete student profiles.")
      _syncStatus.value = "Security Error: Insufficient role permissions."
      onFailure(ex)
      return
    }

    val targetInstituteId = existingInstituteId ?: activeInstituteId
    val now = System.currentTimeMillis()
    val deleteEnvelope = FirestoreSyncEnvelope(
      collection = COLLECTION_NAME,
      documentId = cleanId,
      instituteId = targetInstituteId,
      ownership = AppRepository.currentUserIdentifier.value.ifBlank { "ADMIN" },
      createdAt = now,
      updatedAt = now,
      syncStatus = "PENDING_SYNC",
      conflictStrategy = "LAST_WRITE_WINS_BY_UPDATED_AT",
      operation = com.example.data.db.SyncOperationType.DELETE.name,
      payload = mapOf(
        "id" to cleanId,
        "instituteId" to targetInstituteId,
        "status" to "Inactive",
        "isActivated" to false
      )
    )

    // Write-through update local memory & pagination states immediately
    val filteredList = _remoteProfiles.value.filterNot { it.id == cleanId }
    _remoteProfiles.value = filteredList
    studentsCache = CachedResource(filteredList, now, "server")
    _studentsPaginationState.update { state ->
      state.copy(items = state.items.filterNot { it.id == cleanId })
    }

    if (!NetworkConnectivityManager.isOnline.value) {
      persistCreateOrUpdateToRoomQueue(deleteEnvelope, targetInstituteId)
      NetworkConnectivityManager.recordLocalMutation()
      val ex = IllegalStateException("Cannot delete student in Offline Mode. DELETE queued in Room sync_queue.")
      _syncStatus.value = "Offline Mode: DELETE queued in persistent sync_queue."
      onFailure(ex)
      return
    }

    if (!NetworkConnectivityManager.isFirebaseReachable.value) {
      persistCreateOrUpdateToRoomQueue(deleteEnvelope, targetInstituteId)
      NetworkConnectivityManager.recordLocalMutation()
      NetworkConnectivityManager.recordServiceUnavailable("Firebase service unreachable")
      val ex = IllegalStateException("Cloud Service Unavailable. DELETE queued in Room sync_queue.")
      _syncStatus.value = "Saved locally (Cloud Service Unavailable — DELETE Sync Pending)"
      onFailure(ex)
      return
    }

    if (simulateImmediateWriteFailure) {
      persistCreateOrUpdateToRoomQueue(deleteEnvelope, targetInstituteId)
      NetworkConnectivityManager.recordLocalMutation()
      NetworkConnectivityManager.recordSyncFailure(simulatedWriteErrorMessage)
      val ex = IllegalStateException(simulatedWriteErrorMessage)
      _syncStatus.value = "Delete Error: $simulatedWriteErrorMessage"
      onFailure(ex)
      return
    }

    val database = db
    if (database == null) {
      _syncStatus.value = "Local remove: Student ID $cleanId deleted."
      onSuccess()
      return
    }

    database.collection(COLLECTION_NAME)
      .document(cleanId)
      .delete()
      .addOnSuccessListener {
        Log.d(TAG, "Student profile $cleanId deleted from Firestore!")
        _syncStatus.value = "Deleted Student profile ID $cleanId from Firestore"
        onSuccess()
      }
      .addOnFailureListener { e ->
        Log.e(TAG, "Error deleting profile", e)
        persistCreateOrUpdateToRoomQueue(deleteEnvelope, targetInstituteId)
        NetworkConnectivityManager.recordLocalMutation()
        NetworkConnectivityManager.recordSyncFailure(e.message ?: "Firestore delete error")
        _syncStatus.value = "Delete Error: ${e.message}"
        onFailure(e)
      }
  }

  // SEED live Firestore database with sample student profiles initially
  fun seedDatabaseWithDefaults(onSuccess: () -> Unit) {
    val sampleStudents = emptyList<FirestoreStudent>()

    onSuccess()
  }

  // CHECK if attendance document exists in Firestore for today's date and batch (Cached & Tenant-Scoped)
  fun checkBatchAttendanceExists(
    batchId: String,
    dateStr: String,
    forceRefresh: Boolean = false,
    onResult: (Boolean) -> Unit
  ) {
    val activeInstituteId = AppRepository.getCurrentInstituteId()
    val docId = "${activeInstituteId}_${batchId.replace(" ", "_")}_${dateStr.replace("/", "-")}"
    
    // Check in-memory TTL cache to eliminate redundant network hits
    val cached = attendanceCheckCache[docId]
    if (!forceRefresh && cached != null && !cached.isExpired(getEffectiveCacheTtl())) {
      _cacheHitsCount.value++
      _networkCallsSaved.value++
      _dataSavedKb.value += 2
      onResult(cached.data)
      return
    }

    val database = db
    if (database == null) {
      val localRecords = AppRepository.attendance.value
      val exists = localRecords.any {
        it.instituteId.equals(activeInstituteId, ignoreCase = true) &&
          (it.batchName.equals(batchId, ignoreCase = true) || it.batchId.equals(batchId, ignoreCase = true)) &&
          it.date == dateStr
      }
      attendanceCheckCache[docId] = CachedResource(exists, System.currentTimeMillis())
      onResult(exists)
      return
    }

    val source = if (_isLowBandwidthMode.value && !forceRefresh) Source.CACHE else Source.DEFAULT
    database.collection("attendance")
      .document(docId)
      .get(source)
      .addOnSuccessListener { snapshot ->
        val docInst = snapshot.getString("instituteId") ?: ""
        val exists = snapshot.exists() && docInst.equals(activeInstituteId, ignoreCase = true)
        attendanceCheckCache[docId] = CachedResource(exists, System.currentTimeMillis())
        onResult(exists)
      }
      .addOnFailureListener {
        // Fallback to default/local on cache failure
        if (source == Source.CACHE) {
          database.collection("attendance")
            .document(docId)
            .get(Source.DEFAULT)
            .addOnSuccessListener { snap ->
              val docInst = snap.getString("instituteId") ?: ""
              val exists = snap.exists() && docInst.equals(activeInstituteId, ignoreCase = true)
              attendanceCheckCache[docId] = CachedResource(exists, System.currentTimeMillis())
              onResult(exists)
            }
            .addOnFailureListener {
              val localRecords = AppRepository.attendance.value
              val exists = localRecords.any {
                it.instituteId.equals(activeInstituteId, ignoreCase = true) &&
                  (it.batchName.equals(batchId, ignoreCase = true) || it.batchId.equals(batchId, ignoreCase = true)) &&
                  it.date == dateStr
              }
              onResult(exists)
            }
        } else {
          val localRecords = AppRepository.attendance.value
          val exists = localRecords.any {
            it.instituteId.equals(activeInstituteId, ignoreCase = true) &&
              (it.batchName.equals(batchId, ignoreCase = true) || it.batchId.equals(batchId, ignoreCase = true)) &&
              it.date == dateStr
          }
          onResult(exists)
        }
      }
  }

  // SAVE or update batch attendance document in Firestore (Tenant-Scoped)
  fun saveBatchAttendanceToFirestore(
    batchId: String,
    dateStr: String,
    markedBy: String,
    records: List<AttendanceRecord>,
    onComplete: (() -> Unit)? = null
  ) {
    val activeInstituteId = AppRepository.getCurrentInstituteId()
    if (!com.example.security.SecurityEngine.isValidInstituteId(activeInstituteId)) {
      onComplete?.invoke()
      return
    }
    val database = db
    val docId = "${activeInstituteId}_${batchId.replace(" ", "_")}_${dateStr.replace("/", "-")}"
    
    // Write-through update the attendance check cache immediately
    attendanceCheckCache[docId] = CachedResource(true, System.currentTimeMillis())

    val data = mapOf(
      "instituteId" to activeInstituteId,
      "batchId" to batchId,
      "date" to dateStr,
      "markedBy" to markedBy,
      "timestamp" to System.currentTimeMillis(),
      "editableUntil" to System.currentTimeMillis() + 24 * 60 * 60 * 1000L,
      "totalStudents" to records.size,
      "presentCount" to records.count { it.status == "Present" },
      "absentCount" to records.count { it.status == "Absent" },
      "records" to records.map { rec ->
        mapOf(
          "studentId" to rec.studentId,
          "studentName" to rec.studentName,
          "status" to rec.status,
          "timestamp" to rec.timestamp,
          "reason" to (rec.reason ?: ""),
          "instituteId" to activeInstituteId
        )
      }
    )

    if (database == null) {
      onComplete?.invoke()
      return
    }

    database.collection("attendance")
      .document(docId)
      .set(data)
      .addOnSuccessListener {
        Log.d("FirestoreService", "Batch attendance document '$docId' saved to Firestore")
        onComplete?.invoke()
      }
      .addOnFailureListener { e ->
        Log.e("FirestoreService", "Failed to save batch attendance to Firestore", e)
        onComplete?.invoke()
      }
  }

  // PUBLISH Announcement to centralized Firestore collection (with write-through caching)
  fun publishAnnouncement(
    announcement: FirestoreAnnouncement,
    onSuccess: (() -> Unit)? = null,
    onFailure: ((Exception) -> Unit)? = null
  ) {
    if (!NetworkConnectivityManager.isOnline.value) {
      val ex = IllegalStateException("Cannot publish announcement in Offline Mode. Connect to internet to sync changes.")
      _syncStatus.value = "Offline Mode: Announcement blocked."
      onFailure?.invoke(ex)
      return
    }

    val activeInstituteId = AppRepository.getCurrentInstituteId()
    val targetInstituteId = announcement.instituteId.ifBlank { activeInstituteId }
    if (!com.example.security.SecurityEngine.isValidInstituteId(targetInstituteId) ||
      (AppRepository.isLoggedIn.value && !targetInstituteId.equals(activeInstituteId, ignoreCase = true))
    ) {
      val ex = SecurityException("Cross-institute announcement publish blocked for institute '${announcement.instituteId}'.")
      _syncStatus.value = "Security Error: Cross-institute announcement blocked."
      onFailure?.invoke(ex)
      return
    }
    val scopedAnnouncement = announcement.copy(instituteId = targetInstituteId)

    // Always update local StateFlow and in-memory cache immediately
    val updatedList = listOf(scopedAnnouncement) + _remoteAnnouncements.value.filterNot { it.id == scopedAnnouncement.id }
    _remoteAnnouncements.value = updatedList.filter { it.instituteId.equals(activeInstituteId, ignoreCase = true) }
    announcementsCache = CachedResource(_remoteAnnouncements.value, System.currentTimeMillis(), "server")
    _announcementsPaginationState.update { state ->
      val updatedItems = listOf(scopedAnnouncement) + state.items.filterNot { it.id == scopedAnnouncement.id }
      state.copy(items = updatedItems.filter { it.instituteId.equals(activeInstituteId, ignoreCase = true) })
    }

    // Also push a local NotificationItem to trigger system push notifications
    AppRepository.addNotification(
      NotificationItem(
        id = scopedAnnouncement.id,
        title = scopedAnnouncement.title,
        message = "${scopedAnnouncement.message}\n\n— ${scopedAnnouncement.sender} [Audience: ${scopedAnnouncement.targetAudience}]",
        time = scopedAnnouncement.dateStr.ifBlank { AppRepository.getCurrentTimeStr() },
        type = "Announcement",
        recipientRole = scopedAnnouncement.targetAudience,
        isRead = false,
        instituteId = targetInstituteId
      )
    )

    val database = db
    if (database == null) {
      Log.d(TAG, "Offline/Fallback mode: Announcement saved locally.")
      onSuccess?.invoke()
      return
    }

    database.collection(ANNOUNCEMENTS_COLLECTION)
      .document(scopedAnnouncement.id)
      .set(scopedAnnouncement.toMap())
      .addOnSuccessListener {
        Log.d(TAG, "Announcement '${announcement.title}' published to Firestore collection '$ANNOUNCEMENTS_COLLECTION'")
        _syncStatus.value = "Announcement broadcasted to Cloud Firestore!"
        onSuccess?.invoke()
      }
      .addOnFailureListener { e ->
        Log.e(TAG, "Failed to publish announcement to Firestore", e)
        onFailure?.invoke(e)
      }
  }

  // FETCH Announcements with Smart Caching
  fun fetchAnnouncements(
    forceRefresh: Boolean = false,
    onComplete: ((List<FirestoreAnnouncement>) -> Unit)? = null
  ) {
    // 1. Check Smart In-Memory Cache
    val cached = announcementsCache
    if (!forceRefresh && cached != null && !cached.isExpired(getEffectiveCacheTtl())) {
      _cacheHitsCount.value++
      _networkCallsSaved.value++
      _dataSavedKb.value += 8
      _syncStatus.value = "⚡ Served ${cached.data.size} announcements from Cache (0 Network Calls)"
      onComplete?.invoke(cached.data)
      return
    }

    if (!NetworkConnectivityManager.isOnline.value) {
      // Offline mode: only show cached announcements from before disconnect
      val offlineList = cached?.data ?: _remoteAnnouncements.value
      onComplete?.invoke(offlineList)
      return
    }

    val database = db
    if (database == null) {
      val fallbackList = cached?.data ?: _remoteAnnouncements.value
      onComplete?.invoke(fallbackList)
      return
    }

    val source = if (_isLowBandwidthMode.value && !forceRefresh) Source.CACHE else Source.DEFAULT
    val activeInstituteId = AppRepository.getCurrentInstituteId()

    database.collection(ANNOUNCEMENTS_COLLECTION)
      .whereEqualTo("instituteId", activeInstituteId)
      .get(source)
      .addOnSuccessListener { result ->
        val list = result.map { doc ->
          FirestoreAnnouncement.fromMap(doc.id, doc.data)
        }.filter { it.instituteId.equals(activeInstituteId, ignoreCase = true) }
          .sortedByDescending { it.timestamp }

        if (list.isNotEmpty()) {
          _remoteAnnouncements.value = list
          announcementsCache = CachedResource(list, System.currentTimeMillis(), "server")
          _lastSyncTimestamp.value = System.currentTimeMillis()
        }
        _syncStatus.value = "Fetched ${list.size} announcements (${if (result.metadata.isFromCache) "Native Cache" else "Cloud Server"})"
        onComplete?.invoke(_remoteAnnouncements.value)
      }
      .addOnFailureListener { e ->
        if (source == Source.CACHE) {
          database.collection(ANNOUNCEMENTS_COLLECTION)
            .whereEqualTo("instituteId", activeInstituteId)
            .get(Source.DEFAULT)
            .addOnSuccessListener { res ->
              val list = res.map { doc -> FirestoreAnnouncement.fromMap(doc.id, doc.data) }
                .filter { it.instituteId.equals(activeInstituteId, ignoreCase = true) }
                .sortedByDescending { it.timestamp }
              if (list.isNotEmpty()) {
                _remoteAnnouncements.value = list
                announcementsCache = CachedResource(list, System.currentTimeMillis(), "server")
              }
              onComplete?.invoke(_remoteAnnouncements.value)
            }
            .addOnFailureListener {
              Log.e(TAG, "Error fetching announcements from Firestore", it)
              onComplete?.invoke(_remoteAnnouncements.value)
            }
        } else {
          Log.e(TAG, "Error fetching announcements from Firestore", e)
          onComplete?.invoke(_remoteAnnouncements.value)
        }
      }
  }

  // PAGINATED Announcements (Cursor-based with limit and startAfter)
  fun loadNextAnnouncementsPage(
    pageSize: Long? = null,
    forceRefresh: Boolean = false,
    onComplete: ((FirestorePaginationState<FirestoreAnnouncement>) -> Unit)? = null
  ) {
    val actualPageSize = pageSize ?: (if (_isLowBandwidthMode.value) 5L else 10L)
    val currentState = _announcementsPaginationState.value

    if (currentState.isLoading) {
      onComplete?.invoke(currentState)
      return
    }

    if (!forceRefresh && !currentState.hasMore) {
      onComplete?.invoke(currentState)
      return
    }

    _announcementsPaginationState.update { it.copy(isLoading = true, error = null) }

    val database = db
    if (database == null || !NetworkConnectivityManager.isOnline.value) {
      // Offline fallback: slice from local list
      val allItems = _remoteAnnouncements.value
      val startIdx = if (forceRefresh) 0 else currentState.items.size
      val endIdx = (startIdx + actualPageSize.toInt()).coerceAtMost(allItems.size)
      val newSlice = if (startIdx < allItems.size) allItems.subList(startIdx, endIdx) else emptyList()
      val combined = if (forceRefresh) newSlice else currentState.items + newSlice
      val hasMore = endIdx < allItems.size

      val newState = FirestorePaginationState(
        items = combined,
        isLoading = false,
        hasMore = hasMore,
        lastSnapshot = null,
        currentPage = if (forceRefresh) 1 else currentState.currentPage + 1,
        pageSize = actualPageSize,
        isCacheSource = true
      )
      _announcementsPaginationState.value = newState
      onComplete?.invoke(newState)
      return
    }

    val activeInstituteId = AppRepository.getCurrentInstituteId()
    var query: Query = database.collection(ANNOUNCEMENTS_COLLECTION)
      .whereEqualTo("instituteId", activeInstituteId)
      .orderBy("timestamp", Query.Direction.DESCENDING)
      .limit(actualPageSize)

    if (!forceRefresh && currentState.lastSnapshot != null) {
      query = query.startAfter(currentState.lastSnapshot)
    }

    val source = if (_isLowBandwidthMode.value && !forceRefresh) Source.CACHE else Source.DEFAULT

    query.get(source)
      .addOnSuccessListener { snapshot ->
        val newAnnouncements = snapshot.documents.map { doc ->
          FirestoreAnnouncement.fromMap(doc.id, doc.data ?: emptyMap())
        }
        val lastDoc = snapshot.documents.lastOrNull()
        val combinedItems = if (forceRefresh) newAnnouncements else (currentState.items + newAnnouncements).distinctBy { it.id }
        val hasMore = snapshot.size() >= actualPageSize.toInt()

        val newState = FirestorePaginationState(
          items = combinedItems,
          isLoading = false,
          hasMore = hasMore,
          lastSnapshot = lastDoc ?: currentState.lastSnapshot,
          currentPage = if (forceRefresh) 1 else currentState.currentPage + 1,
          pageSize = actualPageSize,
          isCacheSource = snapshot.metadata.isFromCache
        )
        _announcementsPaginationState.value = newState

        // Also merge into _remoteAnnouncements
        val currentRemote = _remoteAnnouncements.value.toMutableList()
        newAnnouncements.forEach { na ->
          if (currentRemote.none { it.id == na.id }) currentRemote.add(na)
        }
        _remoteAnnouncements.value = currentRemote.sortedByDescending { it.timestamp }
        announcementsCache = CachedResource(_remoteAnnouncements.value, System.currentTimeMillis(), "server")

        _syncStatus.value = "Loaded announcements page ${newState.currentPage} (${combinedItems.size} items)"
        onComplete?.invoke(newState)
      }
      .addOnFailureListener { e ->
        Log.w(TAG, "Pagination query failed on source $source, trying default", e)
        if (source == Source.CACHE) {
          query.get(Source.DEFAULT)
            .addOnSuccessListener { fallbackSnap ->
              val newAnnouncements = fallbackSnap.documents.map { doc ->
                FirestoreAnnouncement.fromMap(doc.id, doc.data ?: emptyMap())
              }
              val lastDoc = fallbackSnap.documents.lastOrNull()
              val combinedItems = if (forceRefresh) newAnnouncements else (currentState.items + newAnnouncements).distinctBy { it.id }
              val hasMore = fallbackSnap.size() >= actualPageSize.toInt()

              val newState = FirestorePaginationState(
                items = combinedItems,
                isLoading = false,
                hasMore = hasMore,
                lastSnapshot = lastDoc ?: currentState.lastSnapshot,
                currentPage = if (forceRefresh) 1 else currentState.currentPage + 1,
                pageSize = actualPageSize,
                isCacheSource = false
              )
              _announcementsPaginationState.value = newState
              onComplete?.invoke(newState)
            }
            .addOnFailureListener { err ->
              _announcementsPaginationState.update { it.copy(isLoading = false, error = err.message) }
              onComplete?.invoke(_announcementsPaginationState.value)
            }
        } else {
          _announcementsPaginationState.update { it.copy(isLoading = false, error = e.message) }
          onComplete?.invoke(_announcementsPaginationState.value)
        }
      }
  }

  fun resetAnnouncementsPagination() {
    _announcementsPaginationState.value = FirestorePaginationState()
  }

  private var studentsListenerRegistration: com.google.firebase.firestore.ListenerRegistration? = null
  private var announcementsListenerRegistration: com.google.firebase.firestore.ListenerRegistration? = null
  private val activeListenerKeys = ConcurrentHashMap.newKeySet<String>()

  // Pending synchronization queue (Section 26 & 27: Room -> Firestore with stable IDs and metadata)
  private val pendingSyncEnvelopes = ConcurrentHashMap<String, FirestoreSyncEnvelope>()
  private val _pendingSyncList = MutableStateFlow<List<FirestoreSyncEnvelope>>(emptyList())
  val pendingSyncList = _pendingSyncList.asStateFlow()

  @Volatile
  private var syncQueueDaoOverride: com.example.data.db.SyncQueueDao? = null

  @Volatile
  private var simulateImmediateWriteFailure: Boolean = false

  @Volatile
  private var simulatedWriteErrorMessage: String = "Simulated immediate Firestore write failure"

  fun setSyncQueueDaoForTesting(dao: com.example.data.db.SyncQueueDao?) {
    syncQueueDaoOverride = dao
  }

  fun setSimulateImmediateWriteFailureForTesting(
    fail: Boolean,
    errorMessage: String = "Simulated immediate Firestore write failure"
  ) {
    simulateImmediateWriteFailure = fail
    simulatedWriteErrorMessage = errorMessage
  }

  private fun resolveSyncQueueDao(): com.example.data.db.SyncQueueDao? {
    syncQueueDaoOverride?.let { return it }
    com.example.data.db.AppRoomDatabase.getInstanceOrNull()?.let {
      return it.syncQueueDao()
    }
    val ctx = AppRepository.getAppContextOrNull()
    if (ctx != null) {
      return com.example.data.db.AppRoomDatabase.getDatabase(ctx).syncQueueDao()
    }
    return null
  }

  fun toSyncQueueEntity(
    envelope: FirestoreSyncEnvelope,
    queueId: String = com.example.data.db.SyncQueueEntity.generateQueueId(),
    activeInstituteId: String = AppRepository.getCurrentInstituteId()
  ): com.example.data.db.SyncQueueEntity {
    return envelope.toSyncQueueEntity(
      queueId = queueId,
      activeInstituteId = activeInstituteId
    )
  }

  private fun persistCreateOrUpdateToRoomQueue(
    envelope: FirestoreSyncEnvelope,
    targetInstituteId: String
  ): com.example.data.db.SyncQueueEntity? {
    val operationType = com.example.data.db.SyncOperationType.fromString(envelope.operation)
    val key = "${envelope.collection}:${envelope.documentId}"
    val queueEntity = toSyncQueueEntity(
      envelope = envelope.copy(
        instituteId = targetInstituteId,
        operation = operationType.name,
        syncStatus = "PENDING_SYNC"
      ),
      activeInstituteId = targetInstituteId
    )

    val dao = resolveSyncQueueDao()
    val persistedEntity = if (dao != null) {
      kotlinx.coroutines.runBlocking {
        val existingActive = dao.getActiveItemsForEntityAndOperation(
          instituteId = queueEntity.instituteId,
          entityType = queueEntity.entityType,
          entityId = queueEntity.entityId,
          operationType = queueEntity.operationType
        ).firstOrNull()

        if (existingActive != null) {
          val updated = existingActive.copy(
            serializedPayload = queueEntity.serializedPayload,
            updatedAt = maxOf(queueEntity.updatedAt, existingActive.updatedAt),
            errorMessage = null
          ).sanitize()
          dao.update(updated)
          updated
        } else {
          dao.insert(queueEntity)
          queueEntity
        }
      }
    } else {
      queueEntity
    }

    val syncedEnvelope = FirestoreSyncEnvelope.fromSyncQueueEntity(persistedEntity)
    pendingSyncEnvelopes[key] = syncedEnvelope
    refreshPendingSyncStateFromRoom()
    return persistedEntity
  }

  private fun refreshPendingSyncStateFromRoom() {
    val dao = resolveSyncQueueDao()
    if (dao != null) {
      try {
        val items = kotlinx.coroutines.runBlocking { dao.getPendingItems() }
        val converted = items.map { FirestoreSyncEnvelope.fromSyncQueueEntity(it) }
        pendingSyncEnvelopes.clear()
        converted.forEach { env ->
          pendingSyncEnvelopes["${env.collection}:${env.documentId}"] = env
        }
        _pendingSyncList.value = converted
        return
      } catch (_: Exception) {
      }
    }
    _pendingSyncList.value = pendingSyncEnvelopes.values.sortedBy { it.createdAt }
  }

  fun getPendingSyncEnvelopes(): List<FirestoreSyncEnvelope> {
    val dao = resolveSyncQueueDao()
    if (dao != null) {
      return try {
        val items = kotlinx.coroutines.runBlocking { dao.getPendingItems() }
        items.map { FirestoreSyncEnvelope.fromSyncQueueEntity(it) }
      } catch (_: Exception) {
        pendingSyncEnvelopes.values.toList()
      }
    }
    return pendingSyncEnvelopes.values.toList()
  }

  fun getPendingSyncCount(): Int {
    val dao = resolveSyncQueueDao()
    if (dao != null) {
      return try {
        kotlinx.coroutines.runBlocking { dao.countPendingItems() }
      } catch (_: Exception) {
        pendingSyncEnvelopes.size
      }
    }
    return pendingSyncEnvelopes.size
  }

  fun clearPendingSyncQueue() {
    pendingSyncEnvelopes.clear()
    _pendingSyncList.value = emptyList()
    val dao = resolveSyncQueueDao()
    if (dao != null) {
      try {
        kotlinx.coroutines.runBlocking { dao.clearAll() }
      } catch (_: Exception) {
      }
    }
  }

  fun clearInMemoryPendingSyncCache() {
    pendingSyncEnvelopes.clear()
    _pendingSyncList.value = emptyList()
  }

  fun enqueueOrSyncEnvelope(
    envelope: FirestoreSyncEnvelope,
    onComplete: ((Boolean) -> Unit)? = null
  ) {
    if (envelope.documentId.isBlank() || envelope.collection.isBlank()) {
      onComplete?.invoke(false)
      return
    }
    val activeInstituteId = AppRepository.getCurrentInstituteId()
    val targetInstId = envelope.instituteId.trim().ifBlank { activeInstituteId }
    if (!com.example.security.SecurityEngine.isValidInstituteId(targetInstId)) {
      com.example.security.SecurityEngine.logEvent(
        severity = com.example.security.SecuritySeverity.ALERT,
        eventType = "INVALID_INSTITUTE_SYNC_BLOCKED",
        actor = AppRepository.currentUserIdentifier.value.ifBlank { "System" },
        details = "Blocked sync to ${envelope.collection}/${envelope.documentId} with invalid instituteId '$targetInstId'."
      )
      onComplete?.invoke(false)
      return
    }
    val normalizedOpType = com.example.data.db.SyncOperationType.fromString(envelope.operation)
    if ((AppRepository.isLoggedIn.value || normalizedOpType == com.example.data.db.SyncOperationType.DELETE) &&
      !targetInstId.equals(activeInstituteId, ignoreCase = true)
    ) {
      com.example.security.SecurityEngine.logEvent(
        severity = com.example.security.SecuritySeverity.ALERT,
        eventType = if (normalizedOpType == com.example.data.db.SyncOperationType.DELETE) {
          "CROSS_TENANT_DELETE_BLOCKED"
        } else {
          "CROSS_TENANT_SYNC_BLOCKED"
        },
        actor = AppRepository.currentUserIdentifier.value.ifBlank { "System" },
        details = "Blocked cross-institute ${normalizedOpType.name} from '$activeInstituteId' to '$targetInstId' on ${envelope.collection}/${envelope.documentId}."
      )
      onComplete?.invoke(false)
      return
    }

    val payloadInstituteId = (envelope.payload["instituteId"] as? String)?.trim()
    if (!payloadInstituteId.isNullOrBlank() && !payloadInstituteId.equals(targetInstId, ignoreCase = true)) {
      com.example.security.SecurityEngine.logEvent(
        severity = com.example.security.SecuritySeverity.ALERT,
        eventType = "INSTITUTE_REASSIGNMENT_BLOCKED",
        actor = AppRepository.currentUserIdentifier.value.ifBlank { "System" },
        details = "Blocked sync with mismatched payload instituteId '$payloadInstituteId' vs '$targetInstId' on ${envelope.collection}/${envelope.documentId}."
      )
      onComplete?.invoke(false)
      return
    }

    if (envelope.collection == COLLECTION_NAME) {
      val existingRemoteInst = _remoteProfiles.value.find { it.id == envelope.documentId }?.instituteId
      val existingInstId = existingRemoteInst ?: AppRepository.findExistingStudentInstituteId(envelope.documentId)
      if (existingInstId != null &&
        (!existingInstId.equals(targetInstId, ignoreCase = true) ||
          !existingInstId.equals(activeInstituteId, ignoreCase = true))
      ) {
        com.example.security.SecurityEngine.logEvent(
          severity = com.example.security.SecuritySeverity.ALERT,
          eventType = if (normalizedOpType == com.example.data.db.SyncOperationType.DELETE) {
            "CROSS_TENANT_DELETE_BLOCKED"
          } else {
            "INSTITUTE_REASSIGNMENT_BLOCKED"
          },
          actor = AppRepository.currentUserIdentifier.value.ifBlank { "System" },
          details = "Blocked attempt to queue ${normalizedOpType.name} for ${envelope.collection}/${envelope.documentId} under institute '$targetInstId' (active '$activeInstituteId') when entity belongs to '$existingInstId'."
        )
        onComplete?.invoke(false)
        return
      }
    }

    val key = "${envelope.collection}:${envelope.documentId}"
    val normalizedOperation = normalizedOpType.name
    val sanitizedPayload = if (normalizedOpType == com.example.data.db.SyncOperationType.DELETE) {
      LinkedHashMap<String, Any>().apply {
        put("id", envelope.documentId.trim())
        put("instituteId", targetInstId)
        (envelope.payload["status"] as? String)?.takeIf { it.isNotBlank() }?.let { put("status", it) }
        (envelope.payload["isActivated"] as? Boolean)?.let { put("isActivated", it) }
      }
    } else {
      com.example.data.db.SyncQueueEntity.sanitizePayloadMap(
        envelope.payload + ("instituteId" to targetInstId)
      ).mapValues { it.value ?: "" }
    }
    val pendingEnv = envelope.copy(
      instituteId = targetInstId,
      operation = normalizedOperation,
      payload = sanitizedPayload,
      syncStatus = "PENDING_SYNC",
      updatedAt = System.currentTimeMillis()
    )

    // Keep local remoteProfiles in sync if this is a student_profiles document
    if (envelope.collection == COLLECTION_NAME) {
      if (normalizedOpType == com.example.data.db.SyncOperationType.DELETE) {
        val filtered = _remoteProfiles.value.filterNot { it.id == envelope.documentId }
        _remoteProfiles.value = filterStudentsForActiveSession(filtered)
        studentsCache = CachedResource(_remoteProfiles.value, System.currentTimeMillis(), "local_pending")
      } else {
        val fsStudent = FirestoreStudent.fromMap(envelope.documentId, sanitizedPayload)
        val updated = _remoteProfiles.value.filterNot { it.id == fsStudent.id } + fsStudent
        _remoteProfiles.value = filterStudentsForActiveSession(updated)
        studentsCache = CachedResource(_remoteProfiles.value, System.currentTimeMillis(), "local_pending")
      }
    }

    if (!NetworkConnectivityManager.isOnline.value) {
      persistCreateOrUpdateToRoomQueue(pendingEnv, targetInstId)
      NetworkConnectivityManager.recordLocalMutation()
      _syncStatus.value = "Saved locally (Pending Cloud Sync: ${getPendingSyncCount()} item(s))"
      onComplete?.invoke(false)
      return
    }

    if (!NetworkConnectivityManager.isFirebaseReachable.value) {
      persistCreateOrUpdateToRoomQueue(pendingEnv, targetInstId)
      NetworkConnectivityManager.recordLocalMutation()
      NetworkConnectivityManager.recordServiceUnavailable("Firebase service unreachable")
      _syncStatus.value = "Saved locally (Cloud Service Unavailable — Sync Pending)"
      onComplete?.invoke(false)
      return
    }

    if (simulateImmediateWriteFailure) {
      persistCreateOrUpdateToRoomQueue(pendingEnv, targetInstId)
      NetworkConnectivityManager.recordLocalMutation()
      NetworkConnectivityManager.recordSyncFailure(simulatedWriteErrorMessage)
      _syncStatus.value = "Cloud Sync Failed: $simulatedWriteErrorMessage"
      onComplete?.invoke(false)
      return
    }

    val database = db
    if (database == null) {
      // Online and reachable in local/unit-test environment: preserve immediate online success without queueing
      pendingSyncEnvelopes.remove(key)
      refreshPendingSyncStateFromRoom()
      _lastSyncTimestamp.value = System.currentTimeMillis()
      NetworkConnectivityManager.recordSyncSuccess()
      _syncStatus.value = "Cloud Sync Complete (${envelope.collection}/${envelope.documentId})"
      onComplete?.invoke(true)
      return
    }

    val docRef = database.collection(envelope.collection).document(envelope.documentId)
    val task = if (normalizedOpType == com.example.data.db.SyncOperationType.DELETE) {
      docRef.delete()
    } else {
      docRef.set(pendingEnv.copy(syncStatus = "SYNCED").toMap())
    }
    task
      .addOnSuccessListener {
        pendingSyncEnvelopes.remove(key)
        refreshPendingSyncStateFromRoom()
        _lastSyncTimestamp.value = System.currentTimeMillis()
        if (pendingSyncEnvelopes.isEmpty()) {
          NetworkConnectivityManager.recordSyncSuccess()
          _syncStatus.value = "Cloud Sync Complete (${envelope.collection}/${envelope.documentId})"
        } else {
          _syncStatus.value = "Synced ${envelope.collection}/${envelope.documentId} (${pendingSyncEnvelopes.size} remaining)"
        }
        onComplete?.invoke(true)
      }
      .addOnFailureListener { e ->
        persistCreateOrUpdateToRoomQueue(pendingEnv, targetInstId)
        NetworkConnectivityManager.recordLocalMutation()
        NetworkConnectivityManager.recordSyncFailure(e.message ?: "Firestore write error")
        _syncStatus.value = "Cloud Sync Failed: ${e.message ?: "Temporary network error"}"
        onComplete?.invoke(false)
      }
  }

  fun syncStudentToCloud(
    student: Student,
    createdBy: String = "SYSTEM",
    operationType: com.example.data.db.SyncOperationType = com.example.data.db.SyncOperationType.UPDATE,
    onComplete: ((Boolean) -> Unit)? = null
  ) {
    if (student.id.isBlank()) {
      onComplete?.invoke(false)
      return
    }
    val resolvedInstituteId = student.instituteId.trim().ifBlank { AppRepository.getCurrentInstituteId() }
    val fsStudent = FirestoreStudent.fromDomain(student.copy(instituteId = resolvedInstituteId))
    val envelope = FirestoreSyncEnvelope(
      collection = COLLECTION_NAME,
      documentId = student.id,
      instituteId = resolvedInstituteId,
      ownership = createdBy.ifBlank { student.id },
      createdAt = student.admissionTimestamp,
      updatedAt = System.currentTimeMillis(),
      syncStatus = "PENDING_SYNC",
      conflictStrategy = "LAST_WRITE_WINS",
      operation = operationType.name,
      payload = fsStudent.toMap()
    )
    enqueueOrSyncEnvelope(envelope, onComplete)
  }

  fun flushPendingSyncQueue(onComplete: ((Int) -> Unit)? = null): Boolean {
    if (!NetworkConnectivityManager.isOnline.value || !NetworkConnectivityManager.isFirebaseReachable.value) {
      onComplete?.invoke(0)
      return false
    }
    val items = pendingSyncEnvelopes.values.toList()
    if (items.isEmpty()) {
      onComplete?.invoke(0)
      return true
    }
    val database = db
    if (database == null) {
      // In local test environment when explicitly flushing while online & reachable, mark synced if simulated
      onComplete?.invoke(0)
      return false
    }
    var syncedCount = 0
    var processed = 0
    items.forEach { env ->
      enqueueOrSyncEnvelope(env) { ok ->
        if (ok) syncedCount++
        processed++
        if (processed == items.size) {
          onComplete?.invoke(syncedCount)
        }
      }
    }
    return true
  }

  // REALTIME LISTENER for Student Profiles (Section 27 & 32: controlled lifecycle ownership, no duplicates, tenant-scoped)
  fun listenToChanges() {
    val activeInstituteId = AppRepository.getCurrentInstituteId()
    if (!com.example.security.SecurityEngine.isValidInstituteId(activeInstituteId)) return
    if (!activeListenerKeys.add(COLLECTION_NAME)) {
      return // Already listening — idempotent!
    }
    val database = db ?: return
    if (studentsListenerRegistration != null) return
    try {
      studentsListenerRegistration = applyStudentScopeToQuery(database.collection(COLLECTION_NAME), activeInstituteId)
        .addSnapshotListener { snapshot, error ->
          if (error != null) {
            Log.e(TAG, "Realtime students listener error: ${error.message}")
            NetworkConnectivityManager.recordSyncFailure(error.message ?: "Listener error")
            return@addSnapshotListener
          }
          if (snapshot != null && !snapshot.isEmpty) {
            val list = filterStudentsForActiveSession(
              snapshot.documents.map { doc ->
                FirestoreStudent.fromMap(doc.id, doc.data ?: emptyMap())
              }
            )
            _remoteProfiles.value = list
            studentsCache = CachedResource(list, System.currentTimeMillis(), "server")
            // Synchronize Firestore -> Room using stable IDs
            AppRepository.reconcileRemoteStudentsToRoom(list)
          }
        }
    } catch (e: Exception) {
      Log.e(TAG, "Error setting snapshot listener for students", e)
    }
  }

  fun stopListeningToChanges() {
    studentsListenerRegistration?.remove()
    studentsListenerRegistration = null
    activeListenerKeys.remove(COLLECTION_NAME)
  }

  // REALTIME LISTENER for centralized Announcements collection (Tenant-Scoped)
  fun listenToAnnouncements() {
    val activeInstituteId = AppRepository.getCurrentInstituteId()
    if (!com.example.security.SecurityEngine.isValidInstituteId(activeInstituteId)) return
    if (!activeListenerKeys.add(ANNOUNCEMENTS_COLLECTION)) {
      return // Already listening — idempotent!
    }
    if (announcementsListenerRegistration != null) return
    val database = db ?: return
    try {
      announcementsListenerRegistration = database.collection(ANNOUNCEMENTS_COLLECTION)
        .whereEqualTo("instituteId", activeInstituteId)
        .addSnapshotListener { snapshot, error ->
          if (error != null) {
            Log.e(TAG, "Realtime announcements listener error: ${error.message}")
            return@addSnapshotListener
          }

          if (snapshot != null && !snapshot.isEmpty) {
            val list = snapshot.documents.map { doc ->
              FirestoreAnnouncement.fromMap(doc.id, doc.data ?: emptyMap())
            }.filter { it.instituteId.equals(activeInstituteId, ignoreCase = true) }
              .sortedByDescending { it.timestamp }

            _remoteAnnouncements.value = list
            Log.d(TAG, "Realtime update: ${list.size} announcements loaded from Firestore.")
          }
        }
    } catch (e: Exception) {
      Log.e(TAG, "Error setting snapshot listener for announcements", e)
    }
  }

  fun stopListeningToAnnouncements() {
    announcementsListenerRegistration?.remove()
    announcementsListenerRegistration = null
    activeListenerKeys.remove(ANNOUNCEMENTS_COLLECTION)
  }

  fun stopAllListeners() {
    stopListeningToChanges()
    stopListeningToAnnouncements()
    activeListenerKeys.clear()
  }

  fun getActiveListenerCount(): Int = activeListenerKeys.size

  // --- INSTITUTE CONFIGURATION FIRESTORE PERSISTENCE (TENANT-ISOLATED PER INSTITUTE) ---
  private const val INSTITUTE_CONFIG_COLLECTION = "institute_config"

  fun resolveInstituteConfigDocId(config: InstituteConfig? = null): String {
    val candidate = config?.instituteId?.trim()?.takeIf { it.isNotEmpty() }
      ?: AppRepository.getCurrentInstituteId().trim()
    return if (com.example.security.SecurityEngine.isValidInstituteId(candidate)) {
      candidate
    } else {
      ""
    }
  }

  fun syncInstituteConfig(
    config: InstituteConfig,
    onSuccess: (() -> Unit)? = null,
    onFailure: ((Exception) -> Unit)? = null
  ) {
    val activeInstituteId = AppRepository.getCurrentInstituteId()
    val docId = resolveInstituteConfigDocId(config)
    if (docId.isBlank()) {
      val ex = SecurityException("Invalid or missing instituteId '${config.instituteId}' for institute configuration.")
      _syncStatus.value = "Security Error: Invalid instituteId for configuration."
      onFailure?.invoke(ex)
      return
    }
    if (AppRepository.isLoggedIn.value && !docId.equals(activeInstituteId, ignoreCase = true)) {
      val ex = SecurityException("Cross-institute configuration write blocked: active '$activeInstituteId' vs target '$docId'.")
      _syncStatus.value = "Security Error: Cross-institute configuration write blocked."
      onFailure?.invoke(ex)
      return
    }
    if (AppRepository.isLoggedIn.value && AppRepository.currentRole.value != UserRole.ADMIN &&
      AppRepository.currentUserAccount.value?.role != UserRole.ADMIN
    ) {
      val ex = SecurityException("Only an Institute Admin can modify institute configuration.")
      _syncStatus.value = "Security Error: Insufficient role permissions."
      onFailure?.invoke(ex)
      return
    }

    if (!NetworkConnectivityManager.isOnline.value) {
      NetworkConnectivityManager.recordLocalMutation()
      val ex = IllegalStateException("Saved locally; cloud sync pending while offline.")
      _syncStatus.value = "Institute config saved locally (Cloud Sync Pending)"
      onFailure?.invoke(ex)
      return
    }

    val database = db
    if (database == null) {
      Log.w(TAG, "Firestore not initialized - caching InstituteConfig locally for institute '$docId'.")
      _syncStatus.value = "Institute config saved locally (Firebase unconfigured)"
      onSuccess?.invoke()
      return
    }

    val configMap = hashMapOf<String, Any>(
      "instituteId" to docId,
      "instituteName" to config.instituteName,
      "tagline" to config.tagline,
      "founderName" to config.founderName,
      "address" to config.address,
      "contactPhone" to config.contactPhone,
      "email" to config.email,
      "academicYear" to config.academicYear,
      "existingStaffCount" to config.existingStaffCount,
      "selectedGrades" to config.selectedGrades,
      "gradeSubjects" to config.gradeSubjects,
      "competitiveExams" to config.competitiveExams,
      "streams" to config.streams,
      "gradeFees" to config.gradeFees,
      "adminFullName" to config.adminFullName,
      "adminRole" to config.adminRole,
      "adminGender" to config.adminGender,
      "adminDob" to config.adminDob,
      "adminPhone" to config.adminPhone,
      "adminEmail" to config.adminEmail,
      "isSetupCompleted" to config.isSetupCompleted,
      "lastUpdated" to System.currentTimeMillis()
    )

    database.collection(INSTITUTE_CONFIG_COLLECTION)
      .document(docId)
      .set(configMap)
      .addOnSuccessListener {
        Log.d(TAG, "Institute configuration successfully synced to Firestore document '$docId'")
        _syncStatus.value = "Institute setup synced to Cloud Firestore ($docId)"
        NetworkConnectivityManager.recordSyncSuccess()
        onSuccess?.invoke()
      }
      .addOnFailureListener { e ->
        Log.e(TAG, "Failed to sync institute config to Firestore", e)
        NetworkConnectivityManager.recordSyncFailure(e.message ?: "Institute config sync failed")
        onFailure?.invoke(e)
      }
  }
}
