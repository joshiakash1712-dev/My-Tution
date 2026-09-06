package com.example.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface StudentDao {
  @Query("SELECT * FROM students ORDER BY name ASC")
  fun getAllStudents(): Flow<List<StudentEntity>>

  @Query("SELECT * FROM students WHERE id = :id LIMIT 1")
  fun getStudentById(id: String): Flow<StudentEntity?>

  @Query("SELECT * FROM students WHERE batch = :batchName")
  fun getStudentsByBatch(batchName: String): Flow<List<StudentEntity>>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertStudent(student: StudentEntity)

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertStudents(students: List<StudentEntity>)

  @Update
  suspend fun updateStudent(student: StudentEntity)

  @Delete
  suspend fun deleteStudent(student: StudentEntity)

  @Query("DELETE FROM students WHERE id = :id")
  suspend fun deleteStudentById(id: String)

  @Query("DELETE FROM students WHERE id IN (:ids)")
  suspend fun deleteStudentsByIds(ids: List<String>)

  @Query("DELETE FROM students")
  suspend fun deleteAllStudents()

  @Query("SELECT COUNT(*) FROM students")
  suspend fun getCount(): Int
}
