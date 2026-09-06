package com.example.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.data.DynamicTimetableEntry

@Entity(tableName = "timetable_entries")
data class TimetableEntryEntity(
  @PrimaryKey val id: String,
  val subject: String,
  val batch: String,
  val startTime: String,
  val endTime: String,
  val createdAt: Long,
  val teacherName: String = "Subject Faculty",
  val isArchived: Boolean = false
) {
  fun toDomain(): DynamicTimetableEntry {
    return DynamicTimetableEntry(
      id = id,
      subject = subject,
      batch = batch,
      startTime = startTime,
      endTime = endTime,
      createdAt = createdAt,
      teacherName = teacherName
    )
  }

  companion object {
    fun fromDomain(entry: DynamicTimetableEntry): TimetableEntryEntity {
      val elapsed = System.currentTimeMillis() - entry.createdAt
      val isArchived = elapsed >= 23 * 60 * 60 * 1000L || elapsed < 0L
      return TimetableEntryEntity(
        id = entry.id,
        subject = entry.subject,
        batch = entry.batch,
        startTime = entry.startTime,
        endTime = entry.endTime,
        createdAt = entry.createdAt,
        teacherName = entry.teacherName,
        isArchived = isArchived
      )
    }
  }
}
