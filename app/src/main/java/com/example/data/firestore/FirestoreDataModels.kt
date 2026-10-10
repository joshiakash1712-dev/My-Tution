package com.example.data.firestore

import com.example.data.AttendanceRecord
import com.example.data.Course
import com.example.data.CourseEnrollment
import com.example.data.FeeRecord
import com.example.data.FeeStructure
import com.example.data.Student
import com.example.data.StudentFeePayment

/**
 * Type-safe Firestore Data Models for My Tuition (FSI).
 *
 * Designed in compliance with zero-trust Firestore architecture:
 * 1. Default values on every property to enable no-arg reflection constructors for .toObject()
 * 2. Type-coerced numeric parsing accommodating Firestore java.lang.Long and java.lang.Double
 * 3. Bidirectional mapping with Domain and Room entities
 * 4. Multi-tenant instituteId scoping and offline synchronization metadata
 */

// =============================================================================
// 1. FIRESTORE STUDENT DATA MODEL
// =============================================================================
data class FirestoreStudent(
  val id: String = "",
  val name: String = "",
  val photo: String = "",
  val grade: String = "",
  val subjectsEnrolled: List<String> = emptyList(),
  val contactInfo: String = "",
  val parentName: String = "",
  val parentContact: String = "",
  val parentEmail: String = "",
  val email: String = "",
  val dob: String = "",
  val gender: String = "",
  val address: String = "",
  val school: String = "",
  val className: String = "Class 12",
  val batch: String = "",
  val batchId: String = "",
  val courseId: String = "",
  val stream: String = "Science",
  val admissionDate: String = "",
  val status: String = "Active", // "Active" | "Inactive" | "Suspended"
  val attendancePercent: Int = 85,
  val overallAvg: Int = 80,
  val rank: Int = 1,
  val parentId: String = "",
  val feeStatus: String = "Pending",
  val instituteId: String = "FSI_MAIN",
  val createdAt: Long = System.currentTimeMillis(),
  val updatedAt: Long = System.currentTimeMillis(),
  val syncStatus: String = "SYNCED",
  val conflictStrategy: String = "LAST_WRITE_WINS_BY_UPDATED_AT"
) {
  fun toMap(): Map<String, Any> {
    return mapOf(
      "id" to id,
      "name" to name,
      "photo" to photo,
      "grade" to grade,
      "subjectsEnrolled" to subjectsEnrolled,
      "contactInfo" to contactInfo,
      "parentName" to parentName,
      "parentContact" to parentContact,
      "parentEmail" to parentEmail,
      "email" to email,
      "dob" to dob,
      "gender" to gender,
      "address" to address,
      "school" to school,
      "className" to className,
      "batch" to batch,
      "batchId" to batchId,
      "courseId" to courseId,
      "stream" to stream,
      "admissionDate" to admissionDate,
      "status" to status,
      "attendancePercent" to attendancePercent,
      "overallAvg" to overallAvg,
      "rank" to rank,
      "parentId" to parentId,
      "feeStatus" to feeStatus,
      "instituteId" to instituteId,
      "createdAt" to createdAt,
      "updatedAt" to updatedAt,
      "syncStatus" to syncStatus,
      "conflictStrategy" to conflictStrategy
    )
  }

  fun toDomain(existing: Student? = null): Student {
    return Student(
      id = id,
      name = name,
      photo = photo.ifBlank { existing?.photo ?: "" },
      mobile = contactInfo.ifBlank { existing?.mobile ?: "" },
      parentName = parentName.ifBlank { existing?.parentName ?: "" },
      parentContact = parentContact.ifBlank { existing?.parentContact ?: "" },
      parentEmail = parentEmail.ifBlank { existing?.parentEmail ?: "" },
      email = email.ifBlank { existing?.email ?: "${id.lowercase()}@mytuition.com" },
      dob = dob.ifBlank { existing?.dob ?: "" },
      gender = gender.ifBlank { existing?.gender ?: "" },
      address = address.ifBlank { existing?.address ?: "" },
      school = school.ifBlank { existing?.school ?: "" },
      className = className.ifBlank { existing?.className ?: "Class 12" },
      batch = batch.ifBlank { existing?.batch ?: "12th Science" },
      batchId = batchId.ifBlank { existing?.batchId ?: "" },
      courseId = courseId.ifBlank { existing?.courseId ?: "" },
      stream = stream.ifBlank { existing?.stream ?: "Science" },
      admissionDate = admissionDate.ifBlank { existing?.admissionDate ?: "" },
      status = status.ifBlank { existing?.status ?: "Active" },
      attendancePercent = attendancePercent,
      overallAvg = overallAvg,
      rank = rank,
      parentId = parentId.ifBlank { existing?.parentId ?: "PAR_$id" },
      feeStatus = feeStatus.ifBlank { existing?.feeStatus ?: "Pending" },
      instituteId = instituteId.ifBlank { existing?.instituteId ?: "FSI_MAIN" }
    )
  }

  companion object {
    fun fromDomain(student: Student, updatedAt: Long = System.currentTimeMillis(), syncStatus: String = "SYNCED"): FirestoreStudent {
      return FirestoreStudent(
        id = student.id,
        name = student.name,
        photo = student.photo,
        grade = "${student.className} (${student.stream})",
        subjectsEnrolled = listOf("Physics", "Chemistry", "Mathematics"),
        contactInfo = student.mobile,
        parentName = student.parentName,
        parentContact = student.parentContact,
        parentEmail = student.parentEmail,
        email = student.email,
        dob = student.dob,
        gender = student.gender,
        address = student.address,
        school = student.school,
        className = student.className,
        batch = student.batch,
        batchId = student.batchId,
        courseId = student.courseId,
        stream = student.stream,
        admissionDate = student.admissionDate,
        status = student.status,
        attendancePercent = student.attendancePercent,
        overallAvg = student.overallAvg,
        rank = student.rank,
        parentId = student.parentId,
        feeStatus = student.feeStatus,
        instituteId = student.instituteId.ifBlank { "FSI_MAIN" },
        createdAt = if (student.admissionTimestamp > 0L) student.admissionTimestamp else updatedAt,
        updatedAt = updatedAt,
        syncStatus = syncStatus
      )
    }

    fun fromMap(id: String, map: Map<String, Any>): FirestoreStudent {
      return FirestoreStudent(
        id = id,
        name = map["name"] as? String ?: "",
        photo = map["photo"] as? String ?: "",
        grade = map["grade"] as? String ?: "",
        subjectsEnrolled = (map["subjectsEnrolled"] as? List<*>)?.mapNotNull { it as? String } ?: emptyList(),
        contactInfo = map["contactInfo"] as? String ?: "",
        parentName = map["parentName"] as? String ?: "",
        parentContact = map["parentContact"] as? String ?: "",
        parentEmail = map["parentEmail"] as? String ?: "",
        email = map["email"] as? String ?: "",
        dob = map["dob"] as? String ?: "",
        gender = map["gender"] as? String ?: "",
        address = map["address"] as? String ?: "",
        school = map["school"] as? String ?: "",
        className = map["className"] as? String ?: "Class 12",
        batch = map["batch"] as? String ?: "",
        batchId = map["batchId"] as? String ?: "",
        courseId = map["courseId"] as? String ?: "",
        stream = map["stream"] as? String ?: "Science",
        admissionDate = map["admissionDate"] as? String ?: "",
        status = map["status"] as? String ?: "Active",
        attendancePercent = (map["attendancePercent"] as? Number)?.toInt() ?: 85,
        overallAvg = (map["overallAvg"] as? Number)?.toInt() ?: 80,
        rank = (map["rank"] as? Number)?.toInt() ?: 1,
        parentId = map["parentId"] as? String ?: "",
        feeStatus = map["feeStatus"] as? String ?: "Pending",
        instituteId = (map["instituteId"] as? String)?.ifBlank { "FSI_MAIN" } ?: "FSI_MAIN",
        createdAt = (map["createdAt"] as? Number)?.toLong() ?: System.currentTimeMillis(),
        updatedAt = (map["updatedAt"] as? Number)?.toLong() ?: System.currentTimeMillis(),
        syncStatus = map["syncStatus"] as? String ?: "SYNCED",
        conflictStrategy = map["conflictStrategy"] as? String ?: "LAST_WRITE_WINS_BY_UPDATED_AT"
      )
    }
  }
}

