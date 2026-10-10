package com.example.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.data.AttendanceRecord
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Entity(
  tableName = "attendance_records",
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
    Index(value = ["batchId"]),
    Index(value = ["date"]),
    Index(value = ["instituteId"])
  ]
)
data class AttendanceRecordEntity(
  @PrimaryKey val id: String,
  val date: String, // "dd/MM/yyyy"
  val day: Int, // 1..31
  val month: Int, // 1..12
  val monthName: String, // "August"
  val year: Int, // 2026
  val time: String, // "11:15 AM"
  val studentId: String,
  val studentName: String,
  val batchName: String,
  val status: String, // "Present" | "Absent" | "Late"
  val timestamp: Long = System.currentTimeMillis(),
  val lastModifiedTimestamp: Long = System.currentTimeMillis(),
  val reason: String? = null,
  val recordedBy: String? = null,
  val batchId: String = "",
  val instituteId: String = "FSI_MAIN"
) {
  fun toDomain(): AttendanceRecord {
    return AttendanceRecord(
      id = id,
      date = date,
      day = day,
      month = month,
      monthName = monthName,
      year = year,
      time = time,
      studentId = studentId,
      studentName = studentName,
      batchName = batchName,
      status = status,
      timestamp = timestamp,
      lastModifiedTimestamp = lastModifiedTimestamp,
      reason = reason,
      recordedBy = recordedBy,
      batchId = batchId,
      instituteId = instituteId
    )
  }

  companion object {
    fun buildDeterministicId(dateStr: String, batchName: String, studentId: String): String {
      val cleanDate = dateStr.replace("/", "").replace("-", "").trim()
      val cleanBatch = batchName.replace(Regex("[^A-Za-z0-9]"), "").uppercase()
      return "ATT_${cleanDate}_${cleanBatch}_${studentId.trim()}"
    }

    fun fromDomain(record: AttendanceRecord): AttendanceRecordEntity {
      val (d, m, mName, y, t) = parseDateAndTimeString(record.date, record.timestamp)
      val deterministicId = if (record.id.startsWith("ATT_") && record.id.count { it == '_' } >= 3) {
        record.id
      } else {
        buildDeterministicId(record.date, record.batchName, record.studentId)
      }
      return AttendanceRecordEntity(
        id = deterministicId,
        date = record.date,
        day = if (record.day > 0) record.day else d,
        month = if (record.month > 0) record.month else m,
        monthName = if (record.monthName.isNotBlank()) record.monthName else mName,
        year = if (record.year > 0) record.year else y,
        time = if (record.time.isNotBlank()) record.time else t,
        studentId = record.studentId,
        studentName = record.studentName,
        batchName = record.batchName,
        status = record.status,
        timestamp = record.timestamp,
        lastModifiedTimestamp = record.lastModifiedTimestamp,
        reason = record.reason,
        recordedBy = record.recordedBy,
        batchId = record.batchId,
        instituteId = record.instituteId
      )
    }

    fun parseDateAndTimeString(dateStr: String, timestamp: Long): ParsedDateDetails {
      var day = 1
      var month = 1
      var monthName = "January"
      var year = 2026
      var timeStr = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(timestamp))

      try {
        val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        val parsedDate = sdf.parse(dateStr)
        if (parsedDate != null) {
          val cal = Calendar.getInstance()
          cal.time = parsedDate
          day = cal.get(Calendar.DAY_OF_MONTH)
          month = cal.get(Calendar.MONTH) + 1
          monthName = SimpleDateFormat("MMMM", Locale.getDefault()).format(parsedDate)
          year = cal.get(Calendar.YEAR)
        }
      } catch (e: Exception) {
        val cal = Calendar.getInstance()
        cal.timeInMillis = timestamp
        day = cal.get(Calendar.DAY_OF_MONTH)
        month = cal.get(Calendar.MONTH) + 1
        monthName = SimpleDateFormat("MMMM", Locale.getDefault()).format(cal.time)
        year = cal.get(Calendar.YEAR)
      }

      return ParsedDateDetails(day, month, monthName, year, timeStr)
    }
  }
}

data class ParsedDateDetails(
  val day: Int,
  val month: Int,
  val monthName: String,
  val year: Int,
  val time: String
)
