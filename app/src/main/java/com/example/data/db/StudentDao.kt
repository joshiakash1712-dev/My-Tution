package com.example.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface StudentDao {
  @Query("SELECT * FROM students WHERE status != 'Inactive' AND status != 'Archived' ORDER BY name ASC")
  fun getAllStudents(): Flow<List<StudentEntity>>

  @Query("SELECT * FROM students ORDER BY name ASC")
  fun getAllStudentsIncludingInactive(): Flow<List<StudentEntity>>

  @Query("SELECT * FROM students WHERE id = :id LIMIT 1")
  fun getStudentById(id: String): Flow<StudentEntity?>

  @Query("SELECT * FROM students WHERE id = :id LIMIT 1")
  suspend fun getStudentByIdOnce(id: String): StudentEntity?

  @Query("SELECT * FROM students WHERE batch = :batchName AND status != 'Inactive' AND status != 'Archived'")
  fun getStudentsByBatch(batchName: String): Flow<List<StudentEntity>>

  @Upsert
  suspend fun insertStudent(student: StudentEntity)

  @Insert(onConflict = OnConflictStrategy.IGNORE)
  suspend fun insertStudentIgnore(student: StudentEntity)

  @Upsert
  suspend fun insertStudents(students: List<StudentEntity>)

  @Update
  suspend fun updateStudent(student: StudentEntity)

  @Query("UPDATE students SET status = 'Inactive', isActivated = 0, lastUpdated = :timestamp WHERE id = :id")
  suspend fun deactivateStudentById(id: String, timestamp: Long = System.currentTimeMillis())

  @Query("UPDATE students SET status = 'Inactive', isActivated = 0, lastUpdated = :timestamp WHERE id IN (:ids)")
  suspend fun deactivateStudentsByIds(ids: List<String>, timestamp: Long = System.currentTimeMillis())

  @Delete
  suspend fun deleteStudent(student: StudentEntity)

  @Query("DELETE FROM students WHERE id = :id")
  suspend fun deleteStudentById(id: String)

  @Query("DELETE FROM students WHERE id IN (:ids)")
  suspend fun deleteStudentsByIds(ids: List<String>)

  @Query("DELETE FROM students")
  suspend fun deleteAllStudents()

  @Query("SELECT COUNT(*) FROM students WHERE status != 'Inactive' AND status != 'Archived'")
  suspend fun getCount(): Int

  @Query("SELECT COUNT(*) FROM students")
  suspend fun getTotalCountIncludingInactive(): Int
}