// =============================================================================
// 2. FIRESTORE COURSE & ENROLLMENT DATA MODELS
// =============================================================================
data class FirestoreCourse(
  val id: String = "",
  val name: String = "",
  val code: String = "",
  val description: String = "",
  val grade: String = "",
  val stream: String = "",
  val subjects: List<String> = emptyList(),
  val totalFee: Double = 0.0,
  val durationMonths: Int = 12,
  val hoursPerWeek: Int = 10,
  val leadInstructorId: String = "",
  val leadInstructorName: String = "",
  val status: String = "Active", // "Active" | "Upcoming" | "Archived"
  val maxCapacity: Int = 40,
  val enrolledStudentCount: Int = 0,
  val syllabusChapters: List<String> = emptyList(),
  val instituteId: String = "FSI_MAIN",
  val createdAt: Long = System.currentTimeMillis(),
  val updatedAt: Long = System.currentTimeMillis(),
  val syncStatus: String = "SYNCED",
  val conflictStrategy: String = "LAST_WRITE_WINS_BY_UPDATED_AT"
) {
  fun toMap(): Map<String, Any> {
    return mapOf(
      "id" to id,
      "name" to name,
      "code" to code,
      "description" to description,
      "grade" to grade,
      "stream" to stream,
      "subjects" to subjects,
      "totalFee" to totalFee,
      "durationMonths" to durationMonths,
      "hoursPerWeek" to hoursPerWeek,
      "leadInstructorId" to leadInstructorId,
      "leadInstructorName" to leadInstructorName,
      "status" to status,
      "maxCapacity" to maxCapacity,
      "enrolledStudentCount" to enrolledStudentCount,
      "syllabusChapters" to syllabusChapters,
      "instituteId" to instituteId,
      "createdAt" to createdAt,
      "updatedAt" to updatedAt,
      "syncStatus" to syncStatus,
      "conflictStrategy" to conflictStrategy
    )
  }

  fun toDomain(): Course {
    return Course(
      id = id,
      name = name,
      code = code,
      description = description,
      grade = grade,
      stream = stream,
      subjects = subjects,
      totalFee = totalFee,
      durationMonths = durationMonths,
      hoursPerWeek = hoursPerWeek,
      leadInstructorId = leadInstructorId,
      leadInstructorName = leadInstructorName,
      status = status,
      maxCapacity = maxCapacity,
      enrolledStudentCount = enrolledStudentCount,
      syllabusChapters = syllabusChapters,
      instituteId = instituteId,
      createdAt = createdAt,
      updatedAt = updatedAt
    )
  }

  companion object {
    fun fromDomain(course: Course, updatedAt: Long = System.currentTimeMillis(), syncStatus: String = "SYNCED"): FirestoreCourse {
      return FirestoreCourse(
        id = course.id,
        name = course.name,
        code = course.code,
        description = course.description,
        grade = course.grade,
        stream = course.stream,
        subjects = course.subjects,
        totalFee = course.totalFee,
        durationMonths = course.durationMonths,
        hoursPerWeek = course.hoursPerWeek,
        leadInstructorId = course.leadInstructorId,
        leadInstructorName = course.leadInstructorName,
        status = course.status,
        maxCapacity = course.maxCapacity,
        enrolledStudentCount = course.enrolledStudentCount,
        syllabusChapters = course.syllabusChapters,
        instituteId = course.instituteId.ifBlank { "FSI_MAIN" },
        createdAt = course.createdAt,
        updatedAt = updatedAt,
        syncStatus = syncStatus
      )
    }

    fun fromMap(id: String, map: Map<String, Any>): FirestoreCourse {
      return FirestoreCourse(
        id = id,
        name = map["name"] as? String ?: "",
        code = map["code"] as? String ?: "",
        description = map["description"] as? String ?: "",
        grade = map["grade"] as? String ?: "",
        stream = map["stream"] as? String ?: "",
        subjects = (map["subjects"] as? List<*>)?.mapNotNull { it as? String } ?: emptyList(),
        totalFee = (map["totalFee"] as? Number)?.toDouble() ?: 0.0,
        durationMonths = (map["durationMonths"] as? Number)?.toInt() ?: 12,
        hoursPerWeek = (map["hoursPerWeek"] as? Number)?.toInt() ?: 10,
        leadInstructorId = map["leadInstructorId"] as? String ?: "",
        leadInstructorName = map["leadInstructorName"] as? String ?: "",
        status = map["status"] as? String ?: "Active",
        maxCapacity = (map["maxCapacity"] as? Number)?.toInt() ?: 40,
        enrolledStudentCount = (map["enrolledStudentCount"] as? Number)?.toInt() ?: 0,
        syllabusChapters = (map["syllabusChapters"] as? List<*>)?.mapNotNull { it as? String } ?: emptyList(),
        instituteId = (map["instituteId"] as? String)?.ifBlank { "FSI_MAIN" } ?: "FSI_MAIN",
        createdAt = (map["createdAt"] as? Number)?.toLong() ?: System.currentTimeMillis(),
        updatedAt = (map["updatedAt"] as? Number)?.toLong() ?: System.currentTimeMillis(),
        syncStatus = map["syncStatus"] as? String ?: "SYNCED",
        conflictStrategy = map["conflictStrategy"] as? String ?: "LAST_WRITE_WINS_BY_UPDATED_AT"
      )
    }
  }
}

