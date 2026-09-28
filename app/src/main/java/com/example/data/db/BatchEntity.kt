package com.example.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.data.Batch

@Entity(tableName = "batches")
data class BatchEntity(
  @PrimaryKey val id: String,
  val name: String,
  val schedule: String,
  val studentCount: Int,
  val teacherName: String,
  val teacherId: String = "",
  val instituteId: String = "FSI_MAIN",
  val syncedToFirestore: Boolean = false,
  val lastUpdated: Long = System.currentTimeMillis()
) {
  fun toDomain(): Batch {
    return Batch(
      id = id,
      name = name,
      schedule = schedule,
      studentCount = studentCount,
      teacherName = teacherName,
      teacherId = teacherId,
      instituteId = instituteId
    )
  }

  companion object {
    fun fromDomain(batch: Batch, synced: Boolean = false): BatchEntity {
      return BatchEntity(
        id = batch.id,
        name = batch.name,
        schedule = batch.schedule,
        studentCount = batch.studentCount,
        teacherName = batch.teacherName,
        teacherId = batch.teacherId,
        instituteId = batch.instituteId,
        syncedToFirestore = synced
      )
    }
  }
}
