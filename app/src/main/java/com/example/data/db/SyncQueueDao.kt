package com.example.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * Room DAO providing deterministic persistence and lifecycle operations for [SyncQueueEntity].
 * Pending operations are always returned in stable creation-time order (`createdAt ASC, queueId ASC`).
 */
@Dao
interface SyncQueueDao {

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertRawQueueItem(item: SyncQueueEntity)

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertRawQueueItems(items: List<SyncQueueEntity>)

  suspend fun insertQueueItem(item: SyncQueueEntity) {
    insertRawQueueItem(item.sanitize())
  }

  suspend fun insertQueueItems(items: List<SyncQueueEntity>) {
    insertRawQueueItems(items.map { it.sanitize() })
  }

  suspend fun insert(item: SyncQueueEntity) {
    insertQueueItem(item)
  }

  @Update
  suspend fun updateRawQueueItem(item: SyncQueueEntity)

  suspend fun updateQueueItem(item: SyncQueueEntity) {
    updateRawQueueItem(item.sanitize())
  }

  suspend fun update(item: SyncQueueEntity) {
    updateQueueItem(item)
  }

  @Query(
    """
    SELECT * FROM sync_queue
    WHERE status = :pendingStatus
    ORDER BY createdAt ASC, queueId ASC
    """
  )
  suspend fun getPendingItems(
    pendingStatus: SyncQueueStatus = SyncQueueStatus.PENDING
  ): List<SyncQueueEntity>

  @Query(
    """
    SELECT * FROM sync_queue
    WHERE status = :pendingStatus
      AND LOWER(instituteId) = LOWER(:instituteId)
    ORDER BY createdAt ASC, queueId ASC
    """
  )
  suspend fun getPendingItemsByInstitute(
    instituteId: String,
    pendingStatus: SyncQueueStatus = SyncQueueStatus.PENDING
  ): List<SyncQueueEntity>

  @Query(
    """
    SELECT * FROM sync_queue
    WHERE status = :pendingStatus
    ORDER BY createdAt ASC, queueId ASC
    """
  )
  fun observePendingItems(
    pendingStatus: SyncQueueStatus = SyncQueueStatus.PENDING
  ): Flow<List<SyncQueueEntity>>

  @Query(
    """
    SELECT * FROM sync_queue
    ORDER BY createdAt ASC, queueId ASC
    """
  )
  suspend fun getAllItems(): List<SyncQueueEntity>

  @Query(
    """
    SELECT * FROM sync_queue
    ORDER BY createdAt ASC, queueId ASC
    """
  )
  fun observeAllItems(): Flow<List<SyncQueueEntity>>

  @Query("SELECT * FROM sync_queue WHERE queueId = :queueId LIMIT 1")
  suspend fun getItemById(queueId: String): SyncQueueEntity?

  @Query(
    """
    SELECT * FROM sync_queue
    WHERE status = :pendingStatus
      AND LOWER(instituteId) = LOWER(:instituteId)
      AND entityType = :entityType
      AND entityId = :entityId
    ORDER BY createdAt ASC, queueId ASC
    """
  )
  suspend fun getPendingItemsForEntity(
    instituteId: String,
    entityType: String,
    entityId: String,
    pendingStatus: SyncQueueStatus = SyncQueueStatus.PENDING
  ): List<SyncQueueEntity>

  @Query(
    """
    SELECT * FROM sync_queue
    WHERE (status = :pendingStatus OR status = :processingStatus)
      AND LOWER(instituteId) = LOWER(:instituteId)
      AND entityType = :entityType
      AND entityId = :entityId
      AND operationType = :operationType
    ORDER BY createdAt ASC, queueId ASC
    """
  )
  suspend fun getActiveItemsForEntityAndOperation(
    instituteId: String,
    entityType: String,
    entityId: String,
    operationType: SyncOperationType,
    pendingStatus: SyncQueueStatus = SyncQueueStatus.PENDING,
    processingStatus: SyncQueueStatus = SyncQueueStatus.PROCESSING
  ): List<SyncQueueEntity>

  @Query(
    """
    SELECT * FROM sync_queue
    WHERE entityType = :entityType
      AND entityId = :entityId
    ORDER BY createdAt ASC, queueId ASC
    """
  )
  suspend fun getItemsForEntity(
    entityType: String,
    entityId: String
  ): List<SyncQueueEntity>

  @Query(
    """
    UPDATE sync_queue
    SET status = :processingStatus,
        lastAttemptAt = :attemptAt,
        updatedAt = :updatedAt
    WHERE queueId = :queueId
    """
  )
  suspend fun markProcessing(
    queueId: String,
    attemptAt: Long = System.currentTimeMillis(),
    updatedAt: Long = attemptAt,
    processingStatus: SyncQueueStatus = SyncQueueStatus.PROCESSING
  ): Int

  @Query(
    """
    UPDATE sync_queue
    SET status = :failedStatus,
        retryCount = retryCount + 1,
        lastAttemptAt = :attemptAt,
        updatedAt = :updatedAt,
        errorMessage = :errorMessage
    WHERE queueId = :queueId
    """
  )
  suspend fun markFailed(
    queueId: String,
    errorMessage: String? = null,
    attemptAt: Long = System.currentTimeMillis(),
    updatedAt: Long = attemptAt,
    failedStatus: SyncQueueStatus = SyncQueueStatus.FAILED
  ): Int

  @Query(
    """
    UPDATE sync_queue
    SET status = :completedStatus,
        lastAttemptAt = COALESCE(lastAttemptAt, :completedAt),
        updatedAt = :completedAt,
        errorMessage = NULL
    WHERE queueId = :queueId
    """
  )
  suspend fun markCompleted(
    queueId: String,
    completedAt: Long = System.currentTimeMillis(),
    completedStatus: SyncQueueStatus = SyncQueueStatus.COMPLETED
  ): Int

  @Query(
    """
    DELETE FROM sync_queue
    WHERE queueId = :queueId
      AND status = :completedStatus
    """
  )
  suspend fun deleteCompletedItem(
    queueId: String,
    completedStatus: SyncQueueStatus = SyncQueueStatus.COMPLETED
  ): Int

  @Query(
    """
    DELETE FROM sync_queue
    WHERE status = :completedStatus
    """
  )
  suspend fun deleteAllCompletedItems(
    completedStatus: SyncQueueStatus = SyncQueueStatus.COMPLETED
  ): Int

  @Query("DELETE FROM sync_queue WHERE queueId = :queueId")
  suspend fun deleteItemById(queueId: String): Int

  @Query("DELETE FROM sync_queue")
  suspend fun clearAll(): Int

  @Query("SELECT COUNT(*) FROM sync_queue WHERE status = :pendingStatus")
  suspend fun countPendingItems(
    pendingStatus: SyncQueueStatus = SyncQueueStatus.PENDING
  ): Int

  @Query("SELECT COUNT(*) FROM sync_queue WHERE status = :pendingStatus")
  fun observePendingCount(
    pendingStatus: SyncQueueStatus = SyncQueueStatus.PENDING
  ): Flow<Int>
}
