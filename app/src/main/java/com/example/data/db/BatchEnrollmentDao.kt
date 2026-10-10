package com.example.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface BatchEnrollmentDao {
  @Query("SELECT * FROM batch_enrollments ORDER BY enrollmentDate DESC")
  fun getAllEnrollments(): Flow<List<BatchEnrollmentEntity>>

  @Query("SELECT * FROM batch_enrollments WHERE studentId = :studentId")
  fun getEnrollmentsByStudent(studentId: String): Flow<List<BatchEnrollmentEntity>>

  @Query("SELECT * FROM batch_enrollments WHERE batchId = :batchId")
  fun getEnrollmentsByBatch(batchId: String): Flow<List<BatchEnrollmentEntity>>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertEnrollment(enrollment: BatchEnrollmentEntity)

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertEnrollments(enrollments: List<BatchEnrollmentEntity>)

  @Query("UPDATE batch_enrollments SET status = 'Inactive', lastUpdated = :timestamp WHERE studentId = :studentId")
  suspend fun deactivateEnrollmentsByStudent(studentId: String, timestamp: Long = System.currentTimeMillis())

  @Query("DELETE FROM batch_enrollments WHERE studentId = :studentId AND batchId = :batchId")
  suspend fun deleteEnrollment(studentId: String, batchId: String)

  @Query("DELETE FROM batch_enrollments WHERE studentId = :studentId")
  suspend fun deleteEnrollmentsByStudent(studentId: String)

  @Query("DELETE FROM batch_enrollments")
  suspend fun deleteAllEnrollments()

  @Query("SELECT COUNT(*) FROM batch_enrollments")
  suspend fun getCount(): Int
}
