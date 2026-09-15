package com.example.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TeacherDao {

  @Query("SELECT * FROM teachers ORDER BY name ASC")
  fun getAllTeachers(): Flow<List<TeacherEntity>>

  @Query("SELECT * FROM teachers WHERE id = :id LIMIT 1")
  fun getTeacherById(id: String): Flow<TeacherEntity?>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertTeacher(teacher: TeacherEntity)

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertTeachers(teachers: List<TeacherEntity>)

  @Update
  suspend fun updateTeacher(teacher: TeacherEntity)

  @Delete
  suspend fun deleteTeacher(teacher: TeacherEntity)

  @Query("DELETE FROM teachers WHERE id = :id")
  suspend fun deleteTeacherById(id: String)

  @Query("DELETE FROM teachers")
  suspend fun deleteAllTeachers()

  @Query("SELECT COUNT(*) FROM teachers")
  suspend fun getCount(): Int
}
