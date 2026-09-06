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

data class FirestoreStudent(
  val id: String = "",
  val name: String = "",
  val grade: String = "",
  val subjectsEnrolled: List<String> = emptyList(),
  val contactInfo: String = "",
  val parentName: String = "",
  val parentContact: String = ""
) {
  // Convert to Map for Firestore upload
  fun toMap(): Map<String, Any> {
    return mapOf(
      "id" to id,
      "name" to name,
      "grade" to grade,
      "subjectsEnrolled" to subjectsEnrolled,
      "contactInfo" to contactInfo,
      "parentName" to parentName,
      "parentContact" to parentContact
    )
  }

  companion object {
    // Helper to construct from Firestore document map
    fun fromMap(id: String, map: Map<String, Any>): FirestoreStudent {
      return FirestoreStudent(
        id = id,
        name = map["name"] as? String ?: "",
        grade = map["grade"] as? String ?: "",
        subjectsEnrolled = (map["subjectsEnrolled"] as? List<*>)?.mapNotNull { it as? String } ?: emptyList(),
        contactInfo = map["contactInfo"] as? String ?: "",
        parentName = map["parentName"] as? String ?: "",
        parentContact = map["parentContact"] as? String ?: ""
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

  // CREATE/ADD Student Profile to Firestore
  fun createStudentProfile(
    student: FirestoreStudent,
    onSuccess: () -> Unit,
    onFailure: (Exception) -> Unit
  ) {
    if (!NetworkConnectivityManager.isOnline.value) {
      val ex = IllegalStateException("Cannot create student profile in Offline Mode. Connect to internet to sync changes.")
      _syncStatus.value = "Offline Mode: Changes blocked."
      onFailure(ex)
      return
    }

    // Immediately write-through to in-memory cache and pagination state
    val updatedProfiles = _remoteProfiles.value.filterNot { it.id == student.id } + student
    _remoteProfiles.value = updatedProfiles
    studentsCache = CachedResource(updatedProfiles, System.currentTimeMillis(), "server")
    _studentsPaginationState.update { state ->
      val updatedItems = state.items.filterNot { it.id == student.id } + student
      state.copy(items = updatedItems)
    }

    val database = db
    if (database == null) {
      _syncStatus.value = "Local memory update: Student '${student.name}' saved."
      onSuccess()
      return
    }

    database.collection(COLLECTION_NAME)
      .document(student.id)
      .set(student.toMap())
      .addOnSuccessListener {
        Log.d(TAG, "Student profile '${student.name}' successfully written to Firestore!")
        _syncStatus.value = "Student '${student.name}' synchronized to Cloud Firestore!"
        onSuccess()
      }
      .addOnFailureListener { e ->
        Log.e(TAG, "Error writing student profile to Firestore", e)
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
      
      database.collection(COLLECTION_NAME)
        .get(source)
        .addOnSuccessListener { result ->
          val list = result.map { doc ->
            FirestoreStudent.fromMap(doc.id, doc.data)
          }
          if (list.isNotEmpty()) {
            _remoteProfiles.value = list
            studentsCache = CachedResource(list, System.currentTimeMillis(), "server")
            _lastSyncTimestamp.value = System.currentTimeMillis()
          }
          _syncStatus.value = "Fetched ${list.size} profiles (${if (result.metadata.isFromCache) "Native Cache" else "Cloud Server"})"
          _isLoading.value = false
          onComplete?.invoke(_remoteProfiles.value)
        }
        .addOnFailureListener { e ->
          // If CACHE attempt failed in low-bandwidth mode, retry with DEFAULT
          if (source == Source.CACHE) {
            database.collection(COLLECTION_NAME)
              .get(Source.DEFAULT)
              .addOnSuccessListener { defaultRes ->
                val list = defaultRes.map { doc -> FirestoreStudent.fromMap(doc.id, doc.data) }
                if (list.isNotEmpty()) {
                  _remoteProfiles.value = list
                  studentsCache = CachedResource(list, System.currentTimeMillis(), "server")
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

    var query: Query = database.collection(COLLECTION_NAME)
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
    if (!NetworkConnectivityManager.isOnline.value) {
      val ex = IllegalStateException("Cannot update student in Offline Mode. Connect to internet to sync changes.")
      _syncStatus.value = "Offline Mode: Changes blocked."
      onFailure(ex)
      return
    }

    // Write-through update local memory & pagination states immediately
    val updatedList = _remoteProfiles.value.map { if (it.id == student.id) student else it }
    _remoteProfiles.value = updatedList
    studentsCache = CachedResource(updatedList, System.currentTimeMillis(), "server")
    _studentsPaginationState.update { state ->
      state.copy(items = state.items.map { if (it.id == student.id) student else it })
    }

    val database = db
    if (database == null) {
      _syncStatus.value = "Local update: ${student.name}'s profile modified."
      onSuccess()
      return
    }

    database.collection(COLLECTION_NAME)
      .document(student.id)
      .update(student.toMap())
      .addOnSuccessListener {
        Log.d(TAG, "Student ${student.id} profile updated in Firestore!")
        _syncStatus.value = "Updated profile for ${student.name} in Firestore"
        onSuccess()
      }
      .addOnFailureListener { e ->
        Log.e(TAG, "Error updating student profile", e)
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
    if (!NetworkConnectivityManager.isOnline.value) {
      val ex = IllegalStateException("Cannot delete student in Offline Mode. Connect to internet to sync changes.")
      _syncStatus.value = "Offline Mode: Changes blocked."
      onFailure(ex)
      return
    }

    // Write-through update local memory & pagination states immediately
    val filteredList = _remoteProfiles.value.filterNot { it.id == studentId }
    _remoteProfiles.value = filteredList
    studentsCache = CachedResource(filteredList, System.currentTimeMillis(), "server")
    _studentsPaginationState.update { state ->
      state.copy(items = state.items.filterNot { it.id == studentId })
    }

    val database = db
    if (database == null) {
      _syncStatus.value = "Local remove: Student ID $studentId deleted."
      onSuccess()
      return
    }

    database.collection(COLLECTION_NAME)
      .document(studentId)
      .delete()
      .addOnSuccessListener {
        Log.d(TAG, "Student profile $studentId deleted from Firestore!")
        _syncStatus.value = "Deleted Student profile ID $studentId from Firestore"
        onSuccess()
      }
      .addOnFailureListener { e ->
        Log.e(TAG, "Error deleting profile", e)
        _syncStatus.value = "Delete Error: ${e.message}"
        onFailure(e)
      }
  }

  // SEED live Firestore database with sample student profiles initially
  fun seedDatabaseWithDefaults(onSuccess: () -> Unit) {
    val sampleStudents = emptyList<FirestoreStudent>()

    onSuccess()
  }

  // CHECK if attendance document exists in Firestore for today's date and batch (Cached)
  fun checkBatchAttendanceExists(
    batchId: String,
    dateStr: String,
    forceRefresh: Boolean = false,
    onResult: (Boolean) -> Unit
  ) {
    val docId = "${batchId.replace(" ", "_")}_${dateStr.replace("/", "-")}"
    
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
      val exists = localRecords.any { it.batchName.equals(batchId, ignoreCase = true) && it.date == dateStr }
      attendanceCheckCache[docId] = CachedResource(exists, System.currentTimeMillis())
      onResult(exists)
      return
    }

    val source = if (_isLowBandwidthMode.value && !forceRefresh) Source.CACHE else Source.DEFAULT
    database.collection("attendance")
      .document(docId)
      .get(source)
      .addOnSuccessListener { snapshot ->
        val exists = snapshot.exists()
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
              val exists = snap.exists()
              attendanceCheckCache[docId] = CachedResource(exists, System.currentTimeMillis())
              onResult(exists)
            }
            .addOnFailureListener {
              val localRecords = AppRepository.attendance.value
              val exists = localRecords.any { it.batchName.equals(batchId, ignoreCase = true) && it.date == dateStr }
              onResult(exists)
            }
        } else {
          val localRecords = AppRepository.attendance.value
          val exists = localRecords.any { it.batchName.equals(batchId, ignoreCase = true) && it.date == dateStr }
          onResult(exists)
        }
      }
  }

  // SAVE or update batch attendance document in Firestore
  fun saveBatchAttendanceToFirestore(
    batchId: String,
    dateStr: String,
    markedBy: String,
    records: List<AttendanceRecord>,
    onComplete: (() -> Unit)? = null
  ) {
    val database = db
    val docId = "${batchId.replace(" ", "_")}_${dateStr.replace("/", "-")}"
    
    // Write-through update the attendance check cache immediately
    attendanceCheckCache[docId] = CachedResource(true, System.currentTimeMillis())

    val data = mapOf(
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
          "reason" to (rec.reason ?: "")
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

    // Always update local StateFlow and in-memory cache immediately
    val updatedList = listOf(announcement) + _remoteAnnouncements.value.filterNot { it.id == announcement.id }
    _remoteAnnouncements.value = updatedList
    announcementsCache = CachedResource(updatedList, System.currentTimeMillis(), "server")
    _announcementsPaginationState.update { state ->
      val updatedItems = listOf(announcement) + state.items.filterNot { it.id == announcement.id }
      state.copy(items = updatedItems)
    }

    // Also push a local NotificationItem to trigger system push notifications
    AppRepository.addNotification(
      NotificationItem(
        id = announcement.id,
        title = announcement.title,
        message = "${announcement.message}\n\n— ${announcement.sender} [Audience: ${announcement.targetAudience}]",
        time = announcement.dateStr.ifBlank { AppRepository.getCurrentTimeStr() },
        type = "Announcement",
        recipientRole = announcement.targetAudience,
        isRead = false
      )
    )

    val database = db
    if (database == null) {
      Log.d(TAG, "Offline/Fallback mode: Announcement saved locally.")
      onSuccess?.invoke()
      return
    }

    database.collection(ANNOUNCEMENTS_COLLECTION)
      .document(announcement.id)
      .set(announcement.toMap())
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

    database.collection(ANNOUNCEMENTS_COLLECTION)
      .get(source)
      .addOnSuccessListener { result ->
        val list = result.map { doc ->
          FirestoreAnnouncement.fromMap(doc.id, doc.data)
        }.sortedByDescending { it.timestamp }

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
            .get(Source.DEFAULT)
            .addOnSuccessListener { res ->
              val list = res.map { doc -> FirestoreAnnouncement.fromMap(doc.id, doc.data) }.sortedByDescending { it.timestamp }
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

    var query: Query = database.collection(ANNOUNCEMENTS_COLLECTION)
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

  // REALTIME LISTENER for centralized Announcements collection
  fun listenToAnnouncements() {
    val database = db ?: return
    try {
      database.collection(ANNOUNCEMENTS_COLLECTION)
        .addSnapshotListener { snapshot, error ->
          if (error != null) {
            Log.e(TAG, "Realtime announcements listener error: ${error.message}")
            return@addSnapshotListener
          }

          if (snapshot != null && !snapshot.isEmpty) {
            val list = snapshot.documents.map { doc ->
              FirestoreAnnouncement.fromMap(doc.id, doc.data ?: emptyMap())
            }.sortedByDescending { it.timestamp }

            _remoteAnnouncements.value = list
            Log.d(TAG, "Realtime update: ${list.size} announcements loaded from Firestore.")
          }
        }
    } catch (e: Exception) {
      Log.e(TAG, "Error setting snapshot listener for announcements", e)
    }
  }

  // --- INSTITUTE CONFIGURATION FIRESTORE PERSISTENCE ---
  private const val INSTITUTE_CONFIG_COLLECTION = "institute_config"
  private const val INSTITUTE_CONFIG_DOC_ID = "primary_institute"

  fun syncInstituteConfig(
    config: InstituteConfig,
    onSuccess: (() -> Unit)? = null,
    onFailure: ((Exception) -> Unit)? = null
  ) {
    if (!NetworkConnectivityManager.isOnline.value) {
      val ex = IllegalStateException("Cannot sync configuration in Offline Mode.")
      onFailure?.invoke(ex)
      return
    }

    val database = db
    if (database == null) {
      Log.w(TAG, "Firestore not initialized - caching InstituteConfig locally.")
      onSuccess?.invoke()
      return
    }

    val configMap = hashMapOf<String, Any>(
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
      .document(INSTITUTE_CONFIG_DOC_ID)
      .set(configMap)
      .addOnSuccessListener {
        Log.d(TAG, "Institute configuration successfully synced to Firestore document '$INSTITUTE_CONFIG_DOC_ID'")
        _syncStatus.value = "Institute setup synced to Cloud Firestore"
        onSuccess?.invoke()
      }
      .addOnFailureListener { e ->
        Log.e(TAG, "Failed to sync institute config to Firestore", e)
        onFailure?.invoke(e)
      }
  }
}
