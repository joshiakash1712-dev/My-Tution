package com.example.utils

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.core.content.FileProvider
import com.example.data.AttendanceRecord
import com.example.data.Batch
import com.example.data.FeeRecord
import com.example.data.Student
import com.example.data.StudentFeePayment
import com.example.data.db.AppRoomDatabase
import com.example.data.db.BatchEnrollmentEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ExportUtils {

    data class RoomExportResult(
        val file: File,
        val filePath: String,
        val jsonString: String,
        val studentCount: Int,
        val batchCount: Int,
        val enrollmentCount: Int,
        val attendanceCount: Int,
        val paymentCount: Int,
        val fileSizeBytes: Long,
        val formattedSize: String
    )

    private fun escapeCsv(value: String): String {
        val needsQuotes = value.contains(",") || value.contains("\n") || value.contains("\r") || value.contains("\"")
        return if (needsQuotes) {
            "\"" + value.replace("\"", "\"\"") + "\""
        } else {
            value
        }
    }

    fun shareFile(context: Context, file: File, mimeType: String, subject: String, title: String) {
        try {
            val authority = "${context.packageName}.fileprovider"
            val fileUri = FileProvider.getUriForFile(context, authority, file)

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = mimeType
                putExtra(Intent.EXTRA_STREAM, fileUri)
                putExtra(Intent.EXTRA_SUBJECT, subject)
                putExtra(Intent.EXTRA_TEXT, "Room Database JSON Export attached (${file.name}, ${file.length()} bytes).")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            val chooser = Intent.createChooser(shareIntent, title)
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooser)
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "Failed to share file: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
        }
    }

    private fun shareCsvFile(context: Context, filename: String, csvContent: String) {
        try {
            val cacheFile = File(context.cacheDir, filename)
            FileWriter(cacheFile).use { writer ->
                writer.write(csvContent)
            }
            shareFile(context, cacheFile, "text/csv", "Backup Export - $filename", "Export Report & Backup")
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "Failed to export data: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
        }
    }

    fun exportAttendanceToCsv(context: Context, records: List<AttendanceRecord>) {
        val header = "Record ID,Date,Student ID,Student Name,Batch Name,Status,Timestamp\n"
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val body = records.joinToString("\n") { r ->
            val dateStr = sdf.format(Date(r.timestamp))
            listOf(
                r.id,
                r.date,
                r.studentId,
                r.studentName,
                r.batchName,
                r.status,
                dateStr
            ).joinToString(",") { escapeCsv(it) }
        }

        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val filename = "attendance_export_$timestamp.csv"
        shareCsvFile(context, filename, header + body)
    }

    fun exportFeesToCsv(context: Context, records: List<FeeRecord>) {
        val header = "Fee Record ID,Student Name,Fee Amount (INR),Paid Amount (INR),Pending Amount (INR),Due Date,Status,Month,Reminders Sent,Last Reminded\n"
        val body = records.joinToString("\n") { r ->
            listOf(
                r.id,
                r.studentName,
                r.feeAmount.toString(),
                r.paidAmount.toString(),
                r.pendingAmount.toString(),
                r.dueDate,
                r.paymentStatus,
                r.month,
                r.remindedCount.toString(),
                r.lastReminded
            ).joinToString(",") { escapeCsv(it) }
        }

        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val filename = "fee_records_export_$timestamp.csv"
        shareCsvFile(context, filename, header + body)
    }

    /**
     * Exports all tables of the Room SQLite Database to a structured, readable, indented JSON file
     * saved directly into the application's local cache directory (context.cacheDir).
     */
    suspend fun exportRoomDatabaseToJson(
        context: Context,
        students: List<Student>,
        batches: List<Batch>,
        enrollments: List<BatchEnrollmentEntity>,
        attendance: List<AttendanceRecord>,
        feePayments: List<StudentFeePayment>
    ): RoomExportResult = withContext(Dispatchers.IO) {
        val root = JSONObject()
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val now = Date()

        // Metadata Header
        val meta = JSONObject()
        meta.put("application", "My Tuition (FSI)")
        meta.put("databaseName", "fsi_tuition_room.db")
        meta.put("databaseVersion", 3)
        meta.put("exportedAt", sdf.format(now))
        meta.put("timestampMillis", now.time)
        meta.put("environment", "Android Room SQLite (Offline-First)")

        val summary = JSONObject()
        summary.put("totalStudents", students.size)
        summary.put("totalBatches", batches.size)
        summary.put("totalEnrollments", enrollments.size)
        summary.put("totalAttendanceRecords", attendance.size)
        summary.put("totalFeePayments", feePayments.size)
        meta.put("recordsSummary", summary)

        root.put("_exportMetadata", meta)

        // 1. Students Table
        val studentsArray = JSONArray()
        students.forEach { s ->
            val obj = JSONObject()
            obj.put("id", s.id)
            obj.put("name", s.name)
            obj.put("className", s.className)
            obj.put("stream", s.stream)
            obj.put("batch", s.batch)
            obj.put("mobile", s.mobile)
            obj.put("parentName", s.parentName)
            obj.put("parentContact", s.parentContact)
            obj.put("admissionDate", s.admissionDate)
            obj.put("status", s.status)
            obj.put("email", s.email)
            obj.put("gender", s.gender)
            obj.put("dob", s.dob)
            obj.put("address", s.address)
            obj.put("school", s.school)
            obj.put("attendancePercent", s.attendancePercent)
            obj.put("overallAvg", s.overallAvg)
            obj.put("rank", s.rank)
            obj.put("strongestSubject", s.strongestSubject)
            obj.put("weakestSubject", s.weakestSubject)
            studentsArray.put(obj)
        }
        root.put("students", studentsArray)

        // 2. Batches Table
        val batchesArray = JSONArray()
        batches.forEach { b ->
            val obj = JSONObject()
            obj.put("id", b.id)
            obj.put("name", b.name)
            obj.put("schedule", b.schedule)
            obj.put("studentCount", b.studentCount)
            obj.put("teacherName", b.teacherName)
            batchesArray.put(obj)
        }
        root.put("batches", batchesArray)

        // 3. Batch Enrollments Table
        val enrollmentsArray = JSONArray()
        enrollments.forEach { e ->
            val obj = JSONObject()
            obj.put("id", e.id)
            obj.put("studentId", e.studentId)
            obj.put("studentName", e.studentName)
            obj.put("batchId", e.batchId)
            obj.put("batchName", e.batchName)
            obj.put("className", e.className)
            obj.put("stream", e.stream)
            obj.put("enrollmentDate", e.enrollmentDate)
            obj.put("feeStatus", e.feeStatus)
            obj.put("status", e.status)
            obj.put("lastUpdated", e.lastUpdated)
            enrollmentsArray.put(obj)
        }
        root.put("batchEnrollments", enrollmentsArray)

        // 4. Attendance Records Table
        val attendanceArray = JSONArray()
        attendance.forEach { a ->
            val obj = JSONObject()
            obj.put("id", a.id)
            obj.put("date", a.date)
            obj.put("studentId", a.studentId)
            obj.put("studentName", a.studentName)
            obj.put("batchName", a.batchName)
            obj.put("status", a.status)
            obj.put("reason", a.reason ?: "")
            obj.put("recordedBy", a.recordedBy ?: "")
            obj.put("timestamp", a.timestamp)
            obj.put("day", a.day)
            obj.put("month", a.month)
            obj.put("monthName", a.monthName)
            obj.put("year", a.year)
            obj.put("time", a.time)
            attendanceArray.put(obj)
        }
        root.put("attendanceRecords", attendanceArray)

        // 5. Fee Payments Table
        val feePaymentsArray = JSONArray()
        feePayments.forEach { p ->
            val obj = JSONObject()
            obj.put("id", p.id)
            obj.put("studentId", p.studentId)
            obj.put("studentName", p.studentName)
            obj.put("amount", p.amount)
            obj.put("paymentDate", p.paymentDate)
            obj.put("paymentStatus", p.paymentStatus)
            obj.put("feeType", p.feeType)
            obj.put("paymentMode", p.paymentMode)
            obj.put("receiptNo", p.receiptNo)
            obj.put("month", p.month)
            obj.put("remarks", p.remarks)
            obj.put("recordedBy", p.recordedBy)
            obj.put("timestamp", p.timestamp)
            feePaymentsArray.put(obj)
        }
        root.put("feePayments", feePaymentsArray)

        // Pretty print with 2-space indentation
        val formattedJson = root.toString(2)

        // Write to application's local cache directory
        val fileTimestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(now)
        val cacheFile = File(context.cacheDir, "fsi_room_database_export_$fileTimestamp.json")
        cacheFile.writeText(formattedJson)

        val bytes = cacheFile.length()
        val formattedSize = when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> String.format(Locale.getDefault(), "%.1f KB", bytes / 1024.0)
            else -> String.format(Locale.getDefault(), "%.2f MB", bytes / (1024.0 * 1024.0))
        }

        RoomExportResult(
            file = cacheFile,
            filePath = cacheFile.absolutePath,
            jsonString = formattedJson,
            studentCount = students.size,
            batchCount = batches.size,
            enrollmentCount = enrollments.size,
            attendanceCount = attendance.size,
            paymentCount = feePayments.size,
            fileSizeBytes = bytes,
            formattedSize = formattedSize
        )
    }

    /**
     * Direct query snapshot helper to export the entire Room Database from DAOs
     */
    suspend fun exportRoomDatabaseFromLocalDb(context: Context): RoomExportResult = withContext(Dispatchers.IO) {
        val db = AppRoomDatabase.getDatabase(context)
        val students = db.studentDao().getAllStudents().first().map { it.toDomain() }
        val batches = db.batchDao().getAllBatches().first().map { it.toDomain() }
        val enrollments = db.batchEnrollmentDao().getAllEnrollments().first()
        val attendance = db.attendanceDao().getAllAttendance().first().map { it.toDomain() }
        val payments = db.feePaymentDao().getAllPayments().first().map { it.toDomain() }

        exportRoomDatabaseToJson(context, students, batches, enrollments, attendance, payments)
    }
}

