package com.example.data.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Update
import com.example.data.CourseEnrollment
import kotlinx.coroutines.flow.Flow

/**
 * Room persistence entity for student enrollment in courses.
 */
@Entity(
  tableName = "course_enrollments",
  foreignKeys = [
    ForeignKey(
      entity = StudentEntity::class,
      parentColumns = ["id"],
      childColumns = ["studentId"],
      onDelete = ForeignKey.RESTRICT,
      onUpdate = ForeignKey.CASCADE,
      deferred = true
    )
  ],
  indices = [
    Index(value = ["studentId"]),
    Index(value = ["courseId"]),
    Index(value = ["batchId"]),
    Index(value = ["instituteId"])
  ]
)
data class CourseEnrollmentEntity(
  @PrimaryKey val enrollmentId: String,
  val studentId: String,
  val courseId: String,
  val batchId: String = "",
  val enrolledDate: String = "",
  val status: String = "Active", // "Active" | "Completed" | "Withdrawn"
  val discountPercent: Double = 0.0,
  val netFee: Double = 0.0,
  val instituteId: String = "FSI_MAIN",
  val createdAt: Long = System.currentTimeMillis()
) {
  fun toDomain(): CourseEnrollment {
    return CourseEnrollment(
      enrollmentId = enrollmentId,
      studentId = studentId,
      courseId = courseId,
      batchId = batchId,
      enrolledDate = enrolledDate,
      status = status,
      discountPercent = discountPercent,
      netFee = netFee,
      instituteId = instituteId,
      createdAt = createdAt
    )
  }

  companion object {
    fun fromDomain(enrollment: CourseEnrollment): CourseEnrollmentEntity {
      return CourseEnrollmentEntity(
        enrollmentId = enrollment.enrollmentId,
        studentId = enrollment.studentId,
        courseId = enrollment.courseId,
        batchId = enrollment.batchId,
        enrolledDate = enrollment.enrolledDate,
        status = enrollment.status,
        discountPercent = enrollment.discountPercent,
        netFee = enrollment.netFee,
        instituteId = enrollment.instituteId,
        createdAt = enrollment.createdAt
      )
    }
  }
}

@Dao
interface CourseEnrollmentDao {
  @Query("SELECT * FROM course_enrollments ORDER BY createdAt DESC")
  fun getAllEnrollments(): Flow<List<CourseEnrollmentEntity>>

  @Query("SELECT * FROM course_enrollments WHERE studentId = :studentId")
  fun getEnrollmentsByStudent(studentId: String): Flow<List<CourseEnrollmentEntity>>

  @Query("SELECT * FROM course_enrollments WHERE courseId = :courseId")
  fun getEnrollmentsByCourse(courseId: String): Flow<List<CourseEnrollmentEntity>>

  @Query("SELECT * FROM course_enrollments WHERE enrollmentId = :enrollmentId LIMIT 1")
  suspend fun getEnrollmentById(enrollmentId: String): CourseEnrollmentEntity?

  @Query("SELECT COUNT(*) FROM course_enrollments WHERE courseId = :courseId AND status = 'Active'")
  suspend fun getActiveStudentCountInCourse(courseId: String): Int

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertEnrollment(enrollment: CourseEnrollmentEntity)

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertEnrollments(enrollments: List<CourseEnrollmentEntity>)

  @Update
  suspend fun updateEnrollment(enrollment: CourseEnrollmentEntity)

  @Query("DELETE FROM course_enrollments WHERE enrollmentId = :enrollmentId")
  suspend fun deleteEnrollmentById(enrollmentId: String)

  @Query("DELETE FROM course_enrollments WHERE studentId = :studentId")
  suspend fun deleteEnrollmentsByStudent(studentId: String)
}
