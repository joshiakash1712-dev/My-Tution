package com.example.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface BatchDao {
  @Query("SELECT * FROM batches ORDER BY name ASC")
  fun getAllBatches(): Flow<List<BatchEntity>>

  @Query("SELECT * FROM batches WHERE id = :id LIMIT 1")
  fun getBatchById(id: String): Flow<BatchEntity?>

  @Query("SELECT * FROM batches WHERE id = :id LIMIT 1")
  suspend fun getBatchByIdOnce(id: String): BatchEntity?

  @Upsert
  suspend fun insertBatch(batch: BatchEntity)

  @Insert(onConflict = OnConflictStrategy.IGNORE)
  suspend fun insertBatchIgnore(batch: BatchEntity)

  @Upsert
  suspend fun insertBatches(batches: List<BatchEntity>)

  @Update
  suspend fun updateBatch(batch: BatchEntity)

  @Query("UPDATE batches SET teacherId = '', teacherName = 'Unassigned' WHERE teacherId = :teacherId")
  suspend fun unassignTeacherFromBatches(teacherId: String)

  @Query("DELETE FROM batches WHERE id = :id")
  suspend fun deleteBatchById(id: String)

  @Query("DELETE FROM batches")
  suspend fun deleteAllBatches()

  @Query("SELECT COUNT(*) FROM batches")
  suspend fun getCount(): Int
}
