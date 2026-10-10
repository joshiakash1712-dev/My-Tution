package com.example.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface AttendanceDao {

  @Query("SELECT * FROM attendance_records ORDER BY timestamp DESC")
  fun getAllAttendance(): Flow<List<AttendanceRecordEntity>>

  @Query("SELECT * FROM attendance_records WHERE date = :date ORDER BY timestamp DESC")
  fun getAttendanceByDate(date: String): Flow<List<AttendanceRecordEntity>>

  @Query("SELECT * FROM attendance_records WHERE day = :day AND month = :month AND year = :year ORDER BY timestamp DESC")
  fun getAttendanceByDateComponents(day: Int, month: Int, year: Int): Flow<List<AttendanceRecordEntity>>

  @Query("SELECT * FROM attendance_records WHERE studentId = :studentId ORDER BY timestamp DESC")
  fun getAttendanceByStudent(studentId: String): Flow<List<AttendanceRecordEntity>>

  @Query("SELECT * FROM attendance_records WHERE batchName = :batchName ORDER BY timestamp DESC")
  fun getAttendanceByBatch(batchName: String): Flow<List<AttendanceRecordEntity>>

  @Query("SELECT * FROM attendance_records WHERE id = :id LIMIT 1")
  suspend fun getAttendanceById(id: String): AttendanceRecordEntity?

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertAttendance(record: AttendanceRecordEntity)

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertAttendanceList(records: List<AttendanceRecordEntity>)

  @Update
  suspend fun updateAttendance(record: AttendanceRecordEntity)

  @Delete
  suspend fun deleteAttendance(record: AttendanceRecordEntity)

  @Query("DELETE FROM attendance_records WHERE id = :id")
  suspend fun deleteAttendanceById(id: String)

  @Query("DELETE FROM attendance_records")
  suspend fun deleteAllAttendance()

  @Query("SELECT COUNT(*) FROM attendance_records")
  suspend fun getCount(): Int
}