data class FirestoreCourseEnrollment(
  val enrollmentId: String = "",
  val studentId: String = "",
  val courseId: String = "",
  val batchId: String = "",
  val enrolledDate: String = "",
  val status: String = "Active", // "Active" | "Completed" | "Withdrawn"
  val discountPercent: Double = 0.0,
  val netFee: Double = 0.0,
  val instituteId: String = "FSI_MAIN",
  val createdAt: Long = System.currentTimeMillis()
) {
  fun toMap(): Map<String, Any> {
    return mapOf(
      "enrollmentId" to enrollmentId,
      "studentId" to studentId,
      "courseId" to courseId,
      "batchId" to batchId,
      "enrolledDate" to enrolledDate,
      "status" to status,
      "discountPercent" to discountPercent,
      "netFee" to netFee,
      "instituteId" to instituteId,
      "createdAt" to createdAt
    )
  }

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
    fun fromDomain(enrollment: CourseEnrollment): FirestoreCourseEnrollment {
      return FirestoreCourseEnrollment(
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

    fun fromMap(id: String, map: Map<String, Any>): FirestoreCourseEnrollment {
      return FirestoreCourseEnrollment(
        enrollmentId = id,
        studentId = map["studentId"] as? String ?: "",
        courseId = map["courseId"] as? String ?: "",
        batchId = map["batchId"] as? String ?: "",
        enrolledDate = map["enrolledDate"] as? String ?: "",
        status = map["status"] as? String ?: "Active",
        discountPercent = (map["discountPercent"] as? Number)?.toDouble() ?: 0.0,
        netFee = (map["netFee"] as? Number)?.toDouble() ?: 0.0,
        instituteId = (map["instituteId"] as? String)?.ifBlank { "FSI_MAIN" } ?: "FSI_MAIN",
        createdAt = (map["createdAt"] as? Number)?.toLong() ?: System.currentTimeMillis()
      )
    }
  }
}

// =============================================================================
// 3. FIRESTORE ATTENDANCE RECORD DATA MODEL
// =============================================================================
data class FirestoreAttendanceRecord(
  val id: String = "",
  val date: String = "", // "dd/MM/yyyy"
  val day: Int = 0,
  val month: Int = 0,
  val monthName: String = "",
  val year: Int = 0,
  val time: String = "",
  val studentId: String = "",
  val studentName: String = "",
  val courseId: String = "",
  val batchId: String = "",
  val batchName: String = "",
  val status: String = "Present", // "Present" | "Absent" | "Late" | "Excused"
  val sessionType: String = "Regular Class",
  val reason: String = "",
  val recordedBy: String = "",
  val instituteId: String = "FSI_MAIN",
  val timestamp: Long = System.currentTimeMillis(),
  val lastModifiedTimestamp: Long = System.currentTimeMillis(),
  val syncStatus: String = "SYNCED",
  val conflictStrategy: String = "LAST_WRITE_WINS_BY_UPDATED_AT"
) {
  fun toMap(): Map<String, Any> {
    return mapOf(
      "id" to id,
      "date" to date,
      "day" to day,
      "month" to month,
      "monthName" to monthName,
      "year" to year,
      "time" to time,
      "studentId" to studentId,
      "studentName" to studentName,
      "courseId" to courseId,
      "batchId" to batchId,
      "batchName" to batchName,
      "status" to status,
      "sessionType" to sessionType,
      "reason" to reason,
      "recordedBy" to recordedBy,
      "instituteId" to instituteId,
      "timestamp" to timestamp,
      "lastModifiedTimestamp" to lastModifiedTimestamp,
      "syncStatus" to syncStatus,
      "conflictStrategy" to conflictStrategy
    )
  }

  fun toDomain(): AttendanceRecord {
    return AttendanceRecord(
      id = id,
      date = date,
      studentId = studentId,
      studentName = studentName,
      batchName = batchName,
      status = status,
      timestamp = timestamp,
      lastModifiedTimestamp = lastModifiedTimestamp,
      day = day,
      month = month,
      monthName = monthName,
      year = year,
      time = time,
      reason = reason.ifBlank { null },
      recordedBy = recordedBy.ifBlank { null },
      batchId = batchId,
      courseId = courseId,
      sessionType = sessionType,
      instituteId = instituteId
    )
  }

  companion object {
    fun fromDomain(record: AttendanceRecord, syncStatus: String = "SYNCED"): FirestoreAttendanceRecord {
      return FirestoreAttendanceRecord(
        id = record.id,
        date = record.date,
        day = record.day,
        month = record.month,
        monthName = record.monthName,
        year = record.year,
        time = record.time,
        studentId = record.studentId,
        studentName = record.studentName,
        courseId = record.courseId,
        batchId = record.batchId,
        batchName = record.batchName,
        status = record.status,
        sessionType = record.sessionType,
        reason = record.reason ?: "",
        recordedBy = record.recordedBy ?: "",
        instituteId = record.instituteId.ifBlank { "FSI_MAIN" },
        timestamp = record.timestamp,
        lastModifiedTimestamp = record.lastModifiedTimestamp,
        syncStatus = syncStatus
      )
    }

    fun fromMap(id: String, map: Map<String, Any>): FirestoreAttendanceRecord {
      return FirestoreAttendanceRecord(
        id = id,
        date = map["date"] as? String ?: "",
        day = (map["day"] as? Number)?.toInt() ?: 0,
        month = (map["month"] as? Number)?.toInt() ?: 0,
        monthName = map["monthName"] as? String ?: "",
        year = (map["year"] as? Number)?.toInt() ?: 0,
        time = map["time"] as? String ?: "",
        studentId = map["studentId"] as? String ?: "",
        studentName = map["studentName"] as? String ?: "",
        courseId = map["courseId"] as? String ?: "",
        batchId = map["batchId"] as? String ?: "",
        batchName = map["batchName"] as? String ?: "",
        status = map["status"] as? String ?: "Present",
        sessionType = map["sessionType"] as? String ?: "Regular Class",
        reason = map["reason"] as? String ?: "",
        recordedBy = map["recordedBy"] as? String ?: "",
        instituteId = (map["instituteId"] as? String)?.ifBlank { "FSI_MAIN" } ?: "FSI_MAIN",
        timestamp = (map["timestamp"] as? Number)?.toLong() ?: System.currentTimeMillis(),
        lastModifiedTimestamp = (map["lastModifiedTimestamp"] as? Number)?.toLong() ?: System.currentTimeMillis(),
        syncStatus = map["syncStatus"] as? String ?: "SYNCED",
        conflictStrategy = map["conflictStrategy"] as? String ?: "LAST_WRITE_WINS_BY_UPDATED_AT"
      )
    }
  }
}

// =============================================================================
// 4. FIRESTORE FEE DATA MODELS (INVOICE, PAYMENT, STRUCTURE)
// =============================================================================
data class FirestoreFeeInvoice(
  val id: String = "",
  val studentId: String = "",
  val studentName: String = "",
  val courseId: String = "",
  val batchId: String = "",
  val batchName: String = "",
  val feeType: String = "Monthly Tuition",
  val feeAmount: Int = 0,
  val dueDate: String = "",
  val paidAmount: Int = 0,
  val pendingAmount: Int = 0,
  val paymentStatus: String = "Pending", // "Paid" | "Pending" | "Partial" | "Overdue"
  val month: String = "",
  val remindedCount: Int = 0,
  val lastReminded: String = "",
  val instituteId: String = "FSI_MAIN",
  val createdAt: Long = System.currentTimeMillis(),
  val updatedAt: Long = System.currentTimeMillis(),
  val syncStatus: String = "SYNCED",
  val conflictStrategy: String = "LAST_WRITE_WINS_BY_UPDATED_AT"
) {
  fun toMap(): Map<String, Any> {
    return mapOf(
      "id" to id,
      "studentId" to studentId,
      "studentName" to studentName,
      "courseId" to courseId,
      "batchId" to batchId,
      "batchName" to batchName,
      "feeType" to feeType,
      "feeAmount" to feeAmount,
      "dueDate" to dueDate,
      "paidAmount" to paidAmount,
      "pendingAmount" to pendingAmount,
      "paymentStatus" to paymentStatus,
      "month" to month,
      "remindedCount" to remindedCount,
      "lastReminded" to lastReminded,
      "instituteId" to instituteId,
      "createdAt" to createdAt,
      "updatedAt" to updatedAt,
      "syncStatus" to syncStatus,
      "conflictStrategy" to conflictStrategy
    )
  }

  fun toDomain(): FeeRecord {
    return FeeRecord(
      id = id,
      studentName = studentName,
      feeAmount = feeAmount,
      dueDate = dueDate,
      paidAmount = paidAmount,
      pendingAmount = pendingAmount,
      paymentStatus = paymentStatus,
      month = month,
      remindedCount = remindedCount,
      lastReminded = lastReminded,
      studentId = studentId,
      batchName = batchName,
      batchId = batchId,
      courseId = courseId,
      feeType = feeType,
      instituteId = instituteId
    )
  }

  companion object {
    fun fromDomain(record: FeeRecord, updatedAt: Long = System.currentTimeMillis(), syncStatus: String = "SYNCED"): FirestoreFeeInvoice {
      return FirestoreFeeInvoice(
        id = record.id,
        studentId = record.studentId,
        studentName = record.studentName,
        courseId = record.courseId,
        batchId = record.batchId,
        batchName = record.batchName,
        feeType = record.feeType,
        feeAmount = record.feeAmount,
        dueDate = record.dueDate,
        paidAmount = record.paidAmount,
        pendingAmount = record.pendingAmount,
        paymentStatus = record.paymentStatus,
        month = record.month,
        remindedCount = record.remindedCount,
        lastReminded = record.lastReminded,
        instituteId = record.instituteId.ifBlank { "FSI_MAIN" },
        createdAt = updatedAt,
        updatedAt = updatedAt,
        syncStatus = syncStatus
      )
    }

    fun fromMap(id: String, map: Map<String, Any>): FirestoreFeeInvoice {
      return FirestoreFeeInvoice(
        id = id,
        studentId = map["studentId"] as? String ?: "",
        studentName = map["studentName"] as? String ?: "",
        courseId = map["courseId"] as? String ?: "",
        batchId = map["batchId"] as? String ?: "",
        batchName = map["batchName"] as? String ?: "",
        feeType = map["feeType"] as? String ?: "Monthly Tuition",
        feeAmount = (map["feeAmount"] as? Number)?.toInt() ?: 0,
        dueDate = map["dueDate"] as? String ?: "",
        paidAmount = (map["paidAmount"] as? Number)?.toInt() ?: 0,
        pendingAmount = (map["pendingAmount"] as? Number)?.toInt() ?: 0,
        paymentStatus = map["paymentStatus"] as? String ?: "Pending",
        month = map["month"] as? String ?: "",
        remindedCount = (map["remindedCount"] as? Number)?.toInt() ?: 0,
        lastReminded = map["lastReminded"] as? String ?: "",
        instituteId = (map["instituteId"] as? String)?.ifBlank { "FSI_MAIN" } ?: "FSI_MAIN",
        createdAt = (map["createdAt"] as? Number)?.toLong() ?: System.currentTimeMillis(),
        updatedAt = (map["updatedAt"] as? Number)?.toLong() ?: System.currentTimeMillis(),
        syncStatus = map["syncStatus"] as? String ?: "SYNCED",
        conflictStrategy = map["conflictStrategy"] as? String ?: "LAST_WRITE_WINS_BY_UPDATED_AT"
      )
    }
  }
}

data class FirestoreFeePayment(
  val id: String = "",
  val invoiceId: String = "",
  val studentId: String = "",
  val studentName: String = "",
  val courseId: String = "",
  val amount: Double = 0.0,
  val paymentDate: String = "",
  val paymentStatus: String = "Paid", // "Paid" | "Pending" | "Partial" | "Refunded"
  val feeType: String = "Monthly Tuition",
  val paymentMode: String = "UPI", // "UPI" | "Cash" | "Card" | "Net Banking" | "Cheque"
  val receiptNo: String = "",
  val transactionId: String = "",
  val month: String = "",
  val remarks: String = "",
  val recordedBy: String = "Admin",
  val timestamp: Long = System.currentTimeMillis(),
  val instituteId: String = "FSI_MAIN",
  val syncStatus: String = "SYNCED",
  val conflictStrategy: String = "LAST_WRITE_WINS_BY_UPDATED_AT"
) {
  fun toMap(): Map<String, Any> {
    return mapOf(
      "id" to id,
      "invoiceId" to invoiceId,
      "studentId" to studentId,
      "studentName" to studentName,
      "courseId" to courseId,
      "amount" to amount,
      "paymentDate" to paymentDate,
      "paymentStatus" to paymentStatus,
      "feeType" to feeType,
      "paymentMode" to paymentMode,
      "receiptNo" to receiptNo,
      "transactionId" to transactionId,
      "month" to month,
      "remarks" to remarks,
      "recordedBy" to recordedBy,
      "timestamp" to timestamp,
      "instituteId" to instituteId,
      "syncStatus" to syncStatus,
      "conflictStrategy" to conflictStrategy
    )
  }

  fun toDomain(): StudentFeePayment {
    return StudentFeePayment(
      id = id,
      studentId = studentId,
      studentName = studentName,
      amount = amount,
      paymentDate = paymentDate,
      paymentStatus = paymentStatus,
      feeType = feeType,
      paymentMode = paymentMode,
      receiptNo = receiptNo,
      month = month,
      remarks = remarks,
      recordedBy = recordedBy,
      timestamp = timestamp,
      invoiceId = invoiceId,
      courseId = courseId,
      instituteId = instituteId,
      transactionId = transactionId.ifBlank { receiptNo }
    )
  }

  companion object {
    fun fromDomain(payment: StudentFeePayment, syncStatus: String = "SYNCED"): FirestoreFeePayment {
      return FirestoreFeePayment(
        id = payment.id,
        invoiceId = payment.invoiceId,
        studentId = payment.studentId,
        studentName = payment.studentName,
        courseId = payment.courseId,
        amount = payment.amount,
        paymentDate = payment.paymentDate,
        paymentStatus = payment.paymentStatus,
        feeType = payment.feeType,
        paymentMode = payment.paymentMode,
        receiptNo = payment.receiptNo,
        transactionId = payment.transactionId,
        month = payment.month,
        remarks = payment.remarks,
        recordedBy = payment.recordedBy,
        timestamp = payment.timestamp,
        instituteId = payment.instituteId.ifBlank { "FSI_MAIN" },
        syncStatus = syncStatus
      )
    }

    fun fromMap(id: String, map: Map<String, Any>): FirestoreFeePayment {
      return FirestoreFeePayment(
        id = id,
        invoiceId = map["invoiceId"] as? String ?: "",
        studentId = map["studentId"] as? String ?: "",
        studentName = map["studentName"] as? String ?: "",
        courseId = map["courseId"] as? String ?: "",
        amount = (map["amount"] as? Number)?.toDouble() ?: 0.0,
        paymentDate = map["paymentDate"] as? String ?: "",
        paymentStatus = map["paymentStatus"] as? String ?: "Paid",
        feeType = map["feeType"] as? String ?: "Monthly Tuition",
        paymentMode = map["paymentMode"] as? String ?: "UPI",
        receiptNo = map["receiptNo"] as? String ?: "",
        transactionId = map["transactionId"] as? String ?: "",
        month = map["month"] as? String ?: "",
        remarks = map["remarks"] as? String ?: "",
        recordedBy = map["recordedBy"] as? String ?: "Admin",
        timestamp = (map["timestamp"] as? Number)?.toLong() ?: System.currentTimeMillis(),
        instituteId = (map["instituteId"] as? String)?.ifBlank { "FSI_MAIN" } ?: "FSI_MAIN",
        syncStatus = map["syncStatus"] as? String ?: "SYNCED",
        conflictStrategy = map["conflictStrategy"] as? String ?: "LAST_WRITE_WINS_BY_UPDATED_AT"
      )
    }
  }
}

data class FirestoreFeeStructure(
  val id: String = "",
  val courseId: String = "",
  val courseName: String = "",
  val grade: String = "",
  val stream: String = "",
  val baseTuitionFee: Double = 0.0,
  val examFee: Double = 0.0,
  val materialFee: Double = 0.0,
  val totalFee: Double = 0.0,
  val installmentCount: Int = 3,
  val installmentIntervalMonths: Int = 3,
  val allowDiscount: Boolean = true,
  val academicYear: String = "2026–2027",
  val instituteId: String = "FSI_MAIN",
  val createdAt: Long = System.currentTimeMillis(),
  val updatedAt: Long = System.currentTimeMillis(),
  val syncStatus: String = "SYNCED"
) {
  fun toMap(): Map<String, Any> {
    return mapOf(
      "id" to id,
      "courseId" to courseId,
      "courseName" to courseName,
      "grade" to grade,
      "stream" to stream,
      "baseTuitionFee" to baseTuitionFee,
      "examFee" to examFee,
      "materialFee" to materialFee,
      "totalFee" to totalFee,
      "installmentCount" to installmentCount,
      "installmentIntervalMonths" to installmentIntervalMonths,
      "allowDiscount" to allowDiscount,
      "academicYear" to academicYear,
      "instituteId" to instituteId,
      "createdAt" to createdAt,
      "updatedAt" to updatedAt,
      "syncStatus" to syncStatus
    )
  }

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
    fun fromDomain(structure: FeeStructure): FirestoreFeeStructure {
      return FirestoreFeeStructure(
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
        createdAt = structure.createdAt,
        updatedAt = structure.createdAt
      )
    }

    fun fromMap(id: String, map: Map<String, Any>): FirestoreFeeStructure {
      return FirestoreFeeStructure(
        id = id,
        courseId = map["courseId"] as? String ?: "",
        courseName = map["courseName"] as? String ?: "",
        grade = map["grade"] as? String ?: "",
        stream = map["stream"] as? String ?: "",
        baseTuitionFee = (map["baseTuitionFee"] as? Number)?.toDouble() ?: 0.0,
        examFee = (map["examFee"] as? Number)?.toDouble() ?: 0.0,
        materialFee = (map["materialFee"] as? Number)?.toDouble() ?: 0.0,
        totalFee = (map["totalFee"] as? Number)?.toDouble() ?: 0.0,
        installmentCount = (map["installmentCount"] as? Number)?.toInt() ?: 3,
        installmentIntervalMonths = (map["installmentIntervalMonths"] as? Number)?.toInt() ?: 3,
        allowDiscount = map["allowDiscount"] as? Boolean ?: true,
        academicYear = map["academicYear"] as? String ?: "2026–2027",
        instituteId = (map["instituteId"] as? String)?.ifBlank { "FSI_MAIN" } ?: "FSI_MAIN",
        createdAt = (map["createdAt"] as? Number)?.toLong() ?: System.currentTimeMillis(),
        updatedAt = (map["updatedAt"] as? Number)?.toLong() ?: System.currentTimeMillis(),
        syncStatus = map["syncStatus"] as? String ?: "SYNCED"
      )
    }
  }
}
