package com.example.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface FeePaymentDao {

  @Query("SELECT * FROM fee_payments ORDER BY timestamp DESC")
  fun getAllPayments(): Flow<List<FeePaymentEntity>>

  @Query("SELECT * FROM fee_payments WHERE studentId = :studentId ORDER BY timestamp DESC")
  fun getPaymentsByStudent(studentId: String): Flow<List<FeePaymentEntity>>

  @Query("SELECT * FROM fee_payments WHERE paymentStatus = :status ORDER BY timestamp DESC")
  fun getPaymentsByStatus(status: String): Flow<List<FeePaymentEntity>>

  @Query("SELECT * FROM fee_payments WHERE month = :month ORDER BY timestamp DESC")
  fun getPaymentsByMonth(month: String): Flow<List<FeePaymentEntity>>

  @Query("SELECT * FROM fee_payments WHERE id = :id LIMIT 1")
  suspend fun getPaymentById(id: String): FeePaymentEntity?

  @Query("SELECT SUM(amount) FROM fee_payments WHERE paymentStatus IN ('Paid', 'Completed')")
  fun getTotalCollected(): Flow<Double?>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertPayment(payment: FeePaymentEntity)

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertPayments(payments: List<FeePaymentEntity>)

  @Update
  suspend fun updatePayment(payment: FeePaymentEntity)

  @Delete
  suspend fun deletePayment(payment: FeePaymentEntity)

  @Query("DELETE FROM fee_payments WHERE id = :id")
  suspend fun deletePaymentById(id: String)

  @Query("DELETE FROM fee_payments")
  suspend fun deleteAllPayments()

  @Query("SELECT COUNT(*) FROM fee_payments")
  suspend fun getCount(): Int
}
