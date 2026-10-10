package com.example.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
  tableName = "batch_enrollments",
  foreignKeys = [
    ForeignKey(
      entity = StudentEntity::class,
      parentColumns = ["id"],
      childColumns = ["studentId"],
      onDelete = ForeignKey.RESTRICT,
      onUpdate = ForeignKey.CASCADE,
      deferred = true
    ),
    ForeignKey(
      entity = BatchEntity::class,
      parentColumns = ["id"],
      childColumns = ["batchId"],
      onDelete = ForeignKey.RESTRICT,
      onUpdate = ForeignKey.CASCADE,
      deferred = true
    )
  ],
  indices = [
    Index(value = ["studentId"]),
    Index(value = ["batchId"])
  ]
)
data class BatchEnrollmentEntity(
  @PrimaryKey val id: String,
  val studentId: String,
  val studentName: String,
  val batchId: String,
  val batchName: String,
  val className: String,
  val stream: String,
  val enrollmentDate: String,
  val feeStatus: String = "Up-to-Date",
  val status: String = "Active",
  val syncedToFirestore: Boolean = false,
  val lastUpdated: Long = System.currentTimeMillis()
)
