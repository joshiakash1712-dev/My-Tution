package com.example.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.data.AppRepository
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
  val batchId: String = "",
  val teacherId: String = "",
  val subjectId: String = "",
  val instituteId: String = "FSI_MAIN",
  val dateStr: String = "",
  val scheduledStartMillis: Long = 0L,
  val scheduledEndMillis: Long = 0L,
  val isArchived: Boolean = false
) {
  fun toDomain(): DynamicTimetableEntry {
    val effectiveStartMillis = scheduledStartMillis.takeIf { it > 0L }
      ?: AppRepository.computeScheduledStartMillis(dateStr, startTime, createdAt)
    val effectiveEndMillis = scheduledEndMillis.takeIf { it > 0L }
      ?: AppRepository.computeScheduledEndMillis(dateStr, endTime, createdAt)
    val effectiveArchived = isArchived || (effectiveEndMillis > 0L && System.currentTimeMillis() > effectiveEndMillis)
    return DynamicTimetableEntry(
      id = id,
      subject = subject,
      batch = batch,
      startTime = startTime,
      endTime = endTime,
      createdAt = createdAt,
      teacherName = teacherName,
      batchId = batchId,
      teacherId = teacherId,
      subjectId = subjectId,
      instituteId = instituteId,
      dateStr = dateStr,
      scheduledStartMillis = effectiveStartMillis,
      scheduledEndMillis = effectiveEndMillis,
      isArchived = effectiveArchived
    )
  }

  companion object {
    fun fromDomain(entry: DynamicTimetableEntry): TimetableEntryEntity {
      val effectiveStartMillis = entry.scheduledStartMillis.takeIf { it > 0L }
        ?: AppRepository.computeScheduledStartMillis(entry.dateStr, entry.startTime, entry.createdAt)
      val effectiveEndMillis = entry.scheduledEndMillis.takeIf { it > 0L }
        ?: AppRepository.computeScheduledEndMillis(entry.dateStr, entry.endTime, entry.createdAt)
      val effectiveArchived = entry.isArchived || (effectiveEndMillis > 0L && System.currentTimeMillis() > effectiveEndMillis)
      return TimetableEntryEntity(
        id = entry.id,
        subject = entry.subject,
        batch = entry.batch,
        startTime = entry.startTime,
        endTime = entry.endTime,
        createdAt = entry.createdAt,
        teacherName = entry.teacherName,
        batchId = entry.batchId,
        teacherId = entry.teacherId,
        subjectId = entry.subjectId,
        instituteId = entry.instituteId,
        dateStr = entry.dateStr,
        scheduledStartMillis = effectiveStartMillis,
        scheduledEndMillis = effectiveEndMillis,
        isArchived = effectiveArchived
      )
    }
  }
}
