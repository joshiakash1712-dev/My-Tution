package com.example.data.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Update
import com.example.data.FeeStructure
import kotlinx.coroutines.flow.Flow

/**
 * Room persistence entity for course/grade fee schedules, installment rules, and academic pricing.
 */
@Entity(
  tableName = "fee_structures",
  indices = [
    Index(value = ["courseId"]),
    Index(value = ["grade"]),
    Index(value = ["stream"]),
    Index(value = ["instituteId"])
  ]
)
data class FeeStructureEntity(
  @PrimaryKey val id: String,
  val courseId: String,
  val courseName: String,
  val grade: String = "12th",
  val stream: String = "Science",
  val baseTuitionFee: Double = 40000.0,
  val examFee: Double = 3000.0,
  val materialFee: Double = 2000.0,
  val totalFee: Double = 45000.0,
  val installmentCount: Int = 3,
  val installmentIntervalMonths: Int = 3,
  val allowDiscount: Boolean = true,
  val academicYear: String = "2026–2027",
  val instituteId: String = "FSI_MAIN",
  val createdAt: Long = System.currentTimeMillis()
) {
  fun toDomain(): FeeStructure {
    return FeeStructure(
      id = id,
      courseId = courseId,
      courseName = courseName,
      grade = grade,
      stream = stream,
      baseTuitionFee = baseTuitionFee,
      examFee = examFee,
      materialFee = materialFee,
      totalFee = totalFee,
      installmentCount = installmentCount,
      installmentIntervalMonths = installmentIntervalMonths,
      allowDiscount = allowDiscount,
      academicYear = academicYear,
      instituteId = instituteId,
      createdAt = createdAt
    )
  }

  companion object {
    fun fromDomain(structure: FeeStructure): FeeStructureEntity {
      return FeeStructureEntity(
        id = structure.id,
        courseId = structure.courseId,
        courseName = structure.courseName,
        grade = structure.grade,
        stream = structure.stream,
        baseTuitionFee = structure.baseTuitionFee,
        examFee = structure.examFee,
        materialFee = structure.materialFee,
        totalFee = structure.totalFee,
        installmentCount = structure.installmentCount,
        installmentIntervalMonths = structure.installmentIntervalMonths,
        allowDiscount = structure.allowDiscount,
        academicYear = structure.academicYear,
        instituteId = structure.instituteId,
        createdAt = structure.createdAt
      )
    }
  }
}

@Dao
interface FeeStructureDao {
  @Query("SELECT * FROM fee_structures ORDER BY courseName ASC")
  fun getAllFeeStructures(): Flow<List<FeeStructureEntity>>

  @Query("SELECT * FROM fee_structures WHERE instituteId = :instituteId ORDER BY courseName ASC")
  fun getFeeStructuresByInstitute(instituteId: String): Flow<List<FeeStructureEntity>>

  @Query("SELECT * FROM fee_structures WHERE id = :structureId LIMIT 1")
  suspend fun getFeeStructureById(structureId: String): FeeStructureEntity?

  @Query("SELECT * FROM fee_structures WHERE courseId = :courseId LIMIT 1")
  suspend fun getFeeStructureByCourse(courseId: String): FeeStructureEntity?

  @Query("SELECT * FROM fee_structures WHERE grade = :grade")
  fun getFeeStructuresByGrade(grade: String): Flow<List<FeeStructureEntity>>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertFeeStructure(structure: FeeStructureEntity)

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertFeeStructures(structures: List<FeeStructureEntity>)

  @Update
  suspend fun updateFeeStructure(structure: FeeStructureEntity)

  @Query("DELETE FROM fee_structures WHERE id = :structureId")
  suspend fun deleteFeeStructureById(structureId: String)

  @Query("DELETE FROM fee_structures")
  suspend fun deleteAllFeeStructures()
}
