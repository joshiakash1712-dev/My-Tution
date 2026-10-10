package com.example.data.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Update
import com.example.data.Course
import kotlinx.coroutines.flow.Flow

/**
 * Room persistence entity for academic courses and coaching programs.
 */
@Entity(
  tableName = "courses",
  indices = [
    Index(value = ["grade"]),
    Index(value = ["stream"]),
    Index(value = ["instituteId"]),
    Index(value = ["leadInstructorId"])
  ]
)
data class CourseEntity(
  @PrimaryKey val id: String,
  val name: String,
  val code: String,
  val description: String,
  val grade: String,
  val stream: String,
  val subjectsSerialized: String,
  val totalFee: Double,
  val durationMonths: Int,
  val hoursPerWeek: Int,
  val leadInstructorId: String = "",
  val leadInstructorName: String = "",
  val status: String = "Active", // "Active" | "Upcoming" | "Archived"
  val maxCapacity: Int = 40,
  val enrolledStudentCount: Int = 0,
  val syllabusChaptersSerialized: String = "",
  val instituteId: String = "FSI_MAIN",
  val syncedToFirestore: Boolean = false,
  val createdAt: Long = System.currentTimeMillis(),
  val updatedAt: Long = System.currentTimeMillis()
) {
  fun toDomain(): Course {
    val subjectsList = if (subjectsSerialized.isBlank()) {
      emptyList()
    } else {
      subjectsSerialized.split(",").map { it.trim() }.filter { it.isNotEmpty() }
    }

    val chaptersList = if (syllabusChaptersSerialized.isBlank()) {
      emptyList()
    } else {
      syllabusChaptersSerialized.split("||").map { it.trim() }.filter { it.isNotEmpty() }
    }

    return Course(
      id = id,
      name = name,
      code = code,
      description = description,
      grade = grade,
      stream = stream,
      subjects = subjectsList,
      totalFee = totalFee,
      durationMonths = durationMonths,
      hoursPerWeek = hoursPerWeek,
      leadInstructorId = leadInstructorId,
      leadInstructorName = leadInstructorName,
      status = status,
      maxCapacity = maxCapacity,
      enrolledStudentCount = enrolledStudentCount,
      syllabusChapters = chaptersList,
      instituteId = instituteId,
      createdAt = createdAt,
      updatedAt = updatedAt
    )
  }

  companion object {
    fun fromDomain(course: Course, synced: Boolean = false): CourseEntity {
      return CourseEntity(
        id = course.id,
        name = course.name,
        code = course.code,
        description = course.description,
        grade = course.grade,
        stream = course.stream,
        subjectsSerialized = course.subjects.joinToString(","),
        totalFee = course.totalFee,
        durationMonths = course.durationMonths,
        hoursPerWeek = course.hoursPerWeek,
        leadInstructorId = course.leadInstructorId,
        leadInstructorName = course.leadInstructorName,
        status = course.status,
        maxCapacity = course.maxCapacity,
        enrolledStudentCount = course.enrolledStudentCount,
        syllabusChaptersSerialized = course.syllabusChapters.joinToString("||"),
        instituteId = course.instituteId,
        syncedToFirestore = synced,
        createdAt = course.createdAt,
        updatedAt = course.updatedAt
      )
    }
  }
}

@Dao
interface CourseDao {
  @Query("SELECT * FROM courses ORDER BY name ASC")
  fun getAllCourses(): Flow<List<CourseEntity>>

  @Query("SELECT * FROM courses WHERE instituteId = :instituteId ORDER BY name ASC")
  fun getCoursesByInstitute(instituteId: String): Flow<List<CourseEntity>>

  @Query("SELECT * FROM courses WHERE id = :courseId LIMIT 1")
  suspend fun getCourseById(courseId: String): CourseEntity?

  @Query("SELECT * FROM courses WHERE grade = :grade ORDER BY name ASC")
  fun getCoursesByGrade(grade: String): Flow<List<CourseEntity>>

  @Query("SELECT * FROM courses WHERE stream = :stream ORDER BY name ASC")
  fun getCoursesByStream(stream: String): Flow<List<CourseEntity>>

  @Query("SELECT COUNT(*) FROM courses")
  suspend fun getCourseCount(): Int

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertCourse(course: CourseEntity)

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertCourses(courses: List<CourseEntity>)

  @Update
  suspend fun updateCourse(course: CourseEntity)

  @Query("DELETE FROM courses WHERE id = :courseId")
  suspend fun deleteCourseById(courseId: String)

  @Query("DELETE FROM courses")
  suspend fun deleteAllCourses()
}
